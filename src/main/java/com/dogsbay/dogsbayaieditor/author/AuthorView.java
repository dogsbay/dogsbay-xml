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

package com.dogsbay.dogsbayaieditor.author;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

import org.dom4j.Element;
import org.dom4j.Node;

import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.ViewPanel;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.DogsBayDocumentEvent;
import com.dogsbay.xml.DogsBayDocumentListener;
import com.dogsbay.xml.XDocument;
import com.dogsbay.xml.author.model.AuthorDocument;
import com.dogsbay.xml.author.model.AuthorDocumentListener;
import com.dogsbay.xml.author.ui.AuthorEditorPanel;

/**
 * The Author document view: hosts the self-contained
 * {@link AuthorEditorPanel} and bridges it to the application's document
 * lifecycle — DogsBayDocument events in, undoable edits out (to the shared
 * ChangeManager), and block-tree exports back into the document on
 * updateModel()/save. All editor-specific wiring lives here, keeping
 * {@code xml/author} standalone.
 */
public class AuthorView extends ViewPanel implements DogsBayDocumentListener {

    private final DogsBayView view;
    private final AuthorEditorPanel panel;
    private final AuthorDocumentListener dirtyTracker = e -> modelDirty = true;
    private DogsBayDocument document;
    private AuthorDocument trackedAuthorDocument;
    private boolean hasLatest;
    private boolean modelDirty;
    private boolean applying;

    public AuthorView(DogsBayView view) {
        super(new BorderLayout());
        this.view = view;
        this.panel = new AuthorEditorPanel();
        add(panel, BorderLayout.CENTER);

        panel.addUndoableEditListener(e -> view.getChangeManager().addEdit(
                new UndoableAuthorEdit(e.getEdit(), panel::getAuthorDocument, panel.getAuthorDocument())));

        // the font scale is a session-wide preference, remembered between runs
        com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties properties = view.getProperties();
        if (properties != null) {
            com.dogsbay.xml.author.ui.BlockStylesheet.setFontScale(
                    properties.getAuthorFontScalePercent() / 100.0);
            panel.addZoomListener(() -> properties.setAuthorFontScalePercent(
                    (int) Math.round(com.dogsbay.xml.author.ui.BlockStylesheet.getFontScale() * 100)));
        }
    }

    /**
     * Where the writer is, as a path through the block tree, for the XML view
     * to put its caret in the same place. Null when nothing is selected.
     */
    public java.util.List<BlockTextMapper.Step> getSelectionPath() {
        return BlockTextMapper.pathOf(panel.getSelectedBlock());
    }

    /**
     * Selects the block at {@code path} (the deepest one the path reaches) and
     * scrolls to it, so switching in from the XML view lands where the caret was.
     */
    public void selectPath(java.util.List<BlockTextMapper.Step> path) {
        com.dogsbay.xml.author.model.AuthorDocument model = panel.getAuthorDocument();
        if (model == null) {
            return;
        }
        com.dogsbay.xml.author.model.AuthorBlock block =
                BlockTextMapper.targetFor(model.getRoot(), path, panel.getSelectedBlock());
        if (block != null) {
            panel.focusBlock(block, 0);
        }
    }

    /**
     * Flushes text typed into a block but not yet committed to the model, so it
     * becomes its own undoable edit. Called before an undo or redo.
     */
    public void commitPendingText() {
        panel.commitPendingEdits();
    }

    public AuthorEditorPanel getEditorPanel() {
        return panel;
    }

    public DogsBayDocument getDocument() {
        return document;
    }

    /** (Re)imports the document's XML tree into the block model and renders it. */
    public void setDocument(DogsBayDocument document) {
        if (this.document != null) {
            this.document.removeListener(this);
        }
        this.document = document;
        if (document != null) {
            document.addListener(this);
        }
        panel.setReferenceResolver(buildResolver(document));
        if (document != null && !document.isError() && document.getRoot() != null) {
            panel.setAdapter(chooseAdapter(document));
            panel.setContent(document.getRoot());
        } else {
            panel.setContent(null);
        }
        trackAuthorDocument();
        modelDirty = false;
        hasLatest = true;
    }

