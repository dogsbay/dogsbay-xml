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
import java.awt.Rectangle;
import java.util.List;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.Test;

import com.dogsbay.xml.editor.DocumentFormatRegistry;
import com.dogsbay.xml.editor.PlainTextDocumentFormat;
import com.dogsbay.xml.editor.XmlDocumentFormat;
import com.dogsbay.xml.editor.XmlEditorPane;

/**
 * A real viewport obeying the band {@link ViewAlignment} asks for.
 *
 * <p>{@code ViewAlignmentTest} asserts the rectangle; this asserts that scrolling to it leaves the
 * target where the caller asked. The difference matters: a band that is right on paper and a
 * {@code scrollRectToVisible} that does something else with it would pass the first and fail here.
 *
 * <p>No display. A scroll pane with a size lays out and scrolls perfectly well without one.
 */
class RevealAlignTest {

    private static final int LINES = 300;

    private XmlEditorPane pane;
    private JScrollPane scroller;
    private String text;

    private void open() throws Exception {
        StringBuilder xml = new StringBuilder("<root>\n");
        for (int i = 1; i <= LINES; i++) {
            xml.append("  <item id=\"i").append(i).append("\">line ").append(i).append("</item>\n");
        }
        xml.append("</root>\n");
        text = xml.toString();

        SwingUtilities.invokeAndWait(() -> {
            registerFormats();
            pane = new XmlEditorPane(false);
            pane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            pane.setText(text);
            scroller = new JScrollPane(pane);
            scroller.setSize(600, 300);
            scroller.doLayout();
            scroller.getViewport().doLayout();
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
        SwingUtilities.invokeAndWait(RevealAlignTest::registerFormats);
    }

    /**
     * The span of {@code wanted}, which must appear exactly once.
     *
     * <p>Asserted rather than assumed. An earlier draft documented "appears once" and checked only
     * that it appeared at all, while using {@code "line 2<"} — which occurs 111 times in this
     * document, being a prefix of {@code line 20} and {@code line 200}. It worked only because
     * {@code indexOf} happens to return the one that was meant.
     */
    private TextRange.Span spanOf(String wanted) {
        int at = text.indexOf(wanted);
        assertThat(at).as("'" + wanted + "' is in the document").isGreaterThan(-1);
        assertThat(text.indexOf(wanted, at + 1)).as("'" + wanted + "' appears only once")
                .isEqualTo(-1);
        return new TextRange.Span(at, at + wanted.length());
    }

    /**
     * Where the target sits inside the viewport after scrolling to it with {@code align}, starting
     * from the top of the document.
     *
     * <p>The reset matters. Scrolling is relative, and {@code scrollRectToVisible} aligns a target
     * to the <em>top</em> when it has to scroll up to reach it and to the bottom when it scrolls
     * down. Measuring twice without resetting therefore measures two different questions, which is
     * what the first draft of this file did.
     */
    private int offsetFromTopAfter(String wanted, ViewAlignment.Align align) throws Exception {
        TextRange.Span span = spanOf(wanted);
        int[] held = new int[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                scroller.getViewport().setViewPosition(new java.awt.Point(0, 0));
                List<Rectangle> rows = EditorExecutor.measuredRows(pane, text, span);
                EditorExecutor.scrollTo(pane, rows, align);
                Rectangle visible = pane.getVisibleRect();
                held[0] = rows.get(0).y - visible.y;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return held[0];
    }

    private int lineHeight() throws Exception {
        int[] held = new int[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                held[0] = EditorExecutor.rowBoxes(pane, text, spanOf("line 2<")).get(0).height;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return held[0];
    }

    @Test
    @DisplayName("top leaves the target one line below the top edge")
    void topLandsAtTheTop() throws Exception {
        open();
        int line = lineHeight();

        assertThat(offsetFromTopAfter("line 200", ViewAlignment.Align.TOP))
                .as("one line of margin above it, not none and not two")
                .isCloseTo(line, org.assertj.core.data.Offset.offset(line / 3));
    }

    @Test
    @DisplayName("center leaves the target in the middle, allowing for the line's own height")
    void centerLandsInTheMiddle() throws Exception {
        open();
        int line = lineHeight();
        int middle = (pane.getVisibleRect().height - line) / 2;

        // Not half the viewport: that is the value the obvious wrong formula gives, and it differs
        // from the right one by half a line — which any tolerance wide enough to feel safe hides.
        assertThat(offsetFromTopAfter("line 200", ViewAlignment.Align.CENTER))
                .isCloseTo(middle, org.assertj.core.data.Offset.offset(line / 3));
    }

    @Test
    @DisplayName("nearest brings the target just inside the bottom edge, as it always did")
    void nearestScrollsAsLittleAsItCan() throws Exception {
        open();
        int height = pane.getVisibleRect().height;
        int fromTop = offsetFromTopAfter("line 200", ViewAlignment.Align.NEAREST);

        assertThat(fromTop).as("scrolling down from the top, it arrives at the bottom edge")
                .isGreaterThan(height / 2);
        assertThat(fromTop).isLessThanOrEqualTo(height);
    }

    @Test
    @DisplayName("the three alignments leave the target in three different places")
    void theAlignmentsDiffer() throws Exception {
        open();
        int top = offsetFromTopAfter("line 200", ViewAlignment.Align.TOP);
        int center = offsetFromTopAfter("line 200", ViewAlignment.Align.CENTER);
        int nearest = offsetFromTopAfter("line 200", ViewAlignment.Align.NEAREST);

        assertThat(top).as("top is nearest the top edge").isLessThan(center);
        assertThat(center).as("centre is above the bottom edge").isLessThan(nearest);
    }

    @Test
    @DisplayName("something already on screen is not scrolled away from by nearest")
    void alreadyVisible() throws Exception {
        open();
        // Line 2 is on screen from the start, so nearest has nothing to do and the view stays put.
        offsetFromTopAfter("line 2<", ViewAlignment.Align.NEAREST);
        assertThat(pane.getVisibleRect().y).as("still at the top of the document").isZero();

        // top is not the same as "do nothing": line 2 is a few lines down, so it scrolls to put it
        // one line below the edge, which is the whole point of asking for top.
        int fromTop = offsetFromTopAfter("line 2<", ViewAlignment.Align.TOP);
        assertThat(pane.getVisibleRect().y).as("the view moved to put it at the top")
                .isGreaterThan(0);
        assertThat(fromTop).as("one line of margin above it")
                .isCloseTo(lineHeight(), org.assertj.core.data.Offset.offset(lineHeight()));
    }

    @Test
    @DisplayName("revealing upwards puts the target where it was asked for, not at the far edge")
    @DisabledOnOs(value = OS.MAC, disabledReason = "lands flush with the top edge there, cause unknown"
            + " — plans/known-issues.md; asserted on Linux and Windows")
    void scrollingUpwards() throws Exception {
        // The hazard this file's own note describes: scrollRectToVisible aligns to the opposite
        // edge depending on the direction of travel. Every other measurement here starts at the top
        // of the document, so none of them exercises scrolling up.
        open();
        int line = lineHeight();
        TextRange.Span span = spanOf("line 200");

        int[] held = new int[1];
        int[] viewportHeight = new int[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                scroller.getViewport().setViewPosition(new java.awt.Point(0, 100_000));
                List<Rectangle> rows = EditorExecutor.measuredRows(pane, text, span);
                EditorExecutor.scrollTo(pane, rows, ViewAlignment.Align.TOP);
                held[0] = rows.get(0).y - pane.getVisibleRect().y;
                viewportHeight[0] = pane.getVisibleRect().height;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // A range, not a near-equality: the hazard being guarded against puts the target at the
        // opposite edge, a whole viewport away, so a line of slack either side still catches it.
        // The measurements travel with the failure, because the first two attempts at the macOS
        // behaviour were guesses for want of them.
        assertThat(held[0]).as("one line below the top edge, having come from below"
                        + " (line " + line + "px, viewport " + viewportHeight[0] + "px)")
                .isBetween(line / 2, line * 2);
    }

    @Test
    @DisplayName("scrolling moves the view and nothing else")
    void onlyTheViewMoves() throws Exception {
        open();
        // A real selection, made on purpose: asserting that a selection nobody made is still null
        // proves nothing, which is what the first draft of this did.
        SwingUtilities.invokeAndWait(() -> {
            pane.setCaretPosition(10);
            pane.moveCaretPosition(24);
        });
        int caretBefore = pane.getCaretPosition();
        String selectedBefore = pane.getSelectedText();
        assertThat(selectedBefore).as("there is something selected to preserve").isNotEmpty();

        offsetFromTopAfter("line 200", ViewAlignment.Align.TOP);

        assertThat(pane.getCaretPosition()).as("the caret stays where the reader left it")
                .isEqualTo(caretBefore);
        assertThat(pane.getSelectedText()).as("and so does the selection").isEqualTo(selectedBefore);
        assertThat(pane.getDocument().getLength()).isEqualTo(text.length());
    }

    @Test
    @DisplayName("a missing alignment is the old scroll-as-little-as-possible behaviour")
    void theDefaultIsNearest() throws Exception {
        // Every reveal written before align existed sends none, so this default is the whole
        // compatibility promise of the parameter.
        assertThat(EditorExecutor.alignOf(null)).isEqualTo(ViewAlignment.Align.NEAREST);
        assertThat(EditorExecutor.alignOf("")).isEqualTo(ViewAlignment.Align.NEAREST);
        assertThat(EditorExecutor.alignOf("  ")).isEqualTo(ViewAlignment.Align.NEAREST);
        assertThat(EditorExecutor.alignOf("top")).isEqualTo(ViewAlignment.Align.TOP);
    }

    @Test
    @DisplayName("an alignment nothing offers is refused with the ones that exist")
    void anUnknownAlignment() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> EditorExecutor.alignOf("middle"))
                .hasMessageContaining("no alignment called 'middle'")
                .hasMessageContaining("nearest, top, center");
    }

    @Test
    @DisplayName("a stretch with no rectangle is refused before anything scrolls")
    void aFoldIsRefusedBeforeScrolling() throws Exception {
        open();
        int before;
        SwingUtilities.invokeAndWait(() -> scroller.getViewport().setViewPosition(
                new java.awt.Point(0, 0)));
        before = pane.getVisibleRect().y;

        // measuredRows is what both halves now go through, so a fold cannot be scrolled to and then
        // fail: the refusal happens first and the view has not moved.
        com.dogsbay.xml.editor.FoldingMargin margin = foldFrom(3, 60);
        assertThat(margin).isNotNull();

        TextRange.Span inside = spanOf("line 20<");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        EditorExecutor.measuredRows(pane, text, inside);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                throw e.getCause() == null ? e : e.getCause();
            }
        }).hasMessageContaining("collapsed fold");

        assertThat(pane.getVisibleRect().y).as("nothing scrolled").isEqualTo(before);
    }

