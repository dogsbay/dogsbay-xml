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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class LayoutTest {

	/** Stub component with fixed height. */
	static class FixedComponent implements Component {
		final int height;
		final String content;
		final AtomicBoolean dirty = new AtomicBoolean(true);

		FixedComponent(int height, String content) {
			this.height = height;
			this.content = content;
		}

		@Override
		public List<String> render(int width) {
			return java.util.stream.IntStream.range(0, height)
				.mapToObj(i -> content)
				.toList();
		}

		@Override
		public int desiredHeight() { return height; }

		@Override
		public boolean isDirty() { return dirty.get(); }

		@Override
		public void markClean() { dirty.set(false); }
	}

	/** Stub component with flexible height. */
	static class FlexComponent implements Component {
		final String content;
		final AtomicBoolean dirty = new AtomicBoolean(true);

		FlexComponent(String content) {
			this.content = content;
		}

		@Override
		public List<String> render(int width) {
			return List.of(content);
		}

		@Override
		public int desiredHeight() { return -1; }

		@Override
		public boolean isDirty() { return dirty.get(); }

		@Override
		public void markClean() { dirty.set(false); }
	}

	@Test
	void fixedComponentsGetExactHeight() {
		var fixed1 = new FixedComponent(2, "header");
		var fixed2 = new FixedComponent(1, "footer");
		var flex = new FlexComponent("content");

		var layout = new Layout(List.of(fixed1, flex, fixed2));
		List<String> lines = layout.layout(10, 10);

		assertThat(lines).hasSize(10);
		// First 2 lines from fixed1
		assertThat(lines.get(0)).startsWith("header");
		assertThat(lines.get(1)).startsWith("header");
		// Last line from fixed2
		assertThat(lines.get(9)).startsWith("footer");
	}

	@Test
	void flexibleComponentGetsRemainingSpace() {
		var fixed = new FixedComponent(2, "top");
		var flex = new FlexComponent("mid");

		var layout = new Layout(List.of(fixed, flex));
		List<String> lines = layout.layout(5, 8);

		assertThat(lines).hasSize(8);
		// 2 fixed + 6 flexible = 8
		assertThat(lines.get(0)).startsWith("top");
		assertThat(lines.get(1)).startsWith("top");
		assertThat(lines.get(2)).startsWith("mid");
	}

	@Test
	void multipleFlexibleComponentsSplitRemaining() {
		var flex1 = new FlexComponent("a");
		var flex2 = new FlexComponent("b");

		var layout = new Layout(List.of(flex1, flex2));
		List<String> lines = layout.layout(5, 10);

		assertThat(lines).hasSize(10);
		// Each gets 5 lines
		assertThat(lines.get(0)).startsWith("a");
		assertThat(lines.get(5)).startsWith("b");
	}

	@Test
	void zeroRemainingGivesFlexZeroHeight() {
		var fixed = new FixedComponent(10, "x");
		var flex = new FlexComponent("y");

		var layout = new Layout(List.of(fixed, flex));
		List<String> lines = layout.layout(5, 10);

		assertThat(lines).hasSize(10);
		// All 10 lines from fixed, flex gets 0
		for (String line : lines) {
			assertThat(line).startsWith("x");
		}
	}

	@Test
	void outputPaddedToExactWidth() {
		var fixed = new FixedComponent(1, "hi");
		var layout = new Layout(List.of(fixed));
		List<String> lines = layout.layout(10, 3);

		assertThat(lines).hasSize(3);
		assertThat(lines.get(0)).hasSize(10);
		assertThat(lines.get(0)).isEqualTo("hi        ");
	}

	@Test
	void visibleLengthIgnoresAnsiCodes() {
		String styled = AnsiCodes.style("hello", AnsiCodes.RED);
		assertThat(Layout.visibleLength(styled)).isEqualTo(5);
		assertThat(Layout.visibleLength("plain")).isEqualTo(5);
	}

	@Test
	void isDirtyReturnsTrueIfAnyComponentDirty() {
		var c1 = new FixedComponent(1, "a");
		var c2 = new FixedComponent(1, "b");
		c1.dirty.set(false);
		c2.dirty.set(true);

		var layout = new Layout(List.of(c1, c2));
		assertThat(layout.isDirty()).isTrue();
	}

	@Test
	void markAllCleanClearsAllComponents() {
		var c1 = new FixedComponent(1, "a");
		var c2 = new FixedComponent(1, "b");

		var layout = new Layout(List.of(c1, c2));
		layout.markAllClean();

		assertThat(c1.isDirty()).isFalse();
		assertThat(c2.isDirty()).isFalse();
		assertThat(layout.isDirty()).isFalse();
	}
}
