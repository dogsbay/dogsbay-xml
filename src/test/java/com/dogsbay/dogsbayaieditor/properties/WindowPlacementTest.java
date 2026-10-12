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

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The window goes on a screen, not off the edge of every one of them.
 *
 * <p>A window placed where no screen is gets moved by the window manager, which does not tell AWT — so the
 * editor goes on believing the window is where it asked for it to be, and every screen position it reports
 * afterwards is wrong by the distance it was moved. Under mutter that distance is a title bar, which is how a
 * menu came to open 37 px above where it belonged.
 */
class WindowPlacementTest {

    private static final Dimension SCREEN = new Dimension(1920, 1080);
    private static final List<Rectangle> ONE_SCREEN = List.of(new Rectangle(0, 0, 1920, 1080));
    /** A second monitor to the right of the first, as anyone with two of them has. */
    private static final List<Rectangle> TWO_SCREENS =
            List.of(new Rectangle(0, 0, 1920, 1080), new Rectangle(1920, 0, 1920, 1080));

    // ── Where a new window goes ─────────────────────────────────────────

    @Test
    void aWindowSmallerThanTheScreenIsCentred() {
        assertThat(WindowPlacement.centred(SCREEN, new Dimension(1280, 720)))
                .isEqualTo(new Point(320, 180));
    }

    @Test
    void aWindowTheSizeOfTheScreenGoesInTheCorner() {
        assertThat(WindowPlacement.centred(SCREEN, new Dimension(1920, 1080))).isEqualTo(new Point(0, 0));
    }

    @Test
    void aWindowLargerThanTheScreenIsNotPlacedOffIt() {
        // (1080 - 1154) / 2 is negative, and that negative was being handed straight to setLocation. The
        // clamp that would have caught it was in this code once, commented out.
        assertThat(WindowPlacement.centred(SCREEN, new Dimension(1920, 1154)))
                .as("never above the top, whatever the arithmetic says")
                .isEqualTo(new Point(0, 0));
    }

    // ── Where a remembered window goes back ─────────────────────────────

    @Test
    void aRememberedPositionOffTheTopIsBroughtBack() {
        // Saved from where the window was on exit, so one bad placement is otherwise written down and
        // restored on every launch after it
        assertThat(WindowPlacement.onScreen(new Point(0, -37), ONE_SCREEN, new Dimension(1920, 1043)))
                .isEqualTo(new Point(0, 0));
    }

    @Test
    void aPositionThatIsAlreadyOnTheScreenIsLeftAlone() {
        assertThat(WindowPlacement.onScreen(new Point(100, 50), ONE_SCREEN, new Dimension(800, 600)))
                .isEqualTo(new Point(100, 50));
    }

    @Test
    void aWindowOnTheSecondMonitorStaysThere() {
        // Clamping to the primary monitor would move a two-monitor reader's window on every launch — a worse
        // thing to do to them than the off-screen position this is here to catch.
        assertThat(WindowPlacement.onScreen(new Point(2200, 300), TWO_SCREENS, new Dimension(800, 600)))
                .isEqualTo(new Point(2200, 300));
    }

    @Test
    void aMonitorToTheLeftHasNegativeCoordinatesAndIsAScreenLikeAnyOther() {
        List<Rectangle> leftAndPrimary =
                List.of(new Rectangle(0, 0, 1920, 1080), new Rectangle(-1920, 0, 1920, 1080));

        assertThat(WindowPlacement.onScreen(new Point(-1800, 200), leftAndPrimary, new Dimension(800, 600)))
                .isEqualTo(new Point(-1800, 200));
    }

    @Test
    void aPositionOnNoMonitorAtAllComesBackToTheFirst() {
        // The monitor it was on has been unplugged since
        assertThat(WindowPlacement.onScreen(new Point(4000, 2000), TWO_SCREENS, new Dimension(800, 600)))
                .isEqualTo(new Point(1120, 480));
    }

    @Test
    void withNoScreensToAskItDoesNotThrow() {
        assertThat(WindowPlacement.onScreen(new Point(10, 20), List.of(), new Dimension(800, 600)))
                .isEqualTo(new Point(0, 0));
    }

    // ── When the frame has to go ────────────────────────────────────────

    @Test
    void aFullHeightWindowHasNoRoomForATitleBar() {
        // The recording case: --window 1920x1080 on a 1920x1080 display
        assertThat(WindowPlacement.leavesNoRoomForATitleBar(SCREEN, new Dimension(1920, 1080))).isTrue();
        assertThat(WindowPlacement.leavesNoRoomForATitleBar(SCREEN, new Dimension(1200, 1080)))
                .as("a tall narrow window has the same problem")
                .isTrue();
    }

    @Test
    void aWindowMerelyAsWideAsTheScreenKeepsItsFrame() {
        // --window 1920x400 has room for its title bar above it. Taking the frame away would leave a window
        // nobody can move, resize or close, as the cure for a problem it never had.
        assertThat(WindowPlacement.leavesNoRoomForATitleBar(SCREEN, new Dimension(1920, 400))).isFalse();
    }

    @Test
    void aWindowWithRoomAboveItKeepsItsFrame() {
        assertThat(WindowPlacement.leavesNoRoomForATitleBar(SCREEN, new Dimension(1600, 900))).isFalse();
    }
}
