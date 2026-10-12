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
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Layout and painting checks for the editor's text views.
 *
 * <p>Added with stage 2 of {@code plans/sun-code-investigation.md}, which deleted
 * {@code FoldingBoxView} and {@code FoldingCompositeView} — 2002 copies of
 * {@code javax.swing.text.BoxView}/{@code CompositeView} carrying Sun's
 * proprietary header — and re-parented {@code FoldingWrappedPlainView} onto the
 * public {@code BoxView}. That swaps a 23-year-old layout implementation for the
 * current one at runtime, and nothing in the suite exercised view layout at all.
 *
 * <p>These are deliberately geometric rather than pixel-exact. The modern
 * {@code BoxView} lays text out with fractional precision the old copy did not
 * have, so a golden image would enshrine the worse behaviour as correct. What
 * must hold is that text lays out, maps between model and view coherently, and
 * actually paints.
 */
@org.junit.jupiter.api.Tag("ui")
@DisabledIf(value = "isHeadless", disabledReason = "needs a display")
class EditorViewLayoutTest {

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <concept id="sample">
              <title>Editing</title>
              <conbody>
                <p>A paragraph long enough that a narrow viewport has to wrap it \
            across several lines when wrapping is switched on.</p>
                <p>Second paragraph.</p>
              </conbody>
            </concept>""";

    private JFrame frame;

    /**
     * The registry is populated at application startup, and
     * {@code XmlEditorPane}'s constructor assumes it: with no formats registered
     * it NPEs on {@code kit.getFont()}. Register the same set the app does.
     */
    @BeforeEach
    void registerFormats() {
        DocumentFormatRegistry.clear();
        DocumentFormatRegistry.register(new XmlDocumentFormat());
        DocumentFormatRegistry.register(new MarkdownDocumentFormat());
        PlainTextDocumentFormat plainText = new PlainTextDocumentFormat();
        DocumentFormatRegistry.register(plainText);
        DocumentFormatRegistry.setDefault(plainText);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (frame != null) {
            SwingUtilities.invokeAndWait(frame::dispose);
            frame = null;
        }
    }

    /** A realized editor pane holding {@link #XML}. */
    private XmlEditorPane show(boolean wrapped) throws Exception {
        final XmlEditorPane[] holder = new XmlEditorPane[1];
        SwingUtilities.invokeAndWait(() -> {
            XmlEditorPane pane = new XmlEditorPane(wrapped);
            // The kit takes its font from TextPreferences, which the application
            // initialises at startup; without it the view's metrics lookup NPEs.
            pane.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,
                    java.awt.Font.PLAIN, 12));
            pane.setText(XML);
            pane.setCaretPosition(0);
            frame = new JFrame("view-layout-test");
            frame.setContentPane(new JScrollPane(pane));
            frame.setSize(420, 400);
            frame.setVisible(true);
            holder[0] = pane;
        });
        SwingUtilities.invokeAndWait(() -> { });   // let layout settle
        return holder[0];
    }

    @Test
    void unwrappedViewLaysOutWithSaneGeometry() throws Exception {
        XmlEditorPane pane = show(false);

        SwingUtilities.invokeAndWait(() -> {
            assertTrue(pane.getPreferredSize().width > 0, "no width");
            assertTrue(pane.getPreferredSize().height > 0, "no height");
        });
    }

    @Test
    void wrappedViewIsTallerAndNarrowerThanUnwrapped() throws Exception {
        XmlEditorPane unwrapped = show(false);
        int[] plain = new int[2];
        SwingUtilities.invokeAndWait(() -> {
            plain[0] = unwrapped.getPreferredSize().width;
            plain[1] = unwrapped.getPreferredSize().height;
        });
        SwingUtilities.invokeAndWait(frame::dispose);

        XmlEditorPane wrapped = show(true);
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(wrapped.getPreferredSize().width <= plain[0],
                    "wrapping must not need more width than the longest line");
            assertTrue(wrapped.getPreferredSize().height >= plain[1],
                    "wrapping the long paragraph must take at least as many lines");
        });
    }

    @Test
    void modelToViewProducesDistinctRowsForDistinctLines() throws Exception {
        XmlEditorPane pane = show(false);

        SwingUtilities.invokeAndWait(() -> {
            try {
                Rectangle2D first = pane.modelToView2D(0);
                Rectangle2D later = pane.modelToView2D(XML.indexOf("<conbody>"));

                assertNotNull(first);
                assertNotNull(later);
                assertTrue(later.getY() > first.getY(),
                        "a later line must sit below the first");
                assertTrue(first.getHeight() > 0, "zero-height line box");
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void viewToModelRoundTripsThroughModelToView() throws Exception {
        XmlEditorPane pane = show(false);

        SwingUtilities.invokeAndWait(() -> {
            try {
                for (int offset : new int[] {0, 20, XML.indexOf("<title>"),
                        XML.indexOf("Second")}) {
                    Rectangle2D box = pane.modelToView2D(offset);
                    // Aim at the middle of the glyph box, not its edge, so
                    // rounding cannot land on the neighbouring character.
                    int back = pane.viewToModel2D(new java.awt.geom.Point2D.Double(
                            box.getX() + box.getWidth() / 2,
                            box.getY() + box.getHeight() / 2));

                    assertEquals(offset, back, "round trip failed at offset " + offset);
                }
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void wrappedViewActuallyPaintsInk() throws Exception {
        XmlEditorPane pane = show(true);
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);

        SwingUtilities.invokeAndWait(() -> {
            Graphics2D g = image.createGraphics();
            pane.paint(g);
            g.dispose();
        });

        // Any non-background pixel proves the view chain rendered text rather
        // than silently laying out an empty document.
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
        assertTrue(painted, "the view chain produced a blank image");
    }
}
