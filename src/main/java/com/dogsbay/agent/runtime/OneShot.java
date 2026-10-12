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

import com.xagent.core.Agent;
import com.xagent.core.UsageStats;
import com.xagent.event.AgentEvent;
import com.xagent.provider.ProviderConfig;
import com.xagent.provider.ProviderFactory;
import com.xagent.tool.ToolRegistry;

import dev.langchain4j.model.chat.StreamingChatModel;

/**
 * A single-turn, tool-free LLM completion using the configured provider — for
 * quick AI Actions (rewrite, fix, …) that transform a piece of text. Blocks
 * until the turn finishes, so call it off the EDT. Throws if no provider is
 * configured (the caller surfaces guidance).
 */
public final class OneShot {

    private OneShot() {}

    public static OneShotResult complete(String systemPrompt, String userText) {
        return complete(systemPrompt, userText, null);
    }

    /**
     * Run one tool-free turn and return the transformed text together with the
     * provider/model that produced it and the turn's token usage.
     *
     * @param contextNote optional document/schema context prepended to the input so
     *                    the transform stays markup-correct (null/blank to omit)
     */
    public static OneShotResult complete(String systemPrompt, String userText, String contextNote) {
        ProviderConfig config = ProviderConfig.resolve(null, null, null, null);
        StreamingChatModel model = ProviderFactory.create(config);   // throws if no key
        Agent agent = new Agent(model, config, systemPrompt, new ToolRegistry());

        StringBuilder out = new StringBuilder();
        agent.subscribe(event -> {
            if (event instanceof AgentEvent.MessageUpdate u) {
                out.append(u.partialText());
            }
        });
        String input = (contextNote == null || contextNote.isBlank())
                ? userText : contextNote + "\n\n" + userText;
        agent.prompt(input);   // blocks until the (tool-free) turn completes

        UsageStats usage = agent.usageStats();   // configured for this provider/model
        return new OneShotResult(
                out.toString().strip(),
                config.provider(),
                config.model(),
                usage.inputTokens(),
                usage.outputTokens(),
                usage.totalTokens(),
                usage.estimatedCostUsd());
    }
}
