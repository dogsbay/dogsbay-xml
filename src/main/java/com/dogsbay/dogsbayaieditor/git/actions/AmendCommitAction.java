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
import org.eclipse.jgit.revwalk.RevCommit;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Action to amend the last commit.
 * Uses the current commit message field content as the new message.
 * If the field is empty, pre-fills it with the last commit's message.
 */
public class AmendCommitAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private GitPanel gitPanel;

    public AmendCommitAction(GitPanel gitPanel) {
        super("Amend Last Commit");
        this.gitPanel = gitPanel;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gitPanel == null) return;

        Git git = gitPanel.getGit();
        if (git == null) {
            JOptionPane.showMessageDialog(gitPanel,
                "No Git repository is currently open.",
                "Amend Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            // Get the last commit message to pre-fill if needed
            String lastMessage = gitPanel.getLastCommitMessage();
            String currentMessage = gitPanel.getCommitMessage();

            if (currentMessage == null || currentMessage.trim().isEmpty()) {
                if (lastMessage != null) {
                    gitPanel.setCommitMessage(lastMessage);
                }
                JOptionPane.showMessageDialog(gitPanel,
                    "The last commit's message has been loaded.\n" +
                    "Edit it if needed, then use Amend Last Commit again to apply.",
                    "Amend — Edit Message",
                    JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // Confirm the amend
            int response = JOptionPane.showConfirmDialog(gitPanel,
                "Amend the last commit with the current message?\n\n" +
                "This rewrites the last commit. Do not amend commits\n" +
                "that have already been pushed.",
                "Amend Last Commit",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

            if (response != JOptionPane.YES_OPTION) return;

            RevCommit commit = git.commit()
                .setAmend(true)
                .setMessage(currentMessage.trim())
                .call();

            gitPanel.clearCommitMessage();
            gitPanel.refreshGitStatus();

            JOptionPane.showMessageDialog(gitPanel,
                "Commit amended successfully.\n" +
                "Commit: " + commit.getId().abbreviate(7).name(),
                "Amend Successful", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            if (DEBUG) ex.printStackTrace();
            JOptionPane.showMessageDialog(gitPanel,
                "Failed to amend commit:\n" + msg,
                "Amend Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
