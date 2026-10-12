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

package com.dogsbay.dogsbayaieditor.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every command the Bindings page offers must have something behind it.
 *
 * <p>
 * A binding reaches a command through {@code MenuBuilder}, which looks the id
 * up in its menu-item map or its mode-action map and returns quietly when it
 * finds neither. So a catalogue entry for an id nothing implements is worse
 * than no entry at all: the page lists a key, pressing it does nothing, and the
 * clash check still reserves it against every command that does work. Four
 * Agent commands shipped in exactly that state.
 *
 * <p>
 * Checked by reading the source, because both maps are filled at runtime from
 * menus that need a window.
 */
class CatalogueReachabilityTest {

    private static final Path MAIN = Path.of("src/main/java");

    /** The constant names the catalogue passes to {@code binding(...)}. */
    private static List<String> catalogueConstants() throws IOException {
        String source = Files.readString(MAIN.resolve(
                "com/dogsbay/dogsbayaieditor/properties/KeyBindingCatalogue.java"));
        List<String> names = new ArrayList<>();
        var matcher = java.util.regex.Pattern
                .compile("binding\\(KeyPreferences\\.([A-Z0-9_]+),").matcher(source);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /** Every .java file outside the catalogue and the id constants themselves. */
    private static String everythingElse() throws IOException {
        StringBuilder all = new StringBuilder();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                if (name.equals("KeyBindingCatalogue.java") || name.equals("KeyPreferences.java")) {
                    continue;
                }
                all.append(Files.readString(file));
            }
        }
        return all.toString();
    }

    @Test
    @DisplayName("no command is listed that nothing in the product answers to")
    void everyCommandHasSomethingBehindIt() throws Exception {
        List<String> constants = catalogueConstants();
        assertThat(constants).as("the catalogue was read").isNotEmpty();
        String product = everythingElse();

        List<String> unreachable = constants.stream()
                .filter(name -> !product.contains("KeyPreferences." + name))
                .toList();

        assertThat(unreachable)
                .as("catalogue commands with no menu item and no action anywhere in src/main")
                .isEmpty();
    }

    @Test
    @DisplayName("no two commands claim the same default key")
    void noTwoCommandsWantTheSameKey() {
        List<String> taken = new ArrayList<>();
        List<String> clashes = new ArrayList<>();
        for (KeyBinding binding : KeyBindingCatalogue.all()) {
            if (binding.defaultStroke() == null) {
                continue;
            }
            String stroke = binding.defaultStroke().toString();
            if (taken.contains(stroke)) {
                clashes.add(stroke + " (" + binding.label() + ")");
            }
            taken.add(stroke);
        }

        assertThat(clashes).isEmpty();
    }
}
