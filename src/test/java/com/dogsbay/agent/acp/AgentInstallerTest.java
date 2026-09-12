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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.agent.acp.AgentRegistryCatalog.Distribution;
import com.dogsbay.agent.acp.AgentRegistryCatalog.Entry;

class AgentInstallerTest {

    @TempDir Path tmp;

    @AfterEach
    void clearHome() {
        System.clearProperty(AgentInstaller.HOME_PROPERTY);
    }

    private static byte[] zipWith(String name, String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static Entry entry(String id, String archive, String cmd, String sha) {
        return new Entry(id, id, "1.0", null, null, List.of(),
                new Distribution.Binary(id, "linux-x86_64", archive, cmd, cmd.substring(cmd.lastIndexOf('/') + 1),
                        List.of("acp"), sha));
    }

    @Test
    void installsAZipVerifiesTheChecksumAndTheCatalogThenRunsTheManagedTool() throws Exception {
        Path home = tmp.resolve("agents");
        System.setProperty(AgentInstaller.HOME_PROPERTY, home.toString());
        byte[] zip = zipWith("dist/tool", "#!/bin/sh\necho acp\n");
        Entry e = entry("tool-acp", "https://example.test/tool-1.0.zip", "./dist/tool", sha256(zip));
        assertThat(((Distribution.Binary) e.distribution()).canInstall()).isTrue();
        assertThat(e.readiness()).isEqualTo(AgentRegistryCatalog.Readiness.UNAVAILABLE);
        List<String> progress = new ArrayList<>();

        Path exe = new AgentInstaller(home, uri -> new java.io.ByteArrayInputStream(zip)).install(e, progress::add);

        assertThat(exe).isEqualTo(home.resolve("tool-acp/dist/tool")).isRegularFile();
        assertThat(Files.isExecutable(exe)).isTrue();
        assertThat(progress).anyMatch(s -> s.startsWith("Downloading")).anyMatch(s -> s.contains("Checksum verified"));
        assertThat(Files.list(home.resolve("tool-acp"))).noneMatch(p -> p.toString().endsWith(".zip"));
        assertThat(e.readiness()).isEqualTo(AgentRegistryCatalog.Readiness.READY);
        assertThat(e.launchCommand()).containsExactly(exe.toString(), "acp");
        assertThat(((Distribution.Binary) e.distribution()).canInstall()).isFalse();
    }

    @Test
    void zipPermissionBitsSurviveSoHelpersTheLauncherExecsRun() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (org.apache.tools.zip.ZipOutputStream zip = new org.apache.tools.zip.ZipOutputStream(bytes)) {
            org.apache.tools.zip.ZipEntry launcher = new org.apache.tools.zip.ZipEntry("app/bin/launch");
            launcher.setUnixMode(0755);
            zip.putNextEntry(launcher);
            zip.write("#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            org.apache.tools.zip.ZipEntry helper = new org.apache.tools.zip.ZipEntry("app/jbr/bin/java");
            helper.setUnixMode(0755);
            zip.putNextEntry(helper);
            zip.write("#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            org.apache.tools.zip.ZipEntry data = new org.apache.tools.zip.ZipEntry("app/lib/data.txt");
            data.setUnixMode(0644);
            zip.putNextEntry(data);
            zip.write("x".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        byte[] data = bytes.toByteArray();
        Path home = tmp.resolve("agents");
        Entry e = entry("junie-like", "https://example.test/j.zip", "./app/bin/launch", null);
        new AgentInstaller(home, uri -> new java.io.ByteArrayInputStream(data)).install(e, s -> { });
        assertThat(Files.isExecutable(home.resolve("junie-like/app/jbr/bin/java"))).isTrue();
        assertThat(Files.isExecutable(home.resolve("junie-like/app/lib/data.txt"))).isFalse();
    }

    @Test
    void aFailedInstallLeavesNoFolderSoTheAgentIsNotReportedReady() throws Exception {
        Path home = tmp.resolve("agents");
        System.setProperty(AgentInstaller.HOME_PROPERTY, home.toString());
        byte[] zip = zipWith("other", "x");   // does not contain the command
        Entry e = entry("half", "https://example.test/half.zip", "./tool", null);
        assertThatThrownBy(() -> new AgentInstaller(home, uri -> new java.io.ByteArrayInputStream(zip)).install(e, s -> { }))
                .isInstanceOf(IOException.class).hasMessageContaining("did not contain");
        assertThat(Files.exists(home.resolve("half"))).isFalse();
        assertThat(Files.exists(home.resolve("half.installing"))).isFalse();
        assertThat(e.readiness()).isEqualTo(AgentRegistryCatalog.Readiness.UNAVAILABLE);
        assertThat(((Distribution.Binary) e.distribution()).canInstall()).isTrue();
    }

    @Test
    void aChecksumMismatchRefusesAndLeavesNothingBehind() throws Exception {
        Path home = tmp.resolve("agents");
        byte[] zip = zipWith("tool", "x");
        Entry e = entry("bad", "https://example.test/bad.zip", "./tool", "00".repeat(32));
        assertThatThrownBy(() -> new AgentInstaller(home, uri -> new java.io.ByteArrayInputStream(zip)).install(e, s -> { }))
                .isInstanceOf(IOException.class).hasMessageContaining("Checksum mismatch");
        assertThat(Files.exists(home.resolve("bad/tool"))).isFalse();
        assertThat(Files.exists(home.resolve("bad/bad.zip"))).isFalse();
    }

    @Test
    void anArchiveEntryThatEscapesTheFolderIsRefused() throws Exception {
        Path home = tmp.resolve("agents");
        byte[] zip = zipWith("../../escape", "x");
        Entry e = entry("slip", "https://example.test/slip.zip", "./escape", null);
        assertThatThrownBy(() -> new AgentInstaller(home, uri -> new java.io.ByteArrayInputStream(zip)).install(e, s -> { }))
                .isInstanceOf(IOException.class).hasMessageContaining("escapes");
        assertThat(Files.exists(tmp.resolve("escape"))).isFalse();
    }

    @Test
    void aTarballGoesThroughTheSystemTar() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(AgentRegistryCatalog.onPath("tar"));
        Path src = Files.createDirectories(tmp.resolve("src/pkg"));
        Files.writeString(src.resolve("agent"), "#!/bin/sh\n");
        Path tgz = tmp.resolve("agent.tar.gz");
        Process p = new ProcessBuilder("tar", "-czf", tgz.toString(), "-C", tmp.resolve("src").toString(), "pkg")
                .redirectErrorStream(true).start();
        assertThat(p.waitFor()).isZero();
        byte[] data = Files.readAllBytes(tgz);
        Path home = tmp.resolve("agents");
        Entry e = entry("tarred", "https://example.test/agent.tar.gz", "./pkg/agent", null);

        Path exe = new AgentInstaller(home, uri -> new java.io.ByteArrayInputStream(data)).install(e, s -> { });

        assertThat(exe).isEqualTo(home.resolve("tarred/pkg/agent")).isRegularFile();
        assertThat(Files.isExecutable(exe)).isTrue();
    }

    @Test
    void aBareBinaryIsPlacedAsTheCommand() throws Exception {
        Path home = tmp.resolve("agents");
        byte[] bin = "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8);
        Entry e = entry("bare", "https://example.test/sigit-linux-amd64", "./sigit-linux-amd64", null);
        Path exe = new AgentInstaller(home, uri -> new java.io.ByteArrayInputStream(bin)).install(e, s -> { });
        assertThat(exe).isEqualTo(home.resolve("bare/sigit-linux-amd64")).isRegularFile();
        assertThat(Files.isExecutable(exe)).isTrue();
    }
}
