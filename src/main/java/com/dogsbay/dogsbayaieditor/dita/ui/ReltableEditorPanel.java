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
import java.awt.FlowLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

import com.dogsbay.dogsbayaieditor.commands.EditReltableCommand;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.ReltableAuditCommand;
import com.dogsbay.dogsbayaieditor.links.reltable.Reltable;
import com.dogsbay.dogsbayaieditor.links.reltable.ReltableModel;

/**
 * A grid editor for the relationship tables in a DITA map. Renders each
 * {@code <reltable>} as a table (columns = relcolspecs, cells = their topicref
 * targets) and edits through the {@code edit_reltable} command engine — the same
 * formatting-preserving core the CLI/MCP use, so the UI stays thin and untested
 * logic lives in the tested writer. Edits are written to the map file on disk.
 */
public final class ReltableEditorPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final File mapFile;
    private final com.dogsbay.dogsbayaieditor.DogsBayAIEditor editor;
    private final HeadlessExecutor exec = new HeadlessExecutor();
    private final JComboBox<String> tableSelector = new JComboBox<>();
    private final DefaultTableModel grid = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable table = new JTable(grid);
    private ReltableModel model;
    private boolean loading; // suppress the selector listener during a rebuild

    public ReltableEditorPanel(File mapFile, com.dogsbay.dogsbayaieditor.DogsBayAIEditor editor) {
        super(new BorderLayout(6, 6));
        this.mapFile = mapFile;
        this.editor = editor;
        // Columns map 1:1 to reltable columns by position — disable reordering so the
        // view column index equals the model/reltable column.
        table.getTableHeader().setReorderingAllowed(false);
        tableSelector.addActionListener(e -> {
            if (!loading) {
                rebuildGrid();
            }
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new javax.swing.JLabel("Reltable:"));
        top.add(tableSelector);
        JButton newTable = new JButton("New table…");
        newTable.addActionListener(e -> onNewTable());
        top.add(newTable);
        add(top, BorderLayout.NORTH);

        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        button(buttons, "Add row", this::onAddRow);
        button(buttons, "Remove row", this::onRemoveRow);
        button(buttons, "Add topic…", this::onAddTarget);
        button(buttons, "Remove topic", this::onRemoveTarget);
        button(buttons, "Audit", this::onAudit);
        button(buttons, "Refresh", this::load);
        add(buttons, BorderLayout.SOUTH);

        load();
    }

    private static void button(JPanel panel, String label, Runnable action) {
        JButton b = new JButton(label);
        b.addActionListener(e -> action.run());
        panel.add(b);
    }

    /** Re-parse the map and rebuild the selector + grid. */
    public void load() {
        try {
            model = ReltableModel.parse(mapFile);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not read reltables: " + ex.getMessage(),
                    "Reltable", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int keep = tableSelector.getSelectedIndex();
        loading = true;
        try {
            tableSelector.removeAllItems();
            for (int i = 0; i < model.reltables().size(); i++) {
                Reltable t = model.reltables().get(i);
                tableSelector.addItem((t.title() != null ? t.title() : "reltable " + (i + 1))
                        + " (" + t.rows().size() + " rows)");
            }
            if (!model.reltables().isEmpty()) {
                tableSelector.setSelectedIndex(Math.min(Math.max(keep, 0),
                        model.reltables().size() - 1));
            }
        } finally {
            loading = false;
        }
        if (model.reltables().isEmpty()) {
            grid.setRowCount(0);
            grid.setColumnCount(0);
        } else {
            rebuildGrid();
        }
    }

    private Reltable selectedTable() {
        int i = tableSelector.getSelectedIndex();
        return (model == null || i < 0 || i >= model.reltables().size())
                ? null : model.reltables().get(i);
    }

    private void rebuildGrid() {
        Reltable t = selectedTable();
        if (t == null) {
            return;
        }
        int cols = Math.max(1, columnCount(t));
        String[] headers = new String[cols];
        for (int c = 0; c < cols; c++) {
            headers[c] = c < t.columns().size() && t.columns().get(c).type() != null
                    ? t.columns().get(c).type() : "topic";
        }
        grid.setColumnIdentifiers(headers);
        grid.setRowCount(0);
        for (Reltable.Row row : t.rows()) {
            Object[] cells = new Object[cols];
            for (int c = 0; c < cols; c++) {
                cells[c] = c < row.cells().size() ? cellText(row.cells().get(c)) : "";
            }
            grid.addRow(cells);
        }
    }

    private static int columnCount(Reltable t) {
        int cols = t.columns().size();
        for (Reltable.Row row : t.rows()) {
            cols = Math.max(cols, row.cells().size());
        }
        return cols;
    }

    private static String cellText(Reltable.Cell cell) {
        List<String> labels = new ArrayList<>();
        for (Reltable.Target target : cell.targets()) {
            labels.add(target.label());
        }
        return String.join(", ", labels);
    }

    // ── actions (all route through edit_reltable) ────────────────────────────────

    private void onNewTable() {
        String types = JOptionPane.showInputDialog(this,
                "Column types (comma-separated), e.g. concept,task,reference:",
                "concept,task,reference");
        if (types == null) {
            return;
        }
        run(b -> { b.op = "create-table"; b.columns = types; });
    }

    private void onAddRow() {
        if (selectedTable() == null) {
            return;
        }
        run(b -> b.op = "add-row");
    }

    private void onRemoveRow() {
        int r = table.getSelectedRow();
        if (selectedTable() == null || r < 0) {
            return;
        }
        run(b -> { b.op = "remove-row"; b.row = r; });
    }

    private void onAddTarget() {
        int r = table.getSelectedRow();
        int c = table.getSelectedColumn();
        Reltable t = selectedTable();
        if (t == null || r < 0 || c < 0 || r >= t.rows().size()) {
            JOptionPane.showMessageDialog(this, "Select a cell first.", "Add topic",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser(mapFile.getParentFile());
        chooser.setFileFilter(new FileNameExtensionFilter("DITA topics (*.dita)", "dita"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        String href = relativize(mapFile.getParentFile(), chooser.getSelectedFile());
        run(b -> { b.op = "add-target"; b.row = r; b.col = c; b.href = href; });
    }

    private void onRemoveTarget() {
        int r = table.getSelectedRow();
        int c = table.getSelectedColumn();
        Reltable t = selectedTable();
        if (t == null || r < 0 || c < 0 || r >= t.rows().size()
                || c >= t.rows().get(r).cells().size()
                || t.rows().get(r).cells().get(c).targets().isEmpty()) {
            return;
        }
        run(b -> { b.op = "remove-target"; b.row = r; b.col = c; b.index = 0; });
    }

    private void onAudit() {
        try {
            var res = exec.execute(new ReltableAuditCommand(mapFile.toString()));
            StringBuilder sb = new StringBuilder();
            for (var i : res.issues()) {
                sb.append("[").append(i.severity()).append("] ").append(i.cell())
                  .append(" — ").append(i.reason()).append('\n');
            }
            sb.append('\n').append(res.generatedLinks().size())
              .append(" related-link(s) would be generated.");
            JOptionPane.showMessageDialog(this, sb.toString(), "Reltable audit",
                    res.isClean() ? JOptionPane.INFORMATION_MESSAGE
                            : JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Reltable audit",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void run(java.util.function.Consumer<Args> cfg) {
        Args a = new Args();
        cfg.accept(a);
        int tableIndex = Math.max(0, tableSelector.getSelectedIndex());
        try {
            exec.execute(new EditReltableCommand(mapFile.toString(), tableIndex, a.op,
                    a.row, a.col, a.index, a.href, a.keyref, a.navtitle, a.name, a.value,
                    a.columns, false));
            // The map was rewritten on disk; if it's open in a tab, reload that buffer
            // (clean buffers reload silently; a dirty one is deferred, not clobbered).
            if (editor != null) {
                editor.checkAllDocumentsForExternalChanges();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Reltable",
                    JOptionPane.ERROR_MESSAGE);
        }
        load();
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

    private static final class Args {
        String op;
        Integer row;
        Integer col;
        Integer index;
        String href;
        String keyref;
        String navtitle;
        String name;
        String value;
        String columns;
    }
}
