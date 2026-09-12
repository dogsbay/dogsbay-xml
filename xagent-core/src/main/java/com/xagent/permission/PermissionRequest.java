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
package com.xagent.permission;

/**
 * A request for user approval of a tool call.
 *
 * @param toolName   the tool being invoked
 * @param arguments  the raw JSON arguments
 * @param primaryArg the tool's significant argument (command for bash, path for file tools),
 *                   empty if none could be extracted
 */
public record PermissionRequest(String toolName, String arguments, String primaryArg) {

	/** Human-readable one-line summary for display in prompts. */
	public String summary() {
		if (primaryArg != null && !primaryArg.isEmpty()) {
			return toolName + ": " + primaryArg;
		}
		return toolName + " " + arguments;
	}
}
