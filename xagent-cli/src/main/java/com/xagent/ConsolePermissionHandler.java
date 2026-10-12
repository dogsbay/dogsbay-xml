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
package com.xagent;

import com.xagent.permission.PermissionDecision;
import com.xagent.permission.PermissionHandler;
import com.xagent.permission.PermissionRequest;
import com.xagent.tui.AnsiCodes;
import com.xagent.tui.Theme;
import com.xagent.tui.TuiEventRenderer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Terminal prompt for permission requests in REPL mode. Reads stdin
 * directly: the REPL thread is blocked inside Agent.prompt() while tools
 * execute, so stdin is free.
 */
public class ConsolePermissionHandler implements PermissionHandler {

	private final TuiEventRenderer renderer;
	private final boolean color;
	private final BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

	/**
	 * @param renderer the REPL event renderer whose spinner must pause
	 *                 around prompts (null when running with --no-color)
	 */
	public ConsolePermissionHandler(TuiEventRenderer renderer) {
		this.renderer = renderer;
		this.color = renderer != null;
	}

	@Override
	public PermissionDecision handle(PermissionRequest request) {
		if (renderer != null) {
			renderer.pauseSpinner();
		}
		try {
			System.out.println();
			System.out.println(styled("Permission required: ", Theme.DEFAULT.error()) + request.summary());
			System.out.println(styled("  [y] allow once  [s] allow for session  [a] always allow  [n] deny", Theme.DEFAULT.dim()));
			System.out.print(styled("  > ", Theme.DEFAULT.userPrompt()));
			System.out.flush();

			String line;
			try {
				line = stdin.readLine();
			} catch (IOException e) {
				return PermissionDecision.DENY;
			}
			if (line == null) {
				return PermissionDecision.DENY;
			}
			return switch (line.trim().toLowerCase()) {
				case "y", "yes" -> PermissionDecision.ALLOW;
				case "s", "session" -> PermissionDecision.ALLOW_SESSION;
				case "a", "always" -> PermissionDecision.ALLOW_ALWAYS;
				default -> PermissionDecision.DENY;
			};
		} finally {
			if (renderer != null) {
				renderer.resumeSpinner("Running " + request.toolName() + "...");
			}
		}
	}

	private String styled(String text, String style) {
		return color ? AnsiCodes.style(text, style) : text;
	}
}
