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
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.io.File;

import com.dogsbay.dogsbayaieditor.MessageHandler;

/**
 * Action to reveal a file or directory in the system file manager.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/22 $
 * @author DogsBay Ltd
 */
public class RevealInSystemAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private FileSystemNode node;

    /**
     * Creates an action to reveal a file/directory in the system.
     *
     * @param node the file system node to reveal
     */
    public RevealInSystemAction(FileSystemNode node) {
        super("Reveal in System");
        this.node = node;

        // Enable only if Desktop is supported
        setEnabled(node != null && Desktop.isDesktopSupported());
    }

    /**
     * Opens the file/directory in the system file manager.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (node != null && Desktop.isDesktopSupported()) {
            try {
                File file = node.getFile();

                if (DEBUG) {
                    System.out.println("RevealInSystemAction: Revealing " + file.getAbsolutePath());
                }

                // Open the parent directory if it's a file, otherwise open the directory itself
                File dirToOpen = file.isDirectory() ? file : file.getParentFile();

                if (dirToOpen != null && dirToOpen.exists()) {
                    Desktop.getDesktop().open(dirToOpen);
                }
            } catch (Exception ex) {
                MessageHandler.showError("Could not reveal file in system: " + ex.getMessage(),
                    "Reveal Error");
                ex.printStackTrace();
            }
        }
    }
}
