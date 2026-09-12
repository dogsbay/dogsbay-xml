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
 * Fixed 2-line header showing version, provider/model, and session info.
 */
public class HeaderComponent implements Component {

	private final String version;
	private final String provider;
	private final String model;
	private final String sessionInfo;
	private final Theme theme;
	private final AtomicBoolean dirty = new AtomicBoolean(true);

	public HeaderComponent(String version, String provider, String model, String sessionInfo) {
		this(version, provider, model, sessionInfo, Theme.DEFAULT);
	}

	public HeaderComponent(String version, String provider, String model, String sessionInfo, Theme theme) {
		this.version = version;
		this.provider = provider;
		this.model = model;
		this.sessionInfo = sessionInfo;
		this.theme = theme;
	}

	@Override
	public List<String> render(int width) {
		String line1 = style("xagent", theme.header()) + " " +
			style(version, theme.headerValue()) +
			style(" | ", theme.dim()) +
			style(provider + "/" + model, theme.headerValue());

		String line2 = style("session: ", theme.dim()) +
			style(sessionInfo, theme.headerValue());

		return List.of(line1, line2);
	}

	@Override
	public int desiredHeight() {
		return 2;
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
