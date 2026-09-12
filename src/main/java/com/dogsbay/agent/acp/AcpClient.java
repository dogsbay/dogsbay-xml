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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.dogsbay.agent.acp.AcpWire.AgentInfo;
import com.dogsbay.agent.acp.AcpWire.ClientCapabilities;
import com.dogsbay.agent.acp.AcpWire.McpServerConfig;
import com.dogsbay.agent.acp.AcpWire.PermissionRequest;
import com.dogsbay.agent.acp.AcpWire.StopReason;
import com.dogsbay.agent.acp.AcpWire.Update;
import com.dogsbay.agent.acp.JsonRpcConnection.RpcException;

/**
 * The client half of the Agent Client Protocol: drives one agent over a
 * {@link JsonRpcConnection} and serves the four requests an agent makes of
 * its client. Transport-agnostic, so tests connect it to a scripted agent
 * over pipes and the editor connects it to a spawned process.
 *
 * <p>What the agent may ask for is decided by the {@link ClientCapabilities}
 * advertised at {@code initialize}, never by the agent: a file method the
 * tier did not advertise is refused even if the agent asks anyway.
 */
public final class AcpClient implements AutoCloseable {

    /** Receives streamed updates and lifecycle events. */
    public interface Listener {
        void onUpdate(String sessionId, Update update);

        default void onFailure(String message, Throwable cause) {
        }

        /** The agent is waiting on a permission answer; the UI should say so. */
        default void onPermissionRequested(PermissionRequest request) {
        }

        /** The permission was answered ({@code optionId} null = cancelled). */
        default void onPermissionAnswered(PermissionRequest request, String optionId) {
        }
    }

    /** Answers permission prompts; returns the chosen optionId, or null to cancel. */
    public interface PermissionPrompter {
        String choose(PermissionRequest request);
    }

    /** Serves the agent's file requests, if the tier allows them. */
    public interface FileSystem {
        String read(String sessionId, Path path, Integer line, Integer limit) throws IOException;

        void write(String sessionId, Path path, String content) throws IOException;
    }

    private static final long TIMEOUT_SECONDS = 60;
    /** A prompt can legitimately run for many minutes. */
    private static final long PROMPT_TIMEOUT_MINUTES = 60;

    private final JsonRpcConnection connection;
    private final Listener listener;
    private final PermissionPrompter permissions;
    private final FileSystem fileSystem;
    private final ClientCapabilities capabilities;
    private volatile AgentInfo agent;

    public AcpClient(InputStream fromAgent, OutputStream toAgent, ClientCapabilities capabilities,
            Listener listener, PermissionPrompter permissions, FileSystem fileSystem) {
        this.capabilities = Objects.requireNonNull(capabilities);
        this.listener = Objects.requireNonNull(listener);
        this.permissions = Objects.requireNonNull(permissions);
        this.fileSystem = fileSystem;
        BiConsumer<String, Throwable> failure = listener::onFailure;
        this.connection = new JsonRpcConnection(fromAgent, toAgent, this::serve, this::notified, failure);
        connection.start();
    }

    // ── requests we send ────────────────────────────────────────────────

    public AgentInfo initialize(String clientName, String clientVersion)
            throws RpcException, IOException, TimeoutException {
        ObjectNode p = AcpWire.JSON.createObjectNode();
        p.put("protocolVersion", AcpWire.PROTOCOL_VERSION);
        p.set("clientCapabilities", capabilities.toJson());
        p.putObject("clientInfo").put("name", clientName).put("version", clientVersion);
        agent = AgentInfo.from(connection.request("initialize", p, TIMEOUT_SECONDS, TimeUnit.SECONDS));
        return agent;
    }

    /** The agent as it described itself at {@code initialize}, or null before that. */
    public AgentInfo agent() {
        return agent;
    }

    /** Run one of the agent's advertised login flows; may open a browser and take minutes. */
    public void authenticate(String methodId) throws RpcException, IOException, TimeoutException {
        ObjectNode p = AcpWire.JSON.createObjectNode();
        p.put("methodId", methodId);
        connection.request("authenticate", p, 10, TimeUnit.MINUTES);
    }

