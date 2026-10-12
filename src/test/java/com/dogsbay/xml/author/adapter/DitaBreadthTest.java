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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.AuthorDocument;
import com.dogsbay.xml.author.model.BlockType;
import com.dogsbay.xml.author.model.InlineRun;
import com.dogsbay.xml.author.model.InlineStyle;

/** The wider DITA 1.3 vocabulary imports as blocks and runs, not as raw chips. */
class DitaBreadthTest {

    private final DitaBlockAdapter adapter = new DitaBlockAdapter();

    private static List<AuthorBlock> raws(AuthorBlock block) {
        List<AuthorBlock> out = new ArrayList<>();
        if (block.getType().getCategory() == BlockType.Category.RAW) {
            out.add(block);
        }
        block.getChildren().forEach(c -> out.addAll(raws(c)));
        return out;
    }

    private static List<String> types(AuthorBlock block) {
        List<String> out = new ArrayList<>();
        out.add(block.getType().getName());
        block.getChildren().forEach(c -> out.addAll(types(c)));
        return out;
    }

    @Test
    void xmlSpacePreserveKeepsWhitespaceAndDefaultResetsIt() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(
                "<concept id='c'><title>T</title><conbody>"
                + "<section xml:space='preserve'><p>a\n  b</p><p xml:space='default'>c\n  d</p></section>"
                + "<p>e\n  f</p></conbody></concept>"));
        AuthorBlock section = doc.getRoot().getChildren().get(1).getChildren().get(0);
        AuthorBlock preserved = section.getChildren().get(0);
        AuthorBlock reset = section.getChildren().get(1);
        AuthorBlock plain = doc.getRoot().getChildren().get(1).getChildren().get(1);
        assertThat(preserved.preservesSpace()).isTrue();
        assertThat(preserved.getPlainText()).isEqualTo("a\n  b");
        assertThat(reset.preservesSpace()).isFalse();
        assertThat(reset.getPlainText()).isEqualTo("c d");
        assertThat(plain.preservesSpace()).isFalse();
        assertThat(plain.getPlainText()).isEqualTo("e f");
        assertThat(adapter.exportDocument(doc).asXML()).contains("<p>a\n  b</p>").contains("xml:space=\"preserve\"");
    }

    @Test
    void aPrologIsEditableAndItsOddMembersStayRaw() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse("""
            <concept id="c"><title>T</title><prolog>
              <author>Ann</author>
              <critdates><created date="2024-01-01"/><revised modified="2025-02-02"/></critdates>
              <metadata><keywords><keyword>mix</keyword> <indexterm>mixing</indexterm></keywords>
                <prodinfo><prodname>Studio</prodname></prodinfo></metadata>
            </prolog><conbody><p>x</p></conbody></concept>"""));
        AuthorBlock prolog = doc.getRoot().getChildren().get(1);
        assertThat(prolog.getType().getName()).isEqualTo("prolog");
        assertThat(types(prolog)).contains("author", "critdates", "created", "revised", "metadata", "keywords");
        assertThat(raws(prolog)).extracting(b -> b.getRawXml()).singleElement().asString().startsWith("<prodinfo>");
        AuthorBlock keywords = prolog.getChildren().get(2).getChildren().get(0);
        assertThat(keywords.getText()).hasSize(3);
        assertThat(keywords.getText().get(0).attrs()).containsEntry(InlineStyle.DITA_INLINE, "keyword");
        assertThat(doc.getRegistry().insertableChildren(doc.getRoot())).extracting(BlockType::getName)
                .as("a topic that has its prolog is not offered another").doesNotContain("prolog");
    }

    @Test
    void aCalsTableDirectlyInARefbodyIsStructured() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse("""
            <reference id="r"><title>Effects</title><refbody>
              <section><p>Intro.</p></section>
              <table><title>Built-in</title><tgroup cols="2"><colspec colname="a"/><colspec colname="b"/>
                <thead><row><entry>Effect</entry><entry>Use</entry></row></thead>
                <tbody><row><entry>Amplify</entry><entry>Louder.</entry></row></tbody></tgroup></table>
              <refsyn><title>Syntax</title><p>x</p></refsyn>
            </refbody></reference>"""));
        assertThat(raws(doc.getRoot())).isEmpty();
        assertThat(types(doc.getRoot())).contains("table", "tgroup", "colspec", "thead", "tbody", "row", "entry", "refsyn");
    }

    @Test
    void theFormerlyUnusualElementsAreBlocksNow() {
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse("""
            <concept id="u"><title>Unusual<indexterm>index</indexterm></title>
            <conbody>
            <p>Text with a footnote<fn>The note.</fn> and <xref href="a.dita">a link</xref> and <ph>phrase</ph> and <sup>2</sup>.</p>
            <lq>A long quote.</lq>
            <pre>preformatted
              lines</pre>
            <sl><sli>simple list item</sli></sl>
            <parml><plentry><pt>param</pt><pd>desc</pd></plentry></parml>
            <draft-comment>hand note</draft-comment>
            <note type="warning"><p>Careful.</p></note>
            <section><title>S</title><sectiondiv><p>in div</p></sectiondiv></section>
            </conbody></concept>"""));
        assertThat(raws(doc.getRoot())).isEmpty();
        assertThat(types(doc.getRoot())).contains("lq", "pre", "sl", "sli", "parml", "plentry", "pt", "pd",
                "draft-comment", "sectiondiv");
        AuthorBlock title = doc.getRoot().getChildren().get(0);
        assertThat(title.getText()).anySatisfy(r -> assertThat(r.attrs()).containsEntry(InlineStyle.DITA_INLINE, "indexterm"));
        AuthorBlock p = doc.getRoot().getChildren().get(1).getChildren().get(0);
        assertThat(p.getText()).anySatisfy(r -> assertThat(r.attrs()).containsEntry(InlineStyle.DITA_INLINE, "fn"))
                .anySatisfy(r -> assertThat(r.attrs()).containsEntry(InlineStyle.DITA_INLINE, "sup"));
    }

    @Test
    void troubleshootingAndGlossgroupAreTopicTypes() {
        AuthorDocument t = adapter.importDocument(AdapterTestSupport.parse("""
            <troubleshooting id="t"><title>No sound</title><troublebody>
              <condition><p>Nothing plays.</p></condition>
              <troubleSolution><cause><p>Muted.</p></cause>
                <remedy><steps><step><cmd>Unmute.</cmd></step></steps></remedy></troubleSolution>
            </troublebody></troubleshooting>"""));
        assertThat(t.getRoot().getType().getName()).isEqualTo("troubleshooting");
        assertThat(raws(t.getRoot())).isEmpty();
        assertThat(DitaBlockAdapter.isDitaTopic("troubleshooting")).isTrue();
        AuthorDocument g = adapter.importDocument(AdapterTestSupport.parse(
                "<glossgroup id='g'><title>Terms</title><glossentry id='e'><glossterm>Gain</glossterm>"
                + "<glossdef>Level.</glossdef></glossentry></glossgroup>"));
        assertThat(raws(g.getRoot())).isEmpty();
    }

    @Test
    void nestedInlinesFormAChainAndExportInOrder() {
        String xml = "<task id='t'><title>T</title><taskbody><steps><step><cmd>Choose "
                + "<menucascade><uicontrol>File</uicontrol><uicontrol>Export</uicontrol></menucascade> "
                + "then <b outputclass='hot'><codeph>save()</codeph></b>.</cmd></step></steps></taskbody></task>";
        AuthorDocument doc = adapter.importDocument(AdapterTestSupport.parse(xml));
        assertThat(raws(doc.getRoot())).isEmpty();
        AuthorBlock cmd = doc.getRoot().getChildren().get(1).getChildren().get(0).getChildren().get(0).getChildren().get(0);
        InlineRun file = cmd.getText().stream().filter(r -> r.text().equals("File")).findFirst().orElseThrow();
        assertThat(file.attrs()).containsEntry(InlineStyle.DITA_INLINE, "uicontrol");
        assertThat(InlineStyle.chainName(file.attrs().get(InlineStyle.DITA_INLINE_OUTER))).isEqualTo("menucascade");
        InlineRun save = cmd.getText().stream().filter(r -> r.text().equals("save()")).findFirst().orElseThrow();
        assertThat(save.attrs()).containsEntry("b:outputclass", "hot").containsEntry(InlineStyle.DITA_INLINE, "codeph");
        String out = adapter.exportDocument(doc).asXML();
        assertThat(out).contains("<menucascade><uicontrol>File</uicontrol><uicontrol>Export</uicontrol></menucascade>")
                .contains("<b outputclass=\"hot\"><codeph>save()</codeph></b>");
    }
}
