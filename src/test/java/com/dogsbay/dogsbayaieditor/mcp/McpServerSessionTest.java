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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.dogsbay.agent.session.AgentSession;
import com.dogsbay.agent.session.AgentSessionRegistry;
import com.dogsbay.agent.session.CapabilityTier;
import com.dogsbay.agent.session.SessionKind;
import com.dogsbay.dogsbayaieditor.commands.GetContentCommand;
import com.dogsbay.dogsbayaieditor.commands.RecordingExecutor;
import com.sun.net.httpserver.Headers;

class McpServerSessionTest {

    private final RecordingExecutor executor = new RecordingExecutor();
    private final AgentSessionRegistry registry = new AgentSessionRegistry();
    private final McpServer server = new McpServer(executor, registry);

    private static final String INIT = """
        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05",
         "clientInfo":{"name":"Claude Code","version":"1.0"}}}""";
    private static final String CALL = """
        {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_document_content","arguments":{}}}""";

    private String request(String body, Headers in, Headers out, Optional<AgentSession> auth) {
        return server.handleRequest(body, in, out, auth).body();
    }

    @Test
    void initializeOpensASessionNamedFromClientInfoAndReturnsItsId() {
        Headers out = new Headers();
        String response = request(INIT, new Headers(), out, Optional.empty());

        assertThat(response).contains("\"protocolVersion\"");
        String id = out.getFirst(McpServer.SESSION_HEADER);
        assertThat(id).isNotBlank();
        AgentSession s = registry.get(id).orElseThrow();
        assertThat(s.kind()).isEqualTo(SessionKind.EXTERNAL_MCP);
        assertThat(s.displayName()).isEqualTo("Claude Code");
        assertThat(s.identity()).isEqualTo("mcp:claude-code");
        assertThat(s.tier()).isEqualTo(CapabilityTier.T1_COMMANDS);
    }

