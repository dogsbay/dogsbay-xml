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

package com.dogsbay.dogsbayaieditor.update;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Version parsing/comparison used by the startup update check (no network here).
 */
class UpdateCheckerTest {

    @Test
    void detectsNewerVersion() {
        assertThat(new Version("3.4.0").isNewerThan(new Version("3.3.1"))).isTrue();
        assertThat(new Version("4.0.0").isNewerThan(new Version("3.9.9"))).isTrue();
        assertThat(new Version("3.3.2").isNewerThan(new Version("3.3.1"))).isTrue();
    }

    @Test
    void detectsOlderOrEqualVersion() {
        assertThat(new Version("3.3.1").isNewerThan(new Version("3.3.1"))).isFalse();
        assertThat(new Version("3.3.0").isNewerThan(new Version("3.3.1"))).isFalse();
        assertThat(new Version("2.9.9").isNewerThan(new Version("3.0.0"))).isFalse();
    }

    @Test
    void toleratesVPrefixAndPreReleaseSuffix() {
        assertThat(new Version("v4.0.0").isNewerThan(new Version("3.9.9"))).isTrue();
        // "4.0.0-beta.1" must parse as 4.0.0, newer than 3.x.
        assertThat(new Version("4.0.0-beta.1").isNewerThan(new Version("3.3.1"))).isTrue();
        // Equal core versions ignoring the pre-release suffix.
        assertThat(new Version("4.0.0-beta.1").isNewerThan(new Version("4.0.0"))).isFalse();
    }

    @Test
    void toleratesMalformedInput() {
        // Garbage parses to 0.0.0 rather than throwing.
        assertThat(new Version("not-a-version").isNewerThan(new Version("1.0.0"))).isFalse();
        assertThat(new Version("1.0.0").isNewerThan(new Version("garbage"))).isTrue();
    }
}
