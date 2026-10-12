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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FOP's warnings are counted, not shouted.
 *
 * <p>A book of any size produces dozens — a glyph missing from the font, a line overflowing its column, a table FOP
 * lays out its own way — and they arrived in the middle of a command-line check, ahead of the report someone was
 * waiting for.
 */
class FopWarningsTest {

    private static final Logger FOP = LoggerFactory.getLogger("org.apache.fop.apps.FOUserAgent");
    /** The two classes that read font files, and are loud about what they cannot read in them. */
    private static final Logger OPEN_FONT = LoggerFactory.getLogger("org.apache.fop.fonts.truetype.OpenFont");
    private static final Logger GLYPH_CLASS_TABLE = LoggerFactory.getLogger(
            "org.apache.fop.complexscripts.fonts.GlyphClassTable$CoverageSetClassTable");
    /** Where FOP's own events arrive, whatever they are about. */
    private static final Logger EVENTS = LoggerFactory.getLogger(
            "org.apache.fop.events.LoggingEventListener");

    @Test
    void aQuietBuildSaysNothing() {
        try (FopWarnings warnings = FopWarnings.collect()) {
            assertThat(warnings).isNotNull();
            assertThat(warnings.summary()).isNull();
        }
    }

    @Test
    void oneGlyphWarningPerCharacterIsOneKind() {
        try (FopWarnings warnings = FopWarnings.collect()) {
            for (String glyph : new String[] { "−", "ש", "ל", "ו", "ם", "→" }) {
                FOP.warn("Glyph \"{}\" (0x5e9, afii57689) not available in font \"Times-Roman\".", glyph);
            }

            String summary = warnings.summary();

            // forty characters missing from one font is one problem, not forty
            assertThat(summary).contains("6 warnings").contains("6 Glyph");
            assertThat(summary).doesNotContain("afii57689");
        }
    }

    @Test
    void differentKindsAreCountedApart() {
        try (FopWarnings warnings = FopWarnings.collect()) {
            FOP.warn("Glyph \"−\" (0x2212, minus) not available in font \"Times-Roman\".");
            FOP.warn("Glyph \"→\" (0x2192, arrowright) not available in font \"Times-Roman\".");
            FOP.warn("The contents of fo:block line 1 exceed the available area");
            FOP.warn("table-layout=\"auto\" is not supported, using fixed!");

            String summary = warnings.summary();

            assertThat(summary).startsWith("PDF rendering reported 4 warnings");
            assertThat(summary).contains("2 Glyph");
        }
    }

    @Test
    void moreKindsThanItNamesAreCountedAsTheRest() {
        try (FopWarnings warnings = FopWarnings.collect()) {
            for (int i = 0; i < 5; i++) {
                FOP.warn("A distinct kind of trouble number " + (char) ('a' + i));
            }

            assertThat(warnings.summary()).contains("other kind");
        }
    }

    @Test
    void whatTheFontsSayIsCountedApartFromWhatTheDocumentSays() {
        // The first PDF build on a machine parses every auto-detected font and reports what it cannot read;
        // ~/.fop/fop-fonts.cache answers for every later build and they never appear again. So one deliverable
        // reported 84 warnings in the editor (which ran first) and 11 on the command line, and the tutorial
        // could not print a number (reported 2026-09-30). They are about the machine, not the document.
        try (FopWarnings warnings = FopWarnings.collect()) {
            FOP.warn("The contents of fo:block line 1 exceed the available area");
            for (int i = 0; i < 57; i++) {
                GLYPH_CLASS_TABLE.warn("coverage set class table not yet supported");
            }
            for (int i = 0; i < 11; i++) {
                OPEN_FONT.warn("Encountered format constraint violation in advanced (typographic) table (AT)"
                        + " in font 'NotoSansHebrew'");
            }
            // FOP raises these two as events, so they are logged by its event listener and not by a font
            // class — the words are all there is to go on, and they are what still differed between a cold
            // and a warm font cache once the parsing warnings were out of the way.
            EVENTS.warn("Unable to load font file: file:/usr/share/fonts/Cantarell-VF.otf. Reason: null");
            EVENTS.warn("The font directory /usr/share/fonts/nowhere could not be found.");

            String summary = warnings.summary();

            assertThat(summary).startsWith("PDF rendering reported 1 warning (1 The contents of");
            assertThat(summary).contains("read 70 warnings from fonts installed on this machine");
            // the count that is supposed to be about the document must not move with the font cache
            assertThat(summary).doesNotContain("coverage set class table");
        }
    }

    @Test
    void aGlyphMissingForTheDocumentsTextIsTheDocumentsProblem() {
        // These three sit directly in org.apache.fop.fonts and log through commons-logging, not through the
        // event listener — and every one of them is about a character in the reader's topic that the PDF will
        // render as '#'. Taking the whole package as "about the machine" declared such a book clean.
        try (FopWarnings warnings = FopWarnings.collect()) {
            LoggerFactory.getLogger("org.apache.fop.fonts.Typeface")
                    .warn("Glyph 8212 (0x2014, emdash) not available in font \"Times-Roman\".");
            LoggerFactory.getLogger("org.apache.fop.fonts.MultiByteFont")
                    .warn("Unable to map glyph index 42 to Unicode scalar in font 'NotoSansHebrew',"
                            + " substituting missing character '#'");
            LoggerFactory.getLogger("org.apache.fop.fonts.Font")
                    .warn("Glyph ? not available in font Helvetica");

            assertThat(warnings.summary())
                    .startsWith("PDF rendering reported 3 warnings")
                    .doesNotContain("installed on this machine");
        }
    }

    @Test
    void aMissingGlyphIsStillTheDocumentsProblem() {
        // Four of FOP's six font events are about the document — the character it could not draw is in the
        // reader's topic — and only the two auto-detection ones are about the machine.
        try (FopWarnings warnings = FopWarnings.collect()) {
            EVENTS.warn("Glyph \"\u2212\" (0x2212, minus) not available in font \"Times-Roman\".");
            EVENTS.warn("Font \"Symbol,normal,700\" not found. Substituting with \"Symbol,normal,400\".");

            assertThat(warnings.summary())
                    .startsWith("PDF rendering reported 2 warnings")
                    .doesNotContain("installed on this machine");
        }
    }

    @Test
    void aBuildWhoseOnlyTroubleIsTheFontsSaysSoAndNothingElse() {
        try (FopWarnings warnings = FopWarnings.collect()) {
            OPEN_FONT.warn("coverage set class table not yet supported");

            assertThat(warnings.summary())
                    .isEqualTo("PDF rendering read 1 warning from fonts installed on this machine");
        }
    }

    @Test
    void itPutsTheLoggerBackAfterwards() {
        ch.qos.logback.classic.Logger fop =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.apache.fop");
        boolean additiveBefore = fop.isAdditive();

        try (FopWarnings warnings = FopWarnings.collect()) {
            assertThat(fop.isAdditive()).as("ours for the length of the build").isFalse();
        }

        // a build must not leave the logger detached for everything that runs after it
        assertThat(fop.isAdditive()).isEqualTo(additiveBefore);
    }
}
