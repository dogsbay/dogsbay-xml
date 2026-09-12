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
package com.dogsbay.agent.acp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * A stdio MCP server that forwards every line to the editor's HTTP MCP
 * endpoint and writes the answer back, so agents that only speak stdio MCP
 * still get the editor's tools. Launched by hosted agents from the
 * {@code mcpServers} entry {@link McpInjection} builds; the endpoint and
 * the session's token arrive as environment variables. One instance per
 * agent session, exiting when stdin closes.
 */
public final class McpStdioBridge {

    private final URI url;
    private final String token;
    private final HttpClient http;
    private volatile String mcpSessionId;

    McpStdioBridge(URI url, String token, HttpClient http) {
        this.url = url;
        this.token = token;
        this.http = http;
    }

    public static void main(String[] args) throws IOException {
        String url = System.getenv(McpInjection.URL_ENV);
        String token = System.getenv(McpInjection.TOKEN_ENV);
        if (url == null || token == null) {
            System.err.println("McpStdioBridge: " + McpInjection.URL_ENV + " and "
                    + McpInjection.TOKEN_ENV + " must be set");
            System.exit(2);
        }
        new McpStdioBridge(URI.create(url), token,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build())
                .run(System.in, System.out, System.err);
    }

    /** Forward lines until {@code in} closes. */
    public void run(InputStream in, OutputStream out, PrintStream err) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            try {
                Optional<String> reply = forward(line);
                if (reply.isPresent()) {
                    out.write((reply.get() + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            } catch (IOException | InterruptedException e) {
                err.println("McpStdioBridge: " + e.getMessage());
                // A request expects an answer; the editor being down is reported
                // as one, under the request's own id. Notifications get nothing.
                String reply = unreachable(line, e.getMessage());
                if (reply != null) {
                    out.write((reply + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            }
        }
    }

    /** The JSON-RPC error for {@code line} when the editor cannot be reached; null for a notification. */
    static String unreachable(String line, String reason) {
        try {
            com.fasterxml.jackson.databind.JsonNode request = AcpWire.JSON.readTree(line);
            if (!request.hasNonNull("id")) {
                return null;
            }
            com.fasterxml.jackson.databind.node.ObjectNode reply = AcpWire.JSON.createObjectNode();
            reply.put("jsonrpc", "2.0");
            reply.set("id", request.get("id"));
            reply.putObject("error").put("code", -32000).put("message", "editor unreachable: " + reason);
            return AcpWire.JSON.writeValueAsString(reply);
        } catch (IOException e) {
            return null;
        }
    }

    /** POST one message; empty for a notification the editor acknowledged without a body. */
    Optional<String> forward(String line) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(url)
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .header(McpInjection.HOSTED_HEADER, "1")
                .POST(HttpRequest.BodyPublishers.ofString(line, StandardCharsets.UTF_8));
        if (mcpSessionId != null) {
            b.header("Mcp-Session-Id", mcpSessionId);
        }
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        r.headers().firstValue("Mcp-Session-Id").ifPresent(id -> mcpSessionId = id);
        if (r.statusCode() == 202 || r.body() == null || r.body().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(r.body().strip());
    }
}
