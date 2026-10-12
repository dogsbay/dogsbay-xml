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

package com.dogsbay.dogsbayaieditor.commands.results;

/**
 * A button that was found by name, where it is on screen, and whether it was pressed.
 *
 * <p>The position is what makes this worth returning even when the button was pressed here. A recording that
 * wants the pointer to travel to the button and click it — so a viewer sees which button is being pressed,
 * rather than a dialog closing by itself — asks for the position, moves its own pointer there and clicks. The
 * name is still what found the button, so frames, themes, fonts and scaling cannot move the target.
 *
 * @param label   the button's text, as the button spells it
 * @param window  the title of the window it was found in
 * @param pressed whether this call pressed it
 * @param x       the left edge, in screen coordinates
 * @param y       the top edge, in screen coordinates
 * @param width   the button's width
 * @param height  the button's height
 * @param centreX the middle of the button, in screen coordinates — where to point
 * @param centreY the middle of the button, in screen coordinates — where to point
 */
public record ButtonHit(String label, String window, boolean pressed,
        int x, int y, int width, int height, int centreX, int centreY) {
}
