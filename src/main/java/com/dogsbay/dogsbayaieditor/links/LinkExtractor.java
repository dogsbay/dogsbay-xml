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
import java.util.regex.Pattern;


import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Extracts outbound references and key definitions from an XML/DITA file in
 * a single SAX pass, with line numbers (via the SAX {@link Locator} — which
 * dom4j discards).
 *
 * <p>References collected: {@code href}, {@code conref}, {@code conkeyref},
 * {@code keyref}, {@code src}, and XInclude {@code href}. Key definitions:
 * any element with {@code @keys}, including its
 * {@code topicmeta/keywords/keyword} and {@code topicmeta/linktext} text.
 *
 * <p>Parsing is non-validating and never fetches external DTDs/entities
 * (same hardening as {@code DitaMapParser}); a malformed file yields
 * whatever was extracted up to the error rather than throwing.
 */
public final class LinkExtractor {

    /** Everything found in one file. */
    public record ExtractionResult(List<Reference> references,
                                   List<KeyDefinition> keyDefinitions,
                                   List<Submap> submaps) {
        /** Back-compat: no submap-scope info. */
        public ExtractionResult(List<Reference> references,
                                List<KeyDefinition> keyDefinitions) {
            this(references, keyDefinitions, List.of());
        }
    }

    /** A referenced submap (mapref / .ditamap href) and the {@code @keyscope} path it
     *  sits in within this file — so {@link KeySpace} can scope the submap's keys. */
    public record Submap(String targetPath, String scope) {
    }

    private static final String XINCLUDE_NS = "http://www.w3.org/2001/XInclude";
    private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");
    private static final String[] PATH_ATTRS = {"href", "conref", "src"};
    private static final String[] KEY_ATTRS = {"keyref", "conkeyref"};

    private LinkExtractor() {
    }

    /** Extracts from a file on disk. */
    public static ExtractionResult extract(File file) {
        return extract(file, null);
    }

    /**
     * Extracts from a file on disk, skipping subtrees the exclusion rejects
     * (DITAVAL conditional filtering of maps before key resolution).
     */
    public static ExtractionResult extract(File file, ElementExclusion exclusion) {
        try (InputStream in = new FileInputStream(file)) {
            InputSource source = new InputSource(in);
            return extract(source, file, exclusion);
        } catch (IOException e) {
            return new ExtractionResult(List.of(), List.of());
        }
    }

    /** Extracts from in-memory content (editor buffers, tests). */
    public static ExtractionResult extract(String content, File sourceFile) {
        return extract(new InputSource(new StringReader(content)), sourceFile, null);
    }

    private static ExtractionResult extract(InputSource input, File sourceFile,
                                            ElementExclusion exclusion) {
        Handler handler = new Handler(sourceFile, exclusion);
        try {
            XMLReader reader = HardenedSax.newReader();
            reader.setContentHandler(handler);
            reader.parse(input);
        } catch (Exception e) {
            // Malformed mid-edit files are normal — keep what we have.
        }
        return new ExtractionResult(handler.references, handler.keyDefinitions,
                handler.submaps);
    }

    // -------------------------------------------------------------------------

    private static final class Handler extends DefaultHandler {
        final List<Reference> references = new ArrayList<>();
        final List<KeyDefinition> keyDefinitions = new ArrayList<>();
        final List<Submap> submaps = new ArrayList<>();
        // The enclosing @keyscope path (outer→inner); scopeDepths[i] = the element
        // depth that pushed scopePath[i], so endElement pops the right one.
        final List<String> scopePath = new ArrayList<>();
        final Deque<Integer> scopeDepths = new ArrayDeque<>();

        private String currentScope() {
            return String.join(".", scopePath);
        }

        private final File sourceFile;
        private final File baseDir;
        private final ElementExclusion exclusion;
        private Locator locator;
        private int depth;
        /** Depth of the root of a suppressed (excluded) subtree, or -1. */
        private int suppressDepth = -1;

        /** Pending key definition while its subtree is being read. */
        private static final class PendingKey {
            final int startDepth;
            final int line;
            final String keys;
            final String href;
            final String scope;
            final StringBuilder keyword = new StringBuilder();
            final StringBuilder linktext = new StringBuilder();
            // The effective key text is the FIRST keyword/linktext —
            // additional ones (e.g. conditional variants) must not concatenate.
            boolean keywordDone;
            boolean linktextDone;

            PendingKey(int startDepth, int line, String keys, String href, String scope) {
                this.startDepth = startDepth;
                this.line = line;
                this.keys = keys;
                this.href = href;
                this.scope = scope;
            }
        }

        private final Deque<PendingKey> pendingKeys = new ArrayDeque<>();
        private boolean inKeyword;
        private boolean inLinktext;
        /** True once the document root is seen to be a {@code subjectScheme} map. */
        private boolean rootIsSubjectScheme;

