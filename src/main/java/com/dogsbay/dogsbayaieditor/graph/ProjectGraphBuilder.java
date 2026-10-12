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

package com.dogsbay.dogsbayaieditor.graph;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import com.dogsbay.dogsbayaieditor.ditaproject.Deliverable;
import com.dogsbay.dogsbayaieditor.ditaproject.ProjectContextLoader;
import com.dogsbay.dogsbayaieditor.links.HardenedSax;
import com.dogsbay.dogsbayaieditor.links.KeyDefinition;
import com.dogsbay.dogsbayaieditor.links.KeySpace;
import com.dogsbay.dogsbayaieditor.links.LinkExtractor;
import com.dogsbay.dogsbayaieditor.links.Reference;

/**
 * Builds a {@link ProjectGraph} for a DITA project: which maps, topics, keys and
 * DITAVALs exist, how they relate (typed edges), what each deliverable publishes
 * (its narrow {@code ships} set), and structural issues (broken references,
 * undefined/inconsistent/shadowed/unused keys, orphan topics).
 *
 * <p>Each DITA file is read once with {@link LinkExtractor} (references and key
 * definitions) plus one cheap hardened SAX sniff (root element, first title, and —
 * for maps — which references sit inside a {@code <reltable>} or are
 * {@code processing-role="resource-only"}). Key resolution per deliverable uses
 * {@link KeySpace#fromRootMap(File)} of the deliverable's root map; shadowed
 * definitions are found by replaying the same breadth-first, first-definition-wins
 * walk over the cached extractions. DITAVAL filtering of content is not applied.
 */
public final class ProjectGraphBuilder {

    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", ".svn", ".hg", "build", "out", "target", "node_modules");
    private static final Set<String> WALK_EXTENSIONS = Set.of("dita", "ditamap", "ditaval", "xml");
    private static final Set<String> TOPIC_ROOTS = Set.of("topic", "concept", "task", "reference",
            "glossentry", "glossgroup", "troubleshooting", "dita", "learningContent",
            "learningOverview", "learningSummary", "learningAssessment", "learningPlan");
    private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");
    private static final Set<String> SHIPPING_KINDS = Set.of("mapref", "topicref", "conref");

    private final Path absRoot;
    private final Path rootCanon;
    /** canonical path → file facts, in discovery order. */
    private final Map<String, FileInfo> files = new LinkedHashMap<>();
    private final Map<String, KeySpace> keySpaces = new HashMap<>();
    private final Map<String, Set<String>> shipsByRootMap = new HashMap<>();
    private final Set<GraphEdge> edges = new LinkedHashSet<>();
    private final Set<GraphIssue> issues = new LinkedHashSet<>();

    private ProjectGraphBuilder(Path absRoot) {
        this.absRoot = absRoot;
        this.rootCanon = Path.of(canonical(absRoot.toFile()));
    }

