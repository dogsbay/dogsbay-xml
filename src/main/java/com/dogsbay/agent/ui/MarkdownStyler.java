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
package com.dogsbay.agent.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import com.vladsch.flexmark.ast.AutoLink;
import com.vladsch.flexmark.ast.BlockQuote;
import com.vladsch.flexmark.ast.BulletList;
import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.Emphasis;
import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.HardLineBreak;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.HtmlBlockBase;
import com.vladsch.flexmark.ast.HtmlEntity;
import com.vladsch.flexmark.ast.HtmlInline;
import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.ImageRef;
import com.vladsch.flexmark.ast.IndentedCodeBlock;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.LinkRef;
import com.vladsch.flexmark.ast.ListBlock;
import com.vladsch.flexmark.ast.ListItem;
import com.vladsch.flexmark.ast.MailLink;
import com.vladsch.flexmark.ast.OrderedList;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.SoftLineBreak;
import com.vladsch.flexmark.ast.StrongEmphasis;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.ast.ThematicBreak;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.Strikethrough;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListItem;
import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.ext.tables.TableRow;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Block;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.sequence.Escaping;

/**
 * Markdown as styled runs for a {@code StyledDocument} transcript: headings,
 * emphasis, code, lists, quotes, links, tables. No HTML, no browser widget,
 * so it fits the streaming chat panels, which show a message raw while it
 * arrives and swap in these runs when it ends.
 */
public final class MarkdownStyler {

    /** One stretch of text with one style. */
    public record Run(String text, AttributeSet style) {}

    /** The colours a transcript renders with. */
    public record Palette(Color foreground, Color muted, Color codeBackground, Color link) {}

    private static final Parser PARSER = Parser.builder(new MutableDataSet().set(Parser.EXTENSIONS,
            Arrays.asList(TablesExtension.create(), StrikethroughExtension.create(), TaskListExtension.create(),
                    AutolinkExtension.create())))
            .build();

    private MarkdownStyler() {
    }

    private static final java.util.regex.Pattern MARKDOWN = java.util.regex.Pattern.compile(
            "(?m)\\*\\*\\S|`|~~\\S|^\\s*#{1,6}\\s|^\\s*(?:[-*+]|\\d+[.)])\\s|^\\s*>\\s|\\]\\(|^\\s*\\|.*\\|\\s*$|^\\s*---+\\s*$");

    /**
     * Whether the text has Markdown worth styling: emphasis, code, a heading,
     * list, quote, link, table or rule. Plain prose, and replies that merely
     * contain brackets, underscores or angle brackets (XML, file names), are
     * left exactly as they streamed.
     */
    public static boolean looksLikeMarkdown(String text) {
        return text != null && MARKDOWN.matcher(text).find();
    }

    public static List<Run> runs(String markdown, Palette palette) {
        Document doc = PARSER.parse(markdown == null ? "" : markdown);
        Walker w = new Walker(palette);
        w.blocks(doc, 0);
        w.trimTrailingNewlines();
        return w.runs;
    }

    /**
     * Replace the raw text of a finished message, {@code doc[start, end)},
     * with its rendering when it has Markdown worth styling. Only that region
     * is touched: notes appended after it stay where they are.
     */
    /**
     * A family whose characters are all the same width, for code and tables.
     *
     * <p>Java's logical {@code Monospaced} is not one, at least not everywhere:
     * on this machine it resolves to a face measuring {@code i} at 7 pixels and
     * {@code M} at 8, so a table padded to a character count still had its
     * columns land in different places on every row. Each candidate is
     * measured rather than trusted, and an uninstalled family is silently
     * substituted, so the check also confirms the family that came back is the
     * one asked for.
     */
    static final String FIXED_WIDTH_FAMILY = fixedWidthFamily();

    /**
     * Resolve the fixed-width family now, off whatever thread calls this.
     *
     * <p>The probe measures several fonts and takes long enough to be seen; the
     * first thing to touch this class is otherwise a reply being styled on the
     * event thread.
     */
    public static void warmUp() {
        assert FIXED_WIDTH_FAMILY != null;
    }

