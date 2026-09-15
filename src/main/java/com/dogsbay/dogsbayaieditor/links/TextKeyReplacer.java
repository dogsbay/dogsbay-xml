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

package com.dogsbay.dogsbayaieditor.links;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Finds and replaces literal text occurrences in <em>regular text
 * content</em> only — the engine under "Create Key from Selected Text →
 * Replace All". Skipped: everything inside tags (attribute values),
 * comments, CDATA, processing instructions, and the subtree of
 * code-like / definition elements where a {@code <ph keyref>} would be
 * wrong. Matches are whole-word: not embedded in a longer
 * letter-or-digit run.
 */
public final class TextKeyReplacer {

    /**
     * Elements whose text must never be key-replaced: code contexts
     * (literal text), mention elements (names, not prose), and keyword —
     * the key definition itself.
     */
    private static final Set<String> EXCLUDED_ELEMENTS = Set.of(
            "codeblock", "codeph", "coderef", "pre", "lines", "screen",
            "msgblock", "filepath", "userinput", "systemoutput",
            "cmdname", "apiname", "parmname", "varname", "synph",
            "xmlelement", "xmlatt", "keyword");

    /** One replaceable occurrence. */
    public record Occurrence(File file, int line, String context) {
    }

    private TextKeyReplacer() {
    }

    /**
     * Occurrences of {@code text} in the replaceable content of every
     * {@code .dita} topic under {@code root}.
     */
    public static List<Occurrence> scan(File root, String text) throws IOException {
        List<Occurrence> occurrences = new ArrayList<>();
        try (var paths = Files.walk(root.toPath())) {
            for (var path : paths.filter(p -> p.toString().toLowerCase().endsWith(".dita")
                    && !com.dogsbay.dogsbayaieditor.ditaproject.FileSet.inHiddenFolder(root.toPath(), p))
                    .sorted().toList()) {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                for (int[] span : occurrenceSpans(content, text)) {
                    occurrences.add(new Occurrence(path.toFile(),
                            lineOf(content, span[0]),
                            contextAround(content, span[0], span[1])));
                }
            }
        }
        return occurrences;
    }

    /**
     * Replaces every replaceable occurrence of {@code text} in
     * {@code content} with {@code replacement}; null when there are none.
     */
    public static String replace(String content, String text, String replacement) {
        List<int[]> spans = occurrenceSpans(content, text);
        if (spans.isEmpty()) {
            return null;
        }
        StringBuilder out = new StringBuilder(content.length());
        int last = 0;
        for (int[] span : spans) {
            out.append(content, last, span[0]).append(replacement);
            last = span[1];
        }
        out.append(content, last, content.length());
        return out.toString();
    }

    /**
     * Spans of whole-word occurrences of {@code text} in replaceable text
     * content, in document order.
     */
    static List<int[]> occurrenceSpans(String content, String text) {
        List<int[]> spans = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return spans;
        }
        Deque<String> stack = new ArrayDeque<>();
        int i = 0;
        int length = content.length();
        while (i < length) {
            char c = content.charAt(i);
            if (c == '<') {
                if (content.startsWith("<!--", i)) {
                    i = skipPast(content, i, "-->");
                } else if (content.startsWith("<![CDATA[", i)) {
                    i = skipPast(content, i, "]]>");
                } else if (content.startsWith("<?", i)) {
                    i = skipPast(content, i, "?>");
                } else if (content.startsWith("<!", i)) {
                    int gt = content.indexOf('>', i);
                    i = gt < 0 ? length : gt + 1;
                } else {
                    int gt = tagEnd(content, i);
                    if (gt < 0) {
                        return spans;               // malformed tail — stop
                    }
                    if (content.charAt(i + 1) == '/') {
                        if (!stack.isEmpty()) {
                            stack.pop();
                        }
                    } else if (content.charAt(gt - 1) != '/') {
                        stack.push(tagName(content, i));
                    }
                    i = gt + 1;
                }
                continue;
            }
            int next = content.indexOf('<', i);
            if (next < 0) {
                next = length;
            }
            if (!isExcluded(stack)) {
                int from = i;
                int idx;
                while ((idx = content.indexOf(text, from)) >= 0
                        && idx + text.length() <= next) {
                    if (idx >= i && isWholeWord(content, idx, idx + text.length())) {
                        spans.add(new int[] {idx, idx + text.length()});
                        from = idx + text.length();
                    } else {
                        from = idx + 1;
                    }
                    if (from >= next) {
                        break;
                    }
                }
            }
            i = next;
        }
        return spans;
    }

    private static boolean isExcluded(Deque<String> stack) {
        for (String name : stack) {
            if (EXCLUDED_ELEMENTS.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWholeWord(String content, int start, int end) {
        if (start > 0 && Character.isLetterOrDigit(content.charAt(start - 1))) {
            return false;
        }
        return end >= content.length()
                || !Character.isLetterOrDigit(content.charAt(end));
    }

    private static String tagName(String content, int lt) {
        int p = lt + 1;
        while (p < content.length() && !Character.isWhitespace(content.charAt(p))
                && content.charAt(p) != '>' && content.charAt(p) != '/') {
            p++;
        }
        String name = content.substring(lt + 1, p);
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private static int tagEnd(String content, int lt) {
        char inQuote = 0;
        for (int i = lt; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inQuote != 0) {
                if (c == inQuote) {
                    inQuote = 0;
                }
            } else if (c == '"' || c == '\'') {
                inQuote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    private static int skipPast(String content, int from, String marker) {
        int idx = content.indexOf(marker, from);
        return idx < 0 ? content.length() : idx + marker.length();
    }

    private static int lineOf(String content, int idx) {
        int line = 1;
        for (int i = 0; i < idx; i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String contextAround(String content, int start, int end) {
        int from = Math.max(0, start - 24);
        int to = Math.min(content.length(), end + 24);
        return (from > 0 ? "…" : "") + content.substring(from, to)
                .replaceAll("\\s+", " ") + (to < content.length() ? "…" : "");
    }
}
