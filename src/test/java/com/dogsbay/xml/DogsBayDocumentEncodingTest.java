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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The editor's own open-and-edit path, which reached the parser through
 * {@code text.getBytes(getJavaEncoding())} — and so destroyed any character the document's encoding
 * could not represent before the tree existed, where nothing downstream could put it back.
 *
 * <p>This is the half of the chain reachable without a running editor.
 */
class DogsBayDocumentEncodingTest {

    private static DogsBayDocument openedOn(Path file, String content, String encoding) throws Exception {
        Files.write(file, content.getBytes(java.nio.charset.Charset.forName(encoding)));
        DogsBayDocument doc = new DogsBayDocument(file.toUri().toURL());
        doc.load();
        return doc;
    }

    @Test
    @DisplayName("a typed character the encoding cannot store survives reaching the model")
    void typedUnrepresentableCharacterReachesTheModel(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.xml");
        DogsBayDocument doc = openedOn(file,
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root><p>plain</p></root>\n", "ISO-8859-1");

        // as if the author pasted an arrow into a Latin-1 document
        doc.setText("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root><p>a → b café</p></root>\n");

        assertThat(doc.getText()).contains("a → b");
        // the tree is what every later step works from, so the character has to be there too
        assertThat(doc.getDocument().getRootElement().element("p").getText())
                .as("the parsed tree keeps the character")
                .contains("a → b café");
    }

    @Test
    @DisplayName("Latin-1 text already in the file reaches the model intact")
    void latin1TextReachesTheModel(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.xml");
        DogsBayDocument doc = openedOn(file,
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root><p>café naïve</p></root>\n",
                "ISO-8859-1");

        assertThat(doc.getDocument().getRootElement().element("p").getText()).isEqualTo("café naïve");
    }

    @Test
    @DisplayName("a UTF-8 document with an astral-plane character reaches the model intact")
    void astralPlaneReachesTheModel(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.xml");
        DogsBayDocument doc = openedOn(file,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root><p>party 🎉</p></root>\n", "UTF-8");

        assertThat(doc.getDocument().getRootElement().element("p").getText()).isEqualTo("party 🎉");
        assertThat(Files.readAllBytes(file)).isEqualTo(
                ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root><p>party 🎉</p></root>\n")
                        .getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("saving keeps a character the file's encoding cannot hold")
    void saveKeepsAnUnrepresentableCharacter(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.xml");
        DogsBayDocument doc = openedOn(file,
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root><p>plain</p></root>\n", "ISO-8859-1");

        // as if the author pasted an arrow and an emoji into a Latin-1 document, then saved
        doc.setText("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
                + "<root><p outputclass=\"caf\u00e9 \u2192\">a \u2192 b \ud83c\udf89</p></root>\n");
        doc.save();

        String written = Files.readString(file, java.nio.charset.Charset.forName("ISO-8859-1"));

        // the encoder used to answer each of these with '?', unrecoverably
        assertThat(written).contains("a &#8594; b &#127881;");
        assertThat(written).contains("outputclass=\"caf\u00e9 &#8594;\"");
        assertThat(written).doesNotContain("a ? b");
        // and the bytes really are Latin-1
        assertThat(java.nio.charset.Charset.forName("ISO-8859-1").newEncoder().canEncode(written)).isTrue();
    }

    @Test
    @DisplayName("saving a UTF-8 document changes nothing")
    void saveLeavesUtf8Alone(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.xml");
        DogsBayDocument doc = openedOn(file,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root><p>plain</p></root>\n", "UTF-8");

        String edited = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<root><p>a \u2192 b caf\u00e9 \ud83c\udf89</p></root>\n";
        doc.setText(edited);
        doc.save();

        assertThat(Files.readString(file, StandardCharsets.UTF_8))
                .as("UTF-8 holds every character, so nothing is escaped")
                .isEqualTo(edited);
    }

    @Test
    @DisplayName("a comment keeps its text, and the save reports what it cannot hold")
    void commentsAreReportedRatherThanEscaped(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.xml");
        DogsBayDocument doc = openedOn(file,
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root><p>plain</p></root>\n", "ISO-8859-1");

        doc.setText("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
                + "<root><!-- note \u2192 --><p>text \u2192</p></root>\n");

        // Nothing can spell an arrow inside a comment, so this is reported rather than fixed.
        assertThat(doc.unescapableCharacters()).isEqualTo("\u2192");

        doc.save();
        String written = Files.readString(file, java.nio.charset.Charset.forName("ISO-8859-1"));
        assertThat(written).contains("text &#8594;");
        assertThat(written).as("the comment is still written, lossily — a known limit").contains("<!-- note ");
    }

    @Test
    @DisplayName("nothing to lose is reported as nothing")
    void nothingLostIsReportedAsNothing(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.xml");
        DogsBayDocument doc = openedOn(file,
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root><p>caf\u00e9</p></root>\n", "ISO-8859-1");

        assertThat(doc.unescapableCharacters()).isEmpty();
    }

    @Test
    @DisplayName("a CDATA section keeps its characters by becoming escaped text")
    void cdataSurvivesTheSave(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.xml");
        DogsBayDocument doc = openedOn(file,
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root><p>plain</p></root>\n", "ISO-8859-1");

        doc.setText("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
                + "<root><p><![CDATA[if (a<b) \u2192 go]]></p></root>\n");

        // A section cannot hold a reference, but escaped text says the same thing — so this is not
        // a loss and must not be reported as one.
        assertThat(doc.unescapableCharacters()).isEmpty();

        doc.save();
        String written = Files.readString(file, java.nio.charset.Charset.forName("ISO-8859-1"));

        assertThat(written).contains("if (a&lt;b) &#8594; go");
        assertThat(written).doesNotContain("<![CDATA[");
        assertThat(java.nio.charset.Charset.forName("ISO-8859-1").newEncoder().canEncode(written)).isTrue();
    }

    @Test
    @DisplayName("an ordinary CDATA section is left as a CDATA section")
    void ordinaryCdataIsLeftAlone(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("topic.xml");
        DogsBayDocument doc = openedOn(file,
                "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root><p>plain</p></root>\n", "ISO-8859-1");

        doc.setText("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
                + "<root><p><![CDATA[if (a<b) caf\u00e9]]></p></root>\n");
        doc.save();

        assertThat(Files.readString(file, java.nio.charset.Charset.forName("ISO-8859-1")))
                .as("nothing here needs rescuing, so nothing changes")
                .contains("<![CDATA[if (a<b) caf\u00e9]]>");
    }
}
