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

package com.dogsbay.dogsbayaieditor.plugin.json;

import java.awt.BorderLayout;
import java.awt.Color;
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
 * Outline panel for JSON documents. Parses JSON structure and displays
 * object keys and array indices in a hierarchical tree. Clicking a node
 * navigates the editor to the corresponding line.
 */
public class JsonOutlinePanel extends ViewTreePanel implements FormatOutlinePanel {

    private JTree tree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private JScrollPane scrollPane;

    private DogsBayDocument document;
    private DogsBayAIEditor editor;

    private Document swingDocument;
    private DocumentListener swingDocListener;
    private Timer debounceTimer;

    public JsonOutlinePanel(DogsBayAIEditor parent) {
        super(new BorderLayout());

        this.editor = parent;

        rootNode = new DefaultMutableTreeNode("root");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new JsonCellRenderer());

        tree.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                TreePath path = tree.getSelectionPath();
                if (path == null) return;
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                Object userObj = node.getUserObject();
                if (userObj instanceof JsonNodeInfo) {
                    navigateToLine(((JsonNodeInfo) userObj).lineNumber);
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
        } catch (Exception e) {
            // ignore
        }
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
        try {
            parseJson(text, 0, rootNode);
        } catch (Exception e) {
            // Malformed JSON — show what we can
        }
        treeModel.reload();
        expandAll();
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

    /**
     * Simple recursive-descent JSON parser that builds the tree model.
     * Returns the position after the parsed value.
     */
    private int parseJson(String text, int pos, DefaultMutableTreeNode parent) {
        pos = skipWhitespaceAndComments(text, pos);
        if (pos >= text.length()) return pos;

        char c = text.charAt(pos);

        if (c == '{') {
            return parseObject(text, pos, parent);
        } else if (c == '[') {
            return parseArray(text, pos, parent);
        } else {
            // Primitive — skip it
            return skipValue(text, pos);
        }
    }

    private int parseObject(String text, int pos, DefaultMutableTreeNode parent) {
        pos++; // skip {
        pos = skipWhitespaceAndComments(text, pos);

        while (pos < text.length() && text.charAt(pos) != '}') {
            pos = skipWhitespaceAndComments(text, pos);
            if (pos >= text.length() || text.charAt(pos) == '}') break;

            // Parse key
            int lineNumber = countLines(text, pos);
            String key = parseString(text, pos);
            if (key == null) break;
            pos = skipString(text, pos);

            // Skip colon
            pos = skipWhitespaceAndComments(text, pos);
            if (pos < text.length() && text.charAt(pos) == ':') pos++;
            pos = skipWhitespaceAndComments(text, pos);

            // Peek at value type
            String valueDesc = peekValueDescription(text, pos);
            JsonNodeInfo info = new JsonNodeInfo(key, valueDesc, lineNumber);
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(info);
            parent.add(node);

            // Parse value — recurse for objects/arrays
            if (pos < text.length()) {
                char vc = text.charAt(pos);
                if (vc == '{' || vc == '[') {
                    pos = parseJson(text, pos, node);
                } else {
                    pos = skipValue(text, pos);
                }
            }

            // Skip comma
            pos = skipWhitespaceAndComments(text, pos);
            if (pos < text.length() && text.charAt(pos) == ',') pos++;
        }

        if (pos < text.length() && text.charAt(pos) == '}') pos++;
        return pos;
    }

    private int parseArray(String text, int pos, DefaultMutableTreeNode parent) {
        pos++; // skip [
        pos = skipWhitespaceAndComments(text, pos);
        int index = 0;

        while (pos < text.length() && text.charAt(pos) != ']') {
            pos = skipWhitespaceAndComments(text, pos);
            if (pos >= text.length() || text.charAt(pos) == ']') break;

            int lineNumber = countLines(text, pos);
            char vc = text.charAt(pos);

            if (vc == '{' || vc == '[') {
                String desc = (vc == '{') ? "{...}" : "[...]";
                JsonNodeInfo info = new JsonNodeInfo("[" + index + "]", desc, lineNumber);
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(info);
                parent.add(node);
                pos = parseJson(text, pos, node);
            } else {
                // Primitive in array — just skip
                pos = skipValue(text, pos);
            }

            pos = skipWhitespaceAndComments(text, pos);
            if (pos < text.length() && text.charAt(pos) == ',') pos++;
            index++;
        }

        if (pos < text.length() && text.charAt(pos) == ']') pos++;
        return pos;
    }

