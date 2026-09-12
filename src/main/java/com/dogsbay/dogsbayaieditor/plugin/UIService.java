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

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

/**
 * Service for plugins to contribute UI elements to the application.
 * All methods are safe to call from the EDT. If called from a non-EDT thread,
 * implementations will schedule the work on the EDT automatically.
 */
public interface UIService {

    /**
     * Add a menu to the menu bar.
     *
     * @param menu the menu to add
     */
    void addMenu(JMenu menu);

    /**
     * Add a menu item to an existing menu identified by name
     * (e.g. "XML", "Tools", "File").
     *
     * <p>{@link JMenu} is itself a {@link JMenuItem}, so passing a whole menu here
     * contributes it as a <em>submenu</em>. That is the tidiest way to add several related
     * commands: build one {@code JMenu}, add it once, and remove it once on deactivation.
     *
     * @param menuName the name of the existing menu
     * @param item the menu item to add
     */
    void addMenuItem(String menuName, JMenuItem item);

    /**
     * Add a menu item into a submenu of an existing menu — for contributing into a group
     * the core owns, such as "Project" &rarr; "Validate".
     *
     * <p>No-op with a logged warning if either the menu or the submenu is absent, so a
     * plugin cannot break the menu bar by targeting something that has moved.
     *
     * @param menuName the name of the existing top-level menu
     * @param submenuName the text of the submenu within it
     * @param item the menu item to add
     */
    void addMenuItem(String menuName, String submenuName, JMenuItem item);

    /**
     * Add a menu item at a specific position, immediately before the item with the given
     * text. Use this to land in the right group rather than at the end of the menu.
     *
     * <p>Falls back to appending — with a logged warning — when no item matches
     * {@code beforeItemText}, so a renamed anchor degrades to today's behaviour instead of
     * losing the item.
     *
     * @param menuName the name of the existing menu
     * @param beforeItemText the text of the item to insert before
     * @param item the menu item to add
     */
    void addMenuItemBefore(String menuName, String beforeItemText, JMenuItem item);

    /**
     * Remove a previously-added menu item from a named menu (for plugin
     * deactivation). No-op if the menu or item isn't found.
     *
     * @param menuName the name of the menu
     * @param item the menu item to remove (the same instance added)
     */
    void removeMenuItem(String menuName, JMenuItem item);

    /**
     * Remove a previously-added item from a submenu (for plugin deactivation). No-op if
     * the menu, submenu or item isn't found.
     *
     * @param menuName the name of the menu
     * @param submenuName the text of the submenu within it
     * @param item the menu item to remove (the same instance added)
     */
    void removeMenuItem(String menuName, String submenuName, JMenuItem item);

    /**
     * Contribute a persistent component to the status bar (e.g. a clickable
     * indicator/selector), tracked by {@code id} for later removal. Unlike
     * {@link #setStatusMessage}, this is a lasting widget, not transient text.
     *
     * @param id unique id for the item (used by {@link #removeStatusBarItem})
     * @param item the component to show in the status bar
     */
    void addStatusBarItem(String id, JComponent item);

    /**
     * Remove a status-bar item added via {@link #addStatusBarItem}. No-op if absent.
     *
     * @param id the id the item was registered under
     */
    void removeStatusBarItem(String id);

    /**
     * Add a toolbar button for the given action.
     *
     * @param action the action whose button will be added to the toolbar
     */
    void addToolbarButton(Action action);

    /**
     * Add a panel as a tab in the main editor area.
     *
     * @param title the tab title
     * @param tooltip the tooltip shown on hover (may be null)
     * @param panel the component to display in the tab
     * @param onClose optional callback invoked when the tab is closed (may be null)
     */
    void addTab(String title, String tooltip, JComponent panel, Runnable onClose);

    /**
     * Remove a tab by its title.
     *
     * @param title the title of the tab to remove
     */
    void removeTab(String title);

    /**
     * Add a panel to the bottom output area.
     *
     * @param title the panel title
     * @param panel the component to display
     */
    void addBottomPanel(String title, JComponent panel);

    /**
     * Show a status message in the application status bar.
     *
     * @param message the message to display
     */
    void setStatusMessage(String message);

    /** Sidebar position for addSidebarPanel. */
    enum SidebarPosition { LEFT, RIGHT }

    /**
     * Add a panel to a sidebar (left or right explorer container).
     *
     * @param id       unique identifier for the panel
     * @param icon     icon shown in the sidebar button bar
     * @param tooltip  tooltip for the sidebar button
     * @param panel    the component to display
     * @param position which sidebar to add to
     */
    void addSidebarPanel(String id, Icon icon, String tooltip, JComponent panel, SidebarPosition position);

    /**
     * Remove a sidebar panel by its ID.
     *
     * @param id       the panel ID to remove
     * @param position which sidebar to remove from
     */
    void removeSidebarPanel(String id, SidebarPosition position);

    /**
     * Remove a bottom panel by its title.
     *
     * @param title the tab title to remove
     */
    void removeBottomPanel(String title);

    /**
     * Reveal a sidebar panel: un-minimize the sidebar if needed and select
     * the panel with the given ID (as registered via addSidebarPanel).
     *
     * @param id       the panel ID to show
     * @param position which sidebar it lives in
     */
    void showSidebarPanel(String id, SidebarPosition position);

    /**
     * Register a contributor for the text editor's right-click menu. The
     * contributor is consulted each time the popup opens and can return
     * context-aware items based on the click offset (e.g. "Go to Key
     * Definition" when the click lands on a keyref value).
     *
     * @param contributor the contributor to add
     */
    void addEditorPopupContributor(com.dogsbay.xml.editor.EditorPopupContributors.Contributor contributor);

    /**
     * Unregister an editor popup contributor (plugin deactivation).
     *
     * @param contributor the contributor to remove
     */
    void removeEditorPopupContributor(com.dogsbay.xml.editor.EditorPopupContributors.Contributor contributor);

    /**
     * Register a provider of attribute-value completion candidates. Its values are
     * merged into the grammar-provided ones in the completion popup (e.g. a DITA
     * subjectScheme's controlled values for {@code @platform}).
     *
     * @param provider the provider to add
     */
    void addAttributeValueProvider(com.dogsbay.xml.editor.AttributeValueContributors.ValueProvider provider);

    /**
     * Unregister an attribute-value provider (plugin deactivation).
     *
     * @param provider the provider to remove
     */
    void removeAttributeValueProvider(com.dogsbay.xml.editor.AttributeValueContributors.ValueProvider provider);

    /**
     * Load a sidebar icon from the application's icon resources.
     * Handles theme-aware icon loading (light/dark).
     *
     * @param resourcePath classpath resource path (e.g. "com/dogsbay/dogsbayaieditor/icons/sidebar/search.png")
     * @return the loaded icon, or null if not found
     */
    Icon loadSidebarIcon(String resourcePath);
}
