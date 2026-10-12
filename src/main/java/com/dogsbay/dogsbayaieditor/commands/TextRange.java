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
import java.util.List;

/**
 * A stretch of the open document, and whether a reader can see it.
 *
 * <p>{@link EditorText} finds a stretch by the words in it and aims at one character.
 * A caller that already knows the offsets — because it asked the tree where a node is,
 * or computed them itself — wants the whole stretch boxed, line by line, so it can draw
 * over it. This is the part of that answer which does not need a window: what counts as
 * a usable range, and what to call a range the view has pushed out of sight.
 *
 * <p>The boxes themselves need a realised component, so they are the executor's work.
 * Everything here is decided over plain numbers and rectangles, <strong>the refusals
 * included</strong> — a caller meets the wording, and wording that cannot be asserted
 * without a display is wording nobody checks.
 */
public final class TextRange {

    private TextRange() {
    }

    /** A stretch of the document, as character offsets: {@code start} up to but not including {@code end}. */
    public record Span(int start, int end) {

        /** How many characters it covers. */
        public int length() {
            return end - start;
        }
    }

    /** How much of a stretch the reader can actually see. */
    public enum Visibility {

        /** Every line of it is inside the viewport. */
        VISIBLE,

        /** Some of it is, and some is scrolled away. */
        PARTLY,

        /** None of it is. */
        HIDDEN;

        /** The name a caller sees. One word, always — never a boolean with a third case bolted on. */
        public String wire() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** A usable range, or why there is not one. */
    public sealed interface Outcome {

        /** The stretch to box. */
        record Found(Span span) implements Outcome {
        }

        /** Nothing to box, and what to tell the caller. */
        record Refused(String message) implements Outcome {
        }
    }

    /**
     * The stretch {@code start} to {@code end} of a document {@code length} characters long.
     *
     * <p>An empty stretch is refused rather than boxed. A box of no width is nothing to draw and
     * nothing to point at, and a caller that asked for one has made an arithmetic mistake it would
     * rather hear about: the usual one is passing a node's start twice.
     *
     * <p>{@code end} may equal {@code length} — the last character of the document is in the
     * document — and the refusal says what the document's length actually is, because a caller
     * working from a stale copy of the text cannot otherwise tell how stale it is.
     */
    public static Outcome resolve(int start, int end, int length) {
        if (start < 0 || end < 0) {
            return new Outcome.Refused(
                    "A range runs from one character offset to another, counted from 0; "
                            + start + " to " + end + " is not one.");
        }
        // Before the empty-range advice, because that advice is "ask for start to start + 1" and there
        // is no such character when the range begins at or past the end: resolve(400, 400, 400) would
        // otherwise send a caller to 400 to 401, which is refused in turn.
        if (start >= length) {
            return new Outcome.Refused(length == 0
                    ? "The document is empty, so there is nothing at " + start + " to box."
                    : "There is no character at " + start + ": the document is " + length
                            + " characters long, so its last character is at " + (length - 1) + ".");
        }
        if (end < start) {
            return new Outcome.Refused(
                    "'end' comes before 'start': " + start + " to " + end
                            + ". A range runs forwards.");
        }
        if (end == start) {
            return new Outcome.Refused(
                    "A range of no characters has nothing to box: 'start' and 'end' are both "
                            + start + ". To box one character, ask for " + start + " to " + (start + 1) + ".");
        }
        if (end > length) {
            return new Outcome.Refused(
                    "The range " + start + " to " + end + " runs past the end of the document, which is "
                            + length + " characters long.");
        }
        return new Outcome.Found(new Span(start, end));
    }

    /**
     * {@code span} widened to whole characters of {@code text}.
     *
     * <p>A character outside the Basic Multilingual Plane — an emoji, some CJK extensions — is two
     * offsets in a Java string, and an offset that lands between them names half of it. Half a
     * character has no width to measure and cannot be put in a JSON string: it encodes to invalid
     * UTF-8, so the answer would be rejected by the transport rather than merely be wrong. The
     * stretch is widened to take the whole character in, and the answer says which offsets it
     * actually used.
     */
    public static Span wholeCharacters(Span span, String text) {
        int start = span.start();
        int end = span.end();
        if (start > 0 && start < text.length()
                && Character.isLowSurrogate(text.charAt(start))
                && Character.isHighSurrogate(text.charAt(start - 1))) {
            start--;
        }
        if (end > 0 && end < text.length()
                && Character.isLowSurrogate(text.charAt(end))
                && Character.isHighSurrogate(text.charAt(end - 1))) {
            end++;
        }
        return start == span.start() && end == span.end() ? span : new Span(start, end);
    }

