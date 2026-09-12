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

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.explorer.FileOperationUtils;
import com.dogsbay.dogsbayaieditor.explorer.FileSystemNode;

/**
 * Action to rename a file or folder in the file explorer.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/26 $
 * @author DogsBay Ltd
 */
public class RenameAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private DogsBayAIEditor parent;
    private FileSystemNode node;
    private DefaultTreeModel treeModel;
    private JTree tree;

    /**
     * Creates an action to rename a file or folder.
     *
     * @param parent     the main editor window
     * @param node       the node to rename
     * @param treeModel  the tree model
     * @param tree       the tree component
     */
    public RenameAction(DogsBayAIEditor parent, FileSystemNode node, DefaultTreeModel treeModel, JTree tree) {
        super("Rename");
        this.parent = parent;
        this.node = node;
        this.treeModel = treeModel;
        this.tree = tree;

        // Always enabled (as long as node is not null)
        setEnabled(node != null);
    }

    /**
     * Renames the selected file or folder.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (node == null) {
            return;
        }

        File file = node.getFile();
        String currentName = file.getName();

        if (DEBUG) {
            System.out.println("RenameAction: Renaming " + file.getAbsolutePath());
        }

        // Show input dialog with current name pre-filled
        String newName = (String) JOptionPane.showInputDialog(
            parent,
            "Enter new name:",
            "Rename",
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            currentName
        );

        if (newName == null || newName.trim().isEmpty()) {
            // User cancelled or entered empty name
            return;
        }

        newName = newName.trim();

        // Check if name actually changed
        if (newName.equals(currentName)) {
            return;
        }

        // Validate new name
        if (!FileOperationUtils.isValidFileName(newName)) {
            MessageHandler.showError(
                parent,
                "Invalid name. Names cannot contain: < > : \" / \\ | ? *",
                "Invalid Name"
            );
            return;
        }

        // Rename on a background thread
        final String finalNewName = newName;
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    File renamedFile = FileOperationUtils.renameFile(file, finalNewName);

                    // Update UI on EDT
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            // Refresh the parent node to show the renamed item
                            FileSystemNode parentNode = (FileSystemNode) node.getParent();
                            if (parentNode != null) {
                                parentNode.refresh(treeModel);
                                treeModel.nodeStructureChanged(parentNode);

                                // Find and select the renamed node
                                selectRenamedFile(renamedFile);
                            }
                        }
                    });

                } catch (Exception ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            MessageHandler.showError(
                                parent,
                                "Could not rename: " + ex.getMessage(),
                                "Rename Error"
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
     * Finds and selects the renamed file in the tree.
     *
     * @param renamedFile the renamed file
     */
    private void selectRenamedFile(File renamedFile) {
        if (renamedFile == null || tree == null) {
            return;
        }

        FileSystemNode parentNode = (FileSystemNode) node.getParent();
        if (parentNode == null) {
            return;
        }

        // Search for the renamed node
        for (int i = 0; i < parentNode.getChildCount(); i++) {
            Object child = parentNode.getChildAt(i);
            if (child instanceof FileSystemNode) {
                FileSystemNode childNode = (FileSystemNode) child;
                if (childNode.getFile().equals(renamedFile)) {
                    TreePath path = new TreePath(childNode.getPath());
                    tree.setSelectionPath(path);
                    tree.scrollPathToVisible(path);
                    break;
                }
            }
        }
    }
}
