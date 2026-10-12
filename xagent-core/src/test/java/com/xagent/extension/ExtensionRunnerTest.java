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
package com.xagent.extension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xagent.tool.AgentTool;
import com.xagent.tool.AgentToolResult;
import com.xagent.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionRunnerTest {

	@TempDir
	Path tempDir;

	private ExtensionRunner runner;
	private ExtensionContext context;
	private ToolRegistry toolRegistry;

	@BeforeEach
	void setUp() {
		runner = new ExtensionRunner();
		toolRegistry = new ToolRegistry();
		context = new ExtensionContext(tempDir, toolRegistry, List.of());
	}

	@Test
	void lifecycleEventsFireInOrder() {
		var events = new ArrayList<String>();

		var ext = new TestExtension("test") {
			@Override
			public void onLoad(ExtensionContext ctx) { events.add("load"); }
			@Override
			public void onTurnStart(ExtensionContext ctx) { events.add("turnStart"); }
			@Override
			public void onTurnEnd(ExtensionContext ctx) { events.add("turnEnd"); }
			@Override
			public void onAgentEnd(ExtensionContext ctx) { events.add("agentEnd"); }
			@Override
			public void onUnload() { events.add("unload"); }
		};

		runner.loadExtensions(List.of(ext), context, toolRegistry);
		runner.onTurnStart(context);
		runner.onTurnEnd(context);
		runner.onAgentEnd(context);
		runner.unloadAll();

		assertThat(events).containsExactly("load", "turnStart", "turnEnd", "agentEnd", "unload");
	}

	@Test
	void toolCallHooksFireCorrectly() {
		var events = new ArrayList<String>();

		var ext = new TestExtension("test") {
			@Override
			public boolean onBeforeToolCall(String toolName, String arguments, ExtensionContext ctx) {
				events.add("before:" + toolName);
				return true;
			}
			@Override
			public void onAfterToolCall(String toolName, String result, boolean isError, ExtensionContext ctx) {
				events.add("after:" + toolName + ":" + isError);
			}
		};

		runner.loadExtensions(List.of(ext), context, toolRegistry);
		boolean allowed = runner.onBeforeToolCall("read", "{}", context);
		runner.onAfterToolCall("read", "content", false, context);

		assertThat(allowed).isTrue();
		assertThat(events).containsExactly("before:read", "after:read:false");
	}

	@Test
	void extensionCanVetoToolCall() {
		var ext = new TestExtension("blocker") {
			@Override
			public boolean onBeforeToolCall(String toolName, String arguments, ExtensionContext ctx) {
				return !"bash".equals(toolName);
			}
		};

		runner.loadExtensions(List.of(ext), context, toolRegistry);

		assertThat(runner.onBeforeToolCall("read", "{}", context)).isTrue();
		assertThat(runner.onBeforeToolCall("bash", "{}", context)).isFalse();
	}

	@Test
	void extensionRegistersCustomTools() {
		var customTool = new AgentTool() {
			@Override public String name() { return "custom_tool"; }
			@Override public String description() { return "A custom tool"; }
			@Override public JsonNode parametersSchema() {
				return new ObjectMapper().createObjectNode().put("type", "object");
			}
			@Override public AgentToolResult execute(String toolCallId, JsonNode params,
				Supplier<Boolean> isCancelled, Consumer<AgentToolResult> onUpdate) {
				return AgentToolResult.success("custom result");
			}
		};

		var ext = new TestExtension("tools") {
			@Override
			public List<AgentTool> registerTools() {
				return List.of(customTool);
			}
		};

		runner.loadExtensions(List.of(ext), context, toolRegistry);

		assertThat(toolRegistry.get("custom_tool")).isNotNull();
		assertThat(toolRegistry.get("custom_tool").name()).isEqualTo("custom_tool");
	}

	@Test
	void errorInExtensionDoesNotCrashOthers() {
		var events = new ArrayList<String>();

		var badExt = new TestExtension("bad") {
			@Override
			public void onTurnStart(ExtensionContext ctx) {
				throw new RuntimeException("Extension error");
			}
		};

		var goodExt = new TestExtension("good") {
			@Override
			public void onTurnStart(ExtensionContext ctx) {
				events.add("good:turnStart");
			}
		};

		runner.loadExtensions(List.of(badExt, goodExt), context, toolRegistry);
		runner.onTurnStart(context);

		assertThat(events).containsExactly("good:turnStart");
		assertThat(runner.errors()).isNotEmpty();
		assertThat(runner.errors().getFirst()).contains("Extension error");
	}

	@Test
	void errorInOnLoadPreventsRegistration() {
		var ext = new TestExtension("broken") {
			@Override
			public void onLoad(ExtensionContext ctx) {
				throw new RuntimeException("Load failed");
			}
		};

		runner.loadExtensions(List.of(ext), context, toolRegistry);

		assertThat(runner.extensions()).isEmpty();
		assertThat(runner.errors()).hasSize(1);
	}

	@Test
	void multipleExtensionsAllReceiveEvents() {
		var events = new ArrayList<String>();

		var ext1 = new TestExtension("ext1") {
			@Override
			public void onTurnStart(ExtensionContext ctx) { events.add("ext1"); }
		};
		var ext2 = new TestExtension("ext2") {
			@Override
			public void onTurnStart(ExtensionContext ctx) { events.add("ext2"); }
		};

		runner.loadExtensions(List.of(ext1, ext2), context, toolRegistry);
		runner.onTurnStart(context);

		assertThat(events).containsExactly("ext1", "ext2");
	}

	/**
	 * Base test extension with a configurable name.
	 */
	static class TestExtension implements Extension {
		private final String extensionName;

		TestExtension(String name) {
			this.extensionName = name;
		}

		@Override
		public String name() {
			return extensionName;
		}
	}
}
