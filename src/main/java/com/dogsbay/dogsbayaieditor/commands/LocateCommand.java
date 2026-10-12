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

import com.dogsbay.dogsbayaieditor.commands.results.ElementHit;

/**
 * Where something is on screen, found by the text a reader can see on it.
 *
 * <p>{@link ClickButtonCommand} did this for buttons and pressed them. A recording's other clicks — a tree
 * row, a menu, a field beside its label, a tab, an option in a drop-down — were positions measured from a
 * screenshot, which move when a window manager draws a title bar, when the theme or the font changes, and when
 * a dialog gains a line. This names them instead, and answers with where to point.
 *
 * <p>It changes nothing, so it needs no permission to drive: a caller that then clicks is doing its own
 * driving by its own means, and a screenshot already shows the same thing.
 *
 * @param text   the text on the thing, matched as a button's label is — trimmed, ignoring case and a trailing
 *               ellipsis, and ignoring the colon a dialog puts after a field's label
 * @param what   which kind of thing (see {@link UiTargets.Kind}). Required: the text alone is ambiguous, and
 *               choosing between a row and a label by some fixed order would be a coin toss
 * @param window the title of the window to look in; null for the active one
 * @param nth    which one, when a name fits more than one thing — 1-based, and 0 for "there should only be
 *               one". Two rows of the same text is ordinary in a list or a table, so a caller that meets the
 *               ambiguity has to be able to get past it
 * @param column for {@code what: "cell"}, the column's heading
 * @param row    for {@code what: "cell"}, which row — a 1-based number, or {@code last}. Its own parameter
 *               rather than {@code text}, because a cell is reached by where it is: the ones a recording
 *               clicks are empty, and an empty cell has no text to be named by
 * @param offset for {@code what: "text"}, how many characters along from the start of the match to aim, so a
 *               cue can point at one word inside a longer phrase
 */
public record LocateCommand(String text, String what, String window, int nth,
        String column, String row, int offset)
        implements Command<ElementHit>, ReadOnlyCommand {
}
