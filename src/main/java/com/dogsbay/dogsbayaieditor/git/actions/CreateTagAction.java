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

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import com.dogsbay.dogsbayaieditor.git.GitPanel;

/**
 * Mark the current commit with a tag.
 *
 * <p>A stage of a tutorial, a release, anything worth coming back to by name. The panel could create branches
 * and not tags, so marking a point meant the Terminal — or a branch standing in for a tag, which is a different
 * thing: a branch moves when you commit on it, and the mark of a moment should not.
 *
 * <p>Annotated by default. An annotated tag records who made it and when; a lightweight one is a name and
 * nothing else, which is fine for a private bookmark and thin for anything shared.
 */
public class CreateTagAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private final GitPanel gitPanel;

    public CreateTagAction(GitPanel gitPanel) {
        super("Create Tag...");
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
                "Create Tag", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JTextField name = new JTextField(20);
        JTextField message = new JTextField(20);
        JCheckBox annotated = new JCheckBox("Annotated (records who tagged it, and when)", true);
        message.setToolTipText("What this point is. Left empty, an annotated tag takes its own name.");

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 4));
        form.add(new JLabel("Tag name:"));
        form.add(name);
        form.add(new JLabel("Message:"));
        form.add(message);
        form.add(new JLabel(""));
        form.add(annotated);

        String where = headDescription(git);
        Object[] content = {"Tag " + where + ":", form};
        if (JOptionPane.showConfirmDialog(gitPanel, content, "Create Tag",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }

        String tag = name.getText() == null ? "" : name.getText().trim();
        if (tag.isEmpty()) {
            return;
        }
        if (existingTags(git).contains(tag)) {
            // Refused rather than moved: JGit would need --force, and a tag that quietly points somewhere new
            // is worse than one that will not be made.
            JOptionPane.showMessageDialog(gitPanel,
                "There is already a tag called '" + tag + "'.\n"
                + "Tags mark a fixed point, so this one is left where it is.",
                "Create Tag", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            String note = message.getText() == null ? "" : message.getText().trim();
            git.tag()
                .setName(tag)
                .setAnnotated(annotated.isSelected())
                .setMessage(annotated.isSelected() && !note.isEmpty() ? note : null)
                .call();
            gitPanel.refreshGitStatus();
            JOptionPane.showMessageDialog(gitPanel,
                "Tagged " + where + " as '" + tag + "'.\n"
                + "Tags are not pushed with a branch; push it when you want others to have it.",
                "Create Tag", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(gitPanel,
                "Could not create the tag: " + ex.getMessage(),
                "Create Tag", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** What is about to be tagged, in the words a reader would use: the branch, or the commit when detached. */
    private static String headDescription(Git git) {
        try {
            Repository repo = git.getRepository();
            String branch = repo.getBranch();
            if (branch == null) {
                return "this commit";
            }
            var head = repo.resolve("HEAD");
            String shortId = head == null ? "" : " (" + head.abbreviate(7).name() + ")";
            return repo.getFullBranch() != null && repo.getFullBranch().startsWith("refs/heads/")
                    ? "the tip of " + branch + shortId : "this commit" + shortId;
        } catch (Exception e) {
            return "this commit";
        }
    }

    /** The tag names already in the repository. */
    static List<String> existingTags(Git git) {
        try {
            return git.tagList().call().stream().map(CreateTagAction::shortName).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** {@code refs/tags/stage-01} as {@code stage-01}. */
    static String shortName(Ref ref) {
        String name = ref.getName();
        int at = name.lastIndexOf('/');
        return at >= 0 ? name.substring(at + 1) : name;
    }
}
