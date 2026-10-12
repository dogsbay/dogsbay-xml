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

import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.RangeHit;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.editor.DocumentFormatRegistry;
import com.dogsbay.xml.editor.PlainTextDocumentFormat;
import com.dogsbay.xml.editor.XmlDocumentFormat;
import com.dogsbay.xml.editor.XmlEditorPane;

/**
 * From an XPath expression to rectangles, as a typed {@link RangeHit}.
 *
 * <p>{@code LocateNodeTest} stops at the span and {@code RowBoxesTest} starts from one. Between
 * them sat the thing the command actually returns, and nothing asserted it — the same hole review
 * found for {@code locateRange}, while the coverage matrix claimed a tier defined as "asserted on
 * typed results" for both.
 *
 * <p>It composes exactly what the executor composes, minus the three things that need a running
 * editor window: resolving the view, bringing the model up to date, and the not-on-screen check.
 * No display — a pane with a size and a font is enough.
 */
class LocateNodeBoxesTest {

    private static final String XML = """
            <topic id="t">
              <title>Compression</title>
              <body>
                <p outputclass="lead">Audacity reads <b>many</b> formats.</p>
                <table><tgroup cols="2"/></table>
              </body>
            </topic>
            """;

    @TempDir
    Path dir;

    private XmlEditorPane pane;
    private DogsBayDocument document;

    /** The same text in a laid-out pane and in a parsed document, as the editor holds both. */
    private void open() throws Exception {
        Path file = dir.resolve("topic.xml");
        Files.writeString(file, XML);
        document = new DogsBayDocument(file.toUri().toURL());
        document.load();

        SwingUtilities.invokeAndWait(() -> {
            registerFormats();
            pane = new XmlEditorPane(false);
            pane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            pane.setText(document.getText());
            pane.setSize(900, 600);
            pane.doLayout();
        });
    }

    private static void registerFormats() {
        DocumentFormatRegistry.clear();
        DocumentFormatRegistry.register(new XmlDocumentFormat());
        PlainTextDocumentFormat plain = new PlainTextDocumentFormat();
        DocumentFormatRegistry.register(plain);
        DocumentFormatRegistry.setDefault(plain);
    }

    @AfterEach
    void putTheRegistryBack() throws Exception {
        SwingUtilities.invokeAndWait(LocateNodeBoxesTest::registerFormats);
    }

    /** The answer the command would give for {@code select} and {@code part}. */
    private RangeHit hitFor(String select, String part) throws Exception {
        String all = document.getText();
        Object node = EditorExecutor.oneNodeFor(
                new LocateNodeCommand(select, part, 0, Map.of()), document);
        TextRange.Outcome measured = EditorExecutor.partOf(node, NodeParts.chosen(part), all);
        assertThat(measured).as("refused: " + measured).isInstanceOf(TextRange.Outcome.Found.class);

        TextRange.Span span = ((TextRange.Outcome.Found) measured).span();
        TextRange.Outcome bounded = TextRange.resolve(span.start(), span.end(), all.length());
        assertThat(bounded).as("out of bounds: " + bounded).isInstanceOf(TextRange.Outcome.Found.class);
        TextRange.Span whole = TextRange.wholeCharacters(
                ((TextRange.Outcome.Found) bounded).span(), all);

        RangeHit[] held = new RangeHit[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                List<Rectangle> rows = EditorExecutor.rowBoxes(pane, all, whole);
                assertThat(rows).as("the pane placed the stretch").isNotNull();
                held[0] = TextRange.rangeHit(whole, all, rows, pane.getVisibleRect(),
                        new Point(0, 0), "window", 4000);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return held[0];
    }

    @Test
    @DisplayName("an element answers with its own source and a rectangle over it")
    void anElement() throws Exception {
        open();
        RangeHit hit = hitFor("//title", "element");

        assertThat(hit.text()).isEqualTo("<title>Compression</title>");
        assertThat(document.getText().substring(hit.start(), hit.end())).isEqualTo(hit.text());
        assertThat(hit.visibility()).isEqualTo("visible");
        assertThat(hit.boxes()).as("one line, one box").hasSize(1);
        assertThat(hit.boxes().get(0).width()).isGreaterThan(0);
        assertThat(hit.boxes().get(0).height()).isGreaterThan(0);
        assertThat(hit.window()).isEqualTo("window");
    }

    @Test
    @DisplayName("an attribute's value is boxed, narrower than the whole attribute and further along")
    void anAttributeValue() throws Exception {
        open();
        RangeHit value = hitFor("//tgroup/@cols", "value");
        RangeHit attribute = hitFor("//tgroup/@cols", "attribute");

        assertThat(value.text()).isEqualTo("2");
        assertThat(attribute.text()).isEqualTo("cols=\"2\"");
        assertThat(value.boxes().get(0).width()).isLessThan(attribute.boxes().get(0).width());
        assertThat(value.boxes().get(0).x()).isGreaterThan(attribute.boxes().get(0).x());
    }

    @Test
    @DisplayName("content holding markup is one box, because it is one line")
    void mixedContent() throws Exception {
        open();
        RangeHit hit = hitFor("//p[@outputclass]", "content");

        assertThat(hit.text()).isEqualTo("Audacity reads <b>many</b> formats.");
        assertThat(hit.boxes()).hasSize(1);
    }

    @Test
    @DisplayName("a node spanning lines is boxed once per line, top to bottom")
    void anElementOverSeveralLines() throws Exception {
        open();
        RangeHit hit = hitFor("//body", "element");

        assertThat(hit.text()).startsWith("<body>").endsWith("</body>");
        assertThat(hit.boxes()).as("body runs over four lines").hasSize(4);
        for (int i = 1; i < hit.boxes().size(); i++) {
            assertThat(hit.boxes().get(i).y()).isGreaterThan(hit.boxes().get(i - 1).y());
        }
    }

    @Test
    @DisplayName("the start tag and the end tag of one element are different rectangles")
    void tagsAreNotTheWholeElement() throws Exception {
        open();
        RangeHit start = hitFor("//body", "start-tag");
        RangeHit end = hitFor("//body", "end-tag");

        assertThat(start.text()).isEqualTo("<body>");
        assertThat(end.text()).isEqualTo("</body>");
        assertThat(end.boxes().get(0).y()).as("the end tag is further down the page")
                .isGreaterThan(start.boxes().get(0).y());
    }
}
