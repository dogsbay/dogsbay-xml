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

package com.dogsbay.dogsbayaieditor.framework;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * Seeds the default DITA-OT publishing framework on a fresh install.
 *
 * <p>A fresh editor install can already <em>edit and validate</em> DITA (the
 * grammar/DTD/template bundle is extracted by
 * {@link com.dogsbay.dogsbayaieditor.plugin.dita.DitaBuiltInAssets}), but the
 * Framework Manager starts empty, so <em>publishing</em> (HTML5) has nothing to
 * run against. This installs a trimmed DITA-OT {@value #VERSION} home — HTML5/
 * XHTML only, no PDF — bundled as a classpath resource, and registers it as a
 * {@link FrameworkProperties} so publishing works out of the box.
 *
 * <p>The bundle ships <strong>without</strong> DITA-OT's own {@code lib/}: the
 * toolkit runs in-process via {@code org.dita.dost.ProcessorFactory}, so it
 * resolves its libraries (Saxon, Xerces, icu4j, …) from the editor's classpath.
 * That keeps the bundle at ~3.6 MB compressed. Regenerate it with the Gradle
 * {@code bundleDitaOt} task (see {@code plans/default-dita-framework.md}).
 *
 * <p>Idempotent and version-gated: does nothing once a framework named
 * {@value #NAME} at version {@value #VERSION} is registered and its home was
 * extracted from the current bundle revision. The bundle's revision (a resource
 * next to the zip, written by {@code bundleDitaOt}) changes when its content
 * does, such as when the dogsbay publishing plugin was added; an installed home
 * from an older revision is replaced, so existing installs get the new content.
 */
public final class DefaultDitaOtFramework {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDitaOtFramework.class);

    /** Tool/family name — the prefix shared by every DITA-OT framework version. */
    public static final String NAME = "DITA-OT";

    /** Bundled DITA-OT version. Keep in sync with {@code bundleDitaOt}'s version in the build file. */
    public static final String VERSION = "4.3.5";

    /** The registered framework name, version-explicit (matches the bundled {@code framework.xml}). */
    public static final String FRAMEWORK_NAME = NAME + " " + VERSION;

    private static final String RESOURCE =
            "/com/dogsbay/dogsbayaieditor/framework/builtin/dita-ot-framework-" + VERSION + ".zip";

    private static final String REVISION_RESOURCE =
            "/com/dogsbay/dogsbayaieditor/framework/builtin/dita-ot-framework-" + VERSION + ".revision";

    /** The file in the install folder that records the bundle revision its home came from. */
    static final String REVISION_FILE = "bundle-revision";

    private DefaultDitaOtFramework() {}

    /**
     * Install + register the default framework if it isn't already. Safe to call
     * on every startup; never throws (logs and returns on any failure so a broken
     * bundle can't block editor startup).
     *
     * @param config the editor configuration to register the framework in
     */
    /**
     * Bring the bundled framework's files up to date without the editor's configuration.
     *
     * <p>{@link #installIfNeeded} both extracts and registers, and only the running editor has a
     * {@link ConfigurationProperties} to register in — so a headless run (the CLI, the MCP server, CI) kept whatever
     * an editor launch had extracted, however old. A bundle that gained an output type reached nobody who had not
     * started the GUI since, which for a command-line check is nobody at all.
     *
     * <p>Only the extraction half: a headless run resolves the home by path, so there is nothing to register.
     * Idempotent, and cheap when the revision already matches.
     *
     * @return the framework's DITA-OT home when it is present, or null.
     */
    public static File ensureExtracted() {
        try {
            if (DefaultDitaOtFramework.class.getResource(RESOURCE) == null) {
                return null;
            }
            File installDir = new File(ConfigurationProperties.getDefaultFrameworksDirectory(), FRAMEWORK_NAME);
            String revision = bundledRevision();
            if (needsExtract(installDir, revision)) {
                LOG.info("Refreshing the bundled DITA-OT framework at {} to revision {}", installDir, revision);
                extractBundled(installDir);
                if (revision != null) {
                    Files.writeString(new File(installDir, REVISION_FILE).toPath(), revision + "\n");
                }
            }
            File home = new File(installDir, "dita-ot");
            return home.isDirectory() ? home : null;
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not refresh the bundled DITA-OT framework: {}", e.toString());
            return null;
        }
    }

    public static void installIfNeeded(ConfigurationProperties config) {
        try {
            if (DefaultDitaOtFramework.class.getResource(RESOURCE) == null) {
                LOG.info("No bundled DITA-OT framework ({}) — skipping default-framework seed", RESOURCE);
                return;
            }

            File installDir = new File(ConfigurationProperties.getDefaultFrameworksDirectory(), FRAMEWORK_NAME);
            File home = new File(installDir, "dita-ot");
            String revision = bundledRevision();
            boolean registered = isRegistered(config);
            if (registered && !needsExtract(installDir, revision)) {
                return;
            }

            // Extract when the home is missing or from an older bundle — config persists
            // on clean shutdown, but if it didn't (force-quit) we re-register (cheap)
            // without re-extracting ~19 MB.
            if (needsExtract(installDir, revision)) {
                if (home.isDirectory()) {
                    LOG.info("Replacing the default framework at {} with bundle revision {}", installDir, revision);
                    deleteRecursively(home.toPath());
                }
                extractResourceZip(installDir);
                if (revision != null) {
                    Files.writeString(new File(installDir, REVISION_FILE).toPath(), revision + "\n");
                }
            }
            if (registered) {
                return;
            }

            // framework.xml + dita-ot/ are at the bundle root, so the install dir
            // is both source and target — importFromDirectory parses in place
            // (no copy) and registers a FrameworkProperties in the config.
            FrameworkProperties fw = new FrameworkImporter(config).importFromDirectory(installDir, installDir);
            LOG.info("Seeded default framework {} {} at {}",
                    fw.getName(), fw.getVersion(), installDir.getAbsolutePath());
        } catch (Exception e) {
            LOG.warn("Failed to seed default DITA-OT framework: {}", e.getMessage(), e);
        }
    }

    private static boolean isRegistered(ConfigurationProperties config) {
        Vector list = config.getFrameworkProperties();
        if (list == null) {
            return false;
        }
        for (Object o : list) {
            if (o instanceof FrameworkProperties fp
                    && FRAMEWORK_NAME.equals(fp.getName()) && VERSION.equals(fp.getVersion())) {
                return true;
            }
        }
        return false;
    }

    /** The bundled framework's revision, or null when the build carries none. */
    static String bundledRevision() throws IOException {
        try (InputStream in = DefaultDitaOtFramework.class.getResourceAsStream(REVISION_RESOURCE)) {
            return in == null ? null : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        }
    }

    /**
     * Whether the install folder needs the bundle extracted: its home is missing, or it came
     * from a different bundle revision (an install from before revisions has no record).
     */
    static boolean needsExtract(File installDir, String revision) throws IOException {
        if (!new File(installDir, "dita-ot").isDirectory()) {
            return true;
        }
        if (revision == null) {
            return false;
        }
        File recorded = new File(installDir, REVISION_FILE);
        return !recorded.isFile() || !revision.equals(Files.readString(recorded.toPath()).trim());
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    /** True when this build carries the bundled DITA-OT framework. */
    public static boolean isBundled() {
        return DefaultDitaOtFramework.class.getResource(RESOURCE) != null;
    }

    /**
     * Extract the bundled framework into {@code destDir}, for callers that need a
     * working DITA-OT without registering it (such as tests).
     *
     * @return the DITA-OT home inside {@code destDir}
     */
    public static File extractBundled(File destDir) throws IOException {
        extractResourceZip(destDir);
        return new File(destDir, "dita-ot");
    }

    /** Extract the bundled framework zip into {@code destDir} (created if absent). */
    private static void extractResourceZip(File destDir) throws IOException {
        Path destRoot = destDir.toPath().toAbsolutePath().normalize();
        Files.createDirectories(destRoot);
        try (InputStream in = DefaultDitaOtFramework.class.getResourceAsStream(RESOURCE);
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = destRoot.resolve(entry.getName()).normalize();
                // Zip-slip guard: every entry must stay under destRoot.
                if (!target.startsWith(destRoot)) {
                    throw new IOException("Unsafe zip entry outside target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }
}
