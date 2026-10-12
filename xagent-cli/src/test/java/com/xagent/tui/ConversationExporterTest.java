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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationExporterTest {

	@Test
	void exportsUserBlockAsQuote() {
		var blocks = List.<ChatArea.ChatBlock>of(
			new ChatArea.ChatBlock.UserBlock("hello world")
		);
		String md = ConversationExporter.toMarkdown(blocks);
		assertThat(md).contains("> hello world");
	}

	@Test
	void exportsAssistantBlockAsRawMarkdown() {
		var blocks = List.<ChatArea.ChatBlock>of(
			new ChatArea.ChatBlock.AssistantBlock("# Heading\n\nSome **bold** text", true)
		);
		String md = ConversationExporter.toMarkdown(blocks);
		assertThat(md).contains("# Heading");
		assertThat(md).contains("**bold**");
	}

	@Test
	void exportsToolBlockAsFencedCode() {
		var blocks = List.<ChatArea.ChatBlock>of(
			new ChatArea.ChatBlock.ToolBlock("bash", "ls -la", "file1\nfile2", false, true)
		);
		String md = ConversationExporter.toMarkdown(blocks);
		assertThat(md).contains("**Tool: bash**");
		assertThat(md).contains("`ls -la`");
		assertThat(md).contains("```");
		assertThat(md).contains("file1\nfile2");
	}

	@Test
	void exportsMultipleBlocks() {
		var blocks = List.<ChatArea.ChatBlock>of(
			new ChatArea.ChatBlock.UserBlock("what files exist?"),
			new ChatArea.ChatBlock.ToolBlock("bash", "ls", "a.txt\nb.txt", false, true),
			new ChatArea.ChatBlock.AssistantBlock("Found 2 files.", true)
		);
		String md = ConversationExporter.toMarkdown(blocks);
		assertThat(md).contains("> what files exist?");
		assertThat(md).contains("**Tool: bash**");
		assertThat(md).contains("Found 2 files.");
	}

	@Test
	void exportsInfoBlock() {
		var blocks = List.<ChatArea.ChatBlock>of(
			new ChatArea.ChatBlock.InfoBlock("Session resumed.")
		);
		String md = ConversationExporter.toMarkdown(blocks);
		assertThat(md).contains("Session resumed.");
	}

	@Test
	void exportsMultilineUserBlock() {
		var blocks = List.<ChatArea.ChatBlock>of(
			new ChatArea.ChatBlock.UserBlock("line1\nline2")
		);
		String md = ConversationExporter.toMarkdown(blocks);
		assertThat(md).contains("> line1\n> line2");
	}

	@Test
	void toolBlockWithoutResult() {
		var blocks = List.<ChatArea.ChatBlock>of(
			new ChatArea.ChatBlock.ToolBlock("read", "file.txt", null, false, false)
		);
		String md = ConversationExporter.toMarkdown(blocks);
		assertThat(md).contains("**Tool: read**");
		assertThat(md).doesNotContain("```");
	}
}
