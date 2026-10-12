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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpConfigLoaderTest {

	@TempDir
	Path tempDir;

	@Test
	void parseStdioServer() throws IOException {
		Path configDir = tempDir.resolve(".xagent");
		Files.createDirectories(configDir);
		Files.writeString(configDir.resolve("mcp.json"), """
			{
			  "mcpServers": {
			    "filesystem": {
			      "transport": "stdio",
			      "command": "npx",
			      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/home/user"],
			      "env": {"NODE_PATH": "/usr/lib/node_modules"}
			    }
			  }
			}
			""");

		var configs = McpConfigLoader.load(tempDir);
		assertThat(configs).hasSize(1);

		var config = configs.get(0);
		assertThat(config.name()).isEqualTo("filesystem");
		assertThat(config.transport()).isEqualTo("stdio");
		assertThat(config.isStdio()).isTrue();
		assertThat(config.isHttp()).isFalse();
		assertThat(config.command()).isEqualTo("npx");
		assertThat(config.args()).containsExactly("-y", "@modelcontextprotocol/server-filesystem", "/home/user");
		assertThat(config.env()).containsEntry("NODE_PATH", "/usr/lib/node_modules");
	}

	@Test
	void parseHttpServer() throws IOException {
		Path configDir = tempDir.resolve(".xagent");
		Files.createDirectories(configDir);
		Files.writeString(configDir.resolve("mcp.json"), """
			{
			  "mcpServers": {
			    "custom-api": {
			      "transport": "http",
			      "url": "http://localhost:8080/mcp",
			      "headers": {"X-Custom": "value"}
			    }
			  }
			}
			""");

		var configs = McpConfigLoader.load(tempDir);
		assertThat(configs).hasSize(1);

		var config = configs.get(0);
		assertThat(config.name()).isEqualTo("custom-api");
		assertThat(config.transport()).isEqualTo("http");
		assertThat(config.isHttp()).isTrue();
		assertThat(config.isStdio()).isFalse();
		assertThat(config.url()).isEqualTo("http://localhost:8080/mcp");
		assertThat(config.headers()).containsEntry("X-Custom", "value");
	}

	@Test
	void parseMultipleServers() throws IOException {
		Path configDir = tempDir.resolve(".xagent");
		Files.createDirectories(configDir);
		Files.writeString(configDir.resolve("mcp.json"), """
			{
			  "mcpServers": {
			    "server1": {"transport": "stdio", "command": "cmd1"},
			    "server2": {"transport": "http", "url": "http://example.com/mcp"}
			  }
			}
			""");

		var configs = McpConfigLoader.load(tempDir);
		assertThat(configs).hasSize(2);
		assertThat(configs.get(0).name()).isEqualTo("server1");
		assertThat(configs.get(1).name()).isEqualTo("server2");
	}

	@Test
	void defaultTransportIsStdio() throws IOException {
		Path configDir = tempDir.resolve(".xagent");
		Files.createDirectories(configDir);
		Files.writeString(configDir.resolve("mcp.json"), """
			{
			  "mcpServers": {
			    "default": {"command": "some-server"}
			  }
			}
			""");

		var configs = McpConfigLoader.load(tempDir);
		assertThat(configs).hasSize(1);
		assertThat(configs.get(0).transport()).isEqualTo("stdio");
		assertThat(configs.get(0).isStdio()).isTrue();
	}

	@Test
	void missingConfigReturnsEmptyList() throws IOException {
		var configs = McpConfigLoader.load(tempDir);
		assertThat(configs).isEmpty();
	}

	@Test
	void emptyMcpServersReturnsEmptyList() throws IOException {
		Path configDir = tempDir.resolve(".xagent");
		Files.createDirectories(configDir);
		Files.writeString(configDir.resolve("mcp.json"), """
			{"mcpServers": {}}
			""");

		var configs = McpConfigLoader.load(tempDir);
		assertThat(configs).isEmpty();
	}

	@Test
	void resolveEnvVarsSubstitutes() {
		// HOME is a standard env var on Linux
		String home = System.getenv("HOME");
		if (home != null) {
			String result = McpConfigLoader.resolveEnvVars("path/${HOME}/file");
			assertThat(result).isEqualTo("path/" + home + "/file");
		}
	}

	@Test
	void resolveEnvVarsKeepsUnresolved() {
		String result = McpConfigLoader.resolveEnvVars("${XAGENT_NONEXISTENT_VAR_12345}");
		assertThat(result).isEqualTo("${XAGENT_NONEXISTENT_VAR_12345}");
	}

	@Test
	void resolveEnvVarsHandlesNull() {
		assertThat(McpConfigLoader.resolveEnvVars(null)).isNull();
	}

	@Test
	void malformedJsonThrows() throws IOException {
		Path configDir = tempDir.resolve(".xagent");
		Files.createDirectories(configDir);
		Files.writeString(configDir.resolve("mcp.json"), "not json");

		assertThatThrownBy(() -> McpConfigLoader.load(tempDir))
			.isInstanceOf(IOException.class);
	}

	@Test
	void localConfigOverridesGlobal() throws IOException {
		// This test exercises parseFile directly since we can't easily mock the global path
		Path file1 = tempDir.resolve("global.json");
		Files.writeString(file1, """
			{
			  "mcpServers": {
			    "shared": {"transport": "stdio", "command": "global-cmd"},
			    "global-only": {"transport": "stdio", "command": "global"}
			  }
			}
			""");

		Path file2 = tempDir.resolve("local.json");
		Files.writeString(file2, """
			{
			  "mcpServers": {
			    "shared": {"transport": "stdio", "command": "local-cmd"},
			    "local-only": {"transport": "stdio", "command": "local"}
			  }
			}
			""");

		Map<String, McpServerConfig> global = McpConfigLoader.parseFile(file1);
		Map<String, McpServerConfig> local = McpConfigLoader.parseFile(file2);

		// Simulate merge: global first, then local overrides
		var merged = new java.util.LinkedHashMap<>(global);
		merged.putAll(local);

		assertThat(merged).hasSize(3);
		assertThat(merged.get("shared").command()).isEqualTo("local-cmd");
		assertThat(merged.get("global-only").command()).isEqualTo("global");
		assertThat(merged.get("local-only").command()).isEqualTo("local");
	}
}
