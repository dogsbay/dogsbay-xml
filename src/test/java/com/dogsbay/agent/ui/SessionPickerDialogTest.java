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

package com.dogsbay.agent.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.xagent.session.SessionManager.SessionInfo;

/**
 * The picker's label is how a session is identified — the id is eight
 * characters of hex, which tells you nothing about which conversation it was.
 */
class SessionPickerDialogTest {

    private static SessionInfo session(String id, String preview) {
        return new SessionInfo(id, Instant.parse("2026-03-13T11:30:00Z"),
                "openai-codex", "gpt-5.6-sol", "/home/x/project", preview);
    }

    @Test
    void aLabelSaysWhenAndWhatItWasAbout() {
        String label = SessionPickerDialog.label(session("d1578bdb", "Fix the broken conrefs"));

        assertThat(label).contains("2026").contains("Fix the broken conrefs");
    }

    @Test
    void aSessionWithNothingSaidFallsBackToProviderAndModel() {
        // An empty conversation has no first user message to preview, and a
        // blank row in the list would be indistinguishable from any other.
        String label = SessionPickerDialog.label(session("d1578bdb", ""));

        assertThat(label).contains("openai-codex").contains("gpt-5.6-sol");
    }

    @Test
    void everySessionGetsARow() {
        var labels = SessionPickerDialog.labels(List.of(
                session("aaaaaaaa", "one"), session("bbbbbbbb", "two"), session("cccccccc", "three")));

        assertThat(labels).hasSize(3);
        assertThat(labels.get(2)).contains("three");
    }
}
