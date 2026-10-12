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

package com.dogsbay.dogsbayaieditor.commands;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The part arithmetic, against positions a real parse produced.
 *
 * <p>The numbers here are not invented: they were read out of {@code XElement} and
 * {@code XAttribute} after loading the two documents below, and every expectation is stated as the
 * text the stretch should cover rather than as an offset. An invented number proves the arithmetic
 * agrees with itself; a measured one proves it agrees with the parser.
 */
class NodePartsTest {

    /** Positions measured from this: root [0,89] content [6,81]; p [9,59] content [31,54]; @id [12,20]. */
    private static final String DOC =
            "<root>\n  <p id=\"one\" class=\"x\">hello <b>there</b> world</p>\n  <empty/>\n  <q>a</q>\n</root>\n";

    /**
     * And from this: e [3,19] content [3,19]; @a [6,11]; @b [12,17]; x [19,26] content [22,21];
     * y [26,34] content [29,29]; n:m [34,62] content [55,55].
     */
    private static final String ODD =
            "<r><e a=\"1\" b='2'/><x></x><y>z</y><n:m xmlns:n=\"urn:n\">q</n:m></r>\n";

    private static String covered(TextRange.Outcome outcome, String text) {
        assertThat(outcome).as("refused: " + outcome).isInstanceOf(TextRange.Outcome.Found.class);
        TextRange.Span span = ((TextRange.Outcome.Found) outcome).span();
        return text.substring(span.start(), span.end());
    }

    private static String refusal(TextRange.Outcome outcome) {
        assertThat(outcome).isInstanceOf(TextRange.Outcome.Refused.class);
        return ((TextRange.Outcome.Refused) outcome).message();
    }

    /** The <p> of DOC. */
    private static TextRange.Outcome p(NodeParts.Part part) {
        return NodeParts.ofElement(part, 9, 59, 31, 54, "p");
    }

    @Test
    @DisplayName("an element is its start tag through its end tag")
    void wholeElement() {
        assertThat(covered(p(NodeParts.Part.ELEMENT), DOC))
                .isEqualTo("<p id=\"one\" class=\"x\">hello <b>there</b> world</p>");
    }

    @Test
    @DisplayName("the content is everything between the tags, last character included")
    void content() {
        // The trap: a content end is inclusive, so the naive reading loses the final 'd'.
        assertThat(covered(p(NodeParts.Part.CONTENT), DOC)).isEqualTo("hello <b>there</b> world");
    }

    @Test
    @DisplayName("each tag on its own")
    void tags() {
        assertThat(covered(p(NodeParts.Part.START_TAG), DOC)).isEqualTo("<p id=\"one\" class=\"x\">");
        assertThat(covered(p(NodeParts.Part.END_TAG), DOC)).isEqualTo("</p>");
    }

    @Test
    @DisplayName("an element's name is the name alone, without the angle bracket")
    void elementName() {
        assertThat(covered(p(NodeParts.Part.NAME), DOC)).isEqualTo("p");
        assertThat(covered(NodeParts.ofElement(NodeParts.Part.NAME, 0, 89, 6, 81, "root"), DOC))
                .isEqualTo("root");
    }

    @Test
    @DisplayName("a prefixed name includes its prefix")
    void qualifiedElementName() {
        assertThat(covered(NodeParts.ofElement(NodeParts.Part.NAME, 34, 62, 55, 55, "n:m"), ODD))
                .isEqualTo("n:m");
        // And its content, with the same measured numbers, to keep them honest: 54 is the '>' of
        // the start tag, and an earlier draft of this file had it.
        assertThat(covered(NodeParts.ofElement(NodeParts.Part.CONTENT, 34, 62, 55, 55, "n:m"), ODD))
                .isEqualTo("q");
    }

    @Test
    @DisplayName("a nested element is measured the same way as its parent")
    void nestedElement() {
        assertThat(covered(NodeParts.ofElement(NodeParts.Part.ELEMENT, 37, 49, 40, 44, "b"), DOC))
                .isEqualTo("<b>there</b>");
        assertThat(covered(NodeParts.ofElement(NodeParts.Part.CONTENT, 37, 49, 40, 44, "b"), DOC))
                .isEqualTo("there");
    }

    // ── the shapes that report their positions oddly ────────────────────

    /** <empty/> of DOC, which reports its content as the element itself. */
    private static TextRange.Outcome empty(NodeParts.Part part) {
        return NodeParts.ofElement(part, 62, 70, 62, 70, "empty");
    }

    @Test
    @DisplayName("a self-closing element is its own start tag")
    void selfClosingIsItsOwnStartTag() {
        assertThat(covered(empty(NodeParts.Part.ELEMENT), DOC)).isEqualTo("<empty/>");
        assertThat(covered(empty(NodeParts.Part.START_TAG), DOC)).isEqualTo("<empty/>");
    }

