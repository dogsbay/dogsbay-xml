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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.RecordingExecutor;
import com.dogsbay.dogsbayaieditor.mcp.McpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * {@link JsonRpcHandler#commandClassOf} is a hand-written mirror of
 * {@link JsonRpcHandler#dispatch}. This test drives every MCP tool through
 * dispatch with arguments synthesised from its schema and checks the command
 * class that was built is the one the map claims.
 */
class JsonRpcMethodMapTest {

    private static final ObjectMapper M = new ObjectMapper();

    @Test
    void everyToolsMethodBuildsTheCommandClassTheMapSays() throws Exception {
        RecordingExecutor recorder = new RecordingExecutor();
        JsonRpcHandler handler = new JsonRpcHandler(recorder);
        McpServer catalog = new McpServer(new HeadlessExecutor());
        List<String> problems = new ArrayList<>();

        for (McpServer.McpTool tool : catalog.getTools()) {
            recorder.calls.clear();
            try {
                handler.dispatch(tool.rpcMethod(), synthesise(tool.inputSchema()));
            } catch (Exception e) {
                // argument validation may still reject; we only need the command class
            }
            Class<?> expected = JsonRpcHandler.commandClassOf(tool.rpcMethod());
            if (expected == null) {
                problems.add(tool.rpcMethod() + ": not in commandClassOf");
                continue;
            }
            if (recorder.calls.isEmpty()) {
                problems.add(tool.rpcMethod() + ": dispatch built no command from " + synthesise(tool.inputSchema()));
                continue;
            }
            Class<?> actual = recorder.calls.get(0).command().getClass();
            if (actual != expected) {
                problems.add(tool.rpcMethod() + ": map says " + expected.getSimpleName()
                        + " but dispatch built " + actual.getSimpleName());
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void readOnlyFollowsTheMarker() {
        assertThat(JsonRpcHandler.isReadOnly("validate")).isTrue();
        assertThat(JsonRpcHandler.isReadOnly("getContent")).isTrue();
        assertThat(JsonRpcHandler.isReadOnly("setContent")).isFalse();
        assertThat(JsonRpcHandler.isReadOnly("screenshot")).isFalse();
        assertThat(JsonRpcHandler.isReadOnly("close")).isFalse();
        assertThat(JsonRpcHandler.isReadOnly("render-preview")).isFalse();
        assertThat(JsonRpcHandler.isReadOnly("openProject")).isFalse();
        assertThat(JsonRpcHandler.isReadOnly("open")).isTrue();
        assertThat(JsonRpcHandler.isReadOnly("no-such-method")).isFalse();
    }

    /** Fill every schema property with a plausible value of its type. */
    static ObjectNode synthesise(JsonNode schema) {
        ObjectNode args = M.createObjectNode();
        JsonNode props = schema.get("properties");
        if (props == null) {
            return args;
        }
        props.fields().forEachRemaining(e -> {
            String type = e.getValue().path("type").asText("string");
            switch (type) {
                case "integer", "number" -> args.put(e.getKey(), 1);
                case "boolean" -> args.put(e.getKey(), true);
                case "array" -> args.putArray(e.getKey());
                case "object" -> args.putObject(e.getKey());
                default -> args.put(e.getKey(), "x");
            }
        });
        return args;
    }
}
