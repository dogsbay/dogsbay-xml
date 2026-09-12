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
package com.xagent;

import com.xagent.auth.AuthStorage;
import com.xagent.auth.CodexOAuth;
import com.xagent.core.Agent;
import com.xagent.event.AgentEvent;
import com.xagent.mcp.McpConfigLoader;
import com.xagent.mcp.McpManager;
import com.xagent.message.ToolResultMessage;
import com.xagent.permission.PermissionHandler;
import com.xagent.permission.PermissionPolicy;
import com.xagent.permission.PermissionRule;
import com.xagent.permission.PermissionSettings;
import com.xagent.permission.PermissionToolHooks;
import com.xagent.prompt.SystemPromptBuilder;
import com.xagent.provider.ProviderConfig;
import com.xagent.provider.ProviderFactory;
import com.xagent.session.SessionManager;
import com.xagent.skill.BundledSkills;
import com.xagent.skill.Skill;
import com.xagent.skill.SkillLoader;
import com.xagent.skill.SkillPromptFormatter;
import com.xagent.tool.ToolRegistry;
import com.xagent.tui.AnsiCodes;
import com.xagent.tui.Theme;
import com.xagent.tui.TuiApp;
import com.xagent.tui.TuiEventRenderer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(
	name = "xagent",
	mixinStandardHelpOptions = true,
	versionProvider = XAgentCli.ManifestVersionProvider.class,
	description = "Interactive coding agent powered by LangChain4j"
)
public class XAgentCli implements Callable<Integer> {

	@Option(names = {"--provider", "-p"}, description = "LLM provider: openai, anthropic, gemini, ollama")
	private String provider;

	@Option(names = {"--model", "-m"}, description = "Model name (e.g. gpt-5.4, claude-sonnet-4-6-20250217, gemini-3.1-flash-lite-preview)")
	private String model;

	@Option(names = {"--api-key", "-k"}, description = "API key for the provider")
	private String apiKey;

	@Option(names = {"--base-url"}, description = "Custom base URL for the provider")
	private String baseUrl;

	@Option(names = {"--system-prompt", "-s"}, description = "Custom system prompt (replaces default)")
	private String systemPrompt;

	@Option(names = {"--append-system-prompt"}, description = "Text to append to the system prompt")
	private String appendSystemPrompt;

	@Option(names = {"--no-tools"}, description = "Disable all tools (chat-only mode)")
	private boolean noTools;

	@Option(names = {"--skill"}, description = "Additional skill directory path (repeatable)")
	private List<String> skillPaths;

	@Option(names = {"--no-skills"}, description = "Disable skill discovery")
	private boolean noSkills;

	@Option(names = {"--no-bundled-skills"}, description = "Do not load the skills shipped inside the jar")
	private boolean noBundledSkills;

	@Option(names = {"--no-color"}, description = "Disable colored output")
	private boolean noColor;

	@Option(names = {"--tui"}, description = "Enable full-screen TUI mode")
	private boolean tuiMode;

	@Option(names = {"--continue", "-c"}, description = "Resume the most recent session")
	private boolean continueSession;

	@Option(names = {"--session"}, description = "Resume a specific session by ID")
	private String sessionIdArg;

	@Option(names = {"--resume", "-r"}, description = "List recent sessions and choose one to resume")
	private boolean resumeInteractive;

	@Parameters(index = "0", arity = "0..1", paramLabel = "PROMPT",
		description = "Run one prompt non-interactively and exit ('-' reads the prompt from stdin)")
	private String oneShotPrompt;

	@Option(names = {"--output", "-o"}, description = "One-shot output format: text, json, markdown")
	private String outputFormat;

	@Option(names = {"--no-session"}, description = "Do not persist this conversation")
	private boolean noSession;

	@Option(names = {"--yolo"}, description = "Auto-approve all tool calls (configured deny rules still apply)")
	private boolean yolo;

	@Option(names = {"--allow"}, description = "Permission allow rule, e.g. 'bash(mvn *)' or 'edit' (repeatable)")
	private List<String> allowRuleArgs;

	@Option(names = {"--deny"}, description = "Permission deny rule, e.g. 'bash(rm *)' (repeatable)")
	private List<String> denyRuleArgs;

	@Option(names = {"--mcp"}, description = "Enable MCP servers from .xagent/mcp.json")
	private boolean mcpEnabled;

	@Option(names = {"--mcp-config"}, description = "Path to MCP config file (implies --mcp)")
	private String mcpConfigPath;

