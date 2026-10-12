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

package com.dogsbay.dogsbayaieditor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** "Open Sample Project" always creates the sample in a fresh, unique SUBfolder of the
 *  chosen location — never the chosen folder itself, so picking the home folder can't
 *  make home the project root. */
class OpenSampleProjectActionTest {

    @Test
    void firstChildUsesThePlainName(@TempDir Path dir) {
        File child = OpenSampleProjectAction.uniqueChild(dir.toFile(), "audacity-demo");
        assertEquals(new File(dir.toFile(), "audacity-demo"), child);
        assertFalse(child.exists(), "not pre-created — install creates it");
        // it is a SUBfolder of the chosen location, never the location itself
        assertEquals(dir.toFile(), child.getParentFile());
    }

    @Test
    void existingNameGetsASuffix(@TempDir Path dir) throws Exception {
        Files.createDirectory(dir.resolve("audacity-demo"));
        File child = OpenSampleProjectAction.uniqueChild(dir.toFile(), "audacity-demo");
        assertEquals(new File(dir.toFile(), "audacity-demo-2"), child);
        assertFalse(child.exists());
    }

    @Test
    void suffixIncrementsPastExistingCopies(@TempDir Path dir) throws Exception {
        Files.createDirectory(dir.resolve("audacity-demo"));
        Files.createDirectory(dir.resolve("audacity-demo-2"));
        File child = OpenSampleProjectAction.uniqueChild(dir.toFile(), "audacity-demo");
        assertEquals(new File(dir.toFile(), "audacity-demo-3"), child);
    }

    // ── resolveSampleDest: turn the chooser selection into a fresh dest folder ──────────

    @Test
    void acceptedDefaultNameCreatesSubfolderOfBase(@TempDir Path dir) {
        // selection = base/audacity-demo (pre-filled, not yet existing)
        File selected = new File(dir.toFile(), "audacity-demo");
        File dest = OpenSampleProjectAction.resolveSampleDest(selected, dir.toFile(), "audacity-demo");
        assertEquals(new File(dir.toFile(), "audacity-demo"), dest);
    }

    @Test
    void secondRunCreatesSiblingNotNested(@TempDir Path dir) throws Exception {
        // base/audacity-demo already exists → selection resolves to it; must NOT nest
        Files.createDirectory(dir.resolve("audacity-demo"));
        File selected = new File(dir.toFile(), "audacity-demo");
        File dest = OpenSampleProjectAction.resolveSampleDest(selected, dir.toFile(), "audacity-demo");
        assertEquals(new File(dir.toFile(), "audacity-demo-2"), dest, "sibling, never nested inside");
    }

    @Test
    void clearedNameUsesShownFolderAsParentNotRoot(@TempDir Path dir) {
        // user cleared the name → chooser returns the shown directory itself
        File dest = OpenSampleProjectAction.resolveSampleDest(dir.toFile(), dir.toFile(), "audacity-demo");
        assertEquals(new File(dir.toFile(), "audacity-demo"), dest);   // a subfolder, never `dir`
    }

    @Test
    void nullSelectionFallsBackToShownFolder(@TempDir Path dir) {
        File dest = OpenSampleProjectAction.resolveSampleDest(null, dir.toFile(), "audacity-demo");
        assertEquals(new File(dir.toFile(), "audacity-demo"), dest);
    }

    @Test
    void typedNameIsHonored(@TempDir Path dir) {
        File selected = new File(dir.toFile(), "my-sample");
        File dest = OpenSampleProjectAction.resolveSampleDest(selected, dir.toFile(), "audacity-demo");
        assertEquals(new File(dir.toFile(), "my-sample"), dest);
    }

    @Test
    void existingFileWithTheNameBumpsToSibling(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("audacity-demo"), "x");   // a FILE, not a folder
        File selected = new File(dir.toFile(), "audacity-demo");
        File dest = OpenSampleProjectAction.resolveSampleDest(selected, dir.toFile(), "audacity-demo");
        assertEquals(new File(dir.toFile(), "audacity-demo-2"), dest);
    }
}
