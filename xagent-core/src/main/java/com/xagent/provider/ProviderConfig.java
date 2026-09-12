/*
 * Copyright (C) 2002-2026 DogsBay Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.xagent.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves provider configuration from CLI flags, environment variables,
 * and ~/.xagent/settings.json (in that priority order).
 */
public class ProviderConfig {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * The settings file path, resolved per call so tests can isolate it via the
	 * {@code xagent.settings.file} system property (defaults to
	 * {@code ~/.xagent/settings.json}).
	 */
	private static Path settingsFile() {
		String override = System.getProperty("xagent.settings.file");
		return override != null
			? Path.of(override)
			: Path.of(System.getProperty("user.home"), ".xagent", "settings.json");
	}

	private final String provider;
	private final String model;
	private final String apiKey;
	private final String baseUrl;

	private ProviderConfig(String provider, String model, String apiKey, String baseUrl) {
		this.provider = provider;
		this.model = model;
		this.apiKey = apiKey;
		this.baseUrl = baseUrl;
	}

	public String provider() {
		return provider;
	}

	public String model() {
		return model;
	}

	public String apiKey() {
		return apiKey;
	}

	public String baseUrl() {
		return baseUrl;
	}

	/**
	 * Resolve configuration with CLI flags taking highest priority,
	 * then environment variables, then settings file.
	 */
	public static ProviderConfig resolve(
		String cliProvider,
		String cliModel,
		String cliApiKey,
		String cliBaseUrl
	) {
		JsonNode settings = loadSettingsFile();

		// Explicit choice (flag > env > settings) always wins. With nothing
		// chosen, fall back to a smart default based on available credentials.
		String explicitProvider = first(
			cliProvider,
			System.getenv("XAGENT_PROVIDER"),
			jsonString(settings, "defaultProvider")
		);
		String provider = explicitProvider != null ? explicitProvider : smartDefaultProvider(
			System.getenv("OPENAI_API_KEY") != null,
			System.getenv("ANTHROPIC_API_KEY") != null,
			System.getenv("GEMINI_API_KEY") != null,
			codexSignedIn()
		);

		// The flat defaultModel predates the per-provider map and says nothing
		// about which provider chose it, so it counts only when the saved
		// provider is the one being resolved. Otherwise a Gemini model saved
		// before a switch to Anthropic becomes Anthropic's model on the next
		// launch — the invalid pair every backend rejects.
		String flatModel = provider.equals(jsonString(settings, "defaultProvider"))
			? jsonString(settings, "defaultModel") : null;
		String model = first(
			cliModel,
			System.getenv("XAGENT_MODEL"),
			usable(provider, savedModelFor(settings, provider)),
			usable(provider, flatModel),
			defaultModelFor(provider)
		);

		String apiKey = first(
			cliApiKey,
			apiKeyFromEnv(provider),
			jsonString(settings, "apiKey")
		);

		String baseUrl = first(
			cliBaseUrl,
			System.getenv("XAGENT_BASE_URL"),
			jsonString(settings, "baseUrl")
		);

		return new ProviderConfig(provider, model, apiKey, baseUrl);
	}

	/**
	 * Resolve a config for an explicit provider <em>switch</em>. A clean switch must
	 * not inherit the previously-active provider's globally-saved {@code defaultModel}
	 * or flat saved {@code apiKey}: carrying the model across (e.g. a Gemini model into
	 * an {@code openai-codex} / ChatGPT session) yields an invalid provider+model pair
	 * the backend rejects, and carrying the key authenticates the new provider with the
	 * old one's credentials. Deliberate environment overrides ({@code XAGENT_MODEL},
	 * the provider's own {@code *_API_KEY}, {@code XAGENT_BASE_URL}) are still honored.
	 *
	 * <p>A model saved <em>for this provider</em> ({@code models.<provider>}) is a
	 * different matter and is honored: it cannot be another provider's, because it
	 * is filed under the one it belongs to. That is what makes /model stick across
	 * a switch away and back.
	 *
	 * @param provider the provider being switched to
	 * @param apiKey   an explicit key, or null to fall back to env/keychain
	 */
	public static ProviderConfig forProvider(String provider, String apiKey) {
		String model = first(System.getenv("XAGENT_MODEL"),
			usable(provider, savedModelFor(loadSettingsFile(), provider)), defaultModelFor(provider));
		String key = first(apiKey, apiKeyFromEnv(provider));
		String baseUrl = first(System.getenv("XAGENT_BASE_URL"), jsonString(loadSettingsFile(), "baseUrl"));
		return new ProviderConfig(provider, model, key, baseUrl);
	}

