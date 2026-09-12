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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of available commands with metadata for discovery.
 * Used by CLI, MCP, and REST to enumerate available operations.
 */
public class CommandRegistry {

    /**
     * Metadata about a registered command.
     */
    public record CommandMeta(
        String name,
        String description,
        Class<? extends Command<?>> commandClass,
        boolean requiresEditor
    ) {}

    private final Map<String, CommandMeta> commands = new ConcurrentHashMap<>();

    public CommandRegistry() {
        registerBuiltInCommands();
    }

    /**
     * Register a command (used by plugins to add custom commands).
     */
    public void register(CommandMeta meta) {
        commands.put(meta.name(), meta);
    }

    /**
     * Get metadata for a specific command by name.
     */
    public CommandMeta getCommand(String name) {
        return commands.get(name);
    }

    /**
     * List all registered commands.
     */
    public List<CommandMeta> listCommands() {
        List<CommandMeta> list = new ArrayList<>(commands.values());
        list.sort((a, b) -> a.name().compareTo(b.name()));
        return Collections.unmodifiableList(list);
    }

    /**
     * List headless commands only.
     */
    public List<CommandMeta> listHeadlessCommands() {
        return listCommands().stream()
            .filter(c -> !c.requiresEditor())
            .toList();
    }

    /**
     * List editor commands only.
     */
    public List<CommandMeta> listEditorCommands() {
        return listCommands().stream()
            .filter(CommandMeta::requiresEditor)
            .toList();
    }

