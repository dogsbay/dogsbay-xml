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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.dom4j.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.AuthorDocument;
import com.dogsbay.xml.author.model.BlockType;

/**
 * The lossless-round-trip contract, checked over a corpus of real topics
 * (dita-tutorial project) plus crafted edge cases:
 *
 * <ol>
 * <li><b>Model equality:</b> re-importing the export yields the identical
 *     block structure — nothing the model captures is lost on export.</li>
 * <li><b>Fixpoint:</b> export(import(export(x))) == export(x) byte-for-byte —
 *     a document opened and saved twice does not keep changing.</li>
 * </ol>
 */
class DitaRoundTripTest {

    private final DitaBlockAdapter adapter = new DitaBlockAdapter();

    static Stream<Path> corpusTopics() throws IOException {
        Path corpus = Path.of("src/test/resources/author/corpus");
        return Files.list(corpus).filter(p -> p.toString().endsWith(".dita")).sorted();
    }

    @ParameterizedTest
    @MethodSource("corpusTopics")
    void corpusRoundTripsLosslessly(Path topic) {
        Element original = AdapterTestSupport.parseResource("/author/corpus/" + topic.getFileName())
                .getRootElement();
        assertRoundTrip(original);
    }

    @ParameterizedTest
    @MethodSource("corpusTopics")
    void corpusImportsWithStructuredRoot(Path topic) {
        Element original = AdapterTestSupport.parseResource("/author/corpus/" + topic.getFileName())
                .getRootElement();
        AuthorDocument doc = adapter.importDocument(original);
        assertThat(doc.getRoot().getType().getCategory())
                .as("%s root should import structured", topic.getFileName())
                .isNotEqualTo(BlockType.Category.RAW);
    }

    /** Tutorial task topics are plain DITA — they should need almost no raw fallback. */
    @ParameterizedTest
    @MethodSource("corpusTopics")
    void corpusRawFallbackStaysRare(Path topic) {
        Element original = AdapterTestSupport.parseResource("/author/corpus/" + topic.getFileName())
                .getRootElement();
        AuthorDocument doc = adapter.importDocument(original);
        int total = count(doc.getRoot(), false);
        int raw = count(doc.getRoot(), true);
        assertThat(raw)
                .as("%s: %d of %d blocks fell back to raw", topic.getFileName(), raw, total)
                .isLessThanOrEqualTo(total / 5);
    }

    @Test
    void theStandardNamespaceHeaderImportsStructuredAndExportsDeclared() {
        Element original = AdapterTestSupport.parse("<concept id='c' xmlns:ditaarch='http://dita.oasis-open.org/architecture/2005/'"
                + " ditaarch:DITAArchVersion='1.3'><title>T</title><conbody><p>The <ph>quick</ph> fox.</p></conbody></concept>");
        AuthorDocument doc = adapter.importDocument(original);
        assertThat(doc.getRoot().getType().getCategory()).isNotEqualTo(BlockType.Category.RAW);
        assertThat(doc.getRoot().getAttributes()).containsEntry("#xmlns:ditaarch", "http://dita.oasis-open.org/architecture/2005/")
                .containsEntry("ditaarch:DITAArchVersion", "1.3");
        String xml = adapter.exportDocument(doc).asXML();
        assertThat(xml).contains("xmlns:ditaarch=\"http://dita.oasis-open.org/architecture/2005/\"")
                .contains("ditaarch:DITAArchVersion=\"1.3\"").contains("<ph>quick</ph>");
    }

    @Test
    void phrasesInsideOtherInlinesAndDefaultNamespacesFallBackToRaw() {
        AuthorDocument nested = adapter.importDocument(AdapterTestSupport.parse(
                "<concept id='c'><title>T</title><conbody><p>A <b><ph>x</ph></b> z</p></conbody></concept>"));
        assertThat(count(nested.getRoot(), true)).as("the paragraph is a chip, not restructured").isEqualTo(1);
        AuthorDocument defaultNs = adapter.importDocument(AdapterTestSupport.parse(
                "<concept xmlns='urn:d' id='c'><title>T</title><conbody><p>x</p></conbody></concept>"));
        assertThat(defaultNs.getRoot().getType().getCategory()).isEqualTo(BlockType.Category.RAW);
        String phrase = adapter.exportDocument(adapter.importDocument(AdapterTestSupport.parse(
                "<concept id='c'><title>T</title><conbody><p><ph><b>qui</b>ck</ph> fox</p></conbody></concept>"))).asXML();
        assertThat(phrase).contains("<p><ph><b>qui</b>ck</ph> fox</p>");
    }

