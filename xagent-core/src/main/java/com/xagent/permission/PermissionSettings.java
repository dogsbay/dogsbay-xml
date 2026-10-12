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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and saves permission rules in settings.json files.
 *
 * Schema (other keys in the file are preserved):
 * <pre>
 * { "permissions": { "allow": ["bash(mvn *)"], "deny": ["bash(rm *)"] } }
 * </pre>
 */
public final class PermissionSettings {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private PermissionSettings() {}

	/** Allow and deny rules merged from one or more settings files. */
	public record Rules(List<PermissionRule> allow, List<PermissionRule> deny) {}

	/**
	 * Settings files in load order for the given working directory:
	 * user-level {@code ~/.xagent/settings.json}, then project
	 * {@code <cwd>/.xagent/settings.json}.
	 */
	public static List<Path> defaultFiles(Path cwd) {
		return List.of(
			Path.of(System.getProperty("user.home"), ".xagent", "settings.json"),
			cwd.resolve(".xagent").resolve("settings.json")
		);
	}

	/**
	 * Load and merge rules from the given files. Missing or unreadable
	 * files are skipped; malformed rules are ignored.
	 */
	public static Rules load(List<Path> files) {
		var allow = new ArrayList<PermissionRule>();
		var deny = new ArrayList<PermissionRule>();
		for (Path file : files) {
			if (!Files.isRegularFile(file)) continue;
			try {
				JsonNode root = MAPPER.readTree(Files.readString(file));
				JsonNode permissions = root.path("permissions");
				readRules(permissions.path("allow"), allow);
				readRules(permissions.path("deny"), deny);
			} catch (IOException ignored) {
				// unreadable settings file -- treat as absent
			}
		}
		return new Rules(allow, deny);
	}

	/**
	 * Append an allow rule to the given settings file, creating the file
	 * and parent directories if needed. Preserves unrelated keys.
	 * No-op if the rule is already present.
	 */
	public static void appendAllowRule(Path file, PermissionRule rule) throws IOException {
		ObjectNode root;
		if (Files.isRegularFile(file)) {
			JsonNode parsed = MAPPER.readTree(Files.readString(file));
			root = parsed.isObject() ? (ObjectNode) parsed : MAPPER.createObjectNode();
		} else {
			root = MAPPER.createObjectNode();
		}

		ObjectNode permissions = root.has("permissions") && root.get("permissions").isObject()
			? (ObjectNode) root.get("permissions")
			: root.putObject("permissions");
		ArrayNode allow = permissions.has("allow") && permissions.get("allow").isArray()
			? (ArrayNode) permissions.get("allow")
			: permissions.putArray("allow");

		String text = rule.toText();
		for (JsonNode existing : allow) {
			if (text.equals(existing.asText())) return;
		}
		allow.add(text);

		Files.createDirectories(file.getParent());
		Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n");
	}

	private static void readRules(JsonNode array, List<PermissionRule> target) {
		if (!array.isArray()) return;
		for (JsonNode entry : array) {
			PermissionRule rule = PermissionRule.parse(entry.asText());
			if (rule != null) target.add(rule);
		}
	}
}
