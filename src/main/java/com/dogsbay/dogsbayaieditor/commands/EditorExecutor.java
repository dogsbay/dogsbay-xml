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

package com.dogsbay.dogsbayaieditor.commands;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.SessionContext;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;

import java.awt.event.ActionEvent;

import javax.swing.Action;
import javax.swing.SwingUtilities;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.DogsBayURLUtilities;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.commands.results.*;
import com.dogsbay.dogsbayaieditor.project.ProjectProperties;
import com.dogsbay.dogsbayaieditor.ExplorerContainer;

/**
 * Executes commands against a running editor instance.
 * Headless commands delegate to {@link HeadlessExecutor}.
 * Editor commands marshal to the EDT when needed.
 */
public class EditorExecutor implements CommandExecutor {

    private final DogsBayAIEditor editor;
    private final HeadlessExecutor headless;

    public EditorExecutor(DogsBayAIEditor editor) {
        this.editor = editor;
        this.headless = new HeadlessExecutor();
    }

    /**
     * Runs the command as whatever session is bound on this thread. Session
     * binding, gating and auditing are the {@link SessionExecutor}'s job; this
     * class only knows how to carry the bound session across the EDT hop.
     */
    @Override
    public <R> R execute(Command<R> command) throws CommandException {
        return dispatch(command);
    }

