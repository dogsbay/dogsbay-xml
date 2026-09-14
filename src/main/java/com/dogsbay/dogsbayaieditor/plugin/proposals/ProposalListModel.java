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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import javax.swing.table.AbstractTableModel;

import com.dogsbay.dogsbayaieditor.plugin.proposals.ProjectProposals.FileProposals;
import com.dogsbay.xml.review.Proposal;

/**
 * The Proposals panel's rows, optionally one author's: the active document's
 * proposals, or in project scope every file's, grouped by file.
 */
public final class ProposalListModel extends AbstractTableModel {

    /** A row: a proposal and the file it is in (null for the active document). */
    public record Entry(Path file, Proposal proposal) { }

    private static final String[] COLUMNS = {"Kind", "Author", "Element", "Text"};
    private static final String[] PROJECT_COLUMNS = {"File", "Kind", "Author", "Element", "Text"};
    private List<Entry> all = List.of();
    private List<Entry> rows = List.of();
    private String author;   // null = everyone
    private boolean project;
    private Path root;

    /** The active document's proposals. */
    public void set(List<Proposal> proposals) {
        List<Entry> entries = proposals.stream().map(p -> new Entry(null, p)).toList();
        boolean changed = project;
        project = false;
        root = null;
        replace(entries, changed);
    }

    /** Every file's proposals, in the order given; {@code root} shortens the file names shown. */
    public void setProject(List<FileProposals> files, Path root) {
        List<Entry> entries = new ArrayList<>();
        for (FileProposals fp : files) {
            fp.proposals().forEach(p -> entries.add(new Entry(fp.file(), p)));
        }
        boolean changed = !project;
        project = true;
        this.root = root == null ? null : ProjectProposals.key(root);
        replace(entries, changed);
    }

    private void replace(List<Entry> entries, boolean structure) {
        this.all = List.copyOf(entries);
        rows = filtered();
        if (structure) {
            fireTableStructureChanged();
        } else {
            fireTableDataChanged();
        }
    }

    public boolean isProject() {
        return project;
    }

    public void filter(String author) {
        this.author = author;
        rows = filtered();
        fireTableDataChanged();
    }

    public Proposal at(int row) {
        return rows.get(row).proposal();
    }

    public Entry entry(int row) {
        return rows.get(row);
    }

    public List<Proposal> rows() {
        return rows.stream().map(Entry::proposal).toList();
    }

    /** Distinct authors, for the filter. */
    public List<String> authors() {
        TreeSet<String> set = new TreeSet<>();
        for (Entry e : all) {
            if (e.proposal().author() != null) {
                set.add(e.proposal().author());
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * The row of the first proposal at or after {@code offset} in document
     * order, else the last row; -1 when empty. After a decision this is
     * where the review continues.
     */
    public int rowAtOrAfter(int offset) {
        return rowAtOrAfter(null, offset);
    }

    /**
     * Where a review continues after a decision in {@code file}: the first
     * row at or after {@code offset} in that file, else the first row of a
     * later file; -1 when nothing comes after, so a finished review does not
     * jump back into an earlier file. The decided file may no longer be
     * listed, so later is by path, not by position. Outside project scope,
     * or with no file, this is {@link #rowAtOrAfter(int)}.
     */
    public int rowAtOrAfter(Path file, int offset) {
        if (!project || file == null) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).proposal().start() >= offset) {
                    return i;
                }
            }
            return rows.isEmpty() ? -1 : rows.size() - 1;
        }
        Path k = ProjectProposals.key(file);
        for (int i = 0; i < rows.size(); i++) {
            Entry e = rows.get(i);
            int cmp = e.file().compareTo(k);
            if (cmp > 0 || (cmp == 0 && e.proposal().start() >= offset)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The row showing the same proposal as {@code e}, nearest its old
     * position; -1 when gone. A null file (a row from document scope) matches
     * any file, so a selection survives switching scope.
     */
    public int rowOf(Entry e) {
        int best = -1;
        for (int i = 0; i < rows.size(); i++) {
            Entry r = rows.get(i);
            Proposal p = r.proposal();
            Proposal q = e.proposal();
            boolean sameFile = r.file() == null || e.file() == null || r.file().equals(e.file());
            if (sameFile && p.kind() == q.kind() && Objects.equals(p.author(), q.author())
                    && Objects.equals(p.element(), q.element()) && Objects.equals(p.text(), q.text())
                    && (best < 0 || Math.abs(p.start() - q.start())
                            < Math.abs(rows.get(best).proposal().start() - q.start()))) {
                best = i;
            }
        }
        return best;
    }

    public int changeCount() {
        return (int) all.stream().filter(e -> e.proposal().isChange()).count();
    }

    /** Files with at least one proposal; 0 outside project scope. */
    public int fileCount() {
        return project ? (int) all.stream().map(Entry::file).distinct().count() : 0;
    }

    /** The shown name of a file: relative to the root when under it. */
    public String name(Path file) {
        if (file == null) {
            return "";
        }
        Path shown = root != null && file.startsWith(root) ? root.relativize(file) : file;
        return shown.toString().replace('\\', '/');
    }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return columns().length; }
    @Override public String getColumnName(int c) { return columns()[c]; }

    private String[] columns() {
        return project ? PROJECT_COLUMNS : COLUMNS;
    }

    @Override
    public Object getValueAt(int r, int c) {
        Entry e = rows.get(r);
        if (project) {
            if (c == 0) {
                return groupLabel(r);
            }
            c--;
        }
        Proposal p = e.proposal();
        return switch (c) {
            case 0 -> switch (p.kind()) {
                case INSERT -> p.wrapper() ? "wrap" : "insert";
                case DELETE -> p.wrapper() ? "unwrap" : "delete";
                case CHANGED -> "changed";
                case COMMENT -> "comment";
            };
            case 1 -> p.author();
            case 2 -> p.element();
            default -> p.text();
        };
    }

    /** The file and its shown count on a group's first row; blank on the rest. */
    private String groupLabel(int r) {
        Path file = rows.get(r).file();
        if (r > 0 && Objects.equals(rows.get(r - 1).file(), file)) {
            return "";
        }
        if (counts == null) {
            counts = new HashMap<>();
            rows.forEach(e -> counts.merge(e.file(), 1, Integer::sum));
        }
        return name(file) + " (" + counts.getOrDefault(file, 0) + ")";
    }

    /** Rows per file, computed on first use after the rows change. */
    private Map<Path, Integer> counts;

    private List<Entry> filtered() {
        counts = null;
        return author == null ? all : all.stream().filter(e -> author.equals(e.proposal().author())).toList();
    }
}
