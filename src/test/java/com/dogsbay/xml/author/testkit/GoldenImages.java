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

package com.dogsbay.xml.author.testkit;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Tier 2 (in-process GUI render) helper for the comprehensive test suite —
 * see {@code plans/comprehensive-ui-test-suite.md}.
 *
 * <p>Paints a Swing component to a {@link BufferedImage} <em>offscreen</em>,
 * with no window and no display server, mirroring the multi-pass layout dance
 * in {@code AuthorApp.renderScreenshot} (text panes report wrap-aware heights
 * only once sized, and cached layout sizes must be invalidated between passes).
 * This is the Wayland/headless-safe capture path: unlike {@code java.awt.Robot}
 * screen capture it needs no portal consent and produces deterministic output.
 *
 * <p>Golden comparison is intentionally <b>perceptual</b> (a tolerance per
 * channel, a ratio of differing pixels) because font hinting varies across
 * hosts; exact-pixel golden matching is too brittle for CI. Prefer the
 * structural assertions ({@link #isBlank}, {@link #contentPixelRatio}) for the
 * PR path and reserve {@link #assertMatchesGolden} for stable, host-pinned
 * environments (regenerate with {@code -Dgolden.update=true}).
 */
public final class GoldenImages {

    private GoldenImages() {}

    /** Per-channel tolerance below which two pixels are considered equal. */
    private static final int CHANNEL_TOLERANCE = 12;

    /**
     * Lay out and paint {@code component} at the given size, offscreen. Safe to
     * call from any thread; the layout/paint happen on the EDT.
     */
    public static BufferedImage render(JComponent component, int width, int height) {
        AtomicReference<BufferedImage> ref = new AtomicReference<>();
        Runnable job = () -> ref.set(renderOnEdt(component, width, height));
        if (SwingUtilities.isEventDispatchThread()) {
            job.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(job);
            } catch (Exception e) {
                throw new IllegalStateException("offscreen render failed", e);
            }
        }
        return ref.get();
    }

    private static BufferedImage renderOnEdt(JComponent component, int width, int height) {
        component.setSize(width, height);
        // multiple passes: invalidate cached sizes, then lay out, so wrap-aware
        // heights settle (validate() is a no-op without a native peer)
        for (int pass = 0; pass < 4; pass++) {
            invalidateAll(component);
            layoutAll(component);
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            component.paint(g);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void layoutAll(Component c) {
        c.doLayout();
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                layoutAll(child);
            }
        }
    }

    private static void invalidateAll(Component c) {
        c.invalidate();
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                invalidateAll(child);
            }
        }
    }

    // ── Structural assertions (host-stable, for the PR path) ──────────────

    /** True when every pixel matches the top-left pixel (nothing was drawn). */
    public static boolean isBlank(BufferedImage image) {
        int first = image.getRGB(0, 0);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != first) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Fraction of pixels that differ from the background (text, rules, tints) —
     * a cheap proxy for "content was actually rendered". {@code background} is
     * usually {@link Color#WHITE} (the fill used by {@link #render}).
     */
    public static double contentPixelRatio(BufferedImage image, Color background) {
        int bg = background.getRGB();
        long content = 0;
        long total = (long) image.getWidth() * image.getHeight();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (!within(image.getRGB(x, y), bg)) {
                    content++;
                }
            }
        }
        return (double) content / total;
    }

    /** Ratio of pixels differing beyond tolerance between two same-size images. */
    public static double diffRatio(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return 1.0;
        }
        long differing = 0;
        long total = (long) a.getWidth() * a.getHeight();
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (!within(a.getRGB(x, y), b.getRGB(x, y))) {
                    differing++;
                }
            }
        }
        return (double) differing / total;
    }

    // ── Golden comparison (opt-in; host-pinned environments only) ─────────

    /**
     * Compare {@code actual} against the golden PNG at {@code goldenPath}. With
     * {@code -Dgolden.update=true} (or when the golden is absent) the actual is
     * written as the new baseline and the check passes; otherwise the perceptual
     * diff ratio must not exceed {@code maxDiffRatio}.
     *
     * @return the measured diff ratio (0.0 when a baseline was just written)
     */
    public static double assertMatchesGolden(BufferedImage actual, Path goldenPath,
            double maxDiffRatio) {
        boolean update = Boolean.getBoolean("golden.update");
        try {
            if (update || Files.notExists(goldenPath)) {
                Files.createDirectories(goldenPath.getParent());
                ImageIO.write(actual, "png", goldenPath.toFile());
                return 0.0;
            }
            BufferedImage golden = ImageIO.read(goldenPath.toFile());
            double ratio = diffRatio(actual, golden);
            if (ratio > maxDiffRatio) {
                throw new AssertionError(String.format(
                        "golden mismatch for %s: diff ratio %.4f exceeds %.4f "
                                + "(regenerate with -Dgolden.update=true if intended)",
                        goldenPath.getFileName(), ratio, maxDiffRatio));
            }
            return ratio;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean within(int rgb1, int rgb2) {
        if (rgb1 == rgb2) {
            return true;
        }
        int dr = Math.abs(((rgb1 >> 16) & 0xFF) - ((rgb2 >> 16) & 0xFF));
        int dg = Math.abs(((rgb1 >> 8) & 0xFF) - ((rgb2 >> 8) & 0xFF));
        int db = Math.abs((rgb1 & 0xFF) - (rgb2 & 0xFF));
        return dr <= CHANNEL_TOLERANCE && dg <= CHANNEL_TOLERANCE && db <= CHANNEL_TOLERANCE;
    }
}