    private static String fixedWidthFamily() {
        for (String candidate : List.of("Liberation Mono", "DejaVu Sans Mono", "Noto Sans Mono",
                "Menlo", "Consolas", "Courier New", "Monospaced")) {
            if (isFixedWidth(candidate)) {
                return candidate;
            }
        }
        return "Monospaced";   // nothing measured true; the old behaviour
    }

    /** Whether every character this styler emits has the same advance in {@code family}. */
    static boolean isFixedWidth(String family) {
        try {
            java.awt.Font font = new java.awt.Font(family, java.awt.Font.PLAIN, 13);
            if (!"Monospaced".equals(family) && !font.getFamily().equalsIgnoreCase(family)) {
                return false;   // not installed: AWT handed back a substitute
            }
            java.awt.Graphics2D g = new java.awt.image.BufferedImage(1, 1,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();
            try {
                java.awt.FontMetrics metrics = g.getFontMetrics(font);
                int width = metrics.charWidth('M');
                for (char c : "iM │─┼x0.".toCharArray()) {
                    if (metrics.charWidth(c) != width) {
                        return false;
                    }
                }
                return true;
            } finally {
                g.dispose();
            }
        } catch (Exception headlessOrNoFonts) {
            return false;
        }
    }

    public static void restyle(javax.swing.text.StyledDocument doc, int start, int end, String raw,
            Palette palette, int baseFontSize) {
        if (start < 0 || end > doc.getLength() || end <= start || !looksLikeMarkdown(raw)) {
            return;
        }
        try {
            doc.remove(start, end - start);
            int at = start;
            for (Run r : sized(runs(raw, palette), baseFontSize)) {
                doc.insertString(at, r.text(), r.style());
                at += r.text().length();
            }
        } catch (javax.swing.text.BadLocationException ignored) {
            // the region was checked against the document above
        }
    }

    /** Plain text of the runs, for tests and for the transcript's text export. */
    public static String text(List<Run> runs) {
        StringBuilder sb = new StringBuilder();
        runs.forEach(r -> sb.append(r.text()));
        return sb.toString();
    }

    private static final class Walker {
        final List<Run> runs = new ArrayList<>();
        final Palette p;
        boolean bold;
        boolean italic;
        boolean strike;
        boolean code;
        boolean linking;
        /** Inside a table: every glyph the same width, so padded columns line up. */
        boolean tableLayout;
        Color colour;
        int sizeDelta;

        Walker(Palette p) {
            this.p = p;
            this.colour = p.foreground();
        }

        void blocks(Node parent, int depth) {
            for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) {
                block(n, depth);
            }
        }

        void block(Node n, int depth) {
            String indent = "  ".repeat(depth);
            switch (n) {
                case Heading h -> {
                    withSize(h.getLevel() <= 1 ? 4 : h.getLevel() == 2 ? 2 : 1, () -> withBold(() -> {
                        emit(indent);
                        inlines(h);
                    }));
                    emit("\n\n");
                }
                case Paragraph para -> {
                    emit(indent);
                    inlines(para);
                    emit(n.getParent() instanceof ListItem ? "\n" : "\n\n");
                }
                case FencedCodeBlock f -> codeBlock(indent, f.getContentChars().toString());
                case IndentedCodeBlock c -> codeBlock(indent, c.getContentChars().toString());
                case BulletList list -> list(list, depth, false);
                case OrderedList list -> list(list, depth, true);
                case BlockQuote q -> {
                    Walker inner = new Walker(p);
                    inner.colour = p.muted();
                    inner.blocks(q, 0);
                    inner.trimTrailingNewlines();
                    runs.addAll(gutter(inner.runs, indent + "│ ", styled(p.muted(), false, false, false, false, 0)));
                    emit("\n\n");
                }
                case ThematicBreak t -> emit(indent + "────────\n\n", styled(p.muted(), false, false, false, false, 0));
                case TableBlock t -> table(t, indent);
                case HtmlBlockBase h -> emit(indent + h.getChars().toString().strip() + "\n\n");
                case Block b -> {
                    // an unknown block: its inline content, then its children
                    inlines(b);
                    blocks(b, depth);
                }
                default -> inlines(n);
            }
        }

        void list(ListBlock list, int depth, boolean ordered) {
            int number = list instanceof OrderedList o ? o.getStartNumber() : 1;
            for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
                String marker;
                if (item instanceof TaskListItem t) {
                    marker = t.isItemDoneMarker() ? "☑ " : "☐ ";
                } else {
                    marker = ordered ? number + ". " : "• ";
                }
                number++;
                if (item.getFirstChild() instanceof Paragraph) {
                    emit("  ".repeat(depth) + marker);
                } else {
                    emit("  ".repeat(depth) + marker.stripTrailing() + "\n");   // nested list or code block under the marker
                }
                boolean first = true;
                for (Node c = item.getFirstChild(); c != null; c = c.getNext()) {
                    if (c instanceof Paragraph para) {
                        if (!first) {
                            emit("  ".repeat(depth + 1));
                        }
                        inlines(para);
                        emit("\n");
                    } else if (c instanceof ListBlock inner) {
                        list(inner, depth + 1, inner instanceof OrderedList);
                    } else {
                        block(c, depth + 1);
                    }
                    first = false;
                }
            }
            if (depth == 0) {
                emit("\n");
            }
        }

