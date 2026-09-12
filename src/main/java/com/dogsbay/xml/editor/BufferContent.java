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

import javax.swing.text.GapContent;

/**
 * The editor's document content store: a {@link GapContent} plus the direct
 * character access the XML scanner needs.
 *
 * <p>This was previously a fork of {@code javax.swing.text.GapContent} and its
 * package-private {@code GapVector} superclass — 1,259 lines carrying Sun's
 * proprietary header, which could not be published. The fork existed to reach
 * the backing array without copying, and that turned out to be unnecessary:
 * {@code GapContent} is public, and the protected accessors it inherits from the
 * package-private {@code GapVector} are reachable from a subclass in any package.
 * So the whole store is inherited, and only {@link #getChar} is added.
 *
 * <p>See {@code plans/sun-code-investigation.md}.
 *
 * <p><b>Gap layout.</b> Content is a char array with a movable hole at the last
 * edit point, so an offset past the gap start sits {@code gapEnd - gapStart}
 * further along the array than its document offset suggests. {@link #getChar}
 * is the only place here that has to know that.
 */
public class BufferContent extends GapContent {

    /**
     * Creates a content store with a default initial capacity.
     */
    public BufferContent() {
        super();
    }

    /**
     * Creates a content store with room for {@code initialLength} characters
     * before the backing array has to grow.
     *
     * @param initialLength the initial capacity
     */
    public BufferContent(int initialLength) {
        super(initialLength);
    }

    /**
     * Reads a single character by document offset, without copying into a
     * {@code Segment}.
     *
     * <p>This is the scanner's hot path — tag and entity recognition walk the
     * document one character at a time — so it reads the backing array directly
     * rather than going through {@code getChars}.
     *
     * <p>An out-of-range index yields {@code '\0'} rather than throwing.
     * Callers on the paint path ({@code XMLSegment.indexOf} via
     * {@code BookmarkMargin.paintComponent}) can compute a transiently invalid
     * index while an edit is in flight, and an exception escaping there kills
     * Swing's paint loop. Downstream indexOf-style callers already treat
     * {@code '\0'} as "not the character I am looking for" and return -1. See
     * {@code docs-dev/known-issues.md}.
     *
     * @param index the document offset
     * @return the character at {@code index}, or {@code '\0'} if out of range
     */
    public char getChar(int index) {
        if (index < 0 || index >= length()) {
            return '\0';
        }

        char[] array = (char[]) getArray();

        int gapStart = getGapStart();
        int gapEnd = getGapEnd();

        if (index >= gapStart) {
            index = index + (gapEnd - gapStart);
        }

        return array[index];
    }

    /**
     * Releases what the store no longer needs when a document is closed.
     *
     * <p>A no-op: {@code GapContent} owns its own position marks and prunes them
     * as they are collected, which is exactly what the fork's hand-rolled mark
     * vector had to do by hand. Kept so callers do not have to care which
     * implementation is underneath.
     */
    public void cleanup() {
        // nothing to release — GapContent manages its own marks
    }
}
