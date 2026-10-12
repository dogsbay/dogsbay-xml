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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class SpinnerAnimationTest {

	@Test
	void startAndStopLifecycle() throws InterruptedException {
		var out = new ByteArrayOutputStream();
		var spinner = new SpinnerAnimation(new PrintStream(out));

		assertThat(spinner.isRunning()).isFalse();

		spinner.start("Loading...");
		assertThat(spinner.isRunning()).isTrue();

		// Let it animate a few frames
		Thread.sleep(250);

		spinner.stop();
		assertThat(spinner.isRunning()).isFalse();

		String output = out.toString();
		// Should have rendered at least one spinner frame
		assertThat(output).isNotEmpty();
		assertThat(output).contains("Loading...");
	}

	@Test
	void doubleStartDoesNotCreateMultipleThreads() {
		var out = new ByteArrayOutputStream();
		var spinner = new SpinnerAnimation(new PrintStream(out));

		spinner.start("first");
		spinner.start("second"); // should just update label
		assertThat(spinner.isRunning()).isTrue();

		spinner.stop();
		assertThat(spinner.isRunning()).isFalse();
	}

	@Test
	void doubleStopIsSafe() {
		var out = new ByteArrayOutputStream();
		var spinner = new SpinnerAnimation(new PrintStream(out));

		spinner.start("test");
		spinner.stop();
		spinner.stop(); // should not throw
		assertThat(spinner.isRunning()).isFalse();
	}

	@Test
	void stopWithoutStartIsSafe() {
		var out = new ByteArrayOutputStream();
		var spinner = new SpinnerAnimation(new PrintStream(out));

		spinner.stop(); // should not throw
		assertThat(spinner.isRunning()).isFalse();
	}
}
