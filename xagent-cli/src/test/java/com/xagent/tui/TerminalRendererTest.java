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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalRendererTest {

	private TerminalRenderer renderer;
	private StringWriter stringWriter;
	private StubTerminal terminal;

	/**
	 * Minimal stub that only provides writer() for TerminalRenderer testing.
	 */
	static class StubTerminal {
		final PrintWriter writer;

		StubTerminal(StringWriter sw) {
			this.writer = new PrintWriter(sw, true);
		}
	}

	@BeforeEach
	void setUp() {
		renderer = new TerminalRenderer();
		stringWriter = new StringWriter();
	}

	@Test
	void firstRenderPaintsAllLines() {
		var sw = new StringWriter();
		var pw = new PrintWriter(sw, true);
		renderer.renderTo(pw, List.of("line1", "line2", "line3"));

		String output = sw.toString();
		assertThat(output).contains("line1");
		assertThat(output).contains("line2");
		assertThat(output).contains("line3");
	}

	@Test
	void unchangedLinesNotRepainted() {
		var sw = new StringWriter();
		var pw = new PrintWriter(sw, true);
		renderer.renderTo(pw, List.of("line1", "line2", "line3"));

		sw.getBuffer().setLength(0); // reset

		renderer.renderTo(pw, List.of("line1", "CHANGED", "line3"));
		String output = sw.toString();

		assertThat(output).contains("CHANGED");
		// Row 1 and 3 cursor sequences should be absent
		assertThat(output).doesNotContain("\033[1;1H");
		assertThat(output).doesNotContain("\033[3;1H");
		// Row 2 cursor sequence should be present
		assertThat(output).contains("\033[2;1H");
	}

	@Test
	void previousFrameStoredCorrectly() {
		var sw = new StringWriter();
		var pw = new PrintWriter(sw, true);
		List<String> frame = List.of("a", "b", "c");
		renderer.renderTo(pw, frame);

		assertThat(renderer.previousFrame()).isEqualTo(frame);
	}

	@Test
	void invalidateForcesFullRepaint() {
		var sw = new StringWriter();
		var pw = new PrintWriter(sw, true);
		renderer.renderTo(pw, List.of("line1", "line2"));

		sw.getBuffer().setLength(0);

		renderer.invalidate();
		renderer.renderTo(pw, List.of("line1", "line2"));
		String output = sw.toString();

		assertThat(output).contains("line1");
		assertThat(output).contains("line2");
	}

	@Test
	void sizeChangeForcesFullRepaint() {
		var sw = new StringWriter();
		var pw = new PrintWriter(sw, true);
		renderer.renderTo(pw, List.of("line1", "line2"));

		sw.getBuffer().setLength(0);

		renderer.renderTo(pw, List.of("line1", "line2", "line3"));
		String output = sw.toString();

		assertThat(output).contains("line1");
		assertThat(output).contains("line3");
	}

	@Test
	void renderIncludesSyncUpdateSequences() {
		var sw = new StringWriter();
		var pw = new PrintWriter(sw, true);
		renderer.renderTo(pw, List.of("test"));
		String output = sw.toString();

		assertThat(output).contains("\033[?2026h");
		assertThat(output).contains("\033[?2026l");
	}
}
