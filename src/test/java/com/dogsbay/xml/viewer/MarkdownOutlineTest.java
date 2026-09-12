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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MarkdownOutlinePanel heading parsing logic.
 */
class MarkdownOutlineTest {

    @Test
    void parseHeadingsEmpty() {
        List<MarkdownOutlinePanel.HeadingInfo> headings = MarkdownOutlinePanel.parseHeadings("");
        assertTrue(headings.isEmpty());
    }

    @Test
    void parseHeadingsNull() {
        List<MarkdownOutlinePanel.HeadingInfo> headings = MarkdownOutlinePanel.parseHeadings(null);
        assertTrue(headings.isEmpty());
    }

    @Test
    void parseHeadingsNoHeadings() {
        List<MarkdownOutlinePanel.HeadingInfo> headings = MarkdownOutlinePanel.parseHeadings(
                "Just some text\nwith no headings.\n");
        assertTrue(headings.isEmpty());
    }

    @Test
    void parseHeadingsSingleH1() {
        List<MarkdownOutlinePanel.HeadingInfo> headings = MarkdownOutlinePanel.parseHeadings("# Title");
        assertEquals(1, headings.size());
        assertEquals("Title", headings.get(0).text);
        assertEquals(1, headings.get(0).level);
        assertEquals(0, headings.get(0).lineNumber);
    }

    @Test
    void parseHeadingsMultipleLevels() {
        String md = "# Title\n## Section\n### Subsection\n";
        List<MarkdownOutlinePanel.HeadingInfo> headings = MarkdownOutlinePanel.parseHeadings(md);
        assertEquals(3, headings.size());
        assertEquals(1, headings.get(0).level);
        assertEquals(2, headings.get(1).level);
        assertEquals(3, headings.get(2).level);
    }

    @Test
    void parseHeadingsLineNumbers() {
        String md = "# First\nSome text\n## Second\nMore text\n### Third\n";
        List<MarkdownOutlinePanel.HeadingInfo> headings = MarkdownOutlinePanel.parseHeadings(md);
        assertEquals(3, headings.size());
        assertEquals(0, headings.get(0).lineNumber);
        assertEquals(2, headings.get(1).lineNumber);
        assertEquals(4, headings.get(2).lineNumber);
    }

    @Test
    void parseHeadingsIgnoresCodeBlockHeadings() {
        String md = "# Real Heading\n```\n# Not a heading\n```\n## Another Heading\n";
        List<MarkdownOutlinePanel.HeadingInfo> headings = MarkdownOutlinePanel.parseHeadings(md);
        assertEquals(2, headings.size());
        assertEquals("Real Heading", headings.get(0).text);
        assertEquals("Another Heading", headings.get(1).text);
    }

    @Test
    void parseHeadingsH4H5H6() {
        String md = "#### H4\n##### H5\n###### H6\n";
        List<MarkdownOutlinePanel.HeadingInfo> headings = MarkdownOutlinePanel.parseHeadings(md);
        assertEquals(3, headings.size());
        assertEquals(4, headings.get(0).level);
        assertEquals(5, headings.get(1).level);
        assertEquals(6, headings.get(2).level);
    }

    @Test
    void parseHeadingsWithInlineFormatting() {
        String md = "# **Bold** heading\n## `Code` heading\n";
        List<MarkdownOutlinePanel.HeadingInfo> headings = MarkdownOutlinePanel.parseHeadings(md);
        assertEquals(2, headings.size());
        // flexmark getText() returns the raw text content
        assertFalse(headings.get(0).text.isEmpty());
        assertFalse(headings.get(1).text.isEmpty());
    }

    @Test
    void headingInfoToString() {
        MarkdownOutlinePanel.HeadingInfo info = new MarkdownOutlinePanel.HeadingInfo("Test", 2, 5);
        assertEquals("Test", info.toString());
    }

    @Test
    void outlinePanelClassLoads() {
        assertNotNull(OutlinePanel.class);
    }
}
