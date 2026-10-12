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

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;
import com.sun.net.httpserver.HttpServer;

/**
 * Tests for the framework {@code <asset>} delivery mechanism: download,
 * SHA-256 verification, strip-components extraction, and failure rollback.
 *
 * <p>Network behavior is exercised against a loopback {@link HttpServer}
 * bound to an ephemeral port, so the tests are hermetic and offline-safe.
 */
class FrameworkAssetTest {

    private Path tempDir;
    private HttpServer server;

    @BeforeAll
    static void initDom4j() {
        // ConfigurationProperties / DogsBayDocument need the project's XElement factory.
        System.setProperty("org.dom4j.factory", "com.dogsbay.xml.XDocumentFactory");
    }

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("framework-asset-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    // -------------------------------------------------------------------------
    // SHA-256
    // -------------------------------------------------------------------------

    @Test
    void sha256Hex_matchesKnownVector() throws Exception {
        // SHA-256("abc") per FIPS 180-2.
        File f = writeFile("abc.txt", "abc");
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                FrameworkImporter.sha256Hex(f));
    }

    @Test
    void verifySha256_acceptsMatchingDigestCaseInsensitive() throws Exception {
        File f = writeFile("abc.txt", "abc");
        assertDoesNotThrow(() -> FrameworkImporter.verifySha256(f,
                "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"));
    }

    @Test
    void verifySha256_rejectsMismatchWithBothDigestsInMessage() throws Exception {
        File f = writeFile("abc.txt", "abc");
        IOException ex = assertThrows(IOException.class,
                () -> FrameworkImporter.verifySha256(f, "deadbeef"));
        assertTrue(ex.getMessage().contains("deadbeef"), "expected digest in message");
        assertTrue(ex.getMessage().toLowerCase().contains("ba7816bf"), "computed digest in message");
    }

    // -------------------------------------------------------------------------
    // strip-components parsing + path stripping
    // -------------------------------------------------------------------------

    @Test
    void parseStripComponents_defaultsAndClamps() {
        assertEquals(0, FrameworkImporter.parseStripComponents(null));
        assertEquals(0, FrameworkImporter.parseStripComponents(""));
        assertEquals(0, FrameworkImporter.parseStripComponents("notanumber"));
        assertEquals(0, FrameworkImporter.parseStripComponents("-3"));
        assertEquals(2, FrameworkImporter.parseStripComponents("2"));
    }

    @Test
    void stripLeadingComponents_behaviour() {
        assertEquals("dita-ot/bin/dita", FrameworkImporter.stripLeadingComponents("dita-ot/bin/dita", 0));
        assertEquals("bin/dita", FrameworkImporter.stripLeadingComponents("dita-ot/bin/dita", 1));
        assertEquals("dita", FrameworkImporter.stripLeadingComponents("dita-ot/bin/dita", 2));
        // Fewer segments than requested -> empty (entry skipped by extractor).
        assertEquals("", FrameworkImporter.stripLeadingComponents("dita-ot/", 1));
        assertEquals("", FrameworkImporter.stripLeadingComponents("top", 1));
    }

    // -------------------------------------------------------------------------
    // extractZip with strip-components
    // -------------------------------------------------------------------------

    @Test
    void extractZip_stripsLeadingComponent() throws Exception {
        File zip = buildZip("dita-ot.zip");
        File target = tempDir.resolve("out").toFile();

        FrameworkImporter.extractZip(zip, target, 1);

        assertTrue(new File(target, "bin/dita").isFile(), "stripped file extracted");
        assertFalse(new File(target, "dita-ot").exists(), "top-level dir should be stripped");
    }

    @Test
    void extractZip_preservesUnixExecutableBit() throws Exception {
        Assumptions.assumeTrue(
                FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "POSIX permissions not supported on this filesystem");

        File zip = buildZipWithModes("tool.zip");
        File target = tempDir.resolve("out").toFile();

        FrameworkImporter.extractZip(zip, target, 1);

        File script = new File(target, "bin/run");
        File readme = new File(target, "readme.txt");
        assertTrue(script.isFile() && readme.isFile(), "both entries extracted");

        assertTrue(Files.getPosixFilePermissions(script.toPath()).contains(PosixFilePermission.OWNER_EXECUTE),
                "executable bit (0755) must be preserved on bin/run");
        assertFalse(Files.getPosixFilePermissions(readme.toPath()).contains(PosixFilePermission.OWNER_EXECUTE),
                "a 0644 file must not become executable");
    }

