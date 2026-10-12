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

package com.dogsbay.dogsbayaieditor.terminal;

import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;

import java.awt.*;

/**
 * Terminal settings provider for JediTerm terminal emulator.
 * Simplified for JediTerm 3.48 API compatibility.
 */
public class TerminalSettings extends DefaultSettingsProvider {

    /** What the terminal used to be fixed at, and still is unless something asks otherwise. */
    public static final String DEFAULT_FAMILY = "Monospaced";
    public static final int DEFAULT_SIZE = 12;

    private static volatile String family = DEFAULT_FAMILY;
    private static volatile int size = DEFAULT_SIZE;

    /**
     * Show the terminal in this font.
     *
     * <p>It was fixed at {@value #DEFAULT_FAMILY} 12, which is small enough to be unreadable in a recording or on a
     * high-resolution display, and nothing outside the editor could change it.
     *
     * @param fontFamily the family, or null to keep the default.
     * @param fontSize   the size in points; anything below 1 keeps the default.
     */
    public static void setFont(String fontFamily, int fontSize) {
        family = fontFamily == null || fontFamily.isBlank() ? DEFAULT_FAMILY : fontFamily;
        size = fontSize > 0 ? fontSize : DEFAULT_SIZE;
    }

    /** Back to {@value #DEFAULT_FAMILY} at 12. */
    public static void useDefaultFont() {
        setFont(null, 0);
    }

    /** The family the terminal shows. */
    public static String family() {
        return family;
    }

    /** The size the terminal shows, in points. */
    public static int size() {
        return size;
    }

    @Override
    public Font getTerminalFont() {
        return new Font(family, Font.PLAIN, size);
    }

    @Override
    public float getTerminalFontSize() {
        return size;
    }
}
