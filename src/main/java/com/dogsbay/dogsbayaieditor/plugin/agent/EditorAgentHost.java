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
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import java.util.ArrayList;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.dogsbay.agent.AgentContext;
import com.dogsbay.agent.AgentHost;
import com.dogsbay.agent.ToolChangePreview;
import com.dogsbay.dogsbayaieditor.commands.CommandExecutor;
import com.dogsbay.dogsbayaieditor.commands.GetContentCommand;
import com.dogsbay.dogsbayaieditor.commands.GetSelectionCommand;
import com.dogsbay.dogsbayaieditor.commands.ListDocumentsCommand;
import com.dogsbay.dogsbayaieditor.commands.SetContentCommand;
import com.dogsbay.dogsbayaieditor.commands.results.OpenDocument;
import com.dogsbay.dogsbayaieditor.ditaproject.Deliverable;
import com.dogsbay.dogsbayaieditor.plugin.DefaultPluginContext;
import com.dogsbay.dogsbayaieditor.plugin.PluginContext;
import com.dogsbay.dogsbayaieditor.services.DeliverableService;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.XMLError;
import com.dogsbay.xml.XMLGrammar;
import com.xagent.tool.AgentTool;
import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.AgentSessionRegistry;
import com.dogsbay.agent.session.CapabilityTier;
import com.dogsbay.agent.session.SessionKind;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.commands.WriteGate;

/**
 * {@link AgentHost} backed by the live editor. Surfaces the active document and
 * its directory to the agent. This is the only editor-coupled glue; the chat UI
 * and agent runtime live in the standalone-clean {@code com.dogsbay.agent}
 * module behind the {@link AgentHost} SPI.
 */
final class EditorAgentHost implements AgentHost {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PluginContext ctx;
    private final Window dialogParent;
    private final CommandExecutor executor;
    private final List<AgentTool> editorTools;
    private final AgentSessionRegistry sessions;
    private volatile AgentSession session;

    EditorAgentHost(PluginContext ctx, Window dialogParent) {
        this.ctx = ctx;
        this.dialogParent = dialogParent;
        DogsBayAIEditor editor = ((DefaultPluginContext) ctx).getEditor();
        this.executor = editor.getCommandExecutor();
        // The built-in agent is a session like any other caller, so its tool
        // calls are attributed and audited under its own identity.
        this.sessions = editor.getSessionRegistry();
        this.session = sessions.open(SessionKind.BUILTIN, "AI Agent", "ai:xagent",
                CapabilityTier.T1_COMMANDS);
        // Pass a dynamic working-dir supplier so editor tools resolve
        // project-relative paths against the current project root.
        this.editorTools = EditorToolProvider.tools(executor, this::workingDirectory, this::session);
    }

    /** The built-in agent's session (its identity follows the chosen model). */
    AgentSession session() {
        return session;
    }

    @Override
    public void agentModelChanged(String provider, String model) {
        String identity = "ai:xagent/" + (provider == null ? "?" : provider)
                + "/" + (model == null ? "?" : model);
        if (!identity.equals(session.identity())) {
            // Empty after dispose(): a login callback may outlive the chat.
            sessions.updateIdentity(session.id(), identity).ifPresent(s -> session = s);
        }
    }

    @Override
    public void turnEnded() {
        WriteGate gate = ((DefaultPluginContext) ctx).getEditor().getWriteGate();
        if (gate != null) {
            gate.turnEnded(session);
        }
    }

    @Override
    public void dispose() {
        sessions.close(session.id());
    }

    @Override
    public Path workingDirectory() {
        // Prefer the opened project/explorer root so project-wide tools (find,
        // grep, list_keys) and the .xagent.md context file + .xagent/skills
        // resolve against the user's project — not the editor's launch directory
        // and not just the active topic's folder.
        try {
            java.io.File root = ((DefaultPluginContext) ctx).getEditor()
                    .getFileExplorer().getRootDirectory();
            if (root != null && root.isDirectory()) {
                return root.toPath();
            }
        } catch (Exception ignore) {
            // explorer not ready / no folder opened → fall back below
        }
        Path file = activeFile();
        if (file != null && file.getParent() != null) {
            return file.getParent();
        }
        return Path.of(System.getProperty("user.dir"));
    }

