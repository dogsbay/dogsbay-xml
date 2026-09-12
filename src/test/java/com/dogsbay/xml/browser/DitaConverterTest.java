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

package com.dogsbay.xml.browser;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import org.junit.jupiter.api.Test;

import com.dogsbay.xml.editor.XmlDocumentFormat;

/**
 * Tests for the DITA styled preview converter. Pure string-to-string —
 * no GUI, no network, no catalogs.
 */
class DitaConverterTest {

    // -------------------------------------------------------------------------
    // isDita sniffing
    // -------------------------------------------------------------------------

    @Test
    void isDita_recognizesTopicRoots() {
        assertTrue(DitaConverter.isDita("<concept id=\"c\"><title>T</title></concept>"));
        assertTrue(DitaConverter.isDita("<task id=\"t\"><title>T</title></task>"));
        assertTrue(DitaConverter.isDita("<map><topicref href=\"a.dita\"/></map>"));
        assertTrue(DitaConverter.isDita("<bookmap></bookmap>"));
    }

    @Test
    void isDita_recognizesOasisDoctypeEvenWithUnusualRoot() {
        String xml = "<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE myTopic PUBLIC \"-//OASIS//DTD DITA Topic//EN\" \"topic.dtd\">\n"
                + "<myTopic/>";
        assertTrue(DitaConverter.isDita(xml));
    }

