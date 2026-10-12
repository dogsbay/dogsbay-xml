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
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.eclipse.jgit.api.Git;

import com.dogsbay.dogsbayaieditor.git.GitPanel;

/**
 * The tags in this repository.
 *
 * <p>A tag nobody can see afterwards is half a feature: Create Tag… without this is a command whose result
 * only the Terminal can show. Read-only on purpose — deleting a tag is a different decision, and one nobody
 * has asked for.
 */
public class ListTagsAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final GitPanel gitPanel;

    public ListTagsAction(GitPanel gitPanel) {
        super("Tags...");
        this.gitPanel = gitPanel;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gitPanel == null) {
            return;
        }
        Git git = gitPanel.getGit();
        if (git == null) {
            JOptionPane.showMessageDialog(gitPanel, "No Git repository is currently open.",
                "Tags", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<String> tags = CreateTagAction.existingTags(git);
        if (tags.isEmpty()) {
            JOptionPane.showMessageDialog(gitPanel,
                "This repository has no tags yet.\nCreate one to mark a point worth coming back to.",
                "Tags", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JTextArea area = new JTextArea(String.join("\n", tags), Math.min(tags.size() + 1, 16), 40);
        area.setEditable(false);
        area.setCaretPosition(0);
        JOptionPane.showMessageDialog(gitPanel, new JScrollPane(area),
            tags.size() + (tags.size() == 1 ? " tag" : " tags"), JOptionPane.PLAIN_MESSAGE);
    }
}
