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
package com.xagent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xagent.tool.AgentTool;
import com.xagent.tool.ToolRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * MCP (Model Context Protocol) server over stdio: exposes this agent's
 * tools to MCP clients such as Claude Code and Cursor. Messages are
 * newline-delimited JSON-RPC 2.0 per the MCP stdio transport.
 *
 * This is the inverse of {@link McpManager} (the MCP client): instead of
 * consuming external tools, xagent serves its own — typically the XML/DITA
 * toolset, making any MCP-capable agent schema-aware.
 */
public class McpServer {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final String DEFAULT_PROTOCOL_VERSION = "2024-11-05";

	private final ToolRegistry registry;
	private final String serverName;
	private final String serverVersion;

	public McpServer(ToolRegistry registry, String serverName, String serverVersion) {
		this.registry = registry;
		this.serverName = serverName;
		this.serverVersion = serverVersion;
	}

	/**
	 * Serve until EOF on the input stream. The output stream carries only
	 * protocol messages; diagnostics must go elsewhere (stderr).
	 */
	public void run(InputStream in, OutputStream out) throws IOException {
		var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		var writer = new PrintStream(out, true, StandardCharsets.UTF_8);
		String line;
		while ((line = reader.readLine()) != null) {
			if (line.isBlank()) continue;
			handleLine(line).ifPresent(writer::println);
		}
	}

	/**
	 * Handle one JSON-RPC message; returns the serialized response, or
	 * empty for notifications and unparseable input without an id.
	 */
	Optional<String> handleLine(String line) {
		JsonNode message;
		try {
			message = MAPPER.readTree(line);
		} catch (IOException e) {
			return Optional.of(error(null, -32700, "Parse error").toString());
		}

		JsonNode id = message.get("id");
		String method = message.path("method").asText("");

		// notifications (no id) never get a response
		if (id == null || id.isNull()) {
			return Optional.empty();
		}

		ObjectNode response = switch (method) {
			case "initialize" -> initialize(id, message.path("params"));
			case "ping" -> result(id, MAPPER.createObjectNode());
			case "tools/list" -> listTools(id);
			case "tools/call" -> callTool(id, message.path("params"));
			default -> error(id, -32601, "Method not found: " + method);
		};
		return Optional.of(response.toString());
	}

	private ObjectNode initialize(JsonNode id, JsonNode params) {
		ObjectNode result = MAPPER.createObjectNode();
		String requested = params.path("protocolVersion").asText(DEFAULT_PROTOCOL_VERSION);
		result.put("protocolVersion", requested);
		result.putObject("capabilities").putObject("tools");
		ObjectNode serverInfo = result.putObject("serverInfo");
		serverInfo.put("name", serverName);
		serverInfo.put("version", serverVersion);
		return result(id, result);
	}

	private ObjectNode listTools(JsonNode id) {
		ObjectNode result = MAPPER.createObjectNode();
		ArrayNode tools = result.putArray("tools");
		for (AgentTool tool : registry.all()) {
			ObjectNode entry = tools.addObject();
			entry.put("name", tool.name());
			entry.put("description", tool.description());
			entry.set("inputSchema", tool.parametersSchema());
		}
		return result(id, result);
	}

	private ObjectNode callTool(JsonNode id, JsonNode params) {
		String name = params.path("name").asText("");
		AgentTool tool = registry.get(name);
		if (tool == null) {
			return error(id, -32602, "Unknown tool: " + name);
		}
		JsonNode arguments = params.has("arguments") && params.get("arguments").isObject()
			? params.get("arguments")
			: MAPPER.createObjectNode();

		var toolResult = tool.execute("mcp-" + id.asText(), arguments, () -> false, null);

		ObjectNode result = MAPPER.createObjectNode();
		ArrayNode content = result.putArray("content");
		ObjectNode text = content.addObject();
		text.put("type", "text");
		text.put("text", toolResult.content() != null ? toolResult.content() : "");
		result.put("isError", toolResult.isError());
		return result(id, result);
	}

	private static ObjectNode result(JsonNode id, ObjectNode payload) {
		ObjectNode response = MAPPER.createObjectNode();
		response.put("jsonrpc", "2.0");
		response.set("id", id);
		response.set("result", payload);
		return response;
	}

	private static ObjectNode error(JsonNode id, int code, String message) {
		ObjectNode response = MAPPER.createObjectNode();
		response.put("jsonrpc", "2.0");
		if (id != null) {
			response.set("id", id);
		} else {
			response.putNull("id");
		}
		ObjectNode err = response.putObject("error");
		err.put("code", code);
		err.put("message", message);
		return response;
	}
}
