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

package com.dogsbay.dogsbayaieditor.refactor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import com.dogsbay.dogsbayaieditor.refactor.HealthTreeBuilder.Node;

/**
 * Non-modal project health report. Double-click a finding to open the
 * file at the offending line; Refresh re-runs the analysis.
 */
public class HealthReportDialog extends JDialog {

    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final Supplier<Node> refresher;

    /**
     * @param refresher re-runs the health command and returns a fresh tree
     *                  (called on a background thread)
     * @param navigator opens a file at a 1-based line (line -1 = just open)
     */
    public HealthReportDialog(Frame owner, Node reportTree,
                              Supplier<Node> refresher, BiConsumer<java.io.File, Integer> navigator) {
        super(owner, "Project Health", false);
        this.refresher = refresher;

        treeModel = new DefaultTreeModel(toSwingNode(reportTree));
        tree = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setLeafIcon(null);
        renderer.setOpenIcon(null);
        renderer.setClosedIcon(null);
        tree.setCellRenderer(renderer);
        expandAll();

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) {
                    return;
                }
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    return;
                }
                Object userObject = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
                if (userObject instanceof NodeWrapper wrapper && wrapper.node.file() != null) {
                    navigator.accept(wrapper.node.file(), wrapper.node.line());
                }
            }
        });

        JPanel main = new JPanel(new BorderLayout(0, 8));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 8, 10));
        JScrollPane scroll = new JScrollPane(tree);
        scroll.setPreferredSize(new Dimension(640, 360));
        main.add(scroll, BorderLayout.CENTER);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refresh(refreshButton));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> setVisible(false));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(refreshButton);
        buttons.add(closeButton);
        main.add(buttons, BorderLayout.SOUTH);

        setContentPane(main);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
    }

    private void refresh(JButton refreshButton) {
        refreshButton.setEnabled(false);
        new Thread(() -> {
            Node fresh = refresher.get();
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (fresh != null) {
                    treeModel.setRoot(toSwingNode(fresh));
                    treeModel.reload();
                    expandAll();
                }
                refreshButton.setEnabled(true);
            });
        }).start();
    }

    private void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private static DefaultMutableTreeNode toSwingNode(Node node) {
        DefaultMutableTreeNode swingNode = new DefaultMutableTreeNode(new NodeWrapper(node));
        for (Node child : node.children()) {
            swingNode.add(toSwingNode(child));
        }
        return swingNode;
    }

    /** Wraps a descriptor node so the tree displays its label. */
    private record NodeWrapper(Node node) {
        @Override
        public String toString() {
            return node.label();
        }
    }
}
