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
package com.dogsbay.agent.acp;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.dogsbay.agent.secret.SecretStore;

/**
 * Bridges the API keys the built-in agent keeps in the OS keychain to the
 * environment variable a hosted agent's own tool reads. Opt-in per session:
 * the key goes only into that vendor's process for its lifetime, never to
 * disk, and the panel says it was passed.
 */
public final class SavedKeys {

    /** Which keychain account feeds which variable for a registry agent. */
    public record Mapping(String provider, String envVar) {}

    private SavedKeys() {
    }

    /** The mapping for a registry id, or empty when the agent is not one of the key-based vendors. */
    public static Optional<Mapping> mappingFor(String registryId) {
        String id = registryId == null ? "" : registryId.toLowerCase(Locale.ROOT);
        if (id.contains("claude")) {
            return Optional.of(new Mapping("anthropic", "ANTHROPIC_API_KEY"));
        }
        if (id.contains("codex") || id.contains("openai")) {
            return Optional.of(new Mapping("openai", "OPENAI_API_KEY"));
        }
        if (id.contains("gemini")) {
            return Optional.of(new Mapping("gemini", "GEMINI_API_KEY"));
        }
        return Optional.empty();
    }

    /** The saved key for this agent's vendor, if the user stored one for the built-in agent. */
    public static Optional<String> savedKey(SecretStore store, String registryId) {
        return mappingFor(registryId).flatMap(m -> {
            try {
                return store.get(m.provider()).filter(k -> !k.isBlank());
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        });
    }

    /** The environment to add when the user opted in: the variable, or nothing. */
    public static Map<String, String> environment(SecretStore store, String registryId) {
        return mappingFor(registryId)
                .flatMap(m -> savedKey(store, registryId).map(k -> Map.of(m.envVar(), k)))
                .orElse(Map.of());
    }
}
