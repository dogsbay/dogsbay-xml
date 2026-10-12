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

package com.dogsbay.xml.editor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** P5: the attribute-value completion provider registry. */
class AttributeValueContributorsTest {

    @AfterEach
    void cleanup() {
        AttributeValueContributors.clear();
    }

    @Test
    void noProvidersYieldsEmpty() {
        assertThat(AttributeValueContributors.collect("step", "platform", null, null)).isEmpty();
    }

    @Test
    void collectsAndUnionsProviderValues() {
        AttributeValueContributors.register((el, attr, f, pid) ->
                "platform".equals(attr) ? Set.of("windows", "mac") : Set.of());
        AttributeValueContributors.register((el, attr, f, pid) ->
                "platform".equals(attr) ? Set.of("mac", "linux") : Set.of());

        assertThat(AttributeValueContributors.collect("step", "platform", null, null))
            .containsExactlyInAnyOrder("windows", "mac", "linux"); // union, de-duped
        assertThat(AttributeValueContributors.collect("step", "audience", null, null)).isEmpty();
    }

    @Test
    void nullAttributeNameIsSafe() {
        AttributeValueContributors.register((el, attr, f, pid) -> Set.of("x"));
        assertThat(AttributeValueContributors.collect("step", null, null, null)).isEmpty();
    }

    @Test
    void failingProviderIsIsolated() {
        AttributeValueContributors.register((el, attr, f, pid) -> { throw new RuntimeException("boom"); });
        AttributeValueContributors.register((el, attr, f, pid) -> Set.of("mac"));
        assertThat(AttributeValueContributors.collect("step", "platform", null, null))
            .containsExactly("mac");
    }

    @Test
    void providerReceivesDocTypePublicIdForRouting() {
        // A provider can gate on the DOCTYPE rather than guess from the extension.
        AttributeValueContributors.register((el, attr, f, pid) ->
                (pid != null && pid.contains("DITA")) ? Set.of("mac") : Set.of());

        assertThat(AttributeValueContributors.collect(
                "step", "platform", null, "-//OASIS//DTD DITA Topic//EN")).containsExactly("mac");
        assertThat(AttributeValueContributors.collect(
                "step", "platform", null, "-//Some//Other DTD//EN")).isEmpty();
    }

    @Test
    void unregisterStopsContributing() {
        AttributeValueContributors.ValueProvider p = (el, attr, f, pid) -> Set.of("mac");
        AttributeValueContributors.register(p);
        assertThat(AttributeValueContributors.collect("s", "platform", null, null)).containsExactly("mac");
        AttributeValueContributors.unregister(p);
        assertThat(AttributeValueContributors.collect("s", "platform", null, null)).isEmpty();
    }
}