    @Test
    @DisplayName("a self-closing element has no content and no end tag, and is told so")
    void selfClosingHasNoContent() {
        // Its content positions equal the element's, so the plain reading would box the whole tag
        // and call it the content.
        assertThat(refusal(empty(NodeParts.Part.CONTENT)))
                .contains("one empty tag").contains("no content").contains("'element'");
        assertThat(refusal(empty(NodeParts.Part.END_TAG)))
                .contains("no end").contains("start-tag");
    }

    @Test
    @DisplayName("an empty pair reports its content backwards, and is told so")
    void anEmptyPairHasNoContent() {
        // <x></x> reports content 22 to 21. Honest — there is no character between the tags — but
        // nothing can draw it.
        TextRange.Outcome outcome = NodeParts.ofElement(NodeParts.Part.CONTENT, 19, 26, 22, 21, "x");
        assertThat(refusal(outcome)).contains("nothing between its tags");
    }

    @Test
    @DisplayName("an empty pair still has both its tags")
    void anEmptyPairKeepsItsTags() {
        assertThat(covered(NodeParts.ofElement(NodeParts.Part.START_TAG, 19, 26, 22, 21, "x"), ODD))
                .isEqualTo("<x>");
        assertThat(covered(NodeParts.ofElement(NodeParts.Part.END_TAG, 19, 26, 22, 21, "x"), ODD))
                .isEqualTo("</x>");
    }

    @Test
    @DisplayName("content of a single character is not lost")
    void oneCharacterOfContent() {
        // <y>z</y> reports content 29 to 29: with an exclusive reading that is nothing at all.
        assertThat(covered(NodeParts.ofElement(NodeParts.Part.CONTENT, 26, 34, 29, 29, "y"), ODD))
                .isEqualTo("z");
    }

    // ── attributes ──────────────────────────────────────────────────────

    @Test
    @DisplayName("an attribute is its name, its quotes and what is between them")
    void wholeAttribute() {
        assertThat(covered(NodeParts.ofAttribute(NodeParts.Part.ATTRIBUTE, 12, 20, "id", DOC), DOC))
                .isEqualTo("id=\"one\"");
    }

    @Test
    @DisplayName("an attribute's name and value are each on their own")
    void attributeNameAndValue() {
        assertThat(covered(NodeParts.ofAttribute(NodeParts.Part.NAME, 12, 20, "id", DOC), DOC))
                .isEqualTo("id");
        assertThat(covered(NodeParts.ofAttribute(NodeParts.Part.VALUE, 12, 20, "id", DOC), DOC))
                .isEqualTo("one");
    }

    @Test
    @DisplayName("a single-quoted value is found by its own quote")
    void singleQuotedValue() {
        assertThat(covered(NodeParts.ofAttribute(NodeParts.Part.VALUE, 12, 17, "b", ODD), ODD))
                .isEqualTo("2");
        assertThat(covered(NodeParts.ofAttribute(NodeParts.Part.VALUE, 6, 11, "a", ODD), ODD))
                .isEqualTo("1");
    }

    @Test
    @DisplayName("an empty value has nothing to point at, and says which parts do")
    void anEmptyValue() {
        // a="" spans [3,7): the end is one past the closing quote, as @a of ODD showed.
        String text = "<e a=\"\"/>";
        assertThat(refusal(NodeParts.ofAttribute(NodeParts.Part.VALUE, 3, 7, "a", text)))
                .contains("no value between quotes").contains("'name'");
    }

    // ── asking for the wrong kind of part ───────────────────────────────

    @Test
    @DisplayName("an element part asked of an attribute says which parts an attribute has")
    void elementPartOfAnAttribute() {
        assertThat(refusal(NodeParts.ofAttribute(NodeParts.Part.CONTENT, 12, 20, "id", DOC)))
                .contains("part of an element").contains("attribute id")
                .contains("'attribute', 'name' or 'value'");
    }

    @Test
    @DisplayName("an attribute part asked of an element says how to select the attribute")
    void attributePartOfAnElement() {
        assertThat(refusal(p(NodeParts.Part.VALUE)))
                .contains("part of an attribute").contains("<p>").contains("@name");
    }

    @Test
    @DisplayName("text is pointed at the part that does what the caller wants")
    void textOfAnElement() {
        assertThat(refusal(p(NodeParts.Part.TEXT))).contains("'content'");
    }

    // ── a node the text does not contain ────────────────────────────────

    @Test
    @DisplayName("an attribute a DTD supplies is refused, not measured from -1")
    void anAttributeWithNoSourceText() {
        // Positions start at -1 and are stamped while the text is parsed, so an attribute defaulted
        // by a DTD keeps the -1. In DITA that is @class on essentially every element, and the
        // unguarded reading asked for substring(-1, -1).
        for (NodeParts.Part part : NodeParts.Part.values()) {
            assertThat(refusal(NodeParts.ofAttribute(part, -1, -1, "class", DOC)))
                    .as("part " + part.wire())
                    .contains("not written in this document");
        }
    }

    @Test
    @DisplayName("an element the parser never placed is refused for every part")
    void anElementWithNoSourceText() {
        for (NodeParts.Part part : NodeParts.Part.values()) {
            assertThat(refusal(NodeParts.ofElement(part, -1, -1, -1, -1, "p")))
                    .as("part " + part.wire())
                    .contains("not written in this document");
        }
    }

