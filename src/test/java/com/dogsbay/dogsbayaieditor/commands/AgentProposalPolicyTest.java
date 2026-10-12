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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import javax.swing.text.PlainDocument;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.AgentSessionRegistry;
import com.dogsbay.agent.session.SessionKind;
import com.dogsbay.xml.review.ProposalIndex;
import com.dogsbay.xml.review.ProposalOps;

class AgentProposalPolicyTest {

    @TempDir
    Path dir;

    private static final String BASE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE topic PUBLIC "-//OASIS//DTD DITA Topic//EN" "topic.dtd">
        <topic id="t"><title>T</title><body><p>Press the button.</p></body></topic>
        """;
    private final AgentSessionRegistry registry = new AgentSessionRegistry(
            Clock.fixed(Instant.parse("2026-09-04T10:00:00Z"), ZoneOffset.UTC), "alice");
    private final AgentProposalPolicy policy = new AgentProposalPolicy(
            Clock.fixed(Instant.parse("2026-09-04T10:00:00Z"), ZoneOffset.UTC));

    /** A draft comment somebody typed by hand: no author, no rev, no disposition. */
    private static final String WITH_HAND_COMMENT = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE topic PUBLIC "-//OASIS//DTD DITA Topic//EN" "topic.dtd">
        <topic id="t"><title>T</title><body><draft-comment>TODO: confirm minimum OS versions        </draft-comment><p>Press the button.</p></body></topic>
        """;

    @Test
    void anEditNearAnUnattributedDraftCommentIsNotAnError() throws Exception {
        Path file = dir.resolve("a.dita");
        Files.writeString(file, WITH_HAND_COMMENT);
        AgentSession agent = registry.open(SessionKind.ACP_HOSTED, "Claude", "ai:claude-acp", null);
        String modified = WITH_HAND_COMMENT.replace("Press the button.", "Press the red button.");

        // A hand-written draft-comment has no author, and a full scan reports it.
        // Comparing on that author threw instead of marking the edit.
        AgentProposalPolicy.Outcome out = policy.decide(agent, file, WITH_HAND_COMMENT, modified, true,
                AgentProposalPolicy.validatorFor(file));

        assertThat(out.asProposals()).isTrue();
        assertThat(out.text()).contains("TODO: confirm minimum OS versions");
    }

    @Test
    void anUnattributedDraftCommentStillCannotBeRemoved() {
        var before = com.dogsbay.xml.review.ProposalIndex.scan(WITH_HAND_COMMENT);
        String without = WITH_HAND_COMMENT.replaceAll("<draft-comment>.*?</draft-comment>", "");

        String message = AgentProposalPolicy.missingProposals(before, without);

        assertThat(message).isNotNull();
        // Not "by null": nobody signed it.
        assertThat(message).doesNotContain("null").contains("another author");
    }

    @Test
    void keepingTheCommentReportsNothingMissing() {
        var before = com.dogsbay.xml.review.ProposalIndex.scan(WITH_HAND_COMMENT);

        assertThat(AgentProposalPolicy.missingProposals(before, WITH_HAND_COMMENT)).isNull();
    }

    @Test
    void anAgentsEditToADitaFileLandsAsProposalsTheWriterCanAcceptBack() throws Exception {
        Path file = dir.resolve("a.dita");
        Files.writeString(file, BASE);
        AgentSession agent = registry.open(SessionKind.ACP_HOSTED, "Claude", "ai:claude-acp", null);
        String modified = BASE.replace("Press the button.", "Press the red button.");

        AgentProposalPolicy.Outcome out = policy.decide(agent, file, BASE, modified, true,
                AgentProposalPolicy.validatorFor(file));

        assertThat(out.asProposals()).isTrue();
        assertThat(out.proposals()).isEqualTo(1);
        assertThat(out.text()).contains("<ph status=\"new\" rev=\"ai:claude-acp review-mark\">red </ph>");
        assertThat(out.note()).contains("Do not edit the marks");
        assertThat(ProposalOps.acceptedView(out.text())).isEqualTo(modified);
        assertThat(ProposalIndex.scan(out.text()).get(0).author()).isEqualTo("ai:claude-acp");
    }

    @Test
    void theUserNonDitaFilesUntitledAndDisabledAllLandPlain() {
        AgentSession agent = registry.open(SessionKind.BUILTIN, "A", "ai:xagent/m", null);
        String modified = BASE.replace("button", "switch");
        assertThat(policy.decide(registry.user(), dir.resolve("a.dita"), BASE, modified, true, c -> true).asProposals()).isFalse();
        assertThat(policy.decide(agent, dir.resolve("a.xml"), BASE, modified, true, c -> true).asProposals()).isFalse();
        assertThat(policy.decide(agent, null, BASE, modified, true, c -> true).asProposals()).isFalse();
        assertThat(policy.decide(agent, dir.resolve("a.dita"), BASE, modified, false, c -> true).asProposals()).isFalse();
        assertThat(policy.decide(agent, dir.resolve("a.dita"), BASE, BASE, true, c -> true).asProposals()).isFalse();
    }

