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
import com.dogsbay.agent.session.SessionKind;
import com.dogsbay.agent.session.WriteLease;
import com.dogsbay.dogsbayaieditor.ipc.JsonRpcHandler;
import com.dogsbay.xml.edit.StructuralEdit;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Agents read and edit documents that are not open (2026-09-18): get_document_content failed on a closed
 * file, and edits needed the document open. Now a closed document is read and edited as its file, checked
 * against what the agent read as an open one is against its buffer; the writer can turn that off, and then
 * edits need the document open.
 */
class GetContentCommandTest {

    private static final String TOPIC = "<topic id=\"a\"><title>A</title><body><p>Old words.</p></body></topic>";

    @TempDir Path root;

    private final AgentSessionRegistry registry = new AgentSessionRegistry();
    private final AgentSession agent = registry.open(SessionKind.ACP_HOSTED, "Claude", "ai:claude-acp", null);

    /** The editor's view of documents: none open. */
    private static final WriteGate.Documents NONE_OPEN = new WriteGate.Documents() {
        @Override public Path activeDocument() { return null; }
        @Override public String content(Path f) { return null; }
    };

    private SessionExecutor gated(boolean closedFiles) throws Exception {
        root = root.toRealPath();
        return new SessionExecutor(new HeadlessExecutor(), registry,
                new WriteGate(new WriteLease(), () -> root, NONE_OPEN, () -> closedFiles), null);
    }

