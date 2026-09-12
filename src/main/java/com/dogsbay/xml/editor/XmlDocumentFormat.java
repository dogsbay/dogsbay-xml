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

package com.dogsbay.xml.editor;

/**
 * Document format for XML files (.xml, .xsd, .xsl, .xslt, .xhtml, .svg,
 * .wsdl, .rng, .sch, .dita, .ditamap, .fo, .rss, .atom, .pom, .project,
 * .classpath, .fxml, .iml, etc.).
 *
 * XML is also used as the fallback for files that appear to be XML-based
 * (detected by content inspection in DogsBayDocument).
 */
public class XmlDocumentFormat implements DocumentFormat {

    @Override
    public String getName() {
        return "XML";
    }

    @Override
    public String getContentType() {
        return "text/xml";
    }

    @Override
    public String[] getExtensions() {
        return new String[] {
            "xml", "xsd", "xsl", "xslt", "xhtml", "svg",
            "wsdl", "rng", "rnc", "sch",
            "dita", "ditamap", "ditaval",
            "fo", "rss", "atom",
            "pom", "project", "classpath", "fxml", "iml",
            "jnlp", "tld", "xul", "mathml", "mml",
            "plugin"
        };
    }

    @Override
    public DogsBayEditorKit createEditorKit(XmlEditorPane pane) {
        return new XmlEditorKit(pane, null);
    }

    /**
     * DITA topics and maps get a styled preview rendering; all other XML
     * returns null so the preview falls back to the stylesheet-PI / default
     * XSLT path in HtmlPreviewManager. Detection is content-based (root
     * element or OASIS DITA doctype) rather than a separate DocumentFormat,
     * so .dita files keep the XML format's grammar/toolbar behavior.
     */
    @Override
    public String convertToHtml(String sourceText, java.io.File baseDir) {
        return convertToHtml(sourceText, baseDir, null);
    }

    @Override
    public String convertToHtml(String sourceText, java.io.File baseDir,
                                com.dogsbay.xml.browser.PreviewOptions options) {
        if (com.dogsbay.xml.browser.DitaConverter.isDita(sourceText)) {
            return com.dogsbay.xml.browser.DitaConverter.convert(sourceText, baseDir, options);
        }
        return null;
    }

    @Override
    public boolean isXmlBased() {
        return true;
    }

    @Override
    public boolean supportsDesigner() {
        return true;
    }

    @Override
    public boolean supportsViewer() {
        return true;
    }

    @Override
    public boolean supportsValidation() {
        return true;
    }
}
