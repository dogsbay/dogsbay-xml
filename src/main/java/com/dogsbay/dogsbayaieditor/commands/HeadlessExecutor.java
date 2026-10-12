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

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.dogsbay.dogsbayaieditor.commands.results.*;
import com.dogsbay.dogsbayaieditor.ditaproject.BatchResult;
import com.dogsbay.dogsbayaieditor.ditaproject.FileSet;
import com.dogsbay.dogsbayaieditor.validate.DocumentValidator;

/**
 * Executes commands without requiring a running editor GUI.
 * Uses javax.xml APIs for XML processing.
 */
public class HeadlessExecutor implements CommandExecutor {

    /**
     * The commands that need the running editor: its buffers, views, cursor, projects list or window.
     * Everything else runs here, on files. Kept in step with the switch below by a test.
     */
    private static final java.util.Set<Class<?>> NEEDS_EDITOR = java.util.Set.of(
            OpenCommand.class, CloseCommand.class, SaveCommand.class, ListDocumentsCommand.class,
            GetSelectionCommand.class, ReplaceSelectionCommand.class, GotoLineCommand.class, GetCursorCommand.class,
            SetCursorCommand.class, SelectElementCommand.class, GetOutlineCommand.class, GetErrorsCommand.class,
            SearchProjectCommand.class, ListProjectFilesCommand.class, CreateProjectCommand.class,
            OpenProjectCommand.class, ListProjectsCommand.class, GetProjectCommand.class, ListSidebarsCommand.class,
            SwitchSidebarCommand.class, OpenDitaMapCommand.class, NewDocumentCommand.class, AuthorSwitchCommand.class,
            InvokeActionCommand.class, ClickButtonCommand.class, LocateCommand.class, LocateRangeCommand.class, LocateNodeCommand.class, RevealCommand.class, RevealNodeCommand.class, SelectPanelCommand.class, TerminalRunCommand.class, WaitIdleCommand.class, TypeTextCommand.class,
            AuthorOutlineCommand.class, AuthorInsertBlockCommand.class, AuthorSetTextCommand.class,
            AuthorIssuesCommand.class, ScreenshotCommand.class);

    /** Whether {@code command} needs the running editor, so cannot run here. */
    public static boolean needsEditor(Class<?> command) {
        return NEEDS_EDITOR.contains(command);
    }

    /** Whether an agent's edits to DITA documents land as review proposals (the writer's setting). */
    private final boolean agentProposals;
    /** Whether a file is open in a running editor, where a write to the file would go under its tab; or null. */
    private final java.util.function.Predicate<java.nio.file.Path> openInEditor;
    /** The text before agents' changes, for an exact review_revert while this executor lives. */
    private final ProposalSnapshots snapshots = new ProposalSnapshots();

    public HeadlessExecutor() {
        this(true);
    }

    /**
     * @param agentProposals whether an agent's edits to DITA documents land as review proposals; only
     *                       edits made under an agent session are ever marked
     */
    public HeadlessExecutor(boolean agentProposals) {
        this(agentProposals, null);
    }

    /**
     * @param openInEditor whether a file is open in a running editor: a file open there is not written here,
     *                     since the editor's tab would then hold other text than the file; null to not check
     */
    public HeadlessExecutor(boolean agentProposals, java.util.function.Predicate<java.nio.file.Path> openInEditor) {
        this.agentProposals = agentProposals;
        this.openInEditor = openInEditor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> R execute(Command<R> command) throws CommandException {
        return (R) switch (command) {
            case ValidateCommand c -> HeadlessProcessingCommands.executeValidate(c);
            case ParseCommand c -> HeadlessProcessingCommands.executeParse(c);
            case TransformCommand c -> HeadlessProcessingCommands.executeTransform(c);
            case QueryCommand c -> HeadlessProcessingCommands.executeQuery(c);
            case FormatCommand c -> HeadlessProcessingCommands.executeFormat(c);
            case ReflowCommand c -> HeadlessProcessingCommands.executeReflow(c);
            case InfoCommand c -> HeadlessProcessingCommands.executeInfo(c);
            // The project's default root map (.dogsbay/config.xml) resolves keys when the caller names none
            case WhereUsedCommand c -> executeWhereUsed(new WhereUsedCommand(c.file(), c.root(), keyMap(c.root(), c.rootMap())));
            case ListKeysCommand c -> executeListKeys(c);
            case ResolveKeyCommand c -> executeResolveKey(c);
            case CheckLinksCommand c -> withMissingMap(c.root(), c.rootMap(),
                    executeCheckLinks(new CheckLinksCommand(c.root(), keyMap(c.root(), c.rootMap()))));
            case CheckOutputLinksCommand c -> executeCheckOutputLinks(c);
            case RenderPreviewCommand c -> executeRenderPreview(c);
            case RenderReportCommand c -> executeRenderReport(c);
            case ProjectGraphCommand c -> executeProjectGraph(c);
            case HealthCommand c -> executeHealth(new HealthCommand(c.root(), keyMap(c.root(), c.rootMap())));
            case ValidateProjectCommand c -> executeValidateProject(c);
            case ProjectHealthCommand c -> executeProjectHealth(c);
            case CheckProjectCommand c -> executeCheckProject(c);
            case ConrefAuditCommand c -> executeConrefAudit(new ConrefAuditCommand(c.root(), keyMap(c.root(), c.rootMap())));
            case SchematronProjectCommand c -> executeSchematronProject(
                    new SchematronProjectCommand(c.root(), c.scope(), houseRules(c.root(), c.schema())));
            case SchematronCommand c -> executeSchematron(c);
            case ReviewListCommand c -> ReviewCommands.list(ReviewCommands.read(c.file()), c.author());
            case ReviewAcceptCommand c -> executeReviewDecision(c.file(), true, c.id(), c.author(), c.all());
            case ReviewRejectCommand c -> executeReviewDecision(c.file(), false, c.id(), c.author(), c.all());
            case ReviewRevertCommand c -> {
                java.nio.file.Path target = java.nio.file.Path.of(c.file());
                String xml = FileEdits.read(target);
                String identity = com.dogsbay.agent.session.SessionContext.isBound()
                        ? com.dogsbay.agent.session.SessionContext.current().identity() : null;
                ReviewCommands.Decision d = ReviewCommands.revert(xml,
                        identity == null ? null : snapshots.restorable(identity, target, xml).orElse(null),
                        com.dogsbay.dogsbayaieditor.validate.DocumentValidator.isDitaFile(target)
                                ? AgentProposalPolicy.validatorFor(target) : null);
                if (!d.text().equals(xml)) {
                    // an unchanged file keeps its time (watchers, previews)
                    FileEdits.writeIfUnchanged(target, xml, d.text(), openInEditor);
                }
                if (identity != null) {
                    snapshots.forget(identity, target);
                }
                java.nio.file.Path path = java.nio.file.Path.of(c.file());
                d.audit(ReviewCommands.auditFor(path), path);
                yield d.result(path);
            }
            case ReviewCommentCommand c -> {
                String xml = ReviewCommands.read(c.file());
                java.nio.file.Path target = java.nio.file.Path.of(c.file());
                ReviewCommands.write(c.file(), ReviewCommands.comment(xml, c.afterText(), c.elementId(), c.text(),
                        java.time.Clock.systemUTC(), com.dogsbay.dogsbayaieditor.validate.DocumentValidator
                                .isDitaFile(target) ? AgentProposalPolicy.validatorFor(target) : null));
                yield ReviewCommands.ok("Comment added to " + c.file());
            }
            case ListSessionsCommand c -> throw new CommandException(CommandException.ErrorCode.EDITOR_NOT_RUNNING,
                    "Sessions live in the running editor; use the editor connection");
            case ListAgentsCommand c -> throw new CommandException(CommandException.ErrorCode.EDITOR_NOT_RUNNING,
                    "The agent registry lives in the running editor; use the editor connection");
            case AuditLogCommand c -> executeAuditLog(c);
            case ValidateDeliverablesCommand c -> executeValidateDeliverables(c);
            case ValidateDeepCommand c -> executeValidateDeep(c);
            case BuildDeliverablesCommand c -> executeBuildDeliverables(c);
            case ValidateConditionsCommand c -> executeValidateConditions(c);
            case ListSubjectsCommand c -> executeListSubjects(c);
            case MetadataAuditCommand c -> executeMetadataAudit(c);
            case ExportMetadataSchematronCommand c -> executeExportMetadataSchematron(c);
            case MetadataSetCommand c -> executeMetadataSet(c);
            case SetAttributeCommand c -> executeSetAttribute(c);
            case EditMapCommand c -> executeEditMap(c);
            case ReltableAuditCommand c -> executeReltableAudit(c);
            case EditReltableCommand c -> executeEditReltable(c);
            case KeywordAuditCommand c -> executeKeywordAudit(c);
            case IndexAuditCommand c -> executeIndexAudit(c);
            case GlossaryAuditCommand c -> executeGlossaryAudit(
                    new GlossaryAuditCommand(c.root(), c.scope(), keyMap(c.root(), c.rootMap())));
            case ConrefPushAuditCommand c -> executeConrefPushAudit(
                    new ConrefPushAuditCommand(c.root(), c.scope(), keyMap(c.root(), c.rootMap())));
            case ChunkAuditCommand c -> executeChunkAudit(c);
            case SpecializationInfoCommand c -> executeSpecializationInfo(c);
            case ListBranchesCommand c -> executeListBranches(c);
            case RenameFileCommand c -> HeadlessRefactorCommands.executeRenameFile(c);
            case RenameKeyCommand c -> HeadlessRefactorCommands.executeRenameKey(c);
            case DeleteFileCommand c -> HeadlessRefactorCommands.executeDeleteFile(c);
            case RetargetCommand c -> HeadlessRefactorCommands.executeRetarget(c);
            case KeyifyCommand c -> HeadlessRefactorCommands.executeKeyify(c);
            case InlineKeyCommand c -> HeadlessRefactorCommands.executeInlineKey(c);
            case ExtractConrefCommand c -> HeadlessRefactorCommands.executeExtractConref(c);
            case CreateKeydefCommand c -> HeadlessRefactorCommands.executeCreateKeydef(c);
            case InlineConrefCommand c -> HeadlessRefactorCommands.executeInlineConref(c);
            case RenameElementIdCommand c -> HeadlessRefactorCommands.executeRenameElementId(c);
            case MergeKeydefsCommand c -> HeadlessRefactorCommands.executeMergeKeydefs(c);
            case RenameProfileValueCommand c -> HeadlessRefactorCommands.executeRenameProfileValue(c);
            case SplitTopicCommand c -> HeadlessRefactorCommands.executeSplitTopic(c);
            // Editor commands are not supported headless
            case OpenCommand c -> throw editorRequired();
            case CloseCommand c -> throw editorRequired();
            case SaveCommand c -> throw editorRequired();
            case ListDocumentsCommand c -> throw editorRequired();
            case GetContentCommand c when c.readClosed() && c.file() != null -> FileEdits.read(c.file());
            case GetContentCommand c -> throw editorRequired();
            // Without an editor there are no buffers: the file is the document
            case SetContentCommand c when c.file() != null -> FileEdits.writeAgentAware(c.file(),
                    FileEdits.read(c.file()), c.content(), agentProposals, snapshots, openInEditor);
            case SetContentCommand c -> throw editorRequired();
            case EditDocumentCommand c when c.file() != null -> {
                String text = FileEdits.read(c.file());
                TextEdit.Result edit = TextEdit.apply(text, c.find(), c.replace() == null ? "" : c.replace(),
                        Boolean.TRUE.equals(c.all()));
                if (!edit.applied()) {
                    throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, edit.error());
                }
                if (edit.text().equals(text)) {
                    yield EditorExecutor.unchanged(edit.occurrences());
                }
                yield EditorExecutor.withOccurrences(FileEdits.writeAgentAware(c.file(), text, edit.text(), agentProposals, snapshots, openInEditor),
                        edit.occurrences());
            }
            case EditDocumentCommand c -> throw editorRequired();
            case EditStructureCommand c -> {
                if (c.file() == null) {
                    throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, "file is required");
                }
                String text = FileEdits.read(c.file());
                com.dogsbay.agent.session.AgentSession session = com.dogsbay.agent.session.SessionContext.isBound()
                        ? com.dogsbay.agent.session.SessionContext.current() : null;
                StructuralEditPolicy.Outcome outcome = StructuralEditPolicy.decide(session, c.file(),
                        text, c.steps(), agentProposals, AgentProposalPolicy.validatorFor(c.file()));
                boolean dryRun = c.dryRun();
                if (!dryRun) {
                    FileEdits.writeIfUnchanged(c.file(), text, outcome.text(), openInEditor);
                    if (session != null && session.isAgent()) {
                        snapshots.recordWrite(session.identity(), c.file(), text, outcome.text(),
                                !com.dogsbay.xml.review.ProposalIndex.scan(text, session.identity()).isEmpty());
                    }
                }
                yield com.dogsbay.dogsbayaieditor.commands.results.CommandResult.ok(
                        StructuralEditPolicy.message(outcome, dryRun, session == null ? null : session.identity()));
            }
            case GetSelectionCommand c -> throw editorRequired();
            case ReplaceSelectionCommand c -> throw editorRequired();
            case GotoLineCommand c -> throw editorRequired();
            case GetCursorCommand c -> throw editorRequired();
            case SetCursorCommand c -> throw editorRequired();
            case SelectElementCommand c -> throw editorRequired();
            case GetOutlineCommand c -> throw editorRequired();
            case GetErrorsCommand c -> throw editorRequired();
            case SearchProjectCommand c -> throw editorRequired();
            case ListProjectFilesCommand c -> throw editorRequired();
            case CreateProjectCommand c -> throw editorRequired();
            case OpenProjectCommand c -> throw editorRequired();
            case ListProjectsCommand c -> throw editorRequired();
            case GetProjectCommand c -> throw editorRequired();
            case ListSidebarsCommand c -> throw editorRequired();
            case SwitchSidebarCommand c -> throw editorRequired();
            case InvokeActionCommand c -> throw editorRequired();
            case ClickButtonCommand c -> throw editorRequired();
            case LocateCommand c -> throw editorRequired();
            case LocateRangeCommand c -> throw editorRequired();
            case LocateNodeCommand c -> throw editorRequired();
            case RevealNodeCommand c -> throw editorRequired();
            case RevealCommand c -> throw editorRequired();
            case SelectPanelCommand c -> throw editorRequired();
            case WaitIdleCommand c -> throw editorRequired();
            case TypeTextCommand c -> throw editorRequired();
            case TerminalRunCommand c -> throw editorRequired();
            case OpenDitaMapCommand c -> throw editorRequired();
            case NewDocumentCommand c -> throw editorRequired();
            case AuthorSwitchCommand c -> throw editorRequired();
            case AuthorOutlineCommand c -> throw editorRequired();
            case AuthorInsertBlockCommand c -> throw editorRequired();
            case AuthorSetTextCommand c -> throw editorRequired();
            case AuthorIssuesCommand c -> throw editorRequired();
            case ScreenshotCommand c -> throw editorRequired();
            // Previews run in this process (the headless MCP server: until it ends); the registry makes them
            // visible to, and stoppable from, every other process (PreviewCommands)
            case LivePreviewCommand c -> {
                java.nio.file.Path root = PreviewCommands.root(c.root());
                if (PreviewCommands.action(c.action()).equals("stop")) {
                    yield PreviewCommands.stop(root, c.deliverable(),
                            com.dogsbay.dogsbayaieditor.dita.live.LivePreview::stop);
                }
                var deliverable = PreviewCommands.target(root, c.deliverable(),
                        blank(c.deliverable()) ? PreviewCommands.configuredDeliverable(root) : null, null);
                yield PreviewCommands.start(root, deliverable, null, (r, d, home) -> {
                    var preview = new com.dogsbay.dogsbayaieditor.dita.live.LivePreview(r, d, home);
                    preview.start();
                    return preview;
                }, com.dogsbay.dogsbayaieditor.dita.live.LivePreview::stop);
            }
            case LivePreviewStatusCommand c -> PreviewCommands.status(PreviewCommands.root(c.root()), c.deliverable());
        };
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private CommandException editorRequired() {
        return new CommandException(
            CommandException.ErrorCode.EDITOR_NOT_RUNNING,
            "This command requires a running editor instance"
        );
    }

    // ── Reuse analysis (where-used, keys, link health, preview) ─────────

    private java.util.List<ReferenceInfo> executeWhereUsed(WhereUsedCommand cmd)
            throws CommandException {
        java.io.File target = existingFile(cmd.file(), "file");
        java.io.File root = existingDir(cmd.root(), "root");

        com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex index =
                com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex.build(root, null);
        com.dogsbay.dogsbayaieditor.links.KeySpace keySpace = hasRootMap(cmd.rootMap())
                ? com.dogsbay.dogsbayaieditor.links.KeySpace.fromRootMap(
                        existingFile(cmd.rootMap(), "rootMap"))
                : com.dogsbay.dogsbayaieditor.links.KeySpace.empty();

        java.util.List<ReferenceInfo> out = new java.util.ArrayList<>();
        for (com.dogsbay.dogsbayaieditor.links.Reference ref
                : index.usagesOfIncludingKeys(target, keySpace)) {
            out.add(toReferenceInfo(ref));
        }
        return out;
    }

    private static ReferenceInfo toReferenceInfo(
            com.dogsbay.dogsbayaieditor.links.Reference ref) {
        return new ReferenceInfo(
                ref.source() != null ? ref.source().getAbsolutePath() : null,
                ref.line(), ref.element(), ref.attribute(), ref.rawValue(),
                com.dogsbay.dogsbayaieditor.whereused.ReferenceCategory
                        .classify(ref).getDisplayName(),
                ref.isKeyReference() ? ref.keyName() : null);
    }

    private java.util.List<KeyInfo> executeListKeys(ListKeysCommand cmd)
            throws CommandException {
        com.dogsbay.dogsbayaieditor.links.KeySpace keySpace =
                buildKeySpace(cmd.rootMap(), cmd.ditaval());
        java.util.List<KeyInfo> out = new java.util.ArrayList<>();
        keySpace.entries().forEach((name, def) -> out.add(toKeyInfo(name,
                com.dogsbay.dogsbayaieditor.links.KeySpace.scopeOf(name),
                def, keySpace.resolveHrefFile(name))));
        return out;
    }

    private KeyInfo executeResolveKey(ResolveKeyCommand cmd) throws CommandException {
        com.dogsbay.dogsbayaieditor.links.KeySpace keySpace =
                buildKeySpace(cmd.rootMap(), cmd.ditaval());
        String qualified = keySpace.resolveQualifiedName(cmd.key(), cmd.scope());
        com.dogsbay.dogsbayaieditor.links.KeyDefinition def =
                qualified == null ? null : keySpace.resolve(qualified);
        if (def == null) {
            throw new CommandException(CommandException.ErrorCode.FILE_NOT_FOUND,
                    "Key '" + cmd.key() + "' is not defined in " + cmd.rootMap()
                    + (cmd.scope() != null && !cmd.scope().isBlank()
                            ? " (scope '" + cmd.scope() + "')" : ""));
        }
        // Report the scope the key was actually found in (the outward walk may land
        // in an outer/root scope), not the scope the lookup started from.
        return toKeyInfo(cmd.key(),
                com.dogsbay.dogsbayaieditor.links.KeySpace.scopeOf(qualified), def,
                keySpace.resolveHrefFile(qualified));
    }