    /**
     * DITA topics use the rich DITA profile; anything else gets general XML
     * mode — schema-driven when the document has an associated grammar,
     * document-inferred otherwise.
     */
    private com.dogsbay.xml.author.adapter.BlockAdapter chooseAdapter(DogsBayDocument document) {
        String rootName = document.getRoot().getName();
        if (com.dogsbay.xml.author.adapter.DitaBlockAdapter.isDitaTopic(rootName)) {
            return new com.dogsbay.xml.author.adapter.DitaBlockAdapter();
        }
        // Generic XML infers its block structure from the document itself. (An
        // XSD-driven registry used to be built here; XML Schema support was removed.)
        return new com.dogsbay.xml.author.adapter.GenericXmlAdapter();
    }

    private java.io.File keySpaceMap;
    private long keySpaceStamp;
    private com.dogsbay.dogsbayaieditor.links.KeySpace keySpaceCache;

    /**
     * The root map's key space, parsed once per map file and modification
     * time. The split view refreshes this view twice a second while the XML
     * side is typed in; re-reading every map on each refresh was most of it.
     */
    private com.dogsbay.dogsbayaieditor.links.KeySpace cachedKeySpace(java.io.File rootMap) {
        long stamp = keySpaceStamp(rootMap);
        if (keySpaceCache == null || !rootMap.equals(keySpaceMap) || stamp != keySpaceStamp) {
            keySpaceCache = com.dogsbay.dogsbayaieditor.links.KeySpace.fromRootMap(rootMap);
            keySpaceMap = rootMap;
            keySpaceStamp = stamp;
        }
        return keySpaceCache;
    }

    /** A change stamp over the root map and every map it pulls in, so an edited submap invalidates the cache. */
    static long keySpaceStamp(java.io.File rootMap) {
        long stamp = rootMap.lastModified() ^ rootMap.length();
        for (String map : com.dogsbay.dogsbayaieditor.links.KeySpace.mapsInClosure(rootMap)) {
            java.io.File f = new java.io.File(map);
            stamp = stamp * 31 + f.lastModified() + f.length();
        }
        return stamp;
    }

    /**
     * Reference resolution against the document location, with key text/hrefs
     * from the battle-tested project KeySpace (Default Root Map) and
     * conref/href resolution from the filesystem resolver.
     */
    private com.dogsbay.xml.author.spi.ReferenceResolver buildResolver(DogsBayDocument document) {
        java.io.File docFile = null;
        try {
            java.net.URL url = document != null ? document.getURL() : null;
            if (url != null && "file".equals(url.getProtocol())) {
                docFile = new java.io.File(url.toURI());
            }
        } catch (Exception ignored) {
            // unsaved or non-file document
        }
        java.io.File rootMap = view.getMainEditor() != null
                ? view.getMainEditor().getDefaultRootMapFile() : null;
        com.dogsbay.xml.author.spi.DefaultReferenceResolver base =
                new com.dogsbay.xml.author.spi.DefaultReferenceResolver(
                        docFile != null ? docFile.toURI() : null, rootMap);
        if (rootMap == null) {
            return base;
        }
        com.dogsbay.dogsbayaieditor.links.KeySpace keySpace = cachedKeySpace(rootMap);
        return new com.dogsbay.xml.author.spi.ReferenceResolver() {
            @Override
            public String keyText(String key) {
                // Lenient: a bare keyref to a key unique to one @keyscope still renders.
                com.dogsbay.dogsbayaieditor.links.KeyDefinition def =
                        keySpace.resolveLenient(key, "");
                if (def != null) {
                    if (def.keywordText() != null && !def.keywordText().isBlank()) {
                        return def.keywordText();
                    }
                    if (def.linkText() != null && !def.linkText().isBlank()) {
                        return def.linkText();
                    }
                }
                // href-only keys (glossary entries): resolve through the target
                return base.keyText(key);
            }

            @Override
            public String keyHref(String key) {
                java.io.File target = keySpace.resolveHrefFileLenient(key, "");
                return target != null ? target.toURI().toString() : null;
            }

            @Override
            public java.net.URI resolveHref(String href) {
                return base.resolveHref(href);
            }

            @Override
            public org.dom4j.Element resolveConref(String conref) {
                return base.resolveConref(conref);
            }
        };
    }

