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
package com.dogsbay.dogsbayaieditor.commands;

import com.dogsbay.dogsbayaieditor.commands.results.CommandResult;

/**
 * Type text, key by key, the way a person would.
 *
 * <p>Through the real keyboard path, so tag completion, smart indent and prompting behave as they do for someone at the
 * keyboard. That is the point: a watch-along that shows text arriving by other means shows something the viewer cannot
 * reproduce. Setting a document's contents outright is what {@code setContent} is for.
 *
 * <p><b>Typing is driving, not editing.</b> Keys go to whatever has focus. If that is the terminal, typing runs shell
 * commands; if it is a dialog, typing answers it. So this sits behind {@code --allow-driving} with the other two commands
 * that cannot be contained, rather than being treated as an edit to a document.
 *
 * <p>Keys go to whatever had focus when the call started, and if anything takes focus part way through — a completion popup,
 * a dialog, a background job stealing it — the typing stops and says what took it. Following the focus would mean a stray
 * dialog quietly receiving keystrokes, which for a command that can run shell lines is not a risk worth taking.
 *
 * <p>Nothing here marks the edits as a person's. A recording that needs files without revision markup starts the editor with
 * {@code --no-change-marks}, which is about what lands in the document; who typed it stays true in the audit either way.
 *
 * @param text the characters to type. A line feed is Enter; a tab is Tab.
 * @param cps  characters a second, so the typing can be watched; null for 20, and 0 for as fast as the editor will take it.
 */
public record TypeTextCommand(String text, Integer cps) implements Command<CommandResult> {

	/** Fast enough not to bore a viewer, slow enough to read. */
	public static final int DEFAULT_CPS = 20;

	/** Above this the delay is shorter than the interface can honour, so it is the same as asking for no delay. */
	public static final int MAX_CPS = 1000;

	/**
	 * The longest one call may take.
	 *
	 * <p>The command-line client gives a request thirty seconds, and nothing overrides it, so a longer call is answered by a
	 * timeout while the editor carries on typing: the caller cannot tell how much landed, and a retry types it twice. Well
	 * inside the intended use, too — a deliberately watchable five characters a second reaches it at 150 characters. Better
	 * to refuse and say how than to be the first command whose ordinary use breaks its own caller.
	 */
	public static final long MAX_TOTAL_MS = 20_000;

	public TypeTextCommand(String text) {
		this(text, null);
	}

	/** The rate to type at, given or default; 0 means no delay at all. */
	public int rate() {
		if (cps == null || cps < 0) {
			return DEFAULT_CPS;
		}
		return Math.min(cps, MAX_CPS);
	}

	/** The gap between keys in milliseconds, or 0 for none. */
	public long gapMs() {
		int rate = rate();
		return rate == 0 ? 0 : Math.max(1, 1000L / rate);
	}

	/** How long this call would take altogether. */
	public long totalMs() {
		return text == null ? 0 : Math.max(0, text.length() - 1) * gapMs();
	}

	/** What is wrong with what was asked for, or null. */
	public String fault() {
		if (totalMs() > MAX_TOTAL_MS) {
			return "Typing " + text.length() + " characters at " + rate() + " a second would take "
				+ (totalMs() / 1000) + " s, and a call is answered within " + (MAX_TOTAL_MS / 1000) + " s. Type faster,"
				+ " or type it in shorter pieces so each one is answered.";
		}
		return null;
	}
}
