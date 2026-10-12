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

package com.dogsbay.xml.author.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.dogsbay.xml.author.undo.BlockEdit;

/**
 * The structural editing operations behind keyboard and menu actions, each
 * committed as one undoable transaction. Pure model layer — UI code calls
 * these, posts the returned edit, and focuses the returned block.
 */
public final class BlockOperations {

    /** A committed structural change plus the block that should receive focus. */
    public record Result(BlockEdit edit, AuthorBlock focus) {
    }

    /**
     * Child types auto-created when a block is inserted (e.g. a step is
     * unusable without a cmd). Applied recursively.
     */
    private static final Map<String, List<String>> REQUIRED_CHILDREN = Map.ofEntries(
            Map.entry("step", List.of("cmd")),
            Map.entry("substep", List.of("cmd")),
            Map.entry("ul", List.of("li")),
            Map.entry("ol", List.of("li")),
            Map.entry("steps", List.of("step")),
            Map.entry("substeps", List.of("substep")),
            Map.entry("choices", List.of("choice")),
            Map.entry("simpletable", List.of("sthead", "strow")),
            Map.entry("strow", List.of("stentry")),
            Map.entry("sthead", List.of("stentry")),
            Map.entry("table", List.of("tgroup")),
            Map.entry("tgroup", List.of("tbody")),
            Map.entry("thead", List.of("row")),
            Map.entry("tbody", List.of("row")),
            Map.entry("row", List.of("entry")),
            Map.entry("dl", List.of("dlentry")),
            Map.entry("dlentry", List.of("dt", "dd")),
            Map.entry("related-links", List.of("link")),
            Map.entry("glossentry", List.of("glossterm", "glossdef")),
            Map.entry("fig", List.of("title")),
            Map.entry("properties", List.of("property")),
            Map.entry("property", List.of("proptype", "propvalue", "propdesc")),
            Map.entry("choicetable", List.of("chhead", "chrow")),
            Map.entry("chhead", List.of("choptionhd", "chdeschd")),
            Map.entry("chrow", List.of("choption", "chdesc")),
            Map.entry("parml", List.of("plentry")),
            Map.entry("plentry", List.of("pt", "pd")),
            Map.entry("sl", List.of("sli")),
            Map.entry("dlhead", List.of("dthd", "ddhd")),
            Map.entry("steps-unordered", List.of("step")),
            Map.entry("troublebody", List.of("condition", "troubleSolution")),
            Map.entry("troubleSolution", List.of("cause", "remedy")),
            Map.entry("remedy", List.of("steps")),
            Map.entry("glossgroup", List.of("title", "glossentry")),
            Map.entry("hazardstatement", List.of("messagepanel")),
            Map.entry("section", List.of("title")));

    private BlockOperations() {
    }

    // ------------------------------------------------------------------
    // Enter / Backspace
    // ------------------------------------------------------------------

    /**
     * Splits a text block at the caret: {@code block} keeps {@code before},
     * a new sibling of the same type receives {@code after}. The block's
     * child blocks (e.g. a nested list inside an li) stay with the first half.
     */
    public static Result split(AuthorDocument doc, AuthorBlock block,
            List<InlineRun> before, List<InlineRun> after) {
        Transaction tx = doc.begin("Split " + block.getType().getLabel());
        tx.setText(block, before);
        AuthorBlock sibling = tx.insertBlock(block.getType().getName(),
                block.getParent(), block.indexInParent() + 1);
        tx.setText(sibling, after);
        return new Result(tx.commit(), sibling);
    }

    /**
     * Deletes an (empty) block; returns the previous text block — or the
     * parent — as the focus target. Refuses blocks marked not deletable and
     * blocks whose parent would become an invalid empty container is allowed —
     * validation flags it rather than the operation blocking it.
     */
    public static Result deleteBlock(AuthorDocument doc, AuthorBlock block) {
        if (!block.getType().isDeletable() || block.getParent() == null) {
            return null;
        }
        AuthorBlock focus = previousTextBlock(doc, block);
        Transaction tx = doc.begin("Delete " + block.getType().getLabel());
        tx.removeBlock(block);
        return new Result(tx.commit(), focus);
    }

