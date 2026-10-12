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
import java.io.File;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.explorer.FileImport;
import com.dogsbay.dogsbayaieditor.explorer.FileSystemNode;

/**
 * Copy files from anywhere on the machine into a folder of the project.
 *
 * <p>Content arrives from outside — a colleague's topic, images for a task, a schema. Until now that meant
 * Reveal in System and a file manager, which is a trip out of the editor to do something the project tree is
 * looking straight at.
 */
public class AddFilesAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final DogsBayAIEditor parent;
    private final FileSystemNode node;
    private final DefaultTreeModel treeModel;
    private final JTree tree;

    public AddFilesAction(DogsBayAIEditor parent, FileSystemNode node, DefaultTreeModel treeModel, JTree tree) {
        super("Add Files...");
        this.parent = parent;
        this.node = node;
        this.treeModel = treeModel;
        this.tree = tree;
        putValue(SHORT_DESCRIPTION, "Copy files from elsewhere into this folder");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        File folder = node == null ? null : node.getFile();
        if (folder == null || !folder.isDirectory()) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Add files to " + folder.getName());
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        if (chooser.showDialog(parent, "Add") != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File[] chosen = chooser.getSelectedFiles();
        if (chosen == null || chosen.length == 0) {
            // A chooser in multi-selection mode answers with an empty array when the name was typed rather
            // than a row picked, and the action would then do nothing without saying why.
            File single = chooser.getSelectedFile();
            if (single == null) {
                return;
            }
            chosen = new File[] {single};
        }
        FileImport.copyIntoLater(parent, List.of(chosen), node, treeModel, tree);
    }
}
