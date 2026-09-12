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

package com.dogsbay.dogsbayaieditor.plugin.dita;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Vector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.grammar.GrammarProperties;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.dogsbay.dogsbayaieditor.template.TemplateProperties;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;

/**
 * Verifies that bundled DITA editing assets (DTDs, types, templates, catalog)
 * are extracted from the JAR and registered with ConfigurationProperties as
 * built-in (transient) entries — i.e. visible to consumers but never persisted
 * to user config.
 */
class DitaBuiltInAssetsTest {

    private Path tempHome;
    private File targetDir;

    @BeforeAll
    static void initDom4j() {
        // Required for DogsBayDocument to use the project's XElement implementation.
        System.setProperty("org.dom4j.factory", "com.dogsbay.xml.XDocumentFactory");
    }

    @BeforeEach
    void setUp() throws IOException {
        tempHome = Files.createTempDirectory("dita-builtin-test");
        targetDir = tempHome.resolve("dita-extract").toFile();
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempHome)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    @Test
    void extractsAllBundledFiles() throws IOException {
        DitaBuiltInAssets.extractIfNeeded(targetDir);

        assertTrue(new File(targetDir, "manifest.xml").isFile(), "manifest extracted");
        assertTrue(new File(targetDir, "version.txt").isFile(), "version marker extracted");
        assertTrue(new File(targetDir, "types/dita-concept.type").isFile(), "concept type extracted");
        assertTrue(new File(targetDir, "templates/dita-concept-template.xml").isFile(),
                "concept template extracted");
        assertTrue(new File(targetDir, "dtd/catalog.xml").isFile(), "DTD catalog extracted");
        assertTrue(new File(targetDir, "dtd/concept.dtd").isFile(), "concept DTD extracted");
        // Subdirectory under dtd/
        assertTrue(new File(targetDir, "dtd/mathml").isDirectory(),
                "mathml DTD subdirectory extracted");
    }

    @Test
    void skipsExtractionWhenVersionMatches() throws IOException {
        DitaBuiltInAssets.extractIfNeeded(targetDir);
        File marker = new File(targetDir, "version.txt");
        long firstMtime = marker.lastModified();

        // Force the mtime backwards so a re-extraction would be visible.
        assertTrue(marker.setLastModified(firstMtime - 60_000));

        DitaBuiltInAssets.extractIfNeeded(targetDir);
        assertEquals(firstMtime - 60_000, marker.lastModified(),
                "second call should not re-extract when version matches");
    }

    @Test
    void reExtractsWhenVersionDiffers() throws IOException {
        DitaBuiltInAssets.extractIfNeeded(targetDir);
        File marker = new File(targetDir, "version.txt");
        Files.write(marker.toPath(), "stale-version\n".getBytes(StandardCharsets.UTF_8));
        long stalemtime = marker.lastModified();
        assertTrue(marker.setLastModified(stalemtime - 60_000));

        DitaBuiltInAssets.extractIfNeeded(targetDir);
        assertNotEquals(stalemtime - 60_000, marker.lastModified(),
                "outdated version marker should trigger re-extraction");
        String fresh = new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).trim();
        assertNotEquals("stale-version", fresh);
    }

    @Test
    void registersTypesTemplatesAndCatalogAsTransientEntries() throws Exception {
        ConfigurationProperties config = newEmptyConfig();
        DitaBuiltInAssets.install(config, targetDir);

        // Catalog
        Vector catalogs = config.getCatalogs();
        boolean foundCatalog = false;
        for (Object c : catalogs) {
            String path = (String) c;
            if (path.endsWith("dtd/catalog.xml")
                    || path.endsWith("dtd" + File.separator + "catalog.xml")) {
                foundCatalog = true;
                break;
            }
        }
        assertTrue(foundCatalog, "DITA catalog should be registered. Got: " + catalogs);
        assertTrue(System.getProperty("xml.catalog.files", "").contains("catalog.xml"),
                "xml.catalog.files system property should be updated");

        // Grammar types — expect 7
        assertEquals(7, countDitaGrammars(config),
                "should register 7 DITA grammar types. Got descriptions: "
                        + describeGrammars(config));

        // Templates — expect 6
        assertEquals(6, countDitaTemplates(config),
                "should register 6 DITA templates. Got names: "
                        + describeTemplates(config));
    }

    @Test
    void duplicateRegistrationIsIdempotent() throws Exception {
        ConfigurationProperties config = newEmptyConfig();
        DitaBuiltInAssets.install(config, targetDir);
        DitaBuiltInAssets.install(config, targetDir);

        assertEquals(7, countDitaGrammars(config),
                "second install must not duplicate grammar entries");
        assertEquals(6, countDitaTemplates(config),
                "second install must not duplicate template entries");

        long ditaCatalogs = 0;
        for (Object c : config.getCatalogs()) {
            String path = (String) c;
            if (path.contains("dita") || path.contains("dtd")) {
                ditaCatalogs++;
            }
        }
        assertTrue(ditaCatalogs >= 1, "catalog registered");
    }

    private static int countDitaGrammars(ConfigurationProperties config) {
        int count = 0;
        for (Object g : config.getGrammarProperties()) {
            String d = ((GrammarProperties) g).getDescription();
            if (d != null && d.startsWith("DITA 1.3")) count++;
        }
        return count;
    }

    private static int countDitaTemplates(ConfigurationProperties config) {
        int count = 0;
        for (Object t : config.getTemplateProperties()) {
            String n = ((TemplateProperties) t).getName();
            if (n != null && n.startsWith("dita-")) count++;
        }
        return count;
    }

    private static java.util.List<String> describeGrammars(ConfigurationProperties config) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Object g : config.getGrammarProperties()) {
            names.add(((GrammarProperties) g).getDescription());
        }
        return names;
    }

    private static java.util.List<String> describeTemplates(ConfigurationProperties config) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Object t : config.getTemplateProperties()) {
            names.add(((TemplateProperties) t).getName());
        }
        return names;
    }

    @Test
    void builtInEntriesAreNotPersisted() throws Exception {
        ConfigurationProperties config = newEmptyConfig();
        int grammarsBefore = countPersistedChildren(config, "grammar-properties");
        int templatesBefore = countPersistedChildren(config, "template-properties");

        DitaBuiltInAssets.install(config, targetDir);

        // The persisted XElement model under config should not have grown:
        // built-in entries live in transient in-memory lists, not in the document.
        assertEquals(grammarsBefore, countPersistedChildren(config, "grammar-properties"),
                "built-in grammars must not appear in persisted XML");
        assertEquals(templatesBefore, countPersistedChildren(config, "template-properties"),
                "built-in templates must not appear in persisted XML");
    }

    // -------------------------------------------------------------------------

    private static ConfigurationProperties newEmptyConfig() throws Exception {
        XElement root = new XElement("dogsbay");
        root.setText("\n");
        DogsBayDocument doc = new DogsBayDocument(
                Path.of(System.getProperty("java.io.tmpdir"),
                        "test-config-" + System.nanoTime() + ".xml")
                        .toUri().toURL(),
                root);
        return new ConfigurationProperties(doc) {
            @Override public void save() { /* no-op for tests */ }
            @Override public void saveToDisk() { /* no-op for tests */ }
        };
    }

    private static int countPersistedChildren(ConfigurationProperties config, String elementName) {
        XElement root = (XElement) config.getElement();
        if (root == null) return 0;
        XElement[] children = root.getElements(elementName);
        return children == null ? 0 : children.length;
    }
}
