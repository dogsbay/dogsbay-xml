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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dogsbay.agent.acp.AcpWire.AgentInfo;
import com.dogsbay.agent.acp.AcpWire.ClientCapabilities;
import com.dogsbay.agent.acp.AcpWire.McpServerConfig;
import com.dogsbay.agent.acp.AcpWire.StopReason;
import com.dogsbay.agent.acp.AcpWire.Update;
import com.dogsbay.agent.acp.JsonRpcConnection.RpcException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class AcpClientTest {

    /** An in-memory file system the client serves from. */
    static final class MemoryFs implements AcpClient.FileSystem {
        final Map<Path, String> files = new ConcurrentHashMap<>();
        @Override public String read(String s, Path p, Integer line, Integer limit) throws IOException {
            String c = files.get(p);
            if (c == null) throw new IOException("no such file: " + p);
            return c;
        }
        @Override public void write(String s, Path p, String content) { files.put(p, content); }
    }

    private FakeAcpAgent agent;
    private final List<Update> updates = new CopyOnWriteArrayList<>();
    private final List<String> failures = new CopyOnWriteArrayList<>();
    private final MemoryFs fs = new MemoryFs();
    private final List<String> permissionEvents = new CopyOnWriteArrayList<>();
    private final AtomicReference<String> permissionChoice = new AtomicReference<>("allow-once");
    private AcpClient client;

    @BeforeEach
    void setUp() throws Exception {
        agent = new FakeAcpAgent();
        agent.on("initialize", p -> {
            ObjectNode r = FakeAcpAgent.JSON.createObjectNode();
            r.put("protocolVersion", 1);
            r.putObject("agentInfo").put("name", "Fake").put("version", "0.1");
            r.putObject("agentCapabilities").putObject("mcpCapabilities").put("http", false);
            r.putArray("authMethods").addObject().put("id", "oauth").put("name", "Sign in");
            return r;
        });
        agent.on("authenticate", p -> FakeAcpAgent.JSON.createObjectNode());
        agent.on("session/new", p -> FakeAcpAgent.JSON.createObjectNode().put("sessionId", "sess-1"));
    }

    private AcpClient client(ClientCapabilities caps) {
        client = new AcpClient(agent.clientReads, agent.clientWrites, caps, new AcpClient.Listener() {
            @Override public void onUpdate(String sessionId, Update update) { updates.add(update); }
            @Override public void onFailure(String message, Throwable cause) { failures.add(message); }
            @Override public void onPermissionRequested(AcpWire.PermissionRequest r) { permissionEvents.add("asked:" + r.toolCall().title()); }
            @Override public void onPermissionAnswered(AcpWire.PermissionRequest r, String id) { permissionEvents.add("answered:" + id); }
        }, request -> permissionChoice.get(), fs);
        return client;
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) client.close();
        agent.close();
    }

    @Test
    void initializeAdvertisesTheTierAndReadsTheAgentsAnswer() throws Exception {
        AgentInfo info = client(new ClientCapabilities(true, false)).initialize("dogsbay", "1.0");
        assertThat(info.name()).isEqualTo("Fake");
        assertThat(info.protocolVersion()).isEqualTo(1);
        assertThat(info.needsAuth()).isTrue();
        assertThat(info.authMethods().get(0).id()).isEqualTo("oauth");
        assertThat(info.mcpHttp()).isFalse();

        JsonNode sent = agent.requestsFor("initialize").get(0).get("params");
        assertThat(sent.get("protocolVersion").asInt()).isEqualTo(1);
        assertThat(sent.at("/clientCapabilities/fs/readTextFile").asBoolean()).isTrue();
        assertThat(sent.at("/clientCapabilities/fs/writeTextFile").asBoolean()).isFalse();
        assertThat(sent.at("/clientCapabilities/terminal").asBoolean()).isFalse();
        assertThat(sent.at("/clientInfo/name").asText()).isEqualTo("dogsbay");
    }

    @Test
    void newSessionCarriesCwdAndTheInjectedMcpServers() throws Exception {
        AcpClient c = client(new ClientCapabilities(false, false));
        c.initialize("dogsbay", "1.0");
        c.authenticate("oauth");
        String id = c.newSession(Path.of("/proj"), List.of(
                new McpServerConfig.Http("dogsbay-editor", "http://127.0.0.1:1/mcp", Map.of("Authorization", "Bearer t")),
                new McpServerConfig.Stdio("bridge", "java", List.of("-cp", "x"), Map.of("K", "V"))));
        assertThat(id).isEqualTo("sess-1");
        assertThat(agent.requestsFor("authenticate").get(0).at("/params/methodId").asText()).isEqualTo("oauth");
        JsonNode p = agent.requestsFor("session/new").get(0).get("params");
        assertThat(p.get("cwd").asText()).isEqualTo("/proj");
        assertThat(p.at("/mcpServers/0/type").asText()).isEqualTo("http");
        assertThat(p.at("/mcpServers/0/headers/0/name").asText()).isEqualTo("Authorization");
        assertThat(p.at("/mcpServers/1/type").asText()).isEqualTo("stdio");
        assertThat(p.at("/mcpServers/1/command").asText()).isEqualTo("java");
        assertThat(p.at("/mcpServers/1/env/0/name").asText()).isEqualTo("K");
    }

    @Test
    void promptStreamsUpdatesThenReturnsTheStopReason() throws Exception {
        agent.on("session/prompt", p -> {
            try {
                agent.update("sess-1", FakeAcpAgent.textChunk("agent_message_chunk", "Hello "));
                agent.update("sess-1", FakeAcpAgent.textChunk("agent_thought_chunk", "thinking"));
                ObjectNode tc = FakeAcpAgent.JSON.createObjectNode();
                tc.put("sessionUpdate", "tool_call").put("toolCallId", "c1").put("title", "Read a.dita")
                        .put("kind", "read").put("status", "pending");
                tc.putArray("locations").addObject().put("path", "/proj/a.dita");
                agent.update("sess-1", tc);
                ObjectNode plan = FakeAcpAgent.JSON.createObjectNode();
                plan.put("sessionUpdate", "plan");
                plan.putArray("entries").addObject().put("content", "step 1").put("priority", "high").put("status", "pending");
                agent.update("sess-1", plan);
                agent.update("sess-1", FakeAcpAgent.textChunk("agent_message_chunk", "world"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return FakeAcpAgent.JSON.createObjectNode().put("stopReason", "end_turn");
        });
        AcpClient c = client(new ClientCapabilities(false, false));
        c.initialize("dogsbay", "1.0");
        c.newSession(Path.of("/proj"), List.of());

        StopReason stop = c.prompt("sess-1", "hi");

        assertThat(stop).isEqualTo(StopReason.END_TURN);
        assertThat(agent.requestsFor("session/prompt").get(0).at("/params/prompt/0/text").asText()).isEqualTo("hi");
        // updates are notifications, delivered before the prompt result arrives
        assertThat(updates).hasSize(5);
        assertThat(updates.get(0)).isEqualTo(new Update.AgentMessage("Hello "));
        assertThat(updates.get(1)).isEqualTo(new Update.AgentThought("thinking"));
        assertThat(updates.get(2)).isInstanceOf(Update.ToolCall.class);
        assertThat(((Update.ToolCall) updates.get(2)).locations()).containsExactly("/proj/a.dita");
        assertThat(((Update.Plan) updates.get(3)).entries().get(0).content()).isEqualTo("step 1");
        assertThat(updates.get(4)).isEqualTo(new Update.AgentMessage("world"));
    }

    @Test
    void permissionRequestsAreAnsweredWithTheChosenOptionOrCancelled() throws Exception {
        client(new ClientCapabilities(false, false)).initialize("dogsbay", "1.0");
        ObjectNode p = FakeAcpAgent.JSON.createObjectNode();
        p.put("sessionId", "sess-1");
        p.putObject("toolCall").put("toolCallId", "c1").put("title", "Edit a.dita").put("kind", "edit");
        var opts = p.putArray("options");
        opts.addObject().put("optionId", "allow-once").put("name", "Allow").put("kind", "allow_once");
        opts.addObject().put("optionId", "reject-once").put("name", "Reject").put("kind", "reject_once");

        JsonNode r = agent.request("session/request_permission", p);
        assertThat(r.at("/outcome/outcome").asText()).isEqualTo("selected");
        assertThat(r.at("/outcome/optionId").asText()).isEqualTo("allow-once");

        permissionChoice.set(null);
        r = agent.request("session/request_permission", p);
        assertThat(r.at("/outcome/outcome").asText()).isEqualTo("cancelled");
        assertThat(permissionEvents).containsExactly("asked:Edit a.dita", "answered:allow-once",
                "asked:Edit a.dita", "answered:null");
    }

    @Test
    void fileRequestsAreServedOnlyWhenTheTierAdvertisedThem() throws Exception {
        fs.files.put(Path.of("/proj/a.dita"), "<topic/>");
        client(new ClientCapabilities(true, false)).initialize("dogsbay", "1.0");

        ObjectNode read = FakeAcpAgent.JSON.createObjectNode();
        read.put("sessionId", "sess-1").put("path", "/proj/a.dita");
        assertThat(agent.request("fs/read_text_file", read).get("content").asText()).isEqualTo("<topic/>");

        ObjectNode missing = FakeAcpAgent.JSON.createObjectNode();
        missing.put("sessionId", "sess-1").put("path", "/proj/nope.dita");
        assertThatThrownBy(() -> agent.request("fs/read_text_file", missing))
                .hasMessageContaining("no such file");

        ObjectNode write = FakeAcpAgent.JSON.createObjectNode();
        write.put("sessionId", "sess-1").put("path", "/proj/a.dita").put("content", "x");
        assertThatThrownBy(() -> agent.request("fs/write_text_file", write))
                .as("write was not advertised, so it is refused even though the agent asks")
                .hasMessageContaining("-32601");
        assertThat(fs.files.get(Path.of("/proj/a.dita"))).isEqualTo("<topic/>");

        ObjectNode term = FakeAcpAgent.JSON.createObjectNode();
        term.put("sessionId", "sess-1").put("command", "rm");
        assertThatThrownBy(() -> agent.request("terminal/create", term)).hasMessageContaining("terminals");
    }

    @Test
    void writesAreServedAtTierThree() throws Exception {
        client(new ClientCapabilities(true, true)).initialize("dogsbay", "1.0");
        ObjectNode write = FakeAcpAgent.JSON.createObjectNode();
        write.put("sessionId", "sess-1").put("path", "/proj/a.dita").put("content", "x");
        agent.request("fs/write_text_file", write);
        assertThat(fs.files.get(Path.of("/proj/a.dita"))).isEqualTo("x");
    }

    @Test
    void attachmentsTravelAsResourceBlocksAndEmbeddedContextIsParsed() throws Exception {
        AcpClient c = client(new ClientCapabilities(false, false));
        AgentInfo info = c.initialize("dogsbay", "1.0");
        assertThat(info.embeddedContext()).as("fake agent does not advertise it").isFalse();
        agent.on("session/prompt", p -> FakeAcpAgent.JSON.createObjectNode().put("stopReason", "end_turn"));
        c.prompt("sess-1", "look", List.of(new AcpClient.Attachment(Path.of("/p/a.dita"), "application/xml", "<t/>")));
        JsonNode prompt = agent.requestsFor("session/prompt").get(0).at("/params/prompt");
        assertThat(prompt.size()).isEqualTo(2);
        assertThat(prompt.at("/1/type").asText()).isEqualTo("resource");
        assertThat(prompt.at("/1/resource/text").asText()).isEqualTo("<t/>");
        assertThat(prompt.at("/1/resource/uri").asText()).startsWith("file:");
    }

    @Test
    void aCodelessAgentErrorIsInternalNotAuthRequired() throws Exception {
        agent.on("session/prompt", p -> { throw new RuntimeException("no code here"); });
        AcpClient c = client(new ClientCapabilities(false, false));
        c.initialize("dogsbay", "1.0");
        assertThatThrownBy(() -> c.prompt("sess-1", "x")).isInstanceOfSatisfying(RpcException.class,
                e -> assertThat(e.code()).isEqualTo(-32603));
    }

    @Test
    void cancelIsANotificationAndAgentErrorsSurfaceAsRpcExceptions() throws Exception {
        agent.on("session/prompt", p -> { throw new RuntimeException("model unavailable"); });
        AcpClient c = client(new ClientCapabilities(false, false));
        c.initialize("dogsbay", "1.0");
        c.cancel("sess-1");
        assertThat(agent.awaitRequests("session/cancel", 1)).hasSize(1);
        assertThat(agent.requestsFor("session/cancel").get(0).has("id")).isFalse();
        assertThatThrownBy(() -> c.prompt("sess-1", "x"))
                .isInstanceOf(RpcException.class).hasMessageContaining("model unavailable");
    }

    @Test
    void anAgentCrashFailsPendingRequestsAndReportsOnce() throws Exception {
        CountDownLatch inPrompt = new CountDownLatch(1);
        agent.on("session/prompt", p -> {
            inPrompt.countDown();
            try { Thread.sleep(200); } catch (InterruptedException ignore) { }
            try { agent.crash(); } catch (IOException ignore) { }
            return FakeAcpAgent.JSON.createObjectNode();
        });
        AcpClient c = client(new ClientCapabilities(false, false));
        c.initialize("dogsbay", "1.0");
        assertThatThrownBy(() -> c.prompt("sess-1", "x")).isInstanceOf(IOException.class);
        assertThat(inPrompt.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(c.isConnected()).isFalse();
        assertThat(failures).as("an idle client is told the agent went away").contains("agent closed the connection");
    }

    @Test
    void loadSessionReplaysHistoryBeforeAnswering() throws Exception {
        agent.on("session/load", p -> {
            try {
                agent.update("old-1", FakeAcpAgent.textChunk("agent_message_chunk", "earlier reply"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return FakeAcpAgent.JSON.createObjectNode();
        });
        AcpClient c = client(new ClientCapabilities(false, false));
        c.initialize("dogsbay", "1.0");

        c.loadSession("old-1", Path.of("/proj"), List.of(
                new McpServerConfig.Http("dogsbay-editor", "http://127.0.0.1:1/mcp", Map.of())));

        JsonNode p = agent.requestsFor("session/load").get(0).get("params");
        assertThat(p.get("sessionId").asText()).isEqualTo("old-1");
        assertThat(p.get("cwd").asText()).isEqualTo("/proj");
        assertThat(p.at("/mcpServers/0/name").asText()).isEqualTo("dogsbay-editor");
        assertThat(updates).anySatisfy(u -> assertThat(u).isInstanceOf(Update.AgentMessage.class));
    }
}
