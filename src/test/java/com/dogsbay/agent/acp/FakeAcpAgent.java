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
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A scripted ACP agent on the other end of two pipes. Records what the
 * client sent, answers requests through {@link #on}, and can send its own
 * requests and notifications to the client.
 */
final class FakeAcpAgent implements AutoCloseable {

    static final ObjectMapper JSON = new ObjectMapper();

    // NIO pipes, not java.io.Piped*: those throw "write end dead" as soon as a
    // short-lived writer thread exits, and the client answers agent requests
    // on per-request virtual threads.
    private final Pipe agentToClient = Pipe.open();
    private final Pipe clientToAgent = Pipe.open();
    final InputStream clientReads = Channels.newInputStream(agentToClient.source());
    final OutputStream clientWrites = Channels.newOutputStream(clientToAgent.sink());
    private final OutputStream toClient = Channels.newOutputStream(agentToClient.sink());
    private final InputStream fromClient = Channels.newInputStream(clientToAgent.source());

    final List<JsonNode> received = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, Function<JsonNode, JsonNode>> handlers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final Thread reader;
    private long nextId = 1000;

    FakeAcpAgent() throws IOException {
        reader = Thread.ofVirtual().name("fake-agent").start(this::readLoop);
    }

    /** Wait until the client's message for {@code method} has arrived. */
    List<JsonNode> awaitRequests(String method, int count) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (requestsFor(method).size() < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        return requestsFor(method);
    }

    /** Answer {@code method} with the handler's result (params in). */
    FakeAcpAgent on(String method, Function<JsonNode, JsonNode> handler) {
        handlers.put(method, handler);
        return this;
    }

    void send(String method, JsonNode params) throws IOException {
        ObjectNode n = JSON.createObjectNode();
        n.put("jsonrpc", "2.0");
        n.put("method", method);
        n.set("params", params);
        write(n);
    }

    /** Send a request to the client and wait for the result. */
    JsonNode request(String method, JsonNode params) throws Exception {
        long id;
        synchronized (this) {
            id = nextId++;
        }
        CompletableFuture<JsonNode> f = new CompletableFuture<>();
        pending.put(id, f);
        ObjectNode n = JSON.createObjectNode();
        n.put("jsonrpc", "2.0");
        n.put("id", id);
        n.put("method", method);
        n.set("params", params);
        write(n);
        return f.get(10, TimeUnit.SECONDS);
    }

    void update(String sessionId, ObjectNode update) throws IOException {
        ObjectNode p = JSON.createObjectNode();
        p.put("sessionId", sessionId);
        p.set("update", update);
        send("session/update", p);
    }

    static ObjectNode textChunk(String kind, String text) {
        ObjectNode u = JSON.createObjectNode();
        u.put("sessionUpdate", kind);
        u.putObject("content").put("type", "text").put("text", text);
        return u;
    }

    List<JsonNode> requestsFor(String method) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode r : received) {
            if (method.equals(r.path("method").asText())) {
                out.add(r);
            }
        }
        return out;
    }

    private synchronized void write(JsonNode n) throws IOException {
        toClient.write((JSON.writeValueAsString(n) + "\n").getBytes(StandardCharsets.UTF_8));
        toClient.flush();
    }

    private void readLoop() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(fromClient, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                JsonNode msg = JSON.readTree(line);
                received.add(msg);
                if (msg.hasNonNull("method") && msg.hasNonNull("id")) {
                    Function<JsonNode, JsonNode> h = handlers.get(msg.get("method").asText());
                    ObjectNode reply = JSON.createObjectNode();
                    reply.put("jsonrpc", "2.0");
                    reply.set("id", msg.get("id"));
                    if (h == null) {
                        reply.putObject("error").put("code", -32601).put("message", "unscripted: " + msg.get("method"));
                    } else {
                        try {
                            reply.set("result", h.apply(msg.get("params")));
                        } catch (RuntimeException e) {
                            // a handler failure is an internal error; auth_required (-32000) is scripted explicitly
                            reply.putObject("error").put("code", -32603).put("message", String.valueOf(e.getMessage()));
                        }
                    }
                    write(reply);
                } else if (msg.hasNonNull("id")) {
                    CompletableFuture<JsonNode> f = pending.remove(msg.get("id").asLong());
                    if (f != null) {
                        if (msg.has("error")) {
                            f.completeExceptionally(new RuntimeException(msg.get("error").toString()));
                        } else {
                            f.complete(msg.get("result"));
                        }
                    }
                }
            }
        } catch (IOException ignore) {
            // closed
        }
    }

    /** The agent process died: close our end so the client sees EOF. */
    void crash() throws IOException {
        agentToClient.sink().close();
    }

    @Override
    public void close() throws IOException {
        agentToClient.sink().close();
        clientToAgent.source().close();
        reader.interrupt();
    }
}
