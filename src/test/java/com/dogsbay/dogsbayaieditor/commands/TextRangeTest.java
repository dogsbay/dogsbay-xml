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
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The part of boxing a stretch that needs no window: what is a usable range, what to call one
 * the view has pushed out of sight, and the words a caller meets when it asks for neither.
 */
class TextRangeTest {

    private static String refusalFor(int start, int end, int length) {
        TextRange.Outcome outcome = TextRange.resolve(start, end, length);
        assertThat(outcome).isInstanceOf(TextRange.Outcome.Refused.class);
        return ((TextRange.Outcome.Refused) outcome).message();
    }

    private static TextRange.Span spanFor(int start, int end, int length) {
        TextRange.Outcome outcome = TextRange.resolve(start, end, length);
        assertThat(outcome).as("refused: " + outcome).isInstanceOf(TextRange.Outcome.Found.class);
        return ((TextRange.Outcome.Found) outcome).span();
    }

    @Test
    @DisplayName("a stretch inside the document is taken as it is")
    void anOrdinaryRange() {
        TextRange.Span span = spanFor(120, 154, 400);
        assertThat(span.start()).isEqualTo(120);
        assertThat(span.end()).isEqualTo(154);
        assertThat(span.length()).isEqualTo(34);
    }

    @Test
    @DisplayName("the last character of the document is in the document")
    void endMayEqualLength() {
        assertThat(spanFor(399, 400, 400).end()).isEqualTo(400);
    }

    @Test
    @DisplayName("an empty stretch says how to box one character instead")
    void emptyIsRefusedHelpfully() {
        assertThat(refusalFor(42, 42, 400))
                .contains("both 42")
                .contains("ask for 42 to 43");
    }

    @Test
    @DisplayName("a backwards stretch is refused, not quietly swapped")
    void backwardsIsRefused() {
        assertThat(refusalFor(154, 120, 400)).contains("154 to 120").contains("runs forwards");
    }

    @Test
    @DisplayName("past the end says how long the document actually is")
    void pastTheEndNamesTheLength() {
        // A caller working from a stale copy of the text cannot otherwise tell how stale it is.
        assertThat(refusalFor(380, 420, 400)).contains("420").contains("400 characters");
    }

    @Test
    @DisplayName("a negative offset is refused")
    void negativeIsRefused() {
        assertThat(refusalFor(-1, 10, 400)).contains("counted from 0");
    }

    @Test
    @DisplayName("an empty range at the end of the document is not sent to a character that is not there")
    void emptyAtTheEndDoesNotGiveImpossibleAdvice() {
        // The advice for an empty range is "ask for start to start + 1". At the end of the document
        // there is no such character, and following the advice earns a second refusal.
        String message = refusalFor(400, 400, 400);
        assertThat(message).doesNotContain("400 to 401");
        assertThat(message).contains("no character at 400").contains("400 characters");
    }

    @Test
    @DisplayName("an empty document says so rather than counting characters")
    void emptyDocument() {
        assertThat(refusalFor(0, 0, 0)).contains("document is empty");
    }

    @Test
    @DisplayName("a range beginning past the end is refused by where it begins")
    void startPastTheEnd() {
        assertThat(refusalFor(500, 520, 400)).contains("no character at 500");
    }

    @Test
    @DisplayName("a backwards range inside the document is still refused as backwards")
    void backwardsBeatsNothingElse() {
        assertThat(refusalFor(50, 20, 400)).contains("runs forwards");
    }

