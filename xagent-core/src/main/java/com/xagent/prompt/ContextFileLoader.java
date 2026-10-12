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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Discovers and loads context files from cwd up to filesystem root, plus
 * the global ~/.xagent/XAGENT.md. In each directory the first match of
 * {@code .xagent.md}, {@code AGENTS.md}, {@code CLAUDE.md} wins — so
 * xagent-specific instructions override the cross-tool standards, and
 * teams steering other agents with AGENTS.md/CLAUDE.md get the same
 * behavior from xagent without extra files.
 */
public class ContextFileLoader {

	private static final String[] CONTEXT_FILE_NAMES = {".xagent.md", "AGENTS.md", "CLAUDE.md"};

	private final Path home;

	public ContextFileLoader() {
		this(Path.of(System.getProperty("user.home")));
	}

	/**
	 * @param home the user home directory for the global context file
	 *             (injectable for test isolation)
	 */
	public ContextFileLoader(Path home) {
		this.home = home;
	}

	/**
	 * Load all context files, ordered from global -> ancestor -> cwd (broadest first).
	 */
	public List<ContextFile> load(Path cwd) {
		var files = new ArrayList<ContextFile>();

		// Global context
		Path globalContext = home.resolve(".xagent").resolve("XAGENT.md");
		if (Files.isReadable(globalContext)) {
			try {
				String content = Files.readString(globalContext);
				files.add(new ContextFile(globalContext, content, "global"));
			} catch (IOException ignored) {}
		}

		// Walk from cwd up to root; first matching name per directory wins
		var projectFiles = new ArrayList<ContextFile>();
		Path dir = cwd.toAbsolutePath().normalize();
		while (dir != null) {
			for (String name : CONTEXT_FILE_NAMES) {
				Path contextFile = dir.resolve(name);
				if (Files.isReadable(contextFile)) {
					try {
						String content = Files.readString(contextFile);
						projectFiles.add(new ContextFile(contextFile, content, "project"));
						break;
					} catch (IOException ignored) {}
				}
			}
			dir = dir.getParent();
		}

		// Reverse so ancestors come before cwd (broadest first)
		Collections.reverse(projectFiles);
		files.addAll(projectFiles);

		return files;
	}

	/**
	 * A loaded context file with its path, content, and source type.
	 */
	public record ContextFile(Path path, String content, String source) {}
}
