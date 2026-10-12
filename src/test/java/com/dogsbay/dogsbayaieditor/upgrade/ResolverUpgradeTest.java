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
 * Tests that xmlresolver 5.x and the old xml-resolver 1.2 can coexist, and that the
 * 5.x pin holds — see {@link #testXmlResolverVersion()} for why it is a pin.
 *
 * @version $Revision: 1.0 $, $Date: 2025/01/24 $
 * @author DogsBay Ltd
 */
@DisplayName("XML Resolver 5.x alongside xml-resolver 1.2")
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

    /**
     * xmlresolver stays on 5.x until DITA-OT's own engine moves off it.
     *
     * <p>{@code lib/dost.jar} — the DITA-OT 4.3.5 engine, a committed jar, not a Maven
     * coordinate we control — reads {@code ResolverFeature.CACHE_DIRECTORY}, and xmlresolver 6
     * dropped that field. Nothing fails at compile time, because we never name the field
     * ourselves; the first publish aborts instead, with
     * {@code FATAL DITA-OT build aborted: java.lang.NoSuchFieldError: Class
     * org.xmlresolver.ResolverFeature does not have member field '…CACHE_DIRECTORY'}. On
     * 6.1.0 that was every publish: 8 tests across three platforms, each reporting a
     * missing output file rather than the cause.
     *
     * <p>So this is a deliberate pin and not a stale version string. Raising it means
     * shipping a DITA-OT engine built against xmlresolver 6, so check {@code dost.jar}
     * first — {@code unzip -p lib/dost.jar | strings | grep CACHE_DIRECTORY} finding
     * nothing is the signal that the major bump is safe to take.
     */
    @Test
    @DisplayName("xmlresolver is held at 5.x, because DITA-OT's engine needs it")
    public void testXmlResolverVersion() throws Exception {
        Package pkg = Class.forName("org.xmlresolver.Resolver").getPackage();
        String version = pkg == null ? null : pkg.getImplementationVersion();
        assertNotNull(version,
            "xmlresolver publishes its version in the jar manifest; without it this pin "
            + "cannot be checked at all, so treat a null as the jar having changed shape");
        assertTrue(version.startsWith("5."),
            "xmlresolver is pinned to 5.x on purpose and this is " + version
            + ". DITA-OT 4.3.5's lib/dost.jar reads ResolverFeature.CACHE_DIRECTORY, which "
            + "xmlresolver 6 removed, so every publish aborts with NoSuchFieldError. See this "
            + "test's javadoc for the one check that says when the bump is safe.");
    }

    /**
     * The field DITA-OT needs is the thing actually asserted above, so assert it directly.
     *
     * <p>A version prefix is a proxy: it would pass a 5.x release that dropped the field and
     * fail a 6.x that kept it. This reads the field the engine reads, by the same reflection
     * the JVM would use, so it fails for the real reason.
     */
    @Test
    @DisplayName("ResolverFeature still has the CACHE_DIRECTORY that DITA-OT's engine reads")
    public void testCacheDirectoryFeatureIsStillThere() throws Exception {
        Class<?> feature = Class.forName("org.xmlresolver.ResolverFeature");
        // getField throws rather than returning null when the field is gone, and a bare
        // NoSuchFieldException says nothing about why this test cares, so it is caught here
        // and turned into the sentence a reader of the CI log needs.
        try {
            assertNotNull(feature.getField("CACHE_DIRECTORY").get(null),
                "ResolverFeature.CACHE_DIRECTORY resolved to null");
        } catch (NoSuchFieldException gone) {
            fail("ResolverFeature.CACHE_DIRECTORY is missing from this xmlresolver. "
                + "lib/dost.jar, the DITA-OT 4.3.5 engine, reads it, so every publish will "
                + "abort with NoSuchFieldError — nothing fails at compile time because we "
                + "never name the field ourselves. xmlresolver must stay on 5.x until the "
                + "bundled DITA-OT engine is built against 6.");
        }
    }
}