    private static KeyInfo toKeyInfo(String name, String scope,
            com.dogsbay.dogsbayaieditor.links.KeyDefinition def, java.io.File resolved) {
        return new KeyInfo(name,
                def.source() != null ? def.source().getAbsolutePath() : null,
                def.line(), def.href(),
                resolved != null && resolved.isFile() ? resolved.getAbsolutePath() : null,
                def.keywordText() != null ? def.keywordText() : def.linkText(), scope);
    }

    private com.dogsbay.dogsbayaieditor.links.KeySpace buildKeySpace(
            String rootMap, String ditaval) throws CommandException {
        java.io.File map = existingFile(rootMap, "rootMap");
        com.dogsbay.dogsbayaieditor.links.ElementExclusion exclusion = null;
        if (ditaval != null) {
            com.dogsbay.xml.browser.DitavalFilter filter =
                    com.dogsbay.xml.browser.DitavalFilter.parse(
                            existingFile(ditaval, "ditaval"));
            if (!filter.isEmpty()) {
                exclusion = filter::isExcluded;
            }
        }
        return com.dogsbay.dogsbayaieditor.links.KeySpace.fromRootMap(map, exclusion);
    }

    private java.util.List<BrokenRef> executeCheckLinks(CheckLinksCommand cmd)
            throws CommandException {
        java.io.File root = existingDir(cmd.root(), "root");
        com.dogsbay.dogsbayaieditor.links.KeySpace keySpace = cmd.rootMap() != null
                ? com.dogsbay.dogsbayaieditor.links.KeySpace.fromRootMap(
                        existingFile(cmd.rootMap(), "rootMap"))
                : null;

        com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex index =
                com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex.build(root, null);
        java.util.List<BrokenRef> broken = new java.util.ArrayList<>();
        for (com.dogsbay.dogsbayaieditor.links.Reference ref : index.allReferences()) {
            if (ref.isKeyReference()) {
                if (keySpace != null && keySpace.resolve(ref.keyName()) == null) {
                    broken.add(new BrokenRef(ref.source().getAbsolutePath(), ref.line(),
                            ref.attribute(), ref.rawValue(),
                            "key '" + ref.keyName() + "' not defined"));
                }
            } else if (ref.targetPath() != null
                    && !new java.io.File(ref.targetPath()).isFile()) {
                broken.add(new BrokenRef(ref.source().getAbsolutePath(), ref.line(),
                        ref.attribute(), ref.rawValue(), "target not found"));
            }
        }
        return broken;
    }

