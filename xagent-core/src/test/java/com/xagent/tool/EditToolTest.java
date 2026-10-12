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

class EditToolTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@TempDir
	Path tempDir;
	private EditTool tool;

	@BeforeEach
	void setUp() {
		tool = new EditTool(tempDir, new DefaultFileOperations());
	}

	@Test
	void replaceUniqueString() throws IOException {
		Files.writeString(tempDir.resolve("test.txt"), "hello world\nfoo bar");

		ObjectNode p = MAPPER.createObjectNode();
		p.put("path", "test.txt");
		p.put("old_string", "foo bar");
		p.put("new_string", "baz qux");

		var result = tool.execute("1", p, () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(Files.readString(tempDir.resolve("test.txt"))).isEqualTo("hello world\nbaz qux");
	}

	@Test
	void rejectNonUniqueMatch() throws IOException {
		Files.writeString(tempDir.resolve("test.txt"), "foo\nfoo\nfoo");

		ObjectNode p = MAPPER.createObjectNode();
		p.put("path", "test.txt");
		p.put("old_string", "foo");
		p.put("new_string", "bar");

		var result = tool.execute("1", p, () -> false, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("3 times");
	}

	@Test
	void replaceAllOccurrences() throws IOException {
		Files.writeString(tempDir.resolve("test.txt"), "foo\nfoo\nfoo");

		ObjectNode p = MAPPER.createObjectNode();
		p.put("path", "test.txt");
		p.put("old_string", "foo");
		p.put("new_string", "bar");
		p.put("replace_all", true);

		var result = tool.execute("1", p, () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("3 replacement(s)");
		assertThat(Files.readString(tempDir.resolve("test.txt"))).isEqualTo("bar\nbar\nbar");
	}

	@Test
	void stringNotFound() throws IOException {
		Files.writeString(tempDir.resolve("test.txt"), "hello world");

		ObjectNode p = MAPPER.createObjectNode();
		p.put("path", "test.txt");
		p.put("old_string", "missing");
		p.put("new_string", "replacement");

		var result = tool.execute("1", p, () -> false, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("not found");
	}

	@Test
	void fileNotFound() {
		ObjectNode p = MAPPER.createObjectNode();
		p.put("path", "missing.txt");
		p.put("old_string", "foo");
		p.put("new_string", "bar");

		var result = tool.execute("1", p, () -> false, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("not found");
	}
}
