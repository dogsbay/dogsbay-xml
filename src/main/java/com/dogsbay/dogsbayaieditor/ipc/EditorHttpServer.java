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

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

import com.dogsbay.agent.session.AgentSession;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Lightweight embedded HTTP server bound to localhost.
 * Serves MCP (Phase 3) at /mcp and REST API (Phase 4) at /api.
 * Uses JDK built-in com.sun.net.httpserver — zero external dependencies.
 */
public class EditorHttpServer {

    /**
     * The session the request being handled on this thread authenticated as.
     * {@code HttpExchange} attributes are shared per context, not per request,
     * so they cannot carry it; the server runs one virtual thread per request.
     */
    private static final ThreadLocal<AgentSession> REQUEST_SESSION = new ThreadLocal<>();

    private final HttpServer server;
    private final int port;
    private final String authToken;
    private volatile Function<String, Optional<AgentSession>> sessionResolver = t -> Optional.empty();

    public EditorHttpServer(String authToken) throws IOException {
        this(authToken, 0);
    }

    /**
     * @param authToken     bearer token required on every request
     * @param preferredPort the port to bind, or {@code 0} to pick the first free
     *                      port in 19601&ndash;19699 automatically. When a
     *                      preferred port is given but unavailable, the range is
     *                      tried, then the OS picks a free port.
     */
    public EditorHttpServer(String authToken, int preferredPort) throws IOException {
        this.authToken = authToken;
        this.port = selectPort(preferredPort);
        this.server = HttpServer.create(
            new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0
        );
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        // Health check endpoint
        server.createContext("/status", exchange -> {
            if (!checkOrigin(exchange)) return;
            if (!checkAuth(exchange)) return;
            sendJson(exchange, 200, """
                {"status":"ok","version":"%s"}
                """.formatted(com.dogsbay.dogsbayaieditor.Identity.getIdentity().getVersion()));
        });
    }

    /**
     * Register an HTTP handler at a path. Used by MCP and REST to mount themselves.
     */
    public void addContext(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            if (!checkOrigin(exchange)) return;
            Optional<AgentSession> session = authenticate(exchange);
            if (session == null) return;
            REQUEST_SESSION.set(session.orElse(null));
            try {
                handler.handle(exchange);
            } finally {
                REQUEST_SESSION.remove();
            }
        });
    }

    public void start() {
        server.start();
        System.out.println("[HTTP] Server started on localhost:" + port);
    }

    public void stop() {
        server.stop(1);
        System.out.println("[HTTP] Server stopped");
    }

    public int getPort() {
        return port;
    }

    /**
     * Accept per-session bearer tokens in addition to the master token. A
     * request that authenticates with a session token carries the resolved
     * session as the {@link #SESSION_ATTRIBUTE} exchange attribute; one that
     * uses the master token carries none.
     */
    public void setSessionResolver(Function<String, Optional<AgentSession>> resolver) {
        this.sessionResolver = resolver == null ? t -> Optional.empty() : resolver;
    }

    /**
     * The session the request authenticated as, if it used a session token.
     * Valid only on the thread handling {@code exchange}, inside the handler.
     */
    public static Optional<AgentSession> sessionOf(HttpExchange exchange) {
        return Optional.ofNullable(REQUEST_SESSION.get());
    }

    public String getAuthToken() {
        return authToken;
    }

    /**
     * Check Bearer token auth. Sends 401 and returns false if unauthorized.
     * The token comparison is constant-time to avoid leaking it via timing.
     */
    private boolean checkAuth(HttpExchange exchange) throws IOException {
        return authenticate(exchange) != null;
    }

    /**
     * @return empty for the master token, the session for a session token, or
     *         {@code null} after a 401 has been sent
     */
    private Optional<AgentSession> authenticate(HttpExchange exchange) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        String expected = "Bearer " + authToken;
        if (auth != null && constantTimeEquals(auth, expected)) {
            return Optional.empty();
        }
        if (auth != null && auth.startsWith("Bearer ")) {
            // Session tokens are random, unguessable, and looked up by exact
            // key; the map lookup leaks nothing useful about other tokens.
            Optional<AgentSession> session = sessionResolver.apply(auth.substring("Bearer ".length()));
            if (session.isPresent()) {
                return session;
            }
        }
        sendJson(exchange, 401, """
            {"error":"Unauthorized","message":"Missing or invalid Authorization header"}
            """);
        return null;
    }

    /**
     * Reject requests whose {@code Host} isn't a loopback name and whose
     * {@code Origin} (when present) isn't null/loopback. Defense-in-depth against
     * DNS-rebinding: even if the token leaks into a browser context, a rebound
     * page can't drive this API. Sends 403 and returns false on rejection.
     */
    private boolean checkOrigin(HttpExchange exchange) throws IOException {
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host != null && !isLoopbackHost(host)) {
            sendJson(exchange, 403, """
                {"error":"Forbidden","message":"Invalid Host header"}
                """);
            return false;
        }

        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.equalsIgnoreCase("null") && !isLoopbackOrigin(origin)) {
            sendJson(exchange, 403, """
                {"error":"Forbidden","message":"Cross-origin request rejected"}
                """);
            return false;
        }
        return true;
    }

    static boolean isLoopbackHost(String host) {
        String h = host.trim();
        if (h.startsWith("[")) {
            // Bracketed IPv6 literal, optionally followed by :port — e.g. [::1]:19601.
            int close = h.indexOf(']');
            if (close > 0) {
                h = h.substring(1, close);
            }
        } else {
            // Hostname or IPv4, optionally followed by a single :port.
            int colon = h.indexOf(':');
            if (colon >= 0) {
                h = h.substring(0, colon);
            }
        }
        return h.equalsIgnoreCase("localhost")
            || h.equals("127.0.0.1")
            || h.equals("::1")
            || h.equals("0:0:0:0:0:0:0:1");
    }

    private boolean isLoopbackOrigin(String origin) {
        return origin.startsWith("http://localhost")
            || origin.startsWith("https://localhost")
            || origin.startsWith("http://127.0.0.1")
            || origin.startsWith("https://127.0.0.1")
            || origin.startsWith("http://[::1]")
            || origin.startsWith("https://[::1]");
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Send a JSON response.
     */
    public static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Choose the port to bind: the preferred port if free, otherwise the
     * 19601&ndash;19699 range, otherwise an OS-assigned free port.
     */
    private int selectPort(int preferredPort) throws IOException {
        if (preferredPort > 0 && isPortFree(preferredPort)) {
            return preferredPort;
        }
        return findAvailablePort(19601, 19699);
    }

    private boolean isPortFree(int port) {
        try (ServerSocket ss = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private int findAvailablePort(int start, int end) throws IOException {
        for (int port = start; port <= end; port++) {
            try (ServerSocket ss = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
                return port;
            } catch (IOException e) {
                // port in use, try next
            }
        }
        // Fallback: let OS pick
        try (ServerSocket ss = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return ss.getLocalPort();
        }
    }
}
