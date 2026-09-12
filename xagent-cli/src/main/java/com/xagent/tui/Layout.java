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

/**
 * Vertical layout manager that allocates screen height among components.
 * Fixed-height components get their desiredHeight(), flexible components split the remainder.
 */
public class Layout {

	private final List<Component> components;

	public Layout(List<Component> components) {
		this.components = List.copyOf(components);
	}

	/**
	 * Returns true if any component is dirty.
	 */
	public boolean isDirty() {
		for (var c : components) {
			if (c.isDirty()) return true;
		}
		return false;
	}

	/**
	 * Marks all components as clean.
	 */
	public void markAllClean() {
		for (var c : components) {
			c.markClean();
		}
	}

	/**
	 * Lays out all components within the given dimensions.
	 * Returns exactly {@code height} lines, padding or truncating as needed.
	 */
	public List<String> layout(int width, int height) {
		// Calculate fixed height usage and count flexible components
		int fixedTotal = 0;
		int flexCount = 0;
		for (var c : components) {
			int dh = c.desiredHeight();
			if (dh >= 0) {
				fixedTotal += dh;
			} else {
				flexCount++;
			}
		}

		int remaining = Math.max(0, height - fixedTotal);
		int flexHeight = flexCount > 0 ? remaining / flexCount : 0;
		int flexExtra = flexCount > 0 ? remaining % flexCount : 0;

		List<String> result = new ArrayList<>(height);
		int flexIndex = 0;
		for (var c : components) {
			int dh = c.desiredHeight();
			int allocated;
			if (dh >= 0) {
				allocated = dh;
			} else {
				allocated = flexHeight + (flexIndex < flexExtra ? 1 : 0);
				flexIndex++;
			}

			List<String> rendered = c.render(width);

			// Add rendered lines up to allocated height
			for (int i = 0; i < allocated; i++) {
				if (i < rendered.size()) {
					result.add(padOrTruncate(rendered.get(i), width));
				} else {
					result.add(" ".repeat(width));
				}
			}
		}

		// Ensure exactly height lines
		while (result.size() < height) {
			result.add(" ".repeat(width));
		}
		if (result.size() > height) {
			result = new ArrayList<>(result.subList(0, height));
		}

		return result;
	}

	/**
	 * Pads or truncates a line to exactly the given visible width.
	 * Accounts for ANSI escape sequences which don't take visible space.
	 */
	static String padOrTruncate(String line, int width) {
		int visible = visibleLength(line);
		if (visible == width) {
			return line;
		} else if (visible < width) {
			return line + " ".repeat(width - visible);
		} else {
			return truncateToVisible(line, width);
		}
	}

	/**
	 * Returns the visible length of a string, ignoring ANSI escape sequences.
	 */
	static int visibleLength(String s) {
		int len = 0;
		boolean inEscape = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\033') {
				inEscape = true;
			} else if (inEscape) {
				if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z') {
					inEscape = false;
				}
			} else {
				len++;
			}
		}
		return len;
	}

	/**
	 * Truncates a string to the given visible width, preserving ANSI sequences.
	 */
	static String truncateToVisible(String s, int maxVisible) {
		var sb = new StringBuilder();
		int visible = 0;
		boolean inEscape = false;
		for (int i = 0; i < s.length() && visible < maxVisible; i++) {
			char c = s.charAt(i);
			sb.append(c);
			if (c == '\033') {
				inEscape = true;
			} else if (inEscape) {
				if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z') {
					inEscape = false;
				}
			} else {
				visible++;
			}
		}
		// Append any remaining ANSI sequence that was started but not finished
		sb.append(AnsiCodes.RESET);
		return sb.toString();
	}
}
