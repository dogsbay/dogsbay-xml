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
package com.dogsbay.dogsbayaieditor.plugin.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HostedSessionStoreTest {

    @Test
    void remembersRecentSessionsPerAgentNewestFirstAndIgnoresItselfInGit(@TempDir Path root) throws Exception {
        assertThat(HostedSessionStore.last(root, "claude-acp")).isEmpty();
        HostedSessionStore.remember(root, "claude-acp", "sess-1", "T1_FUNCTIONAL");
        HostedSessionStore.remember(root, "codex-acp", "sess-9", "T2_READER");
        HostedSessionStore.remember(root, "claude-acp", "sess-2", "T1_FUNCTIONAL");

        assertThat(HostedSessionStore.last(root, "claude-acp").orElseThrow().sessionId()).isEqualTo("sess-2");
        assertThat(HostedSessionStore.recent(root, "claude-acp")).extracting(HostedSessionStore.Saved::sessionId)
                .containsExactly("sess-2", "sess-1");
        assertThat(HostedSessionStore.agents(root)).containsExactlyInAnyOrder("claude-acp", "codex-acp");
        assertThat(Files.readString(root.resolve(HostedSessionStore.DIR).resolve(".gitignore"))).isEqualTo("*\n");

        HostedSessionStore.forget(root, "claude-acp", "sess-2");
        assertThat(HostedSessionStore.last(root, "claude-acp").orElseThrow().sessionId()).isEqualTo("sess-1");
        HostedSessionStore.forget(root, "claude-acp", "sess-1");
        assertThat(HostedSessionStore.last(root, "claude-acp")).isEmpty();
        assertThat(HostedSessionStore.agents(root)).containsExactly("codex-acp");
    }

    @Test
    void resumingMovesASessionToTheFrontAndTheListIsCapped(@TempDir Path root) {
        for (int i = 0; i < HostedSessionStore.KEEP + 3; i++) {
            HostedSessionStore.remember(root, "a", "s" + i, "T1");
        }
        assertThat(HostedSessionStore.recent(root, "a")).hasSize(HostedSessionStore.KEEP);
        assertThat(HostedSessionStore.last(root, "a").orElseThrow().sessionId()).isEqualTo("s10");
        HostedSessionStore.remember(root, "a", "s5", "T1");   // resumed
        assertThat(HostedSessionStore.recent(root, "a")).extracting(HostedSessionStore.Saved::sessionId)
                .startsWith("s5", "s10").doesNotContain("s2").hasSize(HostedSessionStore.KEEP);
    }

    @Test
    void theFirstLayoutWithOneSessionPerAgentStillReads(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve(HostedSessionStore.DIR));
        Files.writeString(root.resolve(HostedSessionStore.DIR).resolve(HostedSessionStore.FILE),
                "{\"claude-acp\":{\"sessionId\":\"old\",\"at\":\"2026-09-06T10:00:00Z\",\"tier\":\"T1\",\"title\":\"Named\"}}");
        var saved = HostedSessionStore.last(root, "claude-acp").orElseThrow();
        assertThat(saved.sessionId()).isEqualTo("old");
        assertThat(saved.title()).isEqualTo("Named");
        HostedSessionStore.remember(root, "claude-acp", "new", "T1");
        assertThat(HostedSessionStore.recent(root, "claude-acp")).extracting(HostedSessionStore.Saved::sessionId)
                .containsExactly("new", "old");
    }

    @Test
    void concurrentRemembersBothLand(@TempDir Path root) throws Exception {
        Thread a = Thread.ofVirtual().start(() -> {
            for (int i = 0; i < 50; i++) {
                HostedSessionStore.remember(root, "claude-acp", "c" + i, "T1");
            }
        });
        Thread b = Thread.ofVirtual().start(() -> {
            for (int i = 0; i < 50; i++) {
                HostedSessionStore.remember(root, "codex-acp", "x" + i, "T1");
            }
        });
        a.join();
        b.join();
        assertThat(HostedSessionStore.last(root, "claude-acp").orElseThrow().sessionId()).isEqualTo("c49");
        assertThat(HostedSessionStore.last(root, "codex-acp").orElseThrow().sessionId()).isEqualTo("x49");
    }

    @Test
    void anEntryWithoutASessionIdIsNeverOffered(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve(HostedSessionStore.DIR));
        Files.writeString(root.resolve(HostedSessionStore.DIR).resolve(HostedSessionStore.FILE),
                "{\"a\":{\"sessions\":[{\"sessionId\":null,\"at\":\"2026-09-06T10:00:00Z\"},"
                + "{\"sessionId\":\"ok\",\"at\":\"2026-09-06T09:00:00Z\"}]}}");
        assertThat(HostedSessionStore.recent(root, "a")).extracting(HostedSessionStore.Saved::sessionId)
                .containsExactly("ok");
    }

    @Test
    void aMissingOrBrokenFileReadsAsEmpty(@TempDir Path root) throws Exception {
        assertThat(HostedSessionStore.last(null, "x")).isEmpty();
        Files.createDirectories(root.resolve(HostedSessionStore.DIR));
        Files.writeString(root.resolve(HostedSessionStore.DIR).resolve(HostedSessionStore.FILE), "{not json");
        assertThat(HostedSessionStore.last(root, "x")).isEmpty();
        assertThat(HostedSessionStore.agents(root)).isEmpty();
    }
}
