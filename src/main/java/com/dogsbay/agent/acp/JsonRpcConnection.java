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
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Bidirectional JSON-RPC 2.0 over newline-delimited JSON, as ACP speaks it
 * on an agent's stdin and stdout. Both sides send requests: ours get
 * futures, theirs go to {@link RequestHandler}; notifications go to
 * {@link NotificationHandler}. One reader thread; writes are serialised.
 */
public final class JsonRpcConnection implements AutoCloseable {

    /** Thrown for a JSON-RPC error response, and for a handler's refusal. */
    public static final class RpcException extends Exception {
        private final int code;

        public RpcException(int code, String message) {
            super(message);
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    /** Serves a request from the peer. */
    public interface RequestHandler {
        JsonNode handle(String method, JsonNode params) throws RpcException;
    }

    public interface NotificationHandler {
        void notify(String method, JsonNode params);
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final InputStream rawIn;
    private final BufferedReader in;
    private final OutputStream out;
    private final RequestHandler requests;
    private final NotificationHandler notifications;
    private final BiConsumer<String, Throwable> onFailure;
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private final Object writeLock = new Object();
    private final Thread reader;
    private volatile boolean closed;

    public JsonRpcConnection(InputStream input, OutputStream output, RequestHandler requests,
            NotificationHandler notifications, BiConsumer<String, Throwable> onFailure) {
        this.rawIn = input;
        this.in = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.out = output;
        this.requests = requests;
        this.notifications = notifications;
        this.onFailure = onFailure;
        this.reader = Thread.ofVirtual().name("acp-reader").unstarted(this::readLoop);
    }

    public void start() {
        reader.start();
    }

    /** Send a request and wait for its result. */
    public JsonNode request(String method, JsonNode params, long timeout, TimeUnit unit)
            throws RpcException, IOException, TimeoutException {
        long id = ids.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        ObjectNode msg = JSON.createObjectNode();
        msg.put("jsonrpc", "2.0");
        msg.put("id", id);
        msg.put("method", method);
        if (params != null) {
            msg.set("params", params);
        }
        try {
            write(msg);
            return future.get(timeout, unit);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RpcException r) {
                throw r;
            }
            throw new IOException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        } finally {
            pending.remove(id);
        }
    }

    public void notify(String method, JsonNode params) throws IOException {
        ObjectNode msg = JSON.createObjectNode();
        msg.put("jsonrpc", "2.0");
        msg.put("method", method);
        if (params != null) {
            msg.set("params", params);
        }
        write(msg);
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * Stop and release. Never touches the {@link BufferedReader}: its
     * {@code close} takes the monitor the reader thread holds while blocked
     * in {@code readLine}, and a process pipe read does not respond to
     * interrupt, so that deadlocks. The raw stream is closed instead, and the
     * owner is expected to end the process first, whose EOF is what actually
     * frees the reader.
     */
    @Override
    public void close() {
        closed = true;
        reader.interrupt();
        try {
            out.close();
        } catch (IOException ignore) {
            // closing
        }
        try {
            rawIn.close();
        } catch (IOException ignore) {
            // closing
        }
        IOException gone = new IOException("connection closed");
        pending.values().forEach(f -> f.completeExceptionally(gone));
        pending.clear();
    }

    private void write(JsonNode msg) throws IOException {
        byte[] bytes = (JSON.writeValueAsString(msg) + "\n").getBytes(StandardCharsets.UTF_8);
        synchronized (writeLock) {
            out.write(bytes);
            out.flush();
        }
    }

    private void readLoop() {
        try {
            String line;
            while (!closed && (line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode msg;
                try {
                    msg = JSON.readTree(line);
                } catch (IOException e) {
                    onFailure.accept("unparseable line from agent: " + abbreviate(line), e);
                    continue;
                }
                dispatch(msg);
            }
            if (!closed) {
                // Clean EOF: the agent exited between prompts. Say so, or an
                // idle client would only learn at its next write.
                onFailure.accept("agent closed the connection", null);
            }
        } catch (IOException e) {
            if (!closed) {
                onFailure.accept("agent connection lost", e);
            }
        } finally {
            if (!closed) {
                close();
            }
        }
    }

    private void dispatch(JsonNode msg) {
        boolean hasMethod = msg.hasNonNull("method");
        boolean hasId = msg.hasNonNull("id");
        if (hasMethod && hasId) {
            // Their request: serve on its own thread so a slow permission prompt
            // does not stall the stream.
            Thread.ofVirtual().name("acp-request").start(() -> serve(msg));
        } else if (hasMethod) {
            try {
                notifications.notify(msg.get("method").asText(), msg.get("params"));
            } catch (RuntimeException e) {
                onFailure.accept("notification handler failed", e);
            }
        } else if (hasId) {
            CompletableFuture<JsonNode> future = pending.remove(msg.get("id").asLong());
            if (future == null) {
                return;
            }
            if (msg.has("error")) {
                JsonNode err = msg.get("error");
                // A codeless error is an internal error, never mistaken for auth_required (-32000).
                future.completeExceptionally(new RpcException(err.path("code").asInt(-32603),
                        err.path("message").asText("agent error")));
            } else {
                future.complete(msg.get("result"));
            }
        }
    }

    private void serve(JsonNode msg) {
        ObjectNode reply = JSON.createObjectNode();
        reply.put("jsonrpc", "2.0");
        reply.set("id", msg.get("id"));
        try {
            JsonNode result = requests.handle(msg.get("method").asText(), msg.get("params"));
            reply.set("result", result == null ? JSON.createObjectNode() : result);
        } catch (RpcException e) {
            reply.putObject("error").put("code", e.code()).put("message", e.getMessage());
        } catch (RuntimeException e) {
            reply.putObject("error").put("code", -32603).put("message", String.valueOf(e.getMessage()));
        }
        try {
            write(reply);
        } catch (IOException e) {
            onFailure.accept("could not answer agent request", e);
        }
    }

    private static String abbreviate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
