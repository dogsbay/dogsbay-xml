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

import org.junit.jupiter.api.Test;

import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.AuthorDocument;
import com.dogsbay.xml.author.model.BlockType;
import com.dogsbay.xml.author.model.InlineRun;
import com.dogsbay.xml.author.model.InlineStyle;
import com.dogsbay.xml.author.model.XmlAuxNode;

class DitaBlockAdapterImportTest {

    private final DitaBlockAdapter adapter = new DitaBlockAdapter();

    private AuthorDocument importXml(String xml) {
        return adapter.importDocument(AdapterTestSupport.parse(xml));
    }

    @Test
    void importsConceptStructure() {
        AuthorDocument doc = importXml("""
                <concept id="c1">
                  <title>Widgets</title>
                  <shortdesc>About widgets.</shortdesc>
                  <conbody>
                    <p>First paragraph.</p>
                    <section>
                      <title>Details</title>
                      <p>More.</p>
                    </section>
                  </conbody>
                </concept>""");

        AuthorBlock root = doc.getRoot();
        assertThat(root.getType().getName()).isEqualTo("concept");
        assertThat(root.getAttribute("id")).isEqualTo("c1");
        assertThat(root.getChildren()).extracting(b -> b.getType().getName())
                .containsExactly("title", "shortdesc", "conbody");
        assertThat(root.getChildren().get(0).getPlainText()).isEqualTo("Widgets");

        AuthorBlock conbody = root.getChildren().get(2);
        assertThat(conbody.getChildren()).extracting(b -> b.getType().getName())
                .containsExactly("p", "section");
        AuthorBlock section = conbody.getChildren().get(1);
        assertThat(section.getChildren().get(0).getPlainText()).isEqualTo("Details");
    }

    @Test
    void collapsesInsignificantWhitespace() {
        AuthorDocument doc = importXml("""
                <concept id="c">
                  <title>T</title>
                  <conbody>
                    <p>
                      line one
                      line two
                    </p>
                  </conbody>
                </concept>""");
        AuthorBlock p = doc.getRoot().getChildren().get(1).getChildren().get(0);
        assertThat(p.getPlainText()).isEqualTo("line one line two");
    }

    @Test
    void preservesCodeblockWhitespaceVerbatim() {
        AuthorDocument doc = importXml("<concept id=\"c\"><title>T</title><conbody>"
                + "<codeblock>line 1\n  indented\n\ndone</codeblock>"
                + "</conbody></concept>");
        AuthorBlock code = doc.getRoot().getChildren().get(1).getChildren().get(0);
        assertThat(code.getType().getName()).isEqualTo("codeblock");
        assertThat(code.getPlainText()).isEqualTo("line 1\n  indented\n\ndone");
    }

    @Test
    void mapsInlineFormattingToRuns() {
        AuthorDocument doc = importXml("""
                <task id="t"><title>T</title><taskbody><steps><step>
                <cmd>Click <uicontrol>Save</uicontrol> to <b>confirm</b>.</cmd>
                </step></steps></taskbody></task>""");
        AuthorBlock cmd = doc.getRoot().getChildren().get(1)
                .getChildren().get(0).getChildren().get(0).getChildren().get(0);
        List<InlineRun> runs = cmd.getText();
        assertThat(runs).hasSize(5);
        assertThat(runs.get(0)).isEqualTo(InlineRun.of("Click "));
        assertThat(runs.get(1).text()).isEqualTo("Save");
        assertThat(runs.get(1).attrs()).containsEntry(InlineStyle.DITA_INLINE, "uicontrol");
        assertThat(runs.get(2)).isEqualTo(InlineRun.of(" to "));
        assertThat(runs.get(3).attrs()).containsEntry(InlineStyle.BOLD, InlineStyle.TRUE);
        assertThat(runs.get(4)).isEqualTo(InlineRun.of("."));
    }

    @Test
    void mapsXrefAndEmptyKeywordKeyref() {
        AuthorDocument doc = importXml("""
                <concept id="c"><title>T</title><conbody>
                <p>Read <xref href="other.dita">the guide</xref> in <keyword keyref="product"/> now.</p>
                </conbody></concept>""");
        AuthorBlock p = doc.getRoot().getChildren().get(1).getChildren().get(0);
        List<InlineRun> runs = p.getText();
        assertThat(runs.get(1).text()).isEqualTo("the guide");
        assertThat(runs.get(1).attrs()).containsEntry(InlineStyle.HREF, "other.dita");
        // the empty keyref keyword survives as a zero-length run
        InlineRun keywordRun = runs.stream()
                .filter(r -> "product".equals(r.attrs().get(InlineStyle.KEYREF)))
                .findFirst().orElseThrow();
        assertThat(keywordRun.text()).isEmpty();
        assertThat(keywordRun.attrs()).containsEntry(InlineStyle.DITA_INLINE, "keyword");
        assertThat(p.getPlainText()).isEqualTo("Read the guide in  now.");
    }

    @Test
    void emptyXrefSurvivesAsZeroLengthRun() {
        // found by visual testing: <xref href="..."/> with no text was dropped
        AuthorDocument doc = importXml("""
                <concept id="c"><title>T</title><conbody>
                <p>read <xref href="other.dita"/> first.</p>
                </conbody></concept>""");
        AuthorBlock p = doc.getRoot().getChildren().get(1).getChildren().get(0);
        InlineRun xref = p.getText().stream()
                .filter(r -> "other.dita".equals(r.attrs().get(InlineStyle.HREF)))
                .findFirst().orElseThrow();
        assertThat(xref.text()).isEmpty();
        // the space after the empty xref is significant and survives
        assertThat(p.getPlainText()).isEqualTo("read  first.");
    }

