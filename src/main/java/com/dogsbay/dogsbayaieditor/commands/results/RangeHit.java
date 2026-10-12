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

import java.util.List;

/**
 * Where a stretch of the open document is on screen.
 *
 * <p>Deliberately not an {@link ElementHit}. That record is one box, which is right for the
 * one character {@code locate} aims at and wrong for a stretch that runs over several lines:
 * a caller drawing an annotation needs one rectangle per visual line. Overloading
 * {@code ElementHit} would mean a result whose shape depends on which kind was asked for,
 * and a caller that switches on shape is a caller that breaks quietly.
 *
 * @param start      where the stretch begins, as a character offset
 * @param end        where it ends, as a character offset, not included
 * @param text       the characters in it
 * @param visibility {@code visible}, {@code partly} or {@code hidden} — one word, always
 * @param window     the title of the window the document is in
 * @param boxes      one box per visual line, in screen coordinates, in reading order; empty
 *                   when none of the stretch is on screen
 */
public record RangeHit(int start, int end, String text, String visibility, String window,
        List<ScreenBox> boxes) {
}
