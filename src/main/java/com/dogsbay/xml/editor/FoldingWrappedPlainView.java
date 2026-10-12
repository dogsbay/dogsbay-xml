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

import java.awt.Shape;
import java.util.Vector;

import javax.swing.SwingConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Position;
import javax.swing.text.View;
import javax.swing.text.WrappedPlainView;

/**
 * A soft-wrapping view that can hide the lines inside collapsed folds.
 *
 * <p>Base class for the editor's wrapped views — {@code XmlWrappedView},
 * {@code MarkdownWrappedView} and {@code AsciiDocWrappedView} extend it and
 * supply syntax highlighting through {@code drawUnselectedText} /
 * {@code drawSelectedText}.
 *
 * <p>This was a fork of Sun's {@code WrappedPlainView}, and it dragged forks of
 * {@code BoxView} and {@code CompositeView} along with it. All three carried a
 * proprietary header that could not be published. See
 * {@code plans/sun-code-investigation.md}.
 *
 * <h2>Why the old approach needed those forks</h2>
 *
 * <p>The fork expressed folding by <em>lying about {@code getViewCount()}</em>: it
 * kept one child per document line but reported only the visible count, and
 * remapped {@code getView}/{@code getViewIndexAtPosition} between the two spaces.
 * {@code BoxView} sizes its layout arrays from {@code getViewCount()} while
 * {@code replace()} is called with element-space indices, so the two disagreed —
 * which is why {@code FoldingBoxView} had to widen those arrays to the element
 * count, and why deleting it reintroduced an
 * {@code ArrayIndexOutOfBoundsException} while editing under a collapsed fold.
 *
 * <h2>What it does instead</h2>
 *
 * <p>Indices stay honestly in element space — one child per line, always — and a
 * hidden line is simply laid out with <b>zero height</b>. Nothing has to be
 * remapped, {@code BoxView}'s arrays are correctly sized by construction, and the
 * whole wrapping implementation is inherited rather than copied.
 */
public class FoldingWrappedPlainView extends WrappedPlainView {

    /**
     * Constructs a wrapping view that breaks on character boundaries.
     *
     * @param elem the element this view renders
     */
    public FoldingWrappedPlainView(Element elem) {
        super(elem);
    }

    /**
     * Constructs a wrapping view.
     *
     * @param elem the element this view renders
     * @param wordWrap true to break on word boundaries rather than characters
     */
    public FoldingWrappedPlainView(Element elem, boolean wordWrap) {
        super(elem, wordWrap);
    }

    // ── Fold state ──────────────────────────────────────────────────────

    /** The collapsed folds, or null when there is no folding margin. */
    private Vector folds() {
        java.awt.Container host = getContainer();

        if (host instanceof XmlEditorPane) {
            FoldingMargin margin = ((XmlEditorPane) host).getFoldingMargin();

            // Hiding the margin un-folds the document: FoldingMargin.getFold() is
            // gated on isVisible(), so the old views showed every line once the
            // margin was switched off, even with folds still recorded. Keep that.
            if (margin != null && margin.isVisible()) {
                return margin.getFolds();
            }
        }
        return null;
    }

    /**
     * Whether a line is inside a collapsed fold.
     *
     * <p>The argument is both the child index and the element-space line number —
     * they are the same thing here, which is the point of the design.
     *
     * @param line the line to test
     * @return true when it is hidden
     */
    private boolean isHidden(int line) {
        return FoldMapping.isHidden(line, folds());
    }

    /**
     * Signature of the fold set as of the last layout, used to notice changes.
     * -1 forces the first check to register.
     */
    private long foldSignature = -1;

    /** A cheap value that changes whenever the folds do. */
    private long currentFoldSignature() {
        Vector folds = folds();

        if (folds == null) {
            return 0;
        }
        long signature = folds.size();
        for (int i = 0; i < folds.size(); i++) {
            Fold fold = (Fold) folds.elementAt(i);
            signature = (signature * 31) + fold.getStart();
            signature = (signature * 31) + fold.getEnd();
        }
        return signature;
    }