    @Test
    void anUnreviewableWriteFallsBackToPlainTextWithAReason() {
        AgentSession agent = registry.open(SessionKind.BUILTIN, "A", "ai:xagent/m", null);
        AgentProposalPolicy.Outcome out = policy.decide(agent, dir.resolve("a.dita"), BASE,
                "<topic id=\"t\"><title>T</title><body><p>cut off", true, c -> true);
        assertThat(out.asProposals()).isFalse();
        assertThat(out.text()).startsWith("<topic id=\"t\">");
        assertThat(out.note()).contains("not well-formed");
    }

    @Test
    void aWriteThatWouldEraseAnotherAuthorsProposalIsRefused() throws Exception {
        Path file = dir.resolve("a.dita");
        AgentSession codex = registry.open(SessionKind.ACP_HOSTED, "Codex", "ai:codex-acp", null);
        String withClaude = BASE.replace("<p>Press the button.</p>",
                "<p>Press the <ph status=\"new\" rev=\"ai:claude-acp review-mark\">red </ph>button.</p>");
        Files.writeString(file, withClaude);
        // Codex "cleans up" Claude's mark: refused, with the reason
        AgentProposalPolicy.Outcome out = policy.decide(codex, file, withClaude,
                BASE.replace("<p>Press the button.</p>", "<p>Press the red button now.</p>"), true, c -> true);
        assertThat(out.refused()).isTrue();
        assertThat(out.note()).contains("ai:claude-acp").contains("only the writer may accept or reject");
        // Codex edits elsewhere and keeps the mark: fine, and only its own new marks are counted
        AgentProposalPolicy.Outcome ok = policy.decide(codex, file, withClaude,
                withClaude.replace("<title>T</title>", "<title>Title</title>"), true, c -> true);
        assertThat(ok.refused()).isFalse();
        assertThat(ok.asProposals()).isTrue();
        assertThat(ProposalIndex.scan(ok.text())).extracting(p -> p.author()).contains("ai:claude-acp", "ai:codex-acp");
    }

    @Test
    void theCountReportsOnlyThisWritesProposals() throws Exception {
        Path file = dir.resolve("a.dita");
        AgentSession agent = registry.open(SessionKind.BUILTIN, "A", "ai:xagent/m", null);
        String first = policy.decide(agent, file, BASE, BASE.replace("Press the button.", "Press the red button."),
                true, c -> true).text();
        AgentProposalPolicy.Outcome second = policy.decide(agent, file, first,
                first.replace("<title>T</title>", "<title>Recording</title>"), true, c -> true);
        assertThat(second.proposals()).as("the earlier mark is not counted again").isEqualTo(2);
        assertThat(ProposalIndex.scan(second.text(), "ai:xagent/m")).hasSize(3);
    }

    @Test
    void anUnreviewableWriteOverOthersProposalsIsRefusedTooNotAppliedPlain() throws Exception {
        AgentSession agent = registry.open(SessionKind.BUILTIN, "A", "ai:xagent/m", null);
        String withClaude = BASE.replace("<p>Press the button.</p>",
                "<p><ph status=\"new\" rev=\"ai:claude-acp review-mark\">Press</ph> the button.</p>");
        AgentProposalPolicy.Outcome out = policy.decide(agent, dir.resolve("a.dita"), withClaude,
                "<topic id=\"t\"><title>T</title><body><p>cut off", true, c -> true);
        assertThat(out.refused()).isTrue();
        assertThat(out.note()).contains("proposals by other authors");
    }

    @Test
    void theValidatorUsesTheFilesExtensionSoDitaGetsTheBundledDtds() throws Exception {
        Path file = dir.resolve("a.dita");
        Files.writeString(file, BASE);
        var validator = AgentProposalPolicy.validatorFor(file);
        assertThat(validator.test(BASE)).isTrue();
        assertThat(validator.test(BASE.replace("<p>Press the button.</p>", "<p><p>nested</p></p>"))).isFalse();
        try (var files = Files.list(dir)) {
            assertThat(files.filter(p -> p.getFileName().toString().startsWith("dogsbay-review-")))
                    .as("candidates are never written into the project").isEmpty();
        }
    }

    @Test
    void minimalReplaceTouchesOnlyTheDifferingSpan() throws Exception {
        PlainDocument doc = new PlainDocument();
        doc.insertString(0, "hello brave world", null);
        AgentProposalPolicy.replaceMinimal(doc, "hello new world");
        assertThat(doc.getText(0, doc.getLength())).isEqualTo("hello new world");
        AgentProposalPolicy.replaceMinimal(doc, "hello new world");
        assertThat(doc.getText(0, doc.getLength())).isEqualTo("hello new world");
        AgentProposalPolicy.replaceMinimal(doc, "");
        assertThat(doc.getLength()).isZero();
        AgentProposalPolicy.replaceMinimal(doc, "abc");
        assertThat(doc.getText(0, 3)).isEqualTo("abc");
    }
}
