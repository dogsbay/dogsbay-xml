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

package com.dogsbay.dogsbayaieditor.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import com.dogsbay.agent.session.AgentSessionRegistry;
import com.dogsbay.agent.session.AuditLog;
import com.dogsbay.agent.session.WriteLease;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.SessionExecutor;
import com.dogsbay.dogsbayaieditor.commands.UnsavedInEditorGuard;
import com.dogsbay.dogsbayaieditor.commands.WriteGate;
import com.dogsbay.dogsbayaieditor.mcp.McpServer;
import com.dogsbay.dogsbayaieditor.mcp.McpStdioServer;

/**
 * The editor's tools as an MCP server over stdio, without the editor: for Claude Code, Cursor or a CI job
 * working on a project's files. The project folder is the agent's sandbox, as the open project is in the
 * editor; edits are checked against what the agent read and land as review proposals unless turned off.
 */
@Command(name = "mcp",
        description = {"Run the editor's tools as an MCP server over stdio, without the editor, on the files of "
                + "a project folder. The agent reads and writes only inside it, must read a document before "
                + "editing it, and its edits land as review proposals (unless --no-proposals).",
            "Claude Code:  claude mcp add dogsbay -- dogsbay-xml mcp --root /path/to/project"})
public class McpCmd implements Callable<Integer> {

    @Option(names = "--root", description = "The project folder (default: the working directory)")
    private Path root;

    @Option(names = "--no-proposals", description = "Agents' edits apply as plain text, without review marks")
    private boolean noProposals;

    @Override
    public Integer call() throws Exception {
        // Standard output is the protocol: anything else a command prints goes to standard error
        PrintStream protocol = new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true,
                StandardCharsets.UTF_8);
        System.setOut(System.err);
        // Previews this server starts are its own, and end with it (the client's session)
        com.dogsbay.dogsbayaieditor.dita.live.PreviewRegistry.setOwner("mcp");
        Path folder = (root == null ? Path.of("") : root).toAbsolutePath().normalize();
        if (!Files.isDirectory(folder)) {
            System.err.println("Error: no such folder: " + folder);
            return 2;
        }
        Path project = folder.toRealPath();
        McpServer server = server(project, !noProposals, RunningEditor::unsavedFiles);
        System.err.println("dogsbay-xml mcp: serving " + project + (noProposals ? " (plain edits)" : ""));
        new McpStdioServer(server).run(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                protocol);
        return 0;
    }

    /**
     * The headless server for {@code project}: the same tools as the editor's, less those that need it.
     *
     * @param unsaved the files a running editor holds with unsaved changes, which are not written
     */
    static McpServer server(Path project, boolean proposals, java.util.function.Supplier<java.util.Set<Path>> unsaved) {
        AgentSessionRegistry sessions = new AgentSessionRegistry();
        WriteGate gate = new WriteGate(new WriteLease(), () -> project, new WriteGate.Documents() {
            @Override
            public Path activeDocument() {
                return null;
            }

            @Override
            public String content(Path file) {
                return null; // nothing is open: documents are files
            }
        }, () -> true);
        var executor = new SessionExecutor(
                new UnsavedInEditorGuard(new HeadlessExecutor(proposals,
                        file -> unsaved.get().contains(WriteGate.real(file))), unsaved),
                sessions, gate, new AuditLog(() -> project));
        return new McpServer(executor, sessions, McpServer.McpTool::worksWithoutEditor,
                "This server runs without the editor, on the files of " + project + ": documents are files (give "
                + "absolute paths), tools that need the editor are not offered, and you may read and write only "
                + "inside that folder. Your document edits (edit_document, edit_structure, set_document_content) "
                + (proposals ? "land as review proposals" : "apply as plain text: review proposals are off")
                + "; metadata_set, the refactorings and the other tools that write apply directly.");
    }
}
