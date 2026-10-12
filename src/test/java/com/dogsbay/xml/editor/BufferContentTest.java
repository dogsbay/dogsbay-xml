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

import javax.swing.text.Position;
import javax.swing.text.Segment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterisation tests for the editor's document content store.
 *
 * <p>Written against the forked implementation <em>before</em> it was rebuilt on
 * the public {@code javax.swing.text.GapContent}, so the replacement could be
 * held to identical observable behaviour. See
 * {@code plans/sun-code-investigation.md}.
 *
 * <p>The gap is what makes this worth pinning: content is stored as a char array
 * with a movable hole at the insertion point, so every accessor has a
 * before-the-gap and an after-the-gap path, and edits move the hole. The tests
 * deliberately force the gap into different places and read across it.
 */
class BufferContentTest {

    private static BufferContent contentOf(String text) throws Exception {
        BufferContent content = new BufferContent();
        content.insertString(0, text);
        return content;
    }

    /** Everything an AbstractDocument.Content holds carries an implicit trailing \n. */
    private static int lengthOfEmpty() {
        return new BufferContent().length();
    }

    // ── Length ──────────────────────────────────────────────────────────

    @Test
    void emptyContentStillHasTheImplicitTrailingNewline() {
        assertEquals(1, lengthOfEmpty(),
                "AbstractDocument.Content always holds a trailing newline");
    }

    @Test
    void lengthCountsInsertedText() throws Exception {
        assertEquals(6, contentOf("hello").length(), "5 chars + the implicit newline");
    }

    // ── getChar: the scanner's hot path ─────────────────────────────────

    @Test
    void readsCharactersBeforeTheGap() throws Exception {
        BufferContent content = contentOf("hello");

        assertEquals('h', content.getChar(0));
        assertEquals('e', content.getChar(1));
        assertEquals('o', content.getChar(4));
    }

    @Test
    void readsAcrossTheGapAfterAnInsertInTheMiddle() throws Exception {
        BufferContent content = contentOf("hello world");
        // Inserting mid-string parks the gap at the insertion point, so the
        // characters after it live past the hole in the backing array.
        content.insertString(5, " there");

        assertEquals("hello there world", content.getString(0, 17));
        assertEquals('t', content.getChar(6));
        assertEquals('w', content.getChar(12), "read from beyond the gap");
    }

    @Test
    void readsCorrectlyAfterTheGapMovesBackwards() throws Exception {
        BufferContent content = contentOf("abcdefghij");
        content.insertString(8, "XY");    // gap near the end
        content.insertString(1, "Z");     // now force it back to the front

        assertEquals("aZbcdefghXYij", content.getString(0, 13));
        assertEquals('Z', content.getChar(1));
        assertEquals('X', content.getChar(9));
        assertEquals('j', content.getChar(12));
    }

    /**
     * Out-of-range reads return '\0' rather than throwing. Callers on the paint
     * path (XMLSegment.indexOf via BookmarkMargin) can compute a transiently
     * invalid index during an edit, and letting that escape kills Swing's paint
     * loop. Downstream indexOf-style callers read '\0' as "not my character".
     */
    @Test
    void outOfRangeReadsAreBenign() throws Exception {
        BufferContent content = contentOf("hello");

        assertEquals('\0', content.getChar(-1));
        assertEquals('\0', content.getChar(Integer.MIN_VALUE));
        assertEquals('\0', content.getChar(content.length()));
        assertEquals('\0', content.getChar(9999));
    }

    // ── Content interface ───────────────────────────────────────────────

    @Test
    void removesText() throws Exception {
        BufferContent content = contentOf("hello world");
        content.remove(5, 6);

        assertEquals("hello", content.getString(0, 5));
        assertEquals(6, content.length());
    }

    @Test
    void getStringSpansTheGap() throws Exception {
        BufferContent content = contentOf("hello world");
        content.insertString(5, "-gap-");

        assertEquals("hello-gap- world", content.getString(0, 16));
        assertEquals("o-gap- w", content.getString(4, 8), "a slice straddling the hole");
    }

    @Test
    void getCharsFillsASegmentAcrossTheGap() throws Exception {
        BufferContent content = contentOf("hello world");
        content.insertString(5, "-gap-");
        Segment segment = new Segment();

        content.getChars(4, 8, segment);

        assertEquals(8, segment.count);
        assertEquals("o-gap- w", new String(segment.array, segment.offset, segment.count));
    }

