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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig;

/** A build outside the editor finds the same DITA-OT the editor would publish with. */
class DitaOtHomeTest {

    @TempDir
    Path tmp;

    /** The layout the editor bundles: trimmed for in-process builds, with no bin folder. */
    private Path install(Path home) throws Exception {
        Files.createDirectories(home.resolve("plugins"));
        Files.writeString(home.resolve("build.xml"), "<project/>");
        return home;
    }

    /** A DogsBay home with a frameworks folder and, optionally, registered frameworks in settings.xml. */
    private Path dogsbayHome(String... nameAndPath) throws Exception {
        Path home = Files.createDirectories(tmp.resolve("dogsbay"));
        Files.createDirectories(home.resolve("frameworks"));
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\"?>\n<dogsbay>\n");
        for (int i = 0; i + 1 < nameAndPath.length; i += 2) {
            xml.append("  <framework-properties>\n    <name>").append(nameAndPath[i]).append("</name>\n")
               .append("    <dita-ot-path>").append(nameAndPath[i + 1]).append("</dita-ot-path>\n")
               .append("  </framework-properties>\n");
        }
        Files.writeString(home.resolve("settings.xml"), xml.append("</dogsbay>\n"));
        return home;
    }

    private Path project(String framework, String personalPath) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("project"));
        DogsbayProjectConfig config = new DogsbayProjectConfig();
        config.setFramework(framework);
        config.saveShared(root);
        if (personalPath != null) {
            config.setDitaOtPath(personalPath);
            config.saveLocal(root);
        }
        return root;
    }

    @Test
    void anExplicitPathWins() throws Exception {
        Path home = dogsbayHome();
        install(home.resolve("frameworks").resolve(DitaOtHome.BUNDLED_FRAMEWORK).resolve("dita-ot"));
        Path explicit = tmp.resolve("elsewhere");

        assertThat(DitaOtHome.resolve(project(null, null), explicit, home)).isEqualTo(explicit);
    }

    @Test
    void thePersonalPathComesBeforeInstalledFrameworks() throws Exception {
        Path home = dogsbayHome();
        install(home.resolve("frameworks").resolve(DitaOtHome.BUNDLED_FRAMEWORK).resolve("dita-ot"));
        Path personal = install(tmp.resolve("my-dita-ot"));

        assertThat(DitaOtHome.resolve(project(null, personal.toString()), null, home)).isEqualTo(personal);
    }

    @Test
    void theNamedFrameworkIsFoundWhereTheEditorRegisteredIt() throws Exception {
        // Imported from a download folder, not under frameworks/: only settings.xml knows where it is.
        Path imported = install(tmp.resolve("downloads/dita-ot-4.2.4"));
        Path home = dogsbayHome("DITA-OT 4.2.4", imported.toString());
        install(home.resolve("frameworks").resolve(DitaOtHome.BUNDLED_FRAMEWORK).resolve("dita-ot"));

        assertThat(DitaOtHome.resolve(project("DITA-OT 4.2.4", null), null, home)).isEqualTo(imported);
    }

    @Test
    void theNamedFrameworkIsFoundInTheFrameworksFolder() throws Exception {
        Path home = dogsbayHome();
        install(home.resolve("frameworks").resolve(DitaOtHome.BUNDLED_FRAMEWORK).resolve("dita-ot"));
        Path named = install(home.resolve("frameworks").resolve("DITA-OT 4.2.4").resolve("dita-ot"));

        assertThat(DitaOtHome.resolve(project("DITA-OT 4.2.4", null), null, home)).isEqualTo(named);
    }

    @Test
    void theBundledFrameworkIsTheFallback() throws Exception {
        Path home = dogsbayHome();
        Path bundled = install(home.resolve("frameworks").resolve(DitaOtHome.BUNDLED_FRAMEWORK).resolve("dita-ot"));

        // The named framework is not installed, and the personal path does not exist.
        assertThat(DitaOtHome.resolve(project("DITA-OT 9.9", tmp.resolve("gone").toString()), null, home))
                .isEqualTo(bundled);
    }

    @Test
    void anyRegisteredInstallIsTheLastResort() throws Exception {
        Path imported = install(tmp.resolve("downloads/dita-ot-4.2.4"));
        Path home = dogsbayHome("Stale", tmp.resolve("gone").toString(), "DITA-OT 4.2.4", imported.toString());

        assertThat(DitaOtHome.resolve(project(null, null), null, home)).isEqualTo(imported);
    }

    @Test
    void nothingInstalledIsNull() throws Exception {
        assertThat(DitaOtHome.resolve(project(null, null), null, dogsbayHome())).isNull();
    }

    @Test
    void aFullDownloadWithItsLauncherCountsAndAnEmptyFolderDoesNot() throws Exception {
        Path full = tmp.resolve("dita-ot-4.3.5");
        Files.createDirectories(full.resolve("plugins"));
        Files.createDirectories(full.resolve("bin"));
        Files.writeString(full.resolve("bin/dita"), "#!/bin/sh\n");

        assertThat(DitaOtHome.isHome(full)).isTrue();
        assertThat(DitaOtHome.isHome(Files.createDirectories(tmp.resolve("empty")))).isFalse();
    }

    @Test
    void anUnusablePathIsSkippedRatherThanThrown() {
        // A NUL character is not valid in a path on any platform.
        assertThat(DitaOtHome.safePath("bad\0path")).isNull();
        assertThat(DitaOtHome.safePath("  ")).isNull();
        assertThat(DitaOtHome.registeredFrameworks(tmp.resolve("missing-settings.xml"))).isEmpty();
    }

    @Test
    void theBundledNameMatchesTheFrameworkTheEditorInstalls() {
        assertThat(DitaOtHome.BUNDLED_FRAMEWORK)
                .isEqualTo(com.dogsbay.dogsbayaieditor.framework.DefaultDitaOtFramework.FRAMEWORK_NAME);
    }
}
