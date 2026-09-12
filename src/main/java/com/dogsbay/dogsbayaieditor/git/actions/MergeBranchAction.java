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
 * Merge another local branch into the one checked out — the step after an agent
 * has finished work on a branch and it has been reviewed.
 */
public class MergeBranchAction extends AbstractAction {

    private final GitPanel gitPanel;

    public MergeBranchAction(GitPanel gitPanel) {
        super("Merge Branch...");
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
                        "There is no other branch to merge. You are on '" + current + "'.",
                        "Merge Branch", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            String[] names = others.toArray(new String[0]);
            String selected = (String) JOptionPane.showInputDialog(gitPanel,
                    "Merge which branch into '" + current + "'?",
                    "Merge Branch", JOptionPane.PLAIN_MESSAGE, null, names, names[0]);
            if (selected == null) {
                return;
            }

            // Refuse before touching anything, and say why in words.
            String blocker = BranchOps.mergeBlocker(git, selected);
            if (blocker != null) {
                JOptionPane.showMessageDialog(gitPanel, blocker, "Merge Branch",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            BranchOps.MergeReport report = BranchOps.merge(git, selected);
            refresh();
            report(report);

        } catch (org.eclipse.jgit.api.errors.CheckoutConflictException conflict) {
            // JGit throws this rather than returning CHECKOUT_CONFLICT: the
            // merge stopped before touching anything, so there is nothing to
            // resolve and nothing to abort.
            refresh();
            JOptionPane.showMessageDialog(gitPanel,
                    "The merge would overwrite local changes to:\n\n"
                            + bullets(conflict.getConflictingPaths() == null ? List.of()
                                    : conflict.getConflictingPaths())
                            + "\nNothing was merged. Commit or stash those changes and try again.",
                    "Merge Branch", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            // A failed merge can still leave MERGE_HEAD and a written index, so
            // the panel and the open buffers have to be told either way.
            refresh();
            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            error("Failed to merge:\n" + msg);
        }
    }

    /** A merge rewrites the files it brings in, under any tab holding them. */
    private void refresh() {
        gitPanel.refreshGitStatus();
        gitPanel.reloadOpenBuffers();
    }

    /** Say what happened, in the terms the outcome deserves. */
    private void report(BranchOps.MergeReport report) {
        switch (report.outcome()) {
            case ALREADY_UP_TO_DATE -> JOptionPane.showMessageDialog(gitPanel,
                    "'" + report.into() + "' already contains '" + report.branch() + "'. Nothing to merge.",
                    "Merge Branch", JOptionPane.INFORMATION_MESSAGE);
            case FAST_FORWARD -> JOptionPane.showMessageDialog(gitPanel,
                    "Merged '" + report.branch() + "' into '" + report.into()
                            + "' (fast-forward — no merge commit was needed).",
                    "Merge Successful", JOptionPane.INFORMATION_MESSAGE);
            case MERGED -> JOptionPane.showMessageDialog(gitPanel,
                    "Merged '" + report.branch() + "' into '" + report.into() + "'.",
                    "Merge Successful", JOptionPane.INFORMATION_MESSAGE);
            case CONFLICTS -> JOptionPane.showMessageDialog(gitPanel,
                    "The merge stopped with conflicts in:\n\n" + bullets(report.conflicts())
                            + "\nThose files hold conflict markers now. Resolve them, stage them, "
                            + "and commit to finish the merge — or run \"git merge --abort\" in the "
                            + "Terminal to put things back.",
                    "Merge Conflicts", JOptionPane.WARNING_MESSAGE);
            case FAILED -> error("Git refused the merge (" + report.detail() + ") and changed nothing.");
        }
    }

    private static String bullets(List<String> paths) {
        StringBuilder sb = new StringBuilder();
        for (String path : paths.size() > 12 ? paths.subList(0, 12) : paths) {
            sb.append("  • ").append(path).append('\n');
        }
        if (paths.size() > 12) {
            sb.append("  … and ").append(paths.size() - 12).append(" more\n");
        }
        return sb.toString();
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(gitPanel, message, "Merge Error", JOptionPane.ERROR_MESSAGE);
    }
}
