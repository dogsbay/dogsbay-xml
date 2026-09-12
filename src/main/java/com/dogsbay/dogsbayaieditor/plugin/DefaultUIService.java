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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayTabbedView;

/**
 * Default implementation of {@link UIService} that delegates to
 * the {@link DogsBayAIEditor}'s UI components.
 *
 * <p>All methods handle the case where the editor isn't fully initialized yet
 * (e.g. during early plugin activation) by logging a warning and returning.
 */
public class DefaultUIService implements UIService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultUIService.class);

    private final DogsBayAIEditor editor;

    /** Tracks plugin-added tab components by title for later removal. */
    private final Map<String, JComponent> pluginTabs = new ConcurrentHashMap<>();

    public DefaultUIService(DogsBayAIEditor editor) {
        this.editor = editor;
    }

    @Override
    public void addMenu(JMenu menu) {
        ensureEDT(() -> {
            JMenuBar menuBar = editor.getJMenuBar();
            if (menuBar == null) {
                LOG.warn("Cannot add menu '{}': menu bar not yet initialized", menu.getText());
                return;
            }
            // Insert before the last menu (Help) to keep Help at the end
            int count = menuBar.getMenuCount();
            if (count > 0) {
                menuBar.add(menu, count - 1);
            } else {
                menuBar.add(menu);
            }
            menuBar.revalidate();
            menuBar.repaint();
        });
    }

    @Override
    public void addMenuItem(String menuName, JMenuItem item) {
        ensureEDT(() -> {
            JMenuBar menuBar = editor.getJMenuBar();
            if (menuBar == null) {
                LOG.warn("Cannot add menu item to '{}': menu bar not yet initialized", menuName);
                return;
            }
            for (int i = 0; i < menuBar.getMenuCount(); i++) {
                JMenu menu = menuBar.getMenu(i);
                if (menu != null && menuName.equals(menu.getText())) {
                    // Check for duplicate menu item with same text
                    String itemText = item.getText();
                    if (itemText != null) {
                        for (int j = 0; j < menu.getItemCount(); j++) {
                            JMenuItem existing = menu.getItem(j);
                            if (existing != null && itemText.equals(existing.getText())) {
                                LOG.debug("Menu item '{}' already exists in '{}', skipping", itemText, menuName);
                                return;
                            }
                        }
                    }
                    menu.add(item);
                    return;
                }
            }
            LOG.warn("Menu '{}' not found; cannot add menu item", menuName);
        });
    }

    @Override
    public void addMenuItem(String menuName, String submenuName, JMenuItem item) {
        ensureEDT(() -> {
            JMenu submenu = MenuPlacement.findSubmenu(editor.getJMenuBar(), menuName, submenuName);
            if (submenu == null) {
                LOG.warn("Submenu '{}' not found in menu '{}'; cannot add menu item",
                        submenuName, menuName);
                return;
            }
            submenu.add(item);
        });
    }

    @Override
    public void addMenuItemBefore(String menuName, String beforeItemText, JMenuItem item) {
        ensureEDT(() -> {
            JMenu menu = MenuPlacement.findMenu(editor.getJMenuBar(), menuName);
            if (menu == null) {
                LOG.warn("Menu '{}' not found; cannot add menu item", menuName);
                return;
            }
            if (!MenuPlacement.insertBefore(menu, beforeItemText, item)) {
                // The anchor moved or was renamed; the item was appended instead so the
                // command stays reachable rather than being dropped silently.
                LOG.warn("Item '{}' not found in menu '{}'; appended '{}' instead",
                        beforeItemText, menuName, item.getText());
            }
        });
    }

    @Override
    public void removeMenuItem(String menuName, String submenuName, JMenuItem item) {
        ensureEDT(() -> {
            JMenu submenu = MenuPlacement.findSubmenu(editor.getJMenuBar(), menuName, submenuName);
            if (submenu != null) {
                submenu.remove(item);
            }
        });
    }

    @Override
    public void removeMenuItem(String menuName, JMenuItem item) {
        ensureEDT(() -> {
            JMenuBar menuBar = editor.getJMenuBar();
            if (menuBar == null) {
                return;
            }
            for (int i = 0; i < menuBar.getMenuCount(); i++) {
                JMenu menu = menuBar.getMenu(i);
                if (menu != null && menuName.equals(menu.getText())) {
                    menu.remove(item);
                    return;
                }
            }
        });
    }

    @Override
    public void addStatusBarItem(String id, JComponent item) {
        ensureEDT(() -> {
            if (editor.getStatusbar() == null) {
                LOG.warn("Cannot add status-bar item '{}': statusbar not yet initialized", id);
                return;
            }
            editor.getStatusbar().addPluginItem(id, item);
        });
    }

    @Override
    public void removeStatusBarItem(String id) {
        ensureEDT(() -> {
            if (editor.getStatusbar() == null) {
                return;
            }
            editor.getStatusbar().removePluginItem(id);
        });
    }

    @Override
    public void addToolbarButton(Action action) {
        ensureEDT(() -> {
            if (editor.getToolbarManager() == null || editor.getToolbarManager().getToolbar() == null) {
                LOG.warn("Cannot add toolbar button: toolbar not yet initialized");
                return;
            }
            editor.getToolbarManager().getToolbar().addSeparator();
            editor.getToolbarManager().getToolbar().add(action).setMnemonic(0);
        });
    }

    @Override
    public void addTab(String title, String tooltip, JComponent panel, Runnable onClose) {
        ensureEDT(() -> {
            DogsBayTabbedView tabbedView = editor.getSelectedTabbedView();
            if (tabbedView == null) {
                LOG.warn("Cannot add tab '{}': no tabbed view available", title);
                return;
            }
            tabbedView.addCustomPanel(panel, title, tooltip);
            pluginTabs.put(title, panel);
        });
    }

    @Override
    public void removeTab(String title) {
        ensureEDT(() -> {
            JComponent panel = pluginTabs.remove(title);
            if (panel == null) {
                LOG.warn("Tab '{}' not found in plugin tabs", title);
                return;
            }
            DogsBayTabbedView tabbedView = editor.getSelectedTabbedView();
            if (tabbedView == null) {
                LOG.warn("Cannot remove tab '{}': no tabbed view available", title);
                return;
            }
            tabbedView.removeTab(panel);
        });
    }

    @Override
    public void addBottomPanel(String title, JComponent panel) {
        ensureEDT(() -> {
            if (editor.getOutputPanel() == null) {
                LOG.warn("Cannot add bottom panel '{}': output panel not yet initialized", title);
                return;
            }
            // Check for existing tab with the same title to avoid duplicates
            javax.swing.JTabbedPane tabPane = editor.getOutputPanel().getTabbedPane();
            for (int i = 0; i < tabPane.getTabCount(); i++) {
                if (title.equals(tabPane.getTitleAt(i))) {
                    LOG.debug("Bottom panel '{}' already exists, skipping", title);
                    return;
                }
            }
            tabPane.addTab(title, panel);
        });
    }

    @Override
    public void setStatusMessage(String message) {
        ensureEDT(() -> {
            if (editor.getStatusbar() == null) {
                LOG.warn("Cannot set status message: statusbar not yet initialized");
                return;
            }
            editor.getStatusbar().setStatus(message);
        });
    }

    @Override
    public void addSidebarPanel(String id, javax.swing.Icon icon, String tooltip,
                                JComponent panel, SidebarPosition position) {
        ensureEDT(() -> {
            com.dogsbay.dogsbayaieditor.ExplorerContainer container = getContainer(position);
            if (container == null) {
                LOG.warn("Cannot add sidebar panel '{}': container not yet initialized", id);
                return;
            }
            container.addExplorer(id, icon, tooltip, panel);
        });
    }

    @Override
    public void removeSidebarPanel(String id, SidebarPosition position) {
        ensureEDT(() -> {
            com.dogsbay.dogsbayaieditor.ExplorerContainer container = getContainer(position);
            if (container == null) {
                LOG.warn("Cannot remove sidebar panel '{}': container not yet initialized", id);
                return;
            }
            container.removeExplorer(id);
        });
    }

    @Override
    public void removeBottomPanel(String title) {
        ensureEDT(() -> {
            if (editor.getOutputPanel() == null) {
                LOG.warn("Cannot remove bottom panel '{}': output panel not yet initialized", title);
                return;
            }
            javax.swing.JTabbedPane tabPane = editor.getOutputPanel().getTabbedPane();
            for (int i = 0; i < tabPane.getTabCount(); i++) {
                if (title.equals(tabPane.getTitleAt(i))) {
                    tabPane.removeTabAt(i);
                    return;
                }
            }
            LOG.warn("Bottom panel '{}' not found", title);
        });
    }

    @Override
    public void showSidebarPanel(String id, SidebarPosition position) {
        ensureEDT(() -> {
            com.dogsbay.dogsbayaieditor.ExplorerContainer container = getContainer(position);
            if (container == null) {
                LOG.warn("Cannot show sidebar panel '{}': container not yet initialized", id);
                return;
            }
            if (container.isMinimized()) {
                container.setMinimized(false);
            }
            container.setSelectedExplorer(id);
        });
    }

    @Override
    public void addEditorPopupContributor(
            com.dogsbay.xml.editor.EditorPopupContributors.Contributor contributor) {
        com.dogsbay.xml.editor.EditorPopupContributors.register(contributor);
    }

    @Override
    public void removeEditorPopupContributor(
            com.dogsbay.xml.editor.EditorPopupContributors.Contributor contributor) {
        com.dogsbay.xml.editor.EditorPopupContributors.unregister(contributor);
    }

    @Override
    public void addAttributeValueProvider(
            com.dogsbay.xml.editor.AttributeValueContributors.ValueProvider provider) {
        com.dogsbay.xml.editor.AttributeValueContributors.register(provider);
    }

    @Override
    public void removeAttributeValueProvider(
            com.dogsbay.xml.editor.AttributeValueContributors.ValueProvider provider) {
        com.dogsbay.xml.editor.AttributeValueContributors.unregister(provider);
    }

    private com.dogsbay.dogsbayaieditor.ExplorerContainer getContainer(SidebarPosition position) {
        if (position == SidebarPosition.LEFT) {
            return editor.getExplorerContainer();
        } else {
            return editor.getRightExplorerContainer();
        }
    }

    @Override
    public javax.swing.Icon loadSidebarIcon(String resourcePath) {
        return editor.getSidebarIcon(resourcePath);
    }

    /**
     * Ensures the given runnable executes on the EDT.
     */
    private void ensureEDT(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
}
