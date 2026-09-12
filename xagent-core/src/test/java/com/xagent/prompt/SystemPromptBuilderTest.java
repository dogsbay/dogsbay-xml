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
package com.xagent.prompt;

import com.xagent.tool.ToolRegistry;
import com.xagent.tool.operations.DefaultFileOperations;
import com.xagent.tool.operations.DefaultBashOperations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemPromptBuilderTest {

	@TempDir
	Path tempDir;

	@Test
	void defaultPromptContainsGuidelinesAndEnvironment() {
		var registry = new ToolRegistry();
		var builder = new SystemPromptBuilder(registry, tempDir);

		String prompt = builder.build();

		assertThat(prompt).contains("helpful coding assistant");
		assertThat(prompt).contains("Working directory:");
		assertThat(prompt).contains("Date:");
	}

	@Test
	void includesToolDescriptions() {
		var registry = ToolRegistry.createDefault(tempDir);
		var builder = new SystemPromptBuilder(registry, tempDir);

		String prompt = builder.build();

		assertThat(prompt).contains("## Available Tools");
		assertThat(prompt).contains("**read**");
		assertThat(prompt).contains("**write**");
		assertThat(prompt).contains("**bash**");
	}

	@Test
	void customPromptReplacesDefault() {
		var registry = new ToolRegistry();
		var builder = new SystemPromptBuilder(registry, tempDir)
			.customPrompt("You are an XML expert.");

		String prompt = builder.build();

		assertThat(prompt).contains("You are an XML expert.");
		assertThat(prompt).doesNotContain("helpful coding assistant");
	}

	@Test
	void appendPromptAddsToEnd() {
		var registry = new ToolRegistry();
		var builder = new SystemPromptBuilder(registry, tempDir)
			.appendPrompt("Always use formal language.");

		String prompt = builder.build();

		assertThat(prompt).contains("helpful coding assistant");
		assertThat(prompt).contains("Always use formal language.");
	}

	@Test
	void includesContextFiles() throws IOException {
		Files.writeString(tempDir.resolve(".xagent.md"), "This project uses Maven and Java 21.");

		var registry = new ToolRegistry();
		var builder = new SystemPromptBuilder(registry, tempDir);

		String prompt = builder.build();

		assertThat(prompt).contains("## Project Context");
		assertThat(prompt).contains("This project uses Maven and Java 21.");
	}

	@Test
	void noContextSectionWhenNoFiles() {
		var registry = new ToolRegistry();
		// Use a mock loader that returns empty
		var loader = new ContextFileLoader() {
			@Override
			public List<ContextFile> load(Path cwd) {
				return List.of();
			}
		};
		var builder = new SystemPromptBuilder(registry, tempDir, loader);

		String prompt = builder.build();

		assertThat(prompt).doesNotContain("## Project Context");
	}
}
