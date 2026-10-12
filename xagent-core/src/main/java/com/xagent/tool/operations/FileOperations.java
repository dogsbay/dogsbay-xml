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
import java.nio.file.Path;
import java.util.List;

/**
 * Pluggable filesystem operations interface.
 * Mirrors pi's ReadOperations/WriteOperations pattern for testability
 * and future SSH/remote/sandboxed execution.
 */
public interface FileOperations {

	String readFile(Path path) throws IOException;

	void writeFile(Path path, String content) throws IOException;

	void createDirectories(Path dir) throws IOException;

	boolean exists(Path path);

	boolean isReadable(Path path);

	boolean isDirectory(Path path);

	List<Path> listDirectory(Path dir) throws IOException;

	/**
	 * Find files matching a glob pattern under a root directory.
	 */
	List<Path> glob(Path root, String pattern) throws IOException;
}
