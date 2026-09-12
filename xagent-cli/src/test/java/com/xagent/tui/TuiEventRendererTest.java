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
import com.xagent.message.AssistantMessage;
import com.xagent.tool.AgentToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TuiEventRendererTest {

	private ByteArrayOutputStream outBytes;
	private ByteArrayOutputStream errBytes;
	private TuiEventRenderer renderer;

	@BeforeEach
	void setUp() {
		outBytes = new ByteArrayOutputStream();
		errBytes = new ByteArrayOutputStream();
		renderer = new TuiEventRenderer(
			new PrintStream(outBytes),
			new PrintStream(errBytes),
			Theme.DEFAULT
		);
	}

	@Test
	void messageUpdatePrintsText() {
		renderer.accept(new AgentEvent.MessageStart());
		// Give spinner a moment to start, then stop it with an update
		renderer.accept(new AgentEvent.MessageUpdate("Hello"));

		String output = outBytes.toString();
		assertThat(output).contains("Hello");
	}

	@Test
	void messageEndAddsNewline() {
		var msg = new AssistantMessage("test", null, null, null, List.of(), null);
		renderer.accept(new AgentEvent.MessageEnd(msg));

		String output = outBytes.toString();
		assertThat(output).contains("\n");
	}

	@Test
	void toolExecutionStartShowsToolName() {
		renderer.accept(new AgentEvent.ToolExecutionStart("id1", "bash", "{\"command\":\"ls\"}"));

		String output = outBytes.toString();
		assertThat(output).contains("bash");
		assertThat(output).contains("{\"command\":\"ls\"}");
	}

	@Test
	void toolExecutionEndSuccessShowsCheckmark() throws InterruptedException {
		renderer.accept(new AgentEvent.ToolExecutionStart("id1", "read", "{}"));
		Thread.sleep(100); // let spinner start
		var result = AgentToolResult.success("file contents here");
		renderer.accept(new AgentEvent.ToolExecutionEnd("id1", "read", result));

		String output = outBytes.toString();
		assertThat(output).contains("✓");
		assertThat(output).contains("file contents here");
	}

	@Test
	void toolExecutionEndErrorShowsX() {
		var result = AgentToolResult.error("not found");
		renderer.accept(new AgentEvent.ToolExecutionEnd("id1", "read", result));

		String output = outBytes.toString();
		assertThat(output).contains("✗");
		assertThat(output).contains("not found");
	}

	@Test
	void errorOccurredPrintsToStderr() {
		renderer.accept(new AgentEvent.ErrorOccurred(new RuntimeException("something broke")));

		String errOutput = errBytes.toString();
		assertThat(errOutput).contains("Error:");
		assertThat(errOutput).contains("something broke");
	}

	@Test
	void formatBannerIncludesAllInfo() {
		String banner = renderer.formatBanner("v0.1.0", "openai", "gpt-4", "/home/user", "read, write, bash", "abc123");

		assertThat(banner).contains("xagent");
		assertThat(banner).contains("v0.1.0");
		assertThat(banner).contains("openai/gpt-4");
		assertThat(banner).contains("/home/user");
		assertThat(banner).contains("read, write, bash");
		assertThat(banner).contains("abc123");
	}

	@Test
	void noColorModeFallsBackToPlainSubscriber() {
		// When renderer is null, the plain subscriber is used.
		// This test just verifies the renderer itself handles null gracefully in truncate.
		var result = AgentToolResult.success(null);
		renderer.accept(new AgentEvent.ToolExecutionEnd("id1", "test", result));

		String output = outBytes.toString();
		assertThat(output).contains("✓");
	}

	@Test
	void truncatesLongToolResults() {
		String longContent = "x".repeat(1000);
		var result = AgentToolResult.success(longContent);
		renderer.accept(new AgentEvent.ToolExecutionEnd("id1", "test", result));

		String output = outBytes.toString();
		assertThat(output).contains("...");
		// Should be truncated to 500 chars
		assertThat(output.length()).isLessThan(1100); // accounting for ANSI codes
	}
}
