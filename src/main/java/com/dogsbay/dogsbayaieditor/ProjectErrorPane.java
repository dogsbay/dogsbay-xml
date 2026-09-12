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

import com.dogsbay.xml.XMLError;

/**
 * Error pane for project-wide validation results (Validate Project, Schematron).
 *
 * <p>The base {@link ErrorPane} navigation assumes the active document is the
 * errored one and uses a cached editor that is only set for the main pane — so
 * double-clicking a project result did nothing when no document was open. This
 * override always opens the target file from the error's {@code systemId} and
 * then selects the error in the now-active editor, so navigation works with no
 * document open or a non-editor view active.
 */
public final class ProjectErrorPane extends ErrorPane {

    public ProjectErrorPane(DogsBayAIEditor editor) {
        super(editor);
    }

    /**
     * Always shows the filename. Project results span many files, and the base
     * pane hides the name of whichever file is currently active — so clicking a
     * row opened its file and the row then lost its filename, along with every
     * other row from that same file. That reads as the list mutating under you.
     */
    @Override
    protected boolean showsFileName(String systemId) {
        return true;
    }

    @Override
    protected void errorSelected() {
        XMLError error = selectedError();
        if (error == null || error.getSystemId() == null) {
            return;
        }
        DogsBayAIEditor host = owner();

        // Open the target file unless it's already the active document. Compare by
        // canonical path, not basename — DITA projects routinely have duplicate
        // basenames, and endsWith(name) would navigate inside the wrong file.
        if (!isActiveDocument(error.getSystemId(), host)) {
            host.open(URLUtilities.toURL(error.getSystemId()), null, false);
        }

        // Defer selection + focus so a freshly-opened editor is fully realized
        // first — otherwise the very first click's focus lands before the new
        // editor exists (subsequent clicks, on an already-open file, worked).
        javax.swing.SwingUtilities.invokeLater(() -> {
            DogsBayView view = host.getView();
            if (view == null || view.getEditor() == null) {
                return;
            }
            // Confirm the intended file is actually the active one before moving the
            // caret. The open can fail — the file may have been deleted or renamed
            // since validation ran — and selecting the error's line regardless would
            // silently jump to that line number in whatever document happens to be
            // open, which reads as the editor scrolling somewhere arbitrary.
            if (!isActiveDocument(error.getSystemId(), host)) {
                return;
            }
            view.getEditor().selectError(error);
            view.getEditor().setFocus();
        });
    }

    /** True if {@code systemId} resolves to the same file as the active document. */
    private static boolean isActiveDocument(String systemId, DogsBayAIEditor host) {
        var doc = host.getDocument();
        if (doc == null || doc.getURL() == null) {
            return false;
        }
        try {
            java.io.File a = new java.io.File(new java.net.URI(systemId)).getCanonicalFile();
            java.io.File b = new java.io.File(doc.getURL().toURI()).getCanonicalFile();
            return a.equals(b);
        } catch (Exception e) {
            return false;
        }
    }
}
