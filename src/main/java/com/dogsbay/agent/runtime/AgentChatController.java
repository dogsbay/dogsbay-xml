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

package com.dogsbay.agent.runtime;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import javax.swing.SwingUtilities;

import com.dogsbay.agent.AgentHost;
import com.dogsbay.agent.FixRequest;
import com.dogsbay.agent.ProviderChoice;
import com.dogsbay.agent.SlashCommand;
import com.dogsbay.agent.secret.AgentCredentials;
import com.dogsbay.agent.secret.SecretStore;
import com.dogsbay.agent.ui.AgentChatPanel;
import com.xagent.auth.CodexOAuth;
import com.xagent.core.Agent;
import com.xagent.extension.ExtensionContext;
import com.xagent.extension.ExtensionRunner;
import com.xagent.mcp.McpConfigLoader;
import com.xagent.mcp.McpManager;
import com.xagent.mcp.McpServerConfig;
import com.xagent.message.AgentMessage;
import com.xagent.message.AssistantMessage;
import com.xagent.message.ToolResultMessage;
import com.xagent.message.UserMessage;
import com.xagent.provider.ProviderConfig;
import com.xagent.session.SessionManager;
import com.xagent.skill.Skill;
import com.xagent.event.AgentEvent;
import com.xagent.tool.AgentTool;

/**
 * Wires the standalone-clean pieces into a working chat: a lazy {@link Agent}
 * from the host, an {@link AgentRunner}, an {@link AgentChatPanel}, plus provider
 * switching, ChatGPT sign-in, slash commands, skill loading, per-turn editor
 * context, and persistent sessions. Both {@code AgentApp} (standalone) and
 * {@code AgentPlugin} (editor) use this so the behaviour is identical.
 */
public final class AgentChatController implements AgentChatPanel.Actions {

    /** The editor context marker; defined by SessionManager, which also reads it back. */
    private static final String CONTEXT_MARKER = SessionManager.CONTEXT_MARKER;

    /** Context files the agent reads automatically (see xagent ContextFileLoader). */
    private static final String[] CONTEXT_FILES = {"AGENTS.md", ".xagent.md", "CLAUDE.md"};

    /** /init prompt: survey the project (read-only) and write an AGENTS.md. */
    private static final String INIT_PROMPT = """
            Create an AGENTS.md context file at the project root for this project, \
            so future sessions have standing context.

            First survey the project read-only: identify the maps and the root map, \
            the key definitions, the DOCTYPEs / topic types in use, shared content \
            pulled in via conref, any conditional (.ditaval) filters, and any \
            house-style conventions you can infer (e.g. keys vs hardcoded product \
            names, <uicontrol> for UI labels, title casing). Use the read-only \
            tools (list_project_files, list_keys, project_health, get_document_outline, \
            search_project) — do not modify any topics.

            Then write a concise AGENTS.md covering: project layout, the defined keys, \
            the topic types, reuse/conref conventions, conditional-attribute values, \
            and the house-style rules. Keep it tight and useful as agent guidance.""";

    private final AgentHost host;
    private final AgentChatPanel panel;
    private final SessionManager sessionManager = new SessionManager();
    private final SecretStore secrets = SecretStore.keychain();
    private final AgentCredentials credentials = ChatAgentFactory.credentials();

    private Agent agent;
    private AgentRunner runner;
    private TurnCheckpoint checkpoint;
    private List<Skill> skills;
    private Path agentRoot;
    private volatile McpManager mcpManager;
    /** True once the user has submitted — guards against passive session resets. */
    private boolean conversationStarted;

