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

import org.jline.utils.NonBlockingReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class KeyReaderTest {

	@Test
	void regularCharacter() throws IOException {
		var keyReader = new KeyReader(stubReader('a'));
		var event = keyReader.read(16);
		assertThat(event).isInstanceOf(KeyEvent.Character.class);
		assertThat(((KeyEvent.Character) event).ch()).isEqualTo('a');
	}

	@Test
	void enterKey() throws IOException {
		var keyReader = new KeyReader(stubReader(13));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.Enter.class);
	}

	@Test
	void backspaceKey() throws IOException {
		var keyReader = new KeyReader(stubReader(127));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.Backspace.class);
	}

	@Test
	void ctrlC() throws IOException {
		var keyReader = new KeyReader(stubReader(3));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.CtrlC.class);
	}

	@Test
	void ctrlD() throws IOException {
		var keyReader = new KeyReader(stubReader(4));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.CtrlD.class);
	}

	@Test
	void arrowUp() throws IOException {
		var keyReader = new KeyReader(stubReader(27, '[', 'A'));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.Up.class);
	}

	@Test
	void arrowDown() throws IOException {
		var keyReader = new KeyReader(stubReader(27, '[', 'B'));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.Down.class);
	}

	@Test
	void arrowRight() throws IOException {
		var keyReader = new KeyReader(stubReader(27, '[', 'C'));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.Right.class);
	}

	@Test
	void arrowLeft() throws IOException {
		var keyReader = new KeyReader(stubReader(27, '[', 'D'));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.Left.class);
	}

	@Test
	void deleteKey() throws IOException {
		var keyReader = new KeyReader(stubReader(27, '[', '3', '~'));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.Delete.class);
	}

	@Test
	void pageUp() throws IOException {
		var keyReader = new KeyReader(stubReader(27, '[', '5', '~'));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.PageUp.class);
	}

	@Test
	void pageDown() throws IOException {
		var keyReader = new KeyReader(stubReader(27, '[', '6', '~'));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.PageDown.class);
	}

	@Test
	void homeKey() throws IOException {
		var keyReader = new KeyReader(stubReader(27, '[', 'H'));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.Home.class);
	}

	@Test
	void endKey() throws IOException {
		var keyReader = new KeyReader(stubReader(27, '[', 'F'));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.End.class);
	}

	@Test
	void ss3ArrowUp() throws IOException {
		var keyReader = new KeyReader(stubReader(27, 'O', 'A'));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.Up.class);
	}

	@Test
	void ss3ArrowDown() throws IOException {
		var keyReader = new KeyReader(stubReader(27, 'O', 'B'));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.Down.class);
	}

	@Test
	void ss3ArrowRight() throws IOException {
		var keyReader = new KeyReader(stubReader(27, 'O', 'C'));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.Right.class);
	}

	@Test
	void ss3ArrowLeft() throws IOException {
		var keyReader = new KeyReader(stubReader(27, 'O', 'D'));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.Left.class);
	}

	@Test
	void tabKey() throws IOException {
		var keyReader = new KeyReader(stubReader(9));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.Tab.class);
	}

	@Test
	void mouseWheelUp() throws IOException {
		// ESC[<64;10;20M — SGR mouse wheel up
		var keyReader = new KeyReader(stubReader(27, '[', '<', '6', '4', ';', '1', '0', ';', '2', '0', 'M'));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.ScrollUp.class);
	}

	@Test
	void mouseWheelDown() throws IOException {
		// ESC[<65;10;20M — SGR mouse wheel down
		var keyReader = new KeyReader(stubReader(27, '[', '<', '6', '5', ';', '1', '0', ';', '2', '0', 'M'));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.ScrollDown.class);
	}

	@Test
	void mouseClickIgnored() throws IOException {
		// ESC[<0;10;20M — SGR mouse button press (not wheel)
		var keyReader = new KeyReader(stubReader(27, '[', '<', '0', ';', '1', '0', ';', '2', '0', 'M'));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.Unknown.class);
	}

	@Test
	void bareEscape() throws IOException {
		var keyReader = new KeyReader(stubReader(27, -2));
		assertThat(keyReader.read(16)).isInstanceOf(KeyEvent.Escape.class);
	}

	@Test
	void timeoutReturnsNull() throws IOException {
		var keyReader = new KeyReader(stubReader(-2));
		assertThat(keyReader.read(16)).isNull();
	}

	/**
	 * Creates a NonBlockingReader stub that returns the given sequence of values.
	 */
	private NonBlockingReader stubReader(int... chars) {
		var index = new AtomicInteger(0);
		return new NonBlockingReader() {
			@Override
			protected int read(long timeout, boolean isPeek) throws IOException {
				int i = index.getAndIncrement();
				return i < chars.length ? chars[i] : -2;
			}

			@Override
			public int readBuffered(char[] buf, int off, int len, long timeout) throws IOException {
				int c = read(timeout, false);
				if (c < 0) return c;
				buf[off] = (char) c;
				return 1;
			}

			@Override
			public void close() {}
		};
	}
}
