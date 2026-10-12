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

import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

/**
 * Outline panel for Markdown documents. Parses headings from the Markdown
 * source and displays them in a hierarchical tree. Clicking a heading
 * navigates the editor to the corresponding line.
 */
public class MarkdownOutlinePanel extends ViewTreePanel implements DogsBayDocumentListener, FormatOutlinePanel {

    private static final Parser PARSER;

    static {
        MutableDataSet options = new MutableDataSet();
        PARSER = Parser.builder(options).build();
    }

    private JTree tree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private JScrollPane scrollPane;
    private ComponentAdapter resizeListener;

    private DogsBayDocument document;
    private DogsBayAIEditor editor;

    private Document swingDocument;
    private DocumentListener swingDocListener;
    private Timer debounceTimer;

    private boolean updatingSelection = false;

    public MarkdownOutlinePanel(DogsBayAIEditor parent) {
        super(new BorderLayout());

        this.editor = parent;

        rootNode = new DefaultMutableTreeNode("Document");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new HeadingCellRenderer());

        tree.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                if (updatingSelection) return;
                TreePath path = tree.getSelectionPath();
                if (path == null) return;

                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                Object userObj = node.getUserObject();
                if (userObj instanceof HeadingInfo) {
                    HeadingInfo info = (HeadingInfo) userObj;
                    navigateToLine(info.lineNumber);
                }
            }
        });

        scrollPane = new JScrollPane(tree,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        resizeListener = new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                scrollPane.doLayout();
            }
        };
        scrollPane.getViewport().addComponentListener(resizeListener);

        this.setBorder(new EmptyBorder(0, 0, 0, 0));
        this.add(scrollPane, BorderLayout.CENTER);

        // Debounce timer for live updates (400ms)
        debounceTimer = new Timer(400, e -> refreshOutline());
        debounceTimer.setRepeats(false);
    }

    public void setDocument(DogsBayDocument document) {
        // Remove old listeners
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
        // Get the Swing Document from the current editor
        com.dogsbay.dogsbayaieditor.DogsBayView view = editor.getView();
        if (view == null || view.getEditor() == null) {
            return;
        }
        Editor editor = view.getEditor();
        EditorPanel ep = editor.getSelectedEditorPanel();
        if (ep == null) {
            return;
        }
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

        List<HeadingInfo> headings = parseHeadings(text);
        rebuildTree(headings);
    }

    private String getEditorText() {
        // Read directly from Swing Document for up-to-date content
        if (swingDocument != null) {
            try {
                return swingDocument.getText(0, swingDocument.getLength());
            } catch (Exception e) {
                // fall through
            }
        }
        // Fallback to DogsBayDocument
        if (document != null) {
            return document.getText();
        }
        return null;
    }

    static List<HeadingInfo> parseHeadings(String markdown) {
        List<HeadingInfo> headings = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) return headings;

        Node doc = PARSER.parse(markdown);
        Node child = doc.getFirstChild();
        while (child != null) {
            if (child instanceof Heading) {
                Heading h = (Heading) child;
                int level = h.getLevel();
                String text = h.getText().toString();
                // Line numbers are 0-based in flexmark
                int line = h.getStartLineNumber();
                headings.add(new HeadingInfo(text, level, line));
            }
            child = child.getNext();
        }
        return headings;
    }

    private void rebuildTree(List<HeadingInfo> headings) {
        rootNode.removeAllChildren();

        // Build hierarchical tree based on heading levels
        // Use a stack to track nesting
        DefaultMutableTreeNode[] levelNodes = new DefaultMutableTreeNode[7]; // levels 1-6
        levelNodes[0] = rootNode;

        for (HeadingInfo info : headings) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(info);

            // Find the appropriate parent: closest ancestor with a lower heading level
            DefaultMutableTreeNode parent = rootNode;
            for (int l = info.level - 1; l >= 0; l--) {
                if (levelNodes[l] != null) {
                    parent = levelNodes[l];
                    break;
                }
            }

            parent.add(node);
            levelNodes[info.level] = node;

            // Clear deeper levels (a new H2 resets H3, H4, etc.)
            for (int l = info.level + 1; l <= 6; l++) {
                levelNodes[l] = null;
            }
        }

        treeModel.reload();
        expandAll();
    }

    private void navigateToLine(int lineNumber) {
        com.dogsbay.dogsbayaieditor.DogsBayView view = editor.getView();
        if (view == null || view.getEditor() == null) {
            return;
        }
        Editor editor = view.getEditor();
        EditorPanel ep = editor.getSelectedEditorPanel();
        if (ep == null) {
            return;
        }
        javax.swing.text.JTextComponent textComp = ep.getEditor();
        javax.swing.text.Document doc = textComp.getDocument();
        javax.swing.text.Element root = doc.getDefaultRootElement();
        if (lineNumber < 0 || lineNumber >= root.getElementCount()) {
            return;
        }
        javax.swing.text.Element lineElem = root.getElement(lineNumber);
        int startOffset = lineElem.getStartOffset();
        int endOffset = Math.min(lineElem.getEndOffset() - 1, doc.getLength());

        // Select the line and scroll to it
        textComp.select(startOffset, endOffset);

        // Ensure the caret position is visible by scrolling
        try {
            java.awt.Rectangle rect = textComp.modelToView(startOffset);
            if (rect != null) {
                textComp.scrollRectToVisible(rect);
            }
        } catch (javax.swing.text.BadLocationException e) {
            // ignore
        }

        textComp.requestFocusInWindow();
    }

    public void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    public void collapseAll() {
        for (int i = tree.getRowCount() - 1; i >= 1; i--) {
            tree.collapseRow(i);
        }
    }

    public void setFocus() {
        tree.requestFocusInWindow();
    }

    public void setProperties() {
    }

    public void updatePreferences() {
        tree.setCellRenderer(new HeadingCellRenderer());
        tree.updateUI();
    }

    // DogsBayDocumentListener
    public void documentUpdated(DogsBayDocumentEvent event) {
        // The Swing doc listener handles live updates; this is for structural changes
        SwingUtilities.invokeLater(() -> refreshOutline());
    }

    public void documentDeleted(DogsBayDocumentEvent event) {}

    public void cleanup() {
        removeSwingDocListener();
        if (document != null) {
            document.removeListener(this);
        }
        scrollPane.getViewport().removeComponentListener(resizeListener);
        removeAll();
    }

    /**
     * Information about a parsed heading.
     */
    static class HeadingInfo {
        final String text;
        final int level;
        final int lineNumber;

        HeadingInfo(String text, int level, int lineNumber) {
            this.text = text;
            this.level = level;
            this.lineNumber = lineNumber;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /**
     * Cell renderer that styles headings by level.
     */
    private static class HeadingCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (value instanceof DefaultMutableTreeNode) {
                Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObj instanceof HeadingInfo) {
                    HeadingInfo info = (HeadingInfo) userObj;
                    Font base = TextPreferences.getBaseFont();
                    if (info.level == 1) {
                        setFont(base.deriveFont(Font.BOLD, base.getSize() + 2f));
                    } else if (info.level == 2) {
                        setFont(base.deriveFont(Font.BOLD, base.getSize() + 1f));
                    } else if (info.level == 3) {
                        setFont(base.deriveFont(Font.BOLD));
                    } else {
                        setFont(base);
                    }
                    setIcon(null);
                }
            }
            return this;
        }
    }
}