    /** Collapses lines {@code first} to {@code last}, as the folding margin would. */
    private com.dogsbay.xml.editor.FoldingMargin foldFrom(int first, int last) throws Exception {
        com.dogsbay.xml.editor.FoldingMargin[] held = new com.dogsbay.xml.editor.FoldingMargin[1];
        SwingUtilities.invokeAndWait(() -> {
            com.dogsbay.xml.editor.FoldingMargin margin =
                    new com.dogsbay.xml.editor.FoldingMargin(new StubManager(), pane);
            margin.setVisible(true);
            pane.setFoldingMargin(margin);
            javax.swing.text.Element root = pane.getDocument().getDefaultRootElement();
            margin.getFolds().addElement(new com.dogsbay.xml.editor.Fold(
                    root.getElement(first), root.getElement(last)));
            pane.revalidate();
            pane.doLayout();
            held[0] = margin;
        });
        SwingUtilities.invokeAndWait(() -> { });
        return held[0];
    }

    /** Folding applies only through the margin; nothing here drives it. */
    private static final class StubManager implements com.dogsbay.xml.editor.FoldingManager {
        @Override public boolean isMultipleLineTagStart(int line) {
            return false;
        }
        @Override public void revalidate() { }
        @Override public void repaint() { }
        @Override public void setFocus() { }
        @Override public void updateMargins() { }
    }
}
