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
package com.xagent.session;

import java.time.Instant;

/**
 * Sealed interface for entries in a session JSONL file.
 * Each entry is one line of JSON with a "type" discriminator.
 */
public sealed interface SessionEntry {

	String id();
	Instant timestamp();

	/**
	 * Header entry: first line of the session file.
	 */
	record Header(
		String id,
		int version,
		Instant timestamp,
		String cwd,
		String provider,
		String model
	) implements SessionEntry {}

	/**
	 * A user or assistant message in the conversation.
	 */
	record Message(
		String id,
		String parentId,
		Instant timestamp,
		String role,
		String content,
		boolean hasToolCalls
	) implements SessionEntry {}

	/**
	 * A tool call result.
	 */
	record ToolResult(
		String id,
		String parentId,
		Instant timestamp,
		String toolCallId,
		String toolName,
		String content,
		boolean isError
	) implements SessionEntry {}

	/**
	 * Summary of the conversation for display in session list.
	 */
	record Summary(
		String id,
		Instant timestamp,
		String text
	) implements SessionEntry {}
}
