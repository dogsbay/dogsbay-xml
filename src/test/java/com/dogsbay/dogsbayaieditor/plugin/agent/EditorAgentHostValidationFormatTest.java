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

import java.util.ArrayList;
import java.util.List;

import com.dogsbay.xml.XMLError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EditorAgentHostValidationFormatTest {

    private static XMLError error(int line, String message) {
        return new XMLError("topic.dita", line, 1, XMLError.ERROR, message);
    }

    @Test
    @DisplayName("no errors → a clean confirmation line")
    void clean() {
        assertThat(EditorAgentHost.formatValidation("topic.dita", List.of()))
                .isEqualTo("validation: topic.dita — 0 errors");
    }

    @Test
    @DisplayName("errors are listed with line + message")
    void listsErrors() {
        String out = EditorAgentHost.formatValidation("topic.dita",
                List.of(error(14, "element \"p\" not allowed here"),
                        error(31, "keyref \"setup\" unresolved")));
        assertThat(out)
                .contains("topic.dita — 2 error(s)")
                .contains("line 14: element \"p\" not allowed here")
                .contains("line 31: keyref \"setup\" unresolved");
    }

    @Test
    @DisplayName("DITA is detected from the @class architecture marker, not any class attribute")
    void ditaClassDetection() {
        assertThat(EditorAgentHost.isDitaClass("- topic/topic concept/concept ")).isTrue();
        assertThat(EditorAgentHost.isDitaClass("+ topic/p hi-d/b ")).isTrue();
        // XHTML/SVG-style class attributes are NOT DITA
        assertThat(EditorAgentHost.isDitaClass("note important")).isFalse();
        assertThat(EditorAgentHost.isDitaClass("col-md-6")).isFalse();
        assertThat(EditorAgentHost.isDitaClass(null)).isFalse();
        assertThat(EditorAgentHost.isDitaClass("")).isFalse();
    }

    @Test
    @DisplayName("a long error list is capped at five with a summary")
    void capsAtFive() {
        List<XMLError> many = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            many.add(error(i, "problem " + i));
        }
        String out = EditorAgentHost.formatValidation("topic.dita", many);
        assertThat(out)
                .contains("— 9 error(s)")
                .contains("line 5: problem 5")
                .contains("…+4 more")
                .doesNotContain("problem 6");
    }
}