    @ParameterizedTest
    @MethodSource("edgeCases")
    void edgeCasesRoundTripLosslessly(String xml) {
        assertRoundTrip(AdapterTestSupport.parse(xml));
    }

    static Stream<String> edgeCases() {
        return Stream.of(
                // unknown elements, preserved verbatim
                "<concept id='c'><title>T</title><conbody><dl><dlentry><dt>a</dt><dd>b</dd></dlentry></dl></conbody></concept>",
                // interleaved mixed content
                "<concept id='c'><title>T</title><conbody><p>a <ul><li>x</li></ul> b</p></conbody></concept>",
                // comments and PIs between and around blocks
                "<concept id='c'><title>T</title><conbody><!-- one --><p>x</p><?pi data?><p>y</p><!-- end --></conbody></concept>",
                // attributes everywhere, including profiling
                "<task id='t' audience='admin' xml:lang='en'><title outputclass='big'>T</title>"
                        + "<taskbody><steps><step importance='required'><cmd>x</cmd></step></steps></taskbody></task>",
                // nested inline + xref + keyref keyword + empty keyword
                "<concept id='c'><title>T</title><conbody><p>See <xref href='a.dita' format='dita'>this</xref>"
                        + " and <b><i>styled</i></b> with <keyword keyref='k'/> plus <ph keyref='p2'/>.</p></conbody></concept>",
                // codeblock with significant whitespace
                "<concept id='c'><title>T</title><conbody><codeblock outputclass='language-java'>a\n  b\nc</codeblock></conbody></concept>",
                // tables and figures
                "<reference id='r'><title>T</title><refbody><simpletable><sthead><stentry>H</stentry></sthead>"
                        + "<strow><stentry>v1</stentry></strow></simpletable>"
                        + "<section><fig><title>F</title><image href='p.png'><alt>alt text</alt></image></fig></section></refbody></reference>",
                // note variants
                "<concept id='c'><title>T</title><conbody><note type='warning'>Careful.</note>"
                        + "<note><p>Wrapped.</p></note></conbody></concept>",
                // plain phrases: kept, and two adjacent ones stay two
                "<concept id='c'><title>T</title><conbody><p>The <ph>quick</ph> fox and <ph>two</ph><ph>three</ph>.</p></conbody></concept>",
                // a phrase holding styled text is one element again on export
                "<concept id='c'><title>T</title><conbody><p>A <ph>styled <b>phrase</b> and <uicontrol>OK</uicontrol></ph> here.</p></conbody></concept>",
                // partial styling inside a phrase, as the UI produces it
                "<concept id='c'><title>T</title><conbody><p><ph><b>qui</b>ck</ph> fox</p></conbody></concept>",
                // an empty phrase is content too
                "<concept id='c'><title>T</title><conbody><p>Total: <ph/> USD</p></conbody></concept>",
                // a phrase inside another inline cannot be put back there: verbatim
                "<concept id='c'><title>T</title><conbody><p>A <b><ph>x</ph></b> z and <ph keyref='k'>a <ph>b</ph></ph>.</p></conbody></concept>",
                // the wider vocabulary: nested inlines, inline attributes, footnotes, index terms
                "<task id='t'><title>T<indexterm>idx</indexterm></title><taskbody><steps><step><cmd>Choose "
                        + "<menucascade><uicontrol>File</uicontrol><uicontrol>Export</uicontrol></menucascade>"
                        + " then <b outputclass='hot'><codeph>save()</codeph></b> or <sup>2</sup><fn>Note.</fn>.</cmd>"
                        + "<choicetable><chhead><choptionhd>Option</choptionhd><chdeschd>Does</chdeschd></chhead>"
                        + "<chrow><choption>A</choption><chdesc>a</chdesc></chrow></choicetable></step></steps></taskbody></task>",
                "<reference id='r'><title>T</title><refbody><table><tgroup cols='1'><tbody><row><entry>x</entry></row>"
                        + "</tbody></tgroup></table><refsyn><p>y</p></refsyn></refbody></reference>",
                "<concept id='c'><title>T</title><conbody><lq>q</lq><pre>a\n b</pre><sl><sli>s</sli></sl>"
                        + "<parml><plentry><pt>p</pt><pd>d</pd></plentry></parml><draft-comment>n</draft-comment>"
                        + "<section><sectiondiv><p>x</p></sectiondiv></section></conbody></concept>",
                // a default namespace: verbatim rather than moving children out of it
                "<concept xmlns='urn:d' id='c'><title>T</title><conbody><p>x</p></conbody></concept>",
                // the standard namespace header, on the root and on a nested element
                "<concept id='c' xmlns:ditaarch='http://dita.oasis-open.org/architecture/2005/' ditaarch:DITAArchVersion='1.3'>"
                        + "<title>T</title><conbody><p xmlns:x='urn:x' x:role='note'>text</p></conbody></concept>");
    }

