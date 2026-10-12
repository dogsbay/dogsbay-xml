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

package com.dogsbay.dogsbayaieditor.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Two things a user needs before the command line is usable: an honest version
 * and a way to run it without a checkout.
 *
 * <p>{@code --version} reported "dogsbay 0.1.0" for the whole of 4.x, which is
 * worse than no version at all when someone is chasing a version-specific
 * problem; and the only documented way to run the CLI was {@code bin/dogsbay-xml}
 * out of a Gradle build tree.
 */
class CliVersionAndLauncherTest {

    @Test
    void versionComesFromTheManifestNotALiteral() throws Exception {
        String[] version = new DogsBayCli.Version().getVersion();

        assertTrue(version.length == 1, "one version line");
        assertTrue(version[0].startsWith("dogsbay-xml "), "unexpected version line: " + version[0]);
        assertFalse(version[0].contains("0.1.0"),
                "the hardcoded 0.1.0 is back: " + version[0]);

        // From a jar this is the real Implementation-Version. From classes on
        // disk there is no manifest and no version to report — and saying so is
        // the point: a literal here would go stale at the next release and
        // report the old number, which is the bug this replaced.
        String fromJar = DogsBayCli.class.getPackage().getImplementationVersion();
        if (fromJar != null) {
            assertTrue(version[0].endsWith(fromJar),
                    "expected version " + fromJar + ", got " + version[0]);
        } else {
            assertTrue(version[0].contains("development build"),
                    "a build with no manifest must not claim a version: " + version[0]);
        }
    }

    @Test
    void theInstallerShipsACommandLineExecutable() throws Exception {
        String build = Files.readString(Path.of("build.gradle.kts"));

        assertTrue(build.contains("\"--add-launcher\", \"dogsbay-xml="),
                "jpackage must add a dogsbay-xml launcher beside the GUI one");
        assertTrue(build.contains("main-class=com.dogsbay.dogsbayaieditor.cli.DogsBayCli"),
                "the launcher must point at the CLI entry point");
        assertTrue(build.contains("dependsOn(writeCliLauncherProperties)"),
                "the launcher description must be written before jpackage reads it");
    }

    /**
     * The Linux packaging step writes a desktop entry per launcher and ignores
     * the launcher's own linux-shortcut=false, so the CLI would appear in the
     * applications menu as an icon that launches a console program with no
     * console. The overriding template is what keeps it hidden.
     */
    @Test
    void theCommandLineDoesNotGetAnApplicationsMenuEntry() throws Exception {
        String build = Files.readString(Path.of("build.gradle.kts"));

        assertTrue(build.contains("NoDisplay=true"),
                "the CLI desktop entry must be hidden from the applications menu");
        assertTrue(build.contains("\"--resource-dir\""),
                "the hidden desktop entry reaches jpackage through a resource directory");
        assertTrue(build.contains("dependsOn(writeDesktopEntries)"),
                "the desktop entries must be written before jpackage reads them");
    }
}
