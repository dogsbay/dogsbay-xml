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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import javax.swing.text.PlainDocument;

public class AsciiDocScannerTest {

    private AsciiDocScanner createScanner(String text) throws Exception {
        PlainDocument doc = new PlainDocument();
        doc.insertString(0, text, null);
        return new AsciiDocScanner(doc);
    }

    @Test
    @DisplayName("Detects section titles")
    public void testSectionTitles() throws Exception {
        AsciiDocScanner scanner = createScanner("= Title\n== Section\n=== Subsection\n");
        // First token should be section title
        assertEquals(Constants.AD_SECTION_TITLE, scanner.token);
    }

    @Test
    @DisplayName("Detects attribute entries")
    public void testAttributes() throws Exception {
        AsciiDocScanner scanner = createScanner(":author: John Doe\n:version: 1.0\n");
        assertEquals(Constants.AD_ATTRIBUTE, scanner.token);
    }

    @Test
    @DisplayName("Detects line comments")
    public void testLineComments() throws Exception {
        AsciiDocScanner scanner = createScanner("// This is a comment\nContent here\n");
        assertEquals(Constants.AD_COMMENT, scanner.token);
    }

    @Test
    @DisplayName("Detects admonitions")
    public void testAdmonitions() throws Exception {
        AsciiDocScanner scanner = createScanner("NOTE: This is important\n");
        assertEquals(Constants.AD_ADMONITION, scanner.token);
    }

    @Test
    @DisplayName("Detects list markers")
    public void testListMarkers() throws Exception {
        AsciiDocScanner scanner = createScanner("* First item\n* Second item\n");
        assertEquals(Constants.AD_LIST_MARKER, scanner.token);
    }

    @Test
    @DisplayName("Detects monospace inline")
    public void testMonospace() throws Exception {
        AsciiDocScanner scanner = createScanner("Use `code` here\n");
        assertTrue(findToken(scanner, Constants.AD_MONOSPACE), "Should detect monospace token");
    }

    @Test
    @DisplayName("Detects block delimiters")
    public void testBlockDelimiters() throws Exception {
        AsciiDocScanner scanner = createScanner("----\ncode here\n----\n");
        assertEquals(Constants.AD_BLOCK_DELIMITER, scanner.token);
    }

    @Test
    @DisplayName("Detects link macros")
    public void testLinkMacro() throws Exception {
        AsciiDocScanner scanner = createScanner("See link:https://example.com[Example]\n");
        assertTrue(findToken(scanner, Constants.AD_LINK), "Should detect link token");
    }

    @Test
    @DisplayName("Detects image macros")
    public void testImageMacro() throws Exception {
        AsciiDocScanner scanner = createScanner("image::photo.png[Alt text]\n");
        assertTrue(findToken(scanner, Constants.AD_IMAGE), "Should detect image token");
    }

    @Test
    @DisplayName("Detects cross references")
    public void testXref() throws Exception {
        AsciiDocScanner scanner = createScanner("See <<section-id,Section Title>>\n");
        assertTrue(findToken(scanner, Constants.AD_XREF), "Should detect xref token");
    }

    @Test
    @DisplayName("Handles empty document")
    public void testEmptyDocument() throws Exception {
        AsciiDocScanner scanner = createScanner("");
        assertEquals(-1, scanner.token);
    }

    @Test
    @DisplayName("Detects table delimiters")
    public void testTableDelimiters() throws Exception {
        AsciiDocScanner scanner = createScanner("|===\n| A | B\n| 1 | 2\n|===\n");
        assertTrue(findToken(scanner, Constants.AD_TABLE), "Should detect table token");
    }

    /**
     * Scans through all tokens looking for one with the given type.
     * Handles -1 (gap) tokens by continuing past them.
     */
    private boolean findToken(AsciiDocScanner scanner, int targetType) throws Exception {
        // Check current token first
        if (scanner.token == targetType) return true;
        // Scan through remaining tokens (limit iterations to prevent infinite loop)
        for (int i = 0; i < 10000; i++) {
            scanner.scan();
            if (scanner.token == targetType) return true;
            if (scanner.getEndOffset() >= Integer.MAX_VALUE) break;
        }
        return false;
    }
}
