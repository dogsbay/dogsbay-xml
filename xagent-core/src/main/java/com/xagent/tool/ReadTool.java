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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Read file contents with offset/limit support and line numbers.
 * Mirrors pi's read.ts tool.
 */
public class ReadTool implements AgentTool {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private final Path cwd;
	private final FileOperations ops;

	public ReadTool(Path cwd, FileOperations ops) {
		this.cwd = cwd;
		this.ops = ops;
	}

	@Override
	public String name() {
		return "read";
	}

	@Override
	public boolean isReadOnly() {
		return true;
	}

	@Override
	public String description() {
		return "Read a file's contents. Returns the file content with line numbers. "
			+ "Use offset and limit for large files. "
			+ "For image files (png, jpg, gif, webp, svg), returns the image for visual analysis.";
	}

	@Override
	public JsonNode parametersSchema() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode pathProp = properties.putObject("path");
		pathProp.put("type", "string");
		pathProp.put("description", "File path to read (absolute or relative to cwd)");

		ObjectNode offsetProp = properties.putObject("offset");
		offsetProp.put("type", "integer");
		offsetProp.put("description", "Line number to start reading from (1-based). Default: 1");

		ObjectNode limitProp = properties.putObject("limit");
		limitProp.put("type", "integer");
		limitProp.put("description", "Maximum number of lines to read. Default: 2000");

		schema.putArray("required").add("path");
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
		int offset = params.has("offset") ? params.get("offset").asInt(1) : 1;
		int limit = params.has("limit") ? params.get("limit").asInt(TruncationUtil.MAX_LINES) : TruncationUtil.MAX_LINES;

		Path path = PathUtil.resolve(pathStr, cwd);

		if (!ops.exists(path)) {
			return AgentToolResult.error("File not found: " + pathStr);
		}
		if (!ops.isReadable(path)) {
			return AgentToolResult.error("File not readable: " + pathStr);
		}
		if (ops.isDirectory(path)) {
			return AgentToolResult.error("Path is a directory, not a file: " + pathStr);
		}

		try {
			// Check if it's an image file
			String fileName = path.getFileName().toString();
			int dotIdx = fileName.lastIndexOf('.');
			if (dotIdx > 0) {
				String ext = fileName.substring(dotIdx + 1);
				String mimeType = ImageContent.mimeTypeForExtension(ext);
				if (mimeType != null) {
					byte[] bytes = Files.readAllBytes(path);
					String base64 = Base64.getEncoder().encodeToString(bytes);
					var image = new ImageContent(base64, mimeType, fileName);
					return AgentToolResult.successWithImage(
						"Image file: " + fileName + " (" + image.formatSizeLabel() + ")",
						image
					);
				}
			}

			String content = ops.readFile(path);
			String[] lines = content.split("\n", -1);

			int startIdx = Math.max(0, offset - 1);
			int endIdx = Math.min(lines.length, startIdx + limit);

			var sb = new StringBuilder();
			for (int i = startIdx; i < endIdx; i++) {
				if (i > startIdx) sb.append("\n");
				sb.append(String.format("%6d\t%s", i + 1, lines[i]));
			}

			String result = TruncationUtil.truncateHead(sb.toString(), limit, TruncationUtil.MAX_BYTES);

			if (endIdx < lines.length) {
				result += "\n... " + (lines.length - endIdx) + " more lines. Use offset=" + (endIdx + 1) + " to continue.";
			}

			return AgentToolResult.success(result);
		} catch (Exception e) {
			return AgentToolResult.error("Failed to read file: " + e.getMessage());
		}
	}
}
