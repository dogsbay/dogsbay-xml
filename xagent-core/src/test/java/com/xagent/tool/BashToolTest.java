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
package com.xagent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xagent.tool.operations.DefaultBashOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// DefaultBashOperations runs `bash -c` with POSIX shell syntax, which Windows has no
// dependable equivalent for (see plans/known-issues.md).
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "the bash tool needs a POSIX shell")
class BashToolTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@TempDir
	Path tempDir;
	private BashTool tool;

	@BeforeEach
	void setUp() {
		tool = new BashTool(tempDir, new DefaultBashOperations());
	}

	@Test
	void executeSimpleCommand() {
		ObjectNode p = MAPPER.createObjectNode();
		p.put("command", "echo hello");

		var result = tool.execute("1", p, () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("hello");
		assertThat(result.content()).contains("Exit code: 0");
	}

	@Test
	void executeFailingCommand() {
		ObjectNode p = MAPPER.createObjectNode();
		p.put("command", "exit 42");

		var result = tool.execute("1", p, () -> false, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("Exit code: 42");
	}

	@Test
	void captureStderr() {
		ObjectNode p = MAPPER.createObjectNode();
		p.put("command", "echo error >&2; exit 1");

		var result = tool.execute("1", p, () -> false, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("STDERR:");
		assertThat(result.content()).contains("error");
	}

	@Test
	void timeout() {
		ObjectNode p = MAPPER.createObjectNode();
		p.put("command", "sleep 30");
		p.put("timeout", 1);

		var result = tool.execute("1", p, () -> false, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("Timed out");
	}

	@Test
	void cancellation() {
		ObjectNode p = MAPPER.createObjectNode();
		p.put("command", "sleep 30");

		// Cancel immediately
		var result = tool.execute("1", p, () -> true, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("Cancelled");
	}
}
