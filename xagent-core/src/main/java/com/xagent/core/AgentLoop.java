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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xagent.event.AgentEvent;
import com.xagent.message.AgentMessage;
import com.xagent.message.AssistantMessage;
import com.xagent.message.ToolResultMessage;
import com.xagent.tool.AgentTool;
import com.xagent.tool.AgentToolResult;
import com.xagent.tool.ToolRegistry;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The core agentic loop: stream response, execute tool calls, repeat.
 * Supports parallel tool execution and automatic retry with backoff.
 */
public class AgentLoop {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final int MAX_ITERATIONS = 50;

	private final StreamingChatModel model;
	private final ToolRegistry toolRegistry;
	private final String systemPrompt;
	private final String modelName;
	private final String providerName;
	private final Consumer<AgentEvent> emitter;
	private final boolean parallelTools;
	private final RetryConfig retryConfig;
	private final UsageStats usageStats;
	private final ToolHooks toolHooks;
	private volatile boolean cancelled = false;

	public AgentLoop(
		StreamingChatModel model,
		ToolRegistry toolRegistry,
		String systemPrompt,
		String modelName,
		String providerName,
		Consumer<AgentEvent> emitter
	) {
		this(model, toolRegistry, systemPrompt, modelName, providerName, emitter, true, RetryConfig.DEFAULT, null, null);
	}

	public AgentLoop(
		StreamingChatModel model,
		ToolRegistry toolRegistry,
		String systemPrompt,
		String modelName,
		String providerName,
		Consumer<AgentEvent> emitter,
		boolean parallelTools,
		RetryConfig retryConfig
	) {
		this(model, toolRegistry, systemPrompt, modelName, providerName, emitter, parallelTools, retryConfig, null, null);
	}

	public AgentLoop(
		StreamingChatModel model,
		ToolRegistry toolRegistry,
		String systemPrompt,
		String modelName,
		String providerName,
		Consumer<AgentEvent> emitter,
		boolean parallelTools,
		RetryConfig retryConfig,
		UsageStats usageStats
	) {
		this(model, toolRegistry, systemPrompt, modelName, providerName, emitter, parallelTools, retryConfig, usageStats, null);
	}

	public AgentLoop(
		StreamingChatModel model,
		ToolRegistry toolRegistry,
		String systemPrompt,
		String modelName,
		String providerName,
		Consumer<AgentEvent> emitter,
		boolean parallelTools,
		RetryConfig retryConfig,
		UsageStats usageStats,
		ToolHooks toolHooks
	) {
		this.model = model;
		this.toolRegistry = toolRegistry;
		this.systemPrompt = systemPrompt;
		this.modelName = modelName;
		this.providerName = providerName;
		this.emitter = emitter;
		this.parallelTools = parallelTools;
		this.retryConfig = retryConfig;
		this.usageStats = usageStats;
		this.toolHooks = toolHooks;
	}

	public void cancel() {
		cancelled = true;
	}

	/**
	 * Run the agent loop with the given messages.
	 * Modifies the messages list in-place (appends assistant + tool results).
	 */
	public void run(List<AgentMessage> messages) {
		emitter.accept(new AgentEvent.TurnStart());

		for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
			if (cancelled) break;

			// Stream response from LLM (with retry)
			var streamResult = streamResponseWithRetry(messages);
			if (streamResult == null || cancelled) break;

			// Add assistant message to history
			messages.add(streamResult);

			// If no tool calls, we're done
			if (!streamResult.hasToolCalls()) {
				break;
			}

			// Execute tool calls
			var toolCalls = streamResult.toolCalls();
			if (parallelTools && toolCalls.size() > 1) {
				executeToolsParallel(toolCalls, messages);
			} else {
				executeToolsSequential(toolCalls, messages);
			}
		}

