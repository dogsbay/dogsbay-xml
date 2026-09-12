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

import javax.swing.AbstractAction;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;

/**
 * Action to refresh a directory in the file explorer.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/22 $
 * @author DogsBay Ltd
 */
public class RefreshExplorerAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private FileSystemNode node;
    private DefaultTreeModel model;
    private JTree tree;

    /**
     * Creates an action to refresh a directory.
     *
     * @param node  the directory node to refresh
     * @param model the tree model
     * @param tree  the tree component (for preserving expansion state)
     */
    public RefreshExplorerAction(FileSystemNode node, DefaultTreeModel model, JTree tree) {
        super("Refresh");
        this.node = node;
        this.model = model;
        this.tree = tree;

        // Enable for directories only
        setEnabled(node != null && node.isDirectory());
    }

    /**
     * Refreshes the directory contents.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (node != null && node.isDirectory()) {
            if (DEBUG) {
                System.out.println("RefreshExplorerAction: Refreshing " + node.getFile().getAbsolutePath());
            }

            node.refresh(model);
        }
    }

    /**
     * Saves the current expansion state of the tree.
     * 
     * @return a set of string paths representing expanded nodes
     */
    private java.util.Set<String> saveTreeExpansionState() {
        java.util.Set<String> expandedPaths = new java.util.HashSet<>();

        if (tree == null || node == null) {
            return expandedPaths;
        }

        // Enumerate all nodes and save paths of expanded ones
        java.util.Enumeration<?> enumeration = node.depthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            Object element = enumeration.nextElement();
            if (!(element instanceof FileSystemNode)) {
                continue; // Skip non-FileSystemNode elements
            }

            FileSystemNode treeNode = (FileSystemNode) element;
            TreePath path = new TreePath(treeNode.getPath());

            if (tree.isExpanded(path)) {
                // Save the node's string representation as the path key
                expandedPaths.add(getNodePath(treeNode));
            }
        }

        return expandedPaths;
    }

    /**
     * Restores the expansion state of the tree.
     * 
     * @param expandedPaths a set of string paths representing nodes that should be
     *                      expanded
     */
    private void restoreTreeExpansionState(java.util.Set<String> expandedPaths) {
        if (expandedPaths == null || expandedPaths.isEmpty() || tree == null || node == null) {
            return;
        }

        // Enumerate all nodes and expand those that were previously expanded
        java.util.Enumeration<?> enumeration = node.depthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            Object element = enumeration.nextElement();
            if (!(element instanceof FileSystemNode)) {
                continue; // Skip non-FileSystemNode elements
            }

            FileSystemNode treeNode = (FileSystemNode) element;
            String nodePath = getNodePath(treeNode);

            if (expandedPaths.contains(nodePath)) {
                TreePath path = new TreePath(treeNode.getPath());
                tree.expandPath(path);
            }
        }
    }

    /**
     * Gets a string representation of a node's path for identifying it across tree
     * rebuilds.
     * 
     * @param treeNode the tree node
     * @return a string path like "root/folder/subfolder"
     */
    private String getNodePath(FileSystemNode treeNode) {
        Object[] path = treeNode.getUserObjectPath();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.length; i++) {
            if (i > 0) {
                sb.append("/");
            }
            sb.append(path[i].toString());
        }
        return sb.toString();
    }
}
