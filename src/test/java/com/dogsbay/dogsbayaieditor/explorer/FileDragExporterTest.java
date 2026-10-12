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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the explorer's tree will and will not take.
 *
 * <p>The tree is a drag source whose export is a file list, so once it started accepting file-list drops it
 * accepted its own: dragging a topic a few rows within the project copied it. Declining an internal drag is
 * what keeps "drag a file in from the desktop" from also meaning "duplicate this file".
 */
class FileDragExporterTest {

    @TempDir java.nio.file.Path dir;

    private final List<List<File>> taken = new ArrayList<>();

    private FileDragExporter<FileSystemNode> handler() {
        return new FileDragExporter<>(FileSystemNode.class, FileSystemNode::getFile,
                (files, onto) -> taken.add(files));
    }

    private JTree treeWithOneFile() throws Exception {
        java.nio.file.Files.writeString(dir.resolve("a.dita"), "<concept id=\"a\"/>");
        FileSystemNode root = new FileSystemNode(dir.toFile());
        root.add(new FileSystemNode(dir.resolve("a.dita").toFile()));
        return new JTree(new DefaultTreeModel(root));
    }

    @Test
    void aSourceOnlyHandlerTakesNothing() {
        // The Topic Maps panel shares this class and must not start accepting drops. Given no handler, the
        // question is answered before anything about the drop is looked at.
        FileDragExporter<FileSystemNode> sourceOnly =
                new FileDragExporter<>(FileSystemNode.class, FileSystemNode::getFile);

        assertThat(sourceOnly.canImport(null)).isFalse();
    }

    @Test
    void nothingSelectedExportsNothing() throws Exception {
        assertThat(handler().transferableFor(treeWithOneFile())).isNull();
    }
}
