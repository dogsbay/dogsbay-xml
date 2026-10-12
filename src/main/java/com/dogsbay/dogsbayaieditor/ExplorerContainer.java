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

package com.dogsbay.dogsbayaieditor;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container panel for VSCode-style vertical explorer buttons with
 * minimize/restore functionality.
 * Manages a vertical button bar and content panel with CardLayout for switching
 * between explorers.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/22 $
 * @author DogsBay Ltd
 */
public class ExplorerContainer extends JPanel {
    private static final boolean DEBUG = false;

    private static final int DEFAULT_WIDTH = 300;
    private static final int MINIMUM_WIDTH = 200;

    private VerticalButtonBar buttonBar;
    private JPanel contentPanel;
    private CardLayout cardLayout;
    private Map<String, Component> explorers;
    private Map<String, String> explorerNames; // id → display name
    private List<ChangeListener> changeListeners;
    private boolean minimized;
    private int savedWidth;
    private String selectedId;
    private Component bottomComponent;

    /**
     * Creates a new explorer container.
     */
    public ExplorerContainer() {
        super(new BorderLayout());

        explorers = new HashMap<>();
        explorerNames = new HashMap<>();
        changeListeners = new ArrayList<>();
        minimized = false;
        savedWidth = DEFAULT_WIDTH;

        if (DEBUG) {
            System.out.println("ExplorerContainer: Initializing...");
        }

        // Create button bar
        buttonBar = new VerticalButtonBar();
        buttonBar.addChangeListener(e -> onButtonBarChanged());
        buttonBar.addDoubleClickListener(e -> toggleMinimized());

        // Create content panel with CardLayout
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);

        // Add components
        add(buttonBar, BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);

        // Set border similar to original JTabbedPane
        setBorder(new CompoundBorder(
                new MatteBorder(1, 1, 0, 0, UIManager.getColor("controlDkShadow")),
                new MatteBorder(0, 0, 1, 1, UIManager.getColor("controlHighlight"))));

        // Set initial size
        setPreferredSize(new Dimension(DEFAULT_WIDTH, 0));
        setMinimumSize(new Dimension(MINIMUM_WIDTH, 0));


