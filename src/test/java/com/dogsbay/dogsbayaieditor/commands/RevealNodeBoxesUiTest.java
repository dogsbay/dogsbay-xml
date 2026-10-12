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
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.RangeHit;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.editor.DocumentFormatRegistry;
import com.dogsbay.xml.editor.PlainTextDocumentFormat;
import com.dogsbay.xml.editor.XmlDocumentFormat;
import com.dogsbay.xml.editor.XmlEditorPane;

/**
 * Revealing a node, as a typed {@link RangeHit}, in a realised window.
 *
 * <p>The one test in this feature that genuinely needs a display, and the reason is narrow:
 * {@link TextRange#rangeHit} offsets its boxes by {@code getLocationOnScreen()}, which throws for a
 * pane that is not in a realised window. So the answer {@code revealNode} actually returns cannot
 * be obtained offscreen, and without it nothing proves that revealing changes the answer at all —
 * that the boxes move, and that a node reported {@code hidden} becomes {@code visible}.
 *
 * <p>Everything else about alignment is asserted without a window, in {@code ViewAlignmentTest} and
 * {@code RevealAlignTest}.
 */
@Tag("ui")
@DisabledIf(value = "isHeadless", disabledReason = "the answer's boxes are in screen coordinates")
class RevealNodeBoxesUiTest {

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    @TempDir
    Path dir;

    private JFrame frame;
    private JScrollPane scroller;
    private XmlEditorPane pane;
    private DogsBayDocument document;

    private void open() throws Exception {
        StringBuilder xml = new StringBuilder("<topic id=\"t\">\n");
        for (int i = 1; i <= 200; i++) {
            xml.append("  <p id=\"p").append(i).append("\">paragraph ").append(i).append("</p>\n");
        }
        xml.append("  <title>Far Below</title>\n</topic>\n");

        Path file = dir.resolve("topic.xml");
        Files.writeString(file, xml.toString());
        document = new DogsBayDocument(file.toUri().toURL());
        document.load();

        SwingUtilities.invokeAndWait(() -> {
            registerFormats();
            pane = new XmlEditorPane(false);
            pane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            pane.setText(document.getText());
            scroller = new JScrollPane(pane);
            frame = new JFrame("reveal-node-ui-test");
            frame.setContentPane(scroller);
            frame.setSize(700, 300);
            frame.setVisible(true);
            frame.validate();
            scroller.doLayout();
            scroller.getViewport().doLayout();
            pane.doLayout();
            // Realising the frame leaves the view at the bottom of the document, so a node at the
            // end would be on screen before anything revealed it and the premise of these tests
            // would be false. Start at the top, as a reader opening the file does.
            pane.setCaretPosition(0);
            scroller.getViewport().setViewPosition(new java.awt.Point(0, 0));
        });
        SwingUtilities.invokeAndWait(() -> { });

        // The harness must actually clip, or every answer is "visible" and the test proves nothing.
        assertThat(pane.getVisibleRect().height)
                .as("the viewport constrains the pane")
                .isLessThan(pane.getHeight());
    }

    private static void registerFormats() {
        DocumentFormatRegistry.clear();
        DocumentFormatRegistry.register(new XmlDocumentFormat());
        PlainTextDocumentFormat plain = new PlainTextDocumentFormat();
        DocumentFormatRegistry.register(plain);
        DocumentFormatRegistry.setDefault(plain);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (frame != null) {
            SwingUtilities.invokeAndWait(() -> frame.dispose());
        }
        SwingUtilities.invokeAndWait(RevealNodeBoxesUiTest::registerFormats);
    }

    /** The span of a part of the node {@code select} names. */
    private TextRange.Span spanFor(String select, String part) throws Exception {
        String all = document.getText();
        Object node = EditorExecutor.oneNodeFor(
                new LocateNodeCommand(select, part, 0, Map.of()), document);
        TextRange.Outcome outcome = EditorExecutor.partOf(node, NodeParts.chosen(part), all);
        return ((TextRange.Outcome.Found) outcome).span();
    }

