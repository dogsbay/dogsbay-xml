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

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages font loading and availability for the application.
 * Provides a hybrid approach: bundles core coding fonts and detects system fonts.
 *
 * Features:
 * - Loads bundled fonts from resources (JetBrains Mono, Source Code Pro, etc.)
 * - Auto-detects system monospace fonts
 * - Provides sorted, deduplicated font list
 * - Caches results for performance
 *
 * @version 1.0
 */
public class FontManager {

    private static final boolean DEBUG = false;

    // Bundled font resources (relative to classpath)
    // Source Code Pro first: it's the ligature-free default and should head the list.
    // Fira Code is deliberately NOT bundled — its glyph metrics overlap in the Swing
    // fixed-advance editor (mis-positioned characters that look like stray whitespace).
    private static final String[] BUNDLED_FONTS = {
        "/fonts/SourceCodePro-Regular.ttf",
        "/fonts/JetBrainsMono-Regular.ttf"
    };

    // Known monospace font families (for system detection)
    private static final String[] KNOWN_MONOSPACE_FAMILIES = {
        "Monospaced",
        "Courier New",
        "Courier",
        "Monaco",
        "Consolas",
        "Menlo",
        "DejaVu Sans Mono",
        "Liberation Mono",
        "Inconsolata",
        "Hack",
        "Ubuntu Mono",
        "Roboto Mono",
        "Cascadia Code",
        "SF Mono",
        "Andale Mono"
    };

    private static FontManager instance;
    private List<String> availableFonts;
    private Set<String> bundledFontNames;
    // The actual registered bundled Font objects, keyed by family. Used to build fonts
    // deterministically (deriveFont) instead of `new Font(family)`, which resolves
    // ambiguously when the same family is also installed on the system — that ambiguity
    // produced overlapping glyphs for the bundled JetBrains Mono.
    private final java.util.Map<String, Font> bundledFontObjects = new java.util.HashMap<>();
    // Registration order of bundled families, so the list shows them in BUNDLED_FONTS
    // order (Source Code Pro first) rather than alphabetically.
    private final List<String> bundledFontOrder = new ArrayList<>();

    /**
     * Private constructor - use getInstance()
     */
    private FontManager() {
        bundledFontNames = new HashSet<>();
        loadFonts();
    }

    /**
     * Gets the singleton instance of FontManager
     *
     * @return the FontManager instance
     */
    public static synchronized FontManager getInstance() {
        if (instance == null) {
            instance = new FontManager();
        }
        return instance;
    }

    /**
     * Loads bundled fonts and detects system fonts
     */
    private void loadFonts() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Set<String> fontSet = new HashSet<>();

        // 1. Load bundled fonts from resources
        for (String fontPath : BUNDLED_FONTS) {
            try {
                InputStream is = getClass().getResourceAsStream(fontPath);
                if (is != null) {
                    Font font = Font.createFont(Font.TRUETYPE_FONT, is);
                    ge.registerFont(font);
                    fontSet.add(font.getFamily());
                    bundledFontNames.add(font.getFamily());
                    if (!bundledFontOrder.contains(font.getFamily())) {
                        bundledFontOrder.add(font.getFamily());
                    }
                    bundledFontObjects.put(font.getFamily(), font);   // keep the real instance
                    if (DEBUG) {
                        System.out.println("FontManager: Loaded bundled font: " + font.getFamily());
                    }
                    is.close();
                } else if (DEBUG) {
                    System.out.println("FontManager: Bundled font not found: " + fontPath);
                }
            } catch (FontFormatException | IOException e) {
                if (DEBUG) {
                    System.err.println("FontManager: Failed to load font " + fontPath + ": " + e.getMessage());
                }
            }
        }

        // 2. Add known monospace fonts from system
        String[] systemFonts = ge.getAvailableFontFamilyNames();
        for (String fontFamily : systemFonts) {
            // Check if it's a known monospace font
            for (String monoFamily : KNOWN_MONOSPACE_FAMILIES) {
                if (fontFamily.equalsIgnoreCase(monoFamily) ||
                    fontFamily.toLowerCase().contains(monoFamily.toLowerCase())) {
                    fontSet.add(fontFamily);
                    if (DEBUG) {
                        System.out.println("FontManager: Found system monospace font: " + fontFamily);
                    }
                    break;
                }
            }
        }

