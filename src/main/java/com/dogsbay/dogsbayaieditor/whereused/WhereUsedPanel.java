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

package com.dogsbay.dogsbayaieditor.whereused;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.ViewPanel;
import com.dogsbay.dogsbayaieditor.links.KeyDefinition;
import com.dogsbay.dogsbayaieditor.links.KeySpace;
import com.dogsbay.dogsbayaieditor.links.Reference;
import com.dogsbay.dogsbayaieditor.links.ReverseLinkIndex;

/**
 * Left-sidebar "Where Used" panel, organized by target type:
 *
 * <ul>
 *   <li><b>Document tab</b> — everything that references a file, grouped by
 *       what the reference means: In maps / Content reuse / Links / Images
 *       (see {@link ReferenceCategory}). Answers "what breaks if I rename
 *       or move this?" in one view.</li>
 *   <li><b>Keys tab</b> — a key inspector: where the key is defined (from
 *       the context map) and every keyref/conkeyref that uses it.</li>
 * </ul>
 *
 * Both tabs query one shared {@link ReverseLinkIndex}, built lazily over the
 * File Explorer root on a background worker and updated incrementally as
 * documents change.
 */
public class WhereUsedPanel extends ViewPanel {

    private static final String CARD_EMPTY = "empty";
    private static final String CARD_RESULTS = "results";

    private final DogsBayAIEditor parent;

    private final JTabbedPane tabs = new JTabbedPane();

    // Document tab
    private final JLabel docHeader = new JLabel(" ");
    private final DefaultMutableTreeNode docRoot = new DefaultMutableTreeNode();
    private final DefaultTreeModel docModel = new DefaultTreeModel(docRoot);
    private final JTree docTree = new JTree(docModel);
    private final CardLayout docCards = new CardLayout();
    private final JPanel docCardPanel = new JPanel(docCards);

    // Keys tab
    private final JTextField keyField = new JTextField();
    private final JLabel keyHeader = new JLabel(" ");
    private final DefaultMutableTreeNode keyRoot = new DefaultMutableTreeNode();
    private final DefaultTreeModel keyModel = new DefaultTreeModel(keyRoot);
    private final JTree keyTree = new JTree(keyModel);
    private final CardLayout keyCards = new CardLayout();
    private final JPanel keyCardPanel = new JPanel(keyCards);

    // Shared
    private final JLabel statusLabel = new JLabel(" ");

    private ReverseLinkIndex index;        // EDT-confined reference; built off-EDT
    private boolean building;

    /** A navigable non-Reference leaf (key definition, key target). */
    private record NavTarget(File file, int line, String text, String tooltip) {
    }

    public WhereUsedPanel(DogsBayAIEditor parent) {
        super(new BorderLayout());
        this.parent = parent;
        buildUI();
    }

    // -------------------------------------------------------------------------
    // UI construction
    // -------------------------------------------------------------------------

    private void buildUI() {
        tabs.addTab("Document", buildDocumentTab());
        tabs.addTab("Keys", buildKeysTab());
        add(tabs, BorderLayout.CENTER);

        JPanel statusRow = new JPanel(new BorderLayout(6, 0));
        statusRow.setBorder(new EmptyBorder(2, 8, 2, 8));
        statusRow.add(statusLabel, BorderLayout.CENTER);
        JButton rebuildButton = new JButton("Rebuild");
        rebuildButton.setToolTipText("Re-scan the project and rebuild the link index");
        rebuildButton.addActionListener(e -> {
            index = null;
            rerunLastQuery();
        });
        statusRow.add(rebuildButton, BorderLayout.EAST);
        add(statusRow, BorderLayout.SOUTH);
    }

    private JPanel buildDocumentTab() {
        JPanel tab = new JPanel(new BorderLayout());

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(new EmptyBorder(6, 8, 4, 8));

        docHeader.setFont(docHeader.getFont().deriveFont(Font.BOLD));
        docHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(docHeader);
        top.add(Box.createVerticalStrut(6));

        JButton activeDocButton = new JButton("Active Document");
        activeDocButton.setToolTipText("Find everything that references the active document");
        activeDocButton.addActionListener(e -> findUsagesOfActiveDocument());
        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonRow.add(activeDocButton);
        buttonRow.add(Box.createHorizontalGlue());
        top.add(buttonRow);

        tab.add(top, BorderLayout.NORTH);

        configureTree(docTree);
        docCardPanel.add(emptyState(
                "Find everything that references a topic or image —"
                + "<br>maps, content reuse, links."
                + "<br><br>Right-click a reference in the editor and choose"
                + "<br><b>Find Usages</b>, or click <b>Active Document</b>."),
                CARD_EMPTY);
        docCardPanel.add(new JScrollPane(docTree), CARD_RESULTS);
        docCards.show(docCardPanel, CARD_EMPTY);
        tab.add(docCardPanel, BorderLayout.CENTER);
        return tab;
    }

