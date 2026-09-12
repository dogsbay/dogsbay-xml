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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderConfigTest {

	// Isolate from the developer's real ~/.xagent/settings.json: point resolve()
	// at a non-existent file so a local defaultProvider can't sway these tests.
	@BeforeAll
	static void isolateSettingsFile() {
		System.setProperty("xagent.settings.file",
			Path.of(System.getProperty("java.io.tmpdir"), "xagent-test-no-such-settings.json").toString());
	}

	@AfterAll
	static void clearSettingsOverride() {
		System.clearProperty("xagent.settings.file");
	}

	@Test
	void smartDefaultPicksTheSoleCredentialedProvider() {
		assertThat(ProviderConfig.smartDefaultProvider(false, false, true, false)).isEqualTo("gemini");
		assertThat(ProviderConfig.smartDefaultProvider(false, true, false, false)).isEqualTo("anthropic");
		assertThat(ProviderConfig.smartDefaultProvider(false, false, false, true)).isEqualTo("openai-codex");
		assertThat(ProviderConfig.smartDefaultProvider(true, false, false, false)).isEqualTo("openai");
	}

	@Test
	void smartDefaultFallsBackToOpenaiWhenNoneOrAmbiguous() {
		// none configured -> openai (the agent then shows a setup hint)
		assertThat(ProviderConfig.smartDefaultProvider(false, false, false, false)).isEqualTo("openai");
		// more than one -> openai (don't guess; user should pick)
		assertThat(ProviderConfig.smartDefaultProvider(false, true, true, false)).isEqualTo("openai");
		assertThat(ProviderConfig.smartDefaultProvider(true, true, false, false)).isEqualTo("openai");
	}

	@Test
	void resolveWithCliFlags() {
		var config = ProviderConfig.resolve("anthropic", "claude-sonnet-4-20250514", "sk-test", null);

		assertThat(config.provider()).isEqualTo("anthropic");
		assertThat(config.model()).isEqualTo("claude-sonnet-4-20250514");
		assertThat(config.apiKey()).isEqualTo("sk-test");
		assertThat(config.baseUrl()).isNull();
	}

	@Test
	void resolveDefaultsToOpenAi() {
		var config = ProviderConfig.resolve(null, null, null, null);

		assertThat(config.provider()).isEqualTo("openai");
		assertThat(config.model()).isEqualTo("gpt-5.4");
	}

	@Test
	void resolveCliOverridesDefaults() {
		var config = ProviderConfig.resolve("ollama", "llama3", null, "http://localhost:11434");

		assertThat(config.provider()).isEqualTo("ollama");
		assertThat(config.model()).isEqualTo("llama3");
		assertThat(config.baseUrl()).isEqualTo("http://localhost:11434");
	}

	@Test
	void toStringDoesNotLeakApiKey() {
		var config = ProviderConfig.resolve("openai", "gpt-4o", "secret-key-123", null);

		assertThat(config.toString()).doesNotContain("secret-key-123");
	}

	@Test
	void forProviderUsesProvidersDefaultModelNotASavedCrossProviderOne() throws Exception {
		// A previously-saved Gemini default in settings.json.
		java.nio.file.Path settings = java.nio.file.Files.createTempFile("xagent-settings", ".json");
		java.nio.file.Files.writeString(settings,
			"{\"defaultProvider\":\"gemini\",\"defaultModel\":\"gemini-3.1-flash-lite-preview\"}");
		System.setProperty("xagent.settings.file", settings.toString());
		try {
			// resolve() used to carry the saved Gemini model into a different
			// provider; it now applies the flat defaultModel only to the provider
			// it was saved under.
			assertThat(ProviderConfig.resolve("openai-codex", null, null, null).model())
				.isEqualTo(ProviderConfig.codexModel());
			assertThat(ProviderConfig.resolve("gemini", null, null, null).model())
				.isEqualTo("gemini-3.1-flash-lite-preview");

			// And switching providers uses THAT provider's default model.
			var cfg = ProviderConfig.forProvider("openai-codex", null);
			assertThat(cfg.provider()).isEqualTo("openai-codex");
			assertThat(cfg.model())
				.isNotEqualTo("gemini-3.1-flash-lite-preview")
				.isEqualTo(ProviderConfig.codexModel());
		} finally {
			isolateSettingsFile(); // restore the no-such-file override for other tests
			java.nio.file.Files.deleteIfExists(settings);
		}
	}

	@Test
	void forProviderDoesNotInheritAnotherProvidersSavedApiKey() throws Exception {
		// A flat saved key belonging to whatever provider was active before.
		java.nio.file.Path settings = java.nio.file.Files.createTempFile("xagent-settings", ".json");
		java.nio.file.Files.writeString(settings,
			"{\"defaultProvider\":\"openai\",\"apiKey\":\"sk-openai-saved\"}");
		System.setProperty("xagent.settings.file", settings.toString());
		try {
			// resolve() leaks the flat saved key into a different provider (openai-codex
			// has no env key mapping, so the test is deterministic regardless of the
			// developer's environment)...
			assertThat(ProviderConfig.resolve("openai-codex", null, null, null).apiKey())
				.isEqualTo("sk-openai-saved");

			// ...but a clean provider switch must not authenticate with it.
			assertThat(ProviderConfig.forProvider("openai-codex", null).apiKey()).isNull();
		} finally {
			isolateSettingsFile(); // restore the no-such-file override for other tests
			java.nio.file.Files.deleteIfExists(settings);
		}
	}

	// ── the Codex model ─────────────────────────────────────────────────
	//
	// The ChatGPT-subscription backend takes different model names from the
	// OpenAI API. Defaulting a Codex session to the API's "gpt-5.4" got "The
	// 'gpt-5.4' model is not supported when using Codex with a ChatGPT account".

	@Test
	void codexTakesItsModelFromTheCodexCliConfig(@org.junit.jupiter.api.io.TempDir
			java.nio.file.Path home) throws Exception {
		java.nio.file.Path codex = java.nio.file.Files.createDirectories(home.resolve(".codex"));
		java.nio.file.Files.writeString(codex.resolve("config.toml"), """
			model = "gpt-5.6-sol"
			model_reasoning_effort = "high"

			[projects."/somewhere"]
			trust_level = "trusted"
			""");

		String previous = System.getProperty("user.home");
		System.setProperty("user.home", home.toString());
		try {
			assertThat(ProviderConfig.codexModel()).isEqualTo("gpt-5.6-sol");
		} finally {
			System.setProperty("user.home", previous);
		}
	}

	@Test
	void codexIgnoresAPerProjectModelOverride(@org.junit.jupiter.api.io.TempDir
			java.nio.file.Path home) throws Exception {
		java.nio.file.Path codex = java.nio.file.Files.createDirectories(home.resolve(".codex"));
		// A model inside a [section] is that project's or profile's choice, not
		// the default, so it must not become the session's model.
		java.nio.file.Files.writeString(codex.resolve("config.toml"), """
			[profiles.experiment]
			model = "something-experimental"
			""");

		String previous = System.getProperty("user.home");
		System.setProperty("user.home", home.toString());
		try {
			assertThat(ProviderConfig.codexModel()).isEqualTo(ProviderConfig.CODEX_FALLBACK_MODEL);
		} finally {
			System.setProperty("user.home", previous);
		}
	}

	@Test
	void codexFallsBackWhenThereIsNoCodexCli(@org.junit.jupiter.api.io.TempDir
			java.nio.file.Path home) {
		String previous = System.getProperty("user.home");
		System.setProperty("user.home", home.toString());
		try {
			assertThat(ProviderConfig.codexModel()).isEqualTo(ProviderConfig.CODEX_FALLBACK_MODEL);
			// Whatever the fallback is, it must not be the OpenAI API default,
			// which is the name the Codex backend rejects.
			assertThat(ProviderConfig.CODEX_FALLBACK_MODEL).isNotEqualTo("gpt-5.4");
		} finally {
			System.setProperty("user.home", previous);
		}
	}

	// ── per-provider model memory ───────────────────────────────────────
	//
	// One global defaultModel could not be honoured on a provider switch (a
	// Gemini model in a Codex session is a pair the backend rejects), so a
	// /model choice was forgotten the moment you switched away and back. Filed
	// under the provider it belongs to, it can be honoured safely.

	@Test
	void aModelSavedForAProviderSurvivesASwitchAwayAndBack() throws Exception {
		java.nio.file.Path settings = java.nio.file.Files.createTempFile("xagent-settings", ".json");
		java.nio.file.Files.writeString(settings, """
			{"defaultProvider": "gemini",
			 "defaultModel": "gemini-3.1-flash-lite-preview",
			 "models": {"openai-codex": "gpt-5.6-sol", "anthropic": "claude-opus-4-8"}}
			""");
		System.setProperty("xagent.settings.file", settings.toString());
		try {
			assertThat(ProviderConfig.forProvider("openai-codex", null).model())
				.isEqualTo("gpt-5.6-sol");
			assertThat(ProviderConfig.forProvider("anthropic", null).model())
				.isEqualTo("claude-opus-4-8");
			// A provider with no saved entry still gets its own default, never
			// the global defaultModel belonging to another provider.
			assertThat(ProviderConfig.forProvider("ollama", null).model())
				.isEqualTo("qwen3");
		} finally {
			isolateSettingsFile();
			java.nio.file.Files.deleteIfExists(settings);
		}
	}

	@Test
	void savingDefaultsFilesTheModelUnderItsProvider() throws Exception {
		java.nio.file.Path settings = java.nio.file.Files.createTempFile("xagent-settings", ".json");
		java.nio.file.Files.writeString(settings, "{\"models\": {\"anthropic\": \"claude-opus-4-8\"}}");
		System.setProperty("xagent.settings.file", settings.toString());
		try {
			ProviderConfig.saveDefaults("openai-codex", "gpt-5.6-sol", null);

			String written = java.nio.file.Files.readString(settings);
			assertThat(written).contains("\"openai-codex\" : \"gpt-5.6-sol\"");
			// …without discarding what another provider had already chosen.
			assertThat(written).contains("\"anthropic\" : \"claude-opus-4-8\"");
			assertThat(ProviderConfig.forProvider("openai-codex", null).model())
				.isEqualTo("gpt-5.6-sol");
		} finally {
			isolateSettingsFile();
			java.nio.file.Files.deleteIfExists(settings);
		}
	}

	@Test
	void theModelHintSaysWhereACodexDefaultComesFrom(@org.junit.jupiter.api.io.TempDir
			java.nio.file.Path home) throws Exception {
		String previous = System.getProperty("user.home");
		System.setProperty("user.home", home.toString());
		try {
			assertThat(ProviderConfig.defaultModelHint("openai-codex"))
				.contains(ProviderConfig.CODEX_FALLBACK_MODEL)
				.contains("built in");

			java.nio.file.Path codex = java.nio.file.Files.createDirectories(home.resolve(".codex"));
			java.nio.file.Files.writeString(codex.resolve("config.toml"), "model = \"gpt-5.6-sol\"\n");
			assertThat(ProviderConfig.defaultModelHint("openai-codex"))
				.contains("gpt-5.6-sol")
				.contains(".codex/config.toml");

			// Other providers just name their default.
			assertThat(ProviderConfig.defaultModelHint("ollama")).isEqualTo("qwen3");
		} finally {
			System.setProperty("user.home", previous);
		}
	}

	// ── settings written before the Codex default was fixed ─────────────

	@Test
	void aCodexSessionIgnoresTheOpenAiApiModelSavedByTheOldBug() throws Exception {
		java.nio.file.Path settings = java.nio.file.Files.createTempFile("xagent-settings", ".json");
		// Exactly what a machine that ran the old build has on disk: a provider
		// switch persisted the model then in force, which for Codex was the
		// OpenAI API default the ChatGPT backend refuses.
		java.nio.file.Files.writeString(settings,
			"{\"defaultProvider\": \"openai-codex\", \"defaultModel\": \"gpt-5.4\"}");
		System.setProperty("xagent.settings.file", settings.toString());
		try {
			assertThat(ProviderConfig.resolve(null, null, null, null).model())
				.isNotEqualTo("gpt-5.4")
				.isEqualTo(ProviderConfig.codexModel());
			assertThat(ProviderConfig.forProvider("openai-codex", null).model())
				.isNotEqualTo("gpt-5.4");

			// The same name is the right answer for the API provider, and is left alone.
			assertThat(ProviderConfig.resolve("openai", null, null, null).model())
				.isEqualTo("gpt-5.4");
		} finally {
			isolateSettingsFile();
			java.nio.file.Files.deleteIfExists(settings);
		}
	}

	@Test
	void switchingProviderDoesNotPinAModelNobodyChose() throws Exception {
		java.nio.file.Path settings = java.nio.file.Files.createTempFile("xagent-settings", ".json");
		java.nio.file.Files.writeString(settings, "{}");
		System.setProperty("xagent.settings.file", settings.toString());
		try {
			ProviderConfig.saveProviderDefault("openai-codex", null);

			String written = java.nio.file.Files.readString(settings);
			assertThat(written).contains("openai-codex");
			// No model recorded: a default is not a decision, and writing it as
			// one is what froze gpt-5.4 into everyone's settings.
			assertThat(written).doesNotContain("defaultModel");
			assertThat(written).doesNotContain("models");
		} finally {
			isolateSettingsFile();
			java.nio.file.Files.deleteIfExists(settings);
		}
	}

	@Test
	void aTrailingCommentDoesNotHideTheCodexModel(@org.junit.jupiter.api.io.TempDir
			java.nio.file.Path home) throws Exception {
		java.nio.file.Path codex = java.nio.file.Files.createDirectories(home.resolve(".codex"));
		// TOML allows a comment after the value; requiring the whole line to be
		// the assignment made the user's real model invisible.
		java.nio.file.Files.writeString(codex.resolve("config.toml"),
			"model = \"gpt-5.7-codex\"  # my subscription\n");

		String previous = System.getProperty("user.home");
		System.setProperty("user.home", home.toString());
		try {
			assertThat(ProviderConfig.codexModel()).isEqualTo("gpt-5.7-codex");
		} finally {
			System.setProperty("user.home", previous);
		}
	}

	@Test
	void theFlatDefaultModelBelongsToTheProviderThatSavedIt() throws Exception {
		java.nio.file.Path settings = java.nio.file.Files.createTempFile("xagent-settings", ".json");
		// Saved while gemini was active, then the provider was switched away.
		java.nio.file.Files.writeString(settings,
			"{\"defaultProvider\": \"gemini\", \"defaultModel\": \"gemini-3.1-pro\","
			+ " \"models\": {\"gemini\": \"gemini-3.1-pro\"}}");
		System.setProperty("xagent.settings.file", settings.toString());
		try {
			// Switch to anthropic without naming a model.
			ProviderConfig.saveProviderDefault("anthropic", null);

			// Next launch: anthropic must not be asked for the Gemini model that
			// outlived the provider which chose it.
			assertThat(ProviderConfig.resolve(null, null, null, null).model())
				.isNotEqualTo("gemini-3.1-pro")
				.isEqualTo("claude-sonnet-4-6-20250217");
			// Gemini's own choice survives the round trip.
			assertThat(ProviderConfig.forProvider("gemini", null).model())
				.isEqualTo("gemini-3.1-pro");
		} finally {
			isolateSettingsFile();
			java.nio.file.Files.deleteIfExists(settings);
		}
	}

	// ── the plaintext key a machine with no keychain falls back to ──────

	@Test
	void theSavedKeyIsReadBackFromTheFile() throws Exception {
		Path settings = Files.createTempFile("xagent-key", ".json");
		Files.writeString(settings, "{\"defaultProvider\":\"gemini\",\"apiKey\":\"sk-in-a-file\"}");
		System.setProperty("xagent.settings.file", settings.toString());
		try {
			assertThat(ProviderConfig.savedApiKey()).isEqualTo("sk-in-a-file");
		} finally {
			System.setProperty("xagent.settings.file",
				Path.of(System.getProperty("java.io.tmpdir"), "xagent-test-no-such-settings.json").toString());
			Files.deleteIfExists(settings);
		}
	}

	@Test
	void clearingTheKeyLeavesTheRestOfTheFileAlone() throws Exception {
		Path settings = Files.createTempFile("xagent-key", ".json");
		Files.writeString(settings,
			"{\"defaultProvider\":\"gemini\",\"defaultModel\":\"gemini-3\",\"apiKey\":\"sk-in-a-file\"}");
		System.setProperty("xagent.settings.file", settings.toString());
		try {
			ProviderConfig.clearSavedApiKey();

			// The key goes; the provider and model choices are not collateral.
			assertThat(ProviderConfig.savedApiKey()).isNull();
			String left = Files.readString(settings);
			assertThat(left).contains("gemini-3").contains("defaultProvider");
			assertThat(left).doesNotContain("sk-in-a-file");
		} finally {
			System.setProperty("xagent.settings.file",
				Path.of(System.getProperty("java.io.tmpdir"), "xagent-test-no-such-settings.json").toString());
			Files.deleteIfExists(settings);
		}
	}

	@Test
	void readingAndClearingCopeWithNoFileAtAll() throws Exception {
		assertThat(ProviderConfig.savedApiKey()).isNull();

		ProviderConfig.clearSavedApiKey();   // must not throw
	}
}
