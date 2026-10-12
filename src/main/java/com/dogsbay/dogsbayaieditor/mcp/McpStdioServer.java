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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Optional;

import com.sun.net.httpserver.Headers;

/**
 * An {@link McpServer} over stdio, the transport local MCP clients (Claude Code, Cursor) use to start a
 * server: one JSON-RPC message per line in, one answer per line out, notifications unanswered. The
 * session opened by {@code initialize} is kept between requests, as the HTTP transport's session header
 * keeps it, so every call is attributed to the client.
 */
public final class McpStdioServer {

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static void reply(PrintStream out, String body) {
        out.println(body);
        out.flush();
    }

    private static String error(com.fasterxml.jackson.databind.JsonNode id, int code, String message) {
        var node = JSON.createObjectNode().put("jsonrpc", "2.0");
        node.set("id", id == null ? com.fasterxml.jackson.databind.node.NullNode.getInstance() : id);
        node.putObject("error").put("code", code).put("message", message);
        return node.toString();
    }

    private final McpServer server;

    public McpStdioServer(McpServer server) {
        this.server = server;
    }

    /** Serve until {@code in} ends, then close the session. */
    public void run(BufferedReader in, PrintStream out) throws IOException {
        String session = null;
        try {
            for (String line; (line = in.readLine()) != null;) {
                if (line.isBlank()) {
                    continue;
                }
                Headers request = new Headers();
                if (session != null) {
                    request.set(McpServer.SESSION_HEADER, session);
                }
                com.fasterxml.jackson.databind.JsonNode message;
                try {
                    message = JSON.readTree(line);
                } catch (Exception e) {
                    reply(out, error(null, -32700, "Parse error: " + e.getMessage()));
                    continue;
                }
                if (message == null || !message.isObject()) {
                    reply(out, error(null, -32600, message != null && message.isArray()
                            ? "Batches are not supported; send one message per line" : "Invalid request"));
                    continue;
                }
                // A message without an id is a notification: it is handled, and never answered
                boolean notification = !message.has("id");
                Headers response = new Headers();
                McpServer.Response r;
                try {
                    r = server.handleRequest(line, request, response, Optional.empty());
                } catch (Throwable t) {
                    // one failing message (a StackOverflowError on a deep document, say) must not end the session
                    if (!notification) {
                        reply(out, error(message.get("id"), -32603, "Internal error: " + t));
                    }
                    continue;
                }
                String id = response.getFirst(McpServer.SESSION_HEADER);
                if (id != null) {
                    session = id;
                }
                if (r.body() != null && !notification) {
                    reply(out, r.body());
                }
            }
        } finally {
            server.close();
        }
    }
}
