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
package com.dogsbay.dogsbayaieditor.plugin.agent;

import java.awt.Window;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.dogsbay.agent.acp.AcpClient;
import com.dogsbay.agent.acp.AcpProcess;
import com.dogsbay.agent.acp.AcpWire.AgentInfo;
import com.dogsbay.agent.acp.AcpWire.AuthMethod;
import com.dogsbay.agent.acp.AcpWire.McpServerConfig;
import com.dogsbay.agent.acp.AcpWire.StopReason;
import com.dogsbay.agent.acp.AcpWire.Update;
import com.dogsbay.agent.acp.AgentRegistryCatalog;
import com.dogsbay.agent.acp.McpInjection;
import com.dogsbay.agent.acp.SavedKeys;
import com.dogsbay.agent.secret.SecretStore;
import com.dogsbay.agent.acp.SwingAcpPermissions;
import com.dogsbay.agent.acp.Tiers;
import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.AgentSessionRegistry;
import com.dogsbay.agent.session.CapabilityTier;
import com.dogsbay.agent.session.SessionKind;
import com.dogsbay.agent.ui.HostedChatPanel;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.commands.CommandException;
import com.dogsbay.dogsbayaieditor.commands.OpenCommand;

/**
 * One hosted ACP agent in the editor: its registry session, its process,
 * its client, and the panel that shows it. Lifecycle: {@link #start} spawns
 * and handshakes on a background thread; {@link #submit} runs a turn;
 * {@link #close} tears everything down and closes the session.
 */
public final class HostedAgentSession implements HostedChatPanel.Actions {

    private static final Pattern MENTION = com.dogsbay.agent.ui.Mentions.PATTERN;

    private final DogsBayAIEditor editor;
    private final AgentRegistryCatalog.Entry entry;
    private final CapabilityTier tier;
    private final AgentSession session;
    private final HostedChatPanel panel;
    private final Supplier<Window> dialogParent;
    private final Runnable onClosed;
    private final boolean passSavedKey;
    private volatile AcpProcess process;
    private volatile AcpClient client;
    private volatile String acpSessionId;
    private volatile boolean following;
    private volatile boolean closed;
    private volatile boolean briefed;
    private volatile boolean toolsInjected;
    private volatile boolean turnRunning;
    private volatile String resumeSessionId;
    private volatile String title;
    private final java.util.List<Runnable> titleListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public HostedAgentSession(DogsBayAIEditor editor, AgentRegistryCatalog.Entry entry, CapabilityTier tier,
            Supplier<Window> dialogParent, Runnable onClosed) {
        this(editor, entry, tier, false, dialogParent, onClosed);
    }

    /**
     * @param passSavedKey hand the agent the API key the built-in agent has
     *                     saved for the same vendor, in its environment, for
     *                     this session only
     */
    public HostedAgentSession(DogsBayAIEditor editor, AgentRegistryCatalog.Entry entry, CapabilityTier tier,
            boolean passSavedKey, Supplier<Window> dialogParent, Runnable onClosed) {
        this.editor = editor;
        this.entry = entry;
        this.tier = tier;
        this.passSavedKey = passSavedKey;
        this.dialogParent = dialogParent;
        this.onClosed = onClosed;
        AgentSessionRegistry registry = editor.getSessionRegistry();
        this.session = registry.open(SessionKind.ACP_HOSTED, entry.name(), "ai:" + entry.id(), tier);
        this.panel = new HostedChatPanel(entry.name(), Tiers.badge(tier), Tiers.describe(tier), this);
    }

    public HostedChatPanel panel() {
        return panel;
    }

    public AgentRegistryCatalog.Entry entry() {
        return entry;
    }

    /** The session's name: the agent's, unless renamed. */
    public String title() {
        return title != null && !title.isBlank() ? title : entry.name();
    }

    /** Rename the session; the tab, the panel and the remembered session follow. */
    public void rename(String newTitle) {
        title = newTitle == null || newTitle.isBlank() ? null : newTitle.strip();
        panel.setTitle(title());
        Path project = editor.projectRootForAgents();
        if (project != null && acpSessionId != null) {
            HostedSessionStore.rename(project, entry.id(), acpSessionId, title);
        }
        titleListeners.forEach(Runnable::run);
    }

