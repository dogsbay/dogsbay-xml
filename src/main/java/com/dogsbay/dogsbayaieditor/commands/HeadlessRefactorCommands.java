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
 * Headless refactor command handlers, extracted from {@link HeadlessExecutor}
 * (Slice 2 of the large-file split). Stateless static handlers dispatched from
 * {@code HeadlessExecutor.execute}; the shared existingFile/existingDir helpers stay
 * on HeadlessExecutor. Behavior-preserving move.
 */
final class HeadlessRefactorCommands {

    private HeadlessRefactorCommands() {
    }

    static RefactorResult executeRenameFile(RenameFileCommand cmd) throws CommandException {
        java.io.File target = HeadlessExecutor.existingFile(cmd.file(), "file");
        java.io.File root = HeadlessExecutor.existingDir(cmd.root(), "root");
        if (cmd.newPath() == null || cmd.newPath().isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Missing required newPath");
        }
        java.io.File destination = new java.io.File(cmd.newPath()).getAbsoluteFile();

        com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex index =
                com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex.build(root, null);
        com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.Plan plan =
                com.dogsbay.dogsbayaieditor.links.ReferenceRewriter
                        .planRenameFile(target, destination, index);

        java.util.List<RefactorResult.AttributeEditInfo> edits = new java.util.ArrayList<>();
        for (com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.AttributeEdit edit
                : plan.edits()) {
            edits.add(new RefactorResult.AttributeEditInfo(
                    edit.file().getAbsolutePath(), edit.line(),
                    edit.attribute(), edit.oldValue(), edit.newValue()));
        }

        if (!cmd.apply()) {
            return new RefactorResult(false, target.getAbsolutePath(),
                    destination.getAbsolutePath(), edits, plan.warnings(),
                    0, 0, java.util.List.of());
        }

        if (destination.exists()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Destination already exists: " + destination);
        }
        com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.ApplyResult applied =
                com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.apply(plan);
        return new RefactorResult(true, target.getAbsolutePath(),
                destination.getAbsolutePath(), edits, plan.warnings(),
                applied.editsApplied(), applied.filesChanged(), applied.failures());
    }

