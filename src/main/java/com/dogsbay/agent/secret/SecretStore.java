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

import java.util.Optional;

/**
 * A small secret store for the agent's API keys, modelled on VS Code's
 * SecretStorage: keys live in the OS credential store, not in a plaintext
 * config file. {@code account} is the lookup key (the provider name).
 *
 * <p>{@link #get} never throws (returns empty when absent or no backend);
 * {@link #set} throws when no keychain backend is available, so callers can fall
 * back to opt-in plaintext.
 */
public interface SecretStore {

    Optional<String> get(String account);

    void set(String account, String secret) throws Exception;

    void delete(String account);

    /** The OS-keychain-backed store (macOS Keychain / Windows / libsecret). */
    static SecretStore keychain() {
        return new KeychainSecretStore();
    }
}
