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

package com.dogsbay.xml.format;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormatEngineTest {

    private static String fmt(String xml) throws Exception {
        return FormatEngine.format(xml, null, "UTF-8", FormatStyle.defaults());
    }

    @Test
    @DisplayName("formatting is idempotent: format(format(x)) == format(x)")
    void idempotent() throws Exception {
        String[] samples = {
            "<root><a>x</a><b><c/></b></root>",
            "<concept id=\"x\"><title>T</title><conbody><p>Some prose here.</p>"
                + "<note>Be careful.</note></conbody></concept>",
            "<doc>\n  <!-- a comment -->\n  <p>text <b>bold</b> more</p>\n</doc>",
            "<codeblock>line 1\n    indented\nline 3</codeblock>"
        };
        for (String s : samples) {
            String once = fmt(s);
            String twice = fmt(once);
            assertThat(twice).as("idempotent for: " + s).isEqualTo(once);
        }
    }

    @Test
    @DisplayName("codeblock whitespace is preserved verbatim (not reflowed)")
    void preservesCodeblock() throws Exception {
        String xml = "<concept><conbody><codeblock>def f():\n    return 1\n\nf()</codeblock></conbody></concept>";
        String out = fmt(xml);
        assertThat(out).contains("def f():\n    return 1\n\nf()");
    }

    @Test
    @DisplayName("xml:space=preserve content is kept verbatim")
    void preservesXmlSpace() throws Exception {
        String xml = "<doc><pre xml:space=\"preserve\">a   b\n   c</pre></doc>";
        assertThat(fmt(xml)).contains("a   b\n   c");
    }

    @Test
    @DisplayName("comments survive formatting")
    void keepsComments() throws Exception {
        assertThat(fmt("<doc><!-- keep me --><p>x</p></doc>")).contains("<!-- keep me -->");
    }

    @Test
    @DisplayName("long prose is NOT hard-wrapped (no inserted newlines in text)")
    void noHardWrap() throws Exception {
        String longLine = "This is a deliberately long paragraph of prose that would exceed any "
                + "reasonable wrap column if hard wrapping were enabled, but it must stay on one line.";
        String out = fmt("<doc><p>" + longLine + "</p></doc>");
        assertThat(out).contains(longLine);   // the sentence is intact on one line
    }

    @Test
    @DisplayName("author line breaks in prose are preserved (one-sentence-per-line)")
    void preservesSemanticLineBreaks() throws Exception {
        String sbr = "<concept><conbody><p>First sentence.\nSecond sentence.\n"
                + "Third sentence.</p></conbody></concept>";
        String once = fmt(sbr);
        // each sentence stays on its own line (not collapsed)
        assertThat(once).contains("First sentence.\nSecond sentence.\nThird sentence.");
        // and re-formatting an SBR document is a no-op (round-trip stable)
        assertThat(fmt(once)).isEqualTo(once);
    }

    @Test
    @DisplayName("a single blank line between blocks is preserved; multiples collapse to one")
    void preservesSingleBlankLine() throws Exception {
        // one blank line between the two <p> blocks → kept
        String one = fmt("<concept><conbody><p>A.</p>\n\n<p>B.</p></conbody></concept>");
        assertThat(one).contains("<p>A.</p>\n\n    <p>B.</p>");

        // three blank lines → collapsed to a single one
        String many = fmt("<concept><conbody><p>A.</p>\n\n\n\n<p>B.</p></conbody></concept>");
        assertThat(many).contains("<p>A.</p>\n\n    <p>B.</p>");
        assertThat(many).doesNotContain("\n\n\n");

        // no blank line → none introduced
        String none = fmt("<concept><conbody><p>A.</p>\n<p>B.</p></conbody></concept>");
        assertThat(none).doesNotContain("</p>\n\n");
    }

    @Test
    @DisplayName("blank-line preservation is idempotent")
    void blankLineIdempotent() throws Exception {
        String once = fmt("<concept><conbody><p>A.</p>\n\n<p>B.</p></conbody></concept>");
        assertThat(fmt(once)).isEqualTo(once);
    }

    @Test
    @DisplayName("single-line prose stays on one line (no spurious breaks inserted)")
    void singleLineUnchanged() throws Exception {
        String out = fmt("<concept><conbody><p>One sentence only.</p></conbody></concept>");
        assertThat(out).contains("<p>One sentence only.</p>");
    }

    @Test
    @DisplayName("opt-out: preserveTextLineBreaks=false collapses prose to one line")
    void optOutCollapses() throws Exception {
        String sbr = "<concept><conbody><p>First.\nSecond.</p></conbody></concept>";
        String out = FormatEngine.format(sbr, null, "UTF-8",
                FormatStyle.defaults().withPreserveTextLineBreaks(false));
        assertThat(out).contains("<p>First. Second.</p>");
    }

    @Test
    @DisplayName("newline style is applied consistently (CRLF) with no lone LF")
    void newlineStyle() throws Exception {
        String out = FormatEngine.format("<root><a>x</a></root>", null, "UTF-8",
                FormatStyle.defaults().withNewline(FormatStyle.NewlineStyle.CRLF));
        assertThat(out).contains("\r\n");
        assertThat(out.replace("\r\n", "")).doesNotContain("\n");   // every \n is part of \r\n
    }

    @Test
    @DisplayName("trim collapses insignificant whitespace in mixed content (default on)")
    void trimsInsignificantWhitespace() throws Exception {
        String dirty = "<concept><conbody>"
                + "<p>Digital   audio.   Understanding  helps  edits in     <keyword keyref=\"x\"/>.   </p>"
                + "</conbody></concept>";
        String out = fmt(dirty);
        assertThat(out).contains("<p>Digital audio. Understanding helps edits in <keyword keyref=\"x\"/>.</p>");
    }

    @Test
    @DisplayName("significant space between inline elements (no prose) is NOT dropped")
    void keepsSpaceBetweenInlineElements() throws Exception {
        // whitespace WITHOUT a newline = inline content, not indentation → preserved
        assertThat(fmt("<doc><p><ph>a</ph> <ph>b</ph></p></doc>"))
                .contains("<p><ph>a</ph> <ph>b</ph></p>");
        // whitespace WITH a newline between block siblings = indentation → reformatted
        assertThat(fmt("<concept><conbody><p>One.</p>\n  <p>Two.</p></conbody></concept>"))
                .contains("<p>One.</p>\n    <p>Two.</p>");
    }

    @Test
    @DisplayName("trim keeps single boundary spaces around inline elements")
    void trimKeepsInlineBoundarySpace() throws Exception {
        String out = fmt("<doc><p>Sound is a continuous     <ph>x</ph>  of  changes.</p></doc>");
        assertThat(out).contains("a continuous <ph>x</ph> of changes.");
    }

    @Test
    @DisplayName("trim preserves semantic-line-break continuation indent")
    void trimPreservesSbrIndent() throws Exception {
        String out = fmt("<concept><conbody><p>First sentence.\n    Second  sentence  here.</p></conbody></concept>");
        // internal runs collapsed, but the author's newline + continuation indent survive
        // (trim preserves the break and indent; it doesn't re-align like reflow)
        assertThat(out).contains("<p>First sentence.\n    Second sentence here.</p>");
    }

    @Test
    @DisplayName("trim never touches verbatim blocks (codeblock)")
    void trimSkipsCodeblock() throws Exception {
        String out = fmt("<doc><codeblock>def  f():\n    return   1</codeblock></doc>");
        assertThat(out).contains("def  f():\n    return   1");
    }

    @Test
    @DisplayName("trim opt-out preserves mixed-content whitespace")
    void trimOptOutPreserves() throws Exception {
        String dirty = "<doc><p>a  <ph>x</ph>   b</p></doc>";
        String out = FormatEngine.format(dirty, null, "UTF-8",
                FormatStyle.defaults().withTrimWhitespace(false));
        assertThat(out).contains("a  <ph>x</ph>   b");   // verbatim, not collapsed
    }

    @Test
    @DisplayName("indent size 0 = no structural indent (block tags flush-left)")
    void flushBlocks() throws Exception {
        FormatStyle flush = FormatStyle.defaults().withIndent(FormatStyle.IndentUnit.SPACES, 0);
        String out = FormatEngine.format(
                "<concept><conbody><section><p>x</p></section></conbody></concept>", null, "UTF-8", flush);
        // every block element starts at column 0, still one per line
        assertThat(out).contains("\n<conbody>\n<section>\n<p>x</p>\n</section>\n</conbody>");
        assertThat(out).doesNotContain("\n  <");   // no indentation anywhere
    }

    @Test
    @DisplayName("format never wraps attributes onto new lines, even if the global wrap-column is set")
    void ignoresGlobalWrapColumn() throws Exception {
        int prev = com.dogsbay.xml.DogsBayXMLWriter.getMaxLineLength();
        com.dogsbay.xml.DogsBayXMLWriter.setMaxLineLength(40);   // editor Wrap Text on, narrow
        try {
            String out = fmt("<doc><p>A long sentence of prose that exceeds forty columns "
                    + "with an <keyword keyref=\"product-name\"/> inline.</p></doc>");
            assertThat(out).contains("<keyword keyref=\"product-name\"/>");   // tag intact, no split
            assertThat(out).doesNotContain("<keyword\n");
            // the global static is restored, not mutated by formatting
            assertThat(com.dogsbay.xml.DogsBayXMLWriter.getMaxLineLength()).isEqualTo(40);
        } finally {
            com.dogsbay.xml.DogsBayXMLWriter.setMaxLineLength(prev);
        }
    }

    @Test
    @DisplayName("XML declaration uses the IANA encoding name (UTF8 → UTF-8)")
    void canonicalEncodingInDeclaration() throws Exception {
        String in = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<doc/>";
        // even when the caller passes Java's "UTF8", the declaration stays standards-correct
        String out = FormatEngine.format(in, null, "UTF8", FormatStyle.defaults());
        assertThat(out).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(out).doesNotContain("UTF8\"");
    }

    @Test
    @DisplayName("exactly one blank line between the DOCTYPE and the root element")
    void singleBlankAfterDoctype() throws Exception {
        String in = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"concept.dtd\">\n"
                + "<concept id=\"x\"><title>T</title></concept>";
        String out = fmt(in);
        assertThat(out).contains("\"concept.dtd\">\n\n<concept");
        assertThat(out).doesNotContain("\n\n\n");   // never two blank lines
    }

    @Test
    @DisplayName("indent unit is honored (tabs vs spaces)")
    void honorsIndentUnit() throws Exception {
        String xml = "<root><child/></root>";
        String spaces = FormatEngine.format(xml, null, "UTF-8",
                FormatStyle.defaults().withIndent(FormatStyle.IndentUnit.SPACES, 4));
        String tabs = FormatEngine.format(xml, null, "UTF-8",
                FormatStyle.defaults().withIndent(FormatStyle.IndentUnit.TABS, 4));
        assertThat(spaces).contains("\n    <child");
        assertThat(tabs).contains("\n\t<child");
    }
}