    private void trackAuthorDocument() {
        if (trackedAuthorDocument != null) {
            trackedAuthorDocument.removeListener(dirtyTracker);
        }
        trackedAuthorDocument = panel.getAuthorDocument();
        if (trackedAuthorDocument != null) {
            trackedAuthorDocument.addListener(dirtyTracker);
        }
    }

    /** Mirrors the Viewer pattern: false once the document changed under us. */
    public boolean hasLatestInformation() {
        return hasLatest;
    }

    /** True when block-model edits have not yet been exported to the document. */
    public boolean isModelDirty() {
        panel.commitPendingEdits();
        return modelDirty;
    }

    /**
     * Exports the block tree back into the DogsBayDocument (in-place root
     * replacement, preserving doc-level comments/PIs and the DOCTYPE) and
     * refreshes the document text. Called from updateModel() before other
     * views or save read the document.
     */
    /** The project house style's indent unit for this document's file, or the default. */
    private String resolveIndentUnit() {
        try {
            java.net.URL url = document.getURL();
            if (url != null && "file".equals(url.getProtocol())) {
                java.nio.file.Path file = java.nio.file.Path.of(url.toURI());
                return com.dogsbay.dogsbayaieditor.project.FormatStyleResolver.forFile(file)
                        .indentString();
            }
        } catch (Exception ignore) {
            // unresolved file → fall back to the default indent
        }
        return com.dogsbay.xml.format.FormatStyle.defaults().indentString();
    }

    public void applyToDocument() {
        if (document == null || panel.getAuthorDocument() == null) {
            return;
        }
        Element newRoot = panel.exportContent();
        XDocument xdoc = document.getDocument();
        if (xdoc == null || newRoot == null) {
            return;
        }
        // Indent structural blocks with the project house style's indent unit, so
        // Author-view saves match the rest of the document (no 2-space-vs-tabs churn).
        com.dogsbay.xml.author.adapter.BlockXmlFormatter.indent(newRoot, resolveIndentUnit());

        replaceRoot(xdoc, newRoot);

        applying = true;
        try {
            document.update();
        } finally {
            applying = false;
        }
        modelDirty = false;
    }

    /**
     * Swaps the document's root element in place, preserving the order of
     * doc-level comments/PIs around it (clearContent would drop them).
     */
    static void replaceRoot(org.dom4j.Document xdoc, Element newRoot) {
        Element oldRoot = xdoc.getRootElement();
        List<Node> content = new ArrayList<>();
        for (Object node : xdoc.content()) {
            content.add((Node) node);
        }
        xdoc.clearContent();
        boolean replaced = false;
        for (Node node : content) {
            if (node == oldRoot) {
                xdoc.add(newRoot);
                replaced = true;
            } else {
                node.detach();
                xdoc.add(node);
            }
        }
        if (!replaced) {
            xdoc.add(newRoot);
        }
    }

    @Override
    public void documentUpdated(DogsBayDocumentEvent event) {
        if (!applying) {
            hasLatest = false;
        }
    }

    public void documentDeleted(DogsBayDocumentEvent event) {
    }

    @Override
    public void setFocus() {
        if (!panel.hasFocus()) {
            panel.requestFocusInWindow();
        }
    }

    @Override
    public void updatePreferences() {
        panel.rebuild();
    }

    @Override
    public void setProperties() {
    }

    public void cleanup() {
        if (document != null) {
            document.removeListener(this);
            document = null;
        }
        if (trackedAuthorDocument != null) {
            trackedAuthorDocument.removeListener(dirtyTracker);
            trackedAuthorDocument = null;
        }
        panel.setContent(null);
        removeAll();
    }
}
