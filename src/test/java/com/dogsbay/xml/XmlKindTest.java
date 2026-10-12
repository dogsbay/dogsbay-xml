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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dogsbay.xml.editor.DocumentFormatRegistry;
import com.dogsbay.xml.editor.MarkdownDocumentFormat;
import com.dogsbay.xml.editor.PlainTextDocumentFormat;
import com.dogsbay.xml.editor.XmlDocumentFormat;
import org.junit.jupiter.api.io.TempDir;


/**
 * A document's kind comes from its name, not from whether the text parses.
 *
 * <p>The XML commands ask the first question and used to ask the second, which switched Validate and Format off on a topic
 * that was empty or part way through being typed — the one time saying what is missing would help most.
 */
class XmlKindTest {

    @TempDir
    Path dir;

    @BeforeEach
    void registerFormats() {
        // the running editor registers these at startup; a test has to say so itself
        DocumentFormatRegistry.clear();
        DocumentFormatRegistry.register(new XmlDocumentFormat());
        DocumentFormatRegistry.register(new MarkdownDocumentFormat());
        PlainTextDocumentFormat plain = new PlainTextDocumentFormat();
        DocumentFormatRegistry.register(plain);
        DocumentFormatRegistry.setDefault(plain);
    }

    @AfterEach
    void forgetFormats() {
        DocumentFormatRegistry.clear();
    }

    /** A document as the editor has it: read from disk, and parsed, so that a broken one knows it is broken. */
    private DogsBayDocument documentNamed(String name, String content) throws Exception {
        Path file = Files.writeString(dir.resolve(name), content);
        DogsBayDocument document = new DogsBayDocument(file.toUri().toURL());
        try {
            document.load();
        } catch (Exception notWellFormed) {
            // which is the case under test
        }
        return document;
    }

    @Test
    void anEmptyTopicIsStillATopic() throws Exception {
        DogsBayDocument empty = documentNamed("what-is-audacity.dita", "");

        // it does not parse, and that is the right answer to a different question
        assertThat(empty.isXML()).isFalse();
        assertThat(empty.isXMLKind()).isTrue();
    }

    @Test
    void soIsOneHalfTypedAndThatOneAlreadyWorked() throws Exception {
        DogsBayDocument partial = documentNamed("t.dita", "<topic id=\"t\"><title>Half a ti");

        // worth pinning down: a document with a tag left open is XML that does not parse, not something other than XML,
        // so the commands were already available here. The empty file above is the case that was not.
        assertThat(partial.isXML()).isTrue();
        assertThat(partial.isXMLKind()).isTrue();
    }

    @Test
    void aWellFormedDocumentIsBothOfCourse() throws Exception {
        DogsBayDocument whole = documentNamed("t.dita", "<topic id=\"t\"><title>A title</title></topic>");

        assertThat(whole.isXML()).isTrue();
        assertThat(whole.isXMLKind()).isTrue();
    }

    @Test
    void somethingThatIsNotXmlAtAllIsNeither() throws Exception {
        DogsBayDocument notes = documentNamed("notes.md", "# Notes\n\nnot xml at all");

        assertThat(notes.isXMLKind()).isFalse();
    }

}
