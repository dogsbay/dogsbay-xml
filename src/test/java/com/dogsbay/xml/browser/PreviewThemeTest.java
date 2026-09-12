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

package com.dogsbay.xml.browser;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PreviewTheme CSS injection and lookup.
 */
class PreviewThemeTest {

    @Test
    void allThemesHaveCss() {
        for (PreviewTheme theme : PreviewTheme.values()) {
            assertNotNull(theme.getCss(), theme.getDisplayName() + " should have CSS");
            assertFalse(theme.getCss().isEmpty(), theme.getDisplayName() + " CSS should not be empty");
        }
    }

    @Test
    void allThemesHaveDisplayName() {
        for (PreviewTheme theme : PreviewTheme.values()) {
            assertNotNull(theme.getDisplayName());
            assertFalse(theme.getDisplayName().isEmpty());
        }
    }

    @Test
    void toStringReturnsDisplayName() {
        assertEquals("Light", PreviewTheme.LIGHT.toString());
        assertEquals("Dark", PreviewTheme.DARK.toString());
        assertEquals("GitHub", PreviewTheme.GITHUB.toString());
    }

    @Test
    void fromNameFindsThemes() {
        assertEquals(PreviewTheme.LIGHT, PreviewTheme.fromName("Light"));
        assertEquals(PreviewTheme.DARK, PreviewTheme.fromName("Dark"));
        assertEquals(PreviewTheme.GITHUB, PreviewTheme.fromName("GitHub"));
    }

    @Test
    void fromNameDefaultsToLightForUnknown() {
        assertEquals(PreviewTheme.LIGHT, PreviewTheme.fromName("NonExistent"));
        assertEquals(PreviewTheme.LIGHT, PreviewTheme.fromName(null));
        assertEquals(PreviewTheme.LIGHT, PreviewTheme.fromName(""));
    }

    @Test
    void applyToInjectsStyleInHead() {
        String html = "<html><head><title>Test</title></head><body><p>Hello</p></body></html>";
        String result = PreviewTheme.LIGHT.applyTo(html);
        assertTrue(result.contains("<style>"));
        assertTrue(result.contains("</style>"));
        // Style should be before </head>
        int styleEnd = result.indexOf("</style>");
        int headEnd = result.indexOf("</head>");
        assertTrue(styleEnd < headEnd, "Style should be injected before </head>");
    }

    @Test
    void applyToHandlesHtmlWithBody() {
        String html = "<html><body><p>Hello</p></body></html>";
        String result = PreviewTheme.DARK.applyTo(html);
        assertTrue(result.contains("<style>"));
        assertTrue(result.contains("<head>"));
    }

    @Test
    void applyToHandlesPlainHtml() {
        String html = "<h1>Hello</h1><p>World</p>";
        String result = PreviewTheme.GITHUB.applyTo(html);
        assertTrue(result.contains("<style>"));
        assertTrue(result.contains("<h1>Hello</h1>"));
    }

    @Test
    void applyToReturnsNullForNull() {
        assertNull(PreviewTheme.LIGHT.applyTo(null));
    }

    @Test
    void darkThemeHasDarkBackground() {
        assertTrue(PreviewTheme.DARK.getCss().contains("#0d1117"));
    }

    @Test
    void lightThemeHasWhiteBackground() {
        assertTrue(PreviewTheme.LIGHT.getCss().contains("#ffffff"));
    }

    @Test
    void threeThemesExist() {
        assertEquals(3, PreviewTheme.values().length);
    }
}
