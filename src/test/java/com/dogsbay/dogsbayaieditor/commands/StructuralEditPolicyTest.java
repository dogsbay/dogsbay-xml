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

package com.dogsbay.dogsbayaieditor.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.AgentSessionRegistry;
import com.dogsbay.agent.session.SessionKind;
import com.dogsbay.xml.edit.StructuralEdit.Step;
import com.dogsbay.xml.review.ErrorReporting;

/** When edit_structure marks, falls back, refuses, or writes plainly. */
class StructuralEditPolicyTest {

    private final AgentSessionRegistry registry = new AgentSessionRegistry();
    private final AgentSession agent = registry.open(SessionKind.ACP_HOSTED, "Claude", "ai:claude-acp", null);
    private final Path dita = Path.of("/p/topic.dita");

    private static final String DOC = "<topic id=\"t\"><title>T</title><body><section><p>keep</p>"
            + "<p status=\"new\" rev=\"ai:other\">theirs</p></section></body></topic>";

    private static final List<Step> DELETE_SECTION = List.of(new Step("delete", "//section", null, null, null, null, null, null));

    /** A validator that reports an error only for this batch's marks: the fallback's trigger. */
    private static final ErrorReporting MARKS_ARE_ERRORS = xml -> xml.contains("rev=\"ai:claude-acp\"")
            ? List.of("marks break the grammar here") : List.of();

    @Test
    void theFallbackRefusesRatherThanEraseAnotherAuthorsProposal() {
        // Unmarked, the deleted section takes ai:other's proposal with it (review, 2026-09-18)
        assertThatThrownBy(() -> StructuralEditPolicy.decide(agent, dita, DOC, DELETE_SECTION, true, MARKS_ARE_ERRORS))
                .isInstanceOf(CommandException.class)
                .satisfies(e -> assertThat(((CommandException) e).getCode()).isEqualTo(CommandException.ErrorCode.CONFLICT));
    }

    @Test
    void marksThatWouldAddAnErrorFallBackToAPlainEditWithANote() throws Exception {
        String doc = "<topic id=\"t\"><title>T</title><body><section><p>keep</p></section></body></topic>";
        StructuralEditPolicy.Outcome o = StructuralEditPolicy.decide(agent, dita, doc, DELETE_SECTION, true, MARKS_ARE_ERRORS);
        assertThat(o.asProposals()).isFalse();
        assertThat(o.note()).contains("applied without review marks");
        assertThat(o.text()).doesNotContain("section");
    }

    @Test
    void anAgentsStepsAreMarkedWhenTheMarksAddNothing() throws Exception {
        String doc = "<topic id=\"t\"><title>T</title><body><section><p>keep</p></section></body></topic>";
        StructuralEditPolicy.Outcome o = StructuralEditPolicy.decide(agent, dita, doc, DELETE_SECTION, true, x -> true);
        assertThat(o.asProposals()).isTrue();
        assertThat(o.text()).contains("<section status=\"deleted\" rev=\"ai:claude-acp\"");
    }

    @Test
    void noMarksWhenProposalsAreOffForAPersonOrOutsideDita() throws Exception {
        String doc = "<topic id=\"t\"><title>T</title><body><section><p>keep</p></section></body></topic>";
        assertThat(StructuralEditPolicy.decide(agent, dita, doc, DELETE_SECTION, false, x -> true).asProposals()).isFalse();
        assertThat(StructuralEditPolicy.decide(registry.user(), dita, doc, DELETE_SECTION, true, x -> true).asProposals())
                .isFalse();
        assertThat(StructuralEditPolicy.decide(agent, Path.of("/p/pom.xml"), doc, DELETE_SECTION, true, x -> true)
                .asProposals()).isFalse();
    }

    @Test
    void stepsThatAddAnErrorAreRefusedWithTheError() {
        ErrorReporting noSections = xml -> xml.contains("<section>") ? List.of() : List.of("body needs a section");
        String doc = "<topic id=\"t\"><title>T</title><body><section><p>keep</p></section></body></topic>";
        assertThatThrownBy(() -> StructuralEditPolicy.decide(registry.user(), dita, doc, DELETE_SECTION, true, noSections))
                .hasMessageContaining("would add an error").hasMessageContaining("body needs a section");
    }
    @Test
    void marksThatCannotRestoreTheTextExactlyFallBackToAPlainEditWithANote() throws Exception {
        // A tag that already has otherprops is re-rendered to be marked, so rejecting cannot give back its quotes
        String doc = "<topic id=\"t\"><title>T</title><body><section otherprops='x' outputclass=\"&#169;\"><p>keep</p>"
                + "</section><p>stay</p></body></topic>";
        StructuralEditPolicy.Outcome o = StructuralEditPolicy.decide(agent, dita, doc, DELETE_SECTION, true, x -> true);
        assertThat(o.asProposals()).isFalse();
        assertThat(o.note()).contains("could not be marked so that accepting or rejecting them restores the text exactly");
        assertThat(o.text()).doesNotContain("section");
    }

    @Test
    void theSessionsEarlierProposalsDoNotCountAgainstNewMarks() throws Exception {
        String doc = "<topic id=\"t\"><title>T</title><body><section><p>keep</p></section>"
                + "<p status=\"new\" rev=\"ai:claude-acp\">mine, from before</p></body></topic>";
        StructuralEditPolicy.Outcome o = StructuralEditPolicy.decide(agent, dita, doc, DELETE_SECTION, true, x -> true);
        assertThat(o.asProposals()).isTrue();
    }
}
