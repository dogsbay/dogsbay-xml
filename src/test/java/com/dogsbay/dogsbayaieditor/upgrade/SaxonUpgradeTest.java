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
import static org.junit.jupiter.api.Assertions.*;

import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import java.io.*;

/**
 * Tests for Saxon-HE 12.7 upgrade compatibility.
 * Verifies XSLT transformations work with the new Saxon version.
 *
 * Phase 3 of DITA-OT 4.3 library upgrade plan.
 *
 * @version $Revision: 1.0 $, $Date: 2025/01/24 $
 * @author DogsBay Ltd
 */
@DisplayName("Saxon-HE 12.7 Upgrade Tests")
public class SaxonUpgradeTest {

    private static final String SIMPLE_XML = "<?xml version='1.0'?><root><item>value</item></root>";

    private static final String SIMPLE_XSLT =
        "<?xml version='1.0'?>" +
        "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" +
        "  <xsl:template match='/'>" +
        "    <output><xsl:value-of select='/root/item'/></output>" +
        "  </xsl:template>" +
        "</xsl:stylesheet>";

    private static final String IDENTITY_XSLT =
        "<?xml version='1.0'?>" +
        "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" +
        "  <xsl:template match='@*|node()'>" +
        "    <xsl:copy><xsl:apply-templates select='@*|node()'/></xsl:copy>" +
        "  </xsl:template>" +
        "</xsl:stylesheet>";

    @Test
    @DisplayName("Saxon-HE 12.7 classes load")
    public void testSaxonClassesLoad() {
        // Verify Saxon 12.7 core classes are available
        assertDoesNotThrow(() -> {
            Class.forName("net.sf.saxon.s9api.Processor");
        }, "Saxon s9api.Processor should load");

        assertDoesNotThrow(() -> {
            Class.forName("net.sf.saxon.Configuration");
        }, "Saxon Configuration should load");

        assertDoesNotThrow(() -> {
            Class.forName("net.sf.saxon.TransformerFactoryImpl");
        }, "Saxon TransformerFactoryImpl should load");
    }

