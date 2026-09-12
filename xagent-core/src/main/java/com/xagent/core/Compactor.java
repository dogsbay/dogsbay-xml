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

import java.util.ArrayList;
import java.util.List;

/**
 * Compacts conversation history by summarizing older messages when the
 * estimated token count exceeds the configured limit.
 *
 * The compactor keeps recent messages intact and replaces older messages
 * with a summary. The summary is injected as a UserMessage so the LLM
 * has context about what happened earlier in the conversation.
 */
public class Compactor {

	private static final double CHARS_PER_TOKEN = 4.0;

	private final CompactionConfig config;

	public Compactor(CompactionConfig config) {
		this.config = config;
	}

	/**
	 * Estimate the token count for a list of messages.
	 * Uses a rough heuristic of ~4 characters per token.
	 */
	public int estimateTokens(List<AgentMessage> messages) {
		int totalChars = 0;
		for (var msg : messages) {
			totalChars += messageChars(msg);
		}
		return (int) (totalChars / CHARS_PER_TOKEN);
	}

	/**
	 * Check if compaction is needed based on current message history.
	 */
	public boolean needsCompaction(List<AgentMessage> messages) {
		return estimateTokens(messages) > config.maxContextTokens();
	}

	/**
	 * Identify the split point: messages before this index should be compacted,
	 * messages from this index onward are kept as-is.
	 *
	 * Returns -1 if no compaction is needed.
	 */
	public int findSplitPoint(List<AgentMessage> messages) {
		if (!needsCompaction(messages)) return -1;

		// Work backwards from the end, accumulating tokens until we hit keepRecentTokens
		int recentTokens = 0;
		int splitPoint = messages.size();

		for (int i = messages.size() - 1; i >= 0; i--) {
			recentTokens += (int) (messageChars(messages.get(i)) / CHARS_PER_TOKEN);
			if (recentTokens >= config.keepRecentTokens()) {
				splitPoint = i;
				break;
			}
		}

		// Don't compact if split would leave nothing to compact
		if (splitPoint <= 1) return -1;

		return splitPoint;
	}

	/**
	 * Build a summary of the messages that will be compacted.
	 * This produces a structured text summary the LLM can use as context.
	 */
	public String buildSummary(List<AgentMessage> messagesToCompact) {
		var sb = new StringBuilder();
		sb.append("[Conversation summary - earlier messages were compacted to save context]\n\n");

		var topics = new ArrayList<String>();
		var filesRead = new ArrayList<String>();
		var filesWritten = new ArrayList<String>();
		var toolsUsed = new ArrayList<String>();

		for (var msg : messagesToCompact) {
			switch (msg) {
				case UserMessage u -> {
					String preview = truncate(u.content(), 200);
					topics.add("User asked: " + preview);
				}
				case AssistantMessage a -> {
					if (a.content() != null && !a.content().isBlank()) {
						topics.add("Assistant: " + truncate(a.content(), 200));
					}
					if (a.hasToolCalls()) {
						for (var tc : a.toolCalls()) {
							toolsUsed.add(tc.name());
							if ("read".equals(tc.name()) && tc.arguments() != null) {
								extractPath(tc.arguments(), filesRead);
							} else if (("write".equals(tc.name()) || "edit".equals(tc.name())) && tc.arguments() != null) {
								extractPath(tc.arguments(), filesWritten);
							}
						}
					}
				}
				case ToolResultMessage t -> {
					// Tool results are implicitly captured via the tool calls above
				}
			}
		}

		if (!topics.isEmpty()) {
			sb.append("## Topics discussed\n");
			for (var topic : topics) {
				sb.append("- ").append(topic).append("\n");
			}
			sb.append("\n");
		}

		if (!filesRead.isEmpty()) {
			sb.append("## Files read\n");
			for (var f : filesRead.stream().distinct().toList()) {
				sb.append("- ").append(f).append("\n");
			}
			sb.append("\n");
		}

		if (!filesWritten.isEmpty()) {
			sb.append("## Files modified\n");
			for (var f : filesWritten.stream().distinct().toList()) {
				sb.append("- ").append(f).append("\n");
			}
			sb.append("\n");
		}

		return sb.toString();
	}

	/**
	 * Perform compaction on the messages list in-place.
	 * Replaces older messages with a summary UserMessage.
	 * Returns the summary text, or null if no compaction was needed.
	 */
	public String compact(List<AgentMessage> messages) {
		int splitPoint = findSplitPoint(messages);
		if (splitPoint < 0) return null;

		var toCompact = new ArrayList<>(messages.subList(0, splitPoint));
		String summary = buildSummary(toCompact);

		// Remove old messages and insert summary
		messages.subList(0, splitPoint).clear();
		messages.addFirst(new UserMessage(summary));

		return summary;
	}

	private int messageChars(AgentMessage msg) {
		return switch (msg) {
			case UserMessage u -> u.content() != null ? u.content().length() : 0;
			case AssistantMessage a -> (a.content() != null ? a.content().length() : 0)
				+ (a.hasToolCalls() ? a.toolCalls().toString().length() : 0);
			case ToolResultMessage t -> t.content() != null ? t.content().length() : 0;
		};
	}

	private void extractPath(String arguments, List<String> paths) {
		// Simple extraction: look for "path":"..." in JSON arguments
		int idx = arguments.indexOf("\"path\"");
		if (idx < 0) return;
		int colonIdx = arguments.indexOf(':', idx);
		if (colonIdx < 0) return;
		int quoteStart = arguments.indexOf('"', colonIdx + 1);
		if (quoteStart < 0) return;
		int quoteEnd = arguments.indexOf('"', quoteStart + 1);
		if (quoteEnd < 0) return;
		paths.add(arguments.substring(quoteStart + 1, quoteEnd));
	}

	private static String truncate(String text, int maxLen) {
		if (text == null) return "";
		String singleLine = text.replace("\n", " ");
		if (singleLine.length() <= maxLen) return singleLine;
		return singleLine.substring(0, maxLen - 3) + "...";
	}
}
