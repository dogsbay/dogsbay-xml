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
import com.xagent.tool.operations.DefaultFileOperations;
import com.xagent.tool.operations.DefaultBashOperations;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLoopTest {

	@TempDir
	Path tempDir;

	private ToolRegistry toolRegistry;
	private List<AgentEvent> events;

	@BeforeEach
	void setUp() {
		toolRegistry = ToolRegistry.createDefault(tempDir);
		events = new ArrayList<>();
	}

	/**
	 * Mock model that first requests a tool call, then responds with text.
	 */
	static class ToolCallingMockModel implements StreamingChatModel {
		private final AtomicInteger callCount = new AtomicInteger(0);

		@Override
		public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
			int call = callCount.getAndIncrement();

			if (call == 0) {
				// First call: request a tool call
				var toolCall = ToolExecutionRequest.builder()
					.id("call_1")
					.name("ls")
					.arguments("{}")
					.build();
				var aiMessage = AiMessage.builder()
					.text("Let me list the directory.")
					.toolExecutionRequests(List.of(toolCall))
					.build();
				handler.onPartialResponse("Let me list the directory.");
				handler.onCompleteResponse(ChatResponse.builder().aiMessage(aiMessage).build());
			} else {
				// Second call: final text response
				handler.onPartialResponse("The directory is empty.");
				handler.onCompleteResponse(
					ChatResponse.builder().aiMessage(AiMessage.from("The directory is empty.")).build()
				);
			}
		}
	}

	@Test
	void loopExecutesToolAndContinues() {
		var model = new ToolCallingMockModel();
		var loop = new AgentLoop(model, toolRegistry, "test prompt", "mock", "mock", events::add);

		var messages = new ArrayList<AgentMessage>();
		messages.add(new UserMessage("List the directory"));

		loop.run(messages);

		// Should have: user msg + assistant (with tool call) + tool result + assistant (final)
		assertThat(messages).hasSize(4);
		assertThat(messages.get(0).role()).isEqualTo("user");
		assertThat(messages.get(1).role()).isEqualTo("assistant");
		assertThat(messages.get(2).role()).isEqualTo("toolResult");
		assertThat(messages.get(3).role()).isEqualTo("assistant");

		// Verify events
		assertThat(events.stream().filter(e -> e instanceof AgentEvent.ToolExecutionStart).count()).isEqualTo(1);
		assertThat(events.stream().filter(e -> e instanceof AgentEvent.ToolExecutionEnd).count()).isEqualTo(1);
		assertThat(events.stream().filter(e -> e instanceof AgentEvent.MessageEnd).count()).isEqualTo(2);
	}

	@Test
	void loopStopsWhenNoToolCalls() {
		var model = new AgentTest.MockStreamingModel("Just a text response");
		var loop = new AgentLoop(model, toolRegistry, "test prompt", "mock", "mock", events::add);

		var messages = new ArrayList<AgentMessage>();
		messages.add(new UserMessage("Hello"));

		loop.run(messages);

		// Should have: user msg + assistant
		assertThat(messages).hasSize(2);
		assertThat(events.stream().filter(e -> e instanceof AgentEvent.ToolExecutionStart).count()).isEqualTo(0);
	}

	@Test
	void toolCallReadsFile() throws IOException {
		Files.writeString(tempDir.resolve("hello.txt"), "Hello World!");

		// Model that calls read tool
		var model = new StreamingChatModel() {
			private final AtomicInteger callCount = new AtomicInteger(0);

			@Override
			public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
				int call = callCount.getAndIncrement();
				if (call == 0) {
					var toolCall = ToolExecutionRequest.builder()
						.id("call_read")
						.name("read")
						.arguments("{\"path\": \"hello.txt\"}")
						.build();
					var aiMessage = AiMessage.builder()
						.toolExecutionRequests(List.of(toolCall))
						.build();
					handler.onCompleteResponse(ChatResponse.builder().aiMessage(aiMessage).build());
				} else {
					handler.onPartialResponse("The file says Hello World!");
					handler.onCompleteResponse(
						ChatResponse.builder().aiMessage(AiMessage.from("The file says Hello World!")).build()
					);
				}
			}
		};

		var loop = new AgentLoop(model, toolRegistry, "test", "mock", "mock", events::add);
		var messages = new ArrayList<AgentMessage>();
		messages.add(new UserMessage("Read hello.txt"));

		loop.run(messages);

		// Tool result should contain the file content
		var toolResults = events.stream()
			.filter(e -> e instanceof AgentEvent.ToolExecutionEnd)
			.map(e -> ((AgentEvent.ToolExecutionEnd) e).result())
			.toList();

		assertThat(toolResults).hasSize(1);
		assertThat(toolResults.getFirst().content()).contains("Hello World!");
		assertThat(toolResults.getFirst().isError()).isFalse();
	}

	@Test
	void unknownToolReturnsError() {
		var model = new StreamingChatModel() {
			private final AtomicInteger callCount = new AtomicInteger(0);

			@Override
			public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
				int call = callCount.getAndIncrement();
				if (call == 0) {
					var toolCall = ToolExecutionRequest.builder()
						.id("call_bad")
						.name("nonexistent_tool")
						.arguments("{}")
						.build();
					var aiMessage = AiMessage.builder()
						.toolExecutionRequests(List.of(toolCall))
						.build();
					handler.onCompleteResponse(ChatResponse.builder().aiMessage(aiMessage).build());
				} else {
					handler.onPartialResponse("OK");
					handler.onCompleteResponse(
						ChatResponse.builder().aiMessage(AiMessage.from("OK")).build()
					);
				}
			}
		};

		var loop = new AgentLoop(model, toolRegistry, "test", "mock", "mock", events::add);
		var messages = new ArrayList<AgentMessage>();
		messages.add(new UserMessage("test"));

		loop.run(messages);

		var toolResults = events.stream()
			.filter(e -> e instanceof AgentEvent.ToolExecutionEnd)
			.map(e -> ((AgentEvent.ToolExecutionEnd) e).result())
			.toList();

		assertThat(toolResults).hasSize(1);
		assertThat(toolResults.getFirst().isError()).isTrue();
		assertThat(toolResults.getFirst().content()).contains("Unknown tool");
	}

	@Test
	void cancellationStopsLoop() {
		var model = new ToolCallingMockModel();
		var loop = new AgentLoop(model, toolRegistry, "test", "mock", "mock", events::add);
		loop.cancel();

		var messages = new ArrayList<AgentMessage>();
		messages.add(new UserMessage("test"));

		loop.run(messages);

		// Should stop immediately, only TurnStart/TurnEnd/AgentEnd events
		assertThat(messages).hasSize(1); // Only the user message
	}
}
