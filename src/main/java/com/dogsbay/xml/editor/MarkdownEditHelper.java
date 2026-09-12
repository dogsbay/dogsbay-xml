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
 * Static utility methods for inserting Markdown formatting in the editor.
 * Operates directly on a JTextComponent (the XmlEditorPane).
 */
public class MarkdownEditHelper {

    /**
     * Wraps the current selection with prefix and suffix.
     * If nothing is selected, inserts prefix + placeholder + suffix
     * and selects the placeholder.
     */
    public static void wrapSelection(JTextComponent editor, String prefix, String suffix, String placeholder) {
        String selected = editor.getSelectedText();
        int start = editor.getSelectionStart();

        if (selected != null && !selected.isEmpty()) {
            editor.replaceSelection(prefix + selected + suffix);
            editor.setSelectionStart(start + prefix.length());
            editor.setSelectionEnd(start + prefix.length() + selected.length());
        } else {
            String text = prefix + placeholder + suffix;
            editor.replaceSelection(text);
            editor.setSelectionStart(start + prefix.length());
            editor.setSelectionEnd(start + prefix.length() + placeholder.length());
        }
        editor.requestFocus();
    }

    /**
     * Toggles a line prefix (e.g., "# ", "- ", "> ") on the current line(s).
     * If the line already starts with the prefix, removes it.
     * If not, adds it.
     */
    public static void toggleLinePrefix(JTextComponent editor, String prefix) {
        Document doc = editor.getDocument();
        int selStart = editor.getSelectionStart();
        int selEnd = editor.getSelectionEnd();

        try {
            String text = doc.getText(0, doc.getLength());

            // Find the start of the first selected line
            int lineStart = text.lastIndexOf('\n', selStart - 1) + 1;
            // Find the end of the last selected line
            int lineEnd = text.indexOf('\n', selEnd);
            if (lineEnd == -1) lineEnd = text.length();

            String lineText = text.substring(lineStart, lineEnd);
            String[] lines = lineText.split("\n", -1);
            StringBuilder result = new StringBuilder();
            boolean allHavePrefix = true;

            for (String line : lines) {
                if (!line.startsWith(prefix)) {
                    allHavePrefix = false;
                    break;
                }
            }

            for (int i = 0; i < lines.length; i++) {
                if (i > 0) result.append('\n');
                if (allHavePrefix) {
                    result.append(lines[i].substring(prefix.length()));
                } else {
                    result.append(prefix).append(lines[i]);
                }
            }

            doc.remove(lineStart, lineEnd - lineStart);
            doc.insertString(lineStart, result.toString(), null);

            editor.setSelectionStart(lineStart);
            editor.setSelectionEnd(lineStart + result.length());
        } catch (BadLocationException e) {
            // Ignore
        }
        editor.requestFocus();
    }

    /**
     * Inserts text at the cursor position on a new line.
     * If the cursor is not at the start of a line, adds a newline first.
     */
    public static void insertBlock(JTextComponent editor, String block) {
        Document doc = editor.getDocument();
        int pos = editor.getCaretPosition();

        try {
            String text = doc.getText(0, doc.getLength());
            String prefix = "";

            // Ensure we're on a new line
            if (pos > 0 && text.charAt(pos - 1) != '\n') {
                prefix = "\n";
            }

            String toInsert = prefix + block;
            doc.insertString(pos, toInsert, null);
            editor.setCaretPosition(pos + toInsert.length());
        } catch (BadLocationException e) {
            // Ignore
        }
        editor.requestFocus();
    }

    /**
     * Inserts a Markdown table template at the cursor.
     */
    public static void insertTable(JTextComponent editor) {
        String table = "| Header | Header |\n" +
                       "|--------|--------|\n" +
                       "| Cell   | Cell   |\n";
        insertBlock(editor, table);
    }

    /**
     * Cycles the heading level on the current line: no heading → H1 → H2 → H3 → no heading.
     */
    public static void cycleHeading(JTextComponent editor) {
        Document doc = editor.getDocument();
        int pos = editor.getCaretPosition();

        try {
            String text = doc.getText(0, doc.getLength());
            int lineStart = text.lastIndexOf('\n', pos - 1) + 1;
            int lineEnd = text.indexOf('\n', pos);
            if (lineEnd == -1) lineEnd = text.length();

            String line = text.substring(lineStart, lineEnd);

            String newLine;
            if (line.startsWith("### ")) {
                newLine = line.substring(4);
            } else if (line.startsWith("## ")) {
                newLine = "### " + line.substring(3);
            } else if (line.startsWith("# ")) {
                newLine = "## " + line.substring(2);
            } else {
                newLine = "# " + line;
            }

            doc.remove(lineStart, lineEnd - lineStart);
            doc.insertString(lineStart, newLine, null);
            editor.setCaretPosition(lineStart + newLine.length());
        } catch (BadLocationException e) {
            // Ignore
        }
        editor.requestFocus();
    }
}
