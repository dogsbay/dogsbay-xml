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

import java.util.ArrayList;
import java.util.List;

import javax.swing.text.Element;

/**
 * Finding a place in the open document, by the words there rather than by a pixel.
 *
 * <p>Forty-seven of the recordings' remaining clicks put the caret somewhere in the editor, and every one of
 * them was a position measured from a screenshot — so each moved with the font, the theme, the window
 * manager, and with every line added above it. The words do not move.
 *
 * <p>Pure, over the document's text, so the searching is checked without a window.
 */
public final class EditorText {

    private EditorText() {
    }

    /** A stretch of the document, as character offsets. */
    public record Match(int start, int end) {

        /** The single character {@code offset} along from the start, for aiming inside a longer phrase. */
        public Match at(int offset) {
            int from = Math.min(start + Math.max(0, offset), Math.max(start, end - 1));
            return new Match(from, Math.min(from + 1, end));
        }
    }

    /**
     * Every occurrence of {@code needle} in {@code text}, in the order they appear.
     *
     * <p>Exactly as written, including case: a cue sheet quotes the words the lesson puts on screen, and a
     * search that quietly matched a different capitalisation would aim at a line the reader is not being shown.
     * Overlapping occurrences are not sought — the next search begins after the previous match — because two
     * overlapping hits are never two places a person would click.
     */
    public static List<Match> occurrences(String text, String needle) {
        List<Match> found = new ArrayList<>();
        if (text == null || needle == null || needle.isEmpty()) {
            return found;
        }
        int at = text.indexOf(needle);
        while (at >= 0) {
            found.add(new Match(at, at + needle.length()));
            at = text.indexOf(needle, at + needle.length());
        }
        return found;
    }

    /**
     * The character offset of a 1-based line and column, clamped into the document.
     *
     * <p>The inverse of what {@code CaretPositions} does, for the one caller that has a line and column
     * already: an XPath match reports where it is that way.
     */
    public static int offsetOf(Element root, int line, int column) {
        if (root == null) {
            return 0;
        }
        int index = Math.max(0, Math.min(line - 1, root.getElementCount() - 1));
        Element lineElement = root.getElement(index);
        if (lineElement == null) {
            return 0;
        }
        int start = lineElement.getStartOffset();
        // The line's last offset is its newline; aiming at that would measure the start of the next line.
        int lastOnTheLine = Math.max(start, lineElement.getEndOffset() - 1);
        return Math.max(start, Math.min(start + Math.max(0, column - 1), lastOnTheLine));
    }

    /** The place to aim at, or why there is not one. */
    public sealed interface Outcome {

        /** The one place that was asked for. */
        record Found(Match match) implements Outcome {
        }

        /** Nowhere to aim, and what to tell the caller. */
        record Refused(String message) implements Outcome {
        }
    }

    /**
     * The one place {@code nth} and {@code offset} pick out of {@code matches}, or why there is not one.
     *
     * <p>Here rather than in the executor, and over a list rather than a document, because this is the part a
     * caller actually meets. The project learned that the hard way: a menu item that could never be found
     * shipped with sixteen green tests, because the finding was pure and the deciding was not.
     *
     * @param name what the caller asked for, for the message
     * @param nth  1-based; 0 means "there should only be one"
     */
    public static Outcome resolve(List<Match> matches, String name, int nth, int offset) {
        if (matches.isEmpty()) {
            return new Outcome.Refused("Nothing in this document reads '" + name + "'.");
        }
        if (nth >= 1 && nth > matches.size()) {
            return new Outcome.Refused("Asked for " + nth + " of '" + name + "', but this document has "
                    + (matches.size() == 1 ? "only 1." : "only " + matches.size() + "."));
        }
        if (nth < 1 && matches.size() > 1) {
            // Said plainly rather than aimed at the first: a cue that means the third occurrence and gets the
            // first does not fail, it records the wrong thing.
            return new Outcome.Refused(matches.size() + " places in this document read '" + name
                    + "'. Ask for one of them with 'nth'.");
        }
        Match match = matches.get(Math.max(0, nth - 1));
        return new Outcome.Found(offset > 0 ? match.at(offset) : match);
    }

    /**
     * Which row of a table a caller named: 1-based, or -1 for {@code last}.
     *
     * @throws IllegalArgumentException with the words to tell the caller, when it is neither
     */
    public static int rowNumber(String row) {
        String asked = row == null ? "" : row.trim();
        if (asked.isEmpty()) {
            throw new IllegalArgumentException("Which row? Give 'row' a number, counting from 1, or 'last'.");
        }
        if (asked.equalsIgnoreCase("last")) {
            return -1;
        }
        try {
            return Integer.parseInt(asked);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(
                    "A cell's row is a number, or 'last'; '" + asked + "' is neither.");
        }
    }

    /**
     * Whether {@code to} asks for the end or the start, or null when it asks for neither.
     *
     * <p>Null rather than a default: the one parameter whose whole job is a direction must not quietly do the
     * opposite of what it was told, and {@code "bottom"} is the sort of thing a cue sheet says.
     */
    public static Boolean scrollsToEnd(String to) {
        String asked = to == null ? "" : to.trim();
        if (asked.isEmpty() || asked.equalsIgnoreCase("end")) {
            return Boolean.TRUE;
        }
        return asked.equalsIgnoreCase("start") ? Boolean.FALSE : null;
    }
}
