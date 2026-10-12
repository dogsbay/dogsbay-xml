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
import java.awt.event.ActionEvent;
import java.io.File;
import java.net.URL;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.MessageHandler;

/**
 * Action to open a file from the file explorer in the editor.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/22 $
 * @author DogsBay Ltd
 */
public class OpenFromExplorerAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private DogsBayAIEditor parent;
    private FileSystemNode node;

    /**
     * Creates an action to open a file.
     *
     * @param parent the main editor window
     * @param node the file node to open
     */
    public OpenFromExplorerAction(DogsBayAIEditor parent, FileSystemNode node) {
        super("Open");
        this.parent = parent;
        this.node = node;

        // Only enable for files (not directories)
        setEnabled(node != null && !node.isDirectory());
    }

    /**
     * Opens the file in the editor.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (node != null && !node.isDirectory()) {
            File file = node.getFile();

            if (DEBUG) {
                System.out.println("OpenFromExplorerAction: Opening " + file.getAbsolutePath());
            }

            try {
                URL url = com.dogsbay.xml.DogsBayURLUtilities.getURLFromFile(file);
                parent.open(url, null, true);
            } catch (Exception ex) {
                MessageHandler.showError(parent,
                    "Could not open file: " + file.getName(),
                    "Open File Error");
                ex.printStackTrace();
            }
        }
    }
}
