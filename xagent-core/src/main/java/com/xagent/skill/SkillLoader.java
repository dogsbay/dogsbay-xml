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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Discovers and loads skills from standard locations following the Agent Skills specification.
 *
 * Discovery paths (in order):
 * 1. Project: .agents/skills/ and .xagent/skills/ (cwd + ancestors)
 * 2. User: ~/.agents/skills/ and ~/.xagent/skills/
 * 3. CLI: explicit --skill paths
 */
public class SkillLoader {

	private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$");
	private static final int MAX_NAME_LENGTH = 64;
	private static final int MAX_DESCRIPTION_LENGTH = 1024;
	private static final String SKILL_FILE = "SKILL.md";

	private final List<String> warnings = new ArrayList<>();
	private final Path home;

	public SkillLoader() {
		this(Path.of(System.getProperty("user.home")));
	}

	/**
	 * @param home the user home directory to scan for user-level skills
	 *             (injectable for test isolation)
	 */
	public SkillLoader(Path home) {
		this.home = home;
	}

	/**
	 * Load all skills from standard discovery paths.
	 */
	public List<Skill> loadAll(Path cwd, List<Path> extraPaths) {
		var skillsByName = new LinkedHashMap<String, Skill>();

		// Project-level: walk from cwd upward
		Path dir = cwd.toAbsolutePath().normalize();
		var projectDirs = new ArrayList<Path>();
		while (dir != null) {
			projectDirs.add(dir);
			dir = dir.getParent();
		}
		// Process ancestors first (broadest), then cwd (most specific wins on collision)
		for (int i = projectDirs.size() - 1; i >= 0; i--) {
			Path d = projectDirs.get(i);
			loadFromDirectory(d.resolve(".agents/skills"), "project", skillsByName);
			loadFromDirectory(d.resolve(".xagent/skills"), "project", skillsByName);
		}

		// User-level
		loadFromDirectory(home.resolve(".agents/skills"), "user", skillsByName);
		loadFromDirectory(home.resolve(".xagent/skills"), "user", skillsByName);

		// CLI paths
		if (extraPaths != null) {
			for (Path p : extraPaths) {
				loadFromDirectory(p, "path", skillsByName);
			}
		}

		return new ArrayList<>(skillsByName.values());
	}

	public List<String> warnings() {
		return List.copyOf(warnings);
	}

	private void loadFromDirectory(Path skillsDir, String source, Map<String, Skill> skillsByName) {
		if (!Files.isDirectory(skillsDir)) return;

		try (Stream<Path> entries = Files.list(skillsDir)) {
			for (Path entry : entries.toList()) {
				if (Files.isDirectory(entry)) {
					// Look for SKILL.md in subdirectory
					Path skillFile = entry.resolve(SKILL_FILE);
					if (Files.isReadable(skillFile)) {
						loadSkillFile(skillFile, entry, source, skillsByName);
					}
				} else if (entry.getFileName().toString().endsWith(".md")) {
					// Direct .md file in skills root
					loadSkillFile(entry, skillsDir, source, skillsByName);
				}
			}
		} catch (IOException e) {
			warnings.add("Failed to scan skills directory " + skillsDir + ": " + e.getMessage());
		}
	}

	private void loadSkillFile(Path filePath, Path baseDir, String source, Map<String, Skill> skillsByName) {
		try {
			String content = Files.readString(filePath);
			var frontmatter = parseFrontmatter(content);
			if (frontmatter == null) {
				warnings.add(filePath + ": no valid frontmatter found");
				return;
			}

			String name = frontmatter.getOrDefault("name", "").strip();
			String description = frontmatter.getOrDefault("description", "").strip();

			// Validate name
			if (name.isEmpty()) {
				warnings.add(filePath + ": missing required field 'name'");
				return;
			}
			if (name.length() > MAX_NAME_LENGTH) {
				warnings.add(filePath + ": name exceeds " + MAX_NAME_LENGTH + " characters");
				return;
			}
			if (!NAME_PATTERN.matcher(name).matches()) {
				warnings.add(filePath + ": invalid name '" + name + "' (must be lowercase a-z0-9 and hyphens, no leading/trailing/consecutive hyphens)");
				return;
			}
			if (name.contains("--")) {
				warnings.add(filePath + ": name contains consecutive hyphens");
				return;
			}

			// Validate name matches directory (for SKILL.md in subdirectory)
			if (filePath.getFileName().toString().equals(SKILL_FILE)) {
				String dirName = filePath.getParent().getFileName().toString();
				if (!name.equals(dirName)) {
					warnings.add(filePath + ": name '" + name + "' does not match directory '" + dirName + "'");
				}
			}

			// Validate description (required -- skip skill if missing)
			if (description.isEmpty()) {
				warnings.add(filePath + ": missing required field 'description', skipping");
				return;
			}
			if (description.length() > MAX_DESCRIPTION_LENGTH) {
				warnings.add(filePath + ": description exceeds " + MAX_DESCRIPTION_LENGTH + " characters (truncated)");
				description = description.substring(0, MAX_DESCRIPTION_LENGTH);
			}

			// Check for collision
			if (skillsByName.containsKey(name)) {
				warnings.add(filePath + ": skill '" + name + "' already loaded from "
					+ skillsByName.get(name).filePath() + ", skipping");
				return;
			}

			boolean disableModelInvocation = "true".equalsIgnoreCase(
				frontmatter.getOrDefault("disable-model-invocation", "false").strip()
			);
			String license = frontmatter.get("license");
			String compatibility = frontmatter.get("compatibility");

			var skill = new Skill(name, description, filePath.toAbsolutePath(), baseDir.toAbsolutePath(),
				source, disableModelInvocation, license, compatibility, Map.of());
			skillsByName.put(name, skill);
		} catch (IOException e) {
			warnings.add(filePath + ": failed to read: " + e.getMessage());
		}
	}

	/**
	 * Parse YAML frontmatter from a SKILL.md file.
	 * Expects --- delimiters. Returns null if no valid frontmatter found.
	 * Uses simple line-by-line parsing (no YAML library dependency).
	 */
	Map<String, String> parseFrontmatter(String content) {
		if (!content.startsWith("---")) return null;

		int endIdx = content.indexOf("\n---", 3);
		if (endIdx < 0) return null;

		String yaml = content.substring(3, endIdx).strip();
		var result = new LinkedHashMap<String, String>();

		String currentKey = null;
		StringBuilder currentValue = new StringBuilder();

		for (String line : yaml.split("\n")) {
			// Check if this is a new key: value pair
			int colonIdx = line.indexOf(':');
			if (colonIdx > 0 && !line.startsWith(" ") && !line.startsWith("\t")) {
				// Save previous key
				if (currentKey != null) {
					result.put(currentKey, currentValue.toString().strip());
				}
				currentKey = line.substring(0, colonIdx).strip();
				currentValue = new StringBuilder(line.substring(colonIdx + 1));
			} else if (currentKey != null) {
				// Continuation of multiline value
				currentValue.append("\n").append(line);
			}
		}

		// Save last key
		if (currentKey != null) {
			result.put(currentKey, currentValue.toString().strip());
		}

		return result;
	}

	/**
	 * Strip frontmatter from a SKILL.md file, returning only the body content.
	 */
	public static String stripFrontmatter(String content) {
		if (!content.startsWith("---")) return content;
		int endIdx = content.indexOf("\n---", 3);
		if (endIdx < 0) return content;
		return content.substring(endIdx + 4).strip();
	}
}
