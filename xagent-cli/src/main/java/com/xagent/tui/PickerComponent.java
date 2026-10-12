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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.xagent.tui.AnsiCodes.*;

/**
 * A modal single-choice list rendered into the layout (e.g. the provider /
 * model picker). Zero height when inactive, so it appears only while shown.
 * Navigation is driven by {@link TuiApp} via {@link #moveUp()}/{@link #moveDown()}.
 */
public class PickerComponent implements Component {

	private volatile boolean active = false;
	private volatile String title = "";
	private volatile List<String> options = List.of();
	private volatile int selected = 0;
	private final Theme theme;
	private final AtomicBoolean dirty = new AtomicBoolean(true);

	public PickerComponent() {
		this(Theme.DEFAULT);
	}

	public PickerComponent(Theme theme) {
		this.theme = theme;
	}

	/** Show the picker with the given title and options (selection reset to first). */
	public void show(String title, List<String> options) {
		this.title = title;
		this.options = List.copyOf(options);
		this.selected = 0;
		this.active = true;
		dirty.set(true);
	}

	public void hide() {
		this.active = false;
		this.options = List.of();
		dirty.set(true);
	}

	public boolean isActive() {
		return active;
	}

	public int selected() {
		return selected;
	}

	public void moveUp() {
		if (active && !options.isEmpty()) {
			selected = (selected - 1 + options.size()) % options.size();
			dirty.set(true);
		}
	}

	public void moveDown() {
		if (active && !options.isEmpty()) {
			selected = (selected + 1) % options.size();
			dirty.set(true);
		}
	}

	@Override
	public List<String> render(int width) {
		if (!active) {
			return List.of();
		}
		var lines = new ArrayList<String>();
		lines.add(style(title, theme.header()));
		for (int i = 0; i < options.size(); i++) {
			if (i == selected) {
				lines.add(style(" › " + options.get(i), REVERSE));
			} else {
				lines.add("   " + options.get(i));
			}
		}
		lines.add(style("  ↑/↓ select · Enter confirm · Esc cancel", theme.dim()));
		return lines;
	}

	@Override
	public int desiredHeight() {
		// title + options + hint, or nothing when inactive
		return active ? options.size() + 2 : 0;
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
