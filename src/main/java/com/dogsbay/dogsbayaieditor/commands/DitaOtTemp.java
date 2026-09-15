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

package com.dogsbay.dogsbayaieditor.commands;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Where a build keeps DITA-OT's temporary files when a deliverable asks for
 * {@code clean.temp=no}: {@code <project>/.dogsbay/temp/<deliverable>}. Each build
 * replaces its deliverable's folder, so only the latest temporary files are kept.
 * The folder carries a {@code .gitignore} so the files never show up as changes.
 */
public final class DitaOtTemp {

    private DitaOtTemp() {
    }

    /** The folder holding every deliverable's kept temporary files. */
    public static Path folder(Path root) {
        return root.resolve(".dogsbay").resolve("temp");
    }

    /** The kept temporary folder for one deliverable. */
    public static Path dirFor(Path root, String deliverable) {
        return folder(root).resolve(safeName(deliverable));
    }

    /**
     * The kept temporary folder for a deliverable in a build that already gave out
     * the folder names in {@code used}: when two deliverables map to the same folder
     * (the same name, names that differ only in unsafe characters, or only in case on
     * a case-insensitive disk), later ones get {@code -2}, {@code -3} and so on.
     * The chosen name is added to {@code used}.
     */
    public static Path uniqueDir(Path root, String deliverable, java.util.Set<String> used) {
        String base = safeName(deliverable);
        String name = base;
        for (int n = 2; !used.add(name.toLowerCase(java.util.Locale.ROOT)); n++) {
            name = base + "-" + n;
        }
        return folder(root).resolve(name);
    }

    /**
     * An empty folder for a deliverable's next build: the previous build's files are
     * removed, and the {@code .gitignore} is written when missing.
     */
    public static Path prepare(Path root, String deliverable) throws IOException {
        return prepare(root, dirFor(root, deliverable));
    }

    /** As {@link #prepare(Path, String)}, for a folder chosen by {@link #uniqueDir}. */
    public static Path prepare(Path root, Path dir) throws IOException {
        if (!dir.toAbsolutePath().normalize().startsWith(folder(root).toAbsolutePath().normalize())) {
            throw new IOException("Not a temporary build folder: " + dir);
        }
        deleteTree(dir);
        Files.createDirectories(dir);
        Path ignore = folder(root).resolve(".gitignore");
        if (!Files.exists(ignore)) {
            Files.writeString(ignore, "*\n", StandardCharsets.UTF_8);
        }
        return dir;
    }

    /**
     * Remove every kept temporary folder.
     *
     * @return how many deliverable folders were removed
     */
    public static int clear(Path root) throws IOException {
        Path folder = folder(root);
        if (!Files.isDirectory(folder)) {
            return 0;
        }
        int removed = 0;
        try (Stream<Path> children = Files.list(folder)) {
            for (Path child : children.toList()) {
                if (Files.isDirectory(child)) {
                    deleteTree(child);
                    removed++;
                }
            }
        }
        return removed;
    }

    /** A deliverable name as a single safe folder name. */
    static String safeName(String name) {
        String safe = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        return safe.isEmpty() || safe.matches("\\.+") ? "deliverable" : safe;
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
