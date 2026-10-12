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

package com.dogsbay.dogsbayaieditor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The House Style panel must not reset the fields it does not show.
 *
 * <p>{@code FormatStyle} has two fields with no control in Preferences — {@code sortAttributes} and
 * the {@code preserve-space} element list. They were read back from {@code FormatStyle.defaults()},
 * so one OK replaced a user's own default with the built-in one, and "Save to project" wrote that
 * over a team's {@code .dogsbay/config.xml}. Because the writer omits {@code sort-attributes} at its
 * default, a project's {@code sort-attributes="true"} did not flip to false — the line disappeared,
 * which reads as a deliberate deletion.
 *
 * <p>This is a <b>source guard</b>, not a behavioural test: the round trip needs a constructed
 * dialog, which needs a running editor. It encodes the specific mistake so it cannot be made again
 * silently, in the same shape as {@code TextToBytesGuardTest}. A real round-trip test wants
 * {@code houseStyleFromControls}'s logic extracted from the dialog first.
 */
class HouseStyleRoundTripGuardTest {

    @Test
    @DisplayName("the fields with no control are carried from the loaded style, not from defaults")
    void unexposedFieldsAreNotReadFromDefaults() throws Exception {
        Path dialog = Path.of("src/main/java/com/dogsbay/dogsbayaieditor/PreferencesDialog.java");
        // Fails loudly rather than skipping: a guard that silently tests nothing is worse
        // than no guard. (The sibling guard in SaveLossGuardTest does the same.)
        assertThat(Files.exists(dialog)).as("sources must be readable from the test working directory").isTrue();
        String source = Files.readString(dialog, StandardCharsets.UTF_8);

        int from = source.indexOf("private com.dogsbay.xml.format.FormatStyle houseStyleFromControls()");
        assertThat(from).as("houseStyleFromControls must exist").isGreaterThan(0);
        String body = source.substring(from, source.indexOf("\n\t}", from));

        // `d` is FormatStyle.defaults() in that method. These two fields must not come from it.
        assertThat(body)
                .as("preserve-space must come from the loaded style; reading it from defaults() "
                        + "silently empties a project's list on OK")
                .doesNotContain(" d.preserveSpaceElements()", ", d.preserveSpaceElements()");
        assertThat(body)
                .as("sortAttributes must come from the loaded style; reading it from defaults() "
                        + "silently turns a project's setting off, and the writer then omits the "
                        + "attribute entirely so the line vanishes from config.xml")
                .doesNotContain(" d.sortAttributes()", ", d.sortAttributes()");

        // and the style the panel was populated with has to be remembered for that to be possible
        assertThat(source)
                .as("houseStyleToControls must record the style it was given")
                .contains("fsSourceStyle = s;");
    }
}
