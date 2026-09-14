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
import java.nio.file.Path;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;

import com.dogsbay.dogsbayaieditor.plugin.proposals.ProjectProposals.FileProposals;
import com.dogsbay.dogsbayaieditor.plugin.proposals.ProposalListModel.Entry;
import com.dogsbay.xml.review.Proposal;

/**
 * The Proposals sidebar: what agents proposed in the active document, or in
 * every file of the project, and the buttons to decide. Selection jumps the
 * editor to the span, opening its file when needed. The controller
 * ({@link ProposalsPlugin}) supplies the data and performs the decisions;
 * this class is layout and events only.
 */
public final class ProposalsPanel extends JPanel {

    /** What the panel asks the controller to do; a null file means the active document. */
    public interface Actions {
        void select(Path file, Proposal p);
        void accept(Path file, Proposal p);
        void reject(Path file, Proposal p);
        void acceptAll(Path file, String author);
        void rejectAll(Path file, String author);
        void resolve(Path file, Proposal comment);
        /** The scope switch moved; {@code project} is true for all files. */
        void scopeChanged(boolean project);
        /** Look for proposals across the project again. */
        void rescan();
    }

    private static final String EVERYONE = "everyone";
    static final String THIS_DOCUMENT = "This document";
    static final String ALL_FILES = "All files";

    private final ProposalListModel model = new ProposalListModel();
    private final JTable table = new JTable(model);
    private final JComboBox<String> scope = new JComboBox<>(new String[] {THIS_DOCUMENT, ALL_FILES});
    private final JComboBox<String> author = new JComboBox<>();
    private final JLabel summary = new JLabel(" ");
    private final JLabel others = new JLabel();
    private final JButton showAll = new JButton("Show all");
    private final JButton rescan = new JButton("Refresh");
    private final JButton accept = new JButton("Accept");
    private final JButton reject = new JButton("Reject");
    private final JButton acceptAll = new JButton("Accept all");
    private final JButton rejectAll = new JButton("Reject all");
    private final JButton resolve = new JButton("Resolve");
    private boolean updating;

