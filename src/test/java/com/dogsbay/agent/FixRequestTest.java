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

package com.dogsbay.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixRequestTest {

    @Test
    @DisplayName("prompt is focused on the single error and asks to validate")
    void focusedPrompt() {
        String prompt = new FixRequest("install.dita", 14,
                "element \"p\" not allowed here", "expected \"title\"").toPrompt();
        assertThat(prompt)
                .startsWith("Fix this validation error and nothing else.")
                .contains("File: install.dita")
                .contains("Line: 14")
                .contains("Error: element \"p\" not allowed here")
                .contains("Allowed/expected here: expected \"title\"")
                .contains("validate to confirm");
    }

    @Test
    @DisplayName("optional fields are omitted when absent")
    void omitsOptionalFields() {
        String prompt = new FixRequest(null, 0, "keyref unresolved", null).toPrompt();
        assertThat(prompt)
                .contains("Error: keyref unresolved")
                .doesNotContain("File:")
                .doesNotContain("Line:")
                .doesNotContain("Allowed/expected");
    }
}
