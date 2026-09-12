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
package com.dogsbay.dogsbayaieditor.plugin.proposals;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;

import com.dogsbay.xml.review.Proposal;

/**
 * The Proposals sidebar: what agents proposed in the active document, and
 * the buttons to decide. Selection jumps the editor to the span. The
 * controller ({@link ProposalsPlugin}) supplies the data and performs the
 * decisions; this class is layout and events only.
 */
public final class ProposalsPanel extends JPanel {

    /** What the panel asks the controller to do. */
    public interface Actions {
        void select(Proposal p);
        void accept(Proposal p);
        void reject(Proposal p);
        void acceptAll(String author);
        void rejectAll(String author);
        void resolve(Proposal comment);
    }

    private static final String EVERYONE = "everyone";

    private final ProposalListModel model = new ProposalListModel();
    private final JTable table = new JTable(model);
    private final JComboBox<String> author = new JComboBox<>();
    private final JLabel summary = new JLabel(" ");
    private final JButton accept = new JButton("Accept");
    private final JButton reject = new JButton("Reject");
    private final JButton acceptAll = new JButton("Accept all");
    private final JButton rejectAll = new JButton("Reject all");
    private final JButton resolve = new JButton("Resolve");
    private boolean updating;

    public ProposalsPanel(Actions actions) {
        super(new BorderLayout());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(70);
        table.getColumnModel().getColumn(3).setPreferredWidth(260);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || updating) {
                return;
            }
            Proposal p = selected();
            updateButtons(p);
            if (p != null) {
                actions.select(p);
            }
        });

        author.addActionListener(e -> {
            if (!updating) {
                model.filter(currentAuthor());
                updateSummary();
            }
        });
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        top.add(new JLabel("From:"));
        top.add(author);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        accept.addActionListener(e -> withSelected(actions::accept));
        reject.addActionListener(e -> withSelected(actions::reject));
        resolve.addActionListener(e -> withSelected(actions::resolve));
        acceptAll.addActionListener(e -> actions.acceptAll(currentAuthor()));
        rejectAll.addActionListener(e -> actions.rejectAll(currentAuthor()));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        buttons.add(accept);
        buttons.add(reject);
        buttons.add(resolve);
        buttons.add(acceptAll);
        buttons.add(rejectAll);
        JPanel south = new JPanel(new BorderLayout());
        south.add(summary, BorderLayout.WEST);
        south.add(buttons, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);
        updateButtons(null);
    }

    /** Replace the rows, keeping the author filter when that author is still present. */
    public void show(List<Proposal> proposals) {
        updating = true;
        try {
            String keep = currentAuthor();
            model.set(proposals);
            DefaultComboBoxModel<String> authors = new DefaultComboBoxModel<>();
            authors.addElement(EVERYONE);
            model.authors().forEach(authors::addElement);
            author.setModel(authors);
            if (keep != null && model.authors().contains(keep)) {
                author.setSelectedItem(keep);
                model.filter(keep);
            } else {
                model.filter(null);   // the kept author has nothing left: back to everyone
            }
            updateButtons(null);
            updateSummary();
        } finally {
            updating = false;
        }
    }

    private void updateSummary() {
        int changes = model.changeCount();
        summary.setText("  " + (changes == 0 ? "No proposals" : changes + " change(s)"));
        boolean any = model.getRowCount() > 0;
        acceptAll.setEnabled(any);
        rejectAll.setEnabled(any);
    }

    private void updateButtons(Proposal p) {
        accept.setEnabled(p != null && p.isChange());
        reject.setEnabled(p != null && p.isChange());
        resolve.setEnabled(p != null && !p.isChange());
    }

    private String currentAuthor() {
        Object a = author.getSelectedItem();
        return a == null || EVERYONE.equals(a) ? null : a.toString();
    }

    private Proposal selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.at(table.convertRowIndexToModel(row));
    }

    private void withSelected(Consumer<Proposal> action) {
        Proposal p = selected();
        if (p != null) {
            action.accept(p);
        }
    }

    /**
     * Continue the review at the proposal nearest {@code offset}: select its
     * row, which also jumps the editor there.
     */
    public void continueAt(int offset) {
        int row = model.rowAtOrAfter(offset);
        if (row < 0) {
            table.clearSelection();
            return;
        }
        int viewRow = table.convertRowIndexToView(row);
        table.setRowSelectionInterval(viewRow, viewRow);
        table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
    }

    ProposalListModel model() {
        return model;
    }
}
