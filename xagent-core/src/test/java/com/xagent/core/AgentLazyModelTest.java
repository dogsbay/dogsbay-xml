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

import com.xagent.provider.ProviderConfig;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lazy provider resolution: the agent must start without credentials (so
 * /login can run) and only create the model on first prompt.
 */
class AgentLazyModelTest {

	/** Minimal model that ends the turn immediately with no tool calls. */
	static class ImmediateModel implements StreamingChatModel {
		@Override
		public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
			handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());
		}
	}

	private static ProviderConfig config() {
		return ProviderConfig.resolve("openai", "mock", "key", null);
	}

	@Test
	void constructionDoesNotInvokeSupplier() {
		var calls = new AtomicInteger();
		Supplier<StreamingChatModel> supplier = () -> {
			calls.incrementAndGet();
			return new ImmediateModel();
		};

		new Agent(supplier, config(), "sys", new com.xagent.tool.ToolRegistry());

		// the model is created lazily, not at construction
		assertThat(calls.get()).isZero();
	}

	@Test
	void supplierFailureSurfacesAtPromptNotConstruction() {
		Supplier<StreamingChatModel> failing = () -> {
			throw new IllegalStateException("Not signed in to ChatGPT");
		};
		// construction succeeds even though credentials are missing
		var agent = new Agent(failing, config(), "sys", new com.xagent.tool.ToolRegistry());

		assertThatThrownBy(() -> agent.prompt("hi"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Not signed in");

		// history was not mutated by the failed attempt
		assertThat(agent.messages()).isEmpty();
	}

	@Test
	void modelIsResolvedOnceAndCached() {
		var calls = new AtomicInteger();
		Supplier<StreamingChatModel> supplier = () -> {
			calls.incrementAndGet();
			return new ImmediateModel();
		};
		var agent = new Agent(supplier, config(), "sys", new com.xagent.tool.ToolRegistry());

		agent.prompt("one");
		agent.prompt("two");

		assertThat(calls.get()).isEqualTo(1);
	}

	@Test
	void invalidateModelForcesReresolution() {
		var calls = new AtomicInteger();
		Supplier<StreamingChatModel> supplier = () -> {
			calls.incrementAndGet();
			return new ImmediateModel();
		};
		var agent = new Agent(supplier, config(), "sys", new com.xagent.tool.ToolRegistry());

		agent.prompt("one");
		agent.invalidateModel();   // e.g. after /login
		agent.prompt("two");

		assertThat(calls.get()).isEqualTo(2);
	}

	@Test
	void setProviderConfigSwitchesAndReresolves() {
		var calls = new AtomicInteger();
		Supplier<StreamingChatModel> supplier = () -> {
			calls.incrementAndGet();
			return new ImmediateModel();
		};
		var agent = new Agent(supplier, config(), "sys", new com.xagent.tool.ToolRegistry());

		agent.prompt("one");                       // resolves once
		var ollama = ProviderConfig.resolve("ollama", null, null, null);
		agent.setProviderConfig(ollama);           // switch provider

		assertThat(agent.config().provider()).isEqualTo("ollama");
		agent.prompt("two");                       // must re-resolve under new config
		assertThat(calls.get()).isEqualTo(2);
	}

	@Test
	void retryAfterLoginSucceeds() {
		// first prompt fails (not signed in); after "login" the supplier works
		var loggedIn = new java.util.concurrent.atomic.AtomicBoolean(false);
		Supplier<StreamingChatModel> supplier = () -> {
			if (!loggedIn.get()) throw new IllegalStateException("Not signed in");
			return new ImmediateModel();
		};
		var agent = new Agent(supplier, config(), "sys", new com.xagent.tool.ToolRegistry());

		assertThatThrownBy(() -> agent.prompt("hi")).hasMessageContaining("Not signed in");

		loggedIn.set(true);
		agent.invalidateModel();
		agent.prompt("now it works");

		assertThat(agent.messages()).isNotEmpty();
	}
}
