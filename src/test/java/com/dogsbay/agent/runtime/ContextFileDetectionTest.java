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

package com.dogsbay.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The "No AGENTS.md — run /init" hint is driven by
 * {@link AgentChatController#hasContextFile(Path)} against the working directory.
 * It reads the directory live, so detection itself is correct — the startup bug
 * was that the status wasn't re-evaluated once the project folder was opened
 * (now fixed by re-syncing when the panel is shown).
 */
class ContextFileDetectionTest {

    @Test
    void detectsAgentsMd(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("AGENTS.md"), "# context");
        assertThat(AgentChatController.hasContextFile(dir)).isTrue();
    }

    @Test
    void detectsLegacyAndClaudeContextFiles(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(".xagent.md"), "# legacy");
        assertThat(AgentChatController.hasContextFile(dir)).isTrue();
    }

    @Test
    void falseWhenNoContextFile(@TempDir Path dir) {
        assertThat(AgentChatController.hasContextFile(dir)).isFalse();
    }

    @Test
    void falseForNullDirectory() {
        assertThat(AgentChatController.hasContextFile(null)).isFalse();
    }
}
