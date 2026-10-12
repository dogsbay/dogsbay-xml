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

package com.dogsbay.dogsbayaieditor.ditaproject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.DogsBayURLUtilities;
import com.dogsbay.xml.XElement;

/**
 * Loads a {@link ProjectContext} for a workspace folder from DITA-OT project
 * files (<a href="https://www.dita-ot.org/dev/topics/using-project-files.html">spec</a>).
 *
 * <p>A workspace contains 0..N DITA project files, each defining
 * {@link Deliverable}s. They are <strong>auto-discovered</strong>: the
 * conventional {@code project.&#123;json,xml,yaml&#125;} at the root, plus any
 * {@code .json/.xml/.yaml} under {@code projects/} or the root that
 * <em>content-sniffs</em> as a DITA-OT project (has {@code deliverables} /
 * {@code contexts} / {@code publications} / {@code includes}, or an XML
 * {@code <project>} root). Arbitrary file names are supported
 * ({@code rosa-html5.json}, …). The deliverables of every discovered file are
 * unioned; each {@link Deliverable} records its {@link Deliverable#sourceFile()}.
 *
 * <p>The serialization format never escapes this class. Each file is parsed into
 * a format-agnostic {@code RawProject}; {@code <include>}s are followed to merge
 * shared {@code context}/{@code publication} definitions (so cross-file
 * {@code idref}s resolve), and a {@code publication idref} inherits its target's
 * params with local params overriding by name.
 *
 * <p>When no project file exists, a single implicit deliverable is
 * {@linkplain #synthesize synthesized} from the workspace's default root map.
 * Catalogs and the DITA-OT path are supplied by the caller.
 */
public final class ProjectContextLoader {

    /** Conventional project-file names at the workspace root, in precedence order. */
    public static final List<String> PROJECT_FILES =
        List.of("project.json", "project.xml", "project.yaml", "project.yml");

    private ProjectContextLoader() {}

    /**
     * Resolve the project context for a workspace: union the deliverables of every
     * discovered DITA project file, else synthesize from {@code fallbackRootMap}.
     *
     * @param projectRoot     the workspace folder
     * @param fallbackRootMap default root map when there is no project file (may be null)
     * @param catalogs        catalogs for entity resolution (may be null/empty)
     * @param ditaOtPath      resolved DITA-OT path (may be null)
     */
    public static ProjectContext load(Path projectRoot, Path fallbackRootMap,
            List<Path> catalogs, Path ditaOtPath) throws IOException {
        List<Path> files = discoverAll(projectRoot);
        if (files.isEmpty()) {
            return synthesize(projectRoot, fallbackRootMap, catalogs, ditaOtPath);
        }
        List<Deliverable> all = new ArrayList<>();
        for (Path f : files) {
            all.addAll(parseDeliverables(f));
        }
        Path primary = files.get(0);
        return new ProjectContext(projectRoot, primary, formatOf(primary),
                catalogs, all, ditaOtPath, files);
    }

    /** Find the highest-precedence conventional project file directly under the root. */
    public static Optional<Path> discover(Path projectRoot) {
        if (projectRoot == null) {
            return Optional.empty();
        }
        for (String name : PROJECT_FILES) {
            Path p = projectRoot.resolve(name);
            if (Files.isRegularFile(p)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /**
     * Discover every DITA-OT project file in the workspace: conventional root names
     * (always), then content-sniffed {@code .json/.xml/.yaml/.yml} under
     * {@code projects/} and the root. Ordered, de-duplicated by canonical path.
     */
    public static List<Path> discoverAll(Path projectRoot) {
        if (projectRoot == null) {
            return List.of();
        }
        Set<Path> seen = new LinkedHashSet<>();
        // 1. Conventional root names first (highest precedence, no sniff needed).
        for (String name : PROJECT_FILES) {
            Path p = projectRoot.resolve(name);
            if (Files.isRegularFile(p)) {
                seen.add(norm(p));
            }
        }
        // 2. projects/ then root, content-sniffed, name-sorted for determinism.
        for (Path dir : List.of(projectRoot.resolve("projects"), projectRoot)) {
            sniffDir(dir, seen);
        }
        return new ArrayList<>(seen);
    }

    private static void sniffDir(Path dir, Set<Path> seen) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isRegularFile)
             .filter(p -> hasProjectExt(p))
             .sorted()
             .forEach(p -> {
                 Path n = norm(p);
                 if (!seen.contains(n) && isProjectFile(p)) {
                     seen.add(n);
                 }
             });
        } catch (IOException ignore) {
            // unreadable dir → contribute nothing
        }
    }

