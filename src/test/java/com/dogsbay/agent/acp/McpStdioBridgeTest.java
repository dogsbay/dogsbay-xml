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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.AgentSessionRegistry;
import com.dogsbay.agent.session.CapabilityTier;
import com.dogsbay.agent.session.SessionKind;
import com.dogsbay.dogsbayaieditor.commands.RecordingExecutor;
import com.dogsbay.dogsbayaieditor.ipc.EditorHttpServer;
import com.dogsbay.dogsbayaieditor.mcp.McpServer;

class McpStdioBridgeTest {

    @Test
    void forwardsLinesToTheEditorUnderTheHostedSessionAndKeepsTheMcpSession() throws Exception {
        AgentSessionRegistry registry = new AgentSessionRegistry();
        AgentSession hosted = registry.open(SessionKind.ACP_HOSTED, "Claude", "ai:claude-acp",
                CapabilityTier.T1_COMMANDS);
        RecordingExecutor executor = new RecordingExecutor();
        EditorHttpServer server = new EditorHttpServer("master", 0);
        server.setSessionResolver(registry::byToken);
        server.addContext("/mcp", new McpServer(executor, registry));
        server.start();
        try {
            String input = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","clientInfo":{"name":"agent"}}}
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"list_open_documents","arguments":{}}}
                """;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new McpStdioBridge(URI.create("http://127.0.0.1:" + server.getPort() + "/mcp"), hosted.token(),
                    HttpClient.newHttpClient())
                    .run(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), out,
                            new PrintStream(new ByteArrayOutputStream()));

            String[] lines = out.toString(StandardCharsets.UTF_8).strip().split("\n");
            assertThat(lines).as("one answer per request, none for the notification").hasSize(2);
            assertThat(lines[0]).contains("\"id\":1").contains("protocolVersion");
            assertThat(lines[1]).contains("\"id\":2").doesNotContain("\"isError\":true");
            assertThat(executor.calls).hasSize(1);
            assertThat(executor.calls.get(0).session()).as("attributed to the hosted session").isEqualTo(hosted);
            assertThat(registry.list().stream().filter(s -> s.kind() == SessionKind.EXTERNAL_MCP))
                    .as("no extra external session was opened for the bridge").isEmpty();
        } finally {
            server.stop();
        }
    }

    @Test
    void anUnreachableEditorIsReportedAsAnErrorResponseNotACrash() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String input = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"ping\"}\n"
                + "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n";
        new McpStdioBridge(URI.create("http://127.0.0.1:1/mcp"), "t", HttpClient.newHttpClient())
                .run(new ByteArrayInputStream(input.getBytes()), out, new PrintStream(err));
        String[] lines = out.toString().strip().split("\n");
        assertThat(lines).as("the request is answered, the notification is not").hasSize(1);
        assertThat(lines[0]).contains("\"id\":7").contains("editor unreachable");
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(lines[0]).at("/error/code").asInt()).isEqualTo(-32000);
        assertThat(err.toString()).contains("McpStdioBridge");
        assertThat(McpStdioBridge.unreachable("{\"id\":1}", "quote \" and \\ slash")).contains("quote \\\" and \\\\ slash");
    }
}