    @Test
    @DisplayName("a row of no area is judged by where it is, not by what it covers")
    void anEmptyBoxInsideTheViewportCounts() {
        // Rectangle.contains is false for an empty rectangle and intersection is empty for one, so
        // the plain reading reports a stretch the reader is looking at as hidden.
        Rectangle viewport = new Rectangle(0, 0, 500, 500);
        assertThat(TextRange.visibility(List.of(new Rectangle(10, 10, 0, 12)), viewport))
                .isEqualTo(TextRange.Visibility.VISIBLE);
        assertThat(TextRange.visibility(List.of(new Rectangle(10, 10, 0, 0)), viewport))
                .isEqualTo(TextRange.Visibility.VISIBLE);
        assertThat(TextRange.visibility(
                List.of(new Rectangle(10, 10, 100, 20), new Rectangle(10, 30, 0, 12)), viewport))
                .as("one zero-width row among real ones does not make the answer partly")
                .isEqualTo(TextRange.Visibility.VISIBLE);
    }

    @Test
    @DisplayName("a row of no area outside the viewport is still hidden")
    void anEmptyBoxOutsideTheViewportIsHidden() {
        Rectangle viewport = new Rectangle(0, 0, 500, 500);
        assertThat(TextRange.visibility(List.of(new Rectangle(10, 900, 0, 12)), viewport))
                .isEqualTo(TextRange.Visibility.HIDDEN);
    }

    @Test
    @DisplayName("a box flush against the viewport edge is visible")
    void flushWithTheEdgeIsVisible() {
        // Locks in Rectangle.contains at the far edge, which reads as if it might be exclusive.
        Rectangle viewport = new Rectangle(0, 0, 500, 500);
        assertThat(TextRange.visibility(List.of(new Rectangle(400, 400, 100, 100)), viewport))
                .isEqualTo(TextRange.Visibility.VISIBLE);
        assertThat(TextRange.visibility(List.of(new Rectangle(0, 0, 500, 500)), viewport))
                .as("a box exactly the size of the viewport")
                .isEqualTo(TextRange.Visibility.VISIBLE);
        assertThat(TextRange.visibility(List.of(new Rectangle(401, 400, 100, 100)), viewport))
                .as("one pixel over")
                .isEqualTo(TextRange.Visibility.PARTLY);
    }

    @Test
    @DisplayName("every line inside the viewport is visible")
    void allInsideIsVisible() {
        Rectangle viewport = new Rectangle(0, 0, 500, 500);
        List<Rectangle> rows = List.of(new Rectangle(10, 10, 100, 20), new Rectangle(10, 30, 80, 20));
        assertThat(TextRange.visibility(rows, viewport)).isEqualTo(TextRange.Visibility.VISIBLE);
    }

    @Test
    @DisplayName("no line inside the viewport is hidden")
    void noneInsideIsHidden() {
        Rectangle viewport = new Rectangle(0, 0, 500, 500);
        List<Rectangle> rows = List.of(new Rectangle(10, 600, 100, 20), new Rectangle(10, 620, 80, 20));
        assertThat(TextRange.visibility(rows, viewport)).isEqualTo(TextRange.Visibility.HIDDEN);
    }

    @Test
    @DisplayName("some lines on screen and some off is partly")
    void aMixtureIsPartly() {
        Rectangle viewport = new Rectangle(0, 0, 500, 500);
        List<Rectangle> rows = List.of(new Rectangle(10, 480, 100, 20), new Rectangle(10, 600, 80, 20));
        assertThat(TextRange.visibility(rows, viewport)).isEqualTo(TextRange.Visibility.PARTLY);
    }

    @Test
    @DisplayName("one line straddling the edge is partly, not visible")
    void straddlingIsPartly() {
        // The question is "can I draw this where the reader will see all of it", and half a box
        // over the edge answers no as surely as a box that is not there at all.
        Rectangle viewport = new Rectangle(0, 0, 500, 500);
        List<Rectangle> rows = List.of(new Rectangle(10, 490, 100, 20));
        assertThat(TextRange.visibility(rows, viewport)).isEqualTo(TextRange.Visibility.PARTLY);
    }

    @Test
    @DisplayName("no boxes at all is hidden")
    void noBoxesIsHidden() {
        assertThat(TextRange.visibility(List.of(), new Rectangle(0, 0, 500, 500)))
                .isEqualTo(TextRange.Visibility.HIDDEN);
    }

