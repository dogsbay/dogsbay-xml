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

import java.util.List;

import org.junit.jupiter.api.Test;

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
