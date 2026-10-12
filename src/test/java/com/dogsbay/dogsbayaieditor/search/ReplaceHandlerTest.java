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

package com.dogsbay.dogsbayaieditor.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dogsbay.dogsbayaieditor.project.Match;

/**
 * Fidelity tests for replace-in-files: the rewrite must preserve the file's
 * encoding, line endings, and trailing-newline state on every line the replace
 * does not touch, and must write atomically (no temp litter).
 */
class ReplaceHandlerTest {

    @TempDir Path dir;

    private Path write(String name, String content, Charset charset) throws IOException {
        Path p = dir.resolve(name);
        Files.write(p, content.getBytes(charset));
        return p;
    }

    private long tmpFileCount() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".tmp")).count();
        }
    }

    @Test
    void preservesCrlfLineEndings() throws Exception {
        Path file = write("crlf.xml",
                "<root>\r\n  <item>foo</item>\r\n  <other/>\r\n</root>\r\n",
                StandardCharsets.UTF_8);
        URL url = file.toUri().toURL();

        // "foo" is on line 2, columns 8-11 of "  <item>foo</item>"
        Match match = new Match(url, 2, 8, 11, "  <item>foo</item>");
        boolean replaced = ReplaceHandler.replaceSingle(match, "bar", false);

        assertThat(replaced).isTrue();
        assertThat(Files.readString(file))
                .isEqualTo("<root>\r\n  <item>bar</item>\r\n  <other/>\r\n</root>\r\n");
    }

    @Test
    void preservesMissingTrailingNewline() throws Exception {
        Path file = write("notrail.xml", "<root>foo</root>", StandardCharsets.UTF_8);
        URL url = file.toUri().toURL();

        Match match = new Match(url, 1, 6, 9, "<root>foo</root>");
        boolean replaced = ReplaceHandler.replaceSingle(match, "bar", false);

        assertThat(replaced).isTrue();
        assertThat(Files.readString(file)).isEqualTo("<root>bar</root>");
    }

    @Test
    void preservesTrailingNewline() throws Exception {
        Path file = write("trail.xml", "<root>foo</root>\n", StandardCharsets.UTF_8);
        URL url = file.toUri().toURL();

        Match match = new Match(url, 1, 6, 9, "<root>foo</root>");
        boolean replaced = ReplaceHandler.replaceSingle(match, "bar", false);

        assertThat(replaced).isTrue();
        assertThat(Files.readString(file)).isEqualTo("<root>bar</root>\n");
    }

    @Test
    void preservesDeclaredNonUtf8Encoding() throws Exception {
        String content = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root>café foo</root>\n";
        Path file = write("latin1.xml", content, StandardCharsets.ISO_8859_1);
        URL url = file.toUri().toURL();

        // "foo" on line 2 of the latin-1 file: "<root>café foo</root>", cols 11-14
        Match match = new Match(url, 2, 11, 14, "<root>café foo</root>");
        boolean replaced = ReplaceHandler.replaceSingle(match, "bar", false);

        assertThat(replaced).isTrue();
        byte[] bytes = Files.readAllBytes(file);
        String roundTripped = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(roundTripped)
                .isEqualTo("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<root>café bar</root>\n");
        // The é must still be the single ISO-8859-1 byte 0xE9, not a UTF-8 pair or '?'
        assertThat(roundTripped).contains("café");
    }

    @Test
    void leavesNoTempLitter() throws Exception {
        Path file = write("clean.xml", "<root>foo</root>\n", StandardCharsets.UTF_8);
        URL url = file.toUri().toURL();

        Match match = new Match(url, 1, 6, 9, "<root>foo</root>");
        ReplaceHandler.replaceSingle(match, "bar", false);

        assertThat(tmpFileCount()).isZero();
    }

    @Test
    void readFileDetectsEncodingAndPreservesContent() throws Exception {
        String content = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\r\n<root>naïve</root>";
        Path file = write("detect.xml", content, StandardCharsets.ISO_8859_1);

        ReplaceHandler.FileText text = ReplaceHandler.readFile(file.toFile());

        assertThat(text).isNotNull();
        assertThat(text.encoding).isEqualToIgnoringCase("ISO-8859-1");
        assertThat(text.content).isEqualTo(content);
    }
}
