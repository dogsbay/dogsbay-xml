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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import com.dogsbay.dogsbayaieditor.commands.EditMapCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.links.map.MapModel;
import com.dogsbay.dogsbayaieditor.links.map.MapRef;

/**
 * Structural map editor: a topicref tree plus a property form. Every change routes
 * through the {@code edit_map} command engine (the tested, reference-safe,
 * formatting-preserving writer), so the Swing layer stays thin — add/remove/insert,
 * reorder, attribute edits, and New map/bookmap. Edits write the map file on disk and
 * reload (open editor buffers are refreshed). Mirrors {@link ReltableEditorPanel}.
 */
public final class MapEditorPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    /** Enumerated topicref attributes → their allowed values ("" = unset/remove). */
    private static final Map<String, String[]> ENUMS = Map.of(
            "toc", new String[] {"", "yes", "no"},
            "print", new String[] {"", "yes", "no", "printonly"},
            "processing-role", new String[] {"", "normal", "resource-only"},
            "collection-type", new String[] {"", "unordered", "sequence", "choice", "family"},
            "scope", new String[] {"", "local", "peer", "external"});
    /** The attribute set the property form edits, in display order. */
    private static final String[] ATTRS = {
            "navtitle", "href", "toc", "print", "processing-role", "chunk",
            "collection-type", "format", "scope", "type", "keys", "keyref"};

    private final File mapFile;
    private final com.dogsbay.dogsbayaieditor.DogsBayAIEditor editor;
    private final HeadlessExecutor exec = new HeadlessExecutor();
    private final JTree tree = new JTree();
    private final Map<String, javax.swing.JComponent> fields = new LinkedHashMap<>();
    private MapModel model;

    public MapEditorPanel(File mapFile, com.dogsbay.dogsbayaieditor.DogsBayAIEditor editor) {
        super(new BorderLayout(6, 6));
        this.mapFile = mapFile;
        this.editor = editor;
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setRootVisible(true);
        tree.addTreeSelectionListener(e -> populateForm());

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(tree), buildForm());
        split.setResizeWeight(0.5);
        add(split, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        button(buttons, "New map…", () -> onNew(false));
        button(buttons, "New bookmap…", () -> onNew(true));
        button(buttons, "Add topicref…", () -> onInsert("topicref", true));
        button(buttons, "Add topichead", () -> onInsert("topichead", false));
        button(buttons, "Remove", this::onRemove);
        button(buttons, "Move up", () -> onMove(-1));
        button(buttons, "Move down", () -> onMove(1));
        button(buttons, "Refresh", this::load);
        add(buttons, BorderLayout.SOUTH);

        load();
    }

    private static void button(JPanel panel, String label, Runnable action) {
        JButton b = new JButton(label);
        b.addActionListener(e -> action.run());
        panel.add(b);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(2, 4, 2, 4);
        g.anchor = GridBagConstraints.WEST;
        int row = 0;
        for (String attr : ATTRS) {
            g.gridx = 0; g.gridy = row; g.fill = GridBagConstraints.NONE;
            form.add(new JLabel(attr + ":"), g);
            javax.swing.JComponent field;
            if (ENUMS.containsKey(attr)) {
                field = new JComboBox<>(ENUMS.get(attr));
            } else {
                field = new JTextField(18);
            }
            fields.put(attr, field);
            g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
            form.add(field, g);
            g.weightx = 0;
            row++;
        }
        JButton apply = new JButton("Apply attributes");
        apply.addActionListener(e -> onApply());
        g.gridx = 1; g.gridy = row; g.fill = GridBagConstraints.NONE;
        form.add(apply, g);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(javax.swing.BorderFactory.createTitledBorder("Topicref attributes"));
        wrap.add(form, BorderLayout.NORTH);
        wrap.setPreferredSize(new Dimension(280, 0));
        return wrap;
    }

    // ── tree ────────────────────────────────────────────────────────────────────

    /** Re-parse the map and rebuild the tree, preserving the selected node by selector. */
    public void load() {
        String keep = selectedRef() != null ? selectedRef().selector() : null;
        try {
            model = MapModel.parse(mapFile);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not read map: " + ex.getMessage(),
                    "Map editor", JOptionPane.ERROR_MESSAGE);
            return;
        }
        DefaultMutableTreeNode rootNode = node(model.root());
        addChildren(rootNode, model.root());
        tree.setModel(new DefaultTreeModel(rootNode));
        expandAll();
        if (keep != null) {
            selectBySelector(rootNode, keep);
        }
        populateForm();
    }

    private DefaultMutableTreeNode node(MapRef ref) {
        return new DefaultMutableTreeNode(new NodeLabel(ref));
    }

    private void addChildren(DefaultMutableTreeNode parentNode, MapRef parentRef) {
        for (MapRef child : parentRef.children()) {
            DefaultMutableTreeNode n = node(child);
            parentNode.add(n);
            addChildren(n, child);
        }
    }

    private void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private MapRef selectedRef() {
        TreePath p = tree.getSelectionPath();
        if (p == null) {
            return null;
        }
        Object o = ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
        return o instanceof NodeLabel nl ? nl.ref : null;
    }

    private void selectBySelector(DefaultMutableTreeNode root, String selector) {
        java.util.Enumeration<javax.swing.tree.TreeNode> e = root.depthFirstEnumeration();
        while (e.hasMoreElements()) {
            DefaultMutableTreeNode n = (DefaultMutableTreeNode) e.nextElement();
            if (n.getUserObject() instanceof NodeLabel nl
                    && selector.equals(nl.ref.selector())) {
                tree.setSelectionPath(new TreePath(n.getPath()));
                return;
            }
        }
    }

    // ── property form ────────────────────────────────────────────────────────────

    private void populateForm() {
        MapRef ref = selectedRef();
        boolean editable = ref != null && ref != model.root();
        for (Map.Entry<String, javax.swing.JComponent> e : fields.entrySet()) {
            String v = editable ? ref.attr(e.getKey()) : null;
            setFieldValue(e.getValue(), v == null ? "" : v);
            e.getValue().setEnabled(editable);
        }
    }

    private void onApply() {
        MapRef ref = selectedRef();
        if (ref == null || ref == model.root()) {
            return;
        }
        String selector = ref.selector();
        boolean any = false;
        for (String attr : ATTRS) {
            String now = fieldValue(fields.get(attr));
            String was = ref.attr(attr) == null ? "" : ref.attr(attr);
            if (!now.equals(was)) {
                runEdit(new EditMapCommand(mapFile.toString(), "set-attr", selector, null,
                        null, null, attr, now, null, null, null, false), false);
                any = true;
            }
        }
        if (any) {
            load();
            selectBySelector((DefaultMutableTreeNode) tree.getModel().getRoot(), selector);
        }
    }

    // ── structural actions (route through edit_map) ──────────────────────────────

    private void onInsert(String type, boolean pickHref) {
        MapRef parent = selectedRef();
        String parentSel = (parent == null || parent == model.root()) ? "/" : parent.selector();
        String href = null;
        if (pickHref) {
            JFileChooser chooser = new JFileChooser(mapFile.getParentFile());
            chooser.setFileFilter(new FileNameExtensionFilter("DITA topics (*.dita)", "dita"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            href = relativize(mapFile.getParentFile(), chooser.getSelectedFile());
        }
        String navtitle = JOptionPane.showInputDialog(this,
                (pickHref ? "Navtitle (optional):" : "Navtitle:"), "");
        if (navtitle == null) {
            return; // cancelled
        }
        runEdit(new EditMapCommand(mapFile.toString(), "insert", null, parentSel, null,
                type, null, null, href, navtitle.isBlank() ? null : navtitle, null, false),
                true);
    }

    private void onRemove() {
        MapRef ref = selectedRef();
        if (ref == null || ref == model.root()) {
            return;
        }
        if (JOptionPane.showConfirmDialog(this,
                "Remove " + ref.type() + (ref.href() != null ? " → " + ref.href() : "")
                + " and its children?", "Remove", JOptionPane.OK_CANCEL_OPTION)
                != JOptionPane.OK_OPTION) {
            return;
        }
        runEdit(new EditMapCommand(mapFile.toString(), "remove", ref.selector(), null, null,
                null, null, null, null, null, null, false), true);
    }

    private void onMove(int delta) {
        MapRef ref = selectedRef();
        if (ref == null || ref == model.root() || ref.parent() == null) {
            return;
        }
        List<MapRef> siblings = ref.parent().children();
        int idx = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).selector().equals(ref.selector())) {
                idx = i;
                break;
            }
        }
        int dest = idx + delta;
        if (idx < 0 || dest < 0 || dest >= siblings.size()) {
            return; // already at the edge
        }
        // The moved node's selector changes (its child-path shifts); reselect it by its
        // NEW position so the next action targets the right node (@id is stable).
        String parentPath = ref.parent().path();
        String newSelector = ref.id() != null ? ref.id()
                : (parentPath.equals("/") ? "" : parentPath) + "/" + (dest + 1);
        runEdit(new EditMapCommand(mapFile.toString(), "move", ref.selector(),
                ref.parent().selector(), dest, null, null, null, null, null, null, false), true);
        selectBySelector((DefaultMutableTreeNode) tree.getModel().getRoot(), newSelector);
    }

    private void onNew(boolean bookmap) {
        JFileChooser chooser = new JFileChooser(mapFile.getParentFile());
        chooser.setFileFilter(new FileNameExtensionFilter("DITA maps (*.ditamap)", "ditamap"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = chooser.getSelectedFile();
        if (!f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".ditamap")) {
            f = new File(f.getParentFile(), f.getName() + ".ditamap");
        }
        if (f.exists()) {
            JOptionPane.showMessageDialog(this, f.getName() + " already exists.",
                    "New map", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            java.nio.file.Files.writeString(f.toPath(), skeleton(bookmap),
                    java.nio.charset.StandardCharsets.UTF_8);
            if (editor != null) {
                editor.open(com.dogsbay.xml.DogsBayURLUtilities.getURLFromFile(f), null, false);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not create map: " + ex.getMessage(),
                    "New map", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String skeleton(boolean bookmap) {
        if (bookmap) {
            // Minimal valid bookmap — no empty-href <chapter> (that won't build); add
            // chapters with the Add topicref… action (which sets a real href).
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<!DOCTYPE bookmap PUBLIC \"-//OASIS//DTD DITA BookMap//EN\" \"bookmap.dtd\">\n"
                    + "<bookmap>\n"
                    + "  <booktitle><mainbooktitle>New Book</mainbooktitle></booktitle>\n"
                    + "  <frontmatter/>\n"
                    + "</bookmap>\n";
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE map PUBLIC \"-//OASIS//DTD DITA Map//EN\" \"map.dtd\">\n"
                + "<map>\n"
                + "  <title>New Map</title>\n"
                + "</map>\n";
    }

    private void runEdit(EditMapCommand cmd, boolean reload) {
        try {
            var result = exec.execute(cmd);
            for (String w : result.warnings()) {
                JOptionPane.showMessageDialog(this, w, "Reference warning",
                        JOptionPane.WARNING_MESSAGE);
            }
            if (editor != null) {
                editor.checkAllDocumentsForExternalChanges(); // refresh an open map buffer
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Map editor",
                    JOptionPane.ERROR_MESSAGE);
        }
        if (reload) {
            load();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private static void setFieldValue(javax.swing.JComponent c, String value) {
        if (c instanceof JComboBox<?> combo) {
            ((JComboBox<String>) combo).setSelectedItem(value);
        } else if (c instanceof JTextField tf) {
            tf.setText(value);
        }
    }

    private static String fieldValue(javax.swing.JComponent c) {
        if (c instanceof JComboBox<?> combo) {
            Object v = combo.getSelectedItem();
            return v == null ? "" : v.toString().trim();
        }
        return c instanceof JTextField tf ? tf.getText().trim() : "";
    }

    private static String relativize(File fromDir, File target) {
        try {
            return fromDir.toPath().toAbsolutePath().normalize()
                    .relativize(target.toPath().toAbsolutePath().normalize())
                    .toString().replace(File.separatorChar, '/');
        } catch (RuntimeException e) {
            return target.getName();
        }
    }

    /** Tree node label that carries its {@link MapRef}. */
    private static final class NodeLabel {
        private final MapRef ref;

        NodeLabel(MapRef ref) {
            this.ref = ref;
        }

        @Override
        public String toString() {
            if (ref.parent() == null) {
                return ref.type(); // the map/bookmap root
            }
            String label = ref.navtitle() != null ? ref.navtitle()
                    : (ref.href() != null ? ref.href()
                            : (ref.attr("keyref") != null ? "keyref:" + ref.attr("keyref")
                                    : ref.type()));
            return ref.type() + ": " + label;
        }
    }
}
