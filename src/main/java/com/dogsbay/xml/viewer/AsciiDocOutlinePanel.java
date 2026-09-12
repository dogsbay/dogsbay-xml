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

package com.dogsbay.xml.viewer;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.text.Document;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.DogsBayDocumentEvent;
import com.dogsbay.xml.DogsBayDocumentListener;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.xml.editor.EditorPanel;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.ViewTreePanel;
import com.dogsbay.dogsbayaieditor.properties.TextPreferences;

/**
 * Outline panel for AsciiDoc documents. Parses section titles from the
 * source and displays them in a hierarchical tree. Clicking a section
 * navigates the editor to the corresponding line.
 */
public class AsciiDocOutlinePanel extends ViewTreePanel implements DogsBayDocumentListener, FormatOutlinePanel {

    private static final Pattern SECTION_TITLE = Pattern.compile("^(={1,5})\\s+(.+)$", Pattern.MULTILINE);

    private JTree tree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private JScrollPane scrollPane;

    private DogsBayDocument document;
    private DogsBayAIEditor editor;

    private Document swingDocument;
    private DocumentListener swingDocListener;
    private Timer debounceTimer;

    private boolean updatingSelection = false;

    public AsciiDocOutlinePanel(DogsBayAIEditor parent) {
        super(new BorderLayout());

        this.editor = parent;

        rootNode = new DefaultMutableTreeNode("Document");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new SectionCellRenderer());

