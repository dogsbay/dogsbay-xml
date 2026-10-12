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
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import com.dogsbay.xml.author.model.AuthorBlock;

/**
 * Attribute editor for the selected block: a two-column table whose edits
 * commit as undoable attribute transactions. Reserved attributes (names
 * starting with '#', e.g. the image alt carrier) are hidden.
 */
class AttributePanel extends JPanel {

    private final AuthorEditorPanel panel;
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final JLabel title = new JLabel("Attributes");
    private AuthorBlock block;

    AttributePanel(AuthorEditorPanel panel) {
        super(new BorderLayout(4, 2));
        this.panel = panel;
        setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 8));

        title.setFont(title.getFont().deriveFont(11f));
        JButton add = new JButton("+");
        add.setMargin(new java.awt.Insets(0, 4, 0, 4));
        add.setFocusable(false);
        add.setToolTipText("Add attribute");
        add.addActionListener(e -> model.addPlaceholderRow());
        JButton remove = new JButton("−");
        remove.setMargin(new java.awt.Insets(0, 4, 0, 4));
        remove.setFocusable(false);
        remove.setToolTipText("Remove selected attribute");
        remove.addActionListener(e -> removeSelected());

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setOpaque(false);
        header.add(title);
        header.add(Box.createHorizontalGlue());
        header.add(add);
        header.add(Box.createHorizontalStrut(2));
        header.add(remove);
        add(header, BorderLayout.NORTH);

        table.setRowHeight(20);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(100, 76));
        add(scroll, BorderLayout.CENTER);

        showBlock(null);
    }

    final void showBlock(AuthorBlock block) {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        this.block = block;
        title.setText(block == null ? "Attributes"
                : "Attributes — " + block.getType().getLabel());
        model.load(block);
        setVisible(block != null);
    }

    private void removeSelected() {
        int row = table.getSelectedRow();
        if (block != null && row >= 0 && row < model.rows.size()) {
            String name = model.rows.get(row)[0];
            if (!name.isEmpty()) {
                panel.setBlockAttribute(block, name, null);
            }
            model.load(block);
        }
    }

    private final class Model extends AbstractTableModel {

        private final List<String[]> rows = new ArrayList<>();

        void load(AuthorBlock block) {
            rows.clear();
            if (block != null) {
                for (Map.Entry<String, String> e : block.getAttributes().entrySet()) {
                    if (!e.getKey().startsWith("#")) {
                        rows.add(new String[] {e.getKey(), e.getValue()});
                    }
                }
            }
            fireTableDataChanged();
        }

        void addPlaceholderRow() {
            rows.add(new String[] {"", ""});
            fireTableDataChanged();
            int last = rows.size() - 1;
            table.editCellAt(last, 0);
            table.changeSelection(last, 0, false, false);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Name" : "Value";
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return rows.get(rowIndex)[columnIndex];
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            String[] row = rows.get(rowIndex);
            String old = row[columnIndex];
            String value = aValue == null ? "" : aValue.toString().trim();
            if (value.equals(old) || block == null) {
                return;
            }
            if (columnIndex == 0 && !old.isEmpty()) {
                // renaming: drop the old attribute
                panel.setBlockAttribute(block, old, null);
            }
            row[columnIndex] = value;
            if (!row[0].isEmpty()) {
                panel.setBlockAttribute(block, row[0], row[1]);
            }
        }
    }
}
