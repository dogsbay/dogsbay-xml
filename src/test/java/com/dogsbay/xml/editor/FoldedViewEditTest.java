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

package com.dogsbay.xml.editor;

import java.awt.GraphicsEnvironment;
import java.awt.geom.Rectangle2D;
import java.util.Vector;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Position;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Editing a soft-wrapped document that has a collapsed fold.
 *
 * <p>This is the case {@code EditorViewLayoutTest} cannot reach, and its absence
 * let a crash through review once already: deleting {@code FoldingBoxView} (see
 * {@code plans/sun-code-investigation.md}, stage 2) reintroduced an
 * {@code ArrayIndexOutOfBoundsException} here while the whole suite stayed green.
 *
 * <p>Why it needs a real {@link FoldingMargin}: folding is expressed by
 * {@code FoldingWrappedPlainView.getViewCount()} returning the <em>visible</em>
 * line count while {@code replace()} is called with <em>element</em>-space
 * indices. {@code BoxView} sizes its layout arrays from {@code getViewCount()},
 * so the mismatch only bites when a fold is actually collapsed AND the document
 * is edited at a line beyond the visible count — or when a fold is expanded after
 * such an edit. With no margin installed every fold-aware override degenerates to
 * the identity and none of this is exercised.
 */
@org.junit.jupiter.api.Tag("ui")
@DisabledIf(value = "isHeadless", disabledReason = "needs a display")
class FoldedViewEditTest {

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    /** Enough lines that a fold can hide many and still leave plenty below. */
    private static final int LINES = 40;

    private JFrame frame;
    private XmlEditorPane pane;
    private FoldingMargin margin;

    /** The margin only needs these callbacks; none affect the layout maths. */
    private static final class StubManager implements FoldingManager {
        @Override public boolean isMultipleLineTagStart(int line) throws BadLocationException {
            return false;
        }
        @Override public void revalidate() { }
        @Override public void repaint() { }
        @Override public void setFocus() { }
        @Override public void updateMargins() { }
    }

