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

import java.util.List;
import java.util.function.Supplier;

import com.sun.net.httpserver.HttpHandler;

/**
 * Owns the lifecycle of the embedded integration server (the {@link
 * EditorHttpServer} plus its {@link DiscoveryFile}) so it can be started and
 * stopped <em>at runtime</em> when the user toggles the setting — not only once
 * at editor construction.
 *
 * <p>The server is an opt-in surface and is <b>off by default</b>; {@link
 * #start()} is a no-op unless the {@code enabled} supplier returns {@code true}.
 * The supplied {@code endpoints} are the handlers to register (already filtered
 * by the caller for the {@code /mcp} and {@code /rpc} toggles), which keeps this
 * manager free of any editor/command coupling and easy to unit-test.
 *
 * <p>All lifecycle methods are synchronized; callers may invoke them from the
 * EDT (editor startup, Preferences apply).
 */
public final class IpcServerManager {

    /** An HTTP handler to mount at {@code path} when the server starts. */
    /**
     * @param onStop released when the server stops, so an endpoint can close
     *               the sessions it opened; may be null
     */
    public record Endpoint(String path, HttpHandler handler, Runnable onStop) {
        public Endpoint(String path, HttpHandler handler) {
            this(path, handler, null);
        }
    }

    private final Supplier<Boolean> enabled;
    private final Supplier<Integer> preferredPort;
    private final Supplier<List<Endpoint>> endpoints;

    private EditorHttpServer server;

    private List<Endpoint> active = List.of();

    private java.util.function.Function<String, java.util.Optional<com.dogsbay.agent.session.AgentSession>> sessionResolver;
    private DiscoveryFile discoveryFile;
    private boolean running;

    /**
     * @param enabled       whether the server should run (read fresh on each start)
     * @param preferredPort the preferred port, or {@code 0}/{@code null} for auto
     * @param endpoints     the endpoints to register, built fresh on each start
     */
    public IpcServerManager(Supplier<Boolean> enabled, Supplier<Integer> preferredPort,
            Supplier<List<Endpoint>> endpoints) {
        this.enabled = enabled;
        this.preferredPort = preferredPort;
        this.endpoints = endpoints;
    }

    /**
     * Resolve per-session bearer tokens (see {@link EditorHttpServer#setSessionResolver}).
     * Applied to the running server and to every later start.
     */
    public synchronized void setSessionResolver(
            java.util.function.Function<String, java.util.Optional<com.dogsbay.agent.session.AgentSession>> resolver) {
        this.sessionResolver = resolver;
        if (server != null) {
            server.setSessionResolver(resolver);
        }
    }

    /**
     * Start the server if it is enabled and not already running. Best-effort:
     * a bind/start failure is logged and leaves the manager stopped (the editor
     * works without the server).
     */
    public synchronized void start() {
        if (running) {
            return;
        }
        if (!Boolean.TRUE.equals(enabled.get())) {
            return;
        }
        try {
            discoveryFile = new DiscoveryFile();
            Integer p = preferredPort.get();
            server = new EditorHttpServer(discoveryFile.getAuthToken(), p == null ? 0 : p);
            if (sessionResolver != null) {
                server.setSessionResolver(sessionResolver);
            }
            active = endpoints.get();
            for (Endpoint e : active) {
                server.addContext(e.path(), e.handler());
            }
            server.start();
            discoveryFile.write(server.getPort(), null);
            running = true;
        } catch (Exception e) {
            System.err.println("[HTTP] Failed to start integration server: " + e.getMessage());
            cleanup();
        }
    }

    /** Stop the server (if running) and remove its discovery file. */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        cleanup();
    }

    private void cleanup() {
        for (Endpoint e : active) {
            if (e.onStop() != null) {
                try {
                    e.onStop().run();
                } catch (RuntimeException ignore) {
                    // an endpoint's cleanup must not block the server's
                }
            }
        }
        active = List.of();
        if (server != null) {
            try {
                server.stop();
            } catch (Exception ignore) {
                // already stopped / never fully started
            }
            server = null;
        }
        if (discoveryFile != null) {
            discoveryFile.delete();
            discoveryFile = null;
        }
        running = false;
    }

    /** Apply changed configuration by stopping and (if enabled) starting again. */
    public synchronized void restart() {
        stop();
        start();
    }

    public synchronized boolean isRunning() {
        return running;
    }

    /** The bound port while running, or {@code -1} when stopped. */
    public synchronized int getPort() {
        return running && server != null ? server.getPort() : -1;
    }

    /**
     * The persistent bearer token, available even when the server is stopped
     * (so the Preferences UI can show the install command before enabling it).
     */
    public synchronized String getAuthToken() {
        if (running && discoveryFile != null) {
            return discoveryFile.getAuthToken();
        }
        return new DiscoveryFile().getAuthToken();
    }
}