    /** What the command answers, with or without scrolling first. */
    private RangeHit answerFor(TextRange.Span span, ViewAlignment.Align align) throws Exception {
        RangeHit[] held = new RangeHit[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                List<Rectangle> rows = EditorExecutor.measuredRows(pane, document.getText(), span);
                if (align != null) {
                    EditorExecutor.scrollTo(pane, rows, align);
                }
                held[0] = TextRange.rangeHit(span, document.getText(), rows,
                        pane.getVisibleRect(), pane.getLocationOnScreen(), "w", 4000);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return held[0];
    }

    @Test
    @DisplayName("a node out of view is hidden with no boxes, and revealing it makes it visible")
    void revealingChangesTheAnswer() throws Exception {
        open();
        // A paragraph halfway down: far enough below the view to be hidden, far enough above the
        // end of the document for the margin to be deliverable.
        TextRange.Span middle = spanFor("//p[@id='p100']", "element");

        RangeHit before = answerFor(middle, null);
        assertThat(before.visibility()).as("ninety-nine paragraphs above it").isEqualTo("hidden");
        assertThat(before.boxes()).isEmpty();

        RangeHit after = answerFor(middle, ViewAlignment.Align.TOP);
        assertThat(after.visibility()).isEqualTo("visible");
        assertThat(after.boxes()).as("now there is something to draw").isNotEmpty();
        assertThat(after.text()).isEqualTo("<p id=\"p100\">paragraph 100</p>");
        assertThat(after.start()).as("the same stretch, differently placed").isEqualTo(before.start());
    }

    @Test
    @DisplayName("top puts the revealed node near the top of the pane, in screen coordinates")
    void topLandsNearTheTopOfTheWindow() throws Exception {
        open();
        TextRange.Span middle = spanFor("//p[@id='p100']", "element");

        RangeHit top = answerFor(middle, ViewAlignment.Align.TOP);
        // Measured from the VIEWPORT, not the pane. A scrolled pane's own origin sits above the
        // viewport, so subtracting it gives the view coordinate back rather than a screen offset.
        int viewportTop = scroller.getViewport().getLocationOnScreen().y;
        int lineHeight = top.boxes().get(0).height();

        assertThat(top.boxes().get(0).y() - viewportTop)
                .as("about one line below the top of the visible area")
                .isCloseTo(lineHeight, org.assertj.core.data.Offset.offset(lineHeight));
    }

    @Test
    @DisplayName("nearest and top leave the node in different places on screen")
    void theAnswersDifferByAlignment() throws Exception {
        open();
        TextRange.Span middle = spanFor("//p[@id='p100']", "element");

        int nearest = answerFor(middle, ViewAlignment.Align.NEAREST).boxes().get(0).y();
        open();
        int top = answerFor(middle, ViewAlignment.Align.TOP).boxes().get(0).y();

        assertThat(top).as("top is higher up the screen than the bottom edge nearest leaves it at")
                .isLessThan(nearest);
    }

    @Test
    @DisplayName("an attribute is boxed on its own after revealing")
    void anAttributeAfterRevealing() throws Exception {
        open();
        TextRange.Span attribute = spanFor("//p[@id='p100']/@id", "attribute");

        RangeHit hit = answerFor(attribute, ViewAlignment.Align.CENTER);
        assertThat(hit.visibility()).isEqualTo("visible");
        assertThat(hit.text()).isEqualTo("id=\"p100\"");
        assertThat(hit.boxes()).hasSize(1);
        assertThat(hit.boxes().get(0).centreX()).isGreaterThan(hit.boxes().get(0).x());
    }

    @Test
    @DisplayName("at the end of a document the margin is whatever is left, and nothing pretends otherwise")
    void theEndOfTheDocumentLimitsTheScroll() throws Exception {
        open();
        // There is nothing below the last element to scroll into view, so top cannot put it one
        // line from the edge. It is still revealed, and the answer reports where it really is —
        // which is the only honest thing it could say.
        TextRange.Span last = spanFor("//title", "element");

        RangeHit top = answerFor(last, ViewAlignment.Align.TOP);
        int viewportTop = scroller.getViewport().getLocationOnScreen().y;
        int fromTop = top.boxes().get(0).y() - viewportTop;

        assertThat(top.visibility()).as("revealed, even if not where asked").isEqualTo("visible");
        assertThat(fromTop).as("lower than the line of margin top would otherwise give")
                .isGreaterThan(top.boxes().get(0).height() * 2);
    }
}