    @Test
    @DisplayName("a component nothing has laid out yet is not treated as scrolled away")
    void noViewportMeansVisible() {
        List<Rectangle> rows = List.of(new Rectangle(10, 10, 100, 20));
        assertThat(TextRange.visibility(rows, new Rectangle(0, 0, 0, 0)))
                .isEqualTo(TextRange.Visibility.VISIBLE);
        assertThat(TextRange.visibility(rows, null)).isEqualTo(TextRange.Visibility.VISIBLE);
    }

    // ── the answer ──────────────────────────────────────────────────────

    private static final java.awt.Point ORIGIN = new java.awt.Point(100, 200);
    private static final Rectangle VIEWPORT = new Rectangle(0, 0, 500, 500);

    @Test
    @DisplayName("the visibility that was computed is the visibility that is reported")
    void visibilityReachesTheAnswer() {
        String text = "x".repeat(50);
        var onScreen = TextRange.rangeHit(new TextRange.Span(0, 10), text,
                List.of(new Rectangle(10, 10, 50, 17)), VIEWPORT, ORIGIN, "w", 4000);
        var offScreen = TextRange.rangeHit(new TextRange.Span(0, 10), text,
                List.of(new Rectangle(10, 900, 50, 17)), VIEWPORT, ORIGIN, "w", 4000);
        var straddling = TextRange.rangeHit(new TextRange.Span(0, 10), text,
                List.of(new Rectangle(10, 490, 50, 17)), VIEWPORT, ORIGIN, "w", 4000);

        assertThat(onScreen.visibility()).isEqualTo("visible");
        assertThat(offScreen.visibility()).isEqualTo("hidden");
        assertThat(straddling.visibility()).isEqualTo("partly");
    }

    @Test
    @DisplayName("a stretch that is not on screen comes back with no boxes")
    void hiddenMeansNoBoxes() {
        var hit = TextRange.rangeHit(new TextRange.Span(0, 10), "x".repeat(50),
                List.of(new Rectangle(10, 900, 50, 17)), VIEWPORT, ORIGIN, "w", 4000);

        assertThat(hit.visibility()).isEqualTo("hidden");
        assertThat(hit.boxes()).isEmpty();
    }

    @Test
    @DisplayName("a row off screen is left out; one over the edge is clipped and kept")
    void rowsAreClippedNotDropped() {
        var hit = TextRange.rangeHit(new TextRange.Span(0, 10), "x".repeat(50),
                List.of(new Rectangle(10, 10, 50, 17),      // on screen
                        new Rectangle(10, 490, 50, 17),      // half over the bottom edge
                        new Rectangle(10, 900, 50, 17)),     // off screen
                VIEWPORT, ORIGIN, "w", 4000);

        assertThat(hit.boxes()).as("the off-screen row is left out").hasSize(2);
        assertThat(hit.boxes().get(1).height()).as("the straddling row is clipped to the viewport")
                .isEqualTo(10);
    }

    @Test
    @DisplayName("boxes are offset by where the pane sits on screen")
    void boxesAreInScreenCoordinates() {
        var hit = TextRange.rangeHit(new TextRange.Span(0, 10), "x".repeat(50),
                List.of(new Rectangle(10, 20, 50, 17)), VIEWPORT, ORIGIN, "w", 4000);

        assertThat(hit.boxes().get(0).x()).isEqualTo(110);
        assertThat(hit.boxes().get(0).y()).isEqualTo(220);
    }

    @Test
    @DisplayName("the answer echoes the stretch, and only the stretch")
    void theTextIsTheStretch() {
        var hit = TextRange.rangeHit(new TextRange.Span(3, 8), "<p>abcde</p>",
                List.of(new Rectangle(10, 10, 50, 17)), VIEWPORT, ORIGIN, "w", 4000);

        assertThat(hit.text()).isEqualTo("abcde");
        assertThat(hit.start()).isEqualTo(3);
        assertThat(hit.end()).isEqualTo(8);
        assertThat(hit.window()).isEqualTo("w");
    }

