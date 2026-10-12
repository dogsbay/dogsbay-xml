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
package com.dogsbay.xml.review;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal XML tokenizer that keeps source offsets. The review model works
 * by textual splices into the document's exact source, as the map editor and
 * the refactorings do, so it needs to know where every tag and text run
 * sits, not a DOM. Well-formed input is assumed; on malformed input the
 * tokens degrade to text rather than throwing.
 */
public final class XmlTokens {

    public enum Kind { START, END, EMPTY, TEXT, COMMENT, PI, CDATA, DOCTYPE, DECL }

    /**
     * One token. {@code name} and {@code attrs} are set for tags; {@code depth}
     * is the element nesting depth at the token (a START token's own depth is
     * that of its parent's children); {@code parent} indexes the enclosing
     * START token, or -1 at the root.
     */
    public record Token(Kind kind, int start, int end, String name, Map<String, String> attrs,
            int depth, int parent) {

        public String text(String source) {
            return source.substring(start, end);
        }

        public boolean isTag() {
            return kind == Kind.START || kind == Kind.END || kind == Kind.EMPTY;
        }
    }

    private XmlTokens() {
    }

    public static List<Token> tokenize(String src) {
        List<Token> out = new ArrayList<>();
        List<Integer> stack = new ArrayList<>();
        int i = 0;
        int n = src.length();
        while (i < n) {
            int depth = stack.size();
            int parent = stack.isEmpty() ? -1 : stack.get(stack.size() - 1);
            if (src.charAt(i) != '<') {
                int j = src.indexOf('<', i);
                if (j < 0) {
                    j = n;
                }
                out.add(new Token(Kind.TEXT, i, j, null, Map.of(), depth, parent));
                i = j;
                continue;
            }
            if (src.startsWith("<!--", i)) {
                int j = src.indexOf("-->", i + 4);
                j = j < 0 ? n : j + 3;
                out.add(new Token(Kind.COMMENT, i, j, null, Map.of(), depth, parent));
                i = j;
            } else if (src.startsWith("<![CDATA[", i)) {
                int j = src.indexOf("]]>", i + 9);
                j = j < 0 ? n : j + 3;
                out.add(new Token(Kind.CDATA, i, j, null, Map.of(), depth, parent));
                i = j;
            } else if (src.startsWith("<?", i)) {
                int j = src.indexOf("?>", i + 2);
                j = j < 0 ? n : j + 2;
                out.add(new Token(Kind.PI, i, j, null, Map.of(), depth, parent));
                i = j;
            } else if (src.startsWith("<!DOCTYPE", i) || src.startsWith("<!doctype", i)) {
                int j = skipDoctype(src, i);
                out.add(new Token(Kind.DOCTYPE, i, j, null, Map.of(), depth, parent));
                i = j;
            } else if (src.startsWith("<!", i)) {
                int j = src.indexOf('>', i);
                j = j < 0 ? n : j + 1;
                out.add(new Token(Kind.DECL, i, j, null, Map.of(), depth, parent));
                i = j;
            } else if (src.startsWith("</", i)) {
                int j = src.indexOf('>', i);
                j = j < 0 ? n : j + 1;
                String name = src.substring(Math.min(i + 2, j), Math.max(i + 2, j - 1)).trim();
                if (!stack.isEmpty()) {
                    stack.remove(stack.size() - 1);
                }
                int d = stack.size();
                int p = stack.isEmpty() ? -1 : stack.get(stack.size() - 1);
                out.add(new Token(Kind.END, i, j, name, Map.of(), d, p));
                i = j;
            } else if (i + 1 >= n) {
                // a lone trailing '<': text, not a tag
                out.add(new Token(Kind.TEXT, i, n, null, Map.of(), depth, parent));
                i = n;
            } else {
                int j = endOfTag(src, i);
                String inner = src.substring(i + 1, Math.max(i + 1, j - 1));
                boolean empty = inner.endsWith("/");
                if (empty) {
                    inner = inner.substring(0, inner.length() - 1);
                }
                int nameEnd = 0;
                while (nameEnd < inner.length() && !Character.isWhitespace(inner.charAt(nameEnd))) {
                    nameEnd++;
                }
                String name = inner.substring(0, nameEnd);
                Map<String, String> attrs = parseAttrs(inner.substring(nameEnd));
                Token t = new Token(empty ? Kind.EMPTY : Kind.START, i, j, name, attrs, depth, parent);
                out.add(t);
                if (!empty) {
                    stack.add(out.size() - 1);
                }
                i = j;
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** True when every START has its END and nothing closes what was not opened. */
    public static boolean isBalanced(List<Token> tokens) {
        int depth = 0;
        for (Token t : tokens) {
            if (t.kind() == Kind.START) {
                depth++;
            } else if (t.kind() == Kind.END) {
                if (--depth < 0) {
                    return false;
                }
            }
        }
        return depth == 0;
    }

    /** The source offset just past the element starting at {@code startIndex}; its own end when unclosed. */
    public static int endOf(List<Token> tokens, int startIndex) {
        int close = closeOf(tokens, startIndex);
        return close < 0 ? tokens.get(startIndex).end() : tokens.get(close).end();
    }

    /**
     * Render a start or empty tag from a name and attributes. Used for every
     * tag the review model touches, so attribute editing never regexes over
     * raw tag text; untouched tags keep their exact source.
     */
    public static String renderTag(String name, Map<String, String> attrs, boolean empty) {
        StringBuilder sb = new StringBuilder("<").append(name);
        attrs.forEach((k, v) -> sb.append(' ').append(k).append("=\"").append(escapeAttr(v)).append('"'));
        return sb.append(empty ? "/>" : ">").toString();
    }

    /** The index of the END token closing the START token at {@code startIndex}, or -1. */
    public static int closeOf(List<Token> tokens, int startIndex) {
        Token start = tokens.get(startIndex);
        if (start.kind() == Kind.EMPTY) {
            return startIndex;
        }
        int depth = 0;
        for (int k = startIndex; k < tokens.size(); k++) {
            Token t = tokens.get(k);
            if (t.kind() == Kind.START) {
                depth++;
            } else if (t.kind() == Kind.END) {
                depth--;
                if (depth == 0) {
                    return k;
                }
            }
        }
        return -1;
    }

    /** Index of the nearest enclosing START token whose name is in {@code names}, or -1. */
    public static int ancestor(List<Token> tokens, int index, java.util.Set<String> names) {
        int p = tokens.get(index).parent();
        while (p >= 0) {
            if (names.contains(tokens.get(p).name())) {
                return p;
            }
            p = tokens.get(p).parent();
        }
        return -1;
    }

    static Map<String, String> parseAttrs(String s) {
        Map<String, String> attrs = new LinkedHashMap<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            int nameStart = i;
            while (i < n && s.charAt(i) != '=' && !Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            if (i == nameStart) {
                break;
            }
            String name = s.substring(nameStart, i);
            while (i < n && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            if (i < n && s.charAt(i) == '=') {
                i++;
                while (i < n && Character.isWhitespace(s.charAt(i))) {
                    i++;
                }
                if (i < n && (s.charAt(i) == '"' || s.charAt(i) == '\'')) {
                    char q = s.charAt(i);
                    int close = s.indexOf(q, i + 1);
                    if (close < 0) {
                        close = n;
                    }
                    attrs.put(name, unescape(s.substring(i + 1, close)));
                    i = Math.min(n, close + 1);
                } else {
                    int e = i;
                    while (e < n && !Character.isWhitespace(s.charAt(e))) {
                        e++;
                    }
                    attrs.put(name, s.substring(i, e));
                    i = e;
                }
            } else {
                attrs.put(name, "");
            }
        }
        return attrs;
    }

    private static int endOfTag(String src, int i) {
        boolean inQuote = false;
        char q = 0;
        for (int k = i + 1; k < src.length(); k++) {
            char c = src.charAt(k);
            if (inQuote) {
                if (c == q) {
                    inQuote = false;
                }
            } else if (c == '"' || c == '\'') {
                inQuote = true;
                q = c;
            } else if (c == '>') {
                return k + 1;
            }
        }
        return src.length();
    }

    private static int skipDoctype(String src, int i) {
        int depth = 0;
        for (int k = i; k < src.length(); k++) {
            char c = src.charAt(k);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            } else if (c == '>' && depth <= 0) {
                return k + 1;
            }
        }
        return src.length();
    }

    public static String unescape(String s) {
        if (s.indexOf('&') < 0) {
            return s;
        }
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    public static String escapeText(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String escapeAttr(String s) {
        return escapeText(s).replace("\"", "&quot;");
    }
}
