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

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.util.List;
import javax.swing.text.StyleConstants;
import org.junit.jupiter.api.Test;

class MarkdownStylerTest {

    private static final MarkdownStyler.Palette P =
            new MarkdownStyler.Palette(Color.BLACK, Color.GRAY, Color.LIGHT_GRAY, Color.BLUE);

    private static List<MarkdownStyler.Run> runs(String md) {
        return MarkdownStyler.runs(md, P);
    }

    private static MarkdownStyler.Run runWith(List<MarkdownStyler.Run> runs, String text) {
        return runs.stream().filter(r -> r.text().equals(text)).findFirst().orElseThrow();
    }

    @Test
    void emphasisCodeAndHeadingsCarryTheirStyles() {
        var rs = runs("# Title\n\nSome **bold** and *italic* and `code` here.");
        assertThat(MarkdownStyler.text(rs)).isEqualTo("Title\n\nSome bold and italic and code here.");
        assertThat(StyleConstants.isBold(runWith(rs, "Title").style())).isTrue();
        assertThat(runWith(rs, "Title").style().getAttribute(MarkdownStyler.SIZE_DELTA)).isEqualTo(4);
        assertThat(StyleConstants.isBold(runWith(rs, "bold").style())).isTrue();
        assertThat(StyleConstants.isItalic(runWith(rs, "italic").style())).isTrue();
        assertThat(StyleConstants.getFontFamily(runWith(rs, "code").style()))
                .isEqualTo(MarkdownStyler.FIXED_WIDTH_FAMILY);
        assertThat(StyleConstants.isBold(runWith(rs, "Some ").style())).isFalse();
    }

    @Test
    void listsQuotesAndCodeBlocksReadAsText() {
        String md = """
            Steps:

            - **Tip:** first
            - second
              - nested
            1. one
            2. two

            > a quote

            ```bash
            make test
            ```
            """;
        String t = MarkdownStyler.text(runs(md));
        assertThat(t).isEqualTo("""
            Steps:

            • Tip: first
            • second
              • nested

            1. one
            2. two

            │ a quote

            make test""");
        assertThat(StyleConstants.getFontFamily(runWith(runs(md), "make test").style()))
                .isEqualTo(MarkdownStyler.FIXED_WIDTH_FAMILY);
    }

    @Test
    void linksShowTheirTargetAndTablesTheirCells() {
        var rs = runs("See [the docs](https://example.com/d) and <https://x.y>.\n\n| a | b |\n|---|---|\n| 1 | 2 |");
        String t = MarkdownStyler.text(rs);
        assertThat(t).startsWith("See the docs (https://example.com/d) and https://x.y.");
        assertThat(t).contains("a │ b\n──┼──\n1 │ 2");
        assertThat(StyleConstants.isUnderline(runWith(rs, "the docs").style())).isTrue();
        // The header is coloured rather than bold: see tableColumnsLineUp.
        assertThat(StyleConstants.isBold(runWith(rs, "a").style())).isFalse();
    }


    /**
     * A table read as rows of text before this: cells joined by a separator and
     * left wherever the proportional font put them, so nothing lined up.
     */
    @Test
    void tableColumnsLineUp() {
        var rs = runs("""
            | Deliverable | Input | Output |
            |---|---|---|
            | full | audacity-guide.ditamap | HTML5 |
            | podcaster-linux | podcaster-guide.ditamap | PDF |
            """);
        String t = MarkdownStyler.text(rs);

        // Every column starts at the same offset on every line.
        var lines = t.strip().lines().toList();
        assertThat(lines).hasSize(4);
        int bar = lines.get(0).indexOf('│');
        assertThat(bar).isPositive();
        assertThat(lines).allSatisfy(line ->
            assertThat(line.indexOf(line.contains("│") ? '│' : '┼')).isEqualTo(bar));

        // Padding only aligns in a fixed-width face, so the whole table is in one.
        assertThat(StyleConstants.getFontFamily(runWith(rs, "full").style()))
            .isEqualTo(MarkdownStyler.FIXED_WIDTH_FAMILY);
        // …and the header is not bold: the bold monospaced face has different
        // advances from the plain one, so a bold header would sit right of the
        // column it labels. It is coloured instead.
        assertThat(StyleConstants.isBold(runWith(rs, "Deliverable").style())).isFalse();
        assertThat(StyleConstants.getForeground(runWith(rs, "Deliverable").style()))
            .isNotEqualTo(StyleConstants.getForeground(runWith(rs, "full").style()));
        // …without the background that marks a code span.
        assertThat(StyleConstants.getBackground(runWith(rs, "full").style()))
            .isNotEqualTo(new java.awt.Color(0xEEEEEE));
    }

    /**
     * A column is as wide as its widest cell. Capping the padding but not the
     * cell was worse than not capping at all: the long cell ran past its column
     * and pushed that row's separators right of every other row's.
     */
    @Test
    void aLongCellWidensItsColumnRatherThanBreakingTheRow() {
        var rs = runs("| a | b |\n|---|---|\n| " + "x".repeat(120) + " | c |");

        var lines = MarkdownStyler.text(rs).strip().lines().toList();
        int bar = lines.get(0).indexOf('│');
        assertThat(lines).allSatisfy(line ->
            assertThat(line.indexOf(line.contains("│") ? '│' : '┼')).isEqualTo(bar));
    }