	@Option(names = {"--no-mcp"}, description = "Disable MCP even if config files exist")
	private boolean noMcp;

	@Option(names = {"--mcp-serve"}, description = "Run as an MCP server exposing the XML/DITA tools on stdio")
	private boolean mcpServe;

	private PermissionPolicy activePolicy;

	static class ManifestVersionProvider implements CommandLine.IVersionProvider {
		@Override
		public String[] getVersion() {
			return new String[] {"xagent " + Version.get()};
		}
	}

	public static void main(String[] args) {
		int exitCode = new CommandLine(new XAgentCli()).execute(args);
		System.exit(exitCode);
	}

	/**
	 * A one-line hint if the configured provider has no usable credentials yet,
	 * so the user knows how to authenticate before sending a message. Returns
	 * null when credentials look present (or the provider needs none, e.g. ollama).
	 */
	private static String authHint(ProviderConfig config) {
		if ("openai-codex".equals(config.provider())) {
			boolean loggedIn = new AuthStorage().get(CodexOAuth.PROVIDER_ID).isPresent();
			return loggedIn ? null
				: "Not signed in to ChatGPT. Run /login (or /login device), "
					+ "or /provider to choose another provider, before sending a message.";
		}
		if (config.apiKey() == null
			&& ("openai".equals(config.provider()) || "anthropic".equals(config.provider())
				|| "gemini".equals(config.provider()))) {
			return "No API key for " + config.provider()
				+ ". Run /provider to choose a provider (Ollama needs no key, or sign in to ChatGPT), "
				+ "or set an API key / use --api-key.";
		}
		return null;
	}

	private static void parseRuleArgs(List<String> args, List<PermissionRule> target) {
		if (args == null) return;
		for (String arg : args) {
			PermissionRule rule = PermissionRule.parse(arg);
			if (rule != null) {
				target.add(rule);
			} else {
				System.err.println("Warning: ignoring malformed permission rule: " + arg);
			}
		}
	}

