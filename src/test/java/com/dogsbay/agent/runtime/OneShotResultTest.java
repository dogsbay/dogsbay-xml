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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OneShotResultTest {

    @Test
    @DisplayName("subscription/local providers show 'included', not a dollar amount")
    void includedProviders() {
        assertThat(new OneShotResult("x", "openai-codex", "gpt-5.4", 1000, 234, 1234, 0.0).summary())
                .isEqualTo("openai-codex · gpt-5.4 · 1,234 tokens · included");
        assertThat(new OneShotResult("x", "ollama", "qwen3", 500, 100, 600, 0.0).summary())
                .isEqualTo("ollama · qwen3 · 600 tokens · included")
                .doesNotContain("$");
    }

    @Test
    @DisplayName("priced providers show the estimated cost")
    void pricedProvider() {
        String s = new OneShotResult("x", "anthropic", "claude-sonnet-4-6", 1000, 200, 1200, 0.0042)
                .summary();
        assertThat(s)
                .startsWith("anthropic · claude-sonnet-4-6")
                .contains("1,200 tokens")
                .contains("$0.0042");
    }

    @Test
    @DisplayName("two-decimal cost format for amounts over a cent")
    void largerCost() {
        assertThat(new OneShotResult("x", "openai", "gpt-5.4", 100000, 20000, 120000, 0.45).summary())
                .endsWith("$0.45");
    }

    @Test
    @DisplayName("no usage reported → just provider · model")
    void noUsage() {
        assertThat(new OneShotResult("x", "openai", "gpt-5.4", 0, 0, 0, 0.0).summary())
                .isEqualTo("openai · gpt-5.4");
    }
}
