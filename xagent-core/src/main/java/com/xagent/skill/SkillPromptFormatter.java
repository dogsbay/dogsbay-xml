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

import java.util.List;

/**
 * Formats skills as XML for inclusion in the system prompt,
 * following the Agent Skills specification format.
 */
public class SkillPromptFormatter {

	private static final String PREAMBLE =
		"The following skills provide specialized instructions for specific tasks. "
		+ "Use the read tool to load a skill's file when the task matches its description. "
		+ "When a skill file references a relative path, resolve it against the skill's directory.";

	/**
	 * Format skills as an XML catalog for the system prompt.
	 * Filters out skills with disableModelInvocation=true.
	 * Returns empty string if no visible skills.
	 */
	public String format(List<Skill> skills) {
		var visible = skills.stream()
			.filter(s -> !s.disableModelInvocation())
			.toList();

		if (visible.isEmpty()) return "";

		var sb = new StringBuilder();
		sb.append("\n## Skills\n\n");
		sb.append(PREAMBLE);
		sb.append("\n\n<available_skills>\n");

		for (Skill skill : visible) {
			sb.append("  <skill>\n");
			sb.append("    <name>").append(escapeXml(skill.name())).append("</name>\n");
			sb.append("    <description>").append(escapeXml(skill.description())).append("</description>\n");
			sb.append("    <location>").append(escapeXml(skill.filePath().toString())).append("</location>\n");
			sb.append("  </skill>\n");
		}

		sb.append("</available_skills>\n");
		return sb.toString();
	}

	/**
	 * Format a skill invocation block for sending as a user message.
	 */
	public static String formatInvocation(Skill skill, String body, String userArgs) {
		var sb = new StringBuilder();
		sb.append("<skill name=\"").append(escapeXml(skill.name()))
			.append("\" location=\"").append(escapeXml(skill.filePath().toString())).append("\">\n");
		sb.append(body);
		sb.append("\n</skill>");
		if (userArgs != null && !userArgs.isBlank()) {
			sb.append("\n").append(userArgs);
		}
		return sb.toString();
	}

	private static String escapeXml(String text) {
		return text.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;");
	}
}
