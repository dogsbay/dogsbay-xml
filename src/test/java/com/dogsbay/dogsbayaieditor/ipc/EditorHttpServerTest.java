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

package com.dogsbay.dogsbayaieditor.ipc;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

/**
 * The embedded HTTP server honors a preferred port and enforces bearer-token
 * auth on registered endpoints.
 */
class EditorHttpServerTest {

    private static final String TOKEN = "test-token-123";

    @Test
    void honorsPreferredPortWhenFree() throws Exception {
        int free;
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            free = s.getLocalPort();
        }
        EditorHttpServer server = new EditorHttpServer(TOKEN, free);
        try {
            assertThat(server.getPort()).isEqualTo(free);
        } finally {
            server.start();
            server.stop();
        }
    }

    @Test
    void fallsBackToRangeWhenPreferredPortBusy() throws Exception {
        EditorHttpServer first = new EditorHttpServer(TOKEN, 0);
        first.start();
        try {
            // Asking for the now-occupied port must yield a different one.
            EditorHttpServer second = new EditorHttpServer(TOKEN, first.getPort());
            second.start();
            try {
                assertThat(second.getPort()).isNotEqualTo(first.getPort());
            } finally {
                second.stop();
            }
        } finally {
            first.stop();
        }
    }

    @Test
    void enforcesBearerTokenAuth() throws Exception {
        EditorHttpServer server = new EditorHttpServer(TOKEN, 0);
        server.start();
        try {
            String url = "http://localhost:" + server.getPort() + "/status";
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> noAuth = client.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(noAuth.statusCode()).isEqualTo(401);

            HttpResponse<String> withAuth = client.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("Authorization", "Bearer " + TOKEN)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(withAuth.statusCode()).isEqualTo(200);
            assertThat(withAuth.body()).contains("\"status\":\"ok\"");
        } finally {
            server.stop();
        }
    }

    @Test
    void rejectsCrossOriginRequests() throws Exception {
        EditorHttpServer server = new EditorHttpServer(TOKEN, 0);
        server.start();
        try {
            String url = "http://localhost:" + server.getPort() + "/status";
            HttpClient client = HttpClient.newHttpClient();

            // Valid token but a foreign Origin — must be rejected (DNS-rebinding defense).
            HttpResponse<String> crossOrigin = client.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("Authorization", "Bearer " + TOKEN)
                            .header("Origin", "http://evil.example.com")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(crossOrigin.statusCode()).isEqualTo(403);

            // A loopback Origin is allowed.
            HttpResponse<String> localOrigin = client.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .header("Authorization", "Bearer " + TOKEN)
                            .header("Origin", "http://localhost")
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(localOrigin.statusCode()).isEqualTo(200);
        } finally {
            server.stop();
        }
    }

    @Test
    void loopbackHostRecognitionCoversIPv6WithPort() {
        // Accepted loopback Host values (with and without ports, incl. IPv6 literals).
        assertThat(EditorHttpServer.isLoopbackHost("localhost")).isTrue();
        assertThat(EditorHttpServer.isLoopbackHost("localhost:19601")).isTrue();
        assertThat(EditorHttpServer.isLoopbackHost("127.0.0.1")).isTrue();
        assertThat(EditorHttpServer.isLoopbackHost("127.0.0.1:19601")).isTrue();
        assertThat(EditorHttpServer.isLoopbackHost("[::1]")).isTrue();
        assertThat(EditorHttpServer.isLoopbackHost("[::1]:19601")).isTrue();

        // Non-loopback hosts must be rejected.
        assertThat(EditorHttpServer.isLoopbackHost("evil.example.com")).isFalse();
        assertThat(EditorHttpServer.isLoopbackHost("evil.example.com:19601")).isFalse();
        assertThat(EditorHttpServer.isLoopbackHost("10.0.0.5")).isFalse();
    }

    @Test
    void sessionTokensAreAcceptedAndAttachedToTheExchange() throws Exception {
        com.dogsbay.agent.session.AgentSessionRegistry registry =
                new com.dogsbay.agent.session.AgentSessionRegistry();
        com.dogsbay.agent.session.AgentSession hosted = registry.open(
                com.dogsbay.agent.session.SessionKind.ACP_HOSTED, "Codex", "ai:codex-acp", null);

        EditorHttpServer server = new EditorHttpServer(TOKEN, 0);
        server.setSessionResolver(registry::byToken);
        java.util.concurrent.atomic.AtomicReference<String> seen = new java.util.concurrent.atomic.AtomicReference<>();
        server.addContext("/who", exchange -> {
            seen.set(EditorHttpServer.sessionOf(exchange).map(s -> s.identity()).orElse("master"));
            EditorHttpServer.sendJson(exchange, 200, "{}");
        });
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            URI uri = URI.create("http://127.0.0.1:" + server.getPort() + "/who");

            HttpResponse<String> viaSession = client.send(HttpRequest.newBuilder(uri)
                    .header("Authorization", "Bearer " + hosted.token()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(viaSession.statusCode()).isEqualTo(200);
            assertThat(seen.get()).isEqualTo("ai:codex-acp");

            HttpResponse<String> viaMaster = client.send(HttpRequest.newBuilder(uri)
                    .header("Authorization", "Bearer " + TOKEN).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(viaMaster.statusCode()).isEqualTo(200);
            assertThat(seen.get()).isEqualTo("master");

            registry.close(hosted.id());
            HttpResponse<String> closed = client.send(HttpRequest.newBuilder(uri)
                    .header("Authorization", "Bearer " + hosted.token()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(closed.statusCode()).isEqualTo(401);

            HttpResponse<String> bogus = client.send(HttpRequest.newBuilder(uri)
                    .header("Authorization", "Bearer dbs_nope").build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(bogus.statusCode()).isEqualTo(401);
        } finally {
            server.stop();
        }
    }
}
