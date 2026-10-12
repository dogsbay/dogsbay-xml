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
    void capturesErrorsWarningsAndCodedNotes() {
        CollectingDitaOtLogger log = new CollectingDitaOtLogger();

        log.info("Processing /tmp/x/guide.ditamap");   // narration
        log.debug("internal detail");
        log.info("[DOTJ047I] File 'orphan.dita' is not included in any map.");
        log.warn("[DOTX047W] Key 'x' is not defined.");
        log.error("[DOTJ012F][FATAL] Failed to parse 'a.dita'.");

        // A coded INFO message is a diagnostic in everything but severity — DOTJ047I is the one a reader is
        // sent looking for — and it is documented and searchable by that code. The narration is not.
        assertThat(log.messages()).extracting(DitaOtMessage::severity)
            .containsExactly("INFO", "WARN", "FATAL");
        assertThat(log.messages()).extracting(DitaOtMessage::code)
            .containsExactly("DOTJ047I", "DOTX047W", "DOTJ012F");
    }

    @Test
    void theSameThingSaidTwiceIsKeptOnce() {
        CollectingDitaOtLogger log = new CollectingDitaOtLogger();

        // DITA-OT reports this once per element it filtered. A reader looking for the one DOTJ047I line
        // should not have to scan past nine copies of it.
        log.info("[DOTJ031I] No rule for 'audience=podcaster' was found in the DITAVAL file.");
        log.info("[DOTJ031I] No rule for 'audience=podcaster' was found in the DITAVAL file.");
        log.info("[DOTJ031I] No rule for 'audience=podcaster' was found in the DITAVAL file.");
        log.warn("[DOTX047W] Key 'x' is not defined. topics/a.dita:4");
        log.warn("[DOTX047W] Key 'x' is not defined. topics/b.dita:4");

        // The two warnings differ by file, so both stand: two files with the same problem are two problems.
        assertThat(log.messages()).hasSize(3);
        assertThat(log.messages()).extracting(DitaOtMessage::file)
            .containsExactly(null, "topics/a.dita", "topics/b.dita");
    }

    @Test
    void narrationIsTurnedAwayWithoutParsingIt() {
        CollectingDitaOtLogger log = new CollectingDitaOtLogger();

        // A publish logs thousands of these. None is kept, and none is parsed to find that out.
        log.info("Processing /tmp/build/guide.ditamap");
        log.info("[DEBUG] not a DITA-OT code");
        log.info("short");
        log.info((String) null);

        assertThat(log.messages()).isEmpty();
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
    void enabledFlagsLetEverythingWeFilterOurselvesThrough() {
        CollectingDitaOtLogger log = new CollectingDitaOtLogger();
        assertThat(log.isErrorEnabled()).isTrue();
        assertThat(log.isWarnEnabled()).isTrue();
        // On: DITA-OT's LoggerListener asks before it offers a message, and a "no" here loses the coded
        // INFO messages before the filter downstream ever sees them.
        assertThat(log.isInfoEnabled()).isTrue();
        assertThat(log.isDebugEnabled()).isFalse();
    }
}
