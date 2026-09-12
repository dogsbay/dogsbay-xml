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

package com.dogsbay.dogsbayaieditor.plugin.yaml;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;

import javax.swing.JScrollPane;
import javax.swing.JTree;
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
import com.dogsbay.xml.viewer.FormatOutlinePanel;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.ViewTreePanel;

/**
 * Outline panel for YAML documents. Shows keys hierarchically based on
 * indentation level. Clicking a node navigates to the corresponding line.
 */
public class YamlOutlinePanel extends ViewTreePanel implements FormatOutlinePanel {

    private JTree tree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private JScrollPane scrollPane;

    private DogsBayDocument document;
    private DogsBayAIEditor editor;

    private Document swingDocument;
    private DocumentListener swingDocListener;
    private Timer debounceTimer;

    public YamlOutlinePanel(DogsBayAIEditor parent) {
        super(new BorderLayout());
        this.editor = parent;

        rootNode = new DefaultMutableTreeNode("root");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new YamlCellRenderer());

        tree.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                TreePath path = tree.getSelectionPath();
                if (path == null) return;
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                Object userObj = node.getUserObject();
                if (userObj instanceof YamlNodeInfo) {
                    navigateToLine(((YamlNodeInfo) userObj).lineNumber);
                }
            }
        });

        scrollPane = new JScrollPane(tree,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        this.setBorder(new EmptyBorder(0, 0, 0, 0));
        this.add(scrollPane, BorderLayout.CENTER);

        debounceTimer = new Timer(500, e -> refreshOutline());
        debounceTimer.setRepeats(false);
    }

    @Override
    public void setDocument(DogsBayDocument document) {
        removeSwingDocListener();
        this.document = document;
        if (document != null) {
            installSwingDocListener();
            refreshOutline();
        } else {
            rootNode.removeAllChildren();
            treeModel.reload();
        }
    }

    private void installSwingDocListener() {
        try {
            com.dogsbay.dogsbayaieditor.DogsBayView view = editor.getView();
            if (view == null || view.getEditor() == null) return;
            com.dogsbay.xml.editor.EditorPanel ep = view.getEditor().getSelectedEditorPanel();
            if (ep == null) return;
            swingDocument = ep.getEditor().getDocument();
            swingDocListener = new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { debounceTimer.restart(); }
                public void removeUpdate(DocumentEvent e) { debounceTimer.restart(); }
                public void changedUpdate(DocumentEvent e) { debounceTimer.restart(); }
            };
            swingDocument.addDocumentListener(swingDocListener);
        } catch (Exception e) { /* ignore */ }
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
        if (text == null || text.isEmpty()) {
            rootNode.removeAllChildren();
            treeModel.reload();
            return;
        }

        rootNode.removeAllChildren();
        parseYaml(text);
        treeModel.reload();
        expandAll();
    }

    private String getEditorText() {
        if (swingDocument != null) {
            try { return swingDocument.getText(0, swingDocument.getLength()); }
            catch (Exception e) { /* fall through */ }
        }
        if (document != null) return document.getText();
        return null;
    }

    /**
     * Parse YAML by indentation level. Each line with a key: creates a tree node.
     * Indentation determines parent-child relationships.
     */
    private void parseYaml(String text) {
        String[] lines = text.split("\n", -1);

        // Stack of (indent, node) pairs to track hierarchy
        int[] indentStack = new int[64];
        DefaultMutableTreeNode[] nodeStack = new DefaultMutableTreeNode[64];
        int stackDepth = 0;

        indentStack[0] = -1;
        nodeStack[0] = rootNode;
        stackDepth = 1;

        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String line = lines[lineNum];

            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            // Document separator --- or ... resets hierarchy to root
            if (trimmed.equals("---") || trimmed.equals("...")) {
                if (rootNode.getChildCount() > 0) {
                    rootNode.add(new DefaultMutableTreeNode(
                            new YamlNodeInfo("────────────────", null, lineNum, false)));
                }
                // Reset stack so next keys attach to root
                stackDepth = 1;
                indentStack[0] = -1;
                nodeStack[0] = rootNode;
                continue;
            }

            int indent = getIndent(line);
            String content = trimmed;

            // Strip list marker
            boolean isList = false;
            if (content.startsWith("- ")) {
                content = content.substring(2).trim();
                isList = true;
                indent += 2; // Treat list items as one level deeper
            }

            // Find key
            String key = null;
            String value = null;
            int colonPos = findKeyColon(content);
            if (colonPos >= 0) {
                key = content.substring(0, colonPos).trim();
                value = content.substring(colonPos + 1).trim();
                if (value.isEmpty()) value = null;
            } else if (isList) {
                // Array item without key
                key = "- " + truncate(content, 40);
            } else {
                continue; // Skip lines we can't parse
            }

            if (key == null || key.isEmpty()) continue;

            // Pop stack to find parent at lower indent
            while (stackDepth > 1 && indentStack[stackDepth - 1] >= indent) {
                stackDepth--;
            }

            String display = value != null ? key + ": " + truncate(value, 40) : key;
            boolean hasChildren = value == null && colonPos >= 0; // key with no value = parent node

            YamlNodeInfo info = new YamlNodeInfo(key, value, lineNum, hasChildren);
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(info);
            nodeStack[stackDepth - 1].add(node);

            // Push this node as potential parent
            if (stackDepth < indentStack.length) {
                indentStack[stackDepth] = indent;
                nodeStack[stackDepth] = node;
                stackDepth++;
            }
        }
    }

    private int findKeyColon(String content) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inDoubleQuote) {
                if (c == '\\' && i + 1 < content.length()) { i++; continue; }
                if (c == '"') inDoubleQuote = false;
            } else if (inSingleQuote) {
                if (c == '\'') inSingleQuote = false;
            } else {
                if (c == '"') inDoubleQuote = true;
                else if (c == '\'') inSingleQuote = true;
                else if (c == ':' && (i + 1 >= content.length() || content.charAt(i + 1) == ' ')) return i;
                else if (c == '#') return -1;
            }
        }
        return -1;
    }

    private int getIndent(String line) {
        int indent = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') indent++;
            else if (line.charAt(i) == '\t') indent += 2;
            else break;
        }
        return indent;
    }

    private String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }

    private void navigateToLine(int lineNumber) {
        try {
            com.dogsbay.dogsbayaieditor.DogsBayView view = editor.getView();
            if (view == null || view.getEditor() == null) return;
            com.dogsbay.xml.editor.EditorPanel ep = view.getEditor().getSelectedEditorPanel();
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
                if (rect != null) textComp.scrollRectToVisible(rect);
            } catch (javax.swing.text.BadLocationException e) { /* ignore */ }
        } catch (Exception e) { /* ignore */ }
    }

    @Override public void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }
    @Override public void collapseAll() {
        for (int i = tree.getRowCount() - 1; i >= 0; i--) tree.collapseRow(i);
    }
    @Override public void setFocus() { tree.requestFocusInWindow(); }
    public void setProperties() {}
    @Override public void updatePreferences() {}
    @Override public void cleanup() { removeSwingDocListener(); removeAll(); }

    static class YamlNodeInfo {
        final String key;
        final String value;
        final int lineNumber;
        final boolean hasChildren;

        YamlNodeInfo(String key, String value, int lineNumber, boolean hasChildren) {
            this.key = key;
            this.value = value;
            this.lineNumber = lineNumber;
            this.hasChildren = hasChildren;
        }

        @Override public String toString() {
            if (value != null && !value.isEmpty()) return key + ": " + value;
            return key;
        }
    }

    static class YamlCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            setIcon(null);
            if (value instanceof DefaultMutableTreeNode) {
                Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObj instanceof YamlNodeInfo) {
                    YamlNodeInfo info = (YamlNodeInfo) userObj;
                    Font baseFont = tree.getFont();
                    if (info.key.startsWith("──")) {
                        // Separator line — grey, smaller
                        if (baseFont != null) setFont(baseFont.deriveFont(Font.PLAIN));
                        setForeground(java.awt.Color.GRAY);
                    } else if (baseFont != null) {
                        setFont(info.hasChildren ? baseFont.deriveFont(Font.BOLD) : baseFont.deriveFont(Font.PLAIN));
                    }
                }
            }
            return this;
        }
    }
}
