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

package com.dogsbay.xml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Stops a fixed bug from being written again.
 *
 * <p>{@code String.getBytes(charset)} answers any character the charset cannot represent with
 * {@code '?'}, silently and unrecoverably. Encoding a document's text to bytes only to decode it
 * straight back for a parser therefore destroys content before anything can object, and eighteen
 * places in this codebase did it — the formatter, the editor's open and edit path, reflow, validation,
 * both transform paths, search-and-replace, metadata writes and the browser preview. They were found
 * and fixed one subsystem at a time; documentation did not prevent the next one, so this does.
 *
 * <p>The rule: <b>hand a parser characters, not bytes.</b> Where bytes genuinely have to be written,
 * escape first through {@link XMLUtilities#escapeForEncoding}, which is markup-aware.
 */
class TextToBytesGuardTest {

    /** Writing a document's text to bytes, which loses whatever the charset cannot hold. */
    private static final String FORBIDDEN = "getText().getBytes(";

    /** Lines that write bytes on purpose, having escaped them first. */
    private static final String SANCTIONED = "escapeForEncoding";

    @Test
    @DisplayName("no new place encodes a document's text to bytes unescaped")
    void noUnescapedTextToBytes() throws IOException {
        Path main = Paths.get("src/main/java");
        assumeTrue(Files.isDirectory(main), "sources not available");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(main)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(f, StandardCharsets.UTF_8);
                int at = -1;
                while ((at = source.indexOf(FORBIDDEN, at + 1)) >= 0) {
                    int lineStart = source.lastIndexOf('\n', at) + 1;
                    int lineEnd = source.indexOf('\n', at);
                    String line = source.substring(lineStart, lineEnd < 0 ? source.length() : lineEnd);
                    if (line.contains(SANCTIONED) || line.stripLeading().startsWith("//")
                            || line.stripLeading().startsWith("*")) {
                        continue;
                    }
                    int lineNumber = (int) source.substring(0, at).chars().filter(c -> c == '\n').count() + 1;
                    offenders.add(f + ":" + lineNumber + "  " + line.strip());
                }
            }
        }

        assertThat(offenders)
                .as("Hand a parser characters, not bytes: a StringReader over the text. Where bytes "
                        + "must be written, escape first with XMLUtilities.escapeForEncoding. "
                        + "Found:\n" + String.join("\n", offenders))
                .isEmpty();
    }
}