	@Override
	public Integer call() {
		// MCP server mode: no LLM provider involved, stdout is the protocol channel
		if (mcpServe) {
			Path serveCwd = Path.of(System.getProperty("user.dir"));
			var registry = ToolRegistry.createXmlToolset(serveCwd);
			System.err.println("xagent MCP server: serving "
				+ registry.all().stream().map(t -> t.name()).collect(Collectors.joining(", "))
				+ " on stdio (cwd: " + serveCwd + ")");
			try {
				new com.xagent.mcp.McpServer(registry, "xagent", Version.get())
					.run(System.in, System.out);
				return 0;
			} catch (IOException e) {
				System.err.println("MCP server error: " + e.getMessage());
				return 1;
			}
		}

		ProviderConfig config = ProviderConfig.resolve(provider, model, apiKey, baseUrl);
		Path cwd = Path.of(System.getProperty("user.dir"));

		var renderer = noColor ? null : new TuiEventRenderer();
		String toolsDisplay;
		ToolRegistry toolRegistry;
		if (noTools) {
			toolRegistry = new ToolRegistry();
			toolsDisplay = "disabled";
		} else {
			toolRegistry = ToolRegistry.createDefault(cwd);
			toolsDisplay = String.join(", ",
				toolRegistry.all().stream().map(t -> t.name()).toList());
		}

		McpManager mcpManager = null;
		try {
			// MCP setup
			if (!noMcp && (mcpEnabled || mcpConfigPath != null)) {
				try {
					var mcpConfigs = (mcpConfigPath != null)
						? McpConfigLoader.load(Path.of(mcpConfigPath).getParent())
						: McpConfigLoader.load(cwd);
					if (!mcpConfigs.isEmpty()) {
						mcpManager = new McpManager(mcpConfigs);
						int started = mcpManager.startAll();
						var mcpTools = mcpManager.discoverTools();
						toolRegistry.registerAll(mcpTools);
						System.err.println("MCP: " + started + "/" + mcpConfigs.size()
							+ " servers started, " + mcpTools.size() + " tools discovered");
					}
				} catch (Exception e) {
					System.err.println("MCP setup error: " + e.getMessage());
				}
			}

			// Resolve the provider lazily (on first prompt) from the agent's
			// CURRENT config, so the app can start without credentials and
			// /provider or /login can change the provider at runtime.
			final Agent[] agentRef = new Agent[1];
			java.util.function.Supplier<dev.langchain4j.model.chat.StreamingChatModel> modelSupplier =
				() -> ProviderFactory.create(agentRef[0].config());

			// Load skills
			List<Skill> skills = List.of();
			if (!noSkills) {
				var skillLoader = new SkillLoader();
				var extraPaths = new java.util.ArrayList<Path>();
				if (skillPaths != null) {
					skillPaths.forEach(p -> extraPaths.add(Path.of(p)));
				}
				// Bundled skills ship inside the jar and are extracted to a
				// per-version cache so they always match the running release.
				// Added last -> lowest precedence (user/project skills override).
				if (!noBundledSkills) {
					try {
						Path bundled = BundledSkills.extract(BundledSkills.defaultCacheRoot(), Version.get());
						if (bundled != null) {
							extraPaths.add(bundled);
						}
					} catch (IOException e) {
						System.err.println("Could not load bundled skills: " + e.getMessage());
					}
				}
				skills = skillLoader.loadAll(cwd, extraPaths);
				for (String warning : skillLoader.warnings()) {
					System.err.println("Skill warning: " + warning);
				}
			}

			var promptBuilder = new SystemPromptBuilder(toolRegistry, cwd);
			if (systemPrompt != null) {
				promptBuilder.customPrompt(systemPrompt);
			}
			if (appendSystemPrompt != null) {
				promptBuilder.appendPrompt(appendSystemPrompt);
			}
			promptBuilder.skills(skills);
			String prompt = promptBuilder.build();
			var agent = new Agent(modelSupplier, config, prompt, toolRegistry);
			agentRef[0] = agent;

			// Permission setup: rules from settings files + CLI flags
			java.util.function.Consumer<PermissionRule> persister = null;
			if (!noTools) {
				var settingsRules = PermissionSettings.load(PermissionSettings.defaultFiles(cwd));
				var allowRules = new java.util.ArrayList<>(settingsRules.allow());
				var denyRules = new java.util.ArrayList<>(settingsRules.deny());
				parseRuleArgs(allowRuleArgs, allowRules);
				parseRuleArgs(denyRuleArgs, denyRules);
				activePolicy = new PermissionPolicy(allowRules, denyRules);
				Path projectSettingsFile = cwd.resolve(".xagent").resolve("settings.json");
				persister = rule -> {
					try {
						PermissionSettings.appendAllowRule(projectSettingsFile, rule);
					} catch (IOException e) {
						System.err.println("Warning: could not save permission rule: " + e.getMessage());
					}
				};
			}

			// Headless one-shot mode
			if (oneShotPrompt != null) {
				return runOneShot(agent, persister);
			}

			// Session setup
			var sessionManager = new SessionManager();
			String resumeId = null;
			if (sessionIdArg != null) {
				resumeId = sessionIdArg;
			} else if (resumeInteractive) {
				resumeId = pickSession(sessionManager);
				if (resumeId == null) {
					return 0; // user cancelled or no sessions
				}
			} else if (continueSession) {
				resumeId = sessionManager.lastSessionId();
				if (resumeId == null) {
					System.out.println("No previous session found. Starting new session.");
				}
			}

			agent.enableSession(sessionManager, resumeId);
			String sessionInfo;
			if (resumeId != null) {
				sessionInfo = agent.sessionId() + " (resumed, " + agent.messages().size() + " messages)";
			} else {
				sessionInfo = agent.sessionId();
			}

			if (renderer != null) {
				System.out.print(renderer.formatBanner("v" + Version.get(), config.provider(), config.model(),
					cwd.toString(), toolsDisplay, sessionInfo));
			} else {
				System.out.println("xagent v" + Version.get() + " | " + config.provider() + "/" + config.model());
				System.out.println("cwd: " + cwd);
				System.out.println("Tools: " + toolsDisplay);
				System.out.println("Session: " + sessionInfo);
			}
			String authHint = authHint(config);
			if (authHint != null) {
				System.out.println(authHint);
			}

			if (tuiMode) {
				Terminal terminal = TerminalBuilder.builder()
					.system(true)
					.build();
				var tuiApp = new TuiApp(agent, config, sessionManager, skills, terminal, sessionInfo, mcpManager);
				if (activePolicy != null) {
					PermissionHandler handler = yolo
						? PermissionHandler.ALLOW_ALL
						: tuiApp::requestPermission;
					agent.setToolHooks(new PermissionToolHooks(
						toolRegistry, activePolicy, handler, null, persister));
				}
				try {
					tuiApp.run();
				} finally {
					if (mcpManager != null) mcpManager.close();
				}
				return 0;
			}

			if (activePolicy != null) {
				PermissionHandler handler = yolo
					? PermissionHandler.ALLOW_ALL
					: new ConsolePermissionHandler(renderer);
				agent.setToolHooks(new PermissionToolHooks(
					toolRegistry, activePolicy, handler, null, persister));
			}

			if (!skills.isEmpty()) {
				System.out.println("Skills: " + skills.stream().map(Skill::name).collect(Collectors.joining(", ")));
			}
			System.out.println("Type /quit to exit, /clear to clear history, /help for commands.");
			System.out.println();

			runInteractiveLoop(agent, sessionManager, skills, renderer);
			return 0;
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			return 1;
		} finally {
			if (mcpManager != null) {
				mcpManager.close();
			}
		}
	}

