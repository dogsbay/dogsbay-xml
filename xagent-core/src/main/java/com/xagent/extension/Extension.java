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
package com.xagent.extension;

import com.xagent.tool.AgentTool;

import java.util.List;

/**
 * SPI interface for xagent extensions.
 * Extensions can hook into agent lifecycle events and register custom tools.
 *
 * Implementations are discovered via ServiceLoader or JAR scanning from
 * ~/.xagent/extensions/ and .xagent/extensions/.
 */
public interface Extension {

	/**
	 * Unique name for this extension.
	 */
	String name();

	/**
	 * Called when the extension is first loaded. Use to initialize resources.
	 */
	default void onLoad(ExtensionContext context) {}

	/**
	 * Called when the extension is unloaded. Use to clean up resources.
	 */
	default void onUnload() {}

	/**
	 * Called at the start of each agent turn (before the LLM is called).
	 */
	default void onTurnStart(ExtensionContext context) {}

	/**
	 * Called at the end of each agent turn (after all tool calls complete).
	 */
	default void onTurnEnd(ExtensionContext context) {}

	/**
	 * Called before a tool is executed.
	 * Return false to skip execution (the tool result will be an error message).
	 */
	default boolean onBeforeToolCall(String toolName, String arguments, ExtensionContext context) {
		return true;
	}

	/**
	 * Called after a tool finishes execution.
	 */
	default void onAfterToolCall(String toolName, String result, boolean isError, ExtensionContext context) {}

	/**
	 * Called when the agent session ends.
	 */
	default void onAgentEnd(ExtensionContext context) {}

	/**
	 * Return custom tools to register with the agent.
	 * Called once during extension loading.
	 */
	default List<AgentTool> registerTools() {
		return List.of();
	}
}
