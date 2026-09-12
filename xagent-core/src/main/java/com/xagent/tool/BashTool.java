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
import com.xagent.tool.operations.BashOperations;

import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Execute bash commands with timeout and output truncation.
 * Mirrors pi's bash.ts tool.
 */
public class BashTool implements AgentTool {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

	private final Path cwd;
	private final BashOperations ops;

	public BashTool(Path cwd, BashOperations ops) {
		this.cwd = cwd;
		this.ops = ops;
	}

	@Override
	public String name() {
		return "bash";
	}

	@Override
	public String description() {
		return "Execute a bash command and return stdout/stderr. "
			+ "Commands run in the working directory with a default timeout of 120 seconds.";
	}

	@Override
	public JsonNode parametersSchema() {
		ObjectNode schema = MAPPER.createObjectNode();
		schema.put("type", "object");

		ObjectNode properties = schema.putObject("properties");

		ObjectNode cmdProp = properties.putObject("command");
		cmdProp.put("type", "string");
		cmdProp.put("description", "The bash command to execute");

		ObjectNode timeoutProp = properties.putObject("timeout");
		timeoutProp.put("type", "integer");
		timeoutProp.put("description", "Timeout in seconds (default: 120)");

		schema.putArray("required").add("command");
		return schema;
	}

	@Override
	public AgentToolResult execute(
		String toolCallId,
		JsonNode params,
		Supplier<Boolean> isCancelled,
		Consumer<AgentToolResult> onUpdate
	) {
		String command = params.get("command").asText();
		int timeoutSecs = params.has("timeout") ? params.get("timeout").asInt(120) : 120;
		Duration timeout = Duration.ofSeconds(timeoutSecs);

		var result = ops.exec(command, cwd, timeout, null, isCancelled);

		var sb = new StringBuilder();
		if (!result.stdout().isEmpty()) {
			String truncated = TruncationUtil.truncateTail(
				result.stdout(), TruncationUtil.MAX_LINES, TruncationUtil.MAX_BYTES
			);
			sb.append(truncated);
		}
		if (!result.stderr().isEmpty()) {
			if (!sb.isEmpty()) sb.append("\n");
			sb.append("STDERR:\n");
			String truncated = TruncationUtil.truncateTail(
				result.stderr(), 500, 10_000
			);
			sb.append(truncated);
		}

		if (sb.isEmpty()) {
			sb.append("(no output)");
		}

		sb.append("\nExit code: ").append(result.exitCode());

		boolean isError = result.exitCode() != 0;
		return new AgentToolResult(sb.toString(), null, isError, java.util.List.of());
	}
}
