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
 * Wait until the interface has stopped changing, then answer.
 *
 * <p>For anything driving the editor: a recording, a test. It replaces guessing how long to sleep, which is where a pilot
 * recording spent most of its fragility — repaints lagging behind typing, a menu appearing late, a screenshot taken a moment
 * too early.
 *
 * <p><b>What settled means here, exactly.</b> The window's event queue has been empty for {@code quietMs} without
 * interruption. That covers everything queued for the interface: pending repaints, work posted by an action, a menu about to
 * open, a tree about to refresh. It does <b>not</b> cover work happening on another thread that has not yet posted its result,
 * because nothing in the editor tracks such work centrally. So validation or parsing that is still running will not hold this
 * back, though the moment either posts its result to the interface, that result is waited for.
 *
 * <p>In practice a caller wanting to screenshot a validation result should wait for the result to appear rather than for the
 * interface to be quiet. Closing that gap needs the editor to count its background work, which is a larger change.
 *
 * @param quietMs   how long the queue must stay empty; null for 150 ms.
 * @param timeoutMs how long to wait altogether before giving up; null for 10 s.
 */
public record WaitIdleCommand(Integer quietMs, Integer timeoutMs)
		implements Command<CommandResult>, ReadOnlyCommand {

	/** How long the queue must stay empty before the interface counts as settled. */
	public static final int DEFAULT_QUIET_MS = 150;

	/** How long to wait altogether. Long enough for a slow repaint, short enough to be an answer rather than a hang. */
	public static final int DEFAULT_TIMEOUT_MS = 10_000;

	/**
	 * The longest limit allowed. A caller asking for more is asking for a hang with extra steps, and each round trip is work
	 * on the very thread being watched.
	 */
	public static final int MAX_TIMEOUT_MS = 120_000;

	/**
	 * The longest quiet period allowed.
	 *
	 * <p>A live window is never quiet for long: the text caret blinks on a 500 ms timer, so a quiet period at or above that
	 * can never be satisfied in a real editor and would always end in the limit being reached. Refusing it up front is a
	 * better answer than a wait that cannot succeed.
	 */
	public static final int MAX_QUIET_MS = 400;

	public WaitIdleCommand() {
		this(null, null);
	}

	/** The quiet period to use, given or default. */
	public int quiet() {
		return quietMs == null || quietMs < 0 ? DEFAULT_QUIET_MS : quietMs;
	}

	/** The overall limit to use, given or default, never beyond {@link #MAX_TIMEOUT_MS}. */
	public int timeout() {
		return timeoutMs == null || timeoutMs <= 0 ? DEFAULT_TIMEOUT_MS : Math.min(timeoutMs, MAX_TIMEOUT_MS);
	}

	/**
	 * What is wrong with what was asked for, or null.
	 *
	 * <p>Two combinations cannot succeed, and both look reasonable. A quiet period the limit cannot contain fails every
	 * time, because a quiet period is only proved by two observations that far apart; and a quiet period longer than a
	 * blinking caret is never observed in a live window. Saying so beats reporting a busy interface that was idle.
	 */
	public String fault() {
		if (quietMs != null && quietMs > MAX_QUIET_MS) {
			return "A quiet period of " + quietMs + " ms is longer than a live window is ever quiet for (the text caret"
				+ " blinks twice a second), so it could never be observed. Ask for " + MAX_QUIET_MS + " ms or less.";
		}
		if (quiet() >= timeout()) {
			return "A quiet period of " + quiet() + " ms cannot be observed within a limit of " + timeout()
				+ " ms: the limit has to be the longer of the two, and comfortably so.";
		}
		return null;
	}
}
