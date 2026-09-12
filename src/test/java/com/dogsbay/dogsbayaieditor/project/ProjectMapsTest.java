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

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the shared project map scanner used by the chooser and auto-config. */
class ProjectMapsTest {

    private static Set<String> names(List<File> maps) {
        return maps.stream().map(File::getName).collect(Collectors.toSet());
    }

    @Test
    void findsRecursively_skipsDotAndBuildDirs_keepsDotFilenameMaps(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("guide.ditamap"), "<map/>");
        Files.createDirectories(root.resolve("maps"));
        Files.writeString(root.resolve("maps/book.bookmap"), "<bookmap/>");
        Files.writeString(root.resolve("topic.dita"), "<topic/>");          // not a map
        Files.writeString(root.resolve(".draft.ditamap"), "<map/>");        // dot FILENAME → kept
        Files.createDirectories(root.resolve(".git"));
        Files.writeString(root.resolve(".git/hidden.ditamap"), "<map/>");   // dot DIR → skipped
        Files.createDirectories(root.resolve("build"));
        Files.writeString(root.resolve("build/gen.ditamap"), "<map/>");     // build dir → skipped
        Files.createDirectories(root.resolve("node_modules/pkg"));
        Files.writeString(root.resolve("node_modules/pkg/dep.ditamap"), "<map/>"); // skipped

        Set<String> withBook = names(ProjectMaps.find(root.toFile(), true));
        assertEquals(Set.of("guide.ditamap", "book.bookmap", ".draft.ditamap"), withBook,
                "recurses, keeps dot-filename map, skips .dita/.git/build/node_modules");

        Set<String> noBook = names(ProjectMaps.find(root.toFile(), false));
        assertEquals(Set.of("guide.ditamap", ".draft.ditamap"), noBook, "bookmaps excluded when not requested");
    }

    @Test
    void nullOrMissingRootYieldsEmptyList() {
        assertTrue(ProjectMaps.find(null, true).isEmpty());
        assertTrue(ProjectMaps.find(new File("/no/such/dir/xyz"), true).isEmpty());
    }

    @Test
    void unreadableSubdirDoesNotAbortTheScan(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("top.ditamap"), "<map/>");
        Path locked = Files.createDirectories(root.resolve("locked"));
        Files.writeString(locked.resolve("inner.ditamap"), "<map/>");
        try {
            // Make the subdir unreadable; if the FS/posix doesn't honor it (or we're
            // root), the scan simply sees both maps — either way it must not abort.
            locked.toFile().setReadable(false, false);
            locked.toFile().setExecutable(false, false);
        } catch (Exception ignored) {
            // best-effort
        }

        assertTrue(names(ProjectMaps.find(root.toFile(), true)).contains("top.ditamap"),
                "a single unreadable subtree must not empty the whole result");

        locked.toFile().setReadable(true, false);
        locked.toFile().setExecutable(true, false);
    }
}