	/**
	 * Run one prompt non-interactively. Stdout carries only the formatted
	 * result; progress noise is suppressed and errors go to stderr. No
	 * interactive permission prompts: mutating tools run only via
	 * configured rules, --allow flags, or --yolo.
	 */
	private Integer runOneShot(Agent agent, java.util.function.Consumer<PermissionRule> persister) throws IOException {
		String promptText = oneShotPrompt;
		if ("-".equals(promptText)) {
			promptText = new String(System.in.readAllBytes()).strip();
		}
		if (promptText.isEmpty()) {
			System.err.println("Error: empty prompt");
			return 1;
		}

		if (activePolicy != null) {
			PermissionHandler handler = yolo ? PermissionHandler.ALLOW_ALL : PermissionHandler.DENY_ALL;
			agent.setToolHooks(new PermissionToolHooks(
				agent.toolRegistry(), activePolicy, handler, null, persister));
		}

		if (!noSession) {
			var sessionManager = new SessionManager();
			String resumeId = null;
			if (sessionIdArg != null) {
				resumeId = sessionIdArg;
			} else if (continueSession) {
				resumeId = sessionManager.lastSessionId();
			}
			agent.enableSession(sessionManager, resumeId);
		}

		var errorOccurred = new java.util.concurrent.atomic.AtomicBoolean(false);
		agent.subscribe(event -> {
			switch (event) {
				case AgentEvent.ErrorOccurred err -> {
					errorOccurred.set(true);
					System.err.println("Error: " + err.error().getMessage());
				}
				case AgentEvent.RetryAttempt retry ->
					System.err.println("Retrying in " + (retry.delayMs() / 1000) + "s (" + retry.reason() + ")...");
				default -> {}
			}
		});

		try {
			agent.prompt(promptText);
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			return 1;
		}

		String response = lastAssistantText(agent);
		if (response == null || errorOccurred.get()) {
			return 1;
		}

		var format = OneShotFormatter.parseFormat(outputFormat);
		System.out.println(OneShotFormatter.format(format, response, agent.usageStats(), agent.sessionId()));
		return 0;
	}

	private static String lastAssistantText(Agent agent) {
		var messages = agent.messages();
		for (int i = messages.size() - 1; i >= 0; i--) {
			if (messages.get(i) instanceof com.xagent.message.AssistantMessage a) {
				return a.content();
			}
		}
		return null;
	}

