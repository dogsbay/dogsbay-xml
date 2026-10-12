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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BundledSkillsTest {

	@TempDir
	Path tempDir;

	@Test
	void indexListsTheDitaSkills() throws IOException {
		List<String> names = BundledSkills.readIndex();
		assertThat(names).contains("dita-fix-validation", "dita-conref-audit", "dita-metadata-normalize");
	}

	@Test
	void extractWritesSkillFilesUnderVersionDir() throws IOException {
		Path dir = BundledSkills.extract(tempDir, "9.9.9");

		assertThat(dir).isEqualTo(tempDir.resolve("9.9.9"));
		assertThat(Files.isRegularFile(dir.resolve("dita-fix-validation/SKILL.md"))).isTrue();
		assertThat(Files.readString(dir.resolve("dita-fix-validation/SKILL.md")))
			.contains("name: dita-fix-validation");
	}

	@Test
	void extractedSkillsAreDiscoverableBySkillLoader() throws IOException {
		Path dir = BundledSkills.extract(tempDir, "1.0.0");

		// load with an isolated home so only the bundled dir contributes
		var loader = new SkillLoader(tempDir.resolve("empty-home"));
		var skills = loader.loadAll(tempDir.resolve("nonexistent-cwd"), List.of(dir));

		assertThat(skills).extracting(s -> s.name())
			.contains("dita-fix-validation", "dita-conref-audit", "dita-metadata-normalize");
	}

	@Test
	void everyIndexedSkillIsBundledAndLoadsUnderItsOwnName() throws IOException {
		Path dir = BundledSkills.extract(tempDir, "2.0.0");
		var skills = new SkillLoader(tempDir.resolve("empty-home")).loadAll(tempDir.resolve("nonexistent-cwd"), List.of(dir));

		// An index line without a SKILL.md, or a SKILL.md whose name differs from its folder, is a skill no agent sees.
		assertThat(skills).extracting(s -> s.name()).containsAll(BundledSkills.readIndex());
		assertThat(BundledSkills.readIndex()).contains("dita-project-graph");
	}

	@Test
	void extractIsRepeatableAndOverwrites() throws IOException {
		BundledSkills.extract(tempDir, "1.0.0");
		Path dir = BundledSkills.extract(tempDir, "1.0.0");   // second run must not fail
		assertThat(Files.isRegularFile(dir.resolve("dita-conref-audit/SKILL.md"))).isTrue();
	}
}
