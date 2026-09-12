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
import com.xagent.core.ToolHookResult;
import com.xagent.core.ToolHooks;
import com.xagent.tool.AgentTool;
import com.xagent.tool.AgentToolResult;
import com.xagent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionToolHooksTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static AgentTool fakeTool(String name, boolean readOnly) {
		return new AgentTool() {
			@Override
			public String name() {
				return name;
			}

			@Override
			public String description() {
				return "fake";
			}

			@Override
			public boolean isReadOnly() {
				return readOnly;
			}

			@Override
			public JsonNode parametersSchema() {
				return MAPPER.createObjectNode();
			}

			@Override
			public AgentToolResult execute(String toolCallId, JsonNode params,
				Supplier<Boolean> isCancelled, Consumer<AgentToolResult> onUpdate) {
				return AgentToolResult.success("ok");
			}
		};
	}

	private static ToolRegistry registryWith(AgentTool... tools) {
		var registry = new ToolRegistry();
		for (var tool : tools) {
			registry.register(tool);
		}
		return registry;
	}

	@Test
	void readOnlyToolsBypassPermissionChecks() {
		var asked = new ArrayList<PermissionRequest>();
		PermissionHandler handler = request -> {
			asked.add(request);
			return PermissionDecision.DENY;
		};
		var hooks = new PermissionToolHooks(
			registryWith(fakeTool("read", true)), new PermissionPolicy(), handler, null, null);

		var result = hooks.beforeToolCall("read", "{\"path\": \"/etc/passwd\"}");

		assertThat(result).isInstanceOf(ToolHookResult.Allow.class);
		assertThat(asked).isEmpty();
	}

	@Test
	void mutatingToolAsksHandlerAndAllowIsOneShot() {
		var asked = new ArrayList<PermissionRequest>();
		PermissionHandler handler = request -> {
			asked.add(request);
			return PermissionDecision.ALLOW;
		};
		var hooks = new PermissionToolHooks(
			registryWith(fakeTool("write", false)), new PermissionPolicy(), handler, null, null);

		assertThat(hooks.beforeToolCall("write", "{\"path\": \"a.txt\"}"))
			.isInstanceOf(ToolHookResult.Allow.class);
		assertThat(hooks.beforeToolCall("write", "{\"path\": \"a.txt\"}"))
			.isInstanceOf(ToolHookResult.Allow.class);
		// ALLOW grants nothing -- asked every time
		assertThat(asked).hasSize(2);
		assertThat(asked.get(0).primaryArg()).isEqualTo("a.txt");
	}

	@Test
	void denyBlocksExecution() {
		var hooks = new PermissionToolHooks(
			registryWith(fakeTool("bash", false)), new PermissionPolicy(),
			request -> PermissionDecision.DENY, null, null);

		var result = hooks.beforeToolCall("bash", "{\"command\": \"rm -rf /\"}");

		assertThat(result).isInstanceOf(ToolHookResult.Block.class);
	}

	@Test
	void denyRuleBlocksWithoutAsking() {
		var asked = new ArrayList<PermissionRequest>();
		var policy = new PermissionPolicy(List.of(), List.of(PermissionRule.parse("bash(rm *)")));
		var hooks = new PermissionToolHooks(
			registryWith(fakeTool("bash", false)), policy,
			request -> {
				asked.add(request);
				return PermissionDecision.ALLOW;
			}, null, null);

		var result = hooks.beforeToolCall("bash", "{\"command\": \"rm -rf /\"}");

		assertThat(result).isInstanceOf(ToolHookResult.Block.class);
		assertThat(asked).isEmpty();
	}

	@Test
	void allowSessionGrantsForSubsequentCalls() {
		var asked = new ArrayList<PermissionRequest>();
		PermissionHandler handler = request -> {
			asked.add(request);
			return PermissionDecision.ALLOW_SESSION;
		};
		var hooks = new PermissionToolHooks(
			registryWith(fakeTool("bash", false)), new PermissionPolicy(), handler, null, null);

		hooks.beforeToolCall("bash", "{\"command\": \"git status\"}");
		var second = hooks.beforeToolCall("bash", "{\"command\": \"git log\"}");
		assertThat(second).isInstanceOf(ToolHookResult.Allow.class);
		// the git grant covers only git commands -- a different program asks again
		hooks.beforeToolCall("bash", "{\"command\": \"rm -rf /tmp/x\"}");
		assertThat(asked).hasSize(2);
	}

	@Test
	void allowAlwaysPersistsGrantedRules() {
		var persisted = new ArrayList<PermissionRule>();
		var hooks = new PermissionToolHooks(
			registryWith(fakeTool("bash", false)), new PermissionPolicy(),
			request -> PermissionDecision.ALLOW_ALWAYS, null, persisted::add);

		hooks.beforeToolCall("bash", "{\"command\": \"mvn test\"}");

		assertThat(persisted).containsExactly(
			new PermissionRule("bash", "mvn"),
			new PermissionRule("bash", "mvn *")
		);
	}

	@Test
	void delegateBlockWins() {
		ToolHooks delegate = new ToolHooks() {
			@Override
			public ToolHookResult beforeToolCall(String toolName, String arguments) {
				return ToolHookResult.block("custom hook says no");
			}
		};
		var hooks = new PermissionToolHooks(
			registryWith(fakeTool("read", true)), new PermissionPolicy(),
			PermissionHandler.ALLOW_ALL, delegate, null);

		var result = hooks.beforeToolCall("read", "{}");

		assertThat(result).isInstanceOf(ToolHookResult.Block.class);
	}

	@Test
	void delegateModifyIsPreservedAndCheckedAgainstModifiedArgs() {
		ToolHooks delegate = new ToolHooks() {
			@Override
			public ToolHookResult beforeToolCall(String toolName, String arguments) {
				return ToolHookResult.modify("{\"command\": \"rm -rf /\"}");
			}
		};
		var policy = new PermissionPolicy(
			List.of(PermissionRule.parse("bash(ls *)")),
			List.of(PermissionRule.parse("bash(rm *)"))
		);
		var hooks = new PermissionToolHooks(
			registryWith(fakeTool("bash", false)), policy,
			PermissionHandler.ALLOW_ALL, delegate, null);

		// original args would be allowed, but the delegate rewrote them to a denied command
		var result = hooks.beforeToolCall("bash", "{\"command\": \"ls -la\"}");

		assertThat(result).isInstanceOf(ToolHookResult.Block.class);
	}

	@Test
	void delegateModifyFlowsThroughWhenAllowed() {
		ToolHooks delegate = new ToolHooks() {
			@Override
			public ToolHookResult beforeToolCall(String toolName, String arguments) {
				return ToolHookResult.modify("{\"command\": \"ls -la\"}");
			}
		};
		var policy = new PermissionPolicy(List.of(PermissionRule.parse("bash(ls *)")), List.of());
		var hooks = new PermissionToolHooks(
			registryWith(fakeTool("bash", false)), policy,
			PermissionHandler.DENY_ALL, delegate, null);

		var result = hooks.beforeToolCall("bash", "{\"command\": \"pwd\"}");

		assertThat(result).isInstanceOf(ToolHookResult.Modify.class);
		assertThat(((ToolHookResult.Modify) result).newArguments()).contains("ls -la");
	}

	@Test
	void unknownToolIsTreatedAsMutating() {
		var hooks = new PermissionToolHooks(
			registryWith(), new PermissionPolicy(),
			request -> PermissionDecision.DENY, null, null);

		var result = hooks.beforeToolCall("mcp_some_tool", "{}");

		assertThat(result).isInstanceOf(ToolHookResult.Block.class);
	}

	@Test
	void extractPrimaryArgRecognizesKnownFields() {
		assertThat(PermissionToolHooks.extractPrimaryArg("{\"command\": \"ls\"}")).isEqualTo("ls");
		assertThat(PermissionToolHooks.extractPrimaryArg("{\"path\": \"a.txt\"}")).isEqualTo("a.txt");
		assertThat(PermissionToolHooks.extractPrimaryArg("{\"other\": 1}")).isEmpty();
		assertThat(PermissionToolHooks.extractPrimaryArg("not json")).isEmpty();
	}
}
