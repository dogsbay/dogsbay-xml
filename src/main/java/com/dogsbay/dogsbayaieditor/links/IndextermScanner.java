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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Extracts DITA 1.3 index entries from a topic in one hardened SAX pass (same
 * non-validating, no-external-entity parsing as {@link LinkExtractor}). Collects each
 * {@code <indexterm>}'s full nested path (primary &gt; secondary &gt; …, leaf nodes
 * only) and each {@code <index-see>}/{@code <index-see-also>} redirect with its target
 * text — the inputs an {@code index_audit} aggregates into a project index inventory,
 * coverage gaps, and dangling-see detection.
 */
public final class IndextermScanner {

    /** What one topic contributes to the index. */
    public record IndexScan(List<String> entries, List<See> sees) {
        public IndexScan {
            entries = entries == null ? List.of() : List.copyOf(entries);
            sees = sees == null ? List.of() : List.copyOf(sees);
        }
    }

    /**
     * A {@code <index-see>} (or {@code <index-see-also>}) redirect.
     *
     * @param from   the index entry path the see sits under
     * @param target the redirect target text ("see <em>target</em>")
     * @param also   true for {@code <index-see-also>} (a supplement, not a pure redirect)
     */
    public record See(String from, String target, boolean also) {}

    private IndextermScanner() {}

    /** Scan a file on disk; unreadable/malformed files yield an empty scan. */
    public static IndexScan scan(File file) {
        try (InputStream in = new FileInputStream(file)) {
            return scan(new InputSource(in));
        } catch (IOException e) {
            return new IndexScan(List.of(), List.of());
        }
    }

    /** Scan in-memory content (editor buffers, tests). */
    public static IndexScan scan(String content) {
        return scan(new InputSource(new StringReader(content)));
    }

    private static IndexScan scan(InputSource input) {
        Handler handler = new Handler();
        try {
            XMLReader reader = HardenedSax.newReader();
            reader.setContentHandler(handler);
            reader.parse(input);
        } catch (Exception e) {
            // Malformed mid-edit files are normal — keep what we have.
        }
        return new IndexScan(handler.entries, handler.sees);
    }

    private static final class Handler extends DefaultHandler {
        final List<String> entries = new ArrayList<>();
        final List<See> sees = new ArrayList<>();

        /** One open {@code <indexterm>}: its own (direct) text, whether it nests another,
         *  and whether it carries an {@code <index-see>} (a pure redirect with no locator). */
        private static final class Frame {
            final StringBuilder text = new StringBuilder();
            boolean hadChildIndexterm;
            boolean hadSee;
        }

        private final Deque<Frame> stack = new ArrayDeque<>();
        private final StringBuilder seeText = new StringBuilder();
        private boolean inSee;
        private boolean seeAlso;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes a) {
            String name = localName != null && !localName.isEmpty() ? localName : qName;
            if ("indexterm".equals(name)) {
                if (!stack.isEmpty()) {
                    stack.peek().hadChildIndexterm = true;
                }
                stack.push(new Frame());
            } else if ("index-see".equals(name) || "index-see-also".equals(name)) {
                inSee = true;
                seeAlso = "index-see-also".equals(name);
                seeText.setLength(0);
                // A pure <index-see> makes the enclosing indexterm a redirect with no
                // page locator; <index-see-also> supplements, so it keeps its locator.
                if (!seeAlso && !stack.isEmpty()) {
                    stack.peek().hadSee = true;
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inSee) {
                seeText.append(ch, start, length);
            } else if (!stack.isEmpty()) {
                stack.peek().text.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String name = localName != null && !localName.isEmpty() ? localName : qName;
            if ("index-see".equals(name) || "index-see-also".equals(name)) {
                String target = seeText.toString().trim();
                if (!target.isEmpty()) {
                    sees.add(new See(path(), target, seeAlso));
                }
                inSee = false;
            } else if ("indexterm".equals(name) && !stack.isEmpty()) {
                Frame done = stack.pop();
                // A leaf indexterm (no nested indexterm) is a real index entry; a
                // see-only entry is a redirect, recorded via the See above, not here.
                if (!done.hadChildIndexterm && !done.hadSee
                        && done.text.toString().trim().length() > 0) {
                    stack.push(done);          // include self in the path
                    entries.add(path());
                    stack.pop();
                }
            }
        }

        /** The "primary > secondary > …" path of the currently-open indexterms. */
        private String path() {
            List<String> parts = new ArrayList<>();
            // Deque iterates top-first; reverse to outer-first.
            for (Frame f : stack) {
                String t = f.text.toString().trim();
                if (!t.isEmpty()) {
                    parts.add(0, t);
                }
            }
            return String.join(" > ", parts);
        }
    }
}
