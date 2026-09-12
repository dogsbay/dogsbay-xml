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

package com.dogsbay.xml.dita;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

class DitaOtMessageTest {

    @Test
    void parsesCodeSeverityAndFileWithLine() {
        DitaOtMessage m = DitaOtMessage.parse(Level.ERROR,
            "[DOTJ012F][FATAL] Failed to parse the input file 'topics/installing.dita:22:5'.");

        assertThat(m.code()).isEqualTo("DOTJ012F");
        assertThat(m.severity()).isEqualTo("FATAL"); // inline tag wins over slf4j level
        assertThat(m.file()).isEqualTo("topics/installing.dita");
        assertThat(m.line()).isEqualTo(22);
        assertThat(m.column()).isEqualTo(5);
        assertThat(m.isError()).isTrue();
        assertThat(m.message()).doesNotContain("[DOTJ012F]").doesNotContain("[FATAL]");
        assertThat(m.message()).contains("Failed to parse");
    }

    @Test
    void fallsBackToSlf4jLevelWhenNoInlineSeverityTag() {
        DitaOtMessage m = DitaOtMessage.parse(Level.WARN,
            "[DOTX047W] Key 'product-name' is not defined.");

        assertThat(m.code()).isEqualTo("DOTX047W");
        assertThat(m.severity()).isEqualTo("WARN");
        assertThat(m.isError()).isFalse();
        assertThat(m.file()).isNull();
        assertThat(m.line()).isEqualTo(-1);
    }

    @Test
    void normalizesWarningTagToWarn() {
        DitaOtMessage m = DitaOtMessage.parse(Level.INFO, "[WARNING] something minor in guide.ditamap");
        assertThat(m.severity()).isEqualTo("WARN");
        assertThat(m.file()).isEqualTo("guide.ditamap");
    }

    @Test
    void extractsFileWithoutLineNumber() {
        DitaOtMessage m = DitaOtMessage.parse(Level.ERROR,
            "[DOTX031E][ERROR] The href 'topics/missing.dita' cannot be resolved.");
        assertThat(m.file()).isEqualTo("topics/missing.dita");
        assertThat(m.line()).isEqualTo(-1);
        assertThat(m.column()).isEqualTo(-1);
    }

    @Test
    void handlesPlainMessageWithNoCodeOrFile() {
        DitaOtMessage m = DitaOtMessage.parse(Level.ERROR, "Processing terminated.");
        assertThat(m.code()).isNull();
        assertThat(m.severity()).isEqualTo("ERROR");
        assertThat(m.file()).isNull();
        assertThat(m.message()).isEqualTo("Processing terminated.");
    }

    @Test
    void cleansLeadingLocationAndTagsFromRealDitaOtMessage() {
        // The shape DITA-OT actually emits for a DTD parse error.
        DitaOtMessage m = DitaOtMessage.parse(Level.ERROR,
            "file:/x/installing-audacity.dita:10:10: [DOTJ088E][ERROR] "
            + "XML parsing error: Element type \"x\" must be declared.");

        assertThat(m.code()).isEqualTo("DOTJ088E");
        assertThat(m.severity()).isEqualTo("ERROR");
        assertThat(m.file()).isEqualTo("file:/x/installing-audacity.dita");
        assertThat(m.line()).isEqualTo(10);
        assertThat(m.column()).isEqualTo(10);
        assertThat(m.message())
            .isEqualTo("XML parsing error: Element type \"x\" must be declared.");
    }

    @Test
    void cleansLeadingColonAfterTags() {
        DitaOtMessage m = DitaOtMessage.parse(Level.ERROR,
            "file:/x/removing.dita:12:28: [DOTX010E][ERROR]: Unable to find the @conref target.");
        assertThat(m.code()).isEqualTo("DOTX010E");
        assertThat(m.message()).isEqualTo("Unable to find the @conref target.");
    }

    @Test
    void absoluteDitavalPathIsRecognized() {
        DitaOtMessage m = DitaOtMessage.parse(Level.WARN,
            "[DOTJ031I] filter /home/u/proj/filters/mac-beginner.ditaval applied");
        assertThat(m.file()).isEqualTo("/home/u/proj/filters/mac-beginner.ditaval");
    }
}
