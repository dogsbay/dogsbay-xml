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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContextFileLoaderTest {

	@TempDir
	Path tempDir;

	@Test
	void loadsContextFileFromCwd() throws IOException {
		Files.writeString(tempDir.resolve(".xagent.md"), "Project instructions");

		var loader = new ContextFileLoader(tempDir.resolve("isolated-home"));
		var files = loader.load(tempDir);

		var projectFiles = files.stream()
			.filter(f -> "project".equals(f.source()))
			.toList();
		assertThat(projectFiles).isNotEmpty();
		assertThat(projectFiles.stream().anyMatch(f -> f.content().contains("Project instructions"))).isTrue();
	}

	@Test
	void loadsContextFileFromParentDirectory() throws IOException {
		Path parent = tempDir;
		Path child = tempDir.resolve("subdir");
		Files.createDirectories(child);
		Files.writeString(parent.resolve(".xagent.md"), "Parent context");

		var loader = new ContextFileLoader(tempDir.resolve("isolated-home"));
		var files = loader.load(child);

		var projectFiles = files.stream()
			.filter(f -> "project".equals(f.source()))
			.toList();
		assertThat(projectFiles.stream().anyMatch(f -> f.content().contains("Parent context"))).isTrue();
	}

	@Test
	void ancestorsOrderedBroadestFirst() throws IOException {
		Path grandparent = tempDir;
		Path parent = tempDir.resolve("a");
		Path child = tempDir.resolve("a/b");
		Files.createDirectories(child);
		Files.writeString(grandparent.resolve(".xagent.md"), "root");
		Files.writeString(parent.resolve(".xagent.md"), "mid");
		Files.writeString(child.resolve(".xagent.md"), "leaf");

		var loader = new ContextFileLoader(tempDir.resolve("isolated-home"));
		var files = loader.load(child);

		var projectFiles = files.stream()
			.filter(f -> "project".equals(f.source()))
			.toList();

		// Find indexes of our three files
		int rootIdx = -1, midIdx = -1, leafIdx = -1;
		for (int i = 0; i < projectFiles.size(); i++) {
			if (projectFiles.get(i).content().equals("root")) rootIdx = i;
			if (projectFiles.get(i).content().equals("mid")) midIdx = i;
			if (projectFiles.get(i).content().equals("leaf")) leafIdx = i;
		}
		assertThat(rootIdx).isLessThan(midIdx);
		assertThat(midIdx).isLessThan(leafIdx);
	}

	@Test
	void loadsAgentsMdWhenNoXagentMd() throws IOException {
		Files.writeString(tempDir.resolve("AGENTS.md"), "Cross-tool steering");

		var loader = new ContextFileLoader(tempDir.resolve("isolated-home"));
		var files = loader.load(tempDir);

		assertThat(files.stream().anyMatch(f -> f.content().contains("Cross-tool steering"))).isTrue();
	}

	@Test
	void loadsClaudeMdAsLastFallback() throws IOException {
		Files.writeString(tempDir.resolve("CLAUDE.md"), "Claude steering");

		var loader = new ContextFileLoader(tempDir.resolve("isolated-home"));
		var files = loader.load(tempDir);

		assertThat(files.stream().anyMatch(f -> f.content().contains("Claude steering"))).isTrue();
	}

	@Test
	void xagentMdWinsOverAgentsMdAndClaudeMdInSameDirectory() throws IOException {
		Files.writeString(tempDir.resolve(".xagent.md"), "xagent specific");
		Files.writeString(tempDir.resolve("AGENTS.md"), "generic agents");
		Files.writeString(tempDir.resolve("CLAUDE.md"), "claude specific");

		var loader = new ContextFileLoader(tempDir.resolve("isolated-home"));
		var files = loader.load(tempDir);

		var projectFiles = files.stream().filter(f -> "project".equals(f.source())).toList();
		assertThat(projectFiles).hasSize(1);
		assertThat(projectFiles.get(0).content()).isEqualTo("xagent specific");
	}

	@Test
	void agentsMdWinsOverClaudeMdInSameDirectory() throws IOException {
		Files.writeString(tempDir.resolve("AGENTS.md"), "generic agents");
		Files.writeString(tempDir.resolve("CLAUDE.md"), "claude specific");

		var loader = new ContextFileLoader(tempDir.resolve("isolated-home"));
		var files = loader.load(tempDir);

		var projectFiles = files.stream().filter(f -> "project".equals(f.source())).toList();
		assertThat(projectFiles).hasSize(1);
		assertThat(projectFiles.get(0).content()).isEqualTo("generic agents");
	}

	@Test
	void differentNamesAcrossDirectoriesAllLoad() throws IOException {
		Path child = tempDir.resolve("sub");
		Files.createDirectories(child);
		Files.writeString(tempDir.resolve("AGENTS.md"), "parent agents");
		Files.writeString(child.resolve(".xagent.md"), "child xagent");

		var loader = new ContextFileLoader(tempDir.resolve("isolated-home"));
		var files = loader.load(child);

		var projectFiles = files.stream().filter(f -> "project".equals(f.source())).toList();
		assertThat(projectFiles).hasSize(2);
		assertThat(projectFiles.get(0).content()).isEqualTo("parent agents");
		assertThat(projectFiles.get(1).content()).isEqualTo("child xagent");
	}

	@Test
	void globalContextLoadsFromInjectedHome() throws IOException {
		Path home = tempDir.resolve("home");
		Files.createDirectories(home.resolve(".xagent"));
		Files.writeString(home.resolve(".xagent/XAGENT.md"), "global context");

		var loader = new ContextFileLoader(home);
		var files = loader.load(tempDir);

		assertThat(files.stream().anyMatch(
			f -> "global".equals(f.source()) && f.content().equals("global context"))).isTrue();
	}

	@Test
	void returnsEmptyWhenNoContextFiles() {
		var loader = new ContextFileLoader(tempDir.resolve("isolated-home"));
		// tempDir has no .xagent.md
		var files = loader.load(tempDir);

		var projectFiles = files.stream()
			.filter(f -> "project".equals(f.source()))
			.toList();
		assertThat(projectFiles).isEmpty();
	}
}
