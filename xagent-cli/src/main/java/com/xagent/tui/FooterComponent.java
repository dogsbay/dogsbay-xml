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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.xagent.tui.AnsiCodes.*;

/**
 * Fixed 1-line footer showing working directory, keybindings, and optional git branch.
 */
public class FooterComponent implements Component {

	private final String cwd;
	private volatile String gitBranch;
	private final Theme theme;
	private final AtomicBoolean dirty = new AtomicBoolean(true);

	public FooterComponent(String cwd) {
		this(cwd, null, Theme.DEFAULT);
	}

	public FooterComponent(String cwd, String gitBranch) {
		this(cwd, gitBranch, Theme.DEFAULT);
	}

	public FooterComponent(String cwd, String gitBranch, Theme theme) {
		this.cwd = cwd;
		this.gitBranch = gitBranch;
		this.theme = theme;
	}

	public void setGitBranch(String branch) {
		this.gitBranch = branch;
		dirty.set(true);
	}

	@Override
	public List<String> render(int width) {
		String left = style(cwd, theme.dim()) +
			style(" | ", theme.dim()) +
			style("Ctrl+C", BOLD) + style(": abort", theme.dim()) +
			style(" | ", theme.dim()) +
			style("Ctrl+D", BOLD) + style(": quit", theme.dim()) +
			style(" | ", theme.dim()) +
			style("Tab", BOLD) + style(": raw", theme.dim()) +
			style(" | ", theme.dim()) +
			style("/help", BOLD);

		String branch = gitBranch;
		if (branch != null && !branch.isEmpty()) {
			int leftVisible = Layout.visibleLength(left);
			String branchLabel = style(" " + branch, theme.dim());
			int branchVisible = Layout.visibleLength(branchLabel);
			int gap = width - leftVisible - branchVisible;
			if (gap > 0) {
				return List.of(left + " ".repeat(gap) + branchLabel);
			}
		}

		return List.of(left);
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
