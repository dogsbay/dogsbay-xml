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
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vertical button bar component for VSCode-style explorer navigation.
 * Displays toggle buttons vertically with icons, managing exclusive selection
 * and providing double-click minimize functionality.
 *
 * @version $Revision: 1.0 $, $Date: 2025/11/22 $
 * @author DogsBay Ltd
 */
public class VerticalButtonBar extends JPanel {
    private static final boolean DEBUG = false;

    // Slack-style buttons: wide enough for a short caption under the icon
    // (with headroom for the bold active caption).
    private static final int BUTTON_WIDTH = 72;
    private static final int BUTTON_HEIGHT = 50;

    private JPanel topPanel;
    private JPanel bottomPanel;
    private ButtonGroup buttonGroup;
    private Map<String, JToggleButton> buttons;
    private List<ChangeListener> changeListeners;
    private List<ActionListener> doubleClickListeners;
    private String selectedId;

    /**
     * Creates a new vertical button bar.
     */
    public VerticalButtonBar() {
        setLayout(new BorderLayout());

        buttonGroup = new ButtonGroup();
        buttons = new HashMap<>();
        changeListeners = new ArrayList<>();
        doubleClickListeners = new ArrayList<>();

        // Top panel for explorer toggle buttons
        topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setOpaque(false);

        // Bottom panel for pinned action buttons (e.g. settings)
        bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setOpaque(false);

        add(topPanel, BorderLayout.NORTH);
        add(bottomPanel, BorderLayout.SOUTH);

        // Add right border separator (like VSCode)
        setBorder(new MatteBorder(0, 0, 0, 1, UIManager.getColor("controlShadow")));

        // Set background slightly darker than default
        Color bgColor = darkenColor(UIManager.getColor("Panel.background"), 0.95f);
        setBackground(bgColor);

        if (DEBUG) {
            System.out.println("VerticalButtonBar: Initialized");
        }
    }

