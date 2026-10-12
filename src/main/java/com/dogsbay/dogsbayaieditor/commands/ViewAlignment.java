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

import java.awt.Rectangle;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Where a revealed thing should land in the view.
 *
 * <p>{@code scrollRectToVisible} means "scroll as little as possible": it does nothing when the
 * target is already on screen, and otherwise brings it just inside the nearest edge. That is the
 * right default and the wrong answer for a recording, which wants the thing it is about to talk
 * about at the <em>top</em> of the view with what follows it visible underneath.
 *
 * <p>So the work here is turning "put it at the top" into a rectangle that
 * {@code scrollRectToVisible} will satisfy: a band one viewport tall, placed so that making the
 * whole band visible leaves the target where it was asked for.
 *
 * <p>Pure, over rectangles. The scrolling itself is one call in the executor; this is the part that
 * can be got wrong, so it is the part that can be asserted without a window.
 */
public final class ViewAlignment {

    private ViewAlignment() {
    }

    /** Where in the view a revealed thing lands. */
    public enum Align {

        /** Scroll only if it is out of view, and then as little as possible. */
        NEAREST,

        /** Its first line at the top of the view, with a line of margin above it. */
        TOP,

        /** Its first line in the middle of the view. */
        CENTER;

        /** The alignment called {@code name}, or null when there is no such alignment. */
        public static Align of(String name) {
            if (name == null) {
                return null;
            }
            String wanted = name.trim().toLowerCase(Locale.ROOT);
            for (Align align : values()) {
                if (align.wire().equals(wanted)) {
                    return align;
                }
            }
            return null;
        }

        /** The name a caller uses. */
        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Every name, for a refusal to list. */
        public static String names() {
            return String.join(", ", Stream.of(values()).map(Align::wire).toList());
        }
    }

    /**
     * The rectangle to make visible so that {@code first} lands where {@code align} asks.
     *
     * <p>{@code first} is the <strong>first line</strong> of the thing being revealed, not the whole
     * of it. That is deliberate and it is what makes a node taller than the view behave sensibly:
     * its start tag is what lands, because its start tag is what the line is. Centring a
     * two-hundred-line element on its middle would show the least useful part of it.
     *
     * <p>A band one viewport tall is returned rather than a point, because scrolling is asked for by
     * naming a region to bring into view. For {@code TOP} the band begins one line above the target,
     * so the target sits just below the top edge rather than flush against it — unless there is no
     * room for that margin, in which case it loses the margin rather than the target.
     *
     * <p>Neither alignment can promise more than the document allows. Near the end of a document
     * there is nothing left to scroll, so the view stops and the target lands lower than asked;
     * {@code top} and {@code center} then give the same answer. Nothing in the result says so,
     * because nothing in the result could: the position reported is where it really is.
     *
     * @param align   where it should land
     * @param first   the first line of the thing, in the view's own coordinates
     * @param visible the viewport, in the same coordinates
     */
    public static Rectangle scrollTarget(Align align, Rectangle first, Rectangle visible) {
        if (align == null || align == Align.NEAREST || visible == null || visible.isEmpty()) {
            return first;
        }
        // Clamped against the viewport, not just against zero. A band placed one line above the
        // target is only honoured if the target still fits inside it: in a viewport less than two
        // rows tall — a huge font, a split dragged small — an unclamped margin pushes the thing
        // being revealed off the bottom, and the text path then refuses it as "scrolled out of
        // view", caused by the reveal that was meant to cure that. The same clamp covers a caller
        // that hands over a box taller than one row.
        int room = visible.height - first.height;
        int above = align == Align.TOP
                ? Math.max(0, Math.min(first.height, room))
                : Math.max(0, room / 2);
        int top = Math.max(0, first.y - above);
        return new Rectangle(first.x, top, first.width, visible.height);
    }
}
