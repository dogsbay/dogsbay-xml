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
package com.dogsbay.agent.acp;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Installs a registry agent that ships as a downloadable archive into
 * {@code ~/.dogsbay/agents/<id>/}, where the catalog finds it afterwards.
 * Nothing is added to the PATH and nothing else on the machine changes;
 * deleting the folder uninstalls. Archives are checked against the
 * registry's SHA-256 when it gives one.
 */
public final class AgentInstaller {

    /** System property that relocates the managed-agents folder, for tests. */
    public static final String HOME_PROPERTY = "dogsbay.agents.home";

    /** Opens the archive; the default follows redirects over HTTPS. */
    @FunctionalInterface
    public interface Fetcher {
        InputStream open(URI uri) throws IOException;
    }

    private final Path home;
    private final Fetcher fetcher;

    public AgentInstaller() {
        this(home(), AgentInstaller::openHttp);
    }

    AgentInstaller(Path home, Fetcher fetcher) {
        this.home = home;
        this.fetcher = fetcher;
    }

    /** Where managed agents live. */
    public static Path home() {
        String override = System.getProperty(HOME_PROPERTY);
        return override != null && !override.isBlank() ? Path.of(override)
                : Path.of(System.getProperty("user.home"), ".dogsbay", "agents");
    }

    /** The managed executable for {@code agentId} and the registry's relative {@code cmd}, or null. */
    public static Path installed(String agentId, String cmd) {
        if (agentId == null || cmd == null || cmd.isBlank()) {
            return null;
        }
        Path exe = home().resolve(agentId).resolve(cmd.replaceFirst("^\\./", "")).normalize();
        return Files.isRegularFile(exe) ? exe : null;
    }

    /**
     * Download, verify, extract and mark executable. Returns the executable.
     *
     * @param progress short status lines for the user
     */
    public Path install(AgentRegistryCatalog.Entry entry, Consumer<String> progress) throws IOException {
        if (!(entry.distribution() instanceof AgentRegistryCatalog.Distribution.Binary b) || b.archiveUrl() == null) {
            throw new IOException(entry.name() + " has no download for this platform (" 
                    + AgentRegistryCatalog.platformKey() + ")");
        }
        Path dir = home.resolve(entry.id());
        Path staging = home.resolve(entry.id() + ".installing");
        deleteTree(staging);
        Files.createDirectories(staging);
        try {
            Path exe = installInto(staging, b, entry, progress);
            deleteTree(dir);
            Files.move(staging, dir, StandardCopyOption.ATOMIC_MOVE);
            Path installed = dir.resolve(staging.relativize(exe));
            progress.accept("Installed " + entry.name() + " into " + dir);
            return installed;
        } catch (IOException | RuntimeException e) {
            deleteTree(staging);   // a half install must not look like an installed agent
            throw e;
        }
    }

