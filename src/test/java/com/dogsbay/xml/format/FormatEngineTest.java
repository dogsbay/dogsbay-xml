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

    // ── Entities ─────────────────────────────────────────────────────

    @Test
    void leavesEscapedCharactersExactlyAsTheyWere() throws Exception {
        // This reader does not substitute entities, so `&amp;` is five characters of text in the tree rather than one
        // ampersand. Writing it back through a serializer that escapes turned every format into content corruption:
        // `&amp;` became `&amp;amp;`, and again on the next run.
        String source = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n  <p>Tools &amp; Options</p>\n</root>\n";

        String out = fmt(source);

        assertThat(out).contains("Tools &amp; Options");
        assertThat(out).doesNotContain("&amp;amp;");
    }

    @Test
    void everyEscapedCharacterSurvives() throws Exception {
        String source = "<root><p>&lt;tag&gt; &amp; &quot;quoted&quot; &apos;and&apos; &amp;amp; literal</p></root>";

        String out = fmt(source);

        assertThat(out).contains("&lt;tag&gt;").contains("&quot;quoted&quot;").contains("&apos;and&apos;");
        // a literal, authored `&amp;amp;` stays exactly that and does not grow another level
        assertThat(out).contains("&amp;amp; literal").doesNotContain("&amp;amp;amp;");
    }

    @Test
    void formattingTwiceChangesNothingTheSecondTime() throws Exception {
        String source = "<root>\n  <p>Tools &amp; Options and &lt;tags&gt;</p>\n</root>\n";

        String once = fmt(source);
        String twice = fmt(once);

        // the compounding was what made this dangerous: each run escaped what the last had escaped
        assertThat(twice).isEqualTo(once);
    }

    // ── Encodings ────────────────────────────────────────────────────

    private static String fmt(String xml, String encoding) throws Exception {
        return FormatEngine.format(xml, null, encoding, FormatStyle.defaults());
    }

    /**
     * The two invariants that matter for an encoding, and that asserting on the returned Java
     * String alone does not catch: the document still declares the encoding it came in with, and
     * the result can actually be written in that encoding. Without these, "work in UTF-8 throughout
     * and rewrite the declaration" passes every content assertion while losing the document's
     * encoding entirely.
     */
    private static String withoutDeclaration(String xml) {
        return xml.replaceFirst("<\\?xml[^>]*\\?>", "");
    }

    private static void assertWritableAs(String out, String encoding) {
        assertThat(out)
                .as("declaration still names " + encoding)
                .containsPattern("<\\?xml[^>]*encoding=\"" + java.util.regex.Pattern.quote(encoding) + "\"");
        assertThat(java.nio.charset.Charset.forName(encoding).newEncoder().canEncode(out))
                .as("every character of the output is writable in " + encoding + ": " + out)
                .isTrue();
    }

    @Test
    @DisplayName("Latin-1 content survives formatting unchanged")
    void latin1ContentSurvives() throws Exception {
        String source = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root>\n  <p>caf\u00e9 na\u00efve</p>\n</root>\n";

        String out = fmt(source, "ISO-8859-1");

        // the serializer wrote UTF-8 bytes while the caller read them back as Latin-1, so `caf\u00e9` arrived as `cafÃ©`
        assertThat(out).contains("caf\u00e9 na\u00efve");
        assertWritableAs(out, "ISO-8859-1");
    }

    @Test
    @DisplayName("Latin-1 corruption does not compound over repeated formats")
    void latin1DoesNotCompound() throws Exception {
        String source = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root>\n  <p>caf\u00e9</p>\n</root>\n";

        String once = fmt(source, "ISO-8859-1");
        String twice = fmt(once, "ISO-8859-1");
        String thrice = fmt(twice, "ISO-8859-1");

        // each pass used to mangle the last pass's mangling: caf\u00e9 -> cafÃ© -> cafÃÂ©, growing without bound.
        // Assert stability before content, or the content assertion fails first and the compounding
        // assertions — the thing this test is named for — never run.
        assertThat(twice).isEqualTo(once);
        assertThat(thrice).isEqualTo(once);
        assertThat(once).contains("caf\u00e9");
        assertWritableAs(once, "ISO-8859-1");
    }

    @Test
    @DisplayName("every declared encoding round-trips its own non-ASCII characters")
    void everyEncodingRoundTrips() throws Exception {
        String[][] cases = {
            {"ISO-8859-1",   "caf\u00e9 \u00fcber na\u00efve"},
            {"windows-1252", "caf\u00e9 \u201cquoted\u201d \u2014 dash"},
            {"UTF-8",        "caf\u00e9 \u4f60\u597d \ud83c\udf89"},
            {"UTF-16",       "caf\u00e9 \u4f60\u597d"},
            {"Shift_JIS",    "\u3053\u3093\u306b\u3061\u306f"},
        };
        for (String[] c : cases) {
            String encoding = c[0];
            String text = c[1];
            String source = "<?xml version=\"1.0\" encoding=\"" + encoding + "\"?>\n<root><p>" + text + "</p></root>\n";

            String once = fmt(source, encoding);
            String twice = fmt(once, encoding);

            assertThat(once).as("content preserved in " + encoding).contains(text);
            assertThat(twice).as("idempotent in " + encoding).isEqualTo(once);
            assertWritableAs(once, encoding);
        }
    }

    @Test
    @DisplayName("characters the encoding cannot represent are escaped, not dropped")
    void unrepresentableCharactersAreEscaped() throws Exception {
        // an arrow has no Latin-1 byte, so it has to leave as a numeric reference rather than silently becoming `?`
        String source = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root><p>a \u2192 b</p></root>\n";

        String out = fmt(source, "ISO-8859-1");

        // The encoder used to substitute '?' silently, which cannot be undone. The source holds no
        // '?' of its own, so any '?' in the output is a lost character — a sharper check than
        // matching one expected phrase, which a different spacing would slip past.
        // Compare with the declaration removed from both: `<?xml … ?>` is full of question marks.
        assertThat(withoutDeclaration(source)).doesNotContain("?");
        assertThat(withoutDeclaration(out)).doesNotContain("?");
        // Deliberately not accepting a literal U+2192: in a document declared ISO-8859-1 that is
        // exactly the thing that cannot be written, so accepting it would pass a fix that quietly
        // switched the whole pipeline to UTF-8.
        assertThat(out).containsPattern("a &#(8594|x2192); b");
        assertWritableAs(out, "ISO-8859-1");

        // and the escaped form must survive another pass unchanged rather than growing
        assertThat(fmt(out, "ISO-8859-1")).isEqualTo(out);
    }

    @Test
    @DisplayName("an unrepresentable character survives when whitespace trimming is off")
    void unrepresentableSurvivesWithoutTrimming() throws Exception {
        // With trimming on, normalizeWhitespace runs first and escapes the arrow, which masks the
        // byte round-trips inside XMLUtilities.format itself. Turning trimming off is the only way
        // to reach format() with a live unrepresentable character.
        String source = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root><p>a \u2192 b</p></root>\n";
        FormatStyle noTrim = FormatStyle.defaults().withTrimWhitespace(false);

        String out = FormatEngine.format(source, null, "ISO-8859-1", noTrim);

        assertThat(out).containsPattern("a &#(8594|x2192); b");
        assertWritableAs(out, "ISO-8859-1");
    }

    @Test
    @DisplayName("attribute values keep characters the encoding cannot represent")
    void attributeValuesAreEscaped() throws Exception {
        String source = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
                + "<root><p outputclass=\"caf\u00e9 a \u2192 b\">t</p></root>\n";

        String out = fmt(source, "ISO-8859-1");

        assertThat(out).contains("caf\u00e9");
        assertThat(out).containsPattern("outputclass=\"caf\u00e9 a &#(8594|x2192); b\"");
        assertWritableAs(out, "ISO-8859-1");
        assertThat(fmt(out, "ISO-8859-1")).isEqualTo(out);
    }

    @Test
    @DisplayName("an astral-plane character becomes one reference, not two mangled halves")
    void astralPlaneCharacterEscapesOnce() throws Exception {
        String source = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root><p>party \ud83c\udf89 time</p></root>\n";

        String out = fmt(source, "ISO-8859-1");

        // U+1F389 is one code point spelled as a surrogate pair; escaping per char would emit two
        // references in the surrogate range, which no reader can put back together.
        assertThat(out).contains("party &#127881; time");
        assertThat(out).doesNotContain("&#55356;").doesNotContain("&#57225;");
        assertWritableAs(out, "ISO-8859-1");
    }

    @Test
    @DisplayName("an already-escaped reference is left alone and does not grow")
    void existingReferencesDoNotGrow() throws Exception {
        String source = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
                + "<root><p>ref &#8594; and literal &amp;#8594; text</p></root>\n";

        String once = fmt(source, "ISO-8859-1");

        assertThat(once).contains("ref &#8594;").contains("literal &amp;#8594; text");
        assertThat(once).doesNotContain("&amp;#8594; and");
        assertThat(fmt(once, "ISO-8859-1")).isEqualTo(once);
    }

    @Test
    @DisplayName("comments and CDATA are not given numeric references (a reference there is literal text)")
    void commentsAndCdataAreNotNumericEscaped() throws Exception {
        // Pins the deliberate limit documented in UnrepresentableEscaper: inside a comment, a CDATA
        // section or a PI, `&#8594;` is six characters of text rather than a character reference, so
        // escaping there would corrupt content. A character the encoding cannot hold is still lost
        // in those three places — a known gap, not something this test should be read as blessing.
        String source = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
                + "<root><!-- caf\u00e9 note --><p><![CDATA[caf\u00e9 data]]></p></root>\n";

        String out = fmt(source, "ISO-8859-1");

        assertThat(out).contains("<!-- caf\u00e9 note -->");
        assertThat(out).contains("<![CDATA[caf\u00e9 data]]>");
        assertThat(out).doesNotContain("&#233;");
        assertWritableAs(out, "ISO-8859-1");
    }

    // ── Well-formed output ───────────────────────────────────────────

    @Test
    @DisplayName("a CDATA section holding ]]> stays well-formed")
    void cdataHoldingTerminatorStaysWellFormed() throws Exception {
        // `]]>` cannot appear inside one CDATA section, so the source spells it the only legal way:
        // split across two sections. The text node is `a ]]> b`, and writing that back as a single
        // section ended it early and produced a document no parser would accept.
        String source = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<root><p><![CDATA[a ]]]]><![CDATA[> b]]></p></root>\n";

        String out = fmt(source);

        assertThat(out).doesNotContain("<![CDATA[a ]]> b]]>");
        assertWellFormed(out);
        // the content still reads back as the same characters
        assertThat(textOf(out)).contains("a ]]> b");
        // and a second pass neither fails nor keeps splitting
        assertThat(fmt(out)).isEqualTo(out);
    }

    @Test
    @DisplayName("ordinary CDATA is left as one section")
    void ordinaryCdataIsNotSplit() throws Exception {
        String source = "<root><p><![CDATA[plain < & data]]></p></root>";

        String out = fmt(source);

        assertThat(out).contains("<![CDATA[plain < & data]]>");
        assertThat(out).doesNotContain("]]><![CDATA[");
        assertWellFormed(out);
    }

    @Test
    @DisplayName("a UTF-8 BOM does not stop formatting, and is kept")
    void utf8BomSurvives() throws Exception {
        // A byte-order mark reaches us as U+FEFF before the declaration, where the parser rejects it
        // ("Content is not allowed in prolog"), so these files could not be formatted at all.
        String source = "\ufeff<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root><p>hi</p></root>\n";

        String out = fmt(source);

        assertThat(out).startsWith("\ufeff");
        assertThat(out).contains("<p>hi</p>");
        assertWellFormed(out.substring(1));
        // whether a file carries a BOM is the file's business, not the formatter's
        assertThat(fmt(out)).isEqualTo(out);
    }

    @Test
    @DisplayName("a document without a BOM does not gain one")
    void noBomIsNotAdded() throws Exception {
        String out = fmt("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root><p>hi</p></root>\n");

        assertThat(out).doesNotContain("\ufeff");
    }

    private static String textOf(String xml) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        org.w3c.dom.Document d = f.newDocumentBuilder().parse(
                new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        return d.getDocumentElement().getTextContent();
    }

    private static void assertWellFormed(String xml) {
        try {
            javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        } catch (Exception e) {
            throw new AssertionError("output is not well-formed: " + e.getMessage() + "\n" + xml, e);
        }
    }

    // ── Comments in mixed content ────────────────────────────────────

    @Test
    @DisplayName("a comment in prose does not make the file grow on every format")
    void commentInProseIsIdempotent() throws Exception {
        String source = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n  <p>before <!-- note --> after</p>\n</root>\n";

        String once = fmt(source);
        String twice = fmt(once);
        String thrice = fmt(twice);

        // it used to gain a line per pass, without bound — fatal under format-on-save
        assertThat(twice).as("second pass changes nothing").isEqualTo(once);
        assertThat(thrice).as("third pass changes nothing").isEqualTo(once);
        assertThat(once.lines().count()).isLessThanOrEqualTo(source.lines().count() + 1);
    }

    @Test
    @DisplayName("a comment in prose keeps the space that followed it")
    void commentInProseKeepsTheFollowingSpace() throws Exception {
        String out = fmt("<root><p>before <!-- note --> after</p></root>");

        assertThat(out).doesNotContain("--> after".replace(" ", ""));
        assertThat(collapsed(out)).isEqualTo("before after");
    }

    @Test
    @DisplayName("a comment between two words does not push them apart")
    void commentBetweenWordsAddsNoSpace() throws Exception {
        // No whitespace either side, so the reader sees `ab`. Breaking the line before the comment
        // inserts whitespace where the author put none, and `ab` becomes `a b`.
        String out = fmt("<root><p>a<!-- c -->b</p></root>");

        assertThat(collapsed(out)).isEqualTo("ab");
    }

    @Test
    @DisplayName("a comment between elements is still laid out on its own line")
    void commentBetweenElementsStaysOnItsOwnLine() throws Exception {
        // The other half of the contract: where there is no text, a comment is a block and indenting
        // it is both safe and wanted.
        String out = fmt("<root><!-- about a --><a>x</a><!-- about b --><b>y</b></root>");

        assertThat(out).contains("\n  <!-- about a -->");
        assertThat(out).contains("\n  <!-- about b -->");
        assertThat(fmt(out)).isEqualTo(out);
    }

    /** The document's text as a reader sees it: comments gone, runs of whitespace collapsed. */
    private static String collapsed(String xml) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        // The grammar says nothing about the text a reader sees, and resolving it here would need the
        // catalog this helper has no business knowing about.
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        org.w3c.dom.Document d = f.newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        return d.getDocumentElement().getTextContent().replaceAll("\\s+", " ").trim();
    }

    // ── Whitespace significance, from the grammar ────────────────────

    /** A DITA topic whose DOCTYPE resolves through the editor's bundled catalog. */
    private static String ditaTopic(String bodyContent) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE topic PUBLIC \"-//OASIS//DTD DITA Topic//EN\" \"topic.dtd\">\n"
                + "<topic id=\"t\"><title>T</title><body>" + bodyContent + "</body></topic>\n";
    }

    @Test
    @DisplayName("a list item holding blocks is still indented")
    void listItemOfBlocksIsIndented() throws Exception {
        String out = fmt(ditaTopic("<ul><li><p>one</p><p>two</p></li></ul>"));

        // <li> permits #PCDATA, so a rule that went only by the content model would leave every list
        // in every document flat. Whitespace beside a block is discarded, so this is safe.
        assertThat(out).containsPattern("<li>\\s*\\n\\s*<p>one</p>");
        assertThat(out).containsPattern("<p>two</p>\\s*\\n\\s*</li>");
        assertThat(fmt(out)).isEqualTo(out);
    }

    @Test
    @DisplayName("a section holding a title and blocks is still indented")
    void sectionOfBlocksIsIndented() throws Exception {
        String out = fmt(ditaTopic("<section><title>S</title><p>x</p></section>"));

        assertThat(out).containsPattern("<section>\\s*\\n\\s*<title>S</title>");
    }

    @Test
    @DisplayName("prose around an inline element keeps the author's spacing")
    void proseAroundInlineIsUntouched() throws Exception {
        String out = fmt(ditaTopic("<p>Click <uicontrol>Save</uicontrol> to finish.</p>"));

        assertThat(out).contains("<p>Click <uicontrol>Save</uicontrol> to finish.</p>");
    }

    @Test
    @DisplayName("a document with no grammar is laid out exactly as before")
    void noGrammarMeansNoChange() throws Exception {
        // Our own files — config.xml, DITAVALs, plugin.xml — have no grammar. Treating every element
        // as possibly holding text would leave them flat, which costs something and buys nothing.
        String out = fmt("<root><a>x</a><b><c>y</c></b></root>");

        assertThat(out).containsPattern("<root>\\s*\\n\\s*<a>x</a>");
        assertThat(out).containsPattern("<b>\\s*\\n\\s*<c>y</c>");
    }

    // ── sort-attributes ──────────────────────────────────────────────

    @Test
    @DisplayName("attribute order is left alone by default")
    void attributeOrderIsKeptByDefault() throws Exception {
        String out = fmt("<root><p zebra=\"1\" alpha=\"2\" middle=\"3\">x</p></root>");

        assertThat(out).contains("<p zebra=\"1\" alpha=\"2\" middle=\"3\">");
    }

    @Test
    @DisplayName("sort-attributes writes them in name order")
    void sortAttributesOrdersByName() throws Exception {
        FormatStyle sorted = FormatStyle.defaults().withSortAttributes(true);

        String out = FormatEngine.format(
                "<root><p zebra=\"1\" alpha=\"2\" middle=\"3\">x</p></root>", null, "UTF-8", sorted);

        assertThat(out).contains("<p alpha=\"2\" middle=\"3\" zebra=\"1\">");
        // attribute order carries no meaning in XML, so nothing a reader sees has changed
        assertThat(collapsed(out)).isEqualTo("x");
        assertThat(FormatEngine.format(out, null, "UTF-8", sorted)).isEqualTo(out);
    }

    @Test
    @DisplayName("sorting keeps namespace-prefixed attributes and their values intact")
    void sortAttributesKeepsPrefixesAndValues() throws Exception {
        FormatStyle sorted = FormatStyle.defaults().withSortAttributes(true);
        String source = "<root xmlns:z=\"urn:z\"><p z:beta=\"2\" alpha=\"1\" xml:space=\"preserve\">x</p></root>";

        String out = FormatEngine.format(source, null, "UTF-8", sorted);

        assertThat(out).contains("alpha=\"1\"").contains("z:beta=\"2\"").contains("xml:space=\"preserve\"");
        assertThat(out.indexOf("alpha=")).isLessThan(out.indexOf("z:beta="));
    }

    @Test
    @DisplayName("whitespace next to an element the grammar does not declare is left alone")
    void unknownElementKeepsItsSurroundingSpace() throws Exception {
        // Found by the rendering oracle on the demo project. <x> is not in the grammar, so it is
        // treated as inline — conservative about *inserting* whitespace. Dropping whitespace beside it
        // is the opposite of conservative, and it lost the space between "manager." and "TODO:".
        // Only whitespace at a known block's own edges is dropped now.
        String source = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE task PUBLIC \"-//OASIS//DTD DITA Task//EN\" \"task.dtd\">\n"
                + "<task id=\"t\"><title>T</title><taskbody><context>\n"
                + "      <p>Install it with your package manager.</p>\n"
                + "      <x>TODO: confirm versions</x>\n"
                + "    </context></taskbody></task>\n";

        String out = fmt(source);

        assertThat(collapsed(out)).isEqualTo(collapsed(source));
        assertThat(collapsed(out)).contains("manager. TODO:");
    }

    @Test
    @DisplayName("dropping the old indent does not touch an authored space between inlines")
    void authoredSpaceBetweenInlinesSurvivesTheDrop() throws Exception {
        // The space here has no newline, so it is one the author typed rather than indentation.
        String out = fmt(ditaTopic("<p><uicontrol>A</uicontrol> <uicontrol>B</uicontrol></p>"));

        assertThat(out).contains("<p><uicontrol>A</uicontrol> <uicontrol>B</uicontrol></p>");
        assertThat(collapsed(out)).contains("A B");
    }

    // ── the drop's own preconditions, which nothing asserted ─────────

    @Test
    @DisplayName("a preserved block keeps the newlines at its edges")
    void aPreservedBlockKeepsItsEdgeNewlines() throws Exception {
        // writeElement's mixed branch is reached before the preserve-space branch when a verbatim
        // block's only content is markup — <coderef/> is an inline phrase — so the drop took the code
        // sample's first and last line break away. For xml:space="preserve" that is what the
        // attribute forbids. Invisible before because every codeblock test uses text-only content.
        String out = fmt(ditaTopic("<codeblock>\n<coderef href=\"f.txt\"/>\n</codeblock>"));

        assertThat(out).contains("<codeblock>\n<coderef href=\"f.txt\"/>\n</codeblock>");
        assertThat(fmt(out)).isEqualTo(out);
    }

    @Test
    @DisplayName("xml:space=preserve keeps the newlines at its edges")
    void xmlSpacePreserveKeepsItsEdgeNewlines() throws Exception {
        String out = fmt(ditaTopic("<p><pre xml:space=\"preserve\">\n<b>bold</b>\n</pre></p>"));

        assertThat(out).contains("<pre xml:space=\"preserve\">\n<b>bold</b>\n</pre>");
        assertThat(fmt(out)).isEqualTo(out);
    }

    // ── Processing instructions ──────────────────────────────────────

    @Test
    @DisplayName("a processing instruction in prose keeps the spaces around it")
    void processingInstructionInProseKeepsItsSpaces() throws Exception {
        String out = fmt("<root><p>alpha <?ph foo?> beta</p></root>");

        assertThat(out).contains("<p>alpha <?ph foo?> beta</p>");
        assertThat(collapsed(out)).isEqualTo("alpha beta");
    }

    @Test
    @DisplayName("a processing instruction in prose does not make the file grow")
    void processingInstructionInProseConverges() throws Exception {
        // It used to gain two lines on every pass, without bound: the break one pass inserted came
        // back as significant text and earned another. --check could never be satisfied.
        // An already-canonical document, so formatting it is a no-op. A line-count bound would not
        // do: it had no margin left (the formatter adds a blank line after the declaration), and a
        // bug that converged to a wrong-but-stable output would satisfy it anyway.
        String canonical = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n<root>\n  <p>alpha <?ph foo?> beta</p>\n</root>\n";

        assertThat(fmt(canonical)).as("already in house style: formatting changes nothing").isEqualTo(canonical);

        String once = fmt("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n  <p>alpha <?ph foo?> beta</p>\n</root>\n");
        assertThat(fmt(once)).isEqualTo(once);
        assertThat(fmt(fmt(once))).isEqualTo(once);
    }

    @Test
    @DisplayName("a processing instruction between blocks keeps its own line")
    void processingInstructionBetweenBlocksKeepsItsLine() throws Exception {
        // The other half of the contract: with no text beside it, a PI is a container's content and
        // is laid out. Counting it as markup outright glued it to the open tag.
        // Flat input, and an exact indent: the previous version fed in a document already at the
        // indentation it then looked for, and its pattern would have passed on column-0 output too.
        String out = fmt("<root><section><?ph lone?></section></root>");

        assertThat(out).contains("\n  <section>\n    <?ph lone?>\n  </section>");
        assertThat(fmt(out)).isEqualTo(out);
    }

    @Test
    @DisplayName("a processing instruction between two words does not push them apart")
    void processingInstructionBetweenWordsAddsNoSpace() throws Exception {
        String out = fmt("<root><p>a<?ph c?>b</p></root>");

        assertThat(collapsed(out)).isEqualTo("ab");
    }

    @Test
    @DisplayName("a processing instruction in verbatim content does not break the code")
    void processingInstructionInVerbatimContentIsUntouched() throws Exception {
        // The worst form of the bug, and the one no test named: a newline went in beside the PI,
        // inside program text. `codeblock` and xml:space="preserve" must come back byte for byte.
        String code = fmt("<root><codeblock>def f():\n    <?ph x?>   return 1\n</codeblock></root>");
        assertThat(code).contains("<codeblock>def f():\n    <?ph x?>   return 1\n</codeblock>");
        assertThat(fmt(code)).isEqualTo(code);

        String pre = fmt("<root><d xml:space=\"preserve\">a   <?ph x?>   b\n   c</d></root>");
        assertThat(pre).contains("<d xml:space=\"preserve\">a   <?ph x?>   b\n   c</d>");
        assertThat(fmt(pre)).isEqualTo(pre);
    }

    @Test
    @DisplayName("a processing instruction keeps its target and data through a round trip")
    void processingInstructionDataSurvives() throws Exception {
        // Parser-derived rather than string-matched: whatever the layout, the PI a reader gets back
        // must have the same target and the same data.
        for (String pi : new String[] {"<?ph foo?>", "<?ph foo bar?>", "<?xml-stylesheet href=\"a.xsl\" type=\"text/xsl\"?>"}) {
            String source = "<root><p>a " + pi + " b</p></root>";

            String out = fmt(source);

            assertThat(instructionsOf(out)).as("for " + pi).isEqualTo(instructionsOf(source));
            assertThat(fmt(out)).as("stable for " + pi).isEqualTo(out);
        }
    }

    /** Every processing instruction a parser finds, as target plus data. */
    private static java.util.List<String> instructionsOf(String xml) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        org.w3c.dom.Document d = f.newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
        java.util.List<String> found = new java.util.ArrayList<>();
        org.w3c.dom.NodeList all = d.getElementsByTagName("*");
        java.util.ArrayDeque<org.w3c.dom.Node> queue = new java.util.ArrayDeque<>();
        queue.add(d);
        while (!queue.isEmpty()) {
            org.w3c.dom.Node n = queue.poll();
            if (n.getNodeType() == org.w3c.dom.Node.PROCESSING_INSTRUCTION_NODE) {
                found.add(n.getNodeName() + "|" + n.getNodeValue());
            }
            for (org.w3c.dom.Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) {
                queue.add(c);
            }
        }
        return found;
    }
}
