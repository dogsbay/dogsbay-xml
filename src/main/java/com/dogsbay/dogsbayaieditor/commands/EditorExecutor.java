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
            case BuildDeliverablesCommand c -> headless.execute(blank(c.ditaOtHome())
                    ? new BuildDeliverablesCommand(c.root(), c.outputBaseDir(), c.deliverable(), editorDitaOt(c.root()),
                            c.deliverableNames(), c.keepTemp())
                    : c);
            case ValidateConditionsCommand c -> headless.execute(c);
            case ListSubjectsCommand c -> headless.execute(c);
            case MetadataAuditCommand c -> headless.execute(c);
            case ExportMetadataSchematronCommand c -> headless.execute(c);
            case MetadataSetCommand c -> headless.execute(c);
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
            case GetContentCommand c -> executeOnEDT(() -> executeGetContent(c));
            case SetContentCommand c -> executeSetContentAgentAware(c);
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
        };
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
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

    private CommandResult executeSave(SaveCommand cmd) throws Exception {
        if (cmd.all()) {
            Vector views = editor.getViews();
            for (int i = 0; i < views.size(); i++) {
                DogsBayView view = (DogsBayView) views.elementAt(i);
                view.updateModelOrThrow();
                DogsBayDocument doc = view.getDocument();
                if (doc.getURL() != null && !doc.isReadOnly()) {
                    doc.save();
                    view.getChangeManager().markSave();
                }
            }
            return CommandResult.ok("All documents saved");
        }

        DogsBayView view = resolveView(cmd.file());
        view.updateModelOrThrow();
        DogsBayDocument doc = view.getDocument();
        if (doc.getURL() != null) {
            doc.save();
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

    private String executeGetContent(GetContentCommand cmd) throws Exception {
        DogsBayView view = resolveView(cmd.file());
        view.updateModel();
        return view.getDocument().getText();
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

    private CommandResult executeSetContentAgentAware(SetContentCommand cmd) throws CommandException {
        Buffer buf = executeOnEDT(() -> snapshot(resolveView(cmd.file())));
        if (buf == null) {
            return CommandResult.fail("No active editor panel");
        }
        return writeAgentAware(buf, cmd.content());
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
        AgentProposalPolicy.Outcome outcome = new AgentProposalPolicy().decide(SessionContext.current(), buf.file(),
                buf.text(), modified, editor.getProperties().isAgentProposalsEnabled(),
                buf.file() == null ? c -> true : AgentProposalPolicy.validatorFor(buf.file()));
        if (outcome.refused()) {
            throw new CommandException(CommandException.ErrorCode.CONFLICT,
                    outcome.note() + "\nCurrent content:\n" + buf.text());
        }
        return executeOnEDT(() -> {
            spliceOnEdt(buf, buf.text(), outcome.text());
            return CommandResult.ok(outcome.asProposals() ? "Content updated: " + outcome.note()
                    : outcome.note() != null ? "Content updated (" + outcome.note() + ")" : "Content updated");
        });
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
            doc.save();
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

        // Add to config and persist
        editor.getProperties().addProjectProperties(props);

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
            editor.getProperties().setLastOpenedProject(folder.getAbsolutePath());
            editor.getProperties().setLastOpenedFolder(folder.getAbsolutePath());
        }

        return new ProjectInfo(
            cmd.name(),
            cmd.folderPath().toAbsolutePath().toString(),
            cmd.projectType(),
            cmd.defaultRootMap() != null ? cmd.defaultRootMap().toString() : null,
            true
        );
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

        // Find the view for this file
        Vector views = editor.getViews();
        for (int i = 0; i < views.size(); i++) {
            DogsBayView view = (DogsBayView) views.elementAt(i);
            DogsBayDocument doc = view.getDocument();
            if (doc.getURL() != null) {
                try {
                    Path viewPath = Path.of(doc.getURL().toURI());
                    if (viewPath.equals(file.toAbsolutePath()) || viewPath.equals(file)) {
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
