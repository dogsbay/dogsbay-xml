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

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.xagent.tui.AnsiCodes.*;

/**
 * Animated spinner that runs on a virtual thread.
 * Shows a braille dots animation while the agent is working.
 */
public class SpinnerAnimation {

	private static final String[] FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
	private static final long FRAME_INTERVAL_MS = 80;

	private final PrintStream out;
	private final String color;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private Thread thread;
	private String label = "";

	public SpinnerAnimation(PrintStream out, String color) {
		this.out = out;
		this.color = color;
	}

	public SpinnerAnimation(PrintStream out) {
		this(out, Theme.DEFAULT.spinner());
	}

	/**
	 * Starts the spinner with the given label.
	 * If already running, updates the label.
	 */
	public synchronized void start(String label) {
		this.label = label != null ? label : "";
		if (running.get()) {
			return;
		}
		running.set(true);
		thread = Thread.ofVirtual().name("spinner").start(this::animate);
	}

	/**
	 * Stops the spinner and clears its line.
	 */
	public synchronized void stop() {
		if (!running.compareAndSet(true, false)) {
			return;
		}
		if (thread != null) {
			thread.interrupt();
			try {
				thread.join(200);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			thread = null;
		}
		// Clear the spinner line
		out.print(CURSOR_TO_LINE_START + CLEAR_LINE);
		out.flush();
	}

	public boolean isRunning() {
		return running.get();
	}

	private void animate() {
		int frame = 0;
		try {
			while (running.get()) {
				String text = color + FRAMES[frame % FRAMES.length] + RESET
					+ (label.isEmpty() ? "" : " " + label);
				out.print(CURSOR_TO_LINE_START + CLEAR_LINE + text);
				out.flush();
				frame++;
				Thread.sleep(FRAME_INTERVAL_MS);
			}
		} catch (InterruptedException e) {
			// Expected on stop
		}
	}
}
