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

package com.dogsbay.xml.editor;

import java.util.function.IntPredicate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for where the caret lands when a validation result is clicked.
 *
 * <p>Regression: clicking an error reported on line 7 moved the caret to line 6,
 * and one on line 4 to line 2. Those errors carry no column — Schematron rules
 * and the DITA project checks report {@code Col -1} — and the sentinel was being
 * treated as a real 1-based column, so the offset came out as
 * {@code lineStart + (-1 - 1)}: two characters before the line. That is the end
 * of the previous line, or two lines back when the line between was blank, which
 * is exactly the 7 → 6 and 4 → 2 the user saw.
 */
class ErrorSelectionTest {

    /** Line 4 of a document, occupying offsets 100..139. */
    private static final int LINE_START = 100;
    private static final int LINE_END = 140;

    /**
     * A predicate over a real line, so the *argument* matters.
     *
     * <p>Constant predicates hid a whole bug class: checking {@code pos - 1} or
     * {@code pos + 1} — the wrong character, exactly what this fix is about —
     * passed every test.
     */
    private static IntPredicate lineContent(String line) {
        return pos -> {
            int index = pos - LINE_START - 1;   // the character *before* pos
            return index >= 0 && index < line.length()
                    && Character.isWhitespace(line.charAt(index));
        };
    }

    /** Nothing is whitespace — the "caret sits on a tag character" case. */
    private static final IntPredicate NO_WHITESPACE = pos -> false;

    /** Everything is whitespace — the indented-line case. */
    private static final IntPredicate ALL_WHITESPACE = pos -> true;

    // ── No column reported ──────────────────────────────────────────────

    @Test
    void placesTheCaretOnTheLineWhenTheColumnIsTheMinusOneSentinel() {
        int[] range = EditorPanel.errorSelectionRange(LINE_START, LINE_END, -1, NO_WHITESPACE);

        assertArrayEquals(new int[] {LINE_START, LINE_START}, range,
                "caret only: these rows focus the editor on a single click, so a "
                        + "line-wide selection would let one keystroke replace the line");
    }

    @Test
    void neverLandsBeforeTheLineWhenTheColumnIsAbsent() {
        for (int column : new int[] {-1, 0}) {
            int[] range = EditorPanel.errorSelectionRange(
                    LINE_START, LINE_END, column, NO_WHITESPACE);

            assertTrue(range[0] >= LINE_START,
                    "column " + column + " put the selection before the line — the bug");
        }
    }

    // ── A real column ───────────────────────────────────────────────────

    @Test
    void highlightsTheCharacterBeforeTheReportedColumn() {
        // Column 11 on a line starting at 100 -> offset 110.
        int[] range = EditorPanel.errorSelectionRange(LINE_START, LINE_END, 11, NO_WHITESPACE);

        assertArrayEquals(new int[] {109, 110}, range);
    }

    @Test
    void highlightsForwardWhenThePrecedingCharacterIsWhitespace() {
        int[] range = EditorPanel.errorSelectionRange(LINE_START, LINE_END, 11, ALL_WHITESPACE);

        assertArrayEquals(new int[] {110, 111}, range);
    }

    /**
     * Pins which character the whitespace test inspects. "  &lt;p&gt;" has spaces at
     * indices 0-1, so column 3 (offset 102, preceded by index 1 = space) takes the
     * forward branch while column 5 (preceded by '&lt;') takes the backward one.
     */
    @Test
    void inspectsTheCharacterImmediatelyBeforeTheOffset() {
        IntPredicate line = lineContent("  <p>text</p>");

        assertArrayEquals(new int[] {102, 103},
                EditorPanel.errorSelectionRange(LINE_START, LINE_END, 3, line),
                "preceded by a space -> select forward");
        assertArrayEquals(new int[] {103, 104},
                EditorPanel.errorSelectionRange(LINE_START, LINE_END, 5, line),
                "preceded by '<' -> select the character before");
    }

    @Test
    void columnOneHighlightsForwardRatherThanSelectingNothing() {
        int[] range = EditorPanel.errorSelectionRange(LINE_START, LINE_END, 1, NO_WHITESPACE);

        assertArrayEquals(new int[] {LINE_START, LINE_START + 1}, range,
                "no preceding character on this line — and it must not reach back "
                        + "onto the previous line, which is what the old code did");
    }

    @Test
    void aColumnPastTheEndOfTheLineIsClampedToIt() {
        // Validators report columns against their own reformatting of the source,
        // which can overshoot the line as it appears in the editor.
        int[] range = EditorPanel.errorSelectionRange(LINE_START, LINE_END, 9999, NO_WHITESPACE);

        assertTrue(range[1] <= LINE_END, "selection must stay on the line");
        assertEquals(LINE_END - 1, range[0]);
    }

    @Test
    void theForwardBranchIsAlsoClampedToTheLineEnd() {
        // Whitespace branch at the very end of the line: without the clamp the
        // selection would run onto the following line.
        int[] range = EditorPanel.errorSelectionRange(LINE_START, LINE_END, 9999, ALL_WHITESPACE);

        assertArrayEquals(new int[] {LINE_END, LINE_END}, range);
    }

    @Test
    void handlesAnEmptyLineWithAColumn() {
        // lineStart == lineEnd: every branch must collapse to the same offset.
        assertArrayEquals(new int[] {200, 200},
                EditorPanel.errorSelectionRange(200, 200, 1, NO_WHITESPACE));
        assertArrayEquals(new int[] {200, 200},
                EditorPanel.errorSelectionRange(200, 200, 1, ALL_WHITESPACE));
    }

    @Test
    void handlesAnEmptyLineWithoutAColumn() {
        int[] range = EditorPanel.errorSelectionRange(200, 200, -1, NO_WHITESPACE);

        assertArrayEquals(new int[] {200, 200}, range, "caret on the line");
    }
}
