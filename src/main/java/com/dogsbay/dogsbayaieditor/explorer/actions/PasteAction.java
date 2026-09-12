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

package com.dogsbay.dogsbayaieditor.explorer.actions;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.explorer.FileExplorerClipboard;
import com.dogsbay.dogsbayaieditor.explorer.FileOperationUtils;
import com.dogsbay.dogsbayaieditor.explorer.FileSystemNode;

/**
 * Action to paste files or folders from the clipboard.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/26 $
 * @author DogsBay Ltd
 */
public class PasteAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private DogsBayAIEditor parent;
    private FileSystemNode node;
    private DefaultTreeModel treeModel;
    private JTree tree;

    /**
     * Creates an action to paste files or folders.
     *
     * @param parent     the main editor window
     * @param node       the target directory node
     * @param treeModel  the tree model
     * @param tree       the tree component
     */
    public PasteAction(DogsBayAIEditor parent, FileSystemNode node, DefaultTreeModel treeModel, JTree tree) {
        super("Paste");
        this.parent = parent;
        this.node = node;
        this.treeModel = treeModel;
        this.tree = tree;

        // Enable only if clipboard has content AND node is a directory
        FileExplorerClipboard clipboard = FileExplorerClipboard.getInstance();
        setEnabled(node != null && node.isDirectory() && !clipboard.isEmpty());
    }

    /**
     * Pastes files or folders from the clipboard to the selected directory.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (node == null || !node.isDirectory()) {
            return;
        }

        FileExplorerClipboard clipboard = FileExplorerClipboard.getInstance();
        if (clipboard.isEmpty()) {
            return;
        }

        File targetDir = node.getFile();

        if (DEBUG) {
            System.out.println("PasteAction: Pasting to " + targetDir.getAbsolutePath());
        }

        // Perform paste on a background thread
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    // First pass: try to paste without overwriting
                    FileExplorerClipboard.PasteResult result = clipboard.paste(targetDir, 0);

                    if (result.hasConflicts()) {
                        // Handle conflicts on EDT
                        handleConflicts(result.getConflicts(), targetDir);
                    } else {
                        // Success - update UI and select first pasted file
                        String firstPastedFileName = null;
                        if (!result.getPastedFiles().isEmpty()) {
                            firstPastedFileName = result.getPastedFiles().get(0).getName();
                        }
                        updateUIAfterPaste(firstPastedFileName);
                    }

                } catch (Exception ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            MessageHandler.showError(
                                parent,
                                "Could not paste: " + ex.getMessage(),
                                "Paste Error"
                            );
                        }
                    });
                    if (DEBUG) ex.printStackTrace();
                }
            }
        });
        thread.start();
    }

    /**
     * Handles conflicts by asking the user what to do.
     *
     * @param conflicts the list of conflicting files
     * @param targetDir the target directory
     */
    private void handleConflicts(List<File> conflicts, File targetDir) {
        if (conflicts == null || conflicts.isEmpty()) {
            return;
        }

        // Ask user on EDT
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                int userChoice = MessageHandler.CONFIRM_CANCEL_OPTION;
                boolean applyToAll = false;

                for (File file : conflicts) {
                    if (!applyToAll) {
                        String message = "File \"" + file.getName() + "\" already exists in the destination.\n\n" +
                                       "Do you want to overwrite it?";

                        userChoice = MessageHandler.showConfirmYesNoNoToAll(parent, message);

                        if (userChoice == MessageHandler.CONFIRM_CANCEL_OPTION) {
                            // User cancelled
                            return;
                        }

                        if (userChoice == MessageHandler.CONFIRM_NO_TO_ALL_OPTION) {
                            // Skip all remaining conflicts
                            return;
                        }

                        // Check if this applies to all (we'll use NO_TO_ALL for skip all,
                        // and we need to manually track "Yes to all")
                        // Since MessageHandler doesn't have "Yes to All" in the No To All dialog,
                        // we'll handle each conflict individually
                    }

                    if (userChoice == MessageHandler.CONFIRM_YES_OPTION) {
                        // Overwrite this file
                        pasteFileWithOverwrite(file, targetDir);
                    }
                    // If NO, skip this file and continue to next
                }

                // Update UI after handling conflicts
                updateUIAfterPaste();
            }
        });
    }

    /**
     * Pastes a single file with overwrite enabled.
     *
     * @param file      the file to paste
     * @param targetDir the target directory
     */
    private void pasteFileWithOverwrite(File file, File targetDir) {
        try {
            File destFile = new File(targetDir, file.getName());
            FileExplorerClipboard clipboard = FileExplorerClipboard.getInstance();

            if (clipboard.getOperation() == FileExplorerClipboard.ClipboardOperation.COPY) {
                if (file.isDirectory()) {
                    FileOperationUtils.copyDirectory(file, destFile, true);
                } else {
                    FileOperationUtils.copyFile(file, destFile, true);
                }
            } else { // CUT
                FileOperationUtils.moveFile(file, destFile);
            }

            if (DEBUG) {
                System.out.println("PasteAction: Pasted with overwrite " + file.getName());
            }

        } catch (Exception ex) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    MessageHandler.showError(
                        parent,
                        "Could not paste " + file.getName() + ": " + ex.getMessage(),
                        "Paste Error"
                    );
                }
            });
            if (DEBUG) ex.printStackTrace();
        }
    }

    /**
     * Updates the UI after a successful paste operation.
     */
    private void updateUIAfterPaste() {
        updateUIAfterPaste(null);
    }

    /**
     * Updates the UI after a successful paste operation.
     *
     * @param pastedFileName the name of the pasted file to select (null to not select)
     */
    private void updateUIAfterPaste(String pastedFileName) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                // Refresh the target node to show pasted items
                node.refresh(treeModel);
                treeModel.nodeStructureChanged(node);

                // Expand the target node if not already expanded
                TreePath targetPath = new TreePath(node.getPath());
                if (!tree.isExpanded(targetPath)) {
                    tree.expandPath(targetPath);
                }

                // Select and scroll to the newly pasted file/folder
                if (pastedFileName != null) {
                    selectChildNode(node, pastedFileName);
                }

                // Clear cut visual state and repaint tree
                FileExplorerClipboard clipboard = FileExplorerClipboard.getInstance();
                if (clipboard.getOperation() == FileExplorerClipboard.ClipboardOperation.CUT) {
                    clearAllCutStates();
                    clipboard.clear();
                }

                // Repaint tree
                if (tree != null) {
                    tree.repaint();
                }
            }
        });
    }

    /**
     * Selects a child node by name and scrolls to it.
     *
     * @param parentNode the parent node
     * @param childName  the name of the child to select
     */
    private void selectChildNode(FileSystemNode parentNode, String childName) {
        if (parentNode == null || childName == null) {
            return;
        }

        // Search for the child node
        for (int i = 0; i < parentNode.getChildCount(); i++) {
            Object child = parentNode.getChildAt(i);
            if (child instanceof FileSystemNode) {
                FileSystemNode childNode = (FileSystemNode) child;
                if (childNode.getFile().getName().equals(childName)) {
                    // Found it - select and scroll to it
                    TreePath childPath = new TreePath(childNode.getPath());
                    tree.setSelectionPath(childPath);
                    tree.scrollPathToVisible(childPath);
                    return;
                }
            }
        }
    }

    /**
     * Clears the cut state on all nodes in the tree.
     */
    private void clearAllCutStates() {
        if (treeModel == null || treeModel.getRoot() == null) {
            return;
        }

        Object root = treeModel.getRoot();
        if (root instanceof FileSystemNode) {
            clearCutStateRecursive((FileSystemNode) root);
        }
    }

    /**
     * Recursively clears the cut state on a node and its children.
     *
     * @param node the node to clear
     */
    private void clearCutStateRecursive(FileSystemNode node) {
        if (node == null) {
            return;
        }

        node.setCut(false);

        // Clear children
        for (int i = 0; i < node.getChildCount(); i++) {
            Object child = node.getChildAt(i);
            if (child instanceof FileSystemNode) {
                clearCutStateRecursive((FileSystemNode) child);
            }
        }
    }
}
