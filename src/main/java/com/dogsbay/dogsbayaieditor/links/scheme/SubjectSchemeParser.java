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

package com.dogsbay.dogsbayaieditor.links.scheme;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dogsbay.dogsbayaieditor.links.KeySpace;

/**
 * Parses the subjectScheme map(s) in a root map's closure into a
 * {@link SubjectScheme}. Discovery piggybacks on {@link KeySpace#mapsInClosure}
 * (the existing GUI-free mapref crawl); each closure member whose root is a
 * {@code subjectScheme} is parsed and its bindings merged.
 *
 * <p>XML is read with external DTD/entity loading disabled, so DITA subjectScheme
 * doctypes need no catalog. Malformed files are skipped (best-effort), never thrown.
 */
final class SubjectSchemeParser {

    private static final Logger LOG = LoggerFactory.getLogger(SubjectSchemeParser.class);

    private SubjectSchemeParser() {}

    /** A pending enumerationdef before its subject keyref is resolved. */
    private record RawEnum(String attribute, String keyref, SubjectDef inline) {}

    static SubjectScheme fromRootMap(File rootMap) {
        if (rootMap == null || !rootMap.isFile()) {
            return SubjectScheme.empty();
        }
        Map<String, SubjectDef> subjectsByKey = new LinkedHashMap<>();
        List<RawEnum> rawEnums = new ArrayList<>();
        Set<String> schemeMaps = new LinkedHashSet<>();

        for (String path : KeySpace.mapsInClosure(rootMap)) {
            File f = new File(path);
            Element root = readRoot(f);
            if (root == null || !isSubjectScheme(root)) {
                continue;
            }
            schemeMaps.add(path);
            collectSubjects(root, subjectsByKey);
            collectEnums(root, rawEnums);
        }
        return build(subjectsByKey, rawEnums, schemeMaps);
    }

    /**
     * Discover the subjectScheme map(s) anywhere under a project root (no single
     * root map), parse and merge them. Used by project-wide operations — e.g. the
     * rename-profile-value refactor checking whether a renamed value stays governed.
     */
    static SubjectScheme fromProjectRoot(File root) {
        if (root == null || !root.isDirectory()) {
            return SubjectScheme.empty();
        }
        Map<String, SubjectDef> subjectsByKey = new LinkedHashMap<>();
        List<RawEnum> rawEnums = new ArrayList<>();
        Set<String> schemeMaps = new LinkedHashSet<>();
        try (java.util.stream.Stream<java.nio.file.Path> paths =
                java.nio.file.Files.walk(root.toPath())) {
            for (java.nio.file.Path p : paths
                    .filter(x -> x.toString().toLowerCase(java.util.Locale.ROOT)
                            .endsWith(".ditamap"))
                    .sorted().toList()) {
                Element rootEl = readRoot(p.toFile());
                if (rootEl == null || !isSubjectScheme(rootEl)) {
                    continue;
                }
                schemeMaps.add(p.toAbsolutePath().normalize().toString());
                collectSubjects(rootEl, subjectsByKey);
                collectEnums(rootEl, rawEnums);
            }
        } catch (Exception e) {
            LOG.debug("Subject-scheme discovery under {} failed: {}", root, e.toString());
        }
        return build(subjectsByKey, rawEnums, schemeMaps);
    }

    /** Resolve enumerationdef bindings against the merged subject definitions. */
    private static SubjectScheme build(Map<String, SubjectDef> subjectsByKey,
            List<RawEnum> rawEnums, Set<String> schemeMaps) {
        List<EnumerationBinding> bindings = new ArrayList<>();
        for (RawEnum re : rawEnums) {
            if (re.attribute() == null || re.attribute().isBlank()) {
                continue;
            }
            Set<String> values = new LinkedHashSet<>();
            if (re.inline() != null) {
                values.addAll(re.inline().flattenKeys());
            } else if (re.keyref() != null) {
                SubjectDef bound = subjectsByKey.get(re.keyref());
                if (bound != null) {
                    values.addAll(bound.flattenKeys());
                }
            }
            if (!values.isEmpty()) {
                bindings.add(new EnumerationBinding(re.attribute(), re.keyref(), values));
            }
        }
        return SubjectScheme.of(bindings, schemeMaps);
    }

