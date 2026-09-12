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
import com.xagent.tool.AgentTool;
import com.xagent.tool.AgentToolResult;
import com.xagent.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private McpServer server;
	private AtomicReference<JsonNode> lastArgs;

	@BeforeEach
	void setUp() {
		lastArgs = new AtomicReference<>();
		var registry = new ToolRegistry();
		registry.register(new AgentTool() {
			@Override
			public String name() {
				return "echo";
			}

			@Override
			public String description() {
				return "Echoes back the input";
			}

			@Override
			public JsonNode parametersSchema() {
				var schema = MAPPER.createObjectNode();
				schema.put("type", "object");
				schema.putObject("properties").putObject("text").put("type", "string");
				return schema;
			}

			@Override
			public AgentToolResult execute(String toolCallId, JsonNode params,
				Supplier<Boolean> isCancelled, Consumer<AgentToolResult> onUpdate) {
				lastArgs.set(params);
				String text = params.path("text").asText("");
				if ("fail".equals(text)) {
					return AgentToolResult.error("failed: " + text);
				}
				return AgentToolResult.success("echo: " + text);
			}
		});
		server = new McpServer(registry, "xagent", "1.2.3");
	}

	private JsonNode respond(String request) throws IOException {
		var response = server.handleLine(request);
		assertThat(response).isPresent();
		return MAPPER.readTree(response.get());
	}

	@Test
	void initializeEchoesProtocolVersionAndServerInfo() throws IOException {
		var response = respond("""
			{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{}}}
			""".strip());

		assertThat(response.get("id").asInt()).isEqualTo(1);
		var result = response.get("result");
		assertThat(result.get("protocolVersion").asText()).isEqualTo("2025-03-26");
		assertThat(result.get("serverInfo").get("name").asText()).isEqualTo("xagent");
		assertThat(result.get("serverInfo").get("version").asText()).isEqualTo("1.2.3");
		assertThat(result.get("capabilities").has("tools")).isTrue();
	}

	@Test
	void notificationsGetNoResponse() {
		var response = server.handleLine(
			"{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
		assertThat(response).isEmpty();
	}

	@Test
	void toolsListExposesToolWithInputSchema() throws IOException {
		var response = respond("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");

		var tools = response.get("result").get("tools");
		assertThat(tools.size()).isEqualTo(1);
		assertThat(tools.get(0).get("name").asText()).isEqualTo("echo");
		assertThat(tools.get(0).get("description").asText()).isNotEmpty();
		assertThat(tools.get(0).get("inputSchema").get("type").asText()).isEqualTo("object");
	}

	@Test
	void toolsCallExecutesAndReturnsTextContent() throws IOException {
		var response = respond("""
			{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"text":"hi"}}}
			""".strip());

		var result = response.get("result");
		assertThat(result.get("isError").asBoolean()).isFalse();
		assertThat(result.get("content").get(0).get("type").asText()).isEqualTo("text");
		assertThat(result.get("content").get(0).get("text").asText()).isEqualTo("echo: hi");
		assertThat(lastArgs.get().get("text").asText()).isEqualTo("hi");
	}

	@Test
	void toolsCallReportsToolErrors() throws IOException {
		var response = respond("""
			{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"echo","arguments":{"text":"fail"}}}
			""".strip());

		var result = response.get("result");
		assertThat(result.get("isError").asBoolean()).isTrue();
		assertThat(result.get("content").get(0).get("text").asText()).contains("failed");
	}

	@Test
	void unknownToolIsInvalidParams() throws IOException {
		var response = respond("""
			{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"nope","arguments":{}}}
			""".strip());

		assertThat(response.get("error").get("code").asInt()).isEqualTo(-32602);
	}

	@Test
	void unknownMethodIsMethodNotFound() throws IOException {
		var response = respond("{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"bogus/method\"}");
		assertThat(response.get("error").get("code").asInt()).isEqualTo(-32601);
	}

	@Test
	void parseErrorIsReported() throws IOException {
		var response = respond("this is not json");
		assertThat(response.get("error").get("code").asInt()).isEqualTo(-32700);
	}

	@Test
	void runLoopServesNewlineDelimitedRequests() throws IOException {
		String session = """
			{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
			{"jsonrpc":"2.0","method":"notifications/initialized"}
			{"jsonrpc":"2.0","id":2,"method":"tools/list"}
			""";
		var in = new ByteArrayInputStream(session.getBytes(StandardCharsets.UTF_8));
		var out = new ByteArrayOutputStream();

		server.run(in, out);

		String[] lines = out.toString(StandardCharsets.UTF_8).strip().split("\n");
		assertThat(lines).hasSize(2);
		assertThat(MAPPER.readTree(lines[0]).get("id").asInt()).isEqualTo(1);
		assertThat(MAPPER.readTree(lines[1]).get("id").asInt()).isEqualTo(2);
	}

	@Test
	void mcpServerToolsetExcludesGeneralFileTools() {
		var registry = ToolRegistry.createXmlToolset(java.nio.file.Path.of("/tmp"));
		var names = registry.all().stream().map(AgentTool::name).toList();
		assertThat(names).containsExactly("xml_validate", "xpath", "xml_format", "xslt", "dita_build");
		assertThat(registry.get("bash")).isNull();
		assertThat(registry.get("write")).isNull();
	}
}
