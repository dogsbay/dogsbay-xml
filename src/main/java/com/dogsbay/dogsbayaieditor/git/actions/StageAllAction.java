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

package com.dogsbay.dogsbayaieditor.git.actions;

import com.dogsbay.dogsbayaieditor.git.GitPanel;
import org.eclipse.jgit.api.Git;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Action to stage all unstaged files using Git add.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/23 $
 * @author DogsBay Ltd
 */
public class StageAllAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private GitPanel gitPanel;

    /**
     * Creates an action to stage all files.
     *
     * @param gitPanel the Git panel
     */
    public StageAllAction(GitPanel gitPanel) {
        super("Stage All");
        this.gitPanel = gitPanel;
    }

    /**
     * Stages all unstaged files using git add.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (gitPanel == null) {
            return;
        }

        try {
            Git git = gitPanel.getGit();
            if (git == null) {
                if (DEBUG) {
                    System.err.println("StageAllAction: No Git repository");
                }
                return;
            }

            if (DEBUG) {
                System.out.println("StageAllAction: Staging all files");
            }

            // Use JGit to stage all files (. means all changes)
            git.add()
                .addFilepattern(".")
                .setUpdate(false) // Include new files
                .call();

            if (DEBUG) {
                System.out.println("StageAllAction: Staged all files successfully");
            }

            // Refresh the Git status to show changes
            gitPanel.refreshGitStatus();

        } catch (Exception ex) {
            if (DEBUG) {
                System.err.println("StageAllAction: Failed to stage all files: " + ex.getMessage());
                ex.printStackTrace();
            }
            JOptionPane.showMessageDialog(
                gitPanel,
                "Failed to stage all files: " + ex.getMessage(),
                "Stage All Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
