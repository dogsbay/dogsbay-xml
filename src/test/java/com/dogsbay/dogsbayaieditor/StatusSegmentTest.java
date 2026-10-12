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

import java.awt.event.MouseEvent;

import org.junit.jupiter.api.Test;

/** Unit tests for the reusable status-bar segment (headless — no display needed). */
class StatusSegmentTest {

    @Test
    void showsValueOrMutedPlaceholder() {
        StatusSegment s = new StatusSegment("📁", "tip");

        s.setValue("main", "No branch");
        assertTrue(s.hasValue());
        assertEquals("main", s.displayedText());

        s.setValue(null, "No branch");
        assertFalse(s.hasValue());
        assertEquals("No branch", s.displayedText());

        s.setValue("   ", "No branch"); // blank counts as empty → placeholder
        assertFalse(s.hasValue());
        assertEquals("No branch", s.displayedText());
    }

    @Test
    void onlyLeftClickFiresOnClick() {
        StatusSegment s = new StatusSegment(null, null); // null icon/tooltip is allowed
        int[] count = {0};
        s.setOnClick(() -> count[0]++);

        for (var l : s.getMouseListeners()) {
            l.mouseClicked(click(s, MouseEvent.BUTTON3)); // right
            l.mouseClicked(click(s, MouseEvent.BUTTON2)); // middle
        }
        assertEquals(0, count[0], "non-primary buttons must not open the switcher");

        for (var l : s.getMouseListeners()) {
            l.mouseClicked(click(s, MouseEvent.BUTTON1)); // left
        }
        assertEquals(1, count[0], "left click opens the switcher");
    }

    private static MouseEvent click(StatusSegment s, int button) {
        return new MouseEvent(s, MouseEvent.MOUSE_CLICKED, 0L, 0, 1, 1, 1, false, button);
    }
}
