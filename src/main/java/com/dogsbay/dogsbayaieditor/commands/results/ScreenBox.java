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
 * One rectangle on screen, as {@link ElementHit} reports one.
 *
 * <p>Its own type because a stretch of a document is boxed one visual line at a time: an
 * element, or a paragraph that wraps, is several of these rather than one bounding box. A
 * single box around all of it would cover the margin and the lines either side, which is
 * exactly what an annotation must not do.
 *
 * @param x       the left edge, in screen coordinates
 * @param y       the top edge, in screen coordinates
 * @param width   how wide
 * @param height  how tall
 * @param centreX the middle, in screen coordinates — where to point
 * @param centreY the middle, in screen coordinates — where to point
 */
public record ScreenBox(int x, int y, int width, int height, int centreX, int centreY) {

    /** The box of {@code bounds}, offset by where the component sits on screen. */
    public static ScreenBox at(java.awt.Rectangle bounds, java.awt.Point origin) {
        int x = origin.x + bounds.x;
        int y = origin.y + bounds.y;
        return new ScreenBox(x, y, bounds.width, bounds.height,
                x + bounds.width / 2, y + bounds.height / 2);
    }
}
