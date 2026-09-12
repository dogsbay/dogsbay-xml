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

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

/**
 * Utility methods for AsciiDoc formatting operations in the editor.
 */
public class AsciiDocEditHelper {

    /**
     * Wraps the current selection with prefix/suffix markers.
     * If nothing is selected, inserts the markers with placeholder text.
     */
    public static void wrapSelection(JTextComponent editor, String prefix, String suffix, String placeholder) {
        String selected = editor.getSelectedText();
        int start = editor.getSelectionStart();

        if (selected != null && !selected.isEmpty()) {
            editor.replaceSelection(prefix + selected + suffix);
        } else {
            String text = prefix + placeholder + suffix;
            editor.replaceSelection(text);
            editor.setSelectionStart(start + prefix.length());
            editor.setSelectionEnd(start + prefix.length() + placeholder.length());
        }
    }

    /**
     * Toggles a line prefix (e.g. "* " for lists, "= " for sections).
     * If the current line starts with the prefix, removes it.
     * Otherwise, adds it.
     */
    public static void toggleLinePrefix(JTextComponent editor, String prefix) {
        try {
            Document doc = editor.getDocument();
            int caretPos = editor.getCaretPosition();
            int lineStart = getLineStart(doc, caretPos);
            int lineEnd = getLineEnd(doc, caretPos);
            String lineText = doc.getText(lineStart, lineEnd - lineStart);

            if (lineText.startsWith(prefix)) {
                doc.remove(lineStart, prefix.length());
            } else {
                doc.insertString(lineStart, prefix, null);
            }
        } catch (BadLocationException e) {
            // ignore
        }
    }

    /**
     * Cycles the section level on the current line.
     * No prefix → "= ", "= " → "== ", "== " → "=== ", etc.
     * After "===== " wraps back to no prefix.
     */
    public static void cycleSection(JTextComponent editor) {
        try {
            Document doc = editor.getDocument();
            int caretPos = editor.getCaretPosition();
            int lineStart = getLineStart(doc, caretPos);
            int lineEnd = getLineEnd(doc, caretPos);
            String lineText = doc.getText(lineStart, lineEnd - lineStart);

            // Count existing = signs
            int level = 0;
            while (level < lineText.length() && lineText.charAt(level) == '=') {
                level++;
            }

            // Remove existing prefix
            if (level > 0) {
                int removeLen = level;
                if (removeLen < lineText.length() && lineText.charAt(removeLen) == ' ') {
                    removeLen++;
                }
                doc.remove(lineStart, removeLen);
            }

            // Add next level (or wrap to none)
            if (level < 5) {
                String newPrefix = "=".repeat(level + 1) + " ";
                doc.insertString(lineStart, newPrefix, null);
            }
        } catch (BadLocationException e) {
            // ignore
        }
    }

    /**
     * Inserts a block with opening/closing delimiters.
     */
    public static void insertBlock(JTextComponent editor, String delimiter, String placeholder) {
        String selected = editor.getSelectedText();
        String content = (selected != null && !selected.isEmpty()) ? selected : placeholder;
        String block = delimiter + "\n" + content + "\n" + delimiter + "\n";
        editor.replaceSelection(block);
    }

    /**
     * Inserts an AsciiDoc table template.
     */
    public static void insertTable(JTextComponent editor) {
        String table =
            "|===\n" +
            "| Header 1 | Header 2 | Header 3\n" +
            "\n" +
            "| Cell 1 | Cell 2 | Cell 3\n" +
            "| Cell 4 | Cell 5 | Cell 6\n" +
            "|===\n";
        editor.replaceSelection(table);
    }

    /**
     * Inserts an admonition block.
     */
    public static void insertAdmonition(JTextComponent editor, String type) {
        String selected = editor.getSelectedText();
        if (selected != null && !selected.isEmpty()) {
            editor.replaceSelection(type + ": " + selected);
        } else {
            String text = type + ": ";
            int pos = editor.getCaretPosition();
            editor.replaceSelection(text);
            editor.setCaretPosition(pos + text.length());
        }
    }

    private static int getLineStart(Document doc, int offset) throws BadLocationException {
        String text = doc.getText(0, offset);
        int lineStart = text.lastIndexOf('\n');
        return (lineStart >= 0) ? lineStart + 1 : 0;
    }

    private static int getLineEnd(Document doc, int offset) throws BadLocationException {
        String text = doc.getText(offset, doc.getLength() - offset);
        int lineEnd = text.indexOf('\n');
        return (lineEnd >= 0) ? offset + lineEnd : doc.getLength();
    }
}
