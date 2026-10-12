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

import java.nio.file.Path;

/**
 * Path resolution and safety utilities.
 */
public final class PathUtil {

	private PathUtil() {}

	/**
	 * Resolve a path relative to cwd, ensuring it stays within cwd or is absolute.
	 */
	public static Path resolve(String path, Path cwd) {
		Path resolved = Path.of(path);
		if (!resolved.isAbsolute()) {
			resolved = cwd.resolve(resolved);
		}
		return resolved.normalize();
	}

	/**
	 * Check if a path is within the given root directory.
	 */
	public static boolean isWithin(Path path, Path root) {
		Path normalizedPath = path.toAbsolutePath().normalize();
		Path normalizedRoot = root.toAbsolutePath().normalize();
		return normalizedPath.startsWith(normalizedRoot);
	}
}
