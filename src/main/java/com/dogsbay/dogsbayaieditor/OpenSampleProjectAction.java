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

package com.dogsbay.dogsbayaieditor;

import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import com.dogsbay.dogsbayaieditor.samples.SampleProject;

/**
 * "Open Sample Project" — copies the bundled Audacity DITA sample into a fresh subfolder
 * of a location the user chooses, and opens it. The subfolder is named {@code audacity-demo}
 * by default (the user may rename it in the Folder-name box) and always lands in its own
 * new folder — never the chosen folder itself, and never the home folder — with a unique
 * name (a collision bumps to {@code -2}, {@code -3}, …) so nothing existing is overwritten.
 * Opening runs the project auto-setup, so the Map Explorer loads {@code audacity-guide.ditamap}
 * and publishing is wired to {@code DITA-OT 4.3.5} with no manual configuration.
 *
 * <p>The sample is copied (not opened in place) so the user can edit it freely.
 */
public class OpenSampleProjectAction extends AbstractAction {

    private final DogsBayAIEditor editor;

    public OpenSampleProjectAction(DogsBayAIEditor editor) {
        super("Open Sample Project...");
        this.editor = editor;
        putValue(SHORT_DESCRIPTION, "Copy the bundled Audacity DITA sample and open it");
        setEnabled(SampleProject.isAvailable());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!SampleProject.isAvailable()) {
            JOptionPane.showMessageDialog(editor,
                    "This build does not include the sample project.",
                    "Sample Project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // The File menu greys this out when the sample is already open, but the Welcome
        // tab / empty-explorer buttons call actionPerformed directly — guard here too.
        File openRoot = editor.getFileExplorer() != null
                ? editor.getFileExplorer().getRootDirectory() : null;
        if (SampleProject.isSampleFolder(openRoot)) {
            JOptionPane.showMessageDialog(editor,
                    "The sample project is already open.",
                    "Sample Project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Choose WHERE to create the sample. It always lands in a fresh "audacity-demo"
        // subfolder of the chosen location — never the chosen folder itself — so picking
        // your home folder (the chooser's default before anything has been opened, and
        // what a DIRECTORIES_ONLY chooser returns if you just click the approve button)
        // can never turn home into the project root.
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose a location for the sample project");

        File base = null;
        String last = editor.getProperties().getLastOpenedFolder();
        if (last != null && !last.isEmpty()) {
            File lastDir = new File(last);
            base = lastDir.isDirectory() ? lastDir : lastDir.getParentFile();
        }
        if (base == null || !base.isDirectory()) {
            base = new File(System.getProperty("user.home"));
        }
        // Open IN base (showing its contents) and put "audacity-demo" in the Folder name
        // box so it's obvious what gets created. setFileName() sets the field text directly
        // (setSelectedFile would show the resolved path instead); the install always
        // creates a fresh subfolder regardless, so this is just a clear hint.
        chooser.setCurrentDirectory(base);
        Runnable prefillName = () -> {
            if (chooser.getUI() instanceof javax.swing.plaf.basic.BasicFileChooserUI fcUi) {
                fcUi.setFileName(SampleProject.NAME);
            }
        };
        prefillName.run();
        // Re-apply once the dialog is realized (some look-and-feels clear the field on show).
        javax.swing.SwingUtilities.invokeLater(prefillName);

        if (chooser.showDialog(editor, "Create Sample Here") != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File dest = resolveSampleDest(
                chooser.getSelectedFile(), chooser.getCurrentDirectory(), SampleProject.NAME);

        try {
            SampleProject.install(dest, false);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(editor,
                    "Could not create the sample project:\n" + ex.getMessage(),
                    "Sample Project", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Open the copy immediately (responsive) — triggers the project auto-setup
        // (type/root map/framework) and loads the deliverable context.
        editor.getOpenFolderAction().openFolder(dest);
        editor.setStatus("Sample project ready — " + dest.getName());

        // Make it a real git repo (so the user can experiment and revert, and the
        // branch indicator + Git panel light up) — in the background so the commit
        // can't freeze the UI.
        gitInitAsync(dest);
    }

    /**
     * Initialize {@code dir} as a git repo with an initial commit, off the EDT.
     * Best-effort: on failure after init, removes the half-created {@code .git} so a
     * later open can retry; refreshes the Git panel on success.
     */
    private void gitInitAsync(File dir) {
        if (new File(dir, ".git").exists()) {
            return; // already a repo (e.g. re-opened)
        }
        new Thread(() -> {
            boolean createdRepo = false;
            try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.init().setDirectory(dir).call()) {
                createdRepo = true;
                git.add().addFilepattern(".").call();
                try {
                    // Prefer the user's configured git identity (consistent with the
                    // editor's normal commits); fall back to a default so the seed
                    // commit still succeeds when no identity is configured.
                    git.commit().setMessage("Initial sample project").call();
                } catch (Exception noIdentity) {
                    git.commit().setMessage("Initial sample project")
                            .setAuthor("DogsBay XML", "sample@dogsbay.local")
                            .setCommitter("DogsBay XML", "sample@dogsbay.local")
                            .call();
                }
                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (editor.getGitPanel() != null) {
                        editor.getGitPanel().refreshRepository();
                    }
                });
            } catch (Exception e) {
                if (createdRepo) {
                    deleteRecursively(new File(dir, ".git")); // don't leave a half-init repo
                }
            }
        }, "sample-git-init").start();
    }

    /**
     * The fresh, unique destination folder for the sample, from the chooser's selection.
     * Always a NEW subfolder of a location — never the location itself — so it can never be
     * the home folder or a filesystem root, and never overwrites or nests inside an existing
     * copy (a name collision bumps to {@code -2}, {@code -3}, …, as a SIBLING).
     *
     * <p>The folder is named after the Folder-name box: the pre-filled {@code defaultName}
     * ("audacity-demo"), or whatever the user typed. When the box was cleared (the chooser
     * returns the shown directory, or null) the default name is used in that directory.
     */
    static File resolveSampleDest(File selected, File currentDir, String defaultName) {
        File parent;
        String name;
        if (selected == null || sameLocation(selected, currentDir)) {
            // No explicit name → create the default folder in the shown location.
            parent = currentDir;
            name = defaultName;
        } else {
            // The selection is "<parent>/<name>" the user accepted or typed.
            parent = selected.getParentFile();
            name = selected.getName();
        }
        if (parent == null || !parent.isDirectory()) {
            // Don't materialize unexpected intermediate folders for an odd typed path.
            parent = (currentDir != null && currentDir.isDirectory())
                    ? currentDir : new File(System.getProperty("user.home"));
        }
        if (name == null || name.isBlank()) {
            name = defaultName;
        }
        return uniqueChild(parent, name);
    }

    private static boolean sameLocation(File a, File b) {
        return a != null && b != null && a.getAbsoluteFile().equals(b.getAbsoluteFile());
    }

    /**
     * A non-existent child folder of {@code parent}: {@code name}, else {@code name-2},
     * {@code name-3}, … The sample always goes into a fresh subfolder, so an existing
     * copy (or any other files in {@code parent}) is never touched.
     */
    static File uniqueChild(File parent, String name) {
        File f = new File(parent, name);
        if (!f.exists()) {
            return f;
        }
        for (int i = 2; i < 1000; i++) {
            File candidate = new File(parent, name + "-" + i);
            if (!candidate.exists()) {
                return candidate;
            }
        }
        return new File(parent, name + "-" + System.currentTimeMillis());
    }

    private static void deleteRecursively(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteRecursively(k);
            }
        }
        f.delete();
    }
}
