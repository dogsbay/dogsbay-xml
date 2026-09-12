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

import javax.swing.JTextArea;

public class AsciiDocEditHelperTest {

    @Test
    @DisplayName("wrapSelection wraps selected text with markers")
    public void testWrapWithSelection() {
        JTextArea editor = new JTextArea("hello world");
        editor.setSelectionStart(6);
        editor.setSelectionEnd(11);
        AsciiDocEditHelper.wrapSelection(editor, "*", "*", "bold");
        assertEquals("hello *world*", editor.getText());
    }

    @Test
    @DisplayName("wrapSelection inserts placeholder when nothing selected")
    public void testWrapNoSelection() {
        JTextArea editor = new JTextArea("hello ");
        editor.setCaretPosition(6);
        AsciiDocEditHelper.wrapSelection(editor, "`", "`", "code");
        assertEquals("hello `code`", editor.getText());
    }

    @Test
    @DisplayName("toggleLinePrefix adds prefix to line")
    public void testAddLinePrefix() {
        JTextArea editor = new JTextArea("item text");
        editor.setCaretPosition(0);
        AsciiDocEditHelper.toggleLinePrefix(editor, "* ");
        assertEquals("* item text", editor.getText());
    }

    @Test
    @DisplayName("toggleLinePrefix removes existing prefix")
    public void testRemoveLinePrefix() {
        JTextArea editor = new JTextArea("* item text");
        editor.setCaretPosition(3);
        AsciiDocEditHelper.toggleLinePrefix(editor, "* ");
        assertEquals("item text", editor.getText());
    }

    @Test
    @DisplayName("cycleSection adds = prefix to plain line")
    public void testCycleSectionFromNone() {
        JTextArea editor = new JTextArea("Title");
        editor.setCaretPosition(0);
        AsciiDocEditHelper.cycleSection(editor);
        assertEquals("= Title", editor.getText());
    }

    @Test
    @DisplayName("cycleSection increments = level")
    public void testCycleSectionIncrement() {
        JTextArea editor = new JTextArea("= Title");
        editor.setCaretPosition(2);
        AsciiDocEditHelper.cycleSection(editor);
        assertEquals("== Title", editor.getText());
    }

    @Test
    @DisplayName("cycleSection wraps from ===== back to plain")
    public void testCycleSectionWrap() {
        JTextArea editor = new JTextArea("===== Title");
        editor.setCaretPosition(6);
        AsciiDocEditHelper.cycleSection(editor);
        assertEquals("Title", editor.getText());
    }

    @Test
    @DisplayName("insertBlock inserts delimiters around content")
    public void testInsertBlock() {
        JTextArea editor = new JTextArea("");
        editor.setCaretPosition(0);
        AsciiDocEditHelper.insertBlock(editor, "----", "code");
        String text = editor.getText();
        assertTrue(text.startsWith("----\n"));
        assertTrue(text.contains("code"));
        assertTrue(text.endsWith("----\n"));
    }

    @Test
    @DisplayName("insertTable inserts AsciiDoc table template")
    public void testInsertTable() {
        JTextArea editor = new JTextArea("");
        editor.setCaretPosition(0);
        AsciiDocEditHelper.insertTable(editor);
        String text = editor.getText();
        assertTrue(text.contains("|==="));
        assertTrue(text.contains("Header"));
    }

    @Test
    @DisplayName("insertAdmonition with selected text")
    public void testInsertAdmonitionWithSelection() {
        JTextArea editor = new JTextArea("Important info");
        editor.setSelectionStart(0);
        editor.setSelectionEnd(14);
        AsciiDocEditHelper.insertAdmonition(editor, "WARNING");
        assertEquals("WARNING: Important info", editor.getText());
    }
}
