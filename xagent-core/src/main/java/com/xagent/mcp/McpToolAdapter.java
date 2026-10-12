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
import com.xagent.tool.AgentTool;
import com.xagent.tool.AgentToolResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.*;
import dev.langchain4j.mcp.client.McpClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Adapts an MCP tool to the AgentTool interface.
 * Tool names are prefixed with the server name for namespace isolation.
 */
public class McpToolAdapter implements AgentTool {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final String serverName;
	private final McpClient client;
	private final ToolSpecification spec;
	private final JsonNode parametersSchema;

	public McpToolAdapter(String serverName, McpClient client, ToolSpecification spec) {
		this.serverName = serverName;
		this.client = client;
		this.spec = spec;
		this.parametersSchema = convertSchema(spec.parameters());
	}

	@Override
	public String name() {
		return serverName + "." + spec.name();
	}

	@Override
	public String description() {
		return "[" + serverName + "] " + (spec.description() != null ? spec.description() : "");
	}

	@Override
	public JsonNode parametersSchema() {
		return parametersSchema;
	}

	@Override
	public AgentToolResult execute(String toolCallId, JsonNode params,
								   Supplier<Boolean> isCancelled,
								   Consumer<AgentToolResult> onUpdate) {
		if (isCancelled.get()) {
			return AgentToolResult.error("Tool execution cancelled.");
		}

		try {
			// Build ToolExecutionRequest with the original (unprefixed) tool name
			var request = ToolExecutionRequest.builder()
				.id(toolCallId)
				.name(spec.name())
				.arguments(MAPPER.writeValueAsString(params))
				.build();

			var result = client.executeTool(request);

			if (result.isError()) {
				return AgentToolResult.error(result.resultText());
			}
			return AgentToolResult.success(result.resultText());
		} catch (Exception e) {
			return AgentToolResult.error("MCP tool error (" + serverName + "." + spec.name() + "): " + e.getMessage());
		}
	}

	/**
	 * Converts a LangChain4j JsonObjectSchema to a Jackson JsonNode (JSON Schema format).
	 */
	static JsonNode convertSchema(JsonSchemaElement schema) {
		ObjectNode root = MAPPER.createObjectNode();
		root.put("type", "object");

		if (schema instanceof JsonObjectSchema objectSchema) {
			ObjectNode properties = MAPPER.createObjectNode();
			Map<String, JsonSchemaElement> props = objectSchema.properties();
			if (props != null) {
				for (var entry : props.entrySet()) {
					properties.set(entry.getKey(), convertProperty(entry.getValue()));
				}
			}
			root.set("properties", properties);

			List<String> required = objectSchema.required();
			if (required != null && !required.isEmpty()) {
				var requiredArray = root.putArray("required");
				required.forEach(requiredArray::add);
			}
		}

		return root;
	}

	private static JsonNode convertProperty(JsonSchemaElement element) {
		ObjectNode prop = MAPPER.createObjectNode();
		String description = null;

		if (element instanceof JsonStringSchema s) {
			prop.put("type", "string");
			description = s.description();
		} else if (element instanceof JsonIntegerSchema s) {
			prop.put("type", "integer");
			description = s.description();
		} else if (element instanceof JsonBooleanSchema s) {
			prop.put("type", "boolean");
			description = s.description();
		} else if (element instanceof JsonNumberSchema s) {
			prop.put("type", "number");
			description = s.description();
		} else if (element instanceof JsonArraySchema s) {
			prop.put("type", "array");
			description = s.description();
		} else {
			prop.put("type", "string");
		}

		if (description != null) {
			prop.put("description", description);
		}

		return prop;
	}

	/**
	 * Creates AgentTool adapters for all tools from an MCP client.
	 */
	public static List<AgentTool> fromClient(String serverName, McpClient client) {
		List<ToolSpecification> specs = client.listTools();
		List<AgentTool> tools = new ArrayList<>();
		for (var spec : specs) {
			tools.add(new McpToolAdapter(serverName, client, spec));
		}
		return tools;
	}
}
