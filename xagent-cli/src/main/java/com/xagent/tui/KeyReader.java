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
import org.jline.utils.NonBlockingReader;

import java.io.IOException;

/**
 * Parses raw terminal input into KeyEvent instances.
 * Wraps JLine's NonBlockingReader to handle multi-byte escape sequences.
 */
public class KeyReader {

	private final NonBlockingReader reader;

	public KeyReader(Terminal terminal) {
		this.reader = terminal.reader();
	}

	/**
	 * Constructor that accepts a NonBlockingReader directly (for testing).
	 */
	public KeyReader(NonBlockingReader reader) {
		this.reader = reader;
	}

	/**
	 * Reads the next keystroke, waiting up to timeoutMs milliseconds.
	 * Returns null if no input is available within the timeout.
	 */
	public KeyEvent read(long timeoutMs) throws IOException {
		int c = reader.read(timeoutMs);
		if (c == -1 || c == -2) {
			return null; // EOF or timeout
		}

		// Control characters
		if (c == 3) return new KeyEvent.CtrlC();
		if (c == 4) return new KeyEvent.CtrlD();
		if (c == 13 || c == 10) return new KeyEvent.Enter();
		if (c == 127 || c == 8) return new KeyEvent.Backspace();
		if (c == 9) return new KeyEvent.Tab();
		if (c == 27) return parseEscape();

		// Regular character
		if (c >= 32) {
			return new KeyEvent.Character((char) c);
		}

		return new KeyEvent.Unknown();
	}

	private KeyEvent parseEscape() throws IOException {
		int next = reader.read(50); // short timeout for escape sequence
		if (next == -1 || next == -2) {
			return new KeyEvent.Escape(); // bare Escape key
		}

		if (next == '[') {
			return parseCsi();
		}

		if (next == 'O') {
			// SS3 sequences — sent by many terminals in application cursor mode
			// (which alternate screen buffer often enables)
			int code = reader.read(50);
			return switch (code) {
				case 'A' -> new KeyEvent.Up();
				case 'B' -> new KeyEvent.Down();
				case 'C' -> new KeyEvent.Right();
				case 'D' -> new KeyEvent.Left();
				case 'H' -> new KeyEvent.Home();
				case 'F' -> new KeyEvent.End();
				default -> new KeyEvent.Unknown();
			};
		}

		return new KeyEvent.Unknown();
	}

	private KeyEvent parseCsi() throws IOException {
		int code = reader.read(50);
		if (code == -1 || code == -2) return new KeyEvent.Unknown();

		if (code == '<') {
			return parseSgrMouse();
		}

		return switch (code) {
			case 'A' -> new KeyEvent.Up();
			case 'B' -> new KeyEvent.Down();
			case 'C' -> new KeyEvent.Right();
			case 'D' -> new KeyEvent.Left();
			case 'H' -> new KeyEvent.Home();
			case 'F' -> new KeyEvent.End();
			case '3' -> {
				// Delete key sends ESC[3~
				int tilde = reader.read(50);
				yield (tilde == '~') ? new KeyEvent.Delete() : new KeyEvent.Unknown();
			}
			case '5' -> {
				// PageUp sends ESC[5~
				int tilde = reader.read(50);
				yield (tilde == '~') ? new KeyEvent.PageUp() : new KeyEvent.Unknown();
			}
			case '6' -> {
				// PageDown sends ESC[6~
				int tilde = reader.read(50);
				yield (tilde == '~') ? new KeyEvent.PageDown() : new KeyEvent.Unknown();
			}
			case '1' -> {
				// ESC[1~ = Home, or ESC[1;2A = Shift+Up, etc.
				int next = reader.read(50);
				if (next == '~') {
					yield new KeyEvent.Home();
				} else if (next == ';') {
					// Modifier sequences like ESC[1;2A (Shift+Up)
					int modifier = reader.read(50);
					int arrow = reader.read(50);
					if (modifier == '2' && arrow == 'A') yield new KeyEvent.PageUp();  // Shift+Up → scroll up
					if (modifier == '2' && arrow == 'B') yield new KeyEvent.PageDown(); // Shift+Down → scroll down
					yield new KeyEvent.Unknown();
				}
				yield new KeyEvent.Unknown();
			}
			case '4' -> {
				// ESC[4~ = End
				int tilde = reader.read(50);
				yield (tilde == '~') ? new KeyEvent.End() : new KeyEvent.Unknown();
			}
			default -> new KeyEvent.Unknown();
		};
	}

	/**
	 * Parses SGR mouse sequence: ESC[&lt;button;x;yM or ESC[&lt;button;x;ym
	 * We only care about wheel events (button 64 = up, 65 = down).
	 */
	private KeyEvent parseSgrMouse() throws IOException {
		// Read "button;x;y" followed by 'M' (press) or 'm' (release)
		var buf = new StringBuilder();
		while (true) {
			int c = reader.read(50);
			if (c == -1 || c == -2) return new KeyEvent.Unknown();
			if (c == 'M' || c == 'm') break;
			buf.append((char) c);
		}

		// Parse button number (first field before ';')
		String raw = buf.toString();
		int semi = raw.indexOf(';');
		if (semi < 0) return new KeyEvent.Unknown();

		try {
			int button = Integer.parseInt(raw.substring(0, semi));
			if (button == 64) return new KeyEvent.ScrollUp();
			if (button == 65) return new KeyEvent.ScrollDown();
		} catch (NumberFormatException e) {
			// ignore
		}

		return new KeyEvent.Unknown();
	}
}
