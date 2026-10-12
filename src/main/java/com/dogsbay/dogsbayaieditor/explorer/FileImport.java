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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeModel;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;

/**
 * Copying files from outside into a folder of the project: what <em>Add Files…</em> and a drop from the desktop
 * both do once they know which files and which folder.
 *
 * <p>Copies rather than moves. The files come from somewhere the editor does not own — a download, a colleague's
 * folder, another project — and a tool that silently emptied that folder would be a tool nobody drags onto
 * twice.
 */
public final class FileImport {

    private FileImport() {}

    /**
     * Copy {@code files} into the folder {@code target} stands for, refresh the tree and select the first one.
     *
     * @param parent the editor, for the report when something could not be copied and to select what arrived;
     *               null asks for neither, which is what a test wants
     * @param target a folder node, or a file node — a file's folder is what was meant
     * @return the files as they now are inside the project, in the order given
     */
    public static List<File> copyInto(DogsBayAIEditor parent, List<File> files, FileSystemNode target,
            DefaultTreeModel treeModel, JTree tree) {
        File folder = folderOf(target);
        if (folder == null || files == null || files.isEmpty()) {
            return List.of();
        }
        List<File> copied = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (File source : files) {
            if (source == null || !source.exists()) {
                continue;
            }
            if (isInside(source, folder)) {
                continue; // already here: a drop onto its own folder is not a request for a second copy
            }
            if (java.nio.file.Files.isSymbolicLink(source.toPath())) {
                // A link is a pointer to someone else's file. Copying what it points at can mean copying a
                // home directory; copying the link itself would leave the project depending on this machine.
                failed.add(source.getName() + " — is a link, not a file");
                continue;
            }
            String name = FileOperationUtils.generateUniqueFileName(folder, source.getName());
            File destination = new File(folder, name);
            try {
                boolean wrote = source.isDirectory()
                        ? FileOperationUtils.copyDirectory(source, destination, false)
                        : FileOperationUtils.copyFile(source, destination, false);
                if (wrote) {
                    copied.add(destination);
                } else {
                    // Both return false rather than throwing when the destination turned up underneath us.
                    // Reporting it as arrived would hand the reader someone else's file.
                    failed.add(source.getName() + " — " + destination.getName() + " already exists");
                }
            } catch (IOException e) {
                failed.add(source.getName() + " — " + e.getMessage());
            } catch (RuntimeException | StackOverflowError e) {
                // A folder holding a link to one of its own ancestors recurses until the stack gives out, and
                // an Error is not an IOException: without this the drop reports nothing at all and leaves a
                // half-written tree behind.
                failed.add(source.getName() + " — could not be copied (" + e.getClass().getSimpleName() + ")");
            }
        }

        // The tree is refreshed even when nothing was copied: a folder that failed part way through has left
        // something behind, and a reader should see what is there rather than find it on the next refresh.
        FileSystemNode folderNode = target.getFile().isDirectory() ? target
                : target.getParent() instanceof FileSystemNode p ? p : null;
        File first = copied.isEmpty() ? null : copied.get(0);
        List<String> problems = List.copyOf(failed);
        onEdt(() -> {
            if (folderNode != null && treeModel != null) {
                folderNode.refresh(treeModel);
                treeModel.nodeStructureChanged(folderNode);
            }
            if (first != null && parent != null && parent.getFileExplorer() != null) {
                parent.getFileExplorer().selectFile(first);
            }
            if (!problems.isEmpty() && parent != null) {
                JOptionPane.showMessageDialog(parent,
                        (copied.isEmpty() ? "Could not copy:\n" : "Copied " + copied.size()
                                + "; could not copy:\n") + String.join("\n", problems),
                        "Add Files", JOptionPane.WARNING_MESSAGE);
            }
        });
        return copied;
    }

    /**
     * Copy in the background, so a folder of any size does not freeze the editor — and, for a drop, the file
     * manager that is waiting for the transfer to be acknowledged.
     *
     * @see #copyInto
     */
    public static void copyIntoLater(DogsBayAIEditor parent, List<File> files, FileSystemNode target,
            DefaultTreeModel treeModel, JTree tree) {
        Thread worker = new Thread(() -> copyInto(parent, files, target, treeModel, tree), "file-import");
        worker.setDaemon(true);
        worker.start();
    }

    /** Run on the EDT, whichever thread asked: the copying belongs off it, the tree and the dialogs on it. */
    private static void onEdt(Runnable work) {
        if (SwingUtilities.isEventDispatchThread()) {
            work.run();
        } else {
            SwingUtilities.invokeLater(work);
        }
    }

    /** The folder a node stands for: itself, or the folder holding it. */
    static File folderOf(FileSystemNode node) {
        if (node == null || node.getFile() == null) {
            return null;
        }
        File file = node.getFile();
        return file.isDirectory() ? file : file.getParentFile();
    }

    /** Whether the file already sits directly in that folder. */
    static boolean isInside(File file, File folder) {
        File parent = file.getParentFile();
        try {
            return parent != null && parent.getCanonicalFile().equals(folder.getCanonicalFile());
        } catch (IOException e) {
            return parent != null && parent.getAbsoluteFile().equals(folder.getAbsoluteFile());
        }
    }
}
