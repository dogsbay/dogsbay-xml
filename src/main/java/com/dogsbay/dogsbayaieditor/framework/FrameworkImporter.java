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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.DogsBayURLUtilities;
import com.dogsbay.dogsbayaieditor.grammar.GrammarProperties;
import com.dogsbay.dogsbayaieditor.grammar.NamespaceProperties;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.dogsbay.dogsbayaieditor.scenario.ScenarioProperties;
import com.dogsbay.dogsbayaieditor.template.TemplateProperties;

/**
 * Handles Framework import from a local directory or a GitHub repository.
 *
 * <p>Usage:
 * <pre>
 *   FrameworkImporter importer = new FrameworkImporter(configProperties);
 *   importer.setProgressListener(listener);
 *   FrameworkProperties framework = importer.importFromDirectory(localDir, installDir);
 *   // or
 *   FrameworkProperties framework = importer.importFromGitHub("https://github.com/owner/repo", installDir);
 * </pre>
 */
public class FrameworkImporter {

    /** Callback for import progress messages. */
    public interface ProgressListener {
        void onProgress(String message);

        /**
         * Optional byte-level progress for long downloads (e.g. DITA-OT).
         * @param downloaded bytes received so far
         * @param total      total bytes, or a negative value if unknown
         */
        default void onBytes(long downloaded, long total) {}
    }

    private final ConfigurationProperties config;
    private ProgressListener progressListener;

