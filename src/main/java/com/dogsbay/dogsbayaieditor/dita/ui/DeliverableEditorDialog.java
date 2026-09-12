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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.ditaproject.Deliverable;
import com.dogsbay.dogsbayaieditor.ditaproject.DeliverableEdit;
import com.dogsbay.dogsbayaieditor.ditaproject.Param;

/**
 * Form to add or edit one {@link Deliverable}, writing back to a DITA-OT project
 * file via {@link com.dogsbay.dogsbayaieditor.ditaproject.DitaProjectWriter}. The
 * input map and DITAVAL are editable dropdowns prefilled with the project's
 * {@code .ditamap}/{@code .ditaval} files (Browse… still picks an arbitrary one);
 * publication params are edited in a table. Paths are stored relative to the
 * project file. Extra DITAVALs (beyond the first) are carried through unchanged.
 *
 * <p>{@link #showDialog} returns the {@link DeliverableEdit} to write, or null if
 * cancelled. Name is read-only when editing (renaming would orphan the original).
 */
public final class DeliverableEditorDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private static final String[] TRANSTYPES = { "html5", "pdf", "pdf2", "xhtml", "markdown" };

    private final Path projectDir;
    private final Deliverable existing;
    private final List<Path> preservedExtraDitavals;

    private JTextField nameField;
    private JComboBox<String> inputCombo;
    private JComboBox<String> ditavalCombo;
    private JComboBox<String> transtypeCombo;
    private JTextField outputField;
    private DefaultTableModel paramsModel;
    private JTable paramsTable;

    private DeliverableEdit result;

    private DeliverableEditorDialog(DogsBayAIEditor editor, Path projectFile, Deliverable existing) {
        super(editor, existing == null ? "Add Deliverable" : "Edit Deliverable", true);
        this.projectDir = projectFile.toAbsolutePath().normalize().getParent();
        this.existing = existing;
        this.preservedExtraDitavals = new ArrayList<>();
        if (existing != null && existing.ditavals().size() > 1) {
            preservedExtraDitavals.addAll(existing.ditavals().subList(1, existing.ditavals().size()));
        }
        initUI(projectFile);
        pack();
        setLocationRelativeTo(editor);
    }

    /** Show the editor; returns the edit to write, or null if cancelled. */
    public static DeliverableEdit showDialog(DogsBayAIEditor editor, Path projectFile,
            Deliverable existing) {
        DeliverableEditorDialog d = new DeliverableEditorDialog(editor, projectFile, existing);
        d.setVisible(true);
        return d.result;
    }

    private void initUI(Path projectFile) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;
        int row = 0;

        nameField = new JTextField(existing != null ? existing.name() : "", 28);
        nameField.setEditable(existing == null); // renaming would orphan the original
        addRow(form, g, row++, "Name:", nameField, null);

        inputCombo = editableCombo(discover("ditamap"),
                existing != null ? rel(existing.map()) : "");
        addRow(form, g, row++, "Input map:", inputCombo, browse(inputCombo, "DITA maps", "ditamap"));

        ditavalCombo = editableCombo(discover("ditaval"),
                existing != null && existing.ditaval() != null ? rel(existing.ditaval()) : "");
        addRow(form, g, row++, "DITAVAL (optional):", ditavalCombo,
                browse(ditavalCombo, "DITAVAL files", "ditaval"));

        transtypeCombo = new JComboBox<>(TRANSTYPES);
        transtypeCombo.setEditable(true);
        transtypeCombo.setSelectedItem(existing != null && existing.transtype() != null
                ? existing.transtype() : "html5");
        addRow(form, g, row++, "Transtype:", transtypeCombo, null);

        outputField = new JTextField(
                existing != null && existing.output() != null ? existing.output().toString() : "", 28);
        addRow(form, g, row++, "Output (optional):", outputField, null);

        if (!preservedExtraDitavals.isEmpty()) {
            g.gridx = 1; g.gridy = row++; g.gridwidth = 2;
            form.add(new JLabel("<html><i>" + preservedExtraDitavals.size()
                    + " extra DITAVAL(s) preserved.</i></html>"), g);
            g.gridwidth = 1;
        }

        add(form, BorderLayout.NORTH);
        add(buildParamsPanel(), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("Save…");
        ok.addActionListener(e -> onSave(projectFile));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        buttons.add(ok);
        buttons.add(cancel);
        getRootPane().setDefaultButton(ok);
        add(buttons, BorderLayout.SOUTH);
    }

    private JPanel buildParamsPanel() {
        // One column per DITA-OT param attribute — fill exactly one of value/href/path.
        paramsModel = new DefaultTableModel(new Object[] {"Name", "value", "href", "path"}, 0);
        if (existing != null) {
            for (Param p : existing.params()) {
                Object[] r = {p.name(), "", "", ""};
                switch (p.kind()) {
                    case VALUE -> r[1] = p.value();
                    case HREF -> r[2] = p.value();
                    case PATH -> r[3] = p.value();
                }
                paramsModel.addRow(r);
            }
        }
        paramsTable = new JTable(paramsModel);
        JScrollPane scroll = new JScrollPane(paramsTable);
        scroll.setPreferredSize(new Dimension(460, 120));

        JButton add = new JButton("Add param");
        add.addActionListener(e -> paramsModel.addRow(new Object[] {"", "", "", ""}));
        JButton remove = new JButton("Remove");
        remove.addActionListener(e -> {
            if (paramsTable.isEditing()) {
                paramsTable.getCellEditor().stopCellEditing();
            }
            int r = paramsTable.getSelectedRow();
            if (r >= 0) {
                paramsModel.removeRow(r);
            }
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(add);
        buttons.add(remove);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Publication parameters"));
        panel.add(new JLabel("<html><i>Set one of value / href / path per param — "
                + "href and path resolve relative to the project file.</i></html>"),
                BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private void addRow(JPanel form, GridBagConstraints g, int row, String label,
            java.awt.Component field, JButton trailing) {
        g.gridx = 0; g.gridy = row; g.gridwidth = 1; g.fill = GridBagConstraints.NONE;
        form.add(new JLabel(label), g);
        g.gridx = 1; g.gridwidth = trailing == null ? 2 : 1; g.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, g);
        if (trailing != null) {
            g.gridx = 2; g.gridwidth = 1; g.fill = GridBagConstraints.NONE;
            form.add(trailing, g);
        }
    }

    private JComboBox<String> editableCombo(List<String> items, String selected) {
        JComboBox<String> combo = new JComboBox<>(items.toArray(new String[0]));
        combo.setEditable(true);
        combo.setSelectedItem(selected == null ? "" : selected);
        return combo;
    }

    /** Every {@code .ext} file under the project directory, project-relative, sorted. */
    private List<String> discover(String ext) {
        List<String> out = new ArrayList<>();
        if (projectDir == null) {
            return out;
        }
        try (var walk = Files.walk(projectDir, 20)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)
                        .endsWith("." + ext))
                .map(this::rel).sorted().forEach(out::add);
        } catch (Exception ignore) {
            // best effort — the combo is editable, Browse always works
        }
        return out;
    }

    private JButton browse(JComboBox<String> target, String desc, String ext) {
        JButton b = new JButton("Browse…");
        b.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(projectDir.toFile());
            chooser.setFileFilter(new FileNameExtensionFilter(desc + " (*." + ext + ")", ext));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                target.setSelectedItem(rel(chooser.getSelectedFile().toPath()));
            }
        });
        return b;
    }

    /** A project-file-relative string for a path (absolute → relativized; already-relative kept). */
    private String rel(Path p) {
        if (p == null) {
            return "";
        }
        Path abs = p.isAbsolute() ? p.normalize() : projectDir.resolve(p).normalize();
        try {
            return projectDir.relativize(abs).toString();
        } catch (IllegalArgumentException e) {
            return abs.toString();
        }
    }

    private static String comboText(JComboBox<String> combo) {
        Object v = combo.getSelectedItem();
        return v == null ? "" : v.toString().trim();
    }

    private static String cell(DefaultTableModel m, int row, int col) {
        Object v = m.getValueAt(row, col);
        return v == null ? "" : v.toString().trim();
    }

    private void onSave(Path projectFile) {
        String name = nameField.getText().trim();
        String input = comboText(inputCombo);
        if (name.isEmpty() || input.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name and input map are required.",
                    "Incomplete", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (paramsTable.isEditing()) {
            paramsTable.getCellEditor().stopCellEditing();
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Write this deliverable to\n" + projectFile + " ?",
                "Confirm write", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }

        List<String> ditavals = new ArrayList<>();
        String dv = comboText(ditavalCombo);
        if (!dv.isEmpty()) {
            ditavals.add(dv);
        }
        for (Path extra : preservedExtraDitavals) {
            ditavals.add(rel(extra));
        }

        List<Param> params = new ArrayList<>();
        for (int i = 0; i < paramsModel.getRowCount(); i++) {
            String pn = cell(paramsModel, i, 0);
            if (pn.isEmpty()) {
                continue; // a blank-name row is skipped
            }
            String val = cell(paramsModel, i, 1);
            String href = cell(paramsModel, i, 2);
            String path = cell(paramsModel, i, 3);
            // an explicit href/path wins; otherwise it's a literal value (incl. empty)
            if (!href.isEmpty()) {
                params.add(new Param(pn, href, Param.Kind.HREF));
            } else if (!path.isEmpty()) {
                params.add(new Param(pn, path, Param.Kind.PATH));
            } else {
                params.add(new Param(pn, val, Param.Kind.VALUE));
            }
        }

        String transtype = comboText(transtypeCombo);
        String output = outputField.getText().trim();
        result = new DeliverableEdit(name, input, ditavals,
                transtype.isEmpty() ? null : transtype,
                output.isEmpty() ? null : output, params);
        dispose();
    }
}
