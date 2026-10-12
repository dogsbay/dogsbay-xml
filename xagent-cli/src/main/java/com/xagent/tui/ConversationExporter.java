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
package com.xagent.tui;

import java.util.List;

/**
 * Exports conversation blocks to a raw markdown document.
 */
public final class ConversationExporter {

	private ConversationExporter() {}

	/**
	 * Converts chat blocks to a markdown document string.
	 */
	public static String toMarkdown(List<ChatArea.ChatBlock> blocks) {
		var sb = new StringBuilder();

		for (var block : blocks) {
			switch (block) {
				case ChatArea.ChatBlock.UserBlock ub -> {
					for (String line : ub.text().split("\n", -1)) {
						sb.append("> ").append(line).append("\n");
					}
					sb.append("\n");
				}
				case ChatArea.ChatBlock.AssistantBlock ab -> {
					sb.append(ab.text()).append("\n\n");
				}
				case ChatArea.ChatBlock.ToolBlock tb -> {
					sb.append("**Tool: ").append(tb.name()).append("**");
					if (tb.args() != null && !tb.args().isEmpty()) {
						sb.append(" `").append(tb.args()).append("`");
					}
					sb.append("\n\n");
					if (tb.result() != null) {
						sb.append("```\n");
						sb.append(tb.result()).append("\n");
						sb.append("```\n\n");
					}
				}
				case ChatArea.ChatBlock.InfoBlock ib -> {
					sb.append(ib.text()).append("\n\n");
				}
			}
		}

		return sb.toString().stripTrailing() + "\n";
	}
}
