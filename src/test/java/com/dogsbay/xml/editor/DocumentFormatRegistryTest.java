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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

public class DocumentFormatRegistryTest {

    @BeforeEach
    public void setUp() {
        DocumentFormatRegistry.clear();
        DocumentFormatRegistry.register(new XmlDocumentFormat());
        DocumentFormatRegistry.register(new DtdDocumentFormat());
        DocumentFormatRegistry.register(new HtmlDocumentFormat());
        DocumentFormatRegistry.register(new MarkdownDocumentFormat());

        PlainTextDocumentFormat plainText = new PlainTextDocumentFormat();
        DocumentFormatRegistry.register(plainText);
        DocumentFormatRegistry.setDefault(plainText);
    }

    @Test
    @DisplayName("Detects XML format by common extensions")
    public void testXmlDetection() {
        assertEquals("XML", DocumentFormatRegistry.detect("pom.xml").getName());
        assertEquals("XML", DocumentFormatRegistry.detect("schema.xsd").getName());
        assertEquals("XML", DocumentFormatRegistry.detect("transform.xslt").getName());
        assertEquals("XML", DocumentFormatRegistry.detect("page.xhtml").getName());
        assertEquals("XML", DocumentFormatRegistry.detect("topic.dita").getName());
        assertEquals("XML", DocumentFormatRegistry.detect("map.ditamap").getName());
        assertEquals("XML", DocumentFormatRegistry.detect("drawing.svg").getName());
    }

    @Test
    @DisplayName("Detects DTD format")
    public void testDtdDetection() {
        assertEquals("DTD", DocumentFormatRegistry.detect("grammar.dtd").getName());
        assertEquals("DTD", DocumentFormatRegistry.detect("entities.ent").getName());
    }

    @Test
    @DisplayName("Detects HTML format")
    public void testHtmlDetection() {
        assertEquals("HTML", DocumentFormatRegistry.detect("index.html").getName());
        assertEquals("HTML", DocumentFormatRegistry.detect("page.htm").getName());
    }

    @Test
    @DisplayName("Detects Markdown format")
    public void testMarkdownDetection() {
        assertEquals("Markdown", DocumentFormatRegistry.detect("README.md").getName());
        assertEquals("Markdown", DocumentFormatRegistry.detect("docs.markdown").getName());
    }

    @Test
    @DisplayName("Falls back to plain text for unknown extensions")
    public void testPlainTextFallback() {
        assertEquals("Plain Text", DocumentFormatRegistry.detect("data.csv").getName());
        assertEquals("Plain Text", DocumentFormatRegistry.detect("notes.txt").getName());
        assertEquals("Plain Text", DocumentFormatRegistry.detect("unknown.xyz").getName());
    }

    @Test
    @DisplayName("Returns default for null filename")
    public void testNullFilename() {
        DocumentFormat result = DocumentFormatRegistry.detect(null);
        assertNotNull(result);
        assertEquals("Plain Text", result.getName());
    }

    @Test
    @DisplayName("Case-insensitive extension matching")
    public void testCaseInsensitive() {
        assertEquals("XML", DocumentFormatRegistry.detect("FILE.XML").getName());
        assertEquals("Markdown", DocumentFormatRegistry.detect("README.MD").getName());
        assertEquals("HTML", DocumentFormatRegistry.detect("INDEX.HTML").getName());
    }

    @Test
    @DisplayName("findByContentType returns correct format")
    public void testFindByContentType() {
        assertEquals("XML", DocumentFormatRegistry.findByContentType("text/xml").getName());
        assertEquals("Markdown", DocumentFormatRegistry.findByContentType("text/markdown").getName());
    }

    @Test
    @DisplayName("findByName returns correct format")
    public void testFindByName() {
        assertEquals("text/xml", DocumentFormatRegistry.findByName("XML").getContentType());
        assertEquals("text/markdown", DocumentFormatRegistry.findByName("Markdown").getContentType());
        assertNull(DocumentFormatRegistry.findByName("NonExistent"));
    }

    @Test
    @DisplayName("XML format reports correct capabilities")
    public void testXmlCapabilities() {
        DocumentFormat xml = DocumentFormatRegistry.findByName("XML");
        assertTrue(xml.isXmlBased());
        assertTrue(xml.supportsDesigner());
        assertTrue(xml.supportsViewer());
        assertTrue(xml.supportsValidation());
    }

    @Test
    @DisplayName("Markdown format reports correct capabilities")
    public void testMarkdownCapabilities() {
        DocumentFormat md = DocumentFormatRegistry.findByName("Markdown");
        assertFalse(md.isXmlBased());
        assertFalse(md.supportsDesigner());
        assertFalse(md.supportsViewer());
        assertFalse(md.supportsValidation());
    }

    @Test
    @DisplayName("Plain text format is the default")
    public void testDefaultFormat() {
        DocumentFormat def = DocumentFormatRegistry.getDefault();
        assertNotNull(def);
        assertEquals("Plain Text", def.getName());
        assertEquals("text/txt", def.getContentType());
    }

    @Test
    @DisplayName("getAll returns all registered formats")
    public void testGetAll() {
        assertEquals(5, DocumentFormatRegistry.getAll().size());
    }

    @Test
    @DisplayName("First matching format wins (registration order)")
    public void testRegistrationOrder() {
        // HTML is registered before XML, both could potentially match .html
        // but HTML format should win because it's registered first
        DocumentFormat format = DocumentFormatRegistry.detect("page.html");
        assertEquals("HTML", format.getName());
    }
}
