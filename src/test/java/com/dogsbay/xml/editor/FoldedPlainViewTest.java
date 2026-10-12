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

import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Vector;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterisation tests for the <em>unwrapped</em> folding view.
 *
 * <p>{@code FoldingPlainView} is the busiest view in the editor — XML, Markdown,
 * AsciiDoc, JSON and YAML all extend it — and it is one of the two remaining
 * files carrying Sun's proprietary header. These tests pin its observable
 * behaviour <em>before</em> it is reimplemented against the public
 * {@code PlainView} API (stage 3 of {@code plans/sun-code-investigation.md}), so
 * the replacement has a fixed reference.
 *
 * <p>Companion to {@code FoldedViewEditTest}, which covers the wrapped chain.
 * Deliberately geometric rather than pixel-exact, for the reason recorded in that
 * plan: the modern JDK lays text out with fractional precision the 2002 fork
 * lacks, so exact pixel expectations would enshrine the worse behaviour.
 */
@org.junit.jupiter.api.Tag("ui")
@DisabledIf(value = "isHeadless", disabledReason = "needs a display")
class FoldedPlainViewTest {

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    private static final int LINES = 30;

    private JFrame frame;
    private XmlEditorPane pane;
    private FoldingMargin margin;

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
            xml.append("  <item id=\"i").append(i).append("\"/>\n");
        }
        xml.append("</root>");

        SwingUtilities.invokeAndWait(() -> {
            pane = new XmlEditorPane(false);   // unwrapped: the PlainView chain
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

            frame = new JFrame("folded-plainview-test");
            frame.setContentPane(new JScrollPane(pane));
            frame.setSize(420, 400);
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

    /**
     * Paints the editor's <em>view</em> onto a white surface.
     *
     * <p>Deliberately not {@code pane.paint(g)}: that also paints the caret, whose
     * visibility varies with focus in a shared UI-test JVM and differs by exactly
     * one caret bar between otherwise identical renders. The view is what these
     * tests are about.
     */
    private java.awt.image.BufferedImage renderView() throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                400, 300, java.awt.image.BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            Graphics2D g = img.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, 400, 300);
            javax.swing.text.View root = pane.getUI().getRootView(pane);
            root.paint(g, new java.awt.Rectangle(0, 0, 400, 300));
            g.dispose();
        });
        return img;
    }

    private void collapse(int start, int end) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Element root = pane.getDocument().getDefaultRootElement();
            margin.getFolds().addElement(
                    new Fold(root.getElement(start), root.getElement(end)));
            pane.revalidate();
        });
        SwingUtilities.invokeAndWait(() -> { });
    }

    private int startOffsetOfLine(int line) {
        return pane.getDocument().getDefaultRootElement()
                .getElement(line).getStartOffset();
    }

    // ── Preferred height tracks visible lines ───────────────────────────

    @Test
    void collapsingShortensThePreferredHeight() throws Exception {
        int[] before = new int[1];
        SwingUtilities.invokeAndWait(() -> before[0] = pane.getPreferredSize().height);

        collapse(3, 13);   // hides 9 lines

        int[] after = new int[1];
        SwingUtilities.invokeAndWait(() -> after[0] = pane.getPreferredSize().height);

        assertTrue(after[0] < before[0],
                "height must shrink: " + after[0] + " vs " + before[0]);
        // Take the line height from the font, not from the delta being asserted.
        // Deriving it from (before - after) / 9 made this a test that only checked
        // the delta was divisible by 9 — hiding 18 lines would have passed too.
        int[] lineHeight = new int[1];
        SwingUtilities.invokeAndWait(() ->
                lineHeight[0] = pane.getFontMetrics(pane.getFont()).getHeight());
        assertEquals(before[0] - (9 * lineHeight[0]), after[0],
                "height must drop by exactly the 9 hidden lines");
    }

    // ── Geometry across a fold ──────────────────────────────────────────

    @Test
    void aLineAfterTheFoldMovesUpByTheHiddenLines() throws Exception {
        double[] before = new double[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                before[0] = pane.modelToView2D(startOffsetOfLine(20)).getY();
            } catch (Exception e) {
                fail(e);
            }
        });

        collapse(3, 13);

        SwingUtilities.invokeAndWait(() -> {
            try {
                Rectangle2D after = pane.modelToView2D(startOffsetOfLine(20));

                assertTrue(after.getY() < before[0],
                        "line 20 must rise once 9 lines above it are hidden");
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void visibleLinesRemainInOrderAcrossTheFold() throws Exception {
        collapse(3, 13);

        SwingUtilities.invokeAndWait(() -> {
            try {
                double above = pane.modelToView2D(startOffsetOfLine(2)).getY();
                double atStart = pane.modelToView2D(startOffsetOfLine(3)).getY();
                double atEnd = pane.modelToView2D(startOffsetOfLine(13)).getY();
                double below = pane.modelToView2D(startOffsetOfLine(14)).getY();

                assertTrue(atStart > above, "the fold's start line stays visible, below line 2");
                assertTrue(atEnd > atStart, "the fold's end line stays visible");
                assertTrue(below > atEnd, "and the line after it follows");
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    /**
     * A hidden position has no on-screen rectangle, and this view answers with
     * {@code null} rather than throwing. Pinned because it is unusual — Swing
     * callers generally expect non-null — and any reimplementation must make the
     * same choice or fix every caller.
     */
    @Test
    void aHiddenPositionHasNoRectangle() throws Exception {
        collapse(3, 13);

        SwingUtilities.invokeAndWait(() -> {
            try {
                javax.swing.text.View root = pane.getUI().getRootView(pane);
                javax.swing.text.View view = root.getView(0);

                assertNull(view.modelToView(startOffsetOfLine(7),
                                new java.awt.Rectangle(0, 0, 400, 400),
                                javax.swing.text.Position.Bias.Forward),
                        "line 7 is inside the collapsed fold");
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void clickingBelowTheFoldLandsAfterIt() throws Exception {
        collapse(3, 13);

        SwingUtilities.invokeAndWait(() -> {
            try {
                Rectangle2D box = pane.modelToView2D(startOffsetOfLine(14));
                int offset = pane.viewToModel2D(new Point2D.Double(
                        box.getX() + 1, box.getY() + box.getHeight() / 2));

                assertFalse(FoldMapping.isHidden(
                                pane.getDocument().getDefaultRootElement()
                                        .getElementIndex(offset),
                                margin.getFolds()),
                        "a click must never land on a hidden line");
                assertEquals(14, pane.getDocument().getDefaultRootElement()
                        .getElementIndex(offset));
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    // ── Editing and painting ────────────────────────────────────────────

    @Test
    void editingBelowACollapsedFoldDoesNotThrow() throws Exception {
        collapse(3, 13);

        assertDoesNotThrow(() -> SwingUtilities.invokeAndWait(() -> {
            try {
                pane.getDocument().insertString(startOffsetOfLine(20), "<new/>\n", null);
            } catch (BadLocationException e) {
                fail(e);
            }
        }));
    }

    @Test
    void paintsInkWithAFoldCollapsed() throws Exception {
        collapse(3, 13);
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);

        SwingUtilities.invokeAndWait(() -> {
            Graphics2D g = image.createGraphics();
            pane.paint(g);
            g.dispose();
        });

        int background = image.getRGB(1, 1);
        boolean painted = false;
        for (int y = 0; y < image.getHeight() && !painted; y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                if (image.getRGB(x, y) != background) {
                    painted = true;
                    break;
                }
            }
        }
        assertTrue(painted, "the folded view produced a blank image");
    }

    /**
     * Selection must survive the rewrite's own paint loop. PlainView keeps the
     * bounds it draws with private to its own paint, so a view that overrides
     * paint has to split the line itself — this test caught that being missed.
     */
    @Test
    void selectionIsPaintedDifferentlyFromPlainText() throws Exception {
        BufferedImage plain = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        BufferedImage selected = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);

        SwingUtilities.invokeAndWait(() -> {
            Graphics2D g = plain.createGraphics();
            pane.paint(g);
            g.dispose();

            pane.setSelectionStart(0);
            pane.setSelectionEnd(startOffsetOfLine(3));

            Graphics2D g2 = selected.createGraphics();
            pane.paint(g2);
            g2.dispose();
        });

        boolean differs = false;
        for (int y = 0; y < plain.getHeight() && !differs; y++) {
            for (int x = 0; x < plain.getWidth(); x++) {
                if (plain.getRGB(x, y) != selected.getRGB(x, y)) {
                    differs = true;
                    break;
                }
            }
        }
        assertTrue(differs, "selecting the first three lines changed nothing on screen");
    }

    /**
     * Reported: after collapsing a fold, several lines appeared drawn on top of
     * one another — stale glyphs from the pre-fold layout left behind under the
     * newly positioned text.
     *
     * <p>Renders the same fold state two ways: reached by collapsing a live view,
     * and set up from scratch. They must be pixel-identical; any difference is
     * content the repaint failed to clear.
     */
    @Test
    void collapsingRepaintsCleanlyWithNoStaleGlyphs() throws Exception {
        BufferedImage afterCollapse = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        BufferedImage fromScratch = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);

        // 1. paint unfolded, then collapse and paint again into the same buffer,
        //    exactly as the screen would be reused.
        SwingUtilities.invokeAndWait(() -> {
            Graphics2D g = afterCollapse.createGraphics();
            javax.swing.text.View root = pane.getUI().getRootView(pane);
            root.paint(g, new java.awt.Rectangle(0, 0, 400, 300));
            g.dispose();
        });
        collapse(3, 13);
        SwingUtilities.invokeAndWait(() -> {
            pane.revalidate();
            pane.setSize(pane.getWidth(), pane.getHeight());
            Graphics2D g = afterCollapse.createGraphics();
            javax.swing.text.View root = pane.getUI().getRootView(pane);
            root.paint(g, new java.awt.Rectangle(0, 0, 400, 300));
            g.dispose();
        });

        // 2. the same state, painted once onto a clean surface.
        SwingUtilities.invokeAndWait(() -> {
            Graphics2D g = fromScratch.createGraphics();
            javax.swing.text.View root = pane.getUI().getRootView(pane);
            root.paint(g, new java.awt.Rectangle(0, 0, 400, 300));
            g.dispose();
        });

        int differing = 0;
        for (int y = 0; y < 300; y++) {
            for (int x = 0; x < 400; x++) {
                if (afterCollapse.getRGB(x, y) != fromScratch.getRGB(x, y)) {
                    differing++;
                }
            }
        }
        // Both images come from the same component in the same state and the caret
        // is disabled, so this is exactly 0. A 200-pixel slack was too generous to
        // be useful: a line of this fixture's text is only 150-220 ink pixels, so
        // one stale line would have slipped under it.
        assertEquals(0, differing,
                differing + " pixels differ — the collapse left stale content behind");
    }

    /**
     * A clipped repaint must land in the same place as a full one.
     *
     * <p>Swing repaints damaged regions, not whole components, and the clip is not
     * aligned to line boundaries. If the paint loop derives its starting row from
     * the clip but its starting y from something else, rows are drawn one line
     * out — which appears as text overstruck on the row above.
     */
    @Test
    void clippedRepaintMatchesAFullRepaint() throws Exception {
        collapse(3, 13);

        BufferedImage full = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        BufferedImage clipped = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);

        SwingUtilities.invokeAndWait(() -> {
            Graphics2D g = full.createGraphics();
            pane.paint(g);
            g.dispose();

            // Repaint the same picture in horizontal bands whose edges deliberately
            // fall mid-line, as a scroll or damage rectangle would.
            Graphics2D h = clipped.createGraphics();
            for (int band = 0; band < 300; band += 37) {
                Graphics2D slice = (Graphics2D) h.create();
                slice.setClip(0, band, 400, 37);
                pane.paint(slice);
                slice.dispose();
            }
            h.dispose();
        });

        int differing = 0;
        for (int y = 0; y < 300; y++) {
            for (int x = 0; x < 400; x++) {
                if (full.getRGB(x, y) != clipped.getRGB(x, y)) {
                    differing++;
                }
            }
        }
        // Measures 0; the small allowance is for antialiasing at band edges only,
        // well under the ~150 pixels a single misplaced row would cost.
        assertTrue(differing < 20,
                differing + " pixels differ between a banded repaint and a full one "
                        + "— rows are being drawn at the wrong y under a clip");
    }

    @Test
    void unfoldingRestoresTheFullHeight() throws Exception {
        int[] original = new int[1];
        SwingUtilities.invokeAndWait(() -> original[0] = pane.getPreferredSize().height);

        collapse(3, 13);

        int[] restored = new int[1];
        SwingUtilities.invokeAndWait(() -> {
            margin.getFolds().removeAllElements();
            pane.revalidate();
            restored[0] = pane.getPreferredSize().height;
        });

        assertEquals(original[0], restored[0]);
    }
}
