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

package com.dogsbay.dogsbayaieditor.dita.ui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileFilter;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import com.dogsbay.xml.DogsBayURLUtilities;
import java.net.MalformedURLException;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.ViewTreePanel;
import com.dogsbay.dogsbayaieditor.dita.DitaMapNode;
import com.dogsbay.dogsbayaieditor.dita.DitaMapParser;
import com.dogsbay.dogsbayaieditor.dita.DitaNode;

/**
 * Explorer panel for visualizing DITA maps.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/24 $
 * @author DogsBay Ltd
 */
public class DitaExplorerPanel extends ViewTreePanel {
    private static final long serialVersionUID = 1L;

    private DogsBayAIEditor parent;
    private JTree tree;
    private DefaultTreeModel treeModel;
    private File currentMapFile;
    private DitaMapParser parser;
    private JButton publishButton;
    /** Map picker next to the title; switches the displayed map only — never the
     *  active deliverable (deliverable selection stays explicit, in the status bar). */
    private com.dogsbay.dogsbayaieditor.StatusSegment mapChooser;

    public DitaExplorerPanel(DogsBayAIEditor parent) {
        super(new BorderLayout());
        this.parent = parent;
        this.parser = new DitaMapParser();

        // Create toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setRollover(true);
        toolbar.setBorder(new EmptyBorder(2, 2, 2, 2));

        // Title label
        JLabel titleLabel = new JLabel("Topic Maps");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 11f));
        titleLabel.setBorder(new EmptyBorder(0, 2, 0, 0));
        toolbar.add(titleLabel);

        // Map picker — switches which map the explorer shows. It loads the map only;
        // it deliberately does NOT change the active deliverable (that stays explicit).
        toolbar.add(Box.createHorizontalStrut(6));
        mapChooser = new com.dogsbay.dogsbayaieditor.StatusSegment(
                "🗺", "Choose topic map (does not change the active deliverable)");
        mapChooser.setValue(null, "No map");
        mapChooser.setOnClick(this::showMapChooser);
        toolbar.add(mapChooser);

        toolbar.add(Box.createHorizontalGlue());

        // Open Map Button
        JButton openButton = new JButton();
        openButton.setToolTipText("Open DITA Map");
        try {
            openButton.setIcon(new ImageIcon(getClass().getResource("/com/dogsbay/dogsbayaieditor/icons/Open16.gif")));
        } catch (Exception e) {
            openButton.setText("Open");
        }
        openButton.addActionListener(e -> openMap());
        toolbar.add(openButton);

        // Refresh Button
        JButton refreshButton = new JButton();
        refreshButton.setToolTipText("Refresh Map");
        try {
            refreshButton
                    .setIcon(new ImageIcon(getClass().getResource("/com/dogsbay/xml/editor/icons/Refresh16.gif")));
        } catch (Exception e) {
            refreshButton.setText("Refresh");
        }
        refreshButton.addActionListener(e -> refreshMap());
        toolbar.add(refreshButton);

        // Publish Button
        JButton publishButton = new JButton();
        publishButton.setToolTipText("Publish DITA Map");
        try {
            publishButton.setIcon(new ImageIcon(getClass().getResource("/com/dogsbay/dogsbayaieditor/icons/Export16.gif")));
        } catch (Exception e) {
            publishButton.setText("Publish");
        }
        publishButton.setEnabled(false);
        publishButton.addActionListener(e -> {
            if (currentMapFile != null) {
                DitaPublishDialog dialog = new DitaPublishDialog(parent, currentMapFile, parent.getProperties());
                dialog.setVisible(true);
            }
        });
        toolbar.add(publishButton);
        this.publishButton = publishButton;

        // Collapse All Button
        JButton collapseButton = new JButton();
        collapseButton.setToolTipText("Collapse All");
        try {
            collapseButton
                    .setIcon(new ImageIcon(getClass().getResource("/com/dogsbay/dogsbayaieditor/icons/CollapseAll.gif")));
        } catch (Exception e) {
            collapseButton.setText("Collapse All");
        }
        collapseButton.addActionListener(e -> collapseAll());
        toolbar.add(collapseButton);

        add(toolbar, BorderLayout.NORTH);

        // Create Tree
        treeModel = new DefaultTreeModel(new DitaMapNode("No Map Loaded"));
        tree = new JTree(treeModel) {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }
        };
        tree.setShowsRootHandles(true);
        tree.setRootVisible(true);
        tree.setCellRenderer(new DitaTreeCellRenderer());
        tree.setRowHeight(18);
        tree.putClientProperty("JTree.lineStyle", "None");
        // Topics and maps drag out as files: onto an agent chat box they become @mentions.
        tree.setDragEnabled(true);
        tree.setTransferHandler(new com.dogsbay.dogsbayaieditor.explorer.FileDragExporter<>(
                com.dogsbay.dogsbayaieditor.dita.DitaNode.class,
                com.dogsbay.dogsbayaieditor.dita.DitaNode::getResolvedFile));

        // Enable tooltips
        ToolTipManager.sharedInstance().registerComponent(tree);

        // Single-click opens; right-click shows the context menu
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        Object component = path.getLastPathComponent();
                        if (component instanceof DitaNode) {
                            openFile((DitaNode) component);
                        }
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowContextMenu(e);
            }
        });

        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void openMap() {
        JFileChooser chooser = new JFileChooser();
        if (currentMapFile != null) {
            chooser.setCurrentDirectory(currentMapFile.getParentFile());
        }

        chooser.setFileFilter(new FileFilter() {
            @Override
            public String getDescription() {
                return "DITA Maps (*.ditamap)";
            }

            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".ditamap");
            }
        });

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            loadMap(chooser.getSelectedFile());
        }
    }

    private void refreshMap() {
        if (currentMapFile != null) {
            loadMap(currentMapFile);
        }
    }

    private void maybeShowContextMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return;
        }
        Object component = path.getLastPathComponent();
        if (!(component instanceof DitaNode)) {
            return;
        }
        DitaNode node = (DitaNode) component;
        tree.setSelectionPath(path);

        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

        javax.swing.JMenuItem openItem = new javax.swing.JMenuItem("Open");
        openItem.setEnabled(node.getResolvedFile() != null && node.getResolvedFile().isFile());
        openItem.addActionListener(ev -> openFile(node));
        menu.add(openItem);

        menu.addSeparator();

        javax.swing.JMenuItem renameItem = new javax.swing.JMenuItem("Rename/Move with References...");
        File nodeFile = node.getResolvedFile();
        renameItem.setEnabled(nodeFile != null && nodeFile.isFile());
        renameItem.addActionListener(ev ->
                new com.dogsbay.dogsbayaieditor.refactor.RefactorUi(parent)
                        .renameFileWithReferences(nodeFile, () -> {
                            // The root map itself may have moved
                            if (currentMapFile != null && !currentMapFile.isFile()) {
                                clearMap();
                            } else {
                                refreshMap();
                            }
                        }));
        menu.add(renameItem);

        menu.show(tree, e.getX(), e.getY());
    }

    public File getCurrentMapFile() {
        return currentMapFile;
    }

    /** Open a map the user explicitly chose: remember it and surface parse errors. */
    public void loadMap(File mapFile) {
        loadMap(mapFile, true);
    }

    /**
     * Follow the active deliverable's map (quiet: no persist, no parse-error dialog).
     * The deliverable change can fire programmatically on project open/restore, so this
     * must not nag or clobber the remembered 🗺-chooser map.
     */
    public void followDeliverableMap(File mapFile) {
        loadMap(mapFile, false);
    }

    /**
     * @param userInitiated true for an explicit open (persists as the last-open map and
     *        shows a dialog on parse failure); false for an auto/restore load (persists
     *        nothing — so a fallback can't clobber the remembered map — and stays quiet).
     */
    private void loadMap(File mapFile, boolean userInitiated) {
        this.currentMapFile = mapFile;
        if (mapChooser != null) {
            mapChooser.setValue(mapFile != null ? mapFile.getName() : null, "No map");
        }
        if (userInitiated && mapFile != null && parent != null && parent.getProperties() != null) {
            parent.getProperties().setLastDitaMap(mapFile.getAbsolutePath());
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        // Run in background
        new Thread(() -> {
            try {
                final DitaMapNode root = parser.parse(mapFile);

                SwingUtilities.invokeLater(() -> {
                    treeModel.setRoot(root);
                    treeModel.reload();
                    expandAll();
                    publishButton.setEnabled(true);
                    setCursor(Cursor.getDefaultCursor());
                });
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    // Don't nag on every launch for an auto-restored map — only an
                    // explicit open surfaces the error dialog.
                    if (userInitiated) {
                        JOptionPane.showMessageDialog(this, "Error parsing DITA map: " + e.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                    setCursor(Cursor.getDefaultCursor());
                });
            }
        }).start();
    }

    public void clearMap() {
        this.currentMapFile = null;
        if (mapChooser != null) {
            mapChooser.setValue(null, "No map");
        }
        SwingUtilities.invokeLater(() -> {
            treeModel.setRoot(new DitaMapNode("No Map Loaded"));
            treeModel.reload();
            publishButton.setEnabled(false);
        });
    }

    /** Pop a menu of the project's maps; selecting one loads it (no deliverable change). */
    private void showMapChooser() {
        File root = projectRoot();
        java.util.List<File> maps =
                com.dogsbay.dogsbayaieditor.project.ProjectMaps.find(root, true);
        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        if (maps.isEmpty()) {
            javax.swing.JMenuItem none = new javax.swing.JMenuItem("No .ditamap / .bookmap found in project");
            none.setEnabled(false);
            popup.add(none);
        } else {
            for (File m : maps) {
                String label = (root != null)
                        ? root.toPath().relativize(m.toPath()).toString()
                        : m.getName();
                javax.swing.JMenuItem item = new javax.swing.JMenuItem(label);
                if (currentMapFile != null && sameFile(currentMapFile, m)) {
                    item.setFont(item.getFont().deriveFont(Font.BOLD));
                }
                item.addActionListener(e -> loadMap(m));
                popup.add(item);
            }
        }
        popup.show(mapChooser, 0, mapChooser.getHeight());
    }

    private File projectRoot() {
        return parent.getFileExplorer() != null ? parent.getFileExplorer().getRootDirectory() : null;
    }

    /**
     * Load the map to show for the current project — the persisted last-open map if
     * it still lives under the project, else the project's default root map — or clear
     * when there's nothing to show. Used on launch-restore and when the Topic Maps tab
     * is selected. Never changes the active deliverable.
     */
    public void restoreMapForCurrentProject() {
        com.dogsbay.dogsbayaieditor.project.ProjectProperties project =
                parent.getProjectSwitcher() != null ? parent.getProjectSwitcher().getCurrentProject() : null;
        String lastMap = parent.getProperties() != null ? parent.getProperties().getLastDitaMap() : null;
        File map = resolveMapToShow(project, lastMap);
        if (map == null) {
            clearMap();
        } else if (currentMapFile == null || !sameFile(currentMapFile, map)) {
            loadMap(map, false); // auto/restore load: don't persist or nag on parse errors
        }
    }

    /** The persisted last-open map {@code lastMapPath} (if still a file under
     *  {@code project}), else the project's default root map; {@code null} when neither
     *  applies or the project isn't DITA. */
    static File resolveMapToShow(
            com.dogsbay.dogsbayaieditor.project.ProjectProperties project, String lastMapPath) {
        if (project == null
                || !com.dogsbay.dogsbayaieditor.project.ProjectProperties.TYPE_DITA.equals(project.getProjectType())) {
            return null;
        }
        // Prefer the map the user last had open — but only when we can confirm it's a
        // file inside this project (a null folder or a foreign path must not win).
        File folder = project.getFolderPath() != null ? new File(project.getFolderPath()) : null;
        if (folder != null && lastMapPath != null && !lastMapPath.isEmpty()) {
            File f = new File(lastMapPath);
            if (f.isFile() && isUnder(folder, f)) {
                return f;
            }
        }
        // Fall back to the project's default root map (handles absolute + relative paths
        // and existence — reuses the shared resolver).
        return com.dogsbay.dogsbayaieditor.project.ProjectRootMapResolver.rootMapFile(project);
    }

    /** True when {@code file} is the same as, or nested under, directory {@code dir}. */
    private static boolean isUnder(File dir, File file) {
        String d;
        String f;
        try {
            d = dir.getCanonicalPath();
            f = file.getCanonicalPath();
        } catch (Exception e) {
            d = dir.getAbsolutePath();
            f = file.getAbsolutePath();
        }
        return f.equals(d) || f.startsWith(d.endsWith(File.separator) ? d : d + File.separator);
    }

    private static boolean sameFile(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (Exception e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }

    private void openFile(DitaNode node) {
        File file = node.getResolvedFile();
        if (file != null && file.exists() && file.isFile()) {
            try {
                // Use DogsBayAIEditor to open the file
                parent.open(DogsBayURLUtilities.getURLFromFile(file), null, false);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
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
        tree.requestFocus();
    }

    @Override
    public void updatePreferences() {
        // No preferences yet
    }

    @Override
    public void setProperties() {
        // No properties yet
    }
}
