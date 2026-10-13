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
import static org.assertj.core.api.Assertions.assertThatCode;

import java.awt.Font;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

/**
 * The bundled font files parse as fonts.
 *
 * <p>Written because one of them stopped doing so and nothing said a word. A working copy of
 * {@code JetBrainsMono-Regular.ttf} was found with 266,961 of its 273,900 bytes zeroed — the
 * right length, so {@code git status} never compared the contents, and only
 * {@code git update-index --really-refresh} revealed it. The likely cause is a write made
 * while the disk was full.
 *
 * <p>Nothing noticed, by design: {@code FontManager.loadBundledFonts} catches
 * {@code FontFormatException} and {@code IOException} and logs only when its {@code DEBUG}
 * flag is on, which it is not. So a corrupt font simply disappears from the font list, and
 * the editor falls back to a system monospace — a change in what every pane renders with,
 * reported nowhere. {@code FontManagerTest} did not catch it either: it asserts Source Code
 * Pro is bundled, and for JetBrains Mono it deliberately tolerates absence
 * ({@code jbm < 0 ? Integer.MAX_VALUE : jbm}), so the font going missing was a pass.
 *
 * <p>So this asserts the resources themselves, not the registration, and names the file when
 * one goes bad. A test of the bytes is what catches a corrupt file; a test of the font list
 * would also pass on a machine that happens to have the family installed system-wide.
 */
class BundledFontResourcesTest {

    /**
     * Every font FontManager tries to bundle. Kept here rather than read from the class
     * because the point is to notice when the file behind a path stops being a font, and a
     * path that FontManager has quietly dropped should fail this too.
     */
    private static final String[] BUNDLED = {
        "/fonts/SourceCodePro-Regular.ttf",
        "/fonts/JetBrainsMono-Regular.ttf"
    };

    @Test
    void everyBundledFontIsOnTheClasspath() {
        for (String path : BUNDLED) {
            try (InputStream is = FontManager.class.getResourceAsStream(path)) {
                assertThat(is).as("%s is on the classpath", path).isNotNull();
            } catch (Exception e) {
                throw new AssertionError("could not open " + path, e);
            }
        }
    }

    @Test
    void everyBundledFontParsesAsATrueTypeFont() {
        for (String path : BUNDLED) {
            assertThatCode(() -> {
                try (InputStream is = FontManager.class.getResourceAsStream(path)) {
                    Font font = Font.createFont(Font.TRUETYPE_FONT, is);
                    assertThat(font.getFamily()).as("%s names a family", path).isNotBlank();
                }
            }).as("%s parses as a font — if this fails the file is corrupt, not the code; "
                  + "restore it with `git checkout -- src/main/resources%s`", path, path)
              .doesNotThrowAnyException();
        }
    }

    /**
     * The families the editor offers as bundled. This is the half FontManagerTest leaves
     * open: it requires Source Code Pro but allows JetBrains Mono to be missing, which is
     * exactly the state a corrupt file produces.
     */
    @Test
    void bothBundledFamiliesAreRegistered() {
        FontManager fm = FontManager.getInstance();
        assertThat(fm.hasBundledFont("Source Code Pro")).as("Source Code Pro registered").isTrue();
        assertThat(fm.hasBundledFont("JetBrains Mono")).as("JetBrains Mono registered").isTrue();
    }
}