    /** True when the element is a subjectScheme map (by @class token or local name). */
    private static boolean isSubjectScheme(Element root) {
        String cls = root.attributeValue("class");
        if (cls != null && cls.contains("subjectScheme/subjectScheme")) {
            return true;
        }
        return "subjectScheme".equals(root.getName());
    }

    /**
     * Register subject definitions (and their descendants) by key. Skips
     * {@code <enumerationdef>} subtrees (those subjectdefs are references, handled
     * by {@link #collectEnums}); recurses through other containers (subjectHead, …).
     */
    private static void collectSubjects(Element parent, Map<String, SubjectDef> byKey) {
        for (Element child : parent.elements()) {
            String name = child.getName();
            if ("enumerationdef".equals(name)) {
                continue;
            }
            if ("subjectdef".equals(name)) {
                registerSubject(buildSubject(child), byKey);
            } else {
                collectSubjects(child, byKey);
            }
        }
    }

    /** Build a SubjectDef from a {@code <subjectdef>} element, recursively. */
    private static SubjectDef buildSubject(Element el) {
        List<SubjectDef> children = new ArrayList<>();
        for (Element c : el.elements()) {
            if ("subjectdef".equals(c.getName())) {
                children.add(buildSubject(c));
            }
        }
        return new SubjectDef(el.attributeValue("keys"), el.attributeValue("navtitle"), children);
    }

    private static void registerSubject(SubjectDef def, Map<String, SubjectDef> byKey) {
        for (String token : def.keyTokens()) {
            byKey.putIfAbsent(token, def);
        }
        for (SubjectDef c : def.children()) {
            registerSubject(c, byKey);
        }
    }

    /** Collect every {@code <enumerationdef>} (attribute name + bound subject ref/inline). */
    private static void collectEnums(Element parent, List<RawEnum> out) {
        for (Element child : parent.elements()) {
            if ("enumerationdef".equals(child.getName())) {
                String attribute = null;
                String keyref = null;
                SubjectDef inline = null;
                for (Element e : child.elements()) {
                    switch (e.getName()) {
                        case "attributedef" -> attribute = e.attributeValue("name");
                        case "subjectdef" -> {
                            String kr = e.attributeValue("keyref");
                            if (kr != null && !kr.isBlank()) {
                                keyref = kr;
                            } else {
                                // Inline subject: values may be on this element's
                                // @keys and/or on nested <subjectdef> children —
                                // buildSubject handles both.
                                inline = buildSubject(e);
                            }
                        }
                        default -> { /* defaultSubject / elementdef scope: v1 ignores */ }
                    }
                }
                out.add(new RawEnum(attribute, keyref, inline));
            } else {
                collectEnums(child, out);
            }
        }
    }

    /**
     * Parse a map file's root element with external DTD/entities off (so DITA
     * doctypes need no catalog), null on error. We match on local names + authored
     * attributes (keys/name/keyref), never the DTD-defaulted {@code @class}, so
     * skipping the external DTD is correct. A scheme that uses DTD-defined entities
     * won't parse under these settings and is skipped (logged) — rare for
     * structural subjectScheme maps.
     */
    private static Element readRoot(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        SAXReader reader = new SAXReader();
        // Set defensively: an unsupported feature must not abort the read (it just
        // means that protection isn't applied). Xerces (the bundled parser)
        // supports all three.
        trySetFeature(reader, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        trySetFeature(reader, "http://xml.org/sax/features/external-general-entities", false);
        trySetFeature(reader, "http://xml.org/sax/features/external-parameter-entities", false);
        try {
            Document doc = reader.read(file);
            return doc.getRootElement();
        } catch (Exception e) {
            LOG.debug("Skipping unreadable/malformed scheme candidate {}: {}", file, e.toString());
            return null;
        }
    }

    private static void trySetFeature(SAXReader reader, String feature, boolean value) {
        try {
            reader.setFeature(feature, value);
        } catch (org.xml.sax.SAXException e) {
            LOG.debug("SAX feature {} not supported by the parser", feature);
        }
    }
}
