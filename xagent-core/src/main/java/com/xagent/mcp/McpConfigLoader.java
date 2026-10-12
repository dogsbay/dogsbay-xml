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
package com.xagent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads MCP server configuration from mcp.json files.
 * Merges project-local config over global config.
 */
public final class McpConfigLoader {

	private static final String CONFIG_FILE = "mcp.json";
	private static final String CONFIG_DIR = ".xagent";
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final Pattern ENV_VAR = Pattern.compile("\\$\\{([^}]+)}");

	private McpConfigLoader() {}

	/**
	 * Load and merge configs from project-local and global paths.
	 * Local config overrides global config for servers with the same name.
	 */
	public static List<McpServerConfig> load(Path workingDir) throws IOException {
		Map<String, McpServerConfig> servers = new LinkedHashMap<>();

		// Global config: ~/.xagent/mcp.json
		Path globalConfig = Path.of(System.getProperty("user.home")).resolve(CONFIG_DIR).resolve(CONFIG_FILE);
		if (Files.exists(globalConfig)) {
			servers.putAll(parseFile(globalConfig));
		}

		// Project-local config: <cwd>/.xagent/mcp.json
		Path localConfig = workingDir.resolve(CONFIG_DIR).resolve(CONFIG_FILE);
		if (Files.exists(localConfig)) {
			servers.putAll(parseFile(localConfig)); // local overrides global
		}

		return List.copyOf(servers.values());
	}

	/**
	 * Parse a single mcp.json file into a map of server name -> config.
	 */
	static Map<String, McpServerConfig> parseFile(Path file) throws IOException {
		JsonNode root = MAPPER.readTree(Files.readString(file));
		JsonNode serversNode = root.get("mcpServers");
		if (serversNode == null || !serversNode.isObject()) {
			return Map.of();
		}

		Map<String, McpServerConfig> result = new LinkedHashMap<>();
		var fields = serversNode.fields();
		while (fields.hasNext()) {
			var entry = fields.next();
			String name = entry.getKey();
			JsonNode serverNode = entry.getValue();

			String transport = getStringOrDefault(serverNode, "transport", "stdio");
			String command = resolveEnvVars(getStringOrNull(serverNode, "command"));
			List<String> args = getStringList(serverNode, "args");
			Map<String, String> env = getStringMap(serverNode, "env");
			String url = resolveEnvVars(getStringOrNull(serverNode, "url"));
			Map<String, String> headers = getStringMap(serverNode, "headers");

			// Resolve env vars in headers
			Map<String, String> resolvedHeaders = new LinkedHashMap<>();
			for (var h : headers.entrySet()) {
				resolvedHeaders.put(h.getKey(), resolveEnvVars(h.getValue()));
			}

			result.put(name, new McpServerConfig(name, transport, command, args, env, url, resolvedHeaders));
		}

		return result;
	}

	/**
	 * Resolve ${VAR_NAME} references from the environment.
	 */
	static String resolveEnvVars(String value) {
		if (value == null) return null;
		Matcher matcher = ENV_VAR.matcher(value);
		StringBuilder sb = new StringBuilder();
		while (matcher.find()) {
			String varName = matcher.group(1);
			String envValue = System.getenv(varName);
			matcher.appendReplacement(sb, Matcher.quoteReplacement(envValue != null ? envValue : matcher.group()));
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	private static String getStringOrNull(JsonNode node, String field) {
		JsonNode child = node.get(field);
		return child != null && child.isTextual() ? child.asText() : null;
	}

	private static String getStringOrDefault(JsonNode node, String field, String defaultVal) {
		String val = getStringOrNull(node, field);
		return val != null ? val : defaultVal;
	}

	private static List<String> getStringList(JsonNode node, String field) {
		JsonNode child = node.get(field);
		if (child == null || !child.isArray()) return List.of();
		List<String> result = new ArrayList<>();
		child.forEach(n -> result.add(n.asText()));
		return List.copyOf(result);
	}

	private static Map<String, String> getStringMap(JsonNode node, String field) {
		JsonNode child = node.get(field);
		if (child == null || !child.isObject()) return Map.of();
		Map<String, String> result = new LinkedHashMap<>();
		child.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue().asText()));
		return result;
	}
}
