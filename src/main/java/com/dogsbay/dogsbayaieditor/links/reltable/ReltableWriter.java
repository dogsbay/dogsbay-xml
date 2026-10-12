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

package com.dogsbay.dogsbayaieditor.links.reltable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formatting-preserving textual edits to the relationship tables in a map — splices
 * of the map's exact source (never a DOM re-serialize), the same discipline as
 * {@link com.dogsbay.dogsbayaieditor.links.map.MapEditor}. Each op returns the new
 * map text; the caller writes it. Tables/rows/cells are addressed by 0-based index.
 */
public final class ReltableWriter {

    private ReltableWriter() {}

    private record Splice(int at, int delLen, String insert) {}

    /** Append an empty {@code <reltable>} with the given column types before the map's
     *  closing tag. {@code columnTypes} may be empty (no relheader). */
    public static String createTable(ReltableModel model, List<String> columnTypes) {
        String text = model.text();
        // The root element's own close tag — found from the parsed content-end offset, so
        // a trailing comment containing "</map>" (or a "</map-extension>") can't fool us.
        int from = Math.max(0, Math.min(model.rootContentEnd(), text.length()));
        int close = text.indexOf("</" + model.rootName(), from);
        if (close < 0) {
            close = text.lastIndexOf("</" + model.rootName());
        }
        if (close < 0) {
            throw new IllegalStateException("no </" + model.rootName() + "> close tag");
        }
        int lineStart = lineStart(text, close);
        boolean ownLine = text.substring(lineStart, close).isBlank();
        int at = ownLine ? lineStart : close;        // insert before the close tag
        String rootIndent = ownLine ? lineIndent(text, lineStart) : "";
        String ri = rootIndent + "  ";                // reltable is a child of the map
        String inner = ri + "  ";
        StringBuilder sb = new StringBuilder();
        if (!ownLine) {
            sb.append('\n');                          // push the close tag to a fresh line
        }
        sb.append(ri).append("<reltable>");
        if (columnTypes != null && !columnTypes.isEmpty()) {
            sb.append("\n").append(inner).append("<relheader>");
            for (String type : columnTypes) {
                sb.append("\n").append(inner).append("  <relcolspec type=\"")
                  .append(escapeAttr(type)).append("\"/>");
            }
            sb.append("\n").append(inner).append("</relheader>");
        }
        sb.append("\n").append(ri).append("</reltable>\n");
        if (!ownLine) {
            sb.append(rootIndent);                    // re-indent for the close tag's new line
        }
        return applySplices(text, new Splice(at, 0, sb.toString()));
    }

    /** Append a row (one empty {@code <relcell>} per column) to table {@code t}. */
    public static String addRow(ReltableModel model, int t) {
        Reltable table = table(model, t);
        String text = model.text();
        int cols = Math.max(1, columnCount(table));
        if (!table.rows().isEmpty()) {
            Reltable.Row last = table.rows().get(table.rows().size() - 1);
            String indent = lineIndent(text,
                    lineStart(text, last.element().getElementStartPosition()));
            return applySplices(text, new Splice(last.element().getElementEndPosition(),
                    0, "\n" + buildRowText(indent, cols)));
        }
        // Empty (possibly self-closing) table: insert the row inside it robustly.
        String indent = lineIndent(text,
                lineStart(text, table.element().getElementStartPosition())) + "  ";
        return applySplices(text,
                insertIntoElement(text, table.element(), buildRowText(indent, cols).strip()));
    }

    private static String buildRowText(String indent, int cols) {
        StringBuilder row = new StringBuilder(indent).append("<relrow>");
        for (int i = 0; i < cols; i++) {
            row.append("\n").append(indent).append("  <relcell/>");
        }
        return row.append("\n").append(indent).append("</relrow>").toString();
    }

    /** Remove row {@code r} of table {@code t}. */
    public static String removeRow(ReltableModel model, int t, int r) {
        Reltable.Row row = row(model, t, r);
        return applySplices(model.text(), removalSplice(model.text(),
                row.element().getElementStartPosition(), row.element().getElementEndPosition()));
    }

    /** Add a {@code <topicref>} (by href or keyref) to cell (r,c) of table t. */
    public static String addTarget(ReltableModel model, int t, int r, int c,
            String href, String keyref, String navtitle) {
        Reltable.Cell cell = cell(model, t, r, c);
        String text = model.text();
        StringBuilder tr = new StringBuilder("<topicref");
        if (href != null && !href.isBlank()) {
            tr.append(" href=\"").append(escapeAttr(href)).append('"');
        }
        if (keyref != null && !keyref.isBlank()) {
            tr.append(" keyref=\"").append(escapeAttr(keyref)).append('"');
        }
        if (navtitle != null && !navtitle.isBlank()) {
            tr.append(" navtitle=\"").append(escapeAttr(navtitle)).append('"');
        }
        tr.append("/>");
        return applySplices(text, insertIntoElement(text, cell.element(), tr.toString()));
    }

    /** Remove target {@code index} from cell (r,c) of table t. */
    public static String removeTarget(ReltableModel model, int t, int r, int c, int index) {
        Reltable.Cell cell = cell(model, t, r, c);
        if (index < 0 || index >= cell.targets().size()) {
            throw new IllegalArgumentException("no target #" + index + " in cell");
        }
        var target = cell.targets().get(index);
        return applySplices(model.text(), removalSplice(model.text(),
                target.element().getElementStartPosition(),
                target.element().getElementEndPosition()));
    }