    @Override
    public AgentContext currentContext() {
        Path file = activeFile();
        String docType = null;
        String grammar = null;
        try {
            DogsBayDocument doc = ctx.getDocumentManager().getActiveDocument();
            if (doc != null) {
                docType = describeDocType(doc);
                grammar = describeGrammar(doc);
            }
        } catch (Exception ignore) {
            // no active document / model not ready → omit type info
        }

        String deliverable = null;
        Path deliverableMap = null;
        try {
            DeliverableService svc = ((DefaultPluginContext) ctx).getEditor().getDeliverableService();
            Deliverable active = (svc != null) ? svc.getActiveDeliverable() : null;
            if (active != null) {
                deliverable = active.name();
                deliverableMap = active.map();
            }
        } catch (Exception ignore) {
            // no project / no active deliverable → omit deliverable info
        }

        return new AgentContext(file, selection(), openDocuments(), "Agent",
                docType, grammar, deliverable, deliverableMap);
    }

    /** All open documents (deduped by file), or just the active file as a fallback. */
    private List<Path> openDocuments() {
        try {
            List<OpenDocument> docs = executor.execute(new ListDocumentsCommand());
            List<Path> paths = new ArrayList<>();
            for (OpenDocument d : docs) {
                if (d.file() != null && !paths.contains(d.file())) {
                    paths.add(d.file());   // same file can appear once per view
                }
            }
            if (!paths.isEmpty()) {
                return paths;
            }
        } catch (Exception ignore) {
            // executor unavailable → fall back to the active file below
        }
        Path file = activeFile();
        return file == null ? List.of() : List.of(file);
    }

    /** The root element name, annotated "(DITA)" when it carries a DITA @class. */
    private static String describeDocType(DogsBayDocument doc) {
        XElement root = doc.getRoot();
        if (root == null || root.getName() == null) {
            return null;
        }
        return isDitaClass(root.getAttribute("class"))
                ? root.getName() + " (DITA)" : root.getName();
    }

    /**
     * True when {@code class} is a DITA {@code @class} architecture value (e.g.
     * {@code "- topic/topic concept/concept "}): it starts with {@code -} or {@code +}
     * and contains {@code /}. This avoids mislabelling XHTML/SVG (which use plain
     * {@code class} attributes) as DITA.
     */
    static boolean isDitaClass(String cls) {
        if (cls == null) {
            return false;
        }
        String c = cls.trim();
        return (c.startsWith("-") || c.startsWith("+")) && c.contains("/");
    }

    /** The governing grammar as "<file> (<TYPE>)", or null when none is associated. */
    private static String describeGrammar(DogsBayDocument doc) {
        XMLGrammar g = doc.getGrammar();
        if (g == null || g.getLocation() == null || g.getLocation().isBlank()) {
            return null;
        }
        String loc = g.getLocation();
        int slash = Math.max(loc.lastIndexOf('/'), loc.lastIndexOf('\\'));
        String name = (slash >= 0 && slash < loc.length() - 1) ? loc.substring(slash + 1) : loc;
        String type = switch (g.getType()) {
            case XMLGrammar.TYPE_DTD -> "DTD";
            case XMLGrammar.TYPE_XSD -> "XSD";
            case XMLGrammar.TYPE_RNG, XMLGrammar.TYPE_RNC -> "RelaxNG";
            default -> "grammar";
        };
        return name + " (" + type + ")";
    }

    /**
     * Mutating tools that always edit the ACTIVE document — so validating the active
     * document after them is correct. {@code set_document_content} is excluded: it can
     * target a different file via its {@code file} argument, which {@code afterToolCall}
     * can't see, so validating the active document could describe the wrong file.
     */
    private static final Set<String> VALIDATE_AFTER_TOOLS = Set.of(
            "replace_selection", "author_set_text", "author_insert_block");

    @Override
    public String validateAfterMutation(String toolName) {
        if (!VALIDATE_AFTER_TOOLS.contains(toolName)) {
            return null;   // not a content edit to the active document
        }
        try {
            DogsBayDocument doc = ctx.getDocumentManager().getActiveDocument();
            if (doc == null) {
                return null;
            }
            List<XMLError> errors = ctx.getSchemaManager().validate(doc);   // UI-free, background-safe
            Path file = activeFile();
            String name = (file != null && file.getFileName() != null)
                    ? file.getFileName().toString() : "document";
            return formatValidation(name, errors);
        } catch (Exception e) {
            return null;   // never let validation break the turn
        }
    }

    /** Renders a bounded validation summary the agent reads after its edit. */
    static String formatValidation(String docName, List<XMLError> errors) {
        String name = (docName == null || docName.isBlank()) ? "document" : docName;
        if (errors == null || errors.isEmpty()) {
            return "validation: " + name + " — 0 errors";
        }
        StringBuilder sb = new StringBuilder(
                "validation: " + name + " — " + errors.size() + " error(s)");
        int shown = Math.min(errors.size(), 5);
        for (int i = 0; i < shown; i++) {
            XMLError e = errors.get(i);
            sb.append("\n  line ").append(e.getLineNumber()).append(": ").append(e.getMessage());
        }
        if (errors.size() > shown) {
            sb.append("\n  …+").append(errors.size() - shown).append(" more");
        }
        return sb.toString();
    }

