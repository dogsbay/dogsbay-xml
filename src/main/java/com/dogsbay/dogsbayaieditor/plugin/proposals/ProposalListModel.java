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

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import javax.swing.table.AbstractTableModel;

import com.dogsbay.xml.review.Proposal;

/** The Proposals panel's rows: the active document's proposals, optionally one author's. */
public final class ProposalListModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Kind", "Author", "Element", "Text"};
    private List<Proposal> all = List.of();
    private List<Proposal> rows = List.of();
    private String author;   // null = everyone

    public void set(List<Proposal> proposals) {
        this.all = List.copyOf(proposals);
        refilter();
    }

    public void filter(String author) {
        this.author = author;
        refilter();
    }

    private void refilter() {
        rows = author == null ? all : all.stream().filter(p -> author.equals(p.author())).toList();
        fireTableDataChanged();
    }

    public Proposal at(int row) {
        return rows.get(row);
    }

    public List<Proposal> rows() {
        return rows;
    }

    /** Distinct authors in the document, for the filter. */
    public List<String> authors() {
        TreeSet<String> set = new TreeSet<>();
        for (Proposal p : all) {
            if (p.author() != null) {
                set.add(p.author());
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
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).start() >= offset) {
                return i;
            }
        }
        return rows.isEmpty() ? -1 : rows.size() - 1;
    }

    public int changeCount() {
        return (int) all.stream().filter(Proposal::isChange).count();
    }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int c) { return COLUMNS[c]; }

    @Override
    public Object getValueAt(int r, int c) {
        Proposal p = rows.get(r);
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
}
