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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.AgentSessionRegistry;
import com.dogsbay.agent.session.SessionKind;
import com.dogsbay.agent.session.WriteLease;

class WriteGateTest {

    @TempDir
    Path root;

    /** An in-memory editor: open documents and which one is active. */
    static final class FakeDocuments implements WriteGate.Documents {
        final Map<Path, String> open = new HashMap<>();
        Path active;
        @Override public Path activeDocument() { return active; }
        @Override public String content(Path file) {
            return open.get(file == null ? active : file.toAbsolutePath().normalize());
        }
    }

    private final AgentSessionRegistry registry = new AgentSessionRegistry();
    private final FakeDocuments docs = new FakeDocuments();
    private final WriteLease leases = new WriteLease(java.time.Clock.systemUTC(), Duration.ofMinutes(5));
    private WriteGate gate;
    private Path topic;

    @Test
    void untitledPlaceholderIsAPathEveryPlatformAccepts() {
        // Built when the class loads: a character Windows rejects stopped the editor starting there.
        String name = WriteGate.ACTIVE.toString();
        assertThat(name).doesNotContainPattern("[<>:\"|?*\\\\/]");
        assertThat(WriteGate.ACTIVE.isAbsolute()).isFalse();
    }

    @BeforeEach
    void setUp() throws Exception {
        // The gate keys leases by real path. On Windows the temp directory is spelled
        // with a short name (RUNNER~1) that its real path expands, so start from that.
        root = root.toRealPath();
        gate = new WriteGate(leases, () -> root, docs);
        topic = Files.writeString(root.resolve("a.dita"), "<topic/>").toAbsolutePath().normalize();
        docs.open.put(topic, "<topic/>");
        docs.active = topic;
    }

    private AgentSession agent(SessionKind kind, String name) {
        return registry.open(kind, name, "ai:" + name, null);
    }

    @Test
    void theUserAndReadOnlyCommandsPassUntouched() throws Exception {
        gate.check(registry.user(), new SetContentCommand(topic, "x"));
        gate.check(agent(SessionKind.EXTERNAL_MCP, "c"), new GetContentCommand(topic));
        gate.check(agent(SessionKind.EXTERNAL_MCP, "c"), new ValidateCommand(topic, null, null));
    }

    @Test
    void externalSessionsAreContainedToTheProject() throws Exception {
        AgentSession mcp = agent(SessionKind.EXTERNAL_MCP, "claude");
        Path outside = root.getParent().resolve("elsewhere.dita");
        assertThatThrownBy(() -> gate.check(mcp, new NewDocumentCommand(outside)))
                .isInstanceOf(CommandException.class)
                .hasMessageContaining("outside the project")
                .extracting("code").isEqualTo(CommandException.ErrorCode.PERMISSION_DENIED);
        // relative paths resolve exactly as the executors resolve them (the JVM
        // working directory), so a relative path is outside unless CWD is the project
        Path relative = Path.of("sub/new.dita");
        if (!relative.toAbsolutePath().normalize().startsWith(root)) {
            assertThatThrownBy(() -> gate.check(mcp, new NewDocumentCommand(relative)))
                    .hasMessageContaining("outside the project");
        }
        // the built-in agent is the user's own and is not contained
        gate.check(agent(SessionKind.BUILTIN, "chat"), new NewDocumentCommand(outside));
    }

