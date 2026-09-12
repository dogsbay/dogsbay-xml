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

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.agent.acp.AgentRegistryCatalog.Distribution;
import com.dogsbay.agent.acp.AgentRegistryCatalog.Entry;

class AgentRegistryCatalogTest {

    @TempDir
    Path cache;

    @Test
    void bundledSnapshotListsTheMajorAgentsWithLaunchCommands() {
        AgentRegistryCatalog catalog = new AgentRegistryCatalog(cache);
        assertThat(catalog.entries()).hasSizeGreaterThan(20);

        Entry claude = catalog.byId("claude-acp").orElseThrow();
        assertThat(claude.name()).isEqualTo("Claude Agent");
        assertThat(claude.launchCommand()).startsWith("npx", "-y")
                .anyMatch(s -> s.startsWith("@agentclientprotocol/claude-agent-acp"));

        Entry gemini = catalog.byId("gemini").orElseThrow();
        assertThat(gemini.launchCommand()).endsWith("--acp");
        assertThat(gemini.distribution()).isInstanceOf(Distribution.Npx.class);

        assertThat(catalog.byId("codex-acp")).isPresent();
        assertThat(catalog.byId("no-such-agent")).isEmpty();
    }

    @Test
    void installedFollowsThePath() {
        AgentRegistryCatalog catalog = new AgentRegistryCatalog(cache);
        boolean npx = AgentRegistryCatalog.onPath("npx");
        Entry claude = catalog.byId("claude-acp").orElseThrow();
        assertThat(claude.isInstalled()).isEqualTo(npx);
        assertThat(catalog.installed().stream().allMatch(Entry::isInstalled)).isTrue();
        assertThat(AgentRegistryCatalog.onPath("definitely-not-a-program-xyz")).isFalse();
    }

    @Test
    void cachedRegistryWinsOverBundledAndBadCacheFallsBack() throws Exception {
        Files.writeString(cache.resolve("acp-registry.json"), """
            {"version":"1.0.0","agents":[{"id":"only","name":"Only One","distribution":{"uvx":{"package":"only-acp"}}}]}""");
        AgentRegistryCatalog fromCache = new AgentRegistryCatalog(cache);
        assertThat(fromCache.entries()).hasSize(1);
        assertThat(fromCache.byId("only").orElseThrow().launchCommand()).containsExactly("uvx", "only-acp");

        Files.writeString(cache.resolve("acp-registry.json"), "not json");
        assertThat(new AgentRegistryCatalog(cache).entries()).hasSizeGreaterThan(20);
    }

    @Test
    void binaryAgentsRunTheirInstalledToolWithTheRegistrysArguments() {
        AgentRegistryCatalog catalog = new AgentRegistryCatalog(cache);
        Entry opencode = catalog.byId("opencode").orElseThrow();
        Distribution.Binary b = (Distribution.Binary) opencode.distribution();
        assertThat(b.exe()).isEqualTo("opencode");
        assertThat(b.args()).containsExactly("acp");
        assertThat(opencode.launchCommand()).containsExactly("opencode", "acp");
        // installed means on the PATH *and* identified as an ACP-speaking tool by --help
        boolean present = AgentRegistryCatalog.onPath("opencode") && AgentRegistryCatalog.speaksAcp("opencode");
        assertThat(opencode.isInstalled()).isEqualTo(present);
        assertThat(opencode.readiness()).isEqualTo(present ? AgentRegistryCatalog.Readiness.READY
                : AgentRegistryCatalog.Readiness.UNAVAILABLE);
        assertThat(AgentRegistryCatalog.speaksAcp("definitely-not-a-program-xyz")).isFalse();
        assertThat(AgentRegistryCatalog.platformKey()).matches("(darwin|linux|windows)-(aarch64|x86_64)");
    }

    @Test
    void readinessSeparatesInstalledToolsFromFirstUseDownloads() {
        AgentRegistryCatalog catalog = new AgentRegistryCatalog(cache);
        assertThat(AgentRegistryCatalog.binName("@agentclientprotocol/claude-agent-acp@0.74.0")).isEqualTo("claude-agent-acp");
        assertThat(AgentRegistryCatalog.binName("pi-acp@0.0.33")).isEqualTo("pi-acp");
        assertThat(AgentRegistryCatalog.binName("@google/gemini-cli")).isEqualTo("gemini-cli");

        Entry claude = catalog.byId("claude-acp").orElseThrow();
        boolean claudeCli = AgentRegistryCatalog.onPath("claude");
        boolean cached = AgentRegistryCatalog.inNpxCache("claude-agent-acp");
        if (AgentRegistryCatalog.onPath("npx")) {
            assertThat(claude.readiness()).isEqualTo(claudeCli || cached
                    ? AgentRegistryCatalog.Readiness.READY : AgentRegistryCatalog.Readiness.DOWNLOAD);
        } else {
            assertThat(claude.readiness()).isEqualTo(AgentRegistryCatalog.Readiness.UNAVAILABLE);
        }
        // ready and downloadable partition the launchable set
        var ready = catalog.ready();
        var download = catalog.downloadable();
        assertThat(ready).doesNotContainAnyElementsOf(download);
        assertThat(ready.size() + download.size()).isEqualTo(catalog.installed().size());
    }