	private void runInteractiveLoop(Agent agent, SessionManager sessionManager, List<Skill> skills, TuiEventRenderer renderer) throws IOException {
		// Build skill lookup map
		Map<String, Skill> skillMap = skills.stream()
			.collect(Collectors.toMap(Skill::name, s -> s, (a, b) -> a));

		Terminal terminal = TerminalBuilder.builder()
			.system(true)
			.build();

		Path historyFile = Path.of(System.getProperty("user.home"), ".xagent", "history");
		Files.createDirectories(historyFile.getParent());

		LineReader reader = LineReaderBuilder.builder()
			.terminal(terminal)
			.variable(LineReader.HISTORY_FILE, historyFile)
			.build();

		if (renderer != null) {
			agent.subscribe(renderer);
		} else {
			agent.subscribe(event -> {
				switch (event) {
					case AgentEvent.TurnStart ignored -> {}
					case AgentEvent.TurnEnd ignored -> {}
					case AgentEvent.AgentEnd ignored -> {}
					case AgentEvent.MessageStart ignored -> {}
					case AgentEvent.MessageUpdate update -> {
						System.out.print(update.partialText());
						System.out.flush();
					}
					case AgentEvent.MessageEnd msg -> {
						if (msg.message().hasToolCalls()) {
							System.out.println();
						} else {
							System.out.println();
							System.out.println();
						}
					}
					case AgentEvent.ToolExecutionStart start ->
						System.out.println("[tool:" + start.toolName() + "] " + truncate(start.arguments(), 200));
					case AgentEvent.ToolExecutionEnd end -> {
						String preview = truncate(end.result().content(), 300);
						if (end.result().isError()) {
							System.out.println("[error] " + preview);
						} else {
							System.out.println("[result] " + preview);
						}
						System.out.println();
					}
					case AgentEvent.UsageUpdate ignored -> {}
					case AgentEvent.RetryAttempt retry ->
						System.err.println("Retrying in " + (retry.delayMs() / 1000) + "s (" + retry.reason() + ")...");
					case AgentEvent.ErrorOccurred err ->
						System.err.println("\nError: " + err.error().getMessage());
				}
			});
		}

		while (true) {
			String line;
			try {
				String prompt = renderer != null
					? AnsiCodes.style("> ", Theme.DEFAULT.userPrompt())
					: "> ";
				line = reader.readLine(prompt).trim();
			} catch (UserInterruptException e) {
				continue;
			} catch (EndOfFileException e) {
				break;
			}

			if (line.isEmpty()) {
				continue;
			}

			if (line.startsWith("/")) {
				// Check for /skill:name invocation
				if (line.startsWith("/skill:")) {
					handleSkillInvocation(line, skillMap, agent);
					continue;
				}
				if (handleCommand(line, agent, sessionManager, skills, reader)) {
					break;
				}
				continue;
			}

			try {
				agent.prompt(line);
			} catch (Exception e) {
				System.err.println("Error: " + e.getMessage());
				if (isAuthError(e)) {
					System.err.println("Tip: run /provider to pick a provider (Ollama needs no key), "
						+ "or /login to sign in with ChatGPT.");
				}
			}
		}
	}

	private static boolean isAuthError(Throwable e) {
		String m = e.getMessage();
		if (m == null) return false;
		return m.contains("API key required") || m.contains("Not signed in") || m.contains("session expired");
	}

	/** Status label for a provider, shown in the /provider picker. */
	private static String providerStatus(String provider) {
		return switch (provider) {
			case "ollama" -> "local, no key needed";
			case "openai" -> System.getenv("OPENAI_API_KEY") != null ? "API key set" : "no key";
			case "anthropic" -> System.getenv("ANTHROPIC_API_KEY") != null ? "API key set" : "no key";
			case "gemini" -> System.getenv("GEMINI_API_KEY") != null ? "API key set" : "no key";
			case "openai-codex" ->
				new AuthStorage().get(CodexOAuth.PROVIDER_ID).isPresent() ? "signed in" : "not signed in — use /login";
			default -> "";
		};
	}

	/**
	 * Read one line via the active JLine reader (the single owner of stdin
	 * during the REPL). Returns null on Ctrl+C / EOF so callers can cancel.
	 * A non-null {@code mask} hides input (e.g. {@code '\0'} for API keys).
	 */
	private static String readLineOrNull(LineReader reader, String prompt, Character mask) {
		try {
			return mask == null ? reader.readLine(prompt) : reader.readLine(prompt, mask);
		} catch (UserInterruptException | EndOfFileException e) {
			return null;
		}
	}

