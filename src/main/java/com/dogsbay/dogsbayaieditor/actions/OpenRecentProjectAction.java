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

package com.dogsbay.dogsbayaieditor.actions;

import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.AbstractAction;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;

/**
 * An action that can be used to open a recently used project/folder.
 *
 * @version $Revision: 1.0 $, $Date: 2025/12/26 $
 * @author DogsBay Ltd
 */
public class OpenRecentProjectAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private File projectDir = null;
    private DogsBayAIEditor parent = null;

    /**
     * The constructor for the open recent project action.
     *
     * @param parent the editor instance
     * @param projectPath the path to the project directory
     * @param index the menu item index (for mnemonic)
     */
    public OpenRecentProjectAction(DogsBayAIEditor parent, String projectPath, int index) {
        super(index + " " + new File(projectPath).getName());

        char[] chars = String.valueOf(index).toCharArray();
        putValue(MNEMONIC_KEY, Integer.valueOf(chars[0]));

        this.parent = parent;
        this.projectDir = new File(projectPath);
    }

    /**
     * The implementation of the open recent project action.
     *
     * @param e the action event.
     */
    public void actionPerformed(ActionEvent e) {
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            parent.setStatus("Project folder not found: " + projectDir.getAbsolutePath());
            return;
        }

        parent.setWait(true);
        parent.setStatus("Opening project...");

        // Run in Thread
        Runnable runner = new Runnable() {
            public void run() {
                try {
                    // Update file explorer
                    parent.getFileExplorer().setRootDirectory(projectDir);

                    // Refresh Git panel to detect repository in folder
                    if (parent.getGitPanel() != null) {
                        parent.getGitPanel().refreshRepository();
                    }

                    // Switch to file explorer tab
                    parent.switchToFileExplorerTab();

                    // Update recent projects (move to top)
                    parent.getProperties().setLastOpenedProject(projectDir.getAbsolutePath());
                    parent.getProperties().setLastOpenedFolder(projectDir.getAbsolutePath());
                } finally {
                    parent.setStatus("Done");
                    parent.setWait(false);
                }
            }
        };

        // Create and start the thread
        Thread thread = new Thread(runner);
        thread.start();
    }
}
