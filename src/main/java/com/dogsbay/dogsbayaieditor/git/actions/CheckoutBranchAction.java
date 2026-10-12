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
 * Action to checkout an existing local branch.
 * Shows a selection dialog listing all local branches.
 */
public class CheckoutBranchAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private GitPanel gitPanel;

    public CheckoutBranchAction(GitPanel gitPanel) {
        super("Checkout Branch...");
        this.gitPanel = gitPanel;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gitPanel == null) return;

        Git git = gitPanel.getGit();
        if (git == null) {
            JOptionPane.showMessageDialog(gitPanel,
                "No Git repository is currently open.",
                "Checkout Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            // Get all local branches
            List<Ref> branches = git.branchList().call();
            if (branches.isEmpty()) {
                JOptionPane.showMessageDialog(gitPanel,
                    "No branches found.",
                    "Checkout Branch", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // Get current branch to exclude from list
            String currentBranch = git.getRepository().getBranch();

            // Build branch name array (strip refs/heads/ prefix)
            String[] branchNames = branches.stream()
                .map(ref -> ref.getName().replaceFirst("^refs/heads/", ""))
                .filter(name -> !name.equals(currentBranch))
                .toArray(String[]::new);

            if (branchNames.length == 0) {
                JOptionPane.showMessageDialog(gitPanel,
                    "No other branches available. Currently on '" + currentBranch + "'.",
                    "Checkout Branch", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            String selected = (String) JOptionPane.showInputDialog(gitPanel,
                "Select branch to checkout:\n(current: " + currentBranch + ")",
                "Checkout Branch",
                JOptionPane.PLAIN_MESSAGE,
                null,
                branchNames,
                branchNames[0]);

            if (selected == null) return;

            git.checkout()
                .setName(selected)
                .call();

            if (DEBUG) {
                System.out.println("CheckoutBranchAction: Checked out branch: " + selected);
            }

            gitPanel.refreshGitStatus();

            // Files changed on disk under any tab holding them:
            // a branch checkout rewrites every file that differs between the branches.
            gitPanel.reloadOpenBuffers();

            JOptionPane.showMessageDialog(gitPanel,
                "Switched to branch '" + selected + "'.",
                "Checkout Successful", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            if (DEBUG) ex.printStackTrace();
            JOptionPane.showMessageDialog(gitPanel,
                "Failed to checkout branch:\n" + msg,
                "Checkout Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
