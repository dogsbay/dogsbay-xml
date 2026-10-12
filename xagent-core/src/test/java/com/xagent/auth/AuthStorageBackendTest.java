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
package com.xagent.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * When a {@link AuthStorage.SecretBackend} is registered, credentials are stored
 * through it (e.g. an OS keychain) instead of auth.json, with all the normal
 * get/set/remove semantics preserved.
 */
class AuthStorageBackendTest {

    @AfterEach
    void resetBackend() {
        AuthStorage.useBackend(null);   // never leak the global into other tests
    }

    @Test
    void storesAndRetrievesThroughBackend() throws Exception {
        AtomicReference<String> blob = new AtomicReference<>();
        AuthStorage.useBackend(new AuthStorage.SecretBackend() {
            @Override public Optional<String> read() { return Optional.ofNullable(blob.get()); }
            @Override public void write(String json) { blob.set(json); }
        });

        AuthStorage storage = new AuthStorage();   // file path is ignored when a backend is set
        assertThat(storage.get("openai-codex")).isEmpty();

        storage.set("openai-codex", new OAuthCredentials("acc", "ref", 123L, "account-1"));

        // round-trips through the backend (the blob is what was persisted)
        assertThat(blob.get()).contains("openai-codex").contains("acc").contains("ref");
        Optional<OAuthCredentials> got = storage.get("openai-codex");
        assertThat(got).isPresent();
        assertThat(got.get().accessToken()).isEqualTo("acc");
        assertThat(got.get().refreshToken()).isEqualTo("ref");

        assertThat(storage.list()).containsKey("openai-codex");
        assertThat(storage.remove("openai-codex")).isTrue();
        assertThat(storage.get("openai-codex")).isEmpty();
    }
}
