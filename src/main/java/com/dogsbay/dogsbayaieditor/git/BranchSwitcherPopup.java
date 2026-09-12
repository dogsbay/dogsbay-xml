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

package com.dogsbay.dogsbayaieditor.git;

import com.dogsbay.dogsbayaieditor.git.actions.CreateBranchAction;
import com.dogsbay.dogsbayaieditor.git.actions.CreateBranchFromAction;
import com.dogsbay.dogsbayaieditor.git.actions.DeleteBranchAction;
import com.dogsbay.dogsbayaieditor.git.actions.MergeBranchAction;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * A popup menu that appears when clicking the branch label in the status bar.
 * Shows options to create branches and a list of all local branches for switching.
 */
public class BranchSwitcherPopup extends JPopupMenu {
    private static final boolean DEBUG = false;
    private static final int MAX_COMMIT_MSG_LENGTH = 50;

    private GitPanel gitPanel;

    public BranchSwitcherPopup(GitPanel gitPanel) {
        this.gitPanel = gitPanel;
        buildMenu();
    }

    private void buildMenu() {
        Git git = gitPanel.getGit();
        if (git == null) return;

        // Create branch actions
        JMenuItem createBranchItem = new JMenuItem("Create New Branch...");
        createBranchItem.addActionListener(e -> new CreateBranchAction(gitPanel).actionPerformed(e));
        add(createBranchItem);

        JMenuItem createFromItem = new JMenuItem("Create New Branch From...");
        createFromItem.addActionListener(e -> new CreateBranchFromAction(gitPanel).actionPerformed(e));
        add(createFromItem);

        JMenuItem mergeItem = new JMenuItem("Merge Branch...");
        mergeItem.addActionListener(e -> new MergeBranchAction(gitPanel).actionPerformed(e));
        add(mergeItem);

        JMenuItem deleteItem = new JMenuItem("Delete Branch...");
        deleteItem.addActionListener(e -> new DeleteBranchAction(gitPanel).actionPerformed(e));
        add(deleteItem);

        addSeparator();

        // Branch list
        try {
            String currentBranch = git.getRepository().getBranch();
            List<Ref> branches = git.branchList().call();

            if (branches.isEmpty()) {
                JMenuItem noBranches = new JMenuItem("No branches found");
                noBranches.setEnabled(false);
                add(noBranches);
                return;
            }

            // Add a label for the branch list section
            JMenuItem header = new JMenuItem("Switch to branch:");
            header.setEnabled(false);
            header.setFont(header.getFont().deriveFont(Font.ITALIC));
            add(header);

            for (Ref ref : branches) {
                String branchName = ref.getName().replaceFirst("^refs/heads/", "");
                boolean isCurrent = branchName.equals(currentBranch);

                String commitMsg = getLastCommitMessage(git, ref);
                String displayText = branchName;
                if (commitMsg != null && !commitMsg.isEmpty()) {
                    if (commitMsg.length() > MAX_COMMIT_MSG_LENGTH) {
                        commitMsg = commitMsg.substring(0, MAX_COMMIT_MSG_LENGTH) + "...";
                    }
                    displayText = branchName + "  —  " + commitMsg;
                }

                JMenuItem branchItem = new JMenuItem(displayText);

                if (isCurrent) {
                    branchItem.setFont(branchItem.getFont().deriveFont(Font.BOLD));
                    branchItem.setIcon(createCheckIcon());
                    branchItem.setEnabled(false);
                } else {
                    branchItem.addActionListener(e -> checkoutBranch(branchName));
                }

                add(branchItem);
            }

        } catch (Exception ex) {
            if (DEBUG) ex.printStackTrace();
            JMenuItem errorItem = new JMenuItem("Error loading branches");
            errorItem.setEnabled(false);
            add(errorItem);
        }
    }

    private String getLastCommitMessage(Git git, Ref ref) {
        try (RevWalk walk = new RevWalk(git.getRepository())) {
            ObjectId objectId = ref.getObjectId();
            if (objectId == null) return null;
            RevCommit commit = walk.parseCommit(objectId);
            return commit.getShortMessage();
        } catch (Exception ex) {
            if (DEBUG) ex.printStackTrace();
            return null;
        }
    }

    private void checkoutBranch(String branchName) {
        Git git = gitPanel.getGit();
        if (git == null) return;

        try {
            git.checkout()
                .setName(branchName)
                .call();

            gitPanel.refreshGitStatus();

        } catch (Exception ex) {
            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            if (DEBUG) ex.printStackTrace();
            JOptionPane.showMessageDialog(gitPanel,
                "Failed to switch to branch '" + branchName + "':\n" + msg,
                "Checkout Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Icon createCheckIcon() {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c.getForeground());
                g2.setStroke(new BasicStroke(2f));
                // Draw a simple checkmark
                g2.drawLine(x + 2, y + 7, x + 5, y + 10);
                g2.drawLine(x + 5, y + 10, x + 11, y + 3);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 14;
            }

            @Override
            public int getIconHeight() {
                return 14;
            }
        };
    }
}
