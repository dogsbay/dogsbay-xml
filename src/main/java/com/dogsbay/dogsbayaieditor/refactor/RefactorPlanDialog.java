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

package com.dogsbay.dogsbayaieditor.refactor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/**
 * Modal preview of a refactoring plan: summary, warnings, the table of
 * attribute edits, and Apply/Cancel. Nothing is changed unless the user
 * clicks Apply. Resizable; cells show their full text (and the file
 * column its absolute path) as tooltips.
 */
public class RefactorPlanDialog extends JDialog {

    /** What the user chose. */
    public enum Outcome { APPLY, ALTERNATIVE, CANCEL }

    private Outcome outcome = Outcome.CANCEL;

    public RefactorPlanDialog(Frame owner, String title, String summary,
                              List<String> warnings, RefactorPlanTableModel model) {
        this(owner, title, summary, warnings, model, "Apply", null);
    }

    /**
     * @param summary          headline; newlines render as separate lines
     * @param applyLabel       label for the primary (confirm) button
     * @param alternativeLabel optional second action (e.g. "Delete Anyway");
     *                         null for the plain Apply/Cancel dialog
     */
    public RefactorPlanDialog(Frame owner, String title, String summary,
                              List<String> warnings, RefactorPlanTableModel model,
                              String applyLabel, String alternativeLabel) {
        super(owner, title, true);

        JPanel main = new JPanel(new BorderLayout(0, 8));
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 8, 10));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        boolean first = true;
        for (String line : summary.split("\n")) {
            JLabel label = new JLabel(line);
            if (first) {
                label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
                first = false;
            }
            label.setAlignmentX(LEFT_ALIGNMENT);
            header.add(label);
            header.add(Box.createVerticalStrut(2));
        }
        if (!warnings.isEmpty()) {
            JTextArea warningArea = new JTextArea(
                    String.join("\n", warnings.stream().map(w -> "⚠ " + w).toList()));
            warningArea.setEditable(false);
            warningArea.setLineWrap(true);
            warningArea.setWrapStyleWord(true);
            warningArea.setOpaque(false);
            warningArea.setForeground(UIManager.getColor("Actions.Yellow") != null
                    ? UIManager.getColor("Actions.Yellow")
                    : new java.awt.Color(176, 124, 0));
            JScrollPane warningScroll = new JScrollPane(warningArea,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            warningScroll.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
            warningScroll.setOpaque(false);
            warningScroll.getViewport().setOpaque(false);
            int rows = Math.min(warnings.size(), 5);
            warningScroll.setPreferredSize(new Dimension(740,
                    (rows + 1) * warningArea.getFontMetrics(warningArea.getFont()).getHeight()));
            warningScroll.setAlignmentX(LEFT_ALIGNMENT);
            warningScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                    warningScroll.getPreferredSize().height));
            header.add(Box.createVerticalStrut(4));
            header.add(warningScroll);
        }
        main.add(header, BorderLayout.NORTH);

        JTable table = new JTable(model) {
            @Override
            public String getToolTipText(MouseEvent e) {
                Point p = e.getPoint();
                int row = rowAtPoint(p);
                int col = columnAtPoint(p);
                if (row < 0 || col < 0) {
                    return null;
                }
                int modelCol = convertColumnIndexToModel(col);
                if (modelCol == 0) {
                    return model.absolutePath(convertRowIndexToModel(row));
                }
                Object value = getValueAt(row, col);
                return value == null || value.toString().isEmpty()
                        ? null : value.toString();
            }
        };
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(240);
        table.getColumnModel().getColumn(1).setPreferredWidth(40);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(180);
        table.getColumnModel().getColumn(4).setPreferredWidth(180);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(760, 280));
        main.add(scroll, BorderLayout.CENTER);

        JButton applyButton = new JButton(applyLabel);
        applyButton.addActionListener(e -> {
            outcome = Outcome.APPLY;
            setVisible(false);
        });
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> setVisible(false));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(applyButton);
        if (alternativeLabel != null) {
            JButton alternativeButton = new JButton(alternativeLabel);
            alternativeButton.addActionListener(e -> {
                outcome = Outcome.ALTERNATIVE;
                setVisible(false);
            });
            buttons.add(alternativeButton);
        }
        buttons.add(cancelButton);
        main.add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(applyButton);
        setContentPane(main);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);
        pack();
        setMinimumSize(new Dimension(520, 300));
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the dialog and blocks; true when the user clicked Apply.
     */
    public boolean showAndConfirm() {
        return showAndChoose() == Outcome.APPLY;
    }

    /**
     * Shows the dialog and blocks; returns which button closed it.
     */
    public Outcome showAndChoose() {
        setVisible(true);
        dispose();
        return outcome;
    }
}
