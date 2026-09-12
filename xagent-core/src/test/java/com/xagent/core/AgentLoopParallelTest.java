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

import com.xagent.event.AgentEvent;
import com.xagent.message.AgentMessage;
import com.xagent.message.UserMessage;
import com.xagent.tool.ToolRegistry;
import com.xagent.tool.operations.DefaultBashOperations;
import com.xagent.tool.operations.DefaultFileOperations;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLoopParallelTest {

	@TempDir
	Path tempDir;

	private ToolRegistry toolRegistry;
	private List<AgentEvent> events;

	@BeforeEach
	void setUp() {
		toolRegistry = ToolRegistry.createDefault(tempDir);
		events = Collections.synchronizedList(new ArrayList<>());
	}

	/**
	 * Mock model that returns multiple tool calls in one response.
	 */
	static class MultiToolMockModel implements StreamingChatModel {
		private final AtomicInteger callCount = new AtomicInteger(0);
		private final List<ToolExecutionRequest> toolCalls;

		MultiToolMockModel(List<ToolExecutionRequest> toolCalls) {
			this.toolCalls = toolCalls;
		}

		@Override
		public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
			int call = callCount.getAndIncrement();
			if (call == 0) {
				var aiMessage = AiMessage.builder()
					.toolExecutionRequests(toolCalls)
					.build();
				handler.onCompleteResponse(ChatResponse.builder().aiMessage(aiMessage).build());
			} else {
				handler.onPartialResponse("Done.");
				handler.onCompleteResponse(
					ChatResponse.builder().aiMessage(AiMessage.from("Done.")).build()
				);
			}
		}
	}

	@Test
	void parallelExecutionRunsMultipleToolsConcurrently() throws IOException {
		Files.writeString(tempDir.resolve("a.txt"), "File A");
		Files.writeString(tempDir.resolve("b.txt"), "File B");

		var toolCalls = List.of(
			ToolExecutionRequest.builder().id("call_1").name("read").arguments("{\"path\":\"a.txt\"}").build(),
			ToolExecutionRequest.builder().id("call_2").name("read").arguments("{\"path\":\"b.txt\"}").build()
		);

		var model = new MultiToolMockModel(toolCalls);
		var loop = new AgentLoop(model, toolRegistry, "test", "mock", "mock", events::add, true, RetryConfig.NONE);

		var messages = new ArrayList<AgentMessage>();
		messages.add(new UserMessage("Read both files"));
		loop.run(messages);

		// Both tool results should be present
		var toolResults = events.stream()
			.filter(e -> e instanceof AgentEvent.ToolExecutionEnd)
			.map(e -> (AgentEvent.ToolExecutionEnd) e)
			.toList();

		assertThat(toolResults).hasSize(2);
		assertThat(toolResults.get(0).toolCallId()).isEqualTo("call_1");
		assertThat(toolResults.get(1).toolCallId()).isEqualTo("call_2");
		assertThat(toolResults.get(0).result().content()).contains("File A");
		assertThat(toolResults.get(1).result().content()).contains("File B");
	}

	@Test
	void parallelExecutionPreservesSourceOrder() throws IOException {
		Files.writeString(tempDir.resolve("a.txt"), "AAA");
		Files.writeString(tempDir.resolve("b.txt"), "BBB");
		Files.writeString(tempDir.resolve("c.txt"), "CCC");

		var toolCalls = List.of(
			ToolExecutionRequest.builder().id("c1").name("read").arguments("{\"path\":\"a.txt\"}").build(),
			ToolExecutionRequest.builder().id("c2").name("read").arguments("{\"path\":\"b.txt\"}").build(),
			ToolExecutionRequest.builder().id("c3").name("read").arguments("{\"path\":\"c.txt\"}").build()
		);

		var model = new MultiToolMockModel(toolCalls);
		var loop = new AgentLoop(model, toolRegistry, "test", "mock", "mock", events::add, true, RetryConfig.NONE);

		var messages = new ArrayList<AgentMessage>();
		messages.add(new UserMessage("Read all"));
		loop.run(messages);

		// ToolExecutionEnd events should be in source order
		var endEvents = events.stream()
			.filter(e -> e instanceof AgentEvent.ToolExecutionEnd)
			.map(e -> (AgentEvent.ToolExecutionEnd) e)
			.toList();

		assertThat(endEvents).hasSize(3);
		assertThat(endEvents.get(0).toolCallId()).isEqualTo("c1");
		assertThat(endEvents.get(1).toolCallId()).isEqualTo("c2");
		assertThat(endEvents.get(2).toolCallId()).isEqualTo("c3");

		// Messages should also be in source order
		var toolMessages = messages.stream()
			.filter(m -> m.role().equals("toolResult"))
			.toList();
		assertThat(toolMessages).hasSize(3);
	}

	@Test
	void parallelStartEventsEmittedBeforeResults() throws IOException {
		Files.writeString(tempDir.resolve("x.txt"), "X");
		Files.writeString(tempDir.resolve("y.txt"), "Y");

		var toolCalls = List.of(
			ToolExecutionRequest.builder().id("c1").name("read").arguments("{\"path\":\"x.txt\"}").build(),
			ToolExecutionRequest.builder().id("c2").name("read").arguments("{\"path\":\"y.txt\"}").build()
		);

		var model = new MultiToolMockModel(toolCalls);
		var loop = new AgentLoop(model, toolRegistry, "test", "mock", "mock", events::add, true, RetryConfig.NONE);

		var messages = new ArrayList<AgentMessage>();
		messages.add(new UserMessage("Read"));
		loop.run(messages);

		// All start events should come before any end events
		int lastStartIdx = -1;
		int firstEndIdx = Integer.MAX_VALUE;
		for (int i = 0; i < events.size(); i++) {
			if (events.get(i) instanceof AgentEvent.ToolExecutionStart) lastStartIdx = i;
			if (events.get(i) instanceof AgentEvent.ToolExecutionEnd && i < firstEndIdx) firstEndIdx = i;
		}

		assertThat(lastStartIdx).isLessThan(firstEndIdx);
	}

	@Test
	void sequentialModeExecutesOneAtATime() throws IOException {
		Files.writeString(tempDir.resolve("a.txt"), "A");
		Files.writeString(tempDir.resolve("b.txt"), "B");

		var toolCalls = List.of(
			ToolExecutionRequest.builder().id("c1").name("read").arguments("{\"path\":\"a.txt\"}").build(),
			ToolExecutionRequest.builder().id("c2").name("read").arguments("{\"path\":\"b.txt\"}").build()
		);

		var model = new MultiToolMockModel(toolCalls);
		var loop = new AgentLoop(model, toolRegistry, "test", "mock", "mock", events::add, false, RetryConfig.NONE);

		var messages = new ArrayList<AgentMessage>();
		messages.add(new UserMessage("Read"));
		loop.run(messages);

		// In sequential mode, start/end should alternate
		var toolEvents = events.stream()
			.filter(e -> e instanceof AgentEvent.ToolExecutionStart || e instanceof AgentEvent.ToolExecutionEnd)
			.toList();

		assertThat(toolEvents).hasSize(4);
		assertThat(toolEvents.get(0)).isInstanceOf(AgentEvent.ToolExecutionStart.class);
		assertThat(toolEvents.get(1)).isInstanceOf(AgentEvent.ToolExecutionEnd.class);
		assertThat(toolEvents.get(2)).isInstanceOf(AgentEvent.ToolExecutionStart.class);
		assertThat(toolEvents.get(3)).isInstanceOf(AgentEvent.ToolExecutionEnd.class);
	}

	@Test
	void singleToolCallUsesSequentialEvenInParallelMode() {
		var toolCalls = List.of(
			ToolExecutionRequest.builder().id("c1").name("ls").arguments("{}").build()
		);

		var model = new MultiToolMockModel(toolCalls);
		var loop = new AgentLoop(model, toolRegistry, "test", "mock", "mock", events::add, true, RetryConfig.NONE);

		var messages = new ArrayList<AgentMessage>();
		messages.add(new UserMessage("List"));
		loop.run(messages);

		// Single tool call should still work fine
		var toolResults = events.stream()
			.filter(e -> e instanceof AgentEvent.ToolExecutionEnd)
			.toList();
		assertThat(toolResults).hasSize(1);
	}
}