    @Test
    @DisplayName("an element never placed is not mistaken for a self-closing one")
    void unplacedIsNotSelfClosing() {
        // contentStart == elementStart is how a self-closing element is known, and -1 == -1 is true,
        // so the plain reading told the caller a paired element had no end tag.
        assertThat(refusal(NodeParts.ofElement(NodeParts.Part.END_TAG, -1, -1, -1, -1, "p")))
                .doesNotContain("empty tag");
        assertThat(refusal(NodeParts.ofElement(NodeParts.Part.NAME, -1, -1, -1, -1, "p")))
                .as("NAME would otherwise box the first characters of the document, silently")
                .contains("not written in this document");
    }

    @Test
    @DisplayName("an attribute end past the document is refused rather than read")
    void anAttributeBeyondTheText() {
        assertThat(refusal(NodeParts.ofAttribute(NodeParts.Part.ATTRIBUTE, 12, 9999, "id", DOC)))
                .contains("not written in this document");
    }

    @Test
    @DisplayName("an attribute not ending in a quote says why its value cannot be told apart")
    void anAttributeWithNoQuotes() {
        // Reachable only from positions that disagree with the text, but it is the one branch that
        // decides where a value starts, so it should not be a crash.
        String text = "<e a=1/>";
        assertThat(refusal(NodeParts.ofAttribute(NodeParts.Part.VALUE, 3, 6, "a", text)))
                .contains("does not end in a quote").contains("'attribute'");
    }

    // ── the remaining wrong-kind pairings ───────────────────────────────

    @Test
    @DisplayName("every element part asked of an attribute names the attribute's own parts")
    void allElementPartsOfAnAttribute() {
        for (NodeParts.Part part : new NodeParts.Part[] { NodeParts.Part.ELEMENT,
                NodeParts.Part.START_TAG, NodeParts.Part.END_TAG, NodeParts.Part.CONTENT }) {
            assertThat(refusal(NodeParts.ofAttribute(part, 12, 20, "id", DOC)))
                    .as("part " + part.wire())
                    .contains("part of an element").contains("'attribute', 'name' or 'value'");
        }
    }

    @Test
    @DisplayName("text asked of an attribute points at its value")
    void textOfAnAttribute() {
        assertThat(refusal(NodeParts.ofAttribute(NodeParts.Part.TEXT, 12, 20, "id", DOC)))
                .contains("text node").contains("'value'");
    }

    @Test
    @DisplayName("asking for an element's attribute part names how to select one")
    void attributePartOfAnElementByName() {
        assertThat(refusal(p(NodeParts.Part.ATTRIBUTE)))
                .contains("part of an attribute").contains("@name");
    }

    @Test
    @DisplayName("a prefixed attribute's name includes its prefix")
    void prefixedAttributeName() {
        // Measured: n:k="v" spans [22,29] in this text.
        String text = "<r xmlns:n=\"urn:n\"><e n:k=\"v\"/></r>\n";
        assertThat(covered(NodeParts.ofAttribute(NodeParts.Part.NAME, 22, 29, "n:k", text), text))
                .isEqualTo("n:k");
        assertThat(covered(NodeParts.ofAttribute(NodeParts.Part.VALUE, 22, 29, "n:k", text), text))
                .isEqualTo("v");
    }

    // ── naming the parts ────────────────────────────────────────────────

    @Test
    @DisplayName("naming no part means the element")
    void theDefaultPart() {
        assertThat(NodeParts.chosen(null)).isEqualTo(NodeParts.Part.ELEMENT);
        assertThat(NodeParts.chosen("")).isEqualTo(NodeParts.Part.ELEMENT);
        assertThat(NodeParts.chosen("   ")).isEqualTo(NodeParts.Part.ELEMENT);
        assertThat(NodeParts.chosen("content")).isEqualTo(NodeParts.Part.CONTENT);
        assertThat(NodeParts.chosen("tag")).as("not a part").isNull();
    }

    @Test
    @DisplayName("a part nothing has is refused with the ones that exist")
    void theUnknownPartRefusal() {
        assertThat(NodeParts.unknownPart("tag"))
                .contains("no part called 'tag'")
                .contains("element, start-tag, end-tag, content, name, attribute, value, text");
    }

    @Test
    @DisplayName("part names are the hyphenated ones a caller writes")
    void partNames() {
        assertThat(NodeParts.Part.of("start-tag")).isEqualTo(NodeParts.Part.START_TAG);
        assertThat(NodeParts.Part.of("  END-TAG ")).isEqualTo(NodeParts.Part.END_TAG);
        assertThat(NodeParts.Part.of("start_tag")).as("an underscore is forgiven")
                .isEqualTo(NodeParts.Part.START_TAG);
        assertThat(NodeParts.Part.of("tag")).isNull();
        assertThat(NodeParts.Part.of(null)).isNull();
        assertThat(NodeParts.Part.names())
                .isEqualTo("element, start-tag, end-tag, content, name, attribute, value, text");
    }
}
