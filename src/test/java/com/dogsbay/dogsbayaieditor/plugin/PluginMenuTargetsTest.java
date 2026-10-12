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

package com.dogsbay.dogsbayaieditor.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every menu a plugin contributes into must be a menu that exists.
 *
 * <p>
 * {@code DefaultUIService.addMenuItem} walks the menu bar, and when no menu
 * carries the name it was given it logs one line and returns. So a plugin that
 * names a menu the editor does not have is not a crash and not a compile error:
 * it is a command that is simply never installed, and the only trace is a
 * warning in a log nobody reads. The Document Inspector shipped asking for a
 * <strong>Tools</strong> menu, which this editor does not have, and its command
 * was missing for as long as that went unnoticed.
 *
 * <p>
 * This is {@code CatalogueReachabilityTest}'s fault in another organ, and it is
 * checked the same way — by reading the source, because the menu bar is filled
 * at runtime from menus that need a window.
 */
class PluginMenuTargetsTest {

    private static final Path MAIN = Path.of("src/main/java");

    private static final Path MENU_BUILDER = MAIN.resolve(
            "com/dogsbay/dogsbayaieditor/services/MenuBuilder.java");

    /** A menu name as it was written: a literal, or a {@code MenuBuilder} constant. */
    private record Target(String file, String written, String name) {
    }

    /** {@code MenuBuilder}'s string constants, by name. */
    private static Map<String, String> menuBuilderConstants() throws IOException {
        Map<String, String> constants = new LinkedHashMap<>();
        Matcher matcher = Pattern
                .compile("static final String ([A-Z0-9_]+) = \"([^\"]*)\"")
                .matcher(Files.readString(MENU_BUILDER));
        while (matcher.find()) {
            constants.put(matcher.group(1), matcher.group(2));
        }
        return constants;
    }

    /**
     * The menus {@code MenuBuilder} creates, whether named by a literal or by one of its own
     * constants. Submenus are included: a plugin addresses one by name too.
     */
    private static Set<String> menusThatExist() throws IOException {
        Map<String, String> constants = menuBuilderConstants();
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("new JMenu\\(\\s*(\"([^\"]*)\"|([A-Z0-9_]+))")
                .matcher(Files.readString(MENU_BUILDER));
        while (matcher.find()) {
            if (matcher.group(2) != null) {
                names.add(matcher.group(2));
            } else if (constants.containsKey(matcher.group(3))) {
                names.add(constants.get(matcher.group(3)));
            }
        }
        return names;
    }

    /**
     * Every menu name a plugin passes to the menu-contributing methods.
     *
     * <p>
     * The first argument is always a top-level menu. The second is a menu in only one of the
     * four signatures — {@code addMenuItem(menu, submenu, item)}. In
     * {@code addMenuItemBefore(menu, beforeItemText, item)} and
     * {@code addMenuSeparatorBefore(menu, beforeItemText)} it is the item to insert above, which
     * is not a menu and must not be checked as one: {@code MANAGE_PROJECTS_ITEM} is an item, and
     * an earlier draft of this test reported it as a missing menu.
     *
     * <p>
     * A submenu is told from an item by the method's name, not by the argument's shape, because
     * the two look identical. A name that is neither a literal nor a resolvable
     * {@code MenuBuilder} constant is reported rather than skipped: it cannot be checked, which
     * is itself worth knowing.
     */
    private static List<Target> pluginTargets() throws IOException {
        Map<String, String> constants = menuBuilderConstants();
        List<Target> targets = new ArrayList<>();
        String argument = "(\"[^\"]*\"|(?:MenuBuilder\\.)?[A-Za-z0-9_.]+)";
        Pattern call = Pattern.compile("add(MenuItem|MenuItemBefore|MenuSeparatorBefore)\\(\\s*"
                + argument + "\\s*(?:,\\s*" + argument + "\\s*)?(?:,\\s*" + argument + "\\s*)?\\)");
        try (Stream<Path> files = Files.walk(MAIN.resolve("com/dogsbay/dogsbayaieditor/plugin"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                // The service that does the adding, and the interface that declares it.
                if (name.equals("DefaultUIService.java") || name.equals("UIService.java")) {
                    continue;
                }
                Matcher matcher = call.matcher(Files.readString(file));
                while (matcher.find()) {
                    targets.add(target(name, matcher.group(2), constants));
                    // Only the three-argument addMenuItem names a submenu second.
                    boolean threeArguments = matcher.group(4) != null;
                    if (matcher.group(1).equals("MenuItem") && threeArguments) {
                        targets.add(target(name, matcher.group(3), constants));
                    }
                }
            }
        }
        return targets;
    }

    /** One menu name as a plugin wrote it, resolved if it can be. */
    private static Target target(String file, String written, Map<String, String> constants) {
        return new Target(file, written, resolve(written, constants));
    }

    /**
     * The name an argument stands for, or null when it cannot be resolved.
     *
     * <p>
     * A constant may be written bare, through {@code MenuBuilder.}, or fully qualified — the
     * Agent plugin writes out the whole package — so only the last segment is looked up.
     */
    private static String resolve(String written, Map<String, String> constants) {
        if (written.startsWith("\"")) {
            return written.substring(1, written.length() - 1);
        }
        String constant = written.substring(written.lastIndexOf('.') + 1);
        return constants.get(constant);
    }

    @Test
    @DisplayName("no plugin contributes into a menu the editor does not have")
    void everyPluginMenuTargetExists() throws Exception {
        List<Target> targets = pluginTargets();
        assertThat(targets).as("the plugins were read").isNotEmpty();
        Set<String> existing = menusThatExist();
        assertThat(existing).as("MenuBuilder's menus were read").contains("View", "Project");

        List<String> missing = targets.stream()
                .filter(t -> t.name() == null || !existing.contains(t.name()))
                .map(t -> t.file() + " asks for " + t.written()
                        + (t.name() == null ? " (which this test cannot resolve)" : ""))
                .distinct()
                .toList();

        assertThat(missing)
                .as("plugin menu targets that MenuBuilder never creates; it creates " + existing)
                .isEmpty();
    }

}
