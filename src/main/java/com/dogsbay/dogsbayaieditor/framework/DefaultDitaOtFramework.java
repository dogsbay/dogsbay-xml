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
 * {@value #NAME} at version {@value #VERSION} is registered.
 */
public final class DefaultDitaOtFramework {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDitaOtFramework.class);

    /** Tool/family name — the prefix shared by every DITA-OT framework version. */
    public static final String NAME = "DITA-OT";

    /** Bundled DITA-OT version. Keep in sync with {@code dita-ot-wasm/DITA_OT_VERSION}. */
    public static final String VERSION = "4.3.5";

    /** The registered framework name, version-explicit (matches the bundled {@code framework.xml}). */
    public static final String FRAMEWORK_NAME = NAME + " " + VERSION;

    private static final String RESOURCE =
            "/com/dogsbay/dogsbayaieditor/framework/builtin/dita-ot-framework-" + VERSION + ".zip";

    private DefaultDitaOtFramework() {}

    /**
     * Install + register the default framework if it isn't already. Safe to call
     * on every startup; never throws (logs and returns on any failure so a broken
     * bundle can't block editor startup).
     *
     * @param config the editor configuration to register the framework in
     */
    public static void installIfNeeded(ConfigurationProperties config) {
        try {
            if (isRegistered(config)) {
                return;
            }
            if (DefaultDitaOtFramework.class.getResource(RESOURCE) == null) {
                LOG.info("No bundled DITA-OT framework ({}) — skipping default-framework seed", RESOURCE);
                return;
            }

            File installDir = new File(ConfigurationProperties.getDefaultFrameworksDirectory(), FRAMEWORK_NAME);
            File home = new File(installDir, "dita-ot");

            // Extract only when the home is missing — config persists on clean
            // shutdown, but if it didn't (force-quit) we re-register (cheap)
            // without re-extracting ~19 MB.
            if (!home.isDirectory()) {
                extractResourceZip(installDir);
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