    @Test
    void isDita_skipsPrologCommentsAndDoctype() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!-- a comment with <fake> tag -->\n"
                + "<!DOCTYPE concept PUBLIC \"-//ME//DTD Mine//EN\" \"mine.dtd\" [<!ENTITY x \"y\">]>\n"
                + "<concept id=\"c\"/>";
        assertTrue(DitaConverter.isDita(xml));
    }

    @Test
    void isDita_rejectsNonDita() {
        assertFalse(DitaConverter.isDita("<project><modelVersion>4.0</modelVersion></project>"));
        assertFalse(DitaConverter.isDita("# Markdown heading\n\ntext"));
        assertFalse(DitaConverter.isDita(""));
        assertFalse(DitaConverter.isDita(null));
        assertFalse(DitaConverter.isDita("<html><body/></html>"));
    }

    // -------------------------------------------------------------------------
    // Topic rendering
    // -------------------------------------------------------------------------

    @Test
    void convert_taskTopic_rendersSteps() {
        String xml = "<task id=\"t1\"><title>Install the app</title>"
                + "<taskbody>"
                + "<prereq><p>Download it first.</p></prereq>"
                + "<steps>"
                + "<step><cmd>Open the installer</cmd><info><p>Double-click it.</p></info></step>"
                + "<step><cmd>Click Next</cmd></step>"
                + "</steps>"
                + "<result><p>The app is installed.</p></result>"
                + "</taskbody></task>";
        String html = DitaConverter.convert(xml, null);

        assertTrue(html.contains("<h1>Install the app</h1>"), "title as h1");
        assertTrue(html.contains("class=\"steps\""), "steps as styled ol");
        assertTrue(html.contains("Open the installer"), "cmd text present");
        assertTrue(html.contains("class=\"cmd\""), "cmd styled");
        assertTrue(html.contains("Before you begin"), "prereq labeled");
        assertTrue(html.contains("Result"), "result labeled");
    }

    @Test
    void convert_noteTypes_getMatchingCssClasses() {
        String xml = "<concept id=\"c\"><title>T</title><conbody>"
                + "<note><p>plain</p></note>"
                + "<note type=\"warning\"><p>hot</p></note>"
                + "<note type=\"tip\"><p>handy</p></note>"
                + "</conbody></concept>";
        String html = DitaConverter.convert(xml, null);

        assertTrue(html.contains("note-note"), "default note class");
        assertTrue(html.contains("note-warning"), "warning class");
        assertTrue(html.contains("note-tip"), "tip class");
        assertTrue(html.contains(">Warning<"), "warning label");
    }

    @Test
    void convert_relativeImageHref_resolvesAgainstBaseDir() {
        String xml = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p><image href=\"images/screen.png\"/></p>"
                + "</conbody></concept>";
        File baseDir = new File(System.getProperty("java.io.tmpdir"));
        String html = DitaConverter.convert(xml, baseDir);

        String expectedPrefix = baseDir.toURI().toString().replaceAll("/$", "");
        assertTrue(html.contains(expectedPrefix + "/images/screen.png"),
                "image src should be absolute file URL. html: " + extractImg(html));
    }

    @Test
    void convert_absoluteImageHref_leftAlone() {
        String xml = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p><image href=\"https://example.com/pic.png\"/></p>"
                + "</conbody></concept>";
        String html = DitaConverter.convert(xml, new File("/tmp"));
        assertTrue(html.contains("https://example.com/pic.png"));
        assertFalse(html.contains("file:") && html.contains("example.com"),
                "absolute URL must not be prefixed");
    }

    @Test
    void convert_unresolvableConref_rendersVisibleBadge() {
        // No baseDir — the file reference cannot resolve, so the badge shows
        String xml = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p conref=\"shared.dita#shared/legal\"/>"
                + "</conbody></concept>";
        String html = DitaConverter.convert(xml, null);

        assertTrue(html.contains("conref-badge"), "badge rendered");
        assertTrue(html.contains("shared.dita#shared/legal"), "target shown");
    }

    @Test
    void convert_sameDocumentConref_transcludes() {
        String xml = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p id=\"src\">Original text here.</p>"
                + "<p conref=\"#c/src\"/>"
                + "</conbody></concept>";
        String html = DitaConverter.convert(xml, null);

        int first = html.indexOf("Original text here.");
        int second = html.indexOf("Original text here.", first + 1);
        assertTrue(first >= 0 && second > first,
                "content appears twice (source + transclusion): " + html);
        assertTrue(html.contains("class=\"conref-included\""), "reuse marker present");
        assertFalse(html.contains("class=\"conref-badge\""), "no badge once resolved");
    }

    @Test
    void convert_unknownSpecializedElement_childrenPreserved() {
        String xml = "<concept id=\"c\"><title>T</title><conbody>"
                + "<wibble><p>survives</p></wibble>"
                + "</conbody></concept>";
        String html = DitaConverter.convert(xml, null);
        assertTrue(html.contains("survives"), "unknown element content passes through");
    }

    @Test
    void convert_inlineMarkup() {
        String xml = "<concept id=\"c\"><title>T</title><conbody><p>"
                + "Press <uicontrol>OK</uicontrol> in "
                + "<menucascade><uicontrol>File</uicontrol><uicontrol>Save</uicontrol></menucascade>, "
                + "edit <filepath>/etc/app.conf</filepath> and run <codeph>make</codeph>."
                + "</p></conbody></concept>";
        String html = DitaConverter.convert(xml, null);

        assertTrue(html.contains("class=\"uicontrol\""));
        assertTrue(html.contains("File"));
        assertTrue(html.contains("filepath"));
        assertTrue(html.contains("<code>make</code>"));
    }

    @Test
    void convert_xref_rendersAsInertLink() {
        String xml = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p>See <xref href=\"other.dita\">the other topic</xref>.</p>"
                + "</conbody></concept>";
        String html = DitaConverter.convert(xml, null);

        assertTrue(html.contains("the other topic"));
        assertTrue(html.contains("title=\"other.dita\""), "target in tooltip");
    }

    @Test
    void convert_simpletable() {
        String xml = "<reference id=\"r\"><title>Options</title><refbody>"
                + "<simpletable>"
                + "<sthead><stentry>Flag</stentry><stentry>Meaning</stentry></sthead>"
                + "<strow><stentry>-v</stentry><stentry>verbose</stentry></strow>"
                + "</simpletable></refbody></reference>";
        String html = DitaConverter.convert(xml, null);

        assertTrue(html.contains("<th>Flag</th>"));
        assertTrue(html.contains("<td>-v</td>"));
    }

    @Test
    void convert_doctypePresent_noDtdFetchFailure() {
        // The DTD doesn't exist on disk; the hardened parser must not try
        // to resolve it (and must not throw).
        String xml = "<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE concept PUBLIC \"-//OASIS//DTD DITA Concept//EN\" \"no-such.dtd\">\n"
                + "<concept id=\"c\"><title>Works offline</title>"
                + "<conbody><p>body</p></conbody></concept>";
        String html = DitaConverter.convert(xml, null);
        assertTrue(html.contains("Works offline"), "renders despite unresolvable DTD");
        // Match the error container div, not the bare string — the CSS block
        // always contains ".preview-error" as a selector.
        assertFalse(html.contains("<div class=\"preview-error\">"), "no error doc");
    }

    @Test
    void convert_malformedXml_returnsErrorDocNotException() {
        String html = DitaConverter.convert("<concept><title>broken", null);
        assertNotNull(html);
        assertTrue(html.contains("<div class=\"preview-error\">"), "styled error doc");
    }

    @Test
    void convert_profilingAttributes_passThroughAsData() {
        String xml = "<concept id=\"c\"><title>T</title><conbody>"
                + "<p audience=\"admin\" platform=\"linux\">conditional text</p>"
                + "</conbody></concept>";
        String html = DitaConverter.convert(xml, null);

        assertTrue(html.contains("data-audience=\"admin\""));
        assertTrue(html.contains("data-platform=\"linux\""));
    }

    // -------------------------------------------------------------------------
    // Map TOC rendering
    // -------------------------------------------------------------------------

    @Test
    void convert_map_rendersNestedToc() {
        String xml = "<map><title>User Guide</title>"
                + "<topicref href=\"intro.dita\" navtitle=\"Introduction\">"
                + "<topicref href=\"details.dita\" navtitle=\"Details\"/>"
                + "</topicref>"
                + "<topicref href=\"sub.ditamap\" format=\"ditamap\"/>"
                + "</map>";
        String html = DitaConverter.convert(xml, null);

        assertTrue(html.contains("User Guide"), "map title");
        assertTrue(html.contains("Introduction"), "navtitle used");
        assertTrue(html.contains("Details"), "nested entry present");
        assertTrue(html.contains("submap"), "submap badge");
        assertTrue(html.contains("class=\"toc\""), "toc list");
    }

    @Test
    void convert_map_topicmetaNavtitleAndKeydefSkipped() {
        String xml = "<map>"
                + "<keydef keys=\"product\" href=\"p.dita\"/>"
                + "<topicref href=\"a.dita\">"
                + "<topicmeta><navtitle>From Meta</navtitle></topicmeta>"
                + "</topicref>"
                + "</map>";
        String html = DitaConverter.convert(xml, null);

        assertTrue(html.contains("From Meta"), "topicmeta navtitle used");
        assertFalse(html.contains("p.dita"), "keydef not a TOC entry");
    }

    // -------------------------------------------------------------------------
    // Format routing (XmlDocumentFormat hook)
    // -------------------------------------------------------------------------

    @Test
    void xmlFormat_routesDitaToConverter() {
        XmlDocumentFormat format = new XmlDocumentFormat();
        String html = format.convertToHtml(
                "<concept id=\"c\"><title>Routed</title></concept>", null);
        assertNotNull(html, "DITA content must be converted");
        assertTrue(html.contains("Routed"));
    }

    @Test
    void xmlFormat_returnsNullForPlainXml() {
        XmlDocumentFormat format = new XmlDocumentFormat();
        assertNull(format.convertToHtml("<project><x/></project>", null),
                "non-DITA XML falls through to default preview path");
    }

    // -------------------------------------------------------------------------

    private static String extractImg(String html) {
        int i = html.indexOf("<img");
        return i < 0 ? "(no img)" : html.substring(i, Math.min(html.length(), i + 200));
    }
}
