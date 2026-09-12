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
package com.xagent.tui;

import com.xagent.core.Agent;
import com.xagent.event.AgentEvent;
import com.xagent.mcp.McpManager;
import com.xagent.message.AgentMessage;
import com.xagent.message.AssistantMessage;
import com.xagent.message.ToolResultMessage;
import com.xagent.message.UserMessage;
import com.xagent.permission.PermissionDecision;
import com.xagent.permission.PermissionRequest;
import com.xagent.provider.ProviderConfig;
import com.xagent.session.SessionManager;
import com.xagent.skill.Skill;
import com.xagent.skill.SkillLoader;
import com.xagent.skill.SkillPromptFormatter;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Full-screen TUI application orchestrator.
 * Manages the render loop, key input, and agent event subscription.
 */
public class TuiApp {

	private static final long FRAME_TIMEOUT_MS = 16; // ~60fps
	private static final int SPINNER_TICK_INTERVAL = 5; // tick every 5 frames (~80ms)

	private final Agent agent;
	private final ProviderConfig config;
	private final SessionManager sessionManager;
	private final List<Skill> skills;
	private final Terminal terminal;
	private final String sessionInfo;
	private final McpManager mcpManager;

	private HeaderComponent header;
	private FooterComponent footer;
	private StatusComponent status;
	private ChatArea chatArea;
	private InputArea inputArea;
	private PickerComponent picker;

	private enum PickerMode { NONE, PROVIDER, OLLAMA_MODEL, LOADING }
	private volatile PickerMode pickerMode = PickerMode.NONE;
	private static final String[] PICKER_PROVIDERS = {"ollama", "openai", "anthropic", "gemini", "openai-codex"};
	private volatile List<String> ollamaModels = List.of();
	private Layout layout;
	private TerminalRenderer renderer;
	private KeyReader keyReader;

	private volatile boolean running = true;
	private volatile boolean agentBusy = false;
	private volatile CompletableFuture<PermissionDecision> pendingPermission;
	private int frameCount = 0;
	private List<SessionManager.SessionInfo> lastSessionList;

	public TuiApp(Agent agent, ProviderConfig config, SessionManager sessionManager,
				  List<Skill> skills, Terminal terminal, String sessionInfo) {
		this(agent, config, sessionManager, skills, terminal, sessionInfo, null);
	}

	public TuiApp(Agent agent, ProviderConfig config, SessionManager sessionManager,
				  List<Skill> skills, Terminal terminal, String sessionInfo, McpManager mcpManager) {
		this.agent = agent;
		this.config = config;
		this.sessionManager = sessionManager;
		this.skills = skills;
		this.terminal = terminal;
		this.sessionInfo = sessionInfo;
		this.mcpManager = mcpManager;
	}

	public void run() {
		Attributes savedAttributes = terminal.getAttributes();
		try {
			setup();
			mainLoop();
		} finally {
			cleanup(savedAttributes);
		}
	}

	private void setup() {
		// Enter raw mode
		terminal.enterRawMode();

		// Enter alternate screen buffer
		terminal.writer().print("\033[?1049h");
		// Hide cursor
		terminal.writer().print("\033[?25l");
		// Enable SGR mouse mode (wheel events)
		terminal.writer().print("\033[?1000h\033[?1006h");
		terminal.writer().flush();

		// Register resize handler
		terminal.handle(Terminal.Signal.WINCH, signal -> {
			if (renderer != null) renderer.invalidate();
		});

		// Detect git branch
		String gitBranch = detectGitBranch();

		// Create components
		String cwd = System.getProperty("user.dir");
		header = new HeaderComponent("v" + com.xagent.Version.get(), config.provider(), config.model(), sessionInfo);
		footer = new FooterComponent(cwd, gitBranch);
		status = new StatusComponent();
		chatArea = new ChatArea();
		inputArea = new InputArea(this::onInputSubmit);
		picker = new PickerComponent();

		// Populate chat with restored session messages
		loadRestoredMessages();

		layout = new Layout(List.of(header, chatArea, picker, status, footer, inputArea));
		renderer = new TerminalRenderer();
		keyReader = new KeyReader(terminal);

		// Subscribe to agent events
		agent.subscribe(this::onAgentEvent);
	}

