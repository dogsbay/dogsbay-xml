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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatAreaTest {

	private ChatArea chatArea;

	@BeforeEach
	void setUp() {
		chatArea = new ChatArea();
	}

	@Test
	void addUserBlockRendersWithPrefix() {
		chatArea.addUserBlock("hello world");
		List<String> lines = chatArea.render(80);

		assertThat(lines).isNotEmpty();
		// Should contain the user prompt prefix ">"
		String firstLine = stripAnsi(lines.get(0));
		assertThat(firstLine).startsWith("> ");
		assertThat(firstLine).contains("hello world");
	}

	@Test
	void assistantBlockStreaming() {
		chatArea.startAssistantBlock();
		chatArea.appendToAssistant("Hello ");
		chatArea.appendToAssistant("world!");
		chatArea.completeAssistantBlock();

		List<String> lines = chatArea.render(80);
		String content = String.join("\n", lines);
		assertThat(content).contains("Hello world!");
	}

	@Test
	void toolBlockShowsNameAndResult() {
		chatArea.addToolBlock("bash", "ls -la");
		chatArea.updateToolBlock("bash", "file1\nfile2", false);

		List<String> lines = chatArea.render(80);
		String content = lines.stream().map(this::stripAnsi).reduce("", (a, b) -> a + "\n" + b);
		assertThat(content).contains("bash");
	}

	@Test
	void toolBlockErrorShowsXMark() {
		chatArea.addToolBlock("bash", "invalid");
		chatArea.updateToolBlock("bash", "command not found", true);

		List<String> lines = chatArea.render(80);
		String content = String.join("", lines);
		assertThat(content).contains("✗");
	}

	@Test
	void toolBlockSuccessShowsCheckmark() {
		chatArea.addToolBlock("read", "file.txt");
		chatArea.updateToolBlock("read", "contents", false);

		List<String> lines = chatArea.render(80);
		String content = String.join("", lines);
		assertThat(content).contains("✓");
	}

	@Test
	void clearRemovesAllBlocks() {
		chatArea.addUserBlock("test");
		chatArea.clear();

		assertThat(chatArea.blocks()).isEmpty();
	}

	@Test
	void renderVisibleRespectsHeight() {
		chatArea.addUserBlock("line1");
		chatArea.addUserBlock("line2");
		chatArea.addUserBlock("line3");
		chatArea.addUserBlock("line4");
		chatArea.addUserBlock("line5");

		List<String> visible = chatArea.renderVisible(80, 3);
		assertThat(visible).hasSize(3);
	}

	@Test
	void scrollUpChangesVisibleContent() {
		// Add enough content to cause scrolling
		for (int i = 0; i < 20; i++) {
			chatArea.addUserBlock("message " + i);
		}

		List<String> bottom = chatArea.renderVisible(80, 5);
		chatArea.scrollUp(10);
		List<String> scrolled = chatArea.renderVisible(80, 5);

		// Content should be different after scrolling
		assertThat(scrolled).isNotEqualTo(bottom);
	}

	@Test
	void autoScrollToBottomOnNewContent() {
		for (int i = 0; i < 20; i++) {
			chatArea.addUserBlock("message " + i);
		}

		List<String> visible = chatArea.renderVisible(80, 5);
		String content = visible.stream().map(this::stripAnsi).reduce("", (a, b) -> a + " " + b);
		// Last message should be visible
		assertThat(content).contains("message 19");
	}

	@Test
	void rawModeRendersPlainText() {
		chatArea.startAssistantBlock();
		chatArea.appendToAssistant("# Heading\n\nSome **bold** text");
		chatArea.completeAssistantBlock();
		chatArea.toggleRawMode();

		List<String> lines = chatArea.render(80);
		String content = String.join("\n", lines);
		// In raw mode, markdown should appear as-is
		assertThat(content).contains("# Heading");
		assertThat(content).contains("**bold**");
	}

	@Test
	void renderedModeIncludesAnsiForMarkdown() {
		chatArea.startAssistantBlock();
		chatArea.appendToAssistant("**bold text**");
		chatArea.completeAssistantBlock();

		assertThat(chatArea.isRawMode()).isFalse();
		List<String> lines = chatArea.render(80);
		String raw = String.join("", lines);
		// Should contain ANSI codes from markdown rendering
		assertThat(raw).contains("\033[");
	}

	@Test
	void toggleRawModeSwitchesBackAndForth() {
		assertThat(chatArea.isRawMode()).isFalse();
		chatArea.toggleRawMode();
		assertThat(chatArea.isRawMode()).isTrue();
		chatArea.toggleRawMode();
		assertThat(chatArea.isRawMode()).isFalse();
	}

	private String stripAnsi(String s) {
		return s.replaceAll("\033\\[[0-9;]*[a-zA-Z]", "");
	}
}