    @Override
    public ToolChangePreview previewToolChange(String toolName, String argumentsJson) {
        try {
            JsonNode args = MAPPER.readTree(argumentsJson);
            String after = proposedAfterText(toolName, args);
            if (after == null) {
                return null;   // not a previewable content edit
            }
            String before;
            String target;
            switch (toolName) {
                case "replace_selection" -> {
                    before = orEmpty(selection());
                    Path active = activeFile();
                    target = (active != null && active.getFileName() != null)
                            ? active.getFileName().toString() : "active document";
                }
                case "set_document_content" -> {
                    JsonNode file = args.path("file");
                    before = currentContent(file);
                    target = file.isTextual() && !file.asText().isBlank()
                            ? file.asText() : "active document";
                }
                default -> {
                    before = "";
                    target = null;
                }
            }
            // include the target file so approving the diff can't overwrite the wrong file
            String title = (target != null) ? toolName + " — " + target : toolName;
            return new ToolChangePreview(title, before, after);
        } catch (Exception e) {
            return null;   // unparseable args / no editor state → fall back to text prompt
        }
    }

    /** The proposed replacement text for a previewable content tool, else null. */
    static String proposedAfterText(String toolName, JsonNode args) {
        return switch (toolName) {
            case "replace_selection" -> textOrNull(args, "text");
            case "set_document_content" -> textOrNull(args, "content");
            default -> null;
        };
    }

    private static String textOrNull(JsonNode args, String field) {
        JsonNode v = args.path(field);
        return v.isTextual() ? v.asText() : null;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    /** The current text of the named (or active) document, or "" if unavailable. */
    private String currentContent(JsonNode fileNode) {
        try {
            Path file = fileNode.isTextual() ? Path.of(fileNode.asText()) : null;
            String content = executor.execute(new GetContentCommand(file));
            return content == null ? "" : content;
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public Path activeDocumentFile() {
        return activeFile();
    }

    @Override
    public void reloadFiles(java.util.List<Path> files) {
        // Authoritative: the user asked to revert the turn, so a buffer still
        // holding the reverted-away content is stale by definition. Overwrite it
        // rather than routing through the external-change check, which would
        // *prompt* the user immediately after they already said "undo this" — and
        // whose consuming timestamp check would leave a declined buffer
        // permanently out of step with disk.
        java.util.Set<Path> open = new java.util.HashSet<>(openDocuments());
        for (Path f : files) {
            try {
                if (open.contains(f) && java.nio.file.Files.exists(f)) {
                    String disk = java.nio.file.Files.readString(f);
                    executor.execute(new SetContentCommand(f, disk));
                }
            } catch (Exception ignore) {
                // best-effort refresh; the file on disk is already reverted
            }
        }
    }

    @Override
    public void filesChangedOnDisk(java.util.List<Path> files) {
        // Advisory: the user did not ask for their buffers to be replaced, so this
        // must not clobber unsaved work the way reloadFiles deliberately does.
        // checkAllDocumentsForExternalChanges reloads clean buffers silently and
        // prompts (or defers) for dirty ones — the same path the Git panel uses
        // after a discard.
        //
        // It sweeps every open document rather than just `files`. That is wider
        // than needed but not wrong: it acts only on documents whose file actually
        // changed on disk, and picking up an unrelated external edit is the same
        // thing the window-focus check would have done anyway.
        javax.swing.SwingUtilities.invokeLater(() ->
                ((DefaultPluginContext) ctx).getEditor().checkAllDocumentsForExternalChanges());
    }

    /** The current editor selection text, or null if nothing is selected. */
    private String selection() {
        try {
            return executor.execute(new GetSelectionCommand());
        } catch (Exception e) {
            return null;   // no active editor / nothing selected
        }
    }

    @Override
    public List<AgentTool> editorTools() {
        return editorTools;
    }

    @Override
    public Window dialogParent() {
        return dialogParent;
    }

    /** The active document's file path when it is a local file, else null. */
    private Path activeFile() {
        try {
            DogsBayDocument doc = ctx.getDocumentManager().getActiveDocument();
            if (doc == null) {
                return null;
            }
            URL url = doc.getURL();
            if (url != null && "file".equals(url.getProtocol())) {
                return Path.of(url.toURI());
            }
        } catch (Exception ignore) {
            // unresolved / non-file document → no active file context
        }
        return null;
    }
}
