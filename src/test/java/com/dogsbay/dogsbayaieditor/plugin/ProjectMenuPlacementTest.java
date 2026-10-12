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

import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.junit.jupiter.api.Test;

/**
 * Where a plugin's project commands land.
 *
 * <p>The plugin cannot build the Project menu; it inserts into one the core built, anchored on the text of an item.
 * That is fragile in a way nothing else catches: renaming or moving an anchor makes every insertion land somewhere
 * else, and {@code insertBefore} appends rather than failing, so the menu is merely wrong. The anchor used to be
 * "Format Project", which has since moved into a submenu.
 *
 * <p>This replays the core's construction and the plugin's insertions over plain Swing menus — no editor, no
 * display — and asserts the order a reader ends up with.
 */
class ProjectMenuPlacementTest {

    private static final String VALIDATE = "Validate Files";
    private static final String MANAGE_PROJECTS = "Manage Projects...";
    private static final String PROJECT_TOOLS = "Project Tools";

    /** What the core builds before any plugin is active. */
    private static JMenu coreProjectMenu() {
        JMenu menu = new JMenu("Project");
        JMenu validate = new JMenu(VALIDATE);
        validate.add(new JMenuItem("With DTD..."));
        validate.add(new JMenuItem("With Schematron"));
        menu.add(validate);
        menu.addSeparator();
        menu.add(new JMenuItem(MANAGE_PROJECTS));
        JMenu tools = new JMenu(PROJECT_TOOLS);
        tools.add(new JMenuItem("Save Project Settings..."));
        tools.add(new JMenuItem("Format Project"));
        menu.add(tools);
        return menu;
    }

    /** The DITA plugin's contributions, in the order it makes them. */
    private static void contributeAsDitaPluginDoes(JMenu menu) {
        MenuPlacement.insertBefore(menu, VALIDATE, new JMenuItem("Check Project..."));
        MenuPlacement.insertSeparatorBefore(menu, VALIDATE);

        MenuPlacement.insertBefore(menu, MANAGE_PROJECTS, new JMenuItem("Build Deliverables..."));
        MenuPlacement.insertBefore(menu, MANAGE_PROJECTS, new JMenuItem("Live Preview..."));
        MenuPlacement.insertSeparatorBefore(menu, MANAGE_PROJECTS);

        MenuPlacement.insertBefore(menu, MANAGE_PROJECTS, new JMenu("Metadata"));
        MenuPlacement.insertBefore(menu, MANAGE_PROJECTS, new JMenu("Map"));
        MenuPlacement.insertSeparatorBefore(menu, MANAGE_PROJECTS);
    }

    /** The menu as a reader sees it, with "-" for each separator. */
    private static List<String> asRead(JMenu menu) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            lines.add(item == null ? "-" : item.getText());
        }
        return lines;
    }

    @Test
    void theGateIsFirstAndTheOccasionalCommandsAreLast() {
        JMenu menu = coreProjectMenu();

        contributeAsDitaPluginDoes(menu);

        assertThat(asRead(menu)).containsExactly(
                "Check Project...",
                "-",
                "Validate Files",
                "-",
                "Build Deliverables...",
                "Live Preview...",
                "-",
                "Metadata",
                "Map",
                "-",
                "Manage Projects...",
                "Project Tools");
    }

    @Test
    void withNoPluginTheCoreMenuStandsOnItsOwn() {
        assertThat(asRead(coreProjectMenu())).containsExactly(
                "Validate Files", "-", "Manage Projects...", "Project Tools");
    }

    @Test
    void anAnchorThatMovedIsReportedRatherThanGuessedAt() {
        JMenu menu = coreProjectMenu();

        // insertBefore appends and says so; a separator is not placed at all, because one in the wrong place is
        // worse than none. This is what a renamed anchor looks like — the failure the old "Format Project" anchor
        // would have produced silently.
        assertThat(MenuPlacement.insertBefore(menu, "Gone", new JMenuItem("Orphan"))).isFalse();
        assertThat(MenuPlacement.insertSeparatorBefore(menu, "Gone")).isFalse();

        assertThat(asRead(menu)).endsWith("Orphan");
    }

    @Test
    void deactivatingAPluginTakesItsSeparatorsWithIt() {
        JMenu menu = coreProjectMenu();
        contributeAsDitaPluginDoes(menu);

        // What DefaultUIService.removeMenuItem does on deactivation: remove each contributed item, then tidy.
        for (String text : List.of("Check Project...", "Build Deliverables...", "Live Preview...",
                "Metadata", "Map")) {
            for (int i = 0; i < menu.getItemCount(); i++) {
                JMenuItem item = menu.getItem(i);
                if (item != null && text.equals(item.getText())) {
                    menu.remove(i);
                    break;
                }
            }
        }
        MenuPlacement.tidySeparators(menu);

        // Back to the menu the core built: a plugin that leaves rules behind draws groups around nothing.
        assertThat(asRead(menu)).containsExactly(
                "Validate Files", "-", "Manage Projects...", "Project Tools");
    }

    @Test
    void aSeparatorGoesAboveTheItemItIsAskedFor() {
        JMenu menu = coreProjectMenu();

        assertThat(MenuPlacement.insertSeparatorBefore(menu, MANAGE_PROJECTS)).isTrue();

        assertThat(asRead(menu)).containsExactly(
                "Validate Files", "-", "-", "Manage Projects...", "Project Tools");
    }
}
