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
package com.dogsbay.xml.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class ProposalOpsTest {

    static final String DOC = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE task PUBLIC "-//OASIS//DTD DITA Task//EN" "task.dtd">
        <task id="t">
          <title>Record a track</title>
          <shortdesc>Plain <ph status="new" rev="ai:claude-acp review-mark">inserted</ph> text <ph status="deleted" rev="ai:claude-acp review-mark" otherprops="review-deleted">gone</ph> end.<draft-comment author="ai:codex-acp" time="2026-09-04T10:00:00Z" disposition="open">Say why.</draft-comment></shortdesc>
          <taskbody>
            <steps>
              <step><cmd>Press record.</cmd></step>
              <step status="new" rev="ai:codex-acp"><cmd>Wait.</cmd></step>
              <step status="deleted" rev="ai:claude-acp" otherprops="review-deleted"><cmd>Old step.</cmd></step>
              <step status="changed" rev="ai:claude-acp" importance="high"><cmd>Stop.</cmd></step>
            </steps>
          </taskbody>
        </task>
        """;

    @Test
    void indexFindsEveryProposalInOrderWithAuthorsAndExcerpts() {
        List<Proposal> all = ProposalIndex.scan(DOC);
        assertThat(all).extracting(Proposal::kind).containsExactly(Proposal.Kind.INSERT, Proposal.Kind.DELETE,
                Proposal.Kind.COMMENT, Proposal.Kind.INSERT, Proposal.Kind.DELETE, Proposal.Kind.CHANGED);
        assertThat(all.get(0).text()).isEqualTo("inserted");
        assertThat(all.get(0).synthetic()).isTrue();
        assertThat(all.get(2).author()).isEqualTo("ai:codex-acp");
        assertThat(all.get(2).text()).isEqualTo("Say why.");
        assertThat(all.get(3).element()).isEqualTo("step");
        assertThat(all.get(3).synthetic()).isFalse();
        assertThat(ProposalIndex.scan(DOC, "ai:codex-acp")).hasSize(2);
        assertThat(all.get(0).id()).isEqualTo("insert@" + all.get(0).start());
    }

    @Test
    void acceptingEachKindLeavesOrdinaryContent() {
        List<Proposal> all = ProposalIndex.scan(DOC);
        assertThat(ProposalOps.accept(DOC, all.get(0))).contains("Plain inserted text ");
        assertThat(ProposalOps.accept(DOC, all.get(1))).contains("text  end.");
        assertThat(ProposalOps.accept(DOC, all.get(2))).doesNotContain("draft-comment");
        assertThat(ProposalOps.accept(DOC, all.get(3))).contains("<step><cmd>Wait.</cmd></step>");
        assertThat(ProposalOps.accept(DOC, all.get(4))).doesNotContain("Old step");
        assertThat(ProposalOps.accept(DOC, all.get(5))).contains("<step importance=\"high\"><cmd>Stop.</cmd></step>");
    }

    @Test
    void rejectingEachKindRestoresTheBaseline() {
        List<Proposal> all = ProposalIndex.scan(DOC);
        assertThat(ProposalOps.reject(DOC, all.get(0))).contains("Plain  text ");
        assertThat(ProposalOps.reject(DOC, all.get(1))).contains("text gone end.");
        assertThat(ProposalOps.reject(DOC, all.get(3))).doesNotContain("Wait.");
        assertThat(ProposalOps.reject(DOC, all.get(4))).contains("<step><cmd>Old step.</cmd></step>");
        assertThatThrownBy(() -> ProposalOps.reject(DOC, all.get(5)))
                .as("a bare changed mark has nothing to restore and says so")
                .hasMessageContaining("kept no previous version");
    }

    @Test
    void acceptAllByAuthorLeavesTheOthersAndComments() {
        String claude = ProposalOps.acceptAll(DOC, "ai:claude-acp");
        assertThat(ProposalIndex.scan(claude)).extracting(Proposal::author)
                .containsExactly("ai:codex-acp", "ai:codex-acp");
        assertThat(claude).contains("Plain inserted text  end.").doesNotContain("Old step").contains("draft-comment");

        String everything = ProposalOps.acceptedView(DOC);
        assertThat(ProposalIndex.scan(everything)).isEmpty();
        assertThat(everything).doesNotContain("status=").doesNotContain("rev=").doesNotContain("draft-comment");
        assertThatThrownBy(() -> ProposalOps.rejectAll(DOC, null))
                .as("reject-all stops at the bare changed mark rather than skipping it silently")
                .hasMessageContaining("kept no previous version");
        // Codex's one change (the new step) goes; its comment and Claude's four proposals stay.
        assertThat(ProposalIndex.scan(ProposalOps.rejectAll(DOC, "ai:codex-acp"))).extracting(Proposal::author)
                .containsExactly("ai:claude-acp", "ai:claude-acp", "ai:codex-acp", "ai:claude-acp", "ai:claude-acp");
    }

    @Test
    void commentsCanBeAddedAndResolved() {
        int at = DOC.indexOf("Press record.");
        String withComment = ProposalOps.addComment(DOC, at, "ai:gemini", "2026-09-04T11:00:00Z", "Be <specific>.");
        List<Proposal> comments = ProposalIndex.scan(withComment).stream()
                .filter(p -> p.kind() == Proposal.Kind.COMMENT).toList();
        assertThat(comments).hasSize(2);
        Proposal added = comments.get(1);
        assertThat(added.author()).isEqualTo("ai:gemini");
        assertThat(added.text()).isEqualTo("Be <specific>.");
        String resolved = ProposalOps.resolveComment(withComment, added, "accepted");
        assertThat(resolved).contains("disposition=\"accepted\"").contains("disposition=\"open\"");
    }

    @Test
    void aStaleProposalIsRefusedNotMisapplied() {
        Proposal p = ProposalIndex.scan(DOC).get(0);
        String moved = DOC.replace("Plain", "Plainly");
        assertThatThrownBy(() -> ProposalOps.accept(moved, p)).hasMessageContaining("the document changed");
    }

    @Test
    void humanRevisionMetadataIsNotAProposalAndSurvivesAccept() {
        String xml = "<topic id=\"t\"><title>T</title><body><p status=\"new\" rev=\"2.1\">release note</p></body></topic>";
        assertThat(ProposalIndex.scan(xml)).isEmpty();
        assertThat(ProposalOps.acceptedView(xml)).isEqualTo(xml);
    }

    @Test
    void aRealPhMarkedAsAWholeElementKeepsItsTagsOnAccept() {
        String xml = "<p>Press <ph id=\"n\" status=\"new\" rev=\"ai:x\">the</ph> button.</p>";
        String accepted = ProposalOps.accept(xml, ProposalIndex.scan(xml).get(0));
        assertThat(accepted).isEqualTo("<p>Press <ph id=\"n\">the</ph> button.</p>");
    }

    @Test
    void anOuterRemovalWinsOverProposalsNestedInsideIt() {
        String xml = "<body><p status=\"deleted\" rev=\"ai:x\" otherprops=\"review-deleted\">gone "
                + "<draft-comment author=\"ai:y\">why?</draft-comment></p><p>kept</p></body>";
        assertThat(ProposalOps.acceptedView(xml)).isEqualTo("<body><p>kept</p></body>");
    }

    @Test
    void tagsWithAwkwardAttributeValuesAreRebuiltNotRegexed() {
        String xml = "<p status=\"new\" rev=\"ai:bob\" title=\"the rev='2' build\">x</p>";
        assertThat(ProposalOps.accept(xml, ProposalIndex.scan(xml).get(0)))
                .isEqualTo("<p title=\"the rev='2' build\">x</p>");
        String single = "<p status='deleted' rev='ai:u' otherprops='review-deleted'>x</p>";
        assertThat(ProposalOps.reject(single, ProposalIndex.scan(single).get(0))).isEqualTo("<p>x</p>");
    }

    @Test
    void anElementsOwnRevisionAndStatusComeBackWhenTheMarkComesOff() {
        String xml = "<p status=\"new\" rev=\"2.1 ai:x review-prev-status-changed\">x</p>";
        assertThat(ProposalOps.accept(xml, ProposalIndex.scan(xml).get(0)))
                .isEqualTo("<p status=\"changed\" rev=\"2.1\">x</p>");
    }

    @Test
    void unclosedProposalsAreHandledNotThrown() {
        String xml = "<p status=\"new\" rev=\"ai:a\">text";
        List<Proposal> ps = ProposalIndex.scan(xml);
        assertThat(ps).hasSize(1);
        assertThat(ProposalOps.accept(xml, ps.get(0))).isEqualTo("<p>text");
    }
}
