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

/**
 * Result of a beforeToolCall hook.
 */
public sealed interface ToolHookResult {

	/** Allow the tool call to proceed as-is. */
	record Allow() implements ToolHookResult {}

	/** Block the tool call with a reason. */
	record Block(String reason) implements ToolHookResult {}

	/** Modify the tool call arguments. */
	record Modify(String newArguments) implements ToolHookResult {}

	ToolHookResult ALLOW = new Allow();

	static ToolHookResult block(String reason) {
		return new Block(reason);
	}

	static ToolHookResult modify(String newArguments) {
		return new Modify(newArguments);
	}
}