	/**
	 * A saved model, or null when this provider cannot use it.
	 *
	 * <p>Until the Codex default was fixed, every provider switch and sign-in
	 * wrote the model then in force into settings.json — so a Codex session
	 * saved {@code gpt-5.4}, the OpenAI API's default, which that backend
	 * refuses. The file outlives the fix, and nothing else would ever correct
	 * it: a stored value that cannot work is worse than none.
	 */
	private static String usable(String provider, String model) {
		if ("openai-codex".equals(provider) && defaultModelFor("openai").equals(model)) {
			return null;
		}
		return model;
	}

	/**
	 * Persist the provider (and optionally an API key) without pinning a model.
	 *
	 * <p>Switching provider and signing in used to save the model as well, but
	 * that model was a default nobody chose, and writing it turned a default
	 * into a decision the code could never revise. Only {@code /model} records
	 * a model, because only there did someone pick one.
	 */
	public static void saveProviderDefault(String provider, String apiKey) throws IOException {
		saveDefaults(provider, null, apiKey);
	}

	/**
	 * Persist provider/model (and optionally an API key) as the defaults in
	 * ~/.xagent/settings.json, preserving any other keys already in the file.
	 * A null apiKey leaves the stored key untouched.
	 */
	public static void saveDefaults(String provider, String model, String apiKey) throws IOException {
		com.fasterxml.jackson.databind.node.ObjectNode root;
		if (Files.isRegularFile(settingsFile())) {
			JsonNode parsed = MAPPER.readTree(Files.readString(settingsFile()));
			root = parsed.isObject() ? (com.fasterxml.jackson.databind.node.ObjectNode) parsed
				: MAPPER.createObjectNode();
		} else {
			root = MAPPER.createObjectNode();
		}
		// Switching provider without naming a model drops the flat defaultModel:
		// it was the previous provider's, and leaving it makes it the new
		// provider's on the next launch. Per-provider entries keep the real
		// choices, so nothing is lost by switching away and back.
		if (model == null && provider != null
				&& !provider.equals(jsonString(root, "defaultProvider"))) {
			root.remove("defaultModel");
		}
		if (provider != null) root.put("defaultProvider", provider);
		if (model != null) root.put("defaultModel", model);
		if (provider != null && model != null) {
			// Also file it under the provider it belongs to, so switching away and
			// back returns to this model rather than to the built-in default.
			JsonNode existing = root.get(MODELS_FIELD);
			com.fasterxml.jackson.databind.node.ObjectNode models =
				existing != null && existing.isObject()
					? (com.fasterxml.jackson.databind.node.ObjectNode) existing
					: root.putObject(MODELS_FIELD);
			models.put(provider, model);
		}
		if (apiKey != null) root.put("apiKey", apiKey);
		Files.createDirectories(settingsFile().getParent());
		Files.writeString(settingsFile(), MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n");
	}

	/**
	 * The model this provider would use with nothing configured, and where that
	 * comes from — the answer to "why am I talking to this model", which is not
	 * obvious when it was read out of another tool's config file.
	 */
	public static String defaultModelHint(String provider) {
		if ("openai-codex".equals(provider)) {
			String fromCli = codexCliModel();
			return fromCli != null
				? fromCli + " (from ~/.codex/config.toml)"
				: CODEX_FALLBACK_MODEL + " (built in; the Codex CLI's config would win)";
		}
		return defaultModelFor(provider);
	}

	/**
	 * The API key saved in {@code ~/.xagent/settings.json}, or null.
	 *
	 * <p>
	 * This is the plaintext fallback taken when the machine has no credential
	 * store. It is one flat field rather than a per-provider entry, so it serves
	 * whichever provider is resolved — which is also why forgetting a key has to
	 * clear it whatever provider was asked about.
	 */
	public static String savedApiKey() {
		try {
			if (!Files.isRegularFile(settingsFile())) {
				return null;
			}
			JsonNode parsed = MAPPER.readTree(Files.readString(settingsFile()));
			return parsed.isObject() ? jsonString(parsed, "apiKey") : null;
		} catch (Exception unreadable) {
			return null;
		}
	}

	/**
	 * Remove the saved API key, leaving the rest of the file alone. Does nothing
	 * when there is no file and no key.
	 */
	public static void clearSavedApiKey() throws IOException {
		if (!Files.isRegularFile(settingsFile())) {
			return;
		}
		JsonNode parsed = MAPPER.readTree(Files.readString(settingsFile()));
		if (!parsed.isObject()) {
			return;
		}
		com.fasterxml.jackson.databind.node.ObjectNode root =
			(com.fasterxml.jackson.databind.node.ObjectNode) parsed;
		if (root.remove("apiKey") == null) {
			return;
		}
		Files.writeString(settingsFile(), MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n");
	}

	/** Where per-provider model choices live in settings.json. */
	private static final String MODELS_FIELD = "models";

	/**
	 * The model saved for this provider, or null. Keyed by provider, so unlike
	 * the flat {@code defaultModel} it can never hand one provider another's
	 * model — the pair the backend rejects.
	 */
	private static String savedModelFor(JsonNode settings, String provider) {
		if (settings == null || provider == null) {
			return null;
		}
		JsonNode models = settings.get(MODELS_FIELD);
		return models != null && models.isObject() ? jsonString(models, provider) : null;
	}

	/**
	 * Pick a provider when the user hasn't chosen one. If exactly one provider
	 * has usable credentials, use it; otherwise keep openai (the agent then
	 * surfaces a "set a key / run /provider" hint). Ollama is never auto-picked
	 * — it always "works" without a key, so it can't disambiguate.
	 */
	static String smartDefaultProvider(boolean openaiKey, boolean anthropicKey,
		boolean geminiKey, boolean codexSignedIn) {
		var candidates = new java.util.ArrayList<String>();
		if (openaiKey) candidates.add("openai");
		if (anthropicKey) candidates.add("anthropic");
		if (geminiKey) candidates.add("gemini");
		if (codexSignedIn) candidates.add("openai-codex");
		return candidates.size() == 1 ? candidates.get(0) : "openai";
	}

	private static boolean codexSignedIn() {
		try {
			return new com.xagent.auth.AuthStorage().get("openai-codex").isPresent();
		} catch (Exception e) {
			return false;
		}
	}

	private static String apiKeyFromEnv(String provider) {
		return switch (provider) {
			case "openai" -> System.getenv("OPENAI_API_KEY");
			case "anthropic" -> System.getenv("ANTHROPIC_API_KEY");
			case "gemini" -> System.getenv("GEMINI_API_KEY");
			default -> null;
		};
	}

	private static String defaultModelFor(String provider) {
		return switch (provider) {
			case "openai" -> "gpt-5.4";
			case "openai-codex" -> codexModel();
			case "anthropic" -> "claude-sonnet-4-6-20250217";
			case "gemini" -> "gemini-3.1-flash-lite-preview";
			case "ollama" -> "qwen3";
			default -> "gpt-5.4";
		};
	}

	/**
	 * The model for a ChatGPT-subscription Codex session. That backend takes a
	 * different set of model names from the OpenAI API — asking it for the API's
	 * default gets "The 'gpt-5.4' model is not supported when using Codex with a
	 * ChatGPT account", four times over, once per retry.
	 *
	 * <p>The Codex CLI's own configured model is the best answer available: same
	 * account, same backend, and it is the reference client for it. Read it from
	 * {@code ~/.codex/config.toml} and fall back to {@link #CODEX_FALLBACK_MODEL}
	 * when there is no Codex CLI on the machine.
	 */
	static String codexModel() {
		String configured = codexCliModel();
		return configured != null ? configured : CODEX_FALLBACK_MODEL;
	}

	/**
	 * A model name the Codex backend accepts, for a machine with no Codex CLI to
	 * ask. Override it with {@code XAGENT_MODEL} or {@code defaultModel} in
	 * ~/.xagent/settings.json when the subscription offers something newer.
	 */
	static final String CODEX_FALLBACK_MODEL = "gpt-5.6-sol";

	/**
	 * {@code model = "..."} from the top of ~/.codex/config.toml. Only the
	 * top-level key counts: everything after the first {@code [section]} header
	 * is a per-project or per-profile override, which is not the default.
	 */
	private static String codexCliModel() {
		try {
			Path config = Path.of(System.getProperty("user.home"), ".codex", "config.toml");
			if (!Files.exists(config)) {
				return null;
			}
			for (String line : Files.readAllLines(config)) {
				String trimmed = line.trim();
				if (trimmed.startsWith("[")) {
					return null; // past the top-level keys
				}
				// lookingAt, not matches: TOML allows a trailing comment after the
				// value, and requiring the whole line to be the assignment made
				//   model = "gpt-5.7-codex"  # my subscription
				// invisible, silently falling back to the built-in default.
				var m = CODEX_MODEL_LINE.matcher(trimmed);
				if (m.lookingAt()) {
					return m.group(1);
				}
			}
		} catch (Exception ignored) {
			// unreadable/absent config — fall back
		}
		return null;
	}

	private static final java.util.regex.Pattern CODEX_MODEL_LINE =
		java.util.regex.Pattern.compile("^model\\s*=\\s*[\"']([^\"']+)[\"']");

	private static JsonNode loadSettingsFile() {
		try {
			if (Files.exists(settingsFile())) {
				return MAPPER.readTree(Files.readString(settingsFile()));
			}
		} catch (IOException ignored) {
		}
		return null;
	}

	private static String jsonString(JsonNode node, String field) {
		if (node != null && node.has(field) && !node.get(field).isNull()) {
			return node.get(field).asText();
		}
		return null;
	}

	private static String first(String... values) {
		for (String v : values) {
			if (v != null && !v.isBlank()) {
				return v;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return "ProviderConfig{provider='%s', model='%s', baseUrl='%s'}".formatted(
			provider, model, baseUrl
		);
	}
}
