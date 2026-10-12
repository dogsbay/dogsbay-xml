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

import com.xagent.event.AgentEvent;

import java.io.PrintStream;
import java.util.function.Consumer;

import static com.xagent.tui.AnsiCodes.*;

/**
 * Renders AgentEvents to the terminal with ANSI colors and a spinner.
 * Drop-in replacement for the plain-text event subscriber in XAgentCli.
 */
public class TuiEventRenderer implements Consumer<AgentEvent> {

	private final PrintStream out;
	private final PrintStream err;
	private final Theme theme;
	private final SpinnerAnimation spinner;

	public TuiEventRenderer(PrintStream out, PrintStream err, Theme theme) {
		this.out = out;
		this.err = err;
		this.theme = theme;
		this.spinner = new SpinnerAnimation(out, theme.spinner());
	}

	public TuiEventRenderer(PrintStream out, PrintStream err) {
		this(out, err, Theme.DEFAULT);
	}

	public TuiEventRenderer() {
		this(System.out, System.err);
	}

	/**
	 * Stop the spinner so an interactive prompt (e.g. permission approval)
	 * can use the terminal without interleaving.
	 */
	public void pauseSpinner() {
		spinner.stop();
	}

	/** Restart the spinner after an interactive prompt. */
	public void resumeSpinner(String label) {
		spinner.start(label);
	}

	@Override
	public void accept(AgentEvent event) {
		switch (event) {
			case AgentEvent.TurnStart ignored -> {}
			case AgentEvent.TurnEnd ignored -> {}
			case AgentEvent.AgentEnd ignored -> spinner.stop();

			case AgentEvent.MessageStart ignored ->
				spinner.start("Thinking...");

			case AgentEvent.MessageUpdate update -> {
				spinner.stop();
				out.print(theme.assistantText() + update.partialText() + RESET);
				out.flush();
			}

			case AgentEvent.MessageEnd msg -> {
				spinner.stop();
				if (msg.message().hasToolCalls()) {
					out.println();
				} else {
					out.println();
					out.println();
				}
			}

			case AgentEvent.ToolExecutionStart start -> {
				String header = style(start.toolName(), theme.toolName())
					+ " " + style(start.arguments(), theme.toolArgs());
				out.println(header);
				spinner.start("Running " + start.toolName() + "...");
			}

			case AgentEvent.ToolExecutionEnd end -> {
				spinner.stop();
				String content = end.result().content();
				String preview = truncate(content, 500);
				if (end.result().isError()) {
					out.println(style("✗ ", theme.toolResultError()) + style(preview, theme.toolResultError()));
				} else {
					out.println(style("✓ ", theme.toolResultSuccess()) + style(preview, theme.dim()));
				}
				out.println();
			}

			case AgentEvent.UsageUpdate ignored -> {}

			case AgentEvent.RetryAttempt retry ->
				err.println(style("Retrying in " + (retry.delayMs() / 1000) + "s (" + retry.reason() + ")...", theme.toolName()));

			case AgentEvent.ErrorOccurred err_ ->  {
				spinner.stop();
				err.println(style("Error: " + err_.error().getMessage(), theme.error()));
			}
		}
	}

	/**
	 * Formats the startup banner with colors.
	 */
	public String formatBanner(String version, String provider, String model,
							   String cwd, String tools, String sessionInfo) {
		var sb = new StringBuilder();
		sb.append(style("xagent", theme.header()))
			.append(" ").append(style(version, theme.dim()))
			.append(" | ").append(style(provider + "/" + model, theme.headerValue()))
			.append("\n");
		sb.append(style("cwd: ", theme.dim())).append(cwd).append("\n");
		sb.append(style("tools: ", theme.dim())).append(tools).append("\n");
		if (sessionInfo != null && !sessionInfo.isEmpty()) {
			sb.append(style("session: ", theme.dim())).append(sessionInfo).append("\n");
		}
		return sb.toString();
	}

	private static String truncate(String text, int maxLen) {
		if (text == null) return "";
		String singleLine = text.replace("\n", "↵ ");
		if (singleLine.length() <= maxLen) return singleLine;
		return singleLine.substring(0, maxLen - 3) + "...";
	}
}
