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

package com.dogsbay.xml.author.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.dom4j.Element;
import org.junit.jupiter.api.Test;

import com.dogsbay.xml.author.model.AuthorDocument;

class BlockXmlFormatterTest {

    private final DitaBlockAdapter adapter = new DitaBlockAdapter();

    @Test
    void indentsStructureWithoutTouchingTextContent() {
        Element root = AdapterTestSupport.parse(
                "<concept id=\"c\"><title>T</title><conbody>"
                        + "<p>text with <b>inline</b> stays</p>"
                        + "<codeblock>a\n b</codeblock>"
                        + "</conbody></concept>");
        Element exported = adapter.exportDocument(adapter.importDocument(root));
        BlockXmlFormatter.indent(exported);
        String xml = exported.asXML();

        assertThat(xml).contains("<concept id=\"c\">\n  <title>T</title>\n  <conbody>");
        // mixed/text content is untouched
        assertThat(xml).contains("<p>text with <b>inline</b> stays</p>");
        assertThat(xml).contains("<codeblock>a\n b</codeblock>");
    }

    @Test
    void indentedOutputReimportsToTheSameModel() {
        Element root = AdapterTestSupport.parseResource(
                "/author/corpus/recording-your-first-track.dita").getRootElement();
        AuthorDocument imported = adapter.importDocument(root);
        Element exported = adapter.exportDocument(imported);
        BlockXmlFormatter.indent(exported);

        AuthorDocument reimported = adapter.importDocument(exported);
        assertThat(reimported.getRoot().toStructureString())
                .isEqualTo(imported.getRoot().toStructureString());
    }

    @Test
    void aNamespaceDeclarationGetsNoLineOfItsOwn() {
        org.dom4j.Element root = new com.dogsbay.xml.author.adapter.DitaBlockAdapter().exportDocument(
                new com.dogsbay.xml.author.adapter.DitaBlockAdapter().importDocument(AdapterTestSupport.parse(
                        "<concept id='c' xmlns:ditaarch='http://dita.oasis-open.org/architecture/2005/' "
                        + "ditaarch:DITAArchVersion='1.3'><title>T</title><conbody><p>x</p></conbody></concept>")));
        BlockXmlFormatter.indent(root);
        String xml = root.asXML();
        assertThat(xml).startsWith("<concept xmlns:ditaarch=\"http://dita.oasis-open.org/architecture/2005/\"")
                .contains("ditaarch:DITAArchVersion=\"1.3\">\n  <title>T</title>\n  <conbody>");
        assertThat(xml).doesNotContain(">\n  \n");
    }
}
