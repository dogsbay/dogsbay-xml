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
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.agent.session.AuditLog;
import com.dogsbay.agent.session.SessionKind;
import com.dogsbay.dogsbayaieditor.commands.results.AuditEntryInfo;

class AuditLogCommandTest {

    private static AuditLog.Entry entry(String at, String identity, String command, String outcome) {
        return new AuditLog.Entry(Instant.parse(at), "s1", SessionKind.ACP_HOSTED, identity, "Claude", command,
                List.of("/p/a.dita"), false, outcome, null);
    }

    @Test
    void newestFirstFilteredAndCapped(@TempDir Path root) throws Exception {
        AuditLog log = new AuditLog(() -> root);
        log.append(entry("2026-09-06T10:00:00Z", "ai:claude-acp", "set-content", "ok"));
        log.append(entry("2026-09-06T10:01:00Z", "ai:codex-acp", "rename-key", "ok"));
        log.append(entry("2026-09-06T10:02:00Z", "ai:claude-acp", "set-content", "error:CONFLICT"));
        HeadlessExecutor executor = new HeadlessExecutor();

        List<AuditEntryInfo> all = executor.execute(new AuditLogCommand(root.toString(), null, null, 0));
        assertThat(all).extracting(AuditEntryInfo::at)
                .containsExactly("2026-09-06T10:02:00Z", "2026-09-06T10:01:00Z", "2026-09-06T10:00:00Z");

        List<AuditEntryInfo> claude = executor.execute(new AuditLogCommand(root.toString(), "ai:claude-acp", null, 0));
        assertThat(claude).hasSize(2).allSatisfy(e -> assertThat(e.identity()).isEqualTo("ai:claude-acp"));

        List<AuditEntryInfo> one = executor.execute(new AuditLogCommand(root.toString(), null, "set-content", 1));
        assertThat(one).hasSize(1);
        assertThat(one.get(0).outcome()).isEqualTo("error:CONFLICT");
        assertThat(one.get(0).files()).containsExactly("/p/a.dita");
    }

    @Test
    void aProjectWithoutALogIsEmptyNotAnError(@TempDir Path root) throws Exception {
        assertThat(new HeadlessExecutor().execute(new AuditLogCommand(root.toString(), null, null, 50))).isEmpty();
        assertThat(Files.exists(root.resolve(AuditLog.DIR))).isFalse();
    }
}
