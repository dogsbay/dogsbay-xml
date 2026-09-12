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
import java.net.URI;

import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.project.FormatStyleResolver;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XMLUtilities;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.xml.format.FormatStyle;

/**
 * Reflows the active document's prose to one sentence per line (semantic line
 * breaks) and formats it — a deliberate, writer-invoked operation. Verbatim
 * blocks (codeblock/pre/…) are left untouched. Mirrors {@link FormatAction}'s
 * off-EDT run + EDT apply.
 */
public class ReflowSentencesAction extends AbstractAction {

    private Editor editor = null;
    private final DogsBayAIEditor parent;

    public ReflowSentencesAction(DogsBayAIEditor parent) {
        super("Reflow Sentences");
        this.parent = parent;
        putValue(SHORT_DESCRIPTION, "Reflow prose to one sentence per line");
        super.setEnabled(false);
    }

    public void setView(Object view) {
        editor = (view instanceof Editor e) ? e : null;
        setDocument(parent.getDocument());
    }

    public void setDocument(DogsBayDocument doc) {
        setEnabled(doc != null && doc.isXML() && editor != null);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        parent.setWait(true);
        parent.setStatus("Reflowing sentences ...");
        new Thread(() -> {
            try {
                DogsBayDocument document = parent.getDocument();
                parent.getView().updateModel();

                String text = document.getText();
                String encoding = document.getEncoding();
                java.net.URL url = document.getURL();
                String systemId = (url != null) ? url.toString() : null;

                FormatStyle style = styleFor(systemId);
                final String result = XMLUtilities.reflowSentences(text, systemId, encoding, style);

                SwingUtilities.invokeLater(() -> {
                    editor.setText(result);
                    parent.getView().updateModel();
                });
            } catch (Exception e) {
                MessageHandler.showError(parent,
                        "Error reflowing the document.\nPlease make sure the document is well-formed.",
                        "Reflow Error");
            } finally {
                parent.setStatus("Done");
                parent.setWait(false);
                if (editor != null) {
                    editor.setFocus();
                }
            }
        }).start();
    }

    /** The resolved house style for the active file, or the built-in default. */
    private static FormatStyle styleFor(String systemId) {
        if (systemId != null && systemId.startsWith("file:")) {
            try {
                return FormatStyleResolver.forFile(java.nio.file.Path.of(new URI(systemId)));
            } catch (Exception ignore) {
                // unresolved file → default
            }
        }
        return FormatStyle.defaults();
    }
}
