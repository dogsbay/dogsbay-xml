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

import java.util.Set;

import com.xagent.core.ToolHooks;
import com.xagent.tool.AgentToolResult;

/**
 * Nudges the agent toward surgical edits (plans/format-house-style.md, Phase 5).
 * When a tool that re-serializes a whole document succeeds, this appends an in-loop
 * reminder to the tool result — the same self-correct mechanism as the validate
 * loop — so the agent prefers targeted {@code edit}s that preserve formatting,
 * comments, and significant whitespace instead of normalizing the file.
 */
public final class SurgicalEditAdvisor implements ToolHooks {

    /** Tools that round-trip / rewrite a whole document (normalizing its layout). */
    private static final Set<String> WHOLE_DOCUMENT_TOOLS = Set.of(
            "set_document_content", "transform_xslt", "transform");

    @Override
    public AgentToolResult afterToolCall(String toolName, AgentToolResult result) {
        if (result == null || result.isError() || !WHOLE_DOCUMENT_TOOLS.contains(toolName)) {
            return result;
        }
        String tip = "Note: " + toolName + " re-serializes the whole document, which "
                + "normalizes formatting and can drop comments or significant whitespace. "
                + "For a targeted change, prefer the surgical `edit` tool so only your "
                + "intended change is touched.";
        String content = result.content() == null ? "" : result.content();
        String joined = content.isBlank() ? tip : content + "\n\n" + tip;
        return new AgentToolResult(joined, result.details(), false, result.images());
    }
}