    static RefactorResult executeRenameKey(RenameKeyCommand cmd) throws CommandException {
        if (cmd.oldKey() == null || cmd.oldKey().isEmpty()
                || cmd.newKey() == null || cmd.newKey().isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Both oldKey and newKey are required");
        }
        if (cmd.oldKey().equals(cmd.newKey())) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "oldKey and newKey are the same");
        }
        java.io.File root = HeadlessExecutor.existingDir(cmd.root(), "root");

        com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex index =
                com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex.build(root, null);
        com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.Plan plan =
                com.dogsbay.dogsbayaieditor.links.ReferenceRewriter
                        .planRenameKey(cmd.oldKey(), cmd.newKey(), index);

        java.util.List<RefactorResult.AttributeEditInfo> edits = new java.util.ArrayList<>();
        for (com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.AttributeEdit edit
                : plan.edits()) {
            edits.add(new RefactorResult.AttributeEditInfo(
                    edit.file().getAbsolutePath(), edit.line(),
                    edit.attribute(), edit.oldValue(), edit.newValue()));
        }

        if (!cmd.apply()) {
            return new RefactorResult(false, cmd.oldKey(), cmd.newKey(),
                    edits, plan.warnings(), 0, 0, java.util.List.of());
        }
        com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.ApplyResult applied =
                com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.apply(plan);
        return new RefactorResult(true, cmd.oldKey(), cmd.newKey(),
                edits, plan.warnings(),
                applied.editsApplied(), applied.filesChanged(), applied.failures());
    }

    static RefactorResult executeDeleteFile(DeleteFileCommand cmd) throws CommandException {
        java.io.File target = HeadlessExecutor.existingFile(cmd.file(), "file");
        java.io.File root = HeadlessExecutor.existingDir(cmd.root(), "root");

        com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex index =
                com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex.build(root, null);
        com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.Plan plan =
                com.dogsbay.dogsbayaieditor.links.ReferenceRewriter
                        .planDeleteFile(target, cmd.removeRefs(), index);

        // Each "edit" row is one element removal from a map.
        java.util.List<RefactorResult.AttributeEditInfo> edits = new java.util.ArrayList<>();
        for (com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.ElementRemoval removal
                : plan.removals()) {
            edits.add(new RefactorResult.AttributeEditInfo(
                    removal.file().getAbsolutePath(), removal.line(),
                    removal.attribute(), removal.value(),
                    "(<" + removal.element() + "> removed)"));
        }

        if (!cmd.apply()) {
            return new RefactorResult(false, target.getAbsolutePath(), null,
                    edits, plan.warnings(), 0, 0, java.util.List.of());
        }
        com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.ApplyResult applied =
                com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.apply(plan);
        return new RefactorResult(true, target.getAbsolutePath(), null,
                edits, plan.warnings(),
                applied.editsApplied(), applied.filesChanged(), applied.failures());
    }

    static RefactorResult executeRetarget(RetargetCommand cmd) throws CommandException {
        java.io.File from = HeadlessExecutor.existingFile(cmd.from(), "from");
        java.io.File root = HeadlessExecutor.existingDir(cmd.root(), "root");
        if (cmd.to() == null || cmd.to().isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Missing required to");
        }
        java.io.File to = new java.io.File(cmd.to()).getAbsoluteFile();

        com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex index =
                com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex.build(root, null);
        com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.Plan plan =
                com.dogsbay.dogsbayaieditor.links.ReferenceRewriter
                        .planRetarget(from, to, index);
        return finishRefactor(plan, from.getAbsolutePath(), to.getAbsolutePath(),
                cmd.apply());
    }

    static RefactorResult executeKeyify(KeyifyCommand cmd) throws CommandException {
        java.io.File target = HeadlessExecutor.existingFile(cmd.file(), "file");
        java.io.File map = HeadlessExecutor.existingFile(cmd.map(), "map");
        java.io.File root = HeadlessExecutor.existingDir(cmd.root(), "root");
        if (cmd.key() == null || cmd.key().isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Missing required key");
        }

        java.io.File rootMap = null;
        if (cmd.rootMap() != null && !cmd.rootMap().isEmpty()) {
            rootMap = HeadlessExecutor.existingFile(cmd.rootMap(), "rootMap");
        }

        com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex index =
                com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex.build(root, null);
        // Collision checks use the effective key space when a root map is
        // given; the keydef map's own space otherwise.
        com.dogsbay.dogsbayaieditor.links.KeySpace keySpace =
                com.dogsbay.dogsbayaieditor.links.KeySpace.fromRootMap(
                        rootMap != null ? rootMap : map);
        com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.Plan plan =
                com.dogsbay.dogsbayaieditor.links.ReferenceRewriter
                        .planKeyify(target, cmd.key(), map, index, keySpace, rootMap);
        return finishRefactor(plan, target.getAbsolutePath(), cmd.key(), cmd.apply());
    }

    static RefactorResult executeInlineKey(InlineKeyCommand cmd) throws CommandException {
        java.io.File map = HeadlessExecutor.existingFile(cmd.map(), "map");
        java.io.File root = HeadlessExecutor.existingDir(cmd.root(), "root");
        if (cmd.key() == null || cmd.key().isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Missing required key");
        }

        com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex index =
                com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex.build(root, null);
        com.dogsbay.dogsbayaieditor.links.KeySpace keySpace =
                com.dogsbay.dogsbayaieditor.links.KeySpace.fromRootMap(map);
        com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.Plan plan =
                com.dogsbay.dogsbayaieditor.links.ReferenceRewriter
                        .planInlineKey(cmd.key(), index, keySpace);
        return finishRefactor(plan, cmd.key(), null, cmd.apply());
    }

    static RefactorResult executeExtractConref(ExtractConrefCommand cmd)
            throws CommandException {
        java.io.File source = HeadlessExecutor.existingFile(cmd.file(), "file");
        if (cmd.elementId() == null || cmd.elementId().isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Missing required elementId");
        }
        if (cmd.to() == null || cmd.to().isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Missing required to (reuse topic path)");
        }
        java.io.File warehouse = new java.io.File(cmd.to()).getAbsoluteFile();

        com.dogsbay.dogsbayaieditor.links.ConrefExtractor.Plan plan;
        try {
            plan = com.dogsbay.dogsbayaieditor.links.ConrefExtractor
                    .plan(source, cmd.elementId(), warehouse);
        } catch (IllegalArgumentException e) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    e.getMessage());
        } catch (java.io.IOException e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    e.getMessage());
        }

        java.util.List<RefactorResult.AttributeEditInfo> edits = new java.util.ArrayList<>();
        edits.add(new RefactorResult.AttributeEditInfo(
                source.getAbsolutePath(), plan.line(), "extract",
                "<" + plan.elementName() + " id=\"" + plan.elementId() + "\">",
                plan.stub()));
        edits.add(new RefactorResult.AttributeEditInfo(
                warehouse.getAbsolutePath(), -1,
                plan.createWarehouse() ? "create" : "insert", "",
                "<" + plan.elementName() + " id=\"" + plan.elementId()
                        + "\"> into <" + "concept id=\""
                        + plan.warehouseTopicId() + "\">"));

        if (!cmd.apply()) {
            return new RefactorResult(false, source.getAbsolutePath() + "#"
                    + plan.elementId(), warehouse.getAbsolutePath(),
                    edits, plan.warnings(), 0, 0, java.util.List.of());
        }
        com.dogsbay.dogsbayaieditor.links.ConrefExtractor.ApplyResult applied =
                com.dogsbay.dogsbayaieditor.links.ConrefExtractor.apply(plan);
        return new RefactorResult(true, source.getAbsolutePath() + "#"
                + plan.elementId(), warehouse.getAbsolutePath(),
                edits, plan.warnings(),
                applied.done() ? 2 : 0, applied.done() ? 2 : 0, applied.failures());
    }

    static RefactorResult executeCreateKeydef(CreateKeydefCommand cmd)
            throws CommandException {
        java.io.File map = HeadlessExecutor.existingFile(cmd.map(), "map");
        if (cmd.key() == null || cmd.key().isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Missing required key");
        }
        if (cmd.text() == null || cmd.text().isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Missing required text");
        }
        if (cmd.text().contains("<")) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Keyword text must be plain text (no element markup)");
        }
        java.io.File rootMap = null;
        if (cmd.rootMap() != null && !cmd.rootMap().isEmpty()) {
            rootMap = HeadlessExecutor.existingFile(cmd.rootMap(), "rootMap");
        }

        com.dogsbay.dogsbayaieditor.links.KeySpace keySpace =
                com.dogsbay.dogsbayaieditor.links.KeySpace.fromRootMap(
                        rootMap != null ? rootMap : map);
        com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.Plan plan =
                com.dogsbay.dogsbayaieditor.links.ReferenceRewriter
                        .planCreateKeydef(cmd.key(), cmd.text(), map, keySpace, rootMap);
        if (cmd.replaceRoot() == null || cmd.replaceRoot().isEmpty()) {
            return finishRefactor(plan, cmd.key(), map.getAbsolutePath(), cmd.apply());
        }

        // Replace-all: occurrences in regular text content under the root.
        java.io.File replaceRoot = HeadlessExecutor.existingDir(cmd.replaceRoot(), "replaceRoot");
        String stub = "<ph keyref=\"" + cmd.key() + "\"/>";
        java.util.List<com.dogsbay.dogsbayaieditor.links.TextKeyReplacer.Occurrence>
                occurrences;
        try {
            occurrences = com.dogsbay.dogsbayaieditor.links.TextKeyReplacer
                    .scan(replaceRoot, cmd.text());
        } catch (java.io.IOException e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    e.getMessage());
        }

        java.util.List<RefactorResult.AttributeEditInfo> edits = new java.util.ArrayList<>();
        for (com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.ElementInsertion insertion
                : plan.insertions()) {
            edits.add(new RefactorResult.AttributeEditInfo(
                    insertion.file().getAbsolutePath(), -1,
                    "insert", "", insertion.elementText()));
        }
        for (com.dogsbay.dogsbayaieditor.links.TextKeyReplacer.Occurrence occurrence
                : occurrences) {
            edits.add(new RefactorResult.AttributeEditInfo(
                    occurrence.file().getAbsolutePath(), occurrence.line(),
                    "text", occurrence.context(), stub));
        }

        if (!cmd.apply()) {
            return new RefactorResult(false, cmd.key(), map.getAbsolutePath(),
                    edits, plan.warnings(), 0, 0, java.util.List.of());
        }

        com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.ApplyResult applied =
                com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.apply(plan);
        int editsApplied = applied.editsApplied();
        int filesChanged = applied.filesChanged();
        java.util.List<String> failures = new java.util.ArrayList<>(applied.failures());

        java.util.Map<java.io.File, Long> byFile = occurrences.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        com.dogsbay.dogsbayaieditor.links.TextKeyReplacer.Occurrence::file,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));
        for (var entry : byFile.entrySet()) {
            try {
                String content = java.nio.file.Files.readString(entry.getKey().toPath(),
                        java.nio.charset.StandardCharsets.UTF_8);
                String replaced = com.dogsbay.dogsbayaieditor.links.TextKeyReplacer
                        .replace(content, cmd.text(), stub);
                if (replaced == null) {
                    failures.add(entry.getKey() + ": occurrences changed since the plan");
                    continue;
                }
                java.nio.file.Files.writeString(entry.getKey().toPath(), replaced,
                        java.nio.charset.StandardCharsets.UTF_8);
                editsApplied += entry.getValue().intValue();
                filesChanged++;
            } catch (java.io.IOException e) {
                failures.add(entry.getKey() + ": " + e.getMessage());
            }
        }
        return new RefactorResult(true, cmd.key(), map.getAbsolutePath(),
                edits, plan.warnings(), editsApplied, filesChanged, failures);
    }

    static RefactorResult executeInlineConref(InlineConrefCommand cmd)
            throws CommandException {
        java.io.File target = HeadlessExecutor.existingFile(cmd.target(), "target");
        java.io.File root = HeadlessExecutor.existingDir(cmd.root(), "root");
        if (cmd.elementId() == null || cmd.elementId().isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Missing required elementId");
        }
        java.io.File onlyFile = null;
        if (cmd.file() != null && !cmd.file().isEmpty()) {
            onlyFile = HeadlessExecutor.existingFile(cmd.file(), "file");
        }

        com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex index =
                com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex.build(root, null);
        com.dogsbay.dogsbayaieditor.links.ConrefInliner.Plan plan;
        try {
            plan = com.dogsbay.dogsbayaieditor.links.ConrefInliner
                    .plan(target, cmd.elementId(), index, onlyFile);
        } catch (IllegalArgumentException e) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    e.getMessage());
        } catch (java.io.IOException e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    e.getMessage());
        }

        java.util.List<RefactorResult.AttributeEditInfo> edits = new java.util.ArrayList<>();
        for (com.dogsbay.dogsbayaieditor.links.ConrefInliner.Instance instance
                : plan.instances()) {
            edits.add(new RefactorResult.AttributeEditInfo(
                    instance.file().getAbsolutePath(), instance.line(),
                    "conref", instance.conrefValue(),
                    "(inlined <" + plan.elementName() + "> content)"));
        }
        if (!cmd.apply()) {
            return new RefactorResult(false,
                    target.getAbsolutePath() + "#…/" + cmd.elementId(), null,
                    edits, plan.warnings(), 0, 0, java.util.List.of());
        }
        com.dogsbay.dogsbayaieditor.links.ConrefInliner.ApplyResult applied =
                com.dogsbay.dogsbayaieditor.links.ConrefInliner.apply(plan);
        return new RefactorResult(true,
                target.getAbsolutePath() + "#…/" + cmd.elementId(), null,
                edits, plan.warnings(),
                applied.inlined(), applied.filesChanged(), applied.failures());
    }

    static RefactorResult executeRenameElementId(RenameElementIdCommand cmd)
            throws CommandException {
        java.io.File file = HeadlessExecutor.existingFile(cmd.file(), "file");
        java.io.File root = HeadlessExecutor.existingDir(cmd.root(), "root");
        if (cmd.oldId() == null || cmd.oldId().isEmpty()
                || cmd.newId() == null || cmd.newId().isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Both oldId and newId are required");
        }
        if (cmd.oldId().equals(cmd.newId())) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "oldId and newId are the same");
        }
        com.dogsbay.dogsbayaieditor.links.KeySpace keySpace = null;
        if (cmd.rootMap() != null && !cmd.rootMap().isEmpty()) {
            keySpace = com.dogsbay.dogsbayaieditor.links.KeySpace.fromRootMap(
                    HeadlessExecutor.existingFile(cmd.rootMap(), "rootMap"));
        }

        com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex index =
                com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex.build(root, null);
        com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.Plan plan =
                com.dogsbay.dogsbayaieditor.links.ReferenceRewriter
                        .planRenameElementId(file, cmd.oldId(), cmd.newId(),
                                index, keySpace);
        return finishRefactor(plan, file.getAbsolutePath() + "#" + cmd.oldId(),
                "#" + cmd.newId(), cmd.apply());
    }

    static RefactorResult executeMergeKeydefs(MergeKeydefsCommand cmd)
            throws CommandException {
        java.io.File rootMap = HeadlessExecutor.existingFile(cmd.rootMap(), "rootMap");
        com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.Plan plan =
                com.dogsbay.dogsbayaieditor.links.ReferenceRewriter
                        .planMergeKeydefs(rootMap, cmd.key());

        java.util.List<RefactorResult.AttributeEditInfo> edits = new java.util.ArrayList<>();
        for (com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.AttributeEdit edit
                : plan.edits()) {
            edits.add(new RefactorResult.AttributeEditInfo(
                    edit.file().getAbsolutePath(), edit.line(),
                    edit.attribute(), edit.oldValue(), edit.newValue()));
        }
        for (com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.ElementRemoval removal
                : plan.removals()) {
            edits.add(new RefactorResult.AttributeEditInfo(
                    removal.file().getAbsolutePath(), removal.line(),
                    removal.attribute(), removal.value(),
                    "(<" + removal.element() + "> removed)"));
        }
        if (!cmd.apply()) {
            return new RefactorResult(false, rootMap.getAbsolutePath(), null,
                    edits, plan.warnings(), 0, 0, java.util.List.of());
        }
        com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.ApplyResult applied =
                com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.apply(plan);
        return new RefactorResult(true, rootMap.getAbsolutePath(), null,
                edits, plan.warnings(),
                applied.editsApplied(), applied.filesChanged(), applied.failures());
    }

    static RefactorResult executeRenameProfileValue(RenameProfileValueCommand cmd)
            throws CommandException {
        java.io.File root = HeadlessExecutor.existingDir(cmd.root(), "root");
        if (cmd.attribute() == null || cmd.attribute().isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Missing required attribute");
        }
        if (cmd.oldValue() == null || cmd.oldValue().isEmpty()
                || cmd.newValue() == null || cmd.newValue().isEmpty()) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Both oldValue and newValue are required");
        }
        if (cmd.oldValue().equals(cmd.newValue())) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "oldValue and newValue are the same");
        }
        if (cmd.oldValue().matches(".*\\s.*") || cmd.newValue().matches(".*\\s.*")) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    "Profiling values are single tokens (no whitespace)");
        }

        com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.Plan plan;
        try {
            plan = com.dogsbay.dogsbayaieditor.links.ReferenceRewriter
                    .planRenameProfileValue(cmd.attribute(), cmd.oldValue(),
                            cmd.newValue(), root);
        } catch (java.io.IOException e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    e.getMessage());
        }
        return finishRefactor(plan, cmd.attribute() + "=\"" + cmd.oldValue() + "\"",
                cmd.attribute() + "=\"" + cmd.newValue() + "\"", cmd.apply());
    }

    static RefactorResult executeSplitTopic(SplitTopicCommand cmd)
            throws CommandException {
        java.io.File source = HeadlessExecutor.existingFile(cmd.file(), "file");
        java.io.File root = HeadlessExecutor.existingDir(cmd.root(), "root");
        java.io.File map = null;
        if (cmd.map() != null && !cmd.map().isEmpty()) {
            map = HeadlessExecutor.existingFile(cmd.map(), "map");
        }

        com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex index =
                com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex.build(root, null);
        com.dogsbay.dogsbayaieditor.links.TopicSplitter.Plan plan;
        try {
            plan = com.dogsbay.dogsbayaieditor.links.TopicSplitter
                    .plan(source, map, index);
        } catch (IllegalArgumentException e) {
            throw new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
                    e.getMessage());
        } catch (java.io.IOException e) {
            throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR,
                    e.getMessage());
        }

        java.util.List<RefactorResult.AttributeEditInfo> edits = new java.util.ArrayList<>();
        for (com.dogsbay.dogsbayaieditor.links.TopicSplitter.Split split
                : plan.splits()) {
            edits.add(new RefactorResult.AttributeEditInfo(
                    source.getAbsolutePath(), split.line(), "split",
                    "<section> \"" + split.title() + "\"",
                    split.newFile().getName() + " (<concept id=\""
                            + split.newTopicId() + "\">)"));
        }
        for (com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.AttributeEdit edit
                : plan.refEdits()) {
            edits.add(new RefactorResult.AttributeEditInfo(
                    edit.file().getAbsolutePath(), edit.line(),
                    edit.attribute(), edit.oldValue(), edit.newValue()));
        }
        if (map != null) {
            edits.add(new RefactorResult.AttributeEditInfo(
                    map.getAbsolutePath(), -1, "insert", "",
                    plan.splits().size() + " topicref(s)"));
        }

        if (!cmd.apply()) {
            return new RefactorResult(false, source.getAbsolutePath(),
                    plan.splits().size() + " new topic(s)",
                    edits, plan.warnings(), 0, 0, java.util.List.of());
        }
        com.dogsbay.dogsbayaieditor.links.TopicSplitter.ApplyResult applied =
                com.dogsbay.dogsbayaieditor.links.TopicSplitter.apply(plan);
        return new RefactorResult(true, source.getAbsolutePath(),
                applied.filesCreated() + " new topic(s)",
                edits, plan.warnings(),
                applied.editsApplied(), applied.filesCreated(),
                applied.failures());
    }

    /** Converts a plan to a RefactorResult, applying it when requested. */
    private static RefactorResult finishRefactor(
            com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.Plan plan,
            String from, String to, boolean apply) {
        java.util.List<RefactorResult.AttributeEditInfo> edits = new java.util.ArrayList<>();
        for (com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.AttributeEdit edit
                : plan.edits()) {
            String attribute = edit.newAttribute() != null
                    ? edit.attribute() + "→" + edit.newAttribute()
                    : edit.attribute();
            edits.add(new RefactorResult.AttributeEditInfo(
                    edit.file().getAbsolutePath(), edit.line(),
                    attribute, edit.oldValue(), edit.newValue()));
        }
        for (com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.ElementInsertion insertion
                : plan.insertions()) {
            edits.add(new RefactorResult.AttributeEditInfo(
                    insertion.file().getAbsolutePath(), -1,
                    "insert", "", insertion.elementText()));
        }

        if (!apply) {
            return new RefactorResult(false, from, to, edits, plan.warnings(),
                    0, 0, java.util.List.of());
        }
        com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.ApplyResult applied =
                com.dogsbay.dogsbayaieditor.links.ReferenceRewriter.apply(plan);
        return new RefactorResult(true, from, to, edits, plan.warnings(),
                applied.editsApplied(), applied.filesChanged(), applied.failures());
    }
}