    private static boolean hasProjectExt(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".json") || n.endsWith(".xml")
                || n.endsWith(".yaml") || n.endsWith(".yml");
    }

    /** Cheap content sniff: does this file look like a DITA-OT project file? */
    public static boolean isProjectFile(Path p) {
        try {
            String fmt = formatOf(p);
            if ("xml".equals(fmt)) {
                DogsBayDocument doc =
                    new DogsBayDocument(DogsBayURLUtilities.getURLFromFile(p.toFile()));
                doc.loadWithoutSubstitution();
                XElement root = doc.getRoot();
                return root != null && "project".equals(root.getName());
            }
            JsonNode root = ("yaml".equals(fmt) ? new YAMLMapper() : new ObjectMapper())
                    .readTree(p.toFile());
            // Require a DITA-OT-specific signal. "includes" alone is too weak (many
            // unrelated configs have a top-level includes), and a definitions-only
            // include file still carries contexts/publications.
            return root != null && root.isObject()
                    && (root.has("deliverables") || root.has("contexts")
                        || root.has("publications"));
        } catch (Exception e) {
            return false;
        }
    }

    /** A single implicit deliverable named {@code "default"} from a root map. */
    public static ProjectContext synthesize(Path projectRoot, Path rootMap,
            List<Path> catalogs, Path ditaOtPath) {
        List<Deliverable> deliverables = rootMap == null
                ? List.of()
                : List.of(new Deliverable("default", rootMap.toAbsolutePath().normalize(),
                        (Path) null, null));
        return new ProjectContext(projectRoot, null, "synthesized",
                catalogs, deliverables, ditaOtPath, List.of());
    }

    /**
     * Parse the fully-resolved deliverables of one project file, following its
     * {@code <include>}s to resolve {@code idref}s. Each deliverable records this
     * file as its {@link Deliverable#sourceFile()}.
     */
    public static List<Deliverable> parseDeliverables(Path projectFile) throws IOException {
        RawProject rp = parseRaw(projectFile);
        Defs defs = new Defs();
        Set<Path> visited = new LinkedHashSet<>();
        visited.add(norm(projectFile));
        Path base = base(projectFile);
        for (String inc : rp.includes()) {
            mergeInclude(base.resolve(inc.trim()).normalize(), defs, visited);
        }
        defs.merge(rp.contexts(), rp.publications());
        return resolve(rp.deliverables(), defs, projectFile);
    }

    static String formatOf(Path file) {
        String n = file.getFileName().toString().toLowerCase();
        if (n.endsWith(".json")) return "json";
        if (n.endsWith(".xml")) return "xml";
        if (n.endsWith(".yaml") || n.endsWith(".yml")) return "yaml";
        return "unknown";
    }

    private static Path base(Path projectFile) {
        return projectFile.toAbsolutePath().normalize().getParent();
    }

    private static Path norm(Path p) {
        return p.toAbsolutePath().normalize();
    }

    private static Path resolve(Path base, String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        return base.resolve(href.trim()).normalize();
    }

    // ── include / idref resolution ──────────────────────────────────────

    /** Shared context/publication definitions accumulated across included files. */
    private static final class Defs {
        final Map<String, RawCtx> contexts = new LinkedHashMap<>();
        final Map<String, RawPub> publications = new LinkedHashMap<>();

        void merge(Map<String, RawCtx> c, Map<String, RawPub> p) {
            contexts.putAll(c);       // later (including this file) overrides earlier
            publications.putAll(p);
        }
    }

    private static void mergeInclude(Path file, Defs defs, Set<Path> visited)
            throws IOException {
        Path n = norm(file);
        if (visited.contains(n) || !Files.isRegularFile(file)) {
            return; // cycle guard / missing include → skip silently
        }
        visited.add(n);
        RawProject rp = parseRaw(file);
        Path base = base(file);
        for (String inc : rp.includes()) {
            mergeInclude(base.resolve(inc.trim()).normalize(), defs, visited);
        }
        defs.merge(rp.contexts(), rp.publications());
    }

    private static List<Deliverable> resolve(List<RawDeliv> delivs, Defs defs, Path file) {
        List<Deliverable> out = new ArrayList<>();
        for (RawDeliv d : delivs) {
            RawCtx ctx = d.inlineCtx() != null ? d.inlineCtx()
                    : (d.ctxIdref() != null ? defs.contexts.get(d.ctxIdref()) : null);
            if (ctx == null || ctx.map() == null) {
                continue; // a deliverable with no resolvable input map is skipped
            }
            RawPub base = d.pubIdref() != null ? defs.publications.get(d.pubIdref()) : null;
            RawPub pub = mergePub(base, d.pubOverride());

            List<Path> ditavals = new ArrayList<>(ctx.ditavals());
            if (pub != null) {
                ditavals.addAll(pub.ditavals());
            }
            String transtype = pub != null ? pub.transtype() : null;
            List<Param> params = pub != null ? pub.params() : List.of();
            String name = firstNonBlank(d.name(), ctx.id());
            out.add(new Deliverable(
                    name != null ? name : "deliverable-" + (out.size() + 1),
                    ctx.map(), ditavals, transtype, d.output(), params, file, ctx.id()));
        }
        return out;
    }

    /** Merge an inline publication onto an {@code idref} base; override wins. */
    private static RawPub mergePub(RawPub base, RawPub override) {
        if (base == null) {
            return override;
        }
        if (override == null) {
            return base;
        }
        String transtype = override.transtype() != null ? override.transtype() : base.transtype();
        Map<String, Param> byName = new LinkedHashMap<>();
        for (Param p : base.params()) {
            byName.put(p.name(), p);
        }
        for (Param p : override.params()) {
            byName.put(p.name(), p); // local overrides by name
        }
        List<Path> ditavals = new ArrayList<>(base.ditavals());
        ditavals.addAll(override.ditavals());
        return new RawPub(override.id(), transtype, new ArrayList<>(byName.values()), ditavals);
    }

    // ── format-agnostic intermediate ────────────────────────────────────

    private record RawCtx(String id, Path map, List<Path> ditavals) {}
    private record RawPub(String id, String transtype, List<Param> params, List<Path> ditavals) {}
    private record RawDeliv(String name, String ctxIdref, RawCtx inlineCtx,
            String pubIdref, RawPub pubOverride, Path output) {}
    private record RawProject(List<String> includes, Map<String, RawCtx> contexts,
            Map<String, RawPub> publications, List<RawDeliv> deliverables) {}

    private static RawProject parseRaw(Path projectFile) throws IOException {
        String fmt = formatOf(projectFile);
        Path base = base(projectFile);
        return switch (fmt) {
            case "xml" -> parseRawXml(projectFile, base);
            case "json" -> parseRawTree(new ObjectMapper().readTree(projectFile.toFile()), base);
            case "yaml" -> parseRawTree(new YAMLMapper().readTree(projectFile.toFile()), base);
            default -> throw new IOException("Unsupported project file: " + projectFile);
        };
    }

    // ── JSON / YAML ─────────────────────────────────────────────────────

    private static RawProject parseRawTree(JsonNode root, Path base) {
        List<String> includes = new ArrayList<>();
        Map<String, RawCtx> contexts = new LinkedHashMap<>();
        Map<String, RawPub> publications = new LinkedHashMap<>();
        List<RawDeliv> delivs = new ArrayList<>();
        if (root == null || !root.isObject()) {
            return new RawProject(includes, contexts, publications, delivs);
        }
        JsonNode includesNode = root.path("includes");
        if (includesNode.isTextual()) { // a single "includes": "x.json"
            includes.add(includesNode.asText());
        }
        for (JsonNode inc : includesNode) {
            if (inc.isTextual()) {
                includes.add(inc.asText());
            } else {
                String href = text(inc, "href");
                if (href != null) includes.add(href);
            }
        }
        for (JsonNode c : root.path("contexts")) {
            RawCtx ctx = ctxFromJson(c, base);
            if (ctx.id() != null) contexts.put(ctx.id(), ctx);
        }
        for (JsonNode p : root.path("publications")) {
            RawPub pub = pubFromJson(p, base);
            if (pub.id() != null) publications.put(pub.id(), pub);
        }
        for (JsonNode d : root.path("deliverables")) {
            String name = firstNonBlank(text(d, "name"), text(d, "id"));
            JsonNode cn = d.path("context");
            String ctxIdref = cn.isTextual() ? cn.asText() : text(cn, "idref");
            RawCtx inlineCtx = (!cn.isMissingNode() && cn.isObject() && ctxIdref == null)
                    ? ctxFromJson(cn, base) : null;
            JsonNode pn = d.path("publication");
            String pubIdref = pn.isTextual() ? pn.asText() : text(pn, "idref");
            RawPub pubOverride = (!pn.isMissingNode() && pn.isObject())
                    ? pubFromJson(pn, base) : null;
            Path output = text(d, "output") != null ? Path.of(text(d, "output")) : null;
            delivs.add(new RawDeliv(name, ctxIdref, inlineCtx, pubIdref, pubOverride, output));
        }
        return new RawProject(includes, contexts, publications, delivs);
    }

    private static RawCtx ctxFromJson(JsonNode c, Path base) {
        Path map = resolve(base, text(c, "input"));
        List<Path> ditavals = new ArrayList<>();
        // legacy flat "ditaval", then DITA-OT "profiles.ditavals[]"
        Path flat = resolve(base, text(c, "ditaval"));
        if (flat != null) {
            ditavals.add(flat);
        }
        for (JsonNode dv : c.path("profiles").path("ditavals")) {
            String href = dv.isTextual() ? dv.asText() : text(dv, "href");
            Path p = resolve(base, href);
            if (p != null) ditavals.add(p);
        }
        return new RawCtx(text(c, "id"), map, ditavals);
    }

    private static RawPub pubFromJson(JsonNode p, Path base) {
        List<Param> params = new ArrayList<>();
        for (JsonNode pm : p.path("params")) {
            Param param = paramFromJson(pm);
            if (param != null) params.add(param);
        }
        List<Path> ditavals = new ArrayList<>();
        for (JsonNode dv : p.path("profiles").path("ditavals")) {
            String href = dv.isTextual() ? dv.asText() : text(dv, "href");
            Path d = resolve(base, href);
            if (d != null) ditavals.add(d);
        }
        return new RawPub(text(p, "id"), text(p, "transtype"), params, ditavals);
    }

    private static Param paramFromJson(JsonNode pm) {
        String name = text(pm, "name");
        if (name == null) {
            return null;
        }
        if (pm.path("path").isTextual()) {
            return new Param(name, pm.path("path").asText(), Param.Kind.PATH);
        }
        if (pm.path("href").isTextual()) {
            return new Param(name, pm.path("href").asText(), Param.Kind.HREF);
        }
        return new Param(name, text(pm, "value"), Param.Kind.VALUE);
    }

    // ── XML (namespace-safe via local names) ────────────────────────────

    private static RawProject parseRawXml(Path projectFile, Path base) throws IOException {
        XElement root;
        try {
            DogsBayDocument doc =
                new DogsBayDocument(DogsBayURLUtilities.getURLFromFile(projectFile.toFile()));
            doc.loadWithoutSubstitution();
            root = doc.getRoot();
        } catch (Exception e) {
            throw new IOException("Failed to parse project file: " + projectFile, e);
        }
        if (root == null) {
            throw new IOException("Empty project file: " + projectFile);
        }
        List<String> includes = new ArrayList<>();
        Map<String, RawCtx> contexts = new LinkedHashMap<>();
        Map<String, RawPub> publications = new LinkedHashMap<>();
        List<RawDeliv> delivs = new ArrayList<>();
        for (XElement e : childrenNamed(root, "include")) {
            String href = e.getAttribute("href");
            if (href != null && !href.isBlank()) includes.add(href);
        }
        for (XElement c : childrenNamed(root, "context")) {
            RawCtx ctx = ctxFromXml(c, base);
            if (ctx.id() != null) contexts.put(ctx.id(), ctx);
        }
        for (XElement p : childrenNamed(root, "publication")) {
            RawPub pub = pubFromXml(p, base);
            if (pub.id() != null) publications.put(pub.id(), pub);
        }
        for (XElement d : childrenNamed(root, "deliverable")) {
            String name = firstNonBlank(d.getAttribute("name"), d.getAttribute("id"));
            XElement cn = firstChild(d, "context");
            String ctxIdref = cn != null ? cn.getAttribute("idref") : null;
            RawCtx inlineCtx = (cn != null && ctxIdref == null) ? ctxFromXml(cn, base) : null;
            XElement pn = firstChild(d, "publication");
            String pubIdref = pn != null ? pn.getAttribute("idref") : null;
            RawPub pubOverride = pn != null ? pubFromXml(pn, base) : null;
            XElement out = firstChild(d, "output");
            Path output = (out != null && out.getAttribute("href") != null)
                    ? Path.of(out.getAttribute("href")) : null;
            delivs.add(new RawDeliv(name, ctxIdref, inlineCtx, pubIdref, pubOverride, output));
        }
        return new RawProject(includes, contexts, publications, delivs);
    }

    private static RawCtx ctxFromXml(XElement c, Path base) {
        XElement input = firstChild(c, "input");
        Path map = input != null ? resolve(base, input.getAttribute("href")) : null;
        return new RawCtx(c.getAttribute("id"), map, ditavalsFromXml(c, base));
    }

    private static RawPub pubFromXml(XElement p, Path base) {
        List<Param> params = new ArrayList<>();
        for (XElement pm : childrenNamed(p, "param")) {
            String name = pm.getAttribute("name");
            if (name == null || name.isBlank()) {
                continue;
            }
            if (pm.getAttribute("path") != null) {
                params.add(new Param(name, pm.getAttribute("path"), Param.Kind.PATH));
            } else if (pm.getAttribute("href") != null) {
                params.add(new Param(name, pm.getAttribute("href"), Param.Kind.HREF));
            } else {
                params.add(new Param(name, pm.getAttribute("value"), Param.Kind.VALUE));
            }
        }
        return new RawPub(p.getAttribute("id"), p.getAttribute("transtype"), params,
                ditavalsFromXml(p, base));
    }

    /** All {@code <profile>/<ditaval href>} under an element, in order. */
    private static List<Path> ditavalsFromXml(XElement parent, Path base) {
        List<Path> out = new ArrayList<>();
        for (XElement profile : childrenNamed(parent, "profile")) {
            for (XElement dv : childrenNamed(profile, "ditaval")) {
                Path p = resolve(base, dv.getAttribute("href"));
                if (p != null) out.add(p);
            }
        }
        return out;
    }

    private static List<XElement> childrenNamed(XElement parent, String local) {
        List<XElement> out = new ArrayList<>();
        for (XElement e : parent.getElements()) {
            if (local.equals(e.getName())) {
                out.add(e);
            }
        }
        return out;
    }

    private static XElement firstChild(XElement parent, String local) {
        for (XElement e : parent.getElements()) {
            if (local.equals(e.getName())) {
                return e;
            }
        }
        return null;
    }

    // ── small helpers ───────────────────────────────────────────────────

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() && !v.asText().isBlank() ? v.asText() : null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
