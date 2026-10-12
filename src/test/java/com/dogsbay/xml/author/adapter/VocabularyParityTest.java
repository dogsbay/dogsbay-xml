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
import java.util.stream.Stream;

import org.dom4j.Element;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.AuthorDocument;
import com.dogsbay.xml.author.model.BlockType;
import com.dogsbay.xml.author.model.InlineStyle;
import com.dogsbay.xml.author.spi.DefaultReferenceResolver;

/**
 * The ditablocks feature-parity vocabulary: definition lists, CALS tables,
 * glossary entries, related links and abbreviated-form — structured (not raw)
 * and lossless.
 */
class VocabularyParityTest {

    private final DitaBlockAdapter adapter = new DitaBlockAdapter();

    @Test
    void definitionListsAreStructured() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse("""
                <concept id="c"><title>T</title><conbody>
                <dl><dlentry><dt>Amplitude</dt><dd>The height of a <b>sound</b> wave.</dd></dlentry></dl>
                </conbody></concept>"""));
        AuthorBlock dl = doc.getRoot().getChildren().get(1).getChildren().get(0);
        assertThat(dl.getType().getName()).isEqualTo("dl");
        AuthorBlock entry = dl.getChildren().get(0);
        assertThat(entry.getChildren()).extracting(b -> b.getType().getName())
                .containsExactly("dt", "dd");
        assertThat(entry.getChildren().get(0).getPlainText()).isEqualTo("Amplitude");
        assertThat(entry.getChildren().get(1).getText().get(1).attrs())
                .containsEntry(InlineStyle.BOLD, InlineStyle.TRUE);
    }

    @Test
    void glossaryEntriesAreAFirstClassTopicType() {
        Element root = AdapterTestSupport.parseResource(
                "/author/corpus/topics/glossary/g-sample-rate.dita").getRootElement();
        AuthorDocument doc = adapter.importDocument(root);
        assertThat(doc.getRoot().getType().getName()).isEqualTo("glossentry");
        assertThat(doc.getRoot().getChildren()).extracting(b -> b.getType().getName())
                .containsExactly("glossterm", "glossdef");
        assertThat(doc.getRoot().getChildren().get(0).getPlainText()).isEqualTo("Sample Rate");
        assertThat(countRaw(doc.getRoot())).isZero();
        assertThat(DitaBlockAdapter.isDitaTopic("glossentry")).isTrue();
    }

    @Test
    void calsTablesKeepColspecsAndStructure() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse("""
                <reference id="r"><title>T</title><refbody><section>
                <table id="t1"><title>Caption</title>
                  <tgroup cols="2">
                    <colspec colname="c1" colwidth="1*"/>
                    <colspec colname="c2" colwidth="2*"/>
                    <thead><row><entry>Name</entry><entry>Value</entry></row></thead>
                    <tbody><row><entry>a</entry><entry>1</entry></row></tbody>
                  </tgroup>
                </table>
                </section></refbody></reference>"""));
        assertThat(countRaw(doc.getRoot())).isZero();
        AuthorBlock table = find(doc.getRoot(), "table");
        AuthorBlock tgroup = find(table, "tgroup");
        assertThat(tgroup.getAttribute("cols")).isEqualTo("2");
        assertThat(tgroup.getChildren()).extracting(b -> b.getType().getName())
                .containsExactly("colspec", "colspec", "thead", "tbody");
        assertThat(tgroup.getChildren().get(0).getAttribute("colwidth")).isEqualTo("1*");

        // lossless: colspecs and attributes survive the round-trip
        Element export = adapter.exportDocument(doc);
        assertThat(export.asXML())
                .contains("<colspec colname=\"c1\" colwidth=\"1*\"/>")
                .contains("<thead><row><entry>Name</entry>");
        AuthorDocument reimported = adapter.importDocument(export);
        assertThat(reimported.getRoot().toStructureString())
                .isEqualTo(doc.getRoot().toStructureString());
    }

    @Test
    void relatedLinksRenderAsStructuredFooter() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse("""
                <concept id="c"><title>T</title><conbody><p>x</p></conbody>
                <related-links>
                  <link href="a.dita"><linktext>About A</linktext></link>
                  <link href="b.dita"/>
                </related-links></concept>"""));
        AuthorBlock links = find(doc.getRoot(), "related-links");
        assertThat(links.getChildren()).hasSize(2);
        AuthorBlock first = links.getChildren().get(0);
        assertThat(first.getAttribute(DitaBlockAdapter.LINK_TEXT_ATTR)).isEqualTo("About A");
        assertThat(first.getAttribute("href")).isEqualTo("a.dita");

        Element export = adapter.exportDocument(doc);
        assertThat(export.asXML())
                .contains("<link href=\"a.dita\"><linktext>About A</linktext></link>")
                .contains("<link href=\"b.dita\"/>");
    }

    @Test
    void abbreviatedFormIsAnInlineReference() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse("""
                <concept id="c"><title>T</title><conbody>
                <p>The <abbreviated-form keyref="gl-sample-rate"/> matters.</p>
                </conbody></concept>"""));
        AuthorBlock p = doc.getRoot().getChildren().get(1).getChildren().get(0);
        assertThat(p.getType().getCategory()).isNotEqualTo(BlockType.Category.RAW);
        assertThat(p.getText()).anyMatch(r -> r.text().isEmpty()
                && "gl-sample-rate".equals(r.attrs().get(InlineStyle.KEYREF))
                && "abbreviated-form".equals(r.attrs().get(InlineStyle.DITA_INLINE)));

        Element export = adapter.exportDocument(doc);
        assertThat(export.asXML()).contains("<abbreviated-form keyref=\"gl-sample-rate\"/>");
    }

    @Test
    void glossaryKeysResolveToTheirTerm() {
        File corpus = new File("src/test/resources/author/corpus");
        DefaultReferenceResolver resolver = new DefaultReferenceResolver(
                new File(corpus, "topics/what-is-digital-audio.dita").toURI(),
                new File(corpus, "podcaster-guide.ditamap"));
        // gl-* keydefs carry no keyword text — resolution goes through the
        // target glossentry's term
        assertThat(resolver.keyText("gl-sample-rate")).isEqualTo("Sample Rate");
        assertThat(resolver.keyText("gl-waveform")).isEqualTo("Waveform");
        // keyword-text keys still resolve directly
        assertThat(resolver.keyText("product-name")).isEqualTo("Audacity");
    }

    @ParameterizedTest
    @MethodSource("formerlyRawTopics")
    void formerlyRawCorpusContentIsNowFullyStructured(String topic) {
        Element root = AdapterTestSupport.parseResource("/author/corpus/topics/" + topic)
                .getRootElement();
        AuthorDocument doc = adapter.importDocument(root);
        assertThat(countRaw(doc.getRoot()))
                .as("raw blocks in %s", topic)
                .isZero();
    }

    static Stream<String> formerlyRawTopics() {
        // what-is-digital-audio: dl + related-links + abbreviated-form
        // recording-your-first-track: related-links
        return Stream.of("what-is-digital-audio.dita", "recording-your-first-track.dita",
                "what-is-audacity.dita");
    }

    private int countRaw(AuthorBlock block) {
        int n = block.getType().getCategory() == BlockType.Category.RAW ? 1 : 0;
        for (AuthorBlock child : block.getChildren()) {
            n += countRaw(child);
        }
        return n;
    }

    private AuthorBlock find(AuthorBlock block, String type) {
        if (type.equals(block.getType().getName())) {
            return block;
        }
        for (AuthorBlock child : block.getChildren()) {
            AuthorBlock found = find(child, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