    /** Download, verify and extract into {@code dir}; the executable inside it. */
    private Path installInto(Path dir, AgentRegistryCatalog.Distribution.Binary b, AgentRegistryCatalog.Entry entry,
            Consumer<String> progress) throws IOException {
        URI uri = URI.create(b.archiveUrl());
        String fileName = uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1);
        Path archive = dir.resolve(fileName.isEmpty() ? "download" : fileName);
        progress.accept("Downloading " + fileName + "…");
        MessageDigest sha = sha256();
        try (InputStream in = fetcher.open(uri); var out = Files.newOutputStream(archive)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                sha.update(buf, 0, n);
            }
        }
        if (b.sha256() != null && !b.sha256().isBlank()) {
            String got = HexFormat.of().formatHex(sha.digest());
            if (!got.equalsIgnoreCase(b.sha256().trim())) {
                Files.deleteIfExists(archive);
                throw new IOException("Checksum mismatch for " + fileName + ": the download is not what the registry lists");
            }
            progress.accept("Checksum verified.");
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        Path exe = dir.resolve(b.cmd().replaceFirst("^\\./", "")).normalize();
        if (!exe.startsWith(dir)) {
            throw new IOException("Registry command escapes the install folder: " + b.cmd());
        }
        if (lower.endsWith(".zip")) {
            progress.accept("Extracting…");
            unzip(archive, dir);
            Files.deleteIfExists(archive);
        } else if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz") || lower.endsWith(".tar.bz2")
                || lower.endsWith(".tar.xz") || lower.endsWith(".tar")) {
            progress.accept("Extracting…");
            untar(archive, dir);
            Files.deleteIfExists(archive);
        } else {
            // a bare executable: the registry names it as the command
            Files.createDirectories(exe.getParent());
            Files.move(archive, exe, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!Files.isRegularFile(exe)) {
            throw new IOException("The archive did not contain " + b.cmd());
        }
        markExecutable(exe);
        return exe;
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    /**
     * Extract with the archive's own permission bits: an agent zip carries a
     * launcher plus the helpers it execs, and {@code java.util.zip} would drop
     * every mode. Ant's reader keeps them.
     */
    static void unzip(Path archive, Path dir) throws IOException {
        Path root = dir.toAbsolutePath().normalize();
        boolean posix = java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        org.apache.tools.zip.ZipFile zip = new org.apache.tools.zip.ZipFile(archive.toFile());
        try {
            java.util.Enumeration<org.apache.tools.zip.ZipEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                org.apache.tools.zip.ZipEntry e = entries.nextElement();
                Path target = root.resolve(e.getName()).normalize();
                if (!target.startsWith(root)) {
                    throw new IOException("Archive entry escapes the install folder: " + e.getName());
                }
                if (e.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(e)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                int mode = e.getUnixMode();
                if (posix && mode != 0) {
                    Files.setPosixFilePermissions(target, posixPermissions(mode));
                }
            }
        } finally {
            org.apache.tools.zip.ZipFile.closeQuietly(zip);
        }
    }

    private static java.util.Set<java.nio.file.attribute.PosixFilePermission> posixPermissions(int mode) {
        java.nio.file.attribute.PosixFilePermission[] bits = {
            java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE,
            java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE,
            java.nio.file.attribute.PosixFilePermission.OTHERS_READ,
            java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE,
            java.nio.file.attribute.PosixFilePermission.GROUP_WRITE,
            java.nio.file.attribute.PosixFilePermission.GROUP_READ,
            java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE,
            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
            java.nio.file.attribute.PosixFilePermission.OWNER_READ };
        java.util.Set<java.nio.file.attribute.PosixFilePermission> out = java.util.EnumSet.noneOf(
                java.nio.file.attribute.PosixFilePermission.class);
        for (int i = 0; i < bits.length; i++) {
            if ((mode & (1 << i)) != 0) {
                out.add(bits[i]);
            }
        }
        out.add(java.nio.file.attribute.PosixFilePermission.OWNER_READ);
        out.add(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
        return out;
    }

    /** The system tar (GNU or BSD, both present on Linux, macOS and Windows 10+) does the formats Java lacks. */
    static void untar(Path archive, Path dir) throws IOException {
        Process p = new ProcessBuilder("tar", "-xf", archive.toAbsolutePath().toString())
                .directory(dir.toFile()).redirectErrorStream(true).start();
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        try {
            if (!p.waitFor(5, java.util.concurrent.TimeUnit.MINUTES)) {
                p.destroyForcibly();
                throw new IOException("tar did not finish");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while extracting");
        }
        if (p.exitValue() != 0) {
            throw new IOException("tar failed: " + out.strip());
        }
    }

    private static void markExecutable(Path exe) {
        try {
            var perms = new java.util.HashSet<>(Files.getPosixFilePermissions(exe));
            perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
            perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE);
            perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(exe, perms);
        } catch (UnsupportedOperationException | IOException e) {
            exe.toFile().setExecutable(true, false);   // Windows, or a file system without POSIX bits
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static InputStream openHttp(URI uri) throws IOException {
        if (!"https".equals(uri.getScheme())) {
            throw new IOException("Only https downloads are accepted: " + uri);
        }
        try {
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<InputStream> r = client.send(HttpRequest.newBuilder(uri).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (r.statusCode() / 100 != 2) {
                r.body().close();
                throw new IOException("HTTP " + r.statusCode() + " for " + uri);
            }
            return r.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while downloading");
        }
    }
}