	/**
	 * Interactive provider picker: lists providers with their status, switches
	 * the running agent to the chosen one (prompting for an API key or running
	 * the ChatGPT login as needed), and optionally persists it as the default.
	 */
	private void pickProvider(Agent agent, LineReader reader) {
		String[] providers = {"ollama", "openai", "anthropic", "gemini", "openai-codex"};
		System.out.println("Choose a provider:");
		for (int i = 0; i < providers.length; i++) {
			System.out.printf("  %d) %-13s [%s]%n", i + 1, providers[i], providerStatus(providers[i]));
		}

		String input = readLineOrNull(reader, "Enter number (blank to cancel): ", null);
		if (input == null || input.isBlank()) {
			return;
		}
		int choice;
		try {
			choice = Integer.parseInt(input.trim());
		} catch (NumberFormatException e) {
			System.out.println("Invalid choice.");
			return;
		}
		if (choice < 1 || choice > providers.length) {
			System.out.println("Invalid choice.");
			return;
		}
		String provider = providers[choice - 1];

		String model = readLineOrNull(reader, "Model (blank for the provider default): ", null);
		if (model != null) {
			model = model.isBlank() ? null : model.trim();
		}

		if ("openai-codex".equals(provider)) {
			if (new AuthStorage().get(CodexOAuth.PROVIDER_ID).isEmpty()) {
				handleLogin(false);
			}
			if (new AuthStorage().get(CodexOAuth.PROVIDER_ID).isEmpty()) {
				System.out.println("Not signed in; provider unchanged.");
				return;
			}
			switchProvider(agent, providerConfigFor(provider, model, null), null, reader,
				model != null && !model.isBlank());
			return;
		}

		String apiKey = null;
		if (!"ollama".equals(provider) && System.getenv(envVarFor(provider)) == null) {
			apiKey = readLineOrNull(reader, "Enter " + provider + " API key (blank to cancel): ", '\0');
			if (apiKey == null || apiKey.isBlank()) {
				System.out.println("No key entered; provider unchanged.");
				return;
			}
			apiKey = apiKey.trim();
		}
		switchProvider(agent, providerConfigFor(provider, model, apiKey), apiKey, reader,
			model != null && !model.isBlank());
	}

	/**
	 * The config for a provider being switched to. Without an explicit model
	 * this is {@code forProvider}, which takes that provider's own model rather
	 * than whatever the last provider was using — the pair a backend rejects.
	 */
	private static ProviderConfig providerConfigFor(String provider, String model, String apiKey) {
		return model == null || model.isBlank()
			? ProviderConfig.forProvider(provider, apiKey)
			: ProviderConfig.resolve(provider, model, apiKey, null);
	}

	private void switchProvider(Agent agent, ProviderConfig newConfig, String enteredKey, LineReader reader) {
		switchProvider(agent, newConfig, enteredKey, reader, false);
	}

	/**
	 * @param modelWasChosen true when the user named the model, so saving should
	 *                       record it; false when it came from a default
	 */
	private void switchProvider(Agent agent, ProviderConfig newConfig, String enteredKey,
			LineReader reader, boolean modelWasChosen) {
		agent.setProviderConfig(newConfig);
		System.out.println("Switched to " + newConfig.provider() + "/" + newConfig.model() + ".");
		String answer = readLineOrNull(reader, "Save as the default in ~/.xagent/settings.json? [y/N]: ", null);
		if (answer != null && (answer.trim().equalsIgnoreCase("y") || answer.trim().equalsIgnoreCase("yes"))) {
			try {
				// Only a model the user named is saved as a choice; otherwise this
				// records the provider (and key) and leaves the model to resolve.
				if (modelWasChosen) {
					ProviderConfig.saveDefaults(newConfig.provider(), newConfig.model(), enteredKey);
				} else {
					ProviderConfig.saveProviderDefault(newConfig.provider(), enteredKey);
				}
				System.out.println("Saved." + (enteredKey != null ? " (API key written to settings.json)" : ""));
			} catch (IOException e) {
				System.err.println("Could not save settings: " + e.getMessage());
			}
		}
	}

	private static String envVarFor(String provider) {
		return switch (provider) {
			case "openai" -> "OPENAI_API_KEY";
			case "anthropic" -> "ANTHROPIC_API_KEY";
			case "gemini" -> "GEMINI_API_KEY";
			default -> "";
		};
	}

	private void handleSkillInvocation(String line, Map<String, Skill> skillMap, Agent agent) {
		// Parse /skill:name [args]
		String rest = line.substring("/skill:".length());
		String skillName;
		String userArgs = "";
		int spaceIdx = rest.indexOf(' ');
		if (spaceIdx >= 0) {
			skillName = rest.substring(0, spaceIdx);
			userArgs = rest.substring(spaceIdx + 1).strip();
		} else {
			skillName = rest;
		}

		Skill skill = skillMap.get(skillName);
		if (skill == null) {
			System.out.println("Unknown skill: " + skillName + ". Use /skills to list available skills.");
			return;
		}

		try {
			String content = Files.readString(skill.filePath());
			String body = SkillLoader.stripFrontmatter(content);
			String message = SkillPromptFormatter.formatInvocation(skill, body, userArgs);
			agent.prompt(message);
		} catch (IOException e) {
			System.err.println("Error loading skill: " + e.getMessage());
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
		}
	}

