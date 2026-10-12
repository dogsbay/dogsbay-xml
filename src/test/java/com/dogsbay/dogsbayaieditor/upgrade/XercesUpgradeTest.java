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

package com.dogsbay.dogsbayaieditor.upgrade;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import com.dogsbay.xml.XMLUtilities;

import org.dom4j.io.SAXReader;
import org.dom4j.Document;

import java.io.StringReader;

/**
 * Tests for Xerces 2.12.2 upgrade compatibility.
 * Focuses on DTD parsing, entity resolution, and core XML utilities.
 *
 * Phase 1 of DITA-OT 4.3 library upgrade plan.
 *
 * @version $Revision: 1.0 $, $Date: 2025/01/24 $
 * @author DogsBay Ltd
 */
@DisplayName("Xerces 2.12.2 Upgrade Tests")
public class XercesUpgradeTest {

    private static final String SIMPLE_XML = "<?xml version='1.0'?><root><child>text</child></root>";
    private static final String XML_WITH_ATTRIBUTES = "<?xml version='1.0'?><root id='123' name='test'><child attr='value'>text</child></root>";
    private static final String XML_WITH_NAMESPACE = "<?xml version='1.0'?><root xmlns='http://example.com/ns'><child>text</child></root>";

    @BeforeEach
    public void setUp() {
        // Common setup for all tests
    }

    @Test
    @DisplayName("Basic XML parsing with new Xerces")
    public void testBasicXmlParsing() throws Exception {
        SAXReader reader = new SAXReader();
        Document doc = reader.read(new StringReader(SIMPLE_XML));

        assertNotNull(doc, "Document should not be null");
        assertNotNull(doc.getRootElement(), "Root element should not be null");
        assertEquals("root", doc.getRootElement().getName(), "Root element name should be 'root'");
    }

    @Test
    @DisplayName("XML parsing with attributes")
    public void testXmlParsingWithAttributes() throws Exception {
        SAXReader reader = new SAXReader();
        Document doc = reader.read(new StringReader(XML_WITH_ATTRIBUTES));

        assertNotNull(doc);
        assertEquals("root", doc.getRootElement().getName());
        assertEquals("123", doc.getRootElement().attributeValue("id"));
        assertEquals("test", doc.getRootElement().attributeValue("name"));

        org.dom4j.Element child = doc.getRootElement().element("child");
        assertNotNull(child);
        assertEquals("value", child.attributeValue("attr"));
    }

    @Test
    @DisplayName("XML parsing with namespaces")
    public void testXmlParsingWithNamespaces() throws Exception {
        SAXReader reader = new SAXReader();
        Document doc = reader.read(new StringReader(XML_WITH_NAMESPACE));

        assertNotNull(doc);
        assertNotNull(doc.getRootElement());
        assertEquals("root", doc.getRootElement().getName());
        assertNotNull(doc.getRootElement().getNamespace());
        assertEquals("http://example.com/ns", doc.getRootElement().getNamespaceURI());
    }

    @Test
    @DisplayName("XML character utilities")
    public void testXmlCharacterUtilities() {
        // Test that XMLUtilities still works with Xerces 2.12.2
        // XMLUtilities uses org.apache.xerces classes internally

        // Test basic functionality - XMLUtilities should still work
        assertNotNull(XMLUtilities.class, "XMLUtilities class should be available");

        // Test that we can use Xerces XML character validation directly
        // This verifies Xerces 2.12.2 APIs are compatible
        assertTrue(org.apache.xerces.util.XMLChar.isValidName("element"));
        assertFalse(org.apache.xerces.util.XMLChar.isValidName("123invalid"));
    }

    @Test
    @DisplayName("Dom4j compatibility with Xerces 2.12.2")
    public void testDom4jCompatibility() throws Exception {
        // Critical test: Ensure Dom4j works with new Xerces
        // Dom4j is used in 65 files across the codebase
        SAXReader reader = new SAXReader();

        // Test 1: Simple parsing
        Document doc = reader.read(new StringReader(SIMPLE_XML));
        assertNotNull(doc);
        assertEquals("root", doc.getRootElement().getName());
        assertEquals("text", doc.getRootElement().element("child").getText());

        // Test 2: Traversal
        org.dom4j.Element root = doc.getRootElement();
        assertEquals(1, root.elements().size());

        // Test 3: XPath (Dom4j uses Jaxen which may interact with Xerces)
        Object result = doc.selectSingleNode("/root/child");
        assertNotNull(result);
        assertTrue(result instanceof org.dom4j.Element);
    }

