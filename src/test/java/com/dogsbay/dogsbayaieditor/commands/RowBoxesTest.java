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

import java.awt.Font;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dogsbay.xml.editor.DocumentFormat;
import com.dogsbay.xml.editor.DocumentFormatRegistry;
import com.dogsbay.xml.editor.PlainTextDocumentFormat;
import com.dogsbay.xml.editor.XmlDocumentFormat;
import com.dogsbay.xml.editor.Fold;
import com.dogsbay.xml.editor.FoldingManager;
import com.dogsbay.xml.editor.FoldingMargin;
import com.dogsbay.xml.editor.XmlEditorPane;

/**
 * Boxing a stretch row by row, over a laid-out pane.
 *
 * <p>No display and no window: {@code modelToView2D} needs a pane with a size and a font, not a
 * realised frame, so these run in the ordinary suite. The first draft of them was tagged
 * {@code ui} and paid for a display that bought nothing — the one behaviour that genuinely needs a
 * realised, foldable view is the collapsed-fold refusal, and that is still untested.
 *
 * <p>Monospaced throughout, so a box's width can be checked against a character count. That is the
 * assertion that matters: counting rows passes whether or not the boxes are the right shape, and the
 * first version of this file let a box a glyph too narrow, and a one-character box one pixel wide,
 * go by unnoticed.
 */
class RowBoxesTest {

    private XmlEditorPane pane;
    private String text;
    private FoldingMargin margin;

    /** Folding only applies through the margin; nothing here drives it. */
    private static final class StubManager implements FoldingManager {
        @Override public boolean isMultipleLineTagStart(int line) {
            return false;
        }
        @Override public void revalidate() { }
        @Override public void repaint() { }
        @Override public void setFocus() { }
        @Override public void updateMargins() { }
    }

    /** Lays out {@code content} in a pane {@code width} wide, with no frame. */
    private void layOut(String content, boolean wrapped, int width) throws Exception {
        text = content;
        SwingUtilities.invokeAndWait(() -> {
            DocumentFormatRegistry.clear();
            DocumentFormatRegistry.register(new XmlDocumentFormat());
            PlainTextDocumentFormat plain = new PlainTextDocumentFormat();
            DocumentFormatRegistry.register(plain);
            DocumentFormatRegistry.setDefault(plain);

            pane = new XmlEditorPane(wrapped);
            pane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            pane.setText(content);
            pane.setSize(width, 400);
            margin = new FoldingMargin(new StubManager(), pane);
            // EditorPanel drives this from the preference; folding applies only while it is shown.
            margin.setVisible(true);
            pane.setFoldingMargin(margin);
            pane.doLayout();
        });
    }