		emitter.accept(new AgentEvent.TurnEnd());
		emitter.accept(new AgentEvent.AgentEnd());
	}

	private void executeToolsSequential(List<ToolExecutionRequest> toolCalls, List<AgentMessage> messages) {
		for (var toolCall : toolCalls) {
			if (cancelled) break;

			emitter.accept(new AgentEvent.ToolExecutionStart(
				toolCall.id(), toolCall.name(), toolCall.arguments()
			));

			AgentToolResult result = maskSecrets(executeTool(toolCall));

			emitter.accept(new AgentEvent.ToolExecutionEnd(
				toolCall.id(), toolCall.name(), result
			));

			messages.add(new ToolResultMessage(
				toolCall.id(), toolCall.name(), result.content(), result.isError(),
				result.hasImages() ? result.images() : java.util.List.of()
			));
		}
	}

	/** Redact secrets from a tool result so they don't reach the model, the UI, or the session. */
	private static AgentToolResult maskSecrets(AgentToolResult result) {
		String masked = SecretMasker.mask(result.content());
		if (masked.equals(result.content())) {
			return result;
		}
		return new AgentToolResult(masked, result.details(), result.isError(), result.images());
	}

	private void executeToolsParallel(List<ToolExecutionRequest> toolCalls, List<AgentMessage> messages) {
		// Emit all start events upfront
		for (var toolCall : toolCalls) {
			emitter.accept(new AgentEvent.ToolExecutionStart(
				toolCall.id(), toolCall.name(), toolCall.arguments()
			));
		}

		// Execute all tools concurrently using virtual threads
		@SuppressWarnings("unchecked")
		CompletableFuture<AgentToolResult>[] futures = new CompletableFuture[toolCalls.size()];
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int i = 0; i < toolCalls.size(); i++) {
				var toolCall = toolCalls.get(i);
				futures[i] = CompletableFuture.supplyAsync(
					() -> executeTool(toolCall),
					executor
				);
			}

			// Wait for all to complete
			CompletableFuture.allOf(futures).join();
		}

		// Emit results and add messages in source order
		for (int i = 0; i < toolCalls.size(); i++) {
			var toolCall = toolCalls.get(i);
			AgentToolResult result;
			try {
				result = futures[i].get();
			} catch (Exception e) {
				result = AgentToolResult.error("Tool execution failed: " + e.getMessage());
			}
			result = maskSecrets(result);

			emitter.accept(new AgentEvent.ToolExecutionEnd(
				toolCall.id(), toolCall.name(), result
			));

			messages.add(new ToolResultMessage(
				toolCall.id(), toolCall.name(), result.content(), result.isError(),
				result.hasImages() ? result.images() : java.util.List.of()
			));
		}
	}

	/**
	 * Stream response with automatic retry on transient errors.
	 */
	private AssistantMessage streamResponseWithRetry(List<AgentMessage> messages) {
		for (int attempt = 0; attempt <= retryConfig.maxRetries(); attempt++) {
			if (cancelled) return null;

			var result = streamResponse(messages);
			if (result != null) {
				return result;
			}

			// A rejected request is rejected the same way every time. Retrying a
			// bad model name or a stale token just prints the same error four
			// times and delays the one message that says what to fix.
			if (isPermanent(lastStreamError)) {
				return null;
			}

			// streamResponse returned null due to error -- check if we should retry
			if (attempt < retryConfig.maxRetries()) {
				long delay = calculateRetryDelay(attempt);
				emitter.accept(new AgentEvent.RetryAttempt(
					attempt + 1, delay, "API error, attempt " + (attempt + 2) + " of " + (retryConfig.maxRetries() + 1)
				));
				try {
					Thread.sleep(delay);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return null;
				}
			}
		}
		return null;
	}

	/** The error from the last {@link #streamResponse} attempt, or null when it succeeded. */
	private Throwable lastStreamError;

	/**
	 * Whether the request was refused rather than dropped. 4xx means the request
	 * itself is wrong — an unsupported model, an expired token, a malformed body
	 * — and sending it again unchanged cannot help. 408 and 429 are the
	 * exceptions: they are 4xx codes that mean "later", which is what a retry
	 * is for.
	 */
	private static boolean isPermanent(Throwable error) {
		if (error == null) {
			return false;
		}
		for (Throwable t = error; t != null; t = t.getCause()) {
			String message = t.getMessage();
			if (message == null) {
				continue;
			}
			// The first status only. A provider echoes the response body into the
			// same string, so a 502 whose body mentions an upstream 404 would
			// otherwise read as permanent and lose its retry.
			var m = HTTP_STATUS.matcher(message);
			if (m.find()) {
				int status = Integer.parseInt(m.group(1));
				if (status >= 400 && status < 500 && status != 408 && status != 429) {
					return true;
				}
			}
		}
		return false;
	}

	private static final java.util.regex.Pattern HTTP_STATUS =
		java.util.regex.Pattern.compile("HTTP (\\d{3})\\b");

	private long calculateRetryDelay(int attempt) {
		long delay = retryConfig.initialDelayMs() * (1L << attempt);
		return Math.min(delay, retryConfig.maxDelayMs());
	}

	private AssistantMessage streamResponse(List<AgentMessage> messages) {
		var chatMessages = MessageConverter.toLangChain(systemPrompt, messages);

		var requestBuilder = ChatRequest.builder()
			.messages(chatMessages);

		var toolSpecs = toolRegistry.toToolSpecifications();
		if (!toolSpecs.isEmpty()) {
			requestBuilder.toolSpecifications(toolSpecs);
		}

		var request = requestBuilder.build();

		emitter.accept(new AgentEvent.MessageStart());

		var responseBuilder = new StringBuilder();
		var latch = new CountDownLatch(1);
		var responseHolder = new ChatResponse[1];
		var errorHolder = new Throwable[1];

		model.chat(request, new StreamingChatResponseHandler() {
			@Override
			public void onPartialResponse(String partialResponse) {
				responseBuilder.append(partialResponse);
				emitter.accept(new AgentEvent.MessageUpdate(partialResponse));
			}

			@Override
			public void onCompleteResponse(ChatResponse completeResponse) {
				responseHolder[0] = completeResponse;
				latch.countDown();
			}

			@Override
			public void onError(Throwable error) {
				errorHolder[0] = error;
				latch.countDown();
			}
		});

		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}

		if (errorHolder[0] != null) {
			lastStreamError = errorHolder[0];
			emitter.accept(new AgentEvent.ErrorOccurred(errorHolder[0]));
			return null;
		}
		lastStreamError = null;

		ChatResponse response = responseHolder[0];
		AiMessage aiMessage = response.aiMessage();

		// Track token usage
		var tokenUsage = response.tokenUsage();
		if (tokenUsage != null && usageStats != null) {
			usageStats.addUsage(
				tokenUsage.inputTokenCount(),
				tokenUsage.outputTokenCount(),
				tokenUsage.totalTokenCount()
			);
		}

		List<ToolExecutionRequest> toolCalls = aiMessage.toolExecutionRequests() != null
			? aiMessage.toolExecutionRequests()
			: List.of();

		String text = aiMessage.text() != null ? aiMessage.text() : responseBuilder.toString();

		var assistantMsg = new AssistantMessage(
			text, Instant.now(), modelName, providerName, toolCalls, aiMessage
		);

		emitter.accept(new AgentEvent.MessageEnd(assistantMsg));

		// Emit usage update after message
		if (usageStats != null) {
			emitter.accept(new AgentEvent.UsageUpdate(
				usageStats.inputTokens(),
				usageStats.outputTokens(),
				usageStats.totalTokens(),
				0, // message count filled by Agent
				usageStats.estimatedCostUsd()
			));
		}

		return assistantMsg;
	}

	private AgentToolResult executeTool(ToolExecutionRequest toolCall) {
		AgentTool tool = toolRegistry.get(toolCall.name());
		if (tool == null) {
			return AgentToolResult.error("Unknown tool: " + toolCall.name());
		}

		// Before hook
		String arguments = toolCall.arguments() != null ? toolCall.arguments() : "{}";
		if (toolHooks != null) {
			var hookResult = toolHooks.beforeToolCall(toolCall.name(), arguments);
			switch (hookResult) {
				case ToolHookResult.Block block ->
					{ return AgentToolResult.error("Blocked: " + block.reason()); }
				case ToolHookResult.Modify modify ->
					arguments = modify.newArguments();
				case ToolHookResult.Allow ignored -> {}
			}
		}

		try {
			JsonNode params = MAPPER.readTree(arguments);
			var result = tool.execute(
				toolCall.id(),
				params,
				() -> cancelled,
				null
			);

			// After hook
			if (toolHooks != null) {
				result = toolHooks.afterToolCall(toolCall.name(), result);
			}

			return result;
		} catch (Exception e) {
			return AgentToolResult.error("Tool execution failed: " + e.getMessage());
		}
	}
}
