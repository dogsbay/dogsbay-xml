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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The Agent Client Protocol messages this client sends and receives, as
 * plain records plus the JSON they map to. Protocol v1 first; the few v2
 * differences that matter are noted where they appear.
 *
 * <p>Kept in one file on purpose: when a stable JVM SDK ships, this is the
 * file that gets replaced.
 */
public final class AcpWire {

    public static final ObjectMapper JSON = new ObjectMapper();
    public static final int PROTOCOL_VERSION = 1;

    private AcpWire() {
    }

    // ── initialize ──────────────────────────────────────────────────────

    /** What this client offers the agent. */
    public record ClientCapabilities(boolean readTextFile, boolean writeTextFile) {
        public ObjectNode toJson() {
            ObjectNode n = JSON.createObjectNode();
            ObjectNode fs = n.putObject("fs");
            fs.put("readTextFile", readTextFile);
            fs.put("writeTextFile", writeTextFile);
            n.put("terminal", false);
            return n;
        }
    }

    public record AuthMethod(String id, String name, String description) {}

    /** The agent's answer to {@code initialize}. */
    public record AgentInfo(
            int protocolVersion,
            String name,
            String version,
            List<AuthMethod> authMethods,
            boolean mcpHttp,
            boolean loadSession,
            boolean embeddedContext) {

        /**
         * True when the agent offers a login at all. Whether one is
         * <em>needed</em> is only known when {@code session/new} refuses
         * with {@code auth_required}; an agent already signed in through its
         * own CLI still lists its methods.
         */
        public boolean needsAuth() {
            return !authMethods.isEmpty();
        }

        static AgentInfo from(JsonNode r) {
            List<AuthMethod> methods = new ArrayList<>();
            for (JsonNode m : r.path("authMethods")) {
                methods.add(new AuthMethod(m.path("id").asText(), m.path("name").asText(null),
                        m.path("description").asText(null)));
            }
            JsonNode caps = r.path("agentCapabilities");
            JsonNode info = r.path("agentInfo");
            return new AgentInfo(
                    r.path("protocolVersion").asInt(1),
                    info.path("name").asText(null),
                    info.path("version").asText(null),
                    methods,
                    caps.path("mcpCapabilities").path("http").asBoolean(false),
                    caps.path("loadSession").asBoolean(false),
                    caps.path("promptCapabilities").path("embeddedContext").asBoolean(false));
        }
    }

    // ── session/new ─────────────────────────────────────────────────────

    /** An MCP server the agent should connect to; stdio or HTTP. */
    public sealed interface McpServerConfig {
        String name();
        ObjectNode toJson();

        record Stdio(String name, String command, List<String> args, Map<String, String> env)
                implements McpServerConfig {
            @Override
            public ObjectNode toJson() {
                ObjectNode n = JSON.createObjectNode();
                n.put("type", "stdio");
                n.put("name", name);
                n.put("command", command);
                ArrayNode a = n.putArray("args");
                args.forEach(a::add);
                ArrayNode e = n.putArray("env");
                env.forEach((k, v) -> e.addObject().put("name", k).put("value", v));
                return n;
            }
        }

        record Http(String name, String url, Map<String, String> headers) implements McpServerConfig {
            @Override
            public ObjectNode toJson() {
                ObjectNode n = JSON.createObjectNode();
                n.put("type", "http");
                n.put("name", name);
                n.put("url", url);
                ArrayNode h = n.putArray("headers");
                headers.forEach((k, v) -> h.addObject().put("name", k).put("value", v));
                return n;
            }
        }
    }

    // ── session/prompt ──────────────────────────────────────────────────

    /** Why a turn ended. */
    public enum StopReason {
        END_TURN, MAX_TOKENS, MAX_TURN_REQUESTS, REFUSAL, CANCELLED, OTHER;

        static StopReason from(String s) {
            return switch (s == null ? "" : s) {
                case "end_turn" -> END_TURN;
                case "max_tokens" -> MAX_TOKENS;
                case "max_turn_requests" -> MAX_TURN_REQUESTS;
                case "refusal" -> REFUSAL;
                case "cancelled" -> CANCELLED;
                default -> OTHER;
            };
        }
    }

    // ── session/update ──────────────────────────────────────────────────

