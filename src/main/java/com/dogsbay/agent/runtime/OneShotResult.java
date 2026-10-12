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

import java.util.Locale;

/**
 * The outcome of a {@link OneShot} completion: the transformed text plus the
 * provider/model that produced it and the turn's token usage, so callers can
 * show which model ran and what it cost.
 */
public record OneShotResult(
        String text,
        String provider,
        String model,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        double estimatedCostUsd) {

    /** Providers billed by subscription / run locally — no marginal token cost. */
    private static boolean isIncluded(String provider) {
        return "openai-codex".equals(provider) || "ollama".equals(provider);
    }

    /**
     * One-line summary for a dialog footer, e.g.
     * {@code "openai-codex · gpt-5.4 · 1,234 tokens · included"} or
     * {@code "anthropic · claude-sonnet-4-6 · 1,200 tokens · $0.0042"}. When no
     * usage was reported, just {@code "provider · model"}.
     */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(provider != null && !provider.isBlank() ? provider : "unknown provider");
        if (model != null && !model.isBlank()) {
            sb.append(" · ").append(model);
        }
        if (totalTokens > 0) {
            sb.append(" · ").append(String.format(Locale.US, "%,d tokens", totalTokens));
            if (isIncluded(provider)) {
                sb.append(" · included");
            } else if (estimatedCostUsd > 0) {
                sb.append(estimatedCostUsd < 0.01
                        ? String.format(Locale.US, " · $%.4f", estimatedCostUsd)
                        : String.format(Locale.US, " · $%.2f", estimatedCostUsd));
            }
        }
        return sb.toString();
    }
}
