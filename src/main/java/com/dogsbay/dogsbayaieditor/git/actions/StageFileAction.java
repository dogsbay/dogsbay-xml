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
 * Action to stage one or more files for commit using Git add.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/23 $
 * @author DogsBay Ltd
 */
public class StageFileAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private GitPanel gitPanel;

    /** The selected nodes that are not already staged. */
    private final java.util.List<GitFileNode> fileNodes = new java.util.ArrayList<>();

    /**
     * Creates an action to stage a file.
     *
     * @param gitPanel the Git panel
     * @param fileNode the file node to stage
     */
    public StageFileAction(GitPanel gitPanel, GitFileNode fileNode) {
        this(gitPanel, fileNode == null ? java.util.List.of() : java.util.List.of(fileNode));
    }

    /**
     * Creates an action to stage several files at once.
     *
     * @param gitPanel  the Git panel
     * @param nodes     the file nodes to stage
     */
    public StageFileAction(GitPanel gitPanel, java.util.List<GitFileNode> nodes) {
        super("Stage");
        this.gitPanel = gitPanel;

        if (nodes != null) {
            for (GitFileNode node : nodes) {
                // Only stage files that aren't already staged
                if (node != null && !isStaged(node)) {
                    fileNodes.add(node);
                }
            }
        }

        if (fileNodes.size() > 1) {
            putValue(NAME, "Stage " + fileNodes.size() + " Files");
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
     * Stages the files using git add.
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
                    System.err.println("StageFileAction: No Git repository");
                }
                return;
            }

            // Use JGit to stage the whole batch in one command. A deleted file needs
            // setUpdate(true) to be recorded as a removal, so those go separately.
            org.eclipse.jgit.api.AddCommand add = git.add();
            org.eclipse.jgit.api.AddCommand remove = git.add().setUpdate(true);
            boolean hasAdds = false;
            boolean hasRemovals = false;

            for (GitFileNode node : fileNodes) {
                String relativePath = node.getRelativePath();
                if (DEBUG) {
                    System.out.println("StageFileAction: Staging " + relativePath);
                }
                if (node.getStatus() == GitFileNode.GitStatus.DELETED) {
                    remove.addFilepattern(relativePath);
                    hasRemovals = true;
                } else {
                    add.addFilepattern(relativePath);
                    hasAdds = true;
                }
            }

            if (hasAdds) {
                add.call();
            }
            if (hasRemovals) {
                remove.call();
            }

            if (DEBUG) {
                System.out.println("StageFileAction: Staged successfully");
            }

            // Refresh the Git status to show changes
            gitPanel.refreshGitStatus();

        } catch (Exception ex) {
            if (DEBUG) {
                System.err.println("StageFileAction: Failed to stage file: " + ex.getMessage());
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
                "Failed to stage file: " + what + "\n" + ex.getMessage(),
                "Stage Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }
}
