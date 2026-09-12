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

package com.dogsbay.dogsbayaieditor.properties;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;

/**
 * The key settings: every command the product has, with the key it carries.
 *
 * <p>The list is the product's own, so a command that leaves takes its binding
 * with it and one that arrives can be bound the day it lands. The old page
 * listed whatever a saved file contained, which is why it offered to rebind an
 * XSLT debugger that is not shipped and offered nothing for the AI agent.
 */
public final class KeyBindingsPanel extends JPanel {

    private KeyBindings bindings;
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final JTextField filter = new JTextField();
    private List<KeyBinding> shown = new ArrayList<>();

    public KeyBindingsPanel(KeyBindings bindings) {
        super(new BorderLayout(0, 6));
        this.bindings = bindings;
        setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setReorderingAllowed(false);

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.add(new JLabel("Filter:"), BorderLayout.WEST);
        top.add(filter, BorderLayout.CENTER);
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) {
                refresh();
            }

            @Override public void removeUpdate(DocumentEvent e) {
                refresh();
            }

            @Override public void changedUpdate(DocumentEvent e) {
                refresh();
            }
        });

        JButton edit = new JButton("Change…");
        edit.addActionListener(e -> changeSelected());
        JButton clear = new JButton("Bind nothing");
        clear.addActionListener(e -> withSelected(binding -> {
            bindings.bind(binding.id(), null);
            refresh();
        }));
        JButton reset = new JButton("Reset");
        reset.addActionListener(e -> withSelected(binding -> {
            bindings.reset(binding.id());
            refresh();
        }));
        JButton resetAll = new JButton("Reset all");
        resetAll.addActionListener(e -> {
            int answer = JOptionPane.showConfirmDialog(this,
                    "Restore every command's default key?", "Reset all",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.OK_OPTION) {
                bindings.resetAll();
                refresh();
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(resetAll);
        buttons.add(reset);
        buttons.add(clear);
        buttons.add(edit);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        refresh();
    }

    /** Rows matching the filter, on the command, its group or its key. */
    /**
     * Point the page at a different set of bindings and redraw. The settings
     * dialog hands in a fresh copy each time it opens, which is what makes
     * Cancel discard rather than defer.
     */
    public void setBindings(KeyBindings bindings) {
        this.bindings = bindings;
        refresh();
    }

    public void refresh() {
        String needle = filter.getText().strip().toLowerCase(Locale.ROOT);
        List<KeyBinding> matches = new ArrayList<>();
        for (KeyBinding binding : bindings.all()) {
            if (needle.isEmpty() || matches(binding, needle)) {
                matches.add(binding);
            }
        }
        shown = matches;
        model.fireTableDataChanged();
    }

    private boolean matches(KeyBinding binding, String needle) {
        return binding.label().toLowerCase(Locale.ROOT).contains(needle)
                || binding.category().toLowerCase(Locale.ROOT).contains(needle)
                || KeyStrokeCapture.describe(binding.defaultStroke())
                        .toLowerCase(Locale.ROOT).contains(needle);
    }

    private void withSelected(java.util.function.Consumer<KeyBinding> body) {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        body.accept(shown.get(table.convertRowIndexToModel(row)));
    }

    private void changeSelected() {
        withSelected(binding -> {
            KeyStroke stroke = KeyStrokeCapture.ask(this, binding.label());
            if (stroke == null) {
                return;
            }
            String clash = commandUsing(stroke, binding.id());
            if (clash != null) {
                int answer = JOptionPane.showConfirmDialog(this,
                        KeyStrokeCapture.describe(stroke) + " is " + clash + ".\n\n"
                        + "Give it to " + binding.label() + " instead?",
                        "Key already used", JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (answer != JOptionPane.OK_OPTION) {
                    return;
                }
                // Taking a key means the other command loses it; leaving both
                // bound is how a shortcut runs the wrong thing.
                bindings.bind(idUsing(stroke, binding.id()), null);
            }
            bindings.bind(binding.id(), stroke);
            refresh();
        });
    }

    /** The label of the command already using this key, or null. */
    String commandUsing(KeyStroke stroke, String exceptId) {
        for (KeyBinding binding : bindings.all()) {
            if (!binding.id().equals(exceptId) && stroke.equals(binding.defaultStroke())) {
                return binding.label();
            }
        }
        return null;
    }

    private String idUsing(KeyStroke stroke, String exceptId) {
        for (KeyBinding binding : bindings.all()) {
            if (!binding.id().equals(exceptId) && stroke.equals(binding.defaultStroke())) {
                return binding.id();
            }
        }
        return null;
    }

    /** The rows currently shown, for tests. */
    List<KeyBinding> rows() {
        return List.copyOf(shown);
    }

    /** The filter field, for tests. */
    JTextField filterField() {
        return filter;
    }

    private final class Model extends AbstractTableModel {

        private final String[] columns = {"Command", "Group", "Key", ""};

        @Override public int getRowCount() {
            return shown.size();
        }

        @Override public int getColumnCount() {
            return columns.length;
        }

        @Override public String getColumnName(int column) {
            return columns[column];
        }

        @Override public Object getValueAt(int row, int column) {
            KeyBinding binding = shown.get(row);
            return switch (column) {
                case 0 -> binding.label();
                case 1 -> binding.category();
                case 2 -> KeyStrokeCapture.describe(binding.defaultStroke());
                // A quiet mark, so a reader can see what they have changed
                // without a column of "no"s for everything they have not.
                default -> bindings.isOverridden(binding.id()) ? "changed" : "";
            };
        }
    }
}
