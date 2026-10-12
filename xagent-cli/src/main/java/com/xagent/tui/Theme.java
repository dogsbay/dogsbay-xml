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

import static com.xagent.tui.AnsiCodes.*;

/**
 * Color theme for TUI output.
 */
public record Theme(
	String assistantText,
	String userPrompt,
	String toolName,
	String toolArgs,
	String toolResultSuccess,
	String toolResultError,
	String error,
	String header,
	String headerValue,
	String spinner,
	String dim,
	String mdHeading1,
	String mdHeading2,
	String mdHeading3,
	String mdBold,
	String mdItalic,
	String mdCodeInline,
	String mdCodeBlock,
	String mdCodeLang,
	String mdLink,
	String mdBlockquote,
	String mdListMarker,
	String mdHorizontalRule
) {

	public static final Theme DEFAULT = new Theme(
		RESET,           // assistantText: default terminal color
		BRIGHT_GREEN,    // userPrompt: green "> "
		BOLD + CYAN,     // toolName: bold cyan
		DIM,             // toolArgs: dimmed
		GREEN,           // toolResultSuccess: green
		RED,             // toolResultError: red
		BRIGHT_RED,      // error: bright red
		BOLD,            // header: bold
		CYAN,            // headerValue: cyan
		YELLOW,          // spinner: yellow
		BRIGHT_BLACK,    // dim: gray
		BOLD + BRIGHT_BLUE,   // mdHeading1
		BRIGHT_BLUE,          // mdHeading2
		BLUE,                 // mdHeading3
		BOLD,                 // mdBold
		ITALIC,               // mdItalic
		YELLOW,               // mdCodeInline
		BRIGHT_BLACK,         // mdCodeBlock (dim gray)
		DIM + ITALIC,         // mdCodeLang
		UNDERLINE + CYAN,     // mdLink
		DIM + ITALIC,         // mdBlockquote
		CYAN,                 // mdListMarker
		BRIGHT_BLACK          // mdHorizontalRule
	);
}