        // 3. Also detect fonts with "Mono" or "Code" in the name
        for (String fontFamily : systemFonts) {
            String lowerName = fontFamily.toLowerCase();
            if (lowerName.contains("mono") || lowerName.contains("code") ||
                lowerName.contains("courier") || lowerName.contains("terminal")) {
                fontSet.add(fontFamily);
                if (DEBUG && !fontSet.contains(fontFamily)) {
                    System.out.println("FontManager: Found additional monospace font: " + fontFamily);
                }
            }
        }

        // Fira Code is never offered — its glyphs overlap in the fixed-advance editor,
        // and resolveSavedFont migrates any saved selection away from it. Excluding it
        // here keeps the picker consistent with that migration.
        fontSet.removeIf(f -> "Fira Code".equalsIgnoreCase(f));

        // 4. Sort fonts: bundled fonts first, then system fonts alphabetically
        availableFonts = new ArrayList<>(fontSet);
        Collections.sort(availableFonts, (f1, f2) -> {
            boolean b1 = bundledFontNames.contains(f1);
            boolean b2 = bundledFontNames.contains(f2);

            if (b1 && b2) {
                // both bundled → keep BUNDLED_FONTS registration order (Source Code Pro first)
                return Integer.compare(bundledFontOrder.indexOf(f1), bundledFontOrder.indexOf(f2));
            }
            if (b1 && !b2) return -1;  // Bundled fonts first
            if (!b1 && b2) return 1;

            return f1.compareTo(f2);  // Then system fonts alphabetically
        });

        if (DEBUG) {
            System.out.println("FontManager: Total fonts available: " + availableFonts.size());
            System.out.println("FontManager: Bundled fonts: " + bundledFontNames.size());
        }
    }

    /**
     * Gets the list of available font families (bundled + system monospace)
     * Bundled fonts appear first, followed by system fonts alphabetically.
     *
     * @return sorted list of font family names
     */
    public List<String> getAvailableFonts() {
        return new ArrayList<>(availableFonts);
    }

    /**
     * Checks if a font is bundled with the application
     *
     * @param fontFamily the font family name
     * @return true if the font is bundled, false otherwise
     */
    public boolean isBundledFont(String fontFamily) {
        return bundledFontNames.contains(fontFamily);
    }

    /**
     * Gets a display name for a font (marks bundled fonts)
     *
     * @param fontFamily the font family name
     * @return display name with optional marker for bundled fonts
     */
    public String getDisplayName(String fontFamily) {
        if (isBundledFont(fontFamily)) {
            return fontFamily + " ★";  // Star indicates bundled/recommended
        }
        return fontFamily;
    }

    /**
     * Creates a Font object for the given family name
     *
     * @param fontFamily the font family name
     * @param size the font size
     * @return Font object, or default monospaced font if family not found
     */
    public Font createFont(String fontFamily, int size) {
        // Remove the star marker if present
        String cleanFamily = fontFamily.replace(" ★", "");

        // Bundled families: derive from the EXACT registered instance so we never hit
        // the ambiguous `new Font(family)` lookup (which can resolve to a conflicting
        // system copy of the same family — the cause of the overlapping-glyph rendering).
        Font bundled = bundledFontObjects.get(cleanFamily);
        if (bundled != null) {
            return bundled.deriveFont(Font.PLAIN, (float) size);
        }

        if (availableFonts.contains(cleanFamily)) {
            return new Font(cleanFamily, Font.PLAIN, size);
        }

        // Fallback to the logical monospaced font.
        return new Font(Font.MONOSPACED, Font.PLAIN, size);
    }

    /** True when a bundled font with this family was successfully registered. */
    public boolean hasBundledFont(String family) {
        return bundledFontObjects.containsKey(family);
    }
}
