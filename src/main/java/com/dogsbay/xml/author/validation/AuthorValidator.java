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

package com.dogsbay.xml.author.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.AuthorDocument;
import com.dogsbay.xml.author.model.AuthorDocumentListener;
import com.dogsbay.xml.author.model.BlockType;

/**
 * Semantic validation of the block tree (ported from the ditablocks rules):
 * advisory, never blocking edits. {@link #validate} is a pure function;
 * {@link #attach} adds a debounced live mode that re-validates 400 ms after
 * the last model change and hands the issues to a consumer.
 */
public final class AuthorValidator {

    private static final int DEBOUNCE_MS = 400;

    private static final Set<String> TOPIC_ROOTS = Set.of("concept", "task", "reference", "topic");

    private javax.swing.Timer timer;
    private AuthorDocument attached;
    private AuthorDocumentListener listener;

    // ------------------------------------------------------------------
    // Rules
    // ------------------------------------------------------------------

    /** Validates the whole tree; returns issues in document order. */
    public List<ValidationIssue> validate(AuthorDocument doc) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (doc != null && doc.getRoot() != null) {
            check(doc.getRoot(), issues);
            checkDuplicateIds(doc.getRoot(), new java.util.HashMap<>(), issues);
        }
        return issues;
    }

    private void check(AuthorBlock block, List<ValidationIssue> issues) {
        String type = block.getType().getName();

        if (TOPIC_ROOTS.contains(type)) {
            AuthorBlock title = firstChild(block, "title");
            if (title == null) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, block,
                        "Topic has no title"));
            } else if (isEmptyText(title)) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, title,
                        "Title is empty"));
            }
        }

        switch (type) {
            case "step", "substep" -> {
                AuthorBlock cmd = firstChild(block, "cmd");
                boolean hasRawChild = block.getChildren().stream().anyMatch(
                        c -> c.getType().getCategory() == BlockType.Category.RAW);
                if (cmd == null && !hasRawChild) {
                    // raw children may hold the cmd in markup we couldn't model
                    issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, block,
                            "Step has no command"));
                } else if (cmd != null && isEmptyText(cmd) && block.getAttribute("conref") == null) {
                    issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, cmd,
                            "Command is empty"));
                }
            }
            case "steps", "substeps" -> {
                if (block.getChildren().isEmpty()) {
                    issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, block,
                            "Steps list is empty"));
                }
            }
            case "ul", "ol" -> {
                if (block.getChildren().isEmpty()) {
                    issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, block,
                            "List has no items"));
                }
            }
            case "li" -> {
                if (isEmptyText(block) && block.getChildren().isEmpty()) {
                    issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, block,
                            "List item is empty"));
                }
            }
            case "image" -> {
                String href = block.getAttribute("href");
                String keyref = block.getAttribute("keyref");
                if ((href == null || href.isBlank()) && (keyref == null || keyref.isBlank())) {
                    issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, block,
                            "Image has no href"));
                }
            }
            case "glossentry" -> {
                AuthorBlock term = firstChild(block, "glossterm");
                if (term == null) {
                    issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, block,
                            "Glossary entry has no term"));
                } else if (isEmptyText(term)) {
                    issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, term,
                            "Glossary term is empty"));
                }
            }
            case "dlentry" -> {
                if (firstChild(block, "dt") == null) {
                    issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, block,
                            "Definition entry has no term"));
                }
                if (firstChild(block, "dd") == null) {
                    issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, block,
                            "Definition entry has no description"));
                }
            }
            case "link" -> {
                String href = block.getAttribute("href");
                String keyref = block.getAttribute("keyref");
                if ((href == null || href.isBlank()) && (keyref == null || keyref.isBlank())) {
                    issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, block,
                            "Link has no target"));
                }
            }
            case "simpletable" -> checkRowWidths(block, issues,
                    Set.of("strow", "sthead"), "strow");
            case "tgroup" -> {
                for (AuthorBlock section : block.getChildren()) {
                    String name = section.getType().getName();
                    if (("thead".equals(name) || "tbody".equals(name))
                            && section.getChildren().isEmpty()) {
                        issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, section,
                                "Table has no rows"));
                    }
                }
                checkCalsWidths(block, issues);
            }
            default -> {
                // no rule for this type
            }
        }

        for (AuthorBlock child : block.getChildren()) {
            check(child, issues);
        }
    }

    /**
     * Warns on rows whose cell count differs from the first row's. For
     * simpletable the rows are direct children; for tgroup they sit inside
     * thead/tbody sections.
     */
    private void checkRowWidths(AuthorBlock table, List<ValidationIssue> issues,
            Set<String> directRowTypes, String nestedRowType) {
        List<AuthorBlock> rows = new ArrayList<>();
        for (AuthorBlock child : table.getChildren()) {
            String name = child.getType().getName();
            if (directRowTypes.contains(name)) {
                rows.add(child);
            } else {
                for (AuthorBlock nested : child.getChildren()) {
                    if (nestedRowType.equals(nested.getType().getName())) {
                        rows.add(nested);
                    }
                }
            }
        }
        if ("simpletable".equals(table.getType().getName())
                && rows.stream().noneMatch(r -> "strow".equals(r.getType().getName()))) {
            issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, table,
                    "Table has no rows"));
        }
        int expected = -1;
        for (AuthorBlock row : rows) {
            int cells = row.getChildren().size();
            if (expected < 0) {
                expected = cells;
            } else if (cells != expected) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, row,
                        "Row has " + cells + " cells; the table is " + expected + " wide"));
            }
        }
    }

    /**
     * CALS widths, span-aware: a cell spanning {@code namest}..{@code nameend}
     * counts as that many columns, and {@code morerows} carries a cell's
     * columns into the rows below. Rows are measured against {@code @cols}
     * when it parses, else against the first row.
     */
    private void checkCalsWidths(AuthorBlock tgroup, List<ValidationIssue> issues) {
        List<String> colNames = new ArrayList<>();
        for (AuthorBlock c : tgroup.getChildren()) {
            if ("colspec".equals(c.getType().getName())) {
                colNames.add(c.getAttribute("colname"));
            }
        }
        int cols = -1;
        try {
            cols = Integer.parseInt(tgroup.getAttribute("cols").trim());
        } catch (RuntimeException e) {
            // no usable cols: the first row sets the width
        }
        if (cols > 0 && !colNames.isEmpty() && colNames.size() != cols) {
            issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, tgroup,
                    "Table declares " + cols + " columns but defines " + colNames.size()));
        }
        int expected = cols;
        for (AuthorBlock section : tgroup.getChildren()) {
            String name = section.getType().getName();
            if (!"thead".equals(name) && !"tbody".equals(name)) {
                continue;
            }
            List<int[]> carried = new ArrayList<>();   // [columns, rows left] from morerows cells above
            for (AuthorBlock row : section.getChildren()) {
                if (!"row".equals(row.getType().getName())) {
                    continue;
                }
                int width = 0;
                for (int[] c : carried) {
                    width += c[0];
                }
                List<int[]> next = new ArrayList<>();
                for (int[] c : carried) {
                    if (c[1] > 1) {
                        next.add(new int[] {c[0], c[1] - 1});
                    }
                }
                for (AuthorBlock cell : row.getChildren()) {
                    int span = spanWidth(cell, colNames);
                    width += span;
                    int more = 0;
                    try {
                        more = Integer.parseInt(cell.getAttribute("morerows").trim());
                    } catch (RuntimeException e) {
                        // none
                    }
                    if (more > 0) {
                        next.add(new int[] {span, more});
                    }
                }
                carried = next;
                if (expected < 0) {
                    expected = width;
                } else if (width != expected) {
                    issues.add(new ValidationIssue(ValidationIssue.Severity.WARNING, row,
                            "Row covers " + width + " columns; the table is " + expected + " wide"));
                }
            }
        }
    }

    private static int spanWidth(AuthorBlock cell, List<String> colNames) {
        String start = cell.getAttribute("namest");
        String end = cell.getAttribute("nameend");
        if (start != null && end != null) {
            int from = colNames.indexOf(start);
            int to = colNames.indexOf(end);
            if (from >= 0 && to >= from) {
                return to - from + 1;
            }
        }
        return 1;
    }

    private void checkDuplicateIds(AuthorBlock block, java.util.Map<String, AuthorBlock> seen,
            List<ValidationIssue> issues) {
        String id = block.getAttribute("id");
        if (id != null && !id.isBlank()) {
            AuthorBlock first = seen.putIfAbsent(id, block);
            if (first != null) {
                issues.add(new ValidationIssue(ValidationIssue.Severity.ERROR, block,
                        "Duplicate id \"" + id + "\" (also on " + first.getType().getLabel() + ")"));
            }
        }
        for (AuthorBlock child : block.getChildren()) {
            checkDuplicateIds(child, seen, issues);
        }
    }

    private static AuthorBlock firstChild(AuthorBlock block, String typeName) {
        for (AuthorBlock child : block.getChildren()) {
            if (typeName.equals(child.getType().getName())) {
                return child;
            }
        }
        return null;
    }

    private static boolean isEmptyText(AuthorBlock block) {
        if (block.getType().getCategory() != BlockType.Category.TEXT) {
            return false;
        }
        // a lone reference run (keyref/href) counts as content
        return block.getText().isEmpty()
                || block.getText().stream().allMatch(r -> r.text().isBlank() && r.attrs().isEmpty());
    }

    // ------------------------------------------------------------------
    // Live mode
    // ------------------------------------------------------------------

    /** Revalidates {@code doc} (debounced) on every change; also fires once now. */
    public void attach(AuthorDocument doc, Consumer<List<ValidationIssue>> consumer) {
        detach();
        if (doc == null) {
            consumer.accept(List.of());
            return;
        }
        attached = doc;
        timer = new javax.swing.Timer(DEBOUNCE_MS, e -> consumer.accept(validate(doc)));
        timer.setRepeats(false);
        listener = e -> timer.restart();
        doc.addListener(listener);
        consumer.accept(validate(doc));
    }

    public void detach() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        if (attached != null && listener != null) {
            attached.removeListener(listener);
        }
        attached = null;
        listener = null;
    }
}
