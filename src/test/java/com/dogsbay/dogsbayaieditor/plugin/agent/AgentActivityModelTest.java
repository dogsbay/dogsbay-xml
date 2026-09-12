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

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.AgentSessionRegistry;
import com.dogsbay.agent.session.AuditLog;
import com.dogsbay.agent.session.SessionKind;

class AgentActivityModelTest {

    @TempDir
    Path root;

    @Test
    void showsLogEntriesNewestFirstWithReadableColumns() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-04T10:00:00Z"), ZoneOffset.UTC);
        AgentSessionRegistry registry = new AgentSessionRegistry(clock, "u");
        AgentSession claude = registry.open(SessionKind.ACP_HOSTED, "Claude Agent", "ai:claude-acp", null);
        AuditLog log = new AuditLog(() -> root, clock, e -> { throw new AssertionError(e); });
        log.record(claude, "rename-key", List.of("/p/root.ditamap"), true, "ok", null);
        log.record(claude, "set-content", List.of("/p/a.dita"), false, "error:CONFLICT", null);

        AgentActivityDialog.Model model = new AgentActivityDialog.Model();
        model.load(root);

        assertThat(model.getRowCount()).isEqualTo(2);
        assertThat(model.getValueAt(0, 2)).as("newest first").isEqualTo("set-content");
        assertThat(model.getValueAt(0, 1)).isEqualTo("Claude Agent (ai:claude-acp)");
        assertThat(model.getValueAt(0, 4)).isEqualTo("applied");
        assertThat(model.getValueAt(0, 5)).isEqualTo("error:CONFLICT");
        assertThat(model.getValueAt(1, 3)).isEqualTo("/p/root.ditamap");
        assertThat(model.getValueAt(1, 4)).isEqualTo("dry run");

        model.load(null);
        assertThat(model.getRowCount()).isZero();
    }
}
