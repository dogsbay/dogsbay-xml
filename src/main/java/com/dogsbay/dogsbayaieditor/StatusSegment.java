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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * A clickable status-bar segment with a consistent look: a small icon, a value
 * label, and a ▾ dropdown affordance, with a hover highlight and a tooltip.
 *
 * <p>Used for the project / branch / deliverable indicators so the status-bar
 * context strip is uniform. The value can be a real value or a muted placeholder
 * (e.g. "No branch"); click opens the segment's popup via {@link #setOnClick}.
 */
public class StatusSegment extends JPanel {

    private static final Color HOVER = new Color(0, 0, 0, 20);

    private final JLabel valueLabel;
    private boolean hovered = false;
    private boolean hasValue = false;
    private transient Runnable onClick;

    /**
     * @param icon    a short icon string (emoji/glyph), or null for none
     * @param tooltip hover tooltip, or null
     */
    public StatusSegment(String icon, String tooltip) {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(0, 8, 0, 8));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (tooltip != null) {
            setToolTipText(tooltip);
        }

        if (icon != null && !icon.isEmpty()) {
            JLabel iconLabel = new JLabel(icon);
            iconLabel.setBorder(new EmptyBorder(0, 0, 0, 5));
            add(iconLabel, BorderLayout.WEST);
        }

        valueLabel = new JLabel();
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.PLAIN, 11f));
        add(valueLabel, BorderLayout.CENTER);

        JLabel arrow = new JLabel("▾"); // ▾
        arrow.setFont(arrow.getFont().deriveFont(9f));
        arrow.setForeground(Statusbar.placeholderForeground());
        arrow.setBorder(new EmptyBorder(0, 5, 0, 0));
        add(arrow, BorderLayout.EAST);

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return; // only the primary button opens the switcher
                }
                // The popup grabs focus, so mouseExited may not fire — clear the
                // hover highlight now to avoid leaving it stuck on.
                hovered = false;
                repaint();
                if (onClick != null) {
                    onClick.run();
                }
            }
        });
    }

    /** Set the click action (opens this segment's popup). */
    public void setOnClick(Runnable r) {
        this.onClick = r;
    }

    /** Show {@code value}, or a muted {@code placeholder} when it's blank. */
    public void setValue(String value, String placeholder) {
        hasValue = value != null && !value.isBlank();
        valueLabel.setText(hasValue ? value : placeholder);
        valueLabel.setForeground(hasValue ? Statusbar.valueForeground() : Statusbar.placeholderForeground());
    }

    /** True when a real value (not the placeholder) is shown. */
    public boolean hasValue() {
        return hasValue;
    }

    /** The currently displayed text (value or placeholder). */
    public String displayedText() {
        return valueLabel.getText();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (hovered) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(HOVER);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }
}
