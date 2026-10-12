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

import com.xagent.core.ToolHookResult;
import com.xagent.core.ToolHooks;
import com.xagent.tool.AgentToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeToolHooksTest {

    @Test
    @DisplayName("afterToolCall pipes the result through every hook in order")
    void afterToolCallPipes() {
        ToolHooks appendA = new ToolHooks() {
            public AgentToolResult afterToolCall(String t, AgentToolResult r) {
                return AgentToolResult.success(r.content() + "+A");
            }
        };
        ToolHooks appendB = new ToolHooks() {
            public AgentToolResult afterToolCall(String t, AgentToolResult r) {
                return AgentToolResult.success(r.content() + "+B");
            }
        };
        var composite = new CompositeToolHooks(appendA, appendB);
        AgentToolResult out = composite.afterToolCall("x", AgentToolResult.success("base"));
        assertThat(out.content()).isEqualTo("base+A+B");
    }

    @Test
    @DisplayName("a Block in beforeToolCall short-circuits the chain")
    void blockShortCircuits() {
        ToolHooks blocker = new ToolHooks() {
            public ToolHookResult beforeToolCall(String t, String a) {
                return ToolHookResult.block("nope");
            }
        };
        var composite = new CompositeToolHooks(blocker, new ToolHooks() {});
        assertThat(composite.beforeToolCall("x", "{}")).isInstanceOf(ToolHookResult.Block.class);
    }

    @Test
    @DisplayName("all-allow hooks yield ALLOW")
    void allAllow() {
        var composite = new CompositeToolHooks(new ToolHooks() {}, new ToolHooks() {});
        assertThat(composite.beforeToolCall("x", "{}")).isEqualTo(ToolHookResult.ALLOW);
    }
}
