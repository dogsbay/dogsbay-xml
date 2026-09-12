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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xagent.auth.CodexOAuth;
import com.xagent.auth.OAuthCredentials;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * StreamingChatModel for the ChatGPT-subscription Codex backend
 * ({@code https://chatgpt.com/backend-api/codex/responses}). Speaks the
 * Responses API dialect over SSE with OAuth bearer auth — the endpoint
 * behind "Sign in with ChatGPT" (see plans/codex-auth.md).
 *
 * Endpoint contract (learned from openai/codex, pi, opencode):
 * {@code store: false} always, the system prompt goes in the mandatory
 * {@code instructions} field (not the input array), history carries no
 * server item ids, and tool calls round-trip via {@code call_id}.
 */
public class CodexStreamingChatModel implements StreamingChatModel {

	public static final String DEFAULT_BASE_URL = "https://chatgpt.com/backend-api";

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);

	private final Supplier<OAuthCredentials> credentials;
	private final String model;
	private final String baseUrl;
	private final String sessionId = UUID.randomUUID().toString();
	private final HttpClient http;

	/**
	 * @param credentials supplies fresh credentials per request (refreshes
	 *                    behind the scenes); must throw IllegalStateException
	 *                    when the user is not logged in
	 * @param model       subscription model name (e.g. gpt-5.4)
	 * @param baseUrl     backend base URL (injectable for tests)
	 */
	public CodexStreamingChatModel(Supplier<OAuthCredentials> credentials, String model, String baseUrl) {
		this.credentials = credentials;
		this.model = model;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
	}

	@Override
	public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
		Thread.ofVirtual().name("codex-chat").start(() -> {
			try {
				execute(request, handler);
			} catch (Throwable t) {
				handler.onError(t);
			}
		});
	}

	private void execute(ChatRequest request, StreamingChatResponseHandler handler) throws IOException, InterruptedException {
		OAuthCredentials auth = credentials.get();
		ObjectNode body = buildRequestBody(request);

		var httpRequest = HttpRequest.newBuilder()
			.uri(URI.create(baseUrl + "/codex/responses"))
			.timeout(REQUEST_TIMEOUT)
			.header("Authorization", "Bearer " + auth.accessToken())
			.header("ChatGPT-Account-Id", auth.accountId() != null ? auth.accountId() : "")
			.header("originator", CodexOAuth.ORIGINATOR)
			.header("User-Agent", userAgent())
			.header("OpenAI-Beta", "responses=experimental")
			.header("session_id", sessionId)
			.header("Accept", "text/event-stream")
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
			.build();

		HttpResponse<InputStream> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200) {
			String error = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
			throw new IOException("Codex backend HTTP " + response.statusCode() + ": " + truncate(error, 500));
		}

		readSseStream(response.body(), handler);
	}

	// --- request building ---

	ObjectNode buildRequestBody(ChatRequest request) {
		ObjectNode body = MAPPER.createObjectNode();
		body.put("model", model);
		body.put("store", false);
		body.put("stream", true);
		// instructions is mandatory on this backend; system messages go here
		body.put("instructions", extractInstructions(request.messages()));

		ArrayNode input = body.putArray("input");
		for (ChatMessage message : request.messages()) {
			appendInputItems(input, message);
		}

		List<ToolSpecification> tools = request.toolSpecifications();
		if (tools != null && !tools.isEmpty()) {
			ArrayNode toolArray = body.putArray("tools");
			for (ToolSpecification tool : tools) {
				ObjectNode entry = toolArray.addObject();
				entry.put("type", "function");
				entry.put("name", tool.name());
				entry.put("description", tool.description() != null ? tool.description() : "");
				entry.put("strict", false);
				entry.set("parameters", tool.parameters() != null
					? MAPPER.valueToTree(JsonSchemaElementUtils.toMap(tool.parameters()))
					: MAPPER.createObjectNode().put("type", "object"));
			}
			body.put("tool_choice", "auto");
			body.put("parallel_tool_calls", true);
		}
		return body;
	}

	private static String extractInstructions(List<ChatMessage> messages) {
		var sb = new StringBuilder();
		for (ChatMessage message : messages) {
			if (message instanceof SystemMessage system) {
				if (!sb.isEmpty()) sb.append("\n\n");
				sb.append(system.text());
			}
		}
		// the backend rejects requests without instructions
		return sb.isEmpty() ? "You are a helpful coding agent." : sb.toString();
	}

	private static void appendInputItems(ArrayNode input, ChatMessage message) {
		switch (message) {
			case SystemMessage ignored -> {
				// carried in the instructions field instead
			}
			case UserMessage user -> {
				ObjectNode item = input.addObject();
				item.put("type", "message");
				item.put("role", "user");
				ArrayNode content = item.putArray("content");
				for (Content part : user.contents()) {
					switch (part) {
						case TextContent text -> {
							ObjectNode node = content.addObject();
							node.put("type", "input_text");
							node.put("text", text.text());
						}
						case ImageContent image -> {
							ObjectNode node = content.addObject();
							node.put("type", "input_image");
							node.put("image_url", image.image().url() != null
								? image.image().url().toString()
								: "data:" + image.image().mimeType() + ";base64," + image.image().base64Data());
						}
						default -> {
							// unsupported content type -- skip
						}
					}
				}
			}
			case AiMessage ai -> {
				if (ai.text() != null && !ai.text().isBlank()) {
					ObjectNode item = input.addObject();
					item.put("type", "message");
					item.put("role", "assistant");
					ArrayNode content = item.putArray("content");
					ObjectNode node = content.addObject();
					node.put("type", "output_text");
					node.put("text", ai.text());
				}
				if (ai.hasToolExecutionRequests()) {
					for (ToolExecutionRequest call : ai.toolExecutionRequests()) {
						ObjectNode item = input.addObject();
						item.put("type", "function_call");
						item.put("call_id", call.id());
						item.put("name", call.name());
						item.put("arguments", call.arguments() != null ? call.arguments() : "{}");
					}
				}
			}
			case ToolExecutionResultMessage result -> {
				ObjectNode item = input.addObject();
				item.put("type", "function_call_output");
				item.put("call_id", result.id());
				item.put("output", result.text() != null ? result.text() : "");
			}
			default -> {
				// CustomMessage etc. -- not produced by xagent
			}
		}
	}

	// --- response streaming ---

	/**
	 * Accumulates state across SSE events for a single response. The Codex
	 * backend often delivers the assistant message and function calls ONLY as
	 * streaming events and leaves {@code response.completed}'s {@code output}
	 * array empty (see openai/codex, hermes-agent #5732/#5678). We therefore
	 * rebuild the final message from these accumulated events whenever
	 * {@code output} is empty, so tool calls are never lost.
	 */
	private static final class StreamState {
		// text from response.output_text.delta events
		final StringBuilder text = new StringBuilder();
		// text carried on an output_item.done "message" item (some turns send no deltas)
		final StringBuilder itemText = new StringBuilder();
		// function calls keyed by streaming item_id, in arrival order
		final java.util.LinkedHashMap<String, FunctionCall> calls = new java.util.LinkedHashMap<>();

		FunctionCall call(String itemId) {
			return calls.computeIfAbsent(itemId, k -> new FunctionCall());
		}

		boolean isEmpty() {
			return text.isEmpty() && itemText.isEmpty() && calls.isEmpty();
		}
	}

	private static final class FunctionCall {
		String callId;
		String name;
		final StringBuilder arguments = new StringBuilder();
	}

	private void readSseStream(InputStream stream, StreamingChatResponseHandler handler) throws IOException {
		var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
		var data = new StringBuilder();
		var state = new StreamState();
		String line;
		while ((line = reader.readLine()) != null) {
			if (line.startsWith("data:")) {
				data.append(line.substring(5).strip());
			} else if (line.isEmpty() && !data.isEmpty()) {
				if (handleEvent(data.toString(), handler, state)) {
					return;
				}
				data.setLength(0);
			}
		}
		if (!data.isEmpty() && handleEvent(data.toString(), handler, state)) {
			return;
		}
		// The connection ended without a response.completed event. If the stream
		// already delivered text or fully-formed tool calls, recover them rather
		// than discarding a turn whose work had actually arrived.
		if (!state.isEmpty()) {
			handler.onCompleteResponse(toChatResponse(MAPPER.createObjectNode(), state));
			return;
		}
		throw new IOException("Codex stream ended without a completed response");
	}

	/** Returns true when the stream is finished (completed or failed). */
	private boolean handleEvent(String json, StreamingChatResponseHandler handler, StreamState state) throws IOException {
		if ("[DONE]".equals(json)) {
			return false;
		}
		JsonNode event = MAPPER.readTree(json);
		String type = event.path("type").asText("");
		switch (type) {
			case "response.output_text.delta" -> {
				String delta = event.path("delta").asText("");
				if (!delta.isEmpty()) {
					state.text.append(delta);
					handler.onPartialResponse(delta);
				}
			}
			case "response.output_item.added", "response.output_item.done" -> accumulateItem(event.path("item"), state);
			case "response.function_call_arguments.delta" -> {
				FunctionCall call = state.call(event.path("item_id").asText(""));
				if (call.callId == null && event.hasNonNull("call_id")) {
					call.callId = event.path("call_id").asText(null);
				}
				call.arguments.append(event.path("delta").asText(""));
			}
			case "response.function_call_arguments.done" -> {
				FunctionCall call = state.call(event.path("item_id").asText(""));
				if (event.hasNonNull("call_id")) call.callId = event.path("call_id").asText(call.callId);
				if (event.hasNonNull("name")) call.name = event.path("name").asText(call.name);
				// the .done event carries the authoritative final arguments; ignore a
				// present-but-empty value so it can't wipe delta-assembled arguments
				String args = event.path("arguments").asText(null);
				if (args != null && !args.isEmpty()) {
					call.arguments.setLength(0);
					call.arguments.append(args);
				}
			}
			case "response.completed" -> {
				handler.onCompleteResponse(toChatResponse(event.path("response"), state));
				return true;
			}
			case "response.failed" -> throw new IOException("Codex response failed: "
				+ event.path("response").path("error").path("message").asText("unknown error"));
			case "error" -> throw new IOException("Codex stream error: "
				+ event.path("message").asText(event.path("code").asText("unknown")));
			default -> {
				// reasoning summaries, content_part events, etc. -- ignored
			}
		}
		return false;
	}

	/** Records an output item (function_call shell or assistant message text) into the stream state. */
	private static void accumulateItem(JsonNode item, StreamState state) {
		switch (item.path("type").asText("")) {
			case "function_call" -> {
				// the streaming item_id keys the call; output_item.done is authoritative
				String itemId = item.path("id").asText("");
				FunctionCall call = state.call(itemId);
				if (item.hasNonNull("call_id")) call.callId = item.path("call_id").asText(call.callId);
				if (item.hasNonNull("name")) call.name = item.path("name").asText(call.name);
				String args = item.path("arguments").asText(null);
				if (args != null && !args.isEmpty()) {
					call.arguments.setLength(0);
					call.arguments.append(args);
				}
			}
			case "message" -> {
				// A text-only turn may arrive as a completed message item with no
				// output_text.delta events; capture it so the reply isn't lost. Kept
				// separate from delta text so the two can't double-count.
				var sb = new StringBuilder();
				for (JsonNode part : item.path("content")) {
					if ("output_text".equals(part.path("type").asText())) {
						sb.append(part.path("text").asText(""));
					}
				}
				if (sb.length() > 0) {
					state.itemText.setLength(0);
					state.itemText.append(sb);
				}
			}
			default -> {
				// reasoning items etc. -- ignored
			}
		}
	}

	private ChatResponse toChatResponse(JsonNode response, StreamState state) {
		var text = new StringBuilder();
		var toolCalls = new ArrayList<ToolExecutionRequest>();
		for (JsonNode item : response.path("output")) {
			switch (item.path("type").asText("")) {
				case "message" -> {
					for (JsonNode part : item.path("content")) {
						if ("output_text".equals(part.path("type").asText())) {
							text.append(part.path("text").asText(""));
						}
					}
				}
				case "function_call" -> toolCalls.add(ToolExecutionRequest.builder()
					.id(item.path("call_id").asText(item.path("id").asText("")))
					.name(item.path("name").asText(""))
					.arguments(item.path("arguments").asText("{}"))
					.build());
				default -> {
					// reasoning items etc.
				}
			}
		}

		// The Codex backend frequently completes with an empty output array and
		// delivers everything through streaming events instead. Fall back to the
		// accumulated stream state so text and (critically) tool calls survive.
		if (text.isEmpty()) {
			// prefer streamed deltas; otherwise a message item carried the whole reply
			text.append(state.text.length() > 0 ? state.text : state.itemText);
		}
		if (toolCalls.isEmpty()) {
			for (var entry : state.calls.entrySet()) {
				FunctionCall call = entry.getValue();
				if (call.name == null || call.name.isBlank()) {
					continue;
				}
				// mirror the output[] path: call_id, then the item id, then ""
				String id = call.callId != null ? call.callId : entry.getKey();
				toolCalls.add(ToolExecutionRequest.builder()
					.id(id)
					.name(call.name)
					.arguments(call.arguments.length() > 0 ? call.arguments.toString() : "{}")
					.build());
			}
		}

		var messageBuilder = AiMessage.builder().text(text.toString());
		if (!toolCalls.isEmpty()) {
			messageBuilder.toolExecutionRequests(toolCalls);
		}

		var responseBuilder = ChatResponse.builder()
			.aiMessage(messageBuilder.build())
			.modelName(response.path("model").asText(model));
		JsonNode usage = response.path("usage");
		if (usage.isObject()) {
			responseBuilder.tokenUsage(new TokenUsage(
				usage.path("input_tokens").asInt(0),
				usage.path("output_tokens").asInt(0),
				usage.path("total_tokens").asInt(0)));
		}
		return responseBuilder.build();
	}

	private static String userAgent() {
		return "xagent (" + System.getProperty("os.name") + " "
			+ System.getProperty("os.version") + "; " + System.getProperty("os.arch") + ")";
	}

	private static String truncate(String text, int maxLen) {
		return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
	}
}
