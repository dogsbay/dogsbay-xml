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
package com.xagent.permission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionSettingsTest {

	@TempDir
	Path tempDir;

	@Test
	void loadsRulesFromMultipleFiles() throws IOException {
		Path userFile = tempDir.resolve("user-settings.json");
		Path projectFile = tempDir.resolve("project-settings.json");
		Files.writeString(userFile, """
			{"permissions": {"allow": ["bash(git *)"], "deny": ["bash(rm *)"]}}
			""");
		Files.writeString(projectFile, """
			{"permissions": {"allow": ["edit"]}}
			""");

		var rules = PermissionSettings.load(List.of(userFile, projectFile));

		assertThat(rules.allow()).containsExactly(
			PermissionRule.parse("bash(git *)"),
			PermissionRule.parse("edit")
		);
		assertThat(rules.deny()).containsExactly(PermissionRule.parse("bash(rm *)"));
	}

	@Test
	void missingFilesAreSkipped() {
		var rules = PermissionSettings.load(List.of(tempDir.resolve("nope.json")));
		assertThat(rules.allow()).isEmpty();
		assertThat(rules.deny()).isEmpty();
	}

	@Test
	void malformedRulesAreIgnored() throws IOException {
		Path file = tempDir.resolve("settings.json");
		Files.writeString(file, """
			{"permissions": {"allow": ["bash(unclosed", "edit"]}}
			""");
		var rules = PermissionSettings.load(List.of(file));
		assertThat(rules.allow()).containsExactly(PermissionRule.parse("edit"));
	}

	@Test
	void appendCreatesFileAndDirectories() throws IOException {
		Path file = tempDir.resolve(".xagent").resolve("settings.json");

		PermissionSettings.appendAllowRule(file, PermissionRule.parse("bash(mvn *)"));

		var rules = PermissionSettings.load(List.of(file));
		assertThat(rules.allow()).containsExactly(PermissionRule.parse("bash(mvn *)"));
	}

	@Test
	void appendPreservesUnrelatedKeys() throws IOException {
		Path file = tempDir.resolve("settings.json");
		Files.writeString(file, """
			{"provider": "anthropic", "permissions": {"deny": ["bash(rm *)"]}}
			""");

		PermissionSettings.appendAllowRule(file, PermissionRule.parse("edit"));

		String content = Files.readString(file);
		assertThat(content).contains("\"provider\"").contains("anthropic");
		var rules = PermissionSettings.load(List.of(file));
		assertThat(rules.allow()).containsExactly(PermissionRule.parse("edit"));
		assertThat(rules.deny()).containsExactly(PermissionRule.parse("bash(rm *)"));
	}

	@Test
	void appendIsIdempotent() throws IOException {
		Path file = tempDir.resolve("settings.json");
		PermissionSettings.appendAllowRule(file, PermissionRule.parse("edit"));
		PermissionSettings.appendAllowRule(file, PermissionRule.parse("edit"));

		var rules = PermissionSettings.load(List.of(file));
		assertThat(rules.allow()).containsExactly(PermissionRule.parse("edit"));
	}

	@Test
	void defaultFilesAreUserThenProject() {
		var files = PermissionSettings.defaultFiles(Path.of("/work/project"));
		assertThat(files).hasSize(2);
		assertThat(files.get(0).endsWith(Path.of(".xagent", "settings.json"))).isTrue();
		assertThat(files.get(1)).isEqualTo(Path.of("/work/project/.xagent/settings.json"));
	}
}