    /**
     * Discards the cached layout when the fold set has changed.
     *
     * <p>{@code BoxView} caches child offsets and spans and only recomputes when
     * told to. {@code FoldingMargin} announces a fold by calling
     * {@code revalidate()} and {@code repaint()} on the editor, which does not
     * reach that cache — so without this, collapsing a fold left every line laid
     * out at its old position and folding appeared to do nothing.
     */
    private void syncFoldState() {
        // Called from every geometry entry point, not just getPreferredSpan:
        // FoldingMargin.fold() moves the caret (a synchronous modelToView +
        // scrollRectToVisible) BEFORE its revalidate(), so a query arriving
        // between the two would otherwise read the pre-fold layout.
        long signature = currentFoldSignature();

        if (signature != foldSignature) {
            foldSignature = signature;
            layoutChanged(Y_AXIS);
            preferenceChanged(null, false, true);
        }
    }

    // ── Layout ──────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Collapses hidden lines to zero height and re-stacks what is left. This is
     * the single place folding affects layout; every index in {@code offsets} and
     * {@code spans} is a document line, so nothing is remapped and the arrays are
     * always the length {@code BoxView} expects.
     */
    @Override
    protected void layoutMajorAxis(int targetSpan, int axis, int[] offsets, int[] spans) {
        super.layoutMajorAxis(targetSpan, axis, offsets, spans);

        if (axis != Y_AXIS) {
            return;
        }
        int y = 0;
        for (int line = 0; line < spans.length; line++) {
            if (isHidden(line)) {
                spans[line] = 0;
            }
            offsets[line] = y;
            y += spans[line];
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The vertical span counts only what is on screen, consistent with
     * {@link #layoutMajorAxis}. A wrapped line can occupy several rows, so this
     * sums the children rather than multiplying a line count.
     */
    @Override
    public float getPreferredSpan(int axis) {
        syncFoldState();

        if (axis != Y_AXIS || !hasFolds()) {
            return super.getPreferredSpan(axis);
        }
        // Note: WrappedPlainView.updateMetrics() is private, so this branch cannot
        // refresh the font metrics the children read. Harmless today — the UI calls
        // setSize before any preferred-size query, and the X_AXIS path still goes
        // through super — but it is why the children must not be queried from a
        // colder start than this.
        float span = 0;
        for (int line = 0; line < getViewCount(); line++) {
            if (!isHidden(line)) {
                span += getView(line).getPreferredSpan(Y_AXIS);
            }
        }
        return span;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Skips hidden lines outright.
     *
     * <p>Zero height is not enough on its own: {@code BoxView} still offers every
     * child its turn, and a text view draws into the rectangle it is given without
     * regard to that rectangle's height. Every hidden line therefore rendered at
     * the fold boundary, stacking the whole collapsed body into a few rows of
     * unreadable overstruck glyphs — one click on a fold marker was enough.
     */
    @Override
    protected void paintChild(java.awt.Graphics g, java.awt.Rectangle alloc, int index) {
        if (isHidden(index)) {
            return;
        }
        super.paintChild(g, alloc, index);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Follows the folded height. {@code BoxView} derives its minimum from
     * requirements computed over <em>every</em> child, hidden ones included, and
     * {@code JEditorPane.getPreferredSize()} clamps the preferred height up to the
     * UI's minimum — so without this the component kept its unfolded height when a
     * fold collapsed, leaving the scrollbar range unchanged and a large empty
     * region below the last line.
     */
    @Override
    public float getMinimumSpan(int axis) {
        if (axis == Y_AXIS && hasFolds()) {
            return getPreferredSpan(axis);
        }
        return super.getMinimumSpan(axis);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Follows the folded height, for the reason given on
     * {@link #getMinimumSpan}.
     */
    @Override
    public float getMaximumSpan(int axis) {
        if (axis == Y_AXIS && hasFolds()) {
            return getPreferredSpan(axis);
        }
        return super.getMaximumSpan(axis);
    }

    /** Whether anything is currently collapsed. */
    private boolean hasFolds() {
        Vector folds = folds();

        return folds != null && !folds.isEmpty();
    }

    // ── Caret movement ──────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Keeps the caret out of collapsed folds. Zero-height lines are still
     * present in the model, so vertical movement can land on one; this steps
     * through to the next line the user can actually see.
     */
    @Override
    public int getNextVisualPositionFrom(int pos, Position.Bias b, Shape a,
            int direction, Position.Bias[] biasRet) throws BadLocationException {
        if (direction == SwingConstants.NORTH || direction == SwingConstants.SOUTH) {
            return verticalPosition(pos, a, direction == SwingConstants.NORTH, biasRet);
        }
        int next = super.getNextVisualPositionFrom(pos, b, a, direction, biasRet);

        if (next < 0 || !isHidden(getElement().getElementIndex(next))) {
            return next;
        }
        boolean backwards = direction == SwingConstants.NORTH
                || direction == SwingConstants.WEST;

        return escapeFold(next, backwards);
    }

    /**
     * Moves the caret one visual row up or down.
     *
     * <p>Cannot delegate: {@code View} routes vertical movement through
     * {@code Utilities.getPositionAbove/Below}, which dereference
     * {@code modelToView} with no null check. Since {@link #modelToView} returns
     * null inside a collapsed fold, the first step into one threw an NPE out of
     * the caret action. The old fork hid this behind a blanket
     * {@code catch (Throwable)}; stepping in visible space avoids it instead.
     *
     * <p>A wrapped line occupies several rows, so this steps by the height of the
     * row the caret is on rather than by a whole line.
     *
     * @param pos the current offset
     * @param a the allocated region
     * @param up true for NORTH, false for SOUTH
     * @param biasRet receives the bias of the returned position
     * @return the offset one row away, or {@code pos} at the ends
     * @throws BadLocationException if the position cannot be mapped
     */
    private int verticalPosition(int pos, Shape a, boolean up, Position.Bias[] biasRet)
            throws BadLocationException {
        Shape current = modelToView(pos, a, Position.Bias.Forward);

        if (current == null) {
            biasRet[0] = Position.Bias.Forward;
            return pos;
        }
        java.awt.Rectangle box = current.getBounds();
        int rowHeight = Math.max(1, box.height);

        java.awt.Container host = getContainer();
        javax.swing.text.Caret caret = (host instanceof javax.swing.text.JTextComponent)
                ? ((javax.swing.text.JTextComponent) host).getCaret() : null;
        java.awt.Point magic = (caret != null) ? caret.getMagicCaretPosition() : null;
        int x = (magic != null) ? magic.x : box.x;

        java.awt.Rectangle alloc = a.getBounds();
        int y = box.y + (up ? -rowHeight : rowHeight);

        if (y < alloc.y || y > alloc.y + (int) getPreferredSpan(Y_AXIS)) {
            biasRet[0] = Position.Bias.Forward;
            return pos;   // already at the first or last visible row
        }
        return viewToModel(x, y, a, biasRet);
    }

    /**
     * Moves an offset out of the collapsed fold containing it.
     *
     * @param offset a position inside a fold
     * @param backwards true to leave via the fold's start, false via its end
     * @return the first offset on a visible line in that direction
     */
    private int escapeFold(int offset, boolean backwards) {
        Element map = getElement();
        int line = map.getElementIndex(offset);

        while (line >= 0 && line < map.getElementCount() && isHidden(line)) {
            line = backwards ? line - 1 : line + 1;
        }
        if (line < 0) {
            return getStartOffset();
        }
        if (line >= map.getElementCount()) {
            return getEndOffset() - 1;
        }
        Element target = map.getElement(line);

        // Leaving backwards lands at the END of the preceding visible line.
        return backwards ? Math.max(target.getEndOffset() - 1, target.getStartOffset())
                : target.getStartOffset();
    }

    /**
     * {@inheritDoc}
     *
     * <p>A hidden line has no on-screen extent, so it has no position to report.
     * Matching {@code FoldingPlainView}, that is answered with null rather than a
     * zero-height rectangle at the fold boundary, which would let a caret appear
     * to sit on a line that is not displayed.
     */
    @Override
    public Shape modelToView(int pos, Shape a, Position.Bias b) throws BadLocationException {
        syncFoldState();

        if (isHidden(getElement().getElementIndex(pos))) {
            return null;
        }
        return super.modelToView(pos, a, b);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Zero-height children can otherwise swallow a click at their boundary, so
     * a position that resolves into a fold is nudged out to the following visible
     * line.
     */
    @Override
    public int viewToModel(float x, float y, Shape a, Position.Bias[] bias) {
        syncFoldState();
        int offset = super.viewToModel(x, y, a, bias);

        if (isHidden(getElement().getElementIndex(offset))) {
            return escapeFold(offset, false);
        }
        return offset;
    }

}
