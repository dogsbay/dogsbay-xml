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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xagent.tool.AgentToolResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.model.chat.request.json.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolAdapterTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void nameIsPrefixedWithServerName() {
		var spec = ToolSpecification.builder()
			.name("list_files")
			.description("List files in a directory")
			.build();

		var adapter = new McpToolAdapter("filesystem", new StubMcpClient(), spec);
		assertThat(adapter.name()).isEqualTo("filesystem.list_files");
	}

	@Test
	void descriptionIncludesServerPrefix() {
		var spec = ToolSpecification.builder()
			.name("search")
			.description("Search for files")
			.build();

		var adapter = new McpToolAdapter("fs", new StubMcpClient(), spec);
		assertThat(adapter.description()).isEqualTo("[fs] Search for files");
	}

	@Test
	void descriptionHandlesNullDescription() {
		var spec = ToolSpecification.builder()
			.name("tool")
			.build();

		var adapter = new McpToolAdapter("srv", new StubMcpClient(), spec);
		assertThat(adapter.description()).isEqualTo("[srv] ");
	}

	@Test
	void parametersSchemaConvertedCorrectly() {
		var params = JsonObjectSchema.builder()
			.addStringProperty("path", "File path")
			.addIntegerProperty("depth", "Search depth")
			.addBooleanProperty("recursive", "Recurse into subdirs")
			.required("path")
			.build();

		var spec = ToolSpecification.builder()
			.name("search")
			.description("Search")
			.parameters(params)
			.build();

		var adapter = new McpToolAdapter("fs", new StubMcpClient(), spec);
		JsonNode schema = adapter.parametersSchema();

		assertThat(schema.get("type").asText()).isEqualTo("object");
		assertThat(schema.get("properties").get("path").get("type").asText()).isEqualTo("string");
		assertThat(schema.get("properties").get("depth").get("type").asText()).isEqualTo("integer");
		assertThat(schema.get("properties").get("recursive").get("type").asText()).isEqualTo("boolean");
		assertThat(schema.get("required").get(0).asText()).isEqualTo("path");
	}

	@Test
	void executeReturnsSuccessResult() throws Exception {
		var client = new StubMcpClient();
		client.setExecuteResult(ToolExecutionResult.builder()
			.resultText("file1.txt\nfile2.txt").isError(false).build());

		var spec = ToolSpecification.builder()
			.name("list_files")
			.description("List files")
			.build();

		var adapter = new McpToolAdapter("fs", client, spec);
		ObjectNode params = MAPPER.createObjectNode();
		params.put("path", "/home");

		AgentToolResult result = adapter.execute("call-1", params, () -> false, update -> {});
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).isEqualTo("file1.txt\nfile2.txt");
	}

	@Test
	void executeReturnsErrorResult() throws Exception {
		var client = new StubMcpClient();
		client.setExecuteResult(ToolExecutionResult.builder()
			.resultText("Permission denied").isError(true).build());

		var spec = ToolSpecification.builder()
			.name("read_file")
			.description("Read file")
			.build();

		var adapter = new McpToolAdapter("fs", client, spec);
		ObjectNode params = MAPPER.createObjectNode();

		AgentToolResult result = adapter.execute("call-2", params, () -> false, update -> {});
		assertThat(result.isError()).isTrue();
		assertThat(result.content()).isEqualTo("Permission denied");
	}

	@Test
	void executeCatchesExceptionAndReturnsError() throws Exception {
		var client = new StubMcpClient();
		client.setThrowOnExecute(new RuntimeException("Connection lost"));

		var spec = ToolSpecification.builder()
			.name("broken_tool")
			.description("Broken")
			.build();

		var adapter = new McpToolAdapter("srv", client, spec);
		ObjectNode params = MAPPER.createObjectNode();

		AgentToolResult result = adapter.execute("call-3", params, () -> false, update -> {});
		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("Connection lost");
		assertThat(result.content()).contains("srv.broken_tool");
	}

	@Test
	void executeCancelledBeforeExecution() throws Exception {
		var client = new StubMcpClient();

		var spec = ToolSpecification.builder()
			.name("tool")
			.description("Tool")
			.build();

		var adapter = new McpToolAdapter("srv", client, spec);
		ObjectNode params = MAPPER.createObjectNode();

		AgentToolResult result = adapter.execute("call-4", params, () -> true, update -> {});
		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("cancelled");
	}

	@Test
	void executeUsesOriginalToolNameNotPrefixed() throws Exception {
		var client = new StubMcpClient();
		client.setExecuteResult(ToolExecutionResult.builder()
			.resultText("ok").isError(false).build());

		var spec = ToolSpecification.builder()
			.name("original_name")
			.description("Test")
			.build();

		var adapter = new McpToolAdapter("server", client, spec);
		ObjectNode params = MAPPER.createObjectNode();

		adapter.execute("call-5", params, () -> false, update -> {});

		assertThat(client.getLastRequestName()).isEqualTo("original_name");
	}

	@Test
	void fromClientCreatesAdaptersForAllTools() {
		var client = new StubMcpClient();
		client.setToolSpecs(List.of(
			ToolSpecification.builder().name("tool1").description("T1").build(),
			ToolSpecification.builder().name("tool2").description("T2").build(),
			ToolSpecification.builder().name("tool3").description("T3").build()
		));

		var tools = McpToolAdapter.fromClient("srv", client);
		assertThat(tools).hasSize(3);
		assertThat(tools.get(0).name()).isEqualTo("srv.tool1");
		assertThat(tools.get(1).name()).isEqualTo("srv.tool2");
		assertThat(tools.get(2).name()).isEqualTo("srv.tool3");
	}
}
