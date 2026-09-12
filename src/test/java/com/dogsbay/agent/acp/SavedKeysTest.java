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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.dogsbay.agent.secret.SecretStore;

class SavedKeysTest {

    static final class MemoryStore implements SecretStore {
        final Map<String, String> m = new HashMap<>();
        @Override public Optional<String> get(String a) { return Optional.ofNullable(m.get(a)); }
        @Override public void set(String a, String s) { m.put(a, s); }
        @Override public void delete(String a) { m.remove(a); }
    }

    @Test
    void mapsVendorsToTheirVariables() {
        assertThat(SavedKeys.mappingFor("claude-acp")).contains(new SavedKeys.Mapping("anthropic", "ANTHROPIC_API_KEY"));
        assertThat(SavedKeys.mappingFor("codex-acp")).contains(new SavedKeys.Mapping("openai", "OPENAI_API_KEY"));
        assertThat(SavedKeys.mappingFor("gemini")).contains(new SavedKeys.Mapping("gemini", "GEMINI_API_KEY"));
        assertThat(SavedKeys.mappingFor("goose")).isEmpty();
        assertThat(SavedKeys.mappingFor(null)).isEmpty();
    }

    @Test
    void environmentCarriesTheSavedKeyOnlyWhenOneExists() throws Exception {
        MemoryStore store = new MemoryStore();
        assertThat(SavedKeys.savedKey(store, "codex-acp")).isEmpty();
        assertThat(SavedKeys.environment(store, "codex-acp")).isEmpty();

        store.set("openai", "sk-test");
        store.set("anthropic", "   ");
        assertThat(SavedKeys.savedKey(store, "codex-acp")).contains("sk-test");
        assertThat(SavedKeys.environment(store, "codex-acp")).containsExactly(Map.entry("OPENAI_API_KEY", "sk-test"));
        assertThat(SavedKeys.environment(store, "claude-acp")).as("blank keys do not count").isEmpty();
        assertThat(SavedKeys.environment(store, "goose")).isEmpty();
    }

    @Test
    void aBrokenKeychainIsTreatedAsNoKey() {
        SecretStore broken = new SecretStore() {
            @Override public Optional<String> get(String a) { throw new IllegalStateException("no keyring"); }
            @Override public void set(String a, String s) { }
            @Override public void delete(String a) { }
        };
        assertThat(SavedKeys.savedKey(broken, "claude-acp")).isEmpty();
    }
}
