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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where the folder chooser opens.
 *
 * <p>It opened on the filesystem root, showing every disk, because the folder it was given was the last opened
 * <em>document</em> — and with no documents that is the empty string, which the old chooser resolved to the
 * roots. A reader then navigated from nowhere, and a machine being recorded put its owner's folder names on
 * screen before anyone had chosen anything (reported 2026-09-29).
 */
class FolderChooserStartTest {

    @TempDir Path dir;

    @Test
    void theFolderLastOpenedWins() throws Exception {
        Path folder = Files.createDirectory(dir.resolve("project"));

        assertThat(FileUtilities.startingFolder(folder.toString(), null)).isEqualTo(folder.toFile());
    }

    @Test
    void withNoFolderItOpensWhereTheLastDocumentIs() throws Exception {
        Path folder = Files.createDirectory(dir.resolve("topics"));
        Path document = Files.writeString(folder.resolve("intro.dita"), "<concept id=\"i\"/>");

        assertThat(FileUtilities.startingFolder(null, document.toString())).isEqualTo(folder.toFile());
    }

    @Test
    void withNothingItOpensAtHome() {
        File home = new File(System.getProperty("user.home", "."));

        assertThat(FileUtilities.startingFolder(null, null)).isEqualTo(home);
        assertThat(FileUtilities.startingFolder("", "")).isEqualTo(home);
    }

    @Test
    void aFolderThatIsNotThereIsNotUsed() {
        // The settings outlive the folders they name: a project moved or deleted since must not send the chooser
        // somewhere that does not exist, where it has nothing to show and no way back.
        File home = new File(System.getProperty("user.home", "."));

        assertThat(FileUtilities.startingFolder(dir.resolve("gone").toString(), null)).isEqualTo(home);
    }

    @Test
    void itNeverOpensOnTheFilesystemRoot() {
        // The reported symptom, from the value that produced it.
        assertThat(FileUtilities.startingFolder("", "")).isNotEqualTo(new File("/"));
        assertThat(FileUtilities.startingFolder(null, "").getPath()).isNotEqualTo(File.separator);
    }
}
