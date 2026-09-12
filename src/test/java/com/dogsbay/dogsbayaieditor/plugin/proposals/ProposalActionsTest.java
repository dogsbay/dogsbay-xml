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

import javax.swing.text.PlainDocument;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.xml.review.Proposal;
import com.dogsbay.xml.review.ReviewAudit;

class ProposalActionsTest {

    @TempDir
    Path root;

    private static final String DOC = "<topic id=\"t\"><title>T</title><body><p>Press "
            + "<ph status=\"new\" rev=\"ai:claude-acp review-mark\">the red </ph>button"
            + "<ph status=\"deleted\" rev=\"ai:codex-acp review-mark\" otherprops=\"review-deleted\"> now</ph>."
            + "<draft-comment author=\"ai:codex-acp\" time=\"t\" disposition=\"open\">Sure?</draft-comment></p></body></topic>";

    private PlainDocument doc() throws Exception {
        PlainDocument d = new PlainDocument();
        d.insertString(0, DOC, null);
        return d;
    }

    @Test
    void acceptRejectAndResolveEditTheBufferAndRecordWhoDecided() throws Exception {
        PlainDocument d = doc();
        ProposalActions actions = new ProposalActions(() -> root, () -> "user:alice");
        List<Proposal> ps = actions.list(d);
        assertThat(ps).hasSize(3);

        assertThat(actions.accept(d, ps.get(0), root.resolve("a.dita"))).isNull();
        assertThat(ProposalActions.text(d)).contains("Press the red button");
        ps = actions.list(d);
        assertThat(actions.reject(d, ps.get(0), root.resolve("a.dita"))).isNull();
        assertThat(ProposalActions.text(d)).contains("button now.");
        ps = actions.list(d);
        assertThat(actions.resolve(d, ps.get(0), "accepted", root.resolve("a.dita"))).isNull();
        assertThat(ProposalActions.text(d)).contains("disposition=\"accepted\"");

        List<ReviewAudit.Entry> log = ReviewAudit.read(root);
        assertThat(log).extracting(ReviewAudit.Entry::decision).containsExactly("accept", "reject", "resolve:accepted");
        assertThat(log).extracting(ReviewAudit.Entry::decidedBy).containsOnly("user:alice");
        assertThat(log.get(0).proposalAuthor()).isEqualTo("ai:claude-acp");
        assertThat(log.get(0).file()).endsWith("a.dita");
    }

    @Test
    void acceptAllByAuthorLeavesTheOthers() throws Exception {
        PlainDocument d = doc();
        ProposalActions actions = new ProposalActions(() -> root, () -> "user:alice");
        assertThat(actions.acceptAll(d, "ai:codex-acp", null)).isNull();
        List<Proposal> left = actions.list(d);
        assertThat(left).extracting(Proposal::author).containsExactly("ai:claude-acp", "ai:codex-acp");
        assertThat(left.get(1).kind()).isEqualTo(Proposal.Kind.COMMENT);
        assertThat(ReviewAudit.read(root)).hasSize(1);
    }

    @Test
    void aRefusedDecisionComesBackAsAMessageNotAnException() throws Exception {
        PlainDocument d = new PlainDocument();
        d.insertString(0, "<p status=\"changed\" rev=\"ai:x\">k</p>", null);
        ProposalActions actions = new ProposalActions(() -> root, () -> "user:alice");
        String problem = actions.reject(d, actions.list(d).get(0), null);
        assertThat(problem).contains("kept no previous version");
        assertThat(ProposalActions.text(d)).as("nothing changed").contains("status=\"changed\"");
        assertThat(ReviewAudit.read(root)).isEmpty();
    }

    @Test
    void aStaleRowIsRelocatedByWhatItIsNotWhereItWas() throws Exception {
        PlainDocument d = doc();
        ProposalActions actions = new ProposalActions(() -> root, () -> "user:alice");
        List<Proposal> before = actions.list(d);
        Proposal claude = before.get(0);
        // the user types earlier in the document after the list was made
        d.insertString(DOC.indexOf("<body>") + 6, "<p>typed <b>x</b></p>", null);
        assertThat(actions.accept(d, claude, null)).isNull();
        assertThat(ProposalActions.text(d)).contains("Press the red button").contains("<b>x</b>");
        // a proposal that no longer exists at all is reported, not misapplied
        assertThat(actions.accept(d, claude, null)).isEqualTo(ProposalActions.STALE);
    }

    @Test
    void decisionsAreBracketedAsOneCompoundEdit() throws Exception {
        PlainDocument d = doc();
        List<String> calls = new java.util.ArrayList<>();
        ProposalActions actions = new ProposalActions(() -> root, () -> "u", new ProposalActions.Compound() {
            @Override public void begin() { calls.add("begin"); }
            @Override public void end() { calls.add("end"); }
        });
        actions.accept(d, actions.list(d).get(0), null);
        assertThat(calls).containsExactly("begin", "end");
    }

    @Test
    void atFindsTheInnermostProposalUnderTheCaret() throws Exception {
        PlainDocument d = doc();
        List<Proposal> ps = new ProposalActions(() -> root, () -> "u").list(d);
        int inNew = DOC.indexOf("the red");
        assertThat(ProposalActions.at(ps, inNew).author()).isEqualTo("ai:claude-acp");
        assertThat(ProposalActions.at(ps, 0)).isNull();
    }
}
