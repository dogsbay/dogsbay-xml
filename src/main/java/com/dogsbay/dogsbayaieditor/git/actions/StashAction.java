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
 * Action to stash the current working directory changes.
 * Includes untracked files in the stash.
 */
public class StashAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private GitPanel gitPanel;

    public StashAction(GitPanel gitPanel) {
        super("Stash");
        this.gitPanel = gitPanel;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gitPanel == null) return;

        Git git = gitPanel.getGit();
        if (git == null) {
            JOptionPane.showMessageDialog(gitPanel,
                "No Git repository is currently open.",
                "Stash Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            RevCommit stashCommit = git.stashCreate()
                .setIncludeUntracked(true)
                .call();

            gitPanel.refreshGitStatus();

            // Files changed on disk under any tab holding them:
            // stashing reverts the stashed files in the working tree.
            gitPanel.reloadOpenBuffers();

            if (stashCommit == null) {
                JOptionPane.showMessageDialog(gitPanel,
                    "No local changes to stash.",
                    "Stash", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(gitPanel,
                    "Changes stashed successfully.\n" +
                    "Stash: " + stashCommit.getId().abbreviate(7).name(),
                    "Stash Successful", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (Exception ex) {
            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            if (DEBUG) ex.printStackTrace();
            JOptionPane.showMessageDialog(gitPanel,
                "Failed to stash changes:\n" + msg,
                "Stash Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
