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

package com.dogsbay.dogsbayaieditor.samples;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The bundled sample is present and extracts into a usable DITA project. */
class SampleProjectTest {

    @TempDir Path tmp;

    @Test
    void sampleIsBundled() {
        assertTrue(SampleProject.isAvailable(), "audacity-demo.zip resource must ship in the build");
        assertNotEquals("unknown", SampleProject.bundledVersion());
    }

    @Test
    void installExtractsAUsableDitaProject() throws Exception {
        File dest = tmp.resolve("audacity-demo").toFile();
        SampleProject.install(dest);

        // The committed .dogsbay/config.xml is what makes auto-setup load the map.
        assertTrue(new File(dest, ".dogsbay/config.xml").isFile(), "sample must carry .dogsbay/config.xml");
        assertTrue(new File(dest, "audacity-guide.ditamap").isFile(), "sample must carry the root map");
        assertTrue(new File(dest, "project.json").isFile());
        // Version stamp written for a later update-if-behind check.
        assertTrue(new File(dest, ".dogsbay-sample-version").isFile());
    }

    @Test
    void installIsIdempotent_overwritesCleanly() throws Exception {
        File dest = tmp.resolve("audacity-demo").toFile();
        SampleProject.install(dest);
        // Second install over the same dir must not fail (REPLACE_EXISTING).
        assertDoesNotThrow(() -> SampleProject.install(dest));
        assertTrue(new File(dest, "audacity-guide.ditamap").isFile());
    }

    @Test
    void cleanInstall_removesStaleFiles_pristineCopy() throws Exception {
        File dest = tmp.resolve("audacity-demo").toFile();
        SampleProject.install(dest);
        File stale = new File(dest, "stale-renamed-topic.dita");
        java.nio.file.Files.writeString(stale.toPath(), "<concept/>");
        assertTrue(stale.exists());

        SampleProject.install(dest, true); // "overwrite with a fresh copy"

        assertFalse(stale.exists(), "clean install must remove stale files (pristine copy)");
        assertTrue(new File(dest, "audacity-guide.ditamap").isFile());
        assertTrue(new File(dest, ".dogsbay/config.xml").isFile());
    }

    @Test
    void recognizesAnInstalledSampleFolderByContent() throws Exception {
        File dest = tmp.resolve("renamed-by-the-user").toFile(); // folder name is irrelevant
        SampleProject.install(dest);
        assertTrue(SampleProject.isSampleFolder(dest), "an installed sample is recognized by its content");

        assertFalse(SampleProject.isSampleFolder(tmp.resolve("does-not-exist").toFile()));
        assertFalse(SampleProject.isSampleFolder(null));

        // A folder with only one of the signature maps is not the sample.
        File partial = tmp.resolve("partial").toFile();
        partial.mkdirs();
        java.nio.file.Files.writeString(new File(partial, "audacity-guide.ditamap").toPath(), "<map/>");
        assertFalse(SampleProject.isSampleFolder(partial), "all signature maps must be present");
    }

    @Test
    void additiveInstall_leavesExistingFiles() throws Exception {
        File dest = tmp.resolve("audacity-demo").toFile();
        SampleProject.install(dest);
        File extra = new File(dest, "user-notes.txt");
        java.nio.file.Files.writeString(extra.toPath(), "mine");

        SampleProject.install(dest, false); // additive (default)

        assertTrue(extra.exists(), "additive install must not delete existing files");
    }
}
