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

package com.dogsbay.dogsbayaieditor.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Rectangle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Turning "put it at the top" into a rectangle {@code scrollRectToVisible} will satisfy.
 *
 * <p>The band it returns is one viewport tall, so making the whole band visible leaves the target
 * where it was asked for. These assert the band, which is what the view is actually told; that the
 * view then obeys is {@code RevealAlignTest}'s job.
 */
class ViewAlignmentTest {

    private static final Rectangle VIEWPORT = new Rectangle(0, 0, 400, 300);

    /** One line, well below the viewport. */
    private static final Rectangle LINE = new Rectangle(10, 1000, 120, 20);

    @Test
    @DisplayName("nearest asks for the thing itself, which is the old behaviour")
    void nearestIsUnchanged() {
        assertThat(ViewAlignment.scrollTarget(ViewAlignment.Align.NEAREST, LINE, VIEWPORT))
                .isSameAs(LINE);
        assertThat(ViewAlignment.scrollTarget(null, LINE, VIEWPORT)).isSameAs(LINE);
    }

    @Test
    @DisplayName("top asks for a viewport-tall band beginning one line above")
    void topLeavesALineOfMargin() {
        Rectangle band = ViewAlignment.scrollTarget(ViewAlignment.Align.TOP, LINE, VIEWPORT);

        assertThat(band.y).as("one line above the target").isEqualTo(1000 - 20);
        assertThat(band.height).as("a viewport tall, so the target ends up at the top of it")
                .isEqualTo(300);
    }

    @Test
    @DisplayName("center asks for a band that puts the line halfway down")
    void centerPutsItHalfwayDown() {
        Rectangle band = ViewAlignment.scrollTarget(ViewAlignment.Align.CENTER, LINE, VIEWPORT);

        assertThat(band.y).as("(300 - 20) / 2 above the target").isEqualTo(1000 - 140);
        assertThat(band.height).isEqualTo(300);
    }

    @Test
    @DisplayName("neither alignment asks the view to scroll above its start")
    void neverNegative() {
        Rectangle nearTheTop = new Rectangle(10, 5, 120, 20);

        assertThat(ViewAlignment.scrollTarget(ViewAlignment.Align.TOP, nearTheTop, VIEWPORT).y)
                .isZero();
        assertThat(ViewAlignment.scrollTarget(ViewAlignment.Align.CENTER, nearTheTop, VIEWPORT).y)
                .isZero();
    }

    @Test
    @DisplayName("the margin is a line of the target, not a number that happens to fit the fixture")
    void theMarginFollowsTheLineHeight() {
        // Every other case here uses a 20px line, so a hardcoded 20 would pass all of them.
        Rectangle tallLine = new Rectangle(10, 1000, 120, 31);
        Rectangle shortLine = new Rectangle(10, 1000, 120, 9);

        assertThat(ViewAlignment.scrollTarget(ViewAlignment.Align.TOP, tallLine, VIEWPORT).y)
                .isEqualTo(1000 - 31);
        assertThat(ViewAlignment.scrollTarget(ViewAlignment.Align.TOP, shortLine, VIEWPORT).y)
                .isEqualTo(1000 - 9);
    }

    @Test
    @DisplayName("centre is measured from the line's own height, not half the view")
    void centreAllowsForTheLineItself() {
        // (300 - 20) / 2 is 140, and half the view is 150. The difference is half a line, which is
        // exactly what a tolerance wide enough to be safe would hide.
        Rectangle band = ViewAlignment.scrollTarget(ViewAlignment.Align.CENTER, LINE, VIEWPORT);
        assertThat(band.y).isEqualTo(1000 - 140);
        assertThat(band.y).as("not half the viewport").isNotEqualTo(1000 - 150);
    }

    @Test
    @DisplayName("in a view less than two lines tall, the margin goes rather than the target")
    void aViewTooShortForAMargin() {
        // An unclamped margin puts the band's top a full line above the target and the band is only
        // one view tall, so the target falls off the bottom — and the text path then refuses it as
        // scrolled out of view, caused by the reveal meant to cure that.
        Rectangle line = new Rectangle(10, 1000, 120, 17);

        Rectangle roomForMost = ViewAlignment.scrollTarget(
                ViewAlignment.Align.TOP, line, new Rectangle(0, 0, 400, 23));
        assertThat(1000 - roomForMost.y).as("6px of margin is all 23px of view allows")
                .isEqualTo(6);

        Rectangle noRoom = ViewAlignment.scrollTarget(
                ViewAlignment.Align.TOP, line, new Rectangle(0, 0, 400, 17));
        assertThat(noRoom.y).as("flush with the top, rather than pushed off the bottom")
                .isEqualTo(1000);
    }

    @Test
    @DisplayName("a target taller than its view is put at the top by both alignments")
    void aTargetTallerThanTheView() {
        // Reachable from reveal's non-text kinds, which hand over a whole component box rather than
        // one line. Without the clamp the band begins below the target and scrolls it off the top.
        Rectangle tall = new Rectangle(10, 1000, 120, 500);

        assertThat(ViewAlignment.scrollTarget(ViewAlignment.Align.TOP, tall, VIEWPORT).y)
                .isEqualTo(1000);
        assertThat(ViewAlignment.scrollTarget(ViewAlignment.Align.CENTER, tall, VIEWPORT).y)
                .isEqualTo(1000);
    }

    @Test
    @DisplayName("a target taller than the view still lands by its first line")
    void aTallTargetLandsByItsStart() {
        // The caller passes the FIRST LINE of the thing, never the whole of it, so this is true by
        // construction — which is the point. Centring a two-hundred-line element on its middle
        // would show the least useful part of it, and its start tag is what the narration names.
        Rectangle firstLineOfSomethingHuge = new Rectangle(10, 1000, 120, 20);
        Rectangle top = ViewAlignment.scrollTarget(
                ViewAlignment.Align.TOP, firstLineOfSomethingHuge, VIEWPORT);

        assertThat(top.y).isEqualTo(980);
        assertThat(top.height).as("no taller than the view, whatever the element's own height")
                .isEqualTo(VIEWPORT.height);
    }

    @Test
    @DisplayName("a view nothing has laid out yet is left to scroll as little as it can")
    void noViewportMeansNearest() {
        assertThat(ViewAlignment.scrollTarget(ViewAlignment.Align.TOP, LINE, new Rectangle(0, 0, 0, 0)))
                .isSameAs(LINE);
        assertThat(ViewAlignment.scrollTarget(ViewAlignment.Align.TOP, LINE, null)).isSameAs(LINE);
    }

    @Test
    @DisplayName("the band keeps the target's own column, so a long line does not scroll sideways")
    void horizontalPositionIsKept() {
        Rectangle band = ViewAlignment.scrollTarget(ViewAlignment.Align.TOP, LINE, VIEWPORT);

        assertThat(band.x).isEqualTo(LINE.x);
        assertThat(band.width).isEqualTo(LINE.width);
    }

    @Test
    @DisplayName("alignments are named by the words a caller writes")
    void names() {
        assertThat(ViewAlignment.Align.of("top")).isEqualTo(ViewAlignment.Align.TOP);
        assertThat(ViewAlignment.Align.of(" CENTER ")).isEqualTo(ViewAlignment.Align.CENTER);
        assertThat(ViewAlignment.Align.of("centre")).as("not a spelling this takes").isNull();
        assertThat(ViewAlignment.Align.of("middle")).isNull();
        assertThat(ViewAlignment.Align.of(null)).isNull();
        assertThat(ViewAlignment.Align.names()).isEqualTo("nearest, top, center");
    }
}
