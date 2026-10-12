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
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

public class AsciiDocConverterTest {

    @Test
    @DisplayName("Converts section titles to HTML headings")
    public void testSectionTitles() {
        String html = AsciiDocConverter.convert("= Title\n\n== Section\n\n=== Subsection\n");
        assertTrue(html.contains("Title"), "Should contain title text");
        assertTrue(html.contains("Section"), "Should contain section text");
        assertTrue(html.contains("Subsection"), "Should contain subsection text");
        assertTrue(html.contains("<h"), "Should contain heading tags");
    }

    @Test
    @DisplayName("Converts bold text")
    public void testBold() {
        String html = AsciiDocConverter.convert("This is **bold** text\n");
        assertTrue(html.contains("<strong>bold</strong>"));
    }

    @Test
    @DisplayName("Converts italic text")
    public void testItalic() {
        String html = AsciiDocConverter.convert("This is __italic__ text\n");
        assertTrue(html.contains("<em>italic</em>"));
    }

    @Test
    @DisplayName("Converts monospace text")
    public void testMonospace() {
        String html = AsciiDocConverter.convert("Use `command` here\n");
        assertTrue(html.contains("<code>command</code>"));
    }

    @Test
    @DisplayName("Converts link macros")
    public void testLinkMacro() {
        String html = AsciiDocConverter.convert("See link:https://example.com[Example Site]\n");
        assertTrue(html.contains("https://example.com"));
        assertTrue(html.contains("Example Site"));
    }

    @Test
    @DisplayName("Converts admonitions")
    public void testAdmonitions() {
        String html = AsciiDocConverter.convert("= Test\n\nNOTE: This is important\n");
        assertTrue(html.contains("This is important"));
    }

    @Test
    @DisplayName("Converts listing blocks to pre/code")
    public void testListingBlock() {
        String html = AsciiDocConverter.convert("----\nsome code\n----\n");
        assertTrue(html.contains("<pre"));
        assertTrue(html.contains("some code"));
    }

    @Test
    @DisplayName("Removes line comments")
    public void testLineComments() {
        String html = AsciiDocConverter.convert("// hidden comment\nVisible text\n");
        assertFalse(html.contains("hidden comment"));
        assertTrue(html.contains("Visible text"));
    }

    @Test
    @DisplayName("Full convert wraps in HTML document")
    public void testFullConvert() {
        String html = AsciiDocConverter.convert("= Title\n\nParagraph text.\n");
        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("<html>"));
        assertTrue(html.contains("Title"));
        assertTrue(html.contains("Paragraph text."));
    }

    @Test
    @DisplayName("Handles null input")
    public void testNullInput() {
        String html = AsciiDocConverter.convert(null);
        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"));
    }

    @Test
    @DisplayName("Handles empty input")
    public void testEmptyInput() {
        String html = AsciiDocConverter.convert("");
        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"));
    }

    @Test
    @DisplayName("Converts block images")
    public void testBlockImage() {
        String html = AsciiDocConverter.convert("image::photo.png[A photo]\n");
        assertTrue(html.contains("photo.png"));
    }

    @Test
    @DisplayName("Converts cross references")
    public void testXref() {
        String html = AsciiDocConverter.convert("See <<intro,Introduction>>\n");
        assertTrue(html.contains("Introduction"));
        assertTrue(html.contains("intro"));
    }
}
