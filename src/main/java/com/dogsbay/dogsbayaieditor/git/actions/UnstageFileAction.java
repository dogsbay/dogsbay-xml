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
import org.eclipse.jgit.api.Git;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Action to unstage one or more files using Git reset.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/23 $
 * @author DogsBay Ltd
 */
public class UnstageFileAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private GitPanel gitPanel;

    /** The selected nodes that are actually staged. */
    private final java.util.List<GitFileNode> fileNodes = new java.util.ArrayList<>();

    /**
     * Creates an action to unstage a file.
     *
     * @param gitPanel the Git panel
     * @param fileNode the file node to unstage
     */
    public UnstageFileAction(GitPanel gitPanel, GitFileNode fileNode) {
        this(gitPanel, fileNode == null ? java.util.List.of() : java.util.List.of(fileNode));
    }

    /**
     * Creates an action to unstage several files at once.
     *
     * @param gitPanel the Git panel
     * @param nodes    the file nodes to unstage
     */
    public UnstageFileAction(GitPanel gitPanel, java.util.List<GitFileNode> nodes) {
        super("Unstage");
        this.gitPanel = gitPanel;

        if (nodes != null) {
            for (GitFileNode node : nodes) {
                // Only unstage files that are actually staged
                if (node != null && isStaged(node)) {
                    fileNodes.add(node);
                }
            }
        }

        if (fileNodes.size() > 1) {
            putValue(NAME, "Unstage " + fileNodes.size() + " Files");
        }

        setEnabled(!fileNodes.isEmpty());
    }

    /**
     * Checks if a file is staged.
     */
    private boolean isStaged(GitFileNode node) {
        GitFileNode.GitStatus status = node.getStatus();
        return status == GitFileNode.GitStatus.ADDED ||
               status == GitFileNode.GitStatus.STAGED ||
               status == GitFileNode.GitStatus.REMOVED;
    }

    /**
     * Unstages the files using git reset.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (fileNodes.isEmpty() || gitPanel == null) {
            return;
        }

        try {
            Git git = gitPanel.getGit();
            if (git == null) {
                if (DEBUG) {
                    System.err.println("UnstageFileAction: No Git repository");
                }
                return;
            }

            // Use JGit to unstage the whole batch in one command
            org.eclipse.jgit.api.ResetCommand reset = git.reset();
            for (GitFileNode node : fileNodes) {
                if (DEBUG) {
                    System.out.println("UnstageFileAction: Unstaging " + node.getRelativePath());
                }
                reset.addPath(node.getRelativePath());
            }
            reset.call();

            if (DEBUG) {
                System.out.println("UnstageFileAction: Unstaged successfully");
            }

            // Refresh the Git status to show changes
            gitPanel.refreshGitStatus();

        } catch (Exception ex) {
            if (DEBUG) {
                System.err.println("UnstageFileAction: Failed to unstage file: " + ex.getMessage());
                ex.printStackTrace();
            }
            String what = fileNodes.size() == 1
                    ? fileNodes.get(0).getFile().getName()
                    : fileNodes.size() + " files";

            // The batch is not atomic; refresh so the tree reflects whatever did succeed
            // before the failure.
            gitPanel.refreshGitStatus();

            JOptionPane.showMessageDialog(
                gitPanel,
                "Failed to unstage file: " + what + "\n" + ex.getMessage(),
                "Unstage Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
