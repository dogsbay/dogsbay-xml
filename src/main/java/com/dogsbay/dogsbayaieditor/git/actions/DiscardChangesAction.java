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

import com.dogsbay.dogsbayaieditor.git.GitFileNode;
import com.dogsbay.dogsbayaieditor.git.GitPanel;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Action to discard changes to one or more files. This is a destructive
 * operation and requires confirmation.
 *
 * <p>Two different things are being undone here, and the dialog says which is
 * which. A tracked file goes back to HEAD. A new file has no committed version
 * to go back to, so discarding it means deleting it — what {@code git clean}
 * does. Refusing to touch new files at all left no way to get rid of one from
 * the editor, which is why they are now included, named separately, and never
 * removed without being listed first.
 *
 * <p>Conflicted files and staged deletions are still left alone: they are
 * tracked and committed, and deleting one would destroy content.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/23 $
 * @author DogsBay Ltd
 */
public class DiscardChangesAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private GitPanel gitPanel;

    /** Selected nodes that go back to their committed version. */
    private final List<GitFileNode> revertable = new ArrayList<>();

    /** Selected nodes that were never committed, so discarding means deleting. */
    private final List<GitFileNode> deletable = new ArrayList<>();

    /** Selected nodes this action will not touch at all. */
    private final List<GitFileNode> skipped = new ArrayList<>();

    /**
     * Creates an action to discard changes to a single file.
     *
     * @param gitPanel the Git panel
     * @param fileNode the file node to discard changes for
     */
    public DiscardChangesAction(GitPanel gitPanel, GitFileNode fileNode) {
        this(gitPanel, fileNode == null ? List.of() : List.of(fileNode));
    }

    /**
     * Creates an action to discard changes to several files at once.
     *
     * @param gitPanel  the Git panel
     * @param fileNodes the file nodes to discard changes for
     */
    public DiscardChangesAction(GitPanel gitPanel, List<GitFileNode> fileNodes) {
        super("Discard Changes...");
        this.gitPanel = gitPanel;

        if (fileNodes != null) {
            for (GitFileNode node : fileNodes) {
                if (node == null) {
                    continue;
                }
                if (canRevert(node)) {
                    revertable.add(node);
                } else if (node.getStatus() == GitFileNode.GitStatus.UNTRACKED) {
                    deletable.add(node);
                } else {
                    skipped.add(node);
                }
            }
        }

        putValue(NAME, label());
        setEnabled(!revertable.isEmpty() || !deletable.isEmpty());
    }

    /**
     * The menu text. Deleting a new file is not "discarding changes", so when
     * that is all this would do, it says so instead.
     */
    private String label() {
        int total = revertable.size() + deletable.size();
        if (revertable.isEmpty() && !deletable.isEmpty()) {
            return deletable.size() == 1 ? "Delete New File..."
                    : "Delete New Files (" + deletable.size() + ")...";
        }
        return total > 1 ? "Discard Changes (" + total + " Files)..." : "Discard Changes...";
    }

    /** True when the file has a committed version to go back to. */
    private boolean canRevert(GitFileNode node) {
        GitFileNode.GitStatus status = node.getStatus();
        return status == GitFileNode.GitStatus.MODIFIED ||
               status == GitFileNode.GitStatus.DELETED ||
               status == GitFileNode.GitStatus.STAGED;
    }

    /**
     * The files this action will put back to their committed version.
     *
     * @return the revertable file nodes
     */
    public List<GitFileNode> getRevertedFiles() {
        return List.copyOf(revertable);
    }

    /**
     * The new files this action will delete, having no committed version to
     * return them to.
     *
     * @return the deletable file nodes
     */
    public List<GitFileNode> getDeletedFiles() {
        return List.copyOf(deletable);
    }

    /**
     * The selected files this action will not touch — conflicted ones and staged
     * deletions, which are tracked and committed.
     *
     * @return the skipped file nodes
     */
    public List<GitFileNode> getSkippedFiles() {
        return List.copyOf(skipped);
    }

    /**
     * Builds the confirmation message. Reverting and deleting are listed apart,
     * because they are not the same promise: one puts a file back, the other
     * takes it away for good.
     */
    String buildConfirmationMessage() {
        StringBuilder sb = new StringBuilder();

        if (revertable.isEmpty() && deletable.size() == 1) {
            sb.append("Are you sure you want to delete the new file:\n")
              .append(deletable.get(0).getRelativePath())
              .append("\n\nIt has never been committed, so there is no version to go back to.\n");
        } else if (deletable.isEmpty() && revertable.size() == 1) {
            sb.append("Are you sure you want to discard all changes to:\n")
              .append(revertable.get(0).getFile().getName())
              .append("\n");
        } else {
            int total = revertable.size() + deletable.size();
            sb.append("Are you sure you want to discard all changes to ")
              .append(total)
              .append(total == 1 ? " file?\n" : " files?\n");
            if (!revertable.isEmpty()) {
                sb.append("\nBack to the last commit:\n");
                appendList(sb, revertable, 10);
            }
            if (!deletable.isEmpty()) {
                // Every one of them, however many: a revert can be recovered
                // from the last commit, a deletion cannot be recovered at all,
                // so "and 15 more" is not good enough for this half of the list.
                sb.append("\nDeleted (new, never committed):\n");
                appendList(sb, deletable, Integer.MAX_VALUE);
            }
        }

        if (!skipped.isEmpty()) {
            sb.append("\n")
              .append(skipped.size())
              .append(skipped.size() == 1 ? " selected file cannot be reverted"
                                          : " selected files cannot be reverted")
              .append(" and will be left alone.\n");
        }

        sb.append("\nThis operation cannot be undone!");
        return sb.toString();
    }

    /** Names up to {@code limit} files, then says how many more there are. */
    private static void appendList(StringBuilder sb, List<GitFileNode> nodes, int limit) {
        int shown = Math.min(nodes.size(), limit);
        for (int i = 0; i < shown; i++) {
            sb.append("  ").append(nodes.get(i).getRelativePath()).append("\n");
        }
        if (nodes.size() > shown) {
            sb.append("  ... and ").append(nodes.size() - shown).append(" more\n");
        }
    }

    /**
     * Discards the selected files: tracked ones go back to HEAD, new ones are
     * deleted.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (!isEnabled() || gitPanel == null) {
            return;
        }

        // Show confirmation dialog (this is destructive!)
        int response = JOptionPane.showConfirmDialog(
            gitPanel,
            buildConfirmationMessage(),
            "Discard Changes",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );

        if (response != JOptionPane.YES_OPTION) {
            return; // User cancelled
        }

        performDiscard();
    }

    /**
     * Reverts the discardable files to HEAD, without confirming first.
     *
     * <p>Separated from {@link #actionPerformed} so the revert itself can be exercised
     * without a modal dialog. UI callers should go through the action so the user is
     * always asked before anything destructive happens.
     */
    public void performDiscard() {
        if (gitPanel == null || (revertable.isEmpty() && deletable.isEmpty())) {
            return;
        }

        try {
            Git git = gitPanel.getGit();
            if (git == null) {
                if (DEBUG) {
                    System.err.println("DiscardChangesAction: No Git repository");
                }
                return;
            }

            // Use JGit to discard changes for the whole batch in one command rather than
            // a round trip per file.
            //
            // setStartPoint("HEAD") matters: checkout by path with no start point is
            // `git checkout -- <path>`, which restores from the INDEX. For a staged file
            // that would be a silent no-op, and for a staged-then-edited file it would
            // restore the staged version — neither is "discard all changes to this file",
            // which is what the confirmation promises.
            if (!revertable.isEmpty()) {
                CheckoutCommand checkout = git.checkout().setStartPoint(Constants.HEAD);
                for (GitFileNode node : revertable) {
                    if (DEBUG) {
                        System.out.println("DiscardChangesAction: Discarding changes to "
                                + node.getRelativePath());
                    }
                    checkout.addPath(node.getRelativePath());
                }
                checkout.call();
            }

            // New files have nothing to check out; discarding one means removing
            // it. Done after the reverts so a failure there stops short of any
            // deletion.
            List<String> undeletable = new ArrayList<>();
            for (GitFileNode node : deletable) {
                try {
                    java.nio.file.Files.deleteIfExists(node.getFile().toPath());
                } catch (java.io.IOException io) {
                    undeletable.add(node.getRelativePath() + " (" + io.getMessage() + ")");
                }
            }

            if (DEBUG) {
                System.out.println("DiscardChangesAction: Changes discarded successfully");
            }

            // Refresh the Git status to show changes
            gitPanel.refreshGitStatus();

            // The files on disk just changed under any tab holding them.
            gitPanel.reloadOpenBuffers();

            if (!undeletable.isEmpty()) {
                JOptionPane.showMessageDialog(gitPanel,
                        "These new files could not be deleted:\n\n  "
                                + String.join("\n  ", undeletable),
                        "Discard Changes", JOptionPane.WARNING_MESSAGE);
            }

        } catch (Exception ex) {
            if (DEBUG) {
                System.err.println("DiscardChangesAction: Failed to discard changes: " + ex.getMessage());
                ex.printStackTrace();
            }

            // A batched checkout is not atomic: JGit can revert some paths and then fail
            // on a later one. Refresh so the tree stops showing files whose changes are
            // already gone, and don't claim nothing happened. Whatever was reverted before
            // the failure is on disk, so open buffers need telling either way.
            gitPanel.refreshGitStatus();
            gitPanel.reloadOpenBuffers();

            String message;
            if (revertable.isEmpty()) {
                message = "Failed to discard the selected changes.\n\nCheck the refreshed "
                        + "Changes list to see what happened.\n\n" + ex.getMessage();
            } else if (revertable.size() == 1) {
                message = "Failed to discard changes to: "
                        + revertable.get(0).getFile().getName() + "\n" + ex.getMessage();
            } else {
                message = "Failed to discard changes to all " + revertable.size()
                        + " files.\n\nSome files may already have been reverted; check the\n"
                        + "refreshed Changes list.\n\n" + ex.getMessage();
            }
            JOptionPane.showMessageDialog(
                gitPanel,
                message,
                "Discard Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
