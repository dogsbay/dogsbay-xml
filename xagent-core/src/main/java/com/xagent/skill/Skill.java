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
package com.xagent.skill;

import java.nio.file.Path;
import java.util.Map;

/**
 * A loaded skill following the Agent Skills specification.
 * Skills are self-contained folders with a SKILL.md file containing
 * YAML frontmatter (name, description) and Markdown instructions.
 */
public record Skill(
	String name,
	String description,
	Path filePath,
	Path baseDir,
	String source,
	boolean disableModelInvocation,
	String license,
	String compatibility,
	Map<String, String> metadata
) {
	public Skill(String name, String description, Path filePath, Path baseDir, String source) {
		this(name, description, filePath, baseDir, source, false, null, null, Map.of());
	}
}
