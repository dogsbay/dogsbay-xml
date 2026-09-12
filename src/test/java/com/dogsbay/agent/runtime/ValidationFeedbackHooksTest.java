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

import java.awt.Window;
import java.nio.file.Path;
import java.util.List;

import com.dogsbay.agent.AgentContext;
import com.dogsbay.agent.AgentHost;
import com.xagent.tool.AgentTool;
import com.xagent.tool.AgentToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationFeedbackHooksTest {

    /** Minimal host returning a fixed validation string for a chosen tool. */
    private static AgentHost hostReturning(String feedback, String forTool) {
        return new AgentHost() {
            public Path workingDirectory() { return Path.of("."); }
            public AgentContext currentContext() { return AgentContext.empty(); }
            public List<AgentTool> editorTools() { return List.of(); }
            public Window dialogParent() { return null; }
            public String validateAfterMutation(String toolName) {
                return forTool.equals(toolName) ? feedback : null;
            }
        };
    }

    @Test
    @DisplayName("validation feedback is appended to a successful mutation's result")
    void appendsFeedback() {
        var hooks = new ValidationFeedbackHooks(
                hostReturning("validation: topic.dita — 0 errors", "replace_selection"));
        AgentToolResult out = hooks.afterToolCall("replace_selection",
                AgentToolResult.success("Selection replaced"));
        assertThat(out.content())
                .isEqualTo("Selection replaced\n\nvalidation: topic.dita — 0 errors");
        assertThat(out.isError()).isFalse();
    }

    @Test
    @DisplayName("tools with no validation feedback pass through unchanged")
    void noFeedbackPassesThrough() {
        var hooks = new ValidationFeedbackHooks(hostReturning("x", "replace_selection"));
        AgentToolResult original = AgentToolResult.success("grep results");
        assertThat(hooks.afterToolCall("grep", original)).isSameAs(original);
    }

    @Test
    @DisplayName("a failed/denied tool result is never validated")
    void errorResultUntouched() {
        var hooks = new ValidationFeedbackHooks(
                hostReturning("validation: …", "replace_selection"));
        AgentToolResult error = AgentToolResult.error("denied by user");
        assertThat(hooks.afterToolCall("replace_selection", error)).isSameAs(error);
    }
}
