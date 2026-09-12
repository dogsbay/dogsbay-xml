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

import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;

import org.eclipse.jgit.api.Git;

import com.dogsbay.dogsbayaieditor.git.BranchOps;
import com.dogsbay.dogsbayaieditor.git.GitPanel;

/**
 * Delete a local branch — usually the one whose work has just been merged.
 * A branch whose commits are nowhere else takes a second, blunter confirmation.
 */
public class DeleteBranchAction extends AbstractAction {

    private final GitPanel gitPanel;

    public DeleteBranchAction(GitPanel gitPanel) {
        super("Delete Branch...");
        this.gitPanel = gitPanel;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gitPanel == null) {
            return;
        }
        Git git = gitPanel.getGit();
        if (git == null) {
            error("No Git repository is currently open.");
            return;
        }

        try {
            String current = git.getRepository().getBranch();
            List<String> others = BranchOps.localBranches(git).stream()
                    .filter(name -> !name.equals(current)).toList();
            if (others.isEmpty()) {
                JOptionPane.showMessageDialog(gitPanel,
                        "There is no other branch to delete. You are on '" + current + "'.",
                        "Delete Branch", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            String[] names = others.toArray(new String[0]);
            String selected = (String) JOptionPane.showInputDialog(gitPanel,
                    "Delete which branch?\n(you are on '" + current + "')",
                    "Delete Branch", JOptionPane.PLAIN_MESSAGE, null, names, names[0]);
            if (selected == null) {
                return;
            }

            String blocker = BranchOps.deleteBlocker(git, selected);
            if (blocker != null) {
                JOptionPane.showMessageDialog(gitPanel, blocker, "Delete Branch",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            boolean merged = BranchOps.isMerged(git, selected);
            String question = merged
                    ? "Delete branch '" + selected + "'?\n\nIts commits are already in '" + current
                            + "', so nothing is lost."
                    : "Branch '" + selected + "' has commits that are not in '" + current
                            + "'.\n\nDeleting it loses that work. Delete it anyway?";
            int answer = JOptionPane.showConfirmDialog(gitPanel, question, "Delete Branch",
                    JOptionPane.OK_CANCEL_OPTION,
                    merged ? JOptionPane.QUESTION_MESSAGE : JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) {
                return;
            }

            BranchOps.delete(git, selected, !merged);
            gitPanel.refreshGitStatus();
            JOptionPane.showMessageDialog(gitPanel, "Deleted branch '" + selected + "'.",
                    "Delete Branch", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            error("Failed to delete the branch:\n" + msg);
        }
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(gitPanel, message, "Delete Error", JOptionPane.ERROR_MESSAGE);
    }
}
