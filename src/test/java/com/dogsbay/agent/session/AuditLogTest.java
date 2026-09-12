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
package com.dogsbay.agent.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuditLogTest {

    @TempDir
    Path root;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-03T10:00:00Z"), ZoneOffset.UTC);
    private final AgentSessionRegistry registry = new AgentSessionRegistry(clock, "alice");

    @Test
    void recordsAgentCommandsAsOneJsonLineEach() throws Exception {
        AuditLog log = new AuditLog(() -> root, clock, e -> { throw new AssertionError(e); });
        AgentSession s = registry.open(SessionKind.EXTERNAL_MCP, "Claude Code", "mcp:claude-code", null);

        log.record(s, "rename-key", List.of("a.ditamap", "b.dita"), true, "ok", null);
        log.record(s, "rename-key", List.of("a.ditamap", "b.dita"), false, "ok", "2 files changed");

        Path file = root.resolve(AuditLog.DIR).resolve(AuditLog.FILE);
        assertThat(file.resolveSibling(".gitignore")).as("log excludes itself from git").hasContent("*");
        List<String> lines = Files.readAllLines(file);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).contains("\"command\":\"rename-key\"").contains("\"dryRun\":true")
                .doesNotContain("dbs_");
        assertThat(lines.get(1)).contains("\"detail\":\"2 files changed\"");

        List<AuditLog.Entry> read = AuditLog.read(root);
        assertThat(read).hasSize(2);
        assertThat(read.get(0).at()).isEqualTo(clock.instant());
        assertThat(read.get(0).identity()).isEqualTo("mcp:claude-code");
        assertThat(read.get(0).kind()).isEqualTo(SessionKind.EXTERNAL_MCP);
        assertThat(read.get(0).files()).containsExactly("a.ditamap", "b.dita");
        assertThat(read.get(1).dryRun()).isFalse();
        assertThat(read.get(1).detail()).isEqualTo("2 files changed");
    }

    @Test
    void theUserIsNotRecorded() {
        AuditLog log = new AuditLog(() -> root, clock, e -> { throw new AssertionError(e); });
        log.record(registry.user(), "save", List.of("a.dita"), false, "ok", null);
        assertThat(root.resolve(AuditLog.DIR)).doesNotExist();
        assertThat(AuditLog.read(root)).isEmpty();
    }

    @Test
    void noProjectRootMeansNoEntryAndNoError() {
        AuditLog log = new AuditLog(() -> null, clock, e -> { throw new AssertionError(e); });
        AgentSession s = registry.open(SessionKind.BUILTIN, "A", null, null);
        log.record(s, "validate", List.of(), false, "ok", null);
        assertThat(root.resolve(AuditLog.DIR)).doesNotExist();
    }

    @Test
    void writeFailuresGoToTheHookNotTheCaller() throws Exception {
        Path notADir = root.resolve("file");
        Files.writeString(notADir, "x");
        AtomicReference<Exception> failure = new AtomicReference<>();
        AuditLog log = new AuditLog(() -> notADir, clock, failure::set);
        AgentSession s = registry.open(SessionKind.BUILTIN, "A", null, null);
        log.record(s, "validate", List.of(), false, "ok", null);
        assertThat(failure.get()).isNotNull();
    }

    @Test
    void aTornOrForeignLineIsSkippedNotFatal() throws Exception {
        AuditLog log = new AuditLog(() -> root, clock, e -> { throw new AssertionError(e); });
        AgentSession s = registry.open(SessionKind.BUILTIN, "A", null, null);
        log.record(s, "set-content", List.of("a.dita"), false, "ok", null);
        Path file = root.resolve(AuditLog.DIR).resolve(AuditLog.FILE);
        Files.writeString(file, Files.readString(file)
                + "{\"at\":\"2026-09-04T10:00:00Z\",\"session\":\"x\",\"kind\":\"FROM_THE_FUTURE\",\"command\":\"c\",\"outcome\":\"ok\"}\n"
                + "{\"at\":\"2026-09-04T1");   // torn last line
        List<AuditLog.Entry> read = AuditLog.read(root);
        assertThat(read).hasSize(2);
        assertThat(read.get(1).kind()).isEqualTo(SessionKind.EXTERNAL_MCP);
    }

    @Test
    void appendsFromManyThreadsStayLineIntact() throws Exception {
        AuditLog log = new AuditLog(() -> root, clock, e -> { throw new AssertionError(e); });
        AgentSession s = registry.open(SessionKind.BUILTIN, "A", null, null);
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            final int n = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 50; j++) {
                    log.record(s, "cmd-" + n, List.of("f" + j), false, "ok", "x".repeat(200));
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        assertThat(AuditLog.read(root)).hasSize(400);
    }
}
