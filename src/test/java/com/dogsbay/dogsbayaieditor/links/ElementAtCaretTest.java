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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.links.ElementAtCaret.Located;

class ElementAtCaretTest {

    /** Locates with the caret at the {@code |} marker. */
    private Located at(String textWithCaret) {
        int offset = textWithCaret.indexOf('|');
        return ElementAtCaret.locate(textWithCaret.replace("|", ""), offset);
    }

    @Test
    void innermostElementWins() {
        Located located = at("<concept id=\"c\"><conbody><section>"
                + "<p>some |text</p></section></conbody></concept>");
        assertNotNull(located);
        assertEquals("p", located.element());
        assertNull(located.id());
    }

    @Test
    void capturesIdAndLine() {
        Located located = at("<concept id=\"c\">\n<conbody>\n"
                + "<note id=\"safety\">Wear |gloves.</note>\n</conbody></concept>");
        assertNotNull(located);
        assertEquals("note", located.element());
        assertEquals("safety", located.id());
        assertEquals(3, located.line());
    }

    @Test
    void caretInsideStartTagCounts() {
        Located located = at("<concept id=\"c\"><conbody>"
                + "<note id=\"sa|fety\">x</note></conbody></concept>");
        assertNotNull(located);
        assertEquals("note", located.element());
    }

    @Test
    void structuralContainersAreSkipped() {
        // Caret in conbody whitespace: conbody/concept are not extractable
        assertNull(at("<concept id=\"c\"><conbody> | <p>x</p></conbody></concept>"));
        // Caret in the title: title is skipped, so is the root
        assertNull(at("<concept id=\"c\"><title>The T|itle</title></concept>"));
    }

    @Test
    void caretInTitleOfSection_findsSection() {
        // title is skipped but its parent section is extractable
        Located located = at("<concept id=\"c\"><conbody><section>"
                + "<title>S|ec</title><p>x</p></section></conbody></concept>");
        assertNotNull(located);
        assertEquals("section", located.element());
    }

    @Test
    void selfClosingElement() {
        Located located = at("<concept id=\"c\"><conbody>"
                + "<p><image href=\"a.png\" id=\"im|g\"/></p></conbody></concept>");
        assertNotNull(located);
        assertEquals("image", located.element());
        assertEquals("img", located.id());
    }

    @Test
    void commentsAndCdataIgnored() {
        Located located = at("<concept id=\"c\"><conbody><!-- <p>not this</p> -->"
                + "<codeblock id=\"cb\">x = \"<![CDATA[<p>]]>\"|;</codeblock>"
                + "</conbody></concept>");
        assertNotNull(located);
        assertEquals("codeblock", located.element());
        assertEquals("cb", located.id());
    }

    @Test
    void offsetOutsideAnyElement_null() {
        assertNull(ElementAtCaret.locate("<concept id=\"c\"/>", 0));
        assertNull(ElementAtCaret.locate("text only, no xml", 5));
        assertNull(ElementAtCaret.locate(null, 0));
    }

    @Test
    void chainListsAllExtractableAncestorsInnermostFirst() {
        String text = "<concept id=\"c\"><conbody><section><p><note>"
                + "<ph>de|ep</ph></note></p></section></conbody></concept>";
        int offset = text.indexOf('|');
        var chain = ElementAtCaret.locateChain(text.replace("|", ""), offset);

        assertEquals(java.util.List.of("ph", "note", "p", "section"),
                chain.stream().map(ElementAtCaret.Located::element).toList(),
                "innermost first, structural containers excluded");
    }

    @Test
    void chainSkipsContainersWithoutBreaking() {
        String text = "<concept id=\"c\"><conbody><section>"
                + "<title>S|ec</title><p>x</p></section></conbody></concept>";
        int offset = text.indexOf('|');
        var chain = ElementAtCaret.locateChain(text.replace("|", ""), offset);

        assertEquals(java.util.List.of("section"),
                chain.stream().map(ElementAtCaret.Located::element).toList(),
                "title is skipped; the chain continues at its parent");
    }

    @Test
    void nestedSameElementName() {
        Located located = at("<concept id=\"c\"><conbody><ul id=\"outer\">"
                + "<li><ul id=\"inner\"><li>de|ep</li></ul></li></ul>"
                + "</conbody></concept>");
        assertNotNull(located);
        assertEquals("li", located.element());
    }
}
