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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Search file contents using regex patterns.
 * Mirrors pi's grep.ts tool.
 */
public class GrepTool implements AgentTool {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final int MAX_MATCHES = 200;

	private final Path cwd;
	private final FileOperations ops;

	public GrepTool(Path cwd, FileOperations ops) {
		this.cwd = cwd;
		this.ops = ops;
	}

	@Override
	public String name() {
		return "grep";
	}

	@Override
	public boolean isReadOnly() {
		return true;
	}

	@Override
	public String description() {
		return "Search for a regex pattern in files. "
			+ "Searches all files under the given path (or cwd) matching the optional glob pattern.";
	}

	@Override
	public JsonNode parametersSchema() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode patternProp = properties.putObject("pattern");
		patternProp.put("type", "string");
		patternProp.put("description", "Regex pattern to search for");

		ObjectNode pathProp = properties.putObject("path");
		pathProp.put("type", "string");
		pathProp.put("description", "Directory or file to search in (default: cwd)");

		ObjectNode globProp = properties.putObject("glob");
		globProp.put("type", "string");
		globProp.put("description", "Glob pattern to filter files (e.g. '**/*.java')");

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
		String patternStr = params.get("pattern").asText();
		String pathStr = params.has("path") ? params.get("path").asText() : ".";
		String globPattern = params.has("glob") ? params.get("glob").asText() : "**/*";

		Pattern regex;
		try {
			regex = Pattern.compile(patternStr);
		} catch (PatternSyntaxException e) {
			return AgentToolResult.error("Invalid regex: " + e.getMessage());
		}

		Path searchPath = PathUtil.resolve(pathStr, cwd);

		try {
			var files = ops.isDirectory(searchPath)
				? ops.glob(searchPath, globPattern)
				: java.util.List.of(searchPath);

			var sb = new StringBuilder();
			int totalMatches = 0;

			for (Path file : files) {
				if (isCancelled != null && isCancelled.get()) {
					sb.append("\n... search cancelled");
					break;
				}
				if (ops.isDirectory(file) || !ops.isReadable(file)) continue;

				try {
					String content = ops.readFile(file);
					String[] lines = content.split("\n", -1);

					for (int i = 0; i < lines.length; i++) {
						Matcher matcher = regex.matcher(lines[i]);
						if (matcher.find()) {
							if (totalMatches >= MAX_MATCHES) {
								sb.append("\n... ").append(MAX_MATCHES).append(" matches shown (more exist)");
								return AgentToolResult.success(sb.toString());
							}
							Path relative = cwd.relativize(file);
							sb.append(relative).append(":").append(i + 1).append(": ").append(lines[i]).append("\n");
							totalMatches++;
						}
					}
				} catch (Exception ignored) {
					// Skip unreadable files
				}
			}

			if (totalMatches == 0) {
				return AgentToolResult.success("No matches found.");
			}

			return AgentToolResult.success(sb.toString().stripTrailing());
		} catch (Exception e) {
			return AgentToolResult.error("Search failed: " + e.getMessage());
		}
	}
}