        Handler(File sourceFile, ElementExclusion exclusion) {
            this.sourceFile = sourceFile;
            this.baseDir = sourceFile != null ? sourceFile.getParentFile() : null;
            this.exclusion = exclusion;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        private int line() {
            return locator != null ? locator.getLineNumber() : -1;
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes attrs) throws SAXException {
            depth++;
            if (suppressDepth >= 0) {
                return;                          // inside an excluded subtree
            }
            if (exclusion != null && exclusion.isExcluded(attrs)) {
                suppressDepth = depth;
                return;
            }
            String name = localName != null && !localName.isEmpty() ? localName : qName;
            if (depth == 1) {
                rootIsSubjectScheme = "subjectScheme".equals(name);
            }

            // Enter a key scope (before reading path/key attrs, so a keydef or mapref
            // that itself carries @keyscope scopes its own keys/submap). @keyscope may
            // list several names; the first is the canonical segment (others best-effort).
            String keyscope = attrs.getValue("keyscope");
            if (keyscope != null && !keyscope.trim().isEmpty()) {
                scopePath.add(keyscope.trim().split("\\s+")[0]);
                scopeDepths.push(depth);
            }

            // XInclude
            if (XINCLUDE_NS.equals(uri) && "include".equals(name)) {
                String href = attrs.getValue("href");
                if (href != null && !href.isEmpty()) {
                    addPathReference(name, "href", href);
                }
            } else {
                for (String attr : PATH_ATTRS) {
                    String value = attrs.getValue(attr);
                    if (value != null && !value.isEmpty()) {
                        addPathReference(name, attr, value);
                    }
                }
            }
            // subjectScheme bindings (subjectdef / enumerationdef @keyref) are
            // controlled-value references into the scheme's own subject hierarchy,
            // not content key references — excluding them keeps check_links and
            // project_health from reporting them as "undefined keys".
            if (!"subjectdef".equals(name) && !"enumerationdef".equals(name)) {
                for (String attr : KEY_ATTRS) {
                    String value = attrs.getValue(attr);
                    if (value != null && !value.isEmpty()) {
                        references.add(new Reference(sourceFile, line(), name, attr,
                                value, null, fragmentOfKeyRef(value), currentScope()));
                    }
                }
            }

            // Key definition?
            String keys = attrs.getValue("keys");
            if (keys != null && !keys.trim().isEmpty()) {
                pendingKeys.push(new PendingKey(depth, line(), keys.trim(),
                        attrs.getValue("href"), currentScope()));
            }

            if (!pendingKeys.isEmpty()) {
                if ("keyword".equals(name)) inKeyword = true;
                if ("linktext".equals(name)) inLinktext = true;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (suppressDepth >= 0 || pendingKeys.isEmpty()) {
                return;
            }
            PendingKey top = pendingKeys.peek();
            if (inKeyword && !top.keywordDone && top.keyword.length() < 512) {
                top.keyword.append(ch, start, length);
            }
            if (inLinktext && !top.linktextDone && top.linktext.length() < 512) {
                top.linktext.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (suppressDepth >= 0) {
                if (depth == suppressDepth) {
                    suppressDepth = -1;          // leaving the excluded subtree
                }
                depth--;
                return;
            }
            String name = localName != null && !localName.isEmpty() ? localName : qName;
            if ("keyword".equals(name)) {
                inKeyword = false;
                if (!pendingKeys.isEmpty() && pendingKeys.peek().keyword.length() > 0) {
                    pendingKeys.peek().keywordDone = true;
                }
            }
            if ("linktext".equals(name)) {
                inLinktext = false;
                if (!pendingKeys.isEmpty() && pendingKeys.peek().linktext.length() > 0) {
                    pendingKeys.peek().linktextDone = true;
                }
            }

            if (!pendingKeys.isEmpty() && pendingKeys.peek().startDepth == depth) {
                PendingKey done = pendingKeys.pop();
                String keyword = done.keyword.toString().trim();
                String linktext = done.linktext.toString().trim();
                keyDefinitions.add(new KeyDefinition(sourceFile, done.line,
                        done.keys, done.href,
                        keyword.isEmpty() ? null : keyword,
                        linktext.isEmpty() ? null : linktext, done.scope,
                        rootIsSubjectScheme));
            }
            // Leave a key scope this element opened.
            if (!scopeDepths.isEmpty() && scopeDepths.peek() == depth) {
                scopeDepths.pop();
                scopePath.remove(scopePath.size() - 1);
            }
            depth--;
        }

        private void addPathReference(String element, String attr, String rawValue) {
            String path = rawValue;
            String fragment = null;
            int hash = path.indexOf('#');
            if (hash >= 0) {
                fragment = path.substring(hash + 1);
                path = path.substring(0, hash);
            }
            // External schemes (http:, mailto:, ...) are not project files.
            if (SCHEME.matcher(path).find()) {
                return;
            }
            File target;
            if (path.isEmpty()) {
                target = sourceFile;             // same-file conref: "#topic/elem"
            } else if (new File(path).isAbsolute()) {
                target = new File(path);
            } else if (baseDir != null) {
                target = new File(baseDir, path);
            } else {
                target = new File(path);
            }
            String canonicalTarget = canonical(target);
            references.add(new Reference(sourceFile, line(), element, attr,
                    rawValue, canonicalTarget, fragment, currentScope()));
            // Track submaps with their enclosing key scope so KeySpace can scope the
            // referenced map's keys (a mapref under @keyscope, or @keyscope on the mapref).
            boolean isSubmap = "mapref".equals(element)
                    || (canonicalTarget != null
                            && canonicalTarget.toLowerCase().endsWith(".ditamap"));
            if (isSubmap && canonicalTarget != null) {
                submaps.add(new Submap(canonicalTarget, currentScope()));
            }
        }

        private static String fragmentOfKeyRef(String value) {
            int slash = value.indexOf('/');
            return slash >= 0 ? value.substring(slash + 1) : null;
        }
    }

    /** Canonical path with graceful fallback. */
    static String canonical(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

}
