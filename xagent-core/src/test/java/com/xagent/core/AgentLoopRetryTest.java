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
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLoopRetryTest {

	/**
	 * Model that fails N times then succeeds.
	 */
	static class FailThenSucceedModel implements StreamingChatModel {
		private final AtomicInteger callCount = new AtomicInteger(0);
		private final int failCount;

		FailThenSucceedModel(int failCount) {
			this.failCount = failCount;
		}

		int totalCalls() {
			return callCount.get();
		}

		@Override
		public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
			int call = callCount.getAndIncrement();
			if (call < failCount) {
				handler.onError(new RuntimeException("Server error 500"));
			} else {
				handler.onPartialResponse("Success!");
				handler.onCompleteResponse(
					ChatResponse.builder().aiMessage(AiMessage.from("Success!")).build()
				);
			}
		}
	}

	@Test
	void retriesOnErrorAndSucceeds() {
		var events = new ArrayList<AgentEvent>();
		var model = new FailThenSucceedModel(2); // Fail twice, succeed on 3rd
		var retryConfig = new RetryConfig(3, 10, 100); // Short delays for test
		var loop = new AgentLoop(model, new ToolRegistry(), "test", "mock", "mock",
			events::add, true, retryConfig);

		var messages = new ArrayList<AgentMessage>();
		messages.add(new UserMessage("Hello"));
		loop.run(messages);

		// Should succeed after retries
		assertThat(messages).hasSize(2); // user + assistant
		assertThat(messages.get(1).role()).isEqualTo("assistant");

		// Should have emitted retry events
		var retryEvents = events.stream()
			.filter(e -> e instanceof AgentEvent.RetryAttempt)
			.map(e -> (AgentEvent.RetryAttempt) e)
			.toList();
		assertThat(retryEvents).hasSize(2);
		assertThat(retryEvents.get(0).attempt()).isEqualTo(1);
		assertThat(retryEvents.get(1).attempt()).isEqualTo(2);

		// Model should have been called 3 times total
		assertThat(model.totalCalls()).isEqualTo(3);
	}

	@Test
	void givesUpAfterMaxRetries() {
		var events = new ArrayList<AgentEvent>();
		var model = new FailThenSucceedModel(10); // Always fails
		var retryConfig = new RetryConfig(2, 10, 100);
		var loop = new AgentLoop(model, new ToolRegistry(), "test", "mock", "mock",
			events::add, true, retryConfig);

		var messages = new ArrayList<AgentMessage>();
		messages.add(new UserMessage("Hello"));
		loop.run(messages);

		// Should not have added an assistant message
		assertThat(messages).hasSize(1);

		// Should have retried maxRetries times
		var retryEvents = events.stream()
			.filter(e -> e instanceof AgentEvent.RetryAttempt)
			.toList();
		assertThat(retryEvents).hasSize(2);

		// 3 total calls: initial + 2 retries
		assertThat(model.totalCalls()).isEqualTo(3);
	}

	@Test
	void noRetryWhenConfiguredOff() {
		var events = new ArrayList<AgentEvent>();
		var model = new FailThenSucceedModel(1);
		var loop = new AgentLoop(model, new ToolRegistry(), "test", "mock", "mock",
			events::add, true, RetryConfig.NONE);

		var messages = new ArrayList<AgentMessage>();
		messages.add(new UserMessage("Hello"));
		loop.run(messages);

		// Should fail without retry
		assertThat(messages).hasSize(1);
		assertThat(model.totalCalls()).isEqualTo(1);

		var retryEvents = events.stream()
			.filter(e -> e instanceof AgentEvent.RetryAttempt)
			.toList();
		assertThat(retryEvents).isEmpty();
	}

	@Test
	void retryDelayIncreases() {
		var events = new ArrayList<AgentEvent>();
		var model = new FailThenSucceedModel(3);
		var retryConfig = new RetryConfig(3, 100, 10000);
		var loop = new AgentLoop(model, new ToolRegistry(), "test", "mock", "mock",
			events::add, true, retryConfig);

		var messages = new ArrayList<AgentMessage>();
		messages.add(new UserMessage("Hello"));
		loop.run(messages);

		var retryEvents = events.stream()
			.filter(e -> e instanceof AgentEvent.RetryAttempt)
			.map(e -> (AgentEvent.RetryAttempt) e)
			.toList();

		assertThat(retryEvents).hasSize(3);
		// Delays should increase: 100, 200, 400
		assertThat(retryEvents.get(0).delayMs()).isEqualTo(100);
		assertThat(retryEvents.get(1).delayMs()).isEqualTo(200);
		assertThat(retryEvents.get(2).delayMs()).isEqualTo(400);
	}

	@Test
	void retryDelayCappedAtMax() {
		var events = new ArrayList<AgentEvent>();
		var model = new FailThenSucceedModel(4);
		var retryConfig = new RetryConfig(4, 1000, 2000);
		var loop = new AgentLoop(model, new ToolRegistry(), "test", "mock", "mock",
			events::add, true, retryConfig);

		var messages = new ArrayList<AgentMessage>();
		messages.add(new UserMessage("Hello"));
		loop.run(messages);

		var retryEvents = events.stream()
			.filter(e -> e instanceof AgentEvent.RetryAttempt)
			.map(e -> (AgentEvent.RetryAttempt) e)
			.toList();

		// Delays: 1000, 2000, 2000, 2000 (capped at maxDelayMs)
		assertThat(retryEvents.get(0).delayMs()).isEqualTo(1000);
		assertThat(retryEvents.get(1).delayMs()).isEqualTo(2000);
		assertThat(retryEvents.get(2).delayMs()).isEqualTo(2000);
		assertThat(retryEvents.get(3).delayMs()).isEqualTo(2000);
	}


	/** Model that always fails with the given error message. */
	static class AlwaysFailsModel implements StreamingChatModel {
		private final AtomicInteger callCount = new AtomicInteger(0);
		private final String message;

		AlwaysFailsModel(String message) {
			this.message = message;
		}

		int totalCalls() {
			return callCount.get();
		}

		@Override
		public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
			callCount.incrementAndGet();
			handler.onError(new RuntimeException(message));
		}
	}

	private static int callsUntilGivingUp(String errorMessage) {
		var model = new AlwaysFailsModel(errorMessage);
		var loop = new AgentLoop(model, new ToolRegistry(), "test", "mock", "mock",
			e -> { }, true, new RetryConfig(3, 10, 100));

		var messages = new ArrayList<AgentMessage>();
		messages.add(new UserMessage("Hello"));
		loop.run(messages);
		return model.totalCalls();
	}

	/**
	 * A refused request is refused the same way every time. Asking the Codex
	 * backend for a model a ChatGPT account cannot use printed the same 400 four
	 * times before the one line that said what to fix.
	 */
	@Test
	void doesNotRetryARequestTheServerRefused() {
		assertThat(callsUntilGivingUp("Codex backend HTTP 400: {\"detail\":\"The 'gpt-5.4' "
			+ "model is not supported when using Codex with a ChatGPT account.\"}")).isEqualTo(1);
		assertThat(callsUntilGivingUp("HTTP 401: token expired")).isEqualTo(1);
	}

	/**
	 * The provider echoes the response body into the same message, so a 5xx can
	 * quote a 4xx from further upstream. Only the status the error itself
	 * reports decides whether trying again can help.
	 */
	@Test
	void aFourHundredQuotedInsideAFiveHundredBodyStillRetries() {
		assertThat(callsUntilGivingUp("Codex backend HTTP 502: "
			+ "{\"error\":\"upstream returned HTTP 404 for /codex/responses\"}")).isEqualTo(4);
	}

	@Test
	void stillRetriesWhatMightSucceedNextTime() {
		// 5xx is the server having a bad moment…
		assertThat(callsUntilGivingUp("Gateway error: HTTP 503")).isEqualTo(4);
		// …and these two 4xx codes mean "later", which is what a retry is.
		assertThat(callsUntilGivingUp("HTTP 429 rate limited")).isEqualTo(4);
		assertThat(callsUntilGivingUp("HTTP 408 request timeout")).isEqualTo(4);
	}
}