    @Test
    void initializeNamesTheEntryPointsInItsInstructions() throws Exception {
        String response = request(INIT, new Headers(), new Headers(), Optional.empty());

        String instructions = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response)
                .at("/result/instructions").asText();
        assertThat(instructions).contains("project_health").contains("project_graph").contains("render_report");
    }

    @Test
    void toolCallsEchoingTheHeaderRunUnderThatSession() {
        Headers out = new Headers();
        request(INIT, new Headers(), out, Optional.empty());
        String id = out.getFirst(McpServer.SESSION_HEADER);

        Headers in = new Headers();
        in.set(McpServer.SESSION_HEADER, id);
        request(CALL, in, new Headers(), Optional.empty());

        assertThat(executor.calls).hasSize(1);
        assertThat(executor.calls.get(0).command()).isInstanceOf(GetContentCommand.class);
        assertThat(executor.calls.get(0).session().id()).isEqualTo(id);
    }

    @Test
    void aSessionTokenAuthenticatedCallerWinsOverHeadersAndOpensNothing() {
        AgentSession hosted = registry.open(SessionKind.ACP_HOSTED, "Codex", "ai:codex-acp",
                CapabilityTier.T3_DEVELOPER);
        int before = registry.list().size();

        Headers out = new Headers();
        request(INIT, new Headers(), out, Optional.of(hosted));
        request(CALL, new Headers(), new Headers(), Optional.of(hosted));

        assertThat(registry.list()).hasSize(before);
        assertThat(out.getFirst(McpServer.SESSION_HEADER)).isNull();
        assertThat(executor.calls.get(0).session()).isEqualTo(hosted);
    }

    @Test
    void clientsThatNeverSendTheHeaderShareOneFallbackSession() {
        request(CALL, new Headers(), new Headers(), Optional.empty());
        request(CALL, new Headers(), new Headers(), Optional.empty());

        assertThat(executor.calls).hasSize(2);
        AgentSession a = executor.calls.get(0).session();
        assertThat(a.kind()).isEqualTo(SessionKind.EXTERNAL_MCP);
        assertThat(a.identity()).isEqualTo("mcp:unknown");
        assertThat(executor.calls.get(1).session()).isEqualTo(a);
        assertThat(registry.list().stream().filter(s -> s.kind() == SessionKind.EXTERNAL_MCP)).hasSize(1);
    }

    @Test
    void anUnknownOrClosedSessionHeaderIs404SoTheClientReinitializes() {
        Headers in = new Headers();
        in.set(McpServer.SESSION_HEADER, "external_mcp-does-not-exist");
        McpServer.Response r = server.handleRequest(CALL, in, new Headers(), Optional.empty());
        assertThat(r.status()).isEqualTo(404);
        assertThat(r.body()).contains("send initialize again");
        assertThat(executor.calls).as("nothing ran under a guessed id").isEmpty();

        Headers out = new Headers();
        request(INIT, new Headers(), out, Optional.empty());
        String id = out.getFirst(McpServer.SESSION_HEADER);
        assertThat(server.handleDelete(id, Optional.empty()).status()).isEqualTo(200);
        in.set(McpServer.SESSION_HEADER, id);
        assertThat(server.handleRequest(CALL, in, new Headers(), Optional.empty()).status()).isEqualTo(404);
    }

    @Test
    void deleteRequiresTheSessionsOwnTokenOrTheMasterToken() {
        Headers out = new Headers();
        request(INIT, new Headers(), out, Optional.empty());
        String id = out.getFirst(McpServer.SESSION_HEADER);
        AgentSession other = registry.open(SessionKind.ACP_HOSTED, "Codex", "ai:codex-acp", null);

        assertThat(server.handleDelete(null, Optional.empty()).status()).isEqualTo(400);
        assertThat(server.handleDelete(id, Optional.of(other)).status()).isEqualTo(403);
        assertThat(registry.get(id)).isPresent();
        assertThat(server.handleDelete(id, Optional.empty()).status()).isEqualTo(200);
        assertThat(registry.get(id)).isEmpty();
        assertThat(server.handleDelete(id, Optional.empty()).status()).isEqualTo(404);
    }

    @Test
    void closeEndsEverySessionTheServerOpened() {
        AgentSession hosted = registry.open(SessionKind.ACP_HOSTED, "Codex", "ai:codex-acp", null);
        request(INIT, new Headers(), new Headers(), Optional.empty());
        request(INIT, new Headers(), new Headers(), Optional.empty());
        request(CALL, new Headers(), new Headers(), Optional.empty());
        assertThat(registry.list().stream().filter(s -> s.kind() == SessionKind.EXTERNAL_MCP)).hasSize(3);

        server.close();

        assertThat(registry.list().stream().filter(s -> s.kind() == SessionKind.EXTERNAL_MCP)).isEmpty();
        assertThat(registry.get(hosted.id())).as("not ours to close").isPresent();
    }

    @Test
    void initializeSweepsIdleExternalSessions() {
        WriteLeaseTestClock clock = new WriteLeaseTestClock();
        AgentSessionRegistry r = new AgentSessionRegistry(clock, "u");
        McpServer s = new McpServer(executor, r);
        Headers out = new Headers();
        s.handleRequest(INIT, new Headers(), out, Optional.empty());
        String stale = out.getFirst(McpServer.SESSION_HEADER);
        clock.advance(McpServer.IDLE.plusMinutes(1));

        s.handleRequest(INIT, new Headers(), new Headers(), Optional.empty());
        assertThat(r.get(stale)).isEmpty();
    }

    @Test
    void initializeOnlySessionsAreDroppedQuicklyUsedOnesAreNot() {
        WriteLeaseTestClock clock = new WriteLeaseTestClock();
        AgentSessionRegistry r = new AgentSessionRegistry(clock, "u");
        McpServer s = new McpServer(executor, r);
        Headers out = new Headers();
        s.handleRequest(INIT, new Headers(), out, Optional.empty());
        String echoing = out.getFirst(McpServer.SESSION_HEADER);
        Headers in = new Headers();
        in.set(McpServer.SESSION_HEADER, echoing);
        s.handleRequest(CALL, in, new Headers(), Optional.empty());

        Headers out2 = new Headers();
        s.handleRequest(INIT, new Headers(), out2, Optional.empty());
        String silent = out2.getFirst(McpServer.SESSION_HEADER);
        clock.advance(McpServer.UNUSED.plusMinutes(1));

        s.handleRequest(INIT, new Headers(), new Headers(), Optional.empty());
        assertThat(r.get(silent)).as("never echoed its id").isEmpty();
        assertThat(r.get(echoing)).as("used, so kept until idle").isPresent();
    }

    /** A clock the test advances by hand. */
    static final class WriteLeaseTestClock extends java.time.Clock {
        java.time.Instant now = java.time.Instant.parse("2026-09-03T10:00:00Z");
        @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public java.time.Instant instant() { return now; }
        void advance(java.time.Duration d) { now = now.plus(d); }
    }

    @Test
    void slugsAreSafeIdentityFragments() {
        assertThat(McpServer.slug("Claude Code")).isEqualTo("claude-code");
        assertThat(McpServer.slug("  Cursor/1.2 ")).isEqualTo("cursor-1-2");
        assertThat(McpServer.slug("!!!")).isEqualTo("unknown");
    }
}
