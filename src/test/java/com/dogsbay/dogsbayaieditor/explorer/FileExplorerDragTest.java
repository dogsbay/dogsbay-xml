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

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileExplorerDragTest {

    @Test
    void selectedFilesLeaveTheTreeAsAFileListAndAsPaths(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("topics"));
        Path a = Files.writeString(dir.resolve("topics/a.dita"), "<topic/>");
        Path b = Files.writeString(dir.resolve("topics/b.dita"), "<topic/>");
        FileSystemNode root = new FileSystemNode(dir.toFile());
        root.loadChildren();
        FileSystemNode topics = (FileSystemNode) root.getChildAt(0);
        topics.loadChildren();
        JTree tree = new JTree(new DefaultTreeModel(root));
        TreePath pa = new TreePath(new Object[] {root, topics, topics.getChildAt(0)});
        TreePath pb = new TreePath(new Object[] {root, topics, topics.getChildAt(1)});
        tree.setSelectionPaths(new TreePath[] {pa, pb});

        FileDragExporter<FileSystemNode> h = new FileDragExporter<>(FileSystemNode.class, FileSystemNode::getFile);
        assertThat(h.getSourceActions(tree)).isEqualTo(TransferHandler.COPY);
        Transferable t = h.transferableFor(tree);

        @SuppressWarnings("unchecked")
        List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
        assertThat(files).containsExactlyInAnyOrder(a.toFile(), b.toFile());
        assertThat((String) t.getTransferData(DataFlavor.stringFlavor)).contains(a.toString()).contains(b.toString());
        assertThat(h.canImport(new TransferHandler.TransferSupport(tree, t))).isFalse();
    }

    @Test
    void nothingSelectedExportsNothing() throws Exception {
        JTree tree = new JTree(new DefaultTreeModel(new FileSystemNode(new File(System.getProperty("java.io.tmpdir")))));
        tree.clearSelection();
        assertThat(new FileDragExporter<>(FileSystemNode.class, FileSystemNode::getFile).transferableFor(tree)).isNull();
    }

    @Test
    void ditaMapNodesDragOutAsTheirResolvedFiles(@TempDir Path dir) throws Exception {
        Path map = Files.writeString(dir.resolve("root.ditamap"), "<map/>");
        Path topic = Files.writeString(dir.resolve("a.dita"), "<topic/>");
        com.dogsbay.dogsbayaieditor.dita.DitaMapNode root = new com.dogsbay.dogsbayaieditor.dita.DitaMapNode("root.ditamap");
        root.setResolvedFile(map.toFile());
        com.dogsbay.dogsbayaieditor.dita.DitaTopicNode child = new com.dogsbay.dogsbayaieditor.dita.DitaTopicNode("A");
        child.setResolvedFile(topic.toFile());
        root.add(child);
        JTree tree = new JTree(new DefaultTreeModel(root));
        tree.setSelectionPaths(new TreePath[] {new TreePath(new Object[] {root, child}), new TreePath(root)});

        Transferable t = new FileDragExporter<>(com.dogsbay.dogsbayaieditor.dita.DitaNode.class,
                com.dogsbay.dogsbayaieditor.dita.DitaNode::getResolvedFile).transferableFor(tree);

        @SuppressWarnings("unchecked")
        List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
        assertThat(files).containsExactlyInAnyOrder(topic.toFile(), map.toFile());
    }
}
