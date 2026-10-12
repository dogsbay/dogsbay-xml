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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import javax.swing.JFrame;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;

/**
 * Choosing what a new document starts as.
 *
 * <p>Tagged for a display because the dialog is a real window. It earns the tag: the bug this covers was that OK closed
 * without recording the highlighted row, so picking a template and clicking OK produced a blank document, and the only paths
 * that worked were double-clicking and Enter.
 */
@Tag("ui")
class NewDocumentDialogTest {

    @TempDir
    Path dir;

    private JFrame frame;

    @AfterEach
    void close() {
        if (frame != null) {
            frame.dispose();
        }
    }

    private NewDocumentDialog dialog() throws Exception {
        XElement root = new XElement("dogsbay");
        root.setText("\n");
        ConfigurationProperties settings =
                new ConfigurationProperties(new DogsBayDocument(dir.resolve("settings.xml").toUri().toURL(), root));
        frame = new JFrame();
        return new NewDocumentDialog(frame, settings);
    }

    @Test
    void okActsOnWhateverIsHighlighted() throws Exception {
        NewDocumentDialog dialog = dialog();
        dialog.prepareForTest();

        assertThat(dialog.highlighted()).as("the list highlights a row as soon as it opens").isNotNull();
        dialog.pressOkForTest();

        assertThat(dialog.isCancelled()).isFalse();
        // before the fix this was the blank default however the list was highlighted, because OK recorded nothing
        assertThat(dialog.chosenForTest()).isSameAs(dialog.highlighted());
    }

    @Test
    void theListLeavesOutTemplatesThatCannotMakeThisKindOfFile() throws Exception {
        NewDocumentDialog dialog = dialog();

        dialog.setForFile("audacity.ditamap");
        dialog.prepareForTest();

        // the shipped templates are all topics, so naming a map should leave none of them, and the highlighted row should
        // be a real choice rather than the separator
        assertThat(dialog.templateNamesForTest()).isEmpty();
        assertThat(dialog.highlighted()).isNotNull();
        assertThat(dialog.highlightedIsSeparatorForTest()).isFalse();
    }

    @Test
    void withNoFileNameTheListIsWhateverIsInstalled() throws Exception {
        NewDocumentDialog dialog = dialog();

        dialog.setForFile(null);
        dialog.prepareForTest();

        // File > New has no name to filter by, so it must show everything it did before
        assertThat(dialog.highlighted()).isNotNull();
    }

    @Test
    void aBlankDocumentIsTheDeclarationAndNothingElse() throws Exception {
        NewDocumentDialog dialog = dialog();
        dialog.prepareForTest();
        dialog.chooseBlankForTest();

        String text = FileUtilities.initialText(dialog);

        // it used to be a <dogsbay> element with a blank line in it, which had to be deleted before writing
        assertThat(text).isEqualTo(FileUtilities.BLANK_XML);
        assertThat(text).doesNotContain("dogsbay");
    }

    @Test
    void cancellingChoosesNothing() throws Exception {
        NewDocumentDialog dialog = dialog();
        dialog.prepareForTest();

        assertThat(dialog.chosenForTest()).isNull();
        assertThat(dialog.isCancelled()).isTrue();
    }
}
