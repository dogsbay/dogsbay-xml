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

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.AgentSessionRegistry;
import com.dogsbay.agent.session.AuditLog;
import com.dogsbay.agent.session.SessionKind;

class CommandAuditTest {

    @TempDir
    Path root;

    private final AgentSessionRegistry registry = new AgentSessionRegistry();

    @Test
    void namesAreKebabCaseWithoutTheSuffix() {
        assertThat(CommandAudit.name(new RenameKeyCommand("a", "b", "/p", false))).isEqualTo("rename-key");
        assertThat(CommandAudit.name(new SetContentCommand(null, "x"))).isEqualTo("set-content");
        assertThat(CommandAudit.name(new ValidateCommand(Path.of("a"), null, List.of()))).isEqualTo("validate");
    }

    @Test
    void filesComeFromTheCommandsTargets() {
        assertThat(CommandAudit.files(new SetContentCommand(Path.of("/p/a.dita"), "x")))
                .containsExactly(Path.of("/p/a.dita").toString());
        assertThat(CommandAudit.files(new SetContentCommand(null, "x"))).containsExactly("<active-document>");
        // A tree command's root is a target Path, so it is recorded in the platform's form.
        assertThat(CommandAudit.files(new RenameKeyCommand("old", "new", "/proj", true)))
                .containsExactly(Path.of("/proj").toString());
        assertThat(CommandAudit.files(new ExtractConrefCommand("a.dita", "id", "reuse/w.dita", true)))
                .containsExactly("a.dita", Path.of("reuse/w.dita").toString());
        // read-only commands fall back to their path-like inputs, for context
        assertThat(CommandAudit.files(new ValidateCommand(Path.of("a.dita"), Path.of("s.dtd"),
                List.of(Path.of("c.xml"))))).containsExactly("a.dita", "s.dtd", "c.xml");
    }

    @Test
    void dryRunFollowsTheApplyFlag() {
        assertThat(CommandAudit.isDryRun(new RenameKeyCommand("a", "b", "/p", false))).isTrue();
        assertThat(CommandAudit.isDryRun(new RenameKeyCommand("a", "b", "/p", true))).isFalse();
        assertThat(CommandAudit.isDryRun(new SetContentCommand(null, "x"))).isFalse();
    }

    @Test
    void readOnlyCommandsAreNotRecordedMutatingOnesAre() {
        AuditLog log = new AuditLog(() -> root);
        AgentSession agent = registry.open(SessionKind.BUILTIN, "A", "ai:test", null);

        CommandAudit.record(log, agent, new GetContentCommand(null), "ok");
        CommandAudit.record(log, agent, new ListDocumentsCommand(), "ok");
        assertThat(AuditLog.read(root)).isEmpty();

        CommandAudit.record(log, agent, new SetContentCommand(Path.of("/p/a.dita"), "x"), "ok");
        CommandAudit.record(log, agent, new RenameKeyCommand("a", "b", "/p", false), "error:INVALID_ARGUMENT");
        List<AuditLog.Entry> entries = AuditLog.read(root);
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).command()).isEqualTo("set-content");
        assertThat(entries.get(0).identity()).isEqualTo("ai:test");
        assertThat(entries.get(0).files()).containsExactly(Path.of("/p/a.dita").toString());
        assertThat(entries.get(1).dryRun()).isTrue();
        assertThat(entries.get(1).outcome()).isEqualTo("error:INVALID_ARGUMENT");
    }

    @Test
    void theUserIsNeverRecorded() {
        AuditLog log = new AuditLog(() -> root);
        CommandAudit.record(log, registry.user(), new SetContentCommand(Path.of("/p/a.dita"), "x"), "ok");
        assertThat(AuditLog.read(root)).isEmpty();
    }

    @Test
    void theReadOnlyMarkerDecidesWhatIsAudited() {
        assertThat(CommandAudit.isReadOnly(new GetContentCommand(null))).isTrue();
        assertThat(CommandAudit.isReadOnly(new ScreenshotCommand("x.png"))).isFalse();
        assertThat(CommandAudit.isReadOnly(new CloseCommand(null, true))).isFalse();
        long readOnly = java.util.Arrays.stream(Command.class.getPermittedSubclasses())
                .filter(ReadOnlyCommand.class::isAssignableFrom).count();
        assertThat(readOnly).isGreaterThan(40);
    }
}
