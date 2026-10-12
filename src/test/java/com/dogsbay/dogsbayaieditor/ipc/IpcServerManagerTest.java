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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Lifecycle of {@link IpcServerManager}: it starts only when enabled, registers
 * the supplied endpoints, writes/removes the discovery file, and reports status.
 * The discovery directory is isolated to a temp dir via {@code dogsbay.dir}.
 */
class IpcServerManagerTest {

    @TempDir
    static Path tempHome;

    private static String previousDir;

    @BeforeAll
    static void isolateDir() {
        previousDir = System.getProperty("dogsbay.dir");
        System.setProperty("dogsbay.dir", tempHome.toString());
    }

    @AfterAll
    static void restoreDir() {
        if (previousDir == null) {
            System.clearProperty("dogsbay.dir");
        } else {
            System.setProperty("dogsbay.dir", previousDir);
        }
    }

    private IpcServerManager manager;

    @AfterEach
    void stopManager() {
        if (manager != null) {
            manager.stop();
        }
    }

    private static IpcServerManager.Endpoint pingEndpoint() {
        return new IpcServerManager.Endpoint("/ping", exchange -> {
            byte[] body = "pong".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
    }

    @Test
    void doesNotStartWhenDisabled() {
        manager = new IpcServerManager(() -> false, () -> 0, List::of);
        manager.start();
        assertThat(manager.isRunning()).isFalse();
        assertThat(manager.getPort()).isEqualTo(-1);
        assertThat(Files.exists(tempHome.resolve("server.json"))).isFalse();
    }

    @Test
    void startsRegistersEndpointAndWritesDiscovery() throws Exception {
        manager = new IpcServerManager(() -> true, () -> 0,
                () -> List.of(pingEndpoint()));
        manager.start();

        assertThat(manager.isRunning()).isTrue();
        assertThat(manager.getPort()).isGreaterThan(0);
        assertThat(Files.exists(tempHome.resolve("server.json"))).isTrue();

        String url = "http://localhost:" + manager.getPort() + "/ping";
        HttpResponse<String> resp = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", "Bearer " + manager.getAuthToken())
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).isEqualTo("pong");
    }

    @Test
    void stopRemovesDiscoveryAndReleasesPort() {
        manager = new IpcServerManager(() -> true, () -> 0,
                () -> List.of(pingEndpoint()));
        manager.start();
        assertThat(Files.exists(tempHome.resolve("server.json"))).isTrue();

        manager.stop();
        assertThat(manager.isRunning()).isFalse();
        assertThat(manager.getPort()).isEqualTo(-1);
        assertThat(Files.exists(tempHome.resolve("server.json"))).isFalse();
    }

    @Test
    void authTokenAvailableWhenStopped() {
        manager = new IpcServerManager(() -> true, () -> 0, List::of);
        // Not started — token still resolves from the persistent file.
        assertThat(manager.getAuthToken()).isNotBlank();
    }

    @Test
    void endpointsAreToldWhenTheServerStops() throws Exception {
        java.util.concurrent.atomic.AtomicInteger stopped = new java.util.concurrent.atomic.AtomicInteger();
        IpcServerManager mgr = new IpcServerManager(() -> true, () -> 0,
                () -> List.of(new IpcServerManager.Endpoint("/ping", exchange -> {
                    EditorHttpServer.sendJson(exchange, 200, "{}");
                }, stopped::incrementAndGet)));
        mgr.start();
        try {
            assertThat(mgr.isRunning()).isTrue();
            assertThat(stopped.get()).isZero();
        } finally {
            mgr.stop();
        }
        assertThat(stopped.get()).isEqualTo(1);
        mgr.stop();
        assertThat(stopped.get()).as("stop is idempotent").isEqualTo(1);
    }
}