    public void onTitleChanged(Runnable listener) {
        titleListeners.add(listener);
    }

    private final java.util.List<Runnable> turnListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Called on the EDT when a turn ends, so a tab that is not in view can say so. */
    public void onTurnEnded(Runnable listener) {
        turnListeners.add(listener);
    }

    /** The {@code @path} mention for a dropped file, or null when it is outside the project. */
    @Override
    public String mention(java.io.File file) {
        return mentionFor(file, editor.projectRootForAgents());
    }

    /** The {@code @path} for {@code file} under {@code root}, or null when it cannot be attached. */
    static String mentionFor(java.io.File file, Path root) {
        return com.dogsbay.agent.ui.Mentions.forFile(file, root);
    }

    /** The transcript as Markdown, with the facts of the session in front. */
    public String exportMarkdown() {
        Path project = editor.projectRootForAgents();
        return com.dogsbay.agent.ui.TranscriptExport.markdown(title(), entry.name(), Tiers.badge(tier),
                project == null ? null : project.toString(), java.time.Instant.now(), panel.transcriptText());
    }

    public AgentSession session() {
        return session;
    }

    public CapabilityTier tier() {
        return tier;
    }

    /** Before {@link #start()}: pick up this earlier ACP session instead of opening a new one. */
    public void resume(String sessionId) {
        this.resumeSessionId = sessionId;
    }

    /** Spawn, handshake, authenticate if asked, open the ACP session. */
    public void start() {
        Thread.ofVirtual().name("acp-" + entry.id()).start(this::connect);
    }

    private void connect() {
        try {
            Path cwd = workingDirectory();
            java.util.Map<String, String> env = new java.util.LinkedHashMap<>(entry.launchEnv());
            if (passSavedKey) {
                java.util.Map<String, String> key = SavedKeys.environment(SecretStore.keychain(), entry.id());
                env.putAll(key);
                if (!key.isEmpty()) {
                    String var = key.keySet().iterator().next();
                    onEdt(() -> panel.note("Passing the API key saved for the built-in agent as " + var
                            + " to this agent, for this session only."));
                }
            }
            AcpProcess started = AcpProcess.start(entry.launchCommand(), env, cwd,
                    line -> System.err.println("[" + entry.id() + "] " + line));
            if (closed) {
                started.close();   // the tab went away while npx was still downloading
                return;
            }
            process = started;
            client = new AcpClient(process.process().getInputStream(), process.process().getOutputStream(),
                    Tiers.capabilities(tier), new Listener(), new SwingAcpPermissions(dialogParent, entry.name()),
                    tier.allowsFileReads() ? new EditorAcpFileSystem(editor.getCommandExecutor(), session,
                            editor::projectRootForAgents) : null);
            if (closed) {
                teardown();
                return;
            }
            AgentInfo info = client.initialize("DogsBay XML",
                    com.dogsbay.dogsbayaieditor.Identity.getIdentity().getVersion());
            if (closed) {
                teardown();
                return;
            }
            onEdt(() -> panel.note("Connected to " + describe(info)));
            if (Tiers.isDeveloperByConstruction(info) && tier != CapabilityTier.T3_DEVELOPER) {
                onEdt(() -> panel.note("This agent speaks protocol " + info.protocolVersion()
                        + " and reads and writes files itself, whatever tier was chosen. Treat it as T3."));
            }
            // authMethods lists what the agent *offers*; whether a login is needed
            // is only known when session/new refuses with AUTH_REQUIRED.
            String refusal;
            try {
                openSession(cwd);
                return;
            } catch (com.dogsbay.agent.acp.JsonRpcConnection.RpcException e) {
                if (!isAuthRequired(e) || !info.needsAuth()) {
                    throw e;
                }
                refusal = e.getMessage();
            }
            {
                onEdt(() -> {
                    panel.setLoginVisible(true);
                    panel.setStatus("sign-in required");
                    panel.note("The agent said: " + refusal);
                    StringBuilder ways = new StringBuilder();
                    for (AuthMethod m : info.authMethods()) {
                        ways.append("\n    ").append(m.name() != null ? m.name() : m.id());
                        if (m.description() != null) {
                            ways.append(" — ").append(m.description());
                        }
                    }
                    panel.note("The agent needs you to sign in. Click Sign in and choose how:" + ways
                            + "\n  A browser sign-in reuses the login the agent's own CLI has; an API key "
                            + "must be in the agent's environment before the editor starts.");
                });
            }
        } catch (Exception e) {
            onEdt(() -> panel.error("Could not start " + entry.name() + ": " + message(e)));
        }
    }

