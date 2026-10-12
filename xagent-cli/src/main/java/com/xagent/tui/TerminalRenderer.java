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

import org.jline.terminal.Terminal;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Differential rendering engine for the TUI.
 * Compares frames line-by-line and only repaints changed lines.
 * Uses synchronized update mode (CSI 2026) to prevent tearing.
 */
public class TerminalRenderer {

	private static final String SYNC_START = "\033[?2026h";
	private static final String SYNC_END = "\033[?2026l";
	private static final String HIDE_CURSOR = "\033[?25l";
	private static final String SHOW_CURSOR = "\033[?25h";

	private List<String> previousFrame = new ArrayList<>();
	private boolean forceFullRepaint = true;

	/**
	 * Forces the next render to be a full repaint (e.g., after resize).
	 */
	public void invalidate() {
		forceFullRepaint = true;
	}

	/**
	 * Renders the given lines to the terminal, using differential updates.
	 */
	public void render(Terminal terminal, List<String> lines) {
		renderTo(terminal.writer(), lines);
	}

	/**
	 * Renders the given lines to a PrintWriter, using differential updates.
	 * Each line should correspond to one row of the terminal.
	 */
	public void renderTo(PrintWriter writer, List<String> lines) {
		writer.print(HIDE_CURSOR);
		writer.print(SYNC_START);

		if (forceFullRepaint || previousFrame.size() != lines.size()) {
			// Full repaint
			for (int i = 0; i < lines.size(); i++) {
				writer.print(cursorTo(i + 1));
				writer.print("\033[2K"); // clear line
				writer.print(lines.get(i));
			}
			forceFullRepaint = false;
		} else {
			// Differential repaint — only changed lines
			for (int i = 0; i < lines.size(); i++) {
				String newLine = lines.get(i);
				String oldLine = i < previousFrame.size() ? previousFrame.get(i) : "";
				if (!newLine.equals(oldLine)) {
					writer.print(cursorTo(i + 1));
					writer.print("\033[2K"); // clear line
					writer.print(newLine);
				}
			}
		}

		writer.print(SYNC_END);
		writer.print(SHOW_CURSOR);
		writer.flush();

		previousFrame = new ArrayList<>(lines);
	}

	/**
	 * Returns the previous frame for testing purposes.
	 */
	public List<String> previousFrame() {
		return List.copyOf(previousFrame);
	}

	/**
	 * CSI sequence to move cursor to given row, column 1.
	 */
	private static String cursorTo(int row) {
		return "\033[" + row + ";1H";
	}
}
