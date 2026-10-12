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

package com.dogsbay.xml.author.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * A vertical stack with real height-for-width: every child gets the full
 * container width, and its height is its preferred height <em>at that
 * width</em>.
 *
 * <p>Two rules make this safe where BoxLayout (stale cached heights, blocks
 * painting over each other) and a naive height-for-width implementation
 * (geometry mutated from {@code getPreferredSize()} during painting — text
 * smearing under popups) both failed:
 *
 * <ol>
 * <li>Children are only ever resized inside {@link #layoutContainer} — a real
 *     layout pass. Sizing a wrapping text component there makes it report a
 *     wrap-aware preferred height, and if that differs from its previous one
 *     the component's own {@code preferenceChanged → revalidate} schedules the
 *     follow-up pass that grows the ancestors.</li>
 * <li>{@link #preferredLayoutSize} is strictly read-only. Once children have
 *     been laid out at the current width their preferred sizes are
 *     wrap-aware, so the sum is exact; before that it is a one-pass-stale
 *     estimate that the revalidate cascade corrects.</li>
 * </ol>
 */
class VerticalStackLayout implements LayoutManager {

    @Override
    public void layoutContainer(Container parent) {
        Insets in = parent.getInsets();
        int width = Math.max(0, parent.getWidth() - in.left - in.right);
        int y = in.top;
        boolean moved = false;
        for (Component child : parent.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            if (child.getWidth() != width) {
                // a real layout pass: sizing here is legitimate, and makes
                // wrapping children report the right preferred height
                child.setSize(width, Integer.MAX_VALUE / 4);
                moved = true;
            }
            int h = child.getPreferredSize().height;
            if (child.getY() != y || child.getHeight() != h || child.getX() != in.left) {
                moved = true;
            }
            child.setBounds(in.left, y, width, h);
            y += h;
        }
        if (moved) {
            // blocks moved: incremental dirty regions computed before this
            // layout may no longer cover the right pixels — repaint the whole
            // container so no stale fragment can survive the next paint cycle
            parent.repaint();
        }
    }

    /** Read-only: never resizes children (may be called while painting). */
    @Override
    public Dimension preferredLayoutSize(Container parent) {
        Insets in = parent.getInsets();
        int width = Math.max(0, parent.getWidth() - in.left - in.right);
        int height = in.top + in.bottom;
        int maxChildWidth = 0;
        for (Component child : parent.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            Dimension pref = child.getPreferredSize();
            height += pref.height;
            maxChildWidth = Math.max(maxChildWidth, pref.width);
        }
        int prefWidth = width > 0 ? parent.getWidth() : maxChildWidth + in.left + in.right;
        return new Dimension(prefWidth, height);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }
}
