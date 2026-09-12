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
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import java.awt.event.ActionEvent;
import java.io.File;

import com.dogsbay.dogsbayaieditor.explorer.FileExplorerClipboard;
import com.dogsbay.dogsbayaieditor.explorer.FileSystemNode;

/**
 * Action to cut a file or folder to the clipboard.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/26 $
 * @author DogsBay Ltd
 */
public class CutAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private FileSystemNode node;
    private DefaultTreeModel treeModel;
    private JTree tree;

    /**
     * Creates an action to cut a file or folder.
     *
     * @param node       the node to cut
     * @param treeModel  the tree model
     * @param tree       the tree component
     */
    public CutAction(FileSystemNode node, DefaultTreeModel treeModel, JTree tree) {
        super("Cut");
        this.node = node;
        this.treeModel = treeModel;
        this.tree = tree;

        // Always enabled (as long as node is not null)
        setEnabled(node != null);
    }

    /**
     * Cuts the selected file or folder to the clipboard.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (node == null) {
            return;
        }

        File file = node.getFile();

        if (DEBUG) {
            System.out.println("CutAction: Cutting " + file.getAbsolutePath());
        }

        // Clear any previous cut state on all nodes
        clearAllCutStates();

        // Add to clipboard
        FileExplorerClipboard.getInstance().cut(file);

        // Mark this node as cut
        node.setCut(true);

        // Repaint tree to show gray-out effect
        if (tree != null) {
            tree.repaint();
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
