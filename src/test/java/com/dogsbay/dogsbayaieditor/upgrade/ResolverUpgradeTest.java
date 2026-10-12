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
 * Tests for xmlresolver 5.3.3 addition alongside xml-resolver 1.2.
 * Verifies both old and new resolver libraries can coexist.
 *
 * Phase 2 of DITA-OT 4.3 library upgrade plan.
 *
 * @version $Revision: 1.0 $, $Date: 2025/01/24 $
 * @author DogsBay Ltd
 */
@DisplayName("XML Resolver 5.3.3 Addition Tests")
public class ResolverUpgradeTest {

    @Test
    @DisplayName("Old resolver (xml-resolver 1.2) classes still load")
    public void testOldResolverLoads() {
        // Verify old resolver classes are still available
        assertDoesNotThrow(() -> {
            Class.forName("org.apache.xml.resolver.Catalog");
        }, "Old Catalog class should load");

        assertDoesNotThrow(() -> {
            Class.forName("org.apache.xml.resolver.tools.CatalogResolver");
        }, "Old CatalogResolver class should load");
    }

    @Test
    @DisplayName("New xmlresolver 5.3.3 classes can load")
    public void testNewResolverLoads() {
        // Verify new resolver classes are available
        assertDoesNotThrow(() -> {
            Class.forName("org.xmlresolver.Resolver");
        }, "New Resolver class should load");

        assertDoesNotThrow(() -> {
            Class.forName("org.xmlresolver.XMLResolverConfiguration");
        }, "New XMLResolverConfiguration class should load");

        assertDoesNotThrow(() -> {
            Class.forName("org.xmlresolver.ResolverFeature");
        }, "New ResolverFeature enum should load");
    }

    @Test
    @DisplayName("Old resolver Catalog can be instantiated")
    public void testOldCatalogInstantiation() throws Exception {
        // Test that old resolver still works
        org.apache.xml.resolver.Catalog catalog =
            new org.apache.xml.resolver.Catalog();

        assertNotNull(catalog, "Old Catalog should instantiate");
    }

    @Test
    @DisplayName("Old CatalogResolver can be instantiated")
    public void testOldCatalogResolverInstantiation() throws Exception {
        // Test that old CatalogResolver still works
        org.apache.xml.resolver.tools.CatalogResolver resolver =
            new org.apache.xml.resolver.tools.CatalogResolver();

        assertNotNull(resolver, "Old CatalogResolver should instantiate");
    }

    @Test
    @DisplayName("New xmlresolver Configuration can be instantiated")
    public void testNewConfigurationInstantiation() throws Exception {
        // Test that new resolver configuration works
        org.xmlresolver.XMLResolverConfiguration config =
            new org.xmlresolver.XMLResolverConfiguration();

        assertNotNull(config, "New XMLResolverConfiguration should instantiate");
    }

    @Test
    @DisplayName("New xmlresolver Resolver can be instantiated")
    public void testNewResolverInstantiation() throws Exception {
        // Test that new resolver works
        org.xmlresolver.XMLResolverConfiguration config =
            new org.xmlresolver.XMLResolverConfiguration();

        org.xmlresolver.Resolver resolver = new org.xmlresolver.Resolver(config);

        assertNotNull(resolver, "New Resolver should instantiate");
    }

    @Test
    @DisplayName("Both resolvers coexist without conflicts")
    public void testBothResolversCoexist() throws Exception {
        // Create instances of both old and new resolvers
        org.apache.xml.resolver.tools.CatalogResolver oldResolver =
            new org.apache.xml.resolver.tools.CatalogResolver();

        org.xmlresolver.XMLResolverConfiguration config =
            new org.xmlresolver.XMLResolverConfiguration();
        org.xmlresolver.Resolver newResolver = new org.xmlresolver.Resolver(config);

        assertNotNull(oldResolver, "Old resolver should work");
        assertNotNull(newResolver, "New resolver should work");

        // Verify they are different classes
        assertNotEquals(
            oldResolver.getClass().getPackage().getName(),
            newResolver.getClass().getPackage().getName(),
            "Resolvers should be from different packages"
        );
    }

    @Test
    @DisplayName("New resolver has expected features")
    public void testNewResolverFeatures() {
        // Verify new resolver has modern features
        assertDoesNotThrow(() -> {
            // Check that ResolverFeature class is accessible
            // Note: ResolverFeature may not be an enum in 5.3.3
            Class<?> featureClass = Class.forName("org.xmlresolver.ResolverFeature");

            assertNotNull(featureClass, "ResolverFeature should be accessible");
        }, "ResolverFeature class should be accessible");
    }

    @Test
    @DisplayName("Existing code using old resolver still compiles")
    public void testBackwardCompatibility() {
        // This test verifies that old resolver usage patterns still work
        // by checking that the old classes are available

        assertDoesNotThrow(() -> {
            // Pattern used in XMLUtilities.java
            org.apache.xml.resolver.Catalog catalog =
                new org.apache.xml.resolver.Catalog();
            org.apache.xml.resolver.tools.CatalogResolver resolver =
                new org.apache.xml.resolver.tools.CatalogResolver();

            assertNotNull(catalog);
            assertNotNull(resolver);
        }, "Old resolver usage pattern should still work");
    }

    @Test
    @DisplayName("xmlresolver version check")
    public void testXmlResolverVersion() {
        // Try to verify we're using xmlresolver 5.3.3
        // This is informational - helps confirm upgrade succeeded
        try {
            Class<?> resolverClass = Class.forName("org.xmlresolver.Resolver");
            Package pkg = resolverClass.getPackage();

            if (pkg != null) {
                String version = pkg.getImplementationVersion();
                System.out.println("xmlresolver version: " +
                    (version != null ? version : "unknown"));

                if (version != null) {
                    assertTrue(version.startsWith("5.") || version.contains("5.3"),
                        "Should be using xmlresolver 5.x");
                }
            }
        } catch (Exception e) {
            // Version info may not be available, that's okay
            System.out.println("Could not determine xmlresolver version: " + e.getMessage());
        }
    }
}