    /**
     * Build the graph for a project.
     *
     * @param root        the project root folder
     * @param map         optional root map (relative to {@code root} or absolute): the
     *                    graph covers that map as one synthetic deliverable named after
     *                    the map file
     * @param deliverable optional deliverable name from the project file (ignored when
     *                    {@code map} is given)
     * @return the graph; scope {@code "all"} when neither is given
     * @throws IOException              if a project file cannot be read
     * @throws IllegalArgumentException if the root, map or deliverable does not exist
     */
    public static ProjectGraph build(Path root, String map, String deliverable) throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("Project root is not a directory: " + root);
        }
        return new ProjectGraphBuilder(root.toAbsolutePath().normalize()).run(map, deliverable);
    }

    // -------------------------------------------------------------------------

    private ProjectGraph run(String map, String deliverableName) throws IOException {
        List<Deliverable> deliverables;
        String scope;
        boolean all = false;
        if (map != null && !map.isBlank()) {
            Path m = Path.of(map.trim());
            if (!m.isAbsolute()) {
                m = absRoot.resolve(m);
            }
            m = m.normalize();
            if (!Files.isRegularFile(m)) {
                throw new IllegalArgumentException("Map not found: " + map);
            }
            deliverables = List.of(new Deliverable(stripExtension(m.getFileName().toString()),
                    m, (Path) null, null));
            scope = "map:" + idOf(canonical(m.toFile()));
        } else if (deliverableName != null && !deliverableName.isBlank()) {
            List<Deliverable> declared =
                    ProjectContextLoader.load(absRoot, null, null, null).deliverables();
            Deliverable found = declared.stream()
                    .filter(d -> d.name().equals(deliverableName.trim())).findFirst().orElse(null);
            if (found == null) {
                throw new IllegalArgumentException("Unknown deliverable \"" + deliverableName
                        + "\"; available: " + declared.stream().map(Deliverable::name).toList());
            }
            deliverables = List.of(found);
            scope = "deliverable:" + found.name();
        } else {
            all = true;
            scope = "all";
            deliverables = ProjectContextLoader.load(absRoot, null, null, null).deliverables();
        }

        Deque<String> seeds = new ArrayDeque<>();
        if (all) {
            walk(absRoot.toFile(), seeds);
            if (deliverables.isEmpty()) {
                deliverables = unreferencedRootMaps();
            }
        }
        for (Deliverable d : deliverables) {
            seeds.add(canonical(d.map().toFile()));
            for (Path dv : d.ditavals()) {
                seeds.add(canonical(dv.toFile()));
            }
        }
        crawl(seeds);

        // Publication sets, per deliverable and per node.
        Map<String, List<String>> shipsByNode = new HashMap<>();
        List<GraphDeliverable> graphDeliverables = new ArrayList<>();
        Map<String, String> firstRootMapShipping = new HashMap<>();
        for (Deliverable d : deliverables) {
            String rootMap = canonical(d.map().toFile());
            Set<String> shipped = shipsOf(rootMap);
            for (String c : shipped) {
                shipsByNode.computeIfAbsent(c, k -> new ArrayList<>()).add(d.name());
                firstRootMapShipping.putIfAbsent(c, rootMap);
            }
            graphDeliverables.add(new GraphDeliverable(d.name(), idOf(rootMap),
                    d.ditavals().stream().map(p -> idOf(canonical(p.toFile()))).toList(),
                    shipped.stream().map(this::idOf).sorted().toList()));
        }

        emitFileEdges(firstRootMapShipping);
        emitProfileEdges(deliverables);

        checkBrokenReferences();
        checkUndefinedKeys(deliverables);
        checkShadowedKeys(deliverables);
        checkUnusedKeys(shipsByNode.keySet());
        checkOrphanTopics();

        return new ProjectGraph(Instant.now().toString(), absRoot.toString(), scope,
                graphDeliverables, nodes(shipsByNode), List.copyOf(edges), List.copyOf(issues));
    }

    // ---- discovery ----------------------------------------------------------

    private void walk(File dir, Deque<String> seeds) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        java.util.Arrays.sort(children, Comparator.comparing(File::getName));
        for (File child : children) {
            String name = child.getName();
            if (child.isDirectory()) {
                if (!name.startsWith(".") && !SKIP_DIRS.contains(name)) {
                    walk(child, seeds);
                }
            } else if (WALK_EXTENSIONS.contains(extension(name))) {
                String canon = canonical(child);
                FileInfo fi = load(canon);
                if (fi.kind == null) {
                    files.remove(canon);           // a non-DITA .xml file
                } else {
                    seeds.add(canon);
                }
            }
        }
    }

    /** Every walked map that no other map references, as synthetic deliverables. */
    private List<Deliverable> unreferencedRootMaps() {
        Set<String> referenced = new HashSet<>();
        for (FileInfo fi : files.values()) {
            for (LinkExtractor.Submap sub : fi.submaps) {
                if (sub.targetPath() != null && !sub.targetPath().equals(fi.canon)) {
                    referenced.add(sub.targetPath());
                }
            }
        }
        List<Deliverable> out = new ArrayList<>();
        Set<String> names = new HashSet<>();
        files.values().stream()
                .filter(fi -> "map".equals(fi.kind) && fi.exists && !referenced.contains(fi.canon))
                .sorted(Comparator.comparing(fi -> fi.id))
                .forEach(fi -> {
                    String name = stripExtension(fi.file.getName());
                    out.add(new Deliverable(names.add(name) ? name : fi.id,
                            fi.file.toPath(), (Path) null, null));
                });
        return out;
    }

    /** Load everything reachable from the seeds over any reference or key binding. */
    private void crawl(Deque<String> queue) {
        Set<String> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            String canon = queue.removeFirst();
            if (!seen.add(canon)) {
                continue;
            }
            FileInfo fi = load(canon);
            for (Reference r : fi.refs) {
                if (classify(fi, r) != null) {
                    queue.addLast(r.targetPath());
                }
            }
            for (KeyDefinition def : fi.keyDefs) {
                String[] target = keyTarget(def);
                if (target != null) {
                    queue.addLast(target[0]);
                }
            }
        }
    }

    private FileInfo load(String canon) {
        FileInfo existing = files.get(canon);
        if (existing != null) {
            return existing;
        }
        File file = new File(canon);
        FileInfo fi = new FileInfo(canon, file, idOf(canon), file.isFile());
        files.put(canon, fi);
        String ext = extension(file.getName());
        fi.kind = switch (ext) {
            case "ditamap" -> "map";
            case "ditaval" -> "ditaval";
            default -> "topic";
        };
        if (!fi.exists) {
            return fi;
        }
        if ("ditaval".equals(fi.kind)) {
            fi.type = "ditaval";
            return fi;
        }
        Sniff sniff = sniff(file, "map".equals(fi.kind) || "xml".equals(ext));
        if ("xml".equals(ext)) {
            if (sniff.rootName == null) {
                fi.kind = null;
            } else if (isMapRoot(sniff.rootName)) {
                fi.kind = "map";
            } else if (sniff.ditaArch || TOPIC_ROOTS.contains(sniff.rootName)) {
                fi.kind = "topic";
            } else {
                fi.kind = null;
            }
        }
        fi.type = sniff.rootName;
        fi.title = sniff.title;
        fi.reltableRefs = sniff.reltableRefs;
        fi.resourceOnlyRefs = sniff.resourceOnlyRefs;
        if (fi.kind != null) {
            LinkExtractor.ExtractionResult result = LinkExtractor.extract(file);
            fi.refs = result.references();
            fi.keyDefs = "map".equals(fi.kind) ? result.keyDefinitions() : List.of();
            fi.submaps = "map".equals(fi.kind) ? result.submaps() : List.of();
        }
        return fi;
    }

    // ---- classification -----------------------------------------------------

    /**
     * The edge kind of a path reference, or null when it contributes no edge (key
     * references, images/{@code src}, same-file fragments, keydef hrefs — those
     * become {@code keytarget} edges — and resource-only topicrefs).
     */
    private static String classify(FileInfo fi, Reference r) {
        if (r.isKeyReference() || r.targetPath() == null || r.targetPath().equals(fi.canon)) {
            return null;
        }
        String attr = r.attribute();
        String element = r.element();
        String ext = extension(r.targetPath());
        if ("conref".equals(attr)) {
            return "conref";
        }
        if (!"href".equals(attr) || "include".equals(element) || "image".equals(element)) {
            return null;
        }
        if ("map".equals(fi.kind)) {
            if (fi.reltableRefs.contains(position(r))) {
                return "dita".equals(ext) || "xml".equals(ext) ? "reltable" : null;
            }
            if ("ditavalref".equals(element) || "ditaval".equals(ext)) {
                return "ditavalref";
            }
            if ("mapref".equals(element) || "ditamap".equals(ext)) {
                return "mapref";
            }
            if ("xref".equals(element) || "link".equals(element)) {
                return "link";
            }
            if ("keydef".equals(element) || fi.resourceOnlyRefs.contains(position(r))) {
                return null;
            }
            return "dita".equals(ext) || "xml".equals(ext) ? "topicref" : null;
        }
        return "xref".equals(element) || "link".equals(element) ? "link" : null;
    }

    /** {canonical target, fragment} of a key definition's local href, or null. */
    private static String[] keyTarget(KeyDefinition def) {
        String href = def.href();
        if (href == null || href.isBlank() || def.source() == null) {
            return null;
        }
        String fragment = null;
        int hash = href.indexOf('#');
        if (hash >= 0) {
            fragment = href.substring(hash + 1);
            href = href.substring(0, hash);
        }
        if (href.isEmpty() || SCHEME.matcher(href).find()) {
            return null;
        }
        File target = new File(href);
        if (!target.isAbsolute()) {
            target = new File(def.source().getParentFile(), href);
        }
        return new String[] {canonical(target), fragment};
    }

    private static boolean inReltable(FileInfo fi, Reference r) {
        return fi.reltableRefs.contains(position(r));
    }

    // ---- publication sets ---------------------------------------------------

    private KeySpace keySpace(String rootMapCanon) {
        return keySpaces.computeIfAbsent(rootMapCanon, c -> KeySpace.fromRootMap(new File(c)));
    }

    /** The narrow publication set of a root map (canonical paths of existing files). */
    private Set<String> shipsOf(String rootMapCanon) {
        Set<String> cached = shipsByRootMap.get(rootMapCanon);
        if (cached != null) {
            return cached;
        }
        KeySpace ks = keySpace(rootMapCanon);
        Set<String> shipped = new LinkedHashSet<>();
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(rootMapCanon);
        while (!queue.isEmpty()) {
            String canon = queue.removeFirst();
            if (!seen.add(canon)) {
                continue;
            }
            FileInfo fi = load(canon);
            if (!fi.exists || fi.kind == null || "ditaval".equals(fi.kind)) {
                continue;
            }
            shipped.add(canon);
            for (Reference r : fi.refs) {
                if (r.isKeyReference()) {
                    if (inReltable(fi, r) || fi.resourceOnlyRefs.contains(position(r))
                            || "xref".equals(r.element()) || "link".equals(r.element())) {
                        continue;
                    }
                    File target = ks.resolveHrefFileLenient(r.keyName(), r.scope());
                    if (target != null) {
                        queue.addLast(canonical(target));
                    }
                } else {
                    String kind = classify(fi, r);
                    if (kind == null || !SHIPPING_KINDS.contains(kind)) {
                        continue;
                    }
                    queue.addLast(r.targetPath());
                }
            }
        }
        shipsByRootMap.put(rootMapCanon, shipped);
        return shipped;
    }

    // ---- edges --------------------------------------------------------------

    private void emitFileEdges(Map<String, String> firstRootMapShipping) {
        List<FileInfo> sorted = files.values().stream()
                .filter(fi -> fi.exists && fi.kind != null)
                .sorted(Comparator.comparing(fi -> fi.id)).toList();
        for (FileInfo fi : sorted) {
            for (Reference r : fi.refs) {
                Integer line = r.line() > 0 ? r.line() : null;
                if (r.isKeyReference()) {
                    edges.add(new GraphEdge(fi.id, "key:" + r.keyName(), r.attribute(), line,
                            null, r.fragment(), null, null));
                    if ("map".equals(fi.kind) && inReltable(fi, r)) {
                        String ctx = firstRootMapShipping.getOrDefault(fi.canon, fi.canon);
                        File target = keySpace(ctx).resolveHrefFileLenient(r.keyName(), r.scope());
                        if (target != null) {
                            FileInfo t = load(canonical(target));
                            edges.add(new GraphEdge(fi.id, t.id, "reltable", line, null, null,
                                    t.exists ? null : Boolean.TRUE, null));
                        }
                    }
                    continue;
                }
                String kind = classify(fi, r);
                if (kind == null) {
                    continue;
                }
                FileInfo t = load(r.targetPath());
                edges.add(new GraphEdge(fi.id, t.id, kind, line, null, r.fragment(),
                        t.exists ? null : Boolean.TRUE, null));
            }
            for (KeyDefinition def : fi.keyDefs) {
                Integer line = def.line() > 0 ? def.line() : null;
                String[] target = keyTarget(def);
                for (String name : def.keys().split("\\s+")) {
                    if (name.isEmpty()) {
                        continue;
                    }
                    edges.add(new GraphEdge(fi.id, "key:" + name, "keydef", line,
                            null, null, null, null));
                    if (target != null) {
                        FileInfo t = load(target[0]);
                        edges.add(new GraphEdge("key:" + name, t.id, "keytarget", line, fi.id,
                                target[1], t.exists ? null : Boolean.TRUE, null));
                    }
                }
            }
        }
    }

    private void emitProfileEdges(List<Deliverable> deliverables) {
        for (Deliverable d : deliverables) {
            String from = idOf(canonical(d.map().toFile()));
            for (Path dv : d.ditavals()) {
                FileInfo t = load(canonical(dv.toFile()));
                edges.add(new GraphEdge(from, t.id, "profile", null, null, null,
                        t.exists ? null : Boolean.TRUE, d.name()));
                if (!t.exists) {
                    issues.add(new GraphIssue("error", "broken-reference",
                            d.sourceFile() != null ? idOf(canonical(d.sourceFile().toFile())) : null,
                            null, "Deliverable \"" + d.name() + "\" profile references missing file \""
                                    + t.id + "\""));
                }
            }
        }
    }

    // ---- issues -------------------------------------------------------------

    private void checkBrokenReferences() {
        for (GraphEdge e : edges) {
            if (!Boolean.TRUE.equals(e.broken()) || "profile".equals(e.kind())) {
                continue;
            }
            String file = "keytarget".equals(e.kind()) ? e.via() : e.from();
            issues.add(new GraphIssue("error", "broken-reference", file, e.line(),
                    "Reference to missing file \"" + e.to() + "\""));
        }
    }

    private record KeyUse(String file, Integer line, String key, String scope) {}

    private void checkUndefinedKeys(List<Deliverable> deliverables) {
        Map<KeyUse, List<String>> failsIn = new LinkedHashMap<>();
        Map<KeyUse, List<String>> resolvesInShipping = new HashMap<>();
        List<FileInfo> sortedFiles = files.values().stream()
                .sorted(Comparator.comparing(fi -> fi.id)).toList();
        for (FileInfo fi : sortedFiles) {
            for (Reference r : fi.refs) {
                if (!r.isKeyReference()) {
                    continue;
                }
                KeyUse use = new KeyUse(fi.id, r.line() > 0 ? r.line() : null, r.keyName(), r.scope());
                for (Deliverable d : deliverables) {
                    String rootMap = canonical(d.map().toFile());
                    if (!shipsOf(rootMap).contains(fi.canon)) {
                        continue;
                    }
                    boolean resolves = keySpace(rootMap).resolveLenient(r.keyName(), r.scope()) != null;
                    (resolves ? resolvesInShipping : failsIn)
                            .computeIfAbsent(use, k -> new ArrayList<>()).add(d.name());
                }
            }
        }
        for (Map.Entry<KeyUse, List<String>> entry : failsIn.entrySet()) {
            KeyUse use = entry.getKey();
            List<String> resolvesAnywhere = new ArrayList<>();
            for (Deliverable d : deliverables) {
                if (keySpace(canonical(d.map().toFile())).resolveLenient(use.key(), use.scope()) != null) {
                    resolvesAnywhere.add(d.name());
                }
            }
            String fails = String.join(", ", entry.getValue());
            boolean inconsistent = resolvesInShipping.containsKey(use);
            String message = resolvesAnywhere.isEmpty()
                    ? "Key \"" + use.key() + "\" is undefined in " + fails
                    : "Key \"" + use.key() + "\" resolves in " + String.join(", ", resolvesAnywhere)
                            + ", fails in " + fails;
            issues.add(new GraphIssue(inconsistent ? "warning" : "error",
                    inconsistent ? "key-resolves-inconsistently" : "undefined-key",
                    use.file(), use.line(), message));
        }
    }

    private record Winner(KeyDefinition def, String mapId) {}

    private record QueueEntry(String canon, String prefix) {}

    /** Replays {@link KeySpace}'s BFS first-definition-wins walk to find the losers. */
    private void checkShadowedKeys(List<Deliverable> deliverables) {
        Set<String> rootMaps = new LinkedHashSet<>();
        for (Deliverable d : deliverables) {
            rootMaps.add(canonical(d.map().toFile()));
        }
        for (String rootMap : rootMaps) {
            Map<String, Winner> winners = new HashMap<>();
            Set<String> visited = new HashSet<>();
            Deque<QueueEntry> queue = new ArrayDeque<>();
            queue.add(new QueueEntry(rootMap, ""));
            while (!queue.isEmpty()) {
                QueueEntry entry = queue.removeFirst();
                FileInfo fi = load(entry.canon());
                if (!visited.add(entry.canon() + '\n' + entry.prefix()) || !fi.exists) {
                    continue;
                }
                for (KeyDefinition def : fi.keyDefs) {
                    String effScope = combine(entry.prefix(), def.scope());
                    for (String name : def.keys().split("\\s+")) {
                        if (name.isEmpty()) {
                            continue;
                        }
                        String qualified = combine(effScope, name);
                        Winner winner = winners.putIfAbsent(qualified, new Winner(def, fi.id));
                        if (winner != null && winner.def() != def) {
                            issues.add(new GraphIssue("warning", "shadowed-key", fi.id,
                                    def.line() > 0 ? def.line() : null,
                                    "Key \"" + qualified + "\" is shadowed by the definition in "
                                            + winner.mapId() + " line " + winner.def().line()));
                        }
                    }
                }
                for (LinkExtractor.Submap sub : fi.submaps) {
                    if (sub.targetPath() != null) {
                        queue.addLast(new QueueEntry(sub.targetPath(),
                                combine(entry.prefix(), sub.scope())));
                    }
                }
            }
        }
    }

    private void checkUnusedKeys(Set<String> shippedFiles) {
        Set<String> used = new HashSet<>();
        for (FileInfo fi : files.values()) {
            for (Reference r : fi.refs) {
                if (r.isKeyReference()) {
                    String name = r.keyName();
                    used.add(name);
                    used.add(name.substring(name.lastIndexOf('.') + 1));
                }
            }
        }
        files.values().stream()
                .filter(fi -> "map".equals(fi.kind) && shippedFiles.contains(fi.canon))
                .sorted(Comparator.comparing(fi -> fi.id))
                .forEach(fi -> {
                    for (KeyDefinition def : fi.keyDefs) {
                        if (def.subjectScheme()) {
                            continue;
                        }
                        for (String name : def.keys().split("\\s+")) {
                            if (!name.isEmpty() && !used.contains(name)) {
                                issues.add(new GraphIssue("info", "unused-key", fi.id,
                                        def.line() > 0 ? def.line() : null,
                                        "Key \"" + name + "\" is defined but never referenced"));
                            }
                        }
                    }
                });
    }

    private void checkOrphanTopics() {
        Set<String> referenced = new HashSet<>();
        for (GraphEdge e : edges) {
            if (!e.from().equals(e.to())) {
                referenced.add(e.to());
            }
        }
        files.values().stream()
                .filter(fi -> fi.exists && "topic".equals(fi.kind)
                        && fi.id.toLowerCase().endsWith(".dita") && !referenced.contains(fi.id))
                .sorted(Comparator.comparing(fi -> fi.id))
                .forEach(fi -> issues.add(new GraphIssue("info", "orphan-topic", fi.id, null,
                        "Topic is not referenced by any map or topic")));
    }

    // ---- nodes --------------------------------------------------------------

    private List<GraphNode> nodes(Map<String, List<String>> shipsByNode) {
        List<GraphNode> nodes = new ArrayList<>();
        files.values().stream()
                .filter(fi -> fi.kind != null)
                .sorted(Comparator.comparing(fi -> fi.id))
                .forEach(fi -> {
                    List<String> ships = shipsByNode.get(fi.canon);
                    nodes.add(new GraphNode(fi.id, fi.kind, fi.type, fi.title,
                            fi.exists ? null : Boolean.TRUE,
                            ships == null ? null : List.copyOf(ships)));
                });
        Set<String> keys = new TreeSet<>();
        for (GraphEdge e : edges) {
            if (e.from().startsWith("key:")) {
                keys.add(e.from());
            }
            if (e.to().startsWith("key:")) {
                keys.add(e.to());
            }
        }
        for (String key : keys) {
            nodes.add(new GraphNode(key, "key", null, null, null, null));
        }
        return nodes;
    }

    // ---- sniffing -----------------------------------------------------------

    private static final class FileInfo {
        final String canon;
        final File file;
        final String id;
        final boolean exists;
        String kind;
        String type;
        String title;
        List<Reference> refs = List.of();
        List<KeyDefinition> keyDefs = List.of();
        List<LinkExtractor.Submap> submaps = List.of();
        Set<String> reltableRefs = Set.of();
        Set<String> resourceOnlyRefs = Set.of();

        FileInfo(String canon, File file, String id, boolean exists) {
            this.canon = canon;
            this.file = file;
            this.id = id;
            this.exists = exists;
        }
    }

    private static final class Sniff extends DefaultHandler {
        private static final SAXException STOP = new SAXException("stop");
        private static final String[] REF_ATTRS = {"href", "keyref", "conkeyref", "conref"};

        final boolean fullPass;
        String rootName;
        boolean ditaArch;
        String title;
        final Set<String> reltableRefs = new HashSet<>();
        final Set<String> resourceOnlyRefs = new HashSet<>();

        private Locator locator;
        private int depth;
        private int titleDepth = -1;
        private int reltableDepth = -1;
        private boolean mapRoot;
        private String titleAttr;
        private final StringBuilder text = new StringBuilder();
        /** Elements open inside the title: {depth, text length at start}, with their key names alongside. */
        private final java.util.Deque<int[]> inlineKeys = new java.util.ArrayDeque<>();
        private final java.util.Deque<String> inlineKeyNames = new java.util.ArrayDeque<>();

        Sniff(boolean fullPass) {
            this.fullPass = fullPass;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attrs)
                throws SAXException {
            depth++;
            String name = localName != null && !localName.isEmpty() ? localName : qName;
            if (depth == 1) {
                rootName = name;
                mapRoot = isMapRoot(name);
                titleAttr = attrs.getValue("title");
                for (int i = 0; i < attrs.getLength(); i++) {
                    if ("DITAArchVersion".equals(attrs.getLocalName(i))
                            || attrs.getQName(i).endsWith("DITAArchVersion")) {
                        ditaArch = true;
                    }
                }
            }
            if (title == null && titleDepth < 0
                    && ((depth == 2 && ("title".equals(name) || "glossterm".equals(name)))
                        || "mainbooktitle".equals(name))) {
                titleDepth = depth;
                text.setLength(0);
            } else if (titleDepth >= 0) {
                // "What is <keyword keyref="product"/>?" has no text for the key; keep a marker for it.
                String key = attrs.getValue("keyref");
                if (key == null || key.isEmpty()) {
                    key = attrs.getValue("conkeyref");
                }
                inlineKeys.push(new int[] {depth, text.length()});
                inlineKeyNames.push(key == null ? "" : key);
            }
            if (!mapRoot) {
                return;
            }
            if ("reltable".equals(name) && reltableDepth < 0) {
                reltableDepth = depth;
                return;
            }
            boolean resourceOnly = "resource-only".equals(attrs.getValue("processing-role"));
            if (reltableDepth < 0 && !resourceOnly) {
                return;
            }
            int line = locator != null ? locator.getLineNumber() : -1;
            for (String attr : REF_ATTRS) {
                String value = attrs.getValue(attr);
                if (value != null && !value.isEmpty()) {
                    String pos = position(line, attr, value);
                    if (reltableDepth >= 0) {
                        reltableRefs.add(pos);
                    }
                    if (resourceOnly) {
                        resourceOnlyRefs.add(pos);
                    }
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (titleDepth >= 0 && text.length() < 1024) {
                text.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (!inlineKeys.isEmpty() && inlineKeys.peek()[0] == depth) {
                int textAtStart = inlineKeys.pop()[1];
                String key = inlineKeyNames.pop();
                if (!key.isEmpty() && text.toString().substring(textAtStart).isBlank() && text.length() < 1024) {
                    text.append('[').append(key).append(']');
                }
            }
            if (titleDepth == depth) {
                titleDepth = -1;
                String t = text.toString().replaceAll("\\s+", " ").trim();
                if (!t.isEmpty()) {
                    title = t;
                    if (!fullPass && !mapRoot) {
                        throw STOP;
                    }
                }
            }
            if (reltableDepth == depth) {
                reltableDepth = -1;
            }
            depth--;
        }

        @Override
        public void endDocument() {
            if (title == null && titleAttr != null && !titleAttr.isBlank()) {
                title = titleAttr.trim();
            }
        }
    }

    private static Sniff sniff(File file, boolean fullPass) {
        Sniff handler = new Sniff(fullPass);
        try (InputStream in = new FileInputStream(file)) {
            XMLReader reader = HardenedSax.newReader();
            reader.setContentHandler(handler);
            reader.parse(new InputSource(in));
        } catch (Exception e) {
            // STOP after the title, or a malformed file: keep what was read.
        }
        if (handler.title == null && handler.titleAttr != null && !handler.titleAttr.isBlank()) {
            handler.title = handler.titleAttr.trim();
        }
        return handler;
    }

    // ---- helpers ------------------------------------------------------------

    private static boolean isMapRoot(String rootName) {
        return rootName.endsWith("map") || "subjectScheme".equals(rootName);
    }

    private static String position(Reference r) {
        return position(r.line(), r.attribute(), r.rawValue());
    }

    private static String position(int line, String attribute, String value) {
        return line + "\n" + attribute + "\n" + value;
    }

    private static String combine(String a, String b) {
        if (a == null || a.isEmpty()) {
            return b == null ? "" : b;
        }
        return b == null || b.isEmpty() ? a : a + "." + b;
    }

    private String idOf(String canon) {
        Path p = Path.of(canon);
        String rel;
        try {
            rel = rootCanon.relativize(p).toString();
        } catch (IllegalArgumentException e) {
            rel = p.toString();                    // e.g. a different Windows drive
        }
        return rel.replace(File.separatorChar, '/');
    }

    private static String extension(String name) {
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf(File.separatorChar));
        int dot = name.lastIndexOf('.');
        return dot > slash ? name.substring(dot + 1).toLowerCase() : "";
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String canonical(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }
}
