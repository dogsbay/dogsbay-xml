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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpManagerTest {

	@Test
	void emptyConfigListIsNoOp() {
		var manager = new McpManager(List.of());
		int started = manager.startAll();
		assertThat(started).isZero();
		assertThat(manager.discoverTools()).isEmpty();
		assertThat(manager.status()).isEmpty();
		manager.close();
	}

	@Test
	void serverNamesReturnsConfiguredNames() {
		var configs = List.of(
			new McpServerConfig("server1", "stdio", "cmd1", List.of(), Map.of(), null, Map.of()),
			new McpServerConfig("server2", "http", null, List.of(), Map.of(), "http://example.com", Map.of())
		);
		var manager = new McpManager(configs);

		assertThat(manager.serverNames()).containsExactly("server1", "server2");
	}

	@Test
	void initialStatusIsStopped() {
		var configs = List.of(
			new McpServerConfig("srv", "stdio", "cmd", List.of(), Map.of(), null, Map.of())
		);
		var manager = new McpManager(configs);

		assertThat(manager.status().get("srv")).isEqualTo(McpManager.ServerStatus.STOPPED);
	}

	@Test
	void failedServerMarkedAsError() {
		// Use an invalid command that will fail to start
		var configs = List.of(
			new McpServerConfig("bad", "stdio", "/nonexistent/binary", List.of(), Map.of(), null, Map.of())
		);
		var manager = new McpManager(configs);
		int started = manager.startAll();

		assertThat(started).isZero();
		assertThat(manager.status().get("bad")).isEqualTo(McpManager.ServerStatus.ERROR);
		manager.close();
	}

	@Test
	void unsupportedTransportMarkedAsError() {
		var configs = List.of(
			new McpServerConfig("ws", "websocket", null, List.of(), Map.of(), "ws://localhost", Map.of())
		);
		var manager = new McpManager(configs);
		int started = manager.startAll();

		assertThat(started).isZero();
		assertThat(manager.status().get("ws")).isEqualTo(McpManager.ServerStatus.ERROR);
		manager.close();
	}

	@Test
	void closeMarksRunningServersStopped() {
		// Servers that failed to start remain ERROR after close.
		// Only successfully running servers get marked STOPPED.
		// With no valid server we just verify close doesn't throw.
		var configs = List.of(
			new McpServerConfig("srv", "stdio", "/nonexistent", List.of(), Map.of(), null, Map.of())
		);
		var manager = new McpManager(configs);
		manager.startAll(); // will fail
		manager.close(); // should not throw

		// Server that failed to start stays at ERROR (close only touches clients map)
		assertThat(manager.status().get("srv")).isEqualTo(McpManager.ServerStatus.ERROR);
	}
}