    private JPanel buildKeysTab() {
        JPanel tab = new JPanel(new BorderLayout());

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(new EmptyBorder(6, 8, 4, 8));

        JPanel keyRow = new JPanel();
        keyRow.setLayout(new BoxLayout(keyRow, BoxLayout.X_AXIS));
        keyRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        keyRow.add(new JLabel("Key: "));
        keyField.setToolTipText("Inspect a key: definition and all usages — press Enter");
        keyField.addActionListener(e -> findUsagesOfKey(keyField.getText().trim()));
        keyRow.add(keyField);
        top.add(keyRow);
        top.add(Box.createVerticalStrut(6));

        keyHeader.setFont(keyHeader.getFont().deriveFont(Font.BOLD));
        keyHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(keyHeader);

        tab.add(top, BorderLayout.NORTH);

        configureTree(keyTree);
        keyCardPanel.add(emptyState(
                "Inspect a key — where it is defined and every"
                + "<br>keyref/conkeyref that uses it."
                + "<br><br>Type a key name and press Enter, or right-click"
                + "<br>a keyref in the editor."),
                CARD_EMPTY);
        keyCardPanel.add(new JScrollPane(keyTree), CARD_RESULTS);
        keyCards.show(keyCardPanel, CARD_EMPTY);
        tab.add(keyCardPanel, BorderLayout.CENTER);
        return tab;
    }

