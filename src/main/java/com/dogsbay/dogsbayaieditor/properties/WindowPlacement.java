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

package com.dogsbay.dogsbayaieditor.properties;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;

/**
 * Where the editor's window goes.
 *
 * <p>Both answers here have to stay on the screen, and that is the whole of it. A window placed off the top or
 * the left is one the window manager will move, and it does not tell AWT that it has: the editor goes on
 * believing the window is where it asked for it to be, and every screen position it reports afterwards — where
 * a menu opens, where a button is — is wrong by the distance it was moved. Under mutter that distance is the
 * height of a title bar, which is how a menu comes to open 37 px above where it belongs (reported 2026-09-30).
 *
 * <p>Pure, and separate from the settings file, because the arithmetic is the part that was wrong and a test
 * cannot ask a headless machine for a screen.
 */
public final class WindowPlacement {

    private WindowPlacement() {
    }

    /**
     * The middle of the screen for a window of this size — never off the top or the left.
     *
     * <p>{@code (screen - app) / 2} is negative for a window as large as the screen, and that negative was
     * being handed straight to {@code setLocation}. The clamp was in this code once, commented out.
     */
    public static Point centred(Dimension screen, Dimension app) {
        return new Point(Math.max(0, (screen.width - app.width) / 2),
                Math.max(0, (screen.height - app.height) / 2));
    }

    /**
     * {@code wanted}, if it is on a screen, and otherwise moved onto the first one.
     *
     * <p>Applied to a remembered position as well as a computed one. A position is saved from where the window
     * was on exit, so one bad placement is otherwise written down and restored on every launch afterwards.
     *
     * <p><b>Every</b> screen, not the primary one. A reader with two monitors parks the editor on the second,
     * and its remembered x is past the width of the first — clamping that to the primary monitor would move
     * their window every launch, which is a worse thing to do to them than the off-screen position this is
     * here to catch. A monitor above or to the left has negative coordinates and is a screen like any other.
     *
     * @param screens the bounds of the screens, the first being the one to fall back to
     */
    public static Point onScreen(Point wanted, List<Rectangle> screens, Dimension app) {
        for (Rectangle screen : screens) {
            if (screen.contains(wanted)) {
                return new Point(wanted);
            }
        }
        Rectangle first = screens.isEmpty() ? new Rectangle(0, 0, app.width, app.height) : screens.get(0);
        return new Point(
                clamp(wanted.x, first.x, first.x + Math.max(0, first.width - app.width)),
                clamp(wanted.y, first.y, first.y + Math.max(0, first.height - app.height)));
    }

    private static int clamp(int value, int least, int most) {
        return Math.max(least, Math.min(value, most));
    }

    /**
     * Whether a window this tall leaves no room for a title bar above it.
     *
     * <p>Height, and not width: what a window manager cannot fit above a full-height window is the title bar,
     * so that is the one that makes it take the frame away — mutter makes such a window full screen. AWT is
     * not told, goes on expecting the title bar it asked for, and every screen position it reports is out by
     * the height of the frame it no longer has: the editor reported a button at y=-10 that was drawn at y=27,
     * and menus opened over the menu bar (measured 2026-09-30). A run asking for a window this tall should
     * say up front that it wants no frame, so the two of them have nothing to disagree about.
     *
     * <p>A window merely as <em>wide</em> as the screen is framed normally — {@code --window 1920x400} has
     * room for its title bar and must keep it, or a window nobody can move, resize or close is the cure for
     * a problem it never had.
     */
    public static boolean leavesNoRoomForATitleBar(Dimension screen, Dimension app) {
        return app.height >= screen.height;
    }
}
