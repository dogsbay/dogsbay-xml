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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * A popup menu that appears when clicking the branch label in the status bar.
 * Shows options to create branches and a list of all local branches for switching.
 */
public class BranchSwitcherPopup extends JPopupMenu {
    private static final boolean DEBUG = false;
    private static final int MAX_COMMIT_MSG_LENGTH = 50;
    /**
     * How many branches the menu will show.
     *
     * <p>A popup does not scroll, and it is drawn upward from the status bar by its own height, so a repository with
     * hundreds of remote branches would push the items off the top of the screen. The rest stay reachable in the Git
     * panel, which does scroll.
     */
    private static final int MAX_BRANCH_ITEMS = 40;

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
            // Remote branches as well as local ones. A fresh clone has one local branch, so a reader following a tutorial
            // whose stages are branches on the remote saw nothing to switch to and had to reach for git — which is the one
            // thing the switcher exists to avoid.
            Set<String> remotes = git.getRepository().getRemoteNames();
            List<Ref> branches = git.branchList()
                    .setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.ALL).call();
            branches = withoutDuplicateRemotes(branches, remotes, local -> upstreamRemoteOf(git, local));

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

            int shown = Math.min(branches.size(), MAX_BRANCH_ITEMS);
            // One walk for the whole menu rather than one per branch: this runs on the event thread at click time.
            try (RevWalk walk = new RevWalk(git.getRepository())) {
                for (Ref ref : branches.subList(0, shown)) {
                    boolean remoteOnly = ref.getName().startsWith(REMOTES);
                    String branchName = shortNameOf(ref);
                    boolean isCurrent = !remoteOnly && branchName.equals(currentBranch);

                    String commitMsg = getLastCommitMessage(walk, ref);
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
                    } else if (remoteOnly) {
                        String startPoint = ref.getName().substring(REMOTES.length());
                        String localName = withoutRemote(branchName, remotes);
                        branchItem.addActionListener(e -> checkoutRemote(branchName, startPoint, localName));
                    } else {
                        branchItem.addActionListener(e -> checkoutBranch(branchName));
                    }

                    add(branchItem);
                }
            }
            if (branches.size() > shown) {
                JMenuItem more = new JMenuItem((branches.size() - shown) + " more in the Git panel");
                more.setEnabled(false);
                add(more);
            }

        } catch (Exception ex) {
            if (DEBUG) ex.printStackTrace();
            JMenuItem errorItem = new JMenuItem("Error loading branches");
            errorItem.setEnabled(false);
            add(errorItem);
        }
    }

    private String getLastCommitMessage(RevWalk walk, Ref ref) {
        try {
            ObjectId objectId = ref.getObjectId();
            if (objectId == null) return null;
            RevCommit commit = walk.parseCommit(objectId);
            return commit.getShortMessage();
        } catch (Exception ex) {
            if (DEBUG) ex.printStackTrace();
            return null;
        }
    }

    private static final String REMOTES = "refs/remotes/";
    private static final String HEADS = "refs/heads/";

    /** A branch's name as a person reads it: {@code feature/x} locally, {@code origin/feature/x} on a remote. */
    static String shortNameOf(Ref ref) {
        String name = ref.getName();
        return name.startsWith(REMOTES) ? name.substring(REMOTES.length())
                : name.startsWith(HEADS) ? name.substring(HEADS.length()) : name;
    }

    /**
     * Drop remote branches a local branch already follows, and each remote's {@code HEAD} pointer.
     *
     * <p>Otherwise a clone with one stage checked out lists it twice, once as itself and once as the remote it came from,
     * and the second one would make a second local branch when chosen.
     *
     * <p>Sameness is judged by what the local branch tracks, not by name. Two remotes can carry a branch of the same
     * name holding different work — a fork and the repository it was forked from, which is how a reader following a
     * tutorial from their own fork sees it — and hiding one because the other is checked out would put it out of reach.
     *
     * @param branches    every branch, local and remote.
     * @param remotes     the configured remote names, which may themselves contain a slash.
     * @param upstreamOf  the remote a local branch follows, or null when it follows none.
     * @return the ones worth offering.
     */
    static List<Ref> withoutDuplicateRemotes(List<Ref> branches, Set<String> remotes,
            UnaryOperator<String> upstreamOf) {
        Set<String> local = new HashSet<>();
        for (Ref ref : branches) {
            if (ref.getName().startsWith(HEADS)) {
                local.add(shortNameOf(ref));
            }
        }
        List<Ref> kept = new ArrayList<>();
        for (Ref ref : branches) {
            if (!ref.getName().startsWith(REMOTES)) {
                kept.add(ref);
                continue;
            }
            String shortName = shortNameOf(ref);
            String remote = remoteOf(shortName, remotes);
            if (remote != null && shortName.equals(remote + "/HEAD")) {
                continue; // the remote's own pointer, not a branch anyone switches to
            }
            String localName = withoutRemote(shortName, remotes);
            if (local.contains(localName) && remote != null && remote.equals(upstreamOf.apply(localName))) {
                continue;
            }
            kept.add(ref);
        }
        return kept;
    }

    /** The configured remote a short remote-branch name belongs to, or null when none of them matches. */
    static String remoteOf(String shortName, Set<String> remotes) {
        String best = null;
        for (String remote : remotes) {
            if (shortName.startsWith(remote + "/") && (best == null || remote.length() > best.length())) {
                best = remote; // longest match: a remote may be named my/fork, and another fork
            }
        }
        return best;
    }

    /**
     * {@code origin/tutorial/01-setup} without its remote, which is the local branch it would become.
     *
     * <p>Split on the configured remote names rather than the first slash, because {@code git remote add my/fork} is
     * accepted and splitting such a name would make a local branch called {@code fork/tutorial/01-setup}.
     */
    static String withoutRemote(String shortName, Set<String> remotes) {
        String remote = remoteOf(shortName, remotes);
        return remote == null ? shortName : shortName.substring(remote.length() + 1);
    }

    /** The remote a local branch follows, read from the repository's configuration. */
    private static String upstreamRemoteOf(Git git, String localBranch) {
        return git.getRepository().getConfig().getString("branch", localBranch, "remote");
    }

    /**
     * Switch to a branch that exists only on a remote, making the local branch that follows it.
     *
     * <p>Checking out the remote name directly would leave the repository on a detached head, where the next edit belongs
     * to no branch — a state a reader moving between tutorial stages should never be put in.
     */
    void checkoutRemoteForTest(String remoteName, String startPoint, String localName) {
        checkoutRemote(remoteName, startPoint, localName);
    }

    private void checkoutRemote(String remoteName, String startPoint, String localName) {
        Git git = gitPanel.getGit();
        if (git == null) {
            return;
        }
        try {
            git.checkout()
                .setName(localName)
                .setCreateBranch(true)
                .setStartPoint(startPoint)
                .setUpstreamMode(org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.TRACK)
                .call();
            gitPanel.refreshGitStatus();
        } catch (org.eclipse.jgit.api.errors.RefAlreadyExistsException already) {
            // A local branch of that name is in the way. If it is the same commit the remote points at, it is the branch
            // the reader asked for and switching to it is right. If it is not, it is somebody else's work under a
            // borrowed name, and checking it out silently would hand back content they never asked for.
            if (sameCommit(git, localName, startPoint)) {
                checkoutBranch(localName);
            } else {
                JOptionPane.showMessageDialog(gitPanel,
                    "A local branch called '" + localName + "' already exists and is not the same as '"
                        + remoteName + "'.\nRename or delete it first, or switch to it from this menu.",
                    "Branch Already Exists", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception ex) {
            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            if (DEBUG) {
                ex.printStackTrace();
            }
            JOptionPane.showMessageDialog(gitPanel,
                "Failed to switch to '" + remoteName + "':\n" + msg,
                "Checkout Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Whether a local branch and a remote-tracking branch point at the same commit. */
    private static boolean sameCommit(Git git, String localName, String startPoint) {
        try {
            ObjectId local = git.getRepository().resolve(HEADS + localName);
            ObjectId remote = git.getRepository().resolve(REMOTES + startPoint);
            return local != null && local.equals(remote);
        } catch (Exception ex) {
            if (DEBUG) ex.printStackTrace();
            return false;
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
