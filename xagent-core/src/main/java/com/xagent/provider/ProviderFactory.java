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

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.time.Duration;

/**
 * Creates LangChain4j StreamingChatModel instances from ProviderConfig.
 */
public final class ProviderFactory {

	private ProviderFactory() {}

	public static StreamingChatModel create(ProviderConfig config) {
		return switch (config.provider()) {
			case "openai" -> createOpenAi(config);
			case "openai-codex" -> createCodex(config);
			case "anthropic" -> createAnthropic(config);
			case "gemini" -> createGemini(config);
			case "ollama" -> createOllama(config);
			default -> throw new IllegalArgumentException(
				"Unknown provider: " + config.provider()
					+ ". Supported: openai, openai-codex, anthropic, gemini, ollama"
			);
		};
	}

	private static StreamingChatModel createOpenAi(ProviderConfig config) {
		if (config.apiKey() == null) {
			throw new IllegalStateException(
				"OpenAI API key required. Set OPENAI_API_KEY or use --api-key."
			);
		}
		var builder = OpenAiStreamingChatModel.builder()
			.apiKey(config.apiKey())
			.modelName(config.model())
			.timeout(Duration.ofSeconds(120));
		if (config.baseUrl() != null) {
			builder.baseUrl(config.baseUrl());
		}
		return builder.build();
	}

	private static StreamingChatModel createCodex(ProviderConfig config) {
		var storage = new com.xagent.auth.AuthStorage();
		var oauth = new com.xagent.auth.CodexOAuth();
		if (storage.get(com.xagent.auth.CodexOAuth.PROVIDER_ID).isEmpty()) {
			throw new IllegalStateException(
				"Not signed in to ChatGPT. Run /login (or /login device) first."
			);
		}
		String baseUrl = config.baseUrl() != null
			? config.baseUrl()
			: CodexStreamingChatModel.DEFAULT_BASE_URL;
		return new CodexStreamingChatModel(
			() -> storage.getValid(com.xagent.auth.CodexOAuth.PROVIDER_ID, oauth::refresh)
				.orElseThrow(() -> new IllegalStateException(
					"ChatGPT session expired and refresh failed. Run /login again.")),
			config.model(),
			baseUrl);
	}

	private static StreamingChatModel createAnthropic(ProviderConfig config) {
		if (config.apiKey() == null) {
			throw new IllegalStateException(
				"Anthropic API key required. Set ANTHROPIC_API_KEY or use --api-key."
			);
		}
		var builder = AnthropicStreamingChatModel.builder()
			.apiKey(config.apiKey())
			.modelName(config.model())
			.maxTokens(4096)
			// Prompt caching: the system prompt (tool docs, context files,
			// skills catalog) and tool specs are identical across the agent
			// loop's many calls, so cache reads dominate the 25% write surcharge.
			.cacheSystemMessages(true)
			.cacheTools(true)
			.timeout(Duration.ofSeconds(120));
		if (config.baseUrl() != null) {
			builder.baseUrl(config.baseUrl());
		}
		return builder.build();
	}

	private static StreamingChatModel createGemini(ProviderConfig config) {
		if (config.apiKey() == null) {
			throw new IllegalStateException(
				"Gemini API key required. Set GEMINI_API_KEY or use --api-key."
			);
		}
		var thinkingConfig = GeminiThinkingConfig.builder()
			.includeThoughts(true)
			.build();
		var builder = GoogleAiGeminiStreamingChatModel.builder()
			.apiKey(config.apiKey())
			.modelName(config.model())
			.thinkingConfig(thinkingConfig)
			.returnThinking(true)
			.sendThinking(true)
			.timeout(Duration.ofSeconds(120));
		return builder.build();
	}

	private static StreamingChatModel createOllama(ProviderConfig config) {
		var builder = OllamaStreamingChatModel.builder()
			.modelName(config.model())
			.timeout(Duration.ofMinutes(5));
		String baseUrl = config.baseUrl() != null
			? config.baseUrl()
			: "http://localhost:11434";
		builder.baseUrl(baseUrl);
		return builder.build();
	}
}
