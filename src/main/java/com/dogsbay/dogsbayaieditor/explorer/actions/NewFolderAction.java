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
 * Action to create a new folder in the file explorer.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/26 $
 * @author DogsBay Ltd
 */
public class NewFolderAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private DogsBayAIEditor parent;
    private FileSystemNode node;
    private DefaultTreeModel treeModel;
    private JTree tree;

    /**
     * Creates an action to create a new folder.
     *
     * @param parent     the main editor window
     * @param node       the directory node where the folder will be created
     * @param treeModel  the tree model
     * @param tree       the tree component
     */
    public NewFolderAction(DogsBayAIEditor parent, FileSystemNode node, DefaultTreeModel treeModel, JTree tree) {
        super("New Folder");
        this.parent = parent;
        this.node = node;
        this.treeModel = treeModel;
        this.tree = tree;

        // Only enable for directories
        setEnabled(node != null && node.isDirectory());
    }

    /**
     * Creates a new folder in the selected directory.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (node == null || !node.isDirectory()) {
            return;
        }

        File directory = node.getFile();

        if (DEBUG) {
            System.out.println("NewFolderAction: Creating folder in " + directory.getAbsolutePath());
        }

        // Show input dialog for folder name
        String folderName = JOptionPane.showInputDialog(
            parent,
            "Enter folder name:",
            "New Folder",
            JOptionPane.PLAIN_MESSAGE
        );

        if (folderName == null || folderName.trim().isEmpty()) {
            // User cancelled or entered empty name
            return;
        }

        folderName = folderName.trim();

        // Validate folder name
        if (!FileOperationUtils.isValidFileName(folderName)) {
            MessageHandler.showError(
                parent,
                "Invalid folder name. Folder names cannot contain: < > : \" / \\ | ? *",
                "Invalid Folder Name"
            );
            return;
        }

        // Create the folder on a background thread
        final String finalFolderName = folderName;
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    File newFolder = FileOperationUtils.createDirectory(directory, finalFolderName);

                    // Update UI on EDT
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            // Refresh the node to show the new folder
                            node.refresh(treeModel);
                            treeModel.nodeStructureChanged(node);

                            // Find and select the new folder node
                            selectNewFolder(newFolder);
                        }
                    });

                } catch (Exception ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            MessageHandler.showError(
                                parent,
                                "Could not create folder: " + ex.getMessage(),
                                "Create Folder Error"
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
     * Finds and selects the newly created folder in the tree.
     *
     * @param newFolder the newly created folder
     */
    private void selectNewFolder(File newFolder) {
        if (newFolder == null || tree == null) {
            return;
        }

        // Search for the node
        for (int i = 0; i < node.getChildCount(); i++) {
            Object child = node.getChildAt(i);
            if (child instanceof FileSystemNode) {
                FileSystemNode childNode = (FileSystemNode) child;
                if (childNode.getFile().equals(newFolder)) {
                    TreePath path = new TreePath(childNode.getPath());
                    tree.setSelectionPath(path);
                    tree.scrollPathToVisible(path);
                    break;
                }
            }
        }
    }
}