    private String peekValueDescription(String text, int pos) {
        if (pos >= text.length()) return "";
        char c = text.charAt(pos);
        if (c == '{') return "{...}";
        if (c == '[') return "[...]";
        if (c == '"') {
            String s = parseString(text, pos);
            if (s != null && s.length() > 40) s = s.substring(0, 37) + "...";
            return s != null ? "\"" + s + "\"" : "";
        }
        // Number, boolean, null — read to next delimiter
        int end = pos;
        while (end < text.length() && !isDelimiter(text.charAt(end))) end++;
        return text.substring(pos, end).trim();
    }

    private boolean isDelimiter(char c) {
        return c == ',' || c == '}' || c == ']' || c == '\n' || c == '\r';
    }

    private String parseString(String text, int pos) {
        if (pos >= text.length() || text.charAt(pos) != '"') return null;
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < text.length() && text.charAt(pos) != '"') {
            if (text.charAt(pos) == '\\' && pos + 1 < text.length()) {
                pos++;
                sb.append(text.charAt(pos));
            } else {
                sb.append(text.charAt(pos));
            }
            pos++;
        }
        return sb.toString();
    }

    private int skipString(String text, int pos) {
        if (pos >= text.length() || text.charAt(pos) != '"') return pos;
        pos++;
        while (pos < text.length() && text.charAt(pos) != '"') {
            if (text.charAt(pos) == '\\' && pos + 1 < text.length()) pos++;
            pos++;
        }
        if (pos < text.length()) pos++; // closing quote
        return pos;
    }

    private int skipValue(String text, int pos) {
        if (pos >= text.length()) return pos;
        char c = text.charAt(pos);
        if (c == '"') return skipString(text, pos);
        if (c == '{') return skipBlock(text, pos, '{', '}');
        if (c == '[') return skipBlock(text, pos, '[', ']');
        // Number, keyword — skip to delimiter
        while (pos < text.length() && !isDelimiter(text.charAt(pos))
                && text.charAt(pos) != '}' && text.charAt(pos) != ']') {
            pos++;
        }
        return pos;
    }

    private int skipBlock(String text, int pos, char open, char close) {
        int depth = 0;
        boolean inString = false;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (inString) {
                if (c == '\\' && pos + 1 < text.length()) { pos += 2; continue; }
                if (c == '"') inString = false;
            } else {
                if (c == '"') inString = true;
                else if (c == open) depth++;
                else if (c == close) { depth--; if (depth == 0) return pos + 1; }
            }
            pos++;
        }
        return pos;
    }

    private int skipWhitespaceAndComments(String text, int pos) {
        int len = text.length();
        while (pos < len) {
            char c = text.charAt(pos);
            if (Character.isWhitespace(c)) {
                pos++;
            } else if (c == '/' && pos + 1 < len && text.charAt(pos + 1) == '/') {
                while (pos < len && text.charAt(pos) != '\n') pos++;
            } else if (c == '/' && pos + 1 < len && text.charAt(pos + 1) == '*') {
                pos += 2;
                while (pos + 1 < len && !(text.charAt(pos) == '*' && text.charAt(pos + 1) == '/')) pos++;
                if (pos + 1 < len) pos += 2;
            } else {
                break;
            }
        }
        return pos;
    }

    private int countLines(String text, int pos) {
        int lines = 0;
        for (int i = 0; i < pos && i < text.length(); i++) {
            if (text.charAt(i) == '\n') lines++;
        }
        return lines;
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
            } catch (javax.swing.text.BadLocationException e) {
                // ignore
            }
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    @Override
    public void collapseAll() {
        for (int i = tree.getRowCount() - 1; i >= 0; i--) {
            tree.collapseRow(i);
        }
    }

    @Override
    public void setFocus() {
        tree.requestFocusInWindow();
    }

    public void setProperties() {}

    @Override
    public void updatePreferences() {}

    @Override
    public void cleanup() {
        removeSwingDocListener();
        removeAll();
    }

    // --- Data classes ---

    static class JsonNodeInfo {
        final String key;
        final String value;
        final int lineNumber;

        JsonNodeInfo(String key, String value, int lineNumber) {
            this.key = key;
            this.value = value;
            this.lineNumber = lineNumber;
        }

        @Override
        public String toString() {
            if (value != null && !value.isEmpty()) {
                return key + ": " + value;
            }
            return key;
        }
    }

    static class JsonCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            setIcon(null);

            if (value instanceof DefaultMutableTreeNode) {
                Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObj instanceof JsonNodeInfo) {
                    JsonNodeInfo info = (JsonNodeInfo) userObj;
                    Font baseFont = tree.getFont();
                    if (baseFont != null) {
                        if (info.value != null && (info.value.equals("{...}") || info.value.equals("[...]"))) {
                            setFont(baseFont.deriveFont(Font.BOLD));
                        } else {
                            setFont(baseFont.deriveFont(Font.PLAIN));
                        }
                    }
                }
            }
            return this;
        }
    }
}
