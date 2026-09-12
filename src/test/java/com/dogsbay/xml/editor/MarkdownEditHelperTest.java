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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JTextArea;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MarkdownEditHelper text manipulation methods.
 */
class MarkdownEditHelperTest {

    private JTextArea editor;

    @BeforeEach
    void setUp() {
        editor = new JTextArea();
    }

    @Test
    void wrapSelectionWithSelectedText() {
        editor.setText("hello world");
        editor.setSelectionStart(6);
        editor.setSelectionEnd(11);
        MarkdownEditHelper.wrapSelection(editor, "**", "**", "bold");
        assertEquals("hello **world**", editor.getText());
    }

    @Test
    void wrapSelectionWithNoSelection() {
        editor.setText("hello ");
        editor.setCaretPosition(6);
        MarkdownEditHelper.wrapSelection(editor, "**", "**", "bold text");
        assertEquals("hello **bold text**", editor.getText());
        // Placeholder should be selected
        assertEquals("bold text", editor.getSelectedText());
    }

    @Test
    void toggleLinePrefixAddsPrefix() {
        editor.setText("first line\nsecond line\nthird line");
        editor.setSelectionStart(0);
        editor.setSelectionEnd(10);
        MarkdownEditHelper.toggleLinePrefix(editor, "- ");
        assertEquals("- first line\nsecond line\nthird line", editor.getText());
    }

    @Test
    void toggleLinePrefixRemovesPrefix() {
        editor.setText("- first line\nsecond line");
        editor.setSelectionStart(0);
        editor.setSelectionEnd(12);
        MarkdownEditHelper.toggleLinePrefix(editor, "- ");
        assertEquals("first line\nsecond line", editor.getText());
    }

    @Test
    void toggleLinePrefixMultipleLines() {
        editor.setText("line one\nline two\nline three");
        editor.setSelectionStart(0);
        editor.setSelectionEnd(27);
        MarkdownEditHelper.toggleLinePrefix(editor, "> ");
        assertEquals("> line one\n> line two\n> line three", editor.getText());
    }

    @Test
    void cycleHeadingFromPlainToH1() {
        editor.setText("My Title");
        editor.setCaretPosition(3);
        MarkdownEditHelper.cycleHeading(editor);
        assertEquals("# My Title", editor.getText());
    }

    @Test
    void cycleHeadingFromH1ToH2() {
        editor.setText("# My Title");
        editor.setCaretPosition(5);
        MarkdownEditHelper.cycleHeading(editor);
        assertEquals("## My Title", editor.getText());
    }

    @Test
    void cycleHeadingFromH2ToH3() {
        editor.setText("## My Title");
        editor.setCaretPosition(5);
        MarkdownEditHelper.cycleHeading(editor);
        assertEquals("### My Title", editor.getText());
    }

    @Test
    void cycleHeadingFromH3ToPlain() {
        editor.setText("### My Title");
        editor.setCaretPosition(5);
        MarkdownEditHelper.cycleHeading(editor);
        assertEquals("My Title", editor.getText());
    }

    @Test
    void insertBlockAtEndOfLine() {
        editor.setText("some text");
        editor.setCaretPosition(9);
        MarkdownEditHelper.insertBlock(editor, "---\n");
        assertEquals("some text\n---\n", editor.getText());
    }

    @Test
    void insertBlockAtStartOfLine() {
        editor.setText("some text\n");
        editor.setCaretPosition(10);
        MarkdownEditHelper.insertBlock(editor, "---\n");
        assertEquals("some text\n---\n", editor.getText());
    }

    @Test
    void insertTable() {
        editor.setText("text\n");
        editor.setCaretPosition(5);
        MarkdownEditHelper.insertTable(editor);
        String result = editor.getText();
        assertTrue(result.contains("| Header | Header |"));
        assertTrue(result.contains("|--------|--------|"));
        assertTrue(result.contains("| Cell   | Cell   |"));
    }

    @Test
    void markdownToolbarClassLoads() {
        assertNotNull(MarkdownToolbar.class);
    }

    @Test
    void markdownToolbarIconsClassLoads() {
        assertNotNull(MarkdownToolbarIcons.class);
    }
}
