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

/**
 * ANSI escape sequence constants for terminal styling.
 */
public final class AnsiCodes {

	private AnsiCodes() {}

	public static final String ESC = "\033[";
	public static final String RESET = ESC + "0m";
	public static final String BOLD = ESC + "1m";
	public static final String DIM = ESC + "2m";
	public static final String ITALIC = ESC + "3m";
	public static final String UNDERLINE = ESC + "4m";
	public static final String REVERSE = ESC + "7m";

	// Foreground colors
	public static final String BLACK = ESC + "30m";
	public static final String RED = ESC + "31m";
	public static final String GREEN = ESC + "32m";
	public static final String YELLOW = ESC + "33m";
	public static final String BLUE = ESC + "34m";
	public static final String MAGENTA = ESC + "35m";
	public static final String CYAN = ESC + "36m";
	public static final String WHITE = ESC + "37m";

	// Bright foreground colors
	public static final String BRIGHT_BLACK = ESC + "90m";
	public static final String BRIGHT_RED = ESC + "91m";
	public static final String BRIGHT_GREEN = ESC + "92m";
	public static final String BRIGHT_YELLOW = ESC + "93m";
	public static final String BRIGHT_BLUE = ESC + "94m";
	public static final String BRIGHT_MAGENTA = ESC + "95m";
	public static final String BRIGHT_CYAN = ESC + "96m";
	public static final String BRIGHT_WHITE = ESC + "97m";

	// Cursor control
	public static final String CURSOR_SAVE = ESC + "s";
	public static final String CURSOR_RESTORE = ESC + "u";
	public static final String CLEAR_LINE = ESC + "2K";
	public static final String CURSOR_TO_LINE_START = "\r";

	/**
	 * Wraps text with the given ANSI style code and a reset suffix.
	 */
	public static String style(String text, String... codes) {
		if (text == null || text.isEmpty()) return "";
		var sb = new StringBuilder();
		for (String code : codes) {
			sb.append(code);
		}
		sb.append(text);
		sb.append(RESET);
		return sb.toString();
	}
}