    /** The closest text-bearing block before {@code block} in document order. */
    /** The last text block of the document in reading order, else null. */
    public static AuthorBlock lastTextBlock(AuthorDocument doc) {
        AuthorBlock last = null;
        for (AuthorBlock b : flatten(doc.getRoot())) {
            if (b.getType().hasText()) {
                last = b;
            }
        }
        return last;
    }

    public static AuthorBlock previousTextBlock(AuthorDocument doc, AuthorBlock block) {
        AuthorBlock previous = null;
        for (AuthorBlock candidate : flatten(doc.getRoot())) {
            if (candidate == block) {
                break;
            }
            if (candidate.getType().hasText()) {
                previous = candidate;
            }
        }
        return previous;
    }

    /** The closest text-bearing block after {@code block} in document order. */
    public static AuthorBlock nextTextBlock(AuthorDocument doc, AuthorBlock block) {
        boolean seen = false;
        for (AuthorBlock candidate : flatten(doc.getRoot())) {
            if (seen && candidate.getType().hasText()) {
                return candidate;
            }
            if (candidate == block) {
                seen = true;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Move / indent
    // ------------------------------------------------------------------

    /** Moves a block up or down among its siblings; null when at the edge. */
    public static Result move(AuthorDocument doc, AuthorBlock block, int delta) {
        AuthorBlock parent = block.getParent();
        if (parent == null) {
            return null;
        }
        int index = block.indexInParent();
        int target = index + delta;
        if (target < 0 || target >= parent.getChildren().size()) {
            return null;
        }
        Transaction tx = doc.begin("Move " + block.getType().getLabel());
        tx.moveBlock(block, parent, delta > 0 ? target + 1 : target);
        return new Result(tx.commit(), block);
    }

    /**
     * Indents a list item: it becomes part of a nested list under its
     * preceding sibling item (created if needed).
     */
    public static Result indentListItem(AuthorDocument doc, AuthorBlock li) {
        AuthorBlock list = li.getParent();
        if (li.indexInParent() == 0 || list == null || !isList(list)) {
            return null;
        }
        AuthorBlock previousItem = list.getChildren().get(li.indexInParent() - 1);
        Transaction tx = doc.begin("Indent " + li.getType().getLabel());
        AuthorBlock nested = lastNestedList(previousItem, list.getType().getName());
        if (nested == null) {
            nested = tx.insertBlock(list.getType().getName(), previousItem,
                    previousItem.getChildren().size());
        }
        tx.moveBlock(li, nested, nested.getChildren().size());
        return new Result(tx.commit(), li);
    }

    /**
     * Outdents a list item: it moves after its grandparent list item.
     * Later siblings stay where they are.
     */
    public static Result outdentListItem(AuthorDocument doc, AuthorBlock li) {
        AuthorBlock list = li.getParent();
        AuthorBlock outerItem = list == null ? null : list.getParent();
        if (list == null || outerItem == null || !isList(list)
                || !"li".equals(outerItem.getType().getName())) {
            return null;
        }
        AuthorBlock outerList = outerItem.getParent();
        Transaction tx = doc.begin("Outdent " + li.getType().getLabel());
        tx.moveBlock(li, outerList, outerItem.indexInParent() + 1);
        if (list.getChildren().isEmpty()) {
            tx.removeBlock(list);
        }
        return new Result(tx.commit(), li);
    }

    private static boolean isList(AuthorBlock block) {
        String name = block.getType().getName();
        return "ul".equals(name) || "ol".equals(name);
    }

    private static AuthorBlock lastNestedList(AuthorBlock item, String listType) {
        List<AuthorBlock> children = item.getChildren();
        if (!children.isEmpty()) {
            AuthorBlock last = children.get(children.size() - 1);
            if (last.getType().getName().equals(listType)) {
                return last;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Insertion
    // ------------------------------------------------------------------

    /**
     * Inserts a block of {@code typeName} under {@code parent} at
     * {@code index}, auto-creating required descendants (a step gets its cmd,
     * a table gets a row with a cell, ...). Returns the deepest text block of
     * the created subtree as the focus target.
     */
    public static Result insertBlock(AuthorDocument doc, String typeName,
            AuthorBlock parent, int index) {
        int at = doc.getRegistry().insertIndex(parent, typeName, index);
        if (at < 0) {
            throw new AuthorStructureException("'" + typeName + "' cannot be inserted into '"
                    + parent.getType().getName() + "' here");
        }
        Transaction tx = doc.begin("Insert " + doc.getRegistry().require(typeName).getLabel());
        AuthorBlock created = tx.insertBlock(typeName, parent, at);
        createRequiredChildren(tx, created);
        AuthorBlock focus = firstTextBlock(created);
        return new Result(tx.commit(), focus != null ? focus : created);
    }

    private static void createRequiredChildren(Transaction tx, AuthorBlock block) {
        for (String childType : REQUIRED_CHILDREN.getOrDefault(block.getType().getName(), List.of())) {
            AuthorBlock child = tx.insertBlock(childType, block, block.getChildren().size());
            createRequiredChildren(tx, child);
        }
        if ("tgroup".equals(block.getType().getName())) {
            // a column group needs its column count and one column definition per column
            tx.setAttribute(block, "cols", "1");
            AuthorBlock colspec = tx.insertBlock("colspec", block, 0);
            tx.setAttribute(colspec, "colname", "col1");
        }
    }

    /** The first text block at or under the given block in reading order, else null. */
    public static AuthorBlock firstTextBlock(AuthorBlock block) {
        if (block.getType().hasText()) {
            return block;
        }
        for (AuthorBlock child : block.getChildren()) {
            AuthorBlock found = firstTextBlock(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Lists
    // ------------------------------------------------------------------

    /**
     * Enter on an empty last item: the item is removed and a paragraph is
     * placed after the list, so a writer types out of a list the way word
     * processors let them.
     */
    public static Result exitList(AuthorDocument doc, AuthorBlock item) {
        AuthorBlock list = item.getParent();
        AuthorBlock container = list == null ? null : list.getParent();
        if (container == null || !isListItem(item) || !item.getChildren().isEmpty() || !item.getText().isEmpty()
                || item.indexInParent() != list.getChildren().size() - 1 || list.getChildren().size() < 2) {
            return null;
        }
        if (!doc.getRegistry().isDita()) {
            return null;   // generic XML: "li" and "p" are just names there
        }
        Transaction tx = doc.begin("Leave list");
        tx.removeBlock(item);
        int at = doc.getRegistry().insertIndex(container, "p", list.indexInParent() + 1);
        AuthorBlock p = at < 0 ? null : tx.insertBlock("p", container, at);
        return new Result(tx.commit(), p != null ? p : list);
    }

    /**
     * Backspace in an empty block: the block goes; the only item of a list
     * takes the list with it, since an empty list is only something the
     * validator would flag.
     */
    public static Result deleteEmptyBlock(AuthorDocument doc, AuthorBlock block) {
        AuthorBlock list = block.getParent();
        if (isListItem(block) && list != null && list.getChildren().size() == 1 && list.getParent() != null
                && list.getType().isDeletable()) {
            return deleteBlock(doc, list);
        }
        return deleteBlock(doc, block);
    }

    public static boolean isListItem(AuthorBlock block) {
        String n = block.getType().getName();
        return "li".equals(n) || "sli".equals(n);
    }

    // ------------------------------------------------------------------
    // Tables: simpletable (strow/stentry) and CALS (row/entry under thead/tbody)
    // ------------------------------------------------------------------

    /** The row and cell element names of a table dialect; choice tables have fixed cells and stay out. */
    private record TableShape(String rowType, String cellType) {}

    private static TableShape shapeOf(AuthorDocument doc, AuthorBlock row) {
        TableShape shape = switch (row.getType().getName()) {
            case "strow", "sthead" -> new TableShape("strow", "stentry");
            case "row" -> new TableShape("row", "entry");
            default -> null;
        };
        // a generic document may use these names without meaning DITA tables by them
        return doc.getRegistry().isDita() ? shape : null;
    }

    private static String rowTypeFor(AuthorDocument doc, AuthorBlock row) {
        TableShape s = shapeOf(doc, row);
        return s == null ? null : s.rowType();
    }

    private static String cellTypeFor(AuthorDocument doc, AuthorBlock row) {
        TableShape s = shapeOf(doc, row);
        return s == null ? null : s.cellType();
    }

    /** A row that holds data: a simpletable body row, or a CALS row inside {@code tbody}. */
    public static boolean isBodyRow(AuthorBlock row) {
        String name = row.getType().getName();
        AuthorBlock parent = row.getParent();
        return "strow".equals(name) || ("row".equals(name) && parent != null && "tbody".equals(parent.getType().getName()));
    }

    /** A header row: a {@code sthead}, or a CALS row inside {@code thead}. */
    public static boolean isHeaderRow(AuthorBlock row) {
        String name = row.getType().getName();
        AuthorBlock parent = row.getParent();
        return "sthead".equals(name) || ("row".equals(name) && parent != null && "thead".equals(parent.getType().getName()));
    }

    /** A {@code colname} no existing colspec uses. */
    static String freeColumnName(List<AuthorBlock> colspecs) {
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (AuthorBlock c : colspecs) {
            taken.add(c.getAttribute("colname"));
        }
        for (int n = colspecs.size() + 1; ; n++) {
            String name = "col" + n;
            if (!taken.contains(name)) {
                return name;
            }
        }
    }

    /** The {@code tgroup} of a CALS row, else null. */
    private static AuthorBlock tgroupOf(AuthorBlock row) {
        AuthorBlock section = row.getParent();
        AuthorBlock tgroup = section == null ? null : section.getParent();
        return "row".equals(row.getType().getName()) && tgroup != null && "tgroup".equals(tgroup.getType().getName())
                ? tgroup : null;
    }

    /** Every row of the table {@code row} belongs to, header rows included. */
    private static List<AuthorBlock> rowsOfTable(AuthorBlock row) {
        List<AuthorBlock> rows = new ArrayList<>();
        AuthorBlock tgroup = tgroupOf(row);
        if (tgroup != null) {
            for (AuthorBlock section : tgroup.getChildren()) {
                for (AuthorBlock r : section.getChildren()) {
                    if ("row".equals(r.getType().getName())) {
                        rows.add(r);
                    }
                }
            }
            return rows;
        }
        AuthorBlock table = row.getParent();
        if (table != null) {
            for (AuthorBlock r : table.getChildren()) {
                if (isTableRow(r)) {
                    rows.add(r);
                }
            }
        }
        return rows;
    }

    /** Inserts a row after the given one, with as many cells as the table is wide. */
    public static Result addRowAfter(AuthorDocument doc, AuthorBlock row) {
        String rowType = rowTypeFor(doc, row);
        String cellType = cellTypeFor(doc, row);
        AuthorBlock parent = row.getParent();
        if (rowType == null || cellType == null || parent == null) {
            return null;
        }
        // a header row's "after" is the first body row (a simpletable header is followed by its rows anyway)
        AuthorBlock into = parent;
        int at = row.indexInParent() + 1;
        if ("row".equals(rowType) && "thead".equals(parent.getType().getName())) {
            AuthorBlock tgroup = tgroupOf(row);
            AuthorBlock tbody = tgroup == null ? null : tgroup.getChildren().stream()
                    .filter(c -> "tbody".equals(c.getType().getName())).findFirst().orElse(null);
            if (tbody == null) {
                return null;
            }
            into = tbody;
            at = 0;
        }
        int width = Math.max(1, row.getChildren().size());
        Transaction tx = doc.begin("Insert Row");
        AuthorBlock newRow = tx.insertBlock(rowType, into, at);
        for (int i = 0; i < width; i++) {
            tx.insertBlock(cellType, newRow, i);
        }
        return new Result(tx.commit(), newRow.getChildren().get(0));
    }

    /** The list a block belongs to (itself, or the nearest {@code ul}/{@code ol} ancestor), else null. */
    public static AuthorBlock listOf(AuthorBlock block) {
        for (AuthorBlock b = block; b != null; b = b.getParent()) {
            String name = b.getType().getName();
            if ("ul".equals(name) || "ol".equals(name)) {
                return b;
            }
        }
        return null;
    }

    /**
     * Turns a bulleted list into a numbered one or back: a new list of the
     * other type takes the old list's place, attributes and items, in one
     * undoable step. Null when the block is in no list or the other type is
     * not allowed there.
     */
    public static Result convertList(AuthorDocument doc, AuthorBlock block) {
        AuthorBlock list = listOf(block);
        AuthorBlock parent = list == null ? null : list.getParent();
        if (parent == null || !doc.getRegistry().isDita()) {
            return null;
        }
        String other = "ul".equals(list.getType().getName()) ? "ol" : "ul";
        BlockType otherType = doc.getRegistry().get(other);
        if (otherType == null || !doc.getRegistry().isValidChild(parent.getType(), otherType)) {
            return null;
        }
        AuthorBlock focus = firstTextBlock(list);
        Transaction tx = doc.begin("ul".equals(other) ? "Bulleted List" : "Numbered List");
        AuthorBlock fresh = tx.insertBlock(other, parent, list.indexInParent());
        for (Map.Entry<String, String> attr : list.getAttributes().entrySet()) {
            tx.setAttribute(fresh, attr.getKey(), attr.getValue());
        }
        List<AuthorBlock> items = new ArrayList<>(list.getChildren());
        for (int i = 0; i < items.size(); i++) {
            tx.moveBlock(items.get(i), fresh, i);
        }
        tx.removeBlock(list);
        return new Result(tx.commit(), focus);
    }

    /**
     * Inserts an imported block (a paste) after {@code anchor}, or inside it
     * when it only fits there; null when it fits neither place.
     */
    public static Result pasteBlock(AuthorDocument doc, AuthorBlock anchor, AuthorBlock pasted) {
        BlockTypeRegistry registry = doc.getRegistry();
        AuthorBlock parent = anchor.getParent();
        String type = pasted.getType().getName();
        boolean raw = pasted.getType().getCategory() == BlockType.Category.RAW;
        AuthorBlock into = null;
        int at = -1;
        if (parent != null && (raw || registry.isValidChild(parent.getType(), pasted.getType()))) {
            at = raw ? anchor.indexInParent() + 1 : registry.insertIndex(parent, type, anchor.indexInParent() + 1);
            into = at < 0 ? null : parent;
        }
        if (into == null && (raw ? !anchor.getType().getAllowedChildren().isEmpty()
                : registry.isValidChild(anchor.getType(), pasted.getType()))) {
            at = raw ? anchor.getChildren().size() : registry.insertIndex(anchor, type, anchor.getChildren().size());
            into = at < 0 ? null : anchor;
        }
        if (into == null) {
            return null;
        }
        Transaction tx = doc.begin("Paste " + pasted.getType().getLabel());
        dropTakenIds(doc, pasted);
        tx.insertBlock(pasted, into, at);
        AuthorBlock focus = firstTextBlock(pasted);
        return new Result(tx.commit(), focus != null ? focus : pasted);
    }

    /**
     * Clears {@code id} attributes in a subtree about to be pasted where the
     * document already uses them, so a copy never duplicates an anchor that
     * conrefs and cross-references point at. An id free in this document is
     * kept, which is what a paste from another topic wants.
     */
    private static void dropTakenIds(AuthorDocument doc, AuthorBlock pasted) {
        java.util.Set<String> taken = new java.util.HashSet<>();
        if (doc.getRoot() != null) {
            for (AuthorBlock b : flatten(doc.getRoot())) {
                String id = b.getAttribute("id");
                if (id != null) {
                    taken.add(id);
                }
            }
        }
        for (AuthorBlock b : flatten(pasted)) {
            String id = b.getAttribute("id");
            if (id != null && !taken.add(id)) {
                b.setAttribute("id", null);
            }
        }
    }

    /** Deletes a body row (never a header row); keeps at least one body row. */
    public static Result deleteRow(AuthorDocument doc, AuthorBlock row) {
        String name = row.getType().getName();
        AuthorBlock parent = row.getParent();
        if (parent == null || !isBodyRow(row)) {
            return null;
        }
        long bodyRows = parent.getChildren().stream().filter(c -> name.equals(c.getType().getName())).count();
        if (bodyRows <= 1) {
            return null;
        }
        AuthorBlock focus = previousTextBlock(doc, row);
        Transaction tx = doc.begin("Delete Row");
        tx.removeBlock(row);
        return new Result(tx.commit(), focus);
    }

    /** Inserts a column after the given cell's column, across every row; a CALS table gains a colspec. */
    public static Result addColumnAfter(AuthorDocument doc, AuthorBlock cell) {
        AuthorBlock row = cell.getParent();
        if (row == null || cellTypeFor(doc, row) == null || hasSpans(row)) {
            return null;
        }
        String cellType = cellTypeFor(doc, row);
        int column = cell.indexInParent();
        Transaction tx = doc.begin("Insert Column");
        AuthorBlock focus = null;
        for (AuthorBlock r : rowsOfTable(row)) {
            int at = Math.min(column + 1, r.getChildren().size());
            AuthorBlock created = tx.insertBlock(cellType, r, at);
            if (r == row) {
                focus = created;
            }
        }
        AuthorBlock tgroup = tgroupOf(row);
        if (tgroup != null) {
            adjustColumns(tx, tgroup, column + 1, +1);
        }
        return new Result(tx.commit(), focus);
    }

    /** Deletes the given cell's column across every row; keeps at least one column. */
    public static Result deleteColumn(AuthorDocument doc, AuthorBlock cell) {
        AuthorBlock row = cell.getParent();
        if (row == null || cellTypeFor(doc, row) == null || row.getChildren().size() <= 1 || hasSpans(row)) {
            return null;
        }
        int column = cell.indexInParent();
        AuthorBlock focus = previousTextBlock(doc, cell);
        Transaction tx = doc.begin("Delete Column");
        for (AuthorBlock r : rowsOfTable(row)) {
            if (column < r.getChildren().size()) {
                tx.removeBlock(r.getChildren().get(column));
            }
        }
        AuthorBlock tgroup = tgroupOf(row);
        if (tgroup != null) {
            adjustColumns(tx, tgroup, column, -1);
        }
        return new Result(tx.commit(), focus);
    }

    /**
     * Whether any cell of the table spans columns or rows; column arithmetic by index is wrong
     * for such a table, so the column operations refuse it.
     */
    public static boolean hasSpans(AuthorBlock row) {
        for (AuthorBlock r : rowsOfTable(row)) {
            for (AuthorBlock cell : r.getChildren()) {
                if (cell.getAttribute("namest") != null || cell.getAttribute("nameend") != null
                        || cell.getAttribute("morerows") != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Keeps {@code tgroup/@cols} and its colspecs in step with a column inserted at or removed from {@code index}. */
    private static void adjustColumns(Transaction tx, AuthorBlock tgroup, int index, int delta) {
        List<AuthorBlock> colspecs = new ArrayList<>();
        for (AuthorBlock c : tgroup.getChildren()) {
            if ("colspec".equals(c.getType().getName())) {
                colspecs.add(c);
            }
        }
        int cols;
        try {
            cols = Integer.parseInt(tgroup.getAttribute("cols"));
        } catch (RuntimeException e) {
            cols = Math.max(colspecs.size(), 1);
        }
        tx.setAttribute(tgroup, "cols", Integer.toString(Math.max(1, cols + delta)));
        if (delta > 0 && colspecs.size() == cols) {
            int at = index < colspecs.size() ? colspecs.get(index).indexInParent() : (colspecs.isEmpty()
                    ? 0 : colspecs.get(colspecs.size() - 1).indexInParent() + 1);
            AuthorBlock spec = tx.insertBlock("colspec", tgroup, at);
            tx.setAttribute(spec, "colname", freeColumnName(colspecs));
        } else if (delta < 0 && index < colspecs.size() && colspecs.size() == cols) {
            tx.removeBlock(colspecs.get(index));
        }
    }

    private static boolean isTableRow(AuthorBlock block) {
        String name = block.getType().getName();
        return "strow".equals(name) || "sthead".equals(name) || "row".equals(name);
    }

    // ------------------------------------------------------------------

    public static List<AuthorBlock> flatten(AuthorBlock root) {
        List<AuthorBlock> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    private static void collect(AuthorBlock block, List<AuthorBlock> out) {
        out.add(block);
        for (AuthorBlock child : block.getChildren()) {
            collect(child, out);
        }
    }
}
