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

package com.dogsbay.xml.browser;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.xml.editor.EditorPanel;
import com.dogsbay.xml.transform.TransformerUtilities;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.actions.OpenBrowserAction;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for creating and displaying HTML previews in tabs.
 * Follows the same pattern as DiffViewManager for tab reuse.
 * Supports live refresh: when the user edits the source document,
 * the preview updates automatically after a short debounce delay.
 *
 * @author DogsBay Ltd
 */
public class HtmlPreviewManager {
    private static final boolean DEBUG = false;
    private static final int DEBOUNCE_MS = 500;

    /** Active preview states keyed by tab name. */
    private static final Map<String, PreviewState> activePreviews = new ConcurrentHashMap<>();

    /**
     * Opens an HTML preview of the current document in a new tab.
     * If a preview tab for this document already exists, switches to it
     * and refreshes the content.
     *
     * @param parent the parent DogsBayAIEditor
     */
    public static void openPreview(DogsBayAIEditor parent) {
        openPreview(parent, false);
    }

    /**
     * Opens an HTML preview in a split pane beside the editor.
     *
     * @param parent the parent DogsBayAIEditor
     */
    public static void openPreviewSplit(DogsBayAIEditor parent) {
        openPreview(parent, true);
    }

    private static void openPreview(DogsBayAIEditor parent, boolean split) {
        DogsBayDocument document = parent.getDocument();
        if (document == null) {
            return;
        }

        DogsBayView view = parent.getView();
        if (view != null) {
            view.updateModel();
        }

        String docName = document.getName();
        String tabName = "Preview: " + docName;

        // Capture the Swing Document and scroll viewport now while we have the right view
        javax.swing.text.Document swingDoc = getSwingDocument(view);
        JViewport editorViewport = getEditorViewport(view);

        SwingUtilities.invokeLater(() -> {
            // Reuse existing tab if present — refresh its content
            PreviewState existing = activePreviews.get(tabName);
            if (existing != null && parent.getSelectedTabbedView().selectTabByName(tabName)) {
                refreshFromSwingDoc(existing);
                if (split && parent.getSelectedTabbedView().getTabCount() > 1) {
                    parent.splitVertically();
                }
                return;
            }

            try {
                ConfigurationProperties config = parent.getProperties();
                HtmlPreviewPanel panel = new HtmlPreviewPanel(config);

                // DITA documents get the rendering-context bar (context map
                // for key resolution + ditaval filter), pre-seeded from the active
                // deliverable (map + DITAVAL) when set, else the Default Root Map.
                if (DitaConverter.isDita(document.getText())) {
                    java.io.File seedMap = parent.getDefaultRootMapFile();
                    java.io.File seedDitaval = null;
                    var svc = parent.getDeliverableService();
                    if (svc != null && svc.getActiveDeliverable() != null) {
                        var active = svc.getActiveDeliverable();
                        if (active.map() != null) {
                            seedMap = active.map().toFile();
                        }
                        if (active.ditaval() != null) {
                            seedDitaval = active.ditaval().toFile();
                        }
                    }
                    panel.showDitaContextBar(seedMap, seedDitaval);
                }

                String html = resolveHtml(document, panel.getPreviewOptions());

                if (html != null) {
                    URL docUrl = document.getURL();
                    com.dogsbay.xml.editor.DocumentFormat fmt = document.getDocumentFormat();
                    boolean isHtml = fmt != null && "HTML".equals(fmt.getName());
                    if (docUrl != null && docUrl.getProtocol().equals("file")
                            && !document.hasChangedOnDisk() && isHtml) {
                        panel.loadUrl(docUrl);
                    } else {
                        panel.loadContent(html, null);
                    }
                } else {
                    panel.loadContent("<html><body><p>Unable to generate preview.</p></body></html>", null);
                }

                // Set up live refresh using the captured Swing Document
                PreviewState state = createLiveRefresh(panel, document, swingDoc, tabName);

                // Re-render when the user changes the context map / ditaval
                panel.setOnContextChanged(() -> refreshFromSwingDoc(state));

                // Set up scroll sync from editor to preview
                setupScrollSync(state, editorViewport);

                activePreviews.put(tabName, state);

                // Clean up when tab is closed
                panel.setOnDispose(() -> disposePreview(tabName));

                parent.getSelectedTabbedView().addCustomPanel(panel, tabName, tabName);

                if (split && parent.getSelectedTabbedView().getTabCount() > 1) {
                    parent.splitVertically();
                }

                if (DEBUG) {
                    System.out.println("Opened preview tab: " + tabName);
                }
            } catch (Exception e) {
                if (DEBUG) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Gets the Swing Document from a view, or null if unavailable.
     */
    private static javax.swing.text.Document getSwingDocument(DogsBayView view) {
        try {
            if (view != null) {
                Editor editor = view.getEditor();
                if (editor != null) {
                    return editor.getEditor().getDocument();
                }
            }
        } catch (Exception e) {
            if (DEBUG) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Gets the editor's scroll viewport from a view, or null if unavailable.
     */
    private static JViewport getEditorViewport(DogsBayView view) {
        try {
            if (view != null) {
                Editor editor = view.getEditor();
                if (editor != null) {
                    EditorPanel editorPanel = editor.getSelectedEditorPanel();
                    if (editorPanel != null) {
                        JScrollPane scroller = editorPanel.getScroller();
                        if (scroller != null) {
                            return scroller.getViewport();
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (DEBUG) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Sets up bidirectional scroll sync between editor and preview.
     * Uses suppressScrollSync flag on the panel to prevent feedback loops.
     */
    private static void setupScrollSync(PreviewState state, JViewport viewport) {
        if (viewport == null || state.panel == null) {
            return;
        }

        // Editor → Preview
        ChangeListener listener = (ChangeEvent e) -> {
            if (!state.panel.isScrollSyncEnabled() || state.panel.isSuppressingEditorSync()) {
                return;
            }
            int viewHeight = viewport.getViewSize().height;
            int visibleHeight = viewport.getExtentSize().height;
            int maxScroll = viewHeight - visibleHeight;
            if (maxScroll > 0) {
                double percentage = (double) viewport.getViewPosition().y / maxScroll;
                state.panel.scrollToPercentage(Math.min(1.0, Math.max(0.0, percentage)));
            }
        };

        viewport.addChangeListener(listener);
        state.scrollViewport = viewport;
        state.scrollListener = listener;

        // Preview → Editor
        state.panel.setOnPreviewScrolled(() -> {
            if (!state.panel.isScrollSyncEnabled()) {
                return;
            }
            // Get the percentage from the panel (already on EDT via SwingUtilities.invokeLater)
            javafx.application.Platform.runLater(() -> {
                double pct = state.panel.getScrollPercentage();
                SwingUtilities.invokeLater(() -> {
                    int viewHeight = viewport.getViewSize().height;
                    int visibleHeight = viewport.getExtentSize().height;
                    int maxScroll = viewHeight - visibleHeight;
                    if (maxScroll > 0) {
                        int newY = (int) (pct * maxScroll);
                        java.awt.Point pos = viewport.getViewPosition();
                        viewport.setViewPosition(new java.awt.Point(pos.x, newY));
                    }
                });
            });
        });
    }

    /**
     * Creates the live refresh wiring: a Swing DocumentListener on the
     * captured Swing Document that triggers a debounced preview refresh.
     */
    private static PreviewState createLiveRefresh(HtmlPreviewPanel panel,
                                                   DogsBayDocument document,
                                                   javax.swing.text.Document swingDoc,
                                                   String tabName) {
        // Single-shot debounce timer
        Timer debounceTimer = new Timer(DEBOUNCE_MS, e -> {
            PreviewState state = activePreviews.get(tabName);
            if (state != null) {
                refreshFromSwingDoc(state);
            }
        });
        debounceTimer.setRepeats(false);

        // Listen for edits in the Swing text document
        DocumentListener docListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { debounceTimer.restart(); }
            @Override
            public void removeUpdate(DocumentEvent e) { debounceTimer.restart(); }
            @Override
            public void changedUpdate(DocumentEvent e) { debounceTimer.restart(); }
        };

        // Attach listener to the captured Swing Document
        if (swingDoc != null) {
            swingDoc.addDocumentListener(docListener);
        }

        return new PreviewState(panel, document, swingDoc, debounceTimer, docListener);
    }

    /**
     * Refreshes the preview by reading text directly from the stored
     * Swing Document reference (not via parent.getView()).
     */
    private static void refreshFromSwingDoc(PreviewState state) {
        if (state == null || state.swingDocument == null) {
            return;
        }

        try {
            String text = state.swingDocument.getText(0, state.swingDocument.getLength());
            String html = resolveHtmlFromText(text, state.document,
                    state.panel.getPreviewOptions());
            if (html != null) {
                state.panel.refresh(html);
            }
        } catch (Exception e) {
            if (DEBUG) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Resolves HTML from raw text and document metadata (name, type).
     * Used for live refresh where the document model may not be synced.
     */
    private static String resolveHtmlFromText(String text, DogsBayDocument document,
                                              PreviewOptions options) {
        try {
            com.dogsbay.xml.editor.DocumentFormat format = document.getDocumentFormat();

            // Use format's converter if available
            if (format != null) {
                String html = format.convertToHtml(text, getBaseDir(document), options);
                if (html != null) {
                    return html;
                }
            }

            // For XML, fall back to document-based resolution
            // (the document model needs to be updated for XSLT transforms)
            return resolveHtml(document, options);
        } catch (Exception e) {
            if (DEBUG) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Removes a preview's live refresh wiring. Called when the tab is closed.
     *
     * @param tabName the preview tab name
     */
    public static void disposePreview(String tabName) {
        PreviewState state = activePreviews.remove(tabName);
        if (state != null) {
            state.dispose();
        }
    }

    /**
     * Overload for backward compatibility.
     */
    public static void disposePreview(String tabName, DogsBayAIEditor parent) {
        disposePreview(tabName);
    }

    /**
     * Extracts the parent directory of the document's file, or null if unknown.
     */
    private static File getBaseDir(DogsBayDocument document) {
        try {
            URL url = document.getURL();
            if (url != null && "file".equals(url.getProtocol())) {
                File file = new File(url.toURI());
                return file.getParentFile();
            }
        } catch (Exception e) {
            // ignore — return null
        }
        return null;
    }

    /**
     * Determines the HTML content to display for the given document.
     * Uses the document's format to determine the conversion strategy.
     */
    static String resolveHtml(DogsBayDocument document) {
        return resolveHtml(document, null);
    }

    static String resolveHtml(DogsBayDocument document, PreviewOptions options) {
        try {
            com.dogsbay.xml.editor.DocumentFormat format = document.getDocumentFormat();

            // Check if the format provides its own HTML conversion
            if (format != null) {
                String html = format.convertToHtml(document.getText(),
                        getBaseDir(document), options);
                if (html != null) {
                    return html;
                }
            }

            // XML-based documents: try XSLT transformation
            if (format != null && format.isXmlBased() && !document.isError()) {
                // Check for stylesheet PI
                OpenBrowserAction checker = new OpenBrowserAction(null);
                if (checker.checkForStylesheetPI(document)) {
                    return document.getText();
                }

                // Plain XML — transform with default stylesheet
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                TransformerUtilities.transform(document, out, false);
                return out.toString(document.getJavaEncoding());
            }

            // Fallback — just show raw text
            return document.getText();
        } catch (Exception e) {
            if (DEBUG) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
