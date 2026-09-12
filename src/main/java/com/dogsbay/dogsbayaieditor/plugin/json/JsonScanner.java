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

package com.dogsbay.dogsbayaieditor.plugin.json;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.dogsbay.xml.editor.Constants;

/**
 * Scanner for JSON/JSONC content. Classifies text into token types for
 * syntax highlighting. Compatible with the scanner API used by View classes.
 */
public class JsonScanner {

    private final Document document;
    private List<Token> tokens = Collections.emptyList();
    private int nextIndex = 0;

    public int token = Constants.JSON_TEXT;

    public JsonScanner(Document document) {
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
        token = Constants.JSON_TEXT;
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
        int len = text.length();
        int i = 0;

        while (i < len) {
            char c = text.charAt(i);

            // Whitespace
            if (Character.isWhitespace(c)) {
                int start = i;
                while (i < len && Character.isWhitespace(text.charAt(i))) i++;
                result.add(new Token(Constants.JSON_TEXT, start, i));
                continue;
            }

            // Line comment (JSONC)
            if (c == '/' && i + 1 < len && text.charAt(i + 1) == '/') {
                int start = i;
                while (i < len && text.charAt(i) != '\n') i++;
                result.add(new Token(Constants.JSON_COMMENT, start, i));
                continue;
            }

            // Block comment (JSONC)
            if (c == '/' && i + 1 < len && text.charAt(i + 1) == '*') {
                int start = i;
                i += 2;
                while (i + 1 < len && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) i++;
                if (i + 1 < len) i += 2;
                else i = len;
                result.add(new Token(Constants.JSON_COMMENT, start, i));
                continue;
            }

            // String
            if (c == '"') {
                int start = i;
                i++;
                while (i < len && text.charAt(i) != '"') {
                    if (text.charAt(i) == '\\' && i + 1 < len) i++;
                    i++;
                }
                if (i < len) i++;

                int tokenType = isKey(text, i) ? Constants.JSON_KEY : Constants.JSON_STRING;
                result.add(new Token(tokenType, start, i));
                continue;
            }

            // Number
            if (c == '-' || (c >= '0' && c <= '9')) {
                int start = i;
                if (c == '-') i++;
                if (i < len && text.charAt(i) >= '0' && text.charAt(i) <= '9') {
                    while (i < len && ((text.charAt(i) >= '0' && text.charAt(i) <= '9')
                            || text.charAt(i) == '.' || text.charAt(i) == 'e' || text.charAt(i) == 'E'
                            || text.charAt(i) == '+' || text.charAt(i) == '-')) {
                        i++;
                    }
                    result.add(new Token(Constants.JSON_NUMBER, start, i));
                } else {
                    result.add(new Token(Constants.JSON_PUNCTUATION, start, start + 1));
                    i = start + 1;
                }
                continue;
            }

            // Keywords: true, false, null
            if (c == 't' && matches(text, i, "true")) {
                result.add(new Token(Constants.JSON_KEYWORD, i, i + 4));
                i += 4;
                continue;
            }
            if (c == 'f' && matches(text, i, "false")) {
                result.add(new Token(Constants.JSON_KEYWORD, i, i + 5));
                i += 5;
                continue;
            }
            if (c == 'n' && matches(text, i, "null")) {
                result.add(new Token(Constants.JSON_KEYWORD, i, i + 4));
                i += 4;
                continue;
            }

            // Punctuation
            if (c == '{' || c == '}' || c == '[' || c == ']' || c == ':' || c == ',') {
                result.add(new Token(Constants.JSON_PUNCTUATION, i, i + 1));
                i++;
                continue;
            }

            // Unknown
            result.add(new Token(Constants.JSON_ERROR, i, i + 1));
            i++;
        }

        tokens = result;
    }

    private boolean matches(String text, int pos, String keyword) {
        return pos + keyword.length() <= text.length()
                && text.substring(pos, pos + keyword.length()).equals(keyword);
    }

    private boolean isKey(String text, int pos) {
        int len = text.length();
        while (pos < len && Character.isWhitespace(text.charAt(pos))) pos++;
        return pos < len && text.charAt(pos) == ':';
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
