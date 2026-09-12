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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.dogsbayaieditor.commands.CommandException;
import com.dogsbay.dogsbayaieditor.commands.CommandExecutor;
import com.dogsbay.dogsbayaieditor.commands.GetContentCommand;
import com.dogsbay.dogsbayaieditor.commands.NewDocumentCommand;
import com.dogsbay.dogsbayaieditor.commands.OpenCommand;
import com.dogsbay.dogsbayaieditor.commands.SetContentCommand;
import com.dogsbay.dogsbayaieditor.commands.WriteGate;
import com.dogsbay.dogsbayaieditor.commands.results.CommandResult;

/**
 * Serves an agent's file requests through the editor, so it sees unsaved
 * buffers and its writes go through the document model, the write gate and
 * the audit log under its own session.
 *
 * <p>Reads: the open buffer if the file is open, else the disk, both
 * contained to the project (symlinks resolved; reads are not gated, so this
 * is their only containment). Writes: the buffer if open, else the file is
 * opened (or created) and set; setting persists, so the agent's next disk
 * read sees it and the user keeps undo.
 */
public final class EditorAcpFileSystem implements AcpClient.FileSystem {

    private final CommandExecutor executor;
    private final AgentSession session;
    private final Supplier<Path> projectRoot;

    public EditorAcpFileSystem(CommandExecutor executor, AgentSession session, Supplier<Path> projectRoot) {
        this.executor = executor;
        this.session = session;
        this.projectRoot = projectRoot;
    }

    @Override
    public String read(String sessionId, Path path, Integer line, Integer limit) throws IOException {
        Path file = contained(path);
        String content;
        try {
            content = executor.execute(new GetContentCommand(file), session);
        } catch (CommandException e) {
            if (!Files.isRegularFile(file)) {
                throw new IOException("no such file: " + file);
            }
            content = Files.readString(file, StandardCharsets.UTF_8);
        }
        if (line == null && limit == null) {
            return content;
        }
        String[] lines = content.split("\n", -1);
        int from = Math.max(0, (line == null ? 1 : line) - 1);
        int to = limit == null ? lines.length : Math.min(lines.length, from + limit);
        return from >= lines.length ? "" : String.join("\n", java.util.Arrays.copyOfRange(lines, from, to));
    }

    @Override
    public void write(String sessionId, Path path, String content) throws IOException {
        Path file = contained(path);
        try {
            if (!isOpen(file)) {
                if (!Files.exists(file)) {
                    // NewDocument writes the file to disk and opens it.
                    check(executor.execute(new NewDocumentCommand(file, content), session));
                    return;
                }
                check(executor.execute(new OpenCommand(file, null), session));
                // Read under this session so the gate's conflict check has a baseline.
                executor.execute(new GetContentCommand(file), session);
            }
            // SetContent persists the buffer; an explicit save would only wipe undo history.
            check(executor.execute(new SetContentCommand(file, content), session));
        } catch (CommandException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /** A successful read leaves the gate's baseline in place for a later write. */
    private boolean isOpen(Path file) {
        try {
            executor.execute(new GetContentCommand(file), session);
            return true;
        } catch (CommandException e) {
            return false;
        }
    }

    private static void check(CommandResult result) throws IOException {
        if (result != null && !result.success()) {
            throw new IOException(result.message());
        }
    }

    /**
     * The real path, or a refusal. Symlinks are resolved on both sides, as
     * the write gate does, so a link inside the project cannot reach out.
     * Reads are not gated, so this is their only containment.
     */
    private Path contained(Path path) throws IOException {
        Path root = projectRoot.get();
        if (root == null) {
            throw new IOException("no project is open; open one before the agent can use files");
        }
        Path real = WriteGate.real(path.toAbsolutePath().normalize());
        if (!real.startsWith(WriteGate.real(root))) {
            throw new IOException("'" + path + "' is outside the project '" + root + "'");
        }
        return real;
    }
}