	private void loadRestoredMessages() {
		var messages = agent.messages();
		if (messages.isEmpty()) return;

		for (var msg : messages) {
			switch (msg) {
				case UserMessage u -> {
					chatArea.addUserBlock(u.content());
					inputArea.addHistory(u.content());
				}
				case AssistantMessage a -> {
					if (a.content() != null && !a.content().isEmpty()) {
						chatArea.startAssistantBlock();
						chatArea.appendToAssistant(a.content());
						chatArea.completeAssistantBlock();
					}
					// Create pending tool blocks for any tool calls
					if (a.hasToolCalls()) {
						for (var tc : a.toolCalls()) {
							chatArea.addToolBlock(tc.name(), tc.arguments());
						}
					}
				}
				case ToolResultMessage t ->
					chatArea.updateToolBlock(t.toolName(), t.content(), t.isError());
			}
		}
	}

	private void mainLoop() {
		while (running) {
			try {
				// Read key with frame timeout
				KeyEvent key = keyReader.read(FRAME_TIMEOUT_MS);

				if (key != null) {
					try {
						dispatchKey(key);
					} catch (Throwable t) {
						// A command bug must not kill the TUI: surface it and recover.
						chatArea.addErrorBlock("Command error: " + t);
						pickerMode = PickerMode.NONE;
						if (picker != null) picker.hide();
					}
				}

				// Tick spinner
				frameCount++;
				if (frameCount % SPINNER_TICK_INTERVAL == 0) {
					status.tick();
				}

				// Render if dirty
				if (layout.isDirty()) {
					renderFrame();
					layout.markAllClean();
				}
			} catch (IOException e) {
				// Terminal read error — exit
				running = false;
			}
		}
	}

	/**
	 * Blocking permission prompt for the agent's tool thread. The decision
	 * arrives via {@link #dispatchKey} on the UI thread.
	 */
	public PermissionDecision requestPermission(PermissionRequest request) {
		var future = new CompletableFuture<PermissionDecision>();
		chatArea.addInfoBlock("Permission required: " + request.summary());
		status.setAwaitingApproval(request.toolName());
		pendingPermission = future;
		try {
			return future.get();
		} catch (Exception e) {
			return PermissionDecision.DENY;
		} finally {
			pendingPermission = null;
			status.setRunning(request.toolName());
		}
	}

	private void resolvePermission(PermissionDecision decision) {
		var future = pendingPermission;
		if (future != null) {
			String label = switch (decision) {
				case ALLOW -> "allowed once";
				case ALLOW_SESSION -> "allowed for this session";
				case ALLOW_ALWAYS -> "always allowed (saved to .xagent/settings.json)";
				case DENY -> "denied";
			};
			chatArea.addInfoBlock("Permission " + label + ".");
			future.complete(decision);
		}
	}

