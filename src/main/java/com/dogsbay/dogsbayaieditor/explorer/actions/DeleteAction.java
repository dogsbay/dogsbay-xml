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

import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeModel;
import java.awt.event.ActionEvent;
import java.io.File;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.explorer.FileOperationUtils;
import com.dogsbay.dogsbayaieditor.explorer.FileSystemNode;

/**
 * Action to delete a file or folder in the file explorer.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/26 $
 * @author DogsBay Ltd
 */
public class DeleteAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private DogsBayAIEditor parent;
    private FileSystemNode node;
    private DefaultTreeModel treeModel;

    /**
     * Creates an action to delete a file or folder.
     *
     * @param parent     the main editor window
     * @param node       the node to delete
     * @param treeModel  the tree model
     */
    public DeleteAction(DogsBayAIEditor parent, FileSystemNode node, DefaultTreeModel treeModel) {
        super("Delete");
        this.parent = parent;
        this.node = node;
        this.treeModel = treeModel;

        // Always enabled (as long as node is not null)
        setEnabled(node != null);
    }

    /**
     * Deletes the selected file or folder. Files go through the safe-delete
     * flow: inbound references are scanned first and shown before anything
     * is removed; folders keep the plain confirm.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (node == null) {
            return;
        }

        File file = node.getFile();

        if (DEBUG) {
            System.out.println("DeleteAction: Deleting " + file.getAbsolutePath());
        }

        if (file.isFile()) {
            new com.dogsbay.dogsbayaieditor.refactor.RefactorUi(parent)
                    .deleteFileWithReferences(file, this::refreshParentNode);
            return;
        }

        // Show confirmation dialog
        String message = "Delete folder \"" + file.getName() + "\" and all its contents?\n\n" +
                 "This action cannot be undone.";

        int result = MessageHandler.showConfirm(parent, message);
        if (result != MessageHandler.CONFIRM_YES_OPTION) {
            return;
        }

        // Delete on a background thread
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    FileOperationUtils.deleteDirectory(file);

                    // Update UI on EDT
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            refreshParentNode();
                        }
                    });

                } catch (Exception ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            MessageHandler.showError(
                                parent,
                                "Could not delete: " + ex.getMessage(),
                                "Delete Error"
                            );
                        }
                    });
                    if (DEBUG) ex.printStackTrace();
                }
            }
        });
        thread.start();
    }

    private void refreshParentNode() {
        FileSystemNode parentNode = (FileSystemNode) node.getParent();
        if (parentNode != null) {
            parentNode.refresh(treeModel);
            treeModel.nodeStructureChanged(parentNode);
        }
    }
}
