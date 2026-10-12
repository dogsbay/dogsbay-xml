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

package com.dogsbay.dogsbayaieditor.ditaproject;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Kept DITA-OT temporary files in {@code .dogsbay/temp} must not count as project files. */
class FileSetHiddenFolderTest {

    @TempDir Path root;

    private Path write(String rel) throws Exception {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, "<topic id=\"t\"/>");
        return p;
    }

    @Test
    void underRootSkipsHiddenFolders() throws Exception {
        Path topic = write("topics/t.dita");
        write(".dogsbay/temp/full/topics/t.dita");
        write(".git/x.xml");

        assertThat(FileSet.underRoot(root)).containsExactly(topic);
    }

    @Test
    void onlyFoldersBelowTheRootCount() {
        Path hiddenRoot = root.resolve(".hidden/project");

        assertThat(FileSet.inHiddenFolder(hiddenRoot, hiddenRoot.resolve("topics/t.dita"))).isFalse();
        assertThat(FileSet.inHiddenFolder(hiddenRoot, hiddenRoot.resolve(".dogsbay/temp/t.dita"))).isTrue();
        assertThat(FileSet.inHiddenFolder(root, root.resolve(".profile.dita"))).as("a hidden file, not a folder").isFalse();
    }
}
