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

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locates the profiling value under a caret — pure text, like
 * {@link AttributeReferenceLocator}. Two shapes:
 *
 * <ul>
 *   <li>a profiling attribute in a topic or map
 *       ({@code product="a b"} — the token at the caret is picked out
 *       of multi-value lists);</li>
 *   <li>a {@code val="..."} on a DITAVAL {@code <prop>} rule — the
 *       rule's {@code @att} names the attribute, null when absent
 *       (the rule applies to all attributes; the caller prompts).</li>
 * </ul>
 */
public final class ProfilingValueAtCaret {

    private static final Set<String> PROFILING_ATTRIBUTES = Set.of(
            "product", "audience", "platform", "otherprops", "props");

    private static final Pattern ATTR = Pattern.compile(
            "([A-Za-z_][\\w.:-]*)\\s*=\\s*([\"'])(.*?)\\2");

    /**
     * @param attribute the profiling attribute, or null for a DITAVAL
     *                  rule without {@code @att}
     * @param token     the value token under (or nearest) the caret
     */
    public record Located(String attribute, String token) {
    }

    private ProfilingValueAtCaret() {
    }

    /** The profiling value at {@code offset}, or null. */
    public static Located locate(String text, int offset) {
        if (text == null || offset < 0 || offset > text.length()) {
            return null;
        }
        int lt = text.lastIndexOf('<', Math.min(offset, text.length() - 1));
        if (lt < 0 || lt + 1 >= text.length()) {
            return null;
        }
        char next = text.charAt(lt + 1);
        if (next == '/' || next == '!' || next == '?') {
            return null;
        }
        int gt = tagEnd(text, lt);
        if (gt < 0 || offset > gt) {
            return null;                            // caret is in content, not a tag
        }
        String tag = text.substring(lt, gt + 1);
        String element = tagName(tag);

        Matcher m = ATTR.matcher(tag);
        while (m.find()) {
            int attrStart = lt + m.start();
            int attrEnd = lt + m.end();
            if (offset < attrStart || offset > attrEnd) {
                continue;
            }
            String name = m.group(1);
            String value = m.group(3);
            if (value.trim().isEmpty()) {
                return null;
            }
            if (PROFILING_ATTRIBUTES.contains(name)) {
                return new Located(name, tokenAt(value, offset - (lt + m.start(3))));
            }
            if ("val".equals(name) && "prop".equals(element)) {
                Matcher att = ATTR.matcher(tag);
                String attName = null;
                while (att.find()) {
                    if ("att".equals(att.group(1))) {
                        attName = att.group(3);
                        break;
                    }
                }
                return new Located(attName, value.trim());
            }
            return null;                            // caret on some other attribute
        }
        return null;
    }

    /**
     * The whitespace-separated token containing {@code pos} (an offset
     * into {@code value}); the first token when the caret sits outside
     * the value (e.g. on the attribute name).
     */
    static String tokenAt(String value, int pos) {
        String first = null;
        int i = 0;
        while (i < value.length()) {
            while (i < value.length() && Character.isWhitespace(value.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < value.length() && !Character.isWhitespace(value.charAt(i))) {
                i++;
            }
            if (i > start) {
                String token = value.substring(start, i);
                if (first == null) {
                    first = token;
                }
                if (pos >= start && pos <= i) {
                    return token;
                }
            }
        }
        return first;
    }

    private static String tagName(String tag) {
        int p = 1;
        while (p < tag.length() && !Character.isWhitespace(tag.charAt(p))
                && tag.charAt(p) != '>' && tag.charAt(p) != '/') {
            p++;
        }
        String name = tag.substring(1, p);
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

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
