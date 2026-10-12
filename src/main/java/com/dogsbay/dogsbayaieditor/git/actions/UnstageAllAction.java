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
 * Action to unstage all staged files using Git reset.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/23 $
 * @author DogsBay Ltd
 */
public class UnstageAllAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private GitPanel gitPanel;

    /**
     * Creates an action to unstage all files.
     *
     * @param gitPanel the Git panel
     */
    public UnstageAllAction(GitPanel gitPanel) {
        super("Unstage All");
        this.gitPanel = gitPanel;
    }

    /**
     * Unstages all staged files using git reset.
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
                    System.err.println("UnstageAllAction: No Git repository");
                }
                return;
            }

            if (DEBUG) {
                System.out.println("UnstageAllAction: Unstaging all files");
            }

            // Use JGit to unstage all files (reset without paths = reset index)
            git.reset()
                .call();

            if (DEBUG) {
                System.out.println("UnstageAllAction: Unstaged all files successfully");
            }

            // Refresh the Git status to show changes
            gitPanel.refreshGitStatus();

        } catch (Exception ex) {
            if (DEBUG) {
                System.err.println("UnstageAllAction: Failed to unstage all files: " + ex.getMessage());
                ex.printStackTrace();
            }
            JOptionPane.showMessageDialog(
                gitPanel,
                "Failed to unstage all files: " + ex.getMessage(),
                "Unstage All Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
