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

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillPromptFormatterTest {

	@Test
	void formatsSkillsAsXml() {
		var skill = new Skill("my-skill", "Does useful things.", Path.of("/skills/my-skill/SKILL.md"),
			Path.of("/skills/my-skill"), "project");

		var formatter = new SkillPromptFormatter();
		String output = formatter.format(List.of(skill));

		assertThat(output).contains("<available_skills>");
		assertThat(output).contains("<name>my-skill</name>");
		assertThat(output).contains("<description>Does useful things.</description>");
		assertThat(output).contains("<location>" + Path.of("/skills/my-skill/SKILL.md") + "</location>");
		assertThat(output).contains("</available_skills>");
	}

	@Test
	void filtersHiddenSkills() {
		var visible = new Skill("visible", "Shown to model.", Path.of("/a/SKILL.md"),
			Path.of("/a"), "project");
		var hidden = new Skill("hidden", "Not shown.", Path.of("/b/SKILL.md"),
			Path.of("/b"), "project", true, null, null, java.util.Map.of());

		var formatter = new SkillPromptFormatter();
		String output = formatter.format(List.of(visible, hidden));

		assertThat(output).contains("<name>visible</name>");
		assertThat(output).doesNotContain("<name>hidden</name>");
	}

	@Test
	void returnsEmptyForNoVisibleSkills() {
		var hidden = new Skill("hidden", "Not shown.", Path.of("/b/SKILL.md"),
			Path.of("/b"), "project", true, null, null, java.util.Map.of());

		var formatter = new SkillPromptFormatter();
		String output = formatter.format(List.of(hidden));

		assertThat(output).isEmpty();
	}

	@Test
	void returnsEmptyForEmptyList() {
		var formatter = new SkillPromptFormatter();
		String output = formatter.format(List.of());

		assertThat(output).isEmpty();
	}

	@Test
	void formatsInvocationBlock() {
		var skill = new Skill("review", "Review docs.", Path.of("/skills/review/SKILL.md"),
			Path.of("/skills/review"), "project");

		String block = SkillPromptFormatter.formatInvocation(skill, "# Review\n\nCheck everything.", "my-file.md");

		assertThat(block).contains("<skill name=\"review\"");
		assertThat(block).contains("# Review");
		assertThat(block).contains("my-file.md");
		assertThat(block).contains("</skill>");
	}

	@Test
	void escapesXmlInOutput() {
		var skill = new Skill("test", "Handles <special> & \"chars\".",
			Path.of("/skills/test/SKILL.md"), Path.of("/skills/test"), "project");

		var formatter = new SkillPromptFormatter();
		String output = formatter.format(List.of(skill));

		assertThat(output).contains("&lt;special&gt;");
		assertThat(output).contains("&amp;");
		assertThat(output).contains("&quot;chars&quot;");
	}
}