    /**
     * The earlier session's id when it was asked for and the agent took it
     * back, else null and the caller opens a new one. The replayed history
     * arrives through the listener like live updates.
     */
    private String resumeSession(Path cwd, List<McpServerConfig> mcp) throws Exception {
        String id = resumeSessionId;
        if (id == null) {
            return null;
        }
        if (!client.agent().loadSession()) {
            onEdt(() -> panel.note("This agent cannot resume earlier sessions; starting a new one."));
            return null;
        }
        onEdt(() -> panel.note("Resuming the earlier session…"));
        try {
            client.loadSession(id, cwd, mcp);
            briefed = true;   // the brief is in the replayed history; not sent again
            Path project = editor.projectRootForAgents();
            if (project != null) {
                HostedSessionStore.recent(project, entry.id()).stream().filter(x -> id.equals(x.sessionId()))
                        .map(HostedSessionStore.Saved::title).filter(t -> t != null && !t.isBlank() && title == null)
                        .findFirst().ifPresent(t -> {
                            title = t;
                            onEdt(() -> {
                                panel.setTitle(t);
                                titleListeners.forEach(Runnable::run);
                            });
                        });
            }
            onEdt(() -> panel.note("Resumed the earlier session; its history is above."));
            return id;
        } catch (com.dogsbay.agent.acp.JsonRpcConnection.RpcException e) {
            if (isAuthRequired(e)) {
                throw e;   // sign in first; the resume is retried after the login
            }
            onEdt(() -> panel.note("Could not resume the earlier session (" + message(e) + "); starting a new one."));
            resumeSessionId = null;
            Path project = editor.projectRootForAgents();
            if (project != null) {
                HostedSessionStore.forget(project, entry.id(), id);
            }
            return null;
        }
    }

    private void openSession(Path cwd) throws Exception {
        List<McpServerConfig> mcp = McpInjection.servers(mcpUrl(), session.token(), client.agent().mcpHttp(),
                McpInjection.bridgeCommand());
        String resumed = resumeSession(cwd, mcp);
        acpSessionId = resumed != null ? resumed : client.newSession(cwd, mcp);
        Path project = editor.projectRootForAgents();
        if (project != null) {
            if (client.agent().loadSession()) {
                HostedSessionStore.remember(project, entry.id(), acpSessionId, tier.name());
                if (title != null) {
                    HostedSessionStore.rename(project, entry.id(), acpSessionId, title);   // named while connecting
                }
            } else {
                HostedSessionStore.forget(project, entry.id());   // never offer a resume it cannot do
            }
        }
        toolsInjected = !mcp.isEmpty();
        onEdt(() -> {
            panel.setLoginVisible(false);
            panel.setReady(true);
            if (mcp.isEmpty()) {
                panel.note(IntegrationServerNotice.startedWithoutTools());
            } else {
                panel.note(IntegrationServerNotice.startedWithTools(mcp.get(0) instanceof McpServerConfig.Http));
            }
            panel.focusInput();
        });
    }

    @Override
    public void login() {
        AgentInfo info = client.agent();
        AuthMethod method = chooseAuthMethod(info.authMethods());
        if (method == null) {
            return;
        }
        onEdt(() -> panel.setStatus("signing in with " + (method.name() != null ? method.name() : method.id()) + "…"));
        Thread.ofVirtual().start(() -> {
            try {
                client.authenticate(method.id());
                openSession(workingDirectory());
            } catch (Exception e) {
                onEdt(() -> {
                    panel.setStatus("sign-in required");
                    panel.error("Sign-in with " + (method.name() != null ? method.name() : method.id())
                            + " failed: " + message(e));
                });
            }
        });
    }

    /**
     * ACP's auth_required error (-32000), or an agent that says so in so many
     * words. Bare "auth" is not enough: it matches "author" and "unauthorized".
     */
    static boolean isAuthRequired(com.dogsbay.agent.acp.JsonRpcConnection.RpcException e) {
        String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(java.util.Locale.ROOT);
        return e.code() == -32000 || m.contains("authentication required") || m.contains("not authenticated")
                || m.contains("login required") || m.contains("sign in required") || m.contains("please log in")
                || m.contains("not logged in");
    }

