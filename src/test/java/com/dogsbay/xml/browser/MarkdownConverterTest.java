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

/**
 * Tests for the MarkdownConverter.
 */
public class MarkdownConverterTest {

    @Test
    @DisplayName("MarkdownConverter class can be loaded")
    public void testClassLoading() {
        assertDoesNotThrow(() -> {
            Class.forName("com.dogsbay.xml.browser.MarkdownConverter");
        }, "MarkdownConverter class should be loadable");
    }

    @Test
    @DisplayName("Empty input returns valid HTML document")
    public void testEmptyInput() {
        String result = MarkdownConverter.convert("");
        assertNotNull(result);
        assertTrue(result.contains("<!DOCTYPE html>"));
        assertTrue(result.contains("<html>"));
        assertTrue(result.contains("</html>"));
    }

    @Test
    @DisplayName("Null input returns valid HTML document")
    public void testNullInput() {
        String result = MarkdownConverter.convert(null);
        assertNotNull(result);
        assertTrue(result.contains("<!DOCTYPE html>"));
    }

    @Test
    @DisplayName("Paragraph renders to <p> tag")
    public void testParagraph() {
        String result = MarkdownConverter.convertFragment("Hello world");
        assertTrue(result.contains("<p>Hello world</p>"));
    }

    @Test
    @DisplayName("Heading renders correctly")
    public void testHeading() {
        String result = MarkdownConverter.convertFragment("# My Title");
        assertTrue(result.contains("<h1>"));
        assertTrue(result.contains("My Title"));
    }

    @Test
    @DisplayName("Multiple heading levels render correctly")
    public void testHeadingLevels() {
        String h2 = MarkdownConverter.convertFragment("## Second");
        assertTrue(h2.contains("<h2>"));
        String h3 = MarkdownConverter.convertFragment("### Third");
        assertTrue(h3.contains("<h3>"));
    }

    @Test
    @DisplayName("Bold text renders to <strong>")
    public void testBold() {
        String result = MarkdownConverter.convertFragment("**bold text**");
        assertTrue(result.contains("<strong>bold text</strong>"));
    }

    @Test
    @DisplayName("Italic text renders to <em>")
    public void testItalic() {
        String result = MarkdownConverter.convertFragment("*italic text*");
        assertTrue(result.contains("<em>italic text</em>"));
    }

    @Test
    @DisplayName("Inline code renders to <code>")
    public void testInlineCode() {
        String result = MarkdownConverter.convertFragment("`code`");
        assertTrue(result.contains("<code>code</code>"));
    }

    @Test
    @DisplayName("Code block renders to <pre><code>")
    public void testCodeBlock() {
        String result = MarkdownConverter.convertFragment("```\nint x = 1;\n```");
        assertTrue(result.contains("<pre>"));
        assertTrue(result.contains("<code>"));
        assertTrue(result.contains("int x = 1;"));
    }

    @Test
    @DisplayName("Tables extension works")
    public void testTables() {
        String md = "| A | B |\n|---|---|\n| 1 | 2 |";
        String result = MarkdownConverter.convertFragment(md);
        assertTrue(result.contains("<table>"));
        assertTrue(result.contains("<th>"));
        assertTrue(result.contains("<td>"));
    }

    @Test
    @DisplayName("Strikethrough extension works")
    public void testStrikethrough() {
        String result = MarkdownConverter.convertFragment("~~deleted~~");
        assertTrue(result.contains("<del>deleted</del>"));
    }

    @Test
    @DisplayName("Task list extension works")
    public void testTaskList() {
        String md = "- [x] Done\n- [ ] Todo";
        String result = MarkdownConverter.convertFragment(md);
        assertTrue(result.contains("type=\"checkbox\""));
    }

    @Test
    @DisplayName("Link renders to <a> tag")
    public void testLink() {
        String result = MarkdownConverter.convertFragment("[click](http://example.com)");
        assertTrue(result.contains("<a href=\"http://example.com\""));
        assertTrue(result.contains("click</a>"));
    }

    @Test
    @DisplayName("Blockquote renders correctly")
    public void testBlockquote() {
        String result = MarkdownConverter.convertFragment("> quoted text");
        assertTrue(result.contains("<blockquote>"));
    }

    @Test
    @DisplayName("Full document includes CSS styling")
    public void testFullDocumentHasCSS() {
        String result = MarkdownConverter.convert("# Test");
        assertTrue(result.contains("<style>"));
        assertTrue(result.contains("font-family"));
    }

    @Test
    @DisplayName("Autolink extension works")
    public void testAutolink() {
        String result = MarkdownConverter.convertFragment("Visit http://example.com for more");
        assertTrue(result.contains("<a href=\"http://example.com\""));
    }

    @Test
    @DisplayName("Unordered list renders correctly")
    public void testUnorderedList() {
        String md = "- Item 1\n- Item 2\n- Item 3";
        String result = MarkdownConverter.convertFragment(md);
        assertTrue(result.contains("<ul>"));
        assertTrue(result.contains("<li>"));
    }

    @Test
    @DisplayName("Ordered list renders correctly")
    public void testOrderedList() {
        String md = "1. First\n2. Second\n3. Third";
        String result = MarkdownConverter.convertFragment(md);
        assertTrue(result.contains("<ol>"));
        assertTrue(result.contains("<li>"));
    }

    @Test
    @DisplayName("Horizontal rule renders correctly")
    public void testHorizontalRule() {
        String result = MarkdownConverter.convertFragment("text\n\n---");
        assertTrue(result.contains("<hr"));
    }
}
