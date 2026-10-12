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
import com.xagent.provider.ProviderConfig;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.data.message.AiMessage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTest {

	/**
	 * A mock streaming model that returns a predefined response in chunks.
	 */
	static class MockStreamingModel implements StreamingChatModel {
		private final String response;

		MockStreamingModel(String response) {
			this.response = response;
		}

		@Override
		public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
			// Simulate streaming by sending the response word by word
			String[] words = response.split(" ");
			for (int i = 0; i < words.length; i++) {
				String chunk = (i > 0 ? " " : "") + words[i];
				handler.onPartialResponse(chunk);
			}

			ChatResponse chatResponse = ChatResponse.builder()
				.aiMessage(AiMessage.from(response))
				.build();
			handler.onCompleteResponse(chatResponse);
		}
	}

	private Agent createAgent(String modelResponse) {
		var model = new MockStreamingModel(modelResponse);
		var config = ProviderConfig.resolve("openai", "mock-model", "mock-key", null);
		return new Agent(model, config);
	}

	@Test
	void promptAddsMessagesToHistory() {
		var agent = createAgent("Hello there!");
		agent.prompt("Hi");

		assertThat(agent.messages()).hasSize(2);
		assertThat(agent.messages().get(0).role()).isEqualTo("user");
		assertThat(agent.messages().get(1).role()).isEqualTo("assistant");
	}

	@Test
	void promptEmitsEvents() {
		var agent = createAgent("Hello world");
		var events = new ArrayList<AgentEvent>();
		agent.subscribe(events::add);

		agent.prompt("Hi");

		assertThat(events).isNotEmpty();
		assertThat(events.getFirst()).isInstanceOf(AgentEvent.TurnStart.class);
		assertThat(events.getLast()).isInstanceOf(AgentEvent.AgentEnd.class);

		// Should have updates in between
		long updateCount = events.stream()
			.filter(e -> e instanceof AgentEvent.MessageUpdate)
			.count();
		assertThat(updateCount).isGreaterThan(0);
	}

	@Test
	void streamedTextAssembledCorrectly() {
		var agent = createAgent("This is a test response");
		var updates = new ArrayList<String>();
		agent.subscribe(event -> {
			if (event instanceof AgentEvent.MessageUpdate u) {
				updates.add(u.partialText());
			}
		});

		agent.prompt("test");

		String assembled = String.join("", updates);
		assertThat(assembled).isEqualTo("This is a test response");
	}

	@Test
	void multiTurnConversation() {
		var agent = createAgent("Response");

		agent.prompt("First message");
		agent.prompt("Second message");

		assertThat(agent.messages()).hasSize(4); // 2 user + 2 assistant
	}

	@Test
	void clearHistoryRemovesMessages() {
		var agent = createAgent("Response");
		agent.prompt("Hello");

		assertThat(agent.messages()).hasSize(2);

		agent.clearHistory();
		assertThat(agent.messages()).isEmpty();
	}

	@Test
	void unsubscribeStopsEvents() {
		var agent = createAgent("Response");
		var events = new ArrayList<AgentEvent>();
		Consumer<AgentEvent> listener = events::add;
		agent.subscribe(listener);
		agent.unsubscribe(listener);

		agent.prompt("test");

		assertThat(events).isEmpty();
	}

	@Test
	void errorInSubscriberDoesNotCrashAgent() {
		var agent = createAgent("Response");
		agent.subscribe(event -> {
			throw new RuntimeException("Subscriber error");
		});

		// Should not throw
		agent.prompt("test");
		assertThat(agent.messages()).hasSize(2);
	}
}
