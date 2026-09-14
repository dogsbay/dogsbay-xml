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
package com.dogsbay.dogsbayaieditor.plugin.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class HostedBriefTest {

    @Test
    void tellsTheAgentWhatValidateMeansAndToPreferEditorTools() {
        String b = HostedBrief.text(Path.of("/proj"), Path.of("/proj/a.dita"), true);
        assertThat(b).contains(Path.of("/proj").toString())
                .contains("Active document: " + Path.of("/proj/a.dita"));
        assertThat(b).contains("validate_document").contains("schematron_project").contains("check_links");
        assertThat(b).contains("Prefer them to a shell").contains("\"dogsbay-editor\"");
        assertThat(b).contains("read it in the same session");
        assertThat(b.length()).as("short enough to prepend to every first turn").isLessThan(1900);
    }

    @Test
    void aReplayedFirstTurnShowsOnlyTheUsersWords() {
        String prompt = HostedBrief.text(Path.of("/proj"), null, true) + "Read a.dita and summarise it";
        assertThat(HostedBrief.strip(prompt)).isEqualTo("Read a.dita and summarise it");
        assertThat(HostedBrief.strip("plain question")).isEqualTo("plain question");
        assertThat(HostedBrief.strip(null)).isNull();
    }

    @Test
    void saysSoWhenToolsAreMissing() {
        String b = HostedBrief.text(null, null, false);
        assertThat(b).contains("not available").doesNotContain("Active document");
    }
}
