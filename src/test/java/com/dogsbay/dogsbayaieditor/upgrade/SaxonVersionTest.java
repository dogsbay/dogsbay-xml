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
 * Verifies that Saxon-HE 12.7 is actually loaded and used for transformations.
 *
 * @version $Revision: 1.0 $, $Date: 2025/01/24 $
 * @author DogsBay Ltd
 */
@DisplayName("Saxon Version Verification Tests")
public class SaxonVersionTest {

    private static final String TEST_XML = "<?xml version='1.0'?><test/>";

    private static final String VERSION_CHECK_XSLT =
        "<?xml version='1.0'?>" +
        "<xsl:stylesheet version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" +
        "  <xsl:template match='/'>" +
        "    <result>" +
        "      <product><xsl:value-of select='system-property(\"xsl:product-name\")'/></product>" +
        "      <version><xsl:value-of select='system-property(\"xsl:product-version\")'/></version>" +
        "    </result>" +
        "  </xsl:template>" +
        "</xsl:stylesheet>";

    @Test
    @DisplayName("Verify Saxon 12.7 TransformerFactory is available")
    public void testSaxon127TransformerFactoryAvailable() {
        // Set system property to use Saxon 12.7
        System.setProperty("javax.xml.transform.TransformerFactory",
            "net.sf.saxon.TransformerFactoryImpl");

        TransformerFactory factory = TransformerFactory.newInstance();

        assertNotNull(factory, "TransformerFactory should not be null");
        assertTrue(factory.getClass().getName().contains("saxon"),
            "Should be Saxon TransformerFactory, got: " + factory.getClass().getName());

        System.out.println("TransformerFactory class: " + factory.getClass().getName());
    }

    @Test
    @DisplayName("Verify Saxon version in transformation output")
    public void testSaxonVersionInTransformation() throws Exception {
        // Set system property to use Saxon 12.7
        System.setProperty("javax.xml.transform.TransformerFactory",
            "net.sf.saxon.TransformerFactoryImpl");

        TransformerFactory factory = TransformerFactory.newInstance();

        StreamSource xsltSource = new StreamSource(new StringReader(VERSION_CHECK_XSLT));
        Transformer transformer = factory.newTransformer(xsltSource);

        StreamSource xmlSource = new StreamSource(new StringReader(TEST_XML));
        StringWriter resultWriter = new StringWriter();
        StreamResult result = new StreamResult(resultWriter);

        transformer.transform(xmlSource, result);

        String output = resultWriter.toString();
        System.out.println("Transformation output: " + output);

        // Check that output contains Saxon and version info (case-insensitive)
        assertTrue(output.toUpperCase().contains("SAXON"),
            "Output should contain 'Saxon': " + output);
        assertTrue(output.contains("12") && output.contains("HE"),
            "Output should contain '12' and 'HE': " + output);
    }

    @Test
    @DisplayName("Verify Saxon 12.7 is actually on classpath")
    public void testSaxon127OnClasspath() {
        assertDoesNotThrow(() -> {
            // Try to get Saxon 12.7 Configuration class
            Class<?> configClass = Class.forName("net.sf.saxon.Configuration");
            Object config = configClass.getDeclaredConstructor().newInstance();

            // Get edition code (should be "HE")
            java.lang.reflect.Method editionMethod = configClass.getMethod("getEditionCode");
            String edition = (String) editionMethod.invoke(config);

            // Get product title (should contain "12")
            java.lang.reflect.Method titleMethod = configClass.getMethod("getProductTitle");
            String title = (String) titleMethod.invoke(config);

            System.out.println("Saxon edition: " + edition);
            System.out.println("Saxon product: " + title);

            assertEquals("HE", edition, "Should be Saxon-HE");
            assertTrue(title.contains("12"), "Should be Saxon 12.x: " + title);

        }, "Saxon 12.7 Configuration should be loadable");
    }

    @Test
    @DisplayName("Verify no Saxon 9 classes interfering")
    public void testNoSaxon9Interference() {
        // This test verifies that when we load Saxon classes,
        // we get Saxon 12.7, not Saxon 9

        try {
            Class<?> configClass = Class.forName("net.sf.saxon.Configuration");
            Object config = configClass.getDeclaredConstructor().newInstance();

            java.lang.reflect.Method method = configClass.getMethod("getProductTitle");
            String title = (String) method.invoke(config);

            System.out.println("Loaded Saxon product: " + title);

            // Saxon 9.x would have "Saxon-B" or "Saxon 9.x"
            // Saxon 12.7 should have "SaxonJ-HE 12.7" or similar
            assertFalse(title.contains("Saxon-B"), "Should not be Saxon-B (9.x): " + title);
            assertFalse(title.matches(".*Saxon.*9\\..*"), "Should not be Saxon 9.x: " + title);
            assertTrue(title.contains("12"), "Should be Saxon 12.x: " + title);

        } catch (Exception e) {
            fail("Failed to check Saxon version: " + e.getMessage());
        }
    }
}
