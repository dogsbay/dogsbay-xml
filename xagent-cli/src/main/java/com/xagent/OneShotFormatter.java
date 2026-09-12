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
package com.xagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xagent.core.UsageStats;

/**
 * Formats the result of a headless one-shot run for stdout.
 */
public final class OneShotFormatter {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** Output formats for one-shot mode. */
	public enum Format { TEXT, JSON, MARKDOWN }

	private OneShotFormatter() {}

	public static Format parseFormat(String value) {
		if (value == null) return Format.TEXT;
		return switch (value.toLowerCase()) {
			case "json" -> Format.JSON;
			case "markdown", "md" -> Format.MARKDOWN;
			default -> Format.TEXT;
		};
	}

	public static String format(Format format, String response, UsageStats usage, String sessionId) {
		String text = response != null ? response : "";
		return switch (format) {
			case TEXT -> text;
			case JSON -> toJson(text, usage, sessionId);
			case MARKDOWN -> toMarkdown(text, usage, sessionId);
		};
	}

	private static String toJson(String response, UsageStats usage, String sessionId) {
		ObjectNode root = MAPPER.createObjectNode();
		root.put("response", response);
		if (usage != null) {
			root.put("inputTokens", usage.inputTokens());
			root.put("outputTokens", usage.outputTokens());
			root.put("totalTokens", usage.totalTokens());
			root.put("estimatedCostUsd", usage.estimatedCostUsd());
		}
		if (sessionId != null) {
			root.put("sessionId", sessionId);
		}
		try {
			return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
		} catch (Exception e) {
			// ObjectNode serialization cannot realistically fail; keep the contract
			return "{\"response\": \"\"}";
		}
	}

	private static String toMarkdown(String response, UsageStats usage, String sessionId) {
		var sb = new StringBuilder(response);
		sb.append("\n\n---\n");
		if (usage != null) {
			sb.append("- tokens: ").append(usage.totalTokens())
				.append(" (").append(usage.inputTokens()).append(" in / ")
				.append(usage.outputTokens()).append(" out)\n");
			sb.append("- cost: ").append(usage.formatCost()).append("\n");
		}
		if (sessionId != null) {
			sb.append("- session: ").append(sessionId).append("\n");
		}
		return sb.toString();
	}
}