	private boolean handleCommand(String command, Agent agent, SessionManager sessionManager, List<Skill> skills,
		LineReader reader) {
		return switch (command) {
			case "/quit", "/exit", "/q" -> {
				System.out.println("Goodbye.");
				yield true;
			}
			case "/clear" -> {
				agent.clearHistory();
				System.out.println("Conversation cleared.");
				yield false;
			}
			case "/history" -> {
				var messages = agent.messages();
				if (messages.isEmpty()) {
					System.out.println("No messages yet.");
				} else {
					for (var msg : messages) {
						System.out.println("[" + msg.role() + "] " + truncate(messageContent(msg), 120));
					}
				}
				yield false;
			}
			case "/tools" -> {
				var tools = agent.toolRegistry().all();
				if (tools.isEmpty()) {
					System.out.println("No tools registered.");
				} else {
					for (var tool : tools) {
						System.out.println("  " + tool.name() + " - " + truncate(tool.description(), 80));
					}
				}
				yield false;
			}
			case "/skills" -> {
				if (skills.isEmpty()) {
					System.out.println("No skills loaded.");
				} else {
					for (var skill : skills) {
						String hidden = skill.disableModelInvocation() ? " (hidden)" : "";
						System.out.println("  " + skill.name() + hidden + " - " + truncate(skill.description(), 70));
						System.out.println("    /skill:" + skill.name() + " [args]");
					}
				}
				yield false;
			}
			case "/sessions" -> {
				try {
					var sessions = sessionManager.listSessions();
					if (sessions.isEmpty()) {
						System.out.println("No saved sessions.");
					} else {
						for (var s : sessions) {
							System.out.println("  " + s.id() + "  " + s.timestamp().toString().substring(0, 19)
								+ "  " + s.provider() + "/" + s.model()
								+ (s.preview().isEmpty() ? "" : "  " + s.preview()));
						}
						System.out.println("Resume with: --session <id> or --continue (most recent)");
					}
				} catch (IOException e) {
					System.err.println("Error listing sessions: " + e.getMessage());
				}
				yield false;
			}
			case "/provider" -> {
				pickProvider(agent, reader);
				yield false;
			}
			case "/login" -> {
				handleLogin(false);
				agent.invalidateModel();   // pick up new credentials on next prompt
				yield false;
			}
			case "/login device" -> {
				handleLogin(true);
				agent.invalidateModel();
				yield false;
			}
			case "/logout" -> {
				try {
					boolean removed = new AuthStorage().remove(CodexOAuth.PROVIDER_ID);
					System.out.println(removed
						? "Logged out of ChatGPT (openai-codex)."
						: "No ChatGPT login stored.");
				} catch (IOException e) {
					System.err.println("Logout failed: " + e.getMessage());
				}
				agent.invalidateModel();
				yield false;
			}
			case "/permissions" -> {
				if (activePolicy == null) {
					System.out.println("Permissions inactive (tools disabled).");
				} else if (yolo) {
					System.out.println("Auto-approve (--yolo) is on. Deny rules still apply:");
					activePolicy.denyRules().forEach(r -> System.out.println("  deny  " + r.toText()));
				} else {
					System.out.println("Allow rules:");
					activePolicy.allowRules().forEach(r -> System.out.println("  " + r.toText()));
					System.out.println("Deny rules:");
					activePolicy.denyRules().forEach(r -> System.out.println("  " + r.toText()));
					System.out.println("Session grants:");
					activePolicy.sessionGrants().forEach(r -> System.out.println("  " + r.toText()));
					System.out.println("Rules persist in .xagent/settings.json (project) and ~/.xagent/settings.json (user).");
				}
				yield false;
			}
			case "/compact" -> {
				String summary = agent.compact();
				if (summary != null) {
					int msgCount = agent.messages().size();
					System.out.println("Compacted conversation. " + msgCount + " messages remaining.");
				} else {
					System.out.println("No compaction needed.");
				}
				yield false;
			}
			case "/help" -> {
				System.out.println("Commands:");
				System.out.println("  /quit, /exit, /q   - Exit");
				System.out.println("  /clear             - Clear conversation history");
				System.out.println("  /compact           - Compact conversation history");
				System.out.println("  /history           - Show conversation history");
				System.out.println("  /tools             - List available tools");
				System.out.println("  /skills            - List available skills");
				System.out.println("  /skill:name [args] - Invoke a skill");
				System.out.println("  /sessions          - List saved sessions");
				System.out.println("  /permissions       - Show permission rules and session grants");
				System.out.println("  /provider          - Choose the LLM provider (Ollama needs no key)");
				System.out.println("  /login [device]    - Sign in with ChatGPT (openai-codex)");
				System.out.println("  /logout            - Remove the stored ChatGPT login");
				System.out.println("  /mcp              - Show MCP server status");
				System.out.println("  /mcp-tools        - List MCP tools");
				System.out.println("  /help              - Show this help");
				System.out.println();
				System.out.println("Resume sessions with: --resume, --continue, or --session <id>");
				yield false;
			}
			default -> {
				System.out.println("Unknown command: " + command + ". Type /help for available commands.");
				yield false;
			}
		};
	}