    @Test
    void theToolReadsClosedDocumentsButOtherReadsDoNot() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        Path file = root.resolve("a.dita");
        new JsonRpcHandler(recorder).dispatch("getContent",
                new ObjectMapper().createObjectNode().put("file", file.toString()));
        assertThat(recorder.calls).extracting(c -> (Object) c.command()).containsExactly(new GetContentCommand(file, true));
        assertThat(new GetContentCommand(file).readClosed())
                .as("the write gate and the ACP file system learn from a refusal that a file is not open").isFalse();
    }

    @Test
    void anAgentReadsAndEditsAClosedDocumentAsItsFile() throws Exception {
        SessionExecutor executor = gated(true);
        Path file = Files.writeString(root.resolve("a.dita"), TOPIC);

        assertThat(executor.execute(new GetContentCommand(file, true), agent)).isEqualTo(TOPIC);
        executor.execute(new EditDocumentCommand(file, "Old words.", "New words.", false), agent);

        String written = Files.readString(file);
        assertThat(written).as("an agent's edit to a closed DITA file is a proposal too")
                .contains(">New</ph>").contains("rev=\"ai:claude-acp");
        assertThat(com.dogsbay.xml.review.ProposalOps.acceptAll(written, "ai:claude-acp"))
                .isEqualTo(TOPIC.replace("Old words.", "New words."));
    }

    @Test
    void aClosedFileThatChangedSinceTheAgentReadItIsNotEdited() throws Exception {
        SessionExecutor executor = gated(true);
        Path file = Files.writeString(root.resolve("a.dita"), TOPIC);
        executor.execute(new GetContentCommand(file, true), agent);
        Files.writeString(file, TOPIC.replace("Old words.", "Someone else's words."));

        assertThatThrownBy(() -> executor.execute(new EditStructureCommand(file, List.of(new StructuralEdit.Step(
                "delete", "//p", null, null, null, null, null, null)), false), agent))
                .isInstanceOfSatisfying(CommandException.class, e -> assertThat(e.getCode())
                        .isEqualTo(CommandException.ErrorCode.CONFLICT))
                .hasMessageContaining("Someone else's words.");
        assertThat(Files.readString(file)).contains("Someone else's words.");
    }

    @Test
    void whenTheWriterAllowsOnlyOpenDocumentsAClosedOneIsRefusedWithWhy() throws Exception {
        SessionExecutor executor = gated(false);
        Path file = Files.writeString(root.resolve("a.dita"), TOPIC);
        assertThatThrownBy(() -> executor.execute(new EditDocumentCommand(file, "Old words.", "New words.", false), agent))
                .isInstanceOfSatisfying(CommandException.class, e -> assertThat(e.getCode())
                        .isEqualTo(CommandException.ErrorCode.DOCUMENT_NOT_OPEN))
                .hasMessageContaining("edit only open documents").hasMessageContaining("get_document_content");
        assertThat(Files.readString(file)).isEqualTo(TOPIC);
    }

    @Test
    void withoutAnEditorTheFileIsTheDocument() throws Exception {
        Path file = Files.writeString(root.resolve("a.dita"), TOPIC);
        HeadlessExecutor headless = new HeadlessExecutor();
        assertThat(headless.execute(new GetContentCommand(file, true))).isEqualTo(TOPIC);
        headless.execute(new SetContentCommand(file, "<topic id=\"b\"/>"));
        assertThat(Files.readString(file)).as("a person's write lands as given").isEqualTo("<topic id=\"b\"/>");
        assertThatThrownBy(() -> headless.execute(new GetContentCommand(root.resolve("missing.dita"), true)))
                .isInstanceOfSatisfying(CommandException.class,
                        e -> assertThat(e.getCode()).isEqualTo(CommandException.ErrorCode.FILE_NOT_FOUND));
        assertThatThrownBy(() -> headless.execute(new GetContentCommand(file)))
                .isInstanceOfSatisfying(CommandException.class,
                        e -> assertThat(e.getCode()).isEqualTo(CommandException.ErrorCode.EDITOR_NOT_RUNNING));
    }

    @Test
    void aFileWriteIsRefusedWhenTheFileMovedUnderneath() throws Exception {
        Path file = Files.writeString(root.resolve("a.dita"), TOPIC);
        assertThatThrownBy(() -> FileEdits.writeIfUnchanged(file, "<topic id=\"stale\"/>", "<topic/>", null))
                .isInstanceOfSatisfying(CommandException.class, e -> assertThat(e.getCode())
                        .isEqualTo(CommandException.ErrorCode.CONFLICT));
        assertThat(Files.readString(file)).isEqualTo(TOPIC);
    }
    // ── from the review (2026-09-18) ────────────────────────────────────

    @Test
    void aFileKeepsItsEncodingAndACharacterItCannotStoreIsRefused() throws Exception {
        SessionExecutor executor = gated(true);
        String latin = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<topic id=\"a\"><title>Caf\u00e9</title>"
                + "<body><p>Old words.</p></body></topic>";
        Path file = Files.write(root.resolve("a.dita"), latin.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));

        assertThat(executor.execute(new GetContentCommand(file, true), agent)).isEqualTo(latin);
        executor.execute(new SetContentCommand(file, latin.replace("Old words.", "Cr\u00e8me.")), agent);
        String written = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(written).as("still ISO-8859-1").contains("Caf\u00e9").contains("Cr\u00e8me");

        String before = written;
        assertThatThrownBy(() -> executor.execute(new EditDocumentCommand(file, "Caf\u00e9", "Caf\u00e9 \u2014 bar", false),
                agent)).isInstanceOfSatisfying(CommandException.class, e -> assertThat(e.getCode())
                        .isEqualTo(CommandException.ErrorCode.INVALID_ARGUMENT))
                .hasMessageContaining("ISO-8859-1");
        assertThat(new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo(before);
    }

    @Test
    void aClosedFileOutsideTheProjectIsNotRead() throws Exception {
        SessionExecutor executor = gated(true);
        Path outside = Files.writeString(Files.createTempFile("secret", ".txt"), "secret");
        try {
            assertThatThrownBy(() -> executor.execute(new GetContentCommand(outside, true), agent))
                    .isInstanceOfSatisfying(CommandException.class, e -> assertThat(e.getCode())
                            .isEqualTo(CommandException.ErrorCode.PERMISSION_DENIED));
            assertThat(executor.execute(new GetContentCommand(outside, true), registry.user()))
                    .as("the writer's own reads are not contained").isEqualTo("secret");
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void theWriteMustFindWhatTheGateCheckedAndTheFileStillClosed() throws Exception {
        Path file = Files.writeString(root.resolve("a.dita"), TOPIC);
        try {
            // the gate checked an older text; the executor then read the newer one
            FileEdits.verified(file, WriteGate.hash("<topic id=\"older\"/>"));
            assertThatThrownBy(() -> FileEdits.writeIfUnchanged(file, TOPIC, "<topic/>", null))
                    .isInstanceOfSatisfying(CommandException.class, e -> assertThat(e.getCode())
                            .isEqualTo(CommandException.ErrorCode.CONFLICT));
        } finally {
            FileEdits.clearVerified();
        }
        assertThatThrownBy(() -> FileEdits.writeIfUnchanged(file, TOPIC, "<topic/>", f -> true))
                .as("opened in the editor meanwhile").hasMessageContaining("is open in the editor");
        assertThat(Files.readString(file)).isEqualTo(TOPIC);
    }

    @Test
    void aRefusedWriteLeavesTheRevertRecordAsItWas() throws Exception {
        Path file = Files.writeString(root.resolve("a.dita"), TOPIC);
        ProposalSnapshots snapshots = new ProposalSnapshots();
        com.dogsbay.agent.session.SessionContext.run(agent, () -> {
            assertThatThrownBy(() -> FileEdits.writeAgentAware(file, TOPIC.replace("Old", "Stale"),
                    TOPIC.replace("Old", "New"), true, snapshots, null)).isInstanceOf(CommandException.class);
        });
        assertThat(snapshots.restorable(agent.identity(), file, TOPIC.replace("Old", "New"))).isEmpty();
    }

    @Test
    void editingAFileThatDoesNotExistSaysSo() throws Exception {
        SessionExecutor executor = gated(true);
        assertThatThrownBy(() -> executor.execute(new EditDocumentCommand(root.resolve("nope.dita"), "a", "b", false),
                agent)).isInstanceOfSatisfying(CommandException.class, e -> assertThat(e.getCode())
                        .isEqualTo(CommandException.ErrorCode.FILE_NOT_FOUND));
    }
    @Test
    void withProposalsOffAnAgentsEditIsPlain() throws Exception {
        root = root.toRealPath();
        SessionExecutor executor = new SessionExecutor(new HeadlessExecutor(false), registry,
                new WriteGate(new WriteLease(), () -> root, NONE_OPEN, () -> true), null);
        Path file = Files.writeString(root.resolve("a.dita"), TOPIC);
        executor.execute(new GetContentCommand(file, true), agent);
        executor.execute(new EditDocumentCommand(file, "Old words.", "New words.", false), agent);
        assertThat(Files.readString(file)).isEqualTo(TOPIC.replace("Old words.", "New words."));
    }
}
