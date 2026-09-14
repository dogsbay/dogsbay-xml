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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.plugin.proposals.ProjectProposals.FileProposals;
import com.dogsbay.xml.review.Proposal;

class ProposalListModelTest {

    private static Proposal p(Proposal.Kind k, String author, boolean wrapper) {
        return new Proposal(k, k + "@1", author, null, "t", 1, 2, "p", false, wrapper);
    }

    @Test
    void continuesAtTheNextProposalInDocumentOrder() {
        ProposalListModel m = new ProposalListModel();
        m.set(List.of(new Proposal(Proposal.Kind.INSERT, "a", "ai:a", null, "t", 10, 20, "p", false, false),
                new Proposal(Proposal.Kind.DELETE, "b", "ai:a", null, "t", 40, 50, "p", false, false),
                new Proposal(Proposal.Kind.INSERT, "c", "ai:b", null, "t", 70, 80, "p", false, false)));
        assertThat(m.rowAtOrAfter(10)).as("the decided one is gone; the next starts here or later").isEqualTo(0);
        assertThat(m.rowAtOrAfter(41)).isEqualTo(2);
        assertThat(m.rowAtOrAfter(999)).as("past the end: stay on the last").isEqualTo(2);
        m.filter("ai:b");
        assertThat(m.rowAtOrAfter(0)).isEqualTo(0);
        m.set(List.of());
        assertThat(m.rowAtOrAfter(0)).isEqualTo(-1);
    }

    private static Proposal at(int start, String author) {
        return new Proposal(Proposal.Kind.INSERT, "i" + start, author, null, "t" + start, start, start + 5, "p", false,
                false);
    }

    @Test
    void groupsProjectRowsByFileAndContinuesIntoTheNextFile() {
        Path root = Path.of("proj").toAbsolutePath();
        Path a = root.resolve("topics/a.dita");
        Path b = root.resolve("topics/b.dita");
        Path c = root.resolve("topics/c.dita");
        ProposalListModel m = new ProposalListModel();
        m.setProject(List.of(new FileProposals(a, List.of(at(10, "ai:a"), at(40, "ai:b"))),
                new FileProposals(c, List.of(at(5, "ai:a")))), root);

        assertThat(m.isProject()).isTrue();
        assertThat(m.getColumnCount()).isEqualTo(5);
        assertThat(m.getValueAt(0, 0)).isEqualTo("topics/a.dita (2)");
        assertThat(m.getValueAt(1, 0)).as("only the group's first row names the file").isEqualTo("");
        assertThat(m.getValueAt(2, 0)).isEqualTo("topics/c.dita (1)");
        assertThat(m.getValueAt(0, 1)).isEqualTo("insert");
        assertThat(m.fileCount()).isEqualTo(2);

        assertThat(m.rowAtOrAfter(a, 11)).as("same file, later").isEqualTo(1);
        assertThat(m.rowAtOrAfter(a, 41)).as("file done: first of the next").isEqualTo(2);
        assertThat(m.rowAtOrAfter(b, 0)).as("the decided file is no longer listed").isEqualTo(2);
        assertThat(m.rowAtOrAfter(c, 99)).as("review finished: do not jump back to an earlier file").isEqualTo(-1);

        m.filter("ai:a");
        assertThat(m.getValueAt(0, 0)).as("counts follow the filter").isEqualTo("topics/a.dita (1)");
        Proposal moved = new Proposal(Proposal.Kind.INSERT, "i5", "ai:a", null, "t5", 7, 12, "p", false, false);
        assertThat(m.rowOf(new ProposalListModel.Entry(c, moved))).as("same proposal, moved").isEqualTo(1);
        assertThat(m.rowOf(new ProposalListModel.Entry(a, at(40, "ai:b")))).isEqualTo(-1);

        m.set(List.of(at(1, "ai:a")));
        assertThat(m.isProject()).isFalse();
        assertThat(m.getColumnCount()).isEqualTo(4);
        assertThat(m.fileCount()).isZero();
    }

    @Test
    void filtersByAuthorAndLabelsKinds() {
        ProposalListModel m = new ProposalListModel();
        m.set(List.of(p(Proposal.Kind.INSERT, "ai:a", false), p(Proposal.Kind.DELETE, "ai:b", true),
                p(Proposal.Kind.COMMENT, "ai:a", false), p(Proposal.Kind.CHANGED, "ai:b", false)));
        assertThat(m.authors()).containsExactly("ai:a", "ai:b");
        assertThat(m.changeCount()).isEqualTo(3);
        assertThat(m.getRowCount()).isEqualTo(4);
        assertThat(m.getValueAt(1, 0)).isEqualTo("unwrap");
        assertThat(m.getValueAt(3, 0)).isEqualTo("changed");
        m.filter("ai:a");
        assertThat(m.getRowCount()).isEqualTo(2);
        assertThat(m.at(1).kind()).isEqualTo(Proposal.Kind.COMMENT);
        m.filter(null);
        assertThat(m.getRowCount()).isEqualTo(4);
    }
}
