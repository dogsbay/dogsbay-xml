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
import org.eclipse.jgit.api.ResetCommand;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Action to undo the last commit (soft reset to HEAD~1).
 * Keeps all changes staged, matching VSCode behavior.
 * Pre-fills the commit message field with the undone commit's message.
 */
public class UndoLastCommitAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private GitPanel gitPanel;

    public UndoLastCommitAction(GitPanel gitPanel) {
        super("Undo Last Commit");
        this.gitPanel = gitPanel;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gitPanel == null) return;

        Git git = gitPanel.getGit();
        if (git == null) {
            JOptionPane.showMessageDialog(gitPanel,
                "No Git repository is currently open.",
                "Undo Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            // Get the last commit message before undoing
            String lastMessage = gitPanel.getLastCommitMessage();

            int response = JOptionPane.showConfirmDialog(gitPanel,
                "Undo the last commit?\n\n" +
                "The changes will be kept staged.\n" +
                "Do not undo commits that have already been pushed.",
                "Undo Last Commit",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

            if (response != JOptionPane.YES_OPTION) return;

            // Soft reset to HEAD~1 (keeps changes staged)
            git.reset()
                .setMode(ResetCommand.ResetType.SOFT)
                .setRef("HEAD~1")
                .call();

            // Pre-fill the commit message with the undone commit's message
            if (lastMessage != null) {
                gitPanel.setCommitMessage(lastMessage);
            }

            gitPanel.refreshGitStatus();

            JOptionPane.showMessageDialog(gitPanel,
                "Last commit undone. Changes are still staged.",
                "Undo Successful", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            if (DEBUG) ex.printStackTrace();
            JOptionPane.showMessageDialog(gitPanel,
                "Failed to undo last commit:\n" + msg,
                "Undo Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
