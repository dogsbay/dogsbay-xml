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

import com.dogsbay.dogsbayaieditor.commands.results.IndexAuditResult;

/** index_audit: inventory, coverage gaps, dangling see redirects. */
class IndexAuditCommandTest {

    @TempDir Path dir;
    private final HeadlessExecutor exec = new HeadlessExecutor();

    private void topic(String name, String body) throws Exception {
        Files.writeString(dir.resolve(name),
                "<concept id=\"c\"><title>T</title><conbody>" + body + "</conbody></concept>");
    }

    private static String idx(String... terms) {
        StringBuilder b = new StringBuilder("<p>");
        for (String t : terms) {
            b.append("<indexterm>").append(t).append("</indexterm>");
        }
        return b.append("</p>").toString();
    }

    @Test
    void inventoryCoverageAndDanglingSee() throws Exception {
        topic("a.dita", idx("recording", "audio"));
        topic("b.dita", idx("audio"));                 // audio used in 2 topics
        topic("c.dita", "<p>no index here</p>");       // coverage gap
        topic("d.dita", "<p><indexterm>levels<index-see>nonexistent</index-see></indexterm></p>");

        IndexAuditResult r = exec.execute(new IndexAuditCommand(dir.toString(), "root"));

        // inventory with frequency (audio in 2 topics, most-used first)
        assertThat(r.entries()).extracting(IndexAuditResult.Entry::path)
                .contains("audio", "recording");
        assertThat(r.entries().get(0).path()).isEqualTo("audio");
        assertThat(r.entries().get(0).count()).isEqualTo(2);

        // coverage gap
        assertThat(r.missing()).anyMatch(m -> m.endsWith("c.dita"));

        // the see points at "nonexistent", which is no real entry → dangling
        assertThat(r.dangling()).hasSize(1);
        assertThat(r.dangling().get(0).target()).isEqualTo("nonexistent");
        assertThat(r.dangling().get(0).from()).isEqualTo("levels");
    }

    @Test
    void seeToARealEntryIsNotDangling() throws Exception {
        topic("a.dita", idx("sound"));
        topic("b.dita", "<p><indexterm>audio<index-see>sound</index-see></indexterm></p>");

        IndexAuditResult r = exec.execute(new IndexAuditCommand(dir.toString(), "root"));
        assertThat(r.dangling()).isEmpty();           // "sound" is a real entry
    }
}
