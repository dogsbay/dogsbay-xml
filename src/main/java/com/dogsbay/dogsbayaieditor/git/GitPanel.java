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

package com.dogsbay.dogsbayaieditor.git;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.ExplorerContainer;
import com.dogsbay.dogsbayaieditor.ViewTreePanel;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.dogsbayaieditor.URLUtilities;
import com.dogsbay.dogsbayaieditor.git.actions.*;
import org.bounce.QTree;
import org.bounce.event.PopupListener;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;

/**
 * Git source control panel with VSCode-style interface.
 * Shows changed files tree, commit message area, and commit history.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/23 $
 * @author DogsBay Ltd
 */
public class GitPanel extends ViewTreePanel {
    private static final boolean DEBUG = false;

    private DogsBayAIEditor parent;
    private QTree changesTree;
    private DefaultTreeModel treeModel;
    private JTextField commitMessageField;
    private QTree commitHistoryTree;
    private DefaultTreeModel historyTreeModel;
    private JLabel statusLabel;
    private File repositoryRoot;
    private Git git;
    private Timer autoRefreshTimer;

    // Toolbar buttons
    private JButton refreshButton;
    private JButton commitButton;

    // Icons for file status
    private static final ImageIcon ICON_MODIFIED = loadIcon("GitModifiedIcon.gif");
    private static final ImageIcon ICON_ADDED = loadIcon("GitAddedIcon.gif");
    private static final ImageIcon ICON_DELETED = loadIcon("GitDeletedIcon.gif");
    private static final ImageIcon ICON_STAGED = loadIcon("GitStagedIcon.gif");
    private static final ImageIcon ICON_CONFLICT = loadIcon("GitConflictIcon.gif");
    private static final ImageIcon ICON_UNTRACKED = loadIcon("GitAddedIcon.gif"); // Reuse added icon

    // Auto-refresh interval in milliseconds (5 seconds)
    private static final int AUTO_REFRESH_INTERVAL = 5000;

    // Icon management for badge overlay
    private static final ImageIcon BASE_GIT_ICON = loadIcon("sidebar/source-control.png");
    private int lastChangeCount = -1;

    /**
     * Creates a new Git panel.
     *
     * @param parent the parent DogsBayAIEditor
     */
    public GitPanel(DogsBayAIEditor parent) {
        super(new BorderLayout());
        this.parent = parent;

        if (DEBUG) {
            System.out.println("GitPanel: Initializing...");
        }

        initializeUI();

        // Detect Git repository
        detectAndLoadRepository();

        // Start auto-refresh timer if repository was found
        if (git != null) {
            startAutoRefresh();
        }

        if (DEBUG) {
            System.out.println("GitPanel: Initialized");
        }
    }

    /**
     * Package-private constructor for testing. Does not initialize UI or detect repository.
     *
     * @param git the Git instance to use
     */
    GitPanel(Git git) {
        super(new BorderLayout());
        this.parent = null;
        this.git = git;
    }

    /**
     * Package-private constructor for testing the refresh path: builds the UI
     * (so {@link #loadGitStatus()} can run) and reports the branch to
     * {@code branchSink} instead of a status bar there is no editor for.
     */
    GitPanel(Git git, File repositoryRoot, java.util.function.Consumer<String> branchSink) {
        super(new BorderLayout());
        this.parent = null;
        this.git = git;
        this.branchSink = branchSink;
        initializeUI();
        setRepositoryRoot(repositoryRoot);
    }

    /** Where the branch goes when there is no status bar (tests). */
    private java.util.function.Consumer<String> branchSink;

