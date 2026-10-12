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
 * hardening as {@link LinkExtractor}): every {@code <glossentry>} the file holds (with its
 * {@code @id}, {@code <glossterm>}, and surface forms), and every
 * {@code <abbreviated-form>}/{@code <term>} that references a glossary key. An
 * {@code glossary_audit} aggregates these into the term inventory, undefined
 * abbreviated-forms, and unused glossentries.
 *
 * <p>Every entry, not one: a {@code <glossgroup>} is a container for several, addressed by
 * {@code file.dita#entry-id}, and it is how a project keeps related terms together — the units of
 * a measurement, say. Reading only a root {@code <glossentry>} made each of those entries invisible
 * and every reference to one "not a glossary term": four such false positives on the Audacity
 * tutorial's own glossary, found in CI on 2026-10-12. Groups nest, so this follows them down.
 */
public final class GlossaryScanner {

    /**
     * What one topic contributes to the glossary picture.
     *
     * @param entries every {@code <glossentry>} in the file, in document order
     * @param refs every reference to a glossary key in the file
     * @param rootIsEntry whether the file's root element is itself an entry — which is what makes a
     *     reference with no fragment mean the first of {@code entries} rather than nothing
     */
    public record GlossScan(List<GlossEntry> entries, List<TermRef> refs, boolean rootIsEntry) {
        public GlossScan {
            entries = entries == null ? List.of() : List.copyOf(entries);
            refs = refs == null ? List.of() : List.copyOf(refs);
        }

        /** The file's own entry, when its root element is one. */
        public GlossEntry rootEntry() {
            return rootIsEntry && !entries.isEmpty() ? entries.get(0) : null;
        }
        /** True when the file holds at least one glossary entry. */
        public boolean isGlossentry() {
            return !entries.isEmpty();
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
            return new GlossScan(List.of(), List.of(), false);
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
        return new GlossScan(handler.entries, handler.refs, handler.rootIsEntry);
    }

    private static final class Handler extends DefaultHandler {
        final List<TermRef> refs = new ArrayList<>();
        final List<GlossEntry> entries = new ArrayList<>();
        boolean rootIsEntry;

        private boolean rootSeen;

        /** The entry being read, or null between entries. */
        private String id;
        private StringBuilder term;
        private List<String> surfaceForms;

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
            String name = localName != null && !localName.isEmpty() ? localName : qName;
            if (!rootSeen) {
                rootSeen = true;
                rootIsEntry = "glossentry".equals(name);
            }
            if ("glossentry".equals(name)) {
                // A glossentry nested inside another is not a thing DITA allows, so an entry met
                // while one is open closes the first rather than being lost.
                closeEntry();
                id = a.getValue("id");
                term = new StringBuilder();
                surfaceForms = new ArrayList<>();
            } else if (term != null) {
                if ("glossterm".equals(name)) {
                    // The entry's own term is the first one met: a later <glossterm> is a variant
                    // inside a <glossAlt>, which is a different thing (GlossEntry reads those).
                    inGlossterm = term.isEmpty();
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
            } else if ("glossentry".equals(name)) {
                closeEntry();
            }
        }

        /** Keep the entry being read, if there is one. */
        private void closeEntry() {
            if (term != null) {
                entries.add(new GlossEntry(id, term.toString().trim(), surfaceForms));
            }
            id = null;
            term = null;
            surfaceForms = null;
            inGlossterm = false;
            inSurfaceForm = false;
        }
    }

}
