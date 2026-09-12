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

import com.dogsbay.agent.AgentHost;
import com.xagent.core.ToolHooks;
import com.xagent.tool.AgentToolResult;

/**
 * Post-mutation validation feedback (plans/agent-edit-validate-loop.md). After a
 * mutating content tool succeeds, this appends the host's validation result for
 * the touched document to the tool result, so the agent sees — in the same loop
 * — whether its edit kept the document valid and can self-correct before moving
 * on. Advisory only: it never blocks, and any failure leaves the result
 * untouched.
 *
 * <p>Wired as the delegate of {@code PermissionToolHooks}, whose
 * {@code afterToolCall} calls through to this one.
 */
public final class ValidationFeedbackHooks implements ToolHooks {

    private final AgentHost host;

    public ValidationFeedbackHooks(AgentHost host) {
        this.host = host;
    }

    @Override
    public AgentToolResult afterToolCall(String toolName, AgentToolResult result) {
        if (result == null || result.isError()) {
            return result;   // failed / denied call → nothing to validate
        }
        String feedback;
        try {
            feedback = host.validateAfterMutation(toolName);
        } catch (Exception e) {
            return result;   // never let validation break the turn
        }
        if (feedback == null || feedback.isBlank()) {
            return result;
        }
        String content = result.content() == null ? "" : result.content();
        String joined = content.isBlank() ? feedback : content + "\n\n" + feedback;
        return new AgentToolResult(joined, result.details(), false, result.images());
    }
}