    @Test
    void getCharsAtTheStartAndEnd() throws Exception {
        BufferContent content = contentOf("abcdef");
        Segment segment = new Segment();

        content.getChars(0, 3, segment);
        assertEquals("abc", new String(segment.array, segment.offset, segment.count));

        content.getChars(3, 3, segment);
        assertEquals("def", new String(segment.array, segment.offset, segment.count));
    }

    @Test
    void rejectsAnOutOfRangeGetString() throws Exception {
        BufferContent content = contentOf("hello");

        assertThrows(javax.swing.text.BadLocationException.class,
                () -> content.getString(0, 999));
    }

    // ── Positions ───────────────────────────────────────────────────────

    @Test
    void positionsTrackInsertionsBeforeThem() throws Exception {
        BufferContent content = contentOf("hello world");
        Position position = content.createPosition(6);   // the 'w'
        assertEquals(6, position.getOffset());

        content.insertString(0, ">> ");

        assertEquals(9, position.getOffset(), "shifted by the inserted text");
    }

    @Test
    void positionsIgnoreInsertionsAfterThem() throws Exception {
        BufferContent content = contentOf("hello world");
        Position position = content.createPosition(2);

        content.insertString(8, "XX");

        assertEquals(2, position.getOffset());
    }

    @Test
    void positionsTrackRemovals() throws Exception {
        BufferContent content = contentOf("hello world");
        Position position = content.createPosition(6);

        content.remove(0, 3);

        assertEquals(3, position.getOffset());
    }

    @Test
    void positionAtZeroStaysAtZero() throws Exception {
        BufferContent content = contentOf("hello");
        Position position = content.createPosition(0);

        content.insertString(0, "abc");

        assertEquals(0, position.getOffset(),
                "offset 0 is sticky — text inserted there goes after it");
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    /**
     * The fork's cleanup() nulled its mark structures, which left the store
     * unusable — any later edit NPE'd. GapContent reclaims marks itself, so
     * cleanup() is now a no-op and the store stays usable. Asserting only that it
     * does not throw would pin nothing (an empty method never throws), so this
     * pins the behaviour that actually changed.
     */
    @Test
    void theStoreRemainsUsableAfterCleanup() throws Exception {
        BufferContent content = contentOf("hello");
        Position position = content.createPosition(3);

        content.cleanup();
        content.cleanup();   // repeat: no half-torn-down state

        content.insertString(5, " world");
        assertEquals("hello world", content.getString(0, 11));
        assertEquals('w', content.getChar(6));
        assertEquals(3, position.getOffset(), "positions still track after cleanup");
    }

    /**
     * Documents a contract change from the fork: it rejected an insert at exactly
     * length(), GapContent allows it. No caller does this — every append uses
     * doc.getLength(), which is length() - 1 — and permitting it is stock Swing
     * behaviour, so this is parity-restoring rather than a regression. Pinned so
     * the difference is visible rather than folklore.
     */
    @Test
    void insertingAtLengthIsAcceptedAndBeyondItIsNot() throws Exception {
        BufferContent content = contentOf("hello");

        assertDoesNotThrow(() -> content.insertString(content.length(), "x"),
                "the fork threw here; GapContent accepts it");
        assertThrows(javax.swing.text.BadLocationException.class,
                () -> content.insertString(content.length() + 1, "x"));
        assertThrows(javax.swing.text.BadLocationException.class,
                () -> content.insertString(-1, "x"));
    }

    @Test
    void survivesManyEditsInBothDirections() throws Exception {
        BufferContent content = new BufferContent();
        StringBuilder expected = new StringBuilder();

        for (int i = 0; i < 200; i++) {
            int at = (i * 7) % (expected.length() + 1);
            String chunk = "[" + i + "]";
            content.insertString(at, chunk);
            expected.insert(at, chunk);
        }
        for (int i = 0; i < 50; i++) {
            int at = (i * 3) % Math.max(1, expected.length() - 4);
            content.remove(at, 2);
            expected.delete(at, at + 2);
        }

        assertEquals(expected.toString(), content.getString(0, expected.length()));
        for (int i = 0; i < expected.length(); i++) {
            assertEquals(expected.charAt(i), content.getChar(i), "char at " + i);
        }
    }
}
