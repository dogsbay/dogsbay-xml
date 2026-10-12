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

import java.awt.Point;
import java.awt.Rectangle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.commands.results.ScreenBox;

/**
 * Six integers out of a rectangle and a point.
 *
 * <p>Trivial arithmetic, and the only piece of boxing a stretch that needs nothing at all to test —
 * which is why it went untested while a display-driven test was written for the hard part. Swapping
 * the origin's axes, dropping a {@code / 2}, or transposing width and height all compile and all
 * put an annotation somewhere other than over the words.
 */
class ScreenBoxTest {

    @Test
    @DisplayName("the box is the rectangle, moved by where the component sits")
    void offsetByTheOrigin() {
        ScreenBox box = ScreenBox.at(new Rectangle(10, 20, 60, 18), new Point(100, 200));

        assertThat(box.x()).isEqualTo(110);
        assertThat(box.y()).isEqualTo(220);
        assertThat(box.width()).as("width is not moved").isEqualTo(60);
        assertThat(box.height()).as("height is not moved").isEqualTo(18);
    }

    @Test
    @DisplayName("the centre is the middle of the box, on screen")
    void theCentreIsTheMiddle() {
        ScreenBox box = ScreenBox.at(new Rectangle(10, 20, 60, 18), new Point(100, 200));

        assertThat(box.centreX()).as("110 + 60/2").isEqualTo(140);
        assertThat(box.centreY()).as("220 + 18/2").isEqualTo(229);
    }

    @Test
    @DisplayName("width and height are not swapped, even when they are easy to confuse")
    void widthAndHeightKeepTheirAxes() {
        // A tall narrow box: transposing the two, or measuring the centre from the wrong one, both
        // show up here and in neither of the square cases.
        ScreenBox box = ScreenBox.at(new Rectangle(0, 0, 2, 40), new Point(0, 0));

        assertThat(box.width()).isEqualTo(2);
        assertThat(box.height()).isEqualTo(40);
        assertThat(box.centreX()).isEqualTo(1);
        assertThat(box.centreY()).isEqualTo(20);
    }

    @Test
    @DisplayName("an origin at the screen's corner adds nothing")
    void anOriginOfZero() {
        ScreenBox box = ScreenBox.at(new Rectangle(7, 9, 11, 13), new Point(0, 0));

        assertThat(box.x()).isEqualTo(7);
        assertThat(box.y()).isEqualTo(9);
    }
}
