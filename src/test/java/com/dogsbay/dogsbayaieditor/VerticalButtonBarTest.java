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

import static org.junit.jupiter.api.Assertions.*;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

import org.junit.jupiter.api.Test;

/** Headless tests for the Slack-style captions under the sidebar icons. */
class VerticalButtonBarTest {

    private static final Icon ICON = new ImageIcon(new byte[0]);

    @Test
    void addButtonRendersTooltipAsCaptionBelowIcon() {
        VerticalButtonBar bar = new VerticalButtonBar();
        bar.addButton("explorer", ICON, "Explorer");

        JToggleButton b = bar.buttonFor("explorer");
        assertNotNull(b);
        assertEquals("Explorer", b.getText(), "caption mirrors the tooltip");
        assertEquals("Explorer", b.getToolTipText(), "hover tooltip is preserved");
        assertEquals(SwingConstants.BOTTOM, b.getVerticalTextPosition(), "caption sits below the icon");
        assertEquals(SwingConstants.CENTER, b.getHorizontalTextPosition());
        assertEquals(72, b.getPreferredSize().width, "button widened for the caption");
    }

    @Test
    void blankTooltipLeavesNoCaption() {
        VerticalButtonBar bar = new VerticalButtonBar();
        bar.addButton("blank", ICON, "  ");
        assertEquals("", bar.buttonFor("blank").getText(), "blank tooltip → no caption");
    }

    @Test
    void activeCaptionIsBoldNotLightColoured() {
        VerticalButtonBar bar = new VerticalButtonBar();
        bar.addButton("a", ICON, "Topic Maps");
        bar.addButton("b", ICON, "Git");

        bar.setSelected("a");
        assertTrue(bar.buttonFor("a").getFont().isBold(), "active caption is bold");
        assertFalse(bar.buttonFor("b").getFont().isBold(), "inactive caption stays plain");

        // switching selection un-bolds the previous one
        bar.setSelected("b");
        assertTrue(bar.buttonFor("b").getFont().isBold());
        assertFalse(bar.buttonFor("a").getFont().isBold(), "deselected caption returns to plain");
    }
}
