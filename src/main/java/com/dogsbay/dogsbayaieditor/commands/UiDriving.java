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

/**
 * Whether this run lets a caller drive the editor: run any menu command, or type any line into the terminal.
 *
 * <p>Off unless asked for, because those two cannot be contained. The write gate keeps an agent inside the open project by
 * examining the files a command names, and neither of these names any: a menu command does whatever it does, and a shell line
 * can do anything at all. A first version of them claimed the widest write scope, which read as cautious and was in fact the
 * opposite — a scope with no paths in it leases nothing and refuses nothing, so the gate became a no-op for precisely the two
 * commands that needed it most. Review caught it before it shipped.
 *
 * <p>So containment is not attempted and the capability is withheld instead. A recording or a test asks for it by starting
 * the editor with {@code dogsbay-xml gui --allow-driving}, which is an operator saying "this window is mine to drive".
 * Without it the methods are refused, and an agent holding the integration server's token gains nothing it did not have.
 *
 * <p>Showing a panel is not gated: it changes nothing.
 */
public final class UiDriving {

	private static volatile boolean allowed = false;

	private UiDriving() {
	}

	/** Let this run be driven. Set before the window is shown, by the caller that started it. */
	public static void allow(boolean allow) {
		allowed = allow;
	}

	/** Whether this run may be driven. */
	public static boolean isAllowed() {
		return allowed;
	}

	/** The refusal, worded so the caller knows what to do about it. */
	static CommandException refused(String what) {
		return new CommandException(CommandException.ErrorCode.INVALID_ARGUMENT,
			what + " is not allowed in this editor. It runs whatever it is given and cannot be kept inside the open"
				+ " project, so it is off unless asked for: start the editor with `dogsbay-xml gui --allow-driving`.");
	}
}
