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

/**
 * Locates the navigation-attribute value under a text offset — the basis for
 * "Go to Definition" / "Find Usages" on a right-clicked {@code keyref},
 * {@code conkeyref}, {@code href}, {@code conref}, or {@code src} value.
 *
 * <p>Pure text scanning: no parse tree, tolerant of malformed mid-edit XML
 * (returns null instead of throwing). Handles both quote characters and
 * {@code >} inside attribute values (legal in XML).
 */
public final class AttributeReferenceLocator {

    /** Attributes that navigate somewhere. */
    private static final Set<String> NAV_ATTRIBUTES =
            Set.of("keyref", "conkeyref", "href", "conref", "src");

    /** How far back to look for the enclosing tag's '<'. */
    private static final int MAX_TAG_LENGTH = 4096;

    /**
     * A navigation attribute whose value contains the queried offset.
     *
     * @param element    local name of the enclosing element
     * @param attribute  the attribute name (keyref, href, ...)
     * @param value      the attribute value (unescaped as written)
     * @param valueStart offset of the first value character in the text
     * @param valueEnd   offset just past the last value character
     */
    public record Located(String element, String attribute, String value,
                          int valueStart, int valueEnd) {

        /** True for keyref/conkeyref. */
        public boolean isKeyReference() {
            return "keyref".equals(attribute) || "conkeyref".equals(attribute);
        }

        /** Key name for key references (first path segment). */
        public String keyName() {
            if (!isKeyReference()) {
                return null;
            }
            int slash = value.indexOf('/');
            return slash >= 0 ? value.substring(0, slash) : value;
        }
    }

    private AttributeReferenceLocator() {
    }

    /**
     * The navigation attribute whose value spans {@code offset}, or null
     * when the offset is in text content, an attribute name, a non-navigation
     * attribute, or anything unparseable.
     */
    public static Located locate(String text, int offset) {
        if (text == null || offset < 0 || offset > text.length()) {
            return null;
        }

        // Find the enclosing tag's '<': the nearest '<' at or before offset.
        int tagStart = text.lastIndexOf('<', Math.min(offset, text.length() - 1));
        if (tagStart < 0 || offset - tagStart > MAX_TAG_LENGTH) {
            return null;
        }
        // Not a start tag we can navigate from
        if (tagStart + 1 >= text.length()) {
            return null;
        }
        char first = text.charAt(tagStart + 1);
        if (!Character.isLetter(first) && first != '_') {
            return null;            // </close>, <!--, <?pi, <!DOCTYPE
        }

        // Element name
        int i = tagStart + 1;
        int nameStart = i;
        while (i < text.length() && !Character.isWhitespace(text.charAt(i))
                && text.charAt(i) != '>' && text.charAt(i) != '/') {
            i++;
        }
        String rawName = text.substring(nameStart, i);
        int colon = rawName.indexOf(':');
        String element = colon >= 0 ? rawName.substring(colon + 1) : rawName;

        // Walk attributes: name (= ("..."|'...'))?
        int limit = Math.min(text.length(), tagStart + MAX_TAG_LENGTH);
        while (i < limit) {
            // skip whitespace
            while (i < limit && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i >= limit) {
                return null;
            }
            char c = text.charAt(i);
            if (c == '>' ) {
                // end of tag before the offset's position was inside a value
                return null;
            }
            if (c == '/') {
                i++;
                continue;
            }
            if (c == '<') {
                return null;        // ran into another tag — malformed
            }

            // attribute name
            int attrStart = i;
            while (i < limit && text.charAt(i) != '='
                    && !Character.isWhitespace(text.charAt(i))
                    && text.charAt(i) != '>' && text.charAt(i) != '/') {
                i++;
            }
            String attrName = text.substring(attrStart, i);
            // skip whitespace before '='
            while (i < limit && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i >= limit || text.charAt(i) != '=') {
                continue;           // valueless attribute fragment — keep walking
            }
            i++;                    // past '='
            while (i < limit && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i >= limit) {
                return null;
            }
            char quote = text.charAt(i);
            if (quote != '"' && quote != '\'') {
                return null;        // unquoted — not XML; bail
            }
            int valueStart = i + 1;
            int valueEnd = text.indexOf(quote, valueStart);
            if (valueEnd < 0) {
                // Unterminated value (mid-edit). If the offset is inside it,
                // we still can't trust the content — bail.
                return null;
            }

            if (offset >= valueStart && offset <= valueEnd) {
                if (!NAV_ATTRIBUTES.contains(attrName)) {
                    return null;    // inside a value, but not a navigation attr
                }
                return new Located(element, attrName,
                        text.substring(valueStart, valueEnd), valueStart, valueEnd);
            }
            i = valueEnd + 1;
        }
        return null;
    }
}
