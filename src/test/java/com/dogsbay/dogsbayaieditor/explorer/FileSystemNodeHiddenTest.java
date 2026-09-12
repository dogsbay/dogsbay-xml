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
package com.dogsbay.dogsbayaieditor.explorer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemNodeHiddenTest {

    @TempDir
    Path root;

    @AfterEach
    void reset() {
        FileSystemNode.setShowHidden(false);
    }

    private List<String> children() {
        FileSystemNode node = new FileSystemNode(root.toFile());
        node.loadChildren();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            names.add(((FileSystemNode) node.getChildAt(i)).getFile().getName());
        }
        return names;
    }

    @Test
    void hiddenEntriesAreSkippedUnlessAsked() throws Exception {
        Files.createDirectories(root.resolve(".dogsbay/agent-audit"));
        Files.writeString(root.resolve("a.dita"), "x");
        Files.writeString(root.resolve(".gitignore"), "x");

        assertThat(children()).containsExactly("a.dita");

        FileSystemNode.setShowHidden(true);
        assertThat(children()).containsExactly(".dogsbay", ".gitignore", "a.dita");
    }
}
