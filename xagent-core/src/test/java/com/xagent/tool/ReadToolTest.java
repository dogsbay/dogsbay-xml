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
import com.xagent.tool.operations.DefaultFileOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadToolTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@TempDir
	Path tempDir;
	private ReadTool tool;

	@BeforeEach
	void setUp() {
		tool = new ReadTool(tempDir, new DefaultFileOperations());
	}

	private ObjectNode params(String path) {
		ObjectNode p = MAPPER.createObjectNode();
		p.put("path", path);
		return p;
	}

	@Test
	void readSimpleFile() throws IOException {
		Files.writeString(tempDir.resolve("test.txt"), "hello\nworld");

		var result = tool.execute("1", params("test.txt"), () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("hello");
		assertThat(result.content()).contains("world");
	}

	@Test
	void readWithLineNumbers() throws IOException {
		Files.writeString(tempDir.resolve("test.txt"), "line1\nline2\nline3");

		var result = tool.execute("1", params("test.txt"), () -> false, null);

		assertThat(result.content()).contains("1\tline1");
		assertThat(result.content()).contains("2\tline2");
		assertThat(result.content()).contains("3\tline3");
	}

	@Test
	void readWithOffset() throws IOException {
		Files.writeString(tempDir.resolve("test.txt"), "line1\nline2\nline3\nline4");

		ObjectNode p = params("test.txt");
		p.put("offset", 2);
		var result = tool.execute("1", p, () -> false, null);

		assertThat(result.content()).doesNotContain("line1");
		assertThat(result.content()).contains("line2");
	}

	@Test
	void readWithLimit() throws IOException {
		var sb = new StringBuilder();
		for (int i = 1; i <= 100; i++) {
			if (i > 1) sb.append("\n");
			sb.append("line").append(i);
		}
		Files.writeString(tempDir.resolve("test.txt"), sb.toString());

		ObjectNode p = params("test.txt");
		p.put("limit", 5);
		var result = tool.execute("1", p, () -> false, null);

		assertThat(result.content()).contains("line1");
		assertThat(result.content()).contains("line5");
		assertThat(result.content()).contains("more lines");
	}

	@Test
	void readMissingFile() {
		var result = tool.execute("1", params("missing.txt"), () -> false, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("not found");
	}

	@Test
	void readDirectory() throws IOException {
		Files.createDirectory(tempDir.resolve("subdir"));

		var result = tool.execute("1", params("subdir"), () -> false, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("directory");
	}
}