        if (DEBUG) {
            System.out.println("ExplorerContainer: Initialized");
        }
    }

    /**
     * Adds an explorer to the container.
     *
     * @param id        the unique identifier for this explorer
     * @param icon      the icon to display in the button
     * @param tooltip   the tooltip text for the button
     * @param component the component to display when selected
     */
    public void addExplorer(String id, Icon icon, String tooltip, Component component) {
        if (DEBUG) {
            System.out.println("ExplorerContainer.addExplorer: " + id);
        }

        // Add button to button bar
        buttonBar.addButton(id, icon, tooltip);

        // Add component to card layout
        contentPanel.add(component, id);
        explorers.put(id, component);
        explorerNames.put(id, tooltip);

        // Show the panel that leads the rail, not the one that happened to
        // register first. The editor adds Properties directly and the plugins
        // activate afterwards, so "first to arrive" meant Properties every
        // time, whatever the rail's order said.
        if (!chosenByReader
                && (selectedId == null
                    || SidebarOrder.rankOf(id) < SidebarOrder.rankOf(selectedId))) {
            select(id);
        }
    }

    /**
     * Whether the reader has picked a panel themselves.
     *
     * <p>Once they have, a plugin finishing its start-up must not move them
     * somewhere else.
     */
    private boolean chosenByReader = false;

    /**
     * Removes an explorer from the container.
     *
     * @param id the unique identifier of the explorer to remove
     */
    public void removeExplorer(String id) {
        Component component = explorers.remove(id);
        if (component == null) return;

        contentPanel.remove(component);
        buttonBar.removeButton(id);

        if (id.equals(selectedId)) {
            selectedId = null;
            // The panel they were looking at has gone, so falling back to the
            // one that leads the rail is not overriding a choice of theirs.
            explorers.keySet().stream()
                    .min(java.util.Comparator.comparingInt(SidebarOrder::rankOf))
                    .ifPresent(this::select);
        }

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    /**
     * Sets the selected explorer by ID.
     *
     * @param id the explorer ID to select
     */
    public void setSelectedExplorer(String id) {
        if (!explorers.containsKey(id)) {
            // Asking for a panel that is not here yet is not a choice. Latching
            // on it turned the ordering off for the rest of the session: no
            // later panel could take the lead, however early it ranks.
            return;
        }
        chosenByReader = true;
        select(id);
    }

    /** Select a panel without treating it as the reader's own choice. */
    private void select(String id) {
        if (DEBUG) {
            System.out.println("ExplorerContainer.select: " + id);
        }

        if (!explorers.containsKey(id)) {
            return;
        }

        String oldId = selectedId;
        selectedId = id;

        // Update button bar selection
        buttonBar.setSelected(id);

        // Switch card to show selected component
        cardLayout.show(contentPanel, id);

        // Restore from minimized if selecting different explorer
        if (minimized && !id.equals(oldId)) {
            setMinimized(false);
        }

        // Fire change event if selection actually changed
        if (!id.equals(oldId)) {
            fireStateChanged();
        }
    }

    /**
     * Re-shows the currently selected explorer card. This forces CardLayout
     * to call setVisible(false) then setVisible(true) on the component,
     * which fixes blank trees that were laid out before the container was sized.
     */
    public void refreshSelectedExplorer() {
        System.out.println("=== EXPLORER CONTAINER DEBUG ===");
        System.out.println("  contentPanel.isVisible(): " + contentPanel.isVisible());
        System.out.println("  contentPanel.isShowing(): " + contentPanel.isShowing());
        System.out.println("  contentPanel.getSize(): " + contentPanel.getSize());
        System.out.println("  contentPanel.getComponentCount(): " + contentPanel.getComponentCount());
        System.out.println("  buttonBar.getSize(): " + buttonBar.getSize());
        System.out.println("  minimized: " + minimized);
        System.out.println("  this.getLayout(): " + getLayout());
        for (Component c : getComponents()) {
            System.out.println("  child: " + c.getClass().getSimpleName() + " visible=" + c.isVisible() + " size=" + c.getSize());
        }
        System.out.println("=== END ===");

        if (selectedId != null) {
            Component selected = explorers.get(selectedId);
            if (selected != null) {
                selected.setVisible(false);
                cardLayout.show(contentPanel, selectedId);
                contentPanel.validate();
            }
        }
    }

    /**
     * Returns the IDs of all registered explorers.
     */
    public java.util.Set<String> getExplorerIds() {
        return explorers.keySet();
    }

    /**
     * Returns the display name for an explorer ID.
     */
    public String getExplorerName(String id) {
        return explorerNames.getOrDefault(id, id);
    }

    /**
     * Gets the currently selected explorer component.
     *
     * @return the selected component, or null if none selected
     */
    public Component getSelectedComponent() {
        return selectedId != null ? explorers.get(selectedId) : null;
    }

    /**
     * Gets the ID of the currently selected explorer.
     *
     * @return the selected explorer ID, or null if none selected
     */
    public String getSelectedId() {
        return selectedId;
    }

    /**
     * Updates the icon for an explorer button.
     *
     * @param id   the explorer ID
     * @param icon the new icon to display
     */
    public void updateExplorerIcon(String id, Icon icon) {
        buttonBar.updateIcon(id, icon);
    }

    /**
     * Sets the orientation of the explorer container.
     * 
     * @param orientation the orientation (LEFT or RIGHT)
     */
    /**
     * Sets a component to be displayed at the bottom of the container.
     * This component will span the full width of the container.
     *
     * @param component the component to add
     */
    public void setBottomComponent(Component component) {
        if (this.bottomComponent != null) {
            remove(this.bottomComponent);
        }

        this.bottomComponent = component;

        if (component != null) {
            add(component, BorderLayout.SOUTH);
            // If minimized, hide the bottom component as it likely won't fit or look good
            component.setVisible(!minimized);
        }

        revalidate();
        repaint();
    }

    public void setOrientation(VerticalButtonBar.Orientation orientation) {
        buttonBar.setOrientation(orientation);

        // Update layout
        remove(buttonBar);
        if (orientation == VerticalButtonBar.Orientation.RIGHT) {
            add(buttonBar, BorderLayout.EAST);
        } else {
            add(buttonBar, BorderLayout.WEST);
        }

        revalidate();
        repaint();
    }

    /**
     * Sets the minimized state of the explorer panel.
     * When minimized, only the button bar is visible.
     * When restored, the full panel width is restored.
     *
     * @param minimized true to minimize, false to restore
     */
    public void setMinimized(boolean minimized) {
        if (this.minimized == minimized) {
            return; // No change
        }

        if (DEBUG) {
            System.out.println("ExplorerContainer.setMinimized: " + minimized);
        }

        this.minimized = minimized;

        // Find parent JSplitPane for divider control
        JSplitPane splitPane = findParentSplitPane();

        if (minimized) {
            // Save current width
            if (splitPane != null) {
                // For right side, we might need to calculate differently depending on how split
                // pane is set up
                // But generally getDividerLocation returns the left/top component size
                // If we are on the right, the divider location is (Total - Width)

                // However, simpler to just save our own width
                savedWidth = getWidth();
            } else {
                savedWidth = getWidth();
            }

            if (DEBUG) {
                System.out.println("  Saved width: " + savedWidth);
            }

            // Hide content panel
            contentPanel.setVisible(false);

            if (bottomComponent != null) {
                bottomComponent.setVisible(false);
            }

            // Set to button bar width only
            int buttonBarWidth = buttonBar.getPreferredSize().width;
            setPreferredSize(new Dimension(buttonBarWidth, getHeight()));
            setMinimumSize(new Dimension(buttonBarWidth, 0));

            // Update split pane divider if available
            if (splitPane != null) {
                // If we are the right component of the split pane
                if (splitPane.getRightComponent() == this || splitPane.getRightComponent() == getParent()) {
                    int totalWidth = splitPane.getWidth();
                    int dividerSize = splitPane.getDividerSize();
                    splitPane.setDividerLocation(totalWidth - dividerSize - buttonBarWidth);
                } else {
                    // We are left component
                    splitPane.setDividerLocation(buttonBarWidth);
                }
            }

        } else {
            // Restore content panel
            contentPanel.setVisible(true);

            if (bottomComponent != null) {
                bottomComponent.setVisible(true);
            }

            // Restore size (ensure minimum reasonable width)
            int restoreWidth = Math.max(savedWidth, MINIMUM_WIDTH);
            setPreferredSize(new Dimension(restoreWidth, getHeight()));
            setMinimumSize(new Dimension(MINIMUM_WIDTH, 0));

            if (DEBUG) {
                System.out.println("  Restored width: " + restoreWidth);
            }

            // Restore split pane divider if available
            if (splitPane != null) {
                // If we are the right component of the split pane
                if (splitPane.getRightComponent() == this || splitPane.getRightComponent() == getParent()) {
                    int totalWidth = splitPane.getWidth();
                    int dividerSize = splitPane.getDividerSize();
                    splitPane.setDividerLocation(totalWidth - dividerSize - restoreWidth);
                } else {
                    // We are left component
                    splitPane.setDividerLocation(restoreWidth);
                }
            }
        }

        // Revalidate to update layout
        revalidate();
        repaint();

        // Also revalidate parent to ensure split pane updates
        Container parent = getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }

        // When restoring from minimized, the active card was laid out at 0x0.
        // Re-show it after the layout pass so it gets the correct size.
        if (!minimized && selectedId != null) {
            SwingUtilities.invokeLater(() -> {
                cardLayout.show(contentPanel, selectedId);
                contentPanel.revalidate();
                contentPanel.repaint();
            });
        }
    }

    /**
     * Checks if the explorer panel is currently minimized.
     *
     * @return true if minimized, false otherwise
     */
    public boolean isMinimized() {
        return minimized;
    }

    /**
     * Toggles the minimized state.
     */
    public void toggleMinimized() {
        setMinimized(!minimized);
    }

    /**
     * Sets the properties panel to display at the bottom.
     *
     * @param propertiesPanel the properties panel component
     */
    public void setPropertiesPanel(JComponent propertiesPanel) {
        add(propertiesPanel, BorderLayout.SOUTH);
    }

    /**
     * Adds a pinned action button at the bottom of the button bar.
     *
     * @param icon     the icon to display
     * @param tooltip  the tooltip text
     * @param listener the action to perform when clicked
     */
    public void addBottomButton(Icon icon, String tooltip, java.awt.event.ActionListener listener) {
        buttonBar.addBottomButton(icon, tooltip, listener);
    }

    /**
     * Adds a change listener to be notified when the selected explorer changes.
     *
     * @param listener the listener to add
     */
    public void addChangeListener(ChangeListener listener) {
        changeListeners.add(listener);
    }

    /**
     * Removes a change listener.
     */
    public void removeChangeListener(ChangeListener listener) {
        changeListeners.remove(listener);
    }

    /**
     * Handles button bar selection changes.
     */
    private void onButtonBarChanged() {
        String newId = buttonBar.getSelected();
        if (newId != null && !newId.equals(selectedId)) {
            setSelectedExplorer(newId);
        }
    }

    /**
     * Fires a state change event to all listeners.
     */
    private void fireStateChanged() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener : changeListeners) {
            listener.stateChanged(event);
        }
    }

    /**
     * Finds the parent JSplitPane in the component hierarchy.
     *
     * @return the parent JSplitPane, or null if not found
     */
    private JSplitPane findParentSplitPane() {
        Container parent = getParent();
        while (parent != null) {
            if (parent instanceof JSplitPane) {
                return (JSplitPane) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }
}
