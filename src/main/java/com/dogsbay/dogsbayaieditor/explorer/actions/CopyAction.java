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
import java.awt.event.ActionEvent;
import java.io.File;

import com.dogsbay.dogsbayaieditor.explorer.FileExplorerClipboard;
import com.dogsbay.dogsbayaieditor.explorer.FileSystemNode;

/**
 * Action to copy a file or folder to the clipboard.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/26 $
 * @author DogsBay Ltd
 */
public class CopyAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private FileSystemNode node;
    private JTree tree;

    /**
     * Creates an action to copy a file or folder.
     *
     * @param node  the node to copy
     * @param tree  the tree component (for repaint)
     */
    public CopyAction(FileSystemNode node, JTree tree) {
        super("Copy");
        this.node = node;
        this.tree = tree;

        // Always enabled (as long as node is not null)
        setEnabled(node != null);
    }

    /**
     * Copies the selected file or folder to the clipboard.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (node == null) {
            return;
        }

        File file = node.getFile();

        if (DEBUG) {
            System.out.println("CopyAction: Copying " + file.getAbsolutePath());
        }

        // Add to clipboard
        FileExplorerClipboard.getInstance().copy(file);

        // Repaint tree to clear any previous "cut" visual state
        if (tree != null) {
            tree.repaint();
        }
    }
}
