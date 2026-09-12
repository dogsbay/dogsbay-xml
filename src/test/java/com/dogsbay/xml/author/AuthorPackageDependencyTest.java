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

package com.dogsbay.xml.author;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Enforces the standalone rule from plans/wysiwyg-author-view.md: nothing under
 * {@code com.dogsbay.xml.author} may depend on the application
 * ({@code com.dogsbay.dogsbayaieditor}) or on the other document views, so the
 * Author engine + component stay packageable as a standalone offering.
 */
class AuthorPackageDependencyTest {

    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+)\\s*;");

    private static final List<String> FORBIDDEN_PREFIXES = List.of(
            "com.dogsbay.dogsbayaieditor",
            "com.dogsbay.xml.editor",
            "com.dogsbay.xml.viewer",
            "com.dogsbay.xml.designer",
            "com.dogsbay.xml.browser");

    @Test
    void authorPackagesDoNotDependOnTheApplication() throws IOException {
        Path authorSrc = Path.of("src/main/java/com/dogsbay/xml/author");
        assertThat(authorSrc).isDirectory();

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(authorSrc)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    Matcher m = IMPORT.matcher(line.strip());
                    if (!m.matches()) {
                        continue;
                    }
                    String imported = m.group(1);
                    for (String forbidden : FORBIDDEN_PREFIXES) {
                        if (imported.startsWith(forbidden)) {
                            violations.add(file + " imports " + imported);
                        }
                    }
                }
            }
        }

        assertThat(violations)
                .as("xml/author must stay free of application dependencies (standalone rule)")
                .isEmpty();
    }
}
