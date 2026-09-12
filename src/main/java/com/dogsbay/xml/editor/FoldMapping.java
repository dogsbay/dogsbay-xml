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

/**
 * The line-index arithmetic behind code folding.
 *
 * <p>This is DogsBay's own logic, and until now it lived twice — duplicated
 * verbatim as private methods in {@code FoldingPlainView} and
 * {@code FoldingWrappedPlainView}, both of which need a live editor and so could
 * not be tested. Pulling it out makes it testable headlessly and gives the
 * reimplementation planned in {@code plans/sun-code-investigation.md} a fixed
 * reference to work against.
 *
 * <p><b>The model.</b> A {@link Fold} spans a start and end line and hides the
 * lines <em>strictly between</em> them — {@code end - start - 1} lines. Both the
 * start line (carrying the collapsed marker) and the end line stay visible.
 * {@code FoldingMargin} keeps its folds sorted by start line and binary-searches
 * them, and every method here relies on that ordering.
 *
 * <p><b>Two index spaces.</b> <em>Element</em> indices count every line in the
 * document; <em>visible</em> indices count only the lines currently on screen.
 * With no folds the two coincide.
 *
 * <p>Methods take the fold vector directly rather than a copied list: they are
 * called from paint paths, and this is a hot enough position that allocating per
 * call would be a poor trade for tidiness.
 */
final class FoldMapping {

    private FoldMapping() {
    }

    /**
     * Total lines hidden by {@code folds}.
     *
     * @param folds the collapsed folds, sorted by start line; may be null
     * @return the number of hidden lines, 0 when there are none
     */
    static int hiddenLineCount(Vector folds) {
        if (folds == null) {
            return 0;
        }
        int hidden = 0;
        for (int i = 0; i < folds.size(); i++) {
            Fold fold = (Fold) folds.elementAt(i);
            hidden += (fold.getEnd() - fold.getStart()) - 1;
        }
        return hidden;
    }

    /**
     * How many lines are on screen.
     *
     * @param totalLines the document's total line count
     * @param folds the collapsed folds; may be null
     * @return {@code totalLines} minus the hidden lines
     */
    static int visibleLineCount(int totalLines, Vector folds) {
        return totalLines - hiddenLineCount(folds);
    }

    /**
     * Whether a line is hidden inside a collapsed fold.
     *
     * @param line an element-space line index
     * @param folds the collapsed folds; may be null
     * @return true when some fold hides it
     */
    static boolean isHidden(int line, Vector folds) {
        if (folds == null) {
            return false;
        }
        for (int i = 0; i < folds.size(); i++) {
            if (((Fold) folds.elementAt(i)).contains(line)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts a visible-space index to an element-space index, by adding back
     * the lines each earlier fold hides.
     *
     * @param visibleIndex an index counting only on-screen lines
     * @param folds the collapsed folds, sorted by start line; may be null
     * @return the corresponding index over all lines
     */
    static int toElementIndex(int visibleIndex, Vector folds) {
        if (folds == null) {
            return visibleIndex;
        }
        int index = visibleIndex;
        for (int i = 0; i < folds.size(); i++) {
            Fold fold = (Fold) folds.elementAt(i);

            // The comparison walks with the index as it grows, so a fold that has
            // been pushed past by earlier expansions still counts. Folds are
            // sorted, so the first one at or beyond the index ends the walk.
            if (fold.getStart() < index) {
                index = index + ((fold.getEnd() - fold.getStart()) - 1);
            } else {
                break;
            }
        }
        return index;
    }

    /**
     * Converts an element-space index to a visible-space index, by subtracting
     * the lines each earlier fold hides.
     *
     * <p>Note the asymmetry with {@link #toElementIndex}: the fold comparison uses
     * the <em>original</em> index throughout rather than the running one, because
     * the input is already in element space and does not shift as folds are
     * accounted for.
     *
     * @param elementIndex an index counting all lines
     * @param folds the collapsed folds, sorted by start line; may be null
     * @return the corresponding index over visible lines only
     */
    static int toVisibleIndex(int elementIndex, Vector folds) {
        if (folds == null) {
            return elementIndex;
        }
        int index = elementIndex;
        for (int i = 0; i < folds.size(); i++) {
            Fold fold = (Fold) folds.elementAt(i);

            if (fold.getStart() < elementIndex) {
                index = index - ((fold.getEnd() - fold.getStart()) - 1);
            } else {
                break;
            }
        }
        return index;
    }
}
