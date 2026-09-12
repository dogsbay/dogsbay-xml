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
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * List directory contents.
 * Mirrors pi's ls.ts tool.
 */
public class LsTool implements AgentTool {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private final Path cwd;
	private final FileOperations ops;

	public LsTool(Path cwd, FileOperations ops) {
		this.cwd = cwd;
		this.ops = ops;
	}

	@Override
	public String name() {
		return "ls";
	}

	@Override
	public boolean isReadOnly() {
		return true;
	}

	@Override
	public String description() {
		return "List the contents of a directory. Shows files and subdirectories.";
	}

	@Override
	public JsonNode parametersSchema() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode pathProp = properties.putObject("path");
		pathProp.put("type", "string");
		pathProp.put("description", "Directory path to list (default: cwd)");

		return schema;
	}

	@Override
	public AgentToolResult execute(
		String toolCallId,
		JsonNode params,
		Supplier<Boolean> isCancelled,
		Consumer<AgentToolResult> onUpdate
	) {
		String pathStr = params != null && params.has("path") ? params.get("path").asText() : ".";

		Path dir = PathUtil.resolve(pathStr, cwd);

		if (!ops.exists(dir)) {
			return AgentToolResult.error("Directory not found: " + pathStr);
		}
		if (!ops.isDirectory(dir)) {
			return AgentToolResult.error("Not a directory: " + pathStr);
		}

		try {
			var entries = ops.listDirectory(dir);
			entries.sort(Comparator.comparing(p -> p.getFileName().toString()));

			var sb = new StringBuilder();
			for (Path entry : entries) {
				String name = entry.getFileName().toString();
				if (ops.isDirectory(entry)) {
					name += "/";
				}
				if (!sb.isEmpty()) sb.append("\n");
				sb.append(name);
			}

			if (sb.isEmpty()) {
				return AgentToolResult.success("(empty directory)");
			}

			return AgentToolResult.success(sb.toString());
		} catch (Exception e) {
			return AgentToolResult.error("Failed to list directory: " + e.getMessage());
		}
	}
}
