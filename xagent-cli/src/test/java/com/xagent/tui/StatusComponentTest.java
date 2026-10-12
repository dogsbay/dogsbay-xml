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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatusComponentTest {

	private StatusComponent status;

	@BeforeEach
	void setUp() {
		status = new StatusComponent();
	}

	@Test
	void initialStateIsReady() {
		assertThat(status.state()).isEqualTo(StatusComponent.State.READY);

		List<String> lines = status.render(80);
		assertThat(lines).hasSize(1);
		assertThat(stripAnsi(lines.get(0))).isEqualTo("Ready");
	}

	@Test
	void thinkingState() {
		status.setThinking();
		assertThat(status.state()).isEqualTo(StatusComponent.State.THINKING);

		List<String> lines = status.render(80);
		String content = stripAnsi(lines.get(0));
		assertThat(content).contains("Thinking...");
	}

	@Test
	void runningStateShowsToolName() {
		status.setRunning("bash");
		assertThat(status.state()).isEqualTo(StatusComponent.State.RUNNING);

		List<String> lines = status.render(80);
		String content = stripAnsi(lines.get(0));
		assertThat(content).contains("Running bash...");
	}

	@Test
	void errorStateShowsMessage() {
		status.setError("API timeout");
		assertThat(status.state()).isEqualTo(StatusComponent.State.ERROR);

		List<String> lines = status.render(80);
		String content = stripAnsi(lines.get(0));
		assertThat(content).contains("Error: API timeout");
	}

	@Test
	void transitionFromThinkingToRunningToReady() {
		status.setThinking();
		assertThat(status.state()).isEqualTo(StatusComponent.State.THINKING);

		status.setRunning("read");
		assertThat(status.state()).isEqualTo(StatusComponent.State.RUNNING);

		status.setReady();
		assertThat(status.state()).isEqualTo(StatusComponent.State.READY);
	}

	@Test
	void tickAdvancesSpinnerFrame() {
		status.setThinking();
		String first = status.render(80).get(0);

		// Tick multiple times to change the frame
		for (int i = 0; i < 3; i++) {
			status.tick();
		}
		String later = status.render(80).get(0);

		// Spinner frame char should differ
		assertThat(later).isNotEqualTo(first);
	}

	@Test
	void tickOnReadyDoesNotMarkDirty() {
		status.setReady();
		status.markClean();

		status.tick();
		// Ready state doesn't tick, so shouldn't be dirty
		assertThat(status.isDirty()).isFalse();
	}

	@Test
	void desiredHeightIs1() {
		assertThat(status.desiredHeight()).isEqualTo(1);
	}

	@Test
	void dirtyFlagWorksCorrectly() {
		assertThat(status.isDirty()).isTrue();
		status.markClean();
		assertThat(status.isDirty()).isFalse();

		status.setThinking();
		assertThat(status.isDirty()).isTrue();
	}

	@Test
	void showsUsageWhenSet() {
		status.updateUsage(5, 1500, "$0.0030");
		List<String> lines = status.render(80);
		String content = stripAnsi(lines.get(0));
		assertThat(content).contains("5 msgs");
		assertThat(content).contains("1.5k tokens");
		assertThat(content).contains("$0.0030");
	}

	@Test
	void usageHiddenWhenNoTokens() {
		List<String> lines = status.render(80);
		String content = stripAnsi(lines.get(0));
		assertThat(content).isEqualTo("Ready");
	}

	@Test
	void usageHidesCostForFreeProviders() {
		status.updateUsage(3, 500, "$0.0000");
		List<String> lines = status.render(80);
		String content = stripAnsi(lines.get(0));
		assertThat(content).contains("3 msgs");
		assertThat(content).contains("500 tokens");
		assertThat(content).doesNotContain("$0.0000");
	}

	private String stripAnsi(String s) {
		return s.replaceAll("\033\\[[0-9;]*[a-zA-Z]", "");
	}
}
