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

import com.dogsbay.dogsbayaieditor.commands.results.OpenProposals;
import com.dogsbay.xml.review.ChangeMarkup;
import com.dogsbay.xml.review.ProposalOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HealthOpenProposalsTest {

    private static final String BASE = "<topic id=\"t\"><title>T</title><body><p>green button</p></body></topic>";
    private static final String NEW = "<topic id=\"t\"><title>T</title><body><p>red button</p></body></topic>";

    @Test
    void listsOnlyFilesWithProposalsAndCountsAuthors(@TempDir Path dir) throws Exception {
        String marked = new ChangeMarkup("ai:claude-acp", "2026-09-05T10:00:00Z").mark(BASE, NEW);
        marked = ProposalOps.addComment(marked, marked.indexOf("</p>"), "user:ann", "2026-09-05T10:01:00Z", "Which red?");
        Path a = Files.writeString(dir.resolve("a.dita"), marked);
        Path b = Files.writeString(dir.resolve("b.dita"), BASE);
        Path c = Files.writeString(dir.resolve("notes.txt"), marked);

        List<OpenProposals> open = HeadlessExecutor.openProposals(List.of(a, b, c));

        assertThat(open).hasSize(1);
        OpenProposals op = open.get(0);
        assertThat(op.file()).isEqualTo(a.toString());
        assertThat(op.changes()).isGreaterThan(0);
        assertThat(op.comments()).isEqualTo(1);
        assertThat(op.authors()).containsExactly("ai:claude-acp", "user:ann");
    }

    @Test
    void handWrittenAndResolvedCommentsAreNotOpenProposals(@TempDir Path dir) throws Exception {
        String marked = ProposalOps.addComment(BASE, BASE.indexOf("</p>"), "user:ann", "t", "Which red?");
        var comment = com.dogsbay.xml.review.ProposalIndex.scan(marked).get(0);
        String resolved = ProposalOps.resolveComment(marked, comment, "accepted");
        Path a = Files.writeString(dir.resolve("a.dita"), resolved);
        Path b = Files.writeString(dir.resolve("b.xml"),
                "<topic id=\"t\"><title>T</title><body><p>x<draft-comment>TODO check</draft-comment></p></body></topic>");
        Path c = Files.writeString(dir.resolve("c.xml"), marked);

        var open = HeadlessExecutor.openProposals(List.of(a, b, c));

        assertThat(open).extracting(OpenProposals::file).containsExactly(c.toString());
    }

    @Test
    void anArgsFilterParamJoinsTheListInsteadOfReplacingIt() {
        String joined = com.dogsbay.xml.dita.DitaOtBuilder.joinFilters(
                List.of(new java.io.File("/w/review-open.ditaval")), "/p/prod.ditaval");
        assertThat(joined).isEqualTo("/w/review-open.ditaval" + java.io.File.pathSeparator + "/p/prod.ditaval");
        assertThat(com.dogsbay.xml.dita.DitaOtBuilder.joinFilters(List.of(), null)).isNull();
    }

    @Test
    void theReviewFilterComesFirstSoAProjectRuleCannotOverrideIt(@TempDir Path dir) throws Exception {
        Path own = Files.writeString(dir.resolve("own.ditaval"), "<val/>");
        var list = HeadlessExecutor.withReviewFilter(List.of(own), dir.resolve("work"));
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getName()).isEqualTo(com.dogsbay.xml.review.ReviewDitaval.FILE_NAME);
        assertThat(list.get(0)).exists();
        assertThat(list.get(1)).isEqualTo(own.toFile());
    }
}
