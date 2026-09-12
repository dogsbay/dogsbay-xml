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

class WriteToolTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@TempDir
	Path tempDir;
	private WriteTool tool;

	@BeforeEach
	void setUp() {
		tool = new WriteTool(tempDir, new DefaultFileOperations());
	}

	@Test
	void writeNewFile() throws IOException {
		ObjectNode p = MAPPER.createObjectNode();
		p.put("path", "new.txt");
		p.put("content", "hello world");

		var result = tool.execute("1", p, () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("Created");
		assertThat(Files.readString(tempDir.resolve("new.txt"))).isEqualTo("hello world");
	}

	@Test
	void overwriteExistingFile() throws IOException {
		Files.writeString(tempDir.resolve("existing.txt"), "old content");

		ObjectNode p = MAPPER.createObjectNode();
		p.put("path", "existing.txt");
		p.put("content", "new content");

		var result = tool.execute("1", p, () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("Updated");
		assertThat(Files.readString(tempDir.resolve("existing.txt"))).isEqualTo("new content");
	}

	@Test
	void createParentDirectories() throws IOException {
		ObjectNode p = MAPPER.createObjectNode();
		p.put("path", "sub/dir/file.txt");
		p.put("content", "deep content");

		var result = tool.execute("1", p, () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(Files.readString(tempDir.resolve("sub/dir/file.txt"))).isEqualTo("deep content");
	}
}
