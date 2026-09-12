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

package com.dogsbay.dogsbayaieditor.links;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** DITA specialization reporting: root, @class chain, @domains, doctype. */
class SpecializationScannerTest {

    @Test
    void readsClassChainAndDomains() {
        var spec = SpecializationScanner.scan("""
            <concept id="c" class="- topic/topic concept/concept "
                     domains="(topic hi-d) (topic ut-d)">
              <title>T</title>
            </concept>
            """);
        assertThat(spec.root()).isEqualTo("concept");
        assertThat(spec.classChain()).containsExactly("topic/topic", "concept/concept");
        assertThat(spec.isSpecialized()).isTrue();
        assertThat(spec.domains()).contains("(topic", "hi-d)");
    }

    @Test
    void parsesDoctypePublicAndSystemId() {
        var spec = SpecializationScanner.scan("""
            <?xml version="1.0"?>
            <!DOCTYPE concept PUBLIC "-//OASIS//DTD DITA Concept//EN" "concept.dtd">
            <concept id="c" class="- topic/topic concept/concept "><title>T</title></concept>
            """);
        assertThat(spec.publicId()).isEqualTo("-//OASIS//DTD DITA Concept//EN");
        assertThat(spec.systemId()).isEqualTo("concept.dtd");
    }

    @Test
    void baseTopicIsNotSpecialized() {
        var spec = SpecializationScanner.scan(
                "<topic id=\"t\" class=\"- topic/topic \"><title>T</title></topic>");
        assertThat(spec.root()).isEqualTo("topic");
        assertThat(spec.classChain()).containsExactly("topic/topic");
        assertThat(spec.isSpecialized()).isFalse();
    }
}
