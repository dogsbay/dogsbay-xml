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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reverse link index over a project tree: which files reference a given
 * file (or key)?
 *
 * <p>Built by walking the scope root and running {@link LinkExtractor} on
 * every XML-ish file. Per-file invalidation via {@link #updateFile} keeps
 * the index current as documents change. All public methods are
 * synchronized — built on a worker thread, queried from the EDT.
 */
public final class ReverseLinkIndex {

    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", ".svn", ".hg", "build", "out", "target", "node_modules");
    private static final Set<String> EXTENSIONS = Set.of(
            "dita", "ditamap", "ditaval", "xml");

    private final File root;
    /** canonical target path -> references pointing at it */
    private final Map<String, List<Reference>> byTarget = new HashMap<>();
    /** key name -> key references */
    private final Map<String, List<Reference>> byKey = new HashMap<>();
    /** canonical source path -> that file's outbound references (for invalidation) */
    private final Map<String, List<Reference>> bySource = new HashMap<>();

    private int filesIndexed;

    private ReverseLinkIndex(File root) {
        this.root = root;
    }

    /**
     * Builds the index for a scope root. {@code progress} (optional) is
     * called with each file before it is indexed.
     */
    public static ReverseLinkIndex build(File root, Consumer<File> progress) {
        ReverseLinkIndex index = new ReverseLinkIndex(root);
        index.walk(root, progress);
        return index;
    }

    private void walk(File dir, Consumer<File> progress) {
        File[] children = dir == null ? null : dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            String name = child.getName();
            if (child.isDirectory()) {
                if (!name.startsWith(".") && !SKIP_DIRS.contains(name)) {
                    walk(child, progress);
                }
            } else if (isIndexable(name)) {
                if (progress != null) {
                    progress.accept(child);
                }
                indexFile(child);
            }
        }
    }

    static boolean isIndexable(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase());
    }

    private synchronized void indexFile(File file) {
        List<Reference> refs = LinkExtractor.extract(file).references();
        String sourceCanon = LinkExtractor.canonical(file);
        bySource.put(sourceCanon, refs);
        for (Reference ref : refs) {
            if (ref.isKeyReference()) {
                byKey.computeIfAbsent(ref.keyName(), k -> new ArrayList<>()).add(ref);
            } else if (ref.targetPath() != null) {
                byTarget.computeIfAbsent(ref.targetPath(), k -> new ArrayList<>()).add(ref);
            }
        }
        filesIndexed++;
    }

    /** Re-extracts one file (created or modified). */
    public synchronized void updateFile(File file) {
        removeFile(file);
        if (file.isFile() && isIndexable(file.getName())) {
            indexFile(file);
        }
    }

    /** Drops one file's outbound references (deleted or about to re-index). */
    public synchronized void removeFile(File file) {
        String sourceCanon = LinkExtractor.canonical(file);
        List<Reference> old = bySource.remove(sourceCanon);
        if (old == null) {
            return;
        }
        for (Reference ref : old) {
            List<Reference> list = ref.isKeyReference()
                    ? byKey.get(ref.keyName())
                    : (ref.targetPath() != null ? byTarget.get(ref.targetPath()) : null);
            if (list != null) {
                list.remove(ref);
            }
        }
        filesIndexed = Math.max(0, filesIndexed - 1);
    }

    /** Every reference (from any indexed file) pointing at {@code target}. */
    public synchronized List<Reference> usagesOf(File target) {
        List<Reference> list = byTarget.get(LinkExtractor.canonical(target));
        return list == null ? List.of() : List.copyOf(list);
    }

    /** Every keyref/conkeyref using key {@code keyName} (matched by the authored
     *  literal name). For scope-correct "is this key used?" use
     *  {@link #usedKeyDefinitions(KeySpace)} instead. */
    public synchronized List<Reference> usagesOfKey(String keyName) {
        List<Reference> list = byKey.get(keyName);
        return list == null ? List.of() : List.copyOf(list);
    }

    /**
     * The set of key definitions that at least one keyref/conkeyref resolves to,
     * resolving each usage in its own authoring scope (DITA 1.3 key scopes). Use to
     * decide which keys are unused: a key defined inside a {@code @keyscope} and used
     * via a bare keyref would be missed by {@link #usagesOfKey} (keyed by the literal
     * name) and wrongly reported unused.
     */
    public synchronized Set<KeyDefinition> usedKeyDefinitions(KeySpace keySpace) {
        Set<KeyDefinition> used = new java.util.HashSet<>();
        if (keySpace == null || keySpace.isEmpty()) {
            return used;
        }
        for (List<Reference> refs : byKey.values()) {
            for (Reference ref : refs) {
                KeyDefinition def = keySpace.resolveLenient(ref.keyName(), ref.scope());
                if (def != null) {
                    used.add(def);
                }
            }
        }
        return used;
    }

    /**
     * Usages of {@code target} including indirect ones via keys that a key
     * space resolves to it.
     */
    public synchronized List<Reference> usagesOfIncludingKeys(File target, KeySpace keySpace) {
        List<Reference> result = new ArrayList<>(usagesOf(target));
        if (keySpace != null && !keySpace.isEmpty()) {
            String targetCanon = LinkExtractor.canonical(target);
            // Resolve each keyref usage in its own authoring scope (DITA 1.3 key
            // scopes): a bare keyref under @keyscope binds to the scoped definition,
            // which iterating keyspace entries by qualified name would miss.
            for (List<Reference> refs : byKey.values()) {
                for (Reference ref : refs) {
                    File resolved = keySpace.resolveHrefFileLenient(ref.keyName(), ref.scope());
                    if (resolved != null
                            && LinkExtractor.canonical(resolved).equals(targetCanon)) {
                        result.add(ref);
                    }
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Every file the index has scanned (orphan detection etc.). */
    public synchronized List<File> indexedFiles() {
        List<File> files = new ArrayList<>();
        for (String path : bySource.keySet()) {
            files.add(new File(path));
        }
        return files;
    }

    /** Every indexed outbound reference from every file (check-links etc.). */
    public synchronized List<Reference> allReferences() {
        List<Reference> all = new ArrayList<>();
        for (List<Reference> refs : bySource.values()) {
            all.addAll(refs);
        }
        return all;
    }

    public File getRoot() {
        return root;
    }

    public synchronized int getFilesIndexed() {
        return filesIndexed;
    }
}
