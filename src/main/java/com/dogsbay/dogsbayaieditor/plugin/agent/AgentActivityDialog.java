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
package com.dogsbay.dogsbayaieditor.plugin.agent;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import com.dogsbay.agent.session.AuditLog;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.commands.CommandException;
import com.dogsbay.dogsbayaieditor.commands.OpenCommand;

/**
 * Project → Agent activity: what agents did in this project, from the audit
 * log, newest first. Read-only; "Open log" opens the raw file in the editor.
 */
public final class AgentActivityDialog extends JDialog {

    /** The log as rows, newest first. Swing-free apart from the table model base. */
    static final class Model extends AbstractTableModel {
        private static final String[] COLUMNS = {"When", "Agent", "Command", "Files", "Mode", "Outcome"};
        private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());
        private List<AuditLog.Entry> rows = List.of();

        String problem;

        void load(Path root) {
            problem = null;
            List<AuditLog.Entry> read;
            try {
                read = root == null ? List.of() : AuditLog.read(root);
            } catch (RuntimeException e) {
                read = List.of();
                problem = e.getMessage();
            }
            rows = read.reversed();
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int c) { return COLUMNS[c]; }

        @Override
        public Object getValueAt(int r, int c) {
            AuditLog.Entry e = rows.get(r);
            return switch (c) {
                case 0 -> TIME.format(e.at());
                case 1 -> e.displayName() + " (" + e.identity() + ")";
                case 2 -> e.command();
                case 3 -> String.join(", ", e.files());
                case 4 -> e.dryRun() ? "dry run" : "applied";
                default -> e.outcome();
            };
        }
    }

    private final DogsBayAIEditor editor;
    private final Model model = new Model();
    private final JLabel summary = new JLabel(" ");

    public AgentActivityDialog(Window owner, DogsBayAIEditor editor) {
        super(owner, "Agent activity", ModalityType.MODELESS);
        this.editor = editor;
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(140);
        table.getColumnModel().getColumn(3).setPreferredWidth(320);

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> reload());
        JButton open = new JButton("Open log");
        open.setToolTipText("Open the raw commands.jsonl in the editor");
        open.addActionListener(e -> openLog());
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(refresh);
        buttons.add(open);
        buttons.add(close);

        JPanel south = new JPanel(new BorderLayout());
        south.add(summary, BorderLayout.WEST);
        south.add(buttons, BorderLayout.EAST);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setSize(900, 420);
        setLocationRelativeTo(owner);
        reload();
    }

    private Path logFile() {
        Path root = editor.projectRootForAgents();
        return root == null ? null : root.resolve(AuditLog.DIR).resolve(AuditLog.FILE);
    }

    private void reload() {
        Path root = editor.projectRootForAgents();
        model.load(root);
        Path log = logFile();
        if (root == null) {
            summary.setText("  No project is open");
        } else if (model.problem != null) {
            summary.setText("  Could not read the log: " + model.problem);
        } else if (log == null || !Files.exists(log)) {
            summary.setText("  No agent has changed anything in this project yet");
        } else {
            summary.setText("  " + model.getRowCount() + " entries in " + log);
        }
    }

    private void openLog() {
        Path log = logFile();
        if (log == null || !Files.exists(log)) {
            JOptionPane.showMessageDialog(this, "There is no audit log yet.", "Agent activity",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            editor.getCommandExecutor().execute(new OpenCommand(log, null));
        } catch (CommandException e) {
            JOptionPane.showMessageDialog(this, "Could not open the log: " + e.getMessage(), "Agent activity",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
