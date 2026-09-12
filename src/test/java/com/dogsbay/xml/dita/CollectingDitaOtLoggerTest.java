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

class CollectingDitaOtLoggerTest {

    @Test
    void capturesErrorsAndWarningsButNotInfoOrDebug() {
        CollectingDitaOtLogger log = new CollectingDitaOtLogger();

        log.info("[DOTJ031I] chatty progress message");
        log.debug("internal detail");
        log.warn("[DOTX047W] Key 'x' is not defined.");
        log.error("[DOTJ012F][FATAL] Failed to parse 'a.dita'.");

        assertThat(log.messages()).hasSize(2);
        assertThat(log.messages()).extracting(DitaOtMessage::severity)
            .containsExactly("WARN", "FATAL");
    }

    @Test
    void formatsSlf4jPlaceholders() {
        CollectingDitaOtLogger log = new CollectingDitaOtLogger();
        log.error("[DOTX031E][ERROR] href '{}' cannot be resolved in {}", "missing.dita", "guide.ditamap");

        assertThat(log.messages()).singleElement().satisfies(m -> {
            assertThat(m.code()).isEqualTo("DOTX031E");
            assertThat(m.message()).contains("missing.dita").contains("guide.ditamap");
            assertThat(m.file()).isEqualTo("missing.dita"); // first DITA path wins
        });
    }

    @Test
    void enabledFlagsLetWarnAndErrorThrough() {
        CollectingDitaOtLogger log = new CollectingDitaOtLogger();
        assertThat(log.isErrorEnabled()).isTrue();
        assertThat(log.isWarnEnabled()).isTrue();
        assertThat(log.isInfoEnabled()).isFalse();
        assertThat(log.isDebugEnabled()).isFalse();
    }
}
