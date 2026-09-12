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
 * Find-and-replace editing tool with unique match validation.
 * Mirrors pi's edit.ts tool.
 */
public class EditTool implements AgentTool {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private final Path cwd;
	private final FileOperations ops;

	public EditTool(Path cwd, FileOperations ops) {
		this.cwd = cwd;
		this.ops = ops;
	}

	@Override
	public String name() {
		return "edit";
	}

	@Override
	public String description() {
		return "Edit a file by replacing an exact string match. "
			+ "The old_string must be unique in the file (appear exactly once). "
			+ "Use replace_all=true to replace all occurrences.";
	}

	@Override
	public JsonNode parametersSchema() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode pathProp = properties.putObject("path");
		pathProp.put("type", "string");
		pathProp.put("description", "File path to edit");

		ObjectNode oldProp = properties.putObject("old_string");
		oldProp.put("type", "string");
		oldProp.put("description", "Exact string to find and replace");

		ObjectNode newProp = properties.putObject("new_string");
		newProp.put("type", "string");
		newProp.put("description", "Replacement string");

		ObjectNode replaceAllProp = properties.putObject("replace_all");
		replaceAllProp.put("type", "boolean");
		replaceAllProp.put("description", "Replace all occurrences (default: false)");

		var required = schema.putArray("required");
		required.add("path");
		required.add("old_string");
		required.add("new_string");
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
		String oldString = params.get("old_string").asText();
		String newString = params.get("new_string").asText();
		boolean replaceAll = params.has("replace_all") && params.get("replace_all").asBoolean(false);

		Path path = PathUtil.resolve(pathStr, cwd);

		if (!ops.exists(path)) {
			return AgentToolResult.error("File not found: " + pathStr);
		}

		try {
			String content = ops.readFile(path);

			if (!content.contains(oldString)) {
				return AgentToolResult.error(
					"old_string not found in " + pathStr + ". Make sure the string matches exactly."
				);
			}

			if (!replaceAll) {
				int firstIdx = content.indexOf(oldString);
				int secondIdx = content.indexOf(oldString, firstIdx + 1);
				if (secondIdx >= 0) {
					int count = countOccurrences(content, oldString);
					return AgentToolResult.error(
						"old_string appears " + count + " times in " + pathStr
							+ ". Use replace_all=true or provide a more specific string."
					);
				}
			}

			String newContent;
			int replacements;
			if (replaceAll) {
				replacements = countOccurrences(content, oldString);
				newContent = content.replace(oldString, newString);
			} else {
				replacements = 1;
				int idx = content.indexOf(oldString);
				newContent = content.substring(0, idx) + newString + content.substring(idx + oldString.length());
			}

			ops.writeFile(path, newContent);
			return AgentToolResult.success(
				"Edited " + pathStr + ": " + replacements + " replacement(s) made."
			);
		} catch (Exception e) {
			return AgentToolResult.error("Failed to edit file: " + e.getMessage());
		}
	}

	private static int countOccurrences(String text, String search) {
		int count = 0;
		int idx = 0;
		while ((idx = text.indexOf(search, idx)) >= 0) {
			count++;
			idx += search.length();
		}
		return count;
	}
}
