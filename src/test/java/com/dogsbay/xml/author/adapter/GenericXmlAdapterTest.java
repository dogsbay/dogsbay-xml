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

import java.io.File;

import org.dom4j.Element;
import org.junit.jupiter.api.Test;

import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.AuthorDocument;
import com.dogsbay.xml.author.model.BlockType;
import com.dogsbay.xml.author.model.BlockTypeRegistry;

class GenericXmlAdapterTest {

    private final GenericXmlAdapter adapter = new GenericXmlAdapter();

    @Test
    void importsArbitraryXmlWithInferredClassification() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse("""
                <config version="2">
                  <server>
                    <host>example.com</host>
                    <port>8080</port>
                  </server>
                  <flags>
                    <flag name="debug">true</flag>
                  </flags>
                </config>"""));

        AuthorBlock root = doc.getRoot();
        assertThat(root.getType().getName()).isEqualTo("config");
        assertThat(root.getType().getCategory()).isEqualTo(BlockType.Category.CONTAINER);
        assertThat(root.getAttribute("version")).isEqualTo("2");

        AuthorBlock server = root.getChildren().get(0);
        assertThat(server.getChildren()).extracting(b -> b.getType().getName())
                .containsExactly("host", "port");
        assertThat(server.getChildren().get(0).getType().getCategory())
                .isEqualTo(BlockType.Category.TEXT);
        assertThat(server.getChildren().get(0).getPlainText()).isEqualTo("example.com");

        AuthorBlock flag = root.getChildren().get(1).getChildren().get(0);
        assertThat(flag.getAttribute("name")).isEqualTo("debug");
        assertThat(flag.getPlainText()).isEqualTo("true");
    }

    @Test
    void roundTripsGenericXmlLosslessly() {
        Element original = AdapterTestSupport.parse("""
                <settings>
                  <!-- tuning -->
                  <option key="a">1</option>
                  <option key="b">2</option>
                  <?custom-pi data?>
                  <mixed>this <b>cannot</b> be modelled</mixed>
                </settings>""");
        AuthorDocument imported = adapter.importDocument(original);
        Element export1 = adapter.exportDocument(imported);

        AuthorDocument reimported = adapter.importDocument(export1);
        assertThat(reimported.getRoot().toStructureString())
                .isEqualTo(imported.getRoot().toStructureString());
        assertThat(adapter.exportDocument(reimported).asXML()).isEqualTo(export1.asXML());

        // the mixed-content element survived verbatim as a raw block
        assertThat(export1.asXML()).contains("this <b>cannot</b> be modelled");
        // comments and PIs survive in place
        assertThat(export1.asXML()).contains("<!-- tuning -->").contains("<?custom-pi data?>");
    }

    @Test
    void preservesSignificantWhitespaceWithXmlSpace() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(
                "<doc><pre xml:space=\"preserve\">  keep\n  this  </pre><p>  collapse   this  </p></doc>"));
        assertThat(doc.getRoot().getChildren().get(0).getPlainText()).isEqualTo("  keep\n  this  ");
        assertThat(doc.getRoot().getChildren().get(1).getPlainText()).isEqualTo("collapse this");
    }

    @Test
    void inlineStylesAreNotSupported() {
        assertThat(adapter.supportsInlineStyles()).isFalse();
        assertThat(new DitaBlockAdapter().supportsInlineStyles()).isTrue();
    }


    @Test
    void aFragmentImportsAndExportsForTheBlockClipboard() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(
                "<settings><option key=\"a\">1</option></settings>"));
        AuthorBlock root = doc.getRoot();
        AuthorBlock copied = root.getChildren().get(0);
        Element exported = adapter.exportFragment(copied);
        assertThat(exported.asXML()).isEqualTo("<option key=\"a\">1</option>");

        AuthorBlock pasted = adapter.importFragment(doc, exported, root);
        assertThat(pasted.getType().getName()).isEqualTo("option");
        assertThat(pasted.getPlainText()).isEqualTo("1");
        assertThat(pasted.getAttribute("key")).isEqualTo("a");
        assertThat(pasted.getParent()).as("a fragment is not attached by the import").isNull();

        // unknown markup still comes back, verbatim, as a raw chip
        AuthorBlock raw = adapter.importFragment(doc,
                AdapterTestSupport.parse("<never-seen><x/></never-seen>"), root);
        assertThat(raw.getType().getCategory()).isEqualTo(BlockType.Category.RAW);
        assertThat(raw.getRawXml()).contains("never-seen");
    }
}
