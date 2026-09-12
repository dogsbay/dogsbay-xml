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
import java.net.URL;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.explorer.FileExplorerPanel;
import com.dogsbay.dogsbayaieditor.explorer.FileOperationUtils;
import com.dogsbay.dogsbayaieditor.explorer.FileSystemNode;

/**
 * Action to create a new file in the file explorer.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/26 $
 * @author DogsBay Ltd
 */
public class NewFileAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private DogsBayAIEditor parent;
    private FileSystemNode node;
    private DefaultTreeModel treeModel;
    private JTree tree;

    /**
     * Creates an action to create a new file.
     *
     * @param parent     the main editor window
     * @param node       the directory node where the file will be created
     * @param treeModel  the tree model
     * @param tree       the tree component
     */
    public NewFileAction(DogsBayAIEditor parent, FileSystemNode node, DefaultTreeModel treeModel, JTree tree) {
        super("New File");
        this.parent = parent;
        this.node = node;
        this.treeModel = treeModel;
        this.tree = tree;

        // Only enable for directories
        setEnabled(node != null && node.isDirectory());
    }

    /**
     * Creates a new file in the selected directory.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (node == null || !node.isDirectory()) {
            return;
        }

        File directory = node.getFile();

        if (DEBUG) {
            System.out.println("NewFileAction: Creating file in " + directory.getAbsolutePath());
        }

        // Show input dialog for filename
        String filename = JOptionPane.showInputDialog(
            parent,
            "Enter filename:",
            "New File",
            JOptionPane.PLAIN_MESSAGE
        );

        if (filename == null || filename.trim().isEmpty()) {
            // User cancelled or entered empty name
            return;
        }

        filename = filename.trim();

        // Validate filename
        if (!FileOperationUtils.isValidFileName(filename)) {
            MessageHandler.showError(
                parent,
                "Invalid filename. Filenames cannot contain: < > : \" / \\ | ? *",
                "Invalid Filename"
            );
            return;
        }

        // Create the file on a background thread
        final String finalFilename = filename;
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    File newFile = FileOperationUtils.createFile(directory, finalFilename);

                    // Update UI on EDT
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            // Refresh the node to show the new file
                            node.refresh(treeModel);
                            treeModel.nodeStructureChanged(node);

                            // Find and select the new file node
                            selectNewFile(newFile);

                            // Open the new file in the editor
                            try {
                                URL url = com.dogsbay.xml.DogsBayURLUtilities.getURLFromFile(newFile);
                                parent.open(url, null, true);
                            } catch (Exception ex) {
                                MessageHandler.showError(
                                    parent,
                                    "File created but could not be opened: " + ex.getMessage(),
                                    "Open File Error"
                                );
                                if (DEBUG) ex.printStackTrace();
                            }
                        }
                    });

                } catch (Exception ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            MessageHandler.showError(
                                parent,
                                "Could not create file: " + ex.getMessage(),
                                "Create File Error"
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
     * Finds and selects the newly created file in the tree.
     *
     * @param newFile the newly created file
     */
    private void selectNewFile(File newFile) {
        if (newFile == null || tree == null) {
            return;
        }

        // Search for the node
        for (int i = 0; i < node.getChildCount(); i++) {
            Object child = node.getChildAt(i);
            if (child instanceof FileSystemNode) {
                FileSystemNode childNode = (FileSystemNode) child;
                if (childNode.getFile().equals(newFile)) {
                    TreePath path = new TreePath(childNode.getPath());
                    tree.setSelectionPath(path);
                    tree.scrollPathToVisible(path);
                    break;
                }
            }
        }
    }
}
