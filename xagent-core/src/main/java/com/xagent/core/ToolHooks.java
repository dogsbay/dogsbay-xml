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
package com.xagent.core;

import com.xagent.tool.AgentToolResult;

/**
 * Hooks for intercepting tool calls at the agent-core level.
 * More lightweight than the Extension system -- just implement this interface.
 */
public interface ToolHooks {

	/**
	 * Called before a tool executes. Return ALLOW to proceed,
	 * BLOCK to prevent execution, or MODIFY to change arguments.
	 */
	default ToolHookResult beforeToolCall(String toolName, String arguments) {
		return ToolHookResult.ALLOW;
	}

	/**
	 * Called after a tool executes. Return the original result or a modified one.
	 */
	default AgentToolResult afterToolCall(String toolName, AgentToolResult result) {
		return result;
	}
}
