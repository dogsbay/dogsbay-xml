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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.agent.acp.AcpWire.AgentInfo;
import com.dogsbay.agent.acp.AcpWire.ClientCapabilities;
import com.dogsbay.agent.acp.AcpWire.Update;

/**
 * Talks to a real registry agent. Off in CI: needs npx, network for the
 * first download, and the agent's own login. Run with
 * {@code ./gradlew test --tests '*RealAgentSmokeTest' -Dacp.smoke=claude-acp}
 * (or any registry id).
 */
@EnabledIfSystemProperty(named = "acp.smoke", matches = ".+")
class RealAgentSmokeTest {

    @TempDir
    Path cwd;

    @Test
    void initializeAndNewSessionWithTheRealAgent() throws Exception {
        String id = System.getProperty("acp.smoke");
        AgentRegistryCatalog.Entry entry = new AgentRegistryCatalog(null).byId(id).orElseThrow();
        List<String> stderr = new CopyOnWriteArrayList<>();
        List<Update> updates = new CopyOnWriteArrayList<>();
        AcpProcess process = AcpProcess.start(entry.launchCommand(), entry.launchEnv(), cwd, stderr::add);
        AcpClient client = new AcpClient(process.process().getInputStream(),
                process.process().getOutputStream(), new ClientCapabilities(true, false),
                (s, u) -> updates.add(u), r -> r.options().isEmpty() ? null : r.options().get(0).optionId(),
                null);
        try {
            AgentInfo info = client.initialize("dogsbay-xml", "test");
            System.out.println("agent: " + info);
            assertThat(info.protocolVersion()).isGreaterThanOrEqualTo(1);
            String session = client.newSession(cwd, List.of());
            System.out.println("session: " + session);
            assertThat(session).isNotBlank();
            if (Boolean.getBoolean("acp.smoke.prompt")) {
                Files.writeString(cwd.resolve("hello.txt"), "hello");
                var stop = client.prompt(session, "Reply with exactly the word PONG and nothing else.");
                System.out.println("stop: " + stop + " updates: " + updates.size());
                assertThat(updates).isNotEmpty();
            }
            if (Boolean.getBoolean("acp.smoke.mcp")) {
                // A second session with the editor's MCP server injected: does the
                // agent see and call our tools, attributed to the hosted session?
                var registry = new com.dogsbay.agent.session.AgentSessionRegistry();
                var hosted = registry.open(com.dogsbay.agent.session.SessionKind.ACP_HOSTED, "smoke",
                        "ai:" + id, com.dogsbay.agent.session.CapabilityTier.T1_COMMANDS);
                var executor = new com.dogsbay.dogsbayaieditor.commands.RecordingExecutor();
                var server = new com.dogsbay.dogsbayaieditor.ipc.EditorHttpServer("master", 0);
                server.setSessionResolver(registry::byToken);
                server.addContext("/mcp", new com.dogsbay.dogsbayaieditor.mcp.McpServer(executor, registry));
                server.start();
                try {
                    String url = "http://127.0.0.1:" + server.getPort() + "/mcp";
                    var mcp = McpInjection.servers(url, hosted.token(), info.mcpHttp(), McpInjection.bridgeCommand());
                    System.out.println("mcp injection: " + mcp);
                    String s2 = client.newSession(cwd, mcp);
                    updates.clear();
                    var stop = client.prompt(s2, "Use the dogsbay-editor MCP tool list_open_documents once, "
                            + "then reply with the word DONE.");
                    System.out.println("stop: " + stop);
                    updates.stream().filter(u -> u instanceof Update.ToolCall).forEach(u -> System.out.println("tool: " + u));
                    System.out.println("editor commands run: " + executor.calls);
                    assertThat(executor.calls).as("the agent called an editor tool through MCP").isNotEmpty();
                    assertThat(executor.calls.get(0).session()).as("attributed to the hosted session").isEqualTo(hosted);
                } finally {
                    server.stop();
                }
            }
        } finally {
            // process first, then client: the pipe's EOF is what frees the reader
            process.close();
            client.close();
            stderr.forEach(l -> System.out.println("stderr: " + l));
        }
    }
}
