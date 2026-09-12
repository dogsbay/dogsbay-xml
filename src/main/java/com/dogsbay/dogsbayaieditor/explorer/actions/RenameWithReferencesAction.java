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

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.tree.DefaultTreeModel;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.explorer.FileSystemNode;
import com.dogsbay.dogsbayaieditor.refactor.RefactorUi;

/**
 * Rename/move a file and rewrite every reference to it across the project.
 * Shows the plan before anything changes.
 */
public class RenameWithReferencesAction extends AbstractAction {

    private final DogsBayAIEditor parent;
    private final FileSystemNode node;
    private final DefaultTreeModel treeModel;

    public RenameWithReferencesAction(DogsBayAIEditor parent, FileSystemNode node,
                                      DefaultTreeModel treeModel) {
        super("Rename/Move with References...");
        this.parent = parent;
        this.node = node;
        this.treeModel = treeModel;
        setEnabled(node != null && node.getFile() != null && node.getFile().isFile());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (node == null) {
            return;
        }
        new RefactorUi(parent).renameFileWithReferences(node.getFile(), this::refreshParentNode);
    }

    private void refreshParentNode() {
        FileSystemNode parentNode = (FileSystemNode) node.getParent();
        if (parentNode != null) {
            parentNode.refresh(treeModel);
            treeModel.nodeStructureChanged(parentNode);
        }
    }
}
