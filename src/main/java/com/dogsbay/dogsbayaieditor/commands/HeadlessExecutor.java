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
            case WhereUsedCommand c -> executeWhereUsed(c);
            case ListKeysCommand c -> executeListKeys(c);
            case ResolveKeyCommand c -> executeResolveKey(c);
            case CheckLinksCommand c -> executeCheckLinks(c);
            case RenderPreviewCommand c -> executeRenderPreview(c);
            case RenderReportCommand c -> executeRenderReport(c);
            case ProjectGraphCommand c -> executeProjectGraph(c);
            case HealthCommand c -> executeHealth(c);
            case ValidateProjectCommand c -> executeValidateProject(c);
            case ProjectHealthCommand c -> executeProjectHealth(c);
            case ConrefAuditCommand c -> executeConrefAudit(c);
            case SchematronProjectCommand c -> executeSchematronProject(c);
            case SchematronCommand c -> executeSchematron(c);
            case ReviewListCommand c -> ReviewCommands.list(ReviewCommands.read(c.file()), c.author());
            case ReviewAcceptCommand c -> executeReviewDecision(c.file(), true, c.id(), c.author(), c.all());
            case ReviewRejectCommand c -> executeReviewDecision(c.file(), false, c.id(), c.author(), c.all());
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
            case EditMapCommand c -> executeEditMap(c);
            case ReltableAuditCommand c -> executeReltableAudit(c);
            case EditReltableCommand c -> executeEditReltable(c);
            case KeywordAuditCommand c -> executeKeywordAudit(c);
            case IndexAuditCommand c -> executeIndexAudit(c);
            case GlossaryAuditCommand c -> executeGlossaryAudit(c);
            case ConrefPushAuditCommand c -> executeConrefPushAudit(c);
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
            case GetContentCommand c -> throw editorRequired();
            case SetContentCommand c -> throw editorRequired();
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
            case OpenDitaMapCommand c -> throw editorRequired();
            case NewDocumentCommand c -> throw editorRequired();
            case AuthorSwitchCommand c -> throw editorRequired();
            case AuthorOutlineCommand c -> throw editorRequired();
            case AuthorInsertBlockCommand c -> throw editorRequired();
            case AuthorSetTextCommand c -> throw editorRequired();
            case AuthorIssuesCommand c -> throw editorRequired();
            case ScreenshotCommand c -> throw editorRequired();
        };
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

    private HealthReport executeHealth(HealthCommand cmd) throws CommandException {
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
        try {
            for (com.dogsbay.dogsbayaieditor.ditaproject.Deliverable d : targets) {
                java.io.File outputDir = resolveBuildOutputDir(cmd.outputBaseDir(), root, d).toFile();
                java.io.File tempDir = tempBase.resolve(d.name()).toFile();
                List<java.io.File> ditavals = withReviewFilter(d.ditavals(), tempDir.toPath());
                Map<String, String> params = resolveDeliverableParams(d, root);
                List<com.dogsbay.xml.dita.DitaOtMessage> msgs = builder.build(
                        d.map().toFile(), d.transtype(), ditavals, params, outputDir, tempDir);
                boolean success = msgs.stream()
                        .noneMatch(com.dogsbay.xml.dita.DitaOtMessage::isError);
                out.add(new com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild(
                        d.name(), d.transtype() != null ? d.transtype() : "html5",
                        outputDir.toString(), success, msgs));
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
            return base.resolve(d.name()).normalize();
        }
        if (d.output() != null) {
            java.nio.file.Path out = d.output();
            if (!out.isAbsolute()) {
                java.nio.file.Path base = d.sourceFile() != null
                        ? d.sourceFile().getParent() : root;
                out = base.resolve(out);
            }
            return out.normalize();
        }
        return root.resolve("out").resolve(d.name()).normalize();
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
        List<FileValidation> failures = new ArrayList<>();
        int passed = 0;
        for (java.nio.file.Path f : files) {
            List<ValidationError> errors = validateOne(f, catalogs);
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
     * Comprehensive project health: reuse-health plus DTD/grammar validation of
     * the scope (the publication set when a root map is given, else everything
     * under the root), and — when the command names a schema — a Schematron run
     * over the same scope. {@code clean} is true only when every leg passes; open
     * review proposals are listed but do not make it false.
     */
    private ProjectHealthReport executeProjectHealth(ProjectHealthCommand cmd)
            throws CommandException {
        // A relative map or schema is relative to the project, not to wherever the process runs:
        // an agent passes "guide.ditamap" with the editor's working directory somewhere else.
        java.nio.file.Path projectRoot = existingDir(cmd.root(), "root").toPath();
        if (cmd.rootMap() != null && !cmd.rootMap().isBlank() && !java.nio.file.Path.of(cmd.rootMap()).isAbsolute()
                || cmd.schematron() != null && !cmd.schematron().isBlank()
                        && !java.nio.file.Path.of(cmd.schematron()).isAbsolute()) {
            cmd = new ProjectHealthCommand(cmd.root(), againstRoot(projectRoot, cmd.rootMap()),
                    againstRoot(projectRoot, cmd.schematron()), cmd.include(), cmd.severity(), cmd.groupRules());
        }
        if (!cmd.unknownLegs().isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Unknown project_health check(s) " + cmd.unknownLegs() + "; choose from "
                    + ProjectHealthCommand.LEGS);
        }
        // Asking for house rules without a schema would check nothing and still say clean.
        if (cmd.include() != null && cmd.include().contains("schematron")
                && (cmd.schematron() == null || cmd.schematron().isBlank())) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "The schematron check needs a schema: pass schematron=<file.sch>");
        }
        java.nio.file.Path root = existingDir(cmd.root(), "root").toPath();
        List<String> checked = new ArrayList<>();
        // Grouping needs every finding (a capped list undercounts the rule), and so does filtering
        // by severity: capping first could keep only warnings, drop them, and call the project clean.
        int cap = (cmd.groupRules() || cmd.errorsOnly()) ? -1 : 200;

        HealthReport reuse = null;
        if (cmd.includes("reuse")) {
            reuse = executeHealth(new HealthCommand(cmd.root(), cmd.rootMap()));
            if (cmd.errorsOnly()) {
                // Unused keys and orphans are worth knowing, not errors.
                reuse = new HealthReport(reuse.brokenReferences(), reuse.undefinedKeys(), List.of(), List.of());
            }
            checked.add("reuse");
        }

        List<java.nio.file.Path> files = List.of();
        if (cmd.includes("validation") || cmd.includes("proposals")) {
            try {
                if (cmd.rootMap() != null && !cmd.rootMap().isEmpty()) {
                    java.nio.file.Path rm = java.nio.file.Path.of(cmd.rootMap());
                    files = FileSet.crawlMap(root, rm.isAbsolute() ? rm : root.resolve(cmd.rootMap()));
                } else {
                    files = FileSet.underRoot(root);
                }
            } catch (Exception e) {
                throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                        "Failed to resolve health scope: " + e.getMessage(), e);
            }
        }

        BatchResult<FileValidation> validation = null;
        if (cmd.includes("validation")) {
            validation = validateFiles(files, List.of());
            if (cmd.errorsOnly()) {
                validation = errorLevelOnly(validation);
            }
            checked.add("validation");
        }

        java.util.List<BrokenRef> brokenElementIds = null;
        if (cmd.includes("elementIds")) {
            brokenElementIds = executeConrefAudit(new ConrefAuditCommand(cmd.root(), cmd.rootMap()));
            checked.add("elementIds");
        }

        // Metadata policy leg: a project with a required-metadata policy can't be
        // "clean" while required metadata is missing (empty/clean when no policy).
        String metaScope = (cmd.rootMap() != null && !cmd.rootMap().isEmpty())
                ? "map:" + cmd.rootMap() : "root";
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

        // House rules (shortdesc required, no hardcoded product name, …) are
        // Schematron, so the gate can only see them when given the schema.
        BatchResult<SchematronFinding> schematron = null;
        List<com.dogsbay.dogsbayaieditor.commands.results.RuleGroup> schematronRules = null;
        if (cmd.includes("schematron") && cmd.schematron() != null && !cmd.schematron().isBlank()) {
            java.io.File sch = existingFile(cmd.schematron(), "schematron");
            List<java.nio.file.Path> scoped;
            try {
                scoped = FileSet.resolve(root, metaScope);
            } catch (Exception e) {
                throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                        "Failed to resolve scope '" + metaScope + "': " + e.getMessage(), e);
            }
            schematron = runSchematron(sch, scoped, cap);
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

        List<com.dogsbay.dogsbayaieditor.commands.results.OpenProposals> proposals = null;
        if (cmd.includes("proposals")) {
            proposals = openProposals(files);
            checked.add("proposals");
        }

        return new ProjectHealthReport(reuse, validation, brokenElementIds, metadata, proposals,
                schematron, metadataRules, schematronRules, checked);
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
                org.w3c.dom.Document doc =
                        com.dogsbay.dogsbayaieditor.validate.LineNumberedDom.parse(f.toFile());
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
        return com.dogsbay.dogsbayaieditor.links.scheme.SubjectScheme.empty();
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
