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
import org.eclipse.jgit.lib.Ref;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * Action to create a new branch from a selected base branch and check it out.
 * Shows a branch selection dialog first, then prompts for the new branch name.
 */
public class CreateBranchFromAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private GitPanel gitPanel;

    public CreateBranchFromAction(GitPanel gitPanel) {
        super("Create Branch From...");
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

        try {
            // Get all local branches
            List<Ref> branches = git.branchList().call();
            if (branches.isEmpty()) {
                JOptionPane.showMessageDialog(gitPanel,
                    "No branches found.",
                    "Create Branch From", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // Build branch name array
            String[] branchNames = branches.stream()
                .map(ref -> ref.getName().replaceFirst("^refs/heads/", ""))
                .toArray(String[]::new);

            // Select base branch
            String currentBranch = git.getRepository().getBranch();
            String baseBranch = (String) JOptionPane.showInputDialog(gitPanel,
                "Select base branch:",
                "Create Branch From",
                JOptionPane.PLAIN_MESSAGE,
                null,
                branchNames,
                currentBranch);

            if (baseBranch == null) return;

            // Prompt for new branch name
            String branchName = JOptionPane.showInputDialog(gitPanel,
                "Enter new branch name (from '" + baseBranch + "'):",
                "Create Branch From",
                JOptionPane.PLAIN_MESSAGE);

            if (branchName == null || branchName.trim().isEmpty()) return;

            branchName = branchName.trim();

            // Create the branch from the selected base
            git.branchCreate()
                .setName(branchName)
                .setStartPoint(baseBranch)
                .call();

            // Check it out
            git.checkout()
                .setName(branchName)
                .call();

            if (DEBUG) {
                System.out.println("CreateBranchFromAction: Created branch '" + branchName + "' from '" + baseBranch + "'");
            }

            gitPanel.refreshGitStatus();

            JOptionPane.showMessageDialog(gitPanel,
                "Branch '" + branchName + "' created from '" + baseBranch + "' and checked out.",
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
