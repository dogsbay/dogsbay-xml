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
import java.util.ArrayList;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Extracts DITA 1.3 glossary facts from a topic in one hardened SAX pass (same parsing
 * hardening as {@link LinkExtractor}): whether the topic is a {@code <glossentry>}
 * (with its {@code @id}, {@code <glossterm>}, and surface forms), and every
 * {@code <abbreviated-form>}/{@code <term>} that references a glossary key. An
 * {@code glossary_audit} aggregates these into the term inventory, undefined
 * abbreviated-forms, and unused glossentries.
 */
public final class GlossaryScanner {

    /** What one topic contributes to the glossary picture. */
    public record GlossScan(GlossEntry entry, List<TermRef> refs) {
        public GlossScan {
            refs = refs == null ? List.of() : List.copyOf(refs);
        }
        /** True when the topic is a glossary entry. */
        public boolean isGlossentry() {
            return entry != null;
        }
    }

    /** A {@code <glossentry>} definition. */
    public record GlossEntry(String id, String term, List<String> surfaceForms) {
        public GlossEntry {
            surfaceForms = surfaceForms == null ? List.of() : List.copyOf(surfaceForms);
        }
    }

    /** A {@code <abbreviated-form>} or {@code <term>} that references a key. */
    public record TermRef(String element, String keyref, int line) {}

    private GlossaryScanner() {}

    /** Scan a file on disk; unreadable/malformed files yield an empty scan. */
    public static GlossScan scan(File file) {
        try (InputStream in = new FileInputStream(file)) {
            return scan(new InputSource(in));
        } catch (IOException e) {
            return new GlossScan(null, List.of());
        }
    }

    /** Scan in-memory content (editor buffers, tests). */
    public static GlossScan scan(String content) {
        return scan(new InputSource(new StringReader(content)));
    }

    private static GlossScan scan(InputSource input) {
        Handler handler = new Handler();
        try {
            XMLReader reader = HardenedSax.newReader();
            reader.setContentHandler(handler);
            reader.parse(input);
        } catch (Exception e) {
            // Malformed mid-edit files are normal — keep what we have.
        }
        GlossEntry entry = handler.isGlossentry
                ? new GlossEntry(handler.id, handler.term.toString().trim(),
                        handler.surfaceForms)
                : null;
        return new GlossScan(entry, handler.refs);
    }

    private static final class Handler extends DefaultHandler {
        final List<TermRef> refs = new ArrayList<>();
        final List<String> surfaceForms = new ArrayList<>();
        final StringBuilder term = new StringBuilder();

        private boolean isGlossentry;
        private boolean rootSeen;
        private String id;
        private int depth;
        private boolean inGlossterm;
        private boolean inSurfaceForm;
        private final StringBuilder surface = new StringBuilder();
        private Locator locator;

        @Override
        public void setDocumentLocator(Locator l) {
            this.locator = l;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes a) {
            depth++;
            String name = localName != null && !localName.isEmpty() ? localName : qName;
            if (!rootSeen) {
                rootSeen = true;
                if ("glossentry".equals(name)) {
                    isGlossentry = true;
                    id = a.getValue("id");
                }
            }
            if (isGlossentry) {
                if ("glossterm".equals(name)) {
                    inGlossterm = true;
                } else if ("glossAbbreviation".equals(name) || "glossAcronym".equals(name)
                        || "glossShortForm".equals(name) || "glossSynonym".equals(name)) {
                    inSurfaceForm = true;
                    surface.setLength(0);
                }
            }
            if ("abbreviated-form".equals(name) || "term".equals(name)) {
                String keyref = a.getValue("keyref");
                if (keyref != null && !keyref.isEmpty()) {
                    refs.add(new TermRef(name, keyref,
                            locator != null ? locator.getLineNumber() : -1));
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inGlossterm) {
                term.append(ch, start, length);
            } else if (inSurfaceForm) {
                surface.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            depth--;
            String name = localName != null && !localName.isEmpty() ? localName : qName;
            if ("glossterm".equals(name)) {
                inGlossterm = false;
            } else if (inSurfaceForm && ("glossAbbreviation".equals(name)
                    || "glossAcronym".equals(name) || "glossShortForm".equals(name)
                    || "glossSynonym".equals(name))) {
                inSurfaceForm = false;
                String s = surface.toString().trim();
                if (!s.isEmpty()) {
                    surfaceForms.add(s);
                }
            }
        }
    }

}
