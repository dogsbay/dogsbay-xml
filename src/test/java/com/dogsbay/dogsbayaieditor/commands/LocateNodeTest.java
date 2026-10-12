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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.xml.DogsBayDocument;

/**
 * Finding a node by XPath and measuring a part of it, against a real parse.
 *
 * <p>{@link NodePartsTest} checks the arithmetic against positions that were measured once and
 * written down. This checks the whole path on a document the parser has just read, so the two
 * cannot drift: if the parser ever changes what it stamps, these fail rather than the numbers in
 * that file quietly becoming fiction.
 *
 * <p>No window: everything up to the rectangles is the tree's work.
 */
class LocateNodeTest {

    private static final String XML = """
            <topic id="t">
              <title>Compression</title>
              <body>
                <p outputclass="lead">Audacity reads <b>many</b> formats.</p>
                <table><tgroup cols="2"><colspec colname="c1"/><colspec colname="c2"/></tgroup></table>
                <note type="tip"/>
                <p></p>
              </body>
            </topic>
            """;

    @TempDir
    Path dir;

    private DogsBayDocument document;

    @BeforeEach
    void load() throws Exception {
        Path file = dir.resolve("topic.xml");
        Files.writeString(file, XML);
        document = new DogsBayDocument(file.toUri().toURL());
        document.load();
    }

    /** What the named part of the first match covers, in the document's own text. */
    private String covered(String select, String part) throws Exception {
        return covered(select, part, 0);
    }

    private String covered(String select, String part, int nth) throws Exception {
        String text = document.getText();
        Object node = EditorExecutor.oneNodeFor(
                new LocateNodeCommand(select, part, nth, Map.of()), document);
        TextRange.Outcome outcome = EditorExecutor.partOf(node, NodeParts.Part.of(part), text);
        assertThat(outcome).as("refused: " + outcome).isInstanceOf(TextRange.Outcome.Found.class);
        TextRange.Span span = ((TextRange.Outcome.Found) outcome).span();
        return text.substring(span.start(), span.end());
    }

    /**
     * The refusal {@code partOf} answers with — the node was found, the part was not measurable.
     *
     * <p>Separate from {@link #thrownFor}, because a refusal and a thrown command exception are
     * different outcomes and a helper that returned either could not tell them apart. An earlier
     * draft did, and four tests that read as refusals were exercising the throwing path.
     */
    private String refusalFor(String select, String part, int nth) throws Exception {
        String text = document.getText();
        Object node = EditorExecutor.oneNodeFor(
                new LocateNodeCommand(select, part, nth, Map.of()), document);
        TextRange.Outcome outcome = EditorExecutor.partOf(node, NodeParts.Part.of(part), text);
        assertThat(outcome).as("expected a refusal, got " + outcome)
                .isInstanceOf(TextRange.Outcome.Refused.class);
        return ((TextRange.Outcome.Refused) outcome).message();
    }

    /** The message {@code oneNodeFor} throws — the node could not be found at all. */
    private String thrownFor(String select, int nth) {
        return thrownFor(select, nth, Map.of());
    }

