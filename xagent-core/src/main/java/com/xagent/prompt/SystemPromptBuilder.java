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
package com.xagent.prompt;

import com.xagent.skill.Skill;
import com.xagent.skill.SkillPromptFormatter;
import com.xagent.tool.AgentTool;
import com.xagent.tool.ToolRegistry;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds the system prompt dynamically from tool descriptions, context files, and metadata.
 */
public class SystemPromptBuilder {

	private static final String DEFAULT_GUIDELINES =
		"You are a helpful coding assistant. You have access to tools for reading, writing, and editing files, "
		+ "running bash commands, and searching code. Use them to help the user with their tasks.";

	private final ToolRegistry toolRegistry;
	private final Path cwd;
	private final ContextFileLoader contextLoader;
	private String customPrompt;
	private String appendPrompt;
	private List<Skill> skills = List.of();

	public SystemPromptBuilder(ToolRegistry toolRegistry, Path cwd) {
		this(toolRegistry, cwd, new ContextFileLoader());
	}

	public SystemPromptBuilder(ToolRegistry toolRegistry, Path cwd, ContextFileLoader contextLoader) {
		this.toolRegistry = toolRegistry;
		this.cwd = cwd;
		this.contextLoader = contextLoader;
	}

	/**
	 * Set a custom system prompt that replaces the default guidelines.
	 */
	public SystemPromptBuilder customPrompt(String prompt) {
		this.customPrompt = prompt;
		return this;
	}

	/**
	 * Set text to append after the main prompt.
	 */
	public SystemPromptBuilder appendPrompt(String text) {
		this.appendPrompt = text;
		return this;
	}

	/**
	 * Set skills to include in the prompt.
	 */
	public SystemPromptBuilder skills(List<Skill> skills) {
		this.skills = skills;
		return this;
	}

	/**
	 * Build the complete system prompt.
	 */
	public String build() {
		var sb = new StringBuilder();

		// Guidelines
		if (customPrompt != null) {
			sb.append(customPrompt);
		} else {
			sb.append(DEFAULT_GUIDELINES);
		}

		// Tool descriptions
		var tools = toolRegistry.all();
		if (!tools.isEmpty()) {
			sb.append("\n\n## Available Tools\n\n");
			for (AgentTool tool : tools) {
				sb.append("- **").append(tool.name()).append("**: ").append(tool.description()).append("\n");
			}
		}

		// Context files
		List<ContextFileLoader.ContextFile> contextFiles = contextLoader.load(cwd);
		if (!contextFiles.isEmpty()) {
			sb.append("\n## Project Context\n");
			for (var cf : contextFiles) {
				sb.append("\n### ").append(cf.path().getFileName()).append(" (").append(cf.source()).append(")\n\n");
				sb.append(cf.content().strip());
				sb.append("\n");
			}
		}

		// Skills
		if (!skills.isEmpty()) {
			sb.append(new SkillPromptFormatter().format(skills));
		}

		// Metadata
		sb.append("\n## Environment\n\n");
		sb.append("- Working directory: ").append(cwd).append("\n");
		sb.append("- Date: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n");

		// Append prompt
		if (appendPrompt != null) {
			sb.append("\n").append(appendPrompt).append("\n");
		}

		return sb.toString();
	}
}
