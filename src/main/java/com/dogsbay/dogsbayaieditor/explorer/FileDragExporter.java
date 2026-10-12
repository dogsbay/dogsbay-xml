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
 * that takes files.
 *
 * <p>With a {@link Dropped} handler it takes files the other way too — dragged in from a desktop or a file
 * manager — which is how content gets into a project without leaving the editor. A tree given none stays a
 * source only, as it was.
 *
 * @param <N> the node type; {@code fileOf} says which file a node is, or null for none
 */
public final class FileDragExporter<N> extends TransferHandler {

    /** What to do with files dropped onto a node: the tree owns what "into here" means. */
    @FunctionalInterface
    public interface Dropped<N> {
        /**
         * @param files the files dropped, from outside the application
         * @param onto  the node they were dropped on, or null for the tree's background
         * @return true when they were taken
         */
        boolean accept(List<File> files, N onto);
    }

    private final Class<N> nodeType;
    private final Function<N, File> fileOf;
    private final Dropped<N> dropped;
    /** True while a drag that started in this component is in flight. */
    private boolean draggingFromHere;

    /** A source-only handler: the tree exports its files and takes nothing. */
    public FileDragExporter(Class<N> nodeType, Function<N, File> fileOf) {
        this(nodeType, fileOf, null);
    }

    public FileDragExporter(Class<N> nodeType, Function<N, File> fileOf, Dropped<N> dropped) {
        this.nodeType = nodeType;
        this.fileOf = fileOf;
        this.dropped = dropped;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return COPY;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        Transferable t = transferableFor(c);
        draggingFromHere = t != null;
        return t;
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        draggingFromHere = false;
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
        if (dropped == null || !support.isDrop()
                || !support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return false;
        }
        if (draggingFromHere) {
            // The tree is a drag source too, and what it exports is a file list — so without this a drag
            // inside the tree satisfies every other test here and lands as a copy of the file being dragged.
            // Moving a file within the project is a different operation (references have to follow it, which
            // is Refactor's business), so an internal drag is declined rather than guessed at.
            return false;
        }
        // Copy, whatever the source proposed. A MOVE that we report as done is a file manager's cue to delete
        // the originals, and these are files we do not own.
        support.setDropAction(COPY);
        return true;
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }
        List<File> files;
        try {
            Object data = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
            if (!(data instanceof List<?> list)) {
                return false;
            }
            files = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof File f && f.exists()) {
                    files.add(f);
                }
            }
        } catch (Exception e) {
            return false; // a drop we cannot read is a drop that did not happen
        }
        if (files.isEmpty()) {
            return false;
        }
        return dropped.accept(files, nodeUnder(support));
    }

    /** The node a drop landed on, or null when it landed on nothing the tree knows. */
    private N nodeUnder(TransferSupport support) {
        if (!(support.getDropLocation() instanceof JTree.DropLocation where) || where.getPath() == null) {
            return null;
        }
        Object node = where.getPath().getLastPathComponent();
        return nodeType.isInstance(node) ? nodeType.cast(node) : null;
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