        tree.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                if (updatingSelection) return;
                TreePath path = tree.getSelectionPath();
                if (path == null) return;

                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                Object userObj = node.getUserObject();
                if (userObj instanceof SectionInfo) {
                    navigateToLine(((SectionInfo) userObj).lineNumber);
                }
            }
        });

        scrollPane = new JScrollPane(tree,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        this.setBorder(new EmptyBorder(0, 0, 0, 0));
        this.add(scrollPane, BorderLayout.CENTER);

        debounceTimer = new Timer(400, e -> refreshOutline());
        debounceTimer.setRepeats(false);
    }

    public void setDocument(DogsBayDocument document) {
        if (this.document != null) {
            this.document.removeListener(this);
        }
        removeSwingDocListener();

        this.document = document;

        if (document != null) {
            document.addListener(this);
            installSwingDocListener();
            refreshOutline();
        } else {
            rootNode.removeAllChildren();
            treeModel.reload();
        }
    }

    private void installSwingDocListener() {
        com.dogsbay.dogsbayaieditor.DogsBayView view = editor.getView();
        if (view == null || view.getEditor() == null) return;
        Editor editor = view.getEditor();
        EditorPanel ep = editor.getSelectedEditorPanel();
        if (ep == null) return;
        swingDocument = ep.getEditor().getDocument();
        swingDocListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { debounceTimer.restart(); }
            public void removeUpdate(DocumentEvent e) { debounceTimer.restart(); }
            public void changedUpdate(DocumentEvent e) { debounceTimer.restart(); }
        };
        swingDocument.addDocumentListener(swingDocListener);
    }

    private void removeSwingDocListener() {
        if (swingDocument != null && swingDocListener != null) {
            swingDocument.removeDocumentListener(swingDocListener);
        }
        swingDocument = null;
        swingDocListener = null;
        debounceTimer.stop();
    }

    private void refreshOutline() {
        String text = getEditorText();
        if (text == null) {
            rootNode.removeAllChildren();
            treeModel.reload();
            return;
        }

        List<SectionInfo> sections = parseSections(text);
        rebuildTree(sections);
    }

    private String getEditorText() {
        if (swingDocument != null) {
            try {
                return swingDocument.getText(0, swingDocument.getLength());
            } catch (Exception e) {
                // fall through
            }
        }
        if (document != null) {
            return document.getText();
        }
        return null;
    }

    static List<SectionInfo> parseSections(String asciidoc) {
        List<SectionInfo> sections = new ArrayList<>();
        if (asciidoc == null || asciidoc.isEmpty()) return sections;

        // Count lines up to each match to get line numbers
        Matcher m = SECTION_TITLE.matcher(asciidoc);
        while (m.find()) {
            int level = m.group(1).length();
            String title = m.group(2).trim();
            // Count newlines before this match to get line number
            int lineNumber = 0;
            for (int i = 0; i < m.start(); i++) {
                if (asciidoc.charAt(i) == '\n') lineNumber++;
            }
            sections.add(new SectionInfo(title, level, lineNumber));
        }
        return sections;
    }

    private void rebuildTree(List<SectionInfo> sections) {
        rootNode.removeAllChildren();

        DefaultMutableTreeNode[] levelNodes = new DefaultMutableTreeNode[7];
        levelNodes[0] = rootNode;

        for (SectionInfo info : sections) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(info);

            DefaultMutableTreeNode parent = rootNode;
            for (int l = info.level - 1; l >= 0; l--) {
                if (levelNodes[l] != null) {
                    parent = levelNodes[l];
                    break;
                }
            }

            parent.add(node);
            levelNodes[info.level] = node;

            for (int l = info.level + 1; l <= 6; l++) {
                levelNodes[l] = null;
            }
        }

        treeModel.reload();
        expandAll();
    }

    private void navigateToLine(int lineNumber) {
        com.dogsbay.dogsbayaieditor.DogsBayView view = editor.getView();
        if (view == null || view.getEditor() == null) return;
        Editor editor = view.getEditor();
        EditorPanel ep = editor.getSelectedEditorPanel();
        if (ep == null) return;
        javax.swing.text.JTextComponent textComp = ep.getEditor();
        javax.swing.text.Document doc = textComp.getDocument();
        javax.swing.text.Element root = doc.getDefaultRootElement();
        if (lineNumber < 0 || lineNumber >= root.getElementCount()) return;
        javax.swing.text.Element lineElem = root.getElement(lineNumber);
        int startOffset = lineElem.getStartOffset();
        int endOffset = Math.min(lineElem.getEndOffset() - 1, doc.getLength());

        textComp.select(startOffset, endOffset);
        try {
            java.awt.Rectangle rect = textComp.modelToView(startOffset);
            if (rect != null) {
                textComp.scrollRectToVisible(rect);
            }
        } catch (Exception e) {
            // ignore
        }
        textComp.requestFocusInWindow();
    }

    // DogsBayDocumentListener
    public void documentUpdated(DogsBayDocumentEvent event) {
        if (event.getType() == DogsBayDocumentEvent.CONTENT_UPDATED ||
            event.getType() == DogsBayDocumentEvent.TEXT_UPDATED) {
            SwingUtilities.invokeLater(() -> refreshOutline());
        }
    }

    public void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    public void collapseAll() {
        for (int i = tree.getRowCount() - 1; i >= 0; i--) {
            tree.collapseRow(i);
        }
    }

    public void setFocus() {
        tree.requestFocusInWindow();
    }

    public void setProperties() {}
    public void updatePreferences() {}
    public void updateHelper() {}

    public void cleanup() {
        removeSwingDocListener();
        if (document != null) {
            document.removeListener(this);
        }
        removeAll();
    }

    static class SectionInfo {
        final String title;
        final int level;
        final int lineNumber;

        SectionInfo(String title, int level, int lineNumber) {
            this.title = title;
            this.level = level;
            this.lineNumber = lineNumber;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    class SectionCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            setIcon(null);

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            Object userObj = node.getUserObject();
            if (userObj instanceof SectionInfo) {
                SectionInfo info = (SectionInfo) userObj;
                // Use tree's font as base to avoid compounding size on each repaint
                Font baseFont = tree.getFont();
                if (baseFont != null) {
                    if (info.level == 1) {
                        setFont(baseFont.deriveFont(Font.BOLD, baseFont.getSize2D() + 2f));
                    } else if (info.level == 2) {
                        setFont(baseFont.deriveFont(Font.BOLD));
                    } else {
                        setFont(baseFont.deriveFont(Font.PLAIN));
                    }
                }
            }
            return this;
        }
    }
}
