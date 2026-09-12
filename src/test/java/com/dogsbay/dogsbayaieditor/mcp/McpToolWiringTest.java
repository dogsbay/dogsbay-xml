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

package com.dogsbay.dogsbayaieditor.mcp;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every MCP tool must name an RPC method the handler can actually dispatch.
 *
 * <p>A tool declares its {@code rpcMethod} as a bare string, and
 * {@code JsonRpcHandler} matches it in a {@code switch} on another bare string.
 * Nothing links the two, so a typo — or adding a tool and forgetting the handler
 * case — ships silently and fails only when someone invokes that tool. Adding
 * {@code schematron} touched both files, and this test is the guard that pairing
 * now has.
 *
 * <p>Dispatch is probed with empty arguments: a reachable method fails on its
 * missing parameters, an unreachable one fails with "Method not found".
 */
class McpToolWiringTest {

    private final McpServer server = new McpServer(new HeadlessExecutor());

    @Test
    void everyToolDispatchesToAKnownRpcMethod() {
        com.fasterxml.jackson.databind.node.ObjectNode empty =
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        StringBuilder unreachable = new StringBuilder();

        for (McpServer.McpTool tool : server.getTools()) {
            try {
                server.dispatch(tool.rpcMethod(), empty);
            } catch (Exception e) {
                String message = String.valueOf(e.getMessage());
                if (message.contains("Unknown method")) {
                    unreachable.append("\n  ").append(tool.name())
                            .append(" -> ").append(tool.rpcMethod());
                }
                // Any other failure means the method WAS found and then rejected
                // the empty arguments, which is what should happen.
            }
        }
        assertEquals("", unreachable.toString(),
                "MCP tools naming an RPC method JsonRpcHandler cannot dispatch:" + unreachable);
    }

    @Test
    void toolNamesAreUnique() {
        Set<String> seen = new HashSet<>();

        for (McpServer.McpTool tool : server.getTools()) {
            assertTrue(seen.add(tool.name()), "duplicate MCP tool name: " + tool.name());
        }
    }

    @Test
    void theSingleDocumentSchematronToolIsExposed() {
        assertTrue(server.getTools().stream().anyMatch(t -> "schematron".equals(t.name())),
                "the document-scoped schematron tool is missing from the MCP catalog");
    }
}
