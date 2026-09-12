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

package com.dogsbay.dogsbayaieditor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple tests to verify that core classes can be loaded and instantiated.
 * This validates that the build and test infrastructure is working correctly.
 *
 * More comprehensive functional tests will be added in future phases.
 */
public class SimpleClassLoadingTest {

    @Test
    @DisplayName("Core application classes can be loaded")
    public void testCoreClassesLoad() {
        assertDoesNotThrow(() -> {
            // Verify key classes can be loaded from the JAR
            Class.forName("com.dogsbay.xml.DogsBayDocument");
            Class.forName("com.dogsbay.xml.XMLGrammar");
            Class.forName("com.dogsbay.dogsbayaieditor.scenario.ScenarioProperties");
            Class.forName("com.dogsbay.dogsbayaieditor.plugins.PluginViewProperties");
            Class.forName("com.dogsbay.schema.SchemaDocument");
        }, "All core application classes should be loadable");
    }

    @Test
    @DisplayName("Scenario properties can be instantiated")
    public void testScenarioPropertiesInstantiation() {
        assertDoesNotThrow(() -> {
            com.dogsbay.dogsbayaieditor.scenario.ScenarioProperties props =
                new com.dogsbay.dogsbayaieditor.scenario.ScenarioProperties();
            assertNotNull(props, "ScenarioProperties instance should not be null");
        });
    }

    @Test
    @DisplayName("Grammar model classes can be loaded")
    public void testSchemaClassesLoad() {
        assertDoesNotThrow(() -> {
            // The grammar-neutral model plus the two grammars still supported.
            // (XML Schema support and its Castor-backed model were removed.)
            Class.forName("com.dogsbay.schema.SchemaDocument");
            Class.forName("com.dogsbay.schema.ElementInformation");
            Class.forName("com.dogsbay.schema.dtd.DTDDocument");
            Class.forName("com.dogsbay.schema.rng.RNGDocument");
        }, "Grammar model classes should be loadable");
    }

    @Test
    @DisplayName("JUnit 5 is working correctly")
    public void testJUnit5() {
        assertTrue(true, "JUnit 5 basic assertion works");
        assertFalse(false, "JUnit 5 boolean assertions work");
        assertEquals(2, 1 + 1, "JUnit 5 equality assertions work");
    }
}