    public ProposalsPanel(Actions actions) {
        super(new BorderLayout());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sizeColumns();
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || updating) {
                return;
            }
            Entry selected = selected();
            updateButtons(selected);
            if (selected != null) {
                actions.select(selected.file(), selected.proposal());
            }
        });

        author.addActionListener(e -> {
            if (!updating) {
                model.filter(currentAuthor());
                updateSummary();
            }
        });
        scope.setToolTipText("Proposals in the active document, or in every file of the project");
        scope.addActionListener(e -> {
            if (!updating) {
                updateScopeControls();
                actions.scopeChanged(isProjectScope());
            }
        });
        showAll.setToolTipText("List the proposals in every file");
        showAll.addActionListener(e -> scope.setSelectedItem(ALL_FILES));
        rescan.setToolTipText("Look for proposals across the project again");
        rescan.addActionListener(e -> actions.rescan());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        top.add(scope);
        top.add(new JLabel("From:"));
        top.add(author);
        top.add(rescan);
        top.add(others);
        top.add(showAll);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        accept.addActionListener(e -> withSelected(s -> actions.accept(s.file(), s.proposal())));
        reject.addActionListener(e -> withSelected(s -> actions.reject(s.file(), s.proposal())));
        resolve.addActionListener(e -> withSelected(s -> actions.resolve(s.file(), s.proposal())));
        acceptAll.addActionListener(e -> actions.acceptAll(decidedFile(), currentAuthor()));
        rejectAll.addActionListener(e -> actions.rejectAll(decidedFile(), currentAuthor()));
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
        setOtherFiles(0);
        updateScopeControls();
    }

    /** Replace the rows with the active document's proposals. */
    public void show(List<Proposal> proposals) {
        update(() -> model.set(proposals));
    }

    /** Replace the rows with every file's proposals. */
    public void showProject(List<FileProposals> files, Path root) {
        update(() -> model.setProject(files, root));
    }

    /**
     * Swap the rows, keeping the author filter when that author is still
     * present and the selected proposal when it is still listed.
     */
    private void update(Runnable set) {
        updating = true;
        try {
            String keep = currentAuthor();
            Entry kept = selected();
            boolean wasProject = model.isProject();
            set.run();
            if (wasProject != model.isProject()) {
                sizeColumns();
            }
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
            int row = kept == null ? -1 : model.rowOf(kept);
            if (row >= 0) {
                int viewRow = table.convertRowIndexToView(row);
                table.setRowSelectionInterval(viewRow, viewRow);
            }
            updateButtons(row >= 0 ? model.entry(row) : null);
            updateSummary();
        } finally {
            updating = false;
        }
    }

    /** How many files other than the active one have proposals; shown as a hint in document scope. */
    public void setOtherFiles(int count) {
        others.setText(count == 1 ? "1 other file has proposals" : count + " other files have proposals");
        boolean visible = count > 0 && !isProjectScope();
        others.setVisible(visible);
        showAll.setVisible(visible);
    }

    public boolean isProjectScope() {
        return ALL_FILES.equals(scope.getSelectedItem());
    }

    private void updateScopeControls() {
        boolean project = isProjectScope();
        rescan.setVisible(project);
        if (project) {
            others.setVisible(false);
            showAll.setVisible(false);
        }
        String where = project ? " in the selected proposal's file" : "";
        acceptAll.setToolTipText("Accept every change" + where);
        rejectAll.setToolTipText("Reject every change" + where);
    }

    private void sizeColumns() {
        int[] widths = model.isProject() ? new int[] {160, 60, 110, 70, 260} : new int[] {60, 110, 70, 260};
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private void updateSummary() {
        int changes = model.changeCount();
        String text = changes == 0 ? "No proposals" : changes + " change(s)";
        int files = model.fileCount();
        if (model.isProject() && files > 0) {
            text += " in " + files + (files == 1 ? " file" : " files");
        }
        summary.setText("  " + text);
        updateButtons(selected());
    }

    private void updateButtons(Entry e) {
        Proposal p = e == null ? null : e.proposal();
        accept.setEnabled(p != null && p.isChange());
        reject.setEnabled(p != null && p.isChange());
        resolve.setEnabled(p != null && !p.isChange());
        // Across files, "all" means the selected proposal's file; with none selected there is no file to act on.
        boolean all = model.getRowCount() > 0 && (!model.isProject() || e != null);
        acceptAll.setEnabled(all);
        rejectAll.setEnabled(all);
    }

    private String currentAuthor() {
        Object a = author.getSelectedItem();
        return a == null || EVERYONE.equals(a) ? null : a.toString();
    }

    /** The file "all" acts on: the selected row's in project scope, else the active document. */
    private Path decidedFile() {
        Entry e = selected();
        return e == null ? null : e.file();
    }

    private Entry selected() {
        int row = table.getSelectedRow();
        return row < 0 || row >= table.getRowCount() ? null : model.entry(table.convertRowIndexToModel(row));
    }

    private void withSelected(java.util.function.Consumer<Entry> action) {
        Entry e = selected();
        if (e != null) {
            action.accept(e);
        }
    }

    /**
     * Continue the review at the proposal nearest {@code offset}: select its
     * row, which also jumps the editor there.
     */
    public void continueAt(int offset) {
        continueAt(null, offset);
    }

    /**
     * Continue the review after a decision in {@code file}: at the next
     * proposal in that file, else in the next file, which opens it.
     */
    public void continueAt(Path file, int offset) {
        int row = model.isProject() ? model.rowAtOrAfter(file, offset) : model.rowAtOrAfter(offset);
        if (row < 0) {
            table.clearSelection();
            return;
        }
        int viewRow = table.convertRowIndexToView(row);
        table.clearSelection();   // the kept selection may already be this row; reselect so the editor jumps
        table.setRowSelectionInterval(viewRow, viewRow);
        table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
    }

    ProposalListModel model() {
        return model;
    }
}