    /** One method: use it. Several: ask, preselecting the browser-style one. */
    private AuthMethod chooseAuthMethod(List<AuthMethod> methods) {
        if (methods.isEmpty()) {
            return null;
        }
        if (methods.size() == 1) {
            return methods.get(0);
        }
        AuthMethod preferred = preferredAuthMethod(methods);
        Object[] labels = methods.stream().map(m -> m.name() != null ? m.name() : m.id()).toArray();
        StringBuilder text = new StringBuilder("How should " + entry.name() + " sign in?\n");
        for (AuthMethod m : methods) {
            text.append("\n").append(m.name() != null ? m.name() : m.id());
            if (m.description() != null) {
                text.append(": ").append(m.description());
            }
        }
        int choice = JOptionPane.showOptionDialog(dialogParent.get(), text.toString(), "Sign in to " + entry.name(),
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, labels,
                labels[methods.indexOf(preferred)]);
        return choice < 0 ? null : methods.get(choice);
    }

    /**
     * The method most likely to reuse an existing login: one that names a
     * browser or account flow, not an API key that has to be in the
     * environment. Falls back to the agent's first.
     */
    static AuthMethod preferredAuthMethod(List<AuthMethod> methods) {
        for (AuthMethod m : methods) {
            String id = (m.id() + " " + (m.name() == null ? "" : m.name())).toLowerCase(java.util.Locale.ROOT);
            if (id.contains("chat") || id.contains("oauth") || id.contains("browser") || id.contains("login")
                    || id.contains("account") || id.contains("subscription")) {
                return m;
            }
        }
        for (AuthMethod m : methods) {
            String id = (m.id() + " " + (m.name() == null ? "" : m.name())).toLowerCase(java.util.Locale.ROOT);
            if (!id.contains("key")) {
                return m;
            }
        }
        return methods.get(0);
    }

    @Override
    public void submit(String text) {
        if (turnRunning || acpSessionId == null) {
            onEdt(() -> panel.note("Wait for the current turn to finish, or press Stop."));
            return;
        }
        turnRunning = true;
        Thread.ofVirtual().start(() -> {
            try {
                String prompt = text;
                if (!briefed) {
                    // The first turn carries the editor brief; the panel showed the user's raw text.
                    briefed = true;
                    prompt = HostedBrief.text(editor.projectRootForAgents(), activeFile(), toolsInjected) + text;
                }
                List<AcpClient.Attachment> attached = attachments(text);
                StopReason stop;
                if (attached.isEmpty() || client.agent().embeddedContext()) {
                    stop = client.prompt(acpSessionId, prompt, attached);
                } else {
                    // The agent does not take resource blocks: inline the files as text.
                    StringBuilder inline = new StringBuilder(prompt);
                    for (AcpClient.Attachment a : attached) {
                        inline.append("\n\n--- ").append(a.path()).append(" ---\n").append(a.text());
                    }
                    stop = client.prompt(acpSessionId, inline.toString());
                }
                onEdt(() -> {
                    panel.setBusy(false);
                    if (stop != StopReason.END_TURN) {
                        panel.note("Turn ended: " + stop.name().toLowerCase().replace('_', ' '));
                    }
                });
            } catch (Exception e) {
                onEdt(() -> {
                    panel.setBusy(false);
                    panel.error(message(e));
                });
            } finally {
                turnRunning = false;
                if (editor.getWriteGate() != null) {
                    editor.getWriteGate().turnEnded(session);
                }
                onEdt(() -> turnListeners.forEach(Runnable::run));   // however the turn ended
            }
        });
    }

    @Override
    public void abort() {
        try {
            if (client != null && acpSessionId != null) {
                client.cancel(acpSessionId);
            }
        } catch (IOException e) {
            onEdt(() -> panel.error("Could not cancel: " + message(e)));
        }
    }

    @Override
    public void followChanged(boolean follow) {
        this.following = follow;
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        editor.getSessionRegistry().close(session.id());
        if (onClosed != null) {
            onClosed.run();
        }
        // Ending the process can wait up to three seconds for a stubborn
        // adapter; never do that on the event dispatch thread.
        Thread.ofVirtual().name("acp-close-" + entry.id()).start(this::teardown);
    }