    @Test
    void aLinkIsMeasuredAsItIsRendered() {
        // A link renders as "label (url)"; measuring only the label left the
        // column short by the whole URL and shifted everything after it.
        var rs = runs("| Name | Note |\n|---|---|\n| [docs](https://example.com/g) | link |\n| plain | second |");

        var lines = MarkdownStyler.text(rs).strip().lines().toList();
        int bar = lines.get(0).indexOf('│');
        assertThat(lines).allSatisfy(line ->
            assertThat(line.indexOf(line.contains("│") ? '│' : '┼')).isEqualTo(bar));
    }

    /**
     * Padding cells to a character count only aligns them if every character is
     * the same width. Java's logical "Monospaced" is not: on the machine that
     * prompted this it measures i at 7 pixels and M at 8, so the columns landed
     * somewhere different on every row.
     */
    @Test
    void theFixedWidthFamilyIsMeasuredNotAssumed() {
        assertThat(MarkdownStyler.FIXED_WIDTH_FAMILY).isNotBlank();

        // Whatever was chosen must actually be fixed-width, or be the fallback
        // reached only when nothing on the machine measured true.
        assertThat(MarkdownStyler.isFixedWidth(MarkdownStyler.FIXED_WIDTH_FAMILY)
                || "Monospaced".equals(MarkdownStyler.FIXED_WIDTH_FAMILY)).isTrue();

        // A family that is not installed is substituted by AWT, and a substitute
        // is not what was asked for however it measures.
        assertThat(MarkdownStyler.isFixedWidth("No Such Font At All")).isFalse();
    }
    @Test
    void plainProseIsNotMarkdownAndSizesResolve() {
        assertThat(MarkdownStyler.looksLikeMarkdown("Validation passed with no errors.")).isFalse();
        assertThat(MarkdownStyler.looksLikeMarkdown("- **DTD** passes")).isTrue();
        var sized = MarkdownStyler.sized(runs("## Sub"), 12);
        assertThat(StyleConstants.getFontSize(sized.get(0).style())).isEqualTo(14);
        assertThat(sized.get(0).style().getAttribute(MarkdownStyler.SIZE_DELTA)).isNull();
    }

    @Test
    void escapesEntitiesReferencesCommentsAndSoftBreaksAreFaithful() {
        assertThat(MarkdownStyler.text(runs("my\\_file.xml and a \\* b and AT&amp;T")))
                .isEqualTo("my_file.xml and a * b and AT&T");
        assertThat(MarkdownStyler.text(runs("See note [1] and the [optional] attribute **x**")))
                .isEqualTo("See note [1] and the [optional] attribute x");
        assertThat(MarkdownStyler.text(runs("Add this:\n\n<!-- reviewed -->\n\nthen **rebuild**.")))
                .isEqualTo("Add this:\n\n<!-- reviewed -->\n\nthen rebuild.");
        assertThat(MarkdownStyler.text(runs("line one\nline **two**"))).isEqualTo("line one\nline two");
        assertThat(MarkdownStyler.text(runs("![diagram](out/diff.png)"))).isEqualTo("diagram (out/diff.png)");
    }

    @Test
    void linkLabelsKeepTheirMarkupAndBareUrlsAreLinks() {
        var rs = runs("See [**the docs**](https://x.y/d) or https://x.y/plain");
        assertThat(MarkdownStyler.text(rs)).isEqualTo("See the docs (https://x.y/d) or https://x.y/plain");
        assertThat(StyleConstants.isBold(runWith(rs, "the docs").style())).isTrue();
        assertThat(StyleConstants.isUnderline(runWith(rs, "the docs").style())).isTrue();
        assertThat(StyleConstants.isUnderline(runWith(rs, "https://x.y/plain").style())).isTrue();
    }

    @Test
    void nestedBlocksInListsAndQuotesKeepTheirShape() {
        assertThat(MarkdownStyler.text(runs("- - nested"))).isEqualTo("•\n  • nested");
        assertThat(MarkdownStyler.text(runs("> - a\n> - b"))).isEqualTo("│ • a\n│ • b");
        assertThat(MarkdownStyler.text(runs("> ```\n> line1\n> line2\n> ```"))).isEqualTo("│ line1\n│ line2");
    }

    @Test
    void restyleTouchesOnlyTheMessageRegion() throws Exception {
        var doc = new javax.swing.text.DefaultStyledDocument();
        doc.insertString(0, "Agent: ", null);
        int start = doc.getLength();
        doc.insertString(start, "**bold** text", null);
        int end = doc.getLength();
        doc.insertString(end, "\n• a note\n", null);
        MarkdownStyler.restyle(doc, start, end, "**bold** text", P, 12);
        assertThat(doc.getText(0, doc.getLength())).isEqualTo("Agent: bold text\n• a note\n");
    }

    @Test
    void onlyRealMarkdownCountsAsMarkdown() {
        assertThat(MarkdownStyler.looksLikeMarkdown("my_file.xml:12: expected </b> [x]")).isFalse();
        assertThat(MarkdownStyler.looksLikeMarkdown("a | b")).isFalse();
        assertThat(MarkdownStyler.looksLikeMarkdown("## Result")).isTrue();
        assertThat(MarkdownStyler.looksLikeMarkdown("see [the docs](https://x)")).isTrue();
        assertThat(MarkdownStyler.looksLikeMarkdown("| a | b |\n|---|---|")).isTrue();
        assertThat(MarkdownStyler.looksLikeMarkdown("1. first\n2. second")).isTrue();
    }
}
