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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** C1: read/write of the project-local .dogsbay config (shared + personal). */
class DogsbayProjectConfigTest {

    @TempDir Path root;

    @Test
    void roundTripsSharedAndPersonalSettings() throws Exception {
        DogsbayProjectConfig c = new DogsbayProjectConfig();
        c.setProjectType("DITA");
        c.setDefaultRootMap("audacity-guide.ditamap");
        c.setFramework("DITA-OT");
        c.setDefaultDeliverable("project.json", "full");
        c.setDitaOtPath("/opt/dita-ot");
        c.setActiveDeliverable("project.json", "beginner-mac");
        c.saveShared(root);
        c.saveLocal(root);

        DogsbayProjectConfig r = DogsbayProjectConfig.load(root);
        assertThat(r.getProjectType()).isEqualTo("DITA");
        assertThat(r.getDefaultRootMap()).isEqualTo("audacity-guide.ditamap");
        assertThat(r.getFramework()).isEqualTo("DITA-OT");
        assertThat(r.getDefaultDeliverableFile()).isEqualTo("project.json");
        assertThat(r.getDefaultDeliverableName()).isEqualTo("full");
        // personal layer
        assertThat(r.getDitaOtPath()).isEqualTo("/opt/dita-ot");
        assertThat(r.getActiveDeliverableFile()).isEqualTo("project.json");
        assertThat(r.getActiveDeliverableName()).isEqualTo("beginner-mac");
    }

    @Test
    void committedConfigHasNoMachineSpecificValues() throws Exception {
        DogsbayProjectConfig c = new DogsbayProjectConfig();
        c.setFramework("DITA-OT");
        c.setDitaOtPath("/opt/dita-ot"); // machine-specific
        c.saveShared(root);

        // the shared file names the framework but never the absolute DITA-OT path
        String shared = Files.readString(
                DogsbayProjectConfig.dir(root).resolve(DogsbayProjectConfig.CONFIG_FILE));
        assertThat(shared).contains("DITA-OT").doesNotContain("/opt/dita-ot");
    }

    @Test
    void writesGitignoreForLocalFile() throws Exception {
        new DogsbayProjectConfig().saveShared(root);
        Path gi = DogsbayProjectConfig.dir(root).resolve(DogsbayProjectConfig.GITIGNORE_FILE);
        assertThat(Files.readString(gi)).contains("local.xml");
    }

    @Test
    void gitignoreNotDuplicatedOnRepeatedSaves() throws Exception {
        DogsbayProjectConfig c = new DogsbayProjectConfig();
        c.saveShared(root);
        c.saveLocal(root);
        c.saveShared(root);
        Path gi = DogsbayProjectConfig.dir(root).resolve(DogsbayProjectConfig.GITIGNORE_FILE);
        long count = Files.readAllLines(gi).stream()
                .filter(l -> l.trim().equals("local.xml")).count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void missingConfigLoadsEmptyWithoutError() {
        assertThat(DogsbayProjectConfig.exists(root)).isFalse();
        DogsbayProjectConfig r = DogsbayProjectConfig.load(root);
        assertThat(r.getProjectType()).isNull();
        assertThat(r.getDitaOtPath()).isNull();
    }

    @Test
    void malformedConfigDegradesToEmpty() throws Exception {
        Files.createDirectories(DogsbayProjectConfig.dir(root));
        Files.writeString(DogsbayProjectConfig.dir(root).resolve(DogsbayProjectConfig.CONFIG_FILE),
                "<dogsbay-project><not-closed>");
        DogsbayProjectConfig r = DogsbayProjectConfig.load(root);
        assertThat(r.getProjectType()).isNull(); // no throw
    }
}
