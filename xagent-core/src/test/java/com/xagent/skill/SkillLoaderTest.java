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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillLoaderTest {

	@TempDir
	Path tempDir;

	private SkillLoader loader;

	@BeforeEach
	void setUp() {
		// isolate from the real ~/.agents/skills and ~/.xagent/skills
		loader = new SkillLoader(tempDir.resolve("isolated-home"));
	}

	@Test
	void loadsValidSkillFromDirectory() throws IOException {
		Path skillsDir = tempDir.resolve(".xagent/skills/my-skill");
		Files.createDirectories(skillsDir);
		Files.writeString(skillsDir.resolve("SKILL.md"), """
			---
			name: my-skill
			description: A test skill for demos.
			---

			# My Skill

			Do things.
			""");

		var skills = loader.loadAll(tempDir, List.of());

		assertThat(skills).hasSize(1);
		assertThat(skills.getFirst().name()).isEqualTo("my-skill");
		assertThat(skills.getFirst().description()).isEqualTo("A test skill for demos.");
		assertThat(skills.getFirst().source()).isEqualTo("project");
	}

	@Test
	void loadsFromExtraPath() throws IOException {
		Path extraDir = tempDir.resolve("extra-skills/my-skill");
		Files.createDirectories(extraDir);
		Files.writeString(extraDir.resolve("SKILL.md"), """
			---
			name: my-skill
			description: Extra path skill.
			---

			Body.
			""");

		var skills = loader.loadAll(tempDir, List.of(tempDir.resolve("extra-skills")));

		assertThat(skills).hasSize(1);
		assertThat(skills.getFirst().source()).isEqualTo("path");
	}

	@Test
	void skipsMissingDescription() throws IOException {
		Path skillsDir = tempDir.resolve(".xagent/skills/bad-skill");
		Files.createDirectories(skillsDir);
		Files.writeString(skillsDir.resolve("SKILL.md"), """
			---
			name: bad-skill
			---

			No description.
			""");

		var skills = loader.loadAll(tempDir, List.of());

		assertThat(skills).isEmpty();
		assertThat(loader.warnings()).anyMatch(w -> w.contains("missing required field 'description'"));
	}

	@Test
	void warnsOnNameMismatch() throws IOException {
		Path skillsDir = tempDir.resolve(".xagent/skills/actual-dir");
		Files.createDirectories(skillsDir);
		Files.writeString(skillsDir.resolve("SKILL.md"), """
			---
			name: wrong-name
			description: Name does not match.
			---

			Body.
			""");

		var skills = loader.loadAll(tempDir, List.of());

		// Skill still loads (warning, not error)
		assertThat(skills).hasSize(1);
		assertThat(loader.warnings()).anyMatch(w -> w.contains("does not match directory"));
	}

	@Test
	void rejectsInvalidName() throws IOException {
		Path skillsDir = tempDir.resolve(".xagent/skills/Bad_Name");
		Files.createDirectories(skillsDir);
		Files.writeString(skillsDir.resolve("SKILL.md"), """
			---
			name: Bad_Name
			description: Invalid characters.
			---

			Body.
			""");

		var skills = loader.loadAll(tempDir, List.of());

		assertThat(skills).isEmpty();
		assertThat(loader.warnings()).anyMatch(w -> w.contains("invalid name"));
	}

	@Test
	void rejectsConsecutiveHyphens() throws IOException {
		Path skillsDir = tempDir.resolve(".xagent/skills/bad--name");
		Files.createDirectories(skillsDir);
		Files.writeString(skillsDir.resolve("SKILL.md"), """
			---
			name: bad--name
			description: Consecutive hyphens.
			---

			Body.
			""");

		var skills = loader.loadAll(tempDir, List.of());

		assertThat(skills).isEmpty();
		assertThat(loader.warnings()).anyMatch(w -> w.contains("consecutive hyphens"));
	}

	@Test
	void firstSkillWinsOnCollision() throws IOException {
		// Create same skill in two locations
		Path dir1 = tempDir.resolve(".agents/skills/dupe");
		Path dir2 = tempDir.resolve(".xagent/skills/dupe");
		Files.createDirectories(dir1);
		Files.createDirectories(dir2);
		Files.writeString(dir1.resolve("SKILL.md"), """
			---
			name: dupe
			description: First one.
			---
			""");
		Files.writeString(dir2.resolve("SKILL.md"), """
			---
			name: dupe
			description: Second one.
			---
			""");

		var skills = loader.loadAll(tempDir, List.of());

		assertThat(skills).hasSize(1);
		assertThat(skills.getFirst().description()).isEqualTo("First one.");
		assertThat(loader.warnings()).anyMatch(w -> w.contains("already loaded"));
	}

	@Test
	void parsesDisableModelInvocation() throws IOException {
		Path skillsDir = tempDir.resolve(".xagent/skills/hidden");
		Files.createDirectories(skillsDir);
		Files.writeString(skillsDir.resolve("SKILL.md"), """
			---
			name: hidden
			description: Hidden from model.
			disable-model-invocation: true
			---

			Secret instructions.
			""");

		var skills = loader.loadAll(tempDir, List.of());

		assertThat(skills).hasSize(1);
		assertThat(skills.getFirst().disableModelInvocation()).isTrue();
	}

	@Test
	void parseFrontmatterHandlesMultilineDescription() {
		String content = """
			---
			name: test
			description: This is a
			  multiline description.
			---

			Body.
			""";

		var fm = loader.parseFrontmatter(content);
		assertThat(fm).isNotNull();
		assertThat(fm.get("name")).isEqualTo("test");
		assertThat(fm.get("description")).contains("multiline");
	}

	@Test
	void stripFrontmatterReturnsBody() {
		String content = """
			---
			name: test
			description: Test.
			---

			# Body Content

			Instructions here.
			""";

		String body = SkillLoader.stripFrontmatter(content);
		assertThat(body).contains("# Body Content");
		assertThat(body).contains("Instructions here.");
		assertThat(body).doesNotContain("name: test");
	}

	@Test
	void returnsEmptyWhenNoSkillsExist() {
		var skills = loader.loadAll(tempDir, List.of());
		assertThat(skills).isEmpty();
		assertThat(loader.warnings()).isEmpty();
	}
}
