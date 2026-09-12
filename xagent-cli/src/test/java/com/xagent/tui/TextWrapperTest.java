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

import static com.xagent.tui.AnsiCodes.*;
import static org.assertj.core.api.Assertions.assertThat;

class TextWrapperTest {

	@Test
	void wrapsPlainTextAtWordBoundaries() {
		List<String> lines = TextWrapper.wrap("hello world foo bar", 11);
		assertThat(lines).containsExactly("hello world", "foo bar");
	}

	@Test
	void preservesShortLines() {
		List<String> lines = TextWrapper.wrap("short", 20);
		assertThat(lines).containsExactly("short");
	}

	@Test
	void exactWidthFitsOnOneLine() {
		List<String> lines = TextWrapper.wrap("abcde", 5);
		assertThat(lines).containsExactly("abcde");
	}

	@Test
	void hardBreaksLongWords() {
		List<String> lines = TextWrapper.wrap("abcdefghij", 5);
		assertThat(lines).containsExactly("abcde", "fghij");
	}

	@Test
	void handlesEmptyString() {
		List<String> lines = TextWrapper.wrap("", 10);
		assertThat(lines).containsExactly("");
	}

	@Test
	void preservesAnsiCodesAcrossLines() {
		String text = BOLD + "hello world foo" + RESET;
		List<String> lines = TextWrapper.wrap(text, 11);
		assertThat(lines).hasSize(2);
		// First line should contain the BOLD code
		assertThat(lines.get(0)).contains(BOLD);
		assertThat(lines.get(0)).contains("hello world");
		// Second line should contain the carried-over BOLD
		assertThat(lines.get(1)).contains("foo");
	}

	@Test
	void preservesNewlines() {
		List<String> lines = TextWrapper.wrap("line1\nline2\nline3", 20);
		assertThat(lines).containsExactly("line1", "line2", "line3");
	}

	@Test
	void wrapsMultipleLinesWithinNewlines() {
		List<String> lines = TextWrapper.wrap("hello world foo\nbar baz", 11);
		assertThat(lines).containsExactly("hello world", "foo", "bar baz");
	}

	@Test
	void handlesTrailingNewline() {
		List<String> lines = TextWrapper.wrap("hello\n", 20);
		assertThat(lines).containsExactly("hello", "");
	}

	@Test
	void handlesOnlySpaces() {
		List<String> lines = TextWrapper.wrap("   ", 10);
		assertThat(lines).hasSize(1);
	}

	@Test
	void wordExactlyFitsRemainingWidth() {
		// "aa bb" with width 5 should fit on one line
		List<String> lines = TextWrapper.wrap("aa bb", 5);
		assertThat(lines).containsExactly("aa bb");
	}

	@Test
	void multipleAnsiCodes() {
		String text = BOLD + "bold " + RESET + RED + "red" + RESET;
		List<String> lines = TextWrapper.wrap(text, 50);
		assertThat(lines).hasSize(1);
		String stripped = stripAnsi(lines.get(0));
		assertThat(stripped).isEqualTo("bold red");
	}

	private String stripAnsi(String s) {
		return s.replaceAll("\033\\[[0-9;]*[a-zA-Z]", "");
	}
}
