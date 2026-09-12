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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;

import java.time.Instant;
import java.util.List;

public record AssistantMessage(
	String content,
	Instant timestamp,
	String model,
	String provider,
	List<ToolExecutionRequest> toolCalls,
	AiMessage originalAiMessage
) implements AgentMessage {

	public AssistantMessage(String content, Instant timestamp, String model, String provider) {
		this(content, timestamp, model, provider, List.of(), null);
	}

	public AssistantMessage(String content, Instant timestamp, String model, String provider, List<ToolExecutionRequest> toolCalls) {
		this(content, timestamp, model, provider, toolCalls, null);
	}

	public boolean hasToolCalls() {
		return toolCalls != null && !toolCalls.isEmpty();
	}

	@Override
	public String role() {
		return "assistant";
	}
}
