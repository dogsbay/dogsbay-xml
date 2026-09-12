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

import com.dogsbay.agent.acp.AcpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.AgentSessionRegistry;
import com.dogsbay.agent.session.SessionKind;
import com.dogsbay.dogsbayaieditor.commands.Command;
import com.dogsbay.dogsbayaieditor.commands.CommandException;
import com.dogsbay.dogsbayaieditor.commands.CommandExecutor;
import com.dogsbay.dogsbayaieditor.commands.GetContentCommand;
import com.dogsbay.dogsbayaieditor.commands.NewDocumentCommand;
import com.dogsbay.dogsbayaieditor.commands.OpenCommand;
import com.dogsbay.dogsbayaieditor.commands.SessionExecutor;
import com.dogsbay.dogsbayaieditor.commands.SetContentCommand;
import com.dogsbay.dogsbayaieditor.commands.WriteGate;
import com.dogsbay.dogsbayaieditor.commands.results.CommandResult;
import com.dogsbay.agent.session.WriteLease;

class EditorAcpFileSystemTest {

    @TempDir
    Path root;

    /** Open buffers by path; open/new/set mutate it and the disk, as the editor does. */
    final class FakeEditor implements CommandExecutor, WriteGate.Documents {
        final Map<Path, String> open = new HashMap<>();
        final java.util.List<String> log = new java.util.ArrayList<>();
        boolean setFails;

        @Override public Path activeDocument() { return null; }
        @Override public String content(Path file) { return open.get(file); }

        @Override
        @SuppressWarnings("unchecked")
        public <R> R execute(Command<R> command) throws CommandException {
            log.add(command.getClass().getSimpleName());
            try {
                switch (command) {
                    case GetContentCommand c -> {
                        String s = open.get(c.file());
                        if (s == null) throw new CommandException(CommandException.ErrorCode.DOCUMENT_NOT_OPEN, "not open");
                        return (R) s;
                    }
                    case OpenCommand c -> open.put(c.file(), Files.readString(c.file()));
                    case NewDocumentCommand c -> {
                        Files.createDirectories(c.file().getParent());
                        open.put(c.file(), c.content() == null ? "" : c.content());
                        Files.writeString(c.file(), open.get(c.file()));
                    }
                    case SetContentCommand c -> {
                        if (setFails) return (R) CommandResult.fail("No active editor panel");
                        open.put(c.file(), c.content());
                        Files.writeString(c.file(), c.content());
                    }
                    default -> { }
                }
            } catch (IOException e) {
                throw new CommandException(CommandException.ErrorCode.INTERNAL_ERROR, e.getMessage(), e);
            }
            return null;
        }
    }

    private final AgentSessionRegistry registry = new AgentSessionRegistry();
    private final AgentSession session = registry.open(SessionKind.ACP_HOSTED, "A", "ai:a", null);
    private final FakeEditor editor = new FakeEditor();
    /** The file system over the real session executor and write gate, as in the editor. */
    private EditorAcpFileSystem fs() {
        WriteGate gate = new WriteGate(new WriteLease(), () -> root, editor);
        return new EditorAcpFileSystem(new SessionExecutor(editor, registry, gate, null), session, () -> root);
    }

    @Test
    void readsPreferTheOpenBufferOverTheDisk() throws Exception {
        Path a = Files.writeString(root.resolve("a.dita"), "on disk");
        assertThat(fs().read("s", a, null, null)).isEqualTo("on disk");
        editor.open.put(a, "unsaved");
        assertThat(fs().read("s", a, null, null)).isEqualTo("unsaved");
    }

    @Test
    void readsHonourLineAndLimit() throws Exception {
        Path a = Files.writeString(root.resolve("a.dita"), "l1\nl2\nl3\nl4");
        assertThat(fs().read("s", a, 2, 2)).isEqualTo("l2\nl3");
        assertThat(fs().read("s", a, 4, null)).isEqualTo("l4");
        assertThat(fs().read("s", a, 9, null)).isEmpty();
    }

    @Test
    void readsAndWritesAreContainedToTheProject() throws Exception {
        Path outside = root.getParent().resolve("secret.txt");
        assertThatThrownBy(() -> fs().read("s", outside, null, null)).hasMessageContaining("outside the project");
        assertThatThrownBy(() -> fs().write("s", outside, "x")).hasMessageContaining("outside the project");
        assertThatThrownBy(() -> new EditorAcpFileSystem(editor, session, () -> null).read("s", root.resolve("a"), null, null))
                .hasMessageContaining("no project is open");
    }

    @Test
    void writeToAnOpenBufferSetsItThroughTheGate() throws Exception {
        Path a = Files.writeString(root.resolve("a.dita"), "old");
        editor.open.put(a, "old");
        fs().write("s", a, "new");
        assertThat(editor.open.get(a)).isEqualTo("new");
        assertThat(Files.readString(a)).isEqualTo("new");
        assertThat(editor.log).contains("SetContentCommand").doesNotContain("OpenCommand", "SaveCommand");
    }

    @Test
    void writeToAnExistingUnopenedFileOpensReadsThenSets() throws Exception {
        Path a = Files.writeString(root.resolve("a.dita"), "old");
        fs().write("s", a, "new");
        // the read after open is what lets the gate's conflict check pass
        assertThat(editor.log).containsSubsequence("OpenCommand", "GetContentCommand", "SetContentCommand");
        assertThat(Files.readString(a)).isEqualTo("new");
    }

    @Test
    void writeToANewFileCreatesIt() throws Exception {
        Path n = root.resolve("sub/new.dita");
        fs().write("s", n, "fresh");
        assertThat(editor.log).contains("NewDocumentCommand").doesNotContain("SetContentCommand", "SaveCommand");
        assertThat(Files.readString(n)).isEqualTo("fresh");
    }

    @Test
    void aFailedSetIsReportedNotSwallowed() throws Exception {
        Path a = Files.writeString(root.resolve("a.dita"), "old");
        editor.open.put(a, "old");
        editor.setFails = true;
        assertThatThrownBy(() -> fs().write("s", a, "new")).hasMessageContaining("No active editor panel");
        assertThat(Files.readString(a)).isEqualTo("old");
    }

    @Test
    void aSymlinkInsideTheProjectCannotReachOutside() throws Exception {
        Path secret = Files.writeString(root.getParent().resolve("secret.txt"), "key");
        Path link = root.resolve("notes");
        try {
            Files.createSymbolicLink(link, secret);
        } catch (UnsupportedOperationException | IOException e) {
            return;
        }
        assertThatThrownBy(() -> fs().read("s", link, null, null)).hasMessageContaining("outside the project");
        assertThatThrownBy(() -> fs().write("s", link, "x")).hasMessageContaining("outside the project");
        assertThat(Files.readString(secret)).isEqualTo("key");
    }

    @Test
    void missingFilesAreReportedAsIoErrors() {
        assertThatThrownBy(() -> fs().read("s", root.resolve("nope.dita"), null, null))
                .isInstanceOf(IOException.class).hasMessageContaining("no such file");
    }
}