    @Test
    @DisplayName("Saxon version check")
    public void testSaxonVersion() {
        try {
            // Get Saxon version through Configuration
            Class<?> configClass = Class.forName("net.sf.saxon.Configuration");
            Object config = configClass.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method method = configClass.getMethod("getEditionCode");
            String edition = (String) method.invoke(config);

            assertNotNull(edition, "Saxon edition should not be null");
            System.out.println("Saxon edition: " + edition);

            assertEquals("HE", edition, "Should be using Saxon-HE (Home Edition)");

            // Also check the product title
            method = configClass.getMethod("getProductTitle");
            String title = (String) method.invoke(config);
            System.out.println("Saxon product: " + title);
            assertTrue(title.contains("Saxon") && title.contains("12"),
                "Should be Saxon 12.x");
        } catch (Exception e) {
            fail("Could not determine Saxon version: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Basic XSLT 1.0 transformation")
    public void testBasicXsltTransformation() throws Exception {
        // Explicitly use Saxon's TransformerFactory
        TransformerFactory factory = new net.sf.saxon.TransformerFactoryImpl();

        // Verify we're using Saxon
        assertTrue(factory.getClass().getName().contains("saxon"),
            "Should be using Saxon TransformerFactory");

        StreamSource xsltSource = new StreamSource(new StringReader(SIMPLE_XSLT));
        Transformer transformer = factory.newTransformer(xsltSource);

        StreamSource xmlSource = new StreamSource(new StringReader(SIMPLE_XML));
        StringWriter resultWriter = new StringWriter();
        StreamResult result = new StreamResult(resultWriter);

        transformer.transform(xmlSource, result);

        String output = resultWriter.toString();
        assertTrue(output.contains("<output>value</output>") ||
                   output.contains("<output>value</output>"),
            "Transform should produce expected output");
    }

    @Test
    @DisplayName("Identity transformation")
    public void testIdentityTransformation() throws Exception {
        TransformerFactory factory = new net.sf.saxon.TransformerFactoryImpl();

        StreamSource xsltSource = new StreamSource(new StringReader(IDENTITY_XSLT));
        Transformer transformer = factory.newTransformer(xsltSource);

        StreamSource xmlSource = new StreamSource(new StringReader(SIMPLE_XML));
        StringWriter resultWriter = new StringWriter();
        StreamResult result = new StreamResult(resultWriter);

        transformer.transform(xmlSource, result);

        String output = resultWriter.toString();
        assertTrue(output.contains("<root>") && output.contains("<item>value</item>"),
            "Identity transform should preserve structure");
    }

    @Test
    @DisplayName("Transformer with parameters")
    public void testTransformerWithParameters() throws Exception {
        String paramXslt =
            "<?xml version='1.0'?>" +
            "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" +
            "  <xsl:param name='myParam'/>" +
            "  <xsl:template match='/'>" +
            "    <output><xsl:value-of select='$myParam'/></output>" +
            "  </xsl:template>" +
            "</xsl:stylesheet>";

        TransformerFactory factory = new net.sf.saxon.TransformerFactoryImpl();
        StreamSource xsltSource = new StreamSource(new StringReader(paramXslt));
        Transformer transformer = factory.newTransformer(xsltSource);

        // Set parameter
        transformer.setParameter("myParam", "testValue");

        StreamSource xmlSource = new StreamSource(new StringReader(SIMPLE_XML));
        StringWriter resultWriter = new StringWriter();
        StreamResult result = new StreamResult(resultWriter);

        transformer.transform(xmlSource, result);

        String output = resultWriter.toString();
        assertTrue(output.contains("testValue"),
            "Parameter should be passed to transformation");
    }

    @Test
    @DisplayName("Error handling for invalid XSLT")
    public void testInvalidXsltHandling() {
        String invalidXslt = "<?xml version='1.0'?>" +
            "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" +
            "  <xsl:template match='/'>" +
            "    <xsl:value-of select='$undefinedVariable'/>" +
            "  </xsl:template>" +
            "</xsl:stylesheet>";

        TransformerFactory factory = new net.sf.saxon.TransformerFactoryImpl();
        StreamSource xsltSource = new StreamSource(new StringReader(invalidXslt));

        // Saxon 12.7 throws exception for undefined variables
        assertThrows(TransformerConfigurationException.class, () -> {
            factory.newTransformer(xsltSource);
        }, "XSLT with undefined variable should throw exception");
    }

    @Test
    @DisplayName("Saxon s9api Processor instantiation")
    public void testSaxonS9apiProcessor() throws Exception {
        // Test the s9api (Saxon's modern API)
        Class<?> processorClass = Class.forName("net.sf.saxon.s9api.Processor");
        Object processor = processorClass.getDeclaredConstructor(boolean.class)
            .newInstance(false);

        assertNotNull(processor, "Saxon Processor should instantiate");
    }

    @Test
    @DisplayName("Saxon Configuration instantiation")
    public void testSaxonConfiguration() throws Exception {
        // Test Saxon Configuration class (used in ScenarioProcessor)
        Class<?> configClass = Class.forName("net.sf.saxon.Configuration");
        Object config = configClass.getDeclaredConstructor().newInstance();

        assertNotNull(config, "Saxon Configuration should instantiate");
    }

    @Test
    @DisplayName("Multiple transformations in sequence")
    public void testMultipleTransformations() throws Exception {
        TransformerFactory factory = new net.sf.saxon.TransformerFactoryImpl();
        StreamSource xsltSource = new StreamSource(new StringReader(SIMPLE_XSLT));
        Transformer transformer = factory.newTransformer(xsltSource);

        // Perform multiple transformations
        for (int i = 0; i < 5; i++) {
            StreamSource xmlSource = new StreamSource(new StringReader(SIMPLE_XML));
            StringWriter resultWriter = new StringWriter();
            StreamResult result = new StreamResult(resultWriter);

            transformer.transform(xmlSource, result);

            String output = resultWriter.toString();
            assertTrue(output.contains("value"),
                "Transform #" + i + " should succeed");
        }
    }

    @Test
    @DisplayName("XSLT with XPath expressions")
    public void testXpathInXslt() throws Exception {
        String xpathXslt =
            "<?xml version='1.0'?>" +
            "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" +
            "  <xsl:template match='/'>" +
            "    <result>" +
            "      <count><xsl:value-of select='count(//item)'/></count>" +
            "      <first><xsl:value-of select='//item[1]'/></first>" +
            "    </result>" +
            "  </xsl:template>" +
            "</xsl:stylesheet>";

        TransformerFactory factory = new net.sf.saxon.TransformerFactoryImpl();
        StreamSource xsltSource = new StreamSource(new StringReader(xpathXslt));
        Transformer transformer = factory.newTransformer(xsltSource);

        StreamSource xmlSource = new StreamSource(new StringReader(SIMPLE_XML));
        StringWriter resultWriter = new StringWriter();
        StreamResult result = new StreamResult(resultWriter);

        transformer.transform(xmlSource, result);

        String output = resultWriter.toString();
        assertTrue(output.contains("<count>1</count>") && output.contains("<first>value</first>"),
            "XPath expressions should work in XSLT");
    }

    @Test
    @DisplayName("Old Saxon 6.x has been removed")
    public void testOldSaxonRemoved() {
        // Verify old Saxon 6.x has been removed (replaced by Saxon-HE 12.7)
        assertThrows(ClassNotFoundException.class, () -> {
            Class.forName("com.icl.saxon.TransformerFactoryImpl");
        }, "Old Saxon 6.x should no longer be available (removed in favor of Saxon-HE 12.7)");
    }
}
