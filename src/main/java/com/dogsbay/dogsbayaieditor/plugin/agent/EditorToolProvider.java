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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.dogsbay.dogsbayaieditor.commands.CommandExecutor;
import com.dogsbay.dogsbayaieditor.ipc.JsonRpcHandler;
import com.dogsbay.dogsbayaieditor.mcp.McpServer;
import com.xagent.tool.AgentTool;

/**
 * Builds the editor's {@link AgentTool}s from the MCP tool catalog
 * ({@link McpServer}), executing through the editor's command engine. The agent
 * therefore gets the same definitions as MCP clients, with zero duplication.
 *
 * <p>All catalog tools are exposed and classified read-only vs mutating via
 * {@link #READ_ONLY}. Read-only tools run freely; mutating tools (set content,
 * save, refactor, author insert/setText, …) carry {@code isReadOnly()==false} so
 * {@code PermissionToolHooks} prompts for approval before they run. See
 * plans/phase-5-xagent-plugin.md.
 */
final class EditorToolProvider {

    private EditorToolProvider() {}

    /** rpcMethods that neither mutate files nor change editor state. */
    /**
     * All editor tools, bound to {@code executor}; mutating ones are gated by
     * approval. {@code workingDir} supplies the current project root so
     * project-relative path arguments resolve correctly (see {@link CommandAgentTool}).
     */
    static List<AgentTool> tools(CommandExecutor executor, Supplier<Path> workingDir) {
        return tools(executor, workingDir, null);
    }

    /**
     * As {@link #tools(CommandExecutor, Supplier)}, with every call attributed
     * to the session {@code session} supplies (null: whatever is bound).
     */
    static List<AgentTool> tools(CommandExecutor executor, Supplier<Path> workingDir,
            Supplier<com.dogsbay.agent.session.AgentSession> session) {
        McpServer catalog = new McpServer(executor);
        CommandAgentTool.Dispatcher dispatcher = session == null
                ? catalog::dispatch
                : (m, a) -> catalog.dispatch(m, a, session.get());

        List<AgentTool> tools = new ArrayList<>();
        for (McpServer.McpTool t : catalog.getTools()) {
            boolean readOnly = JsonRpcHandler.isReadOnly(t.rpcMethod());
            tools.add(new CommandAgentTool(
                    t.name(), t.description(), t.inputSchema(), t.rpcMethod(), readOnly,
                    dispatcher, workingDir));
        }
        return tools;
    }
}
