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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.commands.results.KeywordAuditResult;

/** keyword_audit: vocabulary, missing, near-duplicates, co-occurrence. */
class KeywordAuditCommandTest {

    @TempDir Path dir;
    private final HeadlessExecutor exec = new HeadlessExecutor();

    private void topic(String name, String... keywords) throws Exception {
        StringBuilder kw = new StringBuilder();
        for (String k : keywords) {
            kw.append("<keyword>").append(k).append("</keyword>");
        }
        String prolog = keywords.length == 0 ? ""
                : "<prolog><metadata><keywords>" + kw + "</keywords></metadata></prolog>";
        Files.writeString(dir.resolve(name),
                "<concept id=\"c\"><title>T</title>" + prolog + "<conbody/></concept>");
    }

    private KeywordAuditResult audit() throws Exception {
        return exec.execute(new KeywordAuditCommand(dir.toString(), "root"));
    }

    @Test
    void vocabularyMissingNearDupAndRelatedness() throws Exception {
        topic("a.dita", "setup", "audio");
        topic("b.dita", "audio", "export");
        topic("c.dita", "set up");          // near-duplicate of "setup"
        topic("d.dita");                    // no keywords

        KeywordAuditResult r = audit();

        // vocabulary counts (audio appears in 2 topics)
        assertThat(r.vocabulary()).anySatisfy(t ->
                assertThat(t).extracting(KeywordAuditResult.Term::keyword,
                        KeywordAuditResult.Term::count).containsExactly("audio", 2));
        // missing
        assertThat(r.missing()).anyMatch(m -> m.endsWith("d.dita"));
        // near-duplicate: "set up" ~ "setup"
        assertThat(r.nearDuplicates()).anySatisfy(v -> {
            assertThat(java.util.List.of(v.keyword(), v.canonical()))
                    .containsExactlyInAnyOrder("set up", "setup");
        });
        // relatedness: a.dita and b.dita share "audio"
        assertThat(r.relatedness()).anySatisfy(p -> {
            assertThat(p.source() + "|" + p.target()).contains("a.dita").contains("b.dita");
            assertThat(p.keywords()).contains("audio");
        });
    }

    @Test
    void cleanWhenNoKeywordsAnywhere() throws Exception {
        topic("x.dita");
        KeywordAuditResult r = audit();
        assertThat(r.vocabulary()).isEmpty();
        assertThat(r.relatedness()).isEmpty();
        assertThat(r.missing()).hasSize(1);
    }
}
