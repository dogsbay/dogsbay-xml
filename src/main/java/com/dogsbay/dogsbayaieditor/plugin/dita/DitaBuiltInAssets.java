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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dogsbay.dogsbayaieditor.grammar.GrammarProperties;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.dogsbay.dogsbayaieditor.template.TemplateProperties;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.DogsBayURLUtilities;
import com.dogsbay.xml.XElement;

/**
 * Extracts the bundled DITA 1.3 editing assets from the JAR and registers them
 * with the editor as built-in (transient) grammar types, templates, and catalog.
 *
 * <p>Layout of the bundled resources, rooted at
 * {@code /com/dogsbay/dogsbayaieditor/plugin/dita/builtin/}:
 * <pre>
 *   manifest.xml               — declares which type/template/catalog files to load
 *   version.txt                — bumped when the bundle changes; gates re-extraction
 *   types/*.type               — 7 DITA grammar types
 *   templates/*.xml            — 6 DITA document templates
 *   dtd/catalog.xml            — XML catalog mapping DITA public IDs to bundled DTDs
 *   dtd/*.dtd, *.mod, *.ent    — DITA 1.3 DTD set
 * </pre>
 *
 * <p>Assets are extracted to {@code ~/.dogsbay/builtin/dita/} once per editor
 * version. Registration uses the {@code addBuiltInXxx} methods on
 * {@link ConfigurationProperties}, which do not persist to user config.
 */
public final class DitaBuiltInAssets {

    private static final Logger LOG = LoggerFactory.getLogger(DitaBuiltInAssets.class);

    /** Classpath prefix for bundled DITA resources. */
    static final String RESOURCE_PREFIX = "/com/dogsbay/dogsbayaieditor/plugin/dita/builtin/";

    /** Files to extract. Listed explicitly because JAR resources can't be enumerated. */
    private static final List<String> ASSET_FILES = buildAssetList();

    private DitaBuiltInAssets() {}

    /**
     * Extracts bundled assets to {@code ~/.dogsbay/builtin/dita/} (once per
     * version) and registers them with the given config.
     *
     * @return the directory the assets were extracted to.
     */
    public static File install(ConfigurationProperties config) throws IOException {
        File targetDir = defaultTargetDir();
        return install(config, targetDir);
    }

    /** Same as {@link #install(ConfigurationProperties)} but with an explicit target dir (for tests). */
    public static File install(ConfigurationProperties config, File targetDir) throws IOException {
        extractIfNeeded(targetDir);
        register(config, targetDir);
        return targetDir;
    }

    /**
     * Ensure the bundled DITA assets are extracted and current for headless use
     * (CLI / MCP / batch tools), without needing the editor UI to have launched.
     * Idempotent and cheap when the on-disk bundle already matches the JAR version;
     * runs the extraction check at most once per JVM. Unlike {@link #install}, it
     * does not register catalog entries with a config — it only refreshes the files
     * on disk so {@code ~/.dogsbay/builtin/dita/dtd/catalog.xml} resolves the current
     * DTD set (e.g. a newly bundled subjectScheme.dtd after a version bump).
     */
    public static void ensureExtracted() {
        if (extractionChecked) {
            return;
        }
        synchronized (DitaBuiltInAssets.class) {
            if (extractionChecked) {
                return;
            }
            try {
                extractIfNeeded(defaultTargetDir());
            } catch (IOException e) {
                LOG.warn("Could not refresh built-in DITA bundle: {}", e.getMessage());
            }
            extractionChecked = true;
        }
    }

    private static volatile boolean extractionChecked = false;

    static File defaultTargetDir() {
        String home = System.getProperty("user.home");
        return new File(home + File.separator + ".dogsbay"
                + File.separator + "builtin" + File.separator + "dita");
    }

