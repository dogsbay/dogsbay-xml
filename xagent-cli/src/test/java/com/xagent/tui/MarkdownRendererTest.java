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

class MarkdownRendererTest {

	private MarkdownRenderer renderer;

	@BeforeEach
	void setUp() {
		renderer = new MarkdownRenderer(Theme.DEFAULT);
	}

	@Test
	void rendersH1Heading() {
		List<String> lines = renderer.render("# Hello", 80);
		String stripped = stripAnsi(joinLines(lines));
		assertThat(stripped).contains("Hello");
		// Should not contain the markdown prefix
		assertThat(stripped).doesNotContain("# ");
	}

	@Test
	void rendersH2Heading() {
		List<String> lines = renderer.render("## Section", 80);
		String stripped = stripAnsi(joinLines(lines));
		assertThat(stripped).contains("Section");
		assertThat(stripped).doesNotContain("## ");
	}

	@Test
	void rendersH3Heading() {
		List<String> lines = renderer.render("### Sub", 80);
		String stripped = stripAnsi(joinLines(lines));
		assertThat(stripped).contains("Sub");
		assertThat(stripped).doesNotContain("### ");
	}

	@Test
	void rendersBoldText() {
		List<String> lines = renderer.render("some **bold** text", 80);
		String raw = joinLines(lines);
		// Should contain BOLD ANSI code
		assertThat(raw).contains(AnsiCodes.BOLD);
		String stripped = stripAnsi(raw);
		assertThat(stripped).contains("bold");
	}

	@Test
	void rendersItalicText() {
		List<String> lines = renderer.render("some *italic* text", 80);
		String raw = joinLines(lines);
		assertThat(raw).contains(AnsiCodes.ITALIC);
		String stripped = stripAnsi(raw);
		assertThat(stripped).contains("italic");
	}

	@Test
	void rendersNestedBoldItalic() {
		List<String> lines = renderer.render("***bold italic***", 80);
		String raw = joinLines(lines);
		assertThat(raw).contains(AnsiCodes.BOLD);
		assertThat(raw).contains(AnsiCodes.ITALIC);
	}

	@Test
	void rendersFencedCodeBlock() {
		String md = "```java\nint x = 1;\n```";
		List<String> lines = renderer.render(md, 80);
		String stripped = stripAnsi(joinLines(lines));
		assertThat(stripped).contains("java");
		assertThat(stripped).contains("int x = 1;");
	}

	@Test
	void rendersInlineCode() {
		List<String> lines = renderer.render("use `code` here", 80);
		String raw = joinLines(lines);
		assertThat(raw).contains(AnsiCodes.YELLOW); // mdCodeInline is YELLOW
		String stripped = stripAnsi(raw);
		assertThat(stripped).contains("code");
	}

	@Test
	void rendersBulletList() {
		String md = "- one\n- two\n- three";
		List<String> lines = renderer.render(md, 80);
		String stripped = stripAnsi(joinLines(lines));
		assertThat(stripped).contains("- one");
		assertThat(stripped).contains("- two");
		assertThat(stripped).contains("- three");
	}

	@Test
	void rendersOrderedList() {
		String md = "1. first\n2. second\n3. third";
		List<String> lines = renderer.render(md, 80);
		String stripped = stripAnsi(joinLines(lines));
		assertThat(stripped).contains("1. first");
		assertThat(stripped).contains("2. second");
		assertThat(stripped).contains("3. third");
	}

	@Test
	void rendersNestedList() {
		String md = "- outer\n  - inner";
		List<String> lines = renderer.render(md, 80);
		String stripped = stripAnsi(joinLines(lines));
		assertThat(stripped).contains("outer");
		assertThat(stripped).contains("inner");
	}

	@Test
	void rendersBlockquote() {
		String md = "> quoted text";
		List<String> lines = renderer.render(md, 80);
		String stripped = stripAnsi(joinLines(lines));
		assertThat(stripped).contains("│");
		assertThat(stripped).contains("quoted text");
	}

	@Test
	void rendersLink() {
		String md = "[click](http://example.com)";
		List<String> lines = renderer.render(md, 80);
		String stripped = stripAnsi(joinLines(lines));
		assertThat(stripped).contains("click");
		assertThat(stripped).contains("http://example.com");
	}

	@Test
	void rendersHorizontalRule() {
		String md = "above\n\n---\n\nbelow";
		List<String> lines = renderer.render(md, 40);
		String stripped = stripAnsi(joinLines(lines));
		assertThat(stripped).contains("─");
		assertThat(stripped).contains("above");
		assertThat(stripped).contains("below");
	}

	@Test
	void wrapsLongParagraph() {
		String longText = "word ".repeat(20).trim();
		List<String> lines = renderer.render(longText, 30);
		// Should have multiple lines
		assertThat(lines.size()).isGreaterThan(1);
	}

	@Test
	void handlesIncompleteMarkdownGracefully() {
		// Unclosed fence — should treat as text
		String md = "```java\nint x = 1;";
		List<String> lines = renderer.render(md, 80);
		// Should not throw, and should produce output
		assertThat(lines).isNotEmpty();
	}

	@Test
	void handlesEmptyInput() {
		List<String> lines = renderer.render("", 80);
		assertThat(lines).containsExactly("");
	}

	@Test
	void handlesNullInput() {
		List<String> lines = renderer.render(null, 80);
		assertThat(lines).containsExactly("");
	}

	@Test
	void rendersPlainTextWithoutMarkdown() {
		List<String> lines = renderer.render("just plain text", 80);
		String stripped = stripAnsi(joinLines(lines));
		assertThat(stripped).contains("just plain text");
	}

	private String stripAnsi(String s) {
		return s.replaceAll("\033\\[[0-9;]*[a-zA-Z]", "");
	}

	private String joinLines(List<String> lines) {
		return String.join("\n", lines);
	}
}
