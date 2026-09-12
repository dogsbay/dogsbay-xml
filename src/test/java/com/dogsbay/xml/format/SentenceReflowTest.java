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

package com.dogsbay.xml.format;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SentenceReflowTest {

    @Test
    @DisplayName("breaks after each sentence terminator")
    void splitsSentences() {
        assertThat(SentenceReflow.reflow("First sentence. Second one! Third? Done."))
                .isEqualTo("First sentence.\nSecond one!\nThird?\nDone.");
    }

    @Test
    @DisplayName("abbreviations are not treated as sentence ends")
    void keepsAbbreviations() {
        assertThat(SentenceReflow.reflow("Use a tool, e.g. Oxygen, to edit it. Then save."))
                .isEqualTo("Use a tool, e.g. Oxygen, to edit it.\nThen save.");
        assertThat(SentenceReflow.reflow("Dr. Smith wrote it. He is right."))
                .isEqualTo("Dr. Smith wrote it.\nHe is right.");
    }

    @Test
    @DisplayName("decimals and version numbers are not split")
    void keepsDecimals() {
        assertThat(SentenceReflow.reflow("Version 3.4 is out. Upgrade now."))
                .isEqualTo("Version 3.4 is out.\nUpgrade now.");
    }

    @Test
    @DisplayName("a sentence starting with an inline tag is recognized")
    void breaksBeforeInlineTag() {
        assertThat(SentenceReflow.reflow("Open it. <uicontrol>Save</uicontrol> the file."))
                .isEqualTo("Open it.\n<uicontrol>Save</uicontrol> the file.");
    }

    @Test
    @DisplayName("lower-case continuation is not a boundary")
    void noBreakMidSentence() {
        assertThat(SentenceReflow.reflow("see fig. below for details"))
                .isEqualTo("see fig. below for details");
    }

    @Test
    @DisplayName("null/blank is returned unchanged")
    void blankUnchanged() {
        assertThat(SentenceReflow.reflow(null)).isNull();
        assertThat(SentenceReflow.reflow("   ")).isEqualTo("   ");
    }
}