    /**
     * The project graph, with the audits that judge content rather than structure
     * folded into its issues when asked: a relationship map that draws an invalid
     * topic as healthy is the failure this command exists to prevent.
     */
    private com.dogsbay.dogsbayaieditor.graph.ProjectGraph executeProjectGraph(ProjectGraphCommand cmd)
            throws CommandException {
        java.nio.file.Path root = existingDir(cmd.root(), "root").toPath().toAbsolutePath().normalize();
        com.dogsbay.dogsbayaieditor.graph.ProjectGraph graph;
        try {
            graph = com.dogsbay.dogsbayaieditor.graph.ProjectGraphBuilder.build(root, cmd.map(), cmd.deliverable());
        } catch (IllegalArgumentException e) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, e.getMessage());
        } catch (java.io.IOException e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Could not build the project graph: " + e.getMessage(), e);
        }
        if (!cmd.checks()) {
            return graph;
        }

        List<com.dogsbay.dogsbayaieditor.graph.GraphIssue> issues = new ArrayList<>(graph.issues());

        // DTD validation of every shipped map and topic, once each.
        List<java.nio.file.Path> shipped = graph.nodes().stream()
                .filter(n -> ("map".equals(n.kind()) || "topic".equals(n.kind())) && !Boolean.TRUE.equals(n.missing())
                        && n.ships() != null && !n.ships().isEmpty())
                .map(n -> root.resolve(n.id()))
                .toList();
        for (java.nio.file.Path file : shipped) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (ValidationError e : validateOne(file, List.of())) {
                if (seen.add(e.line() + ":" + e.column() + ":" + e.message())) {
                    issues.add(new com.dogsbay.dogsbayaieditor.graph.GraphIssue(
                            "warning".equals(e.severity()) ? "warning" : "error", "invalid-dtd",
                            relativeTo(root, file.toString()), e.line() > 0 ? e.line() : null, e.message()));
                }
            }
        }

        // Element ids and conref push are judged against one key space: the map asked for, else the only deliverable's.
        // The audits resolve a relative map against the working directory; the graph resolved it against root.
        String rootMap = (cmd.map() == null || cmd.map().isBlank()) ? null
                : root.resolve(cmd.map()).toString();
        if (rootMap == null && graph.deliverables().size() == 1) {
            rootMap = root.resolve(graph.deliverables().get(0).map()).toString();
        }
        // The audits scan the project; a graph limited to a map or deliverable reports only its own files.
        boolean scoped = !"all".equals(graph.scope());
        java.util.Set<String> inGraph = graph.nodes().stream()
                .map(com.dogsbay.dogsbayaieditor.graph.GraphNode::id)
                .collect(java.util.stream.Collectors.toSet());
        for (BrokenRef ref : executeConrefAudit(new ConrefAuditCommand(root.toString(), rootMap))) {
            String file = relativeTo(root, ref.source());
            if (scoped && !inGraph.contains(file)) {
                continue;
            }
            issues.add(new com.dogsbay.dogsbayaieditor.graph.GraphIssue("error", "broken-element-id",
                    file, ref.line() > 0 ? ref.line() : null,
                    ref.attribute() + "=\"" + ref.value() + "\": " + ref.reason()));
        }
        String pushScope = rootMap == null ? null : "map:" + rootMap;
        for (var issue : executeConrefPushAudit(new ConrefPushAuditCommand(root.toString(), pushScope, rootMap)).issues()) {
            String file = relativeTo(root, issue.file());
            if (scoped && !inGraph.contains(file)) {
                continue;
            }
            issues.add(new com.dogsbay.dogsbayaieditor.graph.GraphIssue("warning", "conref-push",
                    file, issue.line() > 0 ? issue.line() : null,
                    "<" + issue.element() + ">: " + issue.problem()));
        }

        return new com.dogsbay.dogsbayaieditor.graph.ProjectGraph(graph.generated(), graph.root(), graph.scope(),
                graph.deliverables(), graph.nodes(), graph.edges(), issues);
    }

    /** A project-relative path with forward slashes when {@code path} is under {@code root}; else as given. */
    private static String relativeTo(java.nio.file.Path root, String path) {
        if (path == null) {
            return null;
        }
        java.nio.file.Path p = java.nio.file.Path.of(path).toAbsolutePath().normalize();
        return p.startsWith(root) ? root.relativize(p).toString().replace('\\', '/') : path;
    }

    /** Fill a report template with the command's JSON and write the page. */
    private com.dogsbay.dogsbayaieditor.commands.results.ReportResult executeRenderReport(RenderReportCommand cmd)
            throws CommandException {
        java.nio.file.Path root = (cmd.root() == null || cmd.root().isBlank())
                ? null : existingDir(cmd.root(), "root").toPath();
        if (cmd.output() == null || cmd.output().isBlank()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "output is required: a report is written to a file");
        }
        if (cmd.data() == null || cmd.data().isBlank()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "No data to render: name a source (a read-only tool such as project_graph) or pass data");
        }
        if (cmd.template() == null || cmd.template().isBlank()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Name a template: one of " + com.dogsbay.dogsbayaieditor.reports.ReportRenderer.builtInTemplates()
                    + ", or a path such as .dogsbay/reports/mine.html");
        }
        java.nio.file.Path out = java.nio.file.Path.of(cmd.output());
        if (!out.isAbsolute() && root != null) {
            out = root.resolve(out);
        }
        out = out.toAbsolutePath().normalize();

        java.util.Map<String, String> meta = new java.util.LinkedHashMap<>();
        meta.put("generated", java.time.Instant.now().toString());
        try {
            meta.put("editorVersion", com.dogsbay.dogsbayaieditor.Identity.getIdentity().getVersion());
        } catch (RuntimeException | LinkageError noIdentity) {
            // headless runs without the editor identity still render
        }
        if (cmd.source() != null) {
            meta.put("source", cmd.source());
        }

        String html;
        try {
            String template = com.dogsbay.dogsbayaieditor.reports.ReportRenderer.loadTemplate(cmd.template(), root);
            html = com.dogsbay.dogsbayaieditor.reports.ReportRenderer.render(template, cmd.data(), meta);
        } catch (IllegalArgumentException e) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, e.getMessage());
        }
        try {
            com.dogsbay.dogsbayaieditor.reports.ReportRenderer.write(out, html);
        } catch (Exception e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Cannot write " + out + ": " + e.getMessage(), e);
        }
        return new com.dogsbay.dogsbayaieditor.commands.results.ReportResult(out.toString(), cmd.template(),
                cmd.source(), html.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    private PreviewRenderResult executeRenderPreview(RenderPreviewCommand cmd)
            throws CommandException {
        java.io.File file = existingFile(cmd.file(), "file");
        String text;
        try {
            text = java.nio.file.Files.readString(file.toPath());
        } catch (java.io.IOException e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Cannot read " + file + ": " + e.getMessage());
        }
        if (!com.dogsbay.xml.browser.DitaConverter.isDita(text)) {
            throw new CommandException(CommandException.ErrorCode.PARSE_ERROR,
                    file + " is not a DITA topic or map");
        }
        com.dogsbay.xml.browser.PreviewOptions options =
                new com.dogsbay.xml.browser.PreviewOptions(
                        cmd.map() != null ? existingFile(cmd.map(), "map") : null,
                        cmd.ditaval() != null ? existingFile(cmd.ditaval(), "ditaval") : null,
                        cmd.showChanges());
        String html = com.dogsbay.xml.browser.DitaConverter.convert(
                text, file.getParentFile(), options);

        if (cmd.output() != null) {
            java.io.File out = new java.io.File(cmd.output());
            try {
                java.nio.file.Files.writeString(out.toPath(), html);
            } catch (java.io.IOException e) {
                throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                        "Cannot write " + out + ": " + e.getMessage());
            }
            return new PreviewRenderResult(out.getAbsolutePath(), null);
        }
        return new PreviewRenderResult(null, html);
    }

    /**
     * Links in built output that lead nowhere.
     *
     * <p>Needs no editor and no project: the output is a folder of files, and the question is whether it is whole in
     * itself.
     */
    private java.util.List<com.dogsbay.dogsbayaieditor.commands.results.BrokenRef> executeCheckOutputLinks(
            CheckOutputLinksCommand cmd) throws CommandException {
        java.io.File dir = existingDir(cmd.dir(), "dir");
        try {
            return OutputLinkChecker.check(dir.toPath());
        } catch (java.io.IOException e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                "Could not read the built output: " + e.getMessage());
        }
    }

    private HealthReport executeHealth(HealthCommand cmd) throws CommandException {
        return executeHealth(cmd, null);
    }

    /**
     * Reuse health, optionally for one map's publication set.
     *
     * <p>{@code inScope} narrows which files' <em>references</em> are reported: asked about one deliverable, a broken
     * href in a topic that deliverable does not publish is not its fault, and in a repository holding several stages
     * of the same tutorial it is somebody else's file entirely.
     *
     * <p>Orphans are deliberately not narrowed. An orphan is a file the map does not reach, so scoping the search to
     * what the map reaches would mean never finding one — and a topic left out of the map is the commonest mistake
     * there is.
     */
    private HealthReport executeHealth(HealthCommand cmd, java.util.Set<java.io.File> inScope)
            throws CommandException {
        java.io.File root = existingDir(cmd.root(), "root");
        com.dogsbay.dogsbayaieditor.links.KeySpace keySpace = hasRootMap(cmd.rootMap())
                ? com.dogsbay.dogsbayaieditor.links.KeySpace.fromRootMap(
                        existingFile(cmd.rootMap(), "rootMap"))
                : null;
        com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex index =
                com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex.build(root, null);

        java.util.List<BrokenRef> brokenTargets = new java.util.ArrayList<>();
        java.util.List<BrokenRef> undefinedKeys = new java.util.ArrayList<>();
        for (com.dogsbay.dogsbayaieditor.links.Reference ref : index.allReferences()) {
            if (inScope != null && !inScope.contains(canonical(ref.source()))) {
                continue;
            }
            if (ref.isKeyReference()) {
                if (keySpace != null && keySpace.resolve(ref.keyName()) == null) {
                    undefinedKeys.add(new BrokenRef(ref.source().getAbsolutePath(),
                            ref.line(), ref.attribute(), ref.rawValue(),
                            "key '" + ref.keyName() + "' not defined"));
                }
            } else if (ref.targetPath() != null
                    && !new java.io.File(ref.targetPath()).isFile()) {
                brokenTargets.add(new BrokenRef(ref.source().getAbsolutePath(),
                        ref.line(), ref.attribute(), ref.rawValue(), "target not found"));
            }
        }

        java.util.List<KeyInfo> unusedKeys = new java.util.ArrayList<>();
        if (keySpace != null) {
            // Scope-aware: a key used via a bare keyref under @keyscope binds to the
            // scoped definition, which a literal usagesOfKey(qualifiedName) would miss.
            java.util.Set<com.dogsbay.dogsbayaieditor.links.KeyDefinition> used =
                    index.usedKeyDefinitions(keySpace);
            for (java.util.Map.Entry<String, com.dogsbay.dogsbayaieditor.links.KeyDefinition>
                    entry : keySpace.entries().entrySet()) {
                // subjectScheme subjectdef keys are a controlled-value vocabulary, not
                // content keys an author keyrefs — wiring the scheme into a map must not
                // make every governed value report as an "unused key" (DITA 1.3 §A5).
                if (entry.getValue().subjectScheme()) {
                    continue;
                }
                if (!used.contains(entry.getValue())) {
                    unusedKeys.add(toKeyInfo(entry.getKey(),
                            com.dogsbay.dogsbayaieditor.links.KeySpace.scopeOf(entry.getKey()),
                            entry.getValue(), keySpace.resolveHrefFile(entry.getKey())));
                }
            }
        }

        // Orphan topics: .dita files nothing references (directly or via a
        // key). Maps are entry points, not orphans.
        com.dogsbay.dogsbayaieditor.links.KeySpace orphanKeySpace = keySpace != null
                ? keySpace : com.dogsbay.dogsbayaieditor.links.KeySpace.empty();
        java.util.List<String> orphans = new java.util.ArrayList<>();
        for (java.io.File file : index.indexedFiles()) {
            if (!file.getName().toLowerCase().endsWith(".dita")) {
                continue;
            }
            if (index.usagesOfIncludingKeys(file, orphanKeySpace).isEmpty()) {
                orphans.add(file.getAbsolutePath());
            }
        }
        java.util.Collections.sort(orphans);

        return new HealthReport(brokenTargets, undefinedKeys, unusedKeys, orphans);
    }


    /** A file by the one name every other path for it also resolves to. */
    private static java.io.File canonical(java.io.File file) {
        try {
            return file.getCanonicalFile();
        } catch (java.io.IOException cannotResolve) {
            return file.getAbsoluteFile();
        }
    }

    static java.io.File existingFile(String path, String what)
            throws CommandException {
        if (path == null || path.isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Missing required " + what);
        }
        // Absolutize: refactor engines compute relative paths from parent
        // directories, and a relative File has no parent.
        java.io.File file = new java.io.File(path).getAbsoluteFile();
        if (!file.isFile()) {
            throw new CommandException(CommandException.ErrorCode.FILE_NOT_FOUND,
                    what + " not found: " + path);
        }
        return file;
    }

    static java.io.File existingDir(String path, String what)
            throws CommandException {
        if (path == null || path.isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Missing required " + what);
        }
        java.io.File dir = new java.io.File(path).toPath().toAbsolutePath().normalize().toFile();
        if (!dir.isDirectory()) {
            throw new CommandException(CommandException.ErrorCode.FILE_NOT_FOUND,
                    what + " directory not found: " + path);
        }
        return dir;
    }


    /**
     * Validate every file in a scope on disk (no documents opened). Resolves the
     * scope via {@link FileSet}, validates each file through the shared
     * {@link DocumentValidator}, and returns a compact {@link BatchResult} whose
     * findings are the failing files. A single file's setup failure becomes a
     * finding, not an aborted batch.
     */
    private BatchResult<FileValidation> executeValidateProject(ValidateProjectCommand cmd)
            throws CommandException {
        java.nio.file.Path root = existingDir(cmd.root(), "root").toPath();
        List<java.nio.file.Path> files;
        try {
            files = FileSet.resolve(root, cmd.scope());
        } catch (Exception e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to resolve scope '" + cmd.scope() + "': " + e.getMessage(), e);
        }
        return validateFiles(files, cmd.catalogs());
    }

    /**
     * Validate every deliverable of a project: resolve the DITA project
     * (project.&#123;xml,json,yaml&#125; or a synthesized default), and validate
     * each deliverable's map publication set. Counts per deliverable; each file is
     * validated once however many deliverables ship it, and each finding lists them.
     */
    private com.dogsbay.dogsbayaieditor.commands.results.DeliverablesReport
            executeValidateDeliverables(ValidateDeliverablesCommand cmd) throws CommandException {
        java.nio.file.Path root = existingDir(cmd.root(), "root").toPath();

        List<java.nio.file.Path> catalogs = new ArrayList<>();
        java.nio.file.Path bundled = DocumentValidator.builtinDitaCatalog();
        if (bundled != null) {
            catalogs.add(bundled);
        }

        com.dogsbay.dogsbayaieditor.ditaproject.ProjectContext ctx;
        try {
            ctx = com.dogsbay.dogsbayaieditor.ditaproject.ProjectContextLoader
                    .load(root, null, catalogs, null);
        } catch (Exception e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to load project context: " + e.getMessage(), e);
        }

        java.util.Map<java.nio.file.Path, List<ValidationError>> errorsByFile = new java.util.HashMap<>();
        java.util.Map<java.nio.file.Path, java.util.LinkedHashSet<String>> failingIn = new java.util.LinkedHashMap<>();
        List<com.dogsbay.dogsbayaieditor.commands.results.DeliverablesReport.Deliverable> summaries =
                new ArrayList<>();
        for (com.dogsbay.dogsbayaieditor.ditaproject.Deliverable d : ctx.deliverables()) {
            List<java.nio.file.Path> files = FileSet.crawlMap(root, d.map());
            int passed = 0;
            for (java.nio.file.Path f : files) {
                List<ValidationError> errors = errorsByFile.computeIfAbsent(f, p -> validateOne(p, catalogs));
                if (errors.isEmpty()) {
                    passed++;
                } else {
                    failingIn.computeIfAbsent(f, p -> new java.util.LinkedHashSet<>()).add(d.name());
                }
            }
            summaries.add(new com.dogsbay.dogsbayaieditor.commands.results.DeliverablesReport.Deliverable(
                    d.name(), d.map().toString(), files.size(), passed, files.size() - passed));
        }

        List<com.dogsbay.dogsbayaieditor.commands.results.DeliverablesReport.Finding> findings = new ArrayList<>();
        for (var entry : failingIn.entrySet()) {
            List<String> deliverables = List.copyOf(entry.getValue());
            // The parser can report one fatal error twice (the handler, then the thrown exception).
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (ValidationError e : errorsByFile.get(entry.getKey())) {
                if (!seen.add(e.line() + ":" + e.column() + ":" + e.message())) {
                    continue;
                }
                findings.add(new com.dogsbay.dogsbayaieditor.commands.results.DeliverablesReport.Finding(
                        entry.getKey().toString(), e.line(), e.column(), e.severity(), e.message(), deliverables));
            }
        }
        int cap = 200;
        int truncated = Math.max(0, findings.size() - cap);
        return new com.dogsbay.dogsbayaieditor.commands.results.DeliverablesReport(summaries,
                truncated > 0 ? findings.subList(0, cap) : findings, truncated);
    }

    /**
     * One file's validation errors, empty when it validates. Messages that spell
     * out a whole content model are shortened: a report over many files repeated
     * the same enumeration in every finding.
     */
    /** {@code text} validated as the content of {@code f}; when that cannot run, the file as it stands. */
    private static List<ValidationError> validateText(java.nio.file.Path f, String text,
            List<java.nio.file.Path> catalogs) {
        ValidationResult vr;
        try {
            vr = DocumentValidator.validateText(f, text);
        } catch (DocumentValidator.ValidationSetupException e) {
            return validateOne(f, catalogs);
        }
        return vr.errors().stream()
                .map(e -> new ValidationError(e.line(), e.column(), e.severity(),
                        com.dogsbay.dogsbayaieditor.validate.ValidationMessages.compact(e.message()), e.source()))
                .toList();
    }

    private static List<ValidationError> validateOne(java.nio.file.Path f, List<java.nio.file.Path> catalogs) {
        ValidationResult vr;
        try {
            vr = DocumentValidator.validate(f, null, catalogs);
        } catch (DocumentValidator.ValidationSetupException e) {
            return List.of(new ValidationError(-1, -1, "error", e.getMessage(), "validator"));
        }
        if (vr.valid()) {
            return List.of();
        }
        return vr.errors().stream()
                .map(e -> new ValidationError(e.line(), e.column(), e.severity(),
                        com.dogsbay.dogsbayaieditor.validate.ValidationMessages.compact(e.message()), e.source()))
                .toList();
    }

    /**
     * Deep-validate deliverables with DITA-OT's {@code validate} transtype (T3/T4).
     * Resolves the DITA project, picks the chosen deliverable (or all), and runs
     * each through {@link com.dogsbay.xml.dita.DitaOtValidator} with the
     * deliverable's DITAVAL applied. Requires a configured DITA-OT engine.
     */
    private List<com.dogsbay.dogsbayaieditor.commands.results.DitaOtValidation>
            executeValidateDeep(ValidateDeepCommand cmd) throws CommandException {
        java.nio.file.Path root = existingDir(cmd.root(), "root").toPath();

        List<java.nio.file.Path> catalogs = new ArrayList<>();
        java.nio.file.Path bundled = DocumentValidator.builtinDitaCatalog();
        if (bundled != null) {
            catalogs.add(bundled);
        }

        // The path given, else the one the editor would use: the project's local.xml path, the framework
        // config.xml names, or the bundled DITA-OT, installed under ~/.dogsbay/frameworks.
        java.nio.file.Path otOverride = DitaOtHome.resolve(root,
                (cmd.ditaOtHome() != null && !cmd.ditaOtHome().isBlank()) ? java.nio.file.Path.of(cmd.ditaOtHome()) : null);

        com.dogsbay.dogsbayaieditor.ditaproject.ProjectContext ctx;
        try {
            ctx = com.dogsbay.dogsbayaieditor.ditaproject.ProjectContextLoader
                    .load(root, null, catalogs, otOverride);
        } catch (Exception e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to load project context: " + e.getMessage(), e);
        }

        java.nio.file.Path otPath = ctx.ditaOtPath();
        if (otPath == null) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "DITA-OT is not configured for this project. Import the DITA-OT "
                    + "framework in the editor, or pass the DITA-OT home (--dita-ot), before deep validation.");
        }

        List<com.dogsbay.dogsbayaieditor.ditaproject.Deliverable> targets;
        if (cmd.map() != null && !cmd.map().isBlank()) {
            // Validate one specific map (the editor's "Current Map" action). Borrow
            // the DITAVAL of the deliverable that ships it, if any; else unfiltered.
            java.nio.file.Path mapPath = java.nio.file.Path.of(cmd.map());
            if (!mapPath.isAbsolute()) {
                mapPath = root.resolve(mapPath);
            }
            if (!java.nio.file.Files.exists(mapPath)) {
                throw new CommandException(CommandException.ErrorCode.FILE_NOT_FOUND,
                        "Map not found: " + cmd.map());
            }
            java.nio.file.Path canonical = canonicalize(mapPath);
            com.dogsbay.dogsbayaieditor.ditaproject.Deliverable match = null;
            for (com.dogsbay.dogsbayaieditor.ditaproject.Deliverable d : ctx.deliverables()) {
                if (canonicalize(d.map()).equals(canonical)) {
                    match = d;
                    break;
                }
            }
            targets = List.of(match != null ? match
                    : new com.dogsbay.dogsbayaieditor.ditaproject.Deliverable(
                            mapPath.getFileName().toString(), mapPath, null, null));
        } else if (cmd.deliverable() != null && !cmd.deliverable().isBlank()) {
            com.dogsbay.dogsbayaieditor.ditaproject.Deliverable d =
                    ctx.deliverable(cmd.deliverable());
            if (d == null) {
                throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                        "No deliverable named '" + cmd.deliverable() + "' in this project.");
            }
            targets = List.of(d);
        } else {
            targets = ctx.deliverables();
        }
        if (targets.isEmpty()) {
            return List.of();
        }

        com.dogsbay.xml.dita.DitaOtValidator validator;
        try {
            validator = new com.dogsbay.xml.dita.DitaOtValidator(otPath.toString());
        } catch (java.io.IOException e) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "DITA-OT home is invalid: " + e.getMessage(), e);
        }

        java.nio.file.Path tempBase;
        try {
            tempBase = java.nio.file.Files.createTempDirectory("dogsbay-validate-ot");
        } catch (java.io.IOException e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to create a working directory: " + e.getMessage(), e);
        }

        List<com.dogsbay.dogsbayaieditor.commands.results.DitaOtValidation> out =
                new ArrayList<>();
        try {
            for (com.dogsbay.dogsbayaieditor.ditaproject.Deliverable d : targets) {
                java.io.File tempDir = tempBase.resolve(d.name()).toFile();
                tempDir.mkdirs();
                // Apply ALL of the deliverable's ditavals (context + publication
                // profiles) so deep-validate filters the same content publish ships.
                List<java.io.File> ditavals = withReviewFilter(d.ditavals(), tempDir.toPath());
                List<com.dogsbay.xml.dita.DitaOtMessage> msgs =
                        validator.validate(d.map().toFile(), ditavals, tempDir);
                boolean success = msgs.stream()
                        .noneMatch(com.dogsbay.xml.dita.DitaOtMessage::isError);
                out.add(new com.dogsbay.dogsbayaieditor.commands.results.DitaOtValidation(
                        d.name(), d.map().toString(),
                        d.ditaval() != null ? d.ditaval().toString() : null, success, msgs));
            }
        } finally {
            deleteRecursively(tempBase);
        }
        return out;
    }

    /** Best-effort recursive delete of a temp tree (deepest-first); never throws. */
    private static void deleteRecursively(java.nio.file.Path root) {
        if (root == null) {
            return;
        }
        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    java.nio.file.Files.deleteIfExists(p);
                } catch (java.io.IOException ignore) {
                    // leave what we can't remove
                }
            });
        } catch (java.io.IOException ignore) {
            // tree already gone / unreadable
        }
    }

    /**
     * Build deliverables with DITA-OT — each with its transtype, DITAVAL(s),
     * params, and output dir. One named deliverable or all; per-deliverable result.
     */
    private List<com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild>
            executeBuildDeliverables(BuildDeliverablesCommand cmd) throws CommandException {
        java.nio.file.Path root = existingDir(cmd.root(), "root").toPath();

        List<java.nio.file.Path> catalogs = new ArrayList<>();
        java.nio.file.Path bundled = DocumentValidator.builtinDitaCatalog();
        if (bundled != null) {
            catalogs.add(bundled);
        }
        // The path given, else the one the editor would use: the project's local.xml path, the framework
        // config.xml names, or the bundled DITA-OT, installed under ~/.dogsbay/frameworks.
        java.nio.file.Path otOverride = DitaOtHome.resolve(root,
                (cmd.ditaOtHome() != null && !cmd.ditaOtHome().isBlank()) ? java.nio.file.Path.of(cmd.ditaOtHome()) : null);

        com.dogsbay.dogsbayaieditor.ditaproject.ProjectContext ctx;
        try {
            ctx = com.dogsbay.dogsbayaieditor.ditaproject.ProjectContextLoader
                    .load(root, null, catalogs, otOverride);
        } catch (Exception e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to load project context: " + e.getMessage(), e);
        }

        java.nio.file.Path otPath = ctx.ditaOtPath();
        if (otPath == null) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "DITA-OT is not configured for this project. Import the DITA-OT "
                    + "framework in the editor, or pass the DITA-OT home (--dita-ot), before building.");
        }

        List<com.dogsbay.dogsbayaieditor.ditaproject.Deliverable> targets;
        if (cmd.deliverableNames() != null && !cmd.deliverableNames().isEmpty()) {
            // Build every deliverable whose name is selected — by iterating the actual
            // deliverables (not the names), so same-named deliverables all build.
            java.util.Set<String> wanted = new java.util.HashSet<>(cmd.deliverableNames());
            targets = ctx.deliverables().stream()
                    .filter(d -> wanted.contains(d.name()))
                    .toList();
            if (targets.isEmpty()) {
                throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                        "None of the selected deliverables exist in this project.");
            }
        } else if (cmd.deliverable() != null && !cmd.deliverable().isBlank()) {
            com.dogsbay.dogsbayaieditor.ditaproject.Deliverable d =
                    ctx.deliverable(cmd.deliverable());
            if (d == null) {
                throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                        "No deliverable named '" + cmd.deliverable() + "' in this project.");
            }
            targets = List.of(d);
        } else {
            targets = ctx.deliverables();
        }
        if (targets.isEmpty()) {
            return List.of();
        }

        com.dogsbay.xml.dita.DitaOtBuilder builder;
        try {
            builder = new com.dogsbay.xml.dita.DitaOtBuilder(otPath.toString());
        } catch (java.io.IOException e) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "DITA-OT home is invalid: " + e.getMessage(), e);
        }

        java.nio.file.Path tempBase;
        try {
            tempBase = java.nio.file.Files.createTempDirectory("dogsbay-build");
        } catch (java.io.IOException e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to create a working directory: " + e.getMessage(), e);
        }

        List<com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild> out =
                new ArrayList<>();
        // Kept temporary files are reported as absolute paths, one folder per deliverable.
        java.nio.file.Path tempRoot = root.toAbsolutePath().normalize();
        java.util.Set<String> usedTempDirs = new java.util.HashSet<>();
        try {
            for (com.dogsbay.dogsbayaieditor.ditaproject.Deliverable d : targets) {
                java.io.File outputDir = resolveBuildOutputDir(cmd.outputBaseDir(), root, d).toFile();
                containBuildOutput(root, outputDir.toPath(), d.name());
                java.io.File workDir = tempBase.resolve(d.name()).toFile();
                List<java.io.File> ditavals = withReviewFilter(d.ditavals(), workDir.toPath());
                Map<String, String> params = resolveDeliverableParams(d, root);
                if (cmd.keepTemp() != null) {
                    params.put(com.dogsbay.xml.dita.DitaOtBuilder.CLEAN_TEMP, cmd.keepTemp() ? "no" : "yes");
                }
                if (cmd.changedPaths() != null) {
                    params.put(com.dogsbay.xml.dita.DogsbayPublish.CHANGED_PATHS_PARAM, cmd.changedPaths());
                }
                // Kept temporary files go in the project, replacing the previous build's;
                // otherwise DITA-OT works under the scratch folder deleted below.
                boolean keep = com.dogsbay.xml.dita.DitaOtBuilder.keepsTemp(params);
                java.io.File tempDir = workDir;
                if (keep) {
                    java.nio.file.Path kept = DitaOtTemp.uniqueDir(tempRoot, d.name(), usedTempDirs);
                    try {
                        tempDir = DitaOtTemp.prepare(tempRoot, kept).toFile();
                    } catch (java.io.IOException e) {
                        // A locked folder (open in a file browser, a file in use) fails this
                        // deliverable only; the others still build.
                        out.add(new com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild(
                                d.name(), d.transtype() != null ? d.transtype() : "html5",
                                outputDir.toString(), false,
                                List.of(new com.dogsbay.xml.dita.DitaOtMessage(null, "FATAL",
                                        "Could not prepare the temporary folder " + kept + ": " + e.getMessage(),
                                        kept.toString(), -1, -1)),
                                null));
                        continue;
                    }
                }
                com.dogsbay.xml.dita.BuildProgress.deliverable(d.name());
                String transtype = cmd.transtype() != null && !cmd.transtype().isBlank() ? cmd.transtype() : d.transtype();
                com.dogsbay.xml.dita.DitaOtBuilder.Outcome outcome = builder.buildTimed(
                        d.map().toFile(), transtype, ditavals, params, outputDir, tempDir);
                List<com.dogsbay.xml.dita.DitaOtMessage> msgs = outcome.messages();
                boolean success = msgs.stream()
                        .noneMatch(com.dogsbay.xml.dita.DitaOtMessage::isError);
                var build = new com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild(
                        d.name(), transtype != null ? transtype : "html5",
                        outputDir.toString(), success, msgs, keep ? tempDir.toString() : null,
                        outcome.steps(), outcome.engine());
                out.add(build);
                // Kept per project for comparing builds over time (and engines)
                BuildTimes.append(tempRoot, build);
            }
        } finally {
            deleteRecursively(tempBase);
        }
        return out;
    }

    /** Where a deliverable's output goes: {@code outputBaseDir/<name>} when given,
     *  else the deliverable's declared {@code <output>}, else {@code <root>/out/<name>}. */
    private static java.nio.file.Path resolveBuildOutputDir(String outputBaseDir,
            java.nio.file.Path root, com.dogsbay.dogsbayaieditor.ditaproject.Deliverable d) {
        if (outputBaseDir != null && !outputBaseDir.isBlank()) {
            java.nio.file.Path base = java.nio.file.Path.of(outputBaseDir);
            if (!base.isAbsolute()) {
                base = root.resolve(base);
            }
            java.nio.file.Path out = base.resolve(d.name()).normalize();
            if (!out.startsWith(base.normalize()) || out.equals(base.normalize())) {
                throw new IllegalArgumentException("deliverable name \"" + d.name() + "\" leads out of the output folder");
            }
            return out;
        }
        return d.outputFolder(root);
    }

    /**
     * A hosted or external agent's build writes only inside the project (review, 2026-09-18: a deliverable's
     * output folder comes from the project file, which the agent can edit, and could name any folder).
     */
    private static void containBuildOutput(java.nio.file.Path root, java.nio.file.Path outputDir, String name)
            throws CommandException {
        if (!com.dogsbay.agent.session.SessionContext.isBound()) {
            return;
        }
        var session = com.dogsbay.agent.session.SessionContext.current();
        if (!session.isAgent() || session.kind() == com.dogsbay.agent.session.SessionKind.BUILTIN) {
            return;
        }
        if (!WriteGate.real(outputDir).startsWith(WriteGate.real(root))) {
            throw new CommandException(CommandException.ErrorCode.PERMISSION_DENIED, "The output folder of deliverable \""
                    + name + "\" (" + outputDir + ") is outside the project; " + session.displayName()
                    + " may only build into it");
        }
    }

    /** A deliverable's publication params, with HREF/PATH kinds resolved to
     *  absolute paths against the deliverable's project file (or the root). */
    private static Map<String, String> resolveDeliverableParams(
            com.dogsbay.dogsbayaieditor.ditaproject.Deliverable d, java.nio.file.Path root) {
        java.nio.file.Path base = d.sourceFile() != null ? d.sourceFile().getParent() : root;
        Map<String, String> params = new java.util.LinkedHashMap<>();
        for (com.dogsbay.dogsbayaieditor.ditaproject.Param p : d.params()) {
            params.put(p.name(), p.resolve(base));
        }
        return params;
    }

    /** Canonical (symlink-resolved) path for reliable equality, falling back to a
     * normalized absolute path when the file can't be resolved. */
    private static java.nio.file.Path canonicalize(java.nio.file.Path p) {
        try {
            return p.toRealPath();
        } catch (java.io.IOException e) {
            return p.toAbsolutePath().normalize();
        }
    }

    /**
     * Validate a set of files through the shared {@link DocumentValidator} and
     * aggregate into a compact {@link BatchResult} (findings = failing files).
     * A single file's setup failure becomes a finding, not an aborted batch.
     */
    private BatchResult<FileValidation> validateFiles(List<java.nio.file.Path> files,
            List<java.nio.file.Path> catalogs) {
        return validateFiles(files, catalogs, java.util.Map.of());
    }

    /** As {@link #validateFiles(List, List)}, validating the text in {@code overlay} for the files it holds. */
    private BatchResult<FileValidation> validateFiles(List<java.nio.file.Path> files,
            List<java.nio.file.Path> catalogs, java.util.Map<java.nio.file.Path, String> overlay) {
        List<FileValidation> failures = new ArrayList<>();
        int passed = 0;
        for (java.nio.file.Path f : files) {
            String text = overlay.isEmpty() ? null : overlay.get(HealthChecks.key(f));
            List<ValidationError> errors = text == null ? validateOne(f, catalogs) : validateText(f, text, catalogs);
            if (errors.isEmpty()) {
                passed++;
            } else {
                failures.add(new FileValidation(f.toString(), false, errors));
            }
        }
        return BatchResult.capped(files.size(), passed, files.size() - passed, failures, 200);
    }

    /** The audit log under {@code root}, newest first, filtered and capped as asked. */
    private List<com.dogsbay.dogsbayaieditor.commands.results.AuditEntryInfo> executeAuditLog(AuditLogCommand cmd)
            throws CommandException {
        java.nio.file.Path root = existingDir(cmd.root(), "root").toPath();
        List<com.dogsbay.agent.session.AuditLog.Entry> all;
        try {
            all = com.dogsbay.agent.session.AuditLog.read(root);
        } catch (java.io.UncheckedIOException e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Could not read the audit log: " + e.getMessage());
        }
        List<com.dogsbay.dogsbayaieditor.commands.results.AuditEntryInfo> out = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            com.dogsbay.agent.session.AuditLog.Entry e = all.get(i);
            if (cmd.identity() != null && !cmd.identity().isBlank() && !cmd.identity().equals(e.identity())) {
                continue;
            }
            if (cmd.command() != null && !cmd.command().isBlank() && !cmd.command().equals(e.command())) {
                continue;
            }
            out.add(new com.dogsbay.dogsbayaieditor.commands.results.AuditEntryInfo(e.at().toString(),
                    e.sessionId(), e.kind() == null ? null : e.kind().name(), e.identity(), e.displayName(),
                    e.command(), e.files(), e.dryRun(), e.outcome(), e.detail()));
            if (cmd.limit() > 0 && out.size() >= cmd.limit()) {
                break;
            }
        }
        return out;
    }

    /**
     * The generated review filter first, then the deliverable's DITAVALs, so
     * text an open proposal deletes never reaches output and a project rule
     * on the same key cannot override it (DITA-OT keeps the first rule).
     */
    static List<java.io.File> withReviewFilter(List<java.nio.file.Path> ditavals, java.nio.file.Path workDir) {
        return com.dogsbay.xml.review.ReviewDitaval.prepend(ditavals, workDir);
    }

    /** Files in scope that still carry open proposals, with who proposed. */
    static List<com.dogsbay.dogsbayaieditor.commands.results.OpenProposals> openProposals(
            List<java.nio.file.Path> files) {
        List<com.dogsbay.dogsbayaieditor.commands.results.OpenProposals> out = new ArrayList<>();
        for (java.nio.file.Path f : files) {
            String name = f.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            if (!(name.endsWith(".dita") || name.endsWith(".ditamap") || name.endsWith(".xml")
                    || name.endsWith(".bookmap"))) {
                continue;
            }
            String text;
            try {
                text = java.nio.file.Files.readString(f, java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                continue;
            }
            if (!com.dogsbay.xml.review.ProposalIndex.mayHaveProposals(text)) {
                continue;
            }
            List<com.dogsbay.xml.review.Proposal> ps = com.dogsbay.xml.review.ProposalIndex.scanOpen(text);
            if (ps.isEmpty()) {
                continue;
            }
            int changes = (int) ps.stream().filter(com.dogsbay.xml.review.Proposal::isChange).count();
            List<String> authors = ps.stream().map(com.dogsbay.xml.review.Proposal::author)
                    .filter(java.util.Objects::nonNull).distinct().sorted().toList();
            out.add(new com.dogsbay.dogsbayaieditor.commands.results.OpenProposals(f.toString(), changes,
                    ps.size() - changes, authors));
        }
        return out;
    }

    /**
     * Comprehensive project health: reuse-health plus DTD/grammar validation of the scope, element ids,
     * the metadata policy, the project's house rules (Schematron), conref pushes, the index, house-style
     * formatting and authoring leftovers. {@code clean} is true only when every blocking leg passes; open
     * review proposals are listed but do not make it false.
     *
     * <p>The root map and house rules are the caller's, else the project's ({@link ProjectDefaults}); the
     * report says which ran. Files with open review proposals are judged by the text that would ship
     * (every change accepted): a finding only on the side that accepting removes is a pending fix, not an
     * error (2026-09-18: fixes proposed by an agent kept being reported until the writer accepted them).
     */
    /**
     * Project health, then the build, then the built output — each stage only when the one before it passed.
     *
     * <p>The stages are named in the report, so one that did not run says nothing rather than saying "clean".
     */
    private com.dogsbay.dogsbayaieditor.commands.results.ProjectCheck executeCheckProject(CheckProjectCommand cmd)
            throws CommandException {
        java.util.List<String> stages = new java.util.ArrayList<>();

        stages.add("health");
        ProjectHealthReport health = executeProjectHealth(new ProjectHealthCommand(
                cmd.root(), cmd.rootMap(), cmd.schematron(), null, null, false));
        if (!health.isClean()) {
            return new com.dogsbay.dogsbayaieditor.commands.results.ProjectCheck(
                    health, java.util.List.of(), stages, "health",
                    "the project itself has faults, so nothing was built and no output was read");
        }
        if (!cmd.build()) {
            return new com.dogsbay.dogsbayaieditor.commands.results.ProjectCheck(
                    health, java.util.List.of(), stages, null,
                    "the build was not asked for");
        }

        stages.add("build");
        java.util.List<com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild> builds =
                executeBuildDeliverables(new BuildDeliverablesCommand(
                        cmd.root(), cmd.outputBaseDir(), cmd.deliverable(), cmd.ditaOtHome()));
        if (builds.isEmpty()) {
            // A project that has not declared a deliverable yet is unfinished, not broken — the state every project
            // is in for its first few edits. It is not "not ready", and it is not ready either: the stage is dropped
            // from the report so that nothing here speaks for a build or an output, which is the honest answer.
            stages.remove("build");
            return new com.dogsbay.dogsbayaieditor.commands.results.ProjectCheck(
                    health, builds, stages, null,
                    "no deliverables yet");
        }
        java.nio.file.Path checkRoot = existingDir(cmd.root(), "root").toPath();
        java.util.List<com.dogsbay.dogsbayaieditor.commands.results.UnresolvedKeyRef> unresolved =
                unresolvedKeyReferences(builds, checkRoot);
        if (builds.stream().anyMatch(b -> !b.success())) {
            return new com.dogsbay.dogsbayaieditor.commands.results.ProjectCheck(
                    health, builds, java.util.List.of(), stages, "build",
                    "a deliverable failed to build, so the built output was not read",
                    java.util.List.of(), java.util.List.of(), unresolved);
        }

        return checkBuiltOutput(health, builds, stages, checkRoot, unresolved);
    }

    /** The key DITA-OT names in a "cannot find key definition" note: the first single-quoted word. */
    private static final java.util.regex.Pattern KEY_IN_NOTE =
            java.util.regex.Pattern.compile("'([^']+)'");

    /**
     * DITA-OT's two notes for a key reference it could not resolve: {@code DOTJ047I} in the root scope and
     * {@code DOTJ048I} inside a named {@code @keyscope}. Both end by saying they will use {@code @href} if it
     * is there, and a scoped key missing from a deliverable is exactly the fault this is looking for, so
     * taking only the first would miss half of them.
     *
     * <p>Ask this set nothing about a null code: {@link java.util.Set#of} throws on {@code contains(null)}
     * rather than answering false, and plenty of messages carry no code at all — every FOP layout warning a
     * PDF build produces, for one. The {@code "CODE".equals(code)} this replaced was null-safe by accident of
     * its shape, and the set was not.
     */
    private static final java.util.Set<String> UNRESOLVED_KEY_NOTES = java.util.Set.of("DOTJ047I", "DOTJ048I");

    /**
     * Key references a deliverable's own build could not resolve, and that have no fallback attribute.
     *
     * <p>This is the gap the health report cannot see. Health resolves keys against the project's root map,
     * where a key is usually defined; a deliverable builds a narrower scope, and a key missing from <em>that</em>
     * scope is a link that vanishes from that deliverable and no other. DITA-OT notices, per deliverable, and
     * says so at INFO — visible only with {@code --verbose} and easy to lose (three defects reached a tutorial
     * this way, 2026-09-29).
     *
     * <p>Only successful builds are read. A build that stopped may not have reached the key stage, so neither
     * the presence nor the absence of these notes means anything about it.
     */
    static java.util.List<com.dogsbay.dogsbayaieditor.commands.results.UnresolvedKeyRef> unresolvedKeyReferences(
            java.util.List<com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild> builds,
            java.nio.file.Path root) {
        java.util.List<com.dogsbay.dogsbayaieditor.commands.results.UnresolvedKeyRef> out =
                new java.util.ArrayList<>();
        java.util.Map<String, java.util.Set<Integer>> fallbackLines = new java.util.HashMap<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (var built : builds) {
            if (!built.success()) {
                continue;
            }
            for (com.dogsbay.xml.dita.DitaOtMessage message : built.messages()) {
                if (message.code() == null || !UNRESOLVED_KEY_NOTES.contains(message.code())
                        || message.file() == null || message.file().isBlank() || message.line() <= 0) {
                    // Without a line there is no way to ask whether the element has a fallback, and claiming
                    // there is none would be asserting what we did not check.
                    continue;
                }
                java.nio.file.Path source = sourceOf(message.file(), root);
                if (source == null || hasFallbackAt(source, message.line(), fallbackLines)) {
                    continue;
                }
                java.util.regex.Matcher key = KEY_IN_NOTE.matcher(message.message());
                String name = key.find() ? key.group(1) : "";
                String where = root.relativize(source).toString();
                if (!seen.add(built.name() + "\u0000" + where + "\u0000" + message.line() + "\u0000" + name)) {
                    continue;
                }
                out.add(new com.dogsbay.dogsbayaieditor.commands.results.UnresolvedKeyRef(
                        built.name(), where, message.line(), name));
            }
        }
        return out;
    }

    /**
     * The project file a DITA-OT message is about, or null when it names something outside the project.
     *
     * <p>DITA-OT stamps its messages with the original source as an absolute {@code file:} URI, so what arrives
     * here is {@code file:/home/you/project/topics/a.dita} — which {@code new File(...)} reads as a relative
     * path beginning with a directory called {@code file:}. Getting this wrong loses every topic that is not in
     * the project's top folder, which is nearly all of them.
     */
    static java.nio.file.Path sourceOf(String named, java.nio.file.Path root) {
        if (named == null || named.isBlank()) {
            return null;
        }
        java.nio.file.Path path = null;
        if (named.startsWith("file:")) {
            try {
                path = java.nio.file.Path.of(java.net.URI.create(named.replace(" ", "%20")));
            } catch (Exception e) {
                path = null;
            }
        }
        if (path == null) {
            java.nio.file.Path plain = java.nio.file.Path.of(named);
            path = plain.isAbsolute() ? plain : root.resolve(plain);
        }
        path = path.toAbsolutePath().normalize();
        return java.nio.file.Files.isRegularFile(path) && path.startsWith(root.toAbsolutePath().normalize())
                ? path : null;
    }

    /**
     * Whether the element at that line carries something to fall back on.
     *
     * <p>An {@code @href} for a {@code keyref}, an {@code @conref} for a {@code conkeyref} — DITA uses either
     * when the key gives nothing. The reference index does not see an external {@code @href}
     * ({@code https:}, {@code mailto:}), which it rightly does not treat as a project file, so the line's own
     * text is read as well. Line granularity errs towards silence: another element's {@code @href} on the same
     * line suppresses the warning, which is the safer way for a warning to be wrong.
     */
    private static boolean hasFallbackAt(java.nio.file.Path file, int line,
            java.util.Map<String, java.util.Set<Integer>> cache) {
        java.util.Set<Integer> lines = cache.computeIfAbsent(file.toString(), path -> {
            java.util.Set<Integer> found = new java.util.HashSet<>();
            for (com.dogsbay.dogsbayaieditor.links.Reference ref
                    : com.dogsbay.dogsbayaieditor.links.LinkExtractor.extract(file.toFile()).references()) {
                if ("href".equals(ref.attribute()) || "conref".equals(ref.attribute())) {
                    found.add(ref.line());
                }
            }
            try {
                java.util.List<String> text = java.nio.file.Files.readAllLines(file);
                for (int i = 0; i < text.size(); i++) {
                    if (text.get(i).contains("href=") || text.get(i).contains("conref=")) {
                        found.add(i + 1);
                    }
                }
            } catch (Exception e) {
                // Unreadable as text: the extractor's answer stands on its own.
            }
            return found;
        });
        return lines.contains(line);
    }

    /**
     * Read what each deliverable wrote, and say plainly which ones had nothing to read.
     *
     * <p>A deliverable whose output holds no pages — a PDF, or a build that wrote nothing — yields no broken links,
     * and counting that as a clean output stage would be saying a folder nobody looked at holds together.
     */
    static com.dogsbay.dogsbayaieditor.commands.results.ProjectCheck checkBuiltOutput(
            ProjectHealthReport health,
            java.util.List<com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild> builds,
            java.util.List<String> stages, java.nio.file.Path root) throws CommandException {
        return checkBuiltOutput(health, builds, stages, root, java.util.List.of());
    }

    static com.dogsbay.dogsbayaieditor.commands.results.ProjectCheck checkBuiltOutput(
            ProjectHealthReport health,
            java.util.List<com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild> builds,
            java.util.List<String> stages, java.nio.file.Path root,
            java.util.List<com.dogsbay.dogsbayaieditor.commands.results.UnresolvedKeyRef> unresolvedInBuild)
            throws CommandException {
        java.util.List<com.dogsbay.dogsbayaieditor.commands.results.BrokenRef> broken = new java.util.ArrayList<>();
        java.util.List<String> checked = new java.util.ArrayList<>();
        java.util.List<String> withoutPages = new java.util.ArrayList<>();
        java.util.List<String> empty = new java.util.ArrayList<>();
        for (var built : builds) {
            java.nio.file.Path out = outputFolderOf(built, root);
            if (out == null || !java.nio.file.Files.isDirectory(out)) {
                // Say where it looked. A deliverable reported as having written nothing, when the file is plainly
                // there, is only arguable if the report keeps the folder it checked to itself.
                empty.add(built.name() + (out == null ? "" : " (nothing at " + out + ")"));
                continue;
            }
            try {
                OutputLinkChecker.Scan scan = OutputLinkChecker.scan(out);
                if (scan.pages() > 0) {
                    checked.add(built.name());
                    broken.addAll(scan.broken());
                    continue;
                }
                // No pages, so no links to follow — but a deliverable is not beyond checking for that. A PDF that
                // built and wrote nothing, or wrote an empty file, is a failure that "no pages to read" called clean.
                if (wroteSomething(out)) {
                    withoutPages.add(built.name());
                } else {
                    empty.add(built.name() + " (nothing in " + out + ")");
                }
            } catch (java.io.IOException e) {
                throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Could not read the output of '" + built.name() + "': " + e.getMessage());
            }
        }

        java.util.List<String> notes = new java.util.ArrayList<>();
        if (!withoutPages.isEmpty()) {
            notes.add("no pages to check links in " + String.join(", ", withoutPages));
        }
        if (!empty.isEmpty()) {
            notes.add(String.join(", ", empty) + " wrote nothing");
        }
        String note = notes.isEmpty() ? null : String.join("; ", notes);

        if (!empty.isEmpty()) {
            // A build that reported success and wrote nothing is a failure no other stage would notice.
            stages.add("output");
            return new com.dogsbay.dogsbayaieditor.commands.results.ProjectCheck(
                    health, builds, broken, stages, "output", note, checked, empty, unresolvedInBuild);
        }
        if (checked.isEmpty() && withoutPages.isEmpty()) {
            // Nothing was read at all, so the stage says nothing either way.
            return new com.dogsbay.dogsbayaieditor.commands.results.ProjectCheck(
                    health, builds, java.util.List.of(), stages, null, note, java.util.List.of(),
                    java.util.List.of(), unresolvedInBuild);
        }
        stages.add("output");
        return new com.dogsbay.dogsbayaieditor.commands.results.ProjectCheck(
                health, builds, broken, stages, broken.isEmpty() ? null : "output", note, checked, withoutPages,
                unresolvedInBuild);
    }

    /**
     * Where a deliverable's output really is.
     *
     * <p>A deliverable's declared output folder is resolved against the project file that declares it, and that file
     * is relative when the project was opened by a relative path — so the recorded folder can be relative too.
     * Reading it as it stands would make the answer depend on the directory the process happens to be started in,
     * which is how a PDF that was plainly there came back as "wrote nothing".
     */
    private static java.nio.file.Path outputFolderOf(
            com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild built, java.nio.file.Path root) {
        if (built.outputDir() == null || built.outputDir().isBlank()) {
            return null;
        }
        java.nio.file.Path out = java.nio.file.Path.of(built.outputDir());
        if (!out.isAbsolute() && root != null) {
            out = root.resolve(out);
        }
        return out.toAbsolutePath().normalize();
    }

    /**
     * Whether a build left anything worth having in its output folder.
     *
     * <p>The weakest claim that is still worth making about a deliverable nobody can link-check: it produced a file,
     * and the file is not empty. A PDF build that reports success and writes a zero-byte file is a real failure, and
     * one that no other stage would notice.
     */
    private static boolean wroteSomething(java.nio.file.Path dir) throws java.io.IOException {
        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(dir)) {
            return walk.filter(java.nio.file.Files::isRegularFile).anyMatch(f -> {
                try {
                    return java.nio.file.Files.size(f) > 0;
                } catch (java.io.IOException unreadable) {
                    return false;
                }
            });
        }
    }


    private ProjectHealthReport executeProjectHealth(ProjectHealthCommand cmd)
            throws CommandException {
        java.nio.file.Path root = existingDir(cmd.root(), "root").toPath();
        if (!cmd.unknownLegs().isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Unknown project_health check(s) " + cmd.unknownLegs() + "; choose from "
                    + ProjectHealthCommand.LEGS);
        }
        ProjectDefaults.Resolved defaults = ProjectDefaults.resolve(root, cmd.rootMap(), cmd.schematron());
        // Asking for house rules when there are none would check nothing and still say clean.
        if (cmd.include() != null && cmd.include().contains("schematron")
                && ProjectDefaults.NOT_CONFIGURED.equals(defaults.schematronSource())) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "The schematron check needs house rules: pass schematron=<file.sch>, or name the project's in "
                    + ".dogsbay/config.xml (<default-schematron>)");
        }
        boolean mapMissing = ProjectDefaults.MISSING.equals(defaults.mapSource());
        boolean rulesMissing = ProjectDefaults.MISSING.equals(defaults.schematronSource());
        String config = root.resolve(".dogsbay").resolve("config.xml").toString();
        String keyMap = mapMissing ? null : defaults.map();          // keys: the caller's map or the project's
        String scopeMap = defaults.mapScopes() ? defaults.map() : null;  // scope: only a map the caller named
        List<String> checked = new ArrayList<>();
        // Grouping needs every finding (a capped list undercounts the rule), and so does filtering
        // by severity: capping first could keep only warnings, drop them, and call the project clean.
        int cap = (cmd.groupRules() || cmd.errorsOnly()) ? -1 : 200;

        List<java.nio.file.Path> files;
        try {
            // The XML of the publication set: every leg below reads its files as XML, and a map's reachable files
            // include the images it shows.
            files = scopeMap != null
                    ? FileSet.xmlOnly(FileSet.crawlMap(root, java.nio.file.Path.of(scopeMap)))
                    : FileSet.underRoot(root);
        } catch (Exception e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to resolve health scope: " + e.getMessage(), e);
        }
        java.util.Map<java.nio.file.Path, String> accepted = HealthChecks.acceptedViews(files);
        List<com.dogsbay.dogsbayaieditor.commands.results.PendingFix> pending = new ArrayList<>();

        HealthReport reuse = null;
        if (cmd.includes("reuse")) {
            // Asked about one map, report the references of the files that map publishes.
            java.util.Set<java.io.File> referenceScope = scopeMap == null ? null
                    : files.stream().map(f -> canonical(f.toFile()))
                            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
            reuse = executeHealth(new HealthCommand(cmd.root(), keyMap), referenceScope);
            List<BrokenRef> undefined = reuse.undefinedKeys();
            if (ProjectDefaults.CONFIG.equals(defaults.mapSource())) {
                // The project's map defines the keys of its own publication set; a topic outside it may take its
                // keys from another deliverable's map, so its keyrefs are not judged against this one
                try {
                    java.util.Set<java.nio.file.Path> published = new java.util.HashSet<>();
                    for (java.nio.file.Path p : FileSet.crawlMap(root, java.nio.file.Path.of(keyMap))) {
                        published.add(HealthChecks.key(p));
                    }
                    undefined = undefined.stream().filter(u -> published.contains(HealthChecks.key(u.source()))).toList();
                } catch (Exception e) {
                    // cannot tell the publication set: keep every finding
                }
            }
            List<BrokenRef> broken = new ArrayList<>(stillBroken(reuse.brokenReferences(), accepted, "reuse", pending));
            if (mapMissing) {
                // the project's default root map is gone: keys went unchecked, and that is a problem to fix
                broken.add(0, new BrokenRef(config, 0, "default-root-map", relative(root, defaults.map()),
                        "the project's default root map does not exist, so keys were not checked"));
            }
            reuse = new HealthReport(
                    broken,
                    stillBroken(undefined, accepted, "keys", pending),
                    reuse.unusedKeys(), reuse.orphanTopics());
            if (cmd.errorsOnly()) {
                // Unused keys and orphans are worth knowing, not errors.
                reuse = new HealthReport(reuse.brokenReferences(), reuse.undefinedKeys(), List.of(), List.of());
            }
            checked.add("reuse");
        }

        BatchResult<FileValidation> validation = null;
        if (cmd.includes("validation")) {
            validation = validateFiles(files, List.of(), accepted);   // the text that ships
            pending.addAll(pendingValidation(accepted));
            if (cmd.errorsOnly()) {
                validation = errorLevelOnly(validation);
            }
            checked.add("validation");
        }

        java.util.List<BrokenRef> brokenElementIds = null;
        if (cmd.includes("elementIds")) {
            brokenElementIds = stillBroken(executeConrefAudit(new ConrefAuditCommand(cmd.root(), keyMap)), accepted,
                    "elementIds", pending);
            checked.add("elementIds");
        }

        // Metadata policy leg: a project with a required-metadata policy can't be
        // "clean" while required metadata is missing (empty/clean when no policy).
        String metaScope = scopeMap != null ? "map:" + scopeMap : "root";
        BatchResult<com.dogsbay.dogsbayaieditor.commands.results.MetadataFinding> metadata = null;
        List<com.dogsbay.dogsbayaieditor.commands.results.RuleGroup> metadataRules = null;
        if (cmd.includes("metadata")) {
            metadata = executeMetadataAudit(new MetadataAuditCommand(cmd.root(), metaScope, null), cap);
            if (cmd.errorsOnly()) {
                metadata = keepFindings(metadata, f -> "error".equals(f.severity()));
                if (!cmd.groupRules()) {
                    metadata = capFindings(metadata, 200);
                }
            }
            if (cmd.groupRules()) {
                metadataRules = com.dogsbay.dogsbayaieditor.commands.results.RuleGroup.ofMetadata(metadata.findings());
                metadata = keepFindings(metadata, f -> false);
            }
            checked.add("metadata");
        }

        // House rules (shortdesc required, no hardcoded product name, …): the caller's, else the project's.
        BatchResult<SchematronFinding> schematron = null;
        List<com.dogsbay.dogsbayaieditor.commands.results.RuleGroup> schematronRules = null;
        if (cmd.includes("schematron") && rulesMissing) {
            // the project names house rules that are not there: not "none configured", and not a pass
            schematron = new BatchResult<>(0, 0, 1, List.of(new SchematronFinding(config, "/dogsbay-project",
                    "The project's house rules " + relative(root, defaults.schematron()) + " do not exist, so they "
                    + "were not checked; fix <default-schematron> in .dogsbay/config.xml", null, "default-schematron",
                    0)), 0);
            if (cmd.groupRules()) {
                schematronRules = com.dogsbay.dogsbayaieditor.commands.results.RuleGroup.ofSchematron(
                        schematron.findings());
                schematron = keepFindings(schematron, f -> false);
            }
            checked.add("schematron");
        } else if (cmd.includes("schematron") && defaults.schematron() != null) {
            java.io.File sch = existingFile(defaults.schematron(), "schematron");
            List<java.nio.file.Path> scoped;
            try {
                scoped = FileSet.resolve(root, metaScope);
            } catch (Exception e) {
                throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                        "Failed to resolve scope '" + metaScope + "': " + e.getMessage(), e);
            }
            schematron = runSchematron(sch, scoped, -1, accepted);    // the text that ships
            pending.addAll(pendingSchematron(sch, scoped, schematron, accepted));
            if (cap > 0) {
                schematron = capFindings(schematron, cap);
            }
            if (cmd.errorsOnly()) {
                schematron = keepFindings(schematron, ProjectHealthReport::blocks);
                if (!cmd.groupRules()) {
                    schematron = capFindings(schematron, 200);
                }
            }
            if (cmd.groupRules()) {
                schematronRules = com.dogsbay.dogsbayaieditor.commands.results.RuleGroup.ofSchematron(
                        schematron.findings());
                schematron = keepFindings(schematron, f -> false);
            }
            checked.add("schematron");
        }

        List<com.dogsbay.dogsbayaieditor.commands.results.ConrefPushAuditResult.Issue> conrefPush = null;
        if (cmd.includes("conrefPush")) {
            conrefPush = executeConrefPushAudit(new ConrefPushAuditCommand(cmd.root(), metaScope, keyMap)).issues();
            checked.add("conrefPush");
        }

        List<com.dogsbay.dogsbayaieditor.commands.results.IndexAuditResult.DanglingSee> danglingIndex = null;
        if (cmd.includes("index")) {
            danglingIndex = new ArrayList<>();
            for (var d : executeIndexAudit(new IndexAuditCommand(cmd.root(), metaScope)).dangling()) {
                if (accepted.containsKey(HealthChecks.key(d.file())) && HealthChecks.seeInDeletedProposal(
                        java.nio.file.Path.of(d.file()), d.target())) {
                    pending.add(new com.dogsbay.dogsbayaieditor.commands.results.PendingFix(d.file(), "index",
                            "\"" + d.from() + "\" sees \"" + d.target() + "\", which is not an index entry"));
                } else {
                    danglingIndex.add(d);
                }
            }
            checked.add("index");
        }

        // Warnings from here: worth fixing, but they do not block a clean report.
        List<String> unformatted = null;
        // Formatting every file is the slowest leg: on a large project it runs when asked for by name
        boolean formatAsked = cmd.include() != null && cmd.include().contains("format");
        if (cmd.includes("format") && !cmd.errorsOnly() && (formatAsked || files.size() <= 500)) {
            unformatted = new ArrayList<>();
            for (java.nio.file.Path f : files) {
                if (!HealthChecks.isDocument(f)) {
                    continue;
                }
                try {
                    if (HeadlessProcessingCommands.executeFormat(new FormatCommand(f, null, null)).modified()) {
                        unformatted.add(f.toString());
                    }
                } catch (CommandException e) {
                    // not well-formed: the validation leg says so
                }
            }
            checked.add("format");
        }

        List<com.dogsbay.dogsbayaieditor.commands.results.HealthMarker> markers = null;
        if (cmd.includes("markers") && !cmd.errorsOnly()) {
            markers = HealthChecks.markers(files);
            checked.add("markers");
        }

        List<com.dogsbay.dogsbayaieditor.commands.results.OpenProposals> proposals = null;
        if (cmd.includes("proposals")) {
            proposals = openProposals(files);
            checked.add("proposals");
        }

        com.dogsbay.dogsbayaieditor.ditaproject.BatchResult<
                com.dogsbay.dogsbayaieditor.commands.results.ConditionViolation> conditions = null;
        if (cmd.includes("conditions")) {
            // Only when a scheme governs something: with no subjectScheme in the project every value is allowed,
            // and a leg that always passes is a leg that teaches people to ignore it.
            conditions = executeValidateConditions(
                    new ValidateConditionsCommand(cmd.root(), metaScope, null));
            checked.add("conditions");
        }

        return new ProjectHealthReport(reuse, validation, brokenElementIds, metadata, proposals,
                schematron, metadataRules, schematronRules, checked, conrefPush, danglingIndex, unformatted, markers,
                pending, conditions, new com.dogsbay.dogsbayaieditor.commands.results.HealthDefaults(
                        defaults.map() == null ? null : relative(root, defaults.map()), defaults.mapSource(),
                        defaults.schematron() == null ? null : relative(root, defaults.schematron()),
                        defaults.schematronSource()));
    }

    private static String relative(java.nio.file.Path root, String path) {
        java.nio.file.Path p = java.nio.file.Path.of(path);
        return p.startsWith(root) ? root.relativize(p).toString() : path;
    }

    /**
     * References still broken in the text that ships. A finding is a pending fix only when the element that
     * holds the reference is itself marked deleted by a proposal (accepting removes it); anything else stays,
     * so a guess never hides a real error.
     */
    private static List<BrokenRef> stillBroken(List<BrokenRef> refs, java.util.Map<java.nio.file.Path, String> accepted,
            String leg, List<com.dogsbay.dogsbayaieditor.commands.results.PendingFix> pending) {
        if (accepted.isEmpty()) {
            return refs;
        }
        List<BrokenRef> out = new ArrayList<>();
        for (BrokenRef r : refs) {
            java.nio.file.Path key = HealthChecks.key(r.source());
            if (key != null && accepted.containsKey(key)
                    && HealthChecks.inDeletedProposal(key, r.line(), r.attribute(), r.value())) {
                pending.add(new com.dogsbay.dogsbayaieditor.commands.results.PendingFix(r.source(), leg,
                        "@" + r.attribute() + "=\"" + r.value() + "\": " + r.reason()));
            } else {
                out.add(r);
            }
        }
        return out;
    }

    /** Errors of the files as they stand that the text that ships no longer has: fixed once accepted. */
    private static List<com.dogsbay.dogsbayaieditor.commands.results.PendingFix> pendingValidation(
            java.util.Map<java.nio.file.Path, String> accepted) {
        List<com.dogsbay.dogsbayaieditor.commands.results.PendingFix> out = new ArrayList<>();
        for (var e : accepted.entrySet()) {
            java.nio.file.Path f = e.getKey();
            List<ValidationError> asIs = validateOne(f, List.of());
            if (asIs.isEmpty()) {
                continue;
            }
            List<String> shipping = new ArrayList<>(validateText(f, e.getValue(), List.of()).stream()
                    .map(ValidationError::message).toList());
            for (ValidationError err : asIs) {
                if (!shipping.remove(err.message())) {
                    out.add(new com.dogsbay.dogsbayaieditor.commands.results.PendingFix(f.toString(), "validation",
                            err.message()));
                }
            }
        }
        return out;
    }

    /** House-rule findings of the files as they stand that the text that ships no longer has. */
    private List<com.dogsbay.dogsbayaieditor.commands.results.PendingFix> pendingSchematron(java.io.File sch,
            List<java.nio.file.Path> scoped, BatchResult<SchematronFinding> shipping,
            java.util.Map<java.nio.file.Path, String> accepted) throws CommandException {
        List<com.dogsbay.dogsbayaieditor.commands.results.PendingFix> out = new ArrayList<>();
        if (accepted.isEmpty()) {
            return out;
        }
        List<java.nio.file.Path> proposed = scoped.stream().filter(f -> accepted.containsKey(HealthChecks.key(f)))
                .toList();
        if (proposed.isEmpty()) {
            return out;
        }
        java.util.Map<java.nio.file.Path, List<String>> shipped = new java.util.HashMap<>();
        for (SchematronFinding f : shipping.findings()) {
            shipped.computeIfAbsent(HealthChecks.key(f.file()), k -> new ArrayList<>()).add(f.test() + "\n" + f.message());
        }
        for (SchematronFinding f : runSchematron(sch, proposed, -1).findings()) {
            List<String> still = shipped.getOrDefault(HealthChecks.key(f.file()), new ArrayList<>());
            if (f.test() != null && !still.remove(f.test() + "\n" + f.message())) {
                out.add(new com.dogsbay.dogsbayaieditor.commands.results.PendingFix(f.file(), "schematron",
                        f.message()));
            }
        }
        return out;
    }

    /**
     * The root map an audit resolves keys with: the one the caller named (as given), else the project's default
     * ({@link ProjectDefaults}); {@code none}, or no project default, is no map.
     */
    private static String keyMap(String root, String mapArg) {
        if (mapArg != null && !mapArg.isBlank()) {
            return mapArg.trim().equalsIgnoreCase(ProjectDefaults.NONE) ? null : mapArg;
        }
        if (root == null || root.isBlank()) {
            return null;
        }
        ProjectDefaults.Resolved d = ProjectDefaults.resolve(java.nio.file.Path.of(root), null, ProjectDefaults.NONE);
        return ProjectDefaults.CONFIG.equals(d.mapSource()) ? d.map() : null;
    }

    /** Broken references, with the project's default root map first when its config names one that does not exist. */
    private static List<BrokenRef> withMissingMap(String root, String mapArg, List<BrokenRef> refs) {
        if ((mapArg != null && !mapArg.isBlank()) || root == null || root.isBlank()) {
            return refs;
        }
        java.nio.file.Path r = java.nio.file.Path.of(root);
        ProjectDefaults.Resolved d = ProjectDefaults.resolve(r, null, ProjectDefaults.NONE);
        if (!ProjectDefaults.MISSING.equals(d.mapSource())) {
            return refs;
        }
        List<BrokenRef> out = new ArrayList<>();
        out.add(new BrokenRef(r.resolve(".dogsbay").resolve("config.xml").toString(), 0, "default-root-map",
                relative(r, d.map()), "the project's default root map does not exist, so keys were not checked"));
        out.addAll(refs);
        return out;
    }

    /** The house rules schematron_project runs: the ones named, else the project's; refused when there are none. */
    private static String houseRules(String root, String schemaArg) throws CommandException {
        if (schemaArg != null && !schemaArg.isBlank() && !schemaArg.trim().equalsIgnoreCase(ProjectDefaults.NONE)) {
            return schemaArg;
        }
        ProjectDefaults.Resolved d = root == null || root.isBlank() ? null
                : ProjectDefaults.resolve(java.nio.file.Path.of(root), ProjectDefaults.NONE, null);
        if (d != null && ProjectDefaults.CONFIG.equals(d.schematronSource())) {
            return d.schematron();
        }
        if (d != null && ProjectDefaults.MISSING.equals(d.schematronSource())) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, "The project's house rules "
                    + d.schematron() + " do not exist; fix <default-schematron> in .dogsbay/config.xml, or pass schema");
        }
        throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, "No house rules to run: pass schema "
                + "(a .sch file), or name the project's in .dogsbay/config.xml (<default-schematron>)");
    }

    /** {@code path} resolved against {@code root} when it is relative; null and blank stay as they are. */
    private static String againstRoot(java.nio.file.Path root, String path) {
        if (path == null || path.isBlank() || java.nio.file.Path.of(path).isAbsolute()) {
            return path;
        }
        return root.resolve(path).toString();
    }

    /** At most {@code cap} findings, recording how many were left out. */
    private static <T> BatchResult<T> capFindings(BatchResult<T> result, int cap) {
        if (result.findings().size() <= cap) {
            return result;
        }
        return new BatchResult<>(result.total(), result.passed(), result.failed(),
                result.findings().subList(0, cap), result.truncated() + result.findings().size() - cap);
    }

    /** The same counts with only the findings {@code keep} accepts; the truncation note is dropped with them. */
    private static <T> BatchResult<T> keepFindings(BatchResult<T> result, java.util.function.Predicate<T> keep) {
        List<T> kept = result.findings().stream().filter(keep).toList();
        return new BatchResult<>(result.total(), result.passed(), result.failed(), kept,
                kept.size() == result.findings().size() ? result.truncated() : 0);
    }

    /** Validation reduced to error-level problems: warnings dropped, files left with none no longer failing. */
    private static BatchResult<FileValidation> errorLevelOnly(BatchResult<FileValidation> result) {
        List<FileValidation> kept = new ArrayList<>();
        for (FileValidation fv : result.findings()) {
            List<ValidationError> errors = fv.errors().stream().filter(e -> "error".equals(e.severity())).toList();
            if (!errors.isEmpty()) {
                kept.add(new FileValidation(fv.file(), false, errors));
            }
        }
        int failed = kept.size() + result.truncated();
        return new BatchResult<>(result.total(), result.total() - failed, failed, kept, result.truncated());
    }

    /**
     * Audit reuse references for broken element ids: references whose target
     * file exists but whose fragment ({@code #topic/elementId}) names an id the
     * target doesn't contain. Missing files / undefined keys are check-links's
     * job, so we only report when the file resolves and parses.
     */
    private java.util.List<BrokenRef> executeConrefAudit(ConrefAuditCommand cmd)
            throws CommandException {
        java.io.File root = existingDir(cmd.root(), "root");
        com.dogsbay.dogsbayaieditor.links.KeySpace keySpace = hasRootMap(cmd.rootMap())
                ? com.dogsbay.dogsbayaieditor.links.KeySpace.fromRootMap(
                        existingFile(cmd.rootMap(), "rootMap"))
                : com.dogsbay.dogsbayaieditor.links.KeySpace.empty();
        com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex index =
                com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex.build(root, null);

        java.util.Map<java.io.File, java.util.Set<String>> idCache = new java.util.HashMap<>();
        java.util.List<BrokenRef> broken = new java.util.ArrayList<>();

        for (com.dogsbay.dogsbayaieditor.links.Reference ref : index.allReferences()) {
            if (ref.fragment() == null || ref.fragment().isBlank()) {
                continue;
            }
            // Only audit reuse references (conref / conkeyref / keyref). href/xref/
            // src are navigation/image links whose #fragment may be a non-id anchor
            // or an external target — broken ids there are a link-check concern, not
            // a conref-reuse one, and would be false positives here.
            if (!ref.isKeyReference() && !"conref".equals(ref.attribute())) {
                continue;
            }
            java.io.File target = ref.isKeyReference()
                    ? keySpace.resolveHrefFileLenient(ref.keyName(), ref.scope())
                    : (ref.targetPath() != null ? new java.io.File(ref.targetPath()) : null);
            if (target == null || !target.isFile()) {
                continue; // missing file / undefined key → check-links territory
            }
            String elementId = lastSegment(ref.fragment());
            if (elementId.isBlank()) {
                continue;
            }
            java.util.Set<String> ids = idCache.computeIfAbsent(target,
                    HeadlessExecutor::collectElementIds);
            if (ids == null) {
                continue; // target unparseable → can't verify (validation catches it)
            }
            if (!ids.contains(elementId)) {
                broken.add(new BrokenRef(ref.source().getAbsolutePath(), ref.line(),
                        ref.attribute(), ref.rawValue(),
                        "element id '" + elementId + "' not found in " + target.getName()));
            }
        }
        return broken;
    }

    /**
     * Run a Schematron schema (ph-schematron) against every file in a scope and
     * aggregate failed assertions and fired reports into a {@link BatchResult}.
     * (Both {@code <assert>} and {@code <report>} rules are surfaced.) The schema is
     * compiled once; a file that can't be parsed becomes a finding (visible, not
     * silently skipped). External DTD loading is off, so DITA doctypes need no
     * catalog here.
     */
    private BatchResult<SchematronFinding> executeSchematronProject(SchematronProjectCommand cmd)
            throws CommandException {
        java.io.File root = existingDir(cmd.root(), "root");
        java.io.File sch = existingFile(cmd.schema(), "schema");

        List<java.nio.file.Path> scoped;
        try {
            scoped = FileSet.resolve(root.toPath(), cmd.scope());
        } catch (Exception e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to resolve scope '" + cmd.scope() + "': " + e.getMessage(), e);
        }
        return runSchematron(sch, scoped);
    }

    /**
     * Apply a Schematron schema to one document.
     *
     * <p>Same engine as the project-wide command — the only difference is that the
     * file list is a single file rather than a resolved scope.
     */
    private BatchResult<SchematronFinding> executeSchematron(SchematronCommand cmd)
            throws CommandException {
        java.io.File target = existingFile(cmd.file(), "file");
        java.io.File sch = existingFile(cmd.schema(), "schema");

        return runSchematron(sch, List.of(target.toPath()));
    }

    private com.dogsbay.dogsbayaieditor.commands.results.ReviewResult executeReviewDecision(String file,
            boolean accept, String id, String author, boolean all) throws CommandException {
        String xml = ReviewCommands.read(file);
        ReviewCommands.Decision d = ReviewCommands.decide(xml, accept, id, author, all);
        ReviewCommands.write(file, d.text());
        java.nio.file.Path path = java.nio.file.Path.of(file);
        d.audit(ReviewCommands.auditFor(path), path);
        return d.result(path);
    }

    /**
     * Run {@code sch} over {@code files} and collect the rule matches.
     *
     * @param sch the Schematron schema
     * @param files the documents to check
     * @return findings, capped, with pass/fail counts
     * @throws CommandException if the schema cannot be loaded or is invalid
     */
    private BatchResult<SchematronFinding> runSchematron(java.io.File sch,
            List<java.nio.file.Path> files) throws CommandException {
        return runSchematron(sch, files, 200);
    }

    /** As {@link #runSchematron(java.io.File, List)}, keeping at most {@code cap} findings (negative: all). */
    private BatchResult<SchematronFinding> runSchematron(java.io.File sch,
            List<java.nio.file.Path> files, int cap) throws CommandException {
        return runSchematron(sch, files, cap, java.util.Map.of());
    }

    /** As above, checking the text in {@code overlay} (as the file at its own address) for the files it holds. */
    private BatchResult<SchematronFinding> runSchematron(java.io.File sch,
            List<java.nio.file.Path> files, int cap, java.util.Map<java.nio.file.Path, String> overlay)
            throws CommandException {

        // SchXslt compiles the schema to XSLT 2.0, so pin Saxon explicitly for this
        // run rather than relying on whatever the ambient factory happens to be.
        // Restored in the finally below. The swap is serialized so concurrent
        // schematron runs don't corrupt each other's save/restore. (A transform on
        // another thread during this window could still observe Saxon; the XSLT
        // schematron engine exposes no per-call factory injection, so the global
        // property is the only lever.)
        // ph-schematron 10 parses the SVRL result through JAXB with XSD validation,
        // and asks that SchemaFactory for the XXE protection properties. The
        // bundled Xerces 2.12.2 does not recognise them, so it would log a warning
        // and a stack trace on every run AND leave the protection off. Pinning the
        // JDK's own factory for the run silences the noise by making the request
        // succeed. Restored alongside the transformer factory below.
        synchronized (SCHEMATRON_TF_LOCK) {
        String prevTransformerFactory =
                System.getProperty("javax.xml.transform.TransformerFactory");
        String prevSchemaFactory = System.getProperty(SCHEMA_FACTORY_PROPERTY);
        System.setProperty("javax.xml.transform.TransformerFactory",
                "net.sf.saxon.TransformerFactoryImpl");
        System.setProperty(SCHEMA_FACTORY_PROPERTY,
                "com.sun.org.apache.xerces.internal.jaxp.validation.XMLSchemaFactory");
        try {

        // XSLT (Saxon) engine for full ISO conformance — the pure engine
        // mis-evaluates union rule @context (e.g. "p | entry" matches only p).
        com.helger.schematron.sch.SchematronResourceSCH schematron;
        try {
            schematron = com.helger.schematron.sch.SchematronResourceSCH.fromFile(sch);
            if (!schematron.isValidSchematron()) {
                throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                        "Invalid Schematron schema: " + sch.getPath());
            }
        } catch (CommandException e) {
            throw e;
        } catch (Exception e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to load Schematron schema: " + e.getMessage(), e);
        }


        javax.xml.xpath.XPath xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
        List<SchematronFinding> findings = new ArrayList<>();
        int passed = 0;
        for (java.nio.file.Path f : files) {
            int before = findings.size();
            try {
                // Line-numbered DOM so the SVRL location XPath resolves to a
                // source line for click-to-open; ph-schematron runs on the same DOM.
                String shipped = overlay.isEmpty() ? null : overlay.get(HealthChecks.key(f));
                org.w3c.dom.Document doc = shipped != null
                        ? com.dogsbay.dogsbayaieditor.validate.LineNumberedDom.parse(shipped, f.toUri().toString())
                        : com.dogsbay.dogsbayaieditor.validate.LineNumberedDom.parse(f.toFile());
                var svrl = schematron.applySchematronValidationToSVRL(doc, f.toUri().toString());
                // A failed <assert> and a fired <report> are both rule matches the
                // author wants surfaced; collect both (in document order) rather
                // than only failed assertions.
                for (var msg : com.helger.schematron.svrl.SVRLHelper
                        .getAllFailedAssertionsAndSuccessfulReports(svrl)) {
                    String text = msg.getText() != null ? msg.getText().strip() : "rule matched";
                    int line = lineForLocation(xpath, doc, msg.getLocation());
                    findings.add(new SchematronFinding(f.toString(), msg.getLocation(),
                            text, msg.getRole(), msg.getTest(), line));
                }
            } catch (Exception e) {
                findings.add(new SchematronFinding(f.toString(), null,
                        "could not check: " + e.getMessage(), "error", null, -1));
            }
            if (findings.size() == before) {
                passed++;
            }
        }
        return BatchResult.capped(files.size(), passed, files.size() - passed, findings, cap);
        } finally {
            if (prevTransformerFactory == null) {
                System.clearProperty("javax.xml.transform.TransformerFactory");
            } else {
                System.setProperty("javax.xml.transform.TransformerFactory",
                        prevTransformerFactory);
            }
            if (prevSchemaFactory == null) {
                System.clearProperty(SCHEMA_FACTORY_PROPERTY);
            } else {
                System.setProperty(SCHEMA_FACTORY_PROPERTY, prevSchemaFactory);
            }
        }
        }
    }

    /** Serializes the global TransformerFactory swap used by schematron_project. */
    /** JAXB/XSD factory selector: the JDK name for the W3C XML Schema language. */
    private static final String SCHEMA_FACTORY_PROPERTY =
            "javax.xml.validation.SchemaFactory:http://www.w3.org/2001/XMLSchema";

    private static final Object SCHEMATRON_TF_LOCK = new Object();

    /**
     * Resolve an SVRL location XPath to the 1-based start line of the element it
     * points at. Multiple matches → the first; no match, a namespace-prefixed
     * XPath we can't bind, or any error → -1 (caller falls back to file-only
     * navigation).
     */
    private static int lineForLocation(javax.xml.xpath.XPath xpath,
            org.w3c.dom.Document doc, String location) {
        if (location == null || location.isBlank()) {
            return -1;
        }
        try {
            org.w3c.dom.NodeList nodes = (org.w3c.dom.NodeList) xpath.evaluate(
                    location, doc, javax.xml.xpath.XPathConstants.NODESET);
            if (nodes.getLength() > 0) {
                return com.dogsbay.dogsbayaieditor.validate.LineNumberedDom.lineOf(nodes.item(0));
            }
        } catch (Exception ignore) {
            // unbindable / namespaced location → fall back to file-only
        }
        return -1;
    }

    // ── Subject schemes: validate_conditions / list_subjects ─────────────

    private BatchResult<com.dogsbay.dogsbayaieditor.commands.results.ConditionViolation>
            executeValidateConditions(ValidateConditionsCommand cmd) throws CommandException {
        java.io.File root = existingDir(cmd.root(), "root");
        com.dogsbay.dogsbayaieditor.links.scheme.SubjectScheme scheme =
                resolveScheme(root, cmd.scope(), cmd.subjectScheme());

        List<java.nio.file.Path> files;
        try {
            files = FileSet.resolve(root.toPath(), cmd.scope());
            // A map: scope follows references, but DITAVALs are build-time filters
            // never referenced from a map — so they'd be silently excluded. Their
            // <prop val> values are controlled too, so add the project's ditavals.
            if (cmd.scope() != null && cmd.scope().startsWith("map:")) {
                files = withProjectDitavals(root, files);
            }
        } catch (Exception e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to resolve scope '" + cmd.scope() + "': " + e.getMessage(), e);
        }

        var checker = new com.dogsbay.dogsbayaieditor.links.scheme.ConditionChecker(scheme);
        List<String> governed = scheme.bindings().stream()
                .map(com.dogsbay.dogsbayaieditor.links.scheme.EnumerationBinding::attribute)
                .toList();
        List<com.dogsbay.dogsbayaieditor.commands.results.ConditionViolation> findings =
                new ArrayList<>();
        int passed = 0;
        for (java.nio.file.Path f : files) {
            int before = findings.size();
            String name = f.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            try {
                if (name.endsWith(".ditaval")) {
                    for (var c : com.dogsbay.dogsbayaieditor.links.scheme.DitavalConditions
                            .read(f.toFile())) {
                        for (var fd : checker.check(c.att(), c.val())) {
                            findings.add(toViolation(f, fd, c.line()));
                        }
                    }
                } else if (!governed.isEmpty()) {
                    org.w3c.dom.Document doc = com.dogsbay.dogsbayaieditor.validate
                            .LineNumberedDom.parse(f.toFile());
                    scanConditions(doc.getDocumentElement(), governed, checker, f, findings);
                }
            } catch (Exception e) {
                // Unparseable file is a well-formedness/validation concern, not a
                // condition violation; skip here (validate_project reports it).
            }
            if (findings.size() == before) {
                passed++;
            }
        }
        return BatchResult.capped(files.size(), passed, files.size() - passed, findings, 200);
    }

    /**
     * Union the resolved file set with every {@code *.ditaval} under the project
     * root, de-duplicated by normalized absolute path (so a file already in the set
     * isn't scanned twice). Best-effort — IO failure leaves the set unchanged.
     */
    private static List<java.nio.file.Path> withProjectDitavals(
            java.io.File root, List<java.nio.file.Path> files) {
        try {
            java.util.LinkedHashMap<String, java.nio.file.Path> byPath =
                    new java.util.LinkedHashMap<>();
            for (java.nio.file.Path f : files) {
                byPath.put(f.toAbsolutePath().normalize().toString(), f);
            }
            for (java.nio.file.Path dv : FileSet.fromGlob(root.toPath(), "**/*.ditaval")) {
                byPath.putIfAbsent(dv.toAbsolutePath().normalize().toString(), dv);
            }
            return new ArrayList<>(byPath.values());
        } catch (Exception e) {
            return files;
        }
    }

    /** Recursively check each governed attribute on every element. */
    private static void scanConditions(org.w3c.dom.Element el, List<String> governed,
            com.dogsbay.dogsbayaieditor.links.scheme.ConditionChecker checker,
            java.nio.file.Path file,
            List<com.dogsbay.dogsbayaieditor.commands.results.ConditionViolation> out) {
        if (el == null) {
            return;
        }
        int line = com.dogsbay.dogsbayaieditor.validate.LineNumberedDom.lineOf(el);
        for (String attr : governed) {
            String value = el.getAttribute(attr);
            if (value != null && !value.isEmpty()) {
                for (var fd : checker.check(attr, value)) {
                    out.add(toViolation(file, fd, line));
                }
            }
        }
        org.w3c.dom.NodeList kids = el.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof org.w3c.dom.Element child) {
                scanConditions(child, governed, checker, file, out);
            }
        }
    }

    private static com.dogsbay.dogsbayaieditor.commands.results.ConditionViolation toViolation(
            java.nio.file.Path file,
            com.dogsbay.dogsbayaieditor.links.scheme.ConditionFinding fd, int line) {
        return new com.dogsbay.dogsbayaieditor.commands.results.ConditionViolation(
                file.toString(), fd.attribute(), fd.value(), fd.message(), fd.suggestion(), line);
    }

    private List<com.dogsbay.dogsbayaieditor.commands.results.SubjectDefinition>
            executeListSubjects(ListSubjectsCommand cmd) throws CommandException {
        java.io.File schemeFile = existingFile(cmd.subjectScheme(), "subjectScheme");
        var scheme = com.dogsbay.dogsbayaieditor.links.scheme.SubjectScheme.fromRootMap(schemeFile);
        List<com.dogsbay.dogsbayaieditor.commands.results.SubjectDefinition> out = new ArrayList<>();
        for (var b : scheme.bindings()) {
            out.add(new com.dogsbay.dogsbayaieditor.commands.results.SubjectDefinition(
                    b.attribute(), b.allowedValues().stream().sorted().toList()));
        }
        return out;
    }

    /**
     * Resolve the governing scheme: an explicit {@code subjectScheme} file wins,
     * else the subjectScheme map(s) in a {@code map:} scope's closure, else empty
     * (no scheme ⇒ no findings).
     */
    private com.dogsbay.dogsbayaieditor.links.scheme.SubjectScheme resolveScheme(
            java.io.File root, String scope, String subjectScheme) throws CommandException {
        if (subjectScheme != null && !subjectScheme.isBlank()) {
            java.io.File s = existingFile(subjectScheme, "subjectScheme");
            return com.dogsbay.dogsbayaieditor.links.scheme.SubjectScheme.fromRootMap(s);
        }
        if (scope != null && scope.startsWith("map:")) {
            String m = scope.substring(4).trim();
            java.io.File map = new java.io.File(m).isAbsolute()
                    ? new java.io.File(m) : new java.io.File(root, m);
            if (map.isFile()) {
                return com.dogsbay.dogsbayaieditor.links.scheme.SubjectScheme.fromRootMap(map);
            }
        }
        // No map named, which is how anyone runs this on a whole project: `check .`, or Project ▸ Check Project.
        // Returning an empty scheme there meant every value passed and the project reported ready — a check that
        // only works when you already know to ask for it is not a check.
        String configured = keyMap(root.getPath(), null);
        if (configured != null && !configured.isBlank()) {
            java.io.File map = new java.io.File(configured).isAbsolute()
                    ? new java.io.File(configured) : new java.io.File(root, configured);
            if (map.isFile()) {
                var fromConfigured = com.dogsbay.dogsbayaieditor.links.scheme.SubjectScheme.fromRootMap(map);
                if (!fromConfigured.isEmpty()) {
                    return fromConfigured;
                }
            }
        }
        // Still nothing: a scheme governs the project whether or not a map happens to pull it in, so find the
        // scheme maps themselves. Empty when there are none, which is the honest answer for a project with no
        // controlled values at all.
        return com.dogsbay.dogsbayaieditor.links.scheme.SubjectScheme.fromProjectRoot(root);
    }

    // ── Metadata: metadata_audit ─────────────────────────────────────────

    private BatchResult<com.dogsbay.dogsbayaieditor.commands.results.MetadataFinding>
            executeMetadataAudit(MetadataAuditCommand cmd) throws CommandException {
        return executeMetadataAudit(cmd, 200);
    }

    /** The metadata audit keeping at most {@code cap} findings (negative: all). */
    private BatchResult<com.dogsbay.dogsbayaieditor.commands.results.MetadataFinding>
            executeMetadataAudit(MetadataAuditCommand cmd, int cap) throws CommandException {
        java.io.File root = existingDir(cmd.root(), "root");
        com.dogsbay.dogsbayaieditor.links.metadata.MetadataPolicy policy =
                resolveMetadataPolicy(root, cmd.policy());
        if (policy.isEmpty()) {
            // No policy ⇒ nothing to audit; skip the corpus scan entirely (this is
            // the default, and project_health calls this on every run).
            return BatchResult.capped(0, 0, 0, List.of(), 200);
        }

        List<java.nio.file.Path> files;
        try {
            files = FileSet.resolve(root.toPath(), cmd.scope());
        } catch (Exception e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to resolve scope '" + cmd.scope() + "': " + e.getMessage(), e);
        }

        List<com.dogsbay.dogsbayaieditor.commands.results.MetadataFinding> findings =
                new ArrayList<>();
        int considered = 0;
        int passed = 0;
        for (java.nio.file.Path f : files) {
            String name = f.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            if (!(name.endsWith(".dita") || name.endsWith(".ditamap")
                    || name.endsWith(".bookmap"))) {
                continue; // metadata lives on topics/maps, not ditavals/other XML
            }
            considered++;
            int before = findings.size();
            var snap = com.dogsbay.dogsbayaieditor.links.metadata.MetadataExtractor
                    .extract(f.toFile());
            findings.addAll(com.dogsbay.dogsbayaieditor.links.metadata.MetadataEvaluator
                    .evaluate(f.toString(), snap, policy));
            // A file "fails" only on an error-level finding; recommended-only
            // (warning) files still pass — so isClean()/failed match the done-gate.
            boolean hasError = findings.subList(before, findings.size()).stream()
                    .anyMatch(x -> "error".equals(x.severity()));
            if (!hasError) {
                passed++;
            }
        }
        return BatchResult.capped(considered, passed, considered - passed, findings, cap);
    }

    /**
     * Resolve the metadata policy: an explicit policy file wins, else the shared
     * {@code .dogsbay/config.xml} policy, else empty (no policy ⇒ no findings).
     */
    private com.dogsbay.dogsbayaieditor.links.metadata.MetadataPolicy resolveMetadataPolicy(
            java.io.File root, String policyFile) throws CommandException {
        if (policyFile != null && !policyFile.isBlank()) {
            return com.dogsbay.dogsbayaieditor.links.metadata.MetadataPolicy
                    .fromFile(existingFile(policyFile, "policy"));
        }
        return com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig
                .load(root.toPath()).getMetadataPolicy();
    }

    private BatchResult<com.dogsbay.dogsbayaieditor.commands.results.MetadataChange>
            executeMetadataSet(MetadataSetCommand cmd) throws CommandException {
        java.io.File root = existingDir(cmd.root(), "root");

        // Translate the specs to PrologWriter changes (unknown fields/modes rejected).
        List<com.dogsbay.dogsbayaieditor.links.metadata.PrologWriter.Change> changes =
                new ArrayList<>();
        for (MetadataSetSpec spec : cmd.changes()) {
            var field = com.dogsbay.dogsbayaieditor.links.metadata.MetadataField
                    .byKey(spec.field());
            if (field == null) {
                throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                        "Unknown metadata field: " + spec.field());
            }
            com.dogsbay.dogsbayaieditor.links.metadata.PrologWriter.Mode mode;
            try {
                mode = com.dogsbay.dogsbayaieditor.links.metadata.PrologWriter.Mode
                        .valueOf(spec.mode() == null ? "FILL"
                                : spec.mode().trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                        "Unknown mode '" + spec.mode() + "' (set|fill|append|remove)");
            }
            changes.add(new com.dogsbay.dogsbayaieditor.links.metadata.PrologWriter.Change(
                    field, spec.value(), mode));
        }
        if (changes.isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "No metadata changes specified");
        }

        List<java.nio.file.Path> files;
        try {
            files = FileSet.resolve(root.toPath(), cmd.scope());
        } catch (Exception e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to resolve scope '" + cmd.scope() + "': " + e.getMessage(), e);
        }

        List<com.dogsbay.dogsbayaieditor.commands.results.MetadataChange> made =
                new ArrayList<>();
        int considered = 0;
        int changedFiles = 0;
        for (java.nio.file.Path f : files) {
            String fn = f.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            if (!fn.endsWith(".dita")) {
                continue; // contract is topics (.dita) only — never stamp metadata onto maps
            }
            considered++;
            try {
                var applied = com.dogsbay.dogsbayaieditor.links.metadata.PrologWriter
                        .apply(f.toFile(), changes, cmd.dryRun());
                if (!applied.isEmpty()) {
                    changedFiles++;
                    for (var ch : applied) {
                        made.add(new com.dogsbay.dogsbayaieditor.commands.results.MetadataChange(
                                f.toString(), ch.field().key(),
                                ch.mode().name().toLowerCase(java.util.Locale.ROOT),
                                ch.value()));
                    }
                }
            } catch (Exception e) {
                throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                        "Failed to update " + f + ": " + e.getMessage(), e);
            }
        }
        return BatchResult.capped(considered, considered - changedFiles, changedFiles, made, 200);
    }

    /**
     * One attribute, on one element, in every file of a scope.
     *
     * <p>Each file goes through {@link com.dogsbay.xml.edit.StructuralEdit}, the same splice {@code
     * edit_structure} uses, so a file keeps its formatting, its indentation and its line endings; only the
     * element's start tag is rewritten. A file the selector does not match is skipped rather than failing the
     * run: "every topic" reasonably includes files that are not topics.
     */
    private com.dogsbay.dogsbayaieditor.ditaproject.BatchResult<
            com.dogsbay.dogsbayaieditor.commands.results.AttributeChange> executeSetAttribute(
            SetAttributeCommand cmd) throws CommandException {
        java.io.File root = existingDir(cmd.root(), "root");
        String name = cmd.name() == null ? "" : cmd.name().trim();
        if (name.isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT, "No attribute named");
        }
        String select = cmd.select() == null || cmd.select().isBlank() ? "/*" : cmd.select().trim();
        String value = cmd.value() == null ? "" : cmd.value();

        List<java.nio.file.Path> files;
        try {
            files = FileSet.resolve(root.toPath(), cmd.scope());
        } catch (Exception e) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Failed to resolve scope '" + cmd.scope() + "': " + e.getMessage(), e);
        }

        var step = new com.dogsbay.xml.edit.StructuralEdit.Step(
                "set-attribute", select, null, null, name, value, null, null);
        // Asks whether the attribute is there at all: the structural editor's own test fails when it is missing,
        // and that is the only question --only-if-absent has to answer.
        var probe = new com.dogsbay.xml.edit.StructuralEdit.Step(
                "test", select, null, null, name, null, null, null);

        // Work the whole set out before writing any of it. A file that cannot be read stops the run, and doing
        // that halfway through would leave the project in a state neither the plan nor the reader expected.
        List<com.dogsbay.dogsbayaieditor.commands.results.AttributeChange> made = new ArrayList<>();
        java.util.Map<java.nio.file.Path, byte[]> toWrite = new java.util.LinkedHashMap<>();
        List<String> refused = new ArrayList<>();
        int considered = 0;
        for (java.nio.file.Path f : files) {
            if (!HealthChecks.isDocument(f)) {
                continue;
            }
            if (cmd.scope() == null && !isDitaDocument(f)) {
                // With no scope this walks everything under the root, and "every XML file" there includes
                // pom.xml, build.xml and a toolkit plugin.xml. Asked for a scope, we take it at its word;
                // asked for nothing, we take the DITA documents, which is what the command is for.
                continue;
            }
            com.dogsbay.xml.review.DecodedFile decoded;
            try {
                // As the file says it is encoded, not as UTF-8: a Latin-1 topic read as UTF-8 either refuses
                // to read or comes back with the wrong characters, and writing it back would be mojibake.
                decoded = com.dogsbay.xml.review.DecodedFile.read(f);
            } catch (java.io.IOException e) {
                throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                        "Failed to read " + f + ": " + e.getMessage(), e);
            }
            String before = decoded.text();
            considered++;

            boolean present = hasAttribute(before, probe);
            if (cmd.onlyIfAbsent() && present) {
                continue;
            }
            com.dogsbay.xml.edit.StructuralEdit.Result edited;
            try {
                edited = com.dogsbay.xml.edit.StructuralEdit.apply(before, List.of(step), null);
            } catch (com.dogsbay.xml.edit.StructuralEdit.StepException e) {
                // Not only "the selector found nothing here", which is a fine answer for a file that is not a
                // topic: an XPath that is invalid, or that matches two elements, fails the same way. Silently
                // skipping those leaves a half-applied project and a summary that reads like success, so they
                // are counted and the first of them is reported.
                refused.add(relative(root.toPath(), f.toString()) + " — " + e.getMessage());
                continue;
            }
            if (edited.text().equals(before)) {
                continue; // already what it should be
            }
            try {
                toWrite.put(f, decoded.encode(edited.text()));
            } catch (java.nio.charset.CharacterCodingException e) {
                // The value does not fit the file's own encoding — an accented character into a US-ASCII
                // file. Saying so beats writing bytes the parser will refuse.
                refused.add(relative(root.toPath(), f.toString()) + " — \"" + value
                        + "\" cannot be written in " + decoded.charset().name());
                continue;
            }
            made.add(new com.dogsbay.dogsbayaieditor.commands.results.AttributeChange(
                    f.toString(), select, name, value, present));
        }

        if (!refused.isEmpty() && toWrite.isEmpty()) {
            // Nothing to do and every file refused: that is a selector that does not work, not a project that
            // is already as asked, and answering "0 changed" would be answering the wrong question.
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "No file could be changed. " + refused.size() + " refused, first: " + refused.get(0));
        }
        if (!cmd.dryRun()) {
            for (var entry : toWrite.entrySet()) {
                try {
                    java.nio.file.Files.write(entry.getKey(), entry.getValue());
                } catch (java.io.IOException e) {
                    throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                            "Failed to write " + entry.getKey() + ": " + e.getMessage(), e);
                }
            }
        }
        return BatchResult.capped(considered, considered - toWrite.size(), toWrite.size(), made, 200);
    }

    /** A DITA topic or map by name — what an unscoped set-attribute confines itself to. */
    private static boolean isDitaDocument(java.nio.file.Path file) {
        String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".dita") || name.endsWith(".ditamap") || name.endsWith(".bookmap");
    }

    /** Whether the selected element already carries the attribute: the structural editor's own test answers it. */
    private static boolean hasAttribute(String source, com.dogsbay.xml.edit.StructuralEdit.Step probe) {
        try {
            com.dogsbay.xml.edit.StructuralEdit.apply(source, List.of(probe), null);
            return true;
        } catch (com.dogsbay.xml.edit.StructuralEdit.StepException absent) {
            return false;
        }
    }

    private com.dogsbay.dogsbayaieditor.commands.results.MapEditResult executeEditMap(
            EditMapCommand cmd) throws CommandException {
        java.io.File mapFile = existingFile(cmd.map(), "map");
        com.dogsbay.dogsbayaieditor.links.map.MapModel model;
        try {
            model = com.dogsbay.dogsbayaieditor.links.map.MapModel.parse(mapFile);
        } catch (java.io.IOException e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to parse map " + mapFile + ": " + e.getMessage(), e);
        }
        String op = cmd.op() == null ? "" : cmd.op().trim().toLowerCase(java.util.Locale.ROOT);
        int index = cmd.index() == null ? Integer.MAX_VALUE : cmd.index();
        com.dogsbay.dogsbayaieditor.links.map.MapEditPlan plan;
        try {
            switch (op) {
                case "set-attr" -> {
                    requireArg(cmd.ref(), "ref");
                    requireArg(cmd.name(), "name");
                    plan = com.dogsbay.dogsbayaieditor.links.map.MapEditor
                            .setAttr(model, cmd.ref(), cmd.name(), cmd.value());
                }
                case "insert" -> {
                    String type = (cmd.type() == null || cmd.type().isBlank())
                            ? "topicref" : cmd.type().trim();
                    java.util.Map<String, String> attrs = new java.util.LinkedHashMap<>();
                    if (cmd.href() != null && !cmd.href().isBlank()) {
                        attrs.put("href", cmd.href());
                    }
                    if (cmd.navtitle() != null && !cmd.navtitle().isBlank()) {
                        attrs.put("navtitle", cmd.navtitle());
                    }
                    plan = com.dogsbay.dogsbayaieditor.links.map.MapEditor
                            .insert(model, cmd.parent(), index, type, attrs);
                }
                case "remove" -> {
                    requireArg(cmd.ref(), "ref");
                    plan = com.dogsbay.dogsbayaieditor.links.map.MapEditor.remove(
                            model, cmd.ref(), mapFile.getAbsoluteFile().getParentFile());
                }
                case "move" -> {
                    requireArg(cmd.ref(), "ref");
                    com.dogsbay.dogsbayaieditor.links.map.MapModel dest = null;
                    if (cmd.toMap() != null && !cmd.toMap().isBlank()) {
                        java.io.File destFile = existingFile(cmd.toMap(), "to-map");
                        try {
                            dest = com.dogsbay.dogsbayaieditor.links.map.MapModel.parse(destFile);
                        } catch (java.io.IOException e) {
                            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                                    "Failed to parse destination map: " + e.getMessage(), e);
                        }
                    }
                    plan = com.dogsbay.dogsbayaieditor.links.map.MapEditor
                            .move(model, cmd.ref(), dest, cmd.parent(), index);
                }
                default -> throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                        "Unknown op '" + cmd.op() + "' (set-attr|insert|remove|move)");
            }
        } catch (IllegalArgumentException e) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    e.getMessage(), e);
        }

        List<String> files = plan.changes().stream()
                .map(c -> c.file().toString()).toList();
        if (!cmd.dryRun()) {
            try {
                plan.apply();
            } catch (java.io.IOException e) {
                throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                        "Failed to write map edit: " + e.getMessage(), e);
            }
        }
        return new com.dogsbay.dogsbayaieditor.commands.results.MapEditResult(
                true, files, plan.warnings(), cmd.dryRun());
    }

    private static void requireArg(String value, String name) throws CommandException {
        if (value == null || value.isBlank()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "--" + name + " is required for this operation");
        }
    }

    private com.dogsbay.dogsbayaieditor.commands.results.ReltableAuditResult
            executeReltableAudit(ReltableAuditCommand cmd) throws CommandException {
        java.io.File mapFile = existingFile(cmd.map(), "map");
        com.dogsbay.dogsbayaieditor.links.KeySpace keys = null;
        try {
            keys = com.dogsbay.dogsbayaieditor.links.KeySpace.fromRootMap(mapFile);
        } catch (RuntimeException ignore) {
            // no resolvable key space — href targets still validate
        }
        com.dogsbay.dogsbayaieditor.links.reltable.ReltableModel model;
        try {
            model = com.dogsbay.dogsbayaieditor.links.reltable.ReltableModel.parse(mapFile, keys);
        } catch (java.io.IOException e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to parse map: " + e.getMessage(), e);
        }
        String text = model.text();
        java.util.List<com.dogsbay.dogsbayaieditor.commands.results.ReltableIssue> issues =
                new ArrayList<>();
        java.util.Map<java.io.File, String> rootTypes = new java.util.HashMap<>();
        int ti = 0;
        for (var table : model.reltables()) {
            ti++;
            String label = table.title() != null ? table.title() : "reltable#" + ti;
            for (int r = 0; r < table.rows().size(); r++) {
                var row = table.rows().get(r);
                int nonEmpty = 0;
                for (int c = 0; c < row.cells().size(); c++) {
                    var cell = row.cells().get(c);
                    if (!cell.targets().isEmpty()) {
                        nonEmpty++;
                    }
                    for (var t : cell.targets()) {
                        String loc = "r" + (r + 1) + "c" + (c + 1);
                        int line = lineOf(text, t.element().getElementStartPosition());
                        String val = t.href() != null ? t.href()
                                : (t.keyref() != null ? "keyref:" + t.keyref() : "");
                        java.io.File rf = t.resolvedFile();
                        if (rf == null) {
                            issues.add(reltableIssue(mapFile, line, label, loc, val, "error",
                                    "unresolved target (missing href/keyref or undefined key)"));
                            continue;
                        }
                        // Targets a map, not a topic — judged by the resolved file (covers
                        // both href and keyref), not a substring of the raw href.
                        if (rf.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".ditamap")) {
                            issues.add(reltableIssue(mapFile, line, label, loc, val, "error",
                                    "reltable cell targets a map, not a topic"));
                            continue;
                        }
                        if (!rf.isFile()) {
                            issues.add(reltableIssue(mapFile, line, label, loc, val, "error",
                                    "target not found: " + rf.getName()));
                            continue;
                        }
                        String colType = c < table.columns().size()
                                ? table.columns().get(c).type() : null;
                        if (colType != null) {
                            String rt = rootTypes.computeIfAbsent(rf, this::reltableRootType);
                            if (rt != null && !rt.equals(colType)) {
                                issues.add(reltableIssue(mapFile, line, label, loc, val, "warning",
                                        "column type '" + colType + "' but target is <" + rt + ">"));
                            }
                        }
                    }
                }
                if (nonEmpty < 2) {
                    issues.add(reltableIssue(mapFile,
                            lineOf(text, row.element().getElementStartPosition()), label,
                            "r" + (r + 1), "", "info",
                            "row links nothing (fewer than two non-empty cells)"));
                }
            }
        }
        var links = com.dogsbay.dogsbayaieditor.links.reltable.RelatedLinksProjector.project(model)
                .stream()
                .map(l -> new com.dogsbay.dogsbayaieditor.commands.results.ReltableAuditResult
                        .ReltableLink(l.source().toString(), l.target().toString(),
                                l.text(), l.row()))
                .toList();
        return new com.dogsbay.dogsbayaieditor.commands.results.ReltableAuditResult(issues, links);
    }

    private static com.dogsbay.dogsbayaieditor.commands.results.ReltableIssue reltableIssue(
            java.io.File map, int line, String table, String cell, String value,
            String severity, String reason) {
        return new com.dogsbay.dogsbayaieditor.commands.results.ReltableIssue(
                map.toString(), line, table, cell, value, severity, reason);
    }

    private String reltableRootType(java.io.File f) {
        try {
            String t = java.nio.file.Files.readString(f.toPath(),
                    java.nio.charset.StandardCharsets.UTF_8);
            var root = new com.dogsbay.xml.DogsBayDocument(t).getRoot();
            return root != null ? root.getName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static int lineOf(String text, int offset) {
        if (offset < 0) {
            return -1;
        }
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private com.dogsbay.dogsbayaieditor.commands.results.MapEditResult executeEditReltable(
            EditReltableCommand cmd) throws CommandException {
        java.io.File mapFile = existingFile(cmd.map(), "map");
        com.dogsbay.dogsbayaieditor.links.KeySpace keys = null;
        try {
            keys = com.dogsbay.dogsbayaieditor.links.KeySpace.fromRootMap(mapFile);
        } catch (RuntimeException ignore) {
            // edits don't need key resolution
        }
        com.dogsbay.dogsbayaieditor.links.reltable.ReltableModel model;
        try {
            model = com.dogsbay.dogsbayaieditor.links.reltable.ReltableModel.parse(mapFile, keys);
        } catch (java.io.IOException e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to parse map: " + e.getMessage(), e);
        }
        String op = cmd.op() == null ? "" : cmd.op().trim().toLowerCase(java.util.Locale.ROOT);
        int row = cmd.row() == null ? -1 : cmd.row();
        int col = cmd.col() == null ? -1 : cmd.col();
        String newText;
        try {
            newText = switch (op) {
                case "create-table" -> com.dogsbay.dogsbayaieditor.links.reltable.ReltableWriter
                        .createTable(model, splitColumns(cmd.columns()));
                case "add-row" -> com.dogsbay.dogsbayaieditor.links.reltable.ReltableWriter
                        .addRow(model, cmd.table());
                case "remove-row" -> com.dogsbay.dogsbayaieditor.links.reltable.ReltableWriter
                        .removeRow(model, cmd.table(), row);
                case "add-target" -> {
                    if ((cmd.href() == null || cmd.href().isBlank())
                            && (cmd.keyref() == null || cmd.keyref().isBlank())) {
                        throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                                "add-target requires --href or --keyref");
                    }
                    yield com.dogsbay.dogsbayaieditor.links.reltable.ReltableWriter
                            .addTarget(model, cmd.table(), row, col,
                                    cmd.href(), cmd.keyref(), cmd.navtitle());
                }
                case "remove-target" -> com.dogsbay.dogsbayaieditor.links.reltable.ReltableWriter
                        .removeTarget(model, cmd.table(), row, col,
                                cmd.index() == null ? 0 : cmd.index());
                case "set-attr" -> {
                    requireArg(cmd.name(), "name");
                    yield com.dogsbay.dogsbayaieditor.links.reltable.ReltableWriter
                            .setAttr(model, cmd.table(), row, col, cmd.name(), cmd.value());
                }
                default -> throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                        "Unknown op '" + cmd.op() + "' (create-table|add-row|remove-row|"
                        + "add-target|remove-target|set-attr)");
            };
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    e.getMessage(), e);
        }
        if (!cmd.dryRun()) {
            try {
                java.nio.file.Files.writeString(mapFile.toPath(), newText,
                        java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                        "Failed to write map: " + e.getMessage(), e);
            }
        }
        return new com.dogsbay.dogsbayaieditor.commands.results.MapEditResult(
                true, java.util.List.of(mapFile.toString()), java.util.List.of(), cmd.dryRun());
    }

    private static java.util.List<String> splitColumns(String columns) {
        if (columns == null || columns.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<String> out = new ArrayList<>();
        for (String c : columns.split(",")) {
            if (!c.isBlank()) {
                out.add(c.trim());
            }
        }
        return out;
    }

    private static final int KEYWORD_PAIR_CAP = 100;

    private KeywordAuditResult executeKeywordAudit(KeywordAuditCommand cmd)
            throws CommandException {
        java.io.File root = existingDir(cmd.root(), "root");
        java.util.List<java.nio.file.Path> files;
        try {
            files = FileSet.resolve(root.toPath(), cmd.scope());
        } catch (Exception e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to resolve scope '" + cmd.scope() + "': " + e.getMessage(), e);
        }

        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.List<String>> byKeyword = new java.util.LinkedHashMap<>();
        java.util.List<String> missing = new ArrayList<>();
        for (java.nio.file.Path p : files) {
            if (!p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                    .endsWith(".dita")) {
                continue; // keywords live in topics
            }
            var kws = com.dogsbay.dogsbayaieditor.links.metadata.MetadataExtractor
                    .extract(p.toFile())
                    .get(com.dogsbay.dogsbayaieditor.links.metadata.MetadataField.KEYWORD);
            if (kws.isEmpty()) {
                missing.add(p.toString());
                continue;
            }
            for (String kw : new java.util.LinkedHashSet<>(kws)) { // distinct per topic
                counts.merge(kw, 1, Integer::sum);
                byKeyword.computeIfAbsent(kw, k -> new ArrayList<>()).add(p.toString());
            }
        }

        List<KeywordAuditResult.Term> vocab = counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(e -> new KeywordAuditResult.Term(e.getKey(), e.getValue()))
                .toList();

        // Near-duplicates: surface forms that collapse to the same normalized key.
        java.util.Map<String, java.util.List<String>> byNorm = new java.util.LinkedHashMap<>();
        for (String kw : counts.keySet()) {
            byNorm.computeIfAbsent(normalizeKeyword(kw), k -> new ArrayList<>()).add(kw);
        }
        List<KeywordAuditResult.Variant> variants = new ArrayList<>();
        for (var forms : byNorm.values()) {
            if (forms.size() < 2) {
                continue;
            }
            String canonical = forms.stream()
                    .max(java.util.Comparator.comparingInt(counts::get)).orElse(forms.get(0));
            for (String form : forms) {
                if (!form.equals(canonical)) {
                    variants.add(new KeywordAuditResult.Variant(form, canonical));
                }
            }
        }

        // Co-occurrence: topic pairs sharing keywords (the relatedness signal).
        java.util.Map<java.util.List<String>, java.util.List<String>> shared =
                new java.util.LinkedHashMap<>();
        for (var e : byKeyword.entrySet()) {
            java.util.List<String> fs = e.getValue();
            for (int i = 0; i < fs.size(); i++) {
                for (int j = i + 1; j < fs.size(); j++) {
                    shared.computeIfAbsent(java.util.List.of(fs.get(i), fs.get(j)),
                            k -> new ArrayList<>()).add(e.getKey());
                }
            }
        }
        List<KeywordAuditResult.Pair> pairs = shared.entrySet().stream()
                .map(e -> new KeywordAuditResult.Pair(e.getKey().get(0), e.getKey().get(1),
                        e.getValue().size(), e.getValue()))
                .sorted((a, b) -> Integer.compare(b.shared(), a.shared()))
                .toList();
        int truncated = Math.max(0, pairs.size() - KEYWORD_PAIR_CAP);
        if (truncated > 0) {
            pairs = pairs.subList(0, KEYWORD_PAIR_CAP);
        }
        return new KeywordAuditResult(vocab, missing, variants, pairs, truncated);
    }

    private static String normalizeKeyword(String kw) {
        return kw.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private com.dogsbay.dogsbayaieditor.commands.results.IndexAuditResult
            executeIndexAudit(IndexAuditCommand cmd) throws CommandException {
        java.io.File root = existingDir(cmd.root(), "root");
        java.util.List<java.nio.file.Path> files;
        try {
            files = FileSet.resolve(root.toPath(), cmd.scope());
        } catch (Exception e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to resolve scope '" + cmd.scope() + "': " + e.getMessage(), e);
        }

        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        java.util.Set<String> validTargets = new java.util.HashSet<>();
        java.util.List<String> missing = new ArrayList<>();
        record PendingSee(String file,
                com.dogsbay.dogsbayaieditor.links.IndextermScanner.See see) {}
        java.util.List<PendingSee> sees = new ArrayList<>();

        for (java.nio.file.Path p : files) {
            if (!p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                    .endsWith(".dita")) {
                continue;                       // index terms live in topics
            }
            var scan = com.dogsbay.dogsbayaieditor.links.IndextermScanner.scan(p.toFile());
            if (scan.entries().isEmpty() && scan.sees().isEmpty()) {
                missing.add(p.toString());
                continue;
            }
            for (String path : new java.util.LinkedHashSet<>(scan.entries())) { // distinct/topic
                counts.merge(path, 1, Integer::sum);
                validTargets.add(path);                       // full path is a valid target
                for (String segment : path.split(" > ")) {    // …as is each level (primary/secondary)
                    validTargets.add(segment);
                }
            }
            for (var see : scan.sees()) {
                sees.add(new PendingSee(p.toString(), see));
            }
        }

        var entries = counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(e -> new com.dogsbay.dogsbayaieditor.commands.results
                        .IndexAuditResult.Entry(e.getKey(), e.getValue()))
                .toList();
        var dangling = sees.stream()
                .filter(s -> !validTargets.contains(s.see().target()))
                .map(s -> new com.dogsbay.dogsbayaieditor.commands.results
                        .IndexAuditResult.DanglingSee(s.file(), s.see().from(),
                        s.see().target(), s.see().also()))
                .toList();
        return new com.dogsbay.dogsbayaieditor.commands.results.IndexAuditResult(
                entries, missing, dangling);
    }

    private com.dogsbay.dogsbayaieditor.commands.results.ConrefPushAuditResult
            executeConrefPushAudit(ConrefPushAuditCommand cmd) throws CommandException {
        java.io.File root = existingDir(cmd.root(), "root");
        java.util.List<java.nio.file.Path> files;
        try {
            files = FileSet.resolve(root.toPath(), cmd.scope());
        } catch (Exception e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to resolve scope '" + cmd.scope() + "': " + e.getMessage(), e);
        }
        var keySpace = hasRootMap(cmd.rootMap())
                ? com.dogsbay.dogsbayaieditor.links.KeySpace.fromRootMap(
                        existingFile(cmd.rootMap(), "rootMap"))
                : com.dogsbay.dogsbayaieditor.links.KeySpace.empty();

        var pushes = new ArrayList<
                com.dogsbay.dogsbayaieditor.commands.results.ConrefPushAuditResult.Push>();
        var issues = new ArrayList<
                com.dogsbay.dogsbayaieditor.commands.results.ConrefPushAuditResult.Issue>();
        java.util.Map<java.io.File, java.util.Set<String>> idCache = new java.util.HashMap<>();

        for (java.nio.file.Path p : files) {
            if (!p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                    .endsWith(".dita")) {
                continue;
            }
            java.io.File src = p.toFile();
            for (var push : com.dogsbay.dogsbayaieditor.links.ConrefPushScanner.scan(src)) {
                String target = push.conref() != null ? push.conref()
                        : (push.conkeyref() != null ? "(key) " + push.conkeyref() : "");
                pushes.add(new com.dogsbay.dogsbayaieditor.commands.results
                        .ConrefPushAuditResult.Push(p.toString(), push.line(),
                        push.element(), push.action(), target));
                if (push.pairing() != null) {
                    issues.add(new com.dogsbay.dogsbayaieditor.commands.results
                            .ConrefPushAuditResult.Issue(p.toString(), push.line(),
                            push.element(), push.pairing()));
                }
                // Resolve the push target's element id (only mark/pushreplace carry one).
                if (!push.carriesTarget()) {
                    continue;
                }
                String problem = resolvePushTarget(push, src, keySpace, idCache);
                if (problem != null) {
                    issues.add(new com.dogsbay.dogsbayaieditor.commands.results
                            .ConrefPushAuditResult.Issue(p.toString(), push.line(),
                            push.element(), problem));
                }
            }
        }
        return new com.dogsbay.dogsbayaieditor.commands.results.ConrefPushAuditResult(
                pushes, issues);
    }

    /** Validate a conref-push target ({@code @conref}/{@code @conkeyref} + element id),
     *  returning a problem string or null when it resolves. */
    private String resolvePushTarget(
            com.dogsbay.dogsbayaieditor.links.ConrefPushScanner.Push push, java.io.File src,
            com.dogsbay.dogsbayaieditor.links.KeySpace keySpace,
            java.util.Map<java.io.File, java.util.Set<String>> idCache) {
        if (push.conref() == null && push.conkeyref() == null) {
            // a mark/pushreplace must carry a target reference (pushreplace's pairing
            // check already flags this; a bare mark would otherwise NPE below)
            return push.action() + " has no @conref/@conkeyref target";
        }
        java.io.File targetFile;
        String fragment;
        if (push.conref() != null) {
            String raw = push.conref();
            int hash = raw.indexOf('#');
            String path = hash >= 0 ? raw.substring(0, hash) : raw;
            fragment = hash >= 0 ? raw.substring(hash + 1) : null;
            targetFile = path.isEmpty() ? src
                    : (new java.io.File(path).isAbsolute() ? new java.io.File(path)
                            : new java.io.File(src.getParentFile(), path));
        } else { // conkeyref="key/elemId"
            String raw = push.conkeyref();
            int slash = raw.indexOf('/');
            String key = slash >= 0 ? raw.substring(0, slash) : raw;
            fragment = slash >= 0 ? raw.substring(slash + 1) : null;
            targetFile = keySpace.resolveHrefFileLenient(key, "");
            if (targetFile == null) {
                return "conkeyref key '" + key + "' does not resolve";
            }
        }
        if (targetFile == null || !targetFile.isFile()) {
            return "push target file not found: " + (targetFile != null ? targetFile.getName() : "");
        }
        if (fragment == null || fragment.isBlank()) {
            return null; // topic-level target — no element id to verify
        }
        String elementId = lastSegment(fragment);
        java.util.Set<String> ids = idCache.computeIfAbsent(targetFile,
                HeadlessExecutor::collectElementIds);
        if (ids != null && !ids.contains(elementId)) {
            return "push target id '" + elementId + "' not found in " + targetFile.getName();
        }
        return null;
    }

    // DITA 1.3 @chunk vocabulary, grouped by mutual exclusion.
    private static final java.util.Set<String> CHUNK_SELECT =
            java.util.Set.of("select-branch", "select-document", "select-topic");
    private static final java.util.Set<String> CHUNK_BY =
            java.util.Set.of("by-topic", "by-document");
    private static final java.util.Set<String> CHUNK_TO =
            java.util.Set.of("to-content", "to-navigation");

    private com.dogsbay.dogsbayaieditor.commands.results.ChunkAuditResult
            executeChunkAudit(ChunkAuditCommand cmd) throws CommandException {
        java.io.File root = existingDir(cmd.root(), "root");
        java.util.List<java.nio.file.Path> files;
        try {
            files = FileSet.resolve(root.toPath(), cmd.scope());
        } catch (Exception e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to resolve scope '" + cmd.scope() + "': " + e.getMessage(), e);
        }
        var uses = new ArrayList<
                com.dogsbay.dogsbayaieditor.commands.results.ChunkAuditResult.Use>();
        var issues = new ArrayList<
                com.dogsbay.dogsbayaieditor.commands.results.ChunkAuditResult.Issue>();
        for (java.nio.file.Path p : files) {
            String lower = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            if (!lower.endsWith(".ditamap") && !lower.endsWith(".bookmap")) {
                continue;                       // @chunk lives on map elements
            }
            for (var u : com.dogsbay.dogsbayaieditor.links.ChunkScanner.scan(p.toFile())) {
                uses.add(new com.dogsbay.dogsbayaieditor.commands.results
                        .ChunkAuditResult.Use(p.toString(), u.line(), u.element(), u.value()));
                for (String problem : validateChunk(u.value())) {
                    issues.add(new com.dogsbay.dogsbayaieditor.commands.results
                            .ChunkAuditResult.Issue(p.toString(), u.line(), u.value(), problem));
                }
            }
        }
        return new com.dogsbay.dogsbayaieditor.commands.results.ChunkAuditResult(uses, issues);
    }

    /** Problems with one {@code @chunk} value: unknown tokens + conflicting groups. */
    private static java.util.List<String> validateChunk(String value) {
        java.util.List<String> problems = new ArrayList<>();
        int select = 0, by = 0, to = 0;
        for (String token : value.split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (CHUNK_SELECT.contains(token)) {
                select++;
            } else if (CHUNK_BY.contains(token)) {
                by++;
            } else if (CHUNK_TO.contains(token)) {
                to++;
            } else {
                problems.add("unknown chunk token '" + token + "'");
            }
        }
        if (select > 1) {
            problems.add("conflicting select-* tokens (at most one)");
        }
        if (by > 1) {
            problems.add("conflicting by-topic/by-document (at most one)");
        }
        if (to > 1) {
            problems.add("conflicting to-content/to-navigation (at most one)");
        }
        return problems;
    }

    private com.dogsbay.dogsbayaieditor.commands.results.SpecializationInfoResult
            executeSpecializationInfo(SpecializationInfoCommand cmd) throws CommandException {
        java.io.File file = existingFile(cmd.file(), "file");
        var spec = com.dogsbay.dogsbayaieditor.links.SpecializationScanner.scan(file);
        return new com.dogsbay.dogsbayaieditor.commands.results.SpecializationInfoResult(
                file.getAbsolutePath(), spec.root(), spec.publicId(), spec.systemId(),
                spec.classChain(), spec.domains(), spec.isSpecialized());
    }

    private static String canonPath(java.io.File f) {
        try {
            return f.getCanonicalPath();
        } catch (java.io.IOException e) {
            return f.getAbsolutePath();
        }
    }

    private com.dogsbay.dogsbayaieditor.commands.results.GlossaryAuditResult
            executeGlossaryAudit(GlossaryAuditCommand cmd) throws CommandException {
        java.io.File root = existingDir(cmd.root(), "root");
        java.util.List<java.nio.file.Path> files;
        try {
            files = FileSet.resolve(root.toPath(), cmd.scope());
        } catch (Exception e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to resolve scope '" + cmd.scope() + "': " + e.getMessage(), e);
        }

        // canonical glossentry path -> its inventory entry
        java.util.Map<String, com.dogsbay.dogsbayaieditor.commands.results
                .GlossaryAuditResult.Entry> glossByCanon = new java.util.LinkedHashMap<>();
        record RefAt(String file,
                com.dogsbay.dogsbayaieditor.links.GlossaryScanner.TermRef ref) {}
        java.util.List<RefAt> refs = new ArrayList<>();

        for (java.nio.file.Path p : files) {
            if (!p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                    .endsWith(".dita")) {
                continue;
            }
            var scan = com.dogsbay.dogsbayaieditor.links.GlossaryScanner.scan(p.toFile());
            if (scan.isGlossentry()) {
                glossByCanon.put(
                        canonPath(p.toFile()),
                        new com.dogsbay.dogsbayaieditor.commands.results.GlossaryAuditResult
                                .Entry(p.toString(), scan.entry().id(), scan.entry().term()));
            }
            for (var r : scan.refs()) {
                refs.add(new RefAt(p.toString(), r));
            }
        }

        var entries = new ArrayList<>(glossByCanon.values());
        var undefined = new ArrayList<
                com.dogsbay.dogsbayaieditor.commands.results.GlossaryAuditResult.Ref>();
        var unused = new ArrayList<
                com.dogsbay.dogsbayaieditor.commands.results.GlossaryAuditResult.Entry>();

        if (hasRootMap(cmd.rootMap())) {
            var keySpace = com.dogsbay.dogsbayaieditor.links.KeySpace.fromRootMap(
                    existingFile(cmd.rootMap(), "rootMap"));
            java.util.Set<String> referenced = new java.util.HashSet<>();
            for (RefAt ra : refs) {
                String key = ra.ref().keyref();
                java.io.File target = keySpace.resolveHrefFileLenient(key, "");
                String tcanon = target != null ? canonPath(target) : null;
                boolean isGloss = tcanon != null && glossByCanon.containsKey(tcanon);
                if ("abbreviated-form".equals(ra.ref().element())) {
                    // an abbreviated-form MUST resolve to a glossentry
                    if (isGloss) {
                        referenced.add(tcanon);
                    } else {
                        undefined.add(new com.dogsbay.dogsbayaieditor.commands.results
                                .GlossaryAuditResult.Ref(ra.file(), ra.ref().line(),
                                ra.ref().element(), key));
                    }
                } else { // <term keyref> — may point at any key; only a truly undefined key is wrong
                    if (isGloss) {
                        referenced.add(tcanon);
                    } else if (keySpace.resolveLenient(key, "") == null) {
                        undefined.add(new com.dogsbay.dogsbayaieditor.commands.results
                                .GlossaryAuditResult.Ref(ra.file(), ra.ref().line(),
                                ra.ref().element(), key));
                    }
                }
            }
            for (var e : glossByCanon.entrySet()) {
                if (!referenced.contains(e.getKey())) {
                    unused.add(e.getValue());
                }
            }
        }

        return new com.dogsbay.dogsbayaieditor.commands.results.GlossaryAuditResult(
                entries, undefined, unused);
    }

    private java.util.List<com.dogsbay.dogsbayaieditor.commands.results.BranchInfo>
            executeListBranches(ListBranchesCommand cmd) throws CommandException {
        java.io.File map = existingFile(cmd.map(), "map");
        java.util.List<com.dogsbay.dogsbayaieditor.links.Branch> branches;
        try {
            branches = com.dogsbay.dogsbayaieditor.links.BranchModel.enumerate(map);
        } catch (java.io.IOException e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    "Failed to parse map: " + e.getMessage(), e);
        }
        java.util.List<com.dogsbay.dogsbayaieditor.commands.results.BranchInfo> out =
                new ArrayList<>();
        for (var b : branches) {
            out.add(new com.dogsbay.dogsbayaieditor.commands.results.BranchInfo(
                    b.label(), b.appliesTo(), b.ditavalHref(),
                    b.ditaval() != null && b.ditaval().isFile()
                            ? b.ditaval().getAbsolutePath() : null,  // null when missing
                    b.generatedKeyscope()));
        }
        return out;
    }

    private String executeExportMetadataSchematron(ExportMetadataSchematronCommand cmd)
            throws CommandException {
        java.io.File root = existingDir(cmd.root(), "root");
        String schematron = resolveMetadataPolicy(root, cmd.policy()).toSchematron();
        if (cmd.output() != null && !cmd.output().isBlank()) {
            try {
                java.nio.file.Files.writeString(
                        new java.io.File(cmd.output()).toPath(), schematron,
                        java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                        "Failed to write " + cmd.output() + ": " + e.getMessage(), e);
            }
        }
        return schematron;
    }

    /** True when a root-map argument is present; a blank string counts as absent. */
    private static boolean hasRootMap(String rootMap) {
        return rootMap != null && !rootMap.isBlank();
    }

    /** The element-id segment of a fragment: {@code topic/elem} → {@code elem}. */
    private static String lastSegment(String fragment) {
        int slash = fragment.lastIndexOf('/');
        return slash >= 0 ? fragment.substring(slash + 1) : fragment;
    }

    /**
     * All {@code @id} values in a file, or null if it can't be parsed. External
     * DTD loading is disabled so DITA PUBLIC doctypes don't need a catalog here.
     */
    private static java.util.Set<String> collectElementIds(java.io.File file) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setValidating(false);
            dbf.setFeature("http://xml.org/sax/features/validation", false);
            // DOCTYPE allowed (DITA), external general entities blocked (XXE) — see SecureXml.
            com.dogsbay.xml.SecureXml.hardenDom(dbf, true);
            // This pass only collects @id values; it never needs DTD-declared entities,
            // so skip external DTD loading entirely (no catalog required here).
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            org.w3c.dom.Document doc = dbf.newDocumentBuilder().parse(file);
            java.util.Set<String> ids = new java.util.HashSet<>();
            org.w3c.dom.NodeList all = doc.getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                String id = ((org.w3c.dom.Element) all.item(i)).getAttribute("id");
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
            return ids;
        } catch (Exception e) {
            return null;
        }
    }

}
