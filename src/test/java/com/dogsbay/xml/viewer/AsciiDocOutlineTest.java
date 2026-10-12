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

package com.dogsbay.xml.viewer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class AsciiDocOutlineTest {

    @Test
    @DisplayName("Parses section titles from AsciiDoc")
    public void testParseSections() {
        String doc = "= Document Title\n\n== First Section\n\nSome text.\n\n=== Subsection\n\n== Second Section\n";
        List<AsciiDocOutlinePanel.SectionInfo> sections = AsciiDocOutlinePanel.parseSections(doc);
        assertEquals(4, sections.size());
        assertEquals("Document Title", sections.get(0).title);
        assertEquals(1, sections.get(0).level);
        assertEquals("First Section", sections.get(1).title);
        assertEquals(2, sections.get(1).level);
        assertEquals("Subsection", sections.get(2).title);
        assertEquals(3, sections.get(2).level);
        assertEquals("Second Section", sections.get(3).title);
        assertEquals(2, sections.get(3).level);
    }

    @Test
    @DisplayName("Returns empty list for empty document")
    public void testEmptyDocument() {
        List<AsciiDocOutlinePanel.SectionInfo> sections = AsciiDocOutlinePanel.parseSections("");
        assertTrue(sections.isEmpty());
    }

    @Test
    @DisplayName("Returns empty list for null")
    public void testNullDocument() {
        List<AsciiDocOutlinePanel.SectionInfo> sections = AsciiDocOutlinePanel.parseSections(null);
        assertTrue(sections.isEmpty());
    }

    @Test
    @DisplayName("Handles document with no sections")
    public void testNoSections() {
        List<AsciiDocOutlinePanel.SectionInfo> sections = AsciiDocOutlinePanel.parseSections("Just some text.\nNo headings here.\n");
        assertTrue(sections.isEmpty());
    }

    @Test
    @DisplayName("Tracks line numbers correctly")
    public void testLineNumbers() {
        String doc = "Preamble\n\n= Title\n\nText\n\n== Section\n";
        List<AsciiDocOutlinePanel.SectionInfo> sections = AsciiDocOutlinePanel.parseSections(doc);
        assertEquals(2, sections.size());
        assertEquals(2, sections.get(0).lineNumber); // line 2 (0-indexed)
        assertEquals(6, sections.get(1).lineNumber); // line 6
    }

    @Test
    @DisplayName("Handles all five section levels")
    public void testAllLevels() {
        String doc = "= Level 1\n== Level 2\n=== Level 3\n==== Level 4\n===== Level 5\n";
        List<AsciiDocOutlinePanel.SectionInfo> sections = AsciiDocOutlinePanel.parseSections(doc);
        assertEquals(5, sections.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(i + 1, sections.get(i).level);
        }
    }

    @Test
    @DisplayName("Does not match == without space (not a section title)")
    public void testNotSectionTitle() {
        String doc = "a == b means equality\n";
        List<AsciiDocOutlinePanel.SectionInfo> sections = AsciiDocOutlinePanel.parseSections(doc);
        assertTrue(sections.isEmpty());
    }

    @Test
    @DisplayName("AsciiDocOutlinePanel class can be loaded")
    public void testClassLoading() {
        assertDoesNotThrow(() -> {
            Class.forName("com.dogsbay.xml.viewer.AsciiDocOutlinePanel");
        });
    }
}