    @Test
    void containmentFollowsSymlinksOutOfTheProject() throws Exception {
        Path target = Files.createDirectories(root.getParent().resolve("real-elsewhere"));
        Path link = root.resolve("link");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | java.io.IOException e) {
            return; // no symlinks on this file system
        }
        AgentSession mcp = agent(SessionKind.EXTERNAL_MCP, "claude");
        assertThatThrownBy(() -> gate.check(mcp, new NewDocumentCommand(link.resolve("x.dita"))))
                .hasMessageContaining("outside the project");
    }

    @Test
    void noProjectMeansNoExternalWrites() {
        WriteGate rootless = new WriteGate(leases, () -> null, docs);
        assertThatThrownBy(() -> rootless.check(agent(SessionKind.EXTERNAL_RPC, "cli"),
                new NewDocumentCommand(topic)))
                .hasMessageContaining("No project is open");
    }

    @Test
    void secondAgentIsRefusedWhileTheFirstHoldsTheLease() throws Exception {
        AgentSession a = agent(SessionKind.BUILTIN, "a");
        AgentSession b = agent(SessionKind.ACP_HOSTED, "b");
        gate.noteRead(a, topic, "<topic/>");
        gate.noteRead(b, topic, "<topic/>");

        gate.check(a, new SetContentCommand(topic, "new"));
        assertThatThrownBy(() -> gate.check(b, new SetContentCommand(topic, "other")))
                .isInstanceOf(CommandException.class)
                .hasMessageContaining("a.dita").hasMessageContaining("a")
                .extracting("code").isEqualTo(CommandException.ErrorCode.LOCKED);

        gate.turnEnded(a);
        gate.check(b, new SetContentCommand(topic, "other"));
    }

    @Test
    void editingTheActiveDocumentLeasesItsRealPath() throws Exception {
        AgentSession a = agent(SessionKind.BUILTIN, "a");
        AgentSession b = agent(SessionKind.BUILTIN, "b");
        gate.noteRead(a, null, "<topic/>");
        gate.check(a, new ReplaceSelectionCommand("x"));
        assertThat(leases.holder(topic)).contains(a);
        gate.noteRead(b, topic, "<topic/>");
        assertThatThrownBy(() -> gate.check(b, new SetContentCommand(topic, "y")))
                .extracting("code").isEqualTo(CommandException.ErrorCode.LOCKED);
    }

    @Test
    void aBufferEditWithoutAPriorReadIsRefusedAndPinsNothing() {
        AgentSession a = agent(SessionKind.BUILTIN, "a");
        assertThatThrownBy(() -> gate.check(a, new SetContentCommand(topic, "x")))
                .hasMessageContaining("Read the document before editing")
                .extracting("code").isEqualTo(CommandException.ErrorCode.CONFLICT);
        assertThat(leases.holder(topic)).as("a refused edit takes no lease").isEmpty();
    }

    @Test
    void aFailedCommandGivesItsLeaseBack() throws Exception {
        AgentSession a = agent(SessionKind.BUILTIN, "a");
        gate.noteRead(a, topic, "<topic/>");
        gate.check(a, new SetContentCommand(topic, "x"));
        assertThat(leases.holder(topic)).contains(a);
        gate.abandon(a, new SetContentCommand(topic, "x"));
        assertThat(leases.holder(topic)).isEmpty();
    }

    @Test
    void anUntitledActiveDocumentCanBeEdited() throws Exception {
        docs.active = null;
        docs.open.put(null, "<topic/>");   // content(null) answers for the untitled document
        AgentSession a = agent(SessionKind.BUILTIN, "a");
        gate.noteRead(a, null, "<topic/>");
        gate.check(a, new ReplaceSelectionCommand("x"));
        AgentSession b = agent(SessionKind.BUILTIN, "b");
        gate.noteRead(b, null, "<topic/>");
        assertThatThrownBy(() -> gate.check(b, new ReplaceSelectionCommand("y")))
                .extracting("code").isEqualTo(CommandException.ErrorCode.LOCKED);
    }

    @Test
    void aDocumentOpenedThroughASymlinkIsLookedUpAsGivenAndLeasedByRealPath() throws Exception {
        Path linkDir = root.resolve("link");
        try {
            Files.createSymbolicLink(linkDir, root.resolve("."));
        } catch (UnsupportedOperationException | java.io.IOException e) {
            return;
        }
        Path viaLink = linkDir.resolve("a.dita").toAbsolutePath().normalize();
        docs.open.put(viaLink, "<topic/>");   // the editor knows it by the path it was opened with
        AgentSession a = agent(SessionKind.BUILTIN, "a");
        gate.noteRead(a, viaLink, "<topic/>");
        gate.check(a, new SetContentCommand(viaLink, "x"));
        assertThat(leases.holder(topic)).as("leased by real path").contains(a);
    }

    @Test
    void renderPreviewIsAWriteOnlyWhenItHasAnOutput() throws Exception {
        AgentSession mcp = agent(SessionKind.EXTERNAL_MCP, "c");
        gate.check(mcp, new RenderPreviewCommand(topic.toString(), null, null, null));
        assertThatThrownBy(() -> gate.check(mcp,
                new RenderPreviewCommand(topic.toString(), "/tmp/elsewhere.html", null, null)))
                .hasMessageContaining("outside the project");
    }

    @Test
    void aBufferEditAfterTheDocumentChangedIsRefusedWithTheCurrentContent() throws Exception {
        AgentSession a = agent(SessionKind.BUILTIN, "a");
        gate.noteRead(a, topic, "<topic/>");
        docs.open.put(topic, "<topic><p>the writer typed</p></topic>");
        assertThatThrownBy(() -> gate.check(a, new SetContentCommand(topic, "x")))
                .hasMessageContaining("changed since you read it")
                .hasMessageContaining("the writer typed")
                .extracting("code").isEqualTo(CommandException.ErrorCode.CONFLICT);

        gate.noteRead(a, topic, docs.open.get(topic));
        gate.check(a, new SetContentCommand(topic, "x"));
    }

    @Test
    void consecutiveEditsBySameSessionKeepWorking() throws Exception {
        AgentSession a = agent(SessionKind.BUILTIN, "a");
        gate.noteRead(a, topic, "<topic/>");
        gate.check(a, new SetContentCommand(topic, "v2"));
        docs.open.put(topic, "v2");
        gate.noteWritten(a, new SetContentCommand(topic, "v2"));
        gate.check(a, new SetContentCommand(topic, "v3"));
    }

    @Test
    void saveAndCloseAreLeasedButNotConflictChecked() throws Exception {
        AgentSession a = agent(SessionKind.BUILTIN, "a");
        gate.check(a, new SaveCommand(topic, false));
        assertThat(leases.holder(topic)).contains(a);
        gate.check(a, new CloseCommand(null, false));
    }

    @Test
    void editingAnUnopenedDocumentIsReportedAsSuch() {
        AgentSession a = agent(SessionKind.BUILTIN, "a");
        Path other = root.resolve("b.dita");
        gate.noteRead(a, other, "x");
        assertThatThrownBy(() -> gate.check(a, new SetContentCommand(other, "y")))
                .extracting("code").isEqualTo(CommandException.ErrorCode.DOCUMENT_NOT_OPEN);
    }

    @Test
    void closingASessionForgetsItsReadsAndLeases() throws Exception {
        AgentSession a = agent(SessionKind.BUILTIN, "a");
        gate.noteRead(a, topic, "<topic/>");
        gate.check(a, new SetContentCommand(topic, "x"));
        gate.sessionClosed(a);
        assertThat(leases.holder(topic)).isEmpty();
        assertThatThrownBy(() -> gate.check(a, new SetContentCommand(topic, "x")))
                .extracting("code").isEqualTo(CommandException.ErrorCode.CONFLICT);
    }

    @Test
    void treeCommandsAreContainedAndLeasedOnTheirRoot() throws Exception {
        AgentSession mcp = agent(SessionKind.EXTERNAL_MCP, "c");
        gate.check(mcp, new RenameKeyCommand("a", "b", root.toString(), true));
        assertThat(leases.holder(root)).contains(mcp);
        assertThatThrownBy(() -> gate.check(mcp, new RenameKeyCommand("a", "b", "/", true)))
                .hasMessageContaining("outside the project");
    }
}
