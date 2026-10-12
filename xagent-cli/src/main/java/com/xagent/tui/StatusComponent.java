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

import com.xagent.core.UsageStats;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.xagent.tui.AnsiCodes.*;

/**
 * Fixed 1-line status bar showing agent state, token count, and cost.
 * States: Ready, Thinking..., Running tool_name..., Error: message
 */
public class StatusComponent implements Component {

	private static final String[] SPINNER_FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

	public enum State { READY, THINKING, RUNNING, AWAITING, ERROR }

	private volatile State state = State.READY;
	private volatile String detail = "";
	private volatile int spinnerFrame = 0;
	private volatile int messageCount = 0;
	private volatile long totalTokens = 0;
	private volatile String costDisplay = "";
	private final Theme theme;
	private final AtomicBoolean dirty = new AtomicBoolean(true);

	public StatusComponent() {
		this(Theme.DEFAULT);
	}

	public StatusComponent(Theme theme) {
		this.theme = theme;
	}

	public void setReady() {
		this.state = State.READY;
		this.detail = "";
		dirty.set(true);
	}

	public void setThinking() {
		this.state = State.THINKING;
		this.detail = "";
		dirty.set(true);
	}

	public void setRunning(String toolName) {
		this.state = State.RUNNING;
		this.detail = toolName != null ? toolName : "";
		dirty.set(true);
	}

	public void setAwaitingApproval(String summary) {
		this.state = State.AWAITING;
		this.detail = summary != null ? summary : "";
		dirty.set(true);
	}

	public void setError(String message) {
		this.state = State.ERROR;
		this.detail = message != null ? message : "";
		dirty.set(true);
	}

	public void updateUsage(int messageCount, long totalTokens, String cost) {
		this.messageCount = messageCount;
		this.totalTokens = totalTokens;
		this.costDisplay = cost != null ? cost : "";
		dirty.set(true);
	}

	/**
	 * Advances the spinner frame. Called by the render loop.
	 */
	public void tick() {
		if (state == State.THINKING || state == State.RUNNING) {
			spinnerFrame++;
			dirty.set(true);
		}
	}

	public State state() {
		return state;
	}

	@Override
	public List<String> render(int width) {
		String stateStr = switch (state) {
			case READY -> style("Ready", theme.toolResultSuccess());
			case THINKING -> {
				String frame = SPINNER_FRAMES[spinnerFrame % SPINNER_FRAMES.length];
				yield style(frame + " Thinking...", theme.spinner());
			}
			case RUNNING -> {
				String frame = SPINNER_FRAMES[spinnerFrame % SPINNER_FRAMES.length];
				yield style(frame + " Running " + detail + "...", theme.spinner());
			}
			case AWAITING -> style("Approve " + detail + "? [y]es [s]ession [a]lways [n]o", theme.error());
			case ERROR -> style("Error: " + detail, theme.error());
		};

		// Build usage info on the right side
		String usage = "";
		if (totalTokens > 0 || messageCount > 0) {
			var sb = new StringBuilder();
			if (messageCount > 0) {
				sb.append(messageCount).append(" msgs");
			}
			if (totalTokens > 0) {
				if (!sb.isEmpty()) sb.append(" | ");
				sb.append(UsageStats.formatTokens(totalTokens)).append(" tokens");
			}
			if (!costDisplay.isEmpty() && !"$0.0000".equals(costDisplay)) {
				sb.append(" | ").append(costDisplay);
			}
			usage = style(sb.toString(), theme.dim());
		}

		if (usage.isEmpty()) {
			return List.of(stateStr);
		}

		// Right-align usage info
		int stateVisible = Layout.visibleLength(stateStr);
		int usageVisible = Layout.visibleLength(usage);
		int gap = width - stateVisible - usageVisible;
		if (gap > 0) {
			return List.of(stateStr + " ".repeat(gap) + usage);
		}
		// Not enough room -- show state only
		return List.of(stateStr);
	}

	@Override
	public int desiredHeight() {
		return 1;
	}

	@Override
	public boolean isDirty() {
		return dirty.get();
	}

	@Override
	public void markClean() {
		dirty.set(false);
	}
}