    @Test
    void extractZip_rejectsZipSlip() throws Exception {
        File zip = tempDir.resolve("evil.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip.toPath()))) {
            ZipEntry e = new ZipEntry("../escape.txt");
            zos.putNextEntry(e);
            zos.write("pwned".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        File target = tempDir.resolve("out").toFile();
        assertThrows(IOException.class, () -> FrameworkImporter.extractZip(zip, target, 0));
        assertFalse(new File(tempDir.toFile(), "escape.txt").exists(), "escape must not be written");
    }

    // -------------------------------------------------------------------------
    // downloadToFile
    // -------------------------------------------------------------------------

    @Test
    void downloadToFile_writesBytesAndReportsProgress() throws Exception {
        byte[] payload = "hello world payload".getBytes(StandardCharsets.UTF_8);
        String url = startServingBytes("/blob.bin", payload);

        File dest = tempDir.resolve("dl/blob.bin").toFile();
        long[] lastSeen = {0};
        FrameworkImporter.downloadToFile(url, dest, new FrameworkImporter.ProgressListener() {
            public void onProgress(String message) {}
            public void onBytes(long downloaded, long total) { lastSeen[0] = downloaded; }
        });

        assertArrayEquals(payload, Files.readAllBytes(dest.toPath()));
        assertEquals(payload.length, lastSeen[0], "final progress should equal payload size");
    }

    @Test
    void downloadToFile_throwsOn404WithUrlInMessage() throws Exception {
        String base = startServer();
        // No handler registered for this path -> explicit 404.
        String url = base + "/nope.bin";

        File dest = tempDir.resolve("dl/nope.bin").toFile();
        IOException ex = assertThrows(IOException.class,
                () -> FrameworkImporter.downloadToFile(url, dest, null));
        assertTrue(ex.getMessage().contains("404"), "status in message");
        assertTrue(ex.getMessage().contains(url), "url in message");
        assertFalse(dest.exists(), "no file written on failure");
    }

    // -------------------------------------------------------------------------
    // ensureAsset — full flow
    // -------------------------------------------------------------------------

    @Test
    void ensureAsset_downloadsVerifiesAndExtracts() throws Exception {
        File zip = buildZip("dita-ot.zip");
        byte[] zipBytes = Files.readAllBytes(zip.toPath());
        String sha = FrameworkImporter.sha256Hex(zip);
        String url = startServingBytes("/dita-ot.zip", zipBytes);

        File srcDir = tempDir.resolve("framework/dita-ot").toFile();
        File downloadDir = tempDir.resolve("framework/.download").toFile();

        FrameworkImporter.ensureAsset(srcDir, url, sha, 1, downloadDir, null);

        assertTrue(new File(srcDir, "bin/dita").isFile(), "asset extracted into src dir");
        assertFalse(downloadDir.exists(), "temp download dir cleaned up on success");
    }

    @Test
    void ensureAsset_wrongSha256_failsAndLeavesNoPartialFiles() throws Exception {
        File zip = buildZip("dita-ot.zip");
        byte[] zipBytes = Files.readAllBytes(zip.toPath());
        String url = startServingBytes("/dita-ot.zip", zipBytes);

        File srcDir = tempDir.resolve("framework/dita-ot").toFile();
        File downloadDir = tempDir.resolve("framework/.download").toFile();

        IOException ex = assertThrows(IOException.class, () ->
                FrameworkImporter.ensureAsset(srcDir, url,
                        "0000000000000000000000000000000000000000000000000000000000000000",
                        1, downloadDir, null));
        assertTrue(ex.getMessage().toLowerCase().contains("checksum"), "clear checksum error");
        assertFalse(srcDir.exists(), "no extracted files on checksum failure");
        assertFalse(downloadDir.exists(), "temp download dir cleaned up on failure");
    }

    @Test
    void ensureAsset_404_failsClearly() throws Exception {
        String base = startServer();
        String url = base + "/missing.zip";

        File srcDir = tempDir.resolve("framework/dita-ot").toFile();
        File downloadDir = tempDir.resolve("framework/.download").toFile();

        IOException ex = assertThrows(IOException.class, () ->
                FrameworkImporter.ensureAsset(srcDir, url, "irrelevant", 1, downloadDir, null));
        assertTrue(ex.getMessage().contains("404"), "status in message");
        assertFalse(srcDir.exists(), "no src dir created");
        assertFalse(downloadDir.exists(), "no download dir left behind");
    }

    @Test
    void ensureAsset_missingUrlOrSha_failsFast() {
        File srcDir = tempDir.resolve("dita-ot").toFile();
        File downloadDir = tempDir.resolve(".download").toFile();
        assertThrows(IOException.class,
                () -> FrameworkImporter.ensureAsset(srcDir, null, "abc", 1, downloadDir, null));
        assertThrows(IOException.class,
                () -> FrameworkImporter.ensureAsset(srcDir, "http://x/y.zip", "  ", 1, downloadDir, null));
    }

    // -------------------------------------------------------------------------
    // Integration: <asset> through importFromDirectory (parseAndRegister gate)
    // -------------------------------------------------------------------------

    @Test
    void importFromDirectory_downloadsAssetWhenToolDirAbsent() throws Exception {
        File zip = buildZip("dita-ot.zip");
        String sha = FrameworkImporter.sha256Hex(zip);
        String url = startServingBytes("/dita-ot.zip", Files.readAllBytes(zip.toPath()));

        File frameworkDir = tempDir.resolve("framework").toFile();
        frameworkDir.mkdirs();
        writeFrameworkXml(frameworkDir, url, sha, "1");

        FrameworkImporter importer = new FrameworkImporter(newEmptyConfig());
        FrameworkProperties framework = importer.importFromDirectory(frameworkDir, frameworkDir);

        File ditaOtDir = new File(frameworkDir, "dita-ot");
        assertTrue(ditaOtDir.isDirectory(), "asset extracted to tool dir");
        assertTrue(new File(ditaOtDir, "bin/dita").isFile(), "asset contents present");
        assertEquals(ditaOtDir.getAbsolutePath(), framework.getDitaOtPath(),
                "framework should record the downloaded DITA-OT path");
        assertFalse(new File(frameworkDir, ".download").exists(), "temp download dir cleaned up");
    }

    @Test
    void importFromDirectory_skipsDownloadWhenToolDirPresent() throws Exception {
        // Asset URL points at an unregistered path -> would 404 if (wrongly) fetched.
        String base = startServer();
        String url = base + "/should-not-be-fetched.zip";

        File frameworkDir = tempDir.resolve("framework").toFile();
        File ditaOtDir = new File(frameworkDir, "dita-ot");
        ditaOtDir.mkdirs();
        Files.write(new File(ditaOtDir, "marker.txt").toPath(),
                "preinstalled".getBytes(StandardCharsets.UTF_8));
        writeFrameworkXml(frameworkDir, url, "irrelevant", "1");

        FrameworkImporter importer = new FrameworkImporter(newEmptyConfig());
        FrameworkProperties framework = importer.importFromDirectory(frameworkDir, frameworkDir);

        assertEquals(ditaOtDir.getAbsolutePath(), framework.getDitaOtPath());
        assertTrue(new File(ditaOtDir, "marker.txt").isFile(), "preinstalled content untouched");
        assertFalse(new File(frameworkDir, ".download").exists(), "no download attempted");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private File writeFile(String name, String content) throws IOException {
        File f = tempDir.resolve(name).toFile();
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    private void writeFrameworkXml(File frameworkDir, String assetUrl, String sha256, String strip)
            throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<framework name=\"DITA-OT Test\" version=\"1.0\">\n"
                + "  <description>Test asset framework</description>\n"
                + "  <tool name=\"dita-ot\" src=\"dita-ot\">\n"
                + "    <asset url=\"" + assetUrl + "\" sha256=\"" + sha256
                + "\" strip-components=\"" + strip + "\"/>\n"
                + "  </tool>\n"
                + "</framework>\n";
        Files.write(new File(frameworkDir, "framework.xml").toPath(),
                xml.getBytes(StandardCharsets.UTF_8));
    }

    private ConfigurationProperties newEmptyConfig() throws Exception {
        XElement root = new XElement("dogsbay");
        root.setText("\n");
        DogsBayDocument doc = new DogsBayDocument(
                Path.of(System.getProperty("java.io.tmpdir"),
                        "test-config-" + System.nanoTime() + ".xml").toUri().toURL(),
                root);
        return new ConfigurationProperties(doc) {
            @Override public void save() { /* no-op for tests */ }
            @Override public void saveToDisk() { /* no-op for tests */ }
        };
    }

    /** Build a ZIP mimicking a DITA-OT release: a single top-level folder. */
    private File buildZip(String name) throws IOException {
        File zip = tempDir.resolve(name).toFile();
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip.toPath()))) {
            zos.putNextEntry(new ZipEntry("dita-ot/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("dita-ot/bin/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("dita-ot/bin/dita"));
            zos.write("#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return zip;
    }

    /** Build a ZIP carrying Unix modes (0755 script, 0644 file) under a top-level folder. */
    private File buildZipWithModes(String name) throws IOException {
        File zip = tempDir.resolve(name).toFile();
        try (org.apache.tools.zip.ZipOutputStream zos =
                     new org.apache.tools.zip.ZipOutputStream(zip)) {
            org.apache.tools.zip.ZipEntry binDir = new org.apache.tools.zip.ZipEntry("tool/bin/");
            binDir.setUnixMode(0755);
            zos.putNextEntry(binDir);
            zos.closeEntry();

            org.apache.tools.zip.ZipEntry script = new org.apache.tools.zip.ZipEntry("tool/bin/run");
            script.setUnixMode(0755);
            zos.putNextEntry(script);
            zos.write("#!/bin/sh\necho hi\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            org.apache.tools.zip.ZipEntry readme = new org.apache.tools.zip.ZipEntry("tool/readme.txt");
            readme.setUnixMode(0644);
            zos.putNextEntry(readme);
            zos.write("hello\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return zip;
    }

    /** Start a server with a single GET handler returning {@code bytes}; returns the full URL. */
    private String startServingBytes(String path, byte[] bytes) throws IOException {
        String base = startServer();
        server.createContext(path, exchange -> {
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        return base + path;
    }

    /** Start a loopback server on an ephemeral port; unhandled paths yield 404. */
    private String startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
