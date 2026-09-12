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
 * ANSI-aware word wrapper that breaks at word boundaries.
 */
public final class TextWrapper {

	private TextWrapper() {}

	/**
	 * Word-wraps text with ANSI codes, breaking at word boundaries.
	 * ANSI escape sequences are preserved and carried across line breaks.
	 */
	public static List<String> wrap(String text, int maxWidth) {
		if (maxWidth <= 0) maxWidth = 1;
		if (text.isEmpty()) {
			return List.of("");
		}

		List<String> result = new ArrayList<>();

		for (String rawLine : text.split("\n", -1)) {
			if (rawLine.isEmpty()) {
				result.add("");
				continue;
			}
			wrapLine(rawLine, maxWidth, result);
		}

		return result;
	}

	private static void wrapLine(String line, int maxWidth, List<String> result) {
		// Tokenize into words and ANSI sequences
		List<Token> tokens = tokenize(line);

		StringBuilder currentLine = new StringBuilder();
		int currentVisible = 0;
		// Track active ANSI state to carry across line breaks
		String activeAnsi = "";

		for (Token token : tokens) {
			if (token.isAnsi) {
				// ANSI sequence — append without consuming visible width
				currentLine.append(token.text);
				if (isReset(token.text)) {
					activeAnsi = "";
				} else {
					activeAnsi += token.text;
				}
				continue;
			}

			// Visible text token (a word or space)
			String word = token.text;
			int wordLen = word.length();

			if (word.equals(" ")) {
				// Space: add if it fits, otherwise start new line
				if (currentVisible + 1 <= maxWidth) {
					currentLine.append(" ");
					currentVisible++;
				}
				continue;
			}

			if (currentVisible + wordLen <= maxWidth) {
				// Word fits on current line
				currentLine.append(word);
				currentVisible += wordLen;
			} else if (currentVisible == 0) {
				// Word at start of line but too long — hard break
				hardBreak(word, maxWidth, currentLine, activeAnsi, result);
				// After hard break, currentLine has the remainder
				currentVisible = Layout.visibleLength(currentLine.toString());
			} else {
				// Word doesn't fit — start new line
				// Close current line
				if (!activeAnsi.isEmpty()) {
					currentLine.append(AnsiCodes.RESET);
				}
				result.add(currentLine.toString());

				// Start new line with active ANSI state
				currentLine = new StringBuilder();
				if (!activeAnsi.isEmpty()) {
					currentLine.append(activeAnsi);
				}
				currentVisible = 0;

				if (wordLen <= maxWidth) {
					currentLine.append(word);
					currentVisible = wordLen;
				} else {
					// Still too long — hard break
					hardBreak(word, maxWidth, currentLine, activeAnsi, result);
					currentVisible = Layout.visibleLength(currentLine.toString());
				}
			}
		}

		// Emit remaining content
		result.add(currentLine.toString());
	}

	private static void hardBreak(String word, int maxWidth,
								   StringBuilder currentLine, String activeAnsi,
								   List<String> result) {
		int i = 0;
		while (i < word.length()) {
			int remaining = maxWidth - Layout.visibleLength(currentLine.toString());
			if (remaining <= 0) {
				if (!activeAnsi.isEmpty()) {
					currentLine.append(AnsiCodes.RESET);
				}
				result.add(currentLine.toString());
				currentLine.setLength(0);
				if (!activeAnsi.isEmpty()) {
					currentLine.append(activeAnsi);
				}
				remaining = maxWidth;
			}
			int end = Math.min(i + remaining, word.length());
			currentLine.append(word, i, end);
			i = end;
		}
	}

	private static boolean isReset(String ansi) {
		return ansi.equals(AnsiCodes.RESET);
	}

	/**
	 * Tokenizes text into ANSI sequences, spaces, and words.
	 */
	static List<Token> tokenize(String text) {
		List<Token> tokens = new ArrayList<>();
		int i = 0;
		StringBuilder word = new StringBuilder();

		while (i < text.length()) {
			char c = text.charAt(i);

			if (c == '\033') {
				// Flush current word
				if (!word.isEmpty()) {
					tokens.add(new Token(word.toString(), false));
					word.setLength(0);
				}
				// Read ANSI sequence
				int start = i;
				i++; // skip ESC
				if (i < text.length() && text.charAt(i) == '[') {
					i++; // skip [
					while (i < text.length()) {
						char sc = text.charAt(i);
						i++;
						if ((sc >= 'A' && sc <= 'Z') || (sc >= 'a' && sc <= 'z')) {
							break;
						}
					}
				}
				tokens.add(new Token(text.substring(start, i), true));
			} else if (c == ' ') {
				// Flush current word
				if (!word.isEmpty()) {
					tokens.add(new Token(word.toString(), false));
					word.setLength(0);
				}
				tokens.add(new Token(" ", false));
				i++;
			} else {
				word.append(c);
				i++;
			}
		}

		if (!word.isEmpty()) {
			tokens.add(new Token(word.toString(), false));
		}

		return tokens;
	}

	record Token(String text, boolean isAnsi) {}
}
