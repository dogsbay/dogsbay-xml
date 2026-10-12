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
    void savingTheSetupKeepsPolicyAndHouseStyle() throws Exception {
        Files.createDirectories(root.resolve(".dogsbay"));
        Files.writeString(root.resolve(".dogsbay/config.xml"), """
                <dogsbay-project>
                  <project-type>DITA</project-type>
                  <default-root-map>old.ditamap</default-root-map>
                  <default-deliverable file="project.json" name="full"/>
                  <metadata-policy><rule field="keyword" presence="required"/></metadata-policy>
                  <format-style indent="tabs" size="4"/>
                  <format-on-save>true</format-on-save>
                </dogsbay-project>
                """);

        DogsbayProjectConfig.saveSetup(root, "DITA", "DITA-OT 4.3.5", "guide.ditamap", null, null);

        DogsbayProjectConfig r = DogsbayProjectConfig.load(root);
        assertThat(r.getDefaultRootMap()).isEqualTo("guide.ditamap");
        assertThat(r.getFramework()).isEqualTo("DITA-OT 4.3.5");
        assertThat(r.getDefaultDeliverableName()).isEqualTo("full");
        assertThat(r.getMetadataPolicy().rules()).hasSize(1);
        assertThat(r.getFormatStyle().indentSize()).isEqualTo(4);
        assertThat(r.isFormatOnSave()).isTrue();
    }

    @Test
    void writingOneSettingInPlaceLeavesTheRestOfTheFileAlone() throws Exception {
        // the shared config lives under the project root
        Files.createDirectories(root.resolve(".dogsbay"));
        Files.writeString(root.resolve(".dogsbay/config.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <dogsbay-project>
                  <!-- chosen by the team, do not change lightly -->
                  <project-type>dita</project-type>
                  <default-schematron>house-style.sch</default-schematron>
                  <something-this-version-has-never-heard-of>keep me</something-this-version-has-never-heard-of>
                </dogsbay-project>
                """);

        DogsbayProjectConfig.setSharedElement(root, "default-root-map", "audacity-guide.ditamap");

        assertThat(DogsbayProjectConfig.load(root).getDefaultRootMap()).isEqualTo("audacity-guide.ditamap");
        String written = Files.readString(root.resolve(".dogsbay/config.xml"));
        // this file is shared and in the repository: a comment or a setting a later version added has to survive an
        // older one saving a single field
        assertThat(written).contains("do not change lightly");
        assertThat(written).contains("<something-this-version-has-never-heard-of>keep me<");
        assertThat(written).contains("house-style.sch");
    }

    @Test
    void writingTheSameSettingTwiceDoesNotDuplicateIt() throws Exception {
        // the shared config lives under the project root
        DogsbayProjectConfig.setSharedElement(root, "default-root-map", "one.ditamap");
        DogsbayProjectConfig.setSharedElement(root, "default-root-map", "two.ditamap");

        assertThat(DogsbayProjectConfig.load(root).getDefaultRootMap()).isEqualTo("two.ditamap");
        assertThat(Files.readString(root.resolve(".dogsbay/config.xml")).split("default-root-map", -1).length - 1)
                .as("one element, opened and closed").isEqualTo(2);
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

    // ── one file, one layout ────────────────────────────────────────────────

    /** The shared file as it is on disk. */
    private String sharedFile() throws Exception {
        return Files.readString(root.resolve(".dogsbay/config.xml"));
    }

    @Test
    void bothWritersProduceTheSameFile() throws Exception {
        // Four places write this file, through two mechanisms: a full rewrite (Save Project Settings, Edit
        // Policy, the Format page, auto-configuration) and an edit in place (Manage Projects). The same content
        // has to come out the same way, or a reader's committed file changes shape depending on which dialog
        // they last used.
        DogsbayProjectConfig whole = new DogsbayProjectConfig();
        whole.setProjectType("DITA");
        whole.setDefaultRootMap("guide.ditamap");
        whole.saveShared(root);
        String rewritten = sharedFile();

        Files.delete(root.resolve(".dogsbay/config.xml"));
        DogsbayProjectConfig.setSharedElement(root, "project-type", "DITA");
        DogsbayProjectConfig.setSharedElement(root, "default-root-map", "guide.ditamap");

        assertThat(sharedFile()).isEqualTo(rewritten);
    }

    @Test
    void anEditInPlaceKeepsTheFileFormatted() throws Exception {
        Files.createDirectories(root.resolve(".dogsbay"));
        Files.writeString(root.resolve(".dogsbay/config.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n<dogsbay-project>\n"
                + "  <project-type>DITA</project-type>\n</dogsbay-project>\n");

        DogsbayProjectConfig.setSharedElement(root, "default-root-map", "guide.ditamap");

        String after = sharedFile();
        // The three things an in-place write used to lose: the blank line after the declaration, the new
        // element's own line, and the final newline (reported 2026-09-29).
        assertThat(after).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n<dogsbay-project>");
        assertThat(after).contains("\n  <default-root-map>guide.ditamap</default-root-map>\n");
        assertThat(after).endsWith("</dogsbay-project>\n");
        assertThat(after).doesNotContain(" \n"); // and no trailing spaces
    }

    @Test
    void anEditInPlaceKeepsWhatItDoesNotUnderstand() throws Exception {
        // Why editing in place exists at all: a full rewrite goes through the model, and anything the model has
        // no field for — a comment, an element a later version writes — would not come back.
        Files.createDirectories(root.resolve(".dogsbay"));
        Files.writeString(root.resolve(".dogsbay/config.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n<dogsbay-project>\n"
                + "  <!-- the guide everyone builds -->\n"
                + "  <project-type>DITA</project-type>\n"
                + "  <from-a-later-version>keep me</from-a-later-version>\n</dogsbay-project>\n");

        DogsbayProjectConfig.setSharedElement(root, "default-root-map", "guide.ditamap");

        assertThat(sharedFile())
                .contains("<!-- the guide everyone builds -->")
                .contains("<from-a-later-version>keep me</from-a-later-version>");
    }

    @Test
    void anElementLandsWhereTheOrderSaysRatherThanAtTheEnd() throws Exception {
        Files.createDirectories(root.resolve(".dogsbay"));
        Files.writeString(root.resolve(".dogsbay/config.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n<dogsbay-project>\n"
                + "  <project-type>DITA</project-type>\n"
                + "  <format-style indent=\"spaces\" size=\"2\"/>\n</dogsbay-project>\n");

        DogsbayProjectConfig.setSharedElement(root, "default-root-map", "guide.ditamap");
        DogsbayProjectConfig.setSharedElement(root, "framework", "DITA-OT 4.3.5");

        String after = sharedFile();
        assertThat(after.indexOf("default-root-map")).isGreaterThan(after.indexOf("project-type"));
        assertThat(after.indexOf("framework")).isGreaterThan(after.indexOf("default-root-map"));
        assertThat(after.indexOf("format-style")).isGreaterThan(after.indexOf("framework"));
    }

    @Test
    void anEditInPlaceLeavesAnUndeclaredNewlineUndeclared() throws Exception {
        // Rewriting the file must not give a project an opinion about line endings it never had: the formatter
        // reads that as permission to convert every CRLF file in it.
        Files.createDirectories(root.resolve(".dogsbay"));
        Files.writeString(root.resolve(".dogsbay/config.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n<dogsbay-project>\n"
                + "  <format-style indent=\"spaces\" size=\"2\"/>\n</dogsbay-project>\n");

        DogsbayProjectConfig.setSharedElement(root, "default-root-map", "guide.ditamap");

        assertThat(sharedFile()).doesNotContain("newline=");
        assertThat(DogsbayProjectConfig.load(root).isNewlineDeclared()).isFalse();
    }
}
