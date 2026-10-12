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
 * Scroll something into view, and say where it ended up.
 *
 * <p>The cure for {@link LocateCommand}'s one refusal. "Scrolled out of view" is the only reason {@code
 * locate} fails for something that is really there, so the two make a pair: ask where it is, and if that is
 * the answer, reveal it and ask again. It replaces the scroll-arrow clicks a recording used to make, which
 * were positions like all the others.
 *
 * <p>It scrolls and nothing else. No caret is moved, no selection changed, no document touched — a recording
 * that reveals and then clicks must show the pointer travelling, and a caret that had already jumped there
 * would give the game away. Scrolling changes no document and no setting, which is why this needs no
 * {@code --allow-driving}, on the same grounds as showing a panel.
 *
 * @param what   the kind to reveal, as {@code locate} names them, or {@code panel}
 * @param text   the text on it
 * @param window the window to look in; null for the active one
 * @param nth    which one, when the name fits more than one
 * @param offset for {@code what: "text"}, how many characters along from the start of the match to aim
 * @param column for {@code what: "cell"}, the column's heading
 * @param row    for {@code what: "cell"}, which row — a 1-based number, or {@code last}
 * @param panel  for {@code what: "panel"}, which panel to scroll
 * @param to     for {@code what: "panel"}, {@code end} or {@code start}
 * @param align  where it should land in the view: {@code top}, {@code center}, or {@code nearest},
 *               which is the old behaviour of scrolling as little as possible and the default
 */
public record RevealCommand(String what, String text, String window, int nth,
        String column, String row, int offset, String panel, String to, String align)
        implements Command<ElementHit>, ReadOnlyCommand {
}
