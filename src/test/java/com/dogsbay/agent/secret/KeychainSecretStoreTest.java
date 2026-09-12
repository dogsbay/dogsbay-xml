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
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * The keychain store must degrade gracefully when there's no backend (headless
 * CI, no libsecret) and when an account is absent — get returns empty, delete is
 * a no-op, neither throws. (A real round-trip would prompt/needs a keyring, so
 * it's verified manually.)
 */
class KeychainSecretStoreTest {

    private static final String MISSING = "dogsbay-agent-test-nonexistent-account";

    @Test
    void getMissingReturnsEmptyWithoutThrowing() {
        SecretStore store = SecretStore.keychain();
        assertThat(store.get(MISSING)).isEmpty();
    }

    @Test
    void deleteMissingDoesNotThrow() {
        SecretStore store = SecretStore.keychain();
        assertThatCode(() -> store.delete(MISSING)).doesNotThrowAnyException();
    }
}
