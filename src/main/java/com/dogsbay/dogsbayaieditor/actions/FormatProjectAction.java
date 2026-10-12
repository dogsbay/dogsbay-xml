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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import com.dogsbay.dogsbayaieditor.ChangeManager;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.commands.FormatCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.ReflowCommand;
import com.dogsbay.dogsbayaieditor.commands.results.FormatResult;
import com.dogsbay.dogsbayaieditor.ditaproject.FileSet;
import com.dogsbay.xml.DogsBayDocument;

/**
 * Project-wide <b>Format</b> / <b>Reflow</b>. Walks every XML file under the open
 * project root and formats it to the resolved house style — or, in reflow mode,
 * reflows prose to one sentence per line and then formats — using the same engine the
 * per-file Format/Reflow and the CLI use. Runs off the EDT; open, <em>unmodified</em>
 * buffers are refreshed from disk afterward so the editor matches.
 *
 * <p><b>Unsaved buffers are offered a save first.</b> This reads the files on disk, so an open buffer with
 * unsaved edits was formatted around: the file changed underneath it and the reader met "changed by another
 * process" on their next click, with their edits now sitting against a reformatted file (reported 2026-09-30).
 * Saving them is the only order in which the reader's work and the house style both survive; formatting the
 * buffers instead would make this mean something different from {@code dogsbay-xml format}.
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
                    "Open a project folder first (File → Open Project Folder).",
                    (String) getValue(NAME), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<DogsBayView> toSave = offerToSaveUnsavedBuffers();
        if (toSave == null) {
            return;
        }
        final Path root = rootDir.toPath();
        final String verb = reflow ? "Reflowing" : "Formatting";
        editor.setWait(true);
        editor.setStatus(verb + " project \"" + rootDir.getName() + "\" ...");

        new SwingWorker<int[], Void>() {
            private final List<Path> changed = new ArrayList<>();
            private List<String> couldNotSave = List.of();

            @Override
            protected int[] doInBackground() throws Exception {
                // Off the event dispatch thread, like the editor's Save All: writing several files — each
                // through the format-on-save hook — is not something to do with the interface frozen.
                couldNotSave = saveAll(toSave);
                if (!couldNotSave.isEmpty()) {
                    // Having said the files would be saved, formatting around one that was not is the
                    // surprise this whole prompt exists to remove. Nothing is formatted.
                    return null;
                }
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
                if (r == null) {
                    editor.setStatus("Nothing was " + (reflow ? "reflowed" : "formatted"));
                    editor.setWait(false);
                    JOptionPane.showMessageDialog(editor,
                            "Could not save:\n    " + String.join("\n    ", couldNotSave)
                            + "\n\nNothing was " + (reflow ? "reflowed" : "formatted")
                            + " — the files on disk are as they were.",
                            (String) getValue(NAME), JOptionPane.ERROR_MESSAGE);
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

    /**
     * Whether a buffer holds work that this format would otherwise walk past.
     *
     * <p>Three questions, and the first is the one that had been asked wrong. Unsaved edits are the undo
     * history's business: {@code DogsBayDocument.hasChangedOnDisk()}, which this used to ask, compares the
     * file's timestamp with the one recorded at load, so it answers "someone wrote this file" — and formatting
     * a file makes that true. Every file the format had changed was therefore skipped by the very refresh that
     * existed to bring it back in step, so the refresh did nothing at all and the reader met the
     * external-change prompt instead (reported 2026-09-30).
     *
     * <p>The other two are what the editor's own Save All asks before it writes anything: a document with no
     * URL has nowhere to be saved, and a read-only one would only fail.
     */
    static boolean needsSaving(DogsBayDocument document, ChangeManager changes) {
        return document != null && changes != null
                && document.getURL() != null
                && !document.isReadOnly()
                && changes.isChanged();
    }

    /** {@link #needsSaving} for a view. */
    private static boolean hasUnsavedEdits(DogsBayView view) {
        return needsSaving(view.getDocument(), view.getChangeManager());
    }

    /**
     * Ask about open buffers with unsaved edits.
     *
     * @return the views to save before formatting (empty when there is nothing to save, or the reader would
     *         rather keep the edits in the buffer), or null when they cancelled
     */
    private List<DogsBayView> offerToSaveUnsavedBuffers() {
        List<DogsBayView> unsaved = new ArrayList<>();
        for (Object o : editor.getViews()) {
            if (o instanceof DogsBayView view && hasUnsavedEdits(view)) {
                unsaved.add(view);
            }
        }
        if (unsaved.isEmpty()) {
            return List.of();
        }
        String what = (String) getValue(NAME);
        StringBuilder names = new StringBuilder();
        for (DogsBayView view : unsaved) {
            names.append("\n    ").append(view.getDocument().getName());
        }
        int answer = JOptionPane.showConfirmDialog(editor,
                unsaved.size() + " open file(s) have unsaved changes:" + names
                + "\n\n" + what + " reads the files on disk, so those changes would be left behind."
                + "\n\nSave them first?",
                what, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.CANCEL_OPTION || answer == JOptionPane.CLOSED_OPTION) {
            return null;
        }
        // No: the reader would rather keep the edits in the buffer. Their files are formatted around, as
        // before — they have now been told that is what happens.
        return answer == JOptionPane.YES_OPTION ? unsaved : List.of();
    }

    /**
     * Save each view, in the order the editor's own Save All does it: flush the view's edits into the document
     * (a failed flush must not save stale text), write, then tell the undo history it is saved.
     *
     * <p>That last step is not optional here. {@code DogsBayDocument.save()} moves the file's timestamp and
     * nothing else; without {@link ChangeManager#markSave()} the buffer still reports unsaved edits, and
     * {@link #refreshOpenBuffers} would skip the very files the reader had just asked to save.
     *
     * @return the names of the documents that could not be saved, in order; empty when all of them were
     */
    private static List<String> saveAll(List<DogsBayView> views) {
        List<String> couldNotSave = new ArrayList<>();
        for (DogsBayView view : views) {
            try {
                view.updateModelOrThrow();
                view.getDocument().save();
                view.getChangeManager().markSave();
            } catch (Exception | StackOverflowError failed) {
                couldNotSave.add(view.getDocument().getName());
            }
        }
        return couldNotSave;
    }

    /**
     * Refresh open, unmodified editor buffers from disk so the view matches.
     *
     * <p>A reload, not a {@code SetContentCommand}: setting the content splices into the buffer, which is an
     * edit, so every open file would be left looking unsaved against a disk copy it already matched.
     * {@link DogsBayView#reload(boolean)} reads the file and records the new timestamp, so the buffer is clean
     * and the external-change check has nothing left to ask about. Quietly ({@code false}) — a file that has
     * gone unreadable between the format and here is not worth a dialog per file.
     */
    private void refreshOpenBuffers(List<Path> changed) {
        if (changed.isEmpty()) {
            return;
        }
        Set<Path> changedSet = new HashSet<>(changed);
        Vector views = editor.getViews();
        for (Object o : views) {
            if (!(o instanceof DogsBayView view)) {
                continue;
            }
            DogsBayDocument doc = view.getDocument();
            if (doc == null || doc.getURL() == null || hasUnsavedEdits(view)) {
                continue;   // a buffer the reader kept — don't clobber their edits
            }
            try {
                if (changedSet.contains(Path.of(doc.getURL().toURI()))) {
                    view.reload(false);
                }
            } catch (Exception ignore) {
                // best-effort refresh; the file on disk is already updated
            }
        }
    }
}
