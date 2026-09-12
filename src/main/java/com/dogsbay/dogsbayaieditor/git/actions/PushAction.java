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

import com.dogsbay.dogsbayaieditor.git.GitCommandRunner;
import com.dogsbay.dogsbayaieditor.git.GitCommandRunner.GitCommandResult;
import com.dogsbay.dogsbayaieditor.git.GitPanel;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * Action to push to the remote repository.
 * Uses the system git binary to support the user's existing authentication setup.
 * Runs in a background thread to avoid blocking the EDT.
 */
public class PushAction extends AbstractAction {
    private static final boolean DEBUG = false;

    private GitPanel gitPanel;

    public PushAction(GitPanel gitPanel) {
        super("Push");
        this.gitPanel = gitPanel;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gitPanel == null) return;

        Git git = gitPanel.getGit();
        if (git == null) {
            JOptionPane.showMessageDialog(gitPanel,
                "No Git repository is currently open.",
                "Push Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File repoRoot = git.getRepository().getWorkTree();
        String remoteUrl = getRemoteUrl(git);

        new SwingWorker<GitCommandResult, Void>() {
            @Override
            protected GitCommandResult doInBackground() throws Exception {
                return GitCommandRunner.runGitCommand(repoRoot, "push");
            }

            @Override
            protected void done() {
                try {
                    GitCommandResult result = get();
                    gitPanel.refreshGitStatus();

                    if (result.isSuccess()) {
                        JOptionPane.showMessageDialog(gitPanel,
                            "Push completed successfully.\n" + result.getMessage(),
                            "Push Successful", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(gitPanel,
                            "Push failed:\n" + result.getMessageWithAuthHint(remoteUrl),
                            "Push Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    if (DEBUG) ex.printStackTrace();
                    JOptionPane.showMessageDialog(gitPanel,
                        "Failed to push:\n" + msg,
                        "Push Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static String getRemoteUrl(Git git) {
        try {
            StoredConfig config = git.getRepository().getConfig();
            return config.getString("remote", "origin", "url");
        } catch (Exception e) {
            return null;
        }
    }
}
