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

package com.dogsbay.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.SAXParseException;

/**
 * Pushing the editor's text into the model throws for a file that is not XML —
 * a {@code .gitignore}, Markdown, plain text — even though the text reached the
 * model perfectly well. Saving treated that throw as "your edits were lost" and
 * refused to write, reporting that a .gitignore was not well-formed.
 *
 * <p>
 * These pin both halves of the distinction {@code DogsBayView.updateModel()}
 * now draws: a non-XML document is fine, malformed XML is still a problem.
 */
class PlainTextFlushTest {

    private static final String GITIGNORE = """
        # Built deliverables (DITA-OT output)
        out/
        output/
        *.tmp
        plans/
        """;

    private static DogsBayDocument openedOn(Path file, String content) throws Exception {
        Files.writeString(file, content);
        DogsBayDocument doc = new DogsBayDocument(file.toUri().toURL());
        try {
            doc.load();
        } catch (SAXParseException expected) {
            // Loading a non-XML file throws for the same reason; the text is read.
        }
        return doc;
    }

    @Test
    @DisplayName("a .gitignore is reported as not-XML, not as broken XML")
    void aGitignoreIsNotXmlRatherThanMalformed(@TempDir Path dir) throws Exception {
        DogsBayDocument doc = openedOn(dir.resolve(".gitignore"), GITIGNORE);

        Throwable thrown = catchThrowable(() -> doc.setText(GITIGNORE + "plans/\n"));

        assertThat(thrown).isInstanceOf(XMLUtilities.NotXMLException.class);
    }

    @Test
    @DisplayName("the text reaches the model even though the parse throws")
    void theTextStillReachesTheModel(@TempDir Path dir) throws Exception {
        DogsBayDocument doc = openedOn(dir.resolve(".gitignore"), GITIGNORE);
        String edited = GITIGNORE + "plans/\n";

        catchThrowable(() -> doc.setText(edited));

        // This is why refusing the save was wrong: nothing was lost.
        assertThat(doc.getText()).isEqualTo(edited);
    }

    @Test
    @DisplayName("what a non-XML document saves is what was typed into it")
    void savingWritesWhatWasTyped(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(".gitignore");
        DogsBayDocument doc = openedOn(file, GITIGNORE);
        String edited = GITIGNORE + "plans/\n";

        catchThrowable(() -> doc.setText(edited));
        doc.save();

        assertThat(Files.readString(file)).contains("plans/");
    }

    @Test
    @DisplayName("malformed XML is a different failure, and stays one")
    void malformedXmlIsStillAProblem(@TempDir Path dir) throws Exception {
        DogsBayDocument doc = openedOn(dir.resolve("topic.xml"), "<a>fine</a>");

        Throwable thrown = catchThrowable(() -> doc.setText("<a>unclosed"));

        // Caught by the same catch as .gitignore would hide a real breakage.
        assertThat(thrown).isInstanceOf(SAXParseException.class);
        assertThat(thrown).isNotInstanceOf(XMLUtilities.NotXMLException.class);
    }

    @Test
    @DisplayName("a well-formed XML document still flushes without throwing")
    void goodXmlIsUnaffected(@TempDir Path dir) throws Exception {
        DogsBayDocument doc = openedOn(dir.resolve("topic.xml"), "<a>fine</a>");

        assertThatCode(() -> doc.setText("<a>edited</a>")).doesNotThrowAnyException();
    }
}
