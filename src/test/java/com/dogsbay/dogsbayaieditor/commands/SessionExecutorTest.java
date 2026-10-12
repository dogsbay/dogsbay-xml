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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.AgentSessionRegistry;
import com.dogsbay.agent.session.AuditLog;
import com.dogsbay.agent.session.SessionContext;
import com.dogsbay.agent.session.SessionKind;
import com.dogsbay.agent.session.WriteLease;

class SessionExecutorTest {

    @TempDir
    Path root;

    /** A delegate that serves one open document and records what ran. */
    final class FakeEditor implements CommandExecutor, WriteGate.Documents {
        final RecordingExecutor recorder = new RecordingExecutor();
        String text = "<topic/>";
        Path file;
        boolean failNext;

        @Override public Path activeDocument() { return file; }
        @Override public String content(Path f) { return text; }

        @Override
        @SuppressWarnings("unchecked")
        public <R> R execute(Command<R> command) throws CommandException {
            recorder.execute(command);
            if (failNext) {
                failNext = false;
                throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR, "boom");
            }
            if (command instanceof GetContentCommand) {
                return (R) text;
            }
            if (command instanceof SetContentCommand c) {
                text = c.content();
            }
            return null;
        }
    }

    private final AgentSessionRegistry registry = new AgentSessionRegistry();
    private final FakeEditor editor = new FakeEditor();

    private SessionExecutor executor() throws Exception {
        editor.file = Files.writeString(root.resolve("a.dita"), editor.text).toAbsolutePath();
        WriteGate gate = new WriteGate(new WriteLease(), () -> root, editor);
        return new SessionExecutor(editor, registry, gate, new AuditLog(() -> root));
    }

    @Test
    void anUnboundCallIsTheUserAndIsNeitherGatedNorAudited() throws Exception {
        SessionExecutor ex = executor();
        ex.execute(new SetContentCommand(editor.file, "x"));
        assertThat(editor.recorder.calls.get(0).session()).isEqualTo(registry.user());
        assertThat(AuditLog.read(root)).isEmpty();
    }

    @Test
    void anAgentEditIsGatedThenAuditedAndTheSessionIsBoundForTheDelegate() throws Exception {
        SessionExecutor ex = executor();
        AgentSession a = registry.open(SessionKind.BUILTIN, "A", "ai:a", null);

        assertThatThrownBy(() -> ex.execute(new SetContentCommand(editor.file, "x"), a))
                .isInstanceOf(CommandException.class)
                .extracting("code").isEqualTo(CommandException.ErrorCode.CONFLICT);
        assertThat(editor.recorder.calls).as("refused before the delegate ran").isEmpty();

        String read = ex.execute(new GetContentCommand(editor.file), a);
        assertThat(read).isEqualTo("<topic/>");
        ex.execute(new SetContentCommand(editor.file, "v2"), a);
        ex.execute(new SetContentCommand(editor.file, "v3"), a);

        assertThat(editor.recorder.calls).extracting(c -> c.session().identity())
                .containsExactly("ai:a", "ai:a", "ai:a");
        List<AuditLog.Entry> log = AuditLog.read(root);
        assertThat(log).extracting(AuditLog.Entry::command)
                .containsExactly("set-content", "set-content", "set-content");
        assertThat(log.get(0).outcome()).isEqualTo("error:CONFLICT");
        assertThat(log.get(1).outcome()).isEqualTo("ok");
        assertThat(log.get(1).files()).containsExactly(editor.file.toString());
        assertThat(SessionContext.isBound()).isFalse();
    }

    @Test
    void readingTheSelectionCountsAsReadingTheActiveDocument() throws Exception {
        SessionExecutor ex = executor();
        AgentSession a = registry.open(SessionKind.BUILTIN, "A", "ai:a", null);
        ex.execute(new GetSelectionCommand(), a);
        ex.execute(new ReplaceSelectionCommand("x"), a);
        assertThat(AuditLog.read(root)).extracting(AuditLog.Entry::outcome).containsExactly("ok");
    }

    @Test
    void anOutlineCountsAsAReadForBlockEdits() throws Exception {
        SessionExecutor ex = executor();
        AgentSession a = registry.open(SessionKind.BUILTIN, "A", "ai:a", null);
        ex.execute(new AuthorOutlineCommand(editor.file), a);
        ex.execute(new AuthorSetTextCommand(editor.file, "b1", "x"), a);
        assertThat(AuditLog.read(root)).extracting(AuditLog.Entry::outcome).containsExactly("ok");
    }

    @Test
    void aDelegateFailureReleasesTheLease() throws Exception {
        SessionExecutor ex = executor();
        AgentSession a = registry.open(SessionKind.BUILTIN, "A", "ai:a", null);
        AgentSession b = registry.open(SessionKind.BUILTIN, "B", "ai:b", null);
        ex.execute(new GetContentCommand(editor.file), a);
        ex.execute(new GetContentCommand(editor.file), b);
        editor.failNext = true;
        assertThatThrownBy(() -> ex.execute(new SetContentCommand(editor.file, "x"), a))
                .isInstanceOf(CommandException.class);
        ex.execute(new SetContentCommand(editor.file, "y"), b);   // not LOCKED
    }

    @Test
    void readsAreNotAuditedAndRefusedWritesAre() throws Exception {
        SessionExecutor ex = executor();
        AgentSession mcp = registry.open(SessionKind.EXTERNAL_MCP, "C", "mcp:c", null);
        ex.execute(new GetContentCommand(editor.file), mcp);
        assertThatThrownBy(() -> ex.execute(new NewDocumentCommand(Path.of("/etc/x.dita")), mcp))
                .extracting("code").isEqualTo(CommandException.ErrorCode.PERMISSION_DENIED);
        List<AuditLog.Entry> log = AuditLog.read(root);
        assertThat(log).hasSize(1);
        assertThat(log.get(0).command()).isEqualTo("new-document");
        assertThat(log.get(0).outcome()).isEqualTo("error:PERMISSION_DENIED");
    }

    @Test
    void userOnlyCommandsAreRefusedToAgentsBeforeTheGateRuns() throws Exception {
        SessionExecutor ex = executor();
        AgentSession a = registry.open(SessionKind.BUILTIN, "A", "ai:a", null);
        assertThatThrownBy(() -> ex.execute(new ReviewAcceptCommand("/proj/a.dita", null, null, true), a))
                .isInstanceOfSatisfying(CommandException.class,
                        e -> assertThat(e.getCode()).isEqualTo(CommandException.ErrorCode.PERMISSION_DENIED));
        assertThat(editor.recorder.calls).isEmpty();
        assertThat(AuditLog.read(root)).extracting(AuditLog.Entry::outcome).containsExactly("error:PERMISSION_DENIED");
    }

    @Test
    void worksWithoutAGateOrLog() throws Exception {
        SessionExecutor ex = new SessionExecutor(editor, registry, null, null);
        AgentSession a = registry.open(SessionKind.BUILTIN, "A", "ai:a", null);
        ex.execute(new SetContentCommand(null, "x"), a);
        assertThat(editor.recorder.calls.get(0).session()).isEqualTo(a);
    }
}
