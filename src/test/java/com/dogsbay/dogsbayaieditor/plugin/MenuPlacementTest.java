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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how plugin contributions are located and positioned in the menu bar.
 *
 * <p>The fixture mirrors the real Project menu that {@code MenuBuilder} builds, so these
 * also pin down the structure the DITA plugin contributes into.
 */
public class MenuPlacementTest {

    private JMenuBar menuBar;
    private JMenu projectMenu;

    @BeforeEach
    void setUp() {
        menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        fileMenu.add(new JMenuItem("Open..."));
        menuBar.add(fileMenu);

        projectMenu = new JMenu("Project");
        projectMenu.add(new JMenuItem("Manage Projects..."));
        projectMenu.add(new JMenuItem("Save Project Settings..."));
        projectMenu.addSeparator();

        JMenu validate = new JMenu("Validate");
        validate.add(new JMenuItem("Project..."));
        validate.add(new JMenuItem("Project with Schematron..."));
        projectMenu.add(validate);

        projectMenu.addSeparator();
        projectMenu.add(new JMenuItem("Format Project"));
        projectMenu.add(new JMenuItem("Reflow Project"));
        menuBar.add(projectMenu);
    }

    private String[] itemTexts(JMenu menu) {
        return java.util.stream.IntStream.range(0, menu.getItemCount())
                .mapToObj(menu::getItem)
                .map(i -> i == null ? "—" : i.getText())
                .toArray(String[]::new);
    }

    @Test
    void findsATopLevelMenuByText() {
        assertSame(projectMenu, MenuPlacement.findMenu(menuBar, "Project"));
        assertNull(MenuPlacement.findMenu(menuBar, "Nonexistent"));
    }

    @Test
    void tolerantOfAMenuBarThatIsNotBuiltYet() {
        assertNull(MenuPlacement.findMenu(null, "Project"));
        assertNull(MenuPlacement.findSubmenu(null, "Project", "Validate"));
    }

    @Test
    void findsASubmenuWithinAMenu() {
        JMenu validate = MenuPlacement.findSubmenu(menuBar, "Project", "Validate");

        assertNotNull(validate);
        assertEquals("Validate", validate.getText());
        assertEquals(2, validate.getItemCount());
    }

    @Test
    void aPlainItemIsNotMistakenForASubmenu() {
        assertNull(MenuPlacement.findSubmenu(menuBar, "Project", "Format Project"),
                "Format Project is a JMenuItem, not a submenu");
    }

    @Test
    void missingSubmenuReportsNullRatherThanThrowing() {
        assertNull(MenuPlacement.findSubmenu(menuBar, "Project", "Metadata"));
        assertNull(MenuPlacement.findSubmenu(menuBar, "Nonexistent", "Validate"));
    }

    /** The DITA plugin adds its validators into the core's Validate submenu. */
    @Test
    void contributionsIntoASubmenuLandThere() {
        JMenu validate = MenuPlacement.findSubmenu(menuBar, "Project", "Validate");
        validate.add(new JMenuItem("With DITA-OT — Current Map..."));

        assertArrayEquals(
                new String[] {"Project...", "Project with Schematron...",
                        "With DITA-OT — Current Map..."},
                itemTexts(validate));
    }

    @Test
    void insertBeforePositionsTheItemAndReportsSuccess() {
        boolean anchored = MenuPlacement.insertBefore(
                projectMenu, "Format Project", new JMenuItem("Build Deliverables..."));

        assertTrue(anchored);
        assertArrayEquals(
                new String[] {"Manage Projects...", "Save Project Settings...", "—",
                        "Validate", "—", "Build Deliverables...",
                        "Format Project", "Reflow Project"},
                itemTexts(projectMenu));
    }

    @Test
    void successiveInsertsKeepTheirOrder() {
        MenuPlacement.insertBefore(projectMenu, "Format Project", new JMenu("Metadata"));
        MenuPlacement.insertBefore(projectMenu, "Format Project", new JMenu("Map"));
        MenuPlacement.insertBefore(projectMenu, "Format Project",
                new JMenuItem("Build Deliverables..."));
        MenuPlacement.insertBefore(projectMenu, "Format Project",
                new JMenuItem("Manage Deliverables..."));

        assertArrayEquals(
                new String[] {"Manage Projects...", "Save Project Settings...", "—",
                        "Validate", "—", "Metadata", "Map",
                        "Build Deliverables...", "Manage Deliverables...",
                        "Format Project", "Reflow Project"},
                itemTexts(projectMenu),
                "each insert goes immediately before the anchor, preserving call order");
    }

    /**
     * If the anchor is renamed, the contributed command must stay reachable rather than
     * disappear — appending is the deliberate fallback.
     */
    @Test
    void aMissingAnchorAppendsInsteadOfDroppingTheItem() {
        boolean anchored = MenuPlacement.insertBefore(
                projectMenu, "Renamed Away", new JMenuItem("Build Deliverables..."));

        assertFalse(anchored, "caller is told it was not positioned, so it can warn");
        assertEquals("Build Deliverables...",
                projectMenu.getItem(projectMenu.getItemCount() - 1).getText());
    }
}
