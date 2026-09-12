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

import java.util.function.Consumer;

/**
 * Enforces the permission policy at the tool-hook seam. Read-only tools
 * bypass checks; mutating tools are evaluated against the policy and,
 * when undecided, escalated to the {@link PermissionHandler}.
 *
 * Composes with an optional delegate {@link ToolHooks} so user hooks keep
 * working: the delegate runs first (it may modify arguments), and the
 * permission check evaluates the final arguments that would execute.
 */
public class PermissionToolHooks implements ToolHooks {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final ToolRegistry registry;
	private final PermissionPolicy policy;
	private final PermissionHandler handler;
	private final ToolHooks delegate;
	private final Consumer<PermissionRule> persister;
	private final Object askLock = new Object();

	/**
	 * @param registry  used to look up {@link AgentTool#isReadOnly()}
	 * @param policy    rule evaluation and session grants
	 * @param handler   asked when policy returns ASK
	 * @param delegate  optional user hooks to compose (may be null)
	 * @param persister called with the granted rule on ALLOW_ALWAYS (may be null)
	 */
	public PermissionToolHooks(
		ToolRegistry registry,
		PermissionPolicy policy,
		PermissionHandler handler,
		ToolHooks delegate,
		Consumer<PermissionRule> persister
	) {
		this.registry = registry;
		this.policy = policy;
		this.handler = handler;
		this.delegate = delegate;
		this.persister = persister;
	}

	@Override
	public ToolHookResult beforeToolCall(String toolName, String arguments) {
		String effectiveArgs = arguments;
		ToolHookResult delegateResult = ToolHookResult.ALLOW;
		if (delegate != null) {
			delegateResult = delegate.beforeToolCall(toolName, arguments);
			switch (delegateResult) {
				case ToolHookResult.Block block -> { return block; }
				case ToolHookResult.Modify modify -> effectiveArgs = modify.newArguments();
				case ToolHookResult.Allow ignored -> {}
			}
		}

		AgentTool tool = registry.get(toolName);
		if (tool != null && tool.isReadOnly()) {
			return delegateResult;
		}

		String primaryArg = extractPrimaryArg(effectiveArgs);
		return switch (policy.check(toolName, primaryArg)) {
			case ALLOW -> delegateResult;
			case DENY -> ToolHookResult.block("denied by permission rule");
			case ASK -> ask(toolName, effectiveArgs, primaryArg, delegateResult);
		};
	}

	@Override
	public AgentToolResult afterToolCall(String toolName, AgentToolResult result) {
		return delegate != null ? delegate.afterToolCall(toolName, result) : result;
	}

	private ToolHookResult ask(String toolName, String arguments, String primaryArg, ToolHookResult delegateResult) {
		// Serialize asks: parallel tool execution must not interleave prompts.
		synchronized (askLock) {
			// Another ask may have granted a covering rule while we waited.
			if (policy.check(toolName, primaryArg) == PermissionPolicy.Verdict.ALLOW) {
				return delegateResult;
			}
			var decision = handler.handle(new PermissionRequest(toolName, arguments, primaryArg));
			switch (decision) {
				case DENY -> {
					return ToolHookResult.block("denied by user");
				}
				case ALLOW -> {
					return delegateResult;
				}
				case ALLOW_SESSION, ALLOW_ALWAYS -> {
					for (var rule : PermissionPolicy.grantRulesFor(toolName, primaryArg)) {
						if (decision == PermissionDecision.ALLOW_ALWAYS) {
							policy.addAllowRule(rule);
							if (persister != null) persister.accept(rule);
						} else {
							policy.grantSession(rule);
						}
					}
					return delegateResult;
				}
			}
			return delegateResult;
		}
	}

	/**
	 * The argument permission globs match against: command for bash,
	 * path for file tools. Empty when nothing recognizable is present.
	 */
	static String extractPrimaryArg(String argumentsJson) {
		try {
			JsonNode args = MAPPER.readTree(argumentsJson);
			for (String field : new String[] {"command", "path", "file_path", "file"}) {
				JsonNode value = args.path(field);
				if (value.isTextual()) return value.asText();
			}
		} catch (Exception ignored) {
			// unparseable arguments -- no primary arg
		}
		return "";
	}
}
