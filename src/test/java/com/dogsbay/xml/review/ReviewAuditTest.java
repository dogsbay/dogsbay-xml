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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewAuditTest {

    @TempDir
    Path root;

    @Test
    void recordsDecisionsAsSelfIgnoringJsonLinesAndReadsThemBack() throws Exception {
        ReviewAudit audit = new ReviewAudit(() -> root);
        Proposal p = new Proposal(Proposal.Kind.INSERT, "insert@10", "ai:claude-acp", null,
                "He said \"hi\"\nand left", 10, 40, "ph", true, false);
        audit.record("user:alice", "accept", p, root.resolve("topics/a.dita"));
        audit.record("user:alice", "reject", new Proposal(Proposal.Kind.COMMENT, "comment@5", "ai:codex-acp",
                "t", "x".repeat(300), 5, 9, "draft-comment", false, false), null);

        Path dir = root.resolve(ReviewAudit.DIR);
        assertThat(dir.resolve(".gitignore")).hasContent("*");
        assertThat(Files.readAllLines(dir.resolve(ReviewAudit.FILE))).hasSize(2);

        var read = ReviewAudit.read(root);
        assertThat(read).hasSize(2);
        assertThat(read.get(0).decidedBy()).isEqualTo("user:alice");
        assertThat(read.get(0).proposalAuthor()).isEqualTo("ai:claude-acp");
        assertThat(read.get(0).excerpt()).isEqualTo("He said \"hi\" and left");
        assertThat(read.get(0).file()).endsWith("a.dita");
        assertThat(read.get(1).file()).isNull();
        assertThat(read.get(1).excerpt()).hasSize(161).endsWith("…");
    }

    @Test
    void noProjectMeansNothingWritten() {
        new ReviewAudit(() -> null).record("u", "accept",
                new Proposal(Proposal.Kind.INSERT, "i", "a", null, "t", 0, 1, "ph", true, false), null);
        assertThat(root.resolve(ReviewAudit.DIR)).doesNotExist();
        assertThat(ReviewAudit.read(root)).isEmpty();
    }
}
