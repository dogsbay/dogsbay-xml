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

package com.dogsbay.agent.secret;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The keychain auth backend adapts a {@link SecretStore} to xagent's
 * AuthStorage.SecretBackend. Verified against an in-memory store (no real
 * keychain needed) — read/write round-trips through one account.
 */
class KeychainAuthBackendTest {

    private static SecretStore inMemory(Map<String, String> map) {
        return new SecretStore() {
            @Override public Optional<String> get(String account) {
                return Optional.ofNullable(map.get(account));
            }
            @Override public void set(String account, String secret) {
                map.put(account, secret);
            }
            @Override public void delete(String account) {
                map.remove(account);
            }
        };
    }

    @Test
    void writeThenReadRoundTrips() throws Exception {
        Map<String, String> map = new HashMap<>();
        KeychainAuthBackend backend = new KeychainAuthBackend(inMemory(map));

        assertThat(backend.read()).isEmpty();
        backend.write("{\"openai-codex\":{\"access\":\"a\"}}");
        assertThat(backend.read().orElseThrow()).contains("openai-codex");
        assertThat(map).hasSize(1);   // stored under a single account
    }
}
