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

package com.dogsbay.dogsbayaieditor.links;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Extract-to-conref: move an element (located by its {@code id}) from a
 * topic into a warehouse topic, replacing the original with a
 * {@code conref} stub. Two-phase like {@link ReferenceRewriter}:
 * {@link #plan} computes everything without touching files;
 * {@link #apply} executes.
 */
public final class ConrefExtractor {

    /** Body containers an extracted element can be appended to. */
    private static final String[] BODY_TAGS = {"conbody", "body", "refbody", "taskbody"};

    /**
     * A computed extraction.
     *
     * @param source           the topic the element is extracted from
     * @param elementName      local name of the extracted element
     * @param elementId        the id used as the conref fragment (generated
     *                         when the element didn't carry one)
     * @param sourceText       the element exactly as written in the source
     * @param warehouseText    the element as it lands in the warehouse —
     *                         identical, or with the generated id injected
     * @param line             1-based line of the element in the source
     * @param warehouse        the warehouse topic receiving the element
     * @param createWarehouse  true when the warehouse file will be created
     * @param warehouseTopicId the warehouse topic's root id
     * @param conrefValue      the conref the stub will carry
     * @param warnings         non-fatal concerns
     */
    public record Plan(File source, String elementName, String elementId,
                       String sourceText, String warehouseText, int line,
                       File warehouse, boolean createWarehouse,
                       String warehouseTopicId, String conrefValue,
                       List<String> warnings) {

        /** The stub that replaces the extracted element. */
        public String stub() {
            return "<" + elementName + " conref=\"" + conrefValue + "\"/>";
        }

        /** True when the extraction adds an id the element didn't have. */
        public boolean idGenerated() {
            return !sourceText.equals(warehouseText);
        }
    }

    /** Outcome of applying a plan. */
    public record ApplyResult(boolean done, List<String> failures) {
    }

    private ConrefExtractor() {
    }

    /**
     * Plans extracting the element with {@code id="elementId"} from
     * {@code source} into {@code warehouse} (created as a new concept when
     * it doesn't exist).
     *
     * @throws IOException              when the source can't be read
     * @throws IllegalArgumentException when the element can't be located,
     *                                  or the warehouse can't accept it
     */
    public static Plan plan(File source, String elementId, File warehouse)
            throws IOException {
        String content = Files.readString(source.toPath(), StandardCharsets.UTF_8);
        int[] span = ReferenceRewriter.elementSpan(content, "id", elementId);
        if (span == null) {
            throw new IllegalArgumentException("No element with id=\"" + elementId
                    + "\" found in " + source.getName());
        }
        return planSpan(source, content, span[0], span[2], elementId, warehouse);
    }

    /**
     * Plans extracting the element at {@code [start, end)} of the source's
     * current content — the caret-based GUI flow. When {@code existingId}
     * is null an unused id is generated and injected into the warehouse
     * copy (the source copy is replaced by the stub, so it needs none).
     */
    public static Plan planAt(File source, int start, int end, String existingId,
                              File warehouse) throws IOException {
        return planAt(source, start, end, existingId, warehouse, null);
    }

    /**
     * Like {@link #planAt(File, int, int, String, File)}, but when the
     * element has no id, {@code desiredId} names the one to add instead of
     * a generated one (refused on collision like an existing id).
     */
    public static Plan planAt(File source, int start, int end, String existingId,
                              File warehouse, String desiredId) throws IOException {
        String content = Files.readString(source.toPath(), StandardCharsets.UTF_8);
        if (start < 0 || end > content.length() || start >= end
                || content.charAt(start) != '<') {
            throw new IllegalArgumentException("The document changed since the "
                    + "element was located — save and try again");
        }
        return planSpan(source, content, start, end, existingId, warehouse, desiredId);
    }

    /**
     * A collision-free id suggestion for extracting an {@code elementName}
     * into {@code warehouse} — the default offered when prompting the user.
     */
    public static String suggestId(String elementName, File warehouse) throws IOException {
        String content = warehouse.isFile()
                ? Files.readString(warehouse.toPath(), StandardCharsets.UTF_8) : null;
        String topicId = warehouse.isFile()
                ? ReferenceRewriter.rootTopicId(warehouse) : topicIdFor(warehouse);
        return unusedId(elementName, content, topicId);
    }

    private static Plan planSpan(File source, String content, int start, int end,
                                 String existingId, File warehouse) throws IOException {
        return planSpan(source, content, start, end, existingId, warehouse, null);
    }

    private static Plan planSpan(File source, String content, int start, int end,
                                 String existingId, File warehouse,
                                 String desiredId) throws IOException {
        String sourceText = content.substring(start, end);
        int nameEnd = start + 1;
        while (nameEnd < content.length()
                && !Character.isWhitespace(content.charAt(nameEnd))
                && content.charAt(nameEnd) != '>' && content.charAt(nameEnd) != '/') {
            nameEnd++;
        }
        String elementName = content.substring(start + 1, nameEnd);
        int line = 1 + (int) content.substring(0, start).chars()
                .filter(c -> c == '\n').count();

        List<String> warnings = new ArrayList<>();
        boolean create = !warehouse.exists();
        String warehouseContent = null;
        String warehouseTopicId;
        if (create) {
            warehouseTopicId = topicIdFor(warehouse);
        } else {
            warehouseContent =
                    Files.readString(warehouse.toPath(), StandardCharsets.UTF_8);
            warehouseTopicId = ReferenceRewriter.rootTopicId(warehouse);
            if (warehouseTopicId == null) {
                throw new IllegalArgumentException(
                        "Reuse topic has no topic id: " + warehouse.getName());
            }
            if (findBodyClose(warehouseContent) < 0) {
                throw new IllegalArgumentException("Reuse topic has no body element "
                        + "(conbody/body/refbody) to receive the content: "
                        + warehouse.getName());
            }
        }

        String elementId = existingId;
        String warehouseText = sourceText;
        if (elementId == null) {
            elementId = desiredId != null ? desiredId
                    : unusedId(elementName, warehouseContent, warehouseTopicId);
            if (idTaken(elementId, warehouseContent, warehouseTopicId)) {
                throw new IllegalArgumentException("Reuse topic already has an element "
                        + "with id=\"" + elementId + "\": " + warehouse.getName());
            }
            warehouseText = "<" + elementName + " id=\"" + elementId + "\""
                    + sourceText.substring(1 + elementName.length());
            warnings.add("Element had no id — '" + elementId
                    + "' is added to the copy in the reuse topic");
        } else if (idTaken(elementId, warehouseContent, warehouseTopicId)) {
            throw new IllegalArgumentException("Reuse topic already has an element "
                    + "with id=\"" + elementId + "\": " + warehouse.getName());
        }

        String conref = ReferenceRewriter.relativePath(
                source.getParentFile(), warehouse)
                + "#" + warehouseTopicId + "/" + elementId;
        if (sourceText.contains("conref=") || sourceText.contains("conkeyref=")) {
            warnings.add("Extracted element itself contains conref/conkeyref "
                    + "references — verify they still resolve from "
                    + warehouse.getName());
        }
        return new Plan(source, elementName, elementId, sourceText, warehouseText,
                line, warehouse, create, warehouseTopicId, conref, warnings);
    }

    /** An id not yet used in the warehouse (nor equal to its topic id). */
    private static String unusedId(String elementName, String warehouseContent,
                                   String warehouseTopicId) {
        String base = elementName + "-reuse";
        String candidate = base;
        int n = 2;
        while (idTaken(candidate, warehouseContent, warehouseTopicId)) {
            candidate = base + "-" + n++;
        }
        return candidate;
    }

    private static boolean idTaken(String id, String warehouseContent,
                                   String warehouseTopicId) {
        return id.equals(warehouseTopicId)
                || (warehouseContent != null
                    && (warehouseContent.contains("id=\"" + id + "\"")
                        || warehouseContent.contains("id='" + id + "'")));
    }

    /**
     * Applies a plan: writes the warehouse (created or appended), then
     * replaces the element in the source with the conref stub.
     */
    public static ApplyResult apply(Plan plan) {
        List<String> failures = new ArrayList<>();
        try {
            if (plan.createWarehouse()) {
                File parent = plan.warehouse().getParentFile();
                if (parent != null) {
                    Files.createDirectories(parent.toPath());
                }
                Files.writeString(plan.warehouse().toPath(),
                        newWarehouseContent(plan), StandardCharsets.UTF_8);
            } else {
                String content = Files.readString(
                        plan.warehouse().toPath(), StandardCharsets.UTF_8);
                int close = findBodyClose(content);
                if (close < 0) {
                    failures.add(plan.warehouse() + ": body element disappeared");
                    return new ApplyResult(false, failures);
                }
                Files.writeString(plan.warehouse().toPath(),
                        content.substring(0, close) + plan.warehouseText() + "\n"
                                + content.substring(close),
                        StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            failures.add(plan.warehouse() + ": " + e.getMessage());
            return new ApplyResult(false, failures);
        }

        try {
            String content = Files.readString(
                    plan.source().toPath(), StandardCharsets.UTF_8);
            int idx = content.indexOf(plan.sourceText());
            if (idx < 0) {
                failures.add(plan.source() + ": element changed since the plan "
                        + "was computed — re-run the extraction");
                return new ApplyResult(false, failures);
            }
            Files.writeString(plan.source().toPath(),
                    content.substring(0, idx) + plan.stub()
                            + content.substring(idx + plan.sourceText().length()),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            failures.add(plan.source() + ": " + e.getMessage());
            return new ApplyResult(false, failures);
        }
        return new ApplyResult(true, failures);
    }

    private static String newWarehouseContent(Plan plan) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">\n"
                + "<concept id=\"" + plan.warehouseTopicId() + "\">\n"
                + "  <title>Reused content</title>\n"
                + "  <conbody>\n"
                + plan.warehouseText() + "\n"
                + "  </conbody>\n"
                + "</concept>\n";
    }

    /** Index of the warehouse's closing body tag, or -1. */
    private static int findBodyClose(String content) {
        for (String tag : BODY_TAGS) {
            int idx = content.lastIndexOf("</" + tag);
            if (idx >= 0) {
                return idx;
            }
        }
        return -1;
    }

    /** A topic id derived from the warehouse file name. */
    private static String topicIdFor(File warehouse) {
        String name = warehouse.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        String id = name.replaceAll("[^A-Za-z0-9_-]", "-");
        return id.isEmpty() || !Character.isLetter(id.charAt(0)) ? "warehouse" : id;
    }
}
