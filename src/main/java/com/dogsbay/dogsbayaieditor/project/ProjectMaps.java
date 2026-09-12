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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Shared discovery of DITA map files in a project tree. One scanner so every
 * surface (auto-config root-map detection, the Map Explorer chooser) sees the
 * same set of maps.
 *
 * <p>Uses a pruning {@link java.nio.file.FileVisitor} so build/VCS directories
 * are never descended (fast on large repos), an unreadable subdirectory is
 * skipped rather than aborting the whole scan, and only <em>directory</em> names
 * are filtered — a map file whose own name starts with a dot is still found.
 */
public final class ProjectMaps {

    /** Build/VCS/output directories never worth walking for source maps. */
    private static final Set<String> SKIP_DIRS = Set.of(
            "out", "build", "target", "temp", "tmp", "node_modules", ".git", ".svn", ".hg");

    /** Generous default depth for a "pick any map" chooser. */
    private static final int DEFAULT_MAX_DEPTH = 16;

    private ProjectMaps() {
    }

    /** All {@code .ditamap} (and {@code .bookmap} when requested) under {@code root}. */
    public static List<File> find(File root, boolean includeBookmaps) {
        return find(root, DEFAULT_MAX_DEPTH, includeBookmaps);
    }

    /**
     * All {@code .ditamap} (and optionally {@code .bookmap}) under {@code root},
     * sorted by absolute path. Skips dot-directories and {@link #SKIP_DIRS};
     * tolerant of unreadable subtrees. Returns an empty list for a null/missing root.
     */
    public static List<File> find(File root, int maxDepth, boolean includeBookmaps) {
        if (root == null || !root.isDirectory()) {
            return List.of();
        }
        Path base = root.toPath();
        List<File> maps = new ArrayList<>();
        try {
            Files.walkFileTree(base, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class),
                    Math.max(1, maxDepth), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            if (!dir.equals(base)) {
                                String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                                if (name.startsWith(".") || SKIP_DIRS.contains(name)) {
                                    return FileVisitResult.SKIP_SUBTREE;
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            String n = file.getFileName().toString().toLowerCase();
                            if (n.endsWith(".ditamap") || (includeBookmaps && n.endsWith(".bookmap"))) {
                                maps.add(file.toFile());
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            // Unreadable entry — skip it, keep scanning the rest.
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            // Return whatever was collected before the failure.
        }
        maps.sort(Comparator.comparing(File::getAbsolutePath));
        return maps;
    }
}