    @Test
    void refreshFetchesFromTheRegistryUrlAndWritesTheCache() throws Exception {
        com.dogsbay.dogsbayaieditor.ipc.EditorHttpServer server =
                new com.dogsbay.dogsbayaieditor.ipc.EditorHttpServer("t", 0);
        server.addContext("/registry.json", ex -> com.dogsbay.dogsbayaieditor.ipc.EditorHttpServer.sendJson(ex, 200, """
            {"version":"1.0.0","agents":[{"id":"live","name":"Live","distribution":{"npx":{"package":"live-acp",
             "env":{"LIVE_MODEL":"fast"}}}}]}"""));
        server.start();
        try {
            java.net.URI url = java.net.URI.create("http://127.0.0.1:" + server.getPort() + "/registry.json");
            // the server wants a bearer token; a client that sends none gets 401 and refresh stays false
            AgentRegistryCatalog unauthorised = new AgentRegistryCatalog(cache, HttpClient.newHttpClient(), url);
            assertThat(unauthorised.refresh()).isFalse();
            assertThat(unauthorised.entries()).as("bundled snapshot still serves").hasSizeGreaterThan(20);

            HttpClient authed = HttpClient.newBuilder().build();
            AgentRegistryCatalog catalog = new AgentRegistryCatalog(cache, new AuthorisingClient(authed, "t"), url);
            assertThat(catalog.refresh()).isTrue();
            assertThat(catalog.byId("live").orElseThrow().launchEnv()).containsEntry("LIVE_MODEL", "fast");
            assertThat(cache.resolve("acp-registry.json")).exists();
            assertThat(catalog.refresh()).as("throttled for a day after a fetch").isFalse();
        } finally {
            server.stop();
        }
    }

    @Test
    void unreachableRegistryNeverThrowsAndNeverBlocksEntries() {
        AgentRegistryCatalog catalog = new AgentRegistryCatalog(cache,
                HttpClient.newBuilder().connectTimeout(java.time.Duration.ofMillis(200)).build(),
                java.net.URI.create("http://127.0.0.1:1/registry.json"));
        assertThat(catalog.refresh()).isFalse();
        assertThat(catalog.entries()).isNotEmpty();
    }

    @Test
    void registryEnvIsCarriedIntoTheLaunch() {
        AgentRegistryCatalog catalog = new AgentRegistryCatalog(cache);
        Entry fast = catalog.byId("fast-agent").orElseThrow();
        assertThat(fast.launchEnv()).containsEntry("FAST_AGENT_MODEL", "codexplan");
        assertThat(catalog.byId("claude-acp").orElseThrow().launchEnv()).isEmpty();
    }

    /** Adds the test server's bearer token to every request. */
    static final class AuthorisingClient extends HttpClient {
        private final HttpClient delegate;
        private final String token;
        AuthorisingClient(HttpClient delegate, String token) { this.delegate = delegate; this.token = token; }
        private java.net.http.HttpRequest withAuth(java.net.http.HttpRequest r) {
            java.net.http.HttpRequest.Builder b = java.net.http.HttpRequest.newBuilder(r.uri())
                    .header("Authorization", "Bearer " + token).GET();
            r.timeout().ifPresent(b::timeout);
            return b.build();
        }
        @Override public <T> java.net.http.HttpResponse<T> send(java.net.http.HttpRequest r,
                java.net.http.HttpResponse.BodyHandler<T> h) throws java.io.IOException, InterruptedException {
            return delegate.send(withAuth(r), h);
        }
        @Override public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(
                java.net.http.HttpRequest r, java.net.http.HttpResponse.BodyHandler<T> h) { return delegate.sendAsync(withAuth(r), h); }
        @Override public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(
                java.net.http.HttpRequest r, java.net.http.HttpResponse.BodyHandler<T> h,
                java.net.http.HttpResponse.PushPromiseHandler<T> p) { return delegate.sendAsync(withAuth(r), h, p); }
        @Override public java.util.Optional<java.net.CookieHandler> cookieHandler() { return delegate.cookieHandler(); }
        @Override public java.util.Optional<java.time.Duration> connectTimeout() { return delegate.connectTimeout(); }
        @Override public Redirect followRedirects() { return delegate.followRedirects(); }
        @Override public java.util.Optional<java.net.ProxySelector> proxy() { return delegate.proxy(); }
        @Override public javax.net.ssl.SSLContext sslContext() { return delegate.sslContext(); }
        @Override public javax.net.ssl.SSLParameters sslParameters() { return delegate.sslParameters(); }
        @Override public java.util.Optional<java.net.Authenticator> authenticator() { return delegate.authenticator(); }
        @Override public Version version() { return delegate.version(); }
        @Override public java.util.Optional<java.util.concurrent.Executor> executor() { return delegate.executor(); }
    }
}
