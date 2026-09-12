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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InputAreaTest {

	private InputArea inputArea;
	private List<String> submitted;

	@BeforeEach
	void setUp() {
		submitted = new ArrayList<>();
		inputArea = new InputArea(submitted::add);
	}

	@Test
	void typingCharactersBuildsBuffer() {
		type("hello");
		assertThat(inputArea.bufferContent()).isEqualTo("hello");
		assertThat(inputArea.cursorPosition()).isEqualTo(5);
	}

	@Test
	void backspaceDeletesBeforeCursor() {
		type("abc");
		inputArea.handleKey(new KeyEvent.Backspace());
		assertThat(inputArea.bufferContent()).isEqualTo("ab");
		assertThat(inputArea.cursorPosition()).isEqualTo(2);
	}

	@Test
	void backspaceAtStartDoesNothing() {
		inputArea.handleKey(new KeyEvent.Backspace());
		assertThat(inputArea.bufferContent()).isEmpty();
	}

	@Test
	void deleteRemovesAtCursor() {
		type("abc");
		inputArea.handleKey(new KeyEvent.Home());
		inputArea.handleKey(new KeyEvent.Delete());
		assertThat(inputArea.bufferContent()).isEqualTo("bc");
	}

	@Test
	void leftRightMoveCursor() {
		type("abc");
		inputArea.handleKey(new KeyEvent.Left());
		inputArea.handleKey(new KeyEvent.Left());
		assertThat(inputArea.cursorPosition()).isEqualTo(1);

		inputArea.handleKey(new KeyEvent.Right());
		assertThat(inputArea.cursorPosition()).isEqualTo(2);
	}

	@Test
	void homeEndMoveCursorToEdges() {
		type("hello");
		inputArea.handleKey(new KeyEvent.Home());
		assertThat(inputArea.cursorPosition()).isEqualTo(0);

		inputArea.handleKey(new KeyEvent.End());
		assertThat(inputArea.cursorPosition()).isEqualTo(5);
	}

	@Test
	void enterSubmitsAndClearsBuffer() {
		type("hello");
		inputArea.handleKey(new KeyEvent.Enter());

		assertThat(submitted).containsExactly("hello");
		assertThat(inputArea.bufferContent()).isEmpty();
		assertThat(inputArea.cursorPosition()).isEqualTo(0);
	}

	@Test
	void emptyEnterDoesNotSubmit() {
		inputArea.handleKey(new KeyEvent.Enter());
		assertThat(submitted).isEmpty();
	}

	@Test
	void upDownNavigatesHistory() {
		type("first");
		inputArea.handleKey(new KeyEvent.Enter());
		type("second");
		inputArea.handleKey(new KeyEvent.Enter());

		// Up should show "second"
		inputArea.handleKey(new KeyEvent.Up());
		assertThat(inputArea.bufferContent()).isEqualTo("second");

		// Up again should show "first"
		inputArea.handleKey(new KeyEvent.Up());
		assertThat(inputArea.bufferContent()).isEqualTo("first");

		// Down should show "second"
		inputArea.handleKey(new KeyEvent.Down());
		assertThat(inputArea.bufferContent()).isEqualTo("second");

		// Down again should restore empty
		inputArea.handleKey(new KeyEvent.Down());
		assertThat(inputArea.bufferContent()).isEmpty();
	}

	@Test
	void downWhenNotBrowsingDoesNothing() {
		type("first");
		inputArea.handleKey(new KeyEvent.Enter());

		// Type something new
		type("current");

		// Down when not browsing should do nothing
		inputArea.handleKey(new KeyEvent.Down());
		assertThat(inputArea.bufferContent()).isEqualTo("current");
	}

	@Test
	void insertInMiddle() {
		type("ac");
		inputArea.handleKey(new KeyEvent.Left());
		inputArea.handleKey(new KeyEvent.Character('b'));
		assertThat(inputArea.bufferContent()).isEqualTo("abc");
		assertThat(inputArea.cursorPosition()).isEqualTo(2);
	}

	@Test
	void renderProducesCorrectHeight() {
		type("test");
		List<String> lines = inputArea.render(40);
		assertThat(lines).hasSize(3); // HEIGHT = 3
	}

	@Test
	void desiredHeightIs3() {
		assertThat(inputArea.desiredHeight()).isEqualTo(3);
	}

	private void type(String text) {
		for (char c : text.toCharArray()) {
			inputArea.handleKey(new KeyEvent.Character(c));
		}
	}
}