        /** {@code prefix} at the start of every line of {@code inner}. */
        static List<Run> gutter(List<Run> inner, String prefix, AttributeSet prefixStyle) {
            List<Run> out = new ArrayList<>();
            boolean lineStart = true;
            for (Run r : inner) {
                String t = r.text();
                int from = 0;
                while (from < t.length()) {
                    if (lineStart) {
                        out.add(new Run(prefix, prefixStyle));
                        lineStart = false;
                    }
                    int nl = t.indexOf('\n', from);
                    if (nl < 0) {
                        out.add(new Run(t.substring(from), r.style()));
                        break;
                    }
                    out.add(new Run(t.substring(from, nl + 1), r.style()));
                    lineStart = true;
                    from = nl + 1;
                }
            }
            return out;
        }

        void codeBlock(String indent, String content) {
            String body = content.stripTrailing();
            for (String line : body.split("\n", -1)) {
                emit(indent + line, styled(p.foreground(), false, false, false, true, 0));
                emit("\n");
            }
            emit("\n");
        }

        /**
         * A table, with its columns lined up.
         *
         * <p>Cells used to be joined with a separator and left to fall where the
         * proportional font put them, which reads as rows of text rather than as
         * a table. Padding to a common width only aligns if every glyph is the
         * same width, so the whole block is set in the monospaced face — the same
         * bargain a terminal makes.
         */
        /** Render {@code body} in {@code colour}, restoring what was in force. */
        void withColour(Color colour, Runnable body) {
            Color previous = this.colour;
            this.colour = colour;
            try {
                body.run();
            } finally {
                this.colour = previous;
            }
        }

