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

import java.io.StringWriter;
import java.nio.charset.Charset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The document-save path, which is {@link DogsBayXMLWriter} on its own rather than the two passes
 * of {@code XMLUtilities.format}. Formatting happened to survive unrepresentable characters in
 * attributes only because its first pass escaped them into literal text; this writer is the one
 * that writes a file, so it has to get it right by itself.
 */
class SavePathEncodingTest {

    private static XDocument parse(String xml) throws Exception {
        return XMLUtilities.parse(XMLUtilities.createReader(false, false),
                new java.io.BufferedReader(new java.io.StringReader(xml)), null);
    }

    @Test
    @DisplayName("saving keeps an attribute character the encoding cannot represent")
    void attributeSurvivesTheSavePath() throws Exception {
        XDocument doc = parse("<root><p outputclass=\"café\">t</p></root>");
        // set a character ISO-8859-1 has no byte for, as an editor action would
        doc.getRootElement().element("p").addAttribute("props", "a → b");

        String out = XMLUtilities.write(doc, new DogsBayOutputFormat("", false, "ISO-8859-1"));

        assertThat(out).contains("café");
        assertThat(out).containsPattern("props=\"a &#(8594|x2192); b\"");
        assertThat(Charset.forName("ISO-8859-1").newEncoder().canEncode(out))
                .as("the saved text must be writable in the file's own encoding: " + out)
                .isTrue();
    }

    @Test
    @DisplayName("serialising to a character sink leaves the text exactly as the author typed it")
    void characterSinkIsNotEscaped() throws Exception {
        // DogsBayDocument.writeText serialises the tree into the editor's own buffer through a
        // Writer. Nothing is encoded there, so escaping would put `&#8594;` in front of the author
        // in place of the character they typed.
        XDocument doc = parse("<root><p>a b</p></root>");
        doc.getRootElement().element("p").setText("a → b");

        StringWriter sink = new StringWriter();
        DogsBayXMLWriter writer = new DogsBayXMLWriter(sink, new DogsBayOutputFormat("", false, "ISO-8859-1"));
        writer.write(doc);
        writer.flush();

        assertThat(sink.toString()).contains("a → b").doesNotContain("&#8594;");
    }
}