    @BeforeEach
    void setUp() throws Exception {
        DocumentFormatRegistry.clear();
        DocumentFormatRegistry.register(new XmlDocumentFormat());
        PlainTextDocumentFormat plainText = new PlainTextDocumentFormat();
        DocumentFormatRegistry.register(plainText);
        DocumentFormatRegistry.setDefault(plainText);

        StringBuilder xml = new StringBuilder("<root>\n");
        for (int i = 1; i < LINES - 1; i++) {
            // Long enough to wrap in the 360px frame: the reported artifact showed
            // overstruck text on wrapped paragraph lines.
            xml.append("  <p id=\"i").append(i)
               .append("\">Select a region in the waveform and press Delete to remove it; ")
               .append("the surrounding audio closes the gap in paragraph ").append(i)
               .append(".</p>\n");
        }
        xml.append("</root>");

        SwingUtilities.invokeAndWait(() -> {
            pane = new XmlEditorPane(true);   // wrapped: the BoxView chain
            pane.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,
                    java.awt.Font.PLAIN, 12));
            pane.setText(xml.toString());
            pane.setCaretPosition(0);
            // A blinking caret makes two paints of the same state differ by the
            // width of the caret bar, which is enough to fail a pixel comparison.
            pane.getCaret().setBlinkRate(0);
            pane.getCaret().setVisible(false);

            margin = new FoldingMargin(new StubManager(), pane);
            // EditorPanel drives this from the preference; folding only applies
            // while the margin is shown, so a hidden one would exercise nothing.
            margin.setVisible(true);
            pane.setFoldingMargin(margin);

            frame = new JFrame("folded-edit-test");
            frame.setContentPane(new JScrollPane(pane));
            frame.setSize(360, 300);
            frame.setVisible(true);
        });
        SwingUtilities.invokeAndWait(() -> { });
    }

    @AfterEach
    void tearDown() throws Exception {
        if (frame != null) {
            SwingUtilities.invokeAndWait(frame::dispose);
            frame = null;
        }
    }

    /** Collapses lines {@code start}..{@code end} by hand, as the margin's UI would. */
    private void collapse(int start, int end) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Element root = pane.getDocument().getDefaultRootElement();
            Vector folds = margin.getFolds();
            folds.addElement(new Fold(root.getElement(start), root.getElement(end)));
            pane.revalidate();
            pane.repaint();
        });
        SwingUtilities.invokeAndWait(() -> { });
    }

    private int visibleLineCount() {
        return FoldMapping.visibleLineCount(
                pane.getDocument().getDefaultRootElement().getElementCount(),
                margin.getFolds());
    }

    @Test
    void collapsingReducesTheVisibleLineCount() throws Exception {
        int before = visibleLineCount();

        collapse(2, 12);

        assertEquals(before - 9, visibleLineCount(), "10 lines between 2 and 12, minus none");
    }

    /**
     * Folding must actually take effect in the <em>layout</em>, not merely in the
     * reported preferred span.
     *
     * <p>Asserting on {@code getPreferredSpan} alone is not enough: that is
     * overridden separately, so it stays correct even if the layout does not
     * collapse — a mutant that skipped the zeroing passed such a test while the
     * view rendered at full height. Measuring where a line after the fold
     * actually lands goes through the child allocations, which is the thing that
     * matters.
     */
    @Test
    void collapsingMovesLaterLinesUp() throws Exception {
        Element root = pane.getDocument().getDefaultRootElement();
        int belowOffset = root.getElement(20).getStartOffset();
        double[] before = new double[1];

        SwingUtilities.invokeAndWait(() -> {
            try {
                before[0] = pane.modelToView2D(belowOffset).getY();
            } catch (Exception e) {
                fail(e);
            }
        });

        collapse(2, 12);

        SwingUtilities.invokeAndWait(() -> {
            try {
                pane.revalidate();
                pane.setSize(pane.getWidth(), pane.getHeight());   // force re-layout
                double after = pane.modelToView2D(belowOffset).getY();

                assertTrue(after < before[0],
                        "line 20 must rise once 9 lines above it collapse: "
                                + after + " vs " + before[0]);
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    /**
     * Hidden lines must not be painted at all.
     *
     * <p>Zero-height children still get their turn from {@code BoxView}, and a
     * text view draws happily into a zero-height rectangle — so every hidden line
     * rendered at the fold boundary, stacking the whole collapsed body onto one
     * row of unreadable overstruck glyphs. Reported from the running editor after
     * a single click on a fold marker with soft wrapping on.
     */
    /** Paints the pane onto a white surface. */
    private java.awt.image.BufferedImage render(XmlEditorPane target) throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                360, 300, java.awt.image.BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            java.awt.Graphics2D g = img.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, 360, 300);
            target.paint(g);
            g.dispose();
        });
        return img;
    }

    /**
     * A collapsed fold must render exactly like a document that simply does not
     * contain the hidden lines.
     *
     * <p>This is the assertion that catches overstriking, and it took three
     * attempts to get right. Counting total ink fails, because collapsing removes
     * rows and the total falls either way. Counting the densest row fails too:
     * overstruck text covers the same <em>width</em> as normal text, so the count
     * of non-background pixels barely moves — only the darkness does. Comparing
     * against the document you would expect to see is what actually discriminates.
     *
     * <p>The bug: zero-height children still get their turn from {@code BoxView},
     * and a text view draws into the rectangle it is handed regardless of its
     * height, so every hidden line rendered at the fold boundary. Reported from
     * the running editor after a single click on a fold marker, soft wrapping on.
     */
    @Test
    void aCollapsedFoldRendersLikeADocumentWithoutThoseLines() throws Exception {
        int foldStart = 2;
        int foldEnd = 34;

        String[] lines = pane.getText().split("\n", -1);
        StringBuilder visibleOnly = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            // A fold hides the lines strictly between its ends.
            if (i <= foldStart || i >= foldEnd) {
                visibleOnly.append(lines[i]).append('\n');
            }
        }

        collapse(foldStart, foldEnd);
        SwingUtilities.invokeAndWait(() -> {
            pane.revalidate();
            pane.setSize(pane.getWidth(), pane.getHeight());
        });
        java.awt.image.BufferedImage folded = render(pane);

        // The same visible text, with nothing hidden — the reference.
        XmlEditorPane[] plain = new XmlEditorPane[1];
        SwingUtilities.invokeAndWait(() -> {
            plain[0] = new XmlEditorPane(true);
            plain[0].setFont(pane.getFont());
            plain[0].setText(visibleOnly.toString());
            plain[0].setCaretPosition(0);
            plain[0].getCaret().setBlinkRate(0);
            plain[0].getCaret().setVisible(false);
            plain[0].setSize(pane.getSize());
        });
        java.awt.image.BufferedImage reference = render(plain[0]);

        int differing = 0;
        for (int y = 0; y < 300; y++) {
            for (int x = 0; x < 360; x++) {
                if (folded.getRGB(x, y) != reference.getRGB(x, y)) {
                    differing++;
                }
            }
        }
        // Measures ~17 (two different components, so a small baseline is expected).
        // A row of this fixture is ~500 ink pixels, so 400 would have tolerated most
        // of a misdrawn line; 60 keeps real headroom over the observed noise.
        assertTrue(differing < 60,
                differing + " pixels differ from a document containing only the "
                        + "visible lines — the fold is rendering hidden content");
    }

    /**
     * Collapsing must shrink the <em>component</em>, not just the view.
     *
     * <p>{@code JEditorPane.getPreferredSize()} clamps its preferred height up to
     * the UI's minimum, and {@code BoxView} derives that minimum from every child
     * including hidden ones — so the view can lay out correctly while the
     * component keeps its unfolded height, leaving the scrollbar range unchanged
     * and dead space below the text. Asserting on the view alone missed this.
     */
    @Test
    void collapsingShrinksTheComponentNotJustTheView() throws Exception {
        int[] before = new int[2];
        SwingUtilities.invokeAndWait(() -> {
            before[0] = pane.getPreferredSize().height;
            before[1] = pane.getUI().getMinimumSize(pane).height;
        });

        collapse(2, 34);
        SwingUtilities.invokeAndWait(() -> {
            pane.revalidate();
            pane.setSize(pane.getWidth(), pane.getHeight());
        });

        int[] after = new int[2];
        SwingUtilities.invokeAndWait(() -> {
            after[0] = pane.getPreferredSize().height;
            after[1] = pane.getUI().getMinimumSize(pane).height;
        });

        assertTrue(after[1] < before[1],
                "the UI minimum height must shrink (" + before[1] + " -> " + after[1]
                        + ") or getPreferredSize clamps back to the unfolded height");
        assertTrue(after[0] < before[0],
                "the component's preferred height must shrink: " + before[0]
                        + " -> " + after[0]);
    }

    /**
     * Arrow keys must cross a collapsed fold without throwing.
     *
     * <p>Swing routes vertical movement through
     * {@code Utilities.getPositionAbove/Below}, which dereference
     * {@code modelToView} with no null check — and this view returns null inside a
     * fold. The old implementation hid the resulting NPE behind a blanket
     * {@code catch (Throwable)}; nothing tested it either way.
     */
    @Test
    void verticalCaretMovementCrossesAFoldWithoutThrowing() throws Exception {
        collapse(2, 34);
        Element root = pane.getDocument().getDefaultRootElement();
        java.awt.Rectangle alloc = new java.awt.Rectangle(0, 0, 360, 4000);

        SwingUtilities.invokeAndWait(() -> {
            javax.swing.text.View view = pane.getUI().getRootView(pane).getView(0);
            Position.Bias[] bias = new Position.Bias[1];

            assertDoesNotThrow(() -> {
                // Down from the fold's start line, and up from the line after it.
                view.getNextVisualPositionFrom(root.getElement(2).getStartOffset(),
                        Position.Bias.Forward, alloc, javax.swing.SwingConstants.SOUTH, bias);
                view.getNextVisualPositionFrom(root.getElement(34).getStartOffset(),
                        Position.Bias.Forward, alloc, javax.swing.SwingConstants.NORTH, bias);
            });
        });
    }

    /** Moving down from the fold's start line must land on the line after it. */
    @Test
    void movingDownOutOfAFoldSkipsTheHiddenLines() throws Exception {
        collapse(2, 34);
        Element root = pane.getDocument().getDefaultRootElement();
        java.awt.Rectangle alloc = new java.awt.Rectangle(0, 0, 360, 4000);

        SwingUtilities.invokeAndWait(() -> {
            javax.swing.text.View view = pane.getUI().getRootView(pane).getView(0);
            Position.Bias[] bias = new Position.Bias[1];
            try {
                int next = view.getNextVisualPositionFrom(
                        root.getElement(2).getStartOffset(), Position.Bias.Forward,
                        alloc, javax.swing.SwingConstants.SOUTH, bias);

                assertFalse(FoldMapping.isHidden(root.getElementIndex(next), margin.getFolds()),
                        "the caret must never land inside a collapsed fold");
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    /**
     * The stage-2 crash, scenario A: edit at an element line beyond the visible
     * count. The layout arrays must be wide enough for element indices, not just
     * visible ones.
     */
    @Test
    void editingBelowACollapsedFoldDoesNotThrow() throws Exception {
        collapse(2, 12);
        Element root = pane.getDocument().getDefaultRootElement();
        int visible = visibleLineCount();
        assertTrue(LINES > visible + 2, "fixture must have lines beyond the visible count");

        // A line whose element index exceeds the visible line count.
        int line = visible + 2;
        int offset = root.getElement(line).getStartOffset();

        assertDoesNotThrow(() -> SwingUtilities.invokeAndWait(() -> {
            try {
                pane.getDocument().insertString(offset, "<new/>\n", null);
            } catch (BadLocationException e) {
                fail(e);
            }
        }));
    }

    /**
     * The stage-2 crash, scenario B: expand a fold after an edit made while
     * folded. {@code FoldingMargin.unfold()} only revalidates, so the layout
     * arrays must already be sized for the restored line count.
     */
    @Test
    void expandingAfterAnEditDoesNotThrow() throws Exception {
        collapse(2, 12);
        Element root = pane.getDocument().getDefaultRootElement();
        int offset = root.getElement(visibleLineCount() + 2).getStartOffset();

        SwingUtilities.invokeAndWait(() -> {
            try {
                pane.getDocument().insertString(offset, "<new/>\n", null);
            } catch (BadLocationException e) {
                fail(e);
            }
        });

        assertDoesNotThrow(() -> SwingUtilities.invokeAndWait(() -> {
            margin.getFolds().removeAllElements();   // unfold
            pane.setSize(pane.getWidth() + 1, pane.getHeight());
            pane.revalidate();
            pane.paint(pane.getGraphics() != null ? pane.getGraphics()
                    : new java.awt.image.BufferedImage(360, 300,
                            java.awt.image.BufferedImage.TYPE_INT_RGB).createGraphics());
        }));
    }

    /**
     * Positions either side of a collapsed fold must still map to increasing
     * y-coordinates — the fold collapses the gap, it does not invert the order.
     */
    @Test
    void modelToViewStaysMonotoneAcrossAFold() throws Exception {
        collapse(2, 12);
        Element root = pane.getDocument().getDefaultRootElement();

        SwingUtilities.invokeAndWait(() -> {
            try {
                Rectangle2D above = pane.modelToView2D(root.getElement(1).getStartOffset());
                Rectangle2D below = pane.modelToView2D(root.getElement(13).getStartOffset());

                assertNotNull(above);
                assertNotNull(below);
                assertTrue(below.getY() > above.getY(),
                        "a line after the fold must sit below one before it");
            } catch (Exception e) {
                fail(e);
            }
        });
    }
}
