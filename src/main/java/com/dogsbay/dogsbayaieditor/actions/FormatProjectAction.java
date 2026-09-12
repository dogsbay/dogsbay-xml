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

package com.dogsbay.dogsbayaieditor.actions;

import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.commands.CommandExecutor;
import com.dogsbay.dogsbayaieditor.commands.FormatCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.ReflowCommand;
import com.dogsbay.dogsbayaieditor.commands.SetContentCommand;
import com.dogsbay.dogsbayaieditor.commands.results.FormatResult;
import com.dogsbay.dogsbayaieditor.ditaproject.FileSet;
import com.dogsbay.xml.DogsBayDocument;

/**
 * Project-wide <b>Format</b> / <b>Reflow</b>. Walks every XML file under the open
 * project root and formats it to the resolved house style — or, in reflow mode,
 * reflows prose to one sentence per line and then formats — using the same engine the
 * per-file Format/Reflow and the CLI use. Runs off the EDT; open, <em>unmodified</em>
 * buffers are refreshed from disk afterward so the editor matches (unsaved buffers are
 * left untouched).
 */
public class FormatProjectAction extends AbstractAction {

    private final DogsBayAIEditor editor;
    private final boolean reflow;

    public FormatProjectAction(DogsBayAIEditor editor, boolean reflow) {
        super(reflow ? "Reflow Project" : "Format Project");
        this.editor = editor;
        this.reflow = reflow;
        putValue(SHORT_DESCRIPTION, reflow
                ? "Reflow prose to one sentence per line and format every file in the project"
                : "Format every file in the project to the house style");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        File rootDir = (editor.getFileExplorer() != null)
                ? editor.getFileExplorer().getRootDirectory() : null;
        if (rootDir == null || !rootDir.isDirectory()) {
            JOptionPane.showMessageDialog(editor,
                    "Open a project folder first (File → Open Folder).",
                    (String) getValue(NAME), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        final Path root = rootDir.toPath();
        final String verb = reflow ? "Reflowing" : "Formatting";
        editor.setWait(true);
        editor.setStatus(verb + " project \"" + rootDir.getName() + "\" ...");

        new SwingWorker<int[], Void>() {
            private final List<Path> changed = new ArrayList<>();

            @Override
            protected int[] doInBackground() throws Exception {
                List<Path> files = FileSet.underRoot(root);
                HeadlessExecutor ex = new HeadlessExecutor();
                int reformatted = 0;
                int failed = 0;
                for (Path f : files) {
                    try {
                        FormatResult r = reflow
                                ? ex.execute(new ReflowCommand(f, f))
                                : ex.execute(new FormatCommand(f, null, f));
                        if (r.modified()) {
                            reformatted++;
                            changed.add(f);
                        }
                    } catch (Exception perFile) {
                        failed++;   // not well-formed / unreadable → skip, keep going
                    }
                }
                return new int[] { files.size(), reformatted, failed };
            }

            @Override
            protected void done() {
                int[] r;
                try {
                    r = get();
                } catch (Exception ex) {
                    editor.setStatus("Project " + (reflow ? "reflow" : "format") + " failed: " + ex.getMessage());
                    editor.setWait(false);
                    return;
                }
                refreshOpenBuffers(changed);
                int unchanged = r[0] - r[1] - r[2];
                editor.setStatus(r[0] + " file(s): " + r[1] + (reflow ? " reflowed" : " reformatted")
                        + ", " + unchanged + " unchanged"
                        + (r[2] > 0 ? ", " + r[2] + " skipped" : ""));
                editor.setWait(false);
            }
        }.execute();
    }

    /** Refresh open, unmodified editor buffers from disk so the view matches. */
    private void refreshOpenBuffers(List<Path> changed) {
        if (changed.isEmpty()) {
            return;
        }
        Set<Path> changedSet = new HashSet<>(changed);
        CommandExecutor exec = editor.getCommandExecutor();
        Vector views = editor.getViews();
        for (Object o : views) {
            if (!(o instanceof DogsBayView view)) {
                continue;
            }
            DogsBayDocument doc = view.getDocument();
            if (doc == null || doc.getURL() == null || doc.hasChangedOnDisk()) {
                continue;   // skip unsaved buffers — don't clobber the user's edits
            }
            try {
                Path f = Path.of(doc.getURL().toURI());
                if (changedSet.contains(f)) {
                    exec.execute(new SetContentCommand(f, Files.readString(f)));
                }
            } catch (Exception ignore) {
                // best-effort refresh; the file on disk is already updated
            }
        }
    }
}
