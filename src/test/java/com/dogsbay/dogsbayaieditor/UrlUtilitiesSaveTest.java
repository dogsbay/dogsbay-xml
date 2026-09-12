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

package com.dogsbay.dogsbayaieditor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression tests for the atomic local-file save path in
 * {@link URLUtilities#save(URL, InputStream, String)}: a save must write the
 * exact bytes, leave no temp litter behind, and a failed write must never
 * truncate the previously-good file.
 */
class UrlUtilitiesSaveTest {

    @TempDir Path dir;

    private static InputStream input(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private long tmpFileCount() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".tmp")).count();
        }
    }

    @Test
    void savesContentToNewFile() throws Exception {
        Path target = dir.resolve("new.xml");

        URLUtilities.save(target.toUri().toURL(), input("<root>hello</root>"), "UTF-8");

        assertThat(target).exists();
        assertThat(Files.readString(target)).isEqualTo("<root>hello</root>");
        assertThat(tmpFileCount()).isZero();
    }

    @Test
    void overwritesExistingFileAndLeavesNoTempLitter() throws Exception {
        Path target = dir.resolve("existing.xml");
        Files.writeString(target, "<root>old</root>");

        URLUtilities.save(target.toUri().toURL(), input("<root>new</root>"), "UTF-8");

        assertThat(Files.readString(target)).isEqualTo("<root>new</root>");
        assertThat(tmpFileCount()).isZero();
    }

    @Test
    void failedWritePreservesOriginalContent() throws Exception {
        Path target = dir.resolve("precious.xml");
        Files.writeString(target, "<root>precious</root>");

        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("simulated read failure");
            }
        };

        assertThatThrownBy(() -> URLUtilities.save(target.toUri().toURL(), failing, "UTF-8"))
                .isInstanceOf(IOException.class);

        assertThat(Files.readString(target)).isEqualTo("<root>precious</root>");
        assertThat(tmpFileCount()).isZero();
    }

    @Test
    void preservesNonAsciiContent() throws Exception {
        Path target = dir.resolve("unicode.xml");
        String content = "<root>café — naïve 中文</root>";

        URLUtilities.save(target.toUri().toURL(), input(content), "UTF-8");

        assertThat(Files.readString(target)).isEqualTo(content);
    }

    @Test
    void preservesExistingFilePermissions() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "POSIX permissions not supported");

        Path target = dir.resolve("shared.xml");
        Files.writeString(target, "<root>old</root>");
        // Group- and world-readable (a common web-served / shared document).
        var perms = java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--");
        Files.setPosixFilePermissions(target, perms);

        URLUtilities.save(target.toUri().toURL(), input("<root>new</root>"), "UTF-8");

        assertThat(Files.readString(target)).isEqualTo("<root>new</root>");
        assertThat(Files.getPosixFilePermissions(target))
                .as("save must not strip the file's group/other read access")
                .isEqualTo(perms);
    }

    @Test
    void writesThroughSymlinkInsteadOfSeveringIt() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "symlink test requires POSIX");

        Path realFile = dir.resolve("real.xml");
        Files.writeString(realFile, "<root>old</root>");
        Path link = dir.resolve("link.xml");
        Files.createSymbolicLink(link, realFile);

        URLUtilities.save(link.toUri().toURL(), input("<root>new</root>"), "UTF-8");

        assertThat(Files.isSymbolicLink(link)).as("the symlink must be preserved").isTrue();
        assertThat(Files.readString(realFile))
                .as("the write must reach the real target through the link")
                .isEqualTo("<root>new</root>");
    }
}
