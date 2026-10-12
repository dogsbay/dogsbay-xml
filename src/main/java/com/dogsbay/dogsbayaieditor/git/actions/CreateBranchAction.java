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
 * Action to create a new branch and check it out.
 * Shows an input dialog for the branch name.
 */
public class CreateBranchAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private GitPanel gitPanel;

    public CreateBranchAction(GitPanel gitPanel) {
        super("Create Branch...");
        this.gitPanel = gitPanel;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gitPanel == null) return;

        Git git = gitPanel.getGit();
        if (git == null) {
            JOptionPane.showMessageDialog(gitPanel,
                "No Git repository is currently open.",
                "Branch Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String branchName = JOptionPane.showInputDialog(gitPanel,
            "Enter new branch name:",
            "Create Branch",
            JOptionPane.PLAIN_MESSAGE);

        if (branchName == null || branchName.trim().isEmpty()) return;

        branchName = branchName.trim();

        try {
            // Create the branch
            git.branchCreate()
                .setName(branchName)
                .call();

            // Check it out
            git.checkout()
                .setName(branchName)
                .call();

            if (DEBUG) {
                System.out.println("CreateBranchAction: Created and checked out branch: " + branchName);
            }

            gitPanel.refreshGitStatus();

            JOptionPane.showMessageDialog(gitPanel,
                "Branch '" + branchName + "' created and checked out.",
                "Branch Created", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            if (DEBUG) ex.printStackTrace();
            JOptionPane.showMessageDialog(gitPanel,
                "Failed to create branch:\n" + msg,
                "Branch Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