    /**
     * Adds a button to the bar.
     *
     * @param id      the unique identifier for this button
     * @param icon    the icon to display
     * @param tooltip the tooltip text
     */
    public void addButton(String id, Icon icon, String tooltip) {
        if (DEBUG) {
            System.out.println("VerticalButtonBar.addButton: " + id);
        }

        JToggleButton button = new JToggleButton(icon);
        button.setToolTipText(tooltip);
        // Slack-style: a short caption under the icon (the tooltip still shows on hover).
        configureLabel(button, tooltip);

        // Apply VSCode-style styling
        styleButton(button);

        // Add to button group for exclusive selection
        buttonGroup.add(button);
        buttons.put(id, button);

        // Selection listener
        button.addActionListener(e -> {
            if (button.isSelected()) {
                setSelected(id);
            }
        });

        // Double-click listener
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && button.isSelected()) {
                    if (DEBUG) {
                        System.out.println("VerticalButtonBar: Double-click on " + id);
                    }
                    fireDoubleClick(id);
                }
            }
        });

        // Placed by SidebarOrder rather than by arrival: a plugin starting
        // late should not decide what sits at the top of the rail.
        int at = insertionPoint(id);
        topPanel.add(button, at);
        topPanel.add(Box.createVerticalStrut(2), at + 1);
        order.add(Math.min(at / 2, order.size()), id);
    }

    /**
     * Where a button belongs among the ones already there.
     *
     * <p>Each button is followed by a strut, so the component index is twice
     * the button's position.
     */
    private int insertionPoint(String id) {
        int rank = SidebarOrder.rankOf(id);
        for (int i = 0; i < order.size(); i++) {
            if (SidebarOrder.rankOf(order.get(i)) > rank) {
                return i * 2;
            }
        }
        return order.size() * 2;
    }

    /** The ids in the order they are shown, so an insertion knows where to go. */
    private final java.util.List<String> order = new java.util.ArrayList<>();

    /**
     * Removes a button by its ID.
     *
     * @param id the button ID to remove
     */
    public void removeButton(String id) {
        JToggleButton button = buttons.remove(id);
        if (button == null) return;

        buttonGroup.remove(button);
        int at = topPanel.getComponentZOrder(button);
        topPanel.remove(button);
        // The strut that followed it, or the next button inherits its spacing
        // and the rail drifts every time a plugin is disabled.
        if (at >= 0 && at < topPanel.getComponentCount()) {
            topPanel.remove(at);
        }
        order.remove(id);

        topPanel.revalidate();
        topPanel.repaint();

        // If the removed button was selected, select the first remaining
        if (id.equals(selectedId)) {
            selectedId = null;
            if (!buttons.isEmpty()) {
                String firstId = buttons.keySet().iterator().next();
                setSelected(firstId);
            }
        }
    }

    /**
     * Adds a pinned action button at the bottom of the bar.
     * Unlike explorer toggle buttons, this is a regular button that
     * triggers an action without selecting an explorer panel.
     *
     * @param icon     the icon to display
     * @param tooltip  the tooltip text
     * @param listener the action to perform when clicked
     */
    public void addBottomButton(Icon icon, String tooltip, ActionListener listener) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        configureLabel(button, tooltip);

        // Same sizing and flat styling as the toggle buttons
        button.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        button.setMaximumSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        button.setMinimumSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setFocusable(false);
        button.setOpaque(true);
        button.setMargin(new Insets(0, 0, 0, 0));

        Color bgColor = darkenColor(UIManager.getColor("Panel.background"), 0.95f);
        Color hoverColor = lightenColor(bgColor, 1.15f);
        button.setBackground(bgColor);

        // Hover effect
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(hoverColor);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(bgColor);
            }
        });

        button.addActionListener(listener);

        bottomPanel.add(Box.createVerticalStrut(2));
        bottomPanel.add(button);
    }

    /**
     * Sets the selected button by ID.
     *
     * @param id the button ID to select
     */
    public void setSelected(String id) {
        JToggleButton button = buttons.get(id);
        if (button != null) {
            String oldId = selectedId;
            selectedId = id;
            button.setSelected(true);

            if (DEBUG) {
                System.out.println("VerticalButtonBar.setSelected: " + id);
            }

            if (!id.equals(oldId)) {
                fireStateChanged();
            }
        }
    }

    /**
     * Gets the ID of the currently selected button.
     *
     * @return the selected button ID, or null if none selected
     */
    public String getSelected() {
        return selectedId;
    }

    /**
     * Updates the icon for a button.
     *
     * @param id   the button ID
     * @param icon the new icon to display
     */
    public void updateIcon(String id, Icon icon) {
        JToggleButton button = buttons.get(id);
        if (button != null) {
            button.setIcon(icon);
        }
    }

    /**
     * Adds a change listener to be notified when selection changes.
     *
     * @param listener the listener to add
     */
    public void addChangeListener(ChangeListener listener) {
        changeListeners.add(listener);
    }

    /**
     * Adds a double-click listener to be notified when a button is double-clicked.
     *
     * @param listener the listener to add
     */
    public void addDoubleClickListener(ActionListener listener) {
        doubleClickListeners.add(listener);
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
     * Fires a double-click event to all listeners.
     *
     * @param id the ID of the double-clicked button
     */
    private void fireDoubleClick(String id) {
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, id);
        for (ActionListener listener : doubleClickListeners) {
            listener.actionPerformed(event);
        }
    }

    /** Test/inspection hook: the toggle button registered for {@code id}, or null. */
    JToggleButton buttonFor(String id) {
        return buttons.get(id);
    }

    /** Render the caption under the icon (Slack-style), centered, in a small font. */
    private void configureLabel(javax.swing.AbstractButton button, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        button.setText(text);
        button.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        button.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        button.setIconTextGap(2);
        // Small caption that fits the 68px button without ellipsizing the longest
        // labels (e.g. "Topic Maps", "Where Used", "XPath Query").
        button.setFont(button.getFont().deriveFont(java.awt.Font.PLAIN, 9f));
    }

    /**
     * Applies VSCode-style styling to a button.
     *
     * @param button the button to style
     */
    private void styleButton(JToggleButton button) {
        // Size
        button.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        button.setMaximumSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
        button.setMinimumSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));

        // Remove default chrome
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setFocusable(false);
        button.setOpaque(true);

        // Margin for icon centering
        button.setMargin(new Insets(0, 0, 0, 0));

        // Colors
        Color bgColor = darkenColor(UIManager.getColor("Panel.background"), 0.95f);
        Color hoverColor = lightenColor(bgColor, 1.15f);

        button.setBackground(bgColor);

        // Hover effect
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!button.isSelected()) {
                    button.setBackground(hoverColor);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!button.isSelected()) {
                    button.setBackground(bgColor);
                }
            }
        });

        // Selection effect: bold caption (in the normal, readable text colour) plus
        // the VSCode-style accent border. No highlight fill — its visibility is
        // L&F-dependent (contentAreaFilled=false) and could leave low-contrast text.
        button.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                button.setBackground(bgColor);
                button.setFont(button.getFont().deriveFont(java.awt.Font.BOLD));
                updateButtonSelectionStyle(button);
            } else {
                button.setBackground(bgColor);
                button.setFont(button.getFont().deriveFont(java.awt.Font.PLAIN));
                button.setBorder(null);
            }
        });
    }

    /**
     * Darkens a color by a factor.
     *
     * @param color  the color to darken
     * @param factor the darkening factor (< 1.0)
     * @return the darkened color
     */
    private Color darkenColor(Color color, float factor) {
        int r = Math.max(0, (int) (color.getRed() * factor));
        int g = Math.max(0, (int) (color.getGreen() * factor));
        int b = Math.max(0, (int) (color.getBlue() * factor));
        return new Color(r, g, b);
    }

    /**
     * Lightens a color by a factor.
     *
     * @param color  the color to lighten
     * @param factor the lightening factor (> 1.0)
     * @return the lightened color
     */
    private Color lightenColor(Color color, float factor) {
        int r = Math.min(255, (int) (color.getRed() * factor));
        int g = Math.min(255, (int) (color.getGreen() * factor));
        int b = Math.min(255, (int) (color.getBlue() * factor));
        return new Color(r, g, b);
    }

    /**
     * Enum for button bar orientation.
     */
    public enum Orientation {
        LEFT,
        RIGHT
    }

    private Orientation orientation = Orientation.LEFT;

    /**
     * Sets the orientation of the button bar.
     *
     * @param orientation the orientation (LEFT or RIGHT)
     */
    public void setOrientation(Orientation orientation) {
        this.orientation = orientation;

        // Update border based on orientation
        if (orientation == Orientation.RIGHT) {
            // Border on West (separator)
            setBorder(new MatteBorder(0, 1, 0, 0, UIManager.getColor("controlShadow")));
        } else {
            // Border on East (separator) - Default
            setBorder(new MatteBorder(0, 0, 0, 1, UIManager.getColor("controlShadow")));
        }

        // Re-style buttons to update selection indicators
        for (JToggleButton button : buttons.values()) {
            if (button.isSelected()) {
                updateButtonSelectionStyle(button);
            }
        }
    }

    /**
     * Updates the selection style of a button based on orientation.
     *
     * @param button the button to style
     */
    private void updateButtonSelectionStyle(JToggleButton button) {
        Color activeColor = UIManager.getColor("textHighlight");
        Color indicatorColor = lightenColor(activeColor, 1.3f);

        if (orientation == Orientation.RIGHT) {
            // Indicator on Right
            button.setBorder(new CompoundBorder(
                    new MatteBorder(0, 0, 0, 3, indicatorColor),
                    new EmptyBorder(0, 0, 0, 0)));
        } else {
            // Indicator on Left - Default
            button.setBorder(new CompoundBorder(
                    new MatteBorder(0, 3, 0, 0, indicatorColor),
                    new EmptyBorder(0, 0, 0, 0)));
        }
    }
}
