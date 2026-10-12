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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.xagent.tui.AnsiCodes.*;

/**
 * Fixed 3-line input area with text buffer, cursor, and command history.
 */
public class InputArea implements Component {

	private static final int HEIGHT = 3;

	private final StringBuilder buffer = new StringBuilder();
	private int cursorPos = 0;
	private final List<String> history = new ArrayList<>();
	private int historyIndex = -1;
	private String savedInput = "";
	private final Consumer<String> onSubmit;
	private final Theme theme;
	private final AtomicBoolean dirty = new AtomicBoolean(true);

	public InputArea(Consumer<String> onSubmit) {
		this(onSubmit, Theme.DEFAULT);
	}

	public InputArea(Consumer<String> onSubmit, Theme theme) {
		this.onSubmit = onSubmit;
		this.theme = theme;
	}

	/**
	 * Pre-populates the input history (e.g., from a restored session).
	 */
	public void addHistory(String entry) {
		if (entry != null && !entry.isEmpty()) {
			history.add(entry);
		}
	}

	/**
	 * Handles a key event, modifying the buffer as needed.
	 */
	public void handleKey(KeyEvent key) {
		switch (key) {
			case KeyEvent.Character c -> {
				buffer.insert(cursorPos, c.ch());
				cursorPos++;
				historyIndex = -1;
			}
			case KeyEvent.Backspace ignored -> {
				if (cursorPos > 0) {
					buffer.deleteCharAt(cursorPos - 1);
					cursorPos--;
				}
			}
			case KeyEvent.Delete ignored -> {
				if (cursorPos < buffer.length()) {
					buffer.deleteCharAt(cursorPos);
				}
			}
			case KeyEvent.Left ignored -> {
				if (cursorPos > 0) cursorPos--;
			}
			case KeyEvent.Right ignored -> {
				if (cursorPos < buffer.length()) cursorPos++;
			}
			case KeyEvent.Home ignored -> cursorPos = 0;
			case KeyEvent.End ignored -> cursorPos = buffer.length();
			case KeyEvent.Up ignored -> navigateHistory(-1);
			case KeyEvent.Down ignored -> navigateHistory(1);
			case KeyEvent.Enter ignored -> submit();
			default -> { return; } // Don't mark dirty for unhandled keys
		}
		dirty.set(true);
	}

	private void submit() {
		String text = buffer.toString().trim();
		if (!text.isEmpty()) {
			history.add(text);
		}
		buffer.setLength(0);
		cursorPos = 0;
		historyIndex = -1;
		if (!text.isEmpty()) {
			onSubmit.accept(text);
		}
	}

	private void navigateHistory(int direction) {
		if (history.isEmpty()) return;

		if (historyIndex == -1 && direction == -1) {
			// Enter history from the bottom
			savedInput = buffer.toString();
			historyIndex = history.size() - 1;
		} else if (historyIndex == -1 && direction == 1) {
			// Not browsing history, Down does nothing
			return;
		} else if (direction == -1) {
			historyIndex = Math.max(0, historyIndex - 1);
		} else if (direction == 1) {
			historyIndex++;
			if (historyIndex >= history.size()) {
				// Past the end — restore saved input
				historyIndex = -1;
				buffer.setLength(0);
				buffer.append(savedInput);
				cursorPos = buffer.length();
				return;
			}
		}

		if (historyIndex >= 0 && historyIndex < history.size()) {
			buffer.setLength(0);
			buffer.append(history.get(historyIndex));
			cursorPos = buffer.length();
		}
	}

	/**
	 * Returns the current buffer content (for testing).
	 */
	public String bufferContent() {
		return buffer.toString();
	}

	/**
	 * Returns the current cursor position (for testing).
	 */
	public int cursorPosition() {
		return cursorPos;
	}

	@Override
	public List<String> render(int width) {
		String prompt = style("> ", theme.userPrompt());
		int promptVisLen = 2;
		int contentWidth = width - promptVisLen;
		if (contentWidth <= 0) contentWidth = 1;

		String text = buffer.toString();

		// Simple rendering: show text with cursor indicator
		// Insert a cursor marker (inverted space or block char)
		String beforeCursor = text.substring(0, cursorPos);
		String afterCursor = text.substring(cursorPos);
		String cursorChar = cursorPos < text.length()
			? String.valueOf(text.charAt(cursorPos))
			: " ";
		String skipAfter = cursorPos < text.length() ? afterCursor.substring(1) : "";

		String displayText = beforeCursor + "\033[7m" + cursorChar + RESET + skipAfter;

		// Wrap into lines
		List<String> contentLines = wrapForDisplay(text, contentWidth);
		List<String> displayLines = wrapForDisplayWithCursor(text, cursorPos, contentWidth);

		List<String> result = new ArrayList<>(HEIGHT);
		// Top border (thin line)
		result.add(style("─".repeat(width), theme.dim()));

		for (int i = 0; i < HEIGHT - 1; i++) {
			if (i == 0) {
				String lineContent = displayLines.isEmpty() ? "\033[7m \033[0m" : displayLines.get(0);
				result.add(prompt + lineContent);
			} else if (i < displayLines.size()) {
				result.add("  " + displayLines.get(i));
			} else {
				result.add("");
			}
		}

		return result;
	}

	private List<String> wrapForDisplay(String text, int maxWidth) {
		if (text.isEmpty()) return List.of("");
		List<String> lines = new ArrayList<>();
		int i = 0;
		while (i < text.length()) {
			int end = Math.min(i + maxWidth, text.length());
			lines.add(text.substring(i, end));
			i = end;
		}
		return lines;
	}

	private List<String> wrapForDisplayWithCursor(String text, int cursor, int maxWidth) {
		if (maxWidth <= 0) maxWidth = 1;
		List<String> lines = new ArrayList<>();
		int i = 0;
		while (i < text.length() || i <= cursor) {
			int end = Math.min(i + maxWidth, Math.max(text.length(), cursor + 1));
			if (end <= i) break;
			String segment = (end <= text.length()) ? text.substring(i, end) : text.substring(i) + " ".repeat(end - text.length());

			// Check if cursor is in this segment
			if (cursor >= i && cursor < i + maxWidth) {
				int localCursor = cursor - i;
				String before = segment.substring(0, localCursor);
				String cursorCh = localCursor < segment.length() ? segment.substring(localCursor, localCursor + 1) : " ";
				String after = localCursor + 1 < segment.length() ? segment.substring(localCursor + 1) : "";
				lines.add(before + "\033[7m" + cursorCh + RESET + after);
			} else {
				lines.add(segment);
			}

			i += maxWidth;
			if (i > text.length() && i > cursor) break;
		}
		if (lines.isEmpty()) {
			lines.add("\033[7m \033[0m");
		}
		return lines;
	}

	@Override
	public int desiredHeight() {
		return HEIGHT;
	}

	@Override
	public boolean isDirty() {
		return dirty.get();
	}

	@Override
	public void markClean() {
		dirty.set(false);
	}
}
