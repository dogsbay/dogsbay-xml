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

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Interface for tools the agent can call.
 * Mirrors pi's AgentTool in packages/agent/src/types.ts.
 */
public interface AgentTool {

	String name();

	String description();

	/**
	 * True if this tool cannot modify files or system state. Read-only
	 * tools bypass the permission system. Defaults to false so unknown
	 * tools (MCP, extensions) require approval.
	 */
	default boolean isReadOnly() {
		return false;
	}

	/**
	 * JSON Schema describing the tool's parameters.
	 */
	JsonNode parametersSchema();

	/**
	 * Execute the tool with the given parameters.
	 *
	 * @param toolCallId  unique ID for this tool call
	 * @param params      parsed parameters as JSON
	 * @param isCancelled supplier that returns true if the operation should be cancelled
	 * @param onUpdate    callback for streaming partial results
	 * @return the tool execution result
	 */
	AgentToolResult execute(
		String toolCallId,
		JsonNode params,
		Supplier<Boolean> isCancelled,
		Consumer<AgentToolResult> onUpdate
	);
}
