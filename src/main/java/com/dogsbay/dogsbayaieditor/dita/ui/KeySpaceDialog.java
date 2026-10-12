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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.function.BiConsumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import com.dogsbay.dogsbayaieditor.commands.results.KeyInfo;

/**
 * The key space of a root map: every key, what it resolves to, and where it was defined.
 *
 * <p>A key is the one thing in DITA whose definition is deliberately somewhere else, and until now the only way
 * to see the whole set was {@code dogsbay-xml keys} in a terminal. A reader asking "where does
 * {@code product-name} come from?" had to leave the editor to find out.
 *
 * <p>Double-click a row to open the map that defines that key, at the line that defines it.
 */
public class KeySpaceDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final String[] COLUMNS = {"Key", "Resolves to", "Text", "Defined in", "Line"};
    /** The line column is how the double-click navigates; a reader does not need to see it. */
    private static final int LINE_COLUMN = 4;

    /**
     * @param owner      the editor window
     * @param rootMap    the map whose key space this is, for the title
     * @param keys       the keys, in the order the command returned them
     * @param navigateTo opens a file at a line
     */
    public KeySpaceDialog(java.awt.Frame owner, File rootMap, List<KeyInfo> keys,
            BiConsumer<File, Integer> navigateTo) {
        super(owner, "Key Space — " + (rootMap == null ? "project" : rootMap.getName()), false);

        DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (KeyInfo key : keys) {
            model.addRow(new Object[] {
                key.name(),
                resolvedOrNot(rootMap, key),
                key.text() == null ? "" : key.text(),
                shortPath(rootMap, key.definedIn()),
                key.line()
            });
        }

        JTable table = new JTable(model);
        table.removeColumn(table.getColumnModel().getColumn(LINE_COLUMN));
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || navigateTo == null) {
                    return;
                }
                // The row under the pointer, not the selected one: a double-click that lands off a row, or
                // one whose selection has moved under a filter keystroke, would otherwise open the file of a
                // key the reader is not pointing at.
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0) {
                    return;
                }
                int inModel = table.convertRowIndexToModel(row);
                Object where = model.getValueAt(inModel, 3);
                Object line = model.getValueAt(inModel, LINE_COLUMN);
                File file = resolve(rootMap, String.valueOf(where));
                if (file != null) {
                    navigateTo.accept(file, line instanceof Integer n ? n : null);
                }
            }
        });

        JTextField filter = new JTextField();
        filter.getDocument().addDocumentListener(new DocumentListener() {
            private void apply() {
                String text = filter.getText();
                // The columns a reader can see. The model still has the line column after removeColumn hid
                // it, and filtering on it means typing "12" keeps every key defined on line 12, 120 or 312
                // for no reason the table shows.
                sorter.setRowFilter(text == null || text.isBlank() ? null
                        : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text.trim()),
                                0, 1, 2, 3));
            }

            @Override public void insertUpdate(DocumentEvent e) { apply(); }
            @Override public void removeUpdate(DocumentEvent e) { apply(); }
            @Override public void changedUpdate(DocumentEvent e) { apply(); }
        });

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        top.add(new JLabel(keys.size() + (keys.size() == 1 ? " key" : " keys")), BorderLayout.WEST);
        top.add(filter, BorderLayout.CENTER);

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        buttons.add(close);

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setPreferredSize(new Dimension(760, 460));
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * What a key resolves to, or the href it could not resolve, said so.
     *
     * <p>An unresolved href is relative to the map that defined it rather than to this one, so shown plainly
     * it reads exactly like a path that resolved — and a key pointing at a deleted topic looks fine.
     */
    static String resolvedOrNot(File rootMap, KeyInfo key) {
        if (key.resolvedPath() != null && !key.resolvedPath().isBlank()) {
            return shortPath(rootMap, key.resolvedPath());
        }
        return key.href() == null || key.href().isBlank() ? "" : key.href() + "  (does not resolve)";
    }

    /** A path as the reader thinks of it: relative to the map's folder when it is under it. */
    static String shortPath(File rootMap, String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        File base = rootMap == null ? null : rootMap.getParentFile();
        if (base == null) {
            return path;
        }
        String prefix = base.getAbsolutePath() + File.separator;
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }

    /** The reverse of {@link #shortPath}: what a row's text points at on disk. */
    static File resolve(File rootMap, String shown) {
        if (shown == null || shown.isBlank()) {
            return null;
        }
        File direct = new File(shown);
        if (direct.isAbsolute()) {
            return direct.isFile() ? direct : null;
        }
        File base = rootMap == null ? null : rootMap.getParentFile();
        File file = base == null ? direct : new File(base, shown);
        return file.isFile() ? file : null;
    }
}
