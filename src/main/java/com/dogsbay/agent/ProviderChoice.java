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

package com.dogsbay.agent;

/**
 * A provider selection from the chat UI's provider bar. {@code apiKey} may be
 * blank/null for providers that need none (Ollama) or that authenticate out of
 * band (ChatGPT/Codex OAuth). {@code remember} requests that the key be persisted
 * to {@code ~/.xagent/settings.json} (opt-in); when false the key applies only to
 * the current session.
 */
public record ProviderChoice(String provider, String apiKey, boolean remember) {

    /** Convenience: a choice that does not persist the key (session-only). */
    public ProviderChoice(String provider, String apiKey) {
        this(provider, apiKey, false);
    }

    public boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
