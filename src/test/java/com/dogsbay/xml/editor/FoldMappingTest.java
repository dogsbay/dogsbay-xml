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

import java.util.Vector;

import javax.swing.text.Element;
import javax.swing.text.PlainDocument;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterisation tests for the code-folding line arithmetic.
 *
 * <p>This is the logic the Sun-derived folding views actually contribute, and it
 * had no coverage at all: it lived as duplicated private methods inside two view
 * classes that need a live editor to instantiate. It is pinned here <em>before</em>
 * those views are reimplemented (stage 3 of
 * {@code plans/sun-code-investigation.md}), so the replacement has a fixed
 * reference.
 *
 * <p>Folds are built from a real {@link PlainDocument}, since {@link Fold}
 * derives its line numbers from {@code Element.getParentElement().getElementIndex}.
 * No display is required.
 *
 * <p>A fold spanning lines {@code [start, end]} hides the lines strictly between
 * them; both ends stay visible.
 */
class FoldMappingTest {

    /** A document of {@code lines} numbered lines, so indices are easy to read. */
    private static PlainDocument document(int lines) throws Exception {
        PlainDocument doc = new PlainDocument();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < lines; i++) {
            text.append("line ").append(i).append('\n');
        }
        doc.insertString(0, text.toString(), null);
        return doc;
    }

    /** Folds over {@code doc}, given as start/end line pairs, in sorted order. */
    private static Vector foldsOf(PlainDocument doc, int... startEndPairs) {
        Element root = doc.getDefaultRootElement();
        Vector folds = new Vector();
        for (int i = 0; i < startEndPairs.length; i += 2) {
            folds.addElement(new Fold(root.getElement(startEndPairs[i]),
                    root.getElement(startEndPairs[i + 1])));
        }
        return folds;
    }

    // ── The model ───────────────────────────────────────────────────────

    @Test
    void aFoldHidesTheLinesStrictlyBetweenItsEnds() throws Exception {
        Vector folds = foldsOf(document(20), 5, 10);

        assertFalse(FoldMapping.isHidden(5, folds), "the start line carries the marker");
        assertTrue(FoldMapping.isHidden(6, folds));
        assertTrue(FoldMapping.isHidden(9, folds));
        assertFalse(FoldMapping.isHidden(10, folds), "the end line stays visible");
        assertFalse(FoldMapping.isHidden(11, folds));
    }

    @Test
    void hiddenCountIsTheSpanMinusItsTwoVisibleEnds() throws Exception {
        assertEquals(4, FoldMapping.hiddenLineCount(foldsOf(document(20), 5, 10)));
        assertEquals(0, FoldMapping.hiddenLineCount(foldsOf(document(20), 5, 6)),
                "adjacent lines hide nothing");
        assertEquals(0, FoldMapping.hiddenLineCount(new Vector()));
        assertEquals(0, FoldMapping.hiddenLineCount(null));
    }

    @Test
    void severalFoldsAccumulate() throws Exception {
        Vector folds = foldsOf(document(40), 3, 6, 10, 20, 25, 27);

        assertEquals(2 + 9 + 1, FoldMapping.hiddenLineCount(folds));
        assertEquals(40 - 12, FoldMapping.visibleLineCount(40, folds));
    }

    // ── Index conversion ────────────────────────────────────────────────

    @Test
    void indicesAreUnchangedWithNoFolds() throws Exception {
        Vector none = new Vector();

        for (int i : new int[] {0, 1, 7, 39}) {
            assertEquals(i, FoldMapping.toElementIndex(i, none));
            assertEquals(i, FoldMapping.toVisibleIndex(i, none));
            assertEquals(i, FoldMapping.toElementIndex(i, null));
            assertEquals(i, FoldMapping.toVisibleIndex(i, null));
        }
    }

    @Test
    void indicesBeforeAFoldAreUnaffected() throws Exception {
        Vector folds = foldsOf(document(20), 5, 10);

        assertEquals(0, FoldMapping.toElementIndex(0, folds));
        assertEquals(3, FoldMapping.toElementIndex(3, folds));
        assertEquals(3, FoldMapping.toVisibleIndex(3, folds));
    }

    @Test
    void indicesAfterAFoldShiftByItsHiddenLines() throws Exception {
        // Fold [5,10] hides 4 lines. Visible order: 0..5, then 10, 11, ...
        Vector folds = foldsOf(document(20), 5, 10);

        assertEquals(10, FoldMapping.toElementIndex(6, folds),
                "the 7th visible line is element line 10");
        assertEquals(11, FoldMapping.toElementIndex(7, folds));
        assertEquals(6, FoldMapping.toVisibleIndex(10, folds));
        assertEquals(7, FoldMapping.toVisibleIndex(11, folds));
    }

    @Test
    void conversionsRoundTripForVisibleLines() throws Exception {
        Vector folds = foldsOf(document(40), 3, 6, 10, 20);
        int visible = FoldMapping.visibleLineCount(40, folds);

        for (int v = 0; v < visible; v++) {
            int element = FoldMapping.toElementIndex(v, folds);

            assertFalse(FoldMapping.isHidden(element, folds),
                    "visible index " + v + " mapped onto hidden line " + element);
            assertEquals(v, FoldMapping.toVisibleIndex(element, folds),
                    "round trip failed for visible index " + v);
        }
    }

    @Test
    void conversionIsMonotonic() throws Exception {
        Vector folds = foldsOf(document(40), 3, 6, 10, 20, 25, 27);
        int visible = FoldMapping.visibleLineCount(40, folds);

        int previous = -1;
        for (int v = 0; v < visible; v++) {
            int element = FoldMapping.toElementIndex(v, folds);

            assertTrue(element > previous,
                    "element indices must strictly increase: " + element + " after " + previous);
            previous = element;
        }
    }

    @Test
    void handlesAFoldStartingAtTheFirstLine() throws Exception {
        Vector folds = foldsOf(document(20), 0, 5);

        assertEquals(0, FoldMapping.toElementIndex(0, folds), "line 0 is still line 0");
        assertEquals(5, FoldMapping.toElementIndex(1, folds));
        assertEquals(1, FoldMapping.toVisibleIndex(5, folds));
        assertEquals(16, FoldMapping.visibleLineCount(20, folds));
    }

    @Test
    void handlesBackToBackFolds() throws Exception {
        // [3,6] and [6,9] share line 6, which stays visible for both.
        Vector folds = foldsOf(document(20), 3, 6, 6, 9);

        assertEquals(4, FoldMapping.hiddenLineCount(folds));
        assertFalse(FoldMapping.isHidden(6, folds), "the shared boundary stays visible");
        assertTrue(FoldMapping.isHidden(4, folds));
        assertTrue(FoldMapping.isHidden(7, folds));
    }
}
