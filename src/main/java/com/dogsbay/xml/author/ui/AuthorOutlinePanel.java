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

package com.dogsbay.xml.author.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.AuthorDocument;
import com.dogsbay.xml.author.validation.ValidationIssue;

/**
 * Live tree of the document's blocks: click to select and scroll, collapse
 * sections, drag entries to reorder (edge drop) or reparent (middle drop).
 * Drops that would violate the content model are rejected while dragging.
 */
class AuthorOutlinePanel extends JPanel {

    private static final int PREVIEW_LENGTH = 28;

    private final AuthorEditorPanel panel;
    private final JTree tree;
    private final DefaultTreeModel model;
    private Map<String, List<ValidationIssue>> issuesByBlock = Map.of();
    private boolean syncing;

    AuthorOutlinePanel(AuthorEditorPanel panel) {
        super(new BorderLayout());
        this.panel = panel;

        model = new DefaultTreeModel(new DefaultMutableTreeNode());
        tree = new JTree(model);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new OutlineRenderer());
        tree.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        tree.addTreeSelectionListener(e -> {
            if (syncing) {
                return;
            }
            AuthorBlock block = blockAt(tree.getSelectionPath());
            if (block != null) {
                syncing = true;
                try {
                    panel.focusBlock(block, 0);
                } finally {
                    syncing = false;
                }
            }
        });

        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            tree.setDragEnabled(true);
            tree.setDropMode(DropMode.ON_OR_INSERT);
            tree.setTransferHandler(new OutlineMoveHandler());
        }

        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
    }

    // ------------------------------------------------------------------
    // Content
    // ------------------------------------------------------------------

    /** Rebuilds the tree, preserving expansion and selection by block id. */
    void refresh() {
        AuthorDocument doc = panel.getAuthorDocument();
        Set<String> expandedIds = new HashSet<>();
        for (int i = 0; i < tree.getRowCount(); i++) {
            AuthorBlock block = blockAt(tree.getPathForRow(i));
            if (block != null && tree.isExpanded(i)) {
                expandedIds.add(block.getId());
            }
        }
        String selectedId = panel.getSelectedBlock() != null
                ? panel.getSelectedBlock().getId() : null;

        if (doc == null || doc.getRoot() == null) {
            model.setRoot(new DefaultMutableTreeNode());
            return;
        }
        DefaultMutableTreeNode root = buildNode(doc.getRoot());
        model.setRoot(root);

        boolean firstBuild = expandedIds.isEmpty();
        Enumeration<?> all = root.depthFirstEnumeration();
        Map<String, DefaultMutableTreeNode> byId = new HashMap<>();
        while (all.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) all.nextElement();
            if (node.getUserObject() instanceof AuthorBlock block) {
                byId.put(block.getId(), node);
                if (firstBuild || expandedIds.contains(block.getId())) {
                    tree.expandPath(new TreePath(node.getPath()));
                }
            }
        }
        if (selectedId != null && byId.containsKey(selectedId)) {
            selectById(byId.get(selectedId));
        }
    }

    private DefaultMutableTreeNode buildNode(AuthorBlock block) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(block);
        for (AuthorBlock child : block.getChildren()) {
            node.add(buildNode(child));
        }
        return node;
    }

    /** Mirrors the panel's selection into the tree (no focus steal). */
    void showSelection(AuthorBlock block) {
        if (syncing || block == null) {
            return;
        }
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        Enumeration<?> all = root.depthFirstEnumeration();
        while (all.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) all.nextElement();
            if (node.getUserObject() instanceof AuthorBlock b && b == block) {
                selectById(node);
                return;
            }
        }
    }

    private void selectById(DefaultMutableTreeNode node) {
        syncing = true;
        try {
            TreePath path = new TreePath(node.getPath());
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
        } finally {
            syncing = false;
        }
    }

    void setIssues(Map<String, List<ValidationIssue>> issues) {
        issuesByBlock = issues;
        tree.repaint();
    }

    private AuthorBlock blockAt(TreePath path) {
        if (path == null) {
            return null;
        }
        Object node = path.getLastPathComponent();
        if (node instanceof DefaultMutableTreeNode mutable
                && mutable.getUserObject() instanceof AuthorBlock block) {
            return block;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private final class OutlineRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree jtree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean focused) {
            super.getTreeCellRendererComponent(jtree, value, selected, expanded, leaf, row, focused);
            setIcon(null);
            // must derive from the tree's font, not getFont() (size compounds otherwise)
            setFont(jtree.getFont());
            if (value instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof AuthorBlock block) {
                StringBuilder text = new StringBuilder();
                List<ValidationIssue> issues = issuesByBlock.get(block.getId());
                if (issues != null && !issues.isEmpty()) {
                    text.append(issues.stream().anyMatch(ValidationIssue::isError) ? "✕ " : "⚠ ");
                }
                text.append(block.getType().getLabel());
                String preview = block.getPlainText().strip();
                if (!preview.isEmpty()) {
                    if (preview.length() > PREVIEW_LENGTH) {
                        preview = preview.substring(0, PREVIEW_LENGTH) + "…";
                    }
                    text.append("  —  ").append(preview);
                }
                setText(text.toString());
            }
            return this;
        }
    }

    // ------------------------------------------------------------------
    // Drag and drop
    // ------------------------------------------------------------------

    private final class OutlineMoveHandler extends TransferHandler {

        private AuthorBlock dragged;

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected java.awt.datatransfer.Transferable createTransferable(JComponent c) {
            dragged = blockAt(tree.getSelectionPath());
            if (dragged == null || dragged.getParent() == null) {
                return null; // the root cannot move
            }
            return new java.awt.datatransfer.StringSelection(dragged.getId());
        }

        @Override
        public boolean canImport(TransferSupport support) {
            DropTarget target = resolveDrop(support);
            return target != null;
        }

        @Override
        public boolean importData(TransferSupport support) {
            DropTarget target = resolveDrop(support);
            if (target == null) {
                return false;
            }
            return panel.moveBlockTo(dragged, target.parent, target.index);
        }

        /** Validates the drop location against the content model. */
        private DropTarget resolveDrop(TransferSupport support) {
            if (dragged == null || !(support.getDropLocation()
                    instanceof JTree.DropLocation location) || location.getPath() == null) {
                return null;
            }
            AuthorBlock at = blockAt(location.getPath());
            if (at == null) {
                return null;
            }
            AuthorBlock parent;
            int index;
            if (location.getChildIndex() < 0) {
                parent = at;                                  // drop ON: nest inside
                index = at.getChildren().size();
            } else {
                parent = at;                                  // drop INSERT: among children
                index = location.getChildIndex();
            }
            AuthorDocument doc = panel.getAuthorDocument();
            if (doc == null || dragged.isAncestorOf(parent)
                    || !doc.getRegistry().isValidChild(parent.getType(), dragged.getType())) {
                return null;
            }
            return new DropTarget(parent, index);
        }
    }

    private record DropTarget(AuthorBlock parent, int index) {
    }
}
