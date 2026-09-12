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

package com.dogsbay.dogsbayaieditor.links.metadata;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Maps every element of a source document, in document (pre-order) order, to its character
 * offsets in the <em>original</em> source string — so a metadata edit can splice into the
 * source text rather than re-serialize the DOM (which loses the DOCTYPE↔root whitespace, the
 * trailing newline, and inserted-node indentation).
 *
 * <p>Built from one SAX pass over the same source the DOM was parsed from; because both walk
 * the document in pre-order, the i-th element here corresponds to the i-th element of a DOM
 * pre-order traversal. Offsets index into the source {@code String} (UTF-16 chars).
 */
final class SourcePositions {

    /**
     * One element's source span. All offsets are into the source string.
     * {@code openTagStart}…{@code openTagEnd} covers {@code <name …>} (or {@code <name …/>});
     * {@code closeTagStart}…{@code closeTagEnd} covers {@code </name>}. For a self-closing
     * element there is no separate end tag: {@code closeTagStart == closeTagEnd == openTagEnd}.
     * {@code indent} is the run of spaces/tabs immediately preceding {@code openTagStart} on
     * its line (the element's own indentation).
     */
    record Span(int openTagStart, int openTagEnd, int closeTagStart, int closeTagEnd, String indent) {
        boolean selfClosing() {
            return closeTagStart == openTagEnd;
        }
    }

    private final List<Span> preorder;

    private SourcePositions(List<Span> preorder) {
        this.preorder = preorder;
    }

    /** The i-th element in document pre-order (same order as a DOM pre-order walk). */
    Span get(int preorderIndex) {
        return preorder.get(preorderIndex);
    }

    int size() {
        return preorder.size();
    }

    /** Build the index for {@code source}. Throws on malformed XML (caller already parsed it). */
    static SourcePositions of(String source) throws Exception {
        int[] lineStart = lineStartOffsets(source);
        List<Span> out = new ArrayList<>();

        org.xml.sax.XMLReader reader = org.xml.sax.helpers.XMLReaderFactory.createXMLReader(
                "org.apache.xerces.parsers.SAXParser");
        // Don't fetch the DTD — we only need positions, and the file may reference a DTD by
        // public id that isn't resolvable here.
        reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
        reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        reader.setContentHandler(new DefaultHandler() {
            private Locator locator;
            // Per open element: [openTagEnd offset, slot index in `out`].
            private final Deque<int[]> stack = new ArrayDeque<>();

            @Override
            public void setDocumentLocator(Locator l) {
                this.locator = l;
            }

            @Override
            public void startElement(String uri, String localName, String qName, Attributes atts) {
                int openTagEnd = offset(lineStart, locator);   // just after the '>' of the start tag
                int slot = out.size();
                out.add(null);                                 // placeholder, filled at endElement
                stack.push(new int[] {openTagEnd, slot});
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                int closeTagEnd = offset(lineStart, locator);  // just after the end tag (or self-close)
                int[] frame = stack.pop();
                int openTagEnd = frame[0];
                int slot = frame[1];
                int openTagStart = source.lastIndexOf('<', openTagEnd - 1);
                boolean selfClose = openTagEnd >= 2 && source.charAt(openTagEnd - 2) == '/';
                // Scan back for the end tag's '<' rather than assuming "</name>" length — so a
                // namespaced qName or a legal "</name >" (trailing space) still lands correctly.
                int closeTagStart = selfClose ? openTagEnd : source.lastIndexOf('<', closeTagEnd - 1);
                // Fail-safe: if the offsets don't bracket real tags (e.g. a column-vs-UTF-16
                // mismatch from an astral char), refuse rather than splice into the wrong place.
                if (openTagStart < 0 || source.charAt(openTagStart) != '<'
                        || source.charAt(openTagEnd - 1) != '>'
                        || (!selfClose && (closeTagStart < 0 || source.charAt(closeTagEnd - 1) != '>'))) {
                    throw new IllegalStateException("source offset mismatch near <" + qName + ">");
                }
                out.set(slot, new Span(openTagStart, openTagEnd, closeTagStart, closeTagEnd,
                        indentBefore(source, openTagStart)));
            }
        });
        reader.parse(new InputSource(new java.io.StringReader(source)));
        return new SourcePositions(out);
    }

    /** Offset (0-based) of the position the locator currently points at (one past the event). */
    private static int offset(int[] lineStart, Locator locator) {
        int line = locator.getLineNumber();    // 1-based
        int col = locator.getColumnNumber();   // 1-based, one past the last char of the event
        return lineStart[line - 1] + (col - 1);
    }

    /** Start offset of each 1-based line: {@code lineStart[0]} = 0, then after each '\n'. */
    private static int[] lineStartOffsets(String s) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < s.length(); i++) {
            // A line ends after '\n', or after a lone '\r' (classic-Mac EOL) — matching how the
            // XML parser normalizes line ends and counts Locator lines. '\r\n' counts once (the '\n').
            if (s.charAt(i) == '\n'
                    || (s.charAt(i) == '\r' && (i + 1 >= s.length() || s.charAt(i + 1) != '\n'))) {
                starts.add(i + 1);
            }
        }
        int[] arr = new int[starts.size() + 1];
        for (int i = 0; i < starts.size(); i++) {
            arr[i] = starts.get(i);
        }
        arr[starts.size()] = s.length();   // guard for a locator that points past the last line
        return arr;
    }

    /** The spaces/tabs immediately before {@code pos} on its line (the element's indentation). */
    private static String indentBefore(String s, int pos) {
        int i = pos;
        while (i > 0 && (s.charAt(i - 1) == ' ' || s.charAt(i - 1) == '\t')) {
            i--;
        }
        return s.substring(i, pos);
    }
}