    public String newSession(Path cwd, List<McpServerConfig> mcpServers)
            throws RpcException, IOException, TimeoutException {
        ObjectNode p = AcpWire.JSON.createObjectNode();
        p.put("cwd", cwd.toAbsolutePath().toString());
        ArrayNode servers = p.putArray("mcpServers");
        for (McpServerConfig s : mcpServers) {
            servers.add(s.toJson());
        }
        JsonNode r = connection.request("session/new", p, TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return r.path("sessionId").asText();
    }

    /**
     * Pick up an earlier session. The agent replays its history as
     * {@code session/update} notifications before answering, so the
     * listener sees the old conversation again. Only when
     * {@link AgentInfo#loadSession()} is true.
     */
    public void loadSession(String sessionId, Path cwd, List<McpServerConfig> mcpServers)
            throws RpcException, IOException, TimeoutException {
        ObjectNode p = AcpWire.JSON.createObjectNode();
        p.put("sessionId", sessionId);
        p.put("cwd", cwd.toAbsolutePath().toString());
        ArrayNode servers = p.putArray("mcpServers");
        for (McpServerConfig s : mcpServers) {
            servers.add(s.toJson());
        }
        connection.request("session/load", p, 5, TimeUnit.MINUTES);
    }

    /**
     * Send a prompt and block until the turn ends. Updates stream to the
     * listener meanwhile; call {@link #cancel} from another thread to stop.
     */
    public StopReason prompt(String sessionId, String text) throws RpcException, IOException, TimeoutException {
        return prompt(sessionId, text, List.of());
    }

    /**
     * As {@link #prompt(String, String)} with files attached as embedded
     * resources (the {@code @file} mentions), each with its text inline so
     * the agent needs no file access to read them.
     */
    public StopReason prompt(String sessionId, String text, List<Attachment> attachments)
            throws RpcException, IOException, TimeoutException {
        ObjectNode p = AcpWire.JSON.createObjectNode();
        p.put("sessionId", sessionId);
        ArrayNode blocks = p.putArray("prompt");
        blocks.addObject().put("type", "text").put("text", text);
        for (Attachment a : attachments) {
            ObjectNode block = blocks.addObject();
            block.put("type", "resource");
            ObjectNode resource = block.putObject("resource");
            resource.put("uri", a.path().toUri().toString());
            resource.put("mimeType", a.mimeType());
            resource.put("text", a.text());
        }
        JsonNode r = connection.request("session/prompt", p, PROMPT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        return StopReason.from(r.path("stopReason").asText(null));
    }

    /** A file's text handed to the agent inside the prompt. */
    public record Attachment(Path path, String mimeType, String text) {}

    public void cancel(String sessionId) throws IOException {
        ObjectNode p = AcpWire.JSON.createObjectNode();
        p.put("sessionId", sessionId);
        connection.notify("session/cancel", p);
    }

    public boolean isConnected() {
        return !connection.isClosed();
    }

    /**
     * Release the connection. End the agent process <em>before</em> this:
     * its EOF is what unblocks the reader thread.
     */
    @Override
    public void close() {
        connection.close();
    }

    // ── requests the agent sends ────────────────────────────────────────

    private JsonNode serve(String method, JsonNode params) throws RpcException {
        return switch (method) {
            case "session/request_permission" -> {
                PermissionRequest request = PermissionRequest.from(params);
                listener.onPermissionRequested(request);
                String choice;
                try {
                    choice = permissions.choose(request);
                } catch (RuntimeException e) {
                    choice = null;
                }
                listener.onPermissionAnswered(request, choice);
                yield AcpWire.permissionResult(choice);
            }
            case "fs/read_text_file" -> {
                if (!capabilities.readTextFile() || fileSystem == null) {
                    throw new RpcException(-32601, "this client does not serve fs/read_text_file");
                }
                try {
                    String content = fileSystem.read(params.path("sessionId").asText(),
                            Path.of(params.path("path").asText()),
                            params.hasNonNull("line") ? params.get("line").asInt() : null,
                            params.hasNonNull("limit") ? params.get("limit").asInt() : null);
                    yield AcpWire.JSON.createObjectNode().put("content", content);
                } catch (IOException e) {
                    throw new RpcException(-32000, e.getMessage());
                }
            }
            case "fs/write_text_file" -> {
                if (!capabilities.writeTextFile() || fileSystem == null) {
                    throw new RpcException(-32601, "this client does not serve fs/write_text_file");
                }
                try {
                    fileSystem.write(params.path("sessionId").asText(),
                            Path.of(params.path("path").asText()), params.path("content").asText(""));
                    yield AcpWire.JSON.createObjectNode();
                } catch (IOException e) {
                    throw new RpcException(-32000, e.getMessage());
                }
            }
            default -> {
                if (method.startsWith("terminal/")) {
                    throw new RpcException(-32601, "this client does not provide terminals");
                }
                throw new RpcException(-32601, "Method not found: " + method);
            }
        };
    }

    private void notified(String method, JsonNode params) {
        if ("session/update".equals(method)) {
            listener.onUpdate(params.path("sessionId").asText(), AcpWire.parseUpdate(params.path("update")));
        }
    }
}
