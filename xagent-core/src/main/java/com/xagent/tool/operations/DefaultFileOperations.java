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
package com.xagent.tool.operations;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Default filesystem operations using java.nio.file.
 */
public class DefaultFileOperations implements FileOperations {

	@Override
	public String readFile(Path path) throws IOException {
		return Files.readString(path);
	}

	@Override
	public void writeFile(Path path, String content) throws IOException {
		if (path.getParent() != null) {
			Files.createDirectories(path.getParent());
		}
		Files.writeString(path, content);
	}

	@Override
	public void createDirectories(Path dir) throws IOException {
		Files.createDirectories(dir);
	}

	@Override
	public boolean exists(Path path) {
		return Files.exists(path);
	}

	@Override
	public boolean isReadable(Path path) {
		return Files.isReadable(path);
	}

	@Override
	public boolean isDirectory(Path path) {
		return Files.isDirectory(path);
	}

	@Override
	public List<Path> listDirectory(Path dir) throws IOException {
		try (var stream = Files.list(dir)) {
			return stream.collect(Collectors.toList());
		}
	}

	@Override
	public List<Path> glob(Path root, String pattern) throws IOException {
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
		var results = new ArrayList<Path>();

		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				if (matcher.matches(root.relativize(file))) {
					results.add(file);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) {
				return FileVisitResult.CONTINUE;
			}
		});

		return results;
	}
}