    /** Re-extracts the bundle iff the on-disk {@code version.txt} differs from the JAR copy. */
    static void extractIfNeeded(File targetDir) throws IOException {
        String bundledVersion = readResourceText("version.txt");
        File versionFile = new File(targetDir, "version.txt");
        if (versionFile.isFile()) {
            String onDisk = readFileText(versionFile);
            if (bundledVersion.equals(onDisk)) {
                return;
            }
            LOG.info("DITA built-in bundle version changed ({} -> {}); re-extracting",
                    onDisk, bundledVersion);
        } else {
            LOG.info("Extracting DITA built-in bundle ({}) to {}", bundledVersion, targetDir);
        }
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Cannot create " + targetDir);
        }
        for (String relPath : ASSET_FILES) {
            extractOne(relPath, targetDir);
        }
    }

    /** Registers the extracted assets as built-in (transient) entries. */
    static void register(ConfigurationProperties config, File targetDir) throws IOException {
        File manifest = new File(targetDir, "manifest.xml");
        if (!manifest.isFile()) {
            throw new IOException("Missing manifest.xml in " + targetDir);
        }
        XElement root;
        try {
            URL manifestUrl = DogsBayURLUtilities.getURLFromFile(manifest);
            DogsBayDocument doc = new DogsBayDocument(manifestUrl);
            doc.loadWithoutSubstitution();
            root = doc.getRoot();
        } catch (Exception e) {
            throw new IOException("Failed to load DITA built-in manifest", e);
        }
        if (root == null) {
            throw new IOException("Empty manifest.xml");
        }

        registerCatalogs(config, root, targetDir);
        registerTypes(config, root, targetDir);
        registerTemplates(config, root, targetDir);
    }

    private static void registerCatalogs(ConfigurationProperties config, XElement root, File targetDir) {
        XElement catalogsEl = root.getElement("catalogs");
        if (catalogsEl == null) return;
        XElement[] refs = catalogsEl.getElements("catalog");
        if (refs == null) return;
        for (XElement ref : refs) {
            String src = ref.getAttribute("src");
            if (src == null || src.isEmpty()) continue;
            File catalog = new File(targetDir, src);
            if (!catalog.isFile()) {
                LOG.warn("DITA built-in catalog missing: {}", catalog);
                continue;
            }
            config.addBuiltInCatalog(catalog.getAbsolutePath());
            LOG.debug("Registered DITA built-in catalog: {}", catalog);
        }
    }

    private static void registerTypes(ConfigurationProperties config, XElement root, File targetDir) {
        XElement typesEl = root.getElement("types");
        if (typesEl == null) return;
        XElement[] refs = typesEl.getElements("type");
        if (refs == null) return;
        for (XElement ref : refs) {
            String src = ref.getAttribute("src");
            if (src == null || src.isEmpty()) continue;
            File typeFile = new File(targetDir, src);
            if (!typeFile.isFile()) {
                LOG.warn("DITA built-in type missing: {}", typeFile);
                continue;
            }
            try {
                URL typeUrl = DogsBayURLUtilities.getURLFromFile(typeFile);
                DogsBayDocument doc = new DogsBayDocument(typeUrl);
                doc.loadWithoutSubstitution();
                XElement typeRoot = doc.getRoot();
                if (typeRoot == null) continue;
                if ("types".equals(typeRoot.getName())) {
                    XElement[] inner = typeRoot.getElements("type");
                    if (inner != null) {
                        for (XElement t : inner) {
                            config.addBuiltInGrammarProperties(
                                    new GrammarProperties(config, typeUrl, t));
                        }
                    }
                } else if ("type".equals(typeRoot.getName())) {
                    config.addBuiltInGrammarProperties(
                            new GrammarProperties(config, typeUrl, typeRoot));
                }
            } catch (Exception e) {
                LOG.warn("Failed to register DITA built-in type {}: {}", typeFile, e.toString());
            }
        }
    }

    private static void registerTemplates(ConfigurationProperties config, XElement root, File targetDir) {
        XElement templatesEl = root.getElement("templates");
        if (templatesEl == null) return;
        XElement[] refs = templatesEl.getElements("template");
        if (refs == null) return;
        for (XElement ref : refs) {
            String src = ref.getAttribute("src");
            if (src == null || src.isEmpty()) continue;
            File tFile = new File(targetDir, src);
            if (!tFile.isFile()) {
                LOG.warn("DITA built-in template missing: {}", tFile);
                continue;
            }
            String fileName = tFile.getName();
            int dot = fileName.lastIndexOf('.');
            String name = dot > 0 ? fileName.substring(0, dot) : fileName;
            try {
                URL tUrl = DogsBayURLUtilities.getURLFromFile(tFile);
                config.addBuiltInTemplateProperties(new TemplateProperties(name, tUrl));
            } catch (Exception e) {
                LOG.warn("Failed to register DITA built-in template {}: {}", tFile, e.toString());
            }
        }
    }

    private static void extractOne(String relPath, File targetDir) throws IOException {
        File outFile = new File(targetDir, relPath);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        try (InputStream in = openResource(relPath)) {
            if (in == null) {
                throw new IOException("Missing resource: " + RESOURCE_PREFIX + relPath);
            }
            Files.copy(in, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static InputStream openResource(String relPath) {
        return DitaBuiltInAssets.class.getResourceAsStream(RESOURCE_PREFIX + relPath);
    }

    private static String readResourceText(String relPath) throws IOException {
        try (InputStream in = openResource(relPath);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            if (in == null) {
                throw new IOException("Missing resource: " + RESOURCE_PREFIX + relPath);
            }
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
            return sb.toString().trim();
        }
    }

    private static String readFileText(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
    }

    /**
     * The list of resource paths to extract. Top-level files (manifest, version,
     * types, templates) are hand-listed; the much larger DTD set is read from
     * {@code dtd/files.list} bundled inside the JAR. Bump {@code version.txt}
     * when any of these change so existing installs re-extract.
     */
    private static List<String> buildAssetList() {
        List<String> files = new ArrayList<>();
        files.add("manifest.xml");
        files.add("version.txt");
        files.addAll(Arrays.asList(
                "types/dita-bookmap.type",
                "types/dita-concept.type",
                "types/dita-glossentry.type",
                "types/dita-map.type",
                "types/dita-reference.type",
                "types/dita-task.type",
                "types/dita-topic.type"
        ));
        files.addAll(Arrays.asList(
                "templates/dita-concept-minimal.xml",
                "templates/dita-concept-template.xml",
                "templates/dita-reference-minimal.xml",
                "templates/dita-reference-template.xml",
                "templates/dita-task-minimal.xml",
                "templates/dita-task-template.xml"
        ));
        files.add("dtd/files.list");
        try {
            String listing = readResourceText("dtd/files.list");
            for (String line : listing.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    files.add("dtd/" + trimmed);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Missing dtd/files.list in DITA bundle", e);
        }
        return files;
    }
}
