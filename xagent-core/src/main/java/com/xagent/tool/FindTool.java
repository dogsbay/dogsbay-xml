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
 * Find files matching a glob pattern.
 * Mirrors pi's find.ts tool.
 */
public class FindTool implements AgentTool {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final int MAX_RESULTS = 500;

	private final Path cwd;
	private final FileOperations ops;

	public FindTool(Path cwd, FileOperations ops) {
		this.cwd = cwd;
		this.ops = ops;
	}

	@Override
	public String name() {
		return "find";
	}

	@Override
	public boolean isReadOnly() {
		return true;
	}

	@Override
	public String description() {
		return "Find files matching a glob pattern. Returns file paths relative to the search directory.";
	}

	@Override
	public JsonNode parametersSchema() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode patternProp = properties.putObject("pattern");
		patternProp.put("type", "string");
		patternProp.put("description", "Glob pattern (e.g. '**/*.java', '*.xml')");

		ObjectNode pathProp = properties.putObject("path");
		pathProp.put("type", "string");
		pathProp.put("description", "Root directory to search from (default: cwd)");

		schema.putArray("required").add("pattern");
		return schema;
	}

	@Override
	public AgentToolResult execute(
		String toolCallId,
		JsonNode params,
		Supplier<Boolean> isCancelled,
		Consumer<AgentToolResult> onUpdate
	) {
		String pattern = params.get("pattern").asText();
		String pathStr = params.has("path") ? params.get("path").asText() : ".";

		Path searchRoot = PathUtil.resolve(pathStr, cwd);

		if (!ops.exists(searchRoot) || !ops.isDirectory(searchRoot)) {
			return AgentToolResult.error("Directory not found: " + pathStr);
		}

		try {
			var files = ops.glob(searchRoot, pattern);

			files.sort(Comparator.comparing(Path::toString));

			var sb = new StringBuilder();
			int count = 0;
			for (Path file : files) {
				if (count >= MAX_RESULTS) {
					sb.append("\n... ").append(files.size() - MAX_RESULTS).append(" more files");
					break;
				}
				if (count > 0) sb.append("\n");
				sb.append(cwd.relativize(file));
				count++;
			}

			if (count == 0) {
				return AgentToolResult.success("No files found matching: " + pattern);
			}

			return AgentToolResult.success(sb.toString());
		} catch (Exception e) {
			return AgentToolResult.error("Find failed: " + e.getMessage());
		}
	}
}