    private void registerBuiltInCommands() {
        // Headless commands
        register(new CommandMeta("validate",
            "Validate XML against schema (XSD, DTD, RelaxNG) with catalog support",
            ValidateCommand.class, false));
        register(new CommandMeta("parse",
            "Parse XML and show root element, namespace, and encoding",
            ParseCommand.class, false));
        register(new CommandMeta("transform",
            "Apply XSLT transformation to an XML document",
            TransformCommand.class, false));
        register(new CommandMeta("query",
            "Run XPath query across one or more XML files",
            QueryCommand.class, false));
        register(new CommandMeta("format",
            "Pretty-print XML with configurable indentation",
            FormatCommand.class, false));
        register(new CommandMeta("reflow",
            "Reflow prose to one sentence per line (semantic line breaks), then format",
            ReflowCommand.class, false));
        register(new CommandMeta("info",
            "Show document metadata: encoding, grammar, root element, size",
            InfoCommand.class, false));
        register(new CommandMeta("where-used",
            "Find every reference to a file across a project (maps, conrefs, links, via keys)",
            WhereUsedCommand.class, false));
        register(new CommandMeta("list-keys",
            "List the effective key space of a DITA root map (optionally DITAVAL-filtered)",
            ListKeysCommand.class, false));
        register(new CommandMeta("resolve-key",
            "Resolve one key against a root map: definition location, href, text",
            ResolveKeyCommand.class, false));
        register(new CommandMeta("check-links",
            "Report broken references: missing targets and undefined keys",
            CheckLinksCommand.class, false));
        register(new CommandMeta("render-preview",
            "Render the styled DITA preview to HTML (key resolution + DITAVAL aware)",
            RenderPreviewCommand.class, false));
        register(new CommandMeta("health",
            "Project reuse-health report: broken refs, undefined/unused keys, orphan topics",
            HealthCommand.class, false));
        register(new CommandMeta("validate-project",
            "Validate every file in a scope (map publication set / glob / root) on disk; "
            + "returns counts plus the failing files",
            ValidateProjectCommand.class, false));
        register(new CommandMeta("project-health",
            "Comprehensive project health: reuse-health (refs, keys, orphans) plus "
            + "DTD/grammar validation of the scope; clean means both pass",
            ProjectHealthCommand.class, false));
        register(new CommandMeta("conref-audit",
            "Audit reuse references for broken element ids: conref/keyref/href whose "
            + "target file exists but whose #fragment id is missing",
            ConrefAuditCommand.class, false));
        register(new CommandMeta("schematron-project",
            "Run a Schematron schema (.sch) against every file in a scope; returns "
            + "failed assertions (business/style rules DTD/XSD can't express)",
            SchematronProjectCommand.class, false));
        register(new CommandMeta("schematron",
            "Apply a Schematron schema to a single document",
            SchematronCommand.class, false));
        register(new CommandMeta("review-list",
            "List the review proposals (agent insertions, deletions, comments) in a document",
            ReviewListCommand.class, false));
        register(new CommandMeta("review-accept",
            "Accept a review proposal by id, or all of them; the writer's decision, refused to agents",
            ReviewAcceptCommand.class, false));
        register(new CommandMeta("review-reject",
            "Reject a review proposal by id, or all of them; the writer's decision, refused to agents",
            ReviewRejectCommand.class, false));
        register(new CommandMeta("review-comment",
            "Leave a draft comment in a document after some text or inside an element",
            ReviewCommentCommand.class, false));
        register(new CommandMeta("sessions",
            "List the agent sessions connected to the editor",
            ListSessionsCommand.class, true));
        register(new CommandMeta("agents",
            "List the ACP registry agents and whether this machine can run them",
            ListAgentsCommand.class, true));
        register(new CommandMeta("audit-log",
            "What agents did in a project: the agent audit log, newest first",
            AuditLogCommand.class, true));
        register(new CommandMeta("validate-deliverables",
            "Validate every deliverable of a project (each deliverable's map "
            + "publication set); one result per deliverable",
            ValidateDeliverablesCommand.class, false));
        register(new CommandMeta("validate-conditions",
            "Scan a project/deliverable for profiling-attribute values that violate a "
            + "subjectScheme's controlled values (e.g. platform=\"macos\" vs \"mac\"); "
            + "reports file, line, attribute, value and a near-miss suggestion",
            ValidateConditionsCommand.class, false));
        register(new CommandMeta("list-subjects",
            "List a subjectScheme's controlled vocabulary: each governed attribute "
            + "and its allowed values",
            ListSubjectsCommand.class, false));
        register(new CommandMeta("metadata-audit",
            "Audit topics/maps against the project's required-metadata policy "
            + "(.dogsbay/config.xml or --policy); reports missing-required, forbidden, "
            + "and out-of-vocabulary metadata per file",
            MetadataAuditCommand.class, false));
        register(new CommandMeta("metadata-export-schematron",
            "Compile the required-metadata policy to ISO Schematron for portable "
            + "validation in any Schematron pipeline (CI, DITA-OT, oXygen)",
            ExportMetadataSchematronCommand.class, false));
        register(new CommandMeta("metadata-set",
            "Bulk-apply metadata changes across a scope, field-preserving "
            + "(set/fill/append/remove on prolog fields); dry-run supported",
            MetadataSetCommand.class, false));
        register(new CommandMeta("edit-map",
            "Structurally edit a DITA map (set-attr/insert/remove/move topicrefs), "
            + "reference-safe and formatting-preserving; dry-run supported",
            EditMapCommand.class, false));
        register(new CommandMeta("reltable-audit",
            "Validate a map's relationship tables (broken/maptarget/type-mismatch/"
            + "degenerate) and preview the related-links they generate",
            ReltableAuditCommand.class, false));
        register(new CommandMeta("edit-reltable",
            "Structurally edit a map's relationship table (create-table/add-row/"
            + "remove-row/add-target/remove-target/set-attr), formatting-preserving",
            EditReltableCommand.class, false));
        register(new CommandMeta("keyword-audit",
            "Survey the keyword vocabulary across a project: frequencies, topics with "
            + "none, near-duplicate spellings, and keyword co-occurrence (relatedness)",
            KeywordAuditCommand.class, false));
        register(new CommandMeta("index-audit",
            "Survey the DITA index (indexterm): entry inventory with frequencies, topics "
            + "with no index terms, and dangling index-see/-also redirects",
            IndexAuditCommand.class, false));
        register(new CommandMeta("glossary-audit",
            "Survey the DITA glossary (glossentry): term inventory, abbreviated-form/term "
            + "references that don't resolve to a glossary entry, and unused glossentries",
            GlossaryAuditCommand.class, false));
        register(new CommandMeta("conref-push-audit",
            "Audit DITA conref push (conaction): inventory of mark/pushbefore/pushafter/"
            + "pushreplace, broken sibling pairing, and unresolvable push targets",
            ConrefPushAuditCommand.class, false));
        register(new CommandMeta("chunk-audit",
            "Audit DITA chunking (@chunk) in maps: inventory + token validation against "
            + "the DITA 1.3 vocabulary (unknown tokens, conflicting combinations)",
            ChunkAuditCommand.class, false));
        register(new CommandMeta("specialization-info",
            "Report a DITA file's specialization: DOCTYPE, root element, @class "
            + "generalization chain (general→specific), and @domains",
            SpecializationInfoCommand.class, false));
        register(new CommandMeta("list-branches",
            "Enumerate a map's DITA 1.3 branch-filter variants (ditavalref): each "
            + "variant's DITAVAL, the topicref it filters, and its generated keyscope",
            ListBranchesCommand.class, false));
        register(new CommandMeta("rename-file",
            "Rename/move a file and rewrite every reference to it (dry-run unless applied)",
            RenameFileCommand.class, false));
        register(new CommandMeta("rename-key",
            "Rename a key: all keydef definitions and every keyref/conkeyref (dry-run unless applied)",
            RenameKeyCommand.class, false));
        register(new CommandMeta("delete-file",
            "Safe delete: report inbound references, optionally remove map references (dry-run unless applied)",
            DeleteFileCommand.class, false));
        register(new CommandMeta("retarget",
            "Rewrite every reference from one target file to another (dry-run unless applied)",
            RetargetCommand.class, false));
        register(new CommandMeta("keyify",
            "Convert direct references into key references and add a keydef (dry-run unless applied)",
            KeyifyCommand.class, false));
        register(new CommandMeta("inline-key",
            "Replace keyref/conkeyref usages with the resolved direct path (dry-run unless applied)",
            InlineKeyCommand.class, false));
        register(new CommandMeta("extract-conref",
            "Move an element (by id) into a warehouse topic and conref it back (dry-run unless applied)",
            ExtractConrefCommand.class, false));
        register(new CommandMeta("create-keydef",
            "Define a text key: add a keydef with keyword text to a map (dry-run unless applied)",
            CreateKeydefCommand.class, false));
        register(new CommandMeta("inline-conref",
            "Replace conrefs to a target element with the content itself (dry-run unless applied)",
            InlineConrefCommand.class, false));
        register(new CommandMeta("rename-element-id",
            "Rename an element id and every #fragment that references it (dry-run unless applied)",
            RenameElementIdCommand.class, false));
        register(new CommandMeta("merge-keydefs",
            "Remove shadowed duplicate keydefs from a root map's closure (dry-run unless applied)",
            MergeKeydefsCommand.class, false));
        register(new CommandMeta("rename-profile-value",
            "Rename a profiling attribute value project-wide incl. ditaval rules (dry-run unless applied)",
            RenameProfileValueCommand.class, false));
        register(new CommandMeta("split-topic",
            "Split a topic: top-level sections become standalone topics wired into the map (dry-run unless applied)",
            SplitTopicCommand.class, false));

        // Editor commands
        register(new CommandMeta("open",
            "Open a file in the editor",
            OpenCommand.class, true));
        register(new CommandMeta("close",
            "Close a document or all documents",
            CloseCommand.class, true));
        register(new CommandMeta("save",
            "Save a document or all documents",
            SaveCommand.class, true));
        register(new CommandMeta("list-documents",
            "List all open documents with metadata",
            ListDocumentsCommand.class, true));
        register(new CommandMeta("get-content",
            "Read the content of an open document",
            GetContentCommand.class, true));
        register(new CommandMeta("set-content",
            "Replace the content of an open document",
            SetContentCommand.class, true));
        register(new CommandMeta("get-outline",
            "Get the structural outline of a document",
            GetOutlineCommand.class, true));
        register(new CommandMeta("get-errors",
            "Get current validation errors for a document",
            GetErrorsCommand.class, true));
        register(new CommandMeta("goto-line",
            "Move the editor caret to a 1-based line",
            GotoLineCommand.class, true));
        register(new CommandMeta("get-cursor",
            "Get the caret position as offset plus 1-based line and column",
            GetCursorCommand.class, true));
        register(new CommandMeta("set-cursor",
            "Move the caret to an offset, or by a signed delta",
            SetCursorCommand.class, true));
        register(new CommandMeta("select-element",
            "Select the XML element surrounding the caret, or just its content",
            SelectElementCommand.class, true));
        register(new CommandMeta("search",
            "Search across project files with regex or text",
            SearchProjectCommand.class, true));
        register(new CommandMeta("list-files",
            "List project files matching a glob pattern",
            ListProjectFilesCommand.class, true));

        // Author view commands
        register(new CommandMeta("author-switch",
            "Switch a document to the WYSIWYG Author view",
            AuthorSwitchCommand.class, true));
        register(new CommandMeta("author-outline",
            "Get the Author view's block tree with ids, types and text",
            AuthorOutlineCommand.class, true));
        register(new CommandMeta("author-insert-block",
            "Insert a block (with required children) under a parent block id",
            AuthorInsertBlockCommand.class, true));
        register(new CommandMeta("author-set-text",
            "Replace the plain text of a text block by id",
            AuthorSetTextCommand.class, true));
        register(new CommandMeta("author-issues",
            "Get the Author view's semantic validation issues",
            AuthorIssuesCommand.class, true));
        register(new CommandMeta("screenshot",
            "Paint the editor window into a PNG (visual verification)",
            ScreenshotCommand.class, true));
    }
}