    @SuppressWarnings("unchecked")
    private <R> R dispatch(Command<R> command) throws CommandException {
        return (R) switch (command) {
            // Headless commands delegate
            case ValidateCommand c -> headless.execute(c);
            case ParseCommand c -> headless.execute(c);
            case TransformCommand c -> headless.execute(c);
            case QueryCommand c -> headless.execute(c);
            case FormatCommand c -> headless.execute(c);
            case ReflowCommand c -> headless.execute(c);
            case InfoCommand c -> headless.execute(c);
            case WhereUsedCommand c -> headless.execute(c);
            case ListKeysCommand c -> headless.execute(c);
            case ResolveKeyCommand c -> headless.execute(c);
            case CheckLinksCommand c -> headless.execute(c);
            case CheckOutputLinksCommand c -> headless.execute(c);
            case RenderPreviewCommand c -> headless.execute(c);
            case RenderReportCommand c -> headless.execute(c);
            case ProjectGraphCommand c -> headless.execute(c);
            case HealthCommand c -> headless.execute(c);
            case ValidateProjectCommand c -> headless.execute(c);
            case ProjectHealthCommand c -> headless.execute(c);
            case ConrefAuditCommand c -> headless.execute(c);
            case SchematronProjectCommand c -> headless.execute(c);
            case SchematronCommand c -> headless.execute(c);
            case ValidateDeliverablesCommand c -> headless.execute(c);
            // The editor knows its DITA-OT; an agent's command arrives without one.
            case ValidateDeepCommand c -> headless.execute(blank(c.ditaOtHome())
                    ? new ValidateDeepCommand(c.root(), c.deliverable(), c.map(), editorDitaOt(c.root())) : c);
            case CheckProjectCommand c -> headless.execute(blank(c.ditaOtHome())
                    ? new CheckProjectCommand(c.root(), c.rootMap(), c.schematron(), c.deliverable(),
                            c.outputBaseDir(), editorDitaOt(c.root()), c.build())
                    : c);
            case BuildDeliverablesCommand c -> headless.execute(blank(c.ditaOtHome())
                    ? new BuildDeliverablesCommand(c.root(), c.outputBaseDir(), c.deliverable(), editorDitaOt(c.root()),
                            c.deliverableNames(), c.keepTemp(), c.transtype(), c.changedPaths())
                    : c);
            case ValidateConditionsCommand c -> headless.execute(c);
            case ListSubjectsCommand c -> headless.execute(c);
            case MetadataAuditCommand c -> headless.execute(c);
            case ExportMetadataSchematronCommand c -> headless.execute(c);
            case MetadataSetCommand c -> headless.execute(c);
            case SetAttributeCommand c -> headless.execute(c);
            case EditMapCommand c -> headless.execute(c);
            case ReltableAuditCommand c -> headless.execute(c);
            case EditReltableCommand c -> headless.execute(c);
            case KeywordAuditCommand c -> headless.execute(c);
            case IndexAuditCommand c -> headless.execute(c);
            case GlossaryAuditCommand c -> headless.execute(c);
            case ConrefPushAuditCommand c -> headless.execute(c);
            case ChunkAuditCommand c -> headless.execute(c);
            case SpecializationInfoCommand c -> headless.execute(c);
            case ListBranchesCommand c -> headless.execute(c);

            // Review: the open buffer when the file is open, else the file on disk
            case ReviewListCommand c -> {
                String open = openBufferText(c.file());
                yield open != null ? ReviewCommands.list(open, c.author()) : headless.execute(c);
            }
            case ReviewAcceptCommand c -> reviewDecisionInEditor(c.file(), true, c.id(), c.author(), c.all(), c);
            case ReviewRejectCommand c -> reviewDecisionInEditor(c.file(), false, c.id(), c.author(), c.all(), c);
            case ReviewRevertCommand c -> {
                String open = openBufferText(c.file());
                Path path = Path.of(c.file());
                if (open == null) {
                    // a document not open (edited closed, or open and closed since) reverts on disk, exactly too
                    String onDisk = FileEdits.read(path);
                    String identity = SessionContext.current().identity();
                    ReviewCommands.Decision d = ReviewCommands.revert(onDisk,
                            PROPOSAL_SNAPSHOTS.restorable(identity, path, onDisk).orElse(null),
                            com.dogsbay.dogsbayaieditor.validate.DocumentValidator.isDitaFile(path)
                                    ? AgentProposalPolicy.validatorFor(path) : null);
                    if (!d.text().equals(onDisk)) {
                        FileEdits.writeIfUnchanged(path, onDisk, d.text(), this::isOpen);
                    }
                    PROPOSAL_SNAPSHOTS.forget(identity, path);
                    d.audit(new com.dogsbay.xml.review.ReviewAudit(editor::projectRootForAgents), path);
                    yield d.result(path);
                }
                String identity = SessionContext.current().identity();
                ReviewCommands.Decision d = ReviewCommands.revert(open,
                        PROPOSAL_SNAPSHOTS.restorable(identity, path, open).orElse(null),
                        com.dogsbay.dogsbayaieditor.validate.DocumentValidator.isDitaFile(path)
                                ? AgentProposalPolicy.validatorFor(path) : null);
                if (!d.text().equals(open)) {
                    spliceIntoOpenBuffer(c.file(), open, d.text());
                }
                PROPOSAL_SNAPSHOTS.forget(identity, path);
                d.audit(new com.dogsbay.xml.review.ReviewAudit(editor::projectRootForAgents), path);
                yield d.result(path);
            }
            case ReviewCommentCommand c -> {
                String open = openBufferText(c.file());
                if (open == null) {
                    yield headless.execute(c);
                }
                Path target = Path.of(c.file());
                String result = ReviewCommands.comment(open, c.afterText(), c.elementId(), c.text(),
                        java.time.Clock.systemUTC(), com.dogsbay.dogsbayaieditor.validate.DocumentValidator
                                .isDitaFile(target) ? AgentProposalPolicy.validatorFor(target) : null);
                spliceIntoOpenBuffer(c.file(), open, result);
                yield ReviewCommands.ok("Comment added to " + c.file());
            }
            case ListSessionsCommand c -> editor.getSessionRegistry().list().stream()
                    .map(x -> new com.dogsbay.dogsbayaieditor.commands.results.SessionInfo(x.id(), x.kind().name(),
                            x.displayName(), x.identity(), x.tier().name(), x.createdAt().toString()))
                    .toList();
            case ListAgentsCommand c -> editor.getAgentCatalog().entries().stream()
                    .map(e -> new com.dogsbay.dogsbayaieditor.commands.results.AgentEntryInfo(e.id(), e.name(),
                            e.version(), e.readiness().name(), e.launchCommand(), e.description()))
                    .toList();
            case AuditLogCommand c -> {
                String root = c.root();
                if (root == null || root.isBlank()) {
                    java.nio.file.Path open = editor.projectRootForAgents();
                    if (open == null) {
                        throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                                "No project is open; give a root");
                    }
                    root = open.toString();
                }
                yield headless.execute(new AuditLogCommand(root, c.identity(), c.command(), c.limit()));
            }
            case RenameFileCommand c -> headless.execute(c);
            case RenameKeyCommand c -> headless.execute(c);
            case DeleteFileCommand c -> headless.execute(c);
            case RetargetCommand c -> headless.execute(c);
            case KeyifyCommand c -> headless.execute(c);
            case InlineKeyCommand c -> headless.execute(c);
            case ExtractConrefCommand c -> headless.execute(c);
            case CreateKeydefCommand c -> headless.execute(c);
            case InlineConrefCommand c -> headless.execute(c);
            case RenameElementIdCommand c -> headless.execute(c);
            case MergeKeydefsCommand c -> headless.execute(c);
            case RenameProfileValueCommand c -> headless.execute(c);
            case SplitTopicCommand c -> headless.execute(c);

            // Editor commands
            case OpenCommand c -> executeOnEDT(() -> executeOpen(c));
            case CloseCommand c -> executeOnEDT(() -> executeClose(c));
            case SaveCommand c -> executeOnEDT(() -> executeSave(c));
            case ListDocumentsCommand c -> executeOnEDT(() -> executeListDocuments(c));
            case GetContentCommand c -> executeGetContent(c);
            case SetContentCommand c -> executeSetContentAgentAware(c);
            case EditDocumentCommand c -> executeEditDocument(c);
            case EditStructureCommand c -> executeEditStructure(c);
            case GetSelectionCommand c -> executeOnEDT(() -> executeGetSelection(c));
            case ReplaceSelectionCommand c -> executeReplaceSelectionAgentAware(c);
            case GotoLineCommand c -> executeOnEDT(() -> executeGotoLine(c));
            case GetCursorCommand c -> executeOnEDT(() -> executeGetCursor(c));
            case SetCursorCommand c -> executeOnEDT(() -> executeSetCursor(c));
            case SelectElementCommand c -> executeOnEDT(() -> executeSelectElement(c));
            case GetOutlineCommand c -> executeOnEDT(() -> executeGetOutline(c));
            case GetErrorsCommand c -> executeOnEDT(() -> executeGetErrors(c));

            // Author view commands
            case AuthorSwitchCommand c -> executeOnEDT(() -> executeAuthorSwitch(c));
            case AuthorOutlineCommand c -> executeOnEDT(() -> executeAuthorOutline(c));
            case AuthorInsertBlockCommand c -> executeOnEDT(() -> executeAuthorInsertBlock(c));
            case AuthorSetTextCommand c -> executeOnEDT(() -> executeAuthorSetText(c));
            case AuthorIssuesCommand c -> executeOnEDT(() -> executeAuthorIssues(c));
            case ScreenshotCommand c -> executeOnEDT(() -> executeScreenshot(c));
            case LivePreviewCommand c -> executeLivePreview(c);
            case LivePreviewStatusCommand c -> executeLivePreviewStatus(c);
            case SearchProjectCommand c -> executeSearch(c);
            case ListProjectFilesCommand c -> executeListFiles(c);

            // Project commands
            case CreateProjectCommand c -> executeOnEDT(() -> executeCreateProject(c));
            case OpenProjectCommand c -> executeOnEDT(() -> executeOpenProject(c));
            case ListProjectsCommand c -> executeOnEDT(() -> executeListProjects(c));
            case GetProjectCommand c -> executeOnEDT(() -> executeGetProject(c));

            // New document
            case NewDocumentCommand c -> executeOnEDT(() -> executeNewDocument(c));

            // Sidebar commands
            case ListSidebarsCommand c -> executeOnEDT(() -> executeListSidebars(c));
            case SwitchSidebarCommand c -> executeOnEDT(() -> executeSwitchSidebar(c));
            case OpenDitaMapCommand c -> executeOnEDT(() -> executeOpenDitaMap(c));
            case InvokeActionCommand c -> executeOnEDT(() -> executeInvokeAction(c));
            case ClickButtonCommand c -> executeOnEDT(() -> executeClickButton(c));
            case LocateCommand c -> executeOnEDT(() -> executeLocate(c));
            case LocateRangeCommand c -> executeOnEDT(() -> executeLocateRange(c));
            case LocateNodeCommand c -> executeOnEDT(() -> executeLocateNode(c));
            case RevealNodeCommand c -> executeOnEDT(() -> executeRevealNode(c));
            case RevealCommand c -> executeOnEDT(() -> executeReveal(c));
            case SelectPanelCommand c -> executeOnEDT(() -> executeSelectPanel(c));
            case TerminalRunCommand c -> executeTerminalRun(c);
            case WaitIdleCommand c -> executeWaitIdle(c);
            case TypeTextCommand c -> executeTypeText(c);
        };
    }

    /** The documents before each agent session's review proposals, for an exact review_revert. */
    private static final ProposalSnapshots PROPOSAL_SNAPSHOTS = new ProposalSnapshots();

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    // ── Live preview ────────────────────────────────────────────────────

    /**
     * Start or stop a deliverable's live preview; its window shows, without opening the browser. A preview
     * another process runs (the headless MCP server, a background preview from the command line) is not
     * started again, and can be stopped from here (PreviewCommands, the registry).
     */
    private com.dogsbay.dogsbayaieditor.commands.results.LivePreviewStatus executeLivePreview(LivePreviewCommand cmd)
            throws CommandException {
        String action = PreviewCommands.action(cmd.action());
        java.nio.file.Path root = projectRoot(cmd.root());
        if (action.equals("stop")) {
            // The running preview itself, not the project file: a preview must stay stoppable after its
            // deliverable is renamed or project.json is broken
            return PreviewCommands.stop(root, cmd.deliverable(), preview -> executeOnEDT(() -> {
                com.dogsbay.dogsbayaieditor.dita.live.LivePreviewWindow.close(preview);
                return null;
            }));
        }
        String ditaOt = editorDitaOt(cmd.root());
        com.dogsbay.dogsbayaieditor.ditaproject.Deliverable deliverable = previewTarget(root, cmd.deliverable(), ditaOt);
        return PreviewCommands.start(root, deliverable, ditaOt, (r, d, home) -> executeOnEDT(() ->
                com.dogsbay.dogsbayaieditor.dita.live.LivePreviewWindow.show(editor, r, d, home, false)),
                preview -> executeOnEDT(() -> {
                    com.dogsbay.dogsbayaieditor.dita.live.LivePreviewWindow.close(preview);
                    return null;
                }));
    }

    private java.util.List<com.dogsbay.dogsbayaieditor.commands.results.LivePreviewStatus> executeLivePreviewStatus(
            LivePreviewStatusCommand cmd) throws CommandException {
        return PreviewCommands.status(projectRoot(cmd.root()), cmd.deliverable());
    }

    private static java.nio.file.Path projectRoot(String root) throws CommandException {
        return PreviewCommands.root(root);
    }

    /** The deliverable a live preview command means: named, else the editor's active one for this project. */
    private com.dogsbay.dogsbayaieditor.ditaproject.Deliverable previewTarget(java.nio.file.Path root, String requested,
            String ditaOt) throws CommandException {
        String active = null;
        if (blank(requested)) {
            active = executeOnEDT(() -> {
                java.io.File open = editor.getFileExplorer() != null ? editor.getFileExplorer().getRootDirectory() : null;
                var svc = editor.getDeliverableService();
                return open != null && sameDirectory(open.toPath(), root) && svc != null && svc.getActiveDeliverable() != null
                        ? svc.getActiveDeliverable().name() : null;
            });
            if (active == null) {
                active = PreviewCommands.configuredDeliverable(root);
            }
        }
        return PreviewCommands.target(root, requested, active, ditaOt);
    }

    /**
     * The DITA-OT home the editor publishes with, when the command is for the project open in the
     * editor and that home is an installation; otherwise null, and the headless lookup reads the
     * command's own project. The editor's answer comes from the open folder and selected project,
     * so for another project it would be the wrong engine, or a stale path. Asked on the EDT
     * because it reads that state.
     */
    private String editorDitaOt(String root) {
        if (blank(root)) {
            return null;
        }
        try {
            return executeOnEDT(() -> {
                java.io.File open = editor.getFileExplorer() != null
                        ? editor.getFileExplorer().getRootDirectory() : null;
                if (open == null || !sameDirectory(open.toPath(), java.nio.file.Path.of(root))) {
                    return null;
                }
                String path = editor.getDitaOtPath();
                java.nio.file.Path home = DitaOtHome.safePath(path);
                return DitaOtHome.isHome(home) ? path : null;
            });
        } catch (CommandException | RuntimeException e) {
            return null;
        }
    }

    private static boolean sameDirectory(java.nio.file.Path a, java.nio.file.Path b) {
        try {
            return java.nio.file.Files.isSameFile(a, b);
        } catch (java.io.IOException e) {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        }
    }

    // ── EDT marshaling ──────────────────────────────────────────────────

    @FunctionalInterface
    private interface EdtCallable<T> {
        T call() throws Exception;
    }

    private <T> T executeOnEDT(EdtCallable<T> callable) throws CommandException {
        if (SwingUtilities.isEventDispatchThread()) {
            try {
                return callable.call();
            } catch (CommandException e) {
                throw e;
            } catch (Exception e) {
                throw new CommandException(
                    CommandException.ErrorCode.INTERNAL_ERROR, e.getMessage(), e
                );
            }
        }

        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> exception = new AtomicReference<>();
        // ThreadLocal state does not cross onto the EDT; carry the session over.
        AgentSession session = SessionContext.current();

        try {
            SwingUtilities.invokeAndWait(() -> SessionContext.run(session, () -> {
                try {
                    result.set(callable.call());
                } catch (Exception e) {
                    exception.set(e);
                }
            }));
        } catch (Exception e) {
            throw new CommandException(
                CommandException.ErrorCode.INTERNAL_ERROR,
                "EDT dispatch failed: " + e.getMessage(), e
            );
        }

        if (exception.get() != null) {
            if (exception.get() instanceof CommandException ce) {
                throw ce;
            }
            throw new CommandException(
                CommandException.ErrorCode.INTERNAL_ERROR,
                exception.get().getMessage(), exception.get()
            );
        }

        return result.get();
    }

    // ── Open ────────────────────────────────────────────────────────────

    private CommandResult executeOpen(OpenCommand cmd) throws Exception {
        URL url = DogsBayURLUtilities.getURLFromFile(cmd.file().toFile());
        editor.getDocumentManager().open(url, null, false);
        return CommandResult.ok("Opened " + cmd.file());
    }

    // ── Close ───────────────────────────────────────────────────────────

    private CommandResult executeClose(CloseCommand cmd) throws Exception {
        if (cmd.all()) {
            editor.getDocumentManager().closeAll();
            return CommandResult.ok("All documents closed");
        }

        DogsBayView view = resolveView(cmd.file());
        editor.close(view);
        return CommandResult.ok("Closed " + (cmd.file() != null ? cmd.file() : "active document"));
    }

    // ── Save ────────────────────────────────────────────────────────────

    /**
     * Save, having refused to lose anything. Every save on this path goes through here — a bare
     * {@code doc.save()} in this class is what {@code SaveLossGuardTest} fails on.
     */
    private static void saveGuarded(DogsBayDocument doc) throws Exception {
        SaveLossGuard.refuseIfSaveWouldLoseCharacters(doc);
        doc.save();
    }

    private CommandResult executeSave(SaveCommand cmd) throws Exception {
        if (cmd.all()) {
            Vector views = editor.getViews();
            // Two passes. Refusing inside one loop wrote the documents before the offender and left
            // the rest untouched, reporting only the offender's name — so a caller could not tell
            // what state the workspace was in. Check them all first, write nothing if any refuses.
            List<DogsBayDocument> targets = new ArrayList<>();
            for (int i = 0; i < views.size(); i++) {
                DogsBayView view = (DogsBayView) views.elementAt(i);
                DogsBayDocument doc = view.getDocument();
                if (doc.getURL() == null || doc.isReadOnly()) {
                    continue;   // not a save target, so it has no say in whether the batch proceeds
                }
                // Inside the guard, not outside it: updateModelOrThrow throws when a buffer is not
                // well-formed, and with the batch now all-or-nothing an unsaved scratch buffer or a
                // read-only file mid-edit would abort the whole save and write nothing — naming a tab
                // that was never going to be written.
                view.updateModelOrThrow();
                targets.add(doc);
            }
            SaveLossGuard.refuseIfAnyWouldLoseCharacters(targets);
            for (int i = 0; i < views.size(); i++) {
                DogsBayView view = (DogsBayView) views.elementAt(i);
                DogsBayDocument doc = view.getDocument();
                if (doc.getURL() != null && !doc.isReadOnly()) {
                    saveGuarded(doc);
                    view.getChangeManager().markSave();
                }
            }
            return CommandResult.ok("All documents saved");
        }

        DogsBayView view = resolveView(cmd.file());
        view.updateModelOrThrow();
        DogsBayDocument doc = view.getDocument();
        if (doc.getURL() != null) {
            saveGuarded(doc);
            editor.setDocument(doc);
            // Clear the undo history of the view we saved, not the active one:
            // `dogsbay-xml save --file other.xml` would otherwise mark whatever the
            // user is editing as clean, and it would then close without a prompt.
            view.getChangeManager().discardAllEdits();
            return CommandResult.ok("Saved " + doc.getName());
        }
        return CommandResult.fail("Document has no file path");
    }

    // ── List Documents ──────────────────────────────────────────────────

    private List<OpenDocument> executeListDocuments(ListDocumentsCommand cmd) throws Exception {
        List<OpenDocument> result = new ArrayList<>();
        Vector views = editor.getViews();
        DogsBayView activeView = editor.getView();

        for (int i = 0; i < views.size(); i++) {
            DogsBayView view = (DogsBayView) views.elementAt(i);
            DogsBayDocument doc = view.getDocument();

            Path filePath = null;
            if (doc.getURL() != null && doc.getURL().getProtocol().equals("file")) {
                filePath = Path.of(doc.getURL().toURI());
            }

            result.add(new OpenDocument(
                filePath,
                doc.getName(),
                view.isChanged(),
                null, // grammarType — could be extracted from grammar properties
                "editor", // viewType
                view == activeView
            ));
        }
        return result;
    }

    // ── Get Content ─────────────────────────────────────────────────────

    /** Not on the EDT itself: opening a document for a read takes two turns of the event queue. */
    private String executeGetContent(GetContentCommand cmd) throws CommandException {
        try {
            return executeOnEDT(() -> {
                DogsBayView view = resolveView(cmd.file());
                view.updateModel();
                return view.getDocument().getText();
            });
        } catch (CommandException e) {
            if (!cmd.readClosed() || cmd.file() == null
                    || e.getCode() != CommandException.ErrorCode.DOCUMENT_NOT_OPEN) {
                throw e;
            }
        }
        if (editor.getProperties().isAgentEditClosedFilesEnabled()) {
            return FileEdits.read(cmd.file()); // edits to it will go to the file as well
        }
        return openForReading(cmd.file());
    }

    /**
     * Open {@code file} for a read when agents may edit only open documents, and return its text, leaving
     * the writer's own tab selected: an agent reading a document to edit it should not take the writer away
     * from theirs. The editor adds a new document's tab later on the event queue ({@code createView}), so
     * the text is read, and the writer's tab selected again, on a second turn of the queue, from the view
     * the write gate will read.
     */
    private String openForReading(Path file) throws CommandException {
        FileEdits.read(file); // missing or unreadable: say so, rather than have the editor show a dialog
        DogsBayView active = executeOnEDT(() -> {
            DogsBayView before = editor.getView();
            executeOpen(new OpenCommand(file, null));
            return before;
        });
        return executeOnEDT(() -> {
            DogsBayView view = resolveView(file);
            view.updateModel();
            String text = view.getDocument().getText();
            if (active != null && active != view) {
                editor.select(active);
            }
            return text;
        });
    }

    // ── Set Content ─────────────────────────────────────────────────────

    /** The open buffer's text for {@code file}, or null when it is not open in the editor. */
    private String openBufferText(String file) throws CommandException {
        if (file == null) {
            return null;
        }
        try {
            Buffer buf = executeOnEDT(() -> snapshot(resolveView(Path.of(file))));
            return buf == null ? null : buf.text();
        } catch (CommandException e) {
            if (e.getCode() == CommandException.ErrorCode.DOCUMENT_NOT_OPEN
                    || e.getCode() == CommandException.ErrorCode.FILE_NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    /** Put {@code result} into the open buffer for {@code file} as one undo step, if it still reads {@code expected}. */
    private void spliceIntoOpenBuffer(String file, String expected, String result) throws CommandException {
        executeOnEDT(() -> {
            Buffer buf = snapshot(resolveView(Path.of(file)));
            if (buf == null) {
                throw new CommandException(CommandException.ErrorCode.DOCUMENT_NOT_OPEN, "No active editor panel");
            }
            spliceOnEdt(buf, expected, result);
            return null;
        });
    }

    /**
     * The one way a whole-document result reaches an open buffer: refuse if
     * the buffer moved since {@code expected} was read, splice only the span
     * that differs as one compound undo step, then persist so disk-reading
     * tools see it. Must run on the EDT.
     */
    private void spliceOnEdt(Buffer buf, String expected, String result) throws Exception {
        String now = buf.doc().getText(0, buf.doc().getLength());
        if (!now.equals(expected)) {
            throw new CommandException(CommandException.ErrorCode.CONFLICT,
                    "The document changed while the edit was being prepared; here is its current content, "
                    + "re-read it and apply your edit again:\n" + now);
        }
        var cm = buf.view().getChangeManager();
        if (cm != null) {
            cm.startCompound(true);
        }
        try {
            AgentProposalPolicy.replaceMinimal(buf.doc(), result);
        } finally {
            if (cm != null) {
                cm.endCompound();
            }
        }
        persist(buf.view());
    }

    private com.dogsbay.dogsbayaieditor.commands.results.ReviewResult reviewDecisionInEditor(String file,
            boolean accept, String id, String author, boolean all, Command<?> original) throws CommandException {
        String open = openBufferText(file);
        if (open == null) {
            return (com.dogsbay.dogsbayaieditor.commands.results.ReviewResult) headless.execute(original);
        }
        ReviewCommands.Decision d = ReviewCommands.decide(open, accept, id, author, all);
        spliceIntoOpenBuffer(file, open, d.text());
        Path path = Path.of(file);
        d.audit(new com.dogsbay.xml.review.ReviewAudit(editor::projectRootForAgents), path);
        return d.result(path);
    }

    /** A snapshot of the buffer taken on the EDT, so the proposal work can run off it. */
    private record Buffer(DogsBayView view, javax.swing.text.Document doc, String text, int selStart, int selEnd,
            Path file) {}

    private Buffer snapshot(DogsBayView view) throws Exception {
        var editorPanel = view.getEditor().getSelectedEditorPanel();
        if (editorPanel == null) {
            return null;
        }
        var pane = editorPanel.getEditor();
        var doc = pane.getDocument();
        Path file = null;
        DogsBayDocument d = view.getDocument();
        if (d.getURL() != null && "file".equals(d.getURL().getProtocol())) {
            file = Path.of(d.getURL().toURI());
        }
        return new Buffer(view, doc, doc.getText(0, doc.getLength()),
                Math.min(pane.getSelectionStart(), pane.getSelectionEnd()),
                Math.max(pane.getSelectionStart(), pane.getSelectionEnd()), file);
    }

    /**
     * The file for an edit to a document that is not open, or null when it is open (or no file was named).
     * The write gate decides whether an agent may edit a file that is not open.
     */
    /** Whether {@code file} is open in the editor, in any view (a document open only in the Author view counts). */
    private boolean isOpen(Path file) {
        try {
            return executeOnEDT(() -> {
                resolveView(file);
                return true;
            });
        } catch (CommandException e) {
            return e.getCode() != CommandException.ErrorCode.DOCUMENT_NOT_OPEN; // in doubt, not a file to write
        }
    }

    private Path closedFile(Path file) throws CommandException {
        if (file == null || isOpen(file)) {
            return null;
        }
        if (!java.nio.file.Files.isRegularFile(file)) {
            throw new CommandException(CommandException.ErrorCode.FILE_NOT_FOUND, "No such file: " + file);
        }
        return file;
    }

    /** edit_document whose replacement is the text it found: nothing to write, and it says so. */
    static CommandResult unchanged(int occurrences) {
        return CommandResult.ok("Nothing changed: the replacement is the same as the text found (" + occurrences
                + " occurrence" + (occurrences == 1 ? "" : "s") + ")");
    }

    /** A result with how many occurrences an edit_document replaced. */
    static CommandResult withOccurrences(CommandResult result, int occurrences) {
        return result.success()
                ? CommandResult.ok(result.message() + " (" + occurrences + " occurrence"
                        + (occurrences == 1 ? "" : "s") + " replaced)")
                : result;
    }

    private CommandResult executeSetContentAgentAware(SetContentCommand cmd) throws CommandException {
        Path closed = closedFile(cmd.file());
        if (closed != null) {
            return FileEdits.writeAgentAware(closed, FileEdits.read(closed), cmd.content(),
                    editor.getProperties().isAgentProposalsEnabled(), PROPOSAL_SNAPSHOTS, this::isOpen);
        }
        Buffer buf = executeOnEDT(() -> snapshot(resolveView(cmd.file())));
        if (buf == null) {
            return CommandResult.fail("No active editor panel");
        }
        return writeAgentAware(buf, cmd.content());
    }

    /**
     * Ordered structural steps on a document: in its buffer when it is open, else in the file. An agent's
     * steps land as proposals, recorded so that review_revert can restore the document exactly.
     */
    private CommandResult executeEditStructure(EditStructureCommand cmd) throws CommandException {
        if (cmd.file() == null) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, "file is required");
        }
        AgentSession session = SessionContext.current();
        boolean dryRun = cmd.dryRun();
        boolean proposals = editor.getProperties().isAgentProposalsEnabled();
        // One look at the editor: the document's buffer when it is open, else the file (the write gate decides
        // whether an agent may edit a file that is not open)
        Buffer buf = !isOpen(cmd.file()) ? null : executeOnEDT(() -> snapshot(resolveView(cmd.file())));
        if (buf == null && isOpen(cmd.file())) {
            return CommandResult.fail("No active editor panel");
        }
        Path file = buf != null && buf.file() != null ? buf.file() : cmd.file();
        String baseline = buf != null ? buf.text() : FileEdits.read(cmd.file());
        StructuralEditPolicy.Outcome outcome = StructuralEditPolicy.decide(session, file, baseline, cmd.steps(),
                proposals, AgentProposalPolicy.validatorFor(file));
        String message = StructuralEditPolicy.message(outcome, dryRun, session == null ? null : session.identity());
        if (dryRun) {
            return CommandResult.ok(message);
        }
        if (buf != null) {
            Buffer b = buf;
            executeOnEDT(() -> {
                spliceOnEdt(b, b.text(), outcome.text());
                return null;
            });
        } else {
            FileEdits.writeIfUnchanged(cmd.file(), baseline, outcome.text(), this::isOpen);
        }
        if (session != null && session.isAgent()) {
            // after the write, so a refused write leaves the record as it was
            PROPOSAL_SNAPSHOTS.recordWrite(session.identity(), file, baseline, outcome.text(),
                    !com.dogsbay.xml.review.ProposalIndex.scan(baseline, session.identity()).isEmpty());
        }
        return CommandResult.ok(message);
    }

    /** Replace one exact piece of a document, leaving the rest of the file as it is. */
    private CommandResult executeEditDocument(EditDocumentCommand cmd) throws CommandException {
        Path closed = closedFile(cmd.file());
        Buffer buf = closed != null ? null : executeOnEDT(() -> snapshot(resolveView(cmd.file())));
        if (closed == null && buf == null) {
            return CommandResult.fail("No active editor panel");
        }
        String text = closed != null ? FileEdits.read(closed) : buf.text();
        TextEdit.Result edit = TextEdit.apply(text, cmd.find(), cmd.replace() == null ? "" : cmd.replace(),
                Boolean.TRUE.equals(cmd.all()));
        if (!edit.applied()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, edit.error());
        }
        if (edit.text().equals(text)) {
            return unchanged(edit.occurrences());
        }
        CommandResult result = closed != null
                ? FileEdits.writeAgentAware(closed, text, edit.text(), editor.getProperties().isAgentProposalsEnabled(),
                        PROPOSAL_SNAPSHOTS, this::isOpen)
                : writeAgentAware(buf, edit.text());
        return withOccurrences(result, edit.occurrences());
    }

    private CommandResult executeReplaceSelectionAgentAware(ReplaceSelectionCommand cmd) throws CommandException {
        Buffer buf = executeOnEDT(() -> {
            DogsBayView view = editor.getView();
            if (view == null) {
                throw new CommandException(CommandException.ErrorCode.DOCUMENT_NOT_OPEN, "No document is open");
            }
            return snapshot(view);
        });
        if (buf == null || !SessionContext.current().isAgent()) {
            return executeOnEDT(() -> {
                DogsBayView view = editor.getView();
                view.getEditor().replaceSelection(cmd.text());
                persist(view);   // keep disk in step with the buffer (see writeAgentAware)
                return CommandResult.ok("Selection replaced");
            });
        }
        // An agent's replacement: build the whole new text so the delta can land as proposals.
        String modified = buf.text().substring(0, buf.selStart()) + cmd.text() + buf.text().substring(buf.selEnd());
        return writeAgentAware(buf, modified);
    }

    /**
     * Put {@code modified} into the document. For an agent session writing a
     * DITA document, the delta lands as marked proposals for the writer to
     * review (see {@link AgentProposalPolicy}); for the user, or where marks
     * are impossible, the text lands as given. The diff and validation run
     * on the calling thread; only the splice, one compound undo step, and the
     * save touch the EDT. A write that would erase another author's proposals
     * is refused as a conflict, with the current content, like any stale edit.
     */
    private CommandResult writeAgentAware(Buffer buf, String modified) throws CommandException {
        AgentSession session = SessionContext.current();
        AgentProposalPolicy.Outcome outcome = new AgentProposalPolicy().decide(session, buf.file(),
                buf.text(), modified, editor.getProperties().isAgentProposalsEnabled(),
                buf.file() == null ? c -> true : AgentProposalPolicy.validatorFor(buf.file()));
        if (outcome.refused()) {
            throw new CommandException(CommandException.ErrorCode.CONFLICT,
                    outcome.note() + "\nCurrent content:\n" + buf.text());
        }
        CommandResult result = executeOnEDT(() -> {
            spliceOnEdt(buf, buf.text(), outcome.text());
            return CommandResult.ok(outcome.asProposals() ? "Content updated: " + outcome.note()
                    : outcome.note() != null ? "Content updated (" + outcome.note() + ")" : "Content updated");
        });
        if (session != null && session.isAgent() && buf.file() != null) {
            // so that review_revert can restore the document exactly (ProposalSnapshots); after the write, so a
            // refused write leaves the record as it was
            PROPOSAL_SNAPSHOTS.recordWrite(session.identity(), buf.file(), buf.text(), outcome.text(),
                    !com.dogsbay.xml.review.ProposalIndex.scan(buf.text(), session.identity()).isEmpty());
        }
        return result;
    }

    /**
     * Sync the editor buffer into the document model and save it to disk. Edits
     * made through the agent (set content / replace selection) update the Swing
     * buffer; persisting keeps the model and the file on disk in step so that
     * disk-reading tools — {@code validate_document}, {@code parse}, {@code info}
     * — observe the edit instead of the stale original (which otherwise traps the
     * agent in an edit→re-validate→unchanged loop). The editor saves it itself,
     * so no "changed by another process" prompt appears.
     */
    private void persist(DogsBayView view) throws Exception {
        view.updateModel();
        if (view.getLastModelUpdateError() != null) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "The view's edits could not be applied to the document: "
                            + view.getLastModelUpdateError().getMessage());
        }
        DogsBayDocument doc = view.getDocument();
        if (doc.getURL() != null && !doc.isReadOnly()) {
            // The edit is already in the buffer by the time this runs — every caller applies its
            // change and closes the undo step first. So a refusal here leaves the buffer and the
            // file disagreeing, and the caller has to be told that, or it reads the error as
            // "nothing happened" and moves on with get_document_content and validate_document
            // giving different answers.
            try {
                SaveLossGuard.refuseIfSaveWouldLoseCharacters(doc);
            } catch (CommandException refused) {
                throw new CommandException(CommandException.ErrorCode.ENCODING_LOSS,
                        refused.getMessage()
                                + " The edit is in the editor's buffer but NOT on disk — re-read the "
                                + "document before deciding, or undo the edit.",
                        refused);
            }
            saveGuarded(doc);
            view.getChangeManager().markSave();
        }
    }

    // ── Get Selection ────────────────────────────────────────────────────

    private String executeGetSelection(GetSelectionCommand cmd) throws Exception {
        DogsBayView view = editor.getView();
        if (view == null) {
            throw new CommandException(
                CommandException.ErrorCode.DOCUMENT_NOT_OPEN, "No document is open"
            );
        }
        return view.getEditor().getSelectedText();
    }

    // ── Replace Selection ───────────────────────────────────────────────

    // ── Caret and element selection ─────────────────────────────────────
    //
    // These four were reachable only from the in-editor JavaScript console
    // (exchanger.gotoLine / getCursorPosition / setCursorPosition /
    // moveCursorPosition / selectElement / selectElementContent). They are the
    // one capability the scripting API had that the command engine did not, so
    // they broke the CLI/API parity the engine exists to provide. Ported here so
    // they work from all three transports; see plans/remove-rhino-scripting.md.
    //
    // None of these change the document, so unlike the edit commands above they
    // deliberately do NOT call persist().

    /** The active view, or a DOCUMENT_NOT_OPEN failure. */
    private DogsBayView requireView() throws Exception {
        DogsBayView view = editor.getView();
        if (view == null) {
            throw new CommandException(
                CommandException.ErrorCode.DOCUMENT_NOT_OPEN, "No document is open"
            );
        }
        return view;
    }

    private CommandResult executeGotoLine(GotoLineCommand cmd) throws Exception {
        DogsBayView view = requireView();
        javax.swing.text.Document doc = view.getEditor().getEditor().getDocument();
        int lineCount = doc.getDefaultRootElement().getElementCount();
        // Reject rather than clamp: a caller asking for line 9000 of a 40-line
        // file has a bug, and silently parking the caret at the end would hide it.
        if (cmd.line() < 1 || cmd.line() > lineCount) {
            throw new CommandException(
                CommandException.ErrorCode.INVALID_ARGUMENT,
                "Line " + cmd.line() + " is out of range (document has "
                    + lineCount + " lines)"
            );
        }
        view.getEditor().gotoLine(cmd.line());
        return CommandResult.ok("Moved to line " + cmd.line());
    }

    private CursorPosition executeGetCursor(GetCursorCommand cmd) throws Exception {
        DogsBayView view = requireView();
        int offset = view.getEditor().getCursorPosition();
        return cursorPositionAt(view, offset);
    }

    private CommandResult executeSetCursor(SetCursorCommand cmd) throws Exception {
        DogsBayView view = requireView();
        int target = cmd.relative()
            ? view.getEditor().getCursorPosition() + cmd.position()
            : cmd.position();
        // Editor.setCursorPosition clamps to the document, so a delta that runs
        // off either end lands at the boundary instead of throwing.
        view.getEditor().setCursorPosition(target);
        CursorPosition now = cursorPositionAt(view, view.getEditor().getCursorPosition());
        return CommandResult.ok(
            "Cursor at offset " + now.offset() + " (line " + now.line()
                + ", column " + now.column() + ")"
        );
    }

    private CommandResult executeSelectElement(SelectElementCommand cmd) throws Exception {
        DogsBayView view = requireView();
        if (cmd.contentOnly()) {
            view.getEditor().selectElementContent();
        } else {
            view.getEditor().selectElement();
        }
        String selected = view.getEditor().getSelectedText();
        if (selected == null || selected.isEmpty()) {
            // The caret was not inside an element the scanner recognises.
            throw new CommandException(
                CommandException.ErrorCode.INVALID_ARGUMENT,
                "No element at the cursor position"
            );
        }
        return CommandResult.ok(
            "Selected " + selected.length() + " characters"
        );
    }

    /** Convert a character offset to 1-based line and column. */
    private CursorPosition cursorPositionAt(DogsBayView view, int offset) {
        return CaretPositions.at(
            view.getEditor().getEditor().getDocument().getDefaultRootElement(), offset);
    }

    // ── Author view ─────────────────────────────────────────────────────

    /** Resolves the view's AuthorView with a fresh block model. */
    private com.dogsbay.dogsbayaieditor.author.AuthorView resolveAuthorView(java.nio.file.Path file)
            throws Exception {
        DogsBayView view = resolveView(file);
        view.updateModel();
        if (view.getLastModelUpdateError() != null) {
            // The text buffer is not well-formed right now: the block model would be
            // stale, and pushing an edit through it would overwrite what the user typed.
            throw new CommandException(CommandException.ErrorCode.PARSE_ERROR,
                    "The document's text is not well-formed, so the Author view is out of date: "
                            + view.getLastModelUpdateError().getMessage());
        }
        com.dogsbay.dogsbayaieditor.author.AuthorView author = view.getAuthorView();
        if (!author.hasLatestInformation()) {
            author.setDocument(view.getDocument());
        }
        return author;
    }

    private CommandResult executeAuthorSwitch(AuthorSwitchCommand cmd) throws Exception {
        DogsBayView view = resolveView(cmd.file());
        if (Boolean.TRUE.equals(cmd.split())) {
            view.switchToAuthorSplit();
            return CommandResult.ok("Author split active");
        }
        view.switchToAuthor();
        return CommandResult.ok("Author view active");
    }

    private com.dogsbay.dogsbayaieditor.commands.results.AuthorBlockNode executeAuthorOutline(
            AuthorOutlineCommand cmd) throws Exception {
        com.dogsbay.dogsbayaieditor.author.AuthorView author = resolveAuthorView(cmd.file());
        com.dogsbay.xml.author.model.AuthorDocument doc =
                author.getEditorPanel().getAuthorDocument();
        if (doc == null || doc.getRoot() == null) {
            throw new CommandException(CommandException.ErrorCode.PARSE_ERROR,
                    "Document is not available in the Author view (not well-formed?)");
        }
        return com.dogsbay.dogsbayaieditor.commands.results.AuthorBlockNode.fromBlock(doc.getRoot());
    }

    private CommandResult executeAuthorInsertBlock(AuthorInsertBlockCommand cmd) throws Exception {
        DogsBayView view = resolveView(cmd.file());
        com.dogsbay.dogsbayaieditor.author.AuthorView author = resolveAuthorView(cmd.file());
        int index = cmd.index() == null ? -1 : cmd.index();
        String focusId = author.getEditorPanel().insertBlockById(cmd.type(), cmd.parentId(), index);
        if (focusId == null) {
            return CommandResult.fail("Cannot insert '" + cmd.type() + "' under block "
                    + cmd.parentId() + " (unknown block or invalid position)");
        }
        commitAuthorEdit(view, author);
        return CommandResult.ok(focusId);
    }

    private CommandResult executeAuthorSetText(AuthorSetTextCommand cmd) throws Exception {
        DogsBayView view = resolveView(cmd.file());
        com.dogsbay.dogsbayaieditor.author.AuthorView author = resolveAuthorView(cmd.file());
        if (!author.getEditorPanel().setBlockTextById(cmd.blockId(), cmd.text())) {
            return CommandResult.fail("Block " + cmd.blockId() + " is unknown or carries no text");
        }
        commitAuthorEdit(view, author);
        return CommandResult.ok("Text set on " + cmd.blockId());
    }

    /**
     * An Author-view edit made by a command reaches the document, the text pane
     * and the disk, like the other edit commands: the Editor view otherwise
     * keeps showing the old text and its next keystroke writes it back over
     * the edit, and disk-reading tools would see a stale file.
     */
    private void commitAuthorEdit(DogsBayView view, com.dogsbay.dogsbayaieditor.author.AuthorView author)
            throws Exception {
        author.applyToDocument();
        executeOnEDT(() -> {
            view.refreshCurrentViewFromModel();   // Editor, split or plugin view: whatever is showing
            return null;
        });
        persist(view);
    }

    private java.util.List<com.dogsbay.dogsbayaieditor.commands.results.AuthorIssueInfo>
            executeAuthorIssues(AuthorIssuesCommand cmd) throws Exception {
        com.dogsbay.dogsbayaieditor.author.AuthorView author = resolveAuthorView(cmd.file());
        java.util.List<com.dogsbay.dogsbayaieditor.commands.results.AuthorIssueInfo> out =
                new ArrayList<>();
        for (var issue : author.getEditorPanel().getValidationIssues()) {
            out.add(com.dogsbay.dogsbayaieditor.commands.results.AuthorIssueInfo.fromIssue(issue));
        }
        return out;
    }

    private CommandResult executeScreenshot(ScreenshotCommand cmd) throws Exception {
        javax.swing.JRootPane root = editor.getRootPane();
        if (root.getWidth() <= 0 || root.getHeight() <= 0) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Editor window has no size to paint");
        }
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                root.getWidth(), root.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        root.paint(g);
        g.dispose();
        java.io.File out = new java.io.File(cmd.output());
        javax.imageio.ImageIO.write(image, "png", out);
        return CommandResult.ok(out.getAbsolutePath());
    }

    // ── Get Outline ─────────────────────────────────────────────────────

    private List<OutlineNode> executeGetOutline(GetOutlineCommand cmd) throws Exception {
        DogsBayView view = resolveView(cmd.file());
        view.updateModel();
        DogsBayDocument doc = view.getDocument();

        // Build outline from DOM
        List<OutlineNode> nodes = new ArrayList<>();
        org.dom4j.Element root = doc.getRoot();
        if (root != null) {
            nodes.add(buildOutlineNode(root));
        }
        return nodes;
    }

    private OutlineNode buildOutlineNode(org.dom4j.Element element) {
        List<OutlineNode> children = new ArrayList<>();
        for (Object child : element.elements()) {
            children.add(buildOutlineNode((org.dom4j.Element) child));
        }

        // Build attribute summary
        StringBuilder attrs = new StringBuilder();
        for (Object attr : element.attributes()) {
            org.dom4j.Attribute a = (org.dom4j.Attribute) attr;
            if (attrs.length() > 0) attrs.append(", ");
            attrs.append(a.getName()).append("=").append(a.getValue());
        }

        // Approximate line number (dom4j doesn't track line numbers by default)
        return new OutlineNode(
            element.getName(),
            "element",
            -1, -1,
            attrs.length() > 0 ? attrs.toString() : null,
            children
        );
    }

    // ── Get Errors ──────────────────────────────────────────────────────

    private List<ValidationError> executeGetErrors(GetErrorsCommand cmd) throws Exception {
        com.dogsbay.dogsbayaieditor.OutputPanel out = editor.getOutputPanel();
        if (out == null || out.getErrorPane() == null) {
            return List.of();
        }
        java.io.File want = null;
        if (cmd.file() != null) {
            try {
                want = cmd.file().toFile().getCanonicalFile();
            } catch (Exception ex) {
                want = cmd.file().toAbsolutePath().normalize().toFile();
            }
        }
        List<ValidationError> result = new ArrayList<>();
        for (com.dogsbay.xml.XMLError e : out.getErrorPane().currentErrors()) {
            if (want != null) {
                java.io.File got = systemIdToFile(e.getSystemId());
                if (got == null || !got.equals(want)) {
                    continue;   // canonical-path match, not basename suffix
                }
            }
            result.add(new ValidationError(
                    e.getLineNumber(), e.getColumnNumber(),
                    e.getType() == com.dogsbay.xml.XMLError.WARNING ? "warning" : "error",
                    e.getMessage(),
                    e.getSystemId() != null ? e.getSystemId() : "editor"));
        }
        return result;
    }

    /** A systemId (file: URI or plain path) as a canonical File, or null. */
    private static java.io.File systemIdToFile(String systemId) {
        if (systemId == null) {
            return null;
        }
        try {
            java.net.URI uri = new java.net.URI(systemId);
            java.io.File f = uri.isAbsolute() ? new java.io.File(uri) : new java.io.File(systemId);
            return f.getCanonicalFile();
        } catch (Exception e) {
            try {
                return new java.io.File(systemId).getCanonicalFile();
            } catch (Exception e2) {
                return null;
            }
        }
    }

    // ── Search Project ──────────────────────────────────────────────────

    /**
     * Root for project-wide file operations (search, list files): the opened
     * folder in the file explorer, not the editor's launch directory. Resolving
     * against {@code Path.of(".")} previously walked the editor's own repo and
     * returned its files — sending an agent off to edit the wrong project.
     */
    private Path projectRoot() {
        try {
            if (editor.getFileExplorer() != null) {
                java.io.File root = editor.getFileExplorer().getRootDirectory();
                if (root != null && root.isDirectory()) {
                    return root.toPath();
                }
            }
        } catch (Exception ignore) {
            // explorer not ready → fall back to the process directory
        }
        return Path.of(".").toAbsolutePath().normalize();
    }

    /**
     * Glob predicate over project-relative paths. Java's {@code **&#47;} requires a
     * leading directory, so {@code **&#47;shared/x} won't match a root-level
     * {@code shared/x}; we also accept the pattern with a leading {@code **&#47;}
     * stripped so root-level entries match too.
     */
    private static java.util.function.Predicate<Path> globFilter(Path base, String glob) {
        var fs = java.nio.file.FileSystems.getDefault();
        java.nio.file.PathMatcher primary = fs.getPathMatcher("glob:" + glob);
        java.nio.file.PathMatcher rootLevel =
            glob.startsWith("**/") ? fs.getPathMatcher("glob:" + glob.substring(3)) : null;
        return p -> {
            Path rel = base.relativize(p);
            return primary.matches(rel) || (rootLevel != null && rootLevel.matches(rel));
        };
    }

    private List<SearchMatch> executeSearch(SearchProjectCommand cmd) throws CommandException {
        // Search runs off-EDT (file I/O only)
        // Delegate to a simple grep implementation
        try {
            List<SearchMatch> results = new ArrayList<>();
            Path base = projectRoot();

            java.util.function.Predicate<Path> matcher = cmd.globPattern() != null
                ? globFilter(base, cmd.globPattern())
                : null;

            java.util.regex.Pattern pattern = cmd.regex()
                ? java.util.regex.Pattern.compile(cmd.query(),
                    cmd.caseSensitive() ? 0 : java.util.regex.Pattern.CASE_INSENSITIVE)
                : java.util.regex.Pattern.compile(
                    java.util.regex.Pattern.quote(cmd.query()),
                    cmd.caseSensitive() ? 0 : java.util.regex.Pattern.CASE_INSENSITIVE);

            int limit = cmd.maxResults() > 0 ? cmd.maxResults() : Integer.MAX_VALUE;

            try (var walk = java.nio.file.Files.walk(base, 20)) {
                var files = walk.filter(java.nio.file.Files::isRegularFile);
                if (matcher != null) {
                    files = files.filter(matcher);
                }

                for (Path file : (Iterable<Path>) files::iterator) {
                    if (results.size() >= limit) break;
                    try {
                        List<String> lines = java.nio.file.Files.readAllLines(file);
                        for (int i = 0; i < lines.size() && results.size() < limit; i++) {
                            var m = pattern.matcher(lines.get(i));
                            if (m.find()) {
                                results.add(new SearchMatch(
                                    file, i + 1, m.start() + 1,
                                    m.group(), lines.get(i)
                                ));
                            }
                        }
                    } catch (Exception e) {
                        // skip binary/unreadable files
                    }
                }
            }

            return results;
        } catch (Exception e) {
            throw new CommandException(
                CommandException.ErrorCode.INTERNAL_ERROR,
                "Search failed: " + e.getMessage(), e
            );
        }
    }

    // ── List Project Files ──────────────────────────────────────────────

    private List<Path> executeListFiles(ListProjectFilesCommand cmd) throws CommandException {
        try {
            Path base = projectRoot();
            java.util.function.Predicate<Path> matches = globFilter(base, cmd.globPattern());

            List<Path> results = new ArrayList<>();
            try (var walk = java.nio.file.Files.walk(base, 20)) {
                walk.filter(java.nio.file.Files::isRegularFile)
                    .filter(matches)
                    .forEach(results::add);
            }
            return results;
        } catch (Exception e) {
            throw new CommandException(
                CommandException.ErrorCode.INTERNAL_ERROR,
                "List files failed: " + e.getMessage(), e
            );
        }
    }

    // ── Create Project ────────────────────────────────────────────────

    private ProjectInfo executeCreateProject(CreateProjectCommand cmd) throws Exception {
        // Check for duplicate name
        Vector projects = editor.getProperties().getProjectProperties();
        for (int i = 0; i < projects.size(); i++) {
            ProjectProperties p = (ProjectProperties) projects.elementAt(i);
            if (p.getName().equals(cmd.name())) {
                throw new CommandException(
                    CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Project already exists: " + cmd.name()
                );
            }
        }

        // Create project properties
        ProjectProperties props = new ProjectProperties(cmd.name());
        props.setFolderPath(cmd.folderPath().toAbsolutePath().toString());

        if (cmd.projectType() != null) {
            String type = switch (cmd.projectType().toLowerCase()) {
                case "dita" -> ProjectProperties.TYPE_DITA;
                case "docbook" -> ProjectProperties.TYPE_DOCBOOK;
                default -> ProjectProperties.TYPE_NONE;
            };
            props.setProjectType(type);
        }

        if (cmd.defaultRootMap() != null) {
            props.setDefaultRootMap(cmd.defaultRootMap().toString());
        }
        String houseRules = checkedHouseRules(cmd);

        // Add to config and persist
        editor.getProperties().addProjectProperties(props);
        if (houseRules != null) {
            // House rules are the project's: its shared config, beside the root map, written in place and last
            com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig.setSharedElement(cmd.folderPath(),
                    "default-schematron", houseRules);
        }

        // Refresh project panel
        if (editor.getProjectPanel() != null) {
            editor.getProjectPanel().setProjects(editor.getProperties().getProjectProperties(), false);
        }

        // Switch to the new project
        if (editor.getProjectSwitcher() != null) {
            editor.getProjectSwitcher().setCurrentProject(props);
        }

        // Open folder in file explorer and switch to it
        java.io.File folder = cmd.folderPath().toFile();
        if (folder.exists() && folder.isDirectory()) {
            editor.switchToFileExplorerTab();
            if (editor.getFileExplorer() != null) {
                editor.getFileExplorer().setRootDirectory(folder);
            }
            if (editor.getGitPanel() != null) {
                editor.getGitPanel().refreshRepository();
            }
            // Save as last opened project/folder
            editor.getProperties().rememberOpenProject(folder.getAbsolutePath());
        }

        return new ProjectInfo(
            cmd.name(),
            cmd.folderPath().toAbsolutePath().toString(),
            cmd.projectType(),
            cmd.defaultRootMap() != null ? cmd.defaultRootMap().toString() : null,
            true
        );
    }

    /** The house rules create_project was given, when they name a Schematron file inside the project folder. */
    private static String checkedHouseRules(CreateProjectCommand cmd) throws CommandException {
        if (cmd.houseRules() == null || cmd.houseRules().isBlank()) {
            return null;
        }
        Path folder = cmd.folderPath().toAbsolutePath().normalize();
        Path rules = Path.of(cmd.houseRules());
        Path resolved = (rules.isAbsolute() ? rules : folder.resolve(rules)).normalize();
        if (!java.nio.file.Files.isDirectory(folder) || !resolved.startsWith(folder)
                || !java.nio.file.Files.isRegularFile(resolved)) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, "schematron must name an "
                    + "existing Schematron file inside the project folder " + folder + " (got " + cmd.houseRules() + ")");
        }
        return folder.relativize(resolved).toString().replace('\\', '/');
    }

    // ── Open Project ─────────────────────────────────────────────────

    private ProjectInfo executeOpenProject(OpenProjectCommand cmd) throws Exception {
        Vector projects = editor.getProperties().getProjectProperties();
        for (int i = 0; i < projects.size(); i++) {
            ProjectProperties p = (ProjectProperties) projects.elementAt(i);
            if (p.getName().equals(cmd.name())) {
                // Switch to it
                if (editor.getProjectSwitcher() != null) {
                    editor.getProjectSwitcher().setCurrentProject(p);
                }

                // Open folder in file explorer
                String folderPath = p.getFolderPath();
                if (folderPath != null && !folderPath.isEmpty()) {
                    java.io.File folder = new java.io.File(folderPath);
                    if (folder.exists() && folder.isDirectory()) {
                        editor.switchToFileExplorerTab();
                        if (editor.getFileExplorer() != null) {
                            editor.getFileExplorer().setRootDirectory(folder);
                        }
                        if (editor.getGitPanel() != null) {
                            editor.getGitPanel().refreshRepository();
                        }
                        // An agent switching projects must be what the next start reopens, name and folder
                        editor.getProperties().rememberOpenProject(p, folderPath);
                    }
                }

                // Select in project panel
                if (editor.getProjectPanel() != null) {
                    editor.getProjectPanel().selectProject(p.getName());
                }

                return toProjectInfo(p, true);
            }
        }

        throw new CommandException(
            CommandException.ErrorCode.INVALID_ARGUMENT,
            "Project not found: " + cmd.name()
        );
    }

    // ── List Projects ────────────────────────────────────────────────

    private List<ProjectInfo> executeListProjects(ListProjectsCommand cmd) throws Exception {
        Vector projects = editor.getProperties().getProjectProperties();
        ProjectProperties current = editor.getProjectSwitcher() != null
            ? editor.getProjectSwitcher().getCurrentProject() : null;

        List<ProjectInfo> result = new ArrayList<>();
        for (int i = 0; i < projects.size(); i++) {
            ProjectProperties p = (ProjectProperties) projects.elementAt(i);
            boolean active = current != null && current.getName().equals(p.getName());
            result.add(toProjectInfo(p, active));
        }
        return result;
    }

    // ── Get Project ──────────────────────────────────────────────────

    private ProjectInfo executeGetProject(GetProjectCommand cmd) throws Exception {
        ProjectProperties current = editor.getProjectSwitcher() != null
            ? editor.getProjectSwitcher().getCurrentProject() : null;

        if (current == null) {
            throw new CommandException(
                CommandException.ErrorCode.DOCUMENT_NOT_OPEN,
                "No project is currently active"
            );
        }

        return enrichedProjectInfo(current, true);
    }

    /** Lightweight workspace info (no resolved DITA project) — used by list/open/create. */
    private ProjectInfo toProjectInfo(ProjectProperties p, boolean active) {
        return new ProjectInfo(
            p.getName(),
            p.getFolderPath(),
            p.getProjectType(),
            p.getDefaultRootMap(),
            active
        );
    }

    /**
     * Workspace info enriched with the resolved DITA project — catalogs and
     * deliverables (map · ditaval · transtype) — so the agent gets the root
     * map(s) and conditions declaratively instead of hunting the filesystem.
     */
    private ProjectInfo enrichedProjectInfo(ProjectProperties p, boolean active) {
        com.dogsbay.dogsbayaieditor.ditaproject.ProjectContext ctx = null;
        String folder = p.getFolderPath();
        if (folder != null && !folder.isEmpty()) {
            try {
                java.nio.file.Path root = java.nio.file.Path.of(folder);
                java.util.List<java.nio.file.Path> catalogs = new java.util.ArrayList<>();
                java.nio.file.Path bundled =
                    com.dogsbay.dogsbayaieditor.validate.DocumentValidator.builtinDitaCatalog();
                if (bundled != null) {
                    catalogs.add(bundled);
                }
                java.nio.file.Path rootMap = null;
                String rm = p.getDefaultRootMap();
                if (rm != null && !rm.isEmpty()) {
                    java.nio.file.Path rmp = java.nio.file.Path.of(rm);
                    rootMap = rmp.isAbsolute() ? rmp : root.resolve(rm);
                }
                java.nio.file.Path ditaOt = null;
                String dop = p.getDitaOtPath();
                if (dop != null && !dop.isEmpty()) {
                    ditaOt = java.nio.file.Path.of(dop);
                }
                ctx = com.dogsbay.dogsbayaieditor.ditaproject.ProjectContextLoader
                        .load(root, rootMap, catalogs, ditaOt);
            } catch (Exception ignore) {
                ctx = null; // fall back to lightweight info
            }
        }
        return ProjectInfo.of(p.getName(), folder, p.getProjectType(),
                p.getDefaultRootMap(), active, ctx);
    }

    // ── New Document ──────────────────────────────────────────────────

    private CommandResult executeNewDocument(NewDocumentCommand cmd) throws Exception {
        java.io.File file = cmd.file().toAbsolutePath().toFile();

        if (file.exists()) {
            throw new CommandException(
                CommandException.ErrorCode.INVALID_ARGUMENT,
                "File already exists: " + file + ". Use open_document instead."
            );
        }

        // Create parent directories
        file.getParentFile().mkdirs();

        // Write content or create empty file
        if (cmd.content() != null) {
            java.nio.file.Files.writeString(cmd.file().toAbsolutePath(), cmd.content());
        } else {
            file.createNewFile();
        }

        // Open in editor
        URL url = DogsBayURLUtilities.getURLFromFile(file);
        editor.getDocumentManager().open(url, null, false);

        return CommandResult.ok("Created " + cmd.file().getFileName());
    }

    // ── List Sidebars ─────────────────────────────────────────────────

    private List<SidebarInfo> executeListSidebars(ListSidebarsCommand cmd) throws Exception {
        List<SidebarInfo> result = new ArrayList<>();

        ExplorerContainer left = editor.getExplorerContainer();
        if (left != null) {
            String selectedLeft = left.getSelectedId();
            for (String id : left.getExplorerIds()) {
                result.add(new SidebarInfo(id, left.getExplorerName(id), "left", id.equals(selectedLeft)));
            }
        }

        ExplorerContainer right = editor.getRightExplorerContainer();
        if (right != null) {
            String selectedRight = right.getSelectedId();
            for (String id : right.getExplorerIds()) {
                result.add(new SidebarInfo(id, right.getExplorerName(id), "right", id.equals(selectedRight)));
            }
        }

        return result;
    }

    // ── Switch Sidebar ───────────────────────────────────────────────

    private CommandResult executeSwitchSidebar(SwitchSidebarCommand cmd) throws Exception {
        ExplorerContainer container = "right".equals(cmd.side())
            ? editor.getRightExplorerContainer()
            : editor.getExplorerContainer();

        if (container == null) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR, "Sidebar not available");
        }

        if (!container.getExplorerIds().contains(cmd.id())) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                "Unknown sidebar: " + cmd.id() + ". Available: " + container.getExplorerIds());
        }

        if (container.isMinimized()) {
            container.setMinimized(false);
        }
        container.setSelectedExplorer(cmd.id());
        return CommandResult.ok("Switched to " + container.getExplorerName(cmd.id()));
    }

    // ── Menu commands ────────────────────────────────────────────────

    /**
     * Run a menu command by its id, on the event thread, as the menu itself would.
     *
     * <p>A disabled command is refused rather than quietly doing nothing: a caller that asked for Validate on a document
     * that cannot be validated yet needs to know that, not a success it can do nothing with.
     */
    private CommandResult executeInvokeAction(InvokeActionCommand cmd) throws Exception {
        if (!UiDriving.isAllowed()) {
            throw UiDriving.refused("Running a menu command");
        }
        Action action = findAction(cmd.id(), editor::getModeAction, editor::getModeActionIds);
        // Fired without waiting for it to finish. Many menu commands open a modal dialog, and a modal dialog runs its own
        // event loop: waiting would leave the caller hanging until a person dismissed it, which for an unattended driver
        // means forever. So the checks above are the answer, and the command is started rather than completed.
        SwingUtilities.invokeLater(() -> action.actionPerformed(
            new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, cmd.id())));
        return CommandResult.ok("Started " + cmd.id());
    }

    /**
     * Press a button in a dialog by its name, on the event thread, as a person clicking it does.
     *
     * <p>Started and not waited for, exactly as {@link #executeInvokeAction} is and for the same reason: OK on
     * one dialog opens the next, a modal dialog runs its own event loop, and waiting would leave an unattended
     * caller hanging until a person dismissed something. Everything that can be refused is checked first,
     * synchronously, so a caller that named the wrong button hears about it now.
     */
    private com.dogsbay.dogsbayaieditor.commands.results.ButtonHit executeClickButton(ClickButtonCommand cmd)
            throws Exception {
        // Only the press is driving. Finding a button changes nothing — it is the same look at the screen that
        // a screenshot already gives without permission — and a caller that means to move its own pointer there
        // and click is doing its own driving, by its own means.
        if (cmd.press() && !UiDriving.isAllowed()) {
            throw UiDriving.refused("Pressing a button");
        }
        if (blank(cmd.label())) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, "A button label is needed");
        }
        java.util.List<java.awt.Window> windows = java.util.Arrays.stream(java.awt.Window.getWindows())
                .filter(java.awt.Window::isShowing).toList();
        java.awt.Window active = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        java.util.List<DialogButtons.WindowInfo> described = windows.stream()
                .map(w -> new DialogButtons.WindowInfo(titleOf(w), true, w == active, w instanceof java.awt.Dialog))
                .toList();
        int chosen = DialogButtons.chooseWindow(described, cmd.window());
        if (chosen < 0) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    DialogButtons.noWindow(described, cmd.window()));
        }
        java.awt.Window window = windows.get(chosen);
        DialogButtons.Outcome outcome = DialogButtons.resolve(window, titleOf(window), cmd.label());
        if (outcome instanceof DialogButtons.Outcome.Refused refused) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, refused.message());
        }
        javax.swing.AbstractButton button = ((DialogButtons.Outcome.Found) outcome).button();
        java.awt.Point at = button.getLocationOnScreen();
        int width = button.getWidth();
        int height = button.getHeight();
        if (cmd.press()) {
            SwingUtilities.invokeLater(button::doClick);
        }
        return new com.dogsbay.dogsbayaieditor.commands.results.ButtonHit(
                button.getText(), titleOf(window), cmd.press(),
                at.x, at.y, width, height, at.x + width / 2, at.y + height / 2);
    }

    /**
     * Where something is on screen, found by the text on it.
     *
     * <p>Finds and measures; presses nothing, so it needs no permission to drive. The caller moves its own
     * pointer to the middle and clicks there, which is what keeps the pointer visible in a recording.
     */
    private com.dogsbay.dogsbayaieditor.commands.results.ElementHit executeLocate(LocateCommand cmd)
            throws Exception {
        UiTargets.Kind kind = UiTargets.Kind.of(cmd.what());
        if (kind == null) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    blank(cmd.what()) ? "Say what to look for with 'what'. One of: " + UiTargets.Kind.names()
                            : "There is nothing called '" + cmd.what() + "' to look for. One of: "
                                    + UiTargets.Kind.names());
        }
        if (kind == UiTargets.Kind.TEXT) {
            return locateInTheDocument(cmd.text(), cmd.nth(), cmd.offset(), null);
        }
        if (blank(cmd.text()) && kind != UiTargets.Kind.CELL) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, "Some text is needed");
        }
        Found found = findForLocate(cmd);
        return hitFor(cmd, found);
    }

    /** A thing that was found, and the window it was found in. */
    private record Found(java.awt.Window window, UiTargets.Target target) {
    }

    /** The window a caller named, else the active one — refusing with what is open when neither fits. */
    private java.awt.Window activeWindow(String wanted) throws CommandException {
        java.util.List<java.awt.Window> windows = java.util.Arrays.stream(java.awt.Window.getWindows())
                .filter(java.awt.Window::isShowing).toList();
        java.awt.Window active = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        java.util.List<DialogButtons.WindowInfo> described = windows.stream()
                .map(w -> new DialogButtons.WindowInfo(titleOf(w), true, w == active, w instanceof java.awt.Dialog))
                .toList();
        int chosen = DialogButtons.chooseWindow(described, wanted);
        if (chosen < 0) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    DialogButtons.noWindow(described, wanted));
        }
        return windows.get(chosen);
    }

    /** The one thing {@code cmd} names, or the refusal that says why there is not one. */
    private Found findForLocate(LocateCommand cmd) throws CommandException {
        UiTargets.Kind kind = UiTargets.Kind.of(cmd.what());
        java.awt.Window window = activeWindow(cmd.window());
        if (kind == UiTargets.Kind.CELL) {
            return new Found(window, oneCell(window, cmd));
        }
        UiTargets.Outcome outcome = UiTargets.resolve(window, titleOf(window), kind, cmd.text(), cmd.nth());
        if (outcome instanceof UiTargets.Outcome.Refused refused) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, refused.message());
        }
        return new Found(window, ((UiTargets.Outcome.Found) outcome).target());
    }

    /** A table cell, by its column's heading and its row number — {@code last} for the last row. */
    private UiTargets.Target oneCell(java.awt.Window window, LocateCommand cmd) throws CommandException {
        if (blank(cmd.column())) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Which column? Name it with 'column'. Available: " + UiTargets.columnsOf(window));
        }
        String asked = cmd.row() == null ? "" : cmd.row().trim();
        int row;
        try {
            row = EditorText.rowNumber(cmd.row());
        } catch (IllegalArgumentException notARow) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, notARow.getMessage());
        }
        java.util.List<UiTargets.Target> cells = UiTargets.cells(window, cmd.column(), row);
        if (cells.isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "No cell in column '" + cmd.column() + "' at row " + asked + " in "
                    + DialogButtons.describe(titleOf(window)) + ". Columns: " + UiTargets.columnsOf(window));
        }
        if (cells.size() > 1) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    cells.size() + " columns called '" + cmd.column() + "' in "
                    + DialogButtons.describe(titleOf(window)) + ". This cannot say which one you meant.");
        }
        return cells.get(0);
    }

    /** Where a found thing is on screen, clipped to the part of it anyone can see. */
    private com.dogsbay.dogsbayaieditor.commands.results.ElementHit hitFor(LocateCommand cmd, Found found)
            throws CommandException {
        UiTargets.Target target = found.target();
        if (!target.component().isShowing()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "'" + target.text() + "' is in " + DialogButtons.describe(titleOf(found.window()))
                    + " but is not on screen.");
        }
        java.awt.Rectangle visible = target.component() instanceof javax.swing.JComponent swing
                ? swing.getVisibleRect() : null;
        java.awt.Rectangle box = UiTargets.onScreenPart(target.box(), visible);
        if (box == null) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "'" + target.text() + "' is in " + DialogButtons.describe(titleOf(found.window()))
                    + " but is scrolled out of view.");
        }
        java.awt.Point at = target.component().getLocationOnScreen();
        return new com.dogsbay.dogsbayaieditor.commands.results.ElementHit(
                UiTargets.Kind.of(cmd.what()).name().toLowerCase(java.util.Locale.ROOT),
                target.text(), titleOf(found.window()),
                at.x + box.x, at.y + box.y, box.width, box.height,
                at.x + box.x + box.width / 2, at.y + box.y + box.height / 2);
    }

    /**
     * Where a place in the open document is on screen, found by the words there.
     *
     * <p>Forty-seven of the recordings' clicks put the caret somewhere in the editor by a measured position.
     * The words do not move when the font, the theme or the window manager does.
     */
    private com.dogsbay.dogsbayaieditor.commands.results.ElementHit locateInTheDocument(
            String wanted, int nth, int offset, ViewAlignment.Align align) throws Exception {
        if (blank(wanted)) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Some text is needed to find a place in the document");
        }
        DogsBayView view = requireView();
        com.dogsbay.xml.editor.XmlEditorPane pane = view.getEditor().getEditor();
        javax.swing.text.Document document = pane.getDocument();
        String all = document.getText(0, document.getLength());

        EditorText.Outcome outcome = EditorText.resolve(
                EditorText.occurrences(all, wanted), wanted, nth, offset);
        if (outcome instanceof EditorText.Outcome.Refused refused) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, refused.message());
        }
        EditorText.Match match = ((EditorText.Outcome.Found) outcome).match();

        java.awt.Rectangle box = boxOf(pane, match);
        if (box != null && align != null) {
            // Only the view moves. The caret stays where the reader left it, because a recording has to show
            // the pointer travelling to the place rather than arriving there already.
            pane.scrollRectToVisible(ViewAlignment.scrollTarget(align, box, pane.getVisibleRect()));
            box = boxOf(pane, match);
        }
        if (box == null) {
            // A collapsed fold has no rectangle: FoldingPlainView answers null for an offset inside one.
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "'" + wanted + "' is in this document but is inside a collapsed fold, so there is nowhere"
                    + " on screen to point at. Expand it first.");
        }
        java.awt.Rectangle shown = UiTargets.onScreenPart(box, pane.getVisibleRect());
        if (shown == null) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "'" + wanted + "' is in this document but is scrolled out of view.");
        }
        java.awt.Point at = pane.getLocationOnScreen();
        return new com.dogsbay.dogsbayaieditor.commands.results.ElementHit(
                "text", all.substring(match.start(), Math.min(match.end(), all.length())),
                titleOf(javax.swing.SwingUtilities.getWindowAncestor(pane)),
                at.x + shown.x, at.y + shown.y, shown.width, shown.height,
                at.x + shown.x + shown.width / 2, at.y + shown.y + shown.height / 2);
    }

    /**
     * The box a match occupies, or null when none of it has one.
     *
     * <p>From its first character to its last — not to the character after it, which would make every box one
     * character too wide and put the centre on the boundary. A match running over more than one line reports
     * its first line, since that is where a pointer should land.
     *
     * <p>Null when the text is inside a collapsed fold: {@code FoldingPlainView.modelToView} answers null
     * there, and this editor's views are folding ones.
     */
    private static java.awt.Rectangle boxOf(com.dogsbay.xml.editor.XmlEditorPane pane,
            EditorText.Match match) throws javax.swing.text.BadLocationException {
        java.awt.geom.Rectangle2D from = pane.modelToView2D(match.start());
        java.awt.geom.Rectangle2D to = pane.modelToView2D(Math.max(match.start(), match.end() - 1));
        if (from == null) {
            return null;
        }
        java.awt.Rectangle first = from.getBounds();
        if (to == null) {
            return first;
        }
        java.awt.Rectangle last = to.getBounds();
        return first.y == last.y ? first.union(last) : first;
    }

    /**
     * Where a stretch of the open document is on screen, one box per visual line.
     *
     * <p>{@code locate} aims at a character; this boxes a whole stretch, for a caller drawing over
     * it. The lines are <em>visual</em> lines, not newlines: soft wrap is live, so a single
     * paragraph can occupy several rows and a box per newline would cover the margin and the rows
     * between.
     *
     * <p>It scrolls nothing. A stretch the view has pushed out of sight comes back as
     * {@code hidden} with no boxes, which is {@code reveal}'s cue rather than an error — the caller
     * asked where something is, and "not on screen" is a true answer to that. Being on another card
     * of the window is different and is refused, as {@code locate} refuses it: there is no geometry
     * to report for a pane nobody is looking at.
     */
    private com.dogsbay.dogsbayaieditor.commands.results.RangeHit executeLocateRange(
            LocateRangeCommand cmd) throws Exception {
        DogsBayView view = requireView();
        com.dogsbay.xml.editor.XmlEditorPane pane = view.getEditor().getEditor();
        javax.swing.text.Document document = pane.getDocument();
        String all = document.getText(0, document.getLength());

        TextRange.Outcome outcome = TextRange.resolve(cmd.start(), cmd.end(), all.length());
        if (outcome instanceof TextRange.Outcome.Refused refused) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, refused.message());
        }
        TextRange.Span span = TextRange.wholeCharacters(((TextRange.Outcome.Found) outcome).span(), all);

        if (!pane.isShowing()) {
            // The editor is a card of the window: the Author and Viewer cards leave the text pane
            // sized and laid out but not showing, so its rectangles exist and mean nothing, and
            // getLocationOnScreen would throw where a refusal belongs.
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "The text of this document is not on screen, so there is nowhere to draw."
                    + " Switch to the editor view first.");
        }

        return boxed(pane, all, span);
    }

    /**
     * Where a stretch of {@code all} is on screen, once something has decided what the stretch is.
     *
     * <p>Shared by {@code locateRange}, which is given the offsets, and {@code locateNode}, which
     * works them out from the tree. The two must answer in the same shape and refuse for the same
     * reasons, which copying would not keep true for long.
     */
    private com.dogsbay.dogsbayaieditor.commands.results.RangeHit boxed(
            com.dogsbay.xml.editor.XmlEditorPane pane, String all, TextRange.Span span)
            throws Exception {
        return boxedWith(pane, all, span, measuredRows(pane, all, span));
    }

    /** The answer, for rows something else has already measured. */
    private com.dogsbay.dogsbayaieditor.commands.results.RangeHit boxedWith(
            com.dogsbay.xml.editor.XmlEditorPane pane, String all, TextRange.Span span,
            List<java.awt.Rectangle> rows) {
        return TextRange.rangeHit(span, all, rows, pane.getVisibleRect(), pane.getLocationOnScreen(),
                titleOf(javax.swing.SwingUtilities.getWindowAncestor(pane)), MOST_TEXT);
    }

    /**
     * Where a part of a node is on screen, found by XPath over the open document's own tree.
     *
     * <p>The positions the tree carries are stamped when the model is built from the text, so the
     * model is brought up to date first. {@code updateModelOrThrow} refuses when the buffer is not
     * well-formed, which is the right answer: a tree built from the last good text would point
     * confidently at the wrong characters.
     */
    private com.dogsbay.dogsbayaieditor.commands.results.RangeHit executeLocateNode(
            LocateNodeCommand cmd) throws Exception {
        NodeTarget target = nodeTargetFor(cmd.select(), cmd.part(), cmd.nth(), cmd.namespaces());
        return boxed(target.pane(), target.all(), target.span());
    }

    /**
     * Scroll a part of a node into view, then answer as {@code locateNode} does.
     *
     * <p>The same resolution, one {@code scrollRectToVisible}, and the boxes measured afterwards —
     * they have to be, since the point of scrolling is that they move.
     */
    private com.dogsbay.dogsbayaieditor.commands.results.RangeHit executeRevealNode(
            RevealNodeCommand cmd) throws Exception {
        ViewAlignment.Align align = alignOf(cmd.align());
        NodeTarget target = nodeTargetFor(cmd.select(), cmd.part(), cmd.nth(), cmd.namespaces());
        // Measured once. A row box is in the view's own coordinates, which scrolling does not
        // change — only the viewport moves — so the same rows answer both halves. Measuring again
        // afterwards cost a second pass over the span, which near the row cap is tens of
        // milliseconds on the event dispatch thread for no new information.
        List<java.awt.Rectangle> rows = measuredRows(target.pane(), target.all(), target.span());
        scrollTo(target.pane(), rows, align);
        return boxedWith(target.pane(), target.all(), target.span(), rows);
    }

    /**
     * Scroll so the first line of {@code span} lands where {@code align} asks.
     *
     * <p>A stretch inside a collapsed fold has no rectangle to scroll to. That is refused rather
     * than silently skipped, with the message {@code locateRange} gives, because scrolling cannot
     * cure a fold and a caller told "revealed" would then draw nothing.
     */
    // Package-private so a test can watch a real viewport obey it; the arithmetic is asserted in
    // ViewAlignmentTest, and this is the half that needs a laid-out scroll pane.
    static void scrollTo(com.dogsbay.xml.editor.XmlEditorPane pane,
            List<java.awt.Rectangle> rows, ViewAlignment.Align align) {
        pane.scrollRectToVisible(
                ViewAlignment.scrollTarget(align, rows.get(0), pane.getVisibleRect()));
    }

    /**
     * The rows of {@code span}, or a refusal when it has none to measure.
     *
     * <p>The refusal {@code boxed} and {@code scrollTo} used to make separately, so a stretch
     * inside a collapsed fold is refused once, before anything scrolls. Revealing and then failing
     * would leave the view somewhere the caller was never told about.
     */
    static List<java.awt.Rectangle> measuredRows(com.dogsbay.xml.editor.XmlEditorPane pane,
            String all, TextRange.Span span) throws Exception {
        List<java.awt.Rectangle> rows = rowBoxes(pane, all, span);
        if (rows == null) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    TextRange.noRectangleMessage(span, pane.getVisibleRect()));
        }
        return rows;
    }

    /**
     * The alignment a caller named, defaulting to the old scroll-as-little-as-possible behaviour.
     *
     * <p>Package-private for the tests: every {@code reveal} that existed before {@code align} did
     * sends no alignment, so defaulting to anything but {@code NEAREST} would quietly change all of
     * them, and that default is the compatibility promise this parameter rests on.
     */
    static ViewAlignment.Align alignOf(String name) throws CommandException {
        if (name == null || name.isBlank()) {
            return ViewAlignment.Align.NEAREST;
        }
        ViewAlignment.Align align = ViewAlignment.Align.of(name);
        if (align == null) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "There is no alignment called '" + name + "'. One of: "
                    + ViewAlignment.Align.names() + ".");
        }
        return align;
    }

    /** A node's stretch, and the pane and text it was measured against. */
    private record NodeTarget(com.dogsbay.xml.editor.XmlEditorPane pane, String all,
            TextRange.Span span) {
    }

    /**
     * The stretch a part of a node occupies, with everything checked that both verbs must check.
     *
     * <p>Shared so that {@code locateNode} and {@code revealNode} cannot come to disagree about
     * which documents they work on or how they refuse.
     */
    private NodeTarget nodeTargetFor(String select, String partName, int nth,
            java.util.Map<String, String> namespaces) throws Exception {
        if (blank(select)) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Say which node with 'select', as an XPath expression.");
        }
        NodeParts.Part part = NodeParts.chosen(partName);
        if (part == null) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    NodeParts.unknownPart(partName));
        }

        DogsBayView view = requireView();
        try {
            view.updateModelOrThrow();
        } catch (IllegalStateException stale) {
            // It throws rather than refusing, and an unconverted throw reaches the caller as an
            // internal error. Not well-formed is the commonest thing to meet here and the caller
            // can act on it, so it is a refusal like every other one this command gives.
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "This document's text cannot be read as XML just now, so nothing in it has a"
                    + " position: " + stale.getMessage(), stale);
        }
        DogsBayDocument document = view.getDocument();
        if (document.getDocument() == null) {
            // setText clears the tree and only a successful parse puts one back, and the
            // not-XML case clears the error deliberately — so this is reachable with no error set
            // at all, by opening Markdown, AsciiDoc or anything else that is not XML.
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "This document has no XML tree to search, so an XPath expression has nothing to"
                    + " match. locateRange boxes a stretch of any document by its offsets.");
        }
        com.dogsbay.xml.editor.XmlEditorPane pane = view.getEditor().getEditor();
        String all = pane.getDocument().getText(0, pane.getDocument().getLength());
        if (!all.equals(document.getText())) {
            // The positions belong to the text the model was built from. If that is not the text on
            // screen, every offset after the first difference is wrong, and silently so.
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "The editor's text and its model have come apart, so no position can be trusted"
                    + (all.length() == document.getText().length() ? "."
                            : " (the model holds " + document.getText().length() + " characters and"
                              + " the editor " + all.length() + ")")
                    + " A file whose lines end in CRLF does this until it is edited once, because the"
                    + " editor holds LF and the model holds what the file holds. Otherwise, ask again"
                    + " after the next edit settles.");
        }

        Object node = oneNodeFor(new LocateNodeCommand(select, partName, nth, namespaces), document);
        TextRange.Outcome outcome = partOf(node, part, all);
        if (outcome instanceof TextRange.Outcome.Refused refused) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, refused.message());
        }
        TextRange.Span measured = ((TextRange.Outcome.Found) outcome).span();
        // The same bounds check locateRange makes before it measures anything. The tree's positions
        // are only as good as the text they were stamped from, and an offset past the end of the
        // buffer would otherwise reach substring as an internal error rather than a refusal.
        TextRange.Outcome bounded = TextRange.resolve(measured.start(), measured.end(), all.length());
        if (bounded instanceof TextRange.Outcome.Refused refused) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, refused.message());
        }
        TextRange.Span span = TextRange.wholeCharacters(
                ((TextRange.Outcome.Found) bounded).span(), all);

        if (!pane.isShowing()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "The text of this document is not on screen, so there is nowhere to draw."
                    + " Switch to the editor view first.");
        }
        return new NodeTarget(pane, all, span);
    }

    /** The one node {@code select} and {@code nth} name, or a refusal saying how many there were. */
    // Package-private so a test can drive them with a real parsed document and no window: the
    // positions they read are the parser's, and nothing short of a real parse proves the
    // arithmetic agrees with it.
    static Object oneNodeFor(LocateNodeCommand cmd, DogsBayDocument document)
            throws CommandException {
        List<?> found;
        try {
            org.dom4j.XPath xpath = document.getDocument().createXPath(cmd.select());
            if (cmd.namespaces() != null && !cmd.namespaces().isEmpty()) {
                xpath.setNamespaceURIs(cmd.namespaces());
            }
            found = xpath.selectNodes(document.getDocument());
        } catch (org.dom4j.InvalidXPathException e) {
            throw new CommandException(CommandException.ErrorCode.XPATH_ERROR,
                    "'" + cmd.select() + "' is not an XPath expression this editor can read: "
                    + e.getMessage(), e);
        } catch (org.dom4j.XPathException e) {
            // A prefix with no URI given lands here rather than at parse time.
            throw new CommandException(CommandException.ErrorCode.XPATH_ERROR,
                    "'" + cmd.select() + "' could not be used on this document: " + e.getMessage()
                    + ". A prefix needs its URI in 'namespaces'.", e);
        }
        if (found == null || found.isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Nothing in this document matches '" + cmd.select() + "'.");
        }
        if (cmd.nth() <= 0) {
            if (found.size() > 1) {
                throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                        "'" + cmd.select() + "' matches " + found.size() + " nodes. Say which with"
                        + " 'nth', counting from 1 in document order.");
            }
            return found.get(0);
        }
        if (cmd.nth() > found.size()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "'" + cmd.select() + "' matches " + found.size()
                    + (found.size() == 1 ? " node" : " nodes") + ", so there is no number "
                    + cmd.nth() + ".");
        }
        return found.get(cmd.nth() - 1);
    }

    /** The stretch {@code part} occupies of {@code node}, whatever kind of node it is. */
    static TextRange.Outcome partOf(Object node, NodeParts.Part part, String all) {
        if (node instanceof com.dogsbay.xml.XElement element) {
            return NodeParts.ofElement(part,
                    element.getElementStartPosition(), element.getElementEndPosition(),
                    element.getContentStartPosition(), element.getContentEndPosition(),
                    element.getQualifiedName());
        }
        if (node instanceof com.dogsbay.xml.XAttribute attribute) {
            return NodeParts.ofAttribute(part,
                    attribute.getAttributeStartPosition(), attribute.getAttributeEndPosition(),
                    attribute.getQualifiedName(), all);
        }
        // Text, comments and processing instructions carry no position: only elements and
        // attributes are stamped. Say so rather than answer with something made up.
        if (node instanceof org.dom4j.Document) {
            return new TextRange.Outcome.Refused(
                    "'/' is the document itself, which has no tags of its own. Select its root"
                    + " element instead — '/*' — and ask for the part you want.");
        }
        if (!(node instanceof org.dom4j.Node)) {
            // count(), string() and boolean() come back through selectNodes as a one-item list
            // holding a number, a string or a boolean. The caller asked a question, not for a node.
            return new TextRange.Outcome.Refused(
                    "That expression answers with a " + describeAnswer(node) + " rather than a node, so"
                    + " there is nothing in the document to point at. Select the node itself.");
        }
        String kind = node instanceof org.dom4j.CDATA ? "a CDATA section"
                : node instanceof org.dom4j.Text ? "a text node"
                : node instanceof org.dom4j.Comment ? "a comment"
                : node instanceof org.dom4j.ProcessingInstruction ? "a processing instruction"
                : node instanceof org.dom4j.Namespace ? "a namespace declaration"
                : node instanceof org.dom4j.Entity ? "an entity reference"
                : "that kind of node";
        return new TextRange.Outcome.Refused(
                "This editor records where elements and attributes are, not where " + kind + " is,"
                + " so there is no position to report. Select the element around it and ask for"
                + " 'content', or find the words themselves with locate.");
    }

    /** What an XPath expression answered with, when it did not answer with a node. */
    private static String describeAnswer(Object answer) {
        if (answer instanceof Number) {
            return "number";
        }
        if (answer instanceof Boolean) {
            return "true or false";
        }
        if (answer instanceof String) {
            return "piece of text";
        }
        return "value";
    }

    /**
     * How many visual rows a single answer will box.
     *
     * <p>A stretch is boxed so that something can be drawn over it, and four hundred rectangles is
     * already more than anyone draws. The cap is here because the work is done on the event dispatch
     * thread: without it, a caller asking for a whole minified document would freeze the interface
     * rather than get a large answer.
     */
    private static final int MOST_ROWS = 400;

    /** How many characters of the stretch come back in {@code text}. */
    private static final int MOST_TEXT = 4000;

    /**
     * One rectangle per visual line of {@code span}, in the pane's own coordinates, or null when any
     * of it has no rectangle at all.
     *
     * <p>Rows are found by where the view puts them rather than by looking for newlines, so a row
     * ends where the reader sees it end — at a soft wrap as well as at a line break. The end of each
     * row is found by <strong>bisection on the row's y</strong>, which costs about a dozen
     * measurements per row. Walking offset by offset, as {@code Utilities.getRowEnd} does, costs one
     * measurement per character and each measurement re-measures the line from its start: a
     * 32,000-character line took 3.4 seconds that way, on the event dispatch thread.
     *
     * <p>Each box runs to the <em>far</em> edge of its last character. A rectangle from
     * {@code modelToView2D} is one pixel wide at the character's near edge, so a box built from two
     * of them stops a glyph short — and a one-character stretch came back one pixel wide, which is
     * nothing to draw. The far edge is the near edge of the next character where that character is
     * on the same row, and the glyph's own width where it is not.
     *
     * @throws CommandException when the stretch covers more rows than {@link #MOST_ROWS}
     */
    static List<java.awt.Rectangle> rowBoxes(com.dogsbay.xml.editor.XmlEditorPane pane, String text,
            TextRange.Span span) throws javax.swing.text.BadLocationException, CommandException {
        List<java.awt.Rectangle> rows = new ArrayList<>();
        java.awt.FontMetrics metrics = pane.getFontMetrics(pane.getFont());
        int at = span.start();
        while (at < span.end()) {
            java.awt.geom.Rectangle2D first = pane.modelToView2D(at);
            if (first == null) {
                return null;
            }
            java.awt.Rectangle near = first.getBounds();
            int last = lastOnRow(pane, at, span.end() - 1, near.y);
            if (last < at) {
                return null;
            }
            // A line break is the end of a row rather than something drawn on it.
            int drawn = text.charAt(last) == '\n' && last > at ? last - 1 : last;
            java.awt.geom.Rectangle2D far = pane.modelToView2D(drawn);
            if (far == null) {
                return null;
            }
            java.awt.Rectangle box = near.union(far.getBounds());
            box.width = Math.max(box.width, farEdge(pane, text, metrics, drawn, near.y) - box.x);
            rows.add(box);
            if (rows.size() > MOST_ROWS) {
                throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                        "The characters " + span.start() + " to " + span.end() + " cover more than "
                        + MOST_ROWS + " lines on screen. Ask for a smaller part of it — for an"
                        + " element, part 'start-tag' is usually what a reader is being shown —"
                        + " because boxing more than that holds up the editor and is more"
                        + " rectangles than anything draws.");
            }
            at = last + 1;
        }
        return rows;
    }

    /**
     * The last offset between {@code from} and {@code upTo} that the view draws on the row at
     * {@code y}, found by bisection: a row's y never decreases as the offset grows.
     */
    private static int lastOnRow(com.dogsbay.xml.editor.XmlEditorPane pane, int from, int upTo, int y)
            throws javax.swing.text.BadLocationException {
        int low = from;
        int high = upTo;
        int last = from;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            java.awt.geom.Rectangle2D at = pane.modelToView2D(middle);
            if (at == null) {
                return -1;
            }
            if (at.getBounds().y == y) {
                last = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return last;
    }

    /**
     * Where the character at {@code offset} ends, in the pane's coordinates.
     *
     * <p>The next character's near edge, when the view draws it on the same row. At the end of a row
     * there is no next character to ask, so the glyph's own width stands in — which a tab makes
     * approximate, and a tab is not something a stretch usually ends on.
     */
    private static int farEdge(com.dogsbay.xml.editor.XmlEditorPane pane, String text,
            java.awt.FontMetrics metrics, int offset, int y)
            throws javax.swing.text.BadLocationException {
        java.awt.Rectangle here = pane.modelToView2D(offset).getBounds();
        if (offset + 1 < text.length()) {
            java.awt.geom.Rectangle2D next = pane.modelToView2D(offset + 1);
            if (next != null && next.getBounds().y == y) {
                return next.getBounds().x;
            }
        }
        return here.x + metrics.charWidth(text.charAt(offset));
    }

    /**
     * Scroll something into view and say where it ended up.
     *
     * <p>The cure for the one refusal {@code locate} gives about something that is really there. It scrolls
     * and nothing else: no caret moved, no selection changed.
     */
    private com.dogsbay.dogsbayaieditor.commands.results.ElementHit executeReveal(RevealCommand cmd)
            throws Exception {
        // Before the panel branch and before the lookup. It used to be read inside the scrolling
        // branch, so a cue sheet with a typo in both its target and its alignment was told only
        // about the target, and a panel was never told at all — while its sibling 'to' refuses a
        // typo properly.
        ViewAlignment.Align align = alignOf(cmd.align());
        if ("panel".equalsIgnoreCase(trimmed(cmd.what()))) {
            if (align != ViewAlignment.Align.NEAREST) {
                throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                        "A panel is scrolled to one end or the other, not to an alignment: use"
                        + " 'to' with 'start' or 'end'. 'align' places a line of a document.");
            }
            return revealPanel(cmd);
        }
        UiTargets.Kind kind = UiTargets.Kind.of(cmd.what());
        if (kind == null) {
            // The same refusal locate gives. The two verbs are a pair, and a kind typo is exactly what a cue
            // sheet produces — it must not be an internal error in one of them.
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    blank(cmd.what())
                            ? "Say what to reveal with 'what'. One of: panel, " + UiTargets.Kind.names()
                            : "There is nothing called '" + cmd.what() + "' to reveal. One of: panel, "
                                    + UiTargets.Kind.names());
        }
        if (kind == UiTargets.Kind.TEXT) {
            return locateInTheDocument(cmd.text(), cmd.nth(), cmd.offset(), align);
        }
        // Everything else is a thing locate can already find; revealing it is scrolling its own component to
        // the box locate would have returned, and then answering as locate would.
        LocateCommand asLocate = new LocateCommand(cmd.text(), cmd.what(), cmd.window(), cmd.nth(),
                cmd.column(), cmd.row(), 0);
        if (blank(cmd.text()) && kind != UiTargets.Kind.CELL) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, "Some text is needed");
        }
        Found found = findForLocate(asLocate);
        if (found.target().component() instanceof javax.swing.JComponent swing) {
            swing.scrollRectToVisible(ViewAlignment.scrollTarget(
                    align, found.target().box(), swing.getVisibleRect()));
        }
        return hitFor(asLocate, found);
    }

    /**
     * Scroll a panel to its end, or to its start.
     *
     * <p>The Project Validation pane fills from the top and the verdict is the last line, so a recording that
     * wants to show the verdict used to click the scroll arrow until it appeared — sixteen clicks across the
     * sheets, each of them a measured position.
     */
    private com.dogsbay.dogsbayaieditor.commands.results.ElementHit revealPanel(RevealCommand cmd)
            throws Exception {
        if (blank(cmd.panel())) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Which panel? Name it with 'panel'.");
        }
        Boolean toEnd = EditorText.scrollsToEnd(cmd.to());
        if (toEnd == null) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "'" + cmd.to() + "' is neither 'end' nor 'start'.");
        }
        java.awt.Window window = activeWindow(cmd.window());
        // Through resolve, as everything else is: picking the first of two panes with the same tab name is
        // the mistake a recording carries on past.
        UiTargets.Outcome outcome = UiTargets.resolve(window, titleOf(window), UiTargets.Kind.TAB,
                cmd.panel(), cmd.nth());
        if (outcome instanceof UiTargets.Outcome.Refused refused) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, refused.message());
        }
        UiTargets.Target tab = ((UiTargets.Outcome.Found) outcome).target();
        javax.swing.JTabbedPane pane = (javax.swing.JTabbedPane) tab.component();
        int index = pane.indexOfTab(tab.text());
        javax.swing.JScrollPane scroller = index >= 0 && pane.getComponentAt(index) instanceof java.awt.Container content
                ? UiTargets.scrollerIn(content) : null;
        if (scroller == null) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "'" + tab.text() + "' has nothing to scroll.");
        }
        javax.swing.JViewport viewport = scroller.getViewport();
        viewport.setViewPosition(UiTargets.edgePosition(viewport.getViewPosition(),
                viewport.getViewSize(), viewport.getExtentSize(), toEnd));
        java.awt.Point at = pane.getLocationOnScreen();
        java.awt.Rectangle box = tab.box();
        return new com.dogsbay.dogsbayaieditor.commands.results.ElementHit(
                "panel", tab.text(), titleOf(window),
                at.x + box.x, at.y + box.y, box.width, box.height,
                at.x + box.x + box.width / 2, at.y + box.y + box.height / 2);
    }

    private static String trimmed(String text) {
        return text == null ? "" : text.trim();
    }

    /** A window's title, whatever kind of window it is; "" when it has none. */
    private static String titleOf(java.awt.Window window) {
        if (window instanceof java.awt.Frame frame) {
            return frame.getTitle() == null ? "" : frame.getTitle();
        }
        if (window instanceof java.awt.Dialog dialog) {
            return dialog.getTitle() == null ? "" : dialog.getTitle();
        }
        return "";
    }

    /**
     * The menu command with this id, checked before anything is run.
     *
     * <p>Separated from the editor so it can be tested: what matters here is the refusals, and they are the same whether the
     * action comes from a live menu bar or a map in a test.
     *
     * @param id      the command's id.
     * @param lookup  how to find an action by id.
     * @param ids     every id there is, for an error that has to say what does exist.
     * @return the action, enabled and ready to fire.
     */
    static Action findAction(String id, java.util.function.Function<String, Action> lookup,
            java.util.function.Supplier<java.util.List<String>> ids) throws CommandException {
        if (blank(id)) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, "An action id is needed");
        }
        Action action = lookup.apply(id);
        if (action == null) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                "Unknown action: " + id + ". Available: " + ids.get());
        }
        if (!action.isEnabled()) {
            // refused rather than quietly doing nothing: a caller asking to validate a document that cannot be validated
            // yet needs to know that, not a success it can do nothing with
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                "Action is not available now: " + id);
        }
        return action;
    }

    // ── The panels along the bottom ──────────────────────────────────

    /** The name a caller uses, and the tab's own title. The titles are the ones {@code OutputPanel} adds in its constructor. */
    private static final java.util.Map<String, String> PANELS = java.util.Map.of(
        "errors", "Errors",
        "terminal", "Terminal",
        "projectValidation", "Project Validation");

    /**
     * The tab title for a caller's panel name.
     *
     * @param panel the name a caller uses.
     * @return the tab's title.
     * @throws CommandException when there is no such panel, naming the ones there are.
     */
    static String tabTitleFor(String panel) throws CommandException {
        String title = panel == null ? null : PANELS.get(panel);
        if (title == null) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                "Unknown panel: " + panel + ". Available: " + new java.util.TreeSet<>(PANELS.keySet()));
        }
        return title;
    }

    private CommandResult executeSelectPanel(SelectPanelCommand cmd) throws Exception {
        var panel = editor.getOutputPanel();
        if (panel == null) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR, "The panels are not available");
        }
        String title = tabTitleFor(cmd.panel());
        // The panels can be collapsed altogether, in which case selecting a tab changes what is selected behind a hidden
        // panel and the caller is told it worked while nothing is on screen. So show them first.
        editor.setBottomPanelVisible(true);
        if (!panel.selectTab(title)) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                "The " + cmd.panel() + " panel is busy being filled; try again");
        }
        return CommandResult.ok("Showing " + cmd.panel());
    }

    // ── Typing ───────────────────────────────────────────────────────

    /**
     * Type text key by key into whatever has focus.
     *
     * <p>Off the event thread, because the gaps between keys are what makes the typing watchable and the interface has to be
     * free to repaint between them. Each key is delivered on the event thread; the waiting happens here.
     */
    private CommandResult executeTypeText(TypeTextCommand cmd) throws CommandException {
        if (!UiDriving.isAllowed()) {
            throw UiDriving.refused("Typing");
        }
        if (SwingUtilities.isEventDispatchThread()) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                "Typing cannot be asked for from the interface's own thread: the gaps between keys would stop it repainting");
        }
        String fault = cmd.fault();
        if (fault != null) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, fault);
        }
        return type(cmd.text(), cmd.gapMs(), new Keyboard() {
            @Override
            public Object focusTarget() throws CommandException {
                return executeOnEDT(() -> focusedNow(editor));
            }

            @Override
            public Object pressIfStillFocused(Object target, char c) throws CommandException {
                // One trip, on purpose. Checking the focus and sending the key in two trips leaves a gap in which the event
                // thread runs anything at all, including opening a modal dialog; the key would then be dispatched straight
                // to the component behind it, which is worse than the case the check exists to prevent.
                return executeOnEDT(() -> {
                    Object holder = focusedNow(editor);
                    if (holder != target) {
                        return holder;
                    }
                    sendKey(event -> ((java.awt.Component) target).dispatchEvent(event), target, c);
                    return null;
                });
            }
        }, EditorExecutor::pause);
    }

    /**
     * What keys would reach, as far as this process is concerned.
     *
     * <p>Not simply the focus owner. That is null whenever the editor's window is not the active one, which happens when the
     * operator switches to another window and happens *always* under a bare X server with no window manager — which is
     * exactly where a recording runs. Treating that as "nothing has focus" refused every call, with advice about clicking
     * into the editor that could not help.
     *
     * <p>So the window's most recent focus owner stands in. A deactivated window therefore does not stop the typing, which
     * is right for this command: keys are delivered to the component rather than through the desktop, so where the operating
     * system currently points is not what decides where they land.
     */
    private static Object focusedNow(java.awt.Component editor) {
        java.awt.Component owner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (owner != null) {
            return owner;
        }
        java.awt.Window window = editor instanceof java.awt.Window w ? w
            : javax.swing.SwingUtilities.getWindowAncestor(editor);
        return window == null ? null : window.getMostRecentFocusOwner();
    }

    /**
     * The three events one key makes, in the order a keyboard makes them.
     *
     * <p>Given somewhere to send them rather than sending them, so that what is built can be checked without a window to
     * send it to. A real key also carries Shift for an upper-case letter or a symbol above a digit, and these do not: nothing
     * in this editor reads Shift on a character key, since completion and indenting key off the text that was inserted, but
     * a binding on a bare key code would fire here where a real keypress would not match.
     */
    static void sendKey(java.util.function.Consumer<java.awt.event.KeyEvent> to, Object source, char c) {
        long when = System.currentTimeMillis();
        int code = keyCodeFor(c);
        to.accept(new java.awt.event.KeyEvent((java.awt.Component) source, java.awt.event.KeyEvent.KEY_PRESSED, when, 0,
            code, java.awt.event.KeyEvent.CHAR_UNDEFINED));
        to.accept(new java.awt.event.KeyEvent((java.awt.Component) source, java.awt.event.KeyEvent.KEY_TYPED, when, 0,
            java.awt.event.KeyEvent.VK_UNDEFINED, c));
        to.accept(new java.awt.event.KeyEvent((java.awt.Component) source, java.awt.event.KeyEvent.KEY_RELEASED, when, 0,
            code, java.awt.event.KeyEvent.CHAR_UNDEFINED));
    }

    /** The key code a character comes from. The three that are not characters at all are named; the rest AWT knows. */
    static int keyCodeFor(char c) {
        return switch (c) {
            case '\n' -> java.awt.event.KeyEvent.VK_ENTER;
            case '\t' -> java.awt.event.KeyEvent.VK_TAB;
            case '\b' -> java.awt.event.KeyEvent.VK_BACK_SPACE;
            default -> java.awt.event.KeyEvent.getExtendedKeyCodeForChar(c);
        };
    }

    private static void pause(long ms) throws CommandException {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR, "Interrupted while typing");
        }
    }

    /**
     * Type text into whatever had focus when this started, stopping if anything takes focus away.
     *
     * <p>Separated from the editor so the rules can be tested: which keys are sent, in what order, and what happens when the
     * focus moves. What a real component does with a key is the component's business.
     *
     * @param text     the characters to type.
     * @param gapMs    how long to wait between keys.
     * @param keyboard where the keys would land, and how to send one there if they still would.
     * @param pause    how to wait between keys.
     * @return how much was typed.
     * @throws CommandException when there is nowhere to type, or the focus moved part way through.
     */
    static CommandResult type(String text, long gapMs, Keyboard keyboard, Pause pause) throws CommandException {
        if (text == null || text.isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, "There is no text to type");
        }
        Object target = keyboard.focusTarget();
        if (target == null) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                "Nothing in the window has ever had focus, so there is nowhere to type. Open a document first.");
        }
        for (int i = 0; i < text.length(); i++) {
            Object holder = keyboard.pressIfStillFocused(target, text.charAt(i));
            if (holder != null) {
                throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Stopped after " + i + " of " + text.length() + " characters: the focus moved to " + describe(holder)
                        + ". Typing goes where it started, so that a popup or a dialog cannot quietly receive the rest.");
            }
            if (gapMs > 0 && i < text.length() - 1) {
                pause.forMs(gapMs);
            }
        }
        return CommandResult.ok("Typed " + text.length() + " characters");
    }

    private static String describe(Object holder) {
        if (holder instanceof java.awt.Component c && c.getName() != null) {
            return c.getName() + " (" + holder.getClass().getSimpleName() + ")";
        }
        return holder.getClass().getSimpleName();
    }

    /** Where keys would land, and how to send one there if they still would. */
    interface Keyboard {
        /** What keys would reach now, or null when nothing ever has. */
        Object focusTarget() throws CommandException;

        /**
         * Send one key, but only if {@code target} is still where keys would land.
         *
         * @return null when the key was sent, or whatever holds the focus instead.
         */
        Object pressIfStillFocused(Object target, char c) throws CommandException;
    }

    /** How to wait between keys. */
    interface Pause {
        void forMs(long ms) throws CommandException;
    }

    // ── Waiting for the interface ────────────────────────────────────

    /**
     * Wait for the event queue to stay empty for a while, so a caller can act on a settled window.
     *
     * <p>Deliberately not on the event thread: the whole point is to watch it from outside, and asking it to wait for itself
     * would wait forever. Each round trip through the queue also flushes whatever was already on it.
     */
    private CommandResult executeWaitIdle(WaitIdleCommand cmd) throws CommandException {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                "Waiting for the interface cannot be asked for from the interface's own thread");
        }
        String fault = cmd.fault();
        if (fault != null) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, fault);
        }
        return waitForIdle(cmd.quiet(), cmd.timeout(), EditorExecutor::roundTrip,
            () -> java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().peekEvent() == null);
    }

    /**
     * One round trip through the event queue, waiting no longer than it is given.
     *
     * <p>Bounded on purpose. {@code invokeAndWait} has no limit of its own, so an event thread that is wedged — a long
     * synchronous job running on it, a native dialog that pumps nothing — would hold the caller for as long as it lasted.
     * That is the one thing this command exists to prevent, so the wait is given a deadline and answers without one.
     *
     * @param withinMs how long to allow.
     * @return whether the trip completed in time.
     */
    private static boolean roundTrip(long withinMs) {
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        SwingUtilities.invokeLater(done::countDown);
        try {
            return done.await(Math.max(1, withinMs), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Wait until the event queue has been empty for {@code quietMs}, or give up after {@code timeoutMs}.
     *
     * <p>Separated from the editor so the waiting itself can be tested: it needs an event queue, which every run has, and
     * nothing else. Each round trip returns only once everything already on the queue has been dealt with; the emptiness
     * check afterwards is what notices work that arrived in the meantime.
     *
     * <p>The deadline is consulted before anything blocks, so the answer comes within the limit whatever the interface is
     * doing, including nothing at all.
     *
     * @param quietMs   how long the queue must stay empty.
     * @param timeoutMs how long to wait altogether.
     * @param drain      one bounded round trip through the event queue.
     * @param queueEmpty whether nothing is waiting on the queue. Given rather than read here so a test can decide what the
     *                   interface is doing instead of depending on whatever the test machine happens to be doing.
     * @return how long the wait took.
     * @throws CommandException when the queue never stays quiet for long enough, or the interface stops answering.
     */
    static CommandResult waitForIdle(int quietMs, int timeoutMs, BoundedRoundTrip drain,
            java.util.function.BooleanSupplier queueEmpty) throws CommandException {
        long started = System.currentTimeMillis();
        long deadline = started + timeoutMs;
        long quietSince = 0;
        while (true) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "The interface was still busy after " + timeoutMs + " ms. Something is repainting or working without"
                        + " pause; a longer limit may help, or a shorter quiet period.");
            }
            if (!drain.within(remaining)) {
                throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "The interface did not answer within " + timeoutMs + " ms. Its thread is busy with one long piece of"
                        + " work, or held by a dialog that pumps no events.");
            }
            long now = System.currentTimeMillis();
            boolean empty = queueEmpty.getAsBoolean();
            if (!empty) {
                quietSince = 0;
            } else if (quietMs == 0) {
                return CommandResult.ok("Settled after " + (now - started) + " ms");
            } else if (quietSince == 0) {
                quietSince = now;
            } else if (now - quietSince >= quietMs) {
                return CommandResult.ok("Settled after " + (now - started) + " ms");
            }
            long sleep = Math.min(25, Math.max(1, quietMs == 0 ? 1 : quietMs / 4));
            try {
                Thread.sleep(Math.min(sleep, Math.max(1, deadline - System.currentTimeMillis())));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR, "Interrupted while waiting");
            }
        }
    }

    /** One round trip through the event queue that answers within a limit. */
    interface BoundedRoundTrip {
        boolean within(long withinMs);
    }

    // ── The terminal ─────────────────────────────────────────────────

    /**
     * Type a line into the terminal and run it.
     *
     * <p>Not wrapped in one trip to the event thread: making a terminal is itself queued onto that thread, so asking for one
     * and then using it in the same trip finds nothing there. The steps are taken in turn instead, each waiting for the last,
     * which is why this is the one command here that is not a single {@code executeOnEDT}.
     */
    private CommandResult executeTerminalRun(TerminalRunCommand cmd) throws CommandException {
        if (!UiDriving.isAllowed()) {
            throw UiDriving.refused("Running a command in the terminal");
        }
        if (blank(cmd.command())) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, "A command is needed");
        }
        var container = executeOnEDT(() -> {
            var panel = editor.getOutputPanel();
            if (panel == null || panel.getTerminalContainer() == null) {
                throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR, "The terminal is not available");
            }
            editor.setBottomPanelVisible(true);
            panel.selectTab("Terminal");
            if (panel.getTerminalContainer().getCurrentTerminal() == null) {
                panel.getTerminalContainer().createNewTerminal(); // queued; the next trip is what can see it
            }
            return panel.getTerminalContainer();
        });
        boolean sent = executeOnEDT(() -> {
            var terminal = container.getCurrentTerminal();
            return terminal != null && terminal.send(cmd.command());
        });
        if (!sent) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                "The terminal did not take the command; its shell may have ended");
        }
        return CommandResult.ok("Ran in the terminal: " + cmd.command());
    }

    // ── Open DITA Map ────────────────────────────────────────────────

    private CommandResult executeOpenDitaMap(OpenDitaMapCommand cmd) throws Exception {
        var ditaExplorer = editor.getDitaExplorer();
        if (ditaExplorer == null) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                "DITA explorer plugin is not available");
        }

        java.io.File mapFile = cmd.file().toAbsolutePath().toFile();
        if (!mapFile.exists()) {
            throw new CommandException(CommandException.ErrorCode.FILE_NOT_FOUND,
                "Map file not found: " + mapFile);
        }

        ditaExplorer.loadMap(mapFile);

        // Switch to DITA sidebar
        ExplorerContainer left = editor.getExplorerContainer();
        if (left != null && left.getExplorerIds().contains("dita")) {
            if (left.isMinimized()) {
                left.setMinimized(false);
            }
            left.setSelectedExplorer("dita");
        }

        return CommandResult.ok("Loaded map: " + mapFile.getName());
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * Resolve a file path to a DogsBayView. If file is null, returns the active view.
     */
    private DogsBayView resolveView(Path file) throws CommandException {
        if (file == null) {
            DogsBayView view = editor.getView();
            if (view == null) {
                throw new CommandException(
                    CommandException.ErrorCode.DOCUMENT_NOT_OPEN,
                    "No document is currently open"
                );
            }
            return view;
        }

        // Find the view for this file, however the path is spelled (./, .., a link): a file edited on disk
        // because its open view was missed would be written under the writer's unsaved buffer
        Path wanted = WriteGate.real(file);
        Vector views = editor.getViews();
        for (int i = 0; i < views.size(); i++) {
            DogsBayView view = (DogsBayView) views.elementAt(i);
            DogsBayDocument doc = view.getDocument();
            if (doc.getURL() != null) {
                try {
                    Path viewPath = Path.of(doc.getURL().toURI());
                    if (viewPath.equals(file) || viewPath.equals(file.toAbsolutePath())
                            || WriteGate.real(viewPath).equals(wanted)) {
                        return view;
                    }
                } catch (Exception e) {
                    // skip non-file URLs
                }
            }
        }

        throw new CommandException(
            CommandException.ErrorCode.DOCUMENT_NOT_OPEN,
            "Document not open: " + file
        );
    }
}