    private void configureTree(JTree tree) {
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new ResultCellRenderer());
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) {
                    return;
                }
                Object node = tree.getLastSelectedPathComponent();
                if (!(node instanceof DefaultMutableTreeNode)) {
                    return;
                }
                Object value = ((DefaultMutableTreeNode) node).getUserObject();
                if (value instanceof Reference) {
                    Reference ref = (Reference) value;
                    if (ref.source() != null) {
                        navigateToFile(ref.source(), ref.line());
                    }
                } else if (value instanceof NavTarget) {
                    NavTarget target = (NavTarget) value;
                    navigateToFile(target.file(), target.line());
                }
            }
        });
    }

    private static JPanel emptyState(String htmlBody) {
        JLabel label = new JLabel("<html><div style='text-align:center'>"
                + htmlBody + "</div></html>", SwingConstants.CENTER);
        label.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    // -------------------------------------------------------------------------
    // Queries (public API used by the plugin / editor popup)
    // -------------------------------------------------------------------------

    private File lastTargetFile;
    private String lastTargetKey;

    /** Entry point: find usages of the currently active document. */
    public void findUsagesOfActiveDocument() {
        File file = activeDocumentFile();
        if (file == null) {
            statusLabel.setText("No active file document.");
            return;
        }
        findUsagesOf(file);
    }

    /** Entry point: find usages of a specific file (selects the Document tab). */
    public void findUsagesOf(File target) {
        lastTargetFile = target;
        lastTargetKey = null;
        tabs.setSelectedIndex(0);
        withIndex(() -> {
            KeySpace keySpace = keySpaceOrEmpty();
            List<Reference> usages = index.usagesOfIncludingKeys(target, keySpace);
            showDocumentResults(target, usages);
        });
    }

    /** Entry point: inspect a key (selects the Keys tab, fills the field). */
    public void findUsagesOfKey(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        lastTargetKey = key;
        lastTargetFile = null;
        tabs.setSelectedIndex(1);
        if (!key.equals(keyField.getText())) {
            keyField.setText(key);
        }
        withIndex(() -> showKeyResults(key, index.usagesOfKey(key)));
    }

    private void rerunLastQuery() {
        if (lastTargetFile != null) {
            findUsagesOf(lastTargetFile);
        } else if (lastTargetKey != null) {
            findUsagesOfKey(lastTargetKey);
        } else {
            withIndex(() -> statusLabel.setText(
                    index.getFilesIndexed() + " files indexed."));
        }
    }

    /** A document changed on disk/in editor — keep the index current. */
    public void fileChanged(File file) {
        if (index != null && file != null) {
            index.updateFile(file);
        }
    }

    // -------------------------------------------------------------------------
    // Index lifecycle
    // -------------------------------------------------------------------------

    /** Runs {@code then} on the EDT once the index exists. */
    private void withIndex(Runnable then) {
        if (index != null) {
            then.run();
            return;
        }
        if (building) {
            return;
        }
        File root = scopeRoot();
        if (root == null) {
            statusLabel.setText("No project root — open a folder in the File Explorer.");
            return;
        }
        building = true;
        statusLabel.setText("Indexing " + root.getName() + "…");
        new SwingWorker<ReverseLinkIndex, Void>() {
            @Override
            protected ReverseLinkIndex doInBackground() {
                return ReverseLinkIndex.build(root, null);
            }

            @Override
            protected void done() {
                building = false;
                try {
                    index = get();
                    statusLabel.setText(index.getFilesIndexed() + " files indexed.");
                    then.run();
                } catch (Exception e) {
                    statusLabel.setText("Index failed: " + e.getMessage());
                }
            }
        }.execute();
    }

    private File scopeRoot() {
        try {
            File root = parent.getFileExplorer().getRootDirectory();
            if (root != null && root.isDirectory()) {
                return root;
            }
        } catch (Exception ignored) {
            // fall through
        }
        File active = activeDocumentFile();
        return active != null ? active.getParentFile() : null;
    }

    private File activeDocumentFile() {
        try {
            URL url = parent.getDocument().getURL();
            if (url != null && "file".equals(url.getProtocol())) {
                return new File(url.toURI());
            }
        } catch (Exception ignored) {
            // no usable file
        }
        return null;
    }

    private KeySpace keySpaceOrEmpty() {
        try {
            File rootMap = parent.getDefaultRootMapFile();
            if (rootMap != null) {
                return KeySpace.fromRootMap(rootMap);
            }
        } catch (Exception ignored) {
            // no key space
        }
        return KeySpace.empty();
    }

    // -------------------------------------------------------------------------
    // Document tab results: category -> file -> reference
    // -------------------------------------------------------------------------

    private void showDocumentResults(File target, List<Reference> usages) {
        docRoot.removeAllChildren();

        Map<ReferenceCategory, Map<String, List<Reference>>> byCategory =
                new EnumMap<>(ReferenceCategory.class);
        for (Reference ref : usages) {
            String source = ref.source() != null
                    ? ref.source().getAbsolutePath() : "(unknown)";
            byCategory
                    .computeIfAbsent(ReferenceCategory.classify(ref),
                            c -> new LinkedHashMap<>())
                    .computeIfAbsent(source, s -> new ArrayList<>())
                    .add(ref);
        }

        File root = index != null ? index.getRoot() : null;
        for (ReferenceCategory category : ReferenceCategory.values()) {
            Map<String, List<Reference>> files = byCategory.get(category);
            if (files == null) {
                continue;
            }
            int count = files.values().stream().mapToInt(List::size).sum();
            DefaultMutableTreeNode categoryNode = new DefaultMutableTreeNode(
                    category.getDisplayName() + "  (" + count + ")");
            addFileGroups(categoryNode, files, root);
            docRoot.add(categoryNode);
        }

        docModel.reload();
        expandAll(docTree);

        int files = (int) usages.stream()
                .map(r -> r.source() == null ? "?" : r.source().getAbsolutePath())
                .distinct().count();
        docHeader.setText("References to ‘" + target.getName() + "’ — "
                + usages.size() + " in " + files + " file" + (files == 1 ? "" : "s"));
        docHeader.setToolTipText(target.getAbsolutePath());
        docCards.show(docCardPanel, CARD_RESULTS);
        statusLabel.setText(usages.isEmpty() ? "No references found." : " ");
    }

    // -------------------------------------------------------------------------
    // Keys tab results: definition + usages
    // -------------------------------------------------------------------------

    private void showKeyResults(String key, List<Reference> usages) {
        keyRoot.removeAllChildren();

        // Definition (needs the context map)
        DefaultMutableTreeNode definitionNode = new DefaultMutableTreeNode("Definition");
        File rootMap = null;
        try {
            rootMap = parent.getDefaultRootMapFile();
        } catch (Exception ignored) {
            // none
        }
        if (rootMap == null) {
            definitionNode.add(new DefaultMutableTreeNode(
                    "Unknown — no context map (set a Default Root Map)", false));
        } else {
            KeySpace keySpace = KeySpace.fromRootMap(rootMap);
            // Lenient: a bare key unique to one @keyscope still shows its definition.
            KeyDefinition def = keySpace.resolveLenient(key, "");
            if (def == null) {
                definitionNode.add(new DefaultMutableTreeNode(
                        "Not defined in " + rootMap.getName(), false));
            } else {
                String detail = def.keywordText() != null
                        ? "text: “" + def.keywordText() + "”"
                        : (def.href() != null ? "href: " + def.href() : "definition-only");
                definitionNode.add(new DefaultMutableTreeNode(new NavTarget(
                        def.source(), def.line(),
                        def.source().getName() + ":" + def.line() + " — " + detail,
                        def.source().getAbsolutePath()), false));
                File target = keySpace.resolveHrefFileLenient(key, "");
                if (target != null && target.isFile()) {
                    definitionNode.add(new DefaultMutableTreeNode(new NavTarget(
                            target, -1, "→ opens " + target.getName(),
                            target.getAbsolutePath()), false));
                }
            }
        }
        keyRoot.add(definitionNode);

        // Usages, bucketed by meaning (same classifier as the Document tab)
        DefaultMutableTreeNode usagesNode = new DefaultMutableTreeNode(
                "Usages  (" + usages.size() + ")");
        Map<ReferenceCategory, Map<String, List<Reference>>> byCategory =
                new EnumMap<>(ReferenceCategory.class);
        for (Reference ref : usages) {
            String source = ref.source() != null
                    ? ref.source().getAbsolutePath() : "(unknown)";
            byCategory
                    .computeIfAbsent(ReferenceCategory.classify(ref),
                            c -> new LinkedHashMap<>())
                    .computeIfAbsent(source, s -> new ArrayList<>())
                    .add(ref);
        }
        File root = index != null ? index.getRoot() : null;
        for (ReferenceCategory category : ReferenceCategory.values()) {
            Map<String, List<Reference>> files = byCategory.get(category);
            if (files == null) {
                continue;
            }
            int count = files.values().stream().mapToInt(List::size).sum();
            DefaultMutableTreeNode categoryNode = new DefaultMutableTreeNode(
                    category.getDisplayName() + "  (" + count + ")");
            addFileGroups(categoryNode, files, root);
            usagesNode.add(categoryNode);
        }
        keyRoot.add(usagesNode);

        keyModel.reload();
        expandAll(keyTree);

        keyHeader.setText("Key ‘" + key + "’ — " + usages.size()
                + " usage" + (usages.size() == 1 ? "" : "s"));
        keyCards.show(keyCardPanel, CARD_RESULTS);
        statusLabel.setText(" ");
    }

    // -------------------------------------------------------------------------
    // Shared tree helpers
    // -------------------------------------------------------------------------

    private static void addFileGroups(DefaultMutableTreeNode parentNode,
                                      Map<String, List<Reference>> files, File root) {
        for (Map.Entry<String, List<Reference>> entry : files.entrySet()) {
            DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(
                    relativize(entry.getKey(), root)
                            + "  (" + entry.getValue().size() + ")");
            for (Reference ref : entry.getValue()) {
                fileNode.add(new DefaultMutableTreeNode(ref, false));
            }
            parentNode.add(fileNode);
        }
    }

    private static void expandAll(JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private static String relativize(String absolute, File root) {
        if (root != null) {
            String rootPath = root.getAbsolutePath() + File.separator;
            if (absolute.startsWith(rootPath)) {
                return absolute.substring(rootPath.length());
            }
        }
        return absolute;
    }

    /**
     * Opens a file in the editor and selects the given 1-based line
     * (line &lt;= 0 just opens the file). Also used by the editor popup's
     * Go to Key Definition / Open Target items.
     */
    public void navigateToFile(File file, int line) {
        parent.setWait(true);
        parent.setStatus("Opening...");
        new Thread(() -> {
            try {
                URL url = file.toURI().toURL();
                parent.open(url, null, true);
            } catch (Exception ignored) {
                // open failed — nothing to navigate to
            } finally {
                parent.setStatus("Done");
                parent.setWait(false);
                SwingUtilities.invokeLater(() -> {
                    parent.switchToEditor();
                    try {
                        if (line > 0) {
                            parent.getView().getEditor().selectLineWithoutEnd(line);
                        }
                    } catch (Exception ignored) {
                        // navigation is best-effort
                    }
                });
            }
        }).start();
    }

    /** Renders reference rows ("line N — element, via key") and nav targets. */
    private static class ResultCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            setFont(tree.getFont());
            setToolTipText(null);
            if (value instanceof DefaultMutableTreeNode) {
                Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObject instanceof Reference) {
                    Reference ref = (Reference) userObject;
                    String viaKey = ref.isKeyReference()
                            ? "  — via key ‘" + ref.keyName() + "’" : "";
                    setText("line " + ref.line() + " — " + ref.element() + viaKey);
                    setToolTipText("<" + ref.element() + " @" + ref.attribute()
                            + "=\"" + ref.rawValue() + "\">");
                } else if (userObject instanceof NavTarget) {
                    NavTarget target = (NavTarget) userObject;
                    setText(target.text());
                    setToolTipText(target.tooltip());
                }
            }
            return this;
        }
    }

    // -------------------------------------------------------------------------
    // ViewPanel
    // -------------------------------------------------------------------------

    @Override
    public void setFocus() {
        if (tabs.getSelectedIndex() == 1) {
            keyField.requestFocusInWindow();
        }
    }

    @Override
    public void updatePreferences() {
        // no preferences yet
    }

    @Override
    public void setProperties() {
        // no persisted properties yet
    }
}
