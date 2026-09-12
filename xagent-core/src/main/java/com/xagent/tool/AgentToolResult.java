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

import java.util.List;

/**
 * Result of a tool execution. May include text content and/or images.
 */
public record AgentToolResult(
	String content,
	JsonNode details,
	boolean isError,
	List<ImageContent> images
) {
	public static AgentToolResult success(String content) {
		return new AgentToolResult(content, null, false, List.of());
	}

	public static AgentToolResult error(String message) {
		return new AgentToolResult(message, null, true, List.of());
	}

	public static AgentToolResult error(String message, JsonNode details) {
		return new AgentToolResult(message, details, true, List.of());
	}

	public static AgentToolResult success(String content, JsonNode details) {
		return new AgentToolResult(content, details, false, List.of());
	}

	public static AgentToolResult successWithImage(String content, ImageContent image) {
		return new AgentToolResult(content, null, false, List.of(image));
	}

	public static AgentToolResult successWithImages(String content, List<ImageContent> images) {
		return new AgentToolResult(content, null, false, images);
	}

	public boolean hasImages() {
		return images != null && !images.isEmpty();
	}
}