    @Test
    void anIndexHierarchyIsKeptVerbatimRatherThanFlattened() {
        // a nested same-name inline cannot be a flat run chain: the block stays raw
        assertRoundTrip(AdapterTestSupport.parse(
                "<concept id='c'><title>T</title><prolog><metadata>"
                + "<keywords><indexterm>mixing<indexterm>levels</indexterm></indexterm></keywords>"
                + "</metadata></prolog><conbody>"
                + "<p>see <indexterm>effects<indexterm>reverb</indexterm></indexterm> here</p>"
                + "</conbody></concept>"));
    }

    @Test
    void indentingLeavesPreservedWhitespaceAlone() {
        Element root = AdapterTestSupport.parse(
                "<concept id='c'><title>T</title><conbody>"
                + "<section xml:space='preserve'><ul><li>a</li></ul></section></conbody></concept>");
        com.dogsbay.xml.author.adapter.BlockXmlFormatter.indent(root, "  ");
        assertThat(root.asXML()).contains("<section xml:space=\"preserve\"><ul><li>a</li></ul></section>");
        assertThat(root.asXML()).as("the rest is still indented").contains("\n  <title>");
    }

    @Test
    void prologAndXmlSpaceRoundTrip() {
        assertRoundTrip(AdapterTestSupport.parse("""
            <concept id="c"><title>T</title><prolog><author>Ann</author><source>Notes</source>
              <copyright><copyryear year="2025"/><copyrholder>DogsBay</copyrholder></copyright>
              <critdates><created date="2024-01-01"/></critdates><permissions view="all"/>
              <metadata><audience type="user"/><category>Audio</category>
                <keywords><keyword>mix</keyword><indexterm>mixing</indexterm></keywords>
                <othermeta name="a" content="b"/><prodinfo><prodname>Studio</prodname></prodinfo></metadata>
              <resourceid id="r1"/></prolog>
            <conbody><p xml:space="preserve">a
              b</p></conbody></concept>"""));
    }

    private void assertRoundTrip(Element original) {
        AuthorDocument imported = adapter.importDocument(original);
        Element export1 = adapter.exportDocument(imported);

        // content equality: the export says what the original said, modulo whitespace
        assertThat(canonical(export1)).as("export must not drop or restructure content").isEqualTo(canonical(original));

        // model equality: nothing captured by the model is lost in export
        AuthorDocument reimported = adapter.importDocument(export1);
        assertThat(reimported.getRoot().toStructureString())
                .isEqualTo(imported.getRoot().toStructureString());

        // fixpoint: a second export is byte-identical
        Element export2 = adapter.exportDocument(reimported);
        assertThat(export2.asXML()).isEqualTo(export1.asXML());
    }

    /** Compact serialization with whitespace runs collapsed: tags, attributes and text, not layout. */
    static String canonical(Element e) {
        try {
            java.io.StringWriter out = new java.io.StringWriter();
            org.dom4j.io.OutputFormat format = org.dom4j.io.OutputFormat.createCompactFormat();
            org.dom4j.io.XMLWriter writer = new org.dom4j.io.XMLWriter(out, format);
            writer.write(e);
            writer.flush();
            return out.toString().replaceAll("\\s+", " ").replace("> <", "><").trim();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private int count(AuthorBlock block, boolean rawOnly) {
        int n = !rawOnly || block.getType().getCategory() == BlockType.Category.RAW ? 1 : 0;
        for (AuthorBlock child : block.getChildren()) {
            n += count(child, rawOnly);
        }
        return n;
    }
}
