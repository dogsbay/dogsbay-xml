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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locates the innermost extractable element around a caret offset — pure
 * text, no DOM, so it works on the live editor buffer and tests headlessly
 * (same philosophy as {@link AttributeReferenceLocator}).
 *
 * <p>Containers that make no sense to conref (topic roots, body wrappers,
 * title, metadata) are skipped: with the caret inside a {@code <p>} inside
 * a {@code <section>}, the {@code <p>} wins; with the caret directly in
 * {@code <conbody>} whitespace, nothing is found.</p>
 */
public final class ElementAtCaret {

    /**
     * @param element the element's local name
     * @param id      its id attribute, or null
     * @param start   offset of the {@code <}
     * @param end     offset just past the element's last {@code >}
     * @param line    1-based line of the start tag
     */
    public record Located(String element, String id, int start, int end, int line) {
    }

    /** Structural containers that are never the extraction target. */
    private static final Set<String> NOT_EXTRACTABLE = Set.of(
            "dita", "concept", "task", "topic", "reference", "glossentry",
            "troubleshooting", "map", "bookmap",
            "conbody", "body", "taskbody", "refbody", "glossdef", "abstract",
            "title", "shortdesc", "prolog", "topicmeta", "metadata", "related-links");

    private static final Pattern ID_ATTR = Pattern.compile(
            "\\sid\\s*=\\s*[\"']([^\"']*)[\"']");

    private ElementAtCaret() {
    }

    /**
     * The innermost extractable element whose span contains {@code offset},
     * or null. The span includes the element's tags.
     */
    public static Located locate(String text, int offset) {
        java.util.List<Located> chain = locateChain(text, offset);
        return chain.isEmpty() ? null : chain.get(0);
    }

    /**
     * Every extractable element whose span contains {@code offset},
     * innermost first — the candidates an extraction picker offers.
     * Skipped containers don't break the chain: with the caret in a
     * section's title, the chain starts at the section.
     */
    public static java.util.List<Located> locateChain(String text, int offset) {
        java.util.List<Located> chain = new java.util.ArrayList<>();
        if (text == null || offset < 0 || offset > text.length()) {
            return chain;
        }
        Deque<int[]> stack = new ArrayDeque<>();    // {startOffset, nameStart, nameEnd}
        int i = 0;
        int length = text.length();
        while (i < length) {
            int lt = text.indexOf('<', i);
            if (lt < 0) {
                return chain;
            }
            if (text.startsWith("<!--", lt)) {
                int close = text.indexOf("-->", lt);
                i = close < 0 ? length : close + 3;
                continue;
            }
            if (text.startsWith("<![CDATA[", lt)) {
                int close = text.indexOf("]]>", lt);
                i = close < 0 ? length : close + 3;
                continue;
            }
            if (text.startsWith("<?", lt) || text.startsWith("<!", lt)) {
                int close = text.indexOf('>', lt);
                i = close < 0 ? length : close + 1;
                continue;
            }
            int gt = tagEnd(text, lt);
            if (gt < 0) {
                return chain;
            }

            if (text.charAt(lt + 1) == '/') {       // closing tag
                if (!stack.isEmpty()) {
                    int[] open = stack.pop();
                    Located candidate = candidate(text, open, gt + 1, offset);
                    if (candidate != null) {
                        chain.add(candidate);       // ancestors close later
                    }
                }
            } else if (text.charAt(gt - 1) == '/') { // self-closing
                int[] open = {lt, lt + 1, nameEnd(text, lt)};
                Located candidate = candidate(text, open, gt + 1, offset);
                if (candidate != null) {
                    chain.add(candidate);
                }
            } else {                                // opening tag
                stack.push(new int[] {lt, lt + 1, nameEnd(text, lt)});
            }
            i = gt + 1;
        }
        return chain;
    }

    /**
     * A Located for this element when its span contains the offset and it
     * is extractable; null otherwise. Because elements close innermost
     * first, the first non-null candidate is the answer.
     */
    private static Located candidate(String text, int[] open, int end, int offset) {
        int start = open[0];
        if (offset < start || offset >= end) {
            return null;
        }
        String name = text.substring(open[1], open[2]);
        String localName = name.contains(":")
                ? name.substring(name.indexOf(':') + 1) : name;
        if (NOT_EXTRACTABLE.contains(localName)) {
            return null;
        }
        int startTagEnd = tagEnd(text, start);
        String startTag = text.substring(start, startTagEnd + 1);
        Matcher m = ID_ATTR.matcher(startTag);
        String id = m.find() ? m.group(1) : null;
        int line = 1 + (int) text.substring(0, start).chars()
                .filter(c -> c == '\n').count();
        return new Located(localName, id, start, end, line);
    }

    private static int nameEnd(String text, int lt) {
        int p = lt + 1;
        while (p < text.length() && !Character.isWhitespace(text.charAt(p))
                && text.charAt(p) != '>' && text.charAt(p) != '/') {
            p++;
        }
        return p;
    }

    /** Index of the {@code >} ending the tag at {@code lt}, quote-aware. */
    private static int tagEnd(String text, int lt) {
        char inQuote = 0;
        for (int i = lt; i < text.length(); i++) {
            char c = text.charAt(i);
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
}
