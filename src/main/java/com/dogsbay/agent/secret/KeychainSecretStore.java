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

import com.github.javakeyring.Keyring;

/**
 * {@link SecretStore} backed by the OS credential store via java-keyring
 * (macOS Keychain, Windows Credential Store, Linux libsecret). A fresh
 * {@link Keyring} is created per operation so the backend is detected each time
 * and nothing is held open. {@code get}/{@code delete} are best-effort (no
 * throw); {@code set} propagates failure so the caller can fall back to opt-in
 * plaintext when no keychain is available.
 */
public final class KeychainSecretStore implements SecretStore {

    private static final String SERVICE = "dogsbay-agent";

    @Override
    public Optional<String> get(String account) {
        try (Keyring keyring = Keyring.create()) {
            return Optional.ofNullable(keyring.getPassword(SERVICE, account));
        } catch (Exception e) {
            // no backend, or no entry for this account
            return Optional.empty();
        }
    }

    @Override
    public void set(String account, String secret) throws Exception {
        try (Keyring keyring = Keyring.create()) {
            keyring.setPassword(SERVICE, account, secret);
        }
    }

    @Override
    public void delete(String account) {
        try (Keyring keyring = Keyring.create()) {
            keyring.deletePassword(SERVICE, account);
        } catch (Exception ignore) {
            // best-effort
        }
    }
}
