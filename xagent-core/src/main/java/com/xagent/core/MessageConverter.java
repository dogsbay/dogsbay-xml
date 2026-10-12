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

import com.xagent.message.AgentMessage;
import com.xagent.message.AssistantMessage;
import com.xagent.message.ToolResultMessage;
import com.xagent.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts between AgentMessage and LangChain4j ChatMessage types.
 * Mirrors pi's convertToLlm in agent-session.ts.
 */
public final class MessageConverter {

	private MessageConverter() {}

	/**
	 * Convert agent messages to LangChain4j chat messages,
	 * prepending a system message.
	 */
	public static List<ChatMessage> toLangChain(String systemPrompt, List<AgentMessage> messages) {
		var result = new ArrayList<ChatMessage>();
		result.add(SystemMessage.from(systemPrompt));

		for (AgentMessage msg : messages) {
			switch (msg) {
				case UserMessage u ->
					result.add(dev.langchain4j.data.message.UserMessage.from(u.content()));
				case AssistantMessage a -> {
					if (a.originalAiMessage() != null) {
						result.add(a.originalAiMessage());
					} else if (a.hasToolCalls()) {
						result.add(new AiMessage(a.content(), a.toolCalls()));
					} else {
						result.add(AiMessage.from(a.content() != null ? a.content() : ""));
					}
				}
				case ToolResultMessage t -> {
					result.add(new ToolExecutionResultMessage(t.toolCallId(), t.toolName(), t.content()));
					if (t.hasImages()) {
						var contents = new ArrayList<Content>();
						contents.add(new TextContent("Image from tool " + t.toolName() + ":"));
						for (var img : t.images()) {
							contents.add(new ImageContent(img.base64Data(), img.mimeType()));
						}
						result.add(new dev.langchain4j.data.message.UserMessage(contents));
					}
				}
			}
		}

		return result;
	}
}
