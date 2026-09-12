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

package com.dogsbay.dogsbayaieditor.project;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import com.dogsbay.dogsbayaieditor.framework.DefaultDitaOtFramework;
import com.dogsbay.dogsbayaieditor.framework.FrameworkProperties;
import com.dogsbay.dogsbayaieditor.links.HardenedSax;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Fills in a project's DITA settings on open so the editor "just works":
 * detects a DITA project, sets the default root map, and associates the
 * pre-installed DITA-OT framework.
 *
 * <p>Strictly non-destructive: only <em>unset</em> fields are populated, so an
 * explicit user choice (or a project-local {@code .dogsbay/config.xml}) is never
 * overridden. Safe and quiet — never throws.
 *
 * <p>Root-map detection order: DITA-OT {@code project.json} input → the single
 * {@code .ditamap} in the tree → the map not referenced by any other map (the
 * true root), shallowest path winning ties.
 */
public final class ProjectAutoConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectAutoConfigurer.class);

    /** How deep to scan for maps/topics — deep enough for real projects, bounded for speed. */
    private static final int MAX_DEPTH = 6;

    /** Directories never worth scanning (build output, VCS, editor temp). */
    private static final Set<String> SKIP_DIRS = Set.of("out", "build", "target", "temp", ".git", ".svn", "node_modules");

    /**
     * What the configurer did, for a status-bar note.
     *
     * @param isDita      whether the folder is a DITA project
     * @param rootMap     the resolved root map (relative), or null
     * @param framework   the framework name, or null
     * @param fromConfig  values came from a committed {@code .dogsbay/config.xml}
     * @param wroteConfig a fresh {@code .dogsbay/config.xml} was materialized
     * @param mutated     a previously-unset runtime project field was actually set
     */
    public record Result(boolean isDita, String rootMap, String framework,
                         boolean fromConfig, boolean wroteConfig, boolean mutated) {
        static Result none() { return new Result(false, null, null, false, false, false); }
        /** A runtime field was set, or a config file was written — worth a status note. */
        public boolean changedAnything() { return mutated || wroteConfig; }
    }

    private ProjectAutoConfigurer() {}

    /**
     * Cheap sync used on <em>every</em> project activation (open / switch / startup
     * restore / headless): if the folder has a committed {@code .dogsbay/config.xml},
     * load it and fill any unset runtime project fields. Does <strong>no</strong>
     * filesystem scan and <strong>never writes</strong> — safe to call on the EDT
     * and on command paths. Returns {@link Result#none()} when there's no committed
     * config (detection + materialization is reserved for {@link #configure}).
     *
     * @param project the project to sync in place
     * @return what was synced (for an optional status note)
     */
    public static Result syncFromConfig(ProjectProperties project) {
        try {
            String folderPath = project.getFolderPath();
            if (isBlank(folderPath)) {
                return Result.none();
            }
            Path root = new File(folderPath).toPath();
            if (!DogsbayProjectConfig.exists(root)) {
                return Result.none();
            }
            DogsbayProjectConfig dc = DogsbayProjectConfig.load(root);
            if (!ProjectProperties.TYPE_DITA.equals(dc.getProjectType())) {
                return Result.none();
            }
            boolean mutated = false;
            if (isBlank(project.getProjectType())) {
                project.setProjectType(ProjectProperties.TYPE_DITA);
                mutated = true;
            }
            if (isBlank(project.getDefaultRootMap()) && notBlank(dc.getDefaultRootMap())) {
                project.setDefaultRootMap(dc.getDefaultRootMap());
                mutated = true;
            }
            if (isBlank(project.getFrameworkName()) && notBlank(dc.getFramework())) {
                project.setFrameworkName(dc.getFramework());
                mutated = true;
            }
            return new Result(true, dc.getDefaultRootMap(), dc.getFramework(), true, false, mutated);
        } catch (Exception e) {
            LOG.warn("syncFromConfig failed for '{}': {}", project.getName(), e.getMessage());
            return Result.none();
        }
    }

    /**
     * Configure {@code project} (a freshly opened/created project) in place, with
     * the project-local {@code .dogsbay/config.xml} as the source of truth:
     *
     * <ol>
     *   <li>load {@code .dogsbay/config.xml} — its type/root-map/framework win;</li>
     *   <li>for anything it doesn't specify, auto-detect from the folder;</li>
     *   <li>sync the result into the runtime {@link ProjectProperties} so the Map
     *       Explorer and publishing see it;</li>
     *   <li>if there was no committed config, materialize one (portable + shareable).</li>
     * </ol>
     *
     * The framework is recorded by <em>name</em> only — the absolute DITA-OT path
     * resolves locally (per machine) via the framework fallback, so it stays
     * portable. Strictly fills unset runtime fields; never throws.
     *
     * @param project the project to fill in
     * @param config  the editor config (to find the default DITA-OT framework); may be null
     * @return what was set; {@link Result#none()} for non-DITA folders
     */
    public static Result configure(ProjectProperties project, ConfigurationProperties config) {
        try {
            String folderPath = project.getFolderPath();
            if (isBlank(folderPath)) {
                return Result.none();
            }
            File folder = new File(folderPath);
            if (!folder.isDirectory()) {
                return Result.none();
            }
            Path root = folder.toPath();

            boolean hasConfig = DogsbayProjectConfig.exists(root);
            DogsbayProjectConfig dc = DogsbayProjectConfig.load(root);

            // Type: committed config wins; else detect. Bail on non-DITA folders.
            String type = notBlank(dc.getProjectType()) ? dc.getProjectType()
                    : (looksLikeDita(folder) ? ProjectProperties.TYPE_DITA : null);
            if (!ProjectProperties.TYPE_DITA.equals(type)) {
                return Result.none();
            }

            // Root map: committed config wins; else detect (relative to the folder).
            String rootMap = notBlank(dc.getDefaultRootMap()) ? dc.getDefaultRootMap() : null;
            if (rootMap == null) {
                File r = detectRootMap(folder);
                if (r != null) {
                    rootMap = relativize(folder, r);
                }
            }

            // Framework: committed config wins; else the default DITA-OT (by name).
            String framework = notBlank(dc.getFramework()) ? dc.getFramework()
                    : defaultDitaOtFrameworkName(config);

            // Sync into the runtime project (only fill unset). The absolute DITA-OT
            // path is NOT stored here — getDitaOtPath() resolves it from the
            // installed framework, keeping the project portable across machines.
            boolean mutated = false;
            if (isBlank(project.getProjectType())) {
                project.setProjectType(ProjectProperties.TYPE_DITA);
                mutated = true;
            }
            if (isBlank(project.getDefaultRootMap()) && rootMap != null) {
                project.setDefaultRootMap(rootMap);
                mutated = true;
            }
            if (isBlank(project.getFrameworkName()) && framework != null) {
                project.setFrameworkName(framework);
                mutated = true;
            }

            // Materialize a portable config when the folder had none, so the
            // detected setup is committable/shareable.
            boolean wrote = false;
            if (!hasConfig && rootMap != null) {
                dc.setProjectType(ProjectProperties.TYPE_DITA);
                dc.setDefaultRootMap(rootMap);
                if (framework != null) {
                    dc.setFramework(framework);
                }
                try {
                    dc.saveShared(root);
                    wrote = true;
                } catch (IOException io) {
                    LOG.debug("Could not write .dogsbay/config.xml: {}", io.getMessage());
                }
            }

            if (mutated || wrote) {
                LOG.info("Configured DITA project '{}': rootMap={}, framework={}, fromConfig={}, wrote={}",
                        project.getName(), rootMap, framework, hasConfig, wrote);
            }
            return new Result(true, rootMap, framework, hasConfig, wrote, mutated);
        } catch (Exception e) {
            LOG.warn("Project auto-config failed for '{}': {}", project.getName(), e.getMessage(), e);
            return Result.none();
        }
    }

    // ── DITA detection ──────────────────────────────────────────────────────

    static boolean looksLikeDita(File folder) {
        if (new File(folder, "project.json").isFile()
                || new File(folder, ".dogsbay/config.xml").isFile()) {
            return true;
        }
        try (Stream<Path> walk = Files.walk(folder.toPath(), MAX_DEPTH)) {
            return walk.anyMatch(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return Files.isRegularFile(p) && (n.endsWith(".ditamap") || n.endsWith(".dita"));
            });
        } catch (Exception e) {
            return false;
        }
    }

    // ── Root-map detection ──────────────────────────────────────────────────

    static File detectRootMap(File folder) {
        File fromProject = rootMapFromProjectJson(folder);
        if (fromProject != null) {
            return fromProject;
        }
        List<File> maps = findMaps(folder);
        if (maps.isEmpty()) {
            return null;
        }
        if (maps.size() == 1) {
            return maps.get(0);
        }
        Set<String> referenced = new HashSet<>();
        for (File m : maps) {
            referenced.addAll(referencedMaps(m));
        }
        List<File> roots = new ArrayList<>();
        for (File m : maps) {
            if (!referenced.contains(canonical(m))) {
                roots.add(m);
            }
        }
        List<File> candidates = roots.isEmpty() ? maps : roots;
        // Shallowest path first (a top-level map is the most likely entry point),
        // then alphabetical for a stable choice.
        return candidates.stream()
                .min(Comparator.<File>comparingInt(f -> depth(folder, f)).thenComparing(File::getName))
                .orElse(null);
    }

    /** Read the DITA-OT {@code project.json} input map (the {@code full} deliverable, else the first). */
    private static File rootMapFromProjectJson(File folder) {
        File pj = new File(folder, "project.json");
        if (!pj.isFile()) {
            return null;
        }
        try {
            JsonNode root = new ObjectMapper().readTree(pj);
            JsonNode deliverables = root.get("deliverables");
            if (deliverables == null || !deliverables.isArray() || deliverables.isEmpty()) {
                return null;
            }
            JsonNode chosen = null;
            for (JsonNode d : deliverables) {
                if ("full".equals(text(d, "name"))) { chosen = d; break; }
            }
            if (chosen == null) {
                chosen = deliverables.get(0);
            }
            JsonNode ctx = chosen.get("context");
            String input = ctx != null ? asText(ctx.get("input")) : null;
            if (input == null || input.isBlank()) {
                return null;
            }
            File map = new File(input).isAbsolute() ? new File(input) : new File(folder, input);
            return map.isFile() ? map : null;
        } catch (Exception e) {
            LOG.debug("Could not parse project.json: {}", e.getMessage());
            return null;
        }
    }

    private static List<File> findMaps(File folder) {
        // Shared scanner (prunes build/VCS dirs, tolerant of unreadable subtrees).
        // Root-map detection is .ditamap-only and shallow (MAX_DEPTH).
        return ProjectMaps.find(folder, MAX_DEPTH, false);
    }

    /** Canonical paths of maps referenced (mapref/topicref to a .ditamap) by {@code map}. */
    private static Set<String> referencedMaps(File map) {
        Set<String> refs = new HashSet<>();
        try {
            XMLReader reader = HardenedSax.newReader();
            File parent = map.getParentFile();
            reader.setContentHandler(new DefaultHandler() {
                @Override public void startElement(String uri, String local, String qName, Attributes a) {
                    String href = a.getValue("href");
                    if (href == null || href.isBlank()) return;
                    String format = a.getValue("format");
                    boolean isMap = "ditamap".equals(format)
                            || href.toLowerCase().endsWith(".ditamap")
                            || href.toLowerCase().contains(".ditamap#");
                    if (!isMap) return;
                    String path = href.contains("#") ? href.substring(0, href.indexOf('#')) : href;
                    File target = new File(path).isAbsolute() ? new File(path) : new File(parent, path);
                    refs.add(canonical(target));
                }
            });
            reader.parse(new InputSource(map.toURI().toString()));
        } catch (Exception e) {
            LOG.debug("Could not scan map {} for sub-maps: {}", map.getName(), e.getMessage());
        }
        return refs;
    }

    // ── Framework ───────────────────────────────────────────────────────────

    /** Name of the registered default DITA-OT framework (highest version if several), or null. */
    private static String defaultDitaOtFrameworkName(ConfigurationProperties config) {
        if (config == null) {
            return null;
        }
        var list = config.getFrameworkProperties();
        if (list == null) {
            return null;
        }
        FrameworkProperties best = null;
        for (Object o : list) {
            if (o instanceof FrameworkProperties fp
                    && fp.getName() != null && fp.getName().startsWith(DefaultDitaOtFramework.NAME)
                    && !isBlank(fp.getDitaOtPath())) {
                if (best == null || compareVersions(fp.getVersion(), best.getVersion()) > 0) {
                    best = fp;
                }
            }
        }
        return best != null ? best.getName() : null;
    }

    // ── small helpers ───────────────────────────────────────────────────────

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static String canonical(File f) {
        try { return f.getCanonicalPath(); } catch (Exception e) { return f.getAbsolutePath(); }
    }

    private static String relativize(File folder, File file) {
        try {
            return folder.toPath().relativize(file.toPath()).toString().replace('\\', '/');
        } catch (Exception e) {
            return file.getAbsolutePath();
        }
    }

    private static int depth(File folder, File file) {
        try { return folder.toPath().relativize(file.toPath()).getNameCount(); }
        catch (Exception e) { return Integer.MAX_VALUE; }
    }

    private static int compareVersions(String a, String b) {
        if (a == null) return b == null ? 0 : -1;
        if (b == null) return 1;
        String[] pa = a.split("\\."), pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int va = i < pa.length ? parseIntSafe(pa[i]) : 0;
            int vb = i < pb.length ? parseIntSafe(pb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D.*$", "")); } catch (Exception e) { return 0; }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null ? v.asText(null) : null;
    }

    private static String asText(JsonNode node) {
        return node != null ? node.asText(null) : null;
    }
}
