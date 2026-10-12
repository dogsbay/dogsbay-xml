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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xagent.tool.operations.FileOperations;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Write content to a file, creating parent directories as needed.
 * Mirrors pi's write.ts tool.
 */
public class WriteTool implements AgentTool {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private final Path cwd;
	private final FileOperations ops;

	public WriteTool(Path cwd, FileOperations ops) {
		this.cwd = cwd;
		this.ops = ops;
	}

	@Override
	public String name() {
		return "write";
	}

	@Override
	public String description() {
		return "Write content to a file. Creates the file and parent directories if they don't exist. "
			+ "Overwrites existing content.";
	}

	@Override
	public JsonNode parametersSchema() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode pathProp = properties.putObject("path");
		pathProp.put("type", "string");
		pathProp.put("description", "File path to write to");

		ObjectNode contentProp = properties.putObject("content");
		contentProp.put("type", "string");
		contentProp.put("description", "Content to write to the file");

		var required = schema.putArray("required");
		required.add("path");
		required.add("content");
		return schema;
	}

	@Override
	public AgentToolResult execute(
		String toolCallId,
		JsonNode params,
		Supplier<Boolean> isCancelled,
		Consumer<AgentToolResult> onUpdate
	) {
		String pathStr = params.get("path").asText();
		String content = params.get("content").asText();

		Path path = PathUtil.resolve(pathStr, cwd);

		try {
			boolean existed = ops.exists(path);
			ops.writeFile(path, content);
			String action = existed ? "Updated" : "Created";
			int lines = content.split("\n", -1).length;
			return AgentToolResult.success(
				action + " " + pathStr + " (" + lines + " lines, " + content.length() + " bytes)"
			);
		} catch (Exception e) {
			return AgentToolResult.error("Failed to write file: " + e.getMessage());
		}
	}
}
