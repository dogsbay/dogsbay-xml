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

import com.xagent.tool.AgentTool;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

/**
 * Manages MCP server lifecycle and tool discovery.
 */
public class McpManager implements AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(McpManager.class);
	private static final Duration TOOL_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration INIT_TIMEOUT = Duration.ofSeconds(15);

	private final List<McpServerConfig> configs;
	private final Map<String, McpClient> clients = new LinkedHashMap<>();
	private final Map<String, ServerStatus> statuses = new LinkedHashMap<>();

	public enum ServerStatus { RUNNING, STOPPED, ERROR }

	public McpManager(List<McpServerConfig> configs) {
		this.configs = List.copyOf(configs);
		for (var config : configs) {
			statuses.put(config.name(), ServerStatus.STOPPED);
		}
	}

	/**
	 * Start all configured MCP servers.
	 * Returns the count of successfully started servers.
	 */
	public int startAll() {
		int started = 0;
		for (var config : configs) {
			try {
				McpTransport transport = createTransport(config);
				McpClient client = new DefaultMcpClient.Builder()
					.transport(transport)
					.key(config.name())
					.clientName("xagent")
					.clientVersion("0.1.0")
					.toolExecutionTimeout(TOOL_TIMEOUT)
					.initializationTimeout(INIT_TIMEOUT)
					.build();

				clients.put(config.name(), client);
				statuses.put(config.name(), ServerStatus.RUNNING);
				started++;
				LOG.info("MCP server '{}' started successfully", config.name());
			} catch (Exception e) {
				statuses.put(config.name(), ServerStatus.ERROR);
				LOG.warn("Failed to start MCP server '{}': {}", config.name(), e.getMessage());
			}
		}
		return started;
	}

	/**
	 * Discover tools from all running MCP servers.
	 */
	public List<AgentTool> discoverTools() {
		List<AgentTool> tools = new ArrayList<>();
		for (var entry : clients.entrySet()) {
			String name = entry.getKey();
			McpClient client = entry.getValue();
			if (statuses.get(name) != ServerStatus.RUNNING) continue;

			try {
				tools.addAll(McpToolAdapter.fromClient(name, client));
			} catch (Exception e) {
				LOG.warn("Failed to discover tools from '{}': {}", name, e.getMessage());
				statuses.put(name, ServerStatus.ERROR);
			}
		}
		return tools;
	}

	/**
	 * Get the status of each configured server.
	 */
	public Map<String, ServerStatus> status() {
		return Map.copyOf(statuses);
	}

	/**
	 * Get the list of server names.
	 */
	public List<String> serverNames() {
		return configs.stream().map(McpServerConfig::name).toList();
	}

	@Override
	public void close() {
		for (var entry : clients.entrySet()) {
			try {
				entry.getValue().close();
			} catch (Exception e) {
				LOG.debug("Error closing MCP client '{}': {}", entry.getKey(), e.getMessage());
			}
			statuses.put(entry.getKey(), ServerStatus.STOPPED);
		}
		clients.clear();
	}

	private McpTransport createTransport(McpServerConfig config) {
		if (config.isStdio()) {
			List<String> command = new ArrayList<>();
			command.add(config.command());
			command.addAll(config.args());

			return new StdioMcpTransport.Builder()
				.command(command)
				.environment(config.env())
				.build();
		} else if (config.isHttp()) {
			var builder = new HttpMcpTransport.Builder()
				.sseUrl(config.url());

			if (config.headers() != null && !config.headers().isEmpty()) {
				builder.customHeaders(config.headers());
			}

			return builder.build();
		} else {
			throw new IllegalArgumentException("Unsupported transport: " + config.transport());
		}
	}
}