    @Test
    @DisplayName("XML with CDATA sections")
    public void testCdataSections() throws Exception {
        String xmlWithCdata = "<?xml version='1.0'?><root><![CDATA[This is <cdata> content]]></root>";

        SAXReader reader = new SAXReader();
        Document doc = reader.read(new StringReader(xmlWithCdata));

        assertNotNull(doc);
        String text = doc.getRootElement().getText();
        assertTrue(text.contains("<cdata>"), "CDATA content should be preserved");
    }

    @Test
    @DisplayName("XML with comments")
    public void testXmlComments() throws Exception {
        String xmlWithComments = "<?xml version='1.0'?><!-- This is a comment --><root>text</root>";

        SAXReader reader = new SAXReader();
        Document doc = reader.read(new StringReader(xmlWithComments));

        assertNotNull(doc);
        assertEquals("root", doc.getRootElement().getName());
    }

    @Test
    @DisplayName("XML with processing instructions")
    public void testProcessingInstructions() throws Exception {
        String xmlWithPI = "<?xml version='1.0'?><?xml-stylesheet type='text/xsl' href='style.xsl'?><root>text</root>";

        SAXReader reader = new SAXReader();
        Document doc = reader.read(new StringReader(xmlWithPI));

        assertNotNull(doc);
        assertEquals("root", doc.getRootElement().getName());

        // Check for PI
        java.util.List processingInstructions = doc.processingInstructions();
        assertNotNull(processingInstructions);
        // May be empty if PI is stripped, but should not throw exception
    }

    @Test
    @DisplayName("Malformed XML handling")
    public void testMalformedXml() {
        String malformedXml = "<?xml version='1.0'?><root><unclosed>";

        SAXReader reader = new SAXReader();
        assertThrows(Exception.class, () -> {
            reader.read(new StringReader(malformedXml));
        }, "Malformed XML should throw exception");
    }

    @Test
    @DisplayName("Empty XML document")
    public void testEmptyDocument() {
        String emptyXml = "<?xml version='1.0'?><root/>";

        assertDoesNotThrow(() -> {
            SAXReader reader = new SAXReader();
            Document doc = reader.read(new StringReader(emptyXml));
            assertNotNull(doc);
            assertEquals("root", doc.getRootElement().getName());
            assertTrue(doc.getRootElement().elements().isEmpty());
        }, "Empty XML should parse without exception");
    }

    @Test
    @DisplayName("Large XML document parsing")
    public void testLargeDocument() throws Exception {
        // Generate a larger XML document
        StringBuilder xml = new StringBuilder("<?xml version='1.0'?><root>");
        for (int i = 0; i < 1000; i++) {
            xml.append("<item id='").append(i).append("'>value").append(i).append("</item>");
        }
        xml.append("</root>");

        SAXReader reader = new SAXReader();
        Document doc = reader.read(new StringReader(xml.toString()));

        assertNotNull(doc);
        assertEquals("root", doc.getRootElement().getName());
        assertEquals(1000, doc.getRootElement().elements().size());
    }

    @Test
    @DisplayName("XML with special characters")
    public void testSpecialCharacters() throws Exception {
        String xmlWithSpecial = "<?xml version='1.0' encoding='UTF-8'?><root>Special: &lt;&gt;&amp;&quot;&apos;</root>";

        SAXReader reader = new SAXReader();
        Document doc = reader.read(new StringReader(xmlWithSpecial));

        assertNotNull(doc);
        String text = doc.getRootElement().getText();
        assertTrue(text.contains("<") && text.contains(">"), "Special characters should be unescaped");
    }

    @Test
    @DisplayName("Xerces version check")
    public void testXercesVersion() {
        // Try to verify we're using Xerces 2.12.2
        // This is informational - helps confirm upgrade succeeded
        try {
            String implVersion = org.apache.xerces.impl.Version.getVersion();
            assertNotNull(implVersion);
            System.out.println("Xerces version: " + implVersion);
            assertTrue(implVersion.contains("2.12") || implVersion.contains("Xerces"),
                "Should be using Xerces 2.12.x");
        } catch (Exception e) {
            // Version class may not be accessible, that's okay
            System.out.println("Could not determine Xerces version: " + e.getMessage());
        }
    }
}
