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

package com.dogsbay.agent.runtime;

import java.util.List;

import com.xagent.core.ToolHookResult;
import com.xagent.core.ToolHooks;
import com.xagent.tool.AgentToolResult;

/**
 * Chains several {@link ToolHooks} into one delegate (PermissionToolHooks accepts
 * a single delegate). {@code beforeToolCall} runs each in order — a Block short-
 * circuits, a Modify threads the new arguments forward — and {@code afterToolCall}
 * pipes the result through each in order.
 */
public final class CompositeToolHooks implements ToolHooks {

    private final List<ToolHooks> hooks;

    public CompositeToolHooks(ToolHooks... hooks) {
        this.hooks = List.of(hooks);
    }

    @Override
    public ToolHookResult beforeToolCall(String toolName, String arguments) {
        String args = arguments;
        boolean modified = false;
        for (ToolHooks h : hooks) {
            switch (h.beforeToolCall(toolName, args)) {
                case ToolHookResult.Block block -> {
                    return block;
                }
                case ToolHookResult.Modify modify -> {
                    args = modify.newArguments();
                    modified = true;
                }
                case ToolHookResult.Allow ignored -> {
                    // proceed
                }
            }
        }
        return modified ? ToolHookResult.modify(args) : ToolHookResult.ALLOW;
    }

    @Override
    public AgentToolResult afterToolCall(String toolName, AgentToolResult result) {
        AgentToolResult current = result;
        for (ToolHooks h : hooks) {
            current = h.afterToolCall(toolName, current);
        }
        return current;
    }
}