	private void dispatchKey(KeyEvent key) {
		if (pickerMode != PickerMode.NONE) {
			switch (key) {
				case KeyEvent.Up ignored -> picker.moveUp();
				case KeyEvent.Down ignored -> picker.moveDown();
				case KeyEvent.Enter ignored -> onPickerEnter();
				case KeyEvent.Escape ignored -> cancelPicker();
				case KeyEvent.CtrlC ignored -> cancelPicker();
				default -> {}
			}
			return;
		}
		if (pendingPermission != null) {
			switch (key) {
				case KeyEvent.Character c -> {
					switch (java.lang.Character.toLowerCase(c.ch())) {
						case 'y' -> resolvePermission(PermissionDecision.ALLOW);
						case 's' -> resolvePermission(PermissionDecision.ALLOW_SESSION);
						case 'a' -> resolvePermission(PermissionDecision.ALLOW_ALWAYS);
						case 'n' -> resolvePermission(PermissionDecision.DENY);
						default -> {}
					}
				}
				case KeyEvent.Escape ignored -> resolvePermission(PermissionDecision.DENY);
				case KeyEvent.CtrlC ignored -> resolvePermission(PermissionDecision.DENY);
				case KeyEvent.CtrlD ignored -> {
					resolvePermission(PermissionDecision.DENY);
					running = false;
				}
				default -> {}
			}
			return;
		}
		switch (key) {
			case KeyEvent.CtrlD ignored -> running = false;
			case KeyEvent.CtrlC ignored -> {
				if (agentBusy) {
					agent.abort();
					status.setReady();
				}
			}
			case KeyEvent.PageUp ignored -> chatArea.scrollUp(10);
			case KeyEvent.PageDown ignored -> chatArea.scrollDown(10);
			case KeyEvent.ScrollUp ignored -> chatArea.scrollUp(3);
			case KeyEvent.ScrollDown ignored -> chatArea.scrollDown(3);
			case KeyEvent.Tab ignored -> chatArea.toggleRawMode();
			default -> {
				if (!agentBusy) {
					inputArea.handleKey(key);
				}
			}
		}
	}

	private void onInputSubmit(String text) {
		if (text.startsWith("/")) {
			handleCommand(text);
			return;
		}

		chatArea.addUserBlock(text);
		agentBusy = true;
		status.setThinking();

		// Run agent on virtual thread
		Thread.ofVirtual().name("agent-prompt").start(() -> {
			try {
				agent.prompt(text);
			} catch (Exception e) {
				chatArea.addErrorBlock(e.getMessage());
			} finally {
				agentBusy = false;
				status.setReady();
			}
		});
	}

	/**
	 * Run the ChatGPT (openai-codex) OAuth flow on a background thread, posting
	 * the sign-in URL and status as chat blocks. Neither flow reads stdin, so it
	 * works while the TUI owns the terminal. On success the cached model is
	 * invalidated so the next message uses the new credentials.
	 */
	/**
	 * Provider picker for the TUI. Argument-based (the raw-mode UI can't run the
	 * REPL's interactive line prompts): {@code /provider} lists options with
	 * status; {@code /provider <name> [model]} switches. Key-based providers need
	 * the key in the environment (or use REPL mode to enter one); openai-codex
	 * needs a prior /login.
	 */
	private void handleProvider(String command) {
		chatArea.addUserBlock(command);
		String[] parts = command.trim().split("\\s+");
		if (parts.length == 1) {
			// no args -> interactive arrow-key picker
			openProviderPicker();
			return;
		}
		// typed form: /provider <name> [model]
		String provider = parts[1];
		String model = parts.length > 2 ? parts[2] : null;
		boolean known = false;
		for (String p : PICKER_PROVIDERS) {
			if (p.equals(provider)) known = true;
		}
		if (!known) {
			chatArea.addErrorBlock("Unknown provider: " + provider);
			return;
		}
		applyProvider(provider, model);
	}

	private void openProviderPicker() {
		var options = new ArrayList<String>();
		for (String p : PICKER_PROVIDERS) {
			options.add(String.format("%-13s [%s]", p, providerStatus(p)));
		}
		pickerMode = PickerMode.PROVIDER;
		picker.show("Choose a provider:", options);
	}

	/** Handle Enter in the picker, advancing the provider -> model flow. */
	private void onPickerEnter() {
		int idx = picker.selected();
		switch (pickerMode) {
			case PROVIDER -> {
				String provider = PICKER_PROVIDERS[idx];
				if ("ollama".equals(provider)) {
					loadOllamaModelsThenPick();   // second step: pick the model
				} else {
					pickerMode = PickerMode.NONE;
					picker.hide();
					applyProvider(provider, null);
				}
			}
			case OLLAMA_MODEL -> {
				String model = idx >= 0 && idx < ollamaModels.size() ? ollamaModels.get(idx) : null;
				pickerMode = PickerMode.NONE;
				picker.hide();
				applyProvider("ollama", model);
			}
			default -> { /* LOADING: ignore Enter until models arrive */ }
		}
	}