        void table(TableBlock t, String indent) {
            int[] widths = columnWidths(t);
            boolean wasTable = tableLayout;
            tableLayout = true;
            try {
                for (Node section = t.getFirstChild(); section != null; section = section.getNext()) {
                    if (section.getClass().getSimpleName().equals("TableSeparator")) {
                        continue;
                    }
                    boolean header = section.getClass().getSimpleName().equals("TableHead");
                    for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                        if (row instanceof TableRow) {
                            tableRow(row, widths, header, indent);
                        }
                    }
                    if (header) {
                        rule(widths, indent);
                    }
                }
            } finally {
                tableLayout = wasTable;
            }
            emit("\n");
        }

        private void tableRow(Node row, int[] widths, boolean header, String indent) {
            emit(indent);
            int column = 0;
            for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                if (!(cell instanceof TableCell)) {
                    continue;
                }
                if (column > 0) {
                    emit(" │ ", styled(p.muted(), false, false, false, false, 0));
                }
                Node theCell = cell;
                if (header) {
                    // Colour, not weight. The bold monospaced face does not
                    // share the plain one's advances — on this machine bold 'M'
                    // is 11px against plain's 8 — so a bold header drifts right
                    // of the columns it is meant to label.
                    withColour(p.link(), () -> inlines(theCell));
                } else {
                    inlines(cell);
                }
                // Pad to the column, except after the last cell where trailing
                // spaces would only widen the selection.
                int pad = widths[Math.min(column, widths.length - 1)] - plainText(cell).length();
                if (pad > 0 && column < widths.length - 1) {
                    emit(" ".repeat(pad));
                }
                column++;
            }
            emit("\n");
        }

        /** The line under the header, drawn to the measured widths. */
        private void rule(int[] widths, String indent) {
            StringBuilder line = new StringBuilder(indent);
            for (int i = 0; i < widths.length; i++) {
                if (i > 0) {
                    line.append("─┼─");
                }
                line.append("─".repeat(widths[i]));
            }
            emit(line.toString(), styled(p.muted(), false, false, false, false, 0));
            emit("\n");
        }

        /**
         * The widest cell in each column.
         *
         * <p>Not capped: a cap bounds the padding but not the cell, so a cell
         * past the cap ran over its column and pushed that row's separators
         * right of every other row's — the misalignment this exists to prevent.
         */
        private int[] columnWidths(TableBlock t) {
            List<Integer> widths = new ArrayList<>();
            for (Node section = t.getFirstChild(); section != null; section = section.getNext()) {
                if (section.getClass().getSimpleName().equals("TableSeparator")) {
                    continue;   // the |---| row is markup, not a cell to fit
                }
                for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                    if (!(row instanceof TableRow)) {
                        continue;
                    }
                    int column = 0;
                    for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                        if (!(cell instanceof TableCell)) {
                            continue;
                        }
                        int width = plainText(cell).length();
                        if (column == widths.size()) {
                            widths.add(width);
                        } else {
                            widths.set(column, Math.max(widths.get(column), width));
                        }
                        column++;
                    }
                }
            }
            int[] out = new int[widths.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = widths.get(i);
            }
            return out;
        }

        /** A cell's text without its markup, which is what decides the column width. */
        private String plainText(Node node) {
            StringBuilder text = new StringBuilder();
            for (Node n = node.getFirstChild(); n != null; n = n.getNext()) {
                if (n instanceof Code c) {
                    text.append(c.getText());
                } else if (n instanceof Link l) {
                    // What inline() emits, not what the source says: a link is
                    // rendered as "label (url)", and measuring only the label
                    // left the column short by the whole URL.
                    String label = l.getText().toString();
                    String url = l.getUrl().toString();
                    text.append(label.isEmpty() ? url : label);
                    if (!label.isEmpty() && !label.equals(url)) {
                        text.append(" (").append(url).append(")");
                    }
                } else if (n instanceof Image i) {
                    String alt = i.getText().toString();
                    text.append(alt.isEmpty() ? "image" : alt).append(" (").append(i.getUrl()).append(")");
                } else if (n.getFirstChild() != null) {
                    text.append(plainText(n));
                } else {
                    text.append(n.getChars());
                }
            }
            // Stripped: a cell's source keeps the spaces around the pipes, but
            // the rendered cell does not, and padding to the unstripped width
            // indents every column by however many spaces the author typed.
            return text.toString().strip();
        }

        void inlines(Node parent) {
            for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) {
                inline(n);
            }
        }

        void inline(Node n) {
            switch (n) {
                case Text t -> emit(Escaping.unescapeString(t.getChars()));
                case HtmlEntity e -> emit(Escaping.unescapeString(e.getChars()));
                case SoftLineBreak s -> emit("\n");   // a transcript keeps the agent's line layout
                case HardLineBreak h -> emit("\n");
                case Code c -> emit(c.getText().toString(), styled(colour, bold, italic, strike, true, sizeDelta));
                case Emphasis e -> {
                    boolean was = italic;
                    italic = true;
                    inlines(e);
                    italic = was;
                }
                case StrongEmphasis e -> withBold(() -> inlines(e));
                case Strikethrough s -> {
                    boolean was = strike;
                    strike = true;
                    inlines(s);
                    strike = was;
                }
                case Link l -> {
                    String label = l.getText().toString();
                    String url = l.getUrl().toString();
                    if (label.isEmpty()) {
                        emitLink(url);
                    } else {
                        boolean was = linking;
                        linking = true;
                        inlines(l);   // the label's own markup, bold or code, still applies
                        linking = was;
                        if (!label.equals(url)) {
                            emit(" (" + url + ")", styled(p.muted(), false, false, false, false, sizeDelta));
                        }
                    }
                }
                case Image i -> {
                    String alt = i.getText().toString();
                    emit((alt.isEmpty() ? "image" : alt) + " (" + i.getUrl() + ")",
                            styled(p.muted(), false, true, false, false, sizeDelta));
                }
                case LinkRef r -> emit(r.getChars().toString());     // "[1]" with no definition: as written
                case ImageRef r -> emit(r.getChars().toString());
                case AutoLink a -> emitLink(a.getText().toString());
                case MailLink m -> emitLink(m.getText().toString());
                case HtmlInline h -> emit(h.getChars().toString());
                default -> {
                    if (n.hasChildren()) {
                        inlines(n);
                    } else {
                        emit(n.getChars().toString());
                    }
                }
            }
        }

        void emitLink(String text) {
            boolean was = linking;
            linking = true;
            emit(text);
            linking = was;
        }

        void withBold(Runnable r) {
            boolean was = bold;
            bold = true;
            r.run();
            bold = was;
        }

        void withSize(int delta, Runnable r) {
            int was = sizeDelta;
            sizeDelta = delta;
            r.run();
            sizeDelta = was;
        }

        void emit(String text) {
            SimpleAttributeSet a = styled(linking ? p.link() : colour, bold, italic, strike, code, sizeDelta);
            if (linking) {
                StyleConstants.setUnderline(a, true);
            }
            emit(text, a);
        }

        void emit(String text, AttributeSet style) {
            if (text.isEmpty()) {
                return;
            }
            runs.add(new Run(text, style));
        }

        SimpleAttributeSet styled(Color fg, boolean b, boolean i, boolean s, boolean mono, int size) {
            SimpleAttributeSet a = new SimpleAttributeSet();
            StyleConstants.setForeground(a, fg);
            if (b) {
                StyleConstants.setBold(a, true);
            }
            if (i) {
                StyleConstants.setItalic(a, true);
            }
            if (s) {
                StyleConstants.setStrikeThrough(a, true);
            }
            if (mono || tableLayout) {
                StyleConstants.setFontFamily(a, FIXED_WIDTH_FAMILY);
            }
            if (mono && p.codeBackground() != null) {
                StyleConstants.setBackground(a, p.codeBackground());
            }
            if (size != 0) {
                a.addAttribute(SIZE_DELTA, size);
            }
            return a;
        }

        void trimTrailingNewlines() {
            while (!runs.isEmpty()) {
                Run last = runs.get(runs.size() - 1);
                String t = last.text().replaceAll("\n+$", "");
                if (t.equals(last.text())) {
                    return;
                }
                runs.remove(runs.size() - 1);
                if (!t.isEmpty()) {
                    runs.add(new Run(t, last.style()));
                    return;
                }
            }
        }
    }

    /** Attribute key: points to add to the transcript's base font size (headings). */
    public static final Object SIZE_DELTA = new Object() {
        @Override
        public String toString() {
            return "markdown-size-delta";
        }
    };

    /** The runs with {@link #SIZE_DELTA} resolved against {@code baseSize}, ready to insert. */
    public static List<Run> sized(List<Run> runs, int baseSize) {
        List<Run> out = new ArrayList<>(runs.size());
        for (Run r : runs) {
            Object d = r.style().getAttribute(SIZE_DELTA);
            if (d instanceof Integer delta) {
                SimpleAttributeSet a = new SimpleAttributeSet(r.style());
                a.removeAttribute(SIZE_DELTA);
                StyleConstants.setFontSize(a, baseSize + delta);
                out.add(new Run(r.text(), a));
            } else {
                out.add(r);
            }
        }
        return out;
    }
}
