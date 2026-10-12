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

import java.util.List;
import java.util.Map;

import org.dom4j.Element;
import org.junit.jupiter.api.Test;

import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.AuthorDocument;
import com.dogsbay.xml.author.model.InlineRun;
import com.dogsbay.xml.author.model.InlineStyle;
import com.dogsbay.xml.author.model.XmlAuxNode;

class DitaBlockAdapterExportTest {

    private final DitaBlockAdapter adapter = new DitaBlockAdapter();

    @Test
    void exportsStructureAttributesAndText() {
        AuthorDocument doc = new AuthorDocument(adapter.getRegistry());
        AuthorBlock concept = doc.createBlock("concept");
        concept.setAttribute("id", "c1");
        doc.setRoot(concept);
        AuthorBlock title = doc.createBlock("title");
        title.setText(List.of(InlineRun.of("Widgets")));
        doc.attach(concept, 0, title);
        AuthorBlock conbody = doc.createBlock("conbody");
        doc.attach(concept, 1, conbody);
        AuthorBlock p = doc.createBlock("p");
        p.setAttribute("audience", "admin");
        p.setText(List.of(
                InlineRun.of("Click "),
                InlineRun.styled("Save", InlineStyle.DITA_INLINE, "uicontrol"),
                InlineRun.of(" to "),
                InlineRun.styled("confirm", InlineStyle.BOLD, InlineStyle.TRUE),
                InlineRun.of(".")));
        doc.attach(conbody, 0, p);

        Element out = adapter.exportDocument(doc);
        assertThat(out.asXML()).isEqualTo(
                "<concept id=\"c1\"><title>Widgets</title><conbody>"
                        + "<p audience=\"admin\">Click <uicontrol>Save</uicontrol>"
                        + " to <b>confirm</b>.</p></conbody></concept>");
    }

    @Test
    void exportsCombinedInlineStylesAsNestedElements() {
        AuthorDocument doc = singleParagraphDoc(List.of(new InlineRun("both",
                Map.of(InlineStyle.BOLD, InlineStyle.TRUE, InlineStyle.ITALIC, InlineStyle.TRUE))));
        Element p = firstParagraph(adapter.exportDocument(doc));
        assertThat(p.asXML()).isEqualTo("<p><b><i>both</i></b></p>");
    }

    @Test
    void exportsXrefWithExtraAttributesAndKeywordKeyref() {
        AuthorDocument doc = singleParagraphDoc(List.of(
                new InlineRun("guide", Map.of(
                        InlineStyle.HREF, "g.dita",
                        InlineStyle.SCOPE, "external",
                        "xref:format", "html")),
                InlineRun.of(" and "),
                new InlineRun("", Map.of(
                        InlineStyle.DITA_INLINE, "keyword",
                        InlineStyle.KEYREF, "product"))));
        Element p = firstParagraph(adapter.exportDocument(doc));
        assertThat(p.asXML()).isEqualTo(
                "<p><xref href=\"g.dita\" scope=\"external\" format=\"html\">guide</xref>"
                        + " and <keyword keyref=\"product\"/></p>");
    }

    @Test
    void exportsBareKeyrefAsPh() {
        AuthorDocument doc = singleParagraphDoc(List.of(
                new InlineRun("", Map.of(InlineStyle.KEYREF, "product"))));
        Element p = firstParagraph(adapter.exportDocument(doc));
        assertThat(p.asXML()).isEqualTo("<p><ph keyref=\"product\"/></p>");
    }

    @Test
    void exportsImageAltAsChildElement() {
        AuthorDocument doc = new AuthorDocument(adapter.getRegistry());
        AuthorBlock concept = doc.createBlock("concept");
        doc.setRoot(concept);
        AuthorBlock conbody = doc.createBlock("conbody");
        doc.attach(concept, 0, conbody);
        AuthorBlock image = doc.createBlock("image");
        image.setAttribute("href", "pic.png");
        image.setAttribute(DitaBlockAdapter.IMAGE_ALT_ATTR, "a picture");
        doc.attach(conbody, 0, image);

        Element out = adapter.exportDocument(doc);
        assertThat(out.asXML())
                .contains("<image href=\"pic.png\"><alt>a picture</alt></image>");
    }

    @Test
    void exportsLeadingAndTrailingAuxNodes() {
        AuthorDocument doc = singleParagraphDoc(List.of(InlineRun.of("x")));
        AuthorBlock conbody = doc.getRoot().getChildren().get(0);
        conbody.getChildren().get(0).setLeadingAux(List.of(XmlAuxNode.comment(" lead ")));
        conbody.setTrailingAux(List.of(XmlAuxNode.pi("audience", "admin")));

        Element out = adapter.exportDocument(doc);
        assertThat(out.asXML()).isEqualTo(
                "<concept><conbody><!-- lead --><p>x</p><?audience admin?></conbody></concept>");
    }

    @Test
    void exportsRawBlockVerbatim() {
        AuthorDocument doc = new AuthorDocument(adapter.getRegistry());
        AuthorBlock concept = doc.createBlock("concept");
        doc.setRoot(concept);
        AuthorBlock conbody = doc.createBlock("conbody");
        doc.attach(concept, 0, conbody);
        doc.attach(conbody, 0, doc.createRawBlock("<dl remap=\"x\"><dlentry><dt>a</dt><dd>b</dd></dlentry></dl>"));

        Element out = adapter.exportDocument(doc);
        assertThat(out.asXML())
                .contains("<dl remap=\"x\"><dlentry><dt>a</dt><dd>b</dd></dlentry></dl>");
    }

    private AuthorDocument singleParagraphDoc(List<InlineRun> runs) {
        AuthorDocument doc = new AuthorDocument(adapter.getRegistry());
        AuthorBlock concept = doc.createBlock("concept");
        doc.setRoot(concept);
        AuthorBlock conbody = doc.createBlock("conbody");
        doc.attach(concept, 0, conbody);
        AuthorBlock p = doc.createBlock("p");
        p.setText(runs);
        doc.attach(conbody, 0, p);
        return doc;
    }

    private Element firstParagraph(Element root) {
        return (Element) root.elements().get(0).elements().get(0);
    }
}
