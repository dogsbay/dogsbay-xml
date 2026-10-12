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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.swing.JTextArea;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.commands.EditorText.Match;

/**
 * Finding a place in the open document by the words there.
 *
 * <p>Forty-seven of the recordings' clicks put the caret somewhere in the editor by a position measured from
 * a screenshot — each moving with the font, the theme, the window manager, and with every line added above
 * it. The words do not move.
 */
class EditorTextTest {

    @Test
    void findsEveryOccurrenceInTheOrderTheyAppear() {
        String text = "Save the file. Then save the file again. Save the file once more.";

        assertThat(EditorText.occurrences(text, "Save the file"))
                .containsExactly(new Match(0, 13), new Match(41, 54));
    }

    @Test
    void matchesExactlyAsWritten() {
        // A cue sheet quotes the words the lesson puts on screen; matching a different capitalisation would
        // aim at a line the reader is not being shown
        assertThat(EditorText.occurrences("Save the File", "save the file")).isEmpty();
    }

    @Test
    void overlappingOccurrencesAreNotTwoPlacesToClick() {
        assertThat(EditorText.occurrences("aaaa", "aa")).containsExactly(new Match(0, 2), new Match(2, 4));
    }

    @Test
    void nothingToLookForFindsNothing() {
        assertThat(EditorText.occurrences("some text", "")).isEmpty();
        assertThat(EditorText.occurrences("some text", null)).isEmpty();
        assertThat(EditorText.occurrences(null, "x")).isEmpty();
    }

    @Test
    void anOffsetAimsAtOneCharacterInsideTheMatch() {
        // So a cue can point at one word of a longer phrase
        assertThat(new Match(10, 23).at(5)).isEqualTo(new Match(15, 16));
    }

    @Test
    void anOffsetPastTheEndStaysInsideTheMatch() {
        assertThat(new Match(10, 13).at(99)).isEqualTo(new Match(12, 13));
        assertThat(new Match(10, 13).at(-1)).isEqualTo(new Match(10, 11));
    }

    // ── A line and a column, which is how an XPath reports itself ───────

    private static javax.swing.text.Element rootOf(String text) {
        JTextArea area = new JTextArea(text);
        return area.getDocument().getDefaultRootElement();
    }

    @Test
    void turnsALineAndColumnIntoAnOffset() {
        //       0123456789
        String text = "first\nsecond\nthird";

        assertThat(EditorText.offsetOf(rootOf(text), 1, 1)).isEqualTo(0);
        assertThat(EditorText.offsetOf(rootOf(text), 2, 1)).isEqualTo(6);
        assertThat(EditorText.offsetOf(rootOf(text), 2, 3)).isEqualTo(8);
        assertThat(EditorText.offsetOf(rootOf(text), 3, 1)).isEqualTo(13);
    }

    @Test
    void aColumnPastTheEndOfItsLineStaysOnThatLine() {
        // Aiming at the newline would measure the start of the next line instead
        String text = "ab\ncd";

        assertThat(EditorText.offsetOf(rootOf(text), 1, 99)).isEqualTo(2);
    }

    @Test
    void aLineBeyondTheDocumentIsClampedRatherThanThrowing() {
        String text = "only one line";

        assertThat(EditorText.offsetOf(rootOf(text), 99, 1)).isEqualTo(0);
        assertThat(EditorText.offsetOf(rootOf(text), 0, 1)).isEqualTo(0);
        assertThat(EditorText.offsetOf(null, 1, 1)).isZero();
    }

    // ── Which of the places found, and why not ──────────────────────────

    private static java.util.List<EditorText.Match> some(int howMany) {
        java.util.List<EditorText.Match> matches = new java.util.ArrayList<>();
        for (int i = 0; i < howMany; i++) {
            matches.add(new Match(i * 10, i * 10 + 4));
        }
        return matches;
    }

    @Test
    void oneMatchIsTheOneToAimAt() {
        var outcome = EditorText.resolve(some(1), "Save", 0, 0);

        assertThat(((EditorText.Outcome.Found) outcome).match()).isEqualTo(new Match(0, 4));
    }

    @Test
    void nothingFoundSaysSo() {
        assertThat(((EditorText.Outcome.Refused) EditorText.resolve(java.util.List.of(), "Save", 0, 0))
                .message()).isEqualTo("Nothing in this document reads 'Save'.");
    }

    @Test
    void severalMatchesAndNoNthIsRefusedRatherThanAimedAtTheFirst() {
        // A cue that means the third occurrence and gets the first does not fail, it records the wrong thing
        assertThat(((EditorText.Outcome.Refused) EditorText.resolve(some(3), "Save", 0, 0)).message())
                .contains("3 places").contains("Ask for one of them with 'nth'");
    }

    @Test
    void nthPicksAmongThem() {
        assertThat(((EditorText.Outcome.Found) EditorText.resolve(some(3), "Save", 2, 0)).match())
                .isEqualTo(new Match(10, 14));
    }

    @Test
    void anNthBeyondWhatIsThereCountsWhatThereIs() {
        assertThat(((EditorText.Outcome.Refused) EditorText.resolve(some(2), "Save", 5, 0)).message())
                .contains("Asked for 5").contains("only 2.");
        assertThat(((EditorText.Outcome.Refused) EditorText.resolve(some(1), "Save", 2, 0)).message())
                .contains("only 1.");
    }

    @Test
    void anOffsetIsAppliedToTheMatchThatWasChosen() {
        assertThat(((EditorText.Outcome.Found) EditorText.resolve(some(3), "Save", 2, 2)).match())
                .isEqualTo(new Match(12, 13));
    }

    // ── Which row of a table ────────────────────────────────────────────

    @Test
    void aRowIsANumberCountingFromOne() {
        assertThat(EditorText.rowNumber("3")).isEqualTo(3);
        assertThat(EditorText.rowNumber(" 3 ")).isEqualTo(3);
    }

    @Test
    void lastIsTheLastRow() {
        assertThat(EditorText.rowNumber("last")).isEqualTo(-1);
        assertThat(EditorText.rowNumber("LAST")).isEqualTo(-1);
    }

    @Test
    void aRowThatIsNeitherSaysWhatItShouldBe() {
        assertThatThrownBy(() -> EditorText.rowNumber("lsat"))
                .hasMessageContaining("a number, or 'last'").hasMessageContaining("lsat");
        assertThatThrownBy(() -> EditorText.rowNumber(null))
                .hasMessageContaining("Which row?");
        assertThatThrownBy(() -> EditorText.rowNumber("  "))
                .hasMessageContaining("Which row?");
    }

    // ── Which way to scroll ─────────────────────────────────────────────

    @Test
    void anUnrecognisedDirectionIsNotQuietlyTheOpposite() {
        // The one parameter whose whole job is a direction must not silently do the other thing
        assertThat(EditorText.scrollsToEnd("bottom")).isNull();
        assertThat(EditorText.scrollsToEnd("END ")).isTrue();
        assertThat(EditorText.scrollsToEnd("start")).isFalse();
        assertThat(EditorText.scrollsToEnd(null)).as("the end is what a caller means by default").isTrue();
        assertThat(EditorText.scrollsToEnd("")).isTrue();
    }
}
