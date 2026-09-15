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

class DitaOtTempTest {

    @TempDir Path root;

    @Test
    void keepsEachDeliverableInOneSafeFolder() {
        assertThat(DitaOtTemp.dirFor(root, "full")).isEqualTo(root.resolve(".dogsbay/temp/full"));
        assertThat(DitaOtTemp.safeName("beginner mac")).isEqualTo("beginner_mac");
        assertThat(DitaOtTemp.safeName("../outside")).isEqualTo(".._outside");
        assertThat(DitaOtTemp.safeName("..")).isEqualTo("deliverable");
        assertThat(DitaOtTemp.safeName(" ")).isEqualTo("deliverable");
        assertThat(DitaOtTemp.dirFor(root, "a/b").getParent()).isEqualTo(DitaOtTemp.folder(root));
    }

    @Test
    void givesDeliverablesThatShareAFolderNameTheirOwnFolders() throws Exception {
        java.util.Set<String> used = new java.util.HashSet<>();

        assertThat(DitaOtTemp.uniqueDir(root, "beginner mac", used).getFileName()).hasToString("beginner_mac");
        assertThat(DitaOtTemp.uniqueDir(root, "beginner_mac", used).getFileName()).hasToString("beginner_mac-2");
        assertThat(DitaOtTemp.uniqueDir(root, "Beginner_Mac", used).getFileName())
                .as("differs only in case").hasToString("Beginner_Mac-3");
        assertThat(DitaOtTemp.uniqueDir(root, "full", used).getFileName()).hasToString("full");
        assertThat(DitaOtTemp.uniqueDir(root, "full", used).getFileName()).as("same name").hasToString("full-2");
    }

    @Test
    void refusesToPrepareAFolderOutsideTheTemporaryFolder() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> DitaOtTemp.prepare(root, root.resolve("topics")))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    void prepareReplacesThePreviousBuildAndIgnoresTheFolder() throws Exception {
        Path first = DitaOtTemp.prepare(root, "full");
        Files.writeString(first.resolve("stale.xml"), "<old/>");
        Files.createDirectories(first.resolve("topics")).resolve("t.dita").toFile().createNewFile();

        Path second = DitaOtTemp.prepare(root, "full");

        assertThat(second).isEqualTo(first).isEmptyDirectory();
        assertThat(DitaOtTemp.folder(root).resolve(".gitignore")).hasContent("*");
    }

    @Test
    void clearRemovesEveryKeptFolder() throws Exception {
        assertThat(DitaOtTemp.clear(root)).as("nothing kept yet").isZero();
        Files.writeString(DitaOtTemp.prepare(root, "full").resolve("a.xml"), "<a/>");
        DitaOtTemp.prepare(root, "beginner");

        assertThat(DitaOtTemp.clear(root)).isEqualTo(2);
        assertThat(DitaOtTemp.dirFor(root, "full")).doesNotExist();
        assertThat(DitaOtTemp.folder(root).resolve(".gitignore")).as("the ignore stays").exists();
    }
}