    /**
     * What to tell a caller whose stretch has no rectangle at all.
     *
     * <p>Two states produce that, and they need different advice: a collapsed fold, which the reader
     * can expand, and a pane the layout has never run on, which they cannot do anything about. The
     * executor used to call both a fold, which sent callers looking for one that was not there.
     *
     * <p>Here rather than in the executor because this is a refusal, and the wording of a refusal is
     * what a caller actually meets. One that can only be read by starting a window is one nobody
     * checks.
     */
    public static String noRectangleMessage(Span span, java.awt.Rectangle visible) {
        if (visible == null || visible.isEmpty()) {
            return "This document has not been laid out yet, so nothing in it has a position."
                    + " Wait for the window to settle, with waitIdle, and ask again.";
        }
        return "The characters " + span.start() + " to " + span.end() + " are in this document but"
                + " part of them is inside a collapsed fold, so there is nowhere on screen to draw."
                + " Expand it first.";
    }

    /**
     * The answer for a stretch whose rows have been measured.
     *
     * <p>Here, and not in the executor, so that the shape of the answer can be checked without a
     * window: that the visibility computed above is the visibility reported, that a row off screen
     * is left out while one straddling the edge is clipped and kept, and that a long stretch does
     * not echo the whole document back.
     *
     * @param span    the stretch, after widening to whole characters
     * @param text    the document's text
     * @param rows    one rectangle per visual row, in the pane's coordinates
     * @param visible the pane's viewport, in the same coordinates
     * @param origin  where the pane sits on screen
     * @param window  the title of the window it is in
     * @param mostText how many characters of the stretch to echo
     */
    public static com.dogsbay.dogsbayaieditor.commands.results.RangeHit rangeHit(Span span, String text,
            List<Rectangle> rows, Rectangle visible, java.awt.Point origin, String window, int mostText) {
        List<com.dogsbay.dogsbayaieditor.commands.results.ScreenBox> boxes = new java.util.ArrayList<>();
        for (Rectangle row : rows) {
            Rectangle shown = UiTargets.onScreenPart(row, visible);
            if (shown != null) {
                boxes.add(com.dogsbay.dogsbayaieditor.commands.results.ScreenBox.at(shown, origin));
            }
        }
        String stretch = text.substring(span.start(), span.end());
        return new com.dogsbay.dogsbayaieditor.commands.results.RangeHit(
                span.start(), span.end(),
                stretch.length() <= mostText ? stretch : stretch.substring(0, mostText),
                visibility(rows, visible).wire(), window, boxes);
    }

    /**
     * How much of {@code boxes} lies inside {@code visible}.
     *
     * <p>Each box is one visual line of the stretch, in the coordinates of the component that holds
     * them. A box that is partly on screen makes the whole answer {@code PARTLY}, as does a mixture
     * of boxes on and off screen: the question a caller is asking is "can I draw this where the
     * reader will see all of it", and the answer to that is no in both cases.
     *
     * <p>A null or empty viewport means nothing has laid the component out yet, which is not the
     * same as being scrolled away; it answers {@code VISIBLE}, as {@link UiTargets#onScreenPart}
     * treats the same case. No boxes at all is the opposite polarity — {@code HIDDEN} — because a
     * stretch with nothing to draw is a stretch the reader cannot see.
     *
     * <p>A box of no area is judged by where it is rather than by what it covers.
     * {@code Rectangle.contains} is false for an empty rectangle and {@code intersection} is empty
     * for one, so the plain reading would count a zero-width row as off screen and tell the caller
     * to reveal something already in front of it.
     */
    public static Visibility visibility(List<Rectangle> boxes, Rectangle visible) {
        if (boxes == null || boxes.isEmpty()) {
            return Visibility.HIDDEN;
        }
        if (visible == null || visible.isEmpty()) {
            return Visibility.VISIBLE;
        }
        int whole = 0;
        int none = 0;
        for (Rectangle box : boxes) {
            boolean inside = box.isEmpty()
                    ? visible.contains(box.x, box.y)
                    : visible.contains(box);
            if (inside) {
                whole++;
            } else if (box.isEmpty() || box.intersection(visible).isEmpty()) {
                none++;
            }
        }
        if (whole == boxes.size()) {
            return Visibility.VISIBLE;
        }
        if (none == boxes.size()) {
            return Visibility.HIDDEN;
        }
        return Visibility.PARTLY;
    }
}
