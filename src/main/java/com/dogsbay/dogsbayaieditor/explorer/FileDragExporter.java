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

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.TreePath;

/**
 * Drags a tree's selected nodes out as the files they stand for: onto an
 * agent chat box, which turns them into mentions, or onto anything else
 * that takes files. The tree is a source only; it accepts no drops.
 *
 * @param <N> the node type; {@code fileOf} says which file a node is, or null for none
 */
public final class FileDragExporter<N> extends TransferHandler {

    private final Class<N> nodeType;
    private final Function<N, File> fileOf;

    public FileDragExporter(Class<N> nodeType, Function<N, File> fileOf) {
        this.nodeType = nodeType;
        this.fileOf = fileOf;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return COPY;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        return transferableFor(c);
    }

    /** The selection as a transferable, or null when nothing selected is a file. */
    public Transferable transferableFor(JComponent c) {
        if (!(c instanceof JTree t) || t.getSelectionPaths() == null) {
            return null;
        }
        List<File> files = new ArrayList<>();
        for (TreePath path : t.getSelectionPaths()) {
            Object node = path.getLastPathComponent();
            if (nodeType.isInstance(node)) {
                File f = fileOf.apply(nodeType.cast(node));
                if (f != null && f.exists() && !files.contains(f)) {
                    files.add(f);
                }
            }
        }
        return files.isEmpty() ? null : new FileListTransferable(files);
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return false;
    }

    /** A file list plus the paths as text, one per line, for plain text targets. */
    public static final class FileListTransferable implements Transferable {
        private final List<File> files;

        FileListTransferable(List<File> files) {
            this.files = List.copyOf(files);
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.javaFileListFlavor.equals(flavor) || DataFlavor.stringFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (DataFlavor.javaFileListFlavor.equals(flavor)) {
                return files;
            }
            if (DataFlavor.stringFlavor.equals(flavor)) {
                StringBuilder sb = new StringBuilder();
                for (File f : files) {
                    sb.append(sb.isEmpty() ? "" : "\n").append(f.getAbsolutePath());
                }
                return sb.toString();
            }
            throw new UnsupportedFlavorException(flavor);
        }
    }
}
