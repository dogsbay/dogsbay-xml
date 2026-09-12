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

package com.dogsbay.dogsbayaieditor.plugin.agent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xagent.tool.AgentTool;
import com.xagent.tool.AgentToolResult;

/**
 * Bridges one editor command (by its rpcMethod) to an xagent {@link AgentTool},
 * reusing the MCP tool catalog's name/description/JSON-schema and executing via
 * the editor's command engine ({@link Dispatcher}, EDT-marshaled). This is the
 * editor-coupled glue; the agent UI/runtime stay in the standalone-clean
 * {@code com.dogsbay.agent} module and only receive {@code List<AgentTool>}.
 *
 * <p>Editor commands resolve path arguments against the editor's process
 * directory, but the agent reasons in <em>project-relative</em> terms (its
 * working directory is the opened project). To bridge that, relative path-like
 * arguments that exist under the project root are rewritten to absolute paths
 * before dispatch — so {@code map:"audacity-guide.ditamap"} or {@code root:"."}
 * just work instead of failing and forcing a retry.
 */
final class CommandAgentTool implements AgentTool {

    /** Executes a command by rpcMethod with JSON arguments (the JSON-RPC path). */
    interface Dispatcher {
        Object dispatch(String rpcMethod, JsonNode args) throws Exception;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;
    private final String description;
    private final JsonNode schema;
    private final String rpcMethod;
    private final boolean readOnly;
    private final Dispatcher dispatcher;
    private final Supplier<Path> workingDir;

    CommandAgentTool(String name, String description, JsonNode schema, String rpcMethod,
            boolean readOnly, Dispatcher dispatcher, Supplier<Path> workingDir) {
        this.name = name;
        this.description = description;
        this.schema = schema;
        this.rpcMethod = rpcMethod;
        this.readOnly = readOnly;
        this.dispatcher = dispatcher;
        this.workingDir = workingDir;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public JsonNode parametersSchema() {
        return schema;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public AgentToolResult execute(String toolCallId, JsonNode params,
            Supplier<Boolean> isCancelled, Consumer<AgentToolResult> onUpdate) {
        try {
            JsonNode args = params != null ? params : MAPPER.createObjectNode();
            Object result = dispatcher.dispatch(rpcMethod, resolveRelativePaths(args));
            return AgentToolResult.success(MAPPER.writeValueAsString(result));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.toString();
            return AgentToolResult.error("Error: " + msg);
        }
    }

    /**
     * Rewrite relative, path-like string arguments to absolute paths under the
     * project root when they exist — so the agent can pass project-relative paths
     * to editor commands. Non-paths (XPath, numbers, globs that don't resolve)
     * are left untouched.
     */
    private JsonNode resolveRelativePaths(JsonNode args) {
        if (workingDir == null || args == null || !args.isObject()) {
            return args;
        }
        Path base;
        try {
            base = workingDir.get();
        } catch (Exception e) {
            return args;
        }
        if (base == null) {
            return args;
        }
        ObjectNode copy = ((ObjectNode) args).deepCopy();
        List<String> fields = new ArrayList<>();
        copy.fieldNames().forEachRemaining(fields::add);
        for (String f : fields) {
            JsonNode v = copy.get(f);
            if (v != null && v.isTextual()) {
                String abs = absolutize(base, v.asText());
                if (abs != null) {
                    copy.put(f, abs);
                }
            }
        }
        return copy;
    }

    private static String absolutize(Path base, String value) {
        if (!looksLikePath(value)) {
            return null;
        }
        try {
            Path p = Path.of(value);
            if (p.isAbsolute()) {
                return null;
            }
            Path resolved = base.resolve(value).normalize();
            return Files.exists(resolved) ? resolved.toString() : null;
        } catch (Exception e) {
            return null;   // not a valid path string (e.g. an XPath that slipped through)
        }
    }

    private static boolean looksLikePath(String s) {
        if (s == null || s.isBlank() || s.startsWith("/")) {
            return false;                                  // empty or already absolute
        }
        if (s.contains("//") || s.contains("[") || s.contains("@")) {
            return false;                                  // XPath-ish, never a path
        }
        return s.equals(".") || s.startsWith("./") || s.startsWith("../")
                || s.contains("/")
                || s.matches(".*\\.(dita|ditamap|xml|xsl|xslt|ditaval|dtd|xsd|rng|rnc|sch)$");
    }
}
