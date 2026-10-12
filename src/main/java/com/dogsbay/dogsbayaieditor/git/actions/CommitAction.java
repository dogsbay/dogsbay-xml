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
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.revwalk.RevCommit;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Smart commit action (VSCode-style "Commit").
 * If there are staged changes, commits them.
 * If nothing is staged but there are modified tracked files, auto-stages them and commits.
 * If there are no changes at all, shows a warning.
 */
public class CommitAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private GitPanel gitPanel;

    public CommitAction(GitPanel gitPanel) {
        super("Commit");
        this.gitPanel = gitPanel;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gitPanel == null) return;

        try {
            Git git = gitPanel.getGit();
            if (git == null) {
                JOptionPane.showMessageDialog(gitPanel,
                    "No Git repository is currently open.",
                    "Commit Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String message = gitPanel.getCommitMessage();
            if (message == null || message.trim().isEmpty()) {
                JOptionPane.showMessageDialog(gitPanel,
                    "Please enter a commit message.",
                    "Commit Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            Status status = git.status().call();
            boolean hasStagedChanges = !status.getAdded().isEmpty()
                    || !status.getChanged().isEmpty()
                    || !status.getRemoved().isEmpty();
            boolean hasUnstagedChanges = !status.getModified().isEmpty()
                    || !status.getMissing().isEmpty();

            if (!hasStagedChanges && !hasUnstagedChanges) {
                JOptionPane.showMessageDialog(gitPanel,
                    "No changes to commit.",
                    "Nothing to Commit", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // If nothing is staged, auto-stage modified/deleted tracked files
            if (!hasStagedChanges) {
                if (DEBUG) {
                    System.out.println("CommitAction: No staged changes, auto-staging tracked files");
                }

                // Stage modified tracked files
                git.add()
                    .addFilepattern(".")
                    .setUpdate(true)  // only tracked files, not untracked
                    .call();
            }

            RevCommit commit = git.commit()
                .setMessage(message.trim())
                .call();

            if (DEBUG) {
                System.out.println("CommitAction: Committed: " + commit.getId().getName());
            }

            gitPanel.clearCommitMessage();
            gitPanel.refreshGitStatus();

            JOptionPane.showMessageDialog(gitPanel,
                "Changes committed successfully.\n" +
                "Commit: " + commit.getId().abbreviate(7).name(),
                "Commit Successful", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            if (DEBUG) ex.printStackTrace();
            JOptionPane.showMessageDialog(gitPanel,
                "Failed to commit changes:\n" + ex.getMessage(),
                "Commit Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