    /**
     * Starts the auto-refresh timer to periodically check for file changes.
     */
    private void startAutoRefresh() {
        if (autoRefreshTimer != null) {
            autoRefreshTimer.stop();
        }

        autoRefreshTimer = new Timer(AUTO_REFRESH_INTERVAL, e -> {
            if (git != null && repositoryRoot != null) {
                loadGitStatus();
            }
        });
        autoRefreshTimer.setRepeats(true);
        autoRefreshTimer.start();

        if (DEBUG) {
            System.out.println("GitPanel: Auto-refresh started (every " + (AUTO_REFRESH_INTERVAL / 1000) + " seconds)");
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
                System.out.println("GitPanel: Auto-refresh stopped");
            }
        }
    }

    /**
     * Loads an icon from resources.
     */
    private static ImageIcon loadIcon(String name) {
        try {
            URL url = GitPanel.class.getClassLoader().getResource(
                    "com/dogsbay/dogsbayaieditor/icons/" + name);
            if (url != null) {
                return new ImageIcon(url);
            }
        } catch (Exception e) {
            System.err.println("Failed to load icon: " + name);
        }
        return null;
    }

    /**
     * Creates a badged icon with a count overlay (VSCode-style).
     *
     * @param count the number to display in the badge
     * @return the icon with badge overlay
     */
    private ImageIcon createBadgedIcon(int count) {
        if (BASE_GIT_ICON == null || count <= 0) {
            return BASE_GIT_ICON;
        }

        // Get base icon dimensions
        int width = BASE_GIT_ICON.getIconWidth();
        int height = BASE_GIT_ICON.getIconHeight();

        // Create new image for composite
        BufferedImage composite = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = composite.createGraphics();

        // Enable antialiasing for smooth text and shapes
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Draw base icon
        g2d.drawImage(BASE_GIT_ICON.getImage(), 0, 0, null);

        // Prepare badge text
        String badgeText = count > 99 ? "99+" : String.valueOf(count);

        // Calculate badge size (scaled for icon dimensions)
        int fontSize = Math.max(7, width / 4);
        Font font = new Font("SansSerif", Font.BOLD, fontSize);
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(badgeText);
        int badgeHeight = Math.max(10, fm.getHeight());
        int badgeWidth = Math.max(badgeHeight, textWidth + 4);

        // Position badge in bottom-right corner
        int badgeX = width - badgeWidth;
        int badgeY = height - badgeHeight;

        // Draw badge background (VSCode blue)
        g2d.setColor(new Color(0, 122, 204));
        g2d.fillOval(badgeX, badgeY, badgeWidth, badgeHeight);

        // Draw badge border
        g2d.setColor(new Color(255, 255, 255, 180));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawOval(badgeX, badgeY, badgeWidth, badgeHeight);

        // Draw badge text
        g2d.setColor(Color.WHITE);
        int textX = badgeX + (badgeWidth - textWidth) / 2;
        int textY = badgeY + ((badgeHeight - fm.getHeight()) / 2) + fm.getAscent();
        g2d.drawString(badgeText, textX, textY);

        g2d.dispose();

        return new ImageIcon(composite);
    }

    /**
     * Updates the Git tab icon with a badge showing change count.
     *
     * @param changeCount the number of changes to display
     */
    private void updateTabIcon(int changeCount) {
        if (changeCount == lastChangeCount) {
            return; // No change, skip update
        }

        lastChangeCount = changeCount;

        // Create appropriate icon (with or without badge)
        ImageIcon icon = changeCount > 0 ? createBadgedIcon(changeCount) : BASE_GIT_ICON;

        // Update the explorer button icon
        if (parent != null) {
            SwingUtilities.invokeLater(() -> {
                ExplorerContainer container = parent.getExplorerContainer();
                if (container != null) {
                    container.updateExplorerIcon("git", icon);
                }
            });
        }
    }

    /**
     * Forces an immediate icon update.
     * Call this after the panel has been added to the ExplorerContainer.
     */
    public void refreshIconBadge() {
        // Reset last count to force update
        int currentCount = lastChangeCount;
        lastChangeCount = -1;
        updateTabIcon(currentCount);
    }

    /**
     * Initializes the UI components.
     */
    private void initializeUI() {
        // Main vertical split: Changes (top) and History/Diff (bottom)
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mainSplit.setResizeWeight(0.6); // Give more space to changes

        // Top panel: Changes tree and commit area
        JPanel topPanel = createChangesPanel();
        mainSplit.setTopComponent(topPanel);

        // Bottom panel: History panel (diffs now open in tabs)
        JPanel historyPanel = createHistoryPanel();
        mainSplit.setBottomComponent(historyPanel);

        add(mainSplit, BorderLayout.CENTER);

        // Status bar at bottom
        statusLabel = new JLabel("No Git repository detected");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        add(statusLabel, BorderLayout.SOUTH);
    }

    /**
     * Creates the changes panel (top part).
     */
    private JPanel createChangesPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Toolbar for Git operations
        JToolBar toolbar = createToolbar();
        panel.add(toolbar, BorderLayout.NORTH);

        // Changes tree
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Changes");
        treeModel = new DefaultTreeModel(root);
        changesTree = new QTree(treeModel) {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }
        };
        changesTree.setRootVisible(false);
        changesTree.setShowsRootHandles(true);
        changesTree.setCellRenderer(new GitCellRenderer());

        // Multi-selection over file nodes only: selecting a section header selects the
        // files beneath it, and the header itself never lands in the selection.
        changesTree.setSelectionModel(new GitFileSelectionModel());

        // Add double-click listener to show diff in tab
        changesTree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }

                // Act on the row under the pointer, not on the selection: clicking a
                // section header selects that section's files, and the first of those
                // would otherwise look like "the clicked file" and open a diff tab.
                // Returns null for clicks on the expand/collapse handle.
                TreePath path = changesTree.getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    return;
                }

                Object node = path.getLastPathComponent();
                if (!(node instanceof GitFileNode)) {
                    // A section header stands in for the files it contains.
                    selectSectionFiles(path);
                    return;
                }

                // Check preference for single-click open (reuse File Explorer preference)
                boolean singleClickOpen = parent.getProperties().isFileExplorerSingleClickOpen();
                boolean shouldOpen = (singleClickOpen && e.getClickCount() == 1) || (e.getClickCount() == 2);

                if (shouldOpen) {
                    GitFileNode fileNode = (GitFileNode) node;
                    openDiffInNewTab(fileNode.getFile(), null);
                }
            }
        });

        // Add context menu listener
        changesTree.addMouseListener(new PopupListener() {
            public void popupTriggered(java.awt.event.MouseEvent e) {
                int row = changesTree.getRowForLocation(e.getX(), e.getY());
                if (row != -1) {
                    // Only reset the selection when right-clicking outside it, so a
                    // multi-selection survives the context menu being opened on it.
                    if (!changesTree.isRowSelected(row)) {
                        changesTree.setSelectionRow(row);
                    }
                    java.util.List<GitFileNode> selected = getSelectedFileNodes();
                    if (!selected.isEmpty()) {
                        showContextMenu(e.getX(), e.getY(), selected);
                    }
                }
            }
        });

        // Add keyboard listener for Space/Enter to toggle stage/unstage
        changesTree.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_SPACE ||
                        e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                    java.util.List<GitFileNode> selected = getSelectedFileNodes();
                    if (!selected.isEmpty()) {
                        toggleStageUnstage(selected);
                        e.consume(); // Prevent default behavior
                    }
                }
            }
        });

        JScrollPane treeScroll = new JScrollPane(changesTree);
        treeScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        treeScroll.setBorder(BorderFactory.createTitledBorder("Changes"));

        // Commit message field (single line, with placeholder text)
        String placeholderText = "Message (Ctrl+Enter to commit on current branch)";
        commitMessageField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !hasFocus()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(UIManager.getColor("textInactiveText"));
                    g2.setFont(getFont());
                    Insets ins = getInsets();
                    g2.drawString(placeholderText, ins.left + 2, g.getFontMetrics().getAscent() + ins.top);
                    g2.dispose();
                }
            }
        };
        commitMessageField.setToolTipText(placeholderText);
        // Repaint on focus change to show/hide placeholder
        commitMessageField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent e) { commitMessageField.repaint(); }
            public void focusLost(java.awt.event.FocusEvent e) { commitMessageField.repaint(); }
        });
        // Ctrl+Enter to commit
        commitMessageField.getInputMap().put(
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_DOWN_MASK),
                "commitAction");
        commitMessageField.getActionMap().put("commitAction", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                new CommitAction(GitPanel.this).actionPerformed(e);
            }
        });

        // Commit split button: main "Commit" + dropdown arrow
        JPanel commitButtonPanel = createCommitButtonPanel();

        // Commit section: message field + split button in a compact row
        JPanel commitSection = new JPanel(new BorderLayout(4, 4));
        commitSection.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        commitSection.add(commitMessageField, BorderLayout.CENTER);
        commitSection.add(commitButtonPanel, BorderLayout.SOUTH);

        // Commit section at top, changes tree fills the rest
        JPanel topSection = new JPanel(new BorderLayout());
        topSection.add(toolbar, BorderLayout.NORTH);
        topSection.add(commitSection, BorderLayout.SOUTH);

        panel.remove(toolbar); // Remove toolbar from NORTH (we're re-adding it in topSection)
        panel.add(topSection, BorderLayout.NORTH);
        panel.add(treeScroll, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Creates the toolbar for Git operations.
     * Follows the standard explorer panel pattern: title label on the left,
     * icon-only action buttons on the right.
     */
    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setRollover(true);
        toolbar.setBorder(new javax.swing.border.EmptyBorder(2, 2, 2, 2));

        // Title label (matching other explorer panels)
        JLabel titleLabel = new JLabel("Git");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 11f));
        titleLabel.setBorder(new javax.swing.border.EmptyBorder(0, 2, 0, 0));
        toolbar.add(titleLabel);

        // Push buttons to the right
        toolbar.add(Box.createHorizontalGlue());

        // Commit button (icon-only, duplicates split button for quick access)
        JButton toolbarCommitButton = new JButton();
        toolbarCommitButton.setToolTipText("Commit staged changes");
        toolbarCommitButton.addActionListener(e -> new CommitAction(this).actionPerformed(e));
        try {
            toolbarCommitButton.setIcon(loadIcon("GitCommitIcon.gif"));
        } catch (Exception e) {
            toolbarCommitButton.setText("C");
        }
        toolbar.add(toolbarCommitButton);

        // Refresh button (icon-only)
        refreshButton = new JButton();
        refreshButton.setToolTipText("Refresh Git status");
        refreshButton.setEnabled(false);
        refreshButton.addActionListener(e -> refreshGitStatus());
        try {
            refreshButton.setIcon(loadIcon("GitRefreshIcon.gif"));
        } catch (Exception e) {
            refreshButton.setText("R");
        }
        toolbar.add(refreshButton);

        // More Actions button (...)
        JButton moreActionsButton = new JButton("\u2026");
        moreActionsButton.setToolTipText("More Actions");
        moreActionsButton.setFont(moreActionsButton.getFont().deriveFont(Font.BOLD));
        moreActionsButton.addActionListener(e -> {
            JPopupMenu menu = createMoreActionsMenu();
            menu.show(moreActionsButton, 0, moreActionsButton.getHeight());
        });
        toolbar.add(moreActionsButton);

        return toolbar;
    }

    /**
     * Creates the commit button panel with a split button:
     * main "Commit" button + dropdown arrow for commit variants.
     */
    private JPanel createCommitButtonPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));

        // Main commit button
        commitButton = new JButton("Commit");
        commitButton.setEnabled(false);
        commitButton.addActionListener(e -> new CommitAction(this).actionPerformed(e));
        try {
            commitButton.setIcon(loadIcon("GitCommitIcon.gif"));
        } catch (Exception ignored) {
        }

        // Dropdown arrow button
        JButton dropdownButton = new JButton("\u25BE"); // small down triangle
        dropdownButton.setMargin(new Insets(2, 2, 2, 2));
        dropdownButton.setPreferredSize(new Dimension(20, commitButton.getPreferredSize().height));
        dropdownButton.addActionListener(e -> {
            JPopupMenu commitMenu = new JPopupMenu();

            JMenuItem commitItem = new JMenuItem("Commit");
            commitItem.addActionListener(ev -> new CommitAction(this).actionPerformed(ev));
            commitMenu.add(commitItem);

            JMenuItem commitStagedItem = new JMenuItem("Commit Staged");
            commitStagedItem.addActionListener(ev -> new CommitStagedAction(this).actionPerformed(ev));
            commitMenu.add(commitStagedItem);

            JMenuItem commitAllItem = new JMenuItem("Commit All");
            commitAllItem.addActionListener(ev -> {
                new StageAllAction(this).actionPerformed(ev);
                new CommitAction(this).actionPerformed(ev);
            });
            commitMenu.add(commitAllItem);

            JMenuItem amendItem = new JMenuItem("Amend Last Commit");
            amendItem.addActionListener(ev -> new AmendCommitAction(this).actionPerformed(ev));
            commitMenu.add(amendItem);

            commitMenu.addSeparator();

            JMenuItem undoItem = new JMenuItem("Undo Last Commit");
            undoItem.addActionListener(ev -> new UndoLastCommitAction(this).actionPerformed(ev));
            commitMenu.add(undoItem);

            commitMenu.show(panel, 0, panel.getHeight());
        });

        panel.add(commitButton, BorderLayout.CENTER);
        panel.add(dropdownButton, BorderLayout.EAST);

        return panel;
    }

    /**
     * Creates the "More Actions" popup menu with nested submenus
     * for common Git commands (VSCode-style).
     */
    private JPopupMenu createMoreActionsMenu() {
        JPopupMenu menu = new JPopupMenu();

        // --- Commit submenu ---
        JMenu commitMenu = new JMenu("Commit");
        JMenuItem commitItem = new JMenuItem("Commit");
        commitItem.addActionListener(e -> new CommitAction(this).actionPerformed(e));
        commitMenu.add(commitItem);

        JMenuItem commitStagedItem = new JMenuItem("Commit Staged");
        commitStagedItem.addActionListener(e -> new CommitStagedAction(this).actionPerformed(e));
        commitMenu.add(commitStagedItem);

        JMenuItem commitAllItem = new JMenuItem("Commit All");
        commitAllItem.addActionListener(e -> {
            new StageAllAction(this).actionPerformed(e);
            new CommitAction(this).actionPerformed(e);
        });
        commitMenu.add(commitAllItem);

        JMenuItem amendItem = new JMenuItem("Amend Last Commit");
        amendItem.addActionListener(ev -> new AmendCommitAction(this).actionPerformed(ev));
        commitMenu.add(amendItem);

        commitMenu.addSeparator();

        JMenuItem undoCommitItem = new JMenuItem("Undo Last Commit");
        undoCommitItem.addActionListener(ev -> new UndoLastCommitAction(this).actionPerformed(ev));
        commitMenu.add(undoCommitItem);

        menu.add(commitMenu);

        // --- Changes submenu ---
        JMenu changesMenu = new JMenu("Changes");
        JMenuItem stageAllItem = new JMenuItem("Stage All");
        stageAllItem.addActionListener(new StageAllAction(this));
        changesMenu.add(stageAllItem);

        JMenuItem unstageAllItem = new JMenuItem("Unstage All");
        unstageAllItem.addActionListener(new UnstageAllAction(this));
        changesMenu.add(unstageAllItem);

        JMenuItem discardAllItem = new JMenuItem("Discard All Changes");
        discardAllItem.addActionListener(ev -> discardAllChanges());
        changesMenu.add(discardAllItem);

        menu.add(changesMenu);

        // --- Pull / Push submenu ---
        JMenu pullPushMenu = new JMenu("Pull / Push");
        JMenuItem pullItem = new JMenuItem("Pull");
        pullItem.setEnabled(git != null);
        pullItem.addActionListener(ev -> new PullAction(this).actionPerformed(ev));
        pullPushMenu.add(pullItem);

        JMenuItem pushItem = new JMenuItem("Push");
        pushItem.setEnabled(git != null);
        pushItem.addActionListener(ev -> new PushAction(this).actionPerformed(ev));
        pullPushMenu.add(pushItem);

        JMenuItem fetchItem = new JMenuItem("Fetch");
        fetchItem.setEnabled(git != null);
        fetchItem.addActionListener(ev -> new FetchAction(this).actionPerformed(ev));
        pullPushMenu.add(fetchItem);

        menu.add(pullPushMenu);

        // --- Branch submenu ---
        JMenu branchMenu = new JMenu("Branch");
        JMenuItem createBranchItem = new JMenuItem("Create Branch...");
        createBranchItem.setEnabled(git != null);
        createBranchItem.addActionListener(ev -> new CreateBranchAction(this).actionPerformed(ev));
        branchMenu.add(createBranchItem);

        JMenuItem checkoutBranchItem = new JMenuItem("Checkout Branch...");
        checkoutBranchItem.setEnabled(git != null);
        checkoutBranchItem.addActionListener(ev -> new CheckoutBranchAction(this).actionPerformed(ev));
        branchMenu.add(checkoutBranchItem);

        branchMenu.addSeparator();

        JMenuItem mergeBranchItem = new JMenuItem("Merge Branch...");
        mergeBranchItem.setEnabled(git != null);
        mergeBranchItem.addActionListener(ev -> new MergeBranchAction(this).actionPerformed(ev));
        branchMenu.add(mergeBranchItem);

        JMenuItem deleteBranchItem = new JMenuItem("Delete Branch...");
        deleteBranchItem.setEnabled(git != null);
        deleteBranchItem.addActionListener(ev -> new DeleteBranchAction(this).actionPerformed(ev));
        branchMenu.add(deleteBranchItem);

        menu.add(branchMenu);

        // --- Stash submenu ---
        JMenu stashMenu = new JMenu("Stash");
        JMenuItem stashItem = new JMenuItem("Stash");
        stashItem.setEnabled(git != null);
        stashItem.addActionListener(ev -> new StashAction(this).actionPerformed(ev));
        stashMenu.add(stashItem);

        JMenuItem popStashItem = new JMenuItem("Pop Stash");
        popStashItem.setEnabled(git != null);
        popStashItem.addActionListener(ev -> new PopStashAction(this).actionPerformed(ev));
        stashMenu.add(popStashItem);

        menu.add(stashMenu);

        return menu;
    }

    /**
     * Creates the history panel (bottom part).
     */
    private JPanel createHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Commit History"));

        // Create commit history tree (expandable commits showing files)
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Commits");
        historyTreeModel = new DefaultTreeModel(root);
        commitHistoryTree = new QTree(historyTreeModel) {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }
        };
        commitHistoryTree.setRootVisible(false);
        commitHistoryTree.setShowsRootHandles(true);
        commitHistoryTree.setCellRenderer(new CommitHistoryCellRenderer());
        ToolTipManager.sharedInstance().registerComponent(commitHistoryTree);

        // Add tree expansion listener to load files on demand
        commitHistoryTree.addTreeExpansionListener(new javax.swing.event.TreeExpansionListener() {
            @Override
            public void treeExpanded(javax.swing.event.TreeExpansionEvent event) {
                Object node = event.getPath().getLastPathComponent();
                if (node instanceof CommitNode) {
                    CommitNode commitNode = (CommitNode) node;
                    if (!commitNode.areFilesLoaded()) {
                        commitNode.loadFiles();
                        historyTreeModel.nodeStructureChanged(commitNode);
                    }
                }
            }

            @Override
            public void treeCollapsed(javax.swing.event.TreeExpansionEvent event) {
                // Nothing to do on collapse
            }
        });

        // Add double-click listener to show diff in tab for files in commits
        commitHistoryTree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                // Check preference for single-click open (reuse File Explorer preference)
                boolean singleClickOpen = parent.getProperties().isFileExplorerSingleClickOpen();
                boolean shouldOpen = (singleClickOpen && e.getClickCount() == 1) || (e.getClickCount() == 2);

                if (shouldOpen) {
                    Object node = commitHistoryTree.getLastSelectedPathComponent();
                    if (node instanceof CommitNode.FileChangeNode) {
                        // Clicked on a file in a commit - show its diff in tab
                        CommitNode.FileChangeNode fileNode = (CommitNode.FileChangeNode) node;
                        CommitNode commitNode = (CommitNode) fileNode.getParent();
                        if (commitNode != null) {
                            File file = new File(repositoryRoot, fileNode.getFileName());
                            openDiffInNewTab(file, commitNode.getCommit().getRevCommit());
                        }
                    }
                }
            }
        });

        JScrollPane treeScroll = new JScrollPane(commitHistoryTree);
        treeScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(treeScroll, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Sets the repository root directory.
     *
     * @param root the repository root directory
     */
    public void setRepositoryRoot(File root) {
        this.repositoryRoot = root;
        if (root != null) {
            String branchName = getCurrentBranch();
            statusLabel.setText("Repository: " + root.getName() + " [" + branchName + "]");
        } else {
            statusLabel.setText("No Git repository detected");
        }
    }

    /**
     * Gets the repository root directory.
     *
     * @return the repository root, or null if not set
     */
    public File getRepositoryRoot() {
        return repositoryRoot;
    }

    /**
     * Gets the current branch name.
     *
     * @return the current branch name, or "unknown" if not available
     */
    /** Show {@code branch} in the status bar, if there is one to show it in. */
    private void publishBranch(String branch) {
        if (branchSink != null) {
            branchSink.accept(branch);
            return;
        }
        if (parent != null && parent.getStatusbar() != null) {
            parent.getStatusbar().setBranch(branch);
        }
    }

    private String getCurrentBranch() {
        if (git == null) {
            return "unknown";
        }

        try {
            return git.getRepository().getBranch();
        } catch (Exception e) {
            if (DEBUG) {
                System.err.println("GitPanel: Failed to get current branch: " + e.getMessage());
            }
            return "unknown";
        }
    }

    // Implement abstract methods from ViewTreePanel

    @Override
    public void expandAll() {
        if (changesTree != null) {
            changesTree.expandAll();
        }
    }

    @Override
    public void collapseAll() {
        if (changesTree != null) {
            changesTree.collapseAll();
        }
    }

    @Override
    public void setFocus() {
        if (changesTree != null) {
            changesTree.requestFocusInWindow();
        }
    }

    @Override
    public void updatePreferences() {
        if (changesTree != null) {
            changesTree.updateUI();
        }
    }

    @Override
    public void setProperties() {
        // No properties to set for Git panel in Phase 1
    }

    /**
     * Detects and loads the Git repository.
     */
    private void detectAndLoadRepository() {
        File repoRoot = detectGitRepository();
        setRepositoryRoot(repoRoot);

        if (repoRoot != null) {
            try {
                git = Git.open(repoRoot);
                loadGitStatus();
                refreshButton.setEnabled(true);

                publishBranch(getCurrentBranch());
            } catch (Exception e) {
                if (DEBUG) {
                    System.err.println("GitPanel: Failed to open repository: " + e.getMessage());
                }
                statusLabel.setText("Error opening repository: " + e.getMessage());
            }
        } else {
            // No repository found - clear the UI
            clearPanelState();

            // Clear status bar branch
            if (parent != null && parent.getStatusbar() != null) {
                parent.getStatusbar().clearBranch();
            }
        }
    }

    /**
     * Clears the Git panel state when no repository is active.
     */
    private void clearPanelState() {
        // Clear changes tree
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.removeAllChildren();
        treeModel.reload();

        // Clear commit history tree
        DefaultMutableTreeNode historyRoot = (DefaultMutableTreeNode) historyTreeModel.getRoot();
        historyRoot.removeAllChildren();
        historyTreeModel.reload();

        // Clear icon badge
        updateTabIcon(0);

        // Disable buttons
        refreshButton.setEnabled(false);
        commitButton.setEnabled(false);

        if (DEBUG) {
            System.out.println("GitPanel: Cleared panel state (no repository)");
        }
    }

    /**
     * Detects the Git repository by walking up the directory tree.
     * Checks the File Explorer's root directory first, then falls back to current
     * document,
     * then working directory.
     *
     * @return The repository root directory, or null if not found
     */
    private File detectGitRepository() {
        File dir = null;

        // Priority 1: Try to get directory from File Explorer (like VSCode workspace)
        if (parent.getFileExplorer() != null) {
            dir = parent.getFileExplorer().getRootDirectory();
            if (dir != null && dir.exists() && dir.isDirectory()) {
                if (DEBUG) {
                    System.out.println("GitPanel: Using File Explorer directory: " + dir);
                }
            } else {
                dir = null;
            }
        }

        // Priority 2: If no File Explorer directory, try currently open document
        if (dir == null) {
            DogsBayDocument currentDoc = parent.getDocument();
            if (currentDoc != null && currentDoc.getURL() != null) {
                File docFile = URLUtilities.toFile(currentDoc.getURL());
                if (docFile != null && docFile.getParentFile() != null) {
                    dir = docFile.getParentFile();
                    if (DEBUG) {
                        System.out.println("GitPanel: Using document directory: " + dir);
                    }
                }
            }
        }

        // Priority 3: Fallback to current working directory
        if (dir == null) {
            dir = new File(System.getProperty("user.dir"));
            if (DEBUG) {
                System.out.println("GitPanel: Using working directory: " + dir);
            }
        }

        // Walk up directory tree looking for .git folder
        File searchDir = dir;
        while (searchDir != null) {
            File gitDir = new File(searchDir, ".git");
            if (gitDir.exists() && gitDir.isDirectory()) {
                if (DEBUG) {
                    System.out.println("GitPanel: Found Git repository at: " + searchDir);
                }
                return searchDir;
            }
            searchDir = searchDir.getParentFile();
        }

        if (DEBUG) {
            System.out.println("GitPanel: No Git repository found");
        }
        return null;
    }

    /**
     * Saves the current expansion state of the changes tree.
     * 
     * @return a set of string paths representing expanded nodes
     */
    private java.util.Set<String> saveTreeExpansionState() {
        java.util.Set<String> expandedPaths = new java.util.HashSet<>();

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        if (root == null) {
            return expandedPaths;
        }

        // Enumerate all nodes and save paths of expanded ones
        java.util.Enumeration<?> enumeration = root.depthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) enumeration.nextElement();
            TreePath path = new TreePath(node.getPath());

            if (changesTree.isExpanded(path)) {
                // Save the node's string representation as the path key
                expandedPaths.add(getNodePath(node));
            }
        }

        return expandedPaths;
    }

    /**
     * Restores the expansion state of the changes tree.
     * 
     * @param expandedPaths a set of string paths representing nodes that should be
     *                      expanded
     */
    private void restoreTreeExpansionState(java.util.Set<String> expandedPaths) {
        if (expandedPaths == null || expandedPaths.isEmpty()) {
            // If no saved state, expand all by default (first-time behavior)
            changesTree.expandAll();
            return;
        }

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        if (root == null) {
            return;
        }

        // Enumerate all nodes and expand those that were previously expanded
        java.util.Enumeration<?> enumeration = root.depthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) enumeration.nextElement();
            String nodePath = getNodePath(node);

            if (expandedPaths.contains(nodePath)) {
                TreePath path = new TreePath(node.getPath());
                changesTree.expandPath(path);
            }
        }
    }

    /**
     * Saves the relative paths of the currently selected files.
     *
     * <p>{@code treeModel.reload()} discards the selection, so a bulk operation that
     * refreshes the status would otherwise clear the selection out from under a
     * follow-up operation on the same files.
     *
     * @return the relative paths of the selected files
     */
    private java.util.Set<String> saveTreeSelectionState() {
        java.util.Set<String> selected = new java.util.LinkedHashSet<>();
        for (GitFileNode node : getSelectedFileNodes()) {
            selected.add(selectionKey(node));
        }
        return selected;
    }

    /**
     * Builds the key used to re-find a file node after the tree is rebuilt.
     *
     * <p>The relative path alone is not enough: a file that is staged and then edited
     * again is reported by JGit in both {@code getChanged()} and {@code getModified()},
     * so it has a row in <em>each</em> section. Keying on path alone would reselect both
     * rows when the user had selected one, and a subsequent stage/unstage toggle would
     * then run both operations on the same file. The section is part of the identity.
     *
     * <p>The section's display label can't be used because it carries a count that
     * changes between refreshes; whether the node is staged is exactly what decides which
     * section it was placed in.
     *
     * @param node the file node
     * @return a key stable across tree rebuilds
     */
    private String selectionKey(GitFileNode node) {
        return (isStaged(node) ? "staged:" : "changes:") + node.getRelativePath();
    }

    /**
     * Restores the selection after a tree rebuild, matching on relative path. Files that
     * no longer appear in the status (because the operation resolved them) are dropped.
     *
     * @param selectedPaths the relative paths to reselect
     */
    private void restoreTreeSelectionState(java.util.Set<String> selectedPaths) {
        if (selectedPaths == null || selectedPaths.isEmpty()) {
            return;
        }

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        if (root == null) {
            return;
        }

        java.util.List<TreePath> paths = new java.util.ArrayList<>();
        java.util.Enumeration<?> enumeration = root.depthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            Object node = enumeration.nextElement();
            if (node instanceof GitFileNode) {
                GitFileNode fileNode = (GitFileNode) node;
                if (selectedPaths.contains(selectionKey(fileNode))) {
                    paths.add(new TreePath(fileNode.getPath()));
                }
            }
        }

        if (!paths.isEmpty()) {
            changesTree.setSelectionPaths(paths.toArray(new TreePath[0]));
        }
    }

    /**
     * Gets a string representation of a node's path for identifying it across tree
     * rebuilds.
     * 
     * @param node the tree node
     * @return a string path like "root/Staged Changes/file.txt"
     */
    private String getNodePath(DefaultMutableTreeNode node) {
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
     * Loads the Git status and populates the changes tree.
     */
    void loadGitStatus() {
        if (git == null || repositoryRoot == null) {
            return;
        }

        try {
            // Force JGit to re-read the index from disk so status reflects
            // any changes made since the last call (e.g. after a commit)
            git.getRepository().notifyIndexChanged(false);

            // Save expansion and selection state before clearing
            java.util.Set<String> expandedPaths = saveTreeExpansionState();
            java.util.Set<String> selectedPaths = saveTreeSelectionState();

            Status status = git.status().call();

            // Clear the tree
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
            root.removeAllChildren();

            // Create section nodes
            DefaultMutableTreeNode stagedNode = new DefaultMutableTreeNode("Staged Changes");
            DefaultMutableTreeNode changesNode = new DefaultMutableTreeNode("Changes");

            int stagedCount = 0;
            int changesCount = 0;

            // Add staged files (added to index)
            for (String file : status.getAdded()) {
                stagedNode.add(new GitFileNode(
                        new File(repositoryRoot, file),
                        GitFileNode.GitStatus.ADDED,
                        file));
                stagedCount++;
            }

            // Add staged modified files
            for (String file : status.getChanged()) {
                stagedNode.add(new GitFileNode(
                        new File(repositoryRoot, file),
                        GitFileNode.GitStatus.STAGED,
                        file));
                stagedCount++;
            }

            // Add staged removed files
            for (String file : status.getRemoved()) {
                stagedNode.add(new GitFileNode(
                        new File(repositoryRoot, file),
                        GitFileNode.GitStatus.REMOVED,
                        file));
                stagedCount++;
            }

            // Add unstaged modified files
            for (String file : status.getModified()) {
                changesNode.add(new GitFileNode(
                        new File(repositoryRoot, file),
                        GitFileNode.GitStatus.MODIFIED,
                        file));
                changesCount++;
            }

            // Add untracked files
            for (String file : status.getUntracked()) {
                changesNode.add(new GitFileNode(
                        new File(repositoryRoot, file),
                        GitFileNode.GitStatus.UNTRACKED,
                        file));
                changesCount++;
            }

            // Add missing (deleted) files
            for (String file : status.getMissing()) {
                changesNode.add(new GitFileNode(
                        new File(repositoryRoot, file),
                        GitFileNode.GitStatus.DELETED,
                        file));
                changesCount++;
            }

            // Add conflicting files
            for (String file : status.getConflicting()) {
                changesNode.add(new GitFileNode(
                        new File(repositoryRoot, file),
                        GitFileNode.GitStatus.CONFLICT,
                        file));
                changesCount++;
            }

            // Add sections to tree if they have children
            if (stagedCount > 0) {
                stagedNode.setUserObject("Staged Changes (" + stagedCount + ")");
                root.add(stagedNode);
            }
            if (changesCount > 0) {
                changesNode.setUserObject("Changes (" + changesCount + ")");
                root.add(changesNode);
            }

            // Update tree
            treeModel.reload();

            // Restore expansion and selection state
            restoreTreeExpansionState(expandedPaths);
            restoreTreeSelectionState(selectedPaths);

            // Update status label
            int totalChanges = stagedCount + changesCount;
            String branchName = getCurrentBranch();
            // Every refresh, not just a repository open: a branch can change
            // under the editor (an agent or a terminal running git checkout),
            // and the status bar went on naming the branch we started on.
            publishBranch(branchName);
            String repoName = repositoryRoot.getName();

            if (totalChanges > 0) {
                statusLabel.setText("Repository: " + repoName + " [" + branchName + "] (" + totalChanges + " changes)");
            } else {
                statusLabel.setText("Repository: " + repoName + " [" + branchName + "] (clean)");
            }

            // Enable commit button if there are any changes (smart commit auto-stages if needed)
            commitButton.setEnabled(totalChanges > 0);

            // Update tab icon with badge showing change count
            updateTabIcon(totalChanges);

            // Load commit history
            loadCommitHistory();

        } catch (Exception e) {
            if (DEBUG) {
                System.err.println("GitPanel: Failed to load Git status: " + e.getMessage());
                e.printStackTrace();
            }
            statusLabel.setText("Error loading Git status: " + e.getMessage());
        }
    }

    /**
     * Loads the commit history and populates the history tree.
     * Shows the most recent 50 commits as expandable nodes.
     * Preserves expansion state across refreshes.
     */
    private void loadCommitHistory() {
        if (git == null || repositoryRoot == null) {
            return;
        }

        try {
            // Save currently expanded commit IDs
            java.util.Set<String> expandedCommitIds = new java.util.HashSet<>();
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) historyTreeModel.getRoot();

            for (int i = 0; i < root.getChildCount(); i++) {
                Object child = root.getChildAt(i);
                if (child instanceof CommitNode) {
                    CommitNode node = (CommitNode) child;
                    javax.swing.tree.TreePath path = new javax.swing.tree.TreePath(
                            new Object[] { root, node });
                    if (commitHistoryTree.isExpanded(path)) {
                        expandedCommitIds.add(node.getCommit().getCommitId());
                    }
                }
            }

            // Clear the tree
            root.removeAllChildren();

            // Get the last 50 commits
            Iterable<org.eclipse.jgit.revwalk.RevCommit> commits = git.log()
                    .setMaxCount(50)
                    .call();

            // Add each commit as an expandable node
            int count = 0;
            for (org.eclipse.jgit.revwalk.RevCommit commit : commits) {
                CommitHistoryItem item = new CommitHistoryItem(commit);
                CommitNode commitNode = new CommitNode(item, git);
                root.add(commitNode);
                count++;
            }

            // Reload the tree
            historyTreeModel.reload();

            // Restore expansion state
            for (int i = 0; i < root.getChildCount(); i++) {
                Object child = root.getChildAt(i);
                if (child instanceof CommitNode) {
                    CommitNode node = (CommitNode) child;
                    if (expandedCommitIds.contains(node.getCommit().getCommitId())) {
                        // Load files if not already loaded
                        if (!node.areFilesLoaded()) {
                            node.loadFiles();
                            historyTreeModel.nodeStructureChanged(node);
                        }
                        // Expand the node
                        javax.swing.tree.TreePath path = new javax.swing.tree.TreePath(
                                new Object[] { root, node });
                        commitHistoryTree.expandPath(path);
                    }
                }
            }

            if (DEBUG) {
                System.out.println("GitPanel: Loaded " + count + " commits, restored " +
                        expandedCommitIds.size() + " expanded nodes");
            }

        } catch (Exception e) {
            if (DEBUG) {
                System.err.println("GitPanel: Failed to load commit history: " + e.getMessage());
                e.printStackTrace();
            }
            // Don't show error to user - just leave history tree empty
        }
    }

    /**
     * Refreshes the Git status by reloading from the repository.
     */
    public void refreshGitStatus() {
        loadGitStatus();
    }

    /**
     * Re-detects the Git repository and refreshes status.
     * Call this when the File Explorer changes directories.
     */
    public void refreshRepository() {
        // Stop auto-refresh
        stopAutoRefresh();

        // Close existing Git instance if any
        if (git != null) {
            git.close();
            git = null;
        }

        // Re-detect repository
        detectAndLoadRepository();

        // Restart auto-refresh if repository was found
        if (git != null) {
            startAutoRefresh();
        }
    }

    /**
     * Tells open document buffers that files changed underneath them.
     *
     * <p>A Git operation that rewrites working-tree files — discarding changes, for
     * instance — leaves any tab holding one of those files showing the content that was
     * just thrown away, and saving that tab would write it straight back. The editor's
     * auto-reload normally catches external edits, but it is driven by a window-focus
     * listener, and an operation performed inside the app never loses window focus, so
     * nothing would fire.
     *
     * <p>Clean buffers reload silently; dirty ones prompt, or defer to tab focus if they
     * are in the background. Same pattern as the map and reltable editors, which call
     * this after writing files of their own.
     */
    public void reloadOpenBuffers() {
        if (parent != null) {
            parent.checkAllDocumentsForExternalChanges();
        }
    }

    /**
     * Gets the Git instance for this panel.
     *
     * @return the Git instance, or null if no repository is open
     */
    public Git getGit() {
        return git;
    }

    /**
     * Opens a file from the Git changes tree in the editor.
     *
     * @param node the Git file node to open
     */
    private void openFile(GitFileNode node) {
        if (node == null) {
            return;
        }

        File file = node.getFile();
        if (file == null || !file.exists() || file.isDirectory()) {
            return;
        }

        if (DEBUG) {
            System.out.println("GitPanel: Opening file " + file.getAbsolutePath());
        }

        try {
            // Convert file to URL and open in editor
            java.net.URL url = com.dogsbay.xml.DogsBayURLUtilities.getURLFromFile(file);
            parent.open(url, null, true);
        } catch (Exception e) {
            if (DEBUG) {
                System.err.println("GitPanel: Failed to open file: " + e.getMessage());
                e.printStackTrace();
            }
            javax.swing.JOptionPane.showMessageDialog(
                    this,
                    "Could not open file: " + file.getName() + "\n" + e.getMessage(),
                    "Open File Error",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Shows the context menu for a Git file node.
     *
     * @param x    the x coordinate for the menu
     * @param y    the y coordinate for the menu
     * @param node the selected Git file node
     */
    private void showContextMenu(int x, int y, java.util.List<GitFileNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        if (nodes.size() > 1) {
            showMultiSelectionContextMenu(x, y, nodes);
            return;
        }

        GitFileNode node = nodes.get(0);
        JPopupMenu menu = new JPopupMenu();

        // Determine if file is staged
        boolean isStaged = isStaged(node);

        if (!isStaged) {
            // Unstaged file: offer Stage option
            menu.add(new StageFileAction(this, node));
            menu.addSeparator();
        } else {
            // Staged file: offer Unstage option
            menu.add(new UnstageFileAction(this, node));
            menu.addSeparator();
        }

        // Common options
        menu.add(new AbstractAction("Open") {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openFile(node);
            }
        });

        // XML Diff for all changed files
        // TODO: In future, can add extension filter for known XML types (.xml, .dita,
        // .ditamap, .concept, etc.)
        menu.add(new com.dogsbay.dogsbayaieditor.git.actions.XmlDiffAction(this, node, parent));

        // Discard changes (destructive operation)
        menu.addSeparator();
        menu.add(new DiscardChangesAction(this, node));

        menu.show(changesTree, x, y);
    }

    /**
     * Shows the context menu for a multi-file selection. Only operations that make
     * sense in bulk are offered; Open and XML Diff are single-selection only.
     *
     * @param x     the x coordinate
     * @param y     the y coordinate
     * @param nodes the selected file nodes (more than one)
     */
    private void showMultiSelectionContextMenu(int x, int y, java.util.List<GitFileNode> nodes) {
        JPopupMenu menu = new JPopupMenu();

        java.util.List<GitFileNode> unstaged = new java.util.ArrayList<>();
        java.util.List<GitFileNode> staged = new java.util.ArrayList<>();
        for (GitFileNode node : nodes) {
            if (isStaged(node)) {
                staged.add(node);
            } else {
                unstaged.add(node);
            }
        }

        if (!unstaged.isEmpty()) {
            menu.add(new StageFileAction(this, unstaged));
        }
        if (!staged.isEmpty()) {
            menu.add(new UnstageFileAction(this, staged));
        }
        if (menu.getComponentCount() > 0) {
            menu.addSeparator();
        }

        // Discard changes (destructive operation)
        menu.add(new DiscardChangesAction(this, nodes));

        menu.show(changesTree, x, y);
    }

    /**
     * Gets the file nodes currently selected in the changes tree, in tree order.
     * Section header rows can never be part of the selection (see
     * {@link GitFileSelectionModel}), so every returned node is a real file.
     *
     * @return the selected file nodes; empty if nothing is selected
     */
    public java.util.List<GitFileNode> getSelectedFileNodes() {
        java.util.List<GitFileNode> nodes = new java.util.ArrayList<>();
        TreePath[] paths = changesTree.getSelectionPaths();
        if (paths == null) {
            return nodes;
        }
        // The selection model stores paths in the order they were added; sort by row so
        // callers (and the confirmation dialogs) see them in the order they're displayed.
        paths = paths.clone();
        java.util.Arrays.sort(paths,
                java.util.Comparator.comparingInt(changesTree::getRowForPath));
        for (TreePath path : paths) {
            Object last = path.getLastPathComponent();
            if (last instanceof GitFileNode) {
                nodes.add((GitFileNode) last);
            }
        }
        return nodes;
    }

    /**
     * Checks if a file node is staged.
     *
     * @param node the file node to check
     * @return true if the file is staged
     */
    private boolean isStaged(GitFileNode node) {
        GitFileNode.GitStatus status = node.getStatus();
        return status == GitFileNode.GitStatus.ADDED ||
                status == GitFileNode.GitStatus.STAGED ||
                status == GitFileNode.GitStatus.REMOVED;
    }

    /**
     * Toggles the stage/unstage state of a file.
     * If the file is unstaged, stages it. If staged, unstages it.
     *
     * @param node the file node to toggle
     */
    private void toggleStageUnstage(GitFileNode node) {
        if (node == null) {
            return;
        }
        toggleStageUnstage(java.util.List.of(node));
    }

    /**
     * Toggles the stage/unstage state of every file in a selection. Unstaged files are
     * staged and staged files are unstaged, each as a single batched Git call.
     *
     * @param nodes the file nodes to toggle
     */
    private void toggleStageUnstage(java.util.List<GitFileNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        java.util.List<GitFileNode> toStage = new java.util.ArrayList<>();
        java.util.List<GitFileNode> toUnstage = new java.util.ArrayList<>();
        for (GitFileNode node : ambiguityFreeToggleSet(nodes)) {
            if (isStaged(node)) {
                toUnstage.add(node);
            } else {
                toStage.add(node);
            }
        }

        if (!toStage.isEmpty()) {
            new StageFileAction(this, toStage).actionPerformed(null);
        }
        if (!toUnstage.isEmpty()) {
            new UnstageFileAction(this, toUnstage).actionPerformed(null);
        }
    }

    /**
     * Drops files whose staged and unstaged rows are both in the selection.
     *
     * <p>A file that is staged and then edited again has a row in each section. Toggling
     * such a selection would stage it (index := working tree) and then immediately
     * unstage it, which discards the staged snapshot the user had built — with {@code
     * git add -p}, work that exists nowhere else. There is no single sensible answer for
     * "toggle both rows of one file", so the file is left alone; the user can still act
     * on either row individually.
     *
     * @param nodes the selected file nodes
     * @return the nodes safe to toggle
     */
    static java.util.List<GitFileNode> ambiguityFreeToggleSet(java.util.List<GitFileNode> nodes) {
        java.util.Set<String> staged = new java.util.HashSet<>();
        java.util.Set<String> unstaged = new java.util.HashSet<>();
        for (GitFileNode node : nodes) {
            GitFileNode.GitStatus status = node.getStatus();
            boolean isStaged = status == GitFileNode.GitStatus.ADDED
                    || status == GitFileNode.GitStatus.STAGED
                    || status == GitFileNode.GitStatus.REMOVED;
            (isStaged ? staged : unstaged).add(node.getRelativePath());
        }

        java.util.Set<String> ambiguous = new java.util.HashSet<>(staged);
        ambiguous.retainAll(unstaged);
        if (ambiguous.isEmpty()) {
            return nodes;
        }

        java.util.List<GitFileNode> safe = new java.util.ArrayList<>(nodes.size());
        for (GitFileNode node : nodes) {
            if (!ambiguous.contains(node.getRelativePath())) {
                safe.add(node);
            }
        }
        return safe;
    }

    /**
     * Tree selection model for the changes tree.
     *
     * <p>The changes tree mixes {@link GitFileNode}s with plain section header nodes
     * ("Changes (n)", "Staged Changes (n)"). Without filtering, a shift-click range or
     * Ctrl+A drags those headers into the selection, and bulk actions then have to guess
     * what the user meant. This model drops any path that is not a file node, so callers
     * never see a non-file node selected whichever route the selection came in by.
     *
     * <p>It deliberately does <em>not</em> expand a header into the files beneath it.
     * Swing selects a parent path on the caller's behalf in situations that have nothing
     * to do with the user selecting one: {@code JTree.setExpandedState} calls
     * {@code addSelectionPath(parent)} when collapsing a node that contains the
     * selection, so a header-expanding model would silently grow a one-file selection
     * into the whole section every time that section was collapsed. Selecting a section's
     * files is an explicit user gesture instead, handled in the mouse listener via
     * {@link #selectSectionFiles}.
     */
    static class GitFileSelectionModel extends javax.swing.tree.DefaultTreeSelectionModel {

        GitFileSelectionModel() {
            setSelectionMode(DISCONTIGUOUS_TREE_SELECTION);
        }

        @Override
        public void setSelectionPaths(TreePath[] paths) {
            super.setSelectionPaths(toFilePaths(paths));
        }

        @Override
        public void addSelectionPaths(TreePath[] paths) {
            super.addSelectionPaths(toFilePaths(paths));
        }

        /**
         * Drops any path that does not end in a {@link GitFileNode}, preserving order.
         *
         * @param paths the incoming paths (may be null)
         * @return the file-node paths, or null if the input was null
         */
        private static TreePath[] toFilePaths(TreePath[] paths) {
            if (paths == null) {
                return null;
            }

            java.util.List<TreePath> resolved = new java.util.ArrayList<>(paths.length);
            for (TreePath path : paths) {
                if (path != null && path.getLastPathComponent() instanceof GitFileNode) {
                    resolved.add(path);
                }
            }
            return resolved.toArray(new TreePath[0]);
        }
    }

    /**
     * Selects every file under a section header. This is the explicit "click the heading
     * to select the section" gesture; see {@link GitFileSelectionModel} for why it does
     * not live in the selection model.
     *
     * @param sectionPath the path of the section header node
     */
    private void selectSectionFiles(TreePath sectionPath) {
        TreePath[] paths = fileChildPaths(sectionPath);
        if (paths.length > 0) {
            changesTree.setSelectionPaths(paths);
        }
    }

    /**
     * Gets the paths of the file nodes directly under a section header.
     *
     * @param sectionPath the path of the section header node
     * @return the file paths, in tree order; empty if the section holds no files
     */
    static TreePath[] fileChildPaths(TreePath sectionPath) {
        Object last = sectionPath.getLastPathComponent();
        if (!(last instanceof javax.swing.tree.TreeNode)) {
            return new TreePath[0];
        }

        javax.swing.tree.TreeNode section = (javax.swing.tree.TreeNode) last;
        java.util.List<TreePath> paths = new java.util.ArrayList<>();
        for (int i = 0; i < section.getChildCount(); i++) {
            javax.swing.tree.TreeNode child = section.getChildAt(i);
            if (child instanceof GitFileNode) {
                paths.add(sectionPath.pathByAddingChild(child));
            }
        }
        return paths.toArray(new TreePath[0]);
    }

    /**
     * Gets the current commit message text.
     *
     * @return the commit message text
     */
    public String getCommitMessage() {
        return commitMessageField.getText();
    }

    /**
     * Clears the commit message text area.
     */
    public void clearCommitMessage() {
        commitMessageField.setText("");
    }

    /**
     * Sets the commit message field text.
     *
     * @param message the message to set
     */
    public void setCommitMessage(String message) {
        commitMessageField.setText(message != null ? message : "");
    }

    /**
     * Gets the message of the last commit (HEAD).
     *
     * @return the last commit message, or null if unavailable
     */
    public String getLastCommitMessage() {
        if (git == null) return null;
        try {
            org.eclipse.jgit.revwalk.RevCommit head = git.log().setMaxCount(1).call().iterator().next();
            return head.getFullMessage();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Discards all working directory changes.
     * Checks out all tracked files from HEAD and cleans untracked files.
     * Shows a confirmation dialog since this is destructive.
     */
    private void discardAllChanges() {
        if (git == null) {
            JOptionPane.showMessageDialog(this,
                "No Git repository is currently open.",
                "Discard Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int response = JOptionPane.showConfirmDialog(this,
            "Discard ALL changes in the working directory?\n\n" +
            "This will revert all modified and deleted files to HEAD\n" +
            "and remove all untracked files.\n\n" +
            "This operation cannot be undone!",
            "Discard All Changes",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (response != JOptionPane.YES_OPTION) return;

        try {
            // Checkout all tracked files from HEAD
            git.checkout()
                .setAllPaths(true)
                .call();

            // Clean untracked files and directories
            git.clean()
                .setCleanDirectories(true)
                .call();

            refreshGitStatus();

            // Every tracked file may have changed on disk; tell the open buffers.
            reloadOpenBuffers();

            JOptionPane.showMessageDialog(this,
                "All changes discarded.",
                "Discard Successful", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Failed to discard changes:\n" + ex.getMessage(),
                "Discard Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Opens a diff view in a new editor tab.
     *
     * @param file   the file to show diff for
     * @param commit optional commit for committed file diffs (null for uncommitted)
     */
    private void openDiffInNewTab(File file, RevCommit commit) {
        try {
            String diffText;

            if (commit == null) {
                // Uncommitted changes
                diffText = generateUncommittedDiffText(file);
                DiffViewManager.openUncommittedDiff(parent, file, diffText);
            } else {
                // Committed changes
                String relativePath = repositoryRoot.toPath()
                        .relativize(file.toPath())
                        .toString()
                        .replace('\\', '/');
                diffText = generateCommitDiffText(commit, relativePath);
                DiffViewManager.openCommittedDiff(parent, file, commit, diffText);
            }

        } catch (Exception e) {
            if (DEBUG) {
                System.err.println("GitPanel: Error opening diff: " + e.getMessage());
                e.printStackTrace();
            }
            JOptionPane.showMessageDialog(
                    this,
                    "Error opening diff:\n" + e.getMessage(),
                    "Diff Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Generates diff text for uncommitted changes.
     *
     * @param file the file to generate diff for
     * @return the diff text as a String
     */
    private String generateUncommittedDiffText(File file) throws Exception {
        String relativePath = repositoryRoot.toPath()
                .relativize(file.toPath())
                .toString()
                .replace('\\', '/');

        // Check if file is untracked
        Status status = git.status()
                .addPath(relativePath)
                .call();

        if (!status.getUntracked().isEmpty() || !status.getUntrackedFolders().isEmpty()) {
            return generateNewFileContent(file, relativePath);
        }

        // Generate diff for tracked files
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DiffFormatter formatter = new DiffFormatter(out)) {
            formatter.setRepository(git.getRepository());
            formatter.setContext(3);

            ObjectId headId = git.getRepository().resolve("HEAD");
            if (headId == null) {
                return "No HEAD commit found (empty repository?)";
            }

            try (RevWalk revWalk = new RevWalk(git.getRepository());
                    ObjectReader reader = git.getRepository().newObjectReader()) {

                RevCommit headCommit = revWalk.parseCommit(headId);

                CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
                oldTreeParser.reset(reader, headCommit.getTree());

                FileTreeIterator workTreeIterator = new FileTreeIterator(git.getRepository());

                java.util.List<DiffEntry> diffs = formatter.scan(oldTreeParser, workTreeIterator);

                boolean foundDiff = false;
                for (DiffEntry diff : diffs) {
                    String diffPath = diff.getChangeType() == DiffEntry.ChangeType.DELETE
                            ? diff.getOldPath()
                            : diff.getNewPath();

                    if (diffPath.equals(relativePath)) {
                        formatter.format(diff);
                        foundDiff = true;
                        break;
                    }
                }

                if (!foundDiff) {
                    return "No changes detected for: " + file.getName();
                }
            }
        }

        return out.toString("UTF-8");
    }

    /**
     * Generates content display for new (untracked) files.
     */
    private String generateNewFileContent(File file, String relativePath) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("New file: ").append(relativePath).append("\n\n");

        java.nio.file.Path filePath = file.toPath();
        if (java.nio.file.Files.size(filePath) > 1024 * 1024) {
            sb.append("File is too large to display (")
                    .append(java.nio.file.Files.size(filePath) / 1024)
                    .append(" KB)\n");
        } else {
            java.util.List<String> lines = java.nio.file.Files.readAllLines(filePath);
            int lineNum = 1;
            for (String line : lines) {
                sb.append("+").append(lineNum++).append(": ").append(line).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Generates diff text for committed changes.
     *
     * @param commit   the commit containing the changes
     * @param filePath the relative path of the file
     * @return the diff text as a String
     */
    private String generateCommitDiffText(RevCommit commit, String filePath) throws Exception {
        if (commit.getParentCount() == 0) {
            return "Cannot show diff for initial commit";
        }

        RevCommit parentCommit = commit.getParent(0);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DiffFormatter formatter = new DiffFormatter(out)) {
            formatter.setRepository(git.getRepository());
            formatter.setContext(3);

            try (ObjectReader reader = git.getRepository().newObjectReader()) {
                CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
                oldTreeParser.reset(reader, parentCommit.getTree());

                CanonicalTreeParser newTreeParser = new CanonicalTreeParser();
                newTreeParser.reset(reader, commit.getTree());

                formatter.setPathFilter(
                        org.eclipse.jgit.treewalk.filter.PathFilter.create(filePath));
                formatter.format(oldTreeParser, newTreeParser);
            }
        }

        return out.toString("UTF-8");
    }

    /**
     * Custom cell renderer for commit history tree.
     * Handles both commit nodes and file change nodes.
     */
    private class CommitHistoryCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

            if (value instanceof CommitNode) {
                CommitNode commitNode = (CommitNode) value;
                CommitHistoryItem item = commitNode.getCommit();
                // Simple format: "message - author"
                setText(item.toString());

                // Set tooltip with detailed info
                if (git != null) {
                    setToolTipText(item.formatTooltip(git));
                }
            } else if (value instanceof CommitNode.FileChangeNode) {
                CommitNode.FileChangeNode fileNode = (CommitNode.FileChangeNode) value;
                setText(fileNode.getChangeType() + ": " + fileNode.getFileName());

                // Use appropriate icon based on change type
                ImageIcon icon = getIconForChangeType(fileNode.getChangeTypeEnum());
                if (icon != null) {
                    setIcon(icon);
                }
            } else if (value instanceof DefaultMutableTreeNode) {
                // Handle placeholder or message nodes
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                setText(node.getUserObject().toString());
            }

            return this;
        }

        /**
         * Gets the appropriate icon for a file change type.
         */
        private ImageIcon getIconForChangeType(org.eclipse.jgit.diff.DiffEntry.ChangeType type) {
            switch (type) {
                case ADD:
                    return ICON_ADDED;
                case MODIFY:
                    return ICON_MODIFIED;
                case DELETE:
                    return ICON_DELETED;
                default:
                    return null;
            }
        }
    }

    /**
     * Custom cell renderer for Git changes tree.
     * Shows appropriate icons based on file status.
     */
    private class GitCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

            if (value instanceof GitFileNode) {
                GitFileNode node = (GitFileNode) value;
                ImageIcon icon = getIconForStatus(node.getStatus());
                if (icon != null) {
                    setIcon(icon);
                }
                setText(node.getFile().getName());
            }

            return this;
        }

        /**
         * Gets the appropriate icon for a Git status.
         */
        private ImageIcon getIconForStatus(GitFileNode.GitStatus status) {
            switch (status) {
                case MODIFIED:
                    return ICON_MODIFIED;
                case ADDED:
                    return ICON_ADDED;
                case DELETED:
                    return ICON_DELETED;
                case STAGED:
                    return ICON_STAGED;
                case UNTRACKED:
                    return ICON_UNTRACKED;
                case CONFLICT:
                    return ICON_CONFLICT;
                case REMOVED:
                    return ICON_DELETED;
                default:
                    return null;
            }
        }
    }
}
