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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.AgentSessionRegistry;
import com.dogsbay.agent.session.SessionContext;
import com.dogsbay.agent.session.SessionKind;
import com.dogsbay.dogsbayaieditor.commands.results.ProposalInfo;
import com.dogsbay.dogsbayaieditor.commands.results.ReviewResult;
import com.dogsbay.xml.review.ReviewAudit;

class ReviewCommandsTest {

    @TempDir
    Path root;

    private static final String DOC = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE topic PUBLIC "-//OASIS//DTD DITA Topic//EN" "topic.dtd">
        <topic id="t">
          <title>T</title>
          <body>
            <p id="p1">Press <ph status="new" rev="ai:claude-acp review-mark">the red </ph>button.</p>
            <p status="deleted" rev="ai:codex-acp" otherprops="review-deleted">Old paragraph.</p>
          </body>
        </topic>
        """;

    private final HeadlessExecutor executor = new HeadlessExecutor();
    private final AgentSessionRegistry registry = new AgentSessionRegistry();
    private Path file;

    private final AgentSession user = registry.user();

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(root.resolve(".dogsbay"));
        file = Files.writeString(root.resolve("a.dita"), DOC);
        SessionContext.run(user, () -> { });   // decisions below run as the real user via execute(cmd, user)
    }

    @Test
    void listReportsProposalsWithLinesAndFiltersByAuthor() throws Exception {
        List<ProposalInfo> all = executor.execute(new ReviewListCommand(file.toString(), null));
        assertThat(all).hasSize(2);
        assertThat(all.get(0).kind()).isEqualTo("insert");
        assertThat(all.get(0).author()).isEqualTo("ai:claude-acp");
        assertThat(all.get(0).line()).isEqualTo(6);
        assertThat(all.get(0).synthetic()).isTrue();
        assertThat(all.get(1).element()).isEqualTo("p");
        assertThat(executor.execute(new ReviewListCommand(file.toString(), "ai:codex-acp"))).hasSize(1);
    }

    @Test
    void theUserAcceptsByIdAndRejectsAllAndBothAreAudited() throws Exception {
        List<ProposalInfo> all = executor.execute(new ReviewListCommand(file.toString(), null));
        ReviewResult r = executor.execute(new ReviewAcceptCommand(file.toString(), all.get(0).id(), null, false), user);
        assertThat(r.decided()).isEqualTo(1);
        assertThat(r.remaining()).isEqualTo(1);
        assertThat(Files.readString(file)).contains("Press the red button.");

        ReviewResult r2 = executor.execute(new ReviewRejectCommand(file.toString(), null, null, true), user);
        assertThat(r2.decided()).isEqualTo(1);
        assertThat(Files.readString(file)).contains("<p>Old paragraph.</p>").doesNotContain("status=");

        List<ReviewAudit.Entry> log = ReviewAudit.read(root);
        assertThat(log).extracting(ReviewAudit.Entry::decision).containsExactly("accept", "reject");
        assertThat(log.get(0).decidedBy()).isEqualTo("user:" + System.getProperty("user.name"));
    }

    @Test
    void agentsMayNotDecideButMayComment() throws Exception {
        AgentSession agent = registry.open(SessionKind.ACP_HOSTED, "Claude", "ai:claude-acp", null);
        assertThatThrownBy(() -> executor.execute(new ReviewAcceptCommand(file.toString(), null, null, true), agent))
                .isInstanceOfSatisfying(CommandException.class,
                        e -> assertThat(e.getCode()).isEqualTo(CommandException.ErrorCode.PERMISSION_DENIED));
        assertThat(Files.readString(file)).isEqualTo(DOC);

        executor.execute(new ReviewCommentCommand(file.toString(), "button.", null, "Say which button."), agent);
        String after = Files.readString(file);
        assertThat(after).contains("button.<draft-comment author=\"ai:claude-acp\"");
        assertThat(after).contains("disposition=\"open\">Say which button.</draft-comment>");
        List<ProposalInfo> all = executor.execute(new ReviewListCommand(file.toString(), null));
        assertThat(all).extracting(ProposalInfo::kind).containsExactly("insert", "comment", "delete");

        executor.execute(new ReviewCommentCommand(file.toString(), null, "p1", "Whole paragraph: check."), agent);
        assertThat(Files.readString(file)).as("an element anchor puts the comment at the end of the element")
                .contains("Whole paragraph: check.</draft-comment></p>");
    }

    @Test
    void anAgentMayCommentOnAFileItNeverReadThroughTheGate() throws Exception {
        // the real path: session executor with the write gate in front of the headless executor
        WriteGate gate = new WriteGate(new com.dogsbay.agent.session.WriteLease(), () -> root, new WriteGate.Documents() {
            @Override public Path activeDocument() { return null; }
            @Override public String content(Path f) { return null; }
        });
        SessionExecutor gated = new SessionExecutor(executor, registry, gate, null);
        AgentSession agent = registry.open(SessionKind.ACP_HOSTED, "Claude", "ai:claude-acp", null);
        gated.execute(new ReviewCommentCommand(file.toString(), "button.", null, "Which button?"), agent);
        assertThat(Files.readString(file)).contains("Which button?");
        assertThatThrownBy(() -> gated.execute(new ReviewAcceptCommand(file.toString(), null, null, true), agent))
                .isInstanceOfSatisfying(CommandException.class,
                        e -> assertThat(e.getCode()).as("refused before the gate, as a permission matter")
                                .isEqualTo(CommandException.ErrorCode.PERMISSION_DENIED));
    }

    @Test
    void commentsMatchUnescapedTextAcrossInlineTagsAndSkipDeletedSpansAndComments() throws Exception {
        String doc = DOC.replace("<title>T</title>", "<title>A &amp; B</title>");
        Files.writeString(file, doc);
        executor.execute(new ReviewCommentCommand(file.toString(), "A & B", null, "entity"));
        assertThat(Files.readString(file)).contains("A &amp; B<draft-comment");
        // across the review mark: "Press the red button" spans plain text, a <ph>, and plain text
        executor.execute(new ReviewCommentCommand(file.toString(), "Press the red button", null, "spanning"));
        assertThat(Files.readString(file)).contains("button<draft-comment author=\"user:");
        // text that exists only inside a deleted span or an existing comment is not an anchor
        assertThatThrownBy(() -> executor.execute(new ReviewCommentCommand(file.toString(), "Old paragraph", null, "x")))
                .hasMessageContaining("does not occur");
        assertThatThrownBy(() -> executor.execute(new ReviewCommentCommand(file.toString(), "spanning", null, "x")))
                .hasMessageContaining("does not occur");
    }

    @Test
    void aCommentTheGrammarForbidsIsRefusedWithAdvice() throws Exception {
        // directly under <topic> a draft-comment is invalid at either end; the validator says so
        assertThatThrownBy(() -> executor.execute(new ReviewCommentCommand(file.toString(), null, "t", "x")))
                .hasMessageContaining("not allowed there");
        assertThat(Files.readString(file)).isEqualTo(DOC);
        // inside a paragraph it is fine, and the DTD agrees
        executor.execute(new ReviewCommentCommand(file.toString(), null, "p1", "fine"));
        assertThat(com.dogsbay.dogsbayaieditor.validate.DocumentValidator.validate(file, null, List.of()).errors()).isEmpty();
    }

    @Test
    void contradictoryDecisionFlagsAreRefused() throws Exception {
        List<ProposalInfo> all = executor.execute(new ReviewListCommand(file.toString(), null));
        assertThatThrownBy(() -> executor.execute(new ReviewAcceptCommand(file.toString(), all.get(0).id(), null, true)))
                .hasMessageContaining("not both");
        assertThatThrownBy(() -> executor.execute(new ReviewRejectCommand(file.toString(), all.get(0).id(), "ai:codex-acp", false)))
                .hasMessageContaining("is by ai:claude-acp, not ai:codex-acp");
        assertThat(Files.readString(file)).isEqualTo(DOC);
    }

    @Test
    void withoutAProjectNothingIsAuditedAndNoFolderAppears() throws Exception {
        Path loose = Files.createTempDirectory("dogsbay-loose");
        try {
            Path f = Files.writeString(loose.resolve("a.dita"), DOC);
            List<ProposalInfo> all = executor.execute(new ReviewListCommand(f.toString(), null));
            executor.execute(new ReviewAcceptCommand(f.toString(), all.get(0).id(), null, false));
            try (var files = Files.list(loose)) {
                assertThat(files.map(p -> p.getFileName().toString())).containsExactly("a.dita");
            }
        } finally {
            try (var files = Files.walk(loose)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    void badAnchorsAndMissingIdsAreArgumentErrors() {
        assertThatThrownBy(() -> executor.execute(new ReviewCommentCommand(file.toString(), "nowhere", null, "x")))
                .hasMessageContaining("does not occur");
        assertThatThrownBy(() -> executor.execute(new ReviewCommentCommand(file.toString(), null, "nope", "x")))
                .hasMessageContaining("No element with id");
        assertThatThrownBy(() -> executor.execute(new ReviewCommentCommand(file.toString(), null, null, "x")))
                .hasMessageContaining("afterText or elementId");
        assertThatThrownBy(() -> executor.execute(new ReviewAcceptCommand(file.toString(), "insert@1", null, false)))
                .hasMessageContaining("No proposal insert@1");
        assertThatThrownBy(() -> executor.execute(new ReviewAcceptCommand(file.toString(), null, null, false)))
                .hasMessageContaining("--all");
        assertThatThrownBy(() -> executor.execute(new ReviewListCommand(root.resolve("missing.dita").toString(), null)))
                .hasMessageContaining("No such file");
    }

    @Test
    void sessionsAndAgentsNeedTheEditor() {
        assertThatThrownBy(() -> executor.execute(new ListSessionsCommand())).hasMessageContaining("running editor");
        assertThatThrownBy(() -> executor.execute(new ListAgentsCommand())).hasMessageContaining("running editor");
        assertThat(SessionContext.isBound()).isFalse();
    }
}
