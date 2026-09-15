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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.dogsbay.dogsbayaieditor.links.KeySpace;
import com.dogsbay.dogsbayaieditor.links.Reference;
import com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex;

/**
 * Resolves a scope into the concrete list of files a batch operation runs over.
 * Three modes, reused by every batch op so scoping is consistent:
 *
 * <ul>
 *   <li><b>map</b> — the DITA <em>publication set</em>: the transitive closure
 *       reachable from a root map, following navigation <em>and</em> reuse links
 *       (topicref/href + conref + keyref/conkeyref). The DITA-correct default.</li>
 *   <li><b>glob</b> — a project-relative glob (e.g. {@code topics/**}{@code /*.dita}).</li>
 *   <li><b>root</b> — every XML-ish file under the project root.</li>
 * </ul>
 *
 * <p>The map crawl reuses the editor's {@link ReverseLinkIndex} / {@link KeySpace}
 * (the same machinery behind health/where-used) rather than re-parsing — it just
 * walks references forward from the map. "Validate all files" is then the map set
 * <em>plus</em> the orphans health reports, not a separate mode here.
 */
public final class FileSet {

    /** File extensions treated as XML-ish for {@link #underRoot}. */
    private static final Set<String> XML_EXTENSIONS =
        Set.of(".dita", ".ditamap", ".ditaval", ".xml", ".bookmap");

    private FileSet() {}

    /** Resolve a deliverable's scope: the publication set of its root map. */
    public static List<Path> forDeliverable(Path projectRoot, Deliverable deliverable) {
        return crawlMap(projectRoot, deliverable.map());
    }

    /**
     * The publication set reachable from {@code rootMap} (inclusive), following
     * href/topicref, conref, and keyref/conkeyref. Only existing files are
     * returned; broken references are a validation concern, not a scope one.
     *
     * @param projectRoot the tree to index for reference resolution
     * @param rootMap     the entry-point map
     */
    public static List<Path> crawlMap(Path projectRoot, Path rootMap) {
        ReverseLinkIndex index = ReverseLinkIndex.build(projectRoot.toFile(), null);
        KeySpace keySpace = KeySpace.fromRootMap(rootMap.toFile());

        // Forward adjacency: source file -> referenced target files.
        Map<File, List<File>> forward = new HashMap<>();
        for (Reference r : index.allReferences()) {
            File target = r.isKeyReference()
                    ? keySpace.resolveHrefFileLenient(r.keyName(), r.scope())
                    : (r.targetPath() != null ? new File(r.targetPath()) : null);
            if (target == null) {
                continue;
            }
            forward.computeIfAbsent(canonical(r.source()), k -> new ArrayList<>())
                   .add(canonical(target));
        }

        // BFS from the root map.
        Set<File> visited = new LinkedHashSet<>();
        Deque<File> queue = new ArrayDeque<>();
        File start = canonical(rootMap.toFile());
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            for (File t : forward.getOrDefault(queue.poll(), List.of())) {
                if (visited.add(t)) {
                    queue.add(t);
                }
            }
        }

        return visited.stream()
                .filter(File::isFile)
                .map(f -> f.toPath())
                .sorted()
                .toList();
    }

    /** Files matching a project-relative glob (e.g. {@code topics/**}{@code /*.dita}). */
    public static List<Path> fromGlob(Path projectRoot, String glob) throws IOException {
        PathMatcher primary = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        // Java's `**/` requires a directory boundary, so `topics/**/*.dita` does
        // NOT match `topics/intro.dita` (a file directly under topics) — nor does
        // a leading `**/` match a root-level entry. Also match a "collapsed"
        // variant that drops the `**` segment so direct children match too.
        String collapsed = glob.replace("/**/", "/");
        if (collapsed.startsWith("**/")) {
            collapsed = collapsed.substring(3);
        }
        PathMatcher alt = collapsed.equals(glob)
                ? null : FileSystems.getDefault().getPathMatcher("glob:" + collapsed);
        try (Stream<Path> walk = Files.walk(projectRoot, 20)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        Path rel = projectRoot.relativize(p);
                        return primary.matches(rel) || (alt != null && alt.matches(rel));
                    })
                    .sorted()
                    .toList();
        }
    }

    /**
     * True when {@code file} sits in a hidden folder below {@code root}, such as
     * {@code .git} or {@code .dogsbay} (which holds DITA-OT temporary files that
     * builds keep). Project-wide scans skip these, as {@code ProjectMaps} does.
     */
    public static boolean inHiddenFolder(Path root, Path file) {
        Path rel;
        try {
            rel = root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize());
        } catch (IllegalArgumentException e) {
            return false;
        }
        for (int i = 0; i < rel.getNameCount() - 1; i++) {
            if (rel.getName(i).toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    /** Every XML-ish file under the project root, outside hidden folders. */
    public static List<Path> underRoot(Path projectRoot) throws IOException {
        try (Stream<Path> walk = Files.walk(projectRoot, 20)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> !inHiddenFolder(projectRoot, p))
                    .filter(FileSet::isXmlish)
                    .sorted()
                    .toList();
        }
    }

    /**
     * Resolve a scope spec: {@code "map:<path>"}, {@code "glob:<pattern>"}, or
     * {@code "root"}. A bare value with no prefix is treated as a glob.
     */
    public static List<Path> resolve(Path projectRoot, String spec) throws IOException {
        if (spec == null || spec.isBlank() || spec.equals("root")) {
            return underRoot(projectRoot);
        }
        if (spec.startsWith("map:")) {
            String m = spec.substring(4).trim();
            Path map = Path.of(m);
            return crawlMap(projectRoot, map.isAbsolute() ? map : projectRoot.resolve(m));
        }
        if (spec.startsWith("glob:")) {
            return fromGlob(projectRoot, spec.substring(5).trim());
        }
        return fromGlob(projectRoot, spec);
    }

    private static boolean isXmlish(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        return dot >= 0 && XML_EXTENSIONS.contains(n.substring(dot));
    }

    private static File canonical(File f) {
        try {
            return f.getCanonicalFile();
        } catch (IOException e) {
            return f.getAbsoluteFile();
        }
    }
}
