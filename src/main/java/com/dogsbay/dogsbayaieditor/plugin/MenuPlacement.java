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

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

/**
 * Locating and positioning logic for plugin menu contributions.
 *
 * <p>Split out of {@link DefaultUIService} so it can be tested against a hand-built menu bar
 * — the service itself needs a live editor, which a headless test cannot construct.
 *
 * <p>Menus and items are matched by their display text, consistent with how
 * {@code UIService} has always identified menus.
 */
final class MenuPlacement {

    private MenuPlacement() {
    }

    /**
     * Finds a top-level menu by its text.
     *
     * @param menuBar the menu bar to search; may be null before the UI is built
     * @param menuName the menu text
     * @return the menu, or null if absent
     */
    static JMenu findMenu(JMenuBar menuBar, String menuName) {
        if (menuBar == null) {
            return null;
        }
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null && menuName.equals(menu.getText())) {
                return menu;
            }
        }
        return null;
    }

    /**
     * Finds a submenu by text within a named top-level menu.
     *
     * @param menuBar the menu bar to search
     * @param menuName the top-level menu text
     * @param submenuName the submenu text
     * @return the submenu, or null if either is absent
     */
    static JMenu findSubmenu(JMenuBar menuBar, String menuName, String submenuName) {
        JMenu menu = findMenu(menuBar, menuName);
        if (menu == null) {
            return null;
        }
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem candidate = menu.getItem(i);
            if (candidate instanceof JMenu && submenuName.equals(candidate.getText())) {
                return (JMenu) candidate;
            }
        }
        return null;
    }

    /**
     * Inserts an item immediately before the item with the given text, appending instead
     * when no item matches.
     *
     * <p>Appending on a miss is deliberate: if the anchor is renamed, the contributed
     * command should end up somewhere reachable rather than vanish.
     *
     * @param menu the menu to insert into
     * @param beforeItemText the text of the item to insert before
     * @param item the item to insert
     * @return true if the anchor was found and the item positioned, false if appended
     */
    static boolean insertBefore(JMenu menu, String beforeItemText, JMenuItem item) {
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem existing = menu.getItem(i);
            if (existing != null && beforeItemText.equals(existing.getText())) {
                menu.insert(item, i);
                return true;
            }
        }
        menu.add(item);
        return false;
    }
}