    public FrameworkImporter(ConfigurationProperties config) {
        this.config = config;
    }

    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }

    private void progress(String msg) {
        if (progressListener != null) {
            progressListener.onProgress(msg);
        }
    }

    private static void progress(ProgressListener listener, String msg) {
        if (listener != null) {
            listener.onProgress(msg);
        }
    }

    /**
     * Import a framework from a local directory.
     *
     * @param sourceDir  the framework source directory (contains framework.xml)
     * @param installDir the directory into which the framework will be installed;
     *                   if null the default frameworks directory is used
     * @return the created FrameworkProperties, already registered in config
     * @throws IOException if the source is invalid or the framework cannot be parsed
     */
    public FrameworkProperties importFromDirectory(File sourceDir, File installDir) throws IOException {
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new IOException("Framework source directory does not exist: " + sourceDir);
        }

        File frameworkXml = new File(sourceDir, "framework.xml");
        if (!frameworkXml.exists()) {
            throw new IOException("framework.xml not found in: " + sourceDir);
        }

        // Determine install location
        File targetDir = resolveInstallDir(installDir, sourceDir.getName());

        // Copy framework directory to install location if different
        if (!sourceDir.getCanonicalPath().equals(targetDir.getCanonicalPath())) {
            progress("Copying framework to " + targetDir);
            copyDirectory(sourceDir, targetDir);
        }

        return parseAndRegister(new File(targetDir, "framework.xml"), targetDir, null);
    }

    /**
     * Import a framework from a GitHub repository URL.
     * Downloads the repository ZIP and extracts it to the install directory.
     *
     * @param githubUrl  full GitHub URL e.g. {@code https://github.com/owner/repo}
     * @param installDir target install directory; if null uses the default
     * @return the created FrameworkProperties, already registered in config
     * @throws IOException on download or parse failure
     */
    public FrameworkProperties importFromGitHub(String githubUrl, File installDir) throws IOException {
        String zipUrl = toZipUrl(githubUrl.trim());
        progress("Downloading " + zipUrl);

        // Determine install dir before extracting to get the repo name
        String repoName = extractRepoName(githubUrl);
        File targetDir = resolveInstallDir(installDir, repoName);

        progress("Extracting to " + targetDir);
        extractZip(new URL(zipUrl), targetDir);

        File frameworkXml = new File(targetDir, "framework.xml");
        if (!frameworkXml.exists()) {
            throw new IOException("framework.xml not found after extracting from " + zipUrl);
        }

        return parseAndRegister(frameworkXml, targetDir, githubUrl.trim());
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private File resolveInstallDir(File requestedDir, String defaultName) {
        if (requestedDir != null) {
            return requestedDir;
        }
        return new File(ConfigurationProperties.getDefaultFrameworksDirectory(), defaultName);
    }

    /**
     * Parse framework.xml, register types/scenarios/templates, create and
     * register a FrameworkProperties entry in the config.
     */
    private FrameworkProperties parseAndRegister(File frameworkXml, File frameworkDir, String sourceUrl)
            throws IOException {
        URL xmlUrl;
        try {
            xmlUrl = DogsBayURLUtilities.getURLFromFile(frameworkXml);
        } catch (Exception e) {
            throw new IOException("Cannot convert framework.xml to URL", e);
        }

        DogsBayDocument doc;
        try {
            doc = new DogsBayDocument(xmlUrl);
            doc.loadWithoutSubstitution();
        } catch (Exception e) {
            throw new IOException("Failed to parse framework.xml: " + e.getMessage(), e);
        }

        XElement root = doc.getRoot();
        if (root == null || !root.getName().equals("framework")) {
            throw new IOException("Invalid framework.xml: root element must be <framework>");
        }

        String name    = root.getAttribute("name");
        String version = root.getAttribute("version");

        if (name == null || name.trim().isEmpty()) {
            throw new IOException("framework.xml is missing required 'name' attribute on <framework>");
        }

        progress("Importing framework: " + name + " v" + version);

        // Types
        XElement typesEl = root.getElement("types");
        if (typesEl != null) {
            XElement[] typeRefs = typesEl.getElements("type");
            if (typeRefs != null) {
                for (XElement typeRef : typeRefs) {
                    importTypeFile(typeRef.getAttribute("src"), frameworkDir, frameworkXml);
                }
            }
        }

        // Scenarios
        XElement scenariosEl = root.getElement("scenarios");
        if (scenariosEl != null) {
            XElement[] scenarioRefs = scenariosEl.getElements("scenario");
            if (scenarioRefs != null) {
                for (XElement scenarioRef : scenarioRefs) {
                    importScenarioFile(scenarioRef.getAttribute("src"), frameworkDir, frameworkXml);
                }
            }
        }

        // Templates
        XElement templatesEl = root.getElement("templates");
        if (templatesEl != null) {
            XElement[] templateRefs = templatesEl.getElements("template");
            if (templateRefs != null) {
                for (XElement templateRef : templateRefs) {
                    importTemplateFile(templateRef.getAttribute("src"), frameworkDir);
                }
            }
        }

        // Catalogs
        XElement catalogsEl = root.getElement("catalogs");
        if (catalogsEl != null) {
            XElement[] catalogRefs = catalogsEl.getElements("catalog");
            if (catalogRefs != null) {
                for (XElement catalogRef : catalogRefs) {
                    registerCatalog(catalogRef.getAttribute("src"), frameworkDir);
                }
            }
        }

        // DITA-OT tool. May declare an <asset> to download on first use; when
        // present and the src directory is missing, the asset is fetched,
        // checksum-verified, and extracted before the tool is registered.
        String ditaOtPath = null;
        XElement toolEl = root.getElement("tool");
        if (toolEl != null && "dita-ot".equals(toolEl.getAttribute("name"))) {
            String toolSrc = toolEl.getAttribute("src");
            if (toolSrc != null && !toolSrc.trim().isEmpty()) {
                File ditaOtDir = new File(toolSrc).isAbsolute()
                        ? new File(toolSrc)
                        : new File(frameworkDir, toolSrc);

                XElement assetEl = toolEl.getElement("asset");
                if (assetEl != null && (!ditaOtDir.exists() || !ditaOtDir.isDirectory())) {
                    ensureAsset(ditaOtDir,
                            assetEl.getAttribute("url"),
                            assetEl.getAttribute("sha256"),
                            parseStripComponents(assetEl.getAttribute("strip-components")),
                            new File(frameworkDir, ".download"),
                            progressListener);
                } else if (assetEl != null) {
                    progress("DITA-OT already present, skipping asset download.");
                }

                if (ditaOtDir.exists() && ditaOtDir.isDirectory()) {
                    ditaOtPath = ditaOtDir.getAbsolutePath();
                    progress("Found DITA-OT at: " + ditaOtPath);
                }
            }
        }

        // Build and register FrameworkProperties
        FrameworkProperties framework = new FrameworkProperties();
        framework.setName(name);
        if (version != null) framework.setVersion(version);
        framework.setFolderPath(frameworkDir.getAbsolutePath());
        if (ditaOtPath != null) framework.setDitaOtPath(ditaOtPath);
        if (sourceUrl != null) framework.setSourceUrl(sourceUrl);

        config.addFrameworkProperties(framework);
        progress("Framework '" + name + "' installed successfully.");

        return framework;
    }

    /** Register a catalog file referenced from framework.xml. */
    private void registerCatalog(String src, File frameworkDir) {
        if (src == null || src.trim().isEmpty()) return;
        File catalogFile = new File(src).isAbsolute()
                ? new File(src)
                : new File(frameworkDir, src);
        if (!catalogFile.exists()) {
            progress("Warning: catalog not found: " + catalogFile);
            return;
        }
        config.addCatalog(catalogFile.getAbsolutePath());
        progress("  Registered catalog: " + catalogFile.getAbsolutePath());
    }

    /** Import a single .type file referenced from framework.xml. */
    private void importTypeFile(String src, File frameworkDir, File frameworkXml) {
        if (src == null || src.trim().isEmpty()) return;

        File typeFile = new File(frameworkDir, src);
        if (!typeFile.exists()) {
            progress("Warning: type file not found: " + typeFile);
            return;
        }

        try {
            URL typeUrl = DogsBayURLUtilities.getURLFromFile(typeFile);
            DogsBayDocument doc = new DogsBayDocument(typeUrl);
            doc.loadWithoutSubstitution();
            XElement root = doc.getRoot();

            if (root != null && root.getName().equals("types")) {
                XElement[] types = root.getElements("type");
                if (types != null) {
                    for (XElement type : types) {
                        GrammarProperties gp = new GrammarProperties(config, typeUrl, type);
                        removeExistingGrammar(gp.getDescription());
                        addNamespaceMappings(gp);
                        config.addGrammarProperties(gp);
                        progress("  Added type: " + gp.getDescription());
                    }
                }
            } else if (root != null && root.getName().equals("type")) {
                // Single-type file
                GrammarProperties gp = new GrammarProperties(config, typeUrl, root);
                removeExistingGrammar(gp.getDescription());
                addNamespaceMappings(gp);
                config.addGrammarProperties(gp);
                progress("  Added type: " + gp.getDescription());
            }
        } catch (Exception e) {
            progress("Warning: failed to import type " + src + ": " + e.getMessage());
        }
    }

    /** Import a single .scenario file referenced from framework.xml. */
    private void importScenarioFile(String src, File frameworkDir, File frameworkXml) {
        if (src == null || src.trim().isEmpty()) return;

        File scenarioFile = new File(frameworkDir, src);
        if (!scenarioFile.exists()) {
            progress("Warning: scenario file not found: " + scenarioFile);
            return;
        }

        try {
            URL scenarioUrl = DogsBayURLUtilities.getURLFromFile(scenarioFile);
            DogsBayDocument doc = new DogsBayDocument(scenarioUrl);
            doc.loadWithoutSubstitution();
            XElement root = doc.getRoot();

            if (root != null && root.getName().equals("scenarios")) {
                XElement[] scenarios = root.getElements("scenario");
                if (scenarios != null) {
                    for (XElement scenario : scenarios) {
                        ScenarioProperties sp = new ScenarioProperties(scenarioUrl, scenario);
                        removeExistingScenario(sp.getName());
                        config.addScenarioProperties(sp);
                        progress("  Added scenario: " + sp.getName());
                    }
                }
            } else if (root != null && root.getName().equals("scenario")) {
                ScenarioProperties sp = new ScenarioProperties(scenarioUrl, root);
                removeExistingScenario(sp.getName());
                config.addScenarioProperties(sp);
                progress("  Added scenario: " + sp.getName());
            }
        } catch (Exception e) {
            progress("Warning: failed to import scenario " + src + ": " + e.getMessage());
        }
    }

    /** Import a single template file referenced from framework.xml. */
    private void importTemplateFile(String src, File frameworkDir) {
        if (src == null || src.trim().isEmpty()) return;

        File templateFile = new File(frameworkDir, src);
        if (!templateFile.exists()) {
            progress("Warning: template file not found: " + templateFile);
            return;
        }

        try {
            // Extract display name from filename (strip extension)
            String fileName = templateFile.getName();
            int dot = fileName.lastIndexOf('.');
            String templateName = dot > 0 ? fileName.substring(0, dot) : fileName;

            URL templateUrl = DogsBayURLUtilities.getURLFromFile(templateFile);
            TemplateProperties tp = new TemplateProperties(templateName, templateUrl);
            removeExistingTemplate(templateName);
            config.addTemplateProperties(tp);
            progress("  Added template: " + templateName);
        } catch (Exception e) {
            progress("Warning: failed to import template " + src + ": " + e.getMessage());
        }
    }

    private void removeExistingGrammar(String description) {
        if (description == null) return;
        Vector grammars = config.getGrammarProperties();
        for (int i = 0; i < grammars.size(); i++) {
            GrammarProperties gp = (GrammarProperties) grammars.elementAt(i);
            if (description.equals(gp.getDescription())) {
                config.removeGrammarProperties(gp);
                return;
            }
        }
    }

    private void removeExistingScenario(String name) {
        if (name == null) return;
        Vector scenarios = config.getScenarioProperties();
        for (int i = 0; i < scenarios.size(); i++) {
            ScenarioProperties sp = (ScenarioProperties) scenarios.elementAt(i);
            if (name.equals(sp.getName())) {
                config.removeScenarioProperties(sp);
                return;
            }
        }
    }

    private void removeExistingTemplate(String name) {
        if (name == null) return;
        Vector templates = config.getTemplateProperties();
        for (int i = 0; i < templates.size(); i++) {
            TemplateProperties tp = (TemplateProperties) templates.elementAt(i);
            if (name.equals(tp.getName())) {
                config.removeTemplateProperties(tp);
                return;
            }
        }
    }

    private void addNamespaceMappings(GrammarProperties gp) {
        String prefix = gp.getNamespacePrefix();
        String ns     = gp.getNamespace();
        if (prefix != null && !prefix.isEmpty() && ns != null && !ns.isEmpty()) {
            config.addPrefixNamespaceMapping(prefix, ns);
        }
        Vector namespaces = gp.getNamespaces();
        for (int j = 0; j < namespaces.size(); j++) {
            NamespaceProperties nsProp = (NamespaceProperties) namespaces.elementAt(j);
            if (nsProp.getPrefix() != null && !nsProp.getPrefix().isEmpty()
                    && nsProp.getURI() != null && !nsProp.getURI().isEmpty()) {
                config.addPrefixNamespaceMapping(nsProp.getPrefix(), nsProp.getURI());
            }
        }
    }

    // -------------------------------------------------------------------------
    // GitHub / ZIP helpers
    // -------------------------------------------------------------------------

    /**
     * Convert a GitHub repo URL to a ZIP download URL.
     * E.g. {@code https://github.com/owner/repo} →
     *      {@code https://github.com/owner/repo/archive/refs/heads/main.zip}
     */
    static String toZipUrl(String githubUrl) {
        String url = githubUrl;
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url + "/archive/refs/heads/main.zip";
    }

    /** Extract the repository name from a GitHub URL. */
    static String extractRepoName(String githubUrl) {
        String url = githubUrl.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        int slash = url.lastIndexOf('/');
        return slash >= 0 ? url.substring(slash + 1) : url;
    }

    /**
     * Download a ZIP from the given URL and extract its contents into targetDir.
     * GitHub ZIP archives contain a single top-level folder (repo-main/); we
     * strip that prefix so the framework files land directly in targetDir.
     */
    static void extractZip(URL zipUrl, File targetDir) throws IOException {
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        try (InputStream in = zipUrl.openStream();
             ZipInputStream zis = new ZipInputStream(in)) {

            ZipEntry entry;
            String stripPrefix = null; // the top-level folder to strip

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                // Determine the top-level prefix on first entry
                if (stripPrefix == null) {
                    int slash = name.indexOf('/');
                    stripPrefix = slash >= 0 ? name.substring(0, slash + 1) : "";
                }

                // Strip prefix
                if (!name.startsWith(stripPrefix)) {
                    zis.closeEntry();
                    continue;
                }
                String relative = name.substring(stripPrefix.length());
                if (relative.isEmpty()) {
                    zis.closeEntry();
                    continue;
                }

                File outFile = new File(targetDir, relative);

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    outFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = zis.read(buf)) > 0) {
                            fos.write(buf, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Asset delivery (download external tool resources referenced by <asset>)
    // -------------------------------------------------------------------------

    /**
     * Ensure {@code srcDir} exists by downloading and extracting the referenced
     * asset. Downloads to {@code downloadDir}, verifies the SHA-256, extracts
     * into {@code srcDir} (applying {@code stripComponents}), and removes the
     * temporary archive. On any failure the partial extract is rolled back.
     *
     * @throws IOException on a missing attribute, download error, non-200
     *                     response, checksum mismatch, or extraction failure
     */
    static void ensureAsset(File srcDir, String url, String sha256, int stripComponents,
                            File downloadDir, ProgressListener listener) throws IOException {
        if (url == null || url.trim().isEmpty()) {
            throw new IOException("<asset> is missing the required 'url' attribute");
        }
        if (sha256 == null || sha256.trim().isEmpty()) {
            throw new IOException("<asset> is missing the required 'sha256' attribute for " + url);
        }

        String basename = url.substring(url.lastIndexOf('/') + 1);
        if (basename.isEmpty()) {
            basename = "asset.zip";
        }
        File tmpArchive = new File(downloadDir, basename);
        boolean createdSrcDir = false;

        try {
            progress(listener, "Downloading " + url);
            downloadToFile(url, tmpArchive, listener);

            progress(listener, "Verifying checksum");
            verifySha256(tmpArchive, sha256);

            progress(listener, "Extracting to " + srcDir);
            createdSrcDir = !srcDir.exists();
            extractZip(tmpArchive, srcDir, stripComponents);
        } catch (IOException e) {
            if (createdSrcDir) {
                deleteRecursively(srcDir);
            }
            throw e;
        } finally {
            if (tmpArchive.exists()) {
                tmpArchive.delete();
            }
            String[] leftovers = downloadDir.list();
            if (downloadDir.isDirectory() && leftovers != null && leftovers.length == 0) {
                downloadDir.delete();
            }
        }
    }

    /**
     * Download {@code url} to {@code dest}, following redirects (GitHub release
     * URLs redirect to a CDN). Reports byte progress through {@code listener}.
     *
     * @throws IOException on a non-200 response or any transport error
     */
    static void downloadToFile(String url, File dest, ProgressListener listener) throws IOException {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();

        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted: " + url, e);
        }

        int status = response.statusCode();
        if (status != 200) {
            // Drain and close the body so the connection is released.
            try (InputStream body = response.body()) {
                body.readAllBytes();
            } catch (IOException ignored) {
                // best effort
            }
            throw new IOException("Download failed: HTTP " + status + " for " + url);
        }

        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        File parent = dest.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        long downloaded = 0;
        try (InputStream in = response.body();
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                downloaded += n;
                if (listener != null) {
                    listener.onBytes(downloaded, total);
                }
            }
        }
    }

    /**
     * Verify that {@code file}'s SHA-256 equals {@code expectedHex} (case-insensitive).
     *
     * @throws IOException if the digests differ
     */
    static void verifySha256(File file, String expectedHex) throws IOException {
        String actual = sha256Hex(file);
        String expected = expectedHex.trim();
        if (!actual.equalsIgnoreCase(expected)) {
            throw new IOException("Checksum mismatch for " + file.getName()
                    + ": expected " + expected + " but computed " + actual);
        }
    }

    /** Compute the lowercase hex SHA-256 of a file. */
    static String sha256Hex(File file) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * Extract a local ZIP file into {@code targetDir}, stripping the first
     * {@code stripComponents} path segments from each entry (mirrors
     * {@code tar --strip-components}). Guards against Zip Slip path traversal.
     *
     * <p>Unix file permissions stored in the archive's central directory are
     * preserved (so e.g. {@code bin/dita} stays executable). Uses Ant's
     * {@code org.apache.tools.zip.ZipFile}, which exposes the per-entry Unix
     * mode that {@link java.util.zip.ZipInputStream} discards. Permissions are
     * applied after all content is written, so a restrictive directory mode
     * cannot block extraction of files into it.
     */
    static void extractZip(File zipFile, File targetDir, int stripComponents) throws IOException {
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        String canonicalTarget = targetDir.getCanonicalPath();
        boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

        List<File> modeFiles = new ArrayList<>();
        List<Integer> modeValues = new ArrayList<>();

        org.apache.tools.zip.ZipFile zip = new org.apache.tools.zip.ZipFile(zipFile);
        try {
            Enumeration<org.apache.tools.zip.ZipEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                org.apache.tools.zip.ZipEntry entry = entries.nextElement();
                String relative = stripLeadingComponents(entry.getName(), stripComponents);
                if (relative == null || relative.isEmpty()) {
                    continue;
                }

                File outFile = new File(targetDir, relative);
                String canonicalOut = outFile.getCanonicalPath();
                if (!canonicalOut.equals(canonicalTarget)
                        && !canonicalOut.startsWith(canonicalTarget + File.separator)) {
                    throw new IOException("Zip entry escapes target directory: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    File outParent = outFile.getParentFile();
                    if (outParent != null) {
                        outParent.mkdirs();
                    }
                    try (InputStream in = zip.getInputStream(entry);
                         FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            fos.write(buf, 0, len);
                        }
                    }
                }

                int mode = entry.getUnixMode();
                if (mode != 0) {
                    modeFiles.add(outFile);
                    modeValues.add(mode);
                }
            }
        } finally {
            org.apache.tools.zip.ZipFile.closeQuietly(zip);
        }

        for (int i = 0; i < modeFiles.size(); i++) {
            applyUnixMode(modeFiles.get(i), modeValues.get(i), posix);
        }
    }

    /** Applies a Unix permission {@code mode} (from a zip entry) to {@code file}. */
    private static void applyUnixMode(File file, int mode, boolean posixSupported) throws IOException {
        if (!file.exists()) {
            return;
        }
        if (posixSupported) {
            Files.setPosixFilePermissions(file.toPath(), posixPermissions(mode));
        } else {
            // Non-POSIX filesystem (e.g. Windows): only the owner-execute bit is meaningful.
            file.setExecutable((mode & 0100) != 0, false);
        }
    }

    private static Set<PosixFilePermission> posixPermissions(int mode) {
        Set<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);
        if ((mode & 0400) != 0) perms.add(PosixFilePermission.OWNER_READ);
        if ((mode & 0200) != 0) perms.add(PosixFilePermission.OWNER_WRITE);
        if ((mode & 0100) != 0) perms.add(PosixFilePermission.OWNER_EXECUTE);
        if ((mode & 0040) != 0) perms.add(PosixFilePermission.GROUP_READ);
        if ((mode & 0020) != 0) perms.add(PosixFilePermission.GROUP_WRITE);
        if ((mode & 0010) != 0) perms.add(PosixFilePermission.GROUP_EXECUTE);
        if ((mode & 0004) != 0) perms.add(PosixFilePermission.OTHERS_READ);
        if ((mode & 0002) != 0) perms.add(PosixFilePermission.OTHERS_WRITE);
        if ((mode & 0001) != 0) perms.add(PosixFilePermission.OTHERS_EXECUTE);
        return perms;
    }

    /**
     * Remove the first {@code n} leading path segments from a ZIP entry name.
     * Returns an empty string if the entry has fewer than {@code n} segments.
     */
    static String stripLeadingComponents(String name, int n) {
        if (n <= 0) {
            return name;
        }
        String s = name;
        for (int stripped = 0; stripped < n; stripped++) {
            int slash = s.indexOf('/');
            if (slash < 0) {
                return "";
            }
            s = s.substring(slash + 1);
        }
        return s;
    }

    static int parseStripComponents(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] children = f.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        f.delete();
    }

    /** Recursively copy a directory. */
    private static void copyDirectory(File src, File dst) throws IOException {
        if (!dst.exists()) {
            dst.mkdirs();
        }
        File[] files = src.listFiles();
        if (files == null) return;
        for (File file : files) {
            File target = new File(dst, file.getName());
            if (file.isDirectory()) {
                copyDirectory(file, target);
            } else {
                copyFile(file, target);
            }
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) > 0) {
                fos.write(buf, 0, len);
            }
        }
    }

    /**
     * Remove an installed framework: deregisters its types, scenarios, and
     * templates from config, then removes the FrameworkProperties entry.
     *
     * @param framework the framework to remove
     */
    public void removeFramework(FrameworkProperties framework) {
        // We can only de-register by name/path if we stored framework folder path
        // For now, just remove the FrameworkProperties entry from config
        config.removeFrameworkProperties(framework);
        progress("Framework '" + framework.getName() + "' removed.");
    }
}
