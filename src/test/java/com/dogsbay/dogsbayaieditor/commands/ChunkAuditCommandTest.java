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

import com.dogsbay.dogsbayaieditor.commands.results.ChunkAuditResult;

/** chunk_audit: @chunk inventory + token validation. */
class ChunkAuditCommandTest {

    @TempDir Path dir;
    private final HeadlessExecutor exec = new HeadlessExecutor();

    @Test
    void inventoryAndTokenValidation() throws Exception {
        Files.writeString(dir.resolve("m.ditamap"), "<map><title>M</title>"
                + "<topicref href=\"a.dita\" chunk=\"to-content\"/>"               // ok
                + "<topicref href=\"b.dita\" chunk=\"by-topic to-navigation\"/>"   // ok (two groups)
                + "<topicref href=\"c.dita\" chunk=\"by-topic by-document\"/>"     // conflict
                + "<topicref href=\"d.dita\" chunk=\"select-bogus\"/>"             // unknown token
                + "</map>");

        ChunkAuditResult r = exec.execute(new ChunkAuditCommand(dir.toString(), "root"));

        assertThat(r.uses()).hasSize(4);
        assertThat(r.issues()).anySatisfy(i ->
                assertThat(i.problem()).contains("conflicting by-topic/by-document"));
        assertThat(r.issues()).anySatisfy(i ->
                assertThat(i.problem()).contains("unknown chunk token 'select-bogus'"));
        // the two valid ones produced no issue
        assertThat(r.issues()).hasSize(2);
    }

    @Test
    void cleanMapHasNoIssues() throws Exception {
        Files.writeString(dir.resolve("m.ditamap"), "<map><title>M</title>"
                + "<topicref href=\"a.dita\" chunk=\"select-topic to-content\"/></map>");
        ChunkAuditResult r = exec.execute(new ChunkAuditCommand(dir.toString(), "root"));
        assertThat(r.uses()).hasSize(1);
        assertThat(r.issues()).isEmpty();
    }
}
