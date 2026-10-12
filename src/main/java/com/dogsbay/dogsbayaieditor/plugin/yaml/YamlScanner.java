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

package com.dogsbay.dogsbayaieditor.plugin.yaml;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.dogsbay.xml.editor.Constants;

/**
 * Scanner for YAML content. Classifies text into token types for
 * syntax highlighting: keys, strings, numbers, keywords, comments,
 * anchors, aliases, tags, and block indicators.
 */
public class YamlScanner {

    private final Document document;
    private List<Token> tokens = Collections.emptyList();
    private int nextIndex = 0;

    public int token = Constants.YAML_TEXT;

    public YamlScanner(Document document) {
        this.document = document;
    }

    public void setRange(int start, int end) {
        tokenize();
        nextIndex = 0;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).end > start) {
                nextIndex = i;
                token = tokens.get(i).type;
                return;
            }
        }
        token = Constants.YAML_TEXT;
    }

    public int getStartOffset() {
        return nextIndex < tokens.size() ? tokens.get(nextIndex).start : 0;
    }

    public int getEndOffset() {
        return nextIndex < tokens.size() ? tokens.get(nextIndex).end : 0;
    }

    public void scan() {
        nextIndex++;
        if (nextIndex < tokens.size()) {
            token = tokens.get(nextIndex).type;
        } else {
            token = -1;
        }
    }

    private void tokenize() {
        String text;
        try {
            text = document.getText(0, document.getLength());
        } catch (BadLocationException e) {
            tokens = Collections.emptyList();
            return;
        }

        List<Token> result = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        int offset = 0;

        for (String line : lines) {
            tokenizeLine(line, offset, result);
            offset += line.length() + 1; // +1 for \n
        }

        tokens = result;
    }

    private void tokenizeLine(String line, int lineOffset, List<Token> result) {
        int len = line.length();
        int i = 0;

        // Leading whitespace
        while (i < len && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) i++;
        if (i > 0) {
            result.add(new Token(Constants.YAML_TEXT, lineOffset, lineOffset + i));
        }
        if (i >= len) return;

        // Document markers
        if (i == 0 && (line.equals("---") || line.equals("..."))) {
            result.add(new Token(Constants.YAML_PUNCTUATION, lineOffset, lineOffset + 3));
            return;
        }

        // Comment line
        if (line.charAt(i) == '#') {
            result.add(new Token(Constants.YAML_COMMENT, lineOffset + i, lineOffset + len));
            return;
        }

        // Directive
        if (i == 0 && line.charAt(0) == '%') {
            result.add(new Token(Constants.YAML_TAG, lineOffset, lineOffset + len));
            return;
        }

        // List marker
        if (line.charAt(i) == '-' && i + 1 < len && line.charAt(i + 1) == ' ') {
            result.add(new Token(Constants.YAML_PUNCTUATION, lineOffset + i, lineOffset + i + 2));
            i += 2;
            if (i >= len) return;
        }

        // Try to find key: value pattern
        int colonPos = findKeyColon(line, i);

        if (colonPos >= 0) {
            // Key
            result.add(new Token(Constants.YAML_KEY, lineOffset + i, lineOffset + colonPos));
            // Colon
            result.add(new Token(Constants.YAML_PUNCTUATION, lineOffset + colonPos, lineOffset + colonPos + 1));
            // Value part
            int valStart = colonPos + 1;
            if (valStart < len && line.charAt(valStart) == ' ') valStart++;
            if (valStart < len) {
                tokenizeValue(line, valStart, lineOffset, result);
            }
        } else {
            // No colon — treat as value (e.g. array item value)
            tokenizeValue(line, i, lineOffset, result);
        }
    }

    /**
     * Find the colon that separates key from value. Must not be inside
     * a quoted string and must be followed by space, newline, or end of line.
     */
    private int findKeyColon(String line, int from) {
        int len = line.length();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = from; i < len; i++) {
            char c = line.charAt(i);
            if (inDoubleQuote) {
                if (c == '\\' && i + 1 < len) { i++; continue; }
                if (c == '"') inDoubleQuote = false;
            } else if (inSingleQuote) {
                if (c == '\'') inSingleQuote = false;
            } else {
                if (c == '"') inDoubleQuote = true;
                else if (c == '\'') inSingleQuote = true;
                else if (c == ':' && (i + 1 >= len || line.charAt(i + 1) == ' ' || line.charAt(i + 1) == '\t')) {
                    return i;
                }
                else if (c == '#') return -1; // Comment before colon
            }
        }
        return -1;
    }

    private void tokenizeValue(String line, int from, int lineOffset, List<Token> result) {
        int len = line.length();
        if (from >= len) return;

        // Check for inline comment
        int commentPos = findInlineComment(line, from);
        int valueEnd = (commentPos >= 0) ? commentPos : len;

        String value = line.substring(from, valueEnd).trim();

        if (!value.isEmpty()) {
            int tokenType = classifyValue(value);
            result.add(new Token(tokenType, lineOffset + from, lineOffset + valueEnd));
        }

        // Inline comment
        if (commentPos >= 0) {
            result.add(new Token(Constants.YAML_COMMENT, lineOffset + commentPos, lineOffset + len));
        }
    }

    private int findInlineComment(String line, int from) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int len = line.length();

        for (int i = from; i < len; i++) {
            char c = line.charAt(i);
            if (inDoubleQuote) {
                if (c == '\\' && i + 1 < len) { i++; continue; }
                if (c == '"') inDoubleQuote = false;
            } else if (inSingleQuote) {
                if (c == '\'') inSingleQuote = false;
            } else {
                if (c == '"') inDoubleQuote = true;
                else if (c == '\'') inSingleQuote = true;
                else if (c == '#' && i > 0 && line.charAt(i - 1) == ' ') return i;
            }
        }
        return -1;
    }

    private int classifyValue(String value) {
        if (value.isEmpty()) return Constants.YAML_TEXT;

        char first = value.charAt(0);

        // Anchor &name
        if (first == '&') return Constants.YAML_ANCHOR;
        // Alias *name
        if (first == '*') return Constants.YAML_ANCHOR;
        // Tag !!type or !custom
        if (first == '!') return Constants.YAML_TAG;
        // Block indicators | or >
        if (first == '|' || first == '>') return Constants.YAML_PUNCTUATION;
        // Flow indicators { } [ ]
        if (first == '{' || first == '}' || first == '[' || first == ']') return Constants.YAML_PUNCTUATION;

        // Quoted strings
        if ((first == '"' && value.endsWith("\"")) || (first == '\'' && value.endsWith("'"))) {
            return Constants.YAML_STRING;
        }

        // Keywords
        String lower = value.toLowerCase();
        if (lower.equals("true") || lower.equals("false") || lower.equals("yes") || lower.equals("no")
                || lower.equals("on") || lower.equals("off") || lower.equals("null") || lower.equals("~")) {
            return Constants.YAML_KEYWORD;
        }

        // Number
        if (isNumber(value)) return Constants.YAML_NUMBER;

        // Unquoted string
        return Constants.YAML_STRING;
    }

    private boolean isNumber(String s) {
        if (s.isEmpty()) return false;
        int i = 0;
        if (s.charAt(0) == '-' || s.charAt(0) == '+') i++;
        if (i >= s.length()) return false;

        // Hex
        if (s.startsWith("0x", i) || s.startsWith("0X", i)) return true;
        // Octal
        if (s.startsWith("0o", i) || s.startsWith("0O", i)) return true;

        boolean hasDot = false;
        boolean hasDigit = false;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') { hasDigit = true; }
            else if (c == '.' && !hasDot) { hasDot = true; }
            else if ((c == 'e' || c == 'E') && hasDigit) { i++; if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++; continue; }
            else if (c == '_') { /* YAML allows _ in numbers */ }
            else if (c == '.' && s.equals(".inf") || s.equals("-.inf") || s.equals("+.inf") || s.equals(".nan")) return true;
            else return false;
            i++;
        }
        return hasDigit;
    }

    static class Token {
        final int type;
        final int start;
        final int end;

        Token(int type, int start, int end) {
            this.type = type;
            this.start = start;
            this.end = end;
        }
    }
}
