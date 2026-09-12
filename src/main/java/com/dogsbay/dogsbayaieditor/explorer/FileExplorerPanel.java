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

package com.dogsbay.dogsbayaieditor.explorer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.bounce.QTree;
import org.bounce.event.PopupListener;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.ViewTreePanel;
import com.dogsbay.dogsbayaieditor.explorer.actions.*;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

/**
 * File explorer panel providing a tree view of the file system.
 * Similar to VSCode's file explorer, allows browsing and opening files.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/22 $
 * @author DogsBay Ltd
 */
public class FileExplorerPanel extends ViewTreePanel {
    private static final boolean DEBUG = false;
    private static final int AUTO_REFRESH_INTERVAL = 3000; // 3 seconds

    private QTree tree;
    private DefaultTreeModel treeModel;
    private FileSystemNode root;
    private DogsBayAIEditor parent;

    // CardLayout center: the tree, or an empty state when no folder is open.
    private JPanel centerCards;
    private static final String CARD_TREE = "tree";
    private static final String CARD_EMPTY = "empty";

    // Toolbar buttons that need an open folder — disabled while the explorer is empty.
    private final java.util.List<JButton> rootActions = new java.util.ArrayList<>();
    private Timer autoRefreshTimer;

    /**
     * Creates a new file explorer panel.
     *
     * @param parent the main editor window
     */
    public FileExplorerPanel(DogsBayAIEditor parent) {
        super(new BorderLayout());
        this.parent = parent;

        if (DEBUG) {
            System.out.println("FileExplorerPanel: Initializing...");
        }

        // Initialize root directory from the last-opened folder. On a fresh install
        // (no last folder) we deliberately leave the explorer ROOTLESS and show an
        // empty state instead of dropping the user into their home directory.
        File startDir = null;
        String lastFolder = parent.getProperties().getLastOpenedFolder();

        if (lastFolder != null && !lastFolder.isEmpty()) {
            File lastDir = new File(lastFolder);
            if (lastDir.exists() && lastDir.isDirectory()) {
                startDir = lastDir;
                if (DEBUG) {
                    System.out.println("  Using last opened folder: " + startDir.getAbsolutePath());
                }
            }
        }

        root = (startDir != null) ? new FileSystemNode(startDir) : null;

        // Create tree model
        treeModel = new DefaultTreeModel(root);

        // Create tree
        tree = new FileExplorerTree(treeModel);
        tree.setBorder(new EmptyBorder(2, 2, 2, 2));
        tree.setShowsRootHandles(true);
        tree.setRootVisible(true);
        tree.setCellRenderer(new FileExplorerCellRenderer());
        tree.setRowHeight(18);
        tree.putClientProperty("JTree.lineStyle", "None");
        tree.setEditable(false);
        // Selected files can be dragged out as a file list: onto an agent chat box to mention
        // them, or onto anything else that takes files, such as a desktop or another tool.
        tree.setDragEnabled(true);
        tree.setTransferHandler(new FileDragExporter<>(FileSystemNode.class, FileSystemNode::getFile));

        // Enable tooltips
        ToolTipManager.sharedInstance().registerComponent(tree);

        // Create toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setRollover(true);
        toolbar.setBorder(new EmptyBorder(2, 2, 2, 2));

        // Title label
        JLabel titleLabel = new JLabel("Explorer");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 11f));
        titleLabel.setBorder(new EmptyBorder(0, 2, 0, 0));
        toolbar.add(titleLabel);

        // Add glue to push buttons to the right
        toolbar.add(Box.createHorizontalGlue());

        // Add New File button
        JButton newFileButton = new JButton();
        newFileButton.setToolTipText("New File");
        try {
            newFileButton
                    .setIcon(new ImageIcon(getClass().getResource("/com/dogsbay/dogsbayaieditor/icons/New16.gif")));
        } catch (Exception e) {
            newFileButton.setText("New File");
        }
        newFileButton.addActionListener(e -> {
            FileSystemNode selectedNode = getSelectedDirectoryNode();
            if (selectedNode != null) {
                new NewFileAction(parent, selectedNode, treeModel, tree).actionPerformed(null);
            }
        });
        toolbar.add(newFileButton);

        // Add New Folder button
        JButton newFolderButton = new JButton();
        newFolderButton.setToolTipText("New Folder");
        try {
            newFolderButton
                    .setIcon(new ImageIcon(getClass().getResource("/com/dogsbay/dogsbayaieditor/project/icons/FolderIcon.gif")));
        } catch (Exception e) {
            newFolderButton.setText("New Folder");
        }
        newFolderButton.addActionListener(e -> {
            FileSystemNode selectedNode = getSelectedDirectoryNode();
            if (selectedNode != null) {
                new NewFolderAction(parent, selectedNode, treeModel, tree).actionPerformed(null);
            }
        });
        toolbar.add(newFolderButton);

        // Add separator
        toolbar.addSeparator();

        // Add Refresh button
        JButton refreshButton = new JButton();
        refreshButton.setToolTipText("Refresh Explorer");
        try {
            refreshButton
                    .setIcon(new ImageIcon(getClass().getResource("/com/dogsbay/xml/editor/icons/Refresh16.gif")));
        } catch (Exception e) {
            refreshButton.setText("Refresh");
        }
        refreshButton.addActionListener(e -> refreshExpandedNodes());
        toolbar.add(refreshButton);

        // Add Collapse All button
        JButton collapseButton = new JButton();
        collapseButton.setToolTipText("Collapse All");
        try {
            collapseButton
                    .setIcon(new ImageIcon(getClass().getResource("/com/dogsbay/dogsbayaieditor/icons/CollapseAll.gif")));
        } catch (Exception e) {
            collapseButton.setText("Collapse All");
        }
        collapseButton.addActionListener(e -> {
            if (root != null) {
                // Collapse all rows
                for (int i = tree.getRowCount() - 1; i >= 0; i--) {
                    tree.collapseRow(i);
                }
                tree.expandRow(0); // Keep root expanded
            }
        });
        toolbar.add(collapseButton);

        // Show hidden files (dot files, .dogsbay/ with the agent audit log)
        javax.swing.JToggleButton hiddenButton = new javax.swing.JToggleButton();
        hiddenButton.setToolTipText("Show hidden files and folders");
        hiddenButton.setText("•");
        hiddenButton.setSelected(parent.getProperties().isFileExplorerShowHidden());
        FileSystemNode.setShowHidden(hiddenButton.isSelected());
        hiddenButton.addActionListener(e -> {
            boolean show = hiddenButton.isSelected();
            FileSystemNode.setShowHidden(show);
            parent.getProperties().setFileExplorerShowHidden(show);
            File current = getRootDirectory();
            if (current != null) {
                setRootDirectory(current);   // rebuild the tree with the new filter
            }
        });
        toolbar.add(hiddenButton);

        // These act on the open folder — disable them while the empty state shows.
        java.util.Collections.addAll(rootActions, newFileButton, newFolderButton, refreshButton, collapseButton);

        add(toolbar, BorderLayout.NORTH);

        // Center is a CardLayout: the tree, or an empty state when no folder is open.
        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        centerCards = new JPanel(new java.awt.CardLayout());
        centerCards.add(scrollPane, CARD_TREE);
        centerCards.add(createEmptyStatePanel(), CARD_EMPTY);
        add(centerCards, BorderLayout.CENTER);
        showCard(root != null ? CARD_TREE : CARD_EMPTY);

        // Add listeners
        tree.addTreeWillExpandListener(new LazyLoadListener());
        tree.addTreeSelectionListener(new SelectionListener());

        // Add click listener to open files (single-click or double-click based on preference)
        tree.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                // Check preference for single-click open
                boolean singleClickOpen = parent.getProperties().isFileExplorerSingleClickOpen();

                // Open on single-click if enabled, or always on double-click
                boolean shouldOpen = (singleClickOpen && e.getClickCount() == 1) ||
                                    (e.getClickCount() == 2);

                if (shouldOpen) {
                    FileSystemNode node = (FileSystemNode) tree.getLastSelectedPathComponent();
                    if (node != null && !node.isDirectory()) {
                        new OpenFromExplorerAction(parent, node).actionPerformed(null);
                    }
                }
            }
        });

        // Add context menu listener
        tree.addMouseListener(new PopupListener() {
            public void popupTriggered(MouseEvent e) {
                int row = tree.getRowForLocation(e.getX(), e.getY());
                if (row != -1) {
                    tree.setSelectionRow(row);
                    FileSystemNode node = (FileSystemNode) tree.getLastSelectedPathComponent();
                    if (node != null) {
                        showContextMenu(e.getX(), e.getY(), node);
                    }
                }
            }
        });

        // Add keyboard listener (Enter to open)
        tree.addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    FileSystemNode node = (FileSystemNode) tree.getLastSelectedPathComponent();
                    if (node != null && !node.isDirectory()) {
                        new OpenFromExplorerAction(parent, node).actionPerformed(null);
                    }
                }
            }
        });

        // Add keyboard shortcuts for Copy/Cut/Paste (only active when tree has focus)
        setupKeyboardShortcuts();

        // Pre-load and expand the root directory (only when a folder is open;
        // on a fresh install the explorer starts rootless with an empty state).
        if (root != null) {
            root.loadChildren();
            treeModel.nodeStructureChanged(root);
            tree.expandRow(0);
        }

        // Start auto-refresh timer
        startAutoRefresh();

        if (DEBUG) {
            System.out.println("FileExplorerPanel: Initialized");
        }
    }

    /**
     * Sets up keyboard shortcuts for Copy/Cut/Paste operations.
     * These shortcuts only work when the file explorer tree has focus.
     */
    private void setupKeyboardShortcuts() {
        // Get the input map for when the tree has focus
        InputMap inputMap = tree.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = tree.getActionMap();

        // Ctrl+C - Copy
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK), "explorer-copy");
        actionMap.put("explorer-copy", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FileSystemNode node = (FileSystemNode) tree.getLastSelectedPathComponent();
                if (node != null) {
                    new CopyAction(node, tree).actionPerformed(null);
                }
            }
        });

        // Ctrl+X - Cut
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.CTRL_DOWN_MASK), "explorer-cut");
        actionMap.put("explorer-cut", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FileSystemNode node = (FileSystemNode) tree.getLastSelectedPathComponent();
                if (node != null) {
                    new CutAction(node, treeModel, tree).actionPerformed(null);
                }
            }
        });

        // Ctrl+V - Paste
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, KeyEvent.CTRL_DOWN_MASK), "explorer-paste");
        actionMap.put("explorer-paste", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Use getSelectedDirectoryNode() to paste into parent if file is selected
                FileSystemNode targetNode = getSelectedDirectoryNode();
                if (targetNode != null) {
                    new PasteAction(parent, targetNode, treeModel, tree).actionPerformed(null);
                }
            }
        });

        // Delete key - Delete file/folder (with confirmation)
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "explorer-delete");
        actionMap.put("explorer-delete", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FileSystemNode node = (FileSystemNode) tree.getLastSelectedPathComponent();
                if (node != null) {
                    performDeleteWithSelection(node, true);
                }
            }
        });

        // Shift+Delete - Delete file/folder without confirmation
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, KeyEvent.SHIFT_DOWN_MASK), "explorer-delete-no-confirm");
        actionMap.put("explorer-delete-no-confirm", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FileSystemNode node = (FileSystemNode) tree.getLastSelectedPathComponent();
                if (node != null) {
                    performDeleteWithSelection(node, false);
                }
            }
        });

        // F2 - Rename
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "explorer-rename");
        actionMap.put("explorer-rename", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FileSystemNode node = (FileSystemNode) tree.getLastSelectedPathComponent();
                if (node != null) {
                    new RenameAction(parent, node, treeModel, tree).actionPerformed(null);
                }
            }
        });

        if (DEBUG) {
            System.out.println("FileExplorerPanel: Keyboard shortcuts configured");
        }
    }

    /**
     * Performs a delete operation with smart selection management.
     * After deleting, selects a nearby node (next sibling, previous sibling, or parent).
     *
     * @param node        the node to delete
     * @param showConfirm whether to show confirmation dialog
     */
    private void performDeleteWithSelection(FileSystemNode node, boolean showConfirm) {
        if (node == null) {
            return;
        }

        File file = node.getFile();

        if (DEBUG) {
            System.out.println("performDeleteWithSelection: Deleting " + file.getAbsolutePath() +
                             ", confirm=" + showConfirm);
        }

        // Show confirmation dialog if requested
        if (showConfirm) {
            String message;
            if (file.isDirectory()) {
                message = "Delete folder \"" + file.getName() + "\" and all its contents?\n\n" +
                         "This action cannot be undone.";
            } else {
                message = "Delete file \"" + file.getName() + "\"?\n\n" +
                         "This action cannot be undone.";
            }

            int result = MessageHandler.showConfirm(parent, message);
            if (result != MessageHandler.CONFIRM_YES_OPTION) {
                return;
            }
        }

        // Determine what to select after deletion (before we delete the node)
        FileSystemNode nodeToSelect = findNodeToSelectAfterDelete(node);

        // Delete on a background thread
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    if (file.isDirectory()) {
                        FileOperationUtils.deleteDirectory(file);
                    } else {
                        FileOperationUtils.deleteFile(file);
                    }

                    // Update UI on EDT
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            // Refresh the parent node to remove the deleted item
                            FileSystemNode parentNode = (FileSystemNode) node.getParent();
                            if (parentNode != null) {
                                parentNode.refresh(treeModel);
                                treeModel.nodeStructureChanged(parentNode);
                            }

                            // Select the remembered node
                            if (nodeToSelect != null) {
                                TreePath pathToSelect = new TreePath(nodeToSelect.getPath());
                                tree.setSelectionPath(pathToSelect);
                                tree.scrollPathToVisible(pathToSelect);
                            }
                        }
                    });

                } catch (Exception ex) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            MessageHandler.showError(
                                parent,
                                "Could not delete: " + ex.getMessage(),
                                "Delete Error"
                            );
                        }
                    });
                    if (DEBUG) ex.printStackTrace();
                }
            }
        });
        thread.start();
    }

    /**
     * Finds the best node to select after deleting the given node.
     * Tries: next sibling, previous sibling, parent.
     *
     * @param nodeToDelete the node being deleted
     * @return the node to select, or null
     */
    private FileSystemNode findNodeToSelectAfterDelete(FileSystemNode nodeToDelete) {
        if (nodeToDelete == null) {
            return null;
        }

        FileSystemNode parentNode = (FileSystemNode) nodeToDelete.getParent();
        if (parentNode == null) {
            return null; // Can't delete root
        }

        // Find the index of the node being deleted
        int index = parentNode.getIndex(nodeToDelete);

        // Try next sibling first
        if (index + 1 < parentNode.getChildCount()) {
            Object nextSibling = parentNode.getChildAt(index + 1);
            if (nextSibling instanceof FileSystemNode) {
                return (FileSystemNode) nextSibling;
            }
        }

        // Try previous sibling
        if (index - 1 >= 0) {
            Object prevSibling = parentNode.getChildAt(index - 1);
            if (prevSibling instanceof FileSystemNode) {
                return (FileSystemNode) prevSibling;
            }
        }

        // Fall back to parent
        return parentNode;
    }

    /**
     * Starts the auto-refresh timer to periodically check for file system changes.
     */
    private void startAutoRefresh() {
        if (autoRefreshTimer != null) {
            autoRefreshTimer.stop();
        }

        autoRefreshTimer = new Timer(AUTO_REFRESH_INTERVAL, e -> refreshExpandedNodes());
        autoRefreshTimer.setRepeats(true);
        autoRefreshTimer.start();

        if (DEBUG) {
            System.out.println(
                    "FileExplorerPanel: Auto-refresh started (every " + (AUTO_REFRESH_INTERVAL / 1000) + " seconds)");
        }
    }

    /**
     * Stops the auto-refresh timer.
     */
    private void stopAutoRefresh() {
        if (autoRefreshTimer != null) {
            autoRefreshTimer.stop();
            autoRefreshTimer = null;

            if (DEBUG) {
                System.out.println("FileExplorerPanel: Auto-refresh stopped");
            }
        }
    }

    /**
     * Refreshes all expanded nodes in the tree to detect file system changes.
     */
    private void refreshExpandedNodes() {
        if (tree == null || treeModel == null || root == null) {
            return;
        }

        // Refresh root if it's been loaded
        if (root.isLoaded()) {
            // Pass treeModel to handle events incrementally without collapsing
            root.refresh(treeModel);
        }

        // Recursively refresh all expanded child nodes
        refreshExpandedNodesRecursive(root);
    }

    /**
     * Recursively refreshes expanded directory nodes.
     *
     * @param node the node to check and refresh
     */
    private void refreshExpandedNodesRecursive(FileSystemNode node) {
        if (node == null || !node.isDirectory()) {
            return;
        }

        // Check if this node is expanded
        TreePath path = new TreePath(treeModel.getPathToRoot(node));
        boolean isExpanded = tree.isExpanded(path);

        if (isExpanded && node.isLoaded()) {
            // Refresh this node (incremental update)
            // Pass treeModel to handle events incrementally without collapsing
            node.refresh(treeModel);

            // Recursively refresh children
            int childCount = node.getChildCount();
            for (int i = 0; i < childCount; i++) {
                Object child = node.getChildAt(i);
                if (child instanceof FileSystemNode) {
                    refreshExpandedNodesRecursive((FileSystemNode) child);
                }
            }
        }
    }

    /**
     * Sets the root directory for the file explorer.
     *
     * @param directory the directory to show as root
     */
    private void showCard(String card) {
        if (centerCards != null) {
            ((java.awt.CardLayout) centerCards.getLayout()).show(centerCards, card);
        }
        boolean hasFolder = CARD_TREE.equals(card);
        for (JButton b : rootActions) {
            b.setEnabled(hasFolder);
        }
    }

    /** VSCode-style empty state shown when no folder is open. */
    private JPanel createEmptyStatePanel() {
        JPanel outer = new JPanel(new GridBagLayout());
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        JLabel msg = new JLabel("No folder open");
        msg.setForeground(UIManager.getColor("Label.disabledForeground"));
        msg.setAlignmentX(Component.CENTER_ALIGNMENT);
        msg.setBorder(new EmptyBorder(0, 0, 10, 0));
        col.add(msg);

        // Same order as the Welcome tab: the sample first, because someone with
        // no folder open is more likely to want something to look at than a
        // file dialogue. Two lists of the same two choices in two orders is a
        // small thing that makes a window feel unconsidered.
        col.add(emptyStateButton("Open Sample Project", e -> {
            if (parent != null) parent.getOpenSampleProjectAction().actionPerformed(null);
        }));
        col.add(Box.createVerticalStrut(6));
        col.add(emptyStateButton("Open Folder…", e -> {
            if (parent != null) parent.getOpenFolderAction().actionPerformed(null);
        }));

        outer.add(col);
        return outer;
    }

    private JButton emptyStateButton(String text, java.awt.event.ActionListener onClick) {
        JButton b = new JButton(text);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(200, b.getPreferredSize().height));
        b.addActionListener(onClick);
        return b;
    }

    public void setRootDirectory(File directory) {
        if (directory != null && directory.isDirectory()) {
            if (DEBUG) {
                System.out.println("FileExplorerPanel.setRootDirectory: " + directory.getAbsolutePath());
            }

            root = new FileSystemNode(directory);
            treeModel = new DefaultTreeModel(root);
            tree.setModel(treeModel);

            // A folder is open now — swap the empty state out for the tree.
            showCard(CARD_TREE);

            // Pre-load and expand the root
            root.loadChildren();
            treeModel.nodeStructureChanged(root);
            tree.expandRow(0);

            // Restart auto-refresh for the new directory
            stopAutoRefresh();
            startAutoRefresh();

            // Notify Git panel to refresh repository detection
            if (parent != null && parent.getGitPanel() != null) {
                parent.getGitPanel().refreshRepository();
            }
        }
    }

    /**
     * Gets the current root directory.
     *
     * @return the root directory, or null if not set
     */
    public File getRootDirectory() {
        return root != null ? root.getFile() : null;
    }

    /**
     * Gets the currently selected file, if any.
     *
     * @return the selected file, or null if no selection
     */
    public File getSelectedFile() {
        FileSystemNode node = (FileSystemNode) tree.getLastSelectedPathComponent();
        return node != null ? node.getFile() : null;
    }

    /**
     * Gets the currently selected directory node, or parent if file is selected, or root if nothing selected.
     *
     * @return the directory node for operations
     */
    private FileSystemNode getSelectedDirectoryNode() {
        FileSystemNode node = (FileSystemNode) tree.getLastSelectedPathComponent();

        if (node == null) {
            // Nothing selected, use root
            return root;
        }

        if (node.isDirectory()) {
            // Directory selected, use it
            return node;
        } else {
            // File selected, use parent directory
            return (FileSystemNode) node.getParent();
        }
    }

    /**
     * Saves the current expansion state of the file tree.
     * 
     * @return a set of string paths representing expanded nodes
     */
    private java.util.Set<String> saveTreeExpansionState() {
        java.util.Set<String> expandedPaths = new java.util.HashSet<>();

        if (root == null) {
            return expandedPaths;
        }

        // Enumerate all nodes and save paths of expanded ones
        java.util.Enumeration<?> enumeration = root.depthFirstEnumeration();
        int totalNodes = 0;
        int skippedNodes = 0;
        while (enumeration.hasMoreElements()) {
            Object element = enumeration.nextElement();
            totalNodes++;
            if (!(element instanceof FileSystemNode)) {
                skippedNodes++;
                continue; // Skip non-FileSystemNode elements
            }

            FileSystemNode node = (FileSystemNode) element;
            TreePath path = new TreePath(node.getPath());
            boolean isExpanded = tree.isExpanded(path);

            if (DEBUG) {
                System.out.println("FileExplorerPanel: Checking node: " + node.getFile().getAbsolutePath() +
                        " expanded=" + isExpanded);
            }

            if (isExpanded) {
                // Save the node's string representation as the path key
                String nodePath = getNodePath(node);
                expandedPaths.add(nodePath);
                if (DEBUG) {
                    System.out.println("FileExplorerPanel: Saving expanded path: " + nodePath);
                }
            }
        }

        if (DEBUG) {
            System.out.println("FileExplorerPanel: Checked " + totalNodes + " total nodes, skipped " +
                    skippedNodes + " non-FileSystemNode, saved " + expandedPaths.size() + " expanded paths");
        }
        return expandedPaths;
    }

    /**
     * Restores the expansion state of the file tree.
     * 
     * @param expandedPaths a set of string paths representing nodes that should be
     *                      expanded
     */
    private void restoreTreeExpansionState(java.util.Set<String> expandedPaths) {
        if (expandedPaths == null || expandedPaths.isEmpty()) {
            if (DEBUG) {
                System.out.println("FileExplorerPanel: No paths to restore");
            }
            return;
        }

        if (root == null) {
            return;
        }

        if (DEBUG) {
            System.out.println("FileExplorerPanel: Restoring " + expandedPaths.size() + " expanded paths");
        }

        int restoredCount = 0;
        // Enumerate all nodes and expand those that were previously expanded
        java.util.Enumeration<?> enumeration = root.depthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            Object element = enumeration.nextElement();
            if (!(element instanceof FileSystemNode)) {
                continue; // Skip non-FileSystemNode elements
            }

            FileSystemNode node = (FileSystemNode) element;
            String nodePath = getNodePath(node);

            if (expandedPaths.contains(nodePath)) {
                TreePath path = new TreePath(node.getPath());
                tree.expandPath(path);
                restoredCount++;
                if (DEBUG) {
                    System.out.println("FileExplorerPanel: Restored expanded path: " + nodePath);
                }
            }
        }

        if (DEBUG) {
            System.out
                    .println("FileExplorerPanel: Restored " + restoredCount + " of " + expandedPaths.size() + " paths");
        }
    }

    /**
     * Gets a string representation of a node's path for identifying it across tree
     * rebuilds.
     * 
     * @param node the tree node
     * @return a string path like "root/folder/subfolder"
     */
    private String getNodePath(FileSystemNode node) {
        Object[] path = node.getUserObjectPath();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.length; i++) {
            if (i > 0) {
                sb.append("/");
            }
            sb.append(path[i].toString());
        }
        return sb.toString();
    }

    /**
     * Expands all nodes in the tree.
     * Warning: This can be slow for large directory structures.
     */
    @Override
    public void expandAll() {
        if (tree != null) {
            tree.expandAll();
        }
    }

    /**
     * Collapses all nodes in the tree.
     */
    @Override
    public void collapseAll() {
        if (tree != null) {
            tree.collapseAll();
        }
    }

    /**
     * Refreshes the currently selected node.
     */
    public void refresh() {
        FileSystemNode node = (FileSystemNode) tree.getLastSelectedPathComponent();
        if (node != null) {
            node.refresh(treeModel);
        }
    }

    /**
     * Shows the context menu for the selected node.
     *
     * @param x    the x coordinate for the menu
     * @param y    the y coordinate for the menu
     * @param node the selected node
     */
    private void showContextMenu(int x, int y, FileSystemNode node) {
        JPopupMenu menu = new JPopupMenu();

        if (node.isDirectory()) {
            // Context menu for directories

            // "Open" is disabled for directories (but show placeholder)
            OpenFromExplorerAction openAction = new OpenFromExplorerAction(parent, node);
            menu.add(openAction);

            // New File and New Folder
            menu.add(new NewFileAction(parent, node, treeModel, tree));
            menu.add(new NewFolderAction(parent, node, treeModel, tree));

            // Refresh
            menu.add(new RefreshExplorerAction(node, treeModel, tree));
            menu.addSeparator();

            // Copy, Cut, Paste
            menu.add(new CopyAction(node, tree));
            menu.add(new CutAction(node, treeModel, tree));
            menu.add(new PasteAction(parent, node, treeModel, tree));
            menu.addSeparator();

            // Copy Path actions
            menu.add(new CopyPathAction(node));
            menu.add(new CopyRelativePathAction(node, getRootDirectory()));
            menu.addSeparator();

            // Rename and Delete
            menu.add(new RenameAction(parent, node, treeModel, tree));
            menu.add(new DeleteAction(parent, node, treeModel));

            // Reveal in System (if supported)
            if (Desktop.isDesktopSupported()) {
                menu.addSeparator();
                menu.add(new RevealInSystemAction(node));
            }

        } else {
            // Context menu for files

            // Open
            menu.add(new OpenFromExplorerAction(parent, node));
            menu.addSeparator();

            // Copy and Cut (no Paste for files - user must select folder)
            menu.add(new CopyAction(node, tree));
            menu.add(new CutAction(node, treeModel, tree));
            menu.addSeparator();

            // Copy Path actions
            menu.add(new CopyPathAction(node));
            menu.add(new CopyRelativePathAction(node, getRootDirectory()));
            menu.addSeparator();

            // Rename and Delete
            menu.add(new RenameAction(parent, node, treeModel, tree));
            menu.add(new RenameWithReferencesAction(parent, node, treeModel));
            menu.add(new RetargetAction(parent, node));
            menu.add(new DeleteAction(parent, node, treeModel));

            // Reveal in System (if supported)
            if (Desktop.isDesktopSupported()) {
                menu.addSeparator();
                menu.add(new RevealInSystemAction(node));
            }
        }

        menu.show(tree, x, y);
    }

    /**
     * Selects a file in the file explorer tree by its path.
     * Expands parent folders as needed and scrolls to make the file visible.
     *
     * @param file the file to select
     */
    public void selectFile(File file) {
        if (file == null || tree == null || root == null) {
            return;
        }

        if (DEBUG) {
            System.out.println("FileExplorerPanel.selectFile: " + file.getAbsolutePath());
        }

        // Find the node matching this file
        FileSystemNode targetNode = findNodeForFile(root, file);

        if (targetNode != null) {
            // Build the tree path to this node
            TreePath treePath = new TreePath(targetNode.getPath());

            // Expand parent nodes if needed
            TreePath parentPath = treePath.getParentPath();
            if (parentPath != null) {
                tree.expandPath(parentPath);
            }

            // Select the node
            tree.setSelectionPath(treePath);

            // Scroll to make it visible
            tree.scrollPathToVisible(treePath);

            if (DEBUG) {
                System.out.println("FileExplorerPanel: Selected file: " + file.getAbsolutePath());
            }
        } else {
            if (DEBUG) {
                System.out.println("FileExplorerPanel: Could not find node for file: " + file.getAbsolutePath());
            }
        }
    }

    /**
     * Recursively searches for a FileSystemNode matching the given file.
     *
     * @param node the node to start searching from
     * @param file the file to find
     * @return the matching node, or null if not found
     */
    private FileSystemNode findNodeForFile(FileSystemNode node, File file) {
        if (node == null || file == null) {
            return null;
        }

        // Check if this node matches the file
        try {
            if (node.getFile().getCanonicalPath().equals(file.getCanonicalPath())) {
                return node;
            }
        } catch (Exception e) {
            // If canonical path fails, try direct comparison
            if (node.getFile().getAbsolutePath().equals(file.getAbsolutePath())) {
                return node;
            }
        }

        // If this node is a directory, search its children
        if (node.isDirectory()) {
            // Ensure children are loaded
            if (!node.isLoaded()) {
                node.loadChildren();
                treeModel.nodeStructureChanged(node);
            }

            // Search children
            int childCount = node.getChildCount();
            for (int i = 0; i < childCount; i++) {
                Object child = node.getChildAt(i);
                if (child instanceof FileSystemNode) {
                    FileSystemNode result = findNodeForFile((FileSystemNode) child, file);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Sets focus to the file explorer tree.
     */
    @Override
    public void setFocus() {
        if (tree != null) {
            tree.requestFocusInWindow();
        }
    }

    /**
     * Updates the UI when preferences change.
     */
    @Override
    public void updatePreferences() {
        if (tree != null) {
            tree.updateUI();
        }
    }

    /**
     * Sets properties (currently no properties for file explorer).
     */
    @Override
    public void setProperties() {
        // No properties to set for file explorer in Phase 1
    }

    /**
     * Inner class for the tree component.
     * Extends QTree from org.bounce library for additional functionality.
     */
    private class FileExplorerTree extends QTree {
        public FileExplorerTree(TreeModel model) {
            super(model);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
    }

    /**
     * Listener for lazy loading directory contents on expansion.
     */
    private class LazyLoadListener implements TreeWillExpandListener {
        @Override
        public void treeWillExpand(TreeExpansionEvent event) {
            Object lastComponent = event.getPath().getLastPathComponent();

            if (lastComponent instanceof FileSystemNode) {
                FileSystemNode node = (FileSystemNode) lastComponent;

                if (!node.isLoaded()) {
                    if (DEBUG) {
                        System.out.println("LazyLoadListener: Loading " + node.getFile().getAbsolutePath());
                    }

                    node.loadChildren();
                    treeModel.nodeStructureChanged(node);
                }
            }
        }

        @Override
        public void treeWillCollapse(TreeExpansionEvent event) {
            // No action needed on collapse
        }
    }

    /**
     * Listener for tree selection changes.
     */
    private class SelectionListener implements TreeSelectionListener {
        @Override
        public void valueChanged(TreeSelectionEvent e) {
            Object lastComponent = tree.getLastSelectedPathComponent();

            if (lastComponent instanceof FileSystemNode) {
                FileSystemNode node = (FileSystemNode) lastComponent;

                if (DEBUG) {
                    System.out.println("SelectionListener: Selected " + node.getFile().getAbsolutePath());
                }

                // Update status bar (if parent editor has updateStatus method)
                // For Phase 1, we'll just log. In Phase 2, we can add more interaction.
            }
        }
    }
}