    @Test
    void preservesUnmappedAttributesVerbatim() {
        AuthorDocument doc = importXml("""
                <concept id="c"><title>T</title><conbody>
                <p audience="admin" platform="linux" props="beta">x</p>
                </conbody></concept>""");
        AuthorBlock p = doc.getRoot().getChildren().get(1).getChildren().get(0);
        assertThat(p.getAttributes())
                .containsEntry("audience", "admin")
                .containsEntry("platform", "linux")
                .containsEntry("props", "beta");
    }

    @Test
    void unknownElementBecomesRawBlock() {
        AuthorDocument doc = importXml("""
                <concept id="c"><title>T</title><conbody>
                <object data="movie.mp4"><desc>a video object</desc></object>
                </conbody></concept>""");
        AuthorBlock raw = doc.getRoot().getChildren().get(1).getChildren().get(0);
        assertThat(raw.getType().getCategory()).isEqualTo(BlockType.Category.RAW);
        assertThat(raw.getRawXml()).contains("<object").contains("video object");
    }

    @Test
    void knownElementInWrongPositionBecomesRawBlock() {
        // step is valid DITA-profile type but not allowed directly in conbody
        AuthorDocument doc = importXml("""
                <concept id="c"><title>T</title><conbody>
                <steps><step><cmd>x</cmd></step></steps>
                </conbody></concept>""");
        AuthorBlock raw = doc.getRoot().getChildren().get(1).getChildren().get(0);
        assertThat(raw.getType().getCategory()).isEqualTo(BlockType.Category.RAW);
    }

    @Test
    void interleavedMixedContentFallsBackToRaw() {
        AuthorDocument doc = importXml("""
                <concept id="c"><title>T</title><conbody>
                <p>before <ul><li>x</li></ul> after</p>
                </conbody></concept>""");
        AuthorBlock block = doc.getRoot().getChildren().get(1).getChildren().get(0);
        assertThat(block.getType().getCategory()).isEqualTo(BlockType.Category.RAW);
        assertThat(block.getRawXml()).contains("before").contains("after");
    }

    @Test
    void listItemWithTextThenNestedListIsStructured() {
        AuthorDocument doc = importXml("""
                <concept id="c"><title>T</title><conbody>
                <ul><li>item text<ul><li>nested</li></ul></li></ul>
                </conbody></concept>""");
        AuthorBlock li = doc.getRoot().getChildren().get(1)
                .getChildren().get(0).getChildren().get(0);
        assertThat(li.getType().getName()).isEqualTo("li");
        assertThat(li.getPlainText()).isEqualTo("item text");
        assertThat(li.getChildren()).extracting(b -> b.getType().getName()).containsExactly("ul");
    }

    @Test
    void commentsBetweenBlocksAttachToFollowingBlock() {
        AuthorDocument doc = importXml("""
                <concept id="c"><title>T</title><conbody>
                <p>one</p>
                <!-- review this -->
                <p>two</p>
                <!-- trailing note -->
                </conbody></concept>""");
        AuthorBlock conbody = doc.getRoot().getChildren().get(1);
        assertThat(conbody.getChildren()).hasSize(2);
        assertThat(conbody.getChildren().get(1).getLeadingAux())
                .containsExactly(XmlAuxNode.comment(" review this "));
        assertThat(conbody.getTrailingAux())
                .containsExactly(XmlAuxNode.comment(" trailing note "));
    }

    @Test
    void commentInsideRunningTextFallsBackToRaw() {
        AuthorDocument doc = importXml("""
                <concept id="c"><title>T</title><conbody>
                <p>text <!-- inline comment --> more</p>
                </conbody></concept>""");
        AuthorBlock block = doc.getRoot().getChildren().get(1).getChildren().get(0);
        assertThat(block.getType().getCategory()).isEqualTo(BlockType.Category.RAW);
    }

    @Test
    void imageImportsAsVoidBlockWithAltAttribute() {
        AuthorDocument doc = importXml("""
                <concept id="c"><title>T</title><conbody>
                <fig><title>F</title><image href="pic.png"><alt>a picture</alt></image></fig>
                </conbody></concept>""");
        AuthorBlock image = doc.getRoot().getChildren().get(1)
                .getChildren().get(0).getChildren().get(1);
        assertThat(image.getType().getName()).isEqualTo("image");
        assertThat(image.getAttribute("href")).isEqualTo("pic.png");
        assertThat(image.getAttribute(DitaBlockAdapter.IMAGE_ALT_ATTR)).isEqualTo("a picture");
        assertThat(image.getChildren()).isEmpty();
    }

    @Test
    void unknownRootBecomesRawDocument() {
        AuthorDocument doc = importXml("<learningContent id='t'><title>x</title></learningContent>");
        assertThat(doc.getRoot().getType().getCategory()).isEqualTo(BlockType.Category.RAW);
        assertThat(doc.getRoot().getRawXml()).contains("learningContent");
    }

    @Test
    void noteWithDirectTextIsStructuredText() {
        AuthorDocument doc = importXml("""
                <concept id="c"><title>T</title><conbody>
                <note type="tip">Just some advice.</note>
                </conbody></concept>""");
        AuthorBlock note = doc.getRoot().getChildren().get(1).getChildren().get(0);
        assertThat(note.getType().getName()).isEqualTo("note");
        assertThat(note.getAttribute("type")).isEqualTo("tip");
        assertThat(note.getPlainText()).isEqualTo("Just some advice.");
    }
}
