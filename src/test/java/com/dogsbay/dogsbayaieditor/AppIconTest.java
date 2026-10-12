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

package com.dogsbay.dogsbayaieditor;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The application icon is the paw from the website and the blog, shipped in
 * every size the window managers ask for. A missing size is invisible until
 * something renders at exactly that size and picks a blurred neighbour, so the
 * set is pinned here rather than trusted.
 */
class AppIconTest {

    private static final String DIR = "/com/dogsbay/dogsbayaieditor/icons/app/";
    private static final int[] SIZES = { 16, 24, 32, 48, 64, 128, 256, 512 };

    /** The teal the website draws the paw in. */
    private static final int PAW_RGB = 0x1e6e7d;

    private static BufferedImage icon(int size) throws Exception {
        try (InputStream in = AppIconTest.class.getResourceAsStream(DIR + "dogsbay-paw-" + size + ".png")) {
            assertThat(in).as("dogsbay-paw-" + size + ".png on the classpath").isNotNull();
            return ImageIO.read(in);
        }
    }

    @Test
    @DisplayName("every size the window icon list names is on the classpath, and square")
    void everySizeIsShipped() throws Exception {
        for (int size : SIZES) {
            BufferedImage image = icon(size);

            assertThat(image).as("icon at " + size).isNotNull();
            assertThat(image.getWidth()).as("width at " + size).isEqualTo(size);
            assertThat(image.getHeight()).as("height at " + size).isEqualTo(size);
        }
    }

    @Test
    @DisplayName("the icon has a transparent background, not a white tile")
    void theBackgroundIsTransparent() throws Exception {
        BufferedImage image = icon(64);

        // A window manager composites the icon onto a taskbar of its own colour.
        assertThat(image.getColorModel().hasAlpha()).isTrue();
        assertThat(image.getRGB(1, 1) >>> 24).as("corner alpha").isZero();
    }

    @Test
    @DisplayName("it is drawn in the website's teal")
    void itIsTheWebsiteTeal() throws Exception {
        BufferedImage image = icon(64);

        // The centre of the pad, which is solid at every size.
        int centre = image.getRGB(32, 48);
        assertThat(centre >>> 24).as("the pad is opaque").isEqualTo(255);
        assertThat(centre & 0xFFFFFF).isEqualTo(PAW_RGB);
    }

    @Test
    @DisplayName("the toes stay apart at the smallest size")
    void theToesDoNotFuseAt16() throws Exception {
        BufferedImage image = icon(16);

        // The gaps are the whole reason the toes sit high and the pad low. If a
        // redraw closes them, 16px reads as a bar rather than a paw.
        int row = 5;
        int runs = 0;
        boolean inRun = false;
        for (int x = 0; x < image.getWidth(); x++) {
            boolean ink = (image.getRGB(x, row) >>> 24) > 128;
            if (ink && !inRun) {
                runs++;
            }
            inRun = ink;
        }
        assertThat(runs).as("separate toes across row " + row).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("the packaging formats each platform needs are in the repository")
    void thePackagingIconsExist() {
        Path icons = Path.of("src/main/packaging/icons");

        assertThat(icons.resolve("dogsbay-paw.png")).as("Linux").exists();
        assertThat(icons.resolve("dogsbay-paw.ico")).as("Windows").exists();
        assertThat(icons.resolve("dogsbay-paw.icns")).as("macOS").exists();
    }

    @Test
    @DisplayName("the macOS icon really is an icns, not a renamed PNG")
    void theMacIconIsAnIcns() throws Exception {
        byte[] head = new byte[4];
        try (InputStream in = Files.newInputStream(Path.of("src/main/packaging/icons/dogsbay-paw.icns"))) {
            assertThat(in.read(head)).isEqualTo(4);
        }

        // ImageMagick writes a PNG under the .icns name given half a chance,
        // and jpackage rejects it on macOS only — long after anyone would notice.
        assertThat(new String(head, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("icns");
    }
}