	private void cancelPicker() {
		pickerMode = PickerMode.NONE;
		picker.hide();
		chatArea.addInfoBlock("Provider selection cancelled.");
	}

	/** Fetch the installed Ollama models on a background thread, then show them. */
	private void loadOllamaModelsThenPick() {
		pickerMode = PickerMode.LOADING;
		picker.show("Loading Ollama models…", List.of());
		Thread.ofVirtual().name("ollama-models").start(() -> {
			var models = fetchOllamaModels();
			if (models.isEmpty()) {
				pickerMode = PickerMode.NONE;
				picker.hide();
				chatArea.addInfoBlock("No Ollama models detected (is Ollama running?). "
					+ "Using the default; pull a model or use /provider ollama <model>.");
				applyProvider("ollama", null);
			} else {
				ollamaModels = models;
				pickerMode = PickerMode.OLLAMA_MODEL;
				picker.show("Choose an Ollama model:", models);
			}
		});
	}

	private List<String> fetchOllamaModels() {
		try {
			var http = java.net.http.HttpClient.newBuilder()
				.connectTimeout(java.time.Duration.ofMillis(800)).build();
			var req = java.net.http.HttpRequest.newBuilder()
				.uri(java.net.URI.create("http://localhost:11434/api/tags"))
				.timeout(java.time.Duration.ofSeconds(2)).GET().build();
			var resp = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() != 200) return List.of();
			var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(resp.body());
			var names = new ArrayList<String>();
			for (var m : root.path("models")) {
				String n = m.path("name").asText("");
				if (!n.isEmpty()) names.add(n);
			}
			return names;
		} catch (Exception e) {
			return List.of();
		}
	}

	/** Switch to a provider after validating credentials (used by picker and typed form). */
	private void applyProvider(String provider, String model) {
		if ("openai-codex".equals(provider)
			&& new com.xagent.auth.AuthStorage().get(com.xagent.auth.CodexOAuth.PROVIDER_ID).isEmpty()) {
			chatArea.addInfoBlock("Not signed in. Run /login first, then choose openai-codex.");
			return;
		}
		if (("openai".equals(provider) || "anthropic".equals(provider) || "gemini".equals(provider))
			&& System.getenv(envVarFor(provider)) == null) {
			chatArea.addInfoBlock("No API key for " + provider + " in the environment. Set "
				+ envVarFor(provider) + " and restart, or use REPL mode (without --tui) where /provider can prompt for a key.");
			return;
		}
		// forProvider without an explicit model: a switch must not carry the
		// previous provider's model into the new one.
		var cfg = model == null || model.isBlank()
			? ProviderConfig.forProvider(provider, null)
			: ProviderConfig.resolve(provider, model, null, null);
		agent.setProviderConfig(cfg);
		chatArea.addInfoBlock("Switched to " + cfg.provider() + "/" + cfg.model() + ".");
	}

	private static String providerStatus(String provider) {
		return switch (provider) {
			case "ollama" -> "local, no key needed";
			case "openai" -> System.getenv("OPENAI_API_KEY") != null ? "API key set" : "no key";
			case "anthropic" -> System.getenv("ANTHROPIC_API_KEY") != null ? "API key set" : "no key";
			case "gemini" -> System.getenv("GEMINI_API_KEY") != null ? "API key set" : "no key";
			case "openai-codex" -> new com.xagent.auth.AuthStorage()
				.get(com.xagent.auth.CodexOAuth.PROVIDER_ID).isPresent() ? "signed in" : "not signed in — use /login";
			default -> "";
		};
	}

	private static String envVarFor(String provider) {
		return switch (provider) {
			case "openai" -> "OPENAI_API_KEY";
			case "anthropic" -> "ANTHROPIC_API_KEY";
			case "gemini" -> "GEMINI_API_KEY";
			default -> "";
		};
	}

	private void startLogin(boolean device) {
		Thread.ofVirtual().name("xagent-login").start(() -> {
			try {
				var oauth = new com.xagent.auth.CodexOAuth();
				var storage = new com.xagent.auth.AuthStorage();
				com.xagent.auth.OAuthCredentials creds;
				if (device) {
					var d = oauth.requestDeviceCode();
					chatArea.addInfoBlock("Visit " + d.verifyUrl() + " and enter code: " + d.userCode());
					creds = oauth.pollDeviceToken(d);
				} else {
					creds = oauth.loginViaBrowser(url ->
						chatArea.addInfoBlock("Open this URL to sign in with ChatGPT:\n  " + url));
				}
				storage.set(com.xagent.auth.CodexOAuth.PROVIDER_ID, creds);
				agent.invalidateModel();
				chatArea.addInfoBlock("Signed in to ChatGPT. You can send a message now.");
			} catch (Exception e) {
				chatArea.addErrorBlock("Login failed: " + e.getMessage());
			}
		});
	}

	private void handleCommand(String command) {
		Map<String, Skill> skillMap = skills.stream()
			.collect(Collectors.toMap(Skill::name, s -> s, (a, b) -> a));

		if (command.startsWith("/skill:")) {
			handleSkillInvocation(command, skillMap);
			return;
		}

		if (command.equals("/resume") || command.startsWith("/resume ")) {
			handleResume(command);
			return;
		}

		if (command.equals("/export") || command.startsWith("/export ")) {
			handleExport(command);
			return;
		}

		if (command.equals("/provider") || command.startsWith("/provider ")) {
			handleProvider(command);
			return;
		}

		switch (command) {
			case "/quit", "/exit", "/q" -> running = false;
			case "/mcp" -> {
				chatArea.addUserBlock("/mcp");
				if (mcpManager == null) {
					chatArea.addInfoBlock("MCP is not enabled. Use --mcp flag to enable.");
				} else {
					var sb = new StringBuilder("MCP Servers:\n");
					var statuses = mcpManager.status();
					for (var entry : statuses.entrySet()) {
						sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
					}
					chatArea.addInfoBlock(sb.toString().stripTrailing());
				}
			}
			case "/mcp-tools" -> {
				chatArea.addUserBlock("/mcp-tools");
				if (mcpManager == null) {
					chatArea.addInfoBlock("MCP is not enabled. Use --mcp flag to enable.");
				} else {
					var mcpTools = mcpManager.discoverTools();
					if (mcpTools.isEmpty()) {
						chatArea.addInfoBlock("No MCP tools available.");
					} else {
						var sb = new StringBuilder();
						for (var tool : mcpTools) {
							sb.append("  ").append(tool.name()).append(" - ").append(truncate(tool.description(), 80)).append("\n");
						}
						chatArea.addInfoBlock(sb.toString().stripTrailing());
					}
				}
			}
			case "/login" -> {
				chatArea.addUserBlock("/login");
				startLogin(false);
			}
			case "/login device" -> {
				chatArea.addUserBlock("/login device");
				startLogin(true);
			}
			case "/logout" -> {
				chatArea.addUserBlock("/logout");
				try {
					boolean removed = new com.xagent.auth.AuthStorage()
						.remove(com.xagent.auth.CodexOAuth.PROVIDER_ID);
					chatArea.addInfoBlock(removed
						? "Logged out of ChatGPT (openai-codex)."
						: "No ChatGPT login stored.");
				} catch (java.io.IOException e) {
					chatArea.addErrorBlock("Logout failed: " + e.getMessage());
				}
				agent.invalidateModel();
			}
			case "/clear" -> {
				agent.clearHistory();
				chatArea.clear();
				chatArea.addUserBlock("Conversation cleared.");
			}
			case "/compact" -> {
				chatArea.addUserBlock("/compact");
				String summary = agent.compact();
				if (summary != null) {
					chatArea.addInfoBlock("Compacted conversation. " + agent.messages().size() + " messages remaining.");
				} else {
					chatArea.addInfoBlock("No compaction needed.");
				}
			}
			case "/help" -> {
				chatArea.addUserBlock("/help");
				String helpText = """
					Commands:
					  /quit, /exit, /q   - Exit
					  /clear             - Clear conversation history
					  /compact           - Compact conversation history
					  /history           - Show conversation history
					  /tools             - List available tools
					  /skills            - List available skills
					  /skill:name [args] - Invoke a skill
					  /sessions          - List saved sessions
					  /resume            - List sessions to resume
					  /resume N          - Resume session number N
					  /export [path]     - Export conversation to markdown file
					  /provider [name]   - List/switch LLM provider (e.g. /provider ollama llama3.2)
					  /login [device]    - Sign in with ChatGPT (openai-codex)
					  /logout            - Remove the stored ChatGPT login
					  /mcp              - Show MCP server status
					  /mcp-tools        - List MCP tools
					  /help              - Show this help

					Keybindings:
					  Tab                - Toggle raw/rendered markdown
					  PageUp/PageDown    - Scroll chat
					  Ctrl+C             - Abort agent
					  Ctrl+D             - Quit""";
				chatArea.addInfoBlock(helpText);
			}
			case "/tools" -> {
				chatArea.addUserBlock("/tools");
				var tools = agent.toolRegistry().all();
				if (tools.isEmpty()) {
					chatArea.addInfoBlock("No tools registered.");
				} else {
					var sb = new StringBuilder();
					for (var tool : tools) {
						sb.append("  ").append(tool.name()).append(" - ").append(truncate(tool.description(), 80)).append("\n");
					}
					chatArea.addInfoBlock(sb.toString().stripTrailing());
				}
			}
			case "/skills" -> {
				chatArea.addUserBlock("/skills");
				if (skills.isEmpty()) {
					chatArea.addInfoBlock("No skills loaded.");
				} else {
					var sb = new StringBuilder();
					for (var skill : skills) {
						String hidden = skill.disableModelInvocation() ? " (hidden)" : "";
						sb.append("  ").append(skill.name()).append(hidden).append(" - ")
							.append(truncate(skill.description(), 70)).append("\n");
						sb.append("    /skill:").append(skill.name()).append(" [args]\n");
					}
					chatArea.addInfoBlock(sb.toString().stripTrailing());
				}
			}
			case "/sessions" -> {
				chatArea.addUserBlock("/sessions");
				try {
					var sessions = sessionManager.listSessions();
					if (sessions.isEmpty()) {
						chatArea.addInfoBlock("No saved sessions.");
					} else {
						var sb = new StringBuilder();
						for (var s : sessions) {
							sb.append("  ").append(s.id()).append("  ")
								.append(s.timestamp().toString().substring(0, 19)).append("  ")
								.append(s.provider()).append("/").append(s.model());
							if (!s.preview().isEmpty()) sb.append("  ").append(s.preview());
							sb.append("\n");
						}
						chatArea.addInfoBlock(sb.toString().stripTrailing());
					}
				} catch (IOException e) {
					chatArea.addErrorBlock("Error listing sessions: " + e.getMessage());
				}
			}
			case "/history" -> {
				chatArea.addUserBlock("/history");
				var messages = agent.messages();
				if (messages.isEmpty()) {
					chatArea.addInfoBlock("No messages yet.");
				} else {
					var sb = new StringBuilder();
					for (var msg : messages) {
						sb.append("[").append(msg.role()).append("] ").append(truncate(messageContent(msg), 120)).append("\n");
					}
					chatArea.addInfoBlock(sb.toString().stripTrailing());
				}
			}
			default -> chatArea.addInfoBlock("Unknown command: " + command + ". Type /help for available commands.");
		}
	}

	private void handleResume(String command) {
		String arg = command.length() > "/resume".length()
			? command.substring("/resume".length()).trim()
			: "";

		if (arg.isEmpty()) {
			// List sessions
			try {
				var sessions = sessionManager.listSessions();
				if (sessions.isEmpty()) {
					chatArea.addInfoBlock("No saved sessions.");
					return;
				}
				lastSessionList = sessions;
				int limit = Math.min(sessions.size(), 20);
				var sb = new StringBuilder("Recent sessions:\n\n");
				for (int i = 0; i < limit; i++) {
					var s = sessions.get(i);
					String ts = s.timestamp().toString().substring(0, 19).replace('T', ' ');
					String preview = s.preview().isEmpty() ? "" : "  " + s.preview();
					sb.append(String.format("  %2d) %s  %s/%s%s%n",
						i + 1, ts, s.provider(), s.model(), preview));
				}
				if (sessions.size() > limit) {
					sb.append("  ... and ").append(sessions.size() - limit).append(" more\n");
				}
				sb.append("\nType /resume N to resume a session.");
				chatArea.addUserBlock("/resume");
				chatArea.addInfoBlock(sb.toString().stripTrailing());
			} catch (IOException e) {
				chatArea.addErrorBlock("Error listing sessions: " + e.getMessage());
			}
		} else {
			// Resume session by number
			if (lastSessionList == null || lastSessionList.isEmpty()) {
				chatArea.addInfoBlock("Use /resume first to list sessions.");
				return;
			}
			try {
				int choice = Integer.parseInt(arg);
				int limit = Math.min(lastSessionList.size(), 20);
				if (choice < 1 || choice > limit) {
					chatArea.addErrorBlock("Invalid choice. Pick 1-" + limit + ".");
					return;
				}
				var selected = lastSessionList.get(choice - 1);
				chatArea.addUserBlock("/resume " + choice);

				// Re-initialize agent with the selected session
				agent.clearHistory();
				agent.enableSession(sessionManager, selected.id());

				// Reload chat display
				chatArea.clear();
				loadRestoredMessages();

				int msgCount = agent.messages().size();
				chatArea.addInfoBlock("Resumed session " + selected.id()
					+ " (" + msgCount + " messages)");
			} catch (NumberFormatException e) {
				chatArea.addErrorBlock("Invalid number. Use /resume N where N is a session number.");
			} catch (IOException e) {
				chatArea.addErrorBlock("Error resuming session: " + e.getMessage());
			}
		}
	}

	private void handleExport(String command) {
		String arg = command.length() > "/export".length()
			? command.substring("/export".length()).trim()
			: "";

		chatArea.addUserBlock(command);

		String markdown = ConversationExporter.toMarkdown(chatArea.blocks());
		if (arg.isEmpty()) {
			arg = "conversation-" + System.currentTimeMillis() + ".md";
		}

		try {
			Path path = Path.of(arg);
			Files.writeString(path, markdown);
			chatArea.addInfoBlock("Exported conversation to " + path.toAbsolutePath());
		} catch (IOException e) {
			chatArea.addErrorBlock("Error exporting: " + e.getMessage());
		}
	}

	private void handleSkillInvocation(String line, Map<String, Skill> skillMap) {
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
			chatArea.addErrorBlock("Unknown skill: " + skillName + ". Use /skills to list available skills.");
			return;
		}

		try {
			String content = Files.readString(skill.filePath());
			String body = SkillLoader.stripFrontmatter(content);
			String message = SkillPromptFormatter.formatInvocation(skill, body, userArgs);

			chatArea.addUserBlock("/skill:" + skillName + (userArgs.isEmpty() ? "" : " " + userArgs));
			agentBusy = true;
			status.setThinking();

			Thread.ofVirtual().name("agent-skill").start(() -> {
				try {
					agent.prompt(message);
				} catch (Exception e) {
					chatArea.addErrorBlock(e.getMessage());
				} finally {
					agentBusy = false;
					status.setReady();
				}
			});
		} catch (IOException e) {
			chatArea.addErrorBlock("Error loading skill: " + e.getMessage());
		}
	}

	private void onAgentEvent(AgentEvent event) {
		switch (event) {
			case AgentEvent.TurnStart ignored -> {}
			case AgentEvent.TurnEnd ignored -> {}
			case AgentEvent.MessageStart ignored -> {
				chatArea.startAssistantBlock();
				status.setThinking();
			}
			case AgentEvent.MessageUpdate update -> chatArea.appendToAssistant(update.partialText());
			case AgentEvent.MessageEnd msg -> chatArea.completeAssistantBlock();
			case AgentEvent.ToolExecutionStart start -> {
				chatArea.addToolBlock(start.toolName(), start.arguments());
				status.setRunning(start.toolName());
			}
			case AgentEvent.ToolExecutionEnd end -> {
				chatArea.updateToolBlock(end.toolName(), end.result().content(), end.result().isError());
				status.setThinking();
			}
			case AgentEvent.UsageUpdate usage ->
				status.updateUsage(agent.messages().size(), usage.totalTokens(),
					usage.estimatedCostUsd() > 0 ? String.format("$%.4f", usage.estimatedCostUsd()) : "");
			case AgentEvent.RetryAttempt retry ->
				chatArea.addInfoBlock("Retrying in " + (retry.delayMs() / 1000) + "s (" + retry.reason() + ")...");
			case AgentEvent.ErrorOccurred err -> {
				chatArea.addErrorBlock(err.error().getMessage());
				status.setError(err.error().getMessage());
			}
			case AgentEvent.AgentEnd ignored -> status.setReady();
		}
	}

	private void renderFrame() {
		int width = terminal.getWidth();
		int height = terminal.getHeight();
		if (width <= 0 || height <= 0) return;

		// Custom layout: header, chatArea (visible window), picker, status, footer, input
		int fixedHeight = header.desiredHeight() + picker.desiredHeight() + status.desiredHeight()
			+ footer.desiredHeight() + inputArea.desiredHeight();
		int chatHeight = Math.max(1, height - fixedHeight);

		List<String> frame = new java.util.ArrayList<>(height);

		// Header
		for (String line : header.render(width)) {
			frame.add(Layout.padOrTruncate(line, width));
		}

		// Chat area with scroll
		for (String line : chatArea.renderVisible(width, chatHeight)) {
			frame.add(Layout.padOrTruncate(line, width));
		}

		// Picker overlay (0 lines when inactive)
		for (String line : picker.render(width)) {
			frame.add(Layout.padOrTruncate(line, width));
		}

		// Status
		for (String line : status.render(width)) {
			frame.add(Layout.padOrTruncate(line, width));
		}

		// Footer
		for (String line : footer.render(width)) {
			frame.add(Layout.padOrTruncate(line, width));
		}

		// Input
		for (String line : inputArea.render(width)) {
			frame.add(Layout.padOrTruncate(line, width));
		}

		// Ensure exactly height lines
		while (frame.size() < height) {
			frame.add(" ".repeat(width));
		}
		if (frame.size() > height) {
			frame = new java.util.ArrayList<>(frame.subList(0, height));
		}

		renderer.render(terminal, frame);
	}

	private void cleanup(Attributes savedAttributes) {
		agent.unsubscribe(this::onAgentEvent);

		// Disable SGR mouse mode
		terminal.writer().print("\033[?1006l\033[?1000l");
		// Show cursor
		terminal.writer().print("\033[?25h");
		// Exit alternate screen buffer
		terminal.writer().print("\033[?1049l");
		terminal.writer().flush();

		// Restore terminal attributes
		terminal.setAttributes(savedAttributes);
	}

	private String detectGitBranch() {
		try {
			var process = new ProcessBuilder("git", "branch", "--show-current")
				.redirectErrorStream(true)
				.start();
			String branch = new String(process.getInputStream().readAllBytes()).trim();
			int exitCode = process.waitFor();
			return exitCode == 0 && !branch.isEmpty() ? branch : null;
		} catch (Exception e) {
			return null;
		}
	}

	private static String messageContent(com.xagent.message.AgentMessage msg) {
		return switch (msg) {
			case com.xagent.message.UserMessage u -> u.content();
			case com.xagent.message.AssistantMessage a -> a.content();
			case com.xagent.message.ToolResultMessage t -> "[" + t.toolName() + "] " + t.content();
		};
	}

	private static String truncate(String text, int maxLen) {
		if (text == null) return "";
		String singleLine = text.replace("\n", " ");
		if (singleLine.length() <= maxLen) return singleLine;
		return singleLine.substring(0, maxLen - 3) + "...";
	}
}
