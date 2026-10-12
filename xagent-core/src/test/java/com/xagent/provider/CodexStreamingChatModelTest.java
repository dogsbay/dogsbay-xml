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
package com.xagent.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.xagent.auth.OAuthCredentials;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CodexStreamingChatModelTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private HttpServer backend;
	private String baseUrl;
	private final AtomicReference<JsonNode> lastBody = new AtomicReference<>();
	private final AtomicReference<Map<String, List<String>>> lastHeaders = new AtomicReference<>();
	private final AtomicReference<String> sseResponse = new AtomicReference<>();
	private final AtomicReference<Integer> httpStatus = new AtomicReference<>(200);

	@BeforeEach
	void startBackend() throws IOException {
		backend = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		backend.createContext("/codex/responses", exchange -> {
			lastBody.set(MAPPER.readTree(exchange.getRequestBody().readAllBytes()));
			lastHeaders.set(exchange.getRequestHeaders());
			byte[] body = sseResponse.get().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
			exchange.sendResponseHeaders(httpStatus.get(), body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		backend.start();
		baseUrl = "http://localhost:" + backend.getAddress().getPort();
		httpStatus.set(200);
		sseResponse.set(sse(
			event("response.output_text.delta", "{\"delta\": \"Hello\"}"),
			event("response.output_text.delta", "{\"delta\": \" world\"}"),
			completedEvent("Hello world", null)
		));
	}

	@AfterEach
	void stopBackend() {
		backend.stop(0);
	}

	private CodexStreamingChatModel model() {
		return new CodexStreamingChatModel(
			() -> new OAuthCredentials("access-token", "refresh", Long.MAX_VALUE, "acct-7"),
			"gpt-5.4", baseUrl);
	}

	private static String event(String type, String fieldsJson) {
		try {
			var node = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(fieldsJson);
			node.put("type", type);
			return "data: " + node + "\n\n";
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static String completedEvent(String text, String functionCallJson) {
		var response = MAPPER.createObjectNode();
		response.put("model", "gpt-5.4");
		var output = response.putArray("output");
		if (text != null) {
			var message = output.addObject();
			message.put("type", "message");
			var content = message.putArray("content");
			var part = content.addObject();
			part.put("type", "output_text");
			part.put("text", text);
		}
		if (functionCallJson != null) {
			try {
				output.add(MAPPER.readTree(functionCallJson));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		var usage = response.putObject("usage");
		usage.put("input_tokens", 100);
		usage.put("output_tokens", 20);
		usage.put("total_tokens", 120);
		var event = MAPPER.createObjectNode();
		event.put("type", "response.completed");
		event.set("response", response);
		return "data: " + event + "\n\n";
	}

	private static String sse(String... events) {
		return String.join("", events);
	}

	/** Collects handler callbacks for assertions. */
	private static class CollectingHandler implements StreamingChatResponseHandler {
		final List<String> partials = new CopyOnWriteArrayList<>();
		final CompletableFuture<ChatResponse> complete = new CompletableFuture<>();

		@Override
		public void onPartialResponse(String partialResponse) {
			partials.add(partialResponse);
		}

		@Override
		public void onCompleteResponse(ChatResponse completeResponse) {
			complete.complete(completeResponse);
		}

		@Override
		public void onError(Throwable error) {
			complete.completeExceptionally(error);
		}

		ChatResponse await() throws Exception {
			return complete.get(10, TimeUnit.SECONDS);
		}
	}

	private static ChatRequest simpleRequest() {
		return ChatRequest.builder()
			.messages(List.of(new SystemMessage("Be terse."), UserMessage.from("hi")))
			.build();
	}

	// --- tests ---

	@Test
	void streamsDeltasAndCompletes() throws Exception {
		var handler = new CollectingHandler();

		model().chat(simpleRequest(), handler);
		var response = handler.await();

		assertThat(handler.partials).containsExactly("Hello", " world");
		assertThat(response.aiMessage().text()).isEqualTo("Hello world");
		assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(100);
		assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(20);
	}

	@Test
	void sendsRequiredHeadersAndBodyShape() throws Exception {
		var handler = new CollectingHandler();

		model().chat(simpleRequest(), handler);
		handler.await();

		var headers = lastHeaders.get();
		assertThat(headers.get("Authorization")).containsExactly("Bearer access-token");
		assertThat(headers.get("Chatgpt-account-id")).containsExactly("acct-7");
		assertThat(headers.get("Originator")).containsExactly("xagent");
		assertThat(headers.get("Openai-beta")).containsExactly("responses=experimental");
		assertThat(headers.get("Session_id")).isNotNull();

		var body = lastBody.get();
		assertThat(body.get("model").asText()).isEqualTo("gpt-5.4");
		assertThat(body.get("store").asBoolean()).isFalse();
		assertThat(body.get("stream").asBoolean()).isTrue();
		assertThat(body.get("instructions").asText()).isEqualTo("Be terse.");
		// system message lives in instructions, not in the input array
		assertThat(body.get("input")).hasSize(1);
		var userItem = body.get("input").get(0);
		assertThat(userItem.get("role").asText()).isEqualTo("user");
		assertThat(userItem.get("content").get(0).get("type").asText()).isEqualTo("input_text");
		assertThat(userItem.get("content").get(0).get("text").asText()).isEqualTo("hi");
	}

	@Test
	void mapsToolHistoryAndSpecifications() throws Exception {
		var toolCall = ToolExecutionRequest.builder()
			.id("call-1").name("read").arguments("{\"path\": \"a.txt\"}").build();
		var request = ChatRequest.builder()
			.messages(List.of(
				new SystemMessage("sys"),
				UserMessage.from("read a.txt"),
				AiMessage.builder().text("").toolExecutionRequests(List.of(toolCall)).build(),
				ToolExecutionResultMessage.from(toolCall, "file contents")))
			.toolSpecifications(List.of(ToolSpecification.builder()
				.name("read")
				.description("Read a file")
				.parameters(JsonObjectSchema.builder()
					.addProperties(Map.of("path", JsonStringSchema.builder().build()))
					.required(List.of("path"))
					.build())
				.build()))
			.build();
		var handler = new CollectingHandler();

		model().chat(request, handler);
		handler.await();

		var input = lastBody.get().get("input");
		assertThat(input).hasSize(3);
		assertThat(input.get(0).get("role").asText()).isEqualTo("user");
		assertThat(input.get(1).get("type").asText()).isEqualTo("function_call");
		assertThat(input.get(1).get("call_id").asText()).isEqualTo("call-1");
		assertThat(input.get(1).get("name").asText()).isEqualTo("read");
		assertThat(input.get(2).get("type").asText()).isEqualTo("function_call_output");
		assertThat(input.get(2).get("call_id").asText()).isEqualTo("call-1");
		assertThat(input.get(2).get("output").asText()).isEqualTo("file contents");

		var tools = lastBody.get().get("tools");
		assertThat(tools).hasSize(1);
		assertThat(tools.get(0).get("type").asText()).isEqualTo("function");
		assertThat(tools.get(0).get("name").asText()).isEqualTo("read");
		assertThat(tools.get(0).get("parameters").get("properties").has("path")).isTrue();
		assertThat(lastBody.get().get("tool_choice").asText()).isEqualTo("auto");
		assertThat(lastBody.get().get("parallel_tool_calls").asBoolean()).isTrue();
	}

	@Test
	void parsesFunctionCallsFromCompletedResponse() throws Exception {
		sseResponse.set(sse(completedEvent(null,
			"{\"type\":\"function_call\",\"call_id\":\"call-9\",\"name\":\"grep\",\"arguments\":\"{\\\"pattern\\\":\\\"foo\\\"}\"}")));
		var handler = new CollectingHandler();

		model().chat(simpleRequest(), handler);
		var response = handler.await();

		var calls = response.aiMessage().toolExecutionRequests();
		assertThat(calls).hasSize(1);
		assertThat(calls.get(0).id()).isEqualTo("call-9");
		assertThat(calls.get(0).name()).isEqualTo("grep");
		assertThat(calls.get(0).arguments()).isEqualTo("{\"pattern\":\"foo\"}");
	}

	@Test
	void recoversToolCallFromStreamingEventsWhenCompletedOutputIsEmpty() throws Exception {
		// The Codex backend often streams the function call as events and then
		// completes with an empty output array -- the tool call must survive.
		sseResponse.set(sse(
			event("response.output_item.done",
				"{\"item\":{\"type\":\"function_call\",\"id\":\"fc_1\",\"call_id\":\"call-9\","
					+ "\"name\":\"edit\",\"arguments\":\"{\\\"path\\\":\\\"a.txt\\\"}\"}}"),
			completedEvent(null, null)));
		var handler = new CollectingHandler();

		model().chat(simpleRequest(), handler);
		var response = handler.await();

		var calls = response.aiMessage().toolExecutionRequests();
		assertThat(calls).hasSize(1);
		assertThat(calls.get(0).id()).isEqualTo("call-9");
		assertThat(calls.get(0).name()).isEqualTo("edit");
		assertThat(calls.get(0).arguments()).isEqualTo("{\"path\":\"a.txt\"}");
	}

	@Test
	void assemblesToolCallArgumentsFromStreamingDeltasWhenOutputIsEmpty() throws Exception {
		// added shell -> argument deltas -> done, with an empty completed output.
		sseResponse.set(sse(
			event("response.output_item.added",
				"{\"item\":{\"type\":\"function_call\",\"id\":\"fc_2\",\"call_id\":\"call-3\",\"name\":\"edit\"}}"),
			event("response.function_call_arguments.delta",
				"{\"item_id\":\"fc_2\",\"call_id\":\"call-3\",\"delta\":\"{\\\"path\\\":\"}"),
			event("response.function_call_arguments.delta",
				"{\"item_id\":\"fc_2\",\"call_id\":\"call-3\",\"delta\":\"\\\"b.txt\\\"}\"}"),
			event("response.function_call_arguments.done",
				"{\"item_id\":\"fc_2\",\"call_id\":\"call-3\",\"name\":\"edit\","
					+ "\"arguments\":\"{\\\"path\\\":\\\"b.txt\\\"}\"}"),
			completedEvent(null, null)));
		var handler = new CollectingHandler();

		model().chat(simpleRequest(), handler);
		var response = handler.await();

		var calls = response.aiMessage().toolExecutionRequests();
		assertThat(calls).hasSize(1);
		assertThat(calls.get(0).id()).isEqualTo("call-3");
		assertThat(calls.get(0).name()).isEqualTo("edit");
		assertThat(calls.get(0).arguments()).isEqualTo("{\"path\":\"b.txt\"}");
	}

	@Test
	void recoversStreamedTextWhenCompletedOutputIsEmpty() throws Exception {
		sseResponse.set(sse(
			event("response.output_text.delta", "{\"delta\": \"Done\"}"),
			event("response.output_text.delta", "{\"delta\": \" rewriting.\"}"),
			completedEvent(null, null)));
		var handler = new CollectingHandler();

		model().chat(simpleRequest(), handler);
		var response = handler.await();

		assertThat(handler.partials).containsExactly("Done", " rewriting.");
		assertThat(response.aiMessage().text()).isEqualTo("Done rewriting.");
	}

	@Test
	void prefersCompletedOutputOverStreamStateWhenBothPresent() throws Exception {
		// When output[] is populated it is authoritative; stream state is a fallback only.
		sseResponse.set(sse(
			event("response.output_text.delta", "{\"delta\": \"partial\"}"),
			completedEvent("final text", null)));
		var handler = new CollectingHandler();

		model().chat(simpleRequest(), handler);
		var response = handler.await();

		assertThat(response.aiMessage().text()).isEqualTo("final text");
	}

	@Test
	void recoversAccumulatedToolCallWhenStreamEndsWithoutCompleted() throws Exception {
		// The connection drops after the tool call fully arrived but before
		// response.completed -- the call must be recovered, not discarded.
		sseResponse.set(sse(
			event("response.output_item.done",
				"{\"item\":{\"type\":\"function_call\",\"id\":\"fc_7\",\"call_id\":\"call-7\","
					+ "\"name\":\"edit\",\"arguments\":\"{\\\"path\\\":\\\"a.txt\\\"}\"}}")));
		var handler = new CollectingHandler();

		model().chat(simpleRequest(), handler);
		var response = handler.await();

		var calls = response.aiMessage().toolExecutionRequests();
		assertThat(calls).hasSize(1);
		assertThat(calls.get(0).id()).isEqualTo("call-7");
		assertThat(calls.get(0).name()).isEqualTo("edit");
	}

	@Test
	void recoversTextFromMessageItemWhenNoDeltasAndEmptyOutput() throws Exception {
		// A text-only turn arrives as a completed message item with no deltas.
		sseResponse.set(sse(
			event("response.output_item.done",
				"{\"item\":{\"type\":\"message\",\"role\":\"assistant\",\"content\":"
					+ "[{\"type\":\"output_text\",\"text\":\"Hi there.\"}]}}"),
			completedEvent(null, null)));
		var handler = new CollectingHandler();

		model().chat(simpleRequest(), handler);
		var response = handler.await();

		assertThat(response.aiMessage().text()).isEqualTo("Hi there.");
	}

	@Test
	void recoveredToolCallFallsBackToItemIdWhenCallIdAbsent() throws Exception {
		sseResponse.set(sse(
			event("response.output_item.done",
				"{\"item\":{\"type\":\"function_call\",\"id\":\"fc_noCallId\",\"name\":\"grep\",\"arguments\":\"{}\"}}"),
			completedEvent(null, null)));
		var handler = new CollectingHandler();

		model().chat(simpleRequest(), handler);
		var response = handler.await();

		var calls = response.aiMessage().toolExecutionRequests();
		assertThat(calls).hasSize(1);
		assertThat(calls.get(0).id()).isEqualTo("fc_noCallId");
	}

	@Test
	void emptyDoneArgumentsDoNotWipeDeltaAssembledArguments() throws Exception {
		sseResponse.set(sse(
			event("response.output_item.added",
				"{\"item\":{\"type\":\"function_call\",\"id\":\"fc_d\",\"call_id\":\"call-d\",\"name\":\"edit\"}}"),
			event("response.function_call_arguments.delta",
				"{\"item_id\":\"fc_d\",\"call_id\":\"call-d\",\"delta\":\"{\\\"path\\\":\\\"x.txt\\\"}\"}"),
			event("response.function_call_arguments.done",
				"{\"item_id\":\"fc_d\",\"call_id\":\"call-d\",\"name\":\"edit\",\"arguments\":\"\"}"),
			completedEvent(null, null)));
		var handler = new CollectingHandler();

		model().chat(simpleRequest(), handler);
		var response = handler.await();

		var calls = response.aiMessage().toolExecutionRequests();
		assertThat(calls).hasSize(1);
		assertThat(calls.get(0).arguments()).isEqualTo("{\"path\":\"x.txt\"}");
	}

	@Test
	void surfacesHttpErrors() {
		httpStatus.set(401);
		sseResponse.set("{\"detail\": \"token expired\"}");
		var handler = new CollectingHandler();

		model().chat(simpleRequest(), handler);

		assertThat(handler.complete)
			.failsWithin(java.time.Duration.ofSeconds(10))
			.withThrowableOfType(java.util.concurrent.ExecutionException.class)
			.withMessageContaining("401");
	}

	@Test
	void surfacesStreamErrorEvents() {
		sseResponse.set(sse(event("error", "{\"message\": \"server_is_overloaded\"}")));
		var handler = new CollectingHandler();

		model().chat(simpleRequest(), handler);

		assertThat(handler.complete)
			.failsWithin(java.time.Duration.ofSeconds(10))
			.withThrowableOfType(java.util.concurrent.ExecutionException.class)
			.withMessageContaining("server_is_overloaded");
	}

	@Test
	void surfacesFailedResponses() {
		sseResponse.set(sse(event("response.failed",
			"{\"response\": {\"error\": {\"message\": \"quota exceeded\"}}}")));
		var handler = new CollectingHandler();

		model().chat(simpleRequest(), handler);

		assertThat(handler.complete)
			.failsWithin(java.time.Duration.ofSeconds(10))
			.withThrowableOfType(java.util.concurrent.ExecutionException.class)
			.withMessageContaining("quota exceeded");
	}

	@Test
	void surfacesMissingCredentials() {
		var model = new CodexStreamingChatModel(
			() -> { throw new IllegalStateException("Not signed in"); },
			"gpt-5.4", baseUrl);
		var handler = new CollectingHandler();

		model.chat(simpleRequest(), handler);

		assertThat(handler.complete)
			.failsWithin(java.time.Duration.ofSeconds(10))
			.withThrowableOfType(java.util.concurrent.ExecutionException.class)
			.withMessageContaining("Not signed in");
	}

	@Test
	void instructionsFallBackWhenNoSystemMessage() throws Exception {
		var handler = new CollectingHandler();

		model().chat(ChatRequest.builder().messages(List.of(UserMessage.from("hi"))).build(), handler);
		handler.await();

		assertThat(lastBody.get().get("instructions").asText()).isNotEmpty();
	}
}