    /** One streamed update from the agent. */
    public sealed interface Update {
        record AgentMessage(String text) implements Update {}
        record AgentThought(String text) implements Update {}
        record UserMessage(String text) implements Update {}
        record ToolCall(String id, String title, String kind, String status, JsonNode rawInput,
                List<String> locations) implements Update {}
        record ToolCallUpdate(String id, String title, String kind, String status, JsonNode rawOutput,
                List<String> locations) implements Update {}
        record Plan(List<PlanEntry> entries) implements Update {}
        record Other(String kind, JsonNode raw) implements Update {}
    }

    public record PlanEntry(String content, String priority, String status) {}

    static Update parseUpdate(JsonNode u) {
        String kind = u.path("sessionUpdate").asText("");
        return switch (kind) {
            case "agent_message_chunk" -> new Update.AgentMessage(text(u.path("content")));
            case "agent_thought_chunk" -> new Update.AgentThought(text(u.path("content")));
            case "user_message_chunk" -> new Update.UserMessage(text(u.path("content")));
            case "tool_call" -> new Update.ToolCall(u.path("toolCallId").asText(), u.path("title").asText(null),
                    u.path("kind").asText("other"), u.path("status").asText("pending"), u.get("rawInput"),
                    locations(u));
            case "tool_call_update" -> new Update.ToolCallUpdate(u.path("toolCallId").asText(),
                    u.path("title").asText(null), u.path("kind").asText(null), u.path("status").asText(null),
                    u.get("rawOutput"), locations(u));
            case "plan" -> {
                List<PlanEntry> entries = new ArrayList<>();
                for (JsonNode e : u.path("entries")) {
                    entries.add(new PlanEntry(e.path("content").asText(""), e.path("priority").asText(null),
                            e.path("status").asText(null)));
                }
                yield new Update.Plan(entries);
            }
            default -> new Update.Other(kind, u);
        };
    }

    private static List<String> locations(JsonNode u) {
        List<String> out = new ArrayList<>();
        for (JsonNode l : u.path("locations")) {
            if (l.hasNonNull("path")) {
                out.add(l.get("path").asText());
            }
        }
        return out;
    }

    /** The text of a content block; non-text blocks render as a short marker. */
    static String text(JsonNode content) {
        String type = content.path("type").asText("text");
        return switch (type) {
            case "text" -> content.path("text").asText("");
            case "image" -> "[image]";
            case "audio" -> "[audio]";
            case "resource_link" -> "[" + content.path("uri").asText("resource") + "]";
            case "resource" -> "[resource: " + content.path("resource").path("uri").asText("attached") + "]";
            default -> "";
        };
    }

    // ── session/request_permission ──────────────────────────────────────

    public record PermissionOption(String optionId, String name, String kind) {
        public boolean allows() {
            return kind != null && kind.startsWith("allow");
        }

        public boolean isAlways() {
            return kind != null && kind.endsWith("always");
        }
    }

    /** What the agent wants permission for. */
    public record PermissionRequest(String sessionId, Update.ToolCall toolCall, List<PermissionOption> options) {
        static PermissionRequest from(JsonNode p) {
            JsonNode tc = p.has("toolCall") ? p.get("toolCall") : p.path("subject").path("toolCall");
            Update.ToolCall call = new Update.ToolCall(tc.path("toolCallId").asText(""),
                    tc.path("title").asText(p.path("title").asText(null)), tc.path("kind").asText("other"),
                    tc.path("status").asText("pending"), tc.get("rawInput"), locations(tc));
            List<PermissionOption> options = new ArrayList<>();
            for (JsonNode o : p.path("options")) {
                options.add(new PermissionOption(o.path("optionId").asText(), o.path("name").asText(null),
                        o.path("kind").asText(null)));
            }
            return new PermissionRequest(p.path("sessionId").asText(), call, options);
        }
    }

    /** {@code null} optionId means cancelled. */
    static ObjectNode permissionResult(String optionId) {
        ObjectNode n = JSON.createObjectNode();
        ObjectNode outcome = n.putObject("outcome");
        if (optionId == null) {
            outcome.put("outcome", "cancelled");
        } else {
            outcome.put("outcome", "selected");
            outcome.put("optionId", optionId);
        }
        return n;
    }
}
