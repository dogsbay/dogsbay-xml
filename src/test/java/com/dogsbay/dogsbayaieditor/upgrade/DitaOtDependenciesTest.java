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

/**
 * Tests for DITA-OT 4.3.5 dependencies (Phase 4).
 * Verifies that all DITA-OT libraries are loaded and accessible.
 *
 * @version $Revision: 1.0 $, $Date: 2025/01/24 $
 * @author DogsBay Ltd
 */
@DisplayName("DITA-OT 4.3.5 Dependencies Tests")
public class DitaOtDependenciesTest {

    @Test
    @DisplayName("Verify Apache Ant classes load")
    public void testAntClassesLoad() {
        assertDoesNotThrow(() -> {
            Class<?> projectClass = Class.forName("org.apache.tools.ant.Project");
            assertNotNull(projectClass, "Ant Project class should load");
            System.out.println("Ant classes loaded successfully");
        }, "Apache Ant classes should be loadable");
    }

    @Test
    @DisplayName("Verify DITA-OT core classes load")
    public void testDitaOtCoreClassesLoad() {
        assertDoesNotThrow(() -> {
            // Try to load DITA-OT Configuration class
            Class<?> configClass = Class.forName("org.dita.dost.util.Configuration");
            assertNotNull(configClass, "DITA-OT Configuration class should load");
            System.out.println("DITA-OT core classes loaded successfully");
        }, "DITA-OT core classes should be loadable");
    }

    @Test
    @DisplayName("Verify Commons-IO classes load")
    public void testCommonsIoClassesLoad() {
        assertDoesNotThrow(() -> {
            Class<?> ioutilsClass = Class.forName("org.apache.commons.io.IOUtils");
            assertNotNull(ioutilsClass, "Commons-IO IOUtils class should load");
            System.out.println("Commons-IO classes loaded successfully");
        }, "Commons-IO classes should be loadable");
    }

    @Test
    @DisplayName("Verify Guava classes load")
    public void testGuavaClassesLoad() {
        assertDoesNotThrow(() -> {
            Class<?> listsClass = Class.forName("com.google.common.collect.Lists");
            assertNotNull(listsClass, "Guava Lists class should load");
            System.out.println("Guava classes loaded successfully");
        }, "Guava classes should be loadable");
    }

    @Test
    @DisplayName("Verify ICU4J classes load")
    public void testIcu4jClassesLoad() {
        assertDoesNotThrow(() -> {
            Class<?> unicodeSetClass = Class.forName("com.ibm.icu.text.UnicodeSet");
            assertNotNull(unicodeSetClass, "ICU4J UnicodeSet class should load");
            System.out.println("ICU4J classes loaded successfully");
        }, "ICU4J classes should be loadable");
    }

    @Test
    @DisplayName("Verify Jackson Core classes load")
    public void testJacksonCoreClassesLoad() {
        assertDoesNotThrow(() -> {
            Class<?> jsonFactoryClass = Class.forName("com.fasterxml.jackson.core.JsonFactory");
            assertNotNull(jsonFactoryClass, "Jackson JsonFactory class should load");
            System.out.println("Jackson Core classes loaded successfully");
        }, "Jackson Core classes should be loadable");
    }

    @Test
    @DisplayName("Verify Jackson Databind classes load")
    public void testJacksonDatabindClassesLoad() {
        assertDoesNotThrow(() -> {
            Class<?> objectMapperClass = Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
            assertNotNull(objectMapperClass, "Jackson ObjectMapper class should load");

            // Create an instance to verify it's functional
            Object mapper = objectMapperClass.getDeclaredConstructor().newInstance();
            assertNotNull(mapper, "Should be able to create ObjectMapper instance");

            System.out.println("Jackson Databind classes loaded successfully");
        }, "Jackson Databind classes should be loadable");
    }

    @Test
    @DisplayName("Verify Jackson YAML classes load")
    public void testJacksonYamlClassesLoad() {
        assertDoesNotThrow(() -> {
            Class<?> yamlFactoryClass = Class.forName("com.fasterxml.jackson.dataformat.yaml.YAMLFactory");
            assertNotNull(yamlFactoryClass, "Jackson YAMLFactory class should load");
            System.out.println("Jackson YAML classes loaded successfully");
        }, "Jackson YAML classes should be loadable");
    }

    @Test
    @DisplayName("Verify SLF4J classes load")
    public void testSlf4jClassesLoad() {
        assertDoesNotThrow(() -> {
            Class<?> loggerClass = Class.forName("org.slf4j.Logger");
            Class<?> loggerFactoryClass = Class.forName("org.slf4j.LoggerFactory");
            assertNotNull(loggerClass, "SLF4J Logger class should load");
            assertNotNull(loggerFactoryClass, "SLF4J LoggerFactory class should load");

            // Get a logger instance
            Object logger = loggerFactoryClass.getMethod("getLogger", Class.class)
                    .invoke(null, getClass());
            assertNotNull(logger, "Should be able to get logger instance");

            System.out.println("SLF4J classes loaded successfully");
        }, "SLF4J classes should be loadable");
    }

    @Test
    @DisplayName("Verify Logback classes load")
    public void testLogbackClassesLoad() {
        assertDoesNotThrow(() -> {
            Class<?> logbackClass = Class.forName("ch.qos.logback.classic.Logger");
            assertNotNull(logbackClass, "Logback Logger class should load");
            System.out.println("Logback classes loaded successfully");
        }, "Logback classes should be loadable");
    }

    @Test
    @DisplayName("Verify SnakeYAML classes load")
    public void testSnakeYamlClassesLoad() {
        assertDoesNotThrow(() -> {
            Class<?> yamlClass = Class.forName("org.yaml.snakeyaml.Yaml");
            assertNotNull(yamlClass, "SnakeYAML Yaml class should load");

            // Create an instance to verify it's functional
            Object yaml = yamlClass.getDeclaredConstructor().newInstance();
            assertNotNull(yaml, "Should be able to create Yaml instance");

            System.out.println("SnakeYAML classes loaded successfully");
        }, "SnakeYAML classes should be loadable");
    }

    @Test
    @DisplayName("Verify updated Jing classes load")
    public void testUpdatedJingClassesLoad() {
        assertDoesNotThrow(() -> {
            Class<?> annotationsClass = Class.forName("com.thaiopensource.relaxng.parse.Annotations");
            assertNotNull(annotationsClass, "Jing Annotations class should load");
            System.out.println("Updated Jing classes loaded successfully");
        }, "Updated Jing classes should be loadable");
    }

    @Test
    @DisplayName("Verify all DITA-OT dependencies are compatible")
    public void testDitaOtDependenciesCompatible() {
        // This test verifies that all the major DITA-OT dependencies can be loaded together
        // without conflicts
        assertDoesNotThrow(() -> {
            // Load Saxon
            Class.forName("net.sf.saxon.TransformerFactoryImpl");

            // Load Xerces
            Class.forName("org.apache.xerces.parsers.SAXParser");

            // Load XML Resolver (new)
            Class.forName("org.xmlresolver.Resolver");

            // Load DITA-OT
            Class.forName("org.dita.dost.util.Configuration");

            // Load Guava
            Class.forName("com.google.common.collect.Lists");

            // Load Jackson
            Class.forName("com.fasterxml.jackson.databind.ObjectMapper");

            // Load SLF4J
            Class.forName("org.slf4j.LoggerFactory");

            System.out.println("All DITA-OT dependencies are compatible - no classpath conflicts detected");
        }, "All DITA-OT dependencies should be compatible without classpath conflicts");
    }
}
