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

package com.dogsbay.dogsbayaieditor.commands;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Replacing one exact piece of a document (edit_document). */
class TextEditTest {

    private static final String TOPIC = """
            <task id="t">
              <title>Install</title>
              <taskbody>
                <steps>
                  <step><cmd>Run the installer.</cmd></step>
                  <step><cmd>Run the installer.</cmd></step>
                </steps>
              </taskbody>
            </task>
            """;

    @Test
    void replacesTheOnlyOccurrence() {
        TextEdit.Result result = TextEdit.apply(TOPIC, "<title>Install</title>", "<title>Installing</title>", false);

        assertThat(result.applied()).isTrue();
        assertThat(result.occurrences()).isEqualTo(1);
        assertThat(result.text()).contains("<title>Installing</title>").contains("Run the installer.");
    }

    @Test
    void refusesTextThatAppearsMoreThanOnceAndSaysWhatToDo() {
        TextEdit.Result result = TextEdit.apply(TOPIC, "<cmd>Run the installer.</cmd>", "<cmd>Run setup.</cmd>", false);

        assertThat(result.applied()).isFalse();
        assertThat(result.occurrences()).isEqualTo(2);
        assertThat(result.error()).contains("appears 2 times").contains("all=true");
        assertThat(result.text()).isEqualTo(TOPIC);
    }

    @Test
    void replacesEveryOccurrenceWhenAsked() {
        TextEdit.Result result = TextEdit.apply(TOPIC, "Run the installer.", "Run setup.", true);

        assertThat(result.applied()).isTrue();
        assertThat(result.occurrences()).isEqualTo(2);
        assertThat(result.text()).doesNotContain("Run the installer.");
    }

    @Test
    void changesNothingWhenTheTextIsNotThere() {
        TextEdit.Result result = TextEdit.apply(TOPIC, "<title>install</title>", "x", false);

        assertThat(result.applied()).isFalse();
        assertThat(result.error()).contains("must match exactly");
        assertThat(result.text()).isEqualTo(TOPIC);
    }

    @Test
    void deletesTheTextWithAnEmptyReplacement() {
        TextEdit.Result deleted = TextEdit.apply(TOPIC, "  <title>Install</title>\n", "", false);
        assertThat(deleted.text()).doesNotContain("Install");

        assertThat(TextEdit.apply(TOPIC, "", "x", false).error()).contains("must not be empty");
    }
}
