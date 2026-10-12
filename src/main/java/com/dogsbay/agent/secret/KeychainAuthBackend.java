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

import java.io.IOException;
import java.util.Optional;

import com.xagent.auth.AuthStorage;

/**
 * Adapts the editor's {@link SecretStore} (OS keychain) to xagent's
 * {@link AuthStorage.SecretBackend}, so ChatGPT/Codex OAuth tokens are stored in
 * the keychain instead of {@code ~/.xagent/auth.json}. The whole credential map
 * is a single keychain entry.
 */
public final class KeychainAuthBackend implements AuthStorage.SecretBackend {

    private static final String ACCOUNT = "oauth-tokens";

    private final SecretStore store;

    public KeychainAuthBackend(SecretStore store) {
        this.store = store;
    }

    @Override
    public Optional<String> read() {
        return store.get(ACCOUNT);
    }

    @Override
    public void write(String json) throws IOException {
        try {
            store.set(ACCOUNT, json);
        } catch (Exception e) {
            throw new IOException("keychain write failed", e);
        }
    }
}