	/**
	 * Sign in with ChatGPT (openai-codex). Browser flow by default;
	 * device-code flow for headless/SSH environments.
	 */
	private void handleLogin(boolean deviceFlow) {
		var oauth = new CodexOAuth();
		var storage = new AuthStorage();
		try {
			com.xagent.auth.OAuthCredentials credentials;
			if (deviceFlow) {
				var device = oauth.requestDeviceCode();
				System.out.println("Visit " + device.verifyUrl() + " and enter code: " + device.userCode());
				System.out.println("Waiting for approval...");
				credentials = oauth.pollDeviceToken(device);
			} else {
				credentials = oauth.loginViaBrowser(url -> {
					System.out.println("Open this URL to sign in with your ChatGPT account:");
					System.out.println("  " + url);
					tryOpenBrowser(url);
					System.out.println("Waiting for the browser sign-in to complete (Ctrl+C to abort)...");
				});
			}
			storage.set(CodexOAuth.PROVIDER_ID, credentials);
			System.out.println("Signed in. Credentials stored in " + storage.file()
				+ (credentials.accountId() != null ? " (account " + credentials.accountId() + ")" : ""));
			System.out.println("Note: ChatGPT subscription access is for personal use; "
				+ "use the OpenAI Platform API for production workloads.");
		} catch (IOException e) {
			System.err.println("Login failed: " + e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			System.err.println("Login interrupted.");
		}
	}

	private static void tryOpenBrowser(String url) {
		String opener = System.getProperty("os.name").toLowerCase().contains("mac") ? "open" : "xdg-open";
		try {
			new ProcessBuilder(opener, url)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
		} catch (IOException ignored) {
			// no opener available -- the URL is already printed
		}
	}

	private String pickSession(SessionManager sessionManager) throws IOException {
		var sessions = sessionManager.listSessions();
		if (sessions.isEmpty()) {
			System.out.println("No saved sessions.");
			return null;
		}

		int limit = Math.min(sessions.size(), 20);
		System.out.println("Recent sessions:");
		System.out.println();
		for (int i = 0; i < limit; i++) {
			var s = sessions.get(i);
			String ts = s.timestamp().toString().substring(0, 19).replace('T', ' ');
			String preview = s.preview().isEmpty() ? "" : "  " + s.preview();
			System.out.printf("  %2d) %s  %s/%s%s%n",
				i + 1, ts, s.provider(), s.model(), preview);
		}
		if (sessions.size() > limit) {
			System.out.println("  ... and " + (sessions.size() - limit) + " more");
		}
		System.out.println();
		System.out.print("Enter number (or q to cancel): ");
		System.out.flush();

		var scanner = new Scanner(System.in);
		String input = scanner.nextLine().trim();
		if (input.equalsIgnoreCase("q") || input.isEmpty()) {
			return null;
		}
		try {
			int choice = Integer.parseInt(input);
			if (choice < 1 || choice > limit) {
				System.out.println("Invalid choice.");
				return null;
			}
			return sessions.get(choice - 1).id();
		} catch (NumberFormatException e) {
			System.out.println("Invalid input.");
			return null;
		}
	}

	private static String messageContent(com.xagent.message.AgentMessage msg) {
		return switch (msg) {
			case com.xagent.message.UserMessage u -> u.content();
			case com.xagent.message.AssistantMessage a -> a.content();
			case ToolResultMessage t -> "[" + t.toolName() + "] " + t.content();
		};
	}

	private static String truncate(String text, int maxLen) {
		if (text == null) return "";
		String singleLine = text.replace("\n", " ");
		if (singleLine.length() <= maxLen) return singleLine;
		return singleLine.substring(0, maxLen - 3) + "...";
	}
}