    /** Collapses the lines {@code first} to {@code last}, as the folding margin would. */
    private void collapse(int first, int last) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            javax.swing.text.Element root = pane.getDocument().getDefaultRootElement();
            margin.getFolds().addElement(new Fold(root.getElement(first), root.getElement(last)));
            pane.revalidate();
            pane.doLayout();
        });
        SwingUtilities.invokeAndWait(() -> { });
    }

    private int startOfLine(int line) {
        return pane.getDocument().getDefaultRootElement().getElement(line).getStartOffset();
    }

    @AfterEach
    void putTheRegistryBack() throws Exception {
        // It is global, and a test that empties it breaks whatever runs next in the same JVM.
        SwingUtilities.invokeAndWait(() -> {
            DocumentFormatRegistry.clear();
            DocumentFormatRegistry.register(new XmlDocumentFormat());
            DocumentFormat plain = new PlainTextDocumentFormat();
            DocumentFormatRegistry.register(plain);
            DocumentFormatRegistry.setDefault(plain);
        });
    }

    private List<Rectangle> boxesFor(int start, int end) throws Exception {
        List<List<Rectangle>> held = new ArrayList<>();
        List<Exception> failed = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                held.add(EditorExecutor.rowBoxes(pane, text, new TextRange.Span(start, end)));
            } catch (Exception e) {
                failed.add(e);
            }
        });
        if (!failed.isEmpty()) {
            throw failed.get(0);
        }
        return held.get(0);
    }

    /**
     * How wide the pane's own font makes {@code part}.
     *
     * <p>Not a character count times a character width: a monospaced font's advance is fractional,
     * so five characters are not five times one character to the pixel. The view measures with these
     * same metrics, so this is the number to compare against — within a pixel or two for rounding.
     */
    private int widthOf(String part) {
        return pane.getFontMetrics(pane.getFont()).stringWidth(part);
    }

    private static final int ROUNDING = 3;

    @Test
    @DisplayName("one character is boxed one character wide, not one pixel")
    void oneCharacterIsAGlyphWide() throws Exception {
        // modelToView2D answers a 1-pixel rectangle at a character's near edge, so a box built from
        // two of them is a glyph short — and for a single character it was 1 pixel wide.
        layOut("<p>abcde</p>\n", false, 800);
        List<Rectangle> boxes = boxesFor(3, 4);

        assertThat(boxes).hasSize(1);
        assertThat(boxes.get(0).width).as("a glyph wide, not a pixel")
                .isCloseTo(widthOf("a"), org.assertj.core.data.Offset.offset(ROUNDING));
        assertThat(boxes.get(0).width).isGreaterThan(1);
    }

    @Test
    @DisplayName("five characters are boxed five characters wide")
    void aStretchIsAsWideAsItsCharacters() throws Exception {
        layOut("<p>abcde</p>\n", false, 800);
        List<Rectangle> boxes = boxesFor(3, 8);

        assertThat(boxes).hasSize(1);
        assertThat(boxes.get(0).width).as("the width of 'abcde', not of 'abcd'")
                .isCloseTo(widthOf("abcde"), org.assertj.core.data.Offset.offset(ROUNDING));
    }

    @Test
    @DisplayName("a box starts at the stretch, not at the start of the row")
    void theBoxStartsWhereAskedNotAtTheMargin() throws Exception {
        layOut("<p>abcde</p>\n", false, 800);
        Rectangle fromThree = boxesFor(3, 5).get(0);
        Rectangle fromZero = boxesFor(0, 2).get(0);

        assertThat(fromThree.x - fromZero.x).as("three characters further along the row")
                .isCloseTo(widthOf("<p>"), org.assertj.core.data.Offset.offset(ROUNDING));
    }

    @Test
    @DisplayName("a stretch over three lines is one box per line, top to bottom")
    void aBoxPerLine() throws Exception {
        String content = "<a>\n<b>one</b>\n<c>two</c>\n</a>\n";
        layOut(content, false, 800);
        // To the end, so the third line is inside the stretch rather than just after it.
        List<Rectangle> boxes = boxesFor(content.indexOf("<b>"), content.length());

        assertThat(boxes).hasSize(3);
        for (int i = 1; i < boxes.size(); i++) {
            assertThat(boxes.get(i).y).as("row " + i + " below row " + (i - 1))
                    .isGreaterThan(boxes.get(i - 1).y);
        }
    }

    @Test
    @DisplayName("a line break is not drawn, so it adds no width to its row")
    void aLineBreakIsNotBoxed() throws Exception {
        String content = "<b>one</b>\n<c>two</c>\n";
        layOut(content, false, 800);
        // "one</b>" then the newline: the first row's box must stop at the '>' before it.
        List<Rectangle> boxes = boxesFor(3, content.indexOf("<c>"));

        assertThat(boxes).hasSize(1);
        assertThat(boxes.get(0).width).as("as wide as 'one</b>', with nothing added for the break")
                .isCloseTo(widthOf("one</b>"), org.assertj.core.data.Offset.offset(ROUNDING));
    }

    @Test
    @DisplayName("a line the view wraps is boxed once per visual row, not once per newline")
    void wrappingGivesMoreBoxesThanNewlines() throws Exception {
        String content = "<p>" + "the same words over and over ".repeat(12) + "</p>\n";
        assertThat(content.substring(3, content.indexOf("</p>")))
                .as("the stretch holds no newline, so only wrapping can split it")
                .doesNotContain("\n");
        layOut(content, true, 320);
        List<Rectangle> boxes = boxesFor(3, content.indexOf("</p>"));

        assertThat(boxes.size()).as("one box per wrapped row").isGreaterThan(1);
        for (int i = 1; i < boxes.size(); i++) {
            assertThat(boxes.get(i).y).as("row " + i + " strictly below row " + (i - 1))
                    .isGreaterThan(boxes.get(i - 1).y);
        }
    }

    @Test
    @DisplayName("a stretch ending at the last character of the document is boxed")
    void rightUpToTheEnd() throws Exception {
        String content = "<p>abc</p>";
        layOut(content, false, 800);
        List<Rectangle> boxes = boxesFor(3, content.length());

        assertThat(boxes).hasSize(1);
        assertThat(boxes.get(0).width)
                .isCloseTo(widthOf(content.substring(3)), org.assertj.core.data.Offset.offset(ROUNDING));
    }

    @Test
    @DisplayName("more rows than anything draws is refused rather than slowly answered")
    void theRowCapIsRefused() throws Exception {
        String content = "<a>\n" + "<b>x</b>\n".repeat(500) + "</a>\n";
        layOut(content, false, 800);

        assertThatThrownBy(() -> boxesFor(0, content.length()))
                .hasMessageContaining("more than 400 lines")
                .hasMessageContaining("smaller part");
    }

    @Test
    @DisplayName("a stretch inside a collapsed fold has no boxes at all")
    void aCollapsedFoldHasNoRectangle() throws Exception {
        // The one behaviour here that a laid-out pane alone cannot show: modelToView answers null
        // inside a fold, so there is nowhere to draw and the caller is told to expand it. Scrolling
        // cannot cure this, which is why it is a refusal rather than "hidden".
        StringBuilder content = new StringBuilder("<root>\n");
        for (int i = 1; i <= 20; i++) {
            content.append("  <item id=\"i").append(i).append("\"/>\n");
        }
        content.append("</root>\n");
        layOut(content.toString(), false, 800);
        collapse(3, 13);

        int inside = startOfLine(7);
        assertThat(boxesFor(inside, inside + 5))
                .as("no rectangle for a stretch the fold has hidden")
                .isNull();

        // And the wording the caller meets for that, which TextRange decides.
        assertThat(TextRange.noRectangleMessage(new TextRange.Span(inside, inside + 5),
                pane.getVisibleRect()))
                .contains("collapsed fold").contains("Expand it first");
    }

    @Test
    @DisplayName("a stretch outside the fold is still boxed while the fold is collapsed")
    void aboveACollapsedFoldIsUnaffected() throws Exception {
        StringBuilder content = new StringBuilder("<root>\n");
        for (int i = 1; i <= 20; i++) {
            content.append("  <item id=\"i").append(i).append("\"/>\n");
        }
        content.append("</root>\n");
        layOut(content.toString(), false, 800);
        collapse(3, 13);

        assertThat(boxesFor(1, 5)).as("the first line is still drawn").hasSize(1);
    }

    @Test
    @DisplayName("a long line is boxed without walking it character by character")
    void aLongLineIsNotQuadratic() throws Exception {
        // Utilities.getRowEnd measures once per character, and each measurement re-measures the line
        // from its start: 32,000 characters took 3.4 seconds. Bisection makes it about a dozen
        // measurements a row. The bound is loose on purpose — it is here to catch a return to
        // quadratic, not to police milliseconds.
        String content = "<p>" + "x".repeat(200_000) + "</p>\n";
        layOut(content, false, 800);

        long started = System.nanoTime();
        List<Rectangle> boxes = boxesFor(3, 200_003);
        long took = (System.nanoTime() - started) / 1_000_000;

        assertThat(boxes).hasSize(1);
        assertThat(took).as("200,000 characters in " + took + "ms").isLessThan(2_000L);
    }
}