    /** Set ({@code value} non-empty) or remove an attribute on the table / row / cell.
     *  Scope: {@code row>=0 && col>=0} ⇒ cell; {@code row>=0} (no col) ⇒ row; neither ⇒
     *  the table. A column without a row is ambiguous and rejected. */
    public static String setAttr(ReltableModel model, int t, int row, int col,
            String name, String value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("attribute name is required");
        }
        if (col >= 0 && row < 0) {
            throw new IllegalArgumentException(
                    "set-attr with a column needs a row (cell scope); omit the column for table scope");
        }
        Reltable table = table(model, t);
        com.dogsbay.xml.XElement target;
        if (col >= 0 && row >= 0) {
            target = cell(model, t, row, col).element();
        } else if (row >= 0) {
            target = row(model, t, row).element();
        } else {
            target = table.element();
        }
        String text = model.text();
        int[] tag = startTagRange(text, target.getElementStartPosition());
        String newTag = setAttrInTag(text.substring(tag[0], tag[1]), name, value);
        return text.substring(0, tag[0]) + newTag + text.substring(tag[1]);
    }

    // ── lookups ────────────────────────────────────────────────────────────────

    private static Reltable table(ReltableModel m, int t) {
        if (t < 0 || t >= m.reltables().size()) {
            throw new IllegalArgumentException("no reltable #" + t);
        }
        return m.reltables().get(t);
    }

    private static Reltable.Row row(ReltableModel m, int t, int r) {
        Reltable table = table(m, t);
        if (r < 0 || r >= table.rows().size()) {
            throw new IllegalArgumentException("no row #" + r);
        }
        return table.rows().get(r);
    }

    private static Reltable.Cell cell(ReltableModel m, int t, int r, int c) {
        Reltable.Row row = row(m, t, r);
        if (c < 0 || c >= row.cells().size()) {
            throw new IllegalArgumentException("no cell at column " + c);
        }
        return row.cells().get(c);
    }

    private static int columnCount(Reltable table) {
        int cols = table.columns().size();
        for (Reltable.Row row : table.rows()) {
            cols = Math.max(cols, row.cells().size());
        }
        return cols;
    }

    // ── splice plumbing (mirrors MapEditor) ──────────────────────────────────────

    private static String applySplices(String text, Splice... splices) {
        List<Splice> list = new ArrayList<>(List.of(splices));
        list.sort((a, b) -> Integer.compare(b.at, a.at));
        StringBuilder sb = new StringBuilder(text);
        for (Splice s : list) {
            sb.replace(s.at, s.at + s.delLen, s.insert);
        }
        return sb.toString();
    }

    /** Remove [start,end) plus a leading indentation+newline so no blank line remains. */
    private static Splice removalSplice(String text, int start, int end) {
        int ls = lineStart(text, start);
        if (text.substring(ls, start).isBlank()) {
            int from = ls > 0 ? ls - 1 : ls;
            return new Splice(from, end - from, "");
        }
        return new Splice(start, end - start, "");
    }

    /** Insert {@code child} just before the element's closing tag (expanding a
     *  self-closing element to open/close form). */
    private static Splice insertIntoElement(String text, com.dogsbay.xml.XElement el,
            String child) {
        int start = el.getElementStartPosition();
        int end = el.getElementEndPosition();
        int[] tag = startTagRange(text, start);
        String tagStr = text.substring(tag[0], tag[1]);
        String parentIndent = lineIndent(text, lineStart(text, start));
        String childIndent = parentIndent + "  ";
        if (tagStr.endsWith("/>")) {
            String open = tagStr.substring(0, tagStr.length() - 2).stripTrailing() + ">";
            String replacement = open + "\n" + childIndent + child + "\n" + parentIndent
                    + "</" + el.getName() + ">";
            return new Splice(tag[0], tag[1] - tag[0], replacement);
        }
        int closeTagStart = text.lastIndexOf("</", end);
        int at = lineStart(text, closeTagStart);
        return new Splice(at, 0, childIndent + child + "\n");
    }

    private static int[] startTagRange(String text, int start) {
        boolean sq = false;
        boolean dq = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"' && !sq) {
                dq = !dq;
            } else if (ch == '\'' && !dq) {
                sq = !sq;
            } else if (ch == '>' && !sq && !dq) {
                return new int[] {start, i + 1};
            }
        }
        throw new IllegalStateException("unterminated start tag");
    }

    private static String setAttrInTag(String tag, String name, String value) {
        Pattern p = Pattern.compile(
                "\\s+" + Pattern.quote(name) + "\\s*=\\s*(\"[^\"]*\"|'[^']*')");
        Matcher m = p.matcher(tag);
        boolean present = m.find();
        if (value == null || value.isBlank()) {
            return present ? m.replaceFirst("") : tag;
        }
        String attr = name + "=\"" + escapeAttr(value.trim()) + "\"";
        if (present) {
            return tag.substring(0, m.start()) + " " + attr + tag.substring(m.end());
        }
        boolean selfClose = tag.endsWith("/>");
        int close = selfClose ? tag.length() - 2 : tag.length() - 1;
        return tag.substring(0, close).stripTrailing() + " " + attr + (selfClose ? "/>" : ">");
    }

    private static String escapeAttr(String v) {
        return v.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static int lineStart(String text, int offset) {
        int nl = text.lastIndexOf('\n', offset - 1);
        return nl < 0 ? 0 : nl + 1;
    }

    private static String lineIndent(String text, int lineStart) {
        StringBuilder sb = new StringBuilder();
        for (int i = lineStart; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ' || ch == '\t') {
                sb.append(ch);
            } else {
                break;
            }
        }
        return sb.toString();
    }
}