    public AgentChatController(AgentHost host) {
        this.host = host;
        this.panel = new AgentChatPanel(ChatAgentFactory.PROVIDERS, this);
        buildAgent();   // rooted at the current project directory
        // The plugin activates at startup — often before a project folder is
        // open — so the agent may be rooted at the launch dir and the context
        // file (AGENTS.md) status computed against the wrong directory. Re-sync
        // whenever the panel becomes visible (e.g. the user opens the folder and
        // then reveals the panel) so the root, context file, and skills track the
        // actual project. Silent: the refreshed hint is the visible signal.
        panel.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && panel.isShowing()) {
                syncToProject(false);
            }
        });
    }

    /**
     * Public entry for the host to nudge a re-sync (e.g. the editor opened a
     * project after the plugin activated). Silent and cheap when nothing changed.
     */
    public void refreshForProject() {
        syncToProject(false);
    }

    /**
     * Re-root the agent if the project changed since it was built (the plugin
     * activates before a folder is open), otherwise just refresh the status hint
     * — so a now-present {@code AGENTS.md} is detected without resetting the
     * session. Only a genuine root change rebuilds (which starts a new session).
     *
     * @param announce whether to post a "project changed" note (mid-conversation
     *                 yes; on a silent panel-reveal no)
     */
    private void syncToProject(boolean announce) {
        if (host.workingDirectory().equals(agentRoot)) {
            refreshStatus();
            return;
        }
        // Root changed. Rebuilding re-roots context/skills but resets the session
        // (clearHistory). Only rebuild when announced (an explicit submit) or when
        // there's no conversation to lose — never silently destroy an in-progress
        // chat on a passive signal (panel shown / a document opened in another
        // folder). A genuine change mid-conversation reconciles on the next submit.
        if (announce || !conversationStarted) {
            if (announce) {
                panel.note("Project changed — reloading context from " + host.workingDirectory());
            }
            buildAgent();
        } else {
            refreshStatus();
        }
    }

    /**
     * Build (or rebuild) the agent for the current working directory. The editor
     * plugin activates at startup — before the user opens a project — so the cwd
     * can change once a project is opened; {@link #submit} detects that and calls
     * this so tools, the {@code .xagent.md} context file, and {@code .xagent/skills}
     * resolve against the real project rather than the editor's launch directory.
     * The chosen provider is preserved across a rebuild.
     */
    private void buildAgent() {
        ProviderConfig keep = (agent != null) ? agent.config() : null;
        this.agentRoot = host.workingDirectory();
        this.skills = ChatAgentFactory.loadSkills(host);
        this.checkpoint = new TurnCheckpoint(
                agentRoot.resolve(".xagent").resolve("checkpoints"));
        this.agent = ChatAgentFactory.create(host, skills, checkpoint);
        if (keep != null) {
            agent.setProviderConfig(keep);
        }
        this.runner = new AgentRunner(agent, this::onAgentEvent);
        startNewSession();   // persist this conversation by default
        panel.setProviderSelection(agent.config().provider());   // reflect saved provider
        refreshStatus();
        loadExternalTools(); // external MCP servers + extensions (async)
    }

    /**
     * Persist the active provider/model (and an entered key) to
     * {@code ~/.xagent/settings.json} so the next launch restores them
     * ({@code ProviderConfig.resolve} reads these on startup). A null key leaves
     * any stored key untouched. Shared with the xagent CLI.
     */
    /** Remember the provider (and key), and the model only when the user chose it. */
    private void persistDefaults(String apiKey, boolean modelWasChosen) {
        try {
            ProviderConfig c = agent.config();
            if (modelWasChosen) {
                ProviderConfig.saveDefaults(c.provider(), c.model(), apiKey);
            } else {
                ProviderConfig.saveProviderDefault(c.provider(), apiKey);
            }
        } catch (IOException e) {
            panel.note("Could not save provider settings: " + e.getMessage());
        }
    }

    /**
     * Load tools from external MCP servers (.xagent/mcp.json near the working
     * dir) and ServiceLoader/JAR extensions, registering them with the agent —
     * matching the xagent CLI's reach. Runs off the EDT; input is disabled
     * while loading so registry mutation can't race the agent reading it (the
     * registry is not thread-safe and the agent only reads it during a turn).
     */
    private void loadExternalTools() {
        panel.setLoading(true);
        Thread.ofVirtual().name("agent-ext-tools").start(() -> {
            StringBuilder report = new StringBuilder();
            try {
                List<McpServerConfig> configs = McpConfigLoader.load(host.workingDirectory());
                if (!configs.isEmpty()) {
                    McpManager mgr = new McpManager(configs);
                    int started = mgr.startAll();
                    List<AgentTool> tools = mgr.discoverTools();
                    agent.toolRegistry().registerAll(tools);
                    this.mcpManager = mgr;
                    report.append("MCP: ").append(started).append('/').append(configs.size())
                            .append(" servers, ").append(tools.size()).append(" tools. ");
                }
            } catch (Exception e) {
                report.append("MCP error: ").append(e.getMessage()).append(". ");
            }
            try {
                int before = agent.toolRegistry().size();
                new ExtensionRunner().loadExtensions(
                        new ExtensionContext(host.workingDirectory(), agent.toolRegistry(), agent.messages()),
                        agent.toolRegistry());
                int added = agent.toolRegistry().size() - before;
                if (added > 0) {
                    report.append("Extensions: ").append(added).append(" tools.");
                }
            } catch (Exception e) {
                report.append("Extensions error: ").append(e.getMessage());
            }
            String msg = report.toString().strip();
            SwingUtilities.invokeLater(() -> {
                panel.setLoading(false);
                if (!msg.isEmpty()) {
                    panel.note(msg);
                }
            });
        });
    }

    @Override
    public void submit(String text) {
        // The project may have been opened after the plugin activated; if the
        // working directory changed, rebuild so the agent is rooted correctly.
        syncToProject(true);
        conversationStarted = true;   // protects this chat from passive re-syncs
        if (checkpoint != null) {
            checkpoint.beginTurn();   // start capturing this turn's file changes for /revert
        }
        // Augment with fresh editor context (active file + selection) each turn,
        // invisibly — the panel already echoed the user's raw text.
        String ctx = host.currentContext().toPromptFragment();
        // @path mentions carry their file's text along, invisibly to the transcript.
        String withFiles = com.dogsbay.agent.ui.Mentions.inlined(text, host.workingDirectory());
        runner.submit(ctx.isBlank() ? withFiles : ctx + CONTEXT_MARKER + withFiles);
    }

    @Override
    public String mention(java.io.File file) {
        return com.dogsbay.agent.ui.Mentions.forFile(file, host.workingDirectory());
    }

    @Override
    public void chooseProvider(ProviderChoice choice) {
        String provider = choice.provider();
        // Entered key wins; otherwise reuse a key from the OS keychain so
        // switching to a previously-saved provider needs no re-entry.
        String key = choice.hasKey() ? choice.apiKey()
                : (ChatAgentFactory.isKeyBased(provider) ? secrets.get(provider).orElse(null) : null);
        // Switching provider resets to that provider's default model — don't carry the
        // previous provider's model over (e.g. a Gemini model into an OpenAI/Codex session).
        agent.setProviderConfig(ProviderConfig.forProvider(provider, key));
        refreshStatus();
        panel.setProviderSelection(provider);
        if (choice.remember() && choice.hasKey()) {
            rememberKey(provider, choice.apiKey());
        }
        persistDefaults(null, false);   // provider only — the key lives in the keychain
    }

    /** Save the key to the OS keychain; fall back to opt-in plaintext if none. */
    private void rememberKey(String provider, String key) {
        try {
            secrets.set(provider, key);
            panel.note("Saved API key to the OS keychain.");
        } catch (Exception keychainUnavailable) {
            try {
                // The key, not the model: the model in force here is a default
                // nobody picked, and writing it pins it as a decision.
                ProviderConfig.saveProviderDefault(provider, key);
                panel.note("No OS keychain available — saved key to ~/.xagent/settings.json "
                        + "(plain text).");
            } catch (Exception e) {
                panel.note("Could not save key: " + e.getMessage());
            }
        }
    }

    @Override
    public void command(String raw) {
        SlashCommand cmd = SlashCommand.parse(raw);
        switch (cmd.name()) {
            case "help", "" -> panel.note(helpText());
            case "clear" -> {
                panel.clearTranscript();
                agent.clearHistory();
            }
            case "provider" -> {
                if (cmd.arg().isBlank()) {
                    panel.note("Providers: " + String.join(", ", ChatAgentFactory.PROVIDERS)
                            + "  (current: " + agent.config().provider() + ")");
                } else {
                    chooseProvider(new ProviderChoice(cmd.arg(), ""));
                }
            }
            case "model" -> {
                if (cmd.arg().isBlank()) {
                    String provider = agent.config().provider();
                    panel.note("Current model: " + agent.config().model()
                            + "  (provider: " + provider + ")"
                            + "\nDefault for this provider: " + ProviderConfig.defaultModelHint(provider)
                            + "\nSet another with /model <name>. It is remembered for "
                            + provider);
                } else {
                    agent.setProviderConfig(
                            ProviderConfig.resolve(agent.config().provider(), cmd.arg(), null, null));
                    refreshStatus();
                    persistDefaults(null, true);   // remember the model choice
                    panel.note("Model: " + agent.config().model());
                }
            }
            case "init" -> initProject();
            case "login" -> {
                if ("device".equalsIgnoreCase(cmd.arg())) {
                    loginDevice();
                } else {
                    login();
                }
            }
            case "logout" -> logout();
            case "tools" -> panel.note(toolsList());
            case "export" -> exportConversation();
            case "skills" -> panel.note(skillsList());
            case "compact" -> compact();
            case "new" -> {
                startNewSession();
                panel.clearTranscript();
                refreshStatus();
                panel.note("Started a new session.");
            }
            case "revert" -> revertTurn();
            case "sessions" -> sessions();
            case "rename" -> {
                renameSession(cmd.arg());
                panel.note("Session name: " + panel.sessionTitle());
            }
            case "resume" -> {
                if (cmd.arg().isBlank()) {
                    sessions();
                } else {
                    resumeSession(cmd.arg());
                }
            }
            default -> panel.note("Unknown command: /" + cmd.name() + "   (try /help)");
        }
    }

    /**
     * Hand one validation problem to the agent as a focused fix (quick-fix from a
     * marker — plans/agent-quickfix-on-markers.md). Echoes what's being fixed, then
     * runs the agent; the proposed change flows through the normal diff-preview and
     * post-edit validation.
     */
    public void fixWithAgent(FixRequest request) {
        panel.note("Fixing: " + request.message());
        submit(request.toPrompt());
    }

    /**
     * Render the event, then refresh open buffers when the turn finishes.
     *
     * <p>The agent edits files on disk. Without this, a document open in the editor
     * kept showing its pre-edit content while the file underneath had changed —
     * the editor and disk silently disagreed, and the next save wrote the stale
     * buffer back over the agent's work.
     */
    private void onAgentEvent(AgentEvent event) {
        panel.handle(event);
        if (event instanceof AgentEvent.TurnEnd) {
            reloadChangedFiles();
            host.turnEnded();
            // The first turn names the session; show that on the tab, so an
            // unnamed conversation stops reading "AI Agent" once it has a name.
            panel.setSessionTitle(titleOf(agent.sessionId()));
            refreshStatus();   // …and the banner can name the session now it exists
        }
    }

    /**
     * Refresh open buffers from disk for files this turn changed.
     *
     * <p>Uses the same checkpoint that powers {@code /revert}. Note its list is a
     * superset of what actually changed: it holds every file a mutating tool
     * captured, including ones a denied edit left untouched. That is fine here —
     * the host delegates to the editor's external-change check, which compares
     * against disk and acts only on documents that really differ.
     */
    private void reloadChangedFiles() {
        if (checkpoint == null) {
            return;
        }
        List<Path> changed = checkpoint.changedFiles();
        if (changed.isEmpty()) {
            return;
        }
        // filesChangedOnDisk, not reloadFiles: the user did not ask for their
        // buffers to be replaced, so a dirty buffer must be prompted or deferred,
        // never overwritten. reloadFiles is the authoritative variant and belongs
        // to /revert alone.
        host.filesChangedOnDisk(changed);
    }

    /** Undo the file changes the most recent turn made, restoring open buffers. */
    private void revertTurn() {
        if (checkpoint == null) {
            panel.note("Nothing to revert.");
            return;
        }
        // revert() only restores files whose content actually changed this turn, so an
        // empty result means the turn made no on-disk changes (in-buffer edits undo
        // with the editor's normal undo; denied edits never landed).
        List<Path> reverted = checkpoint.revert();
        if (reverted.isEmpty()) {
            panel.note("Nothing to revert — the last turn changed no files on disk. "
                    + "Use Edit ▸ Undo for in-editor changes.");
            return;
        }
        try {
            host.reloadFiles(reverted);
        } catch (Exception ignore) {
            // disk is already reverted; open buffers just may need a manual reload
        }
        panel.note("Reverted the last turn — restored " + reverted.size()
                + (reverted.size() == 1 ? " file." : " files."));
    }

    // ── Sessions ──────────────────────────────────────────────────────────────

    private void startNewSession() {
        panel.setSessionTitle(null);   // the new conversation has not named itself yet
        conversationStarted = false;   // fresh session — nothing to protect yet
        try {
            agent.clearHistory();
            agent.enableSession(sessionManager, null);
        } catch (IOException e) {
            panel.note("Sessions unavailable: " + e.getMessage());
        }
    }

    @Override
    public void sessions() {
        List<SessionManager.SessionInfo> all;
        try {
            all = sessionManager.listSessions().stream()
                    .sorted(Comparator.comparing(SessionManager.SessionInfo::timestamp).reversed())
                    .toList();
        } catch (IOException e) {
            all = List.of();
        }

        // Every session, not the fifteen most recent: the rest were on disk and
        // unreachable, which is how 237 transcripts accumulate unnoticed.
        var dialog = new com.dogsbay.agent.ui.SessionPickerDialog(
                host.dialogParent(), all, agent.sessionId(), this::deleteSession);
        dialog.setVisible(true);

        switch (dialog.outcome()) {
            case NEW -> {
                // A session nobody has spoken in is already new. Clearing it and
                // saying "started a new session" looks like nothing happened,
                // because nothing did.
                if (sessionIsEmpty()) {
                    panel.note("This session is new already — nothing has been said in it.");
                } else {
                    startNewSession();
                    panel.clearTranscript();
                    refreshStatus();
                    panel.note("Started a new session.");
                }
            }
            case RESUME -> resumeSession(dialog.chosenSessionId());
            case CANCELLED -> { /* nothing to do */ }
        }
    }

    @Override
    public void renameSession(String title) {
        try {
            sessionManager.renameSession(agent.sessionId(), title);
            // Read it back rather than trusting the input: a blank name means
            // "use the automatic one", and the store decides what that is.
            panel.setSessionTitle(titleOf(agent.sessionId()));
        } catch (IOException e) {
            panel.note("Could not rename the session: " + e.getMessage());
        }
    }

    /** Whether the conversation in progress has anything in it yet. */
    private boolean sessionIsEmpty() {
        String id = agent.sessionId();
        return id == null || !java.nio.file.Files.exists(sessionManager.sessionFile(id));
    }

    /** A session's name as the picker shows it, or null when it has none. */
    private String titleOf(String sessionId) {
        String name = sessionManager.sessionTitle(sessionId);
        return name == null || name.isBlank() ? null : name;
    }

    /**
     * Delete one transcript.
     *
     * @return true when the file is gone; false when it could not be removed,
     *         so the caller can say so rather than claim a deletion
     */
    private boolean deleteSession(String sessionId) {
        try {
            sessionManager.deleteSession(sessionId);
            return true;
        } catch (IOException | IllegalArgumentException e) {
            panel.note("Could not delete session " + shortId(sessionId) + ": " + e.getMessage());
            return false;
        }
    }

    private void resumeSession(String id) {
        try {
            agent.clearHistory();
            agent.enableSession(sessionManager, id);
            panel.clearTranscript();
            renderHistory();
            refreshStatus();
            panel.setSessionTitle(titleOf(id));
            panel.note("Resumed session " + shortId(id) + ".");
        } catch (IOException e) {
            panel.note("Could not resume " + shortId(id) + ": " + e.getMessage());
        }
    }

    private void renderHistory() {
        for (AgentMessage m : agent.messages()) {
            switch (m) {
                case UserMessage u -> {
                    String text = stripContext(u.content());
                    if (!text.isBlank()) {
                        panel.renderTurn("You", text);
                    }
                }
                case AssistantMessage a -> {
                    if (a.content() != null && !a.content().isBlank()) {
                        panel.renderTurn("Agent", a.content());
                    }
                }
                case ToolResultMessage t -> panel.note(t.toolName() + " → "
                        + (t.content() == null ? "" : t.content().strip()));
            }
        }
    }

    /** Hide the per-turn context preamble we prepend in {@link #submit}. */
    private static String stripContext(String content) {
        if (content == null) {
            return "";
        }
        return SessionManager.stripEditorContext(content);
    }

    private static String shortId(String id) {
        if (id == null) {
            return "?";
        }
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    // ── Providers / login ──────────────────────────────────────────────────────

    @Override
    public void login() {
        panel.setHint("Opening your browser to sign in to ChatGPT — complete it there…");
        CodexLogin.start(new CodexLogin.Listener() {
            @Override
            public void onAuthUrl(String url) {
                openBrowser(url);
            }

            @Override
            public void onSuccess() {
                SwingUtilities.invokeLater(AgentChatController.this::onSignedIn);
            }

            @Override
            public void onError(String message) {
                SwingUtilities.invokeLater(() -> {
                    panel.setHint("ChatGPT sign-in failed: " + message);
                    panel.setLoginEnabled(true);
                });
            }
        });
    }

    private void loginDevice() {
        panel.note("Starting ChatGPT device sign-in…");
        CodexLogin.startDevice(new CodexLogin.DeviceListener() {
            @Override
            public void onUserCode(String userCode, String verifyUrl) {
                SwingUtilities.invokeLater(() -> panel.note(
                        "Go to " + verifyUrl + " and enter code: " + userCode));
            }

            @Override
            public void onSuccess() {
                SwingUtilities.invokeLater(AgentChatController.this::onSignedIn);
            }

            @Override
            public void onError(String message) {
                SwingUtilities.invokeLater(() -> panel.note("Device sign-in failed: " + message));
            }
        });
    }

    private void onSignedIn() {
        // Sign-in switches to the Codex/ChatGPT provider with ITS default model — not
        // whatever model was previously active (which the backend would reject).
        agent.setProviderConfig(ProviderConfig.forProvider("openai-codex", null));
        panel.setProviderSelection("openai-codex");
        panel.note("Signed in to ChatGPT.");
        panel.setLoginEnabled(true);
        refreshStatus();
        persistDefaults(null, false);   // default to ChatGPT next launch (token in auth.json)
    }

    @Override
    public void abort() {
        runner.abort();
    }

    private void logout() {
        forgetCredential(CodexOAuth.PROVIDER_ID);
    }

    @Override
    public AgentCredentials.State credential(String provider) {
        return credentials.stateOf(provider);
    }

    @Override
    public List<String> storedCredentials() {
        return credentials.stored();
    }

    @Override
    public void forgetCredential(String provider) {
        try {
            // Throws when the credential survived the delete, so this note is
            // only ever written over a store that really gave it up.
            credentials.forget(provider);
            panel.note(CodexOAuth.PROVIDER_ID.equals(provider) ? "Signed out of ChatGPT."
                    : "Forgot the API key for " + provider + ".");
            // The agent is still holding the key it started with; drop it so the
            // next turn asks for credentials rather than quietly using the old one.
            reloadCredentials(provider);
        } catch (Exception e) {
            panel.note("Could not forget the credential: " + e.getMessage());
        }
        refreshStatus();   // whatever happened, the panel must show what is true now
    }

    @Override
    public void forgetAllCredentials() {
        // Never throws: one provider failing must not abandon the others, nor
        // the refresh that tells the panel what is left.
        AgentCredentials.Forgotten result = credentials.forgetAll();
        if (!result.gone().isEmpty()) {
            panel.note("Forgot the stored credentials for " + String.join(", ", result.gone()) + ".");
        }
        if (!result.failed().isEmpty()) {
            panel.note("Could not forget the credentials for " + String.join(", ", result.failed())
                    + " — they are still stored. The keychain may be locked.");
        }
        if (result.gone().isEmpty() && result.failed().isEmpty()) {
            panel.note("Nothing was stored.");
        }
        reloadCredentials(agent.config().provider());
        refreshStatus();
    }

    /**
     * Drop a key the agent is still holding in memory. Only the provider in use
     * matters: the others are read from the store when they are switched to.
     */
    private void reloadCredentials(String provider) {
        if (provider != null && provider.equals(agent.config().provider())) {
            agent.setProviderConfig(ProviderConfig.forProvider(provider,
                    ChatAgentFactory.isKeyBased(provider) ? secrets.get(provider).orElse(null) : null));
        }
    }

    private String toolsList() {
        List<String> names = agent.toolRegistry().all().stream()
                .map(AgentTool::name).sorted().toList();
        return "Tools (" + names.size() + "): " + String.join(", ", names);
    }

    private void exportConversation() {
        StringBuilder md = new StringBuilder("# Agent conversation\n");
        for (AgentMessage m : agent.messages()) {
            switch (m) {
                case UserMessage u -> {
                    String text = stripContext(u.content());
                    if (!text.isBlank()) {
                        md.append("\n## You\n\n").append(text).append('\n');
                    }
                }
                case AssistantMessage a -> {
                    if (a.content() != null && !a.content().isBlank()) {
                        md.append("\n## Agent\n\n").append(a.content()).append('\n');
                    }
                }
                case ToolResultMessage t -> md.append("\n> tool `").append(t.toolName())
                        .append("`: ").append(t.content() == null ? "" : t.content().strip())
                        .append('\n');
            }
        }
        try {
            Path out = host.workingDirectory()
                    .resolve("agent-conversation-" + shortId(agent.sessionId()) + ".md");
            Files.writeString(out, md.toString());
            panel.note("Exported conversation to " + out);
        } catch (Exception e) {
            panel.note("Export failed: " + e.getMessage());
        }
    }

    private void compact() {
        panel.note("Compacting context…");
        Thread.ofVirtual().name("agent-compact").start(() -> {
            try {
                String summary = agent.compact();
                SwingUtilities.invokeLater(() ->
                        panel.note(summary == null ? "Nothing to compact." : "Context compacted."));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> panel.note("Compact failed: " + e.getMessage()));
            }
        });
    }

    /** Survey the project and write an AGENTS.md (runs the agent, write is approval-gated). */
    private void initProject() {
        panel.note("Surveying the project and drafting AGENTS.md…");
        panel.setBusy(true);
        runner.submit(INIT_PROMPT);
    }

    private void refreshStatus() {
        // The id only once the session is a file. One is reserved when the
        // panel opens and written at the first message, so naming it earlier
        // points at nothing — and reads as an old session coming back.
        String sessionId = agent.sessionId();
        boolean started = sessionId != null
                && java.nio.file.Files.exists(sessionManager.sessionFile(sessionId));
        String hint = ChatAgentFactory.authHint(agent.config());
        // Show what will answer as soon as something can: with a key or a
        // sign-in in place, "openai-codex / gpt-5.6-sol" is the confirmation
        // that it worked. The session id waits for a session that exists —
        // naming one before it is a file reads as an old session coming back.
        if (hint == null) {
            panel.setModelLabel(agent.config().provider(), agent.config().model(),
                    started ? sessionId : null);
        } else {
            panel.setModelLabel(null, null, null);
        }
        host.agentModelChanged(agent.config().provider(), agent.config().model());
        // The panel's empty state says what to do next, and what that is
        // depends on whether there is anywhere to send a question yet.
        boolean showingCard = panel.showEmptyState(hint == null);
        if (hint != null) {
            // With the card up, it already says there is no provider, and says
            // it with a button; repeating that in the banner just crowds the
            // panel. Mid-conversation there is no card, so the banner carries it.
            panel.setHint(showingCard ? null : hint);
        } else if (!hasContextFile()) {
            panel.setHint("No AGENTS.md for this project — run /init to generate one.");
        } else {
            panel.setHint(null);
        }
    }

    /** True if the current working directory has a context file the agent reads. */
    private boolean hasContextFile() {
        return hasContextFile(host.workingDirectory());
    }

    /** True if {@code dir} holds one of the agent's context files (AGENTS.md, …). */
    static boolean hasContextFile(Path dir) {
        if (dir == null) {
            return false;
        }
        for (String name : CONTEXT_FILES) {
            if (java.nio.file.Files.exists(dir.resolve(name))) {
                return true;
            }
        }
        return false;
    }

    private String skillsList() {
        StringBuilder sb = new StringBuilder();
        if (skills.isEmpty()) {
            sb.append("No skills loaded.");
        } else {
            sb.append("Loaded skills (").append(skills.size()).append("):");
            for (Skill s : skills) {
                sb.append("\n  - ").append(s.name());
                if (s.description() != null && !s.description().isBlank()) {
                    sb.append(" — ").append(s.description());
                }
            }
        }
        // skills are instruction packs, not callable functions — point to /tools
        sb.append("\n(Skills are guidance, not tools. Run /tools to see callable tools.)");
        return sb.toString();
    }

    private static String helpText() {
        return "Commands: /help, /init, /clear, /provider [name], /model [name], /login [device], "
                + "/logout, /tools, /skills, /compact, /revert, /export, /new, /sessions, /resume [id]. "
                + "Anything else is sent to the agent.";
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ignored) {
            // fall through to showing the URL
        }
        SwingUtilities.invokeLater(() -> panel.setHint("Open this URL to sign in: " + url));
    }

    /** The chat panel to dock (sidebar) or show (standalone). */
    public AgentChatPanel panel() {
        return panel;
    }

    /** Cancel any in-flight turn and stop external MCP servers. */
    public void dispose() {
        runner.abort();
        host.dispose();
        if (mcpManager != null) {
            try {
                mcpManager.close();
            } catch (Exception ignore) {
                // best-effort shutdown
            }
        }
    }
}
