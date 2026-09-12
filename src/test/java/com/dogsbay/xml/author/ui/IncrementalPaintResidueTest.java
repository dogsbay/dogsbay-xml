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

package com.dogsbay.xml.author.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import com.dogsbay.xml.author.adapter.AdapterTestSupport;
import com.dogsbay.xml.author.model.AuthorBlock;

/**
 * Regression instrument for the incremental-painting bug class (selection
 * ghosting, caret residue): full-window paints are always self-consistent, so
 * ScreenshotCommand-style captures cannot see these bugs — only the on-screen
 * result of Swing's dirty-region painting can. This test clicks around inside
 * a selected (tinted) block through the real event pipeline and compares
 * Robot screen captures before and after: any persistent residue shows up as
 * pixel differences once the caret returns to its starting position.
 *
 * <p>Found by users twice: opaque-with-translucent-background blocks trapped
 * old caret/selection pixels under the tint on every click.
 */
@Tag("ui")
@DisabledIf(value = "isHeadless", disabledReason = "needs a display")
class IncrementalPaintResidueTest {

    static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    private JFrame frame;

    @AfterEach
    void tearDown() throws Exception {
        if (frame != null) {
            SwingUtilities.invokeAndWait(() -> frame.dispose());
        }
    }

    @Test
    void clickingInsideASelectedBlockLeavesNoResidue() throws Exception {
        Robot robot = new Robot();
        final TextBlockComponent[] textRef = new TextBlockComponent[1];
        final AuthorEditorPanel[] panelRef = new AuthorEditorPanel[1];

        SwingUtilities.invokeAndWait(() -> {
            AuthorEditorPanel panel = new AuthorEditorPanel();
            panel.setContent(AdapterTestSupport.parse("""
                    <glossentry id="g">
                      <glossterm>Sample Rate</glossterm>
                      <glossdef>The number of audio samples captured per second, measured in
                      Hertz. Higher sample rates capture more detail in the recording.</glossdef>
                    </glossentry>"""));
            panelRef[0] = panel;
            frame = new JFrame("residue-test");
            frame.setContentPane(panel);
            frame.setSize(900, 360);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
        robot.waitForIdle();

        SwingUtilities.invokeAndWait(() -> {
            AuthorEditorPanel panel = panelRef[0];
            AuthorBlock glossdef = panel.getAuthorDocument().getRoot().getChildren().get(1);
            panel.setSelectedBlock(glossdef);   // the tinted code path
            textRef[0] = (TextBlockComponent) panel.getBlockComponent(glossdef.getId())
                    .getComponent(1);
            textRef[0].getCaret().setBlinkRate(0);
            textRef[0].setCaretPosition(0);
        });
        robot.waitForIdle();
        Thread.sleep(150);

        Rectangle area = captureArea(textRef[0]);
        BufferedImage before;
        try {
            before = robot.createScreenCapture(area);
        } catch (SecurityException denied) {
            // Wayland screencast portal: capture needs interactive consent —
            // the instrument runs under Xvfb/X11 (CI), skip here
            assumeTrue(false, "screen capture not permitted on this display server");
            return;
        }
        assumeTrue(!looksBlank(before), "screen capture unavailable on this display server");

        // click at many positions through the real event pipeline (caret
        // moves + a drag selection), then return to the starting state
        SwingUtilities.invokeAndWait(() -> {
            TextBlockComponent text = textRef[0];
            int y = text.getHeight() / 2;
            for (int x = 20; x < text.getWidth() - 40; x += 60) {
                click(text, x, y);
            }
            drag(text, 100, y, 220, y);   // selection highlight on, then…
            click(text, 300, y);          // …cleared by the next click
            text.setCaretPosition(0);     // back to the baseline state
            text.getCaret().setSelectionVisible(false);
        });
        robot.waitForIdle();
        Thread.sleep(200);

        BufferedImage after = robot.createScreenCapture(area);
        int differing = diffPixels(before, after);

        // identical states must render identically; allow a sliver for the
        // caret column and antialiasing wobble, nothing more
        assertThat(differing)
                .as("persistent paint residue after clicks (differing pixels)")
                .isLessThan(area.height * 4);
    }

    private static void click(TextBlockComponent c, int x, int y) {
        long now = System.currentTimeMillis();
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, now, 0, x, y, 1, false,
                MouseEvent.BUTTON1));
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, now, 0, x, y, 1, false,
                MouseEvent.BUTTON1));
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_CLICKED, now, 0, x, y, 1, false,
                MouseEvent.BUTTON1));
    }

    private static void drag(TextBlockComponent c, int x1, int y1, int x2, int y2) {
        long now = System.currentTimeMillis();
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, now, 0, x1, y1, 1, false,
                MouseEvent.BUTTON1));
        for (int x = x1; x <= x2; x += 30) {
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_DRAGGED, now, 0, x, y1, 1, false,
                    MouseEvent.BUTTON1));
        }
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, now, 0, x2, y2, 1, false,
                MouseEvent.BUTTON1));
    }

    private static Rectangle captureArea(TextBlockComponent text) {
        Point p = text.getLocationOnScreen();
        return new Rectangle(p.x, p.y, text.getWidth(), text.getHeight());
    }

    private static boolean looksBlank(BufferedImage image) {
        int first = image.getRGB(0, 0);
        for (int x = 0; x < image.getWidth(); x += 16) {
            for (int y = 0; y < image.getHeight(); y += 4) {
                if (image.getRGB(x, y) != first) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int diffPixels(BufferedImage a, BufferedImage b) {
        int count = 0;
        for (int x = 0; x < a.getWidth(); x++) {
            for (int y = 0; y < a.getHeight(); y++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }
}
