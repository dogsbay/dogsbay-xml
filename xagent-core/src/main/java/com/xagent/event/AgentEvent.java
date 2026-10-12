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
package com.xagent.event;

import com.xagent.message.AssistantMessage;
import com.xagent.tool.AgentToolResult;

/**
 * Sealed interface for events emitted by the agent during streaming.
 */
public sealed interface AgentEvent
	permits AgentEvent.TurnStart,
		AgentEvent.TurnEnd,
		AgentEvent.MessageStart,
		AgentEvent.MessageUpdate,
		AgentEvent.MessageEnd,
		AgentEvent.ToolExecutionStart,
		AgentEvent.ToolExecutionEnd,
		AgentEvent.UsageUpdate,
		AgentEvent.RetryAttempt,
		AgentEvent.AgentEnd,
		AgentEvent.ErrorOccurred {

	record TurnStart() implements AgentEvent {}

	record TurnEnd() implements AgentEvent {}

	record MessageStart() implements AgentEvent {}

	record MessageUpdate(String partialText) implements AgentEvent {}

	record MessageEnd(AssistantMessage message) implements AgentEvent {}

	record ToolExecutionStart(String toolCallId, String toolName, String arguments) implements AgentEvent {}

	record ToolExecutionEnd(String toolCallId, String toolName, AgentToolResult result) implements AgentEvent {}

	record UsageUpdate(long inputTokens, long outputTokens, long totalTokens, int messageCount, double estimatedCostUsd) implements AgentEvent {}

	record RetryAttempt(int attempt, long delayMs, String reason) implements AgentEvent {}

	record AgentEnd() implements AgentEvent {}

	record ErrorOccurred(Throwable error) implements AgentEvent {}
}
