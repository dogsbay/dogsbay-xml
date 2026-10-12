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

import static org.assertj.core.api.Assertions.assertThat;

class PickerComponentTest {

	@Test
	void inactiveRendersNothingAndZeroHeight() {
		var picker = new PickerComponent();
		assertThat(picker.isActive()).isFalse();
		assertThat(picker.desiredHeight()).isZero();
		assertThat(picker.render(80)).isEmpty();
	}

	@Test
	void showRendersTitleOptionsAndHint() {
		var picker = new PickerComponent();
		picker.show("Choose a provider:", List.of("ollama", "openai"));

		assertThat(picker.isActive()).isTrue();
		// title + 2 options + hint line
		assertThat(picker.desiredHeight()).isEqualTo(4);

		String text = String.join("\n", picker.render(80));
		assertThat(text).contains("Choose a provider:");
		assertThat(text).contains("ollama");
		assertThat(text).contains("openai");
		assertThat(text).contains("Enter"); // hint mentions Enter
	}

	@Test
	void selectedRowIsHighlighted() {
		var picker = new PickerComponent();
		picker.show("t", List.of("a", "b"));

		List<String> lines = picker.render(80);
		// lines: [title, option0 (selected), option1, hint]
		assertThat(lines.get(1)).contains(AnsiCodes.REVERSE);
		assertThat(lines.get(2)).doesNotContain(AnsiCodes.REVERSE);
	}

	@Test
	void moveDownAndUpChangeAndWrapSelection() {
		var picker = new PickerComponent();
		picker.show("t", List.of("a", "b", "c"));
		assertThat(picker.selected()).isZero();

		picker.moveDown();
		assertThat(picker.selected()).isEqualTo(1);
		picker.moveDown();
		picker.moveDown();           // wraps from index 2 back to 0
		assertThat(picker.selected()).isZero();
		picker.moveUp();             // wraps from 0 to last
		assertThat(picker.selected()).isEqualTo(2);
	}

	@Test
	void hideResetsToInactive() {
		var picker = new PickerComponent();
		picker.show("t", List.of("a"));
		picker.hide();
		assertThat(picker.isActive()).isFalse();
		assertThat(picker.desiredHeight()).isZero();
	}

	@Test
	void movementOnEmptyOptionsIsSafe() {
		var picker = new PickerComponent();
		picker.show("Loading…", List.of());
		picker.moveDown();
		picker.moveUp();
		assertThat(picker.selected()).isZero();
		// title + hint, no option rows
		assertThat(picker.desiredHeight()).isEqualTo(2);
	}
}