    @Test
    @DisplayName("a long stretch does not echo the whole document back")
    void theTextIsCapped() {
        String text = "y".repeat(10_000);
        var hit = TextRange.rangeHit(new TextRange.Span(0, 10_000), text,
                List.of(new Rectangle(10, 10, 50, 17)), VIEWPORT, ORIGIN, "w", 40);

        assertThat(hit.text()).hasSize(40);
        assertThat(hit.end()).as("the offsets still describe the whole stretch").isEqualTo(10_000);
    }

    // ── whole characters ────────────────────────────────────────────────

    @Test
    @DisplayName("a stretch is widened rather than cutting a character in half")
    void surrogatePairsAreKeptWhole() {
        // An emoji is two offsets in a Java string. Half of it has no width and cannot be put in a
        // JSON string: it encodes to invalid UTF-8.
        String text = "ab😀cd";
        // A stretch beginning on the second half of the emoji: widening the start takes the whole
        // character in, and the end is already on a boundary.
        TextRange.Span fromTheMiddle = TextRange.wholeCharacters(new TextRange.Span(3, 4), text);
        assertThat(fromTheMiddle.start()).as("back onto the high surrogate").isEqualTo(2);
        assertThat(fromTheMiddle.end()).isEqualTo(4);
        assertThat(text.substring(fromTheMiddle.start(), fromTheMiddle.end()))
                .as("the whole emoji, not half of it").isEqualTo("😀");

        // A stretch ending on the second half: the end moves past it.
        TextRange.Span toTheMiddle = TextRange.wholeCharacters(new TextRange.Span(0, 3), text);
        assertThat(toTheMiddle.start()).isEqualTo(0);
        assertThat(toTheMiddle.end()).as("forward past the low surrogate").isEqualTo(4);
        assertThat(text.substring(toTheMiddle.start(), toTheMiddle.end())).isEqualTo("ab😀");
    }

    @Test
    @DisplayName("a stretch already on character boundaries is left exactly as it is")
    void wholeCharactersLeavesCleanRangesAlone() {
        String text = "ab😀cd";
        TextRange.Span span = new TextRange.Span(0, 2);
        assertThat(TextRange.wholeCharacters(span, text)).isSameAs(span);
        assertThat(TextRange.wholeCharacters(new TextRange.Span(2, 4), text))
                .isEqualTo(new TextRange.Span(2, 4));
    }

    // ── no rectangle at all ─────────────────────────────────────────────

    @Test
    @DisplayName("a pane that has never been laid out is not blamed on a fold")
    void notLaidOutIsItsOwnMessage() {
        // Both states give no rectangle, and a caller sent looking for a fold that is not there
        // has been told something confidently wrong.
        String message = TextRange.noRectangleMessage(new TextRange.Span(0, 10), new Rectangle(0, 0, 0, 0));
        assertThat(message).contains("not been laid out").contains("waitIdle");
        assertThat(message).doesNotContain("fold");
    }

    @Test
    @DisplayName("a laid-out pane with no rectangle is a collapsed fold, and says how to fix it")
    void aFoldSaysExpandIt() {
        String message = TextRange.noRectangleMessage(new TextRange.Span(0, 10), new Rectangle(0, 0, 500, 500));
        assertThat(message).contains("collapsed fold").contains("Expand it first");
    }

    @Test
    @DisplayName("the wire name is one word, never a boolean")
    void wireNames() {
        assertThat(TextRange.Visibility.VISIBLE.wire()).isEqualTo("visible");
        assertThat(TextRange.Visibility.PARTLY.wire()).isEqualTo("partly");
        assertThat(TextRange.Visibility.HIDDEN.wire()).isEqualTo("hidden");
    }
}
