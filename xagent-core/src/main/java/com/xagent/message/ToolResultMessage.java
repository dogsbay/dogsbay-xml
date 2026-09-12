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
package com.xagent.message;

import com.xagent.tool.ImageContent;

import java.time.Instant;
import java.util.List;

public record ToolResultMessage(
	String toolCallId,
	String toolName,
	String content,
	boolean isError,
	List<ImageContent> images,
	Instant timestamp
) implements AgentMessage {

	public ToolResultMessage(String toolCallId, String toolName, String content, boolean isError) {
		this(toolCallId, toolName, content, isError, List.of(), Instant.now());
	}

	public ToolResultMessage(String toolCallId, String toolName, String content, boolean isError, Instant timestamp) {
		this(toolCallId, toolName, content, isError, List.of(), timestamp);
	}

	public ToolResultMessage(String toolCallId, String toolName, String content, boolean isError, List<ImageContent> images) {
		this(toolCallId, toolName, content, isError, images, Instant.now());
	}

	public boolean hasImages() {
		return images != null && !images.isEmpty();
	}

	@Override
	public String role() {
		return "toolResult";
	}
}
