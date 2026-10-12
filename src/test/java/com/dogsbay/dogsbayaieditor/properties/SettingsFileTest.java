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

package com.dogsbay.dogsbayaieditor.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.xml.XElement;

/**
 * The settings file is named for what it is, and carries its format's version
 * inside it. The version used to be part of the name — {@code .dogsbay-v33.xml}
 * — so a release that bumped it found no file at all.
 *
 * <p>4.0 reads no 3.x file: there is no installed base to carry, and the
 * fallback chain that tried was five names deep and covered only one of the
 * four files.
 */
class SettingsFileTest {

    @Test
    void afreshHomeUsesTheNameWithoutAVersion(@TempDir Path home) {
        Path file = SettingsFile.locate(home, SettingsFile.MAIN);

        assertThat(file.getFileName()).hasToString("settings.xml");
        assertThat(file).doesNotExist();   // a first run has nothing to read yet
    }






    // ── the version, and migrating on it ────────────────────────────────

    @Test
    void aFileWithNoVersionIsBackedUpBeforeItIsTouched(@TempDir Path home) throws Exception {
        // Everything written before this change looks like this, so it is the
        // one shape that exists on every machine — and the one that runs every
        // migration step there is. Excluding it from the backup left exactly
        // those files unprotected.
        Path file = Files.writeString(home.resolve("settings.xml"), "<dogsbay/>");
        XElement root = new XElement("dogsbay");

        assertThat(SettingsFile.adopt(file, root)).isTrue();

        assertThat(root.getAttribute(SettingsFile.VERSION_ATTRIBUTE))
            .isEqualTo(String.valueOf(SettingsFile.CURRENT_VERSION));
        assertThat(home.resolve("settings.xml.v0.bak")).exists();
        assertThat(home.resolve("settings.xml.v0.bak")).hasContent("<dogsbay/>");
    }

    @Test
    void aFileFromAFutureVersionMustNotBeWrittenBack(@TempDir Path home) throws Exception {
        Path file = Files.writeString(home.resolve("settings.xml"), "<dogsbay/>");
        XElement root = new XElement("dogsbay");
        root.addAttribute(SettingsFile.VERSION_ATTRIBUTE, "99");

        // Leaving it unmigrated is not enough on its own: the editor saves the
        // settings on exit, which would drop everything this build does not know
        // and leave the file still claiming version 99.
        assertThat(SettingsFile.adopt(file, root, List.of(failIfRun()))).isFalse();
    }

    @Test
    void aFileFromAFutureVersionIsLeftAlone(@TempDir Path home) throws Exception {
        Path file = Files.writeString(home.resolve("settings.xml"), "<dogsbay/>");
        XElement root = new XElement("dogsbay");
        root.addAttribute(SettingsFile.VERSION_ATTRIBUTE, "99");

        SettingsFile.adopt(file, root, List.of(failIfRun()));

        // An older build must not rewrite what a newer one wrote.
        assertThat(root.getAttribute(SettingsFile.VERSION_ATTRIBUTE)).isEqualTo("99");
    }

    @Test
    void aMigrationRunsOnceAndLeavesABackup(@TempDir Path home) throws Exception {
        Path file = Files.writeString(home.resolve("settings.xml"), "<dogsbay/>");
        XElement root = new XElement("dogsbay");
        root.addAttribute(SettingsFile.VERSION_ATTRIBUTE, "0");
        var ran = new java.util.concurrent.atomic.AtomicInteger();

        SettingsFile.adopt(file, root, List.of(step(1, r -> {
            ran.incrementAndGet();
            r.addAttribute("migrated", "yes");
        })));

        assertThat(ran.get()).isEqualTo(1);
        assertThat(root.getAttribute("migrated")).isEqualTo("yes");
        assertThat(root.getAttribute(SettingsFile.VERSION_ATTRIBUTE)).isEqualTo("1");
    }

    @Test
    void aFileAtTheCurrentVersionIsNotMigratedAgain(@TempDir Path home) throws Exception {
        Path file = Files.writeString(home.resolve("settings.xml"), "<dogsbay/>");
        XElement root = new XElement("dogsbay");
        root.addAttribute(SettingsFile.VERSION_ATTRIBUTE, String.valueOf(SettingsFile.CURRENT_VERSION));

        SettingsFile.adopt(file, root, List.of(failIfRun()));

        assertThat(root.getAttribute(SettingsFile.VERSION_ATTRIBUTE))
            .isEqualTo(String.valueOf(SettingsFile.CURRENT_VERSION));
    }

    @Test
    void aVersionThatIsNotANumberReadsAsUnversioned() {
        assertThat(SettingsFile.versionOf(null)).isZero();
        assertThat(SettingsFile.versionOf("  ")).isZero();
        assertThat(SettingsFile.versionOf("v33")).isZero();
        assertThat(SettingsFile.versionOf(" 2 ")).isEqualTo(2);
    }

    @Test
    void theBackupIsNamedForTheVersionItLeavesBehind(@TempDir Path home) throws Exception {
        Path file = Files.writeString(home.resolve("settings.xml"), "<dogsbay/>");

        Path backup = SettingsFile.backUp(file, 1);

        assertThat(backup).exists();
        assertThat(backup.getFileName()).hasToString("settings.xml.v1.bak");
        assertThat(SettingsFile.backUp(home.resolve("absent.xml"), 1)).isNull();
    }

    private static SettingsFile.Migration step(int to, java.util.function.Consumer<XElement> apply) {
        return new SettingsFile.Migration() {
            @Override public int to() {
                return to;
            }

            @Override public void apply(XElement root) {
                apply.accept(root);
            }
        };
    }

    private static SettingsFile.Migration failIfRun() {
        return step(1, r -> {
            throw new AssertionError("migrated a file that did not need it");
        });
    }
}