    private String thrownFor(String select, int nth, Map<String, String> namespaces) {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> EditorExecutor.oneNodeFor(
                        new LocateNodeCommand(select, "element", nth, namespaces), document));
        assertThat(thrown).as("expected the node lookup to fail").isNotNull();
        return thrown.getMessage();
    }

    @Test
    @DisplayName("an element, its tags and its content, as the parser placed them")
    void theParsersPositionsAddUp() throws Exception {
        assertThat(covered("//title", "element")).isEqualTo("<title>Compression</title>");
        assertThat(covered("//title", "start-tag")).isEqualTo("<title>");
        assertThat(covered("//title", "end-tag")).isEqualTo("</title>");
        assertThat(covered("//title", "content")).isEqualTo("Compression");
        assertThat(covered("//title", "name")).isEqualTo("title");
    }

    @Test
    @DisplayName("content holding markup comes back whole, last character and all")
    void mixedContent() throws Exception {
        assertThat(covered("//p[@outputclass]", "content"))
                .isEqualTo("Audacity reads <b>many</b> formats.");
    }

    @Test
    @DisplayName("a node is found whether or not a part was asked for")
    void theLookupIgnoresThePart() throws Exception {
        // oneNodeFor reads select, nth and namespaces and never the part. Which part a missing
        // 'part' means is decided in the executor and asserted in LocateNodePartsTest; a test that
        // passed null here and then handed ELEMENT to partOf itself proved only that.
        for (String part : new String[] { null, "element", "value" }) {
            Object node = EditorExecutor.oneNodeFor(
                    new LocateNodeCommand("//title", part, 0, Map.of()), document);
            assertThat(node).as("part " + part).isInstanceOf(com.dogsbay.xml.XElement.class);
        }
    }

    @Test
    @DisplayName("an attribute, its name and its value")
    void attributeParts() throws Exception {
        assertThat(covered("//tgroup/@cols", "attribute")).isEqualTo("cols=\"2\"");
        assertThat(covered("//tgroup/@cols", "name")).isEqualTo("cols");
        assertThat(covered("//tgroup/@cols", "value")).isEqualTo("2");
    }

    @Test
    @DisplayName("a self-closing element is its own start tag, and has no content")
    void selfClosing() throws Exception {
        assertThat(covered("//note", "element")).isEqualTo("<note type=\"tip\"/>");
        assertThat(covered("//note", "start-tag")).isEqualTo("<note type=\"tip\"/>");
        assertThat(covered("//note/@type", "value")).isEqualTo("tip");
        assertThat(refusalFor("//note", "content", 0)).contains("one empty tag");
        assertThat(refusalFor("//note", "end-tag", 0)).contains("no end");
    }

    @Test
    @DisplayName("an element whose tags hold nothing has no content to box")
    void emptyPair() throws Exception {
        assertThat(covered("//p[not(@outputclass)]", "element")).isEqualTo("<p></p>");
        assertThat(refusalFor("//p[not(@outputclass)]", "content", 0))
                .contains("nothing between its tags");
    }

    // ── choosing among matches ──────────────────────────────────────────

    @Test
    @DisplayName("several matches are refused with how many, and nth takes one in document order")
    void severalMatches() throws Exception {
        assertThat(thrownFor("//colspec", 0))
                .contains("matches 2 nodes").contains("'nth'").contains("document order");
        assertThat(covered("//colspec", "element", 1)).isEqualTo("<colspec colname=\"c1\"/>");
        assertThat(covered("//colspec", "element", 2)).isEqualTo("<colspec colname=\"c2\"/>");
        assertThat(covered("//colspec/@colname", "value", 2)).isEqualTo("c2");
    }

    @Test
    @DisplayName("a number past the end says how many there were")
    void nthPastTheEnd() {
        assertThat(thrownFor("//colspec", 5)).contains("matches 2 nodes")
                .contains("no number 5");
    }

    @Test
    @DisplayName("nothing matching says so, with the expression that found nothing")
    void noMatch() {
        assertThat(thrownFor("//shortdesc", 0))
                .contains("Nothing in this document matches").contains("//shortdesc");
    }

    @Test
    @DisplayName("an expression that is not XPath is refused as that, not as an internal error")
    void notAnExpression() {
        assertThat(thrownFor("//[broken", 0)).contains("not an XPath expression");
    }

    // ── nodes the editor does not place ─────────────────────────────────

    @Test
    @DisplayName("a text node has no recorded position, and the refusal says what to ask instead")
    void aTextNode() throws Exception {
        assertThat(refusalFor("//title/text()", "text", 0))
                .contains("a text node").contains("'content'").contains("locate");
    }

    @Test
    @DisplayName("asking for an attribute's part of an element names the way to select it")
    void wrongKindOfPart() throws Exception {
        assertThat(refusalFor("//title", "value", 0)).contains("part of an attribute").contains("@name");
    }

    // ── a node with no source text ──────────────────────────────────────

    @Test
    @DisplayName("an attribute a DTD defaults is refused rather than measured from -1")
    void aDtdDefaultedAttribute() throws Exception {
        // The DITA case: @class and @domains are fixed by the DTD on essentially every element, so
        // they are in the tree with no source text and their positions stay at -1. Asking for one
        // is an ordinary request, and the unguarded reading asked for substring(-1, -1).
        Path file = dir.resolve("defaulted.xml");
        Files.writeString(file, """
                <!DOCTYPE r [<!ELEMENT r (#PCDATA)><!ATTLIST r k CDATA "v">]>
                <r>x</r>
                """);
        DogsBayDocument defaulted = new DogsBayDocument(file.toUri().toURL());
        defaulted.load();

        Object node = EditorExecutor.oneNodeFor(
                new LocateNodeCommand("//r/@k", "attribute", 0, Map.of()), defaulted);
        assertThat(node).as("the DTD default is in the tree")
                .isInstanceOf(com.dogsbay.xml.XAttribute.class);

        for (String part : new String[] { "attribute", "name", "value" }) {
            TextRange.Outcome outcome = EditorExecutor.partOf(
                    node, NodeParts.Part.of(part), defaulted.getText());
            assertThat(outcome).as("part " + part).isInstanceOf(TextRange.Outcome.Refused.class);
            assertThat(((TextRange.Outcome.Refused) outcome).message()).as("part " + part)
                    .contains("not written in this document");
        }
    }

    @Test
    @DisplayName("an expression answering a number, not a node, is refused by kind")
    void anExpressionThatIsNotANodeSet() throws Exception {
        // count(...) and string(...) come back through selectNodes as a list holding a Double or a
        // String, so this is reachable from any client.
        Object node = EditorExecutor.oneNodeFor(
                new LocateNodeCommand("count(//p)", "element", 0, Map.of()), document);
        assertThat(node).isNotInstanceOf(com.dogsbay.xml.XElement.class);

        TextRange.Outcome outcome = EditorExecutor.partOf(node, NodeParts.Part.ELEMENT, document.getText());
        assertThat(((TextRange.Outcome.Refused) outcome).message())
                .as("it says what the expression answered with, not just that it was odd")
                .contains("answers with a number").contains("Select the node itself");
    }

    @Test
    @DisplayName("the document itself points at its root element")
    void theDocumentNode() throws Exception {
        Object node = EditorExecutor.oneNodeFor(
                new LocateNodeCommand("/", "element", 0, Map.of()), document);
        TextRange.Outcome outcome = EditorExecutor.partOf(
                node, NodeParts.Part.ELEMENT, document.getText());
        assertThat(((TextRange.Outcome.Refused) outcome).message())
                .contains("the document itself").contains("'/*'");
    }

    @Test
    @DisplayName("a CDATA section is named as one, not lumped in with text")
    void aCdataSection() throws Exception {
        Path file = dir.resolve("cdata.xml");
        Files.writeString(file, "<r><![CDATA[raw < stuff]]></r>\n");
        DogsBayDocument cdata = new DogsBayDocument(file.toUri().toURL());
        cdata.load();

        Object node = EditorExecutor.oneNodeFor(
                new LocateNodeCommand("//text()", "text", 0, Map.of()), cdata);
        TextRange.Outcome outcome = EditorExecutor.partOf(
                node, NodeParts.Part.TEXT, cdata.getText());
        assertThat(((TextRange.Outcome.Refused) outcome).message()).contains("CDATA section");
    }

    @Test
    @DisplayName("a comment is named as one")
    void aComment() throws Exception {
        Path file = dir.resolve("comment.xml");
        Files.writeString(file, "<r><!-- note --><p>x</p></r>\n");
        DogsBayDocument commented = new DogsBayDocument(file.toUri().toURL());
        commented.load();

        Object node = EditorExecutor.oneNodeFor(
                new LocateNodeCommand("//comment()", "element", 0, Map.of()), commented);
        TextRange.Outcome outcome = EditorExecutor.partOf(
                node, NodeParts.Part.ELEMENT, commented.getText());
        assertThat(((TextRange.Outcome.Refused) outcome).message()).contains("a comment");
    }

    // ── namespaces ──────────────────────────────────────────────────────

    @Test
    @DisplayName("a prefixed expression works when the prefix is given a URI")
    void prefixWithAUri() throws Exception {
        Path file = dir.resolve("ns.xml");
        Files.writeString(file, "<r xmlns:n=\"urn:n\"><n:m>q</n:m></r>\n");
        DogsBayDocument ns = new DogsBayDocument(file.toUri().toURL());
        ns.load();

        Object node = EditorExecutor.oneNodeFor(
                new LocateNodeCommand("//n:m", "element", 0, Map.of("n", "urn:n")), ns);
        TextRange.Outcome outcome = EditorExecutor.partOf(node, NodeParts.Part.ELEMENT, ns.getText());
        TextRange.Span span = ((TextRange.Outcome.Found) outcome).span();
        assertThat(ns.getText().substring(span.start(), span.end())).isEqualTo("<n:m>q</n:m>");
    }

    @Test
    @DisplayName("a prefix the document declares needs no URI from the caller")
    void aDocumentsOwnPrefixJustWorks() throws Exception {
        // dom4j resolves a prefix against the document's own declarations, so the common case needs
        // no 'namespaces' at all. Worth pinning: it is the reason that parameter is optional.
        Path file = dir.resolve("ns2.xml");
        Files.writeString(file, "<r xmlns:n=\"urn:n\"><n:m>q</n:m></r>\n");
        DogsBayDocument ns = new DogsBayDocument(file.toUri().toURL());
        ns.load();

        Object node = EditorExecutor.oneNodeFor(
                new LocateNodeCommand("//n:m", "element", 0, Map.of()), ns);
        TextRange.Outcome outcome = EditorExecutor.partOf(node, NodeParts.Part.ELEMENT, ns.getText());
        TextRange.Span span = ((TextRange.Outcome.Found) outcome).span();
        assertThat(ns.getText().substring(span.start(), span.end())).isEqualTo("<n:m>q</n:m>");
    }

    @Test
    @DisplayName("a prefix nothing declares is refused rather than silently matching nothing")
    void anUndeclaredPrefix() throws Exception {
        Path file = dir.resolve("ns3.xml");
        Files.writeString(file, "<r xmlns:n=\"urn:n\"><n:m>q</n:m></r>\n");
        DogsBayDocument ns = new DogsBayDocument(file.toUri().toURL());
        ns.load();

        // Not just the expression: all three failure paths quote that, including the no-match
        // refusal this test exists to rule out. The guidance is what tells them apart.
        assertThatThrownBy(() -> EditorExecutor.oneNodeFor(
                new LocateNodeCommand("//zz:m", "element", 0, Map.of()), ns))
                .as("an unknown prefix")
                .hasMessageContaining("namespaces")
                .hasMessageContaining("zz:m");
    }
}
