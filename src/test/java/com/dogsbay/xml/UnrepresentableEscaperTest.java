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

package com.dogsbay.xml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnrepresentableEscaperTest {

    private static final UnrepresentableEscaper LATIN1 = new UnrepresentableEscaper("ISO-8859-1");
    private static final UnrepresentableEscaper UTF8 = new UnrepresentableEscaper("UTF-8");

    // ── the plain, per-string form ───────────────────────────────────

    @Test
    void escapesOnlyWhatTheEncodingCannotHold() {
        assertThat(LATIN1.escape("café a → b")).isEqualTo("café a &#8594; b");
        assertThat(LATIN1.escape("plain ascii")).isEqualTo("plain ascii");
    }

    @Test
    @DisplayName("an astral-plane character escapes once, not as two surrogates")
    void astralPlaneEscapesOnce() {
        assertThat(LATIN1.escape("party 🎉")).isEqualTo("party &#127881;");
    }

    @Test
    @DisplayName("a lone surrogate is left alone rather than made into an illegal reference")
    void loneSurrogateIsLeftAlone() {
        // No charset can encode one, and &#55296; is not a legal character reference — escaping it
        // would turn unparseable input into unparseable output.
        assertThat(LATIN1.escape("a\ud800b")).isEqualTo("a\ud800b");
    }

    @Test
    @DisplayName("a Unicode encoding escapes nothing at all")
    void unicodeEncodingIsAPassthrough() {
        String all = "café a → b 🎉 你好";
        assertThat(UTF8.escape(all)).isEqualTo(all);
        assertThat(UTF8.escapeDocument("<p>" + all + "</p>")).isEqualTo("<p>" + all + "</p>");
    }

    @Test
    @DisplayName("an unknown or blank encoding falls back to escaping nothing")
    void unknownEncodingIsAPassthrough() {
        for (String name : new String[] {null, "", "   ", "no-such-charset"}) {
            assertThat(new UnrepresentableEscaper(name).escape("a → b"))
                    .as("encoding " + name).isEqualTo("a → b");
        }
    }

    // ── the whole-document form, used by the save path ───────────────

    @Test
    @DisplayName("text content and attribute values are escaped")
    void documentTextAndAttributesAreEscaped() {
        String xml = "<root><p class=\"a → b\" other='x → y'>text → here</p></root>";

        assertThat(LATIN1.escapeDocument(xml)).isEqualTo(
                "<root><p class=\"a &#8594; b\" other='x &#8594; y'>text &#8594; here</p></root>");
    }

    @Test
    @DisplayName("comments, PIs and the DOCTYPE are left exactly as they are")
    void unescapableRegionsAreUntouched() {
        // A numeric reference in any of these is six characters of text, not a character, so putting
        // one there would corrupt the content rather than preserve it.
        String xml = "<!DOCTYPE root SYSTEM \"a\u2192.dtd\">"
                + "<root><!-- note \u2192 here --><?pi \u2192 ?><p>text \u2192</p></root>";

        String out = LATIN1.escapeDocument(xml);

        assertThat(out).contains("<!-- note \u2192 here -->");
        assertThat(out).contains("<?pi \u2192 ?>");
        assertThat(out).contains("\"a\u2192.dtd\"");
        // ...while the character data beside them is escaped
        assertThat(out).contains("text &#8594;");
    }

    @Test
    @DisplayName("a CDATA section holding an unrepresentable character becomes escaped text")
    void cdataIsDemotedRatherThanLost() {
        // A section cannot hold a reference either, but it and the equivalent escaped text say the
        // same thing to a reader — so demote it and keep the character.
        String out = LATIN1.escapeDocument("<p><![CDATA[a \u2192 b]]></p>");

        assertThat(out).isEqualTo("<p>a &#8594; b</p>");
    }

    @Test
    @DisplayName("demoting a CDATA section escapes the markup it was protecting")
    void demotedCdataEscapesItsMarkup() {
        // Everything CDATA made safe has to be spelled out now, or the result is not well-formed.
        String out = LATIN1.escapeDocument("<p><![CDATA[if (a<b && c>d) \u2192 go]]></p>");

        assertThat(out).isEqualTo("<p>if (a&lt;b &amp;&amp; c&gt;d) &#8594; go</p>");
    }

    @Test
    @DisplayName("an ordinary CDATA section is left as one section")
    void ordinaryCdataIsNotDemoted() {
        String xml = "<p><![CDATA[if (a<b && c>d) caf\u00e9]]></p>";

        assertThat(LATIN1.escapeDocument(xml)).as("Latin-1 holds all of this").isEqualTo(xml);
        assertThat(UTF8.escapeDocument(xml)).isEqualTo(xml);
    }

    @Test
    @DisplayName("what cannot be saved at all is reported, so a caller can say so")
    void unescapableCharactersAreReported() {
        assertThat(LATIN1.findUnescapable("<root><p>text \u2192 here</p></root>"))
                .as("all of it is escapable").isEmpty();

        assertThat(LATIN1.findUnescapable("<root><p><![CDATA[\u2192]]></p></root>"))
                .as("CDATA is rescued by demotion, so it is not a loss")
                .isEmpty();

        // U+2248 as well as U+2192, because Latin-1 has a byte for u-umlaut and this is about the
        // characters it has none for.
        String lossy = "<root><!-- \u2192 --><?pi \u2248?><p>caf\u00e9</p></root>";
        assertThat(LATIN1.findUnescapable(lossy)).isEqualTo("\u2192\u2248");

        assertThat(UTF8.findUnescapable(lossy)).as("nothing is unrepresentable in UTF-8").isEmpty();
    }

    @Test
    @DisplayName("an internal DOCTYPE subset is skipped whole")
    void internalSubsetIsSkipped() {
        String xml = "<!DOCTYPE root [ <!ENTITY x \"→\"> ]><root><p>a → b</p></root>";

        String out = LATIN1.escapeDocument(xml);

        assertThat(out).contains("<!ENTITY x \"→\">");
        assertThat(out).contains("<p>a &#8594; b</p>");
    }

    @Test
    @DisplayName("truncated markup does not lose the rest of the document")
    void truncatedMarkupIsSurvivable() {
        // Not well-formed, but save() must not make it worse or throw.
        assertThat(LATIN1.escapeDocument("<root><!-- unclosed →")).contains("→");
        assertThat(LATIN1.escapeDocument("<root><p attr=\"a →")).contains("&#8594;");
        assertThat(LATIN1.escapeDocument("<root><p>a →")).isEqualTo("<root><p>a &#8594;");
    }

    @Test
    @DisplayName("escaping a document twice changes nothing the second time")
    void documentEscapingIsIdempotent() {
        String xml = "<root><p class=\"a →\">b → c</p><!-- → --></root>";

        String once = LATIN1.escapeDocument(xml);

        assertThat(LATIN1.escapeDocument(once)).isEqualTo(once);
    }

    // ── found by code review: everything escaping cannot reach must be reported ──

    private static boolean wellFormed(String xml) {
        try {
            javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("demoting CDATA cannot leave a ]]> behind")
    void demotionCannotFormATerminator() {
        // `<![CDATA[x\u2192]]]]>>y]]>` is a legal way to write `x\u2192]]>y`. Demoting the first half
        // used to emit `x&#8594;]]>y`, which no parser reads back — and nothing reported it.
        String xml = "<p><![CDATA[x\u2192]]]]>>y</p>";

        String out = LATIN1.escapeDocument(xml);

        assertThat(out).doesNotContain("]]>");
        assertThat(wellFormed(out)).as("output must parse: " + out).isTrue();
        assertThat(LATIN1.escapeDocument(out)).isEqualTo(out);
    }

    @Test
    @DisplayName("interior brackets in demoted CDATA are left readable")
    void demotionLeavesInteriorBracketsAlone() {
        String out = LATIN1.escapeDocument("<p><![CDATA[a[0] = b[1] \u2192 c]]></p>");

        assertThat(out).contains("a[0] = b[1] &#8594; c");
    }

    @Test
    @DisplayName("a character in an element or attribute name is reported, not lost in silence")
    void namesAreReported() {
        UnrepresentableEscaper ascii = new UnrepresentableEscaper("US-ASCII");

        // A name cannot hold a character reference, so this cannot be fixed — but it must be said.
        assertThat(ascii.findUnescapable("<doc><na\u00efve>x</na\u00efve></doc>")).isEqualTo("\u00ef");
        assertThat(ascii.findUnescapable("<doc na\u00efve=\"1\"/>")).isEqualTo("\u00ef");
    }

    @Test
    @DisplayName("a character the markup has made unreachable is reported")
    void unreachableCharactersAreReported() {
        // A bare `<` in character data makes the scanner read on as if it were a tag, so the arrow
        // after it is neither escaped nor escapable. The buffer is malformed either way; losing the
        // character without a word is the part that is not acceptable.
        assertThat(LATIN1.findUnescapable("<doc>a < b \u2192</doc>")).isEqualTo("\u2192");
        assertThat(LATIN1.findUnescapable("<doc a=\u2192>x</doc>")).isEqualTo("\u2192");
    }

    @Test
    @DisplayName("a character in the DOCTYPE is reported")
    void doctypeCharactersAreReported() {
        assertThat(LATIN1.findUnescapable("<!DOCTYPE t SYSTEM \"tache\u2192.dtd\"><t/>"))
                .isEqualTo("\u2192");
    }

    @Test
    @DisplayName("a byte-order mark is set aside, not escaped into the prolog")
    void byteOrderMarkIsNotEscaped() {
        // U+FEFF is unrepresentable in a non-Unicode encoding, so escaping the raw buffer turned the
        // mark into `&#65279;` ahead of the declaration — "Content is not allowed in prolog".
        String xml = "\ufeff<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><t>\u2192</t>";

        String out = LATIN1.escapeDocument(xml);

        assertThat(out).startsWith("\ufeff");
        assertThat(out).doesNotContain("&#65279;");
        assertThat(out).contains("<t>&#8594;</t>");
        assertThat(LATIN1.findUnescapable(xml)).as("the mark itself is not a loss").isEqualTo("\u2192".substring(0, 0));
    }

    @Test
    @DisplayName("what is reported is exactly what escaping could not reach")
    void reportIsDerivedFromTheOutput() {
        // Everything reachable is escaped and so reported as no loss; everything else is named.
        assertThat(LATIN1.findUnescapable("<p a=\"\u2192\">\u2192</p>")).as("both reachable").isEmpty();
        assertThat(LATIN1.findUnescapable("<p><!-- \u2192 --></p>")).isEqualTo("\u2192");
        assertThat(LATIN1.findUnescapable("<p><![CDATA[\u2192]]></p>")).as("rescued by demotion").isEmpty();
    }
}