    /** Process first: its EOF frees the client's reader thread. */
    private void teardown() {
        if (process != null) {
            process.close();
        }
        if (client != null) {
            client.close();
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────

    /** {@code @path} mentions that resolve to project files become attachments. */
    List<AcpClient.Attachment> attachments(String text) {
        return attachments(text, editor.projectRootForAgents());
    }

    static List<AcpClient.Attachment> attachments(String text, Path root) {
        List<AcpClient.Attachment> out = new ArrayList<>();
        if (root == null) {
            return out;
        }
        Matcher m = MENTION.matcher(text);
        while (m.find()) {
            Path p = root.resolve(m.group(1)).normalize();
            if (p.startsWith(root) && Files.isRegularFile(p)) {
                try {
                    out.add(new AcpClient.Attachment(p, mime(p), Files.readString(p, StandardCharsets.UTF_8)));
                } catch (IOException ignore) {
                    // unreadable; the mention stays as text
                }
            }
        }
        return out;
    }

    static String mime(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        if (n.endsWith(".dita") || n.endsWith(".ditamap") || n.endsWith(".xml")) {
            return "application/xml";
        }
        if (n.endsWith(".md")) {
            return "text/markdown";
        }
        return "text/plain";
    }

    private Path activeFile() {
        try {
            for (var d : editor.getCommandExecutor().execute(new com.dogsbay.dogsbayaieditor.commands.ListDocumentsCommand())) {
                if (d.active()) {
                    return d.file();
                }
            }
        } catch (CommandException ignore) {
            // no documents
        }
        return null;
    }

    private Path workingDirectory() {
        Path root = editor.projectRootForAgents();
        return root != null ? root : Path.of(System.getProperty("user.dir"));
    }

    /**
     * The integration server was switched on or off. A running session keeps
     * the tools it started with — there is no way to hand it more — so all this
     * can do is say so, once, to the sessions it actually affects.
     */
    public void integrationServerChanged() {
        String note = IntegrationServerNotice.afterServerChanged(toolsInjected, mcpUrl() != null);
        // Latched on the message, not on having spoken once: a session can be
        // told the server came on and later that it went off, and the second is
        // not a repeat of the first. Toggling the same state says nothing twice.
        if (note != null && !note.equals(lastServerNote)) {
            lastServerNote = note;
            onEdt(() -> panel.note(note));
        }
    }

    private volatile String lastServerNote;

    private String mcpUrl() {
        var ipc = editor.getIpcServerManager();
        if (ipc == null || !ipc.isRunning() || !editor.getProperties().isMcpEndpointEnabled()) {
            return null;
        }
        return "http://127.0.0.1:" + ipc.getPort() + "/mcp";
    }

    private static String describe(AgentInfo info) {
        String name = info.name() != null ? info.name() : "agent";
        return name + (info.version() != null ? " " + info.version() : "") + " (protocol " + info.protocolVersion() + ")";
    }

    private static String message(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private static void onEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    /** Streams updates to the panel and follows edits when asked. */
    private final class Listener implements AcpClient.Listener {
        @Override
        public void onUpdate(String sessionId, Update update) {
            Update shown = update instanceof Update.UserMessage u ? new Update.UserMessage(HostedBrief.strip(u.text()))
                    : update;
            onEdt(() -> panel.render(shown));
            if (following && update instanceof Update.ToolCallUpdate u && "completed".equals(u.status())
                    && isEdit(u.kind()) && !u.locations().isEmpty()) {
                follow(u.locations());
            }
        }

        /** Follow means the files the agent changed, not everything it looked at. */
        private boolean isEdit(String kind) {
            return kind == null || "edit".equals(kind) || "delete".equals(kind) || "move".equals(kind);
        }

        private void follow(List<String> locations) {
            Path root = editor.projectRootForAgents();
            if (root == null) {
                return;
            }
            for (String loc : locations) {
                try {
                    Path p = loc.startsWith("file:") ? Path.of(java.net.URI.create(loc)) : Path.of(loc);
                    if (p.toAbsolutePath().normalize().startsWith(root) && Files.isRegularFile(p)) {
                        editor.getCommandExecutor().execute(new OpenCommand(p, null));
                    }
                } catch (CommandException | RuntimeException ignore) {
                    // follow is best-effort; an odd path from the agent must not hurt the session
                }
            }
        }

        @Override
        public void onPermissionRequested(com.dogsbay.agent.acp.AcpWire.PermissionRequest request) {
            String what = request.toolCall().title() != null ? request.toolCall().title() : "a tool";
            onEdt(() -> {
                panel.note(entry.name() + " is asking permission to run: " + what
                        + " — answer in the dialog (it may be behind this window).");
                panel.setStatus("waiting for your permission…");
            });
        }

        @Override
        public void onPermissionAnswered(com.dogsbay.agent.acp.AcpWire.PermissionRequest request, String optionId) {
            String answer = optionId == null ? "cancelled" : request.options().stream()
                    .filter(o -> o.optionId().equals(optionId)).findFirst()
                    .map(o -> o.name() != null ? o.name() : o.optionId()).orElse(optionId);
            onEdt(() -> {
                panel.note("Permission: " + answer);
                panel.setStatus("working…");
            });
        }

        @Override
        public void onFailure(String message, Throwable cause) {
            if (!closed) {
                onEdt(() -> {
                    panel.setBusy(false);
                    panel.setReady(false);
                    panel.error(message + (cause != null && cause.getMessage() != null ? ": " + cause.getMessage() : ""));
                });
                if (editor.getWriteGate() != null) {
                    editor.getWriteGate().turnEnded(session);
                }
            }
        }
    }

    /** What the start dialog decided. */
    public record StartChoice(CapabilityTier tier, boolean passSavedKey) {}

    /** Ask the user which tier to run an agent at, and whether to pass a saved key; null when cancelled. */
    public static StartChoice chooseStart(Window parent, AgentRegistryCatalog.Entry entry, CapabilityTier initial,
            boolean savedKeyAvailable) {
        Object[] options = {
            "Commands only (T1)", "Read files (T2)", "Read and write files (T3)", "Cancel"
        };
        int initialIndex = switch (initial) {
            case T1_COMMANDS -> 0;
            case T2_READS -> 1;
            case T3_DEVELOPER -> 2;
        };
        javax.swing.JPanel message = new javax.swing.JPanel(new java.awt.BorderLayout(0, 8));
        message.add(new javax.swing.JLabel("<html>How much may " + entry.name() + " do in this project?<br><br>"
                + "<b>T1</b>  " + Tiers.describe(CapabilityTier.T1_COMMANDS) + "<br>"
                + "<b>T2</b>  " + Tiers.describe(CapabilityTier.T2_READS) + "<br>"
                + "<b>T3</b>  " + Tiers.describe(CapabilityTier.T3_DEVELOPER) + "<br><br>"
                + "<i>The tier is what the editor hands over. An agent with its own tools (Claude Code's Read, "
                + "Edit, Bash) still has them and still asks you before using them.</i></html>"),
                java.awt.BorderLayout.CENTER);
        javax.swing.JCheckBox passKey = null;
        if (savedKeyAvailable) {
            String vendor = SavedKeys.mappingFor(entry.id()).map(SavedKeys.Mapping::provider).orElse("this vendor");
            passKey = new javax.swing.JCheckBox("Pass the API key saved for " + vendor
                    + " in the built-in agent to this agent (this session only)");
            passKey.setToolTipText("Otherwise the agent uses its own login or the environment the editor was started from");
            message.add(passKey, java.awt.BorderLayout.SOUTH);
        }
        int choice = JOptionPane.showOptionDialog(parent, message, "Start " + entry.name(),
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[initialIndex]);
        CapabilityTier tier = switch (choice) {
            case 0 -> CapabilityTier.T1_COMMANDS;
            case 1 -> CapabilityTier.T2_READS;
            case 2 -> CapabilityTier.T3_DEVELOPER;
            default -> null;
        };
        return tier == null ? null : new StartChoice(tier, passKey != null && passKey.isSelected());
    }

    /** Ask the user which tier to run an agent at; null when cancelled. */
    public static CapabilityTier chooseTier(Window parent, AgentRegistryCatalog.Entry entry, CapabilityTier initial) {
        StartChoice c = chooseStart(parent, entry, initial, false);
        return c == null ? null : c.tier();
    }
}
