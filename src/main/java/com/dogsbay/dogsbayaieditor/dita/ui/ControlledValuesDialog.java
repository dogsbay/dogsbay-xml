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
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import com.dogsbay.dogsbayaieditor.commands.results.SubjectDefinition;

/**
 * What a subject scheme allows: each profiling attribute, and the values a build will accept for it.
 *
 * <p>The check that enforces this has been in the editor for a while — Project ▸ Validate Files ▸ Controlled
 * Values — but there was no way to see the rules it was enforcing without {@code dogsbay-xml list-subjects} in
 * a terminal. A writer asking "what can platform be?" is asking the most ordinary question there is about a
 * subject scheme, and the editor had no answer.
 *
 * <p>Read-only. The values come from the scheme; changing them means editing it.
 */
public class ControlledValuesDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final String[] COLUMNS = {"Attribute", "Allowed values"};

    /**
     * @param owner       the editor window
     * @param governing   the map whose scheme this is, for the title
     * @param definitions one row per attribute the scheme governs
     */
    public ControlledValuesDialog(java.awt.Frame owner, java.io.File governing,
            List<SubjectDefinition> definitions) {
        super(owner, "Controlled Values — " + (governing == null ? "project" : governing.getName()), false);

        DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (SubjectDefinition definition : definitions) {
            model.addRow(new Object[] {
                "@" + definition.attribute(),
                String.join("  ", definition.values())
            });
        }

        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(140);
        table.getColumnModel().getColumn(1).setPreferredWidth(520);

        // Careful about the empty case: nothing here can tell "this map's closure has no scheme" from "it has
        // one that would not parse", and Check Project searches wider than this view does. Saying "every value
        // is allowed" would be a claim about all three.
        String summary = definitions.isEmpty()
                ? "No subject scheme in this map's closure governs any attribute."
                : definitions.size() + (definitions.size() == 1 ? " attribute is governed."
                        : " attributes are governed.")
                        // Both, because they differ: DITA-OT warns and carries on, and Check Project counts a
                        // value outside the scheme as blocking — which is what a reader is gated by.
                        + " Check Project fails on a value that is not one of these; a build warns.";

        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        top.add(new JLabel(summary), BorderLayout.WEST);

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        buttons.add(close);

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setPreferredSize(new Dimension(700, 360));
        pack();
        setLocationRelativeTo(owner);
    }
}
