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
package com.xagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.xagent.tool.dita.DitaBuildTool;
import com.xagent.tool.operations.BashOperations;
import com.xagent.tool.operations.DefaultBashOperations;
import com.xagent.tool.operations.DefaultFileOperations;
import com.xagent.tool.operations.FileOperations;
import com.xagent.tool.xml.XmlFormatTool;
import com.xagent.tool.xml.XmlValidationTool;
import com.xagent.tool.xml.XPathTool;
import com.xagent.tool.xml.XsltTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of available tools. Converts AgentTool instances to
 * LangChain4j ToolSpecification for sending to the model.
 */
public class ToolRegistry {

	private final Map<String, AgentTool> tools = new LinkedHashMap<>();

	public void register(AgentTool tool) {
		tools.put(tool.name(), tool);
	}

	/**
	 * Register additional tools (e.g. from MCP servers).
	 */
	public void registerAll(List<AgentTool> additionalTools) {
		for (var tool : additionalTools) {
			tools.put(tool.name(), tool);
		}
	}

	public AgentTool get(String name) {
		return tools.get(name);
	}

	public List<AgentTool> all() {
		return List.copyOf(tools.values());
	}

	public int size() {
		return tools.size();
	}

	/**
	 * Convert all registered tools to LangChain4j ToolSpecifications.
	 */
	public List<ToolSpecification> toToolSpecifications() {
		var specs = new ArrayList<ToolSpecification>();
		for (AgentTool tool : tools.values()) {
			specs.add(toSpec(tool));
		}
		return specs;
	}

	/**
	 * Create a registry with the default coding tools.
	 */
	public static ToolRegistry createDefault(Path cwd) {
		return createDefault(cwd, new DefaultFileOperations(), new DefaultBashOperations());
	}

	/**
	 * Create a registry with the default coding tools using custom operations.
	 */
	public static ToolRegistry createDefault(Path cwd, FileOperations fileOps, BashOperations bashOps) {
		var registry = new ToolRegistry();
		registry.register(new ReadTool(cwd, fileOps));
		registry.register(new WriteTool(cwd, fileOps));
		registry.register(new EditTool(cwd, fileOps));
		registry.register(new BashTool(cwd, bashOps));
		registry.register(new GrepTool(cwd, fileOps));
		registry.register(new FindTool(cwd, fileOps));
		registry.register(new LsTool(cwd, fileOps));
		registry.register(new XmlValidationTool(cwd, fileOps));
		registry.register(new XPathTool(cwd, fileOps));
		registry.register(new XmlFormatTool(cwd, fileOps));
		registry.register(new XsltTool(cwd, fileOps));
		registry.register(new DitaBuildTool(cwd, bashOps));
		return registry;
	}

	/**
	 * Create a registry with only the XML/DITA tools — the set served by
	 * MCP server mode. Deliberately excludes general file/shell tools:
	 * MCP clients bring their own.
	 */
	public static ToolRegistry createXmlToolset(Path cwd) {
		var fileOps = new DefaultFileOperations();
		var registry = new ToolRegistry();
		registry.register(new XmlValidationTool(cwd, fileOps));
		registry.register(new XPathTool(cwd, fileOps));
		registry.register(new XmlFormatTool(cwd, fileOps));
		registry.register(new XsltTool(cwd, fileOps));
		registry.register(new DitaBuildTool(cwd, new DefaultBashOperations()));
		return registry;
	}

	private static ToolSpecification toSpec(AgentTool tool) {
		JsonNode schema = tool.parametersSchema();
		JsonObjectSchema objectSchema = convertSchema(schema);

		return ToolSpecification.builder()
			.name(tool.name())
			.description(tool.description())
			.parameters(objectSchema)
			.build();
	}

	private static JsonObjectSchema convertSchema(JsonNode schema) {
		var builder = JsonObjectSchema.builder();
		var properties = new LinkedHashMap<String, dev.langchain4j.model.chat.request.json.JsonSchemaElement>();

		JsonNode props = schema.get("properties");
		if (props != null) {
			var fields = props.fields();
			while (fields.hasNext()) {
				var entry = fields.next();
				String name = entry.getKey();
				JsonNode propSchema = entry.getValue();
				String type = propSchema.has("type") ? propSchema.get("type").asText() : "string";
				String desc = propSchema.has("description") ? propSchema.get("description").asText() : null;

				properties.put(name, switch (type) {
					case "integer" -> JsonIntegerSchema.builder().description(desc).build();
					case "boolean" -> JsonBooleanSchema.builder().description(desc).build();
					default -> JsonStringSchema.builder().description(desc).build();
				});
			}
		}

		builder.addProperties(properties);

		JsonNode required = schema.get("required");
		if (required != null && required.isArray()) {
			var requiredList = new ArrayList<String>();
			required.forEach(node -> requiredList.add(node.asText()));
			builder.required(requiredList);
		}

		return builder.build();
	}
}
