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

package com.dogsbay.dogsbayaieditor.ditaproject;

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
 * Locks the GUI-free boundary of the prospective <strong>dita-core</strong>
 * library (see {@code plans/project-batch-operations.md} — "Should this be its
 * own library?"). The batch layer — {@code links}, {@code ditaproject},
 * {@code validate} — is already free of Swing/AWT and of the editor application,
 * so keeping it that way makes a future extraction a lift-and-shift rather than
 * an untangle. Mirrors {@code AuthorPackageDependencyTest} /
 * {@code AgentPackageDependencyTest}.
 *
 * <p>This is a direct-import source scan, not transitive: the known remaining
 * coupling is that these packages use {@code com.dogsbay.xml} (DogsBayDocument /
 * XElement), whose base classes still drag in Swing. Decoupling a GUI-free XML
 * core out of {@code com.dogsbay.xml} is the documented prerequisite for the
 * physical extraction; until then we tolerate the {@code com.dogsbay.xml} base
 * (same as the agent rule) and only forbid the GUI XML <em>views</em>.
 */
class DitaCorePackageDependencyTest {

    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+)\\s*;");

    /** The packages that make up the GUI-free dita-core today. */
    private static final List<String> CORE_PACKAGES = List.of(
            "src/main/java/com/dogsbay/dogsbayaieditor/links",
            "src/main/java/com/dogsbay/dogsbayaieditor/ditaproject",
            "src/main/java/com/dogsbay/dogsbayaieditor/validate");

    /** Editor-app packages dita-core may import (the headless command contracts). */
    private static final List<String> ALLOWED_EDITOR_PREFIXES = List.of(
            "com.dogsbay.dogsbayaieditor.links",
            "com.dogsbay.dogsbayaieditor.ditaproject",
            "com.dogsbay.dogsbayaieditor.validate",
            "com.dogsbay.dogsbayaieditor.commands"); // Command, results, HeadlessExecutor

    /** GUI / document-view packages dita-core must never touch. */
    private static final List<String> FORBIDDEN_PREFIXES = List.of(
            "javax.swing",
            "java.awt",
            "com.dogsbay.xml.editor",
            "com.dogsbay.xml.viewer",
            "com.dogsbay.xml.designer",
            "com.dogsbay.xml.browser");

    @Test
    void ditaCorePackagesStayGuiFreeAndAppFree() throws IOException {
        List<String> violations = new ArrayList<>();

        for (String pkg : CORE_PACKAGES) {
            Path dir = Path.of(pkg);
            assertThat(dir).as(pkg + " should exist").isDirectory();
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    for (String line : Files.readAllLines(file)) {
                        Matcher m = IMPORT.matcher(line.strip());
                        if (!m.matches()) {
                            continue;
                        }
                        String imported = m.group(1);
                        if (isForbidden(imported)) {
                            violations.add(file.getFileName() + " imports " + imported);
                        }
                    }
                }
            }
        }

        assertThat(violations)
                .as("dita-core (links/ditaproject/validate) must stay free of Swing/AWT, "
                        + "document-view packages, and editor-app packages other than the "
                        + "headless command contracts")
                .isEmpty();
    }

    private static boolean isForbidden(String imported) {
        for (String forbidden : FORBIDDEN_PREFIXES) {
            if (imported.startsWith(forbidden)) {
                return true;
            }
        }
        if (imported.startsWith("com.dogsbay.dogsbayaieditor.")) {
            return ALLOWED_EDITOR_PREFIXES.stream().noneMatch(imported::startsWith);
        }
        return false;
    }
}
