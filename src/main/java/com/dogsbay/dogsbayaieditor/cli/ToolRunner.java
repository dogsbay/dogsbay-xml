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

package com.dogsbay.dogsbayaieditor.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.dogsbay.dogsbayaieditor.commands.CommandException;
import com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor;
import com.dogsbay.dogsbayaieditor.commands.UnsavedInEditorGuard;
import com.dogsbay.dogsbayaieditor.commands.results.CommandResult;
import com.dogsbay.dogsbayaieditor.ipc.JsonRpcHandler;
import com.dogsbay.dogsbayaieditor.mcp.McpServer;
import com.dogsbay.dogsbayaieditor.mcp.McpServer.McpTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Runs the editor's MCP tools from the command line: the same catalog, argument checks and command engine
 * as the MCP servers, as the person at the keyboard (no read-before-edit, no review marks). Without the
 * editor on files, or through the running editor.
 */
final class ToolRunner {

    static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private ToolRunner() {
    }

    /** The editor's tool catalog. */
    static List<McpTool> catalog() {
        return new McpServer(new HeadlessExecutor()).getTools();
    }

    static McpTool find(String name) {
        List<McpTool> tools = catalog();
        for (McpTool t : tools) {
            if (t.name().equals(name)) {
                return t;
            }
        }
        String near = tools.stream().map(McpTool::name).filter(n -> n.contains(name) || name.contains(n))
                .findFirst().orElse(null);
        throw new IllegalArgumentException("No tool named " + name + (near != null ? "; did you mean " + near + "?"
                : "; dogsbay-xml tool --list lists them"));
    }

    /** Arguments given as JSON, {@code @file} or {@code -} (standard input); none is {@code {}}. */
    static ObjectNode arguments(String spec) throws IOException {
        if (spec == null || spec.isBlank()) {
            return MAPPER.createObjectNode();
        }
        String json = spec.equals("-") ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8)
                : spec.startsWith("@") ? Files.readString(Path.of(spec.substring(1))) : spec;
        JsonNode node = MAPPER.readTree(json);
        if (!(node instanceof ObjectNode o)) {
            throw new IllegalArgumentException("The arguments must be a JSON object, such as {\"file\": \"a.dita\"}");
        }
        return o;
    }

    /** Run {@code tool}: without the editor, or through it when {@code viaEditor}. */
    static Object run(McpTool tool, ObjectNode args, boolean viaEditor) throws Exception {
        String problem = McpServer.argumentProblem(tool, args);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        if (viaEditor) {
            EditorClient client = EditorClient.connect();
            if (client == null) {
                throw new IllegalStateException("The editor is not running; leave out --editor to run without it");
            }
            // the editor resolves relative paths against its own working directory, not this shell's
            for (String name : List.of("file", "root", "map", "rootMap", "input", "xslt", "output", "schema",
                    "schematron", "template", "folder")) {
                JsonNode v = args.get(name);
                if (v != null && v.isTextual() && !v.asText().isBlank() && !v.asText().equalsIgnoreCase("none")
                        && (!"template".equals(name) || v.asText().contains("/") || v.asText().contains("."))
                        && !Path.of(v.asText()).isAbsolute()) {
                    args.put(name, Path.of(v.asText()).toAbsolutePath().normalize().toString());
                }
            }
            return client.call(tool.rpcMethod(), args);
        }
        if ("live_preview".equals(tool.name()) && "start".equalsIgnoreCase(args.path("action").asText())) {
            // this process ends as soon as the tool returns, and its previews with it
            throw new IllegalStateException("A preview started here would stop when this command ends: use "
                    + "'dogsbay-xml live-preview start' (background) or 'live-preview run', or add --editor");
        }
        if (!tool.worksWithoutEditor()) {
            throw new IllegalStateException(tool.name() + " needs the running editor: start it and add --editor");
        }
        var executor = new UnsavedInEditorGuard(new HeadlessExecutor(true, RunningEditor::holdsUnsaved),
                RunningEditor::unsavedFiles);
        try {
            return new JsonRpcHandler(executor).dispatch(tool.rpcMethod(), args, ReviewCmd.USER);
        } catch (CommandException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * Whether a tool's result reports failure: a command that did not succeed, a check that is not clean, a
     * document that is not valid, so that scripts and CI can go by the exit code.
     */
    static boolean failed(Object result) {
        if (result instanceof CommandResult r) {
            return !r.success();
        }
        JsonNode n = result instanceof JsonNode j ? j : MAPPER.valueToTree(result);
        if (n == null || !n.isObject()) {
            return false;
        }
        return n.has("success") && !n.get("success").asBoolean(true)
                || n.has("clean") && !n.get("clean").asBoolean(true)
                || n.has("valid") && !n.get("valid").asBoolean(true);
    }

    /** A command result's message, or null for other results. */
    static String message(Object result) {
        if (result instanceof CommandResult r) {
            return r.message();
        }
        return result instanceof JsonNode n && n.has("message") && n.has("success") ? n.get("message").asText() : null;
    }
}
