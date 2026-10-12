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

    /**
     * A pending enumerationdef before its subjects are resolved.
     *
     * @param attribute the bound attribute name.
     * @param keys      the subject names the binding refers to, from {@code @keyref} or {@code @keys}.
     */
    private record RawEnum(String attribute, List<String> keys) {}

    static SubjectScheme fromRootMap(File rootMap) {
        if (rootMap == null || !rootMap.isFile()) {
            return SubjectScheme.empty();
        }
        List<Element> schemes = new ArrayList<>();
        Set<String> schemeMaps = new LinkedHashSet<>();

        for (String path : KeySpace.mapsInClosure(rootMap)) {
            File f = new File(path);
            Element root = readRoot(f);
            if (root == null || !isSubjectScheme(root)) {
                continue;
            }
            schemeMaps.add(path);
            schemes.add(root);
        }
        return build(schemes, schemeMaps);
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
        List<Element> schemes = new ArrayList<>();
        Set<String> schemeMaps = new LinkedHashSet<>();
        try (java.util.stream.Stream<java.nio.file.Path> paths =
                java.nio.file.Files.walk(root.toPath())) {
            for (java.nio.file.Path p : paths
                    .filter(x -> x.toString().toLowerCase(java.util.Locale.ROOT)
                            .endsWith(".ditamap")
                            && !com.dogsbay.dogsbayaieditor.ditaproject.FileSet.inHiddenFolder(root.toPath(), x))
                    .sorted().toList()) {
                Element rootEl = readRoot(p.toFile());
                if (rootEl == null || !isSubjectScheme(rootEl)) {
                    continue;
                }
                schemeMaps.add(p.toAbsolutePath().normalize().toString());
                schemes.add(rootEl);
            }
        } catch (Exception e) {
            LOG.debug("Subject-scheme discovery under {} failed: {}", root, e.toString());
        }
        return build(schemes, schemeMaps);
    }

    /**
     * Resolve every scheme's bindings, and merge them.
     *
     * <p>Each scheme file gets its own registry of subjects. DITA-OT resolves a binding against the subjects defined
     * in the same scheme document, so a {@code keyref} naming a subject that lives in another file binds nothing;
     * resolving across files would govern an attribute a real build leaves alone, and every value an author then used
     * would be reported as a violation that no build agrees with.
     *
     * <p>The bound subject's own key is not one of its values. It names the dimension — {@code os} is the category
     * whose values are {@code linux} and {@code windows} — and a document that wrote {@code platform="os"} would be
     * rejected by a real build.
     *
     * <p>Bindings accumulate: two {@code <enumerationdef>}s naming the same attribute contribute to one value set
     * rather than the later replacing the earlier. A scheme that governs {@code outputclass} once per element writes
     * exactly that, and keeping only the last binding would reject every value but one.
     */
    private static SubjectScheme build(List<Element> schemes, Set<String> schemeMaps) {
        Map<String, Set<String>> valuesByAttribute = new LinkedHashMap<>();
        Map<String, String> firstKeyByAttribute = new LinkedHashMap<>();
        Map<String, Set<SubjectDef>> boundByAttribute = new LinkedHashMap<>();

        for (Element scheme : schemes) {
            Map<String, SubjectDef> subjectsByKey = new LinkedHashMap<>();
            collectSubjects(scheme, subjectsByKey);
            List<RawEnum> rawEnums = new ArrayList<>();
            collectEnums(scheme, rawEnums);
            for (RawEnum re : rawEnums) {
                if (re.attribute() == null || re.attribute().isBlank()) {
                    continue;
                }
                bind(re, subjectsByKey, valuesByAttribute, firstKeyByAttribute, boundByAttribute);
            }
        }

        List<EnumerationBinding> bindings = new ArrayList<>();
        for (var entry : valuesByAttribute.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                // An attribute bound only to a subject with no values of its own governs nothing. DITA-OT would warn
                // on every value instead; a vocabulary of nothing is not what anyone writing that meant, and the cost
                // of guessing wrong falls on a validator that would flag every document in the project.
                bindings.add(new EnumerationBinding(entry.getKey(),
                        firstKeyByAttribute.get(entry.getKey()), entry.getValue()));
            }
        }
        return SubjectScheme.of(bindings, schemeMaps);
    }

    /** Add one enumerationdef's subjects to what its attribute allows. */
    private static void bind(RawEnum re, Map<String, SubjectDef> subjectsByKey,
            Map<String, Set<String>> valuesByAttribute, Map<String, String> firstKeyByAttribute,
            Map<String, Set<SubjectDef>> boundByAttribute) {
        String attribute = re.attribute();
        Set<String> values = valuesByAttribute.computeIfAbsent(attribute, k -> new LinkedHashSet<>());
        Set<SubjectDef> alreadyBound = boundByAttribute.computeIfAbsent(attribute, k -> new LinkedHashSet<>());
        for (String key : re.keys()) {
            SubjectDef bound = subjectsByKey.get(key);
            if (bound == null) {
                continue; // names a subject this scheme never defines, which binds nothing
            }
            if (!alreadyBound.add(bound)) {
                // The same subtree named twice for one attribute adds nothing the first naming did not. Repeating the
                // work would let the second name's category back in as a value of its own.
                continue;
            }
            firstKeyByAttribute.putIfAbsent(attribute, key);
            for (String value : bound.flattenKeys()) {
                if (!value.equals(key)) {
                    values.add(value);
                }
            }
        }
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
     * Register every subject definition, and its descendants, by key.
     *
     * <p>Including the ones inside {@code <enumerationdef>}, which is what DITA-OT does: its reader walks the whole
     * scheme into one registry and then resolves each enumerationdef's child against it. That is how the inline form
     * works at all — {@code <subjectdef keys="os"><subjectdef keys="linux"/></subjectdef>} written inside the
     * enumerationdef registers {@code os} with its subtree, and the binding then finds it by name like any other.
     */
    private static void collectSubjects(Element parent, Map<String, SubjectDef> byKey) {
        for (Element child : parent.elements()) {
            if ("subjectdef".equals(child.getName())) {
                registerSubject(buildSubject(child), byKey);
            } else {
                collectSubjects(child, byKey);
            }
        }
    }

    /**
     * Build a SubjectDef from a {@code <subjectdef>} element, recursively.
     *
     * <p>A narrower subject need not be a direct child: DITA-OT descends through anything that is not itself a
     * subjectdef to find them, so a subject grouped under a {@code <subjectHead>} still belongs to its parent.
     */
    private static SubjectDef buildSubject(Element el) {
        List<SubjectDef> children = new ArrayList<>();
        collectChildSubjects(el, children);
        return new SubjectDef(el.attributeValue("keys"), el.attributeValue("navtitle"), children);
    }

    private static void collectChildSubjects(Element parent, List<SubjectDef> out) {
        for (Element c : parent.elements()) {
            if ("subjectdef".equals(c.getName())) {
                out.add(buildSubject(c));
            } else {
                collectChildSubjects(c, out);
            }
        }
    }

    private static void registerSubject(SubjectDef def, Map<String, SubjectDef> byKey) {
        for (String token : def.keyTokens()) {
            byKey.putIfAbsent(token, def);
        }
        for (SubjectDef c : def.children()) {
            registerSubject(c, byKey);
        }
    }

    /**
     * Collect every {@code <enumerationdef>}: the attribute it binds, and the subjects it binds it to.
     *
     * <p>A child {@code <subjectdef>} here is a <em>reference</em>, by {@code @keyref} or by {@code @keys}, to a
     * subject defined elsewhere in the scheme — not a definition of one. That is what DITA-OT does, and it matters for
     * the common shorthand {@code <subjectdef keys="os"/>}: read as a definition it binds the single value
     * {@code os}, and read as a reference it binds the {@code os} subtree without {@code os}, which is close to the
     * opposite answer.
     */
    private static void collectEnums(Element parent, List<RawEnum> out) {
        for (Element child : parent.elements()) {
            if ("enumerationdef".equals(child.getName())) {
                String attribute = null;
                List<String> keys = new ArrayList<>();
                for (Element e : child.elements()) {
                    switch (e.getName()) {
                        case "attributedef" -> {
                            if (attribute == null) {
                                attribute = e.attributeValue("name"); // the first one, as DITA-OT reads it
                            }
                        }
                        case "subjectdef" -> keys.addAll(referencedKeys(e));
                        default -> { /* defaultSubject / elementdef scope: v1 ignores */ }
                    }
                }
                out.add(new RawEnum(attribute, keys));
            } else {
                collectEnums(child, out);
            }
        }
    }

    /** The subjects a {@code <subjectdef>} inside an enumerationdef refers to: {@code @keyref}, else {@code @keys}. */
    private static List<String> referencedKeys(Element subjectdef) {
        String value = subjectdef.attributeValue("keyref");
        if (value == null || value.isBlank()) {
            value = subjectdef.attributeValue("keys");
        }
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.trim().split("\\s+"));
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
