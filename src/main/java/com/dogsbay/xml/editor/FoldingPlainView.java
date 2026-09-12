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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.util.Vector;

import javax.swing.SwingConstants;
import java.awt.Point;

import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Element;
import javax.swing.text.LayeredHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.PlainView;
import javax.swing.text.Position;
import javax.swing.text.Segment;
import javax.swing.text.TabExpander;
import javax.swing.text.Utilities;

/**
 * A {@link PlainView} that can hide the lines inside collapsed folds.
 *
 * <p>Base class for the editor's unwrapped views — XML, Markdown, AsciiDoc, JSON
 * and YAML all extend it.
 *
 * <p>This was previously a fork of Sun's {@code PlainView}, carrying a
 * proprietary header that could not be published. It is now a genuine subclass:
 * the fork existed because {@code PlainView} indexes the line map directly with
 * no hook for skipping a hidden line, but everything needed to supply that hook
 * is protected and reachable. See {@code plans/sun-code-investigation.md}.
 *
 * <p><b>Two index spaces.</b> <em>Element</em> indices count every line in the
 * document; <em>visible</em> indices count only the lines on screen.
 * {@link FoldMapping} converts between them, and {@link #lineToRect} is where the
 * two meet: because {@code PlainView} routes its own geometry through that
 * method, overriding it makes the inherited {@code modelToView} and damage
 * handling fold-aware without further work.
 *
 * <p>Drawing, selection splitting and syntax highlighting are all inherited.
 * {@link #paint} walks visible rows and calls {@code drawLine}, which dispatches
 * to whatever {@code drawUnselectedText}/{@code drawSelectedText} the concrete
 * view overrides.
 */
public class FoldingPlainView extends PlainView implements TabExpander {

    /** X origin of the text area, the reference point for tab stops. */
    private int tabBase;

    /** Width of one tab in pixels, refreshed each paint. */
    private int tabWidth;

    /**
     * Constructs a folding view over a line-structured element.
     *
     * @param elem the element this view renders
     */
    public FoldingPlainView(Element elem) {
        super(elem);
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

    /** Whether an element-space line is inside a collapsed fold. */
    private boolean isHidden(int line) {
        return FoldMapping.isHidden(line, folds());
    }

    /** How many lines are currently on screen. */
    private int visibleLineCount() {
        return FoldMapping.visibleLineCount(getElement().getElementCount(), folds());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Also refreshes the tab width. It used to be computed only in
     * {@link #paint}, but {@code getPreferredSpan(X_AXIS)} measures the longest
     * line through {@link #nextTabStop} — so before the first paint every tab
     * measured as zero and a tab-indented file opened with roughly half the
     * horizontal extent it needed, clipping long lines with no way to scroll to
     * them. The same applied after any font change.
     */
    @Override
    protected void updateMetrics() {
        super.updateMetrics();

        if (metrics != null) {
            tabWidth = getTabSize() * metrics.charWidth('m');
        }
    }

    // ── Geometry ────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Placed at the line's <em>visible</em> row, so hidden lines take no
     * vertical space. {@code PlainView} uses this for its own model-to-view and
     * damage calculations, which therefore become fold-aware too.
     */
    @Override
    protected Rectangle lineToRect(Shape a, int line) {
        return super.lineToRect(a, FoldMapping.toVisibleIndex(line, folds()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>The vertical span counts visible lines only. The horizontal span is
     * unaffected by folding and is left to the superclass, which tracks the
     * longest line.
     */
    @Override
    public float getPreferredSpan(int axis) {
        if (axis == Y_AXIS) {
            updateMetrics();
            return visibleLineCount() * metrics.getHeight();
        }
        return super.getPreferredSpan(axis);
    }

    /**
     * {@inheritDoc}
     *
     * @return null when {@code pos} lies inside a collapsed fold — it has no
     *         on-screen rectangle. Callers in this editor already expect that;
     *         changing it would mean auditing every one.
     */
    @Override
    public Shape modelToView(int pos, Shape a, Position.Bias b) throws BadLocationException {
        if (isHidden(getElement().getElementIndex(pos))) {
            return null;
        }
        return super.modelToView(pos, a, b);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Maps the y coordinate to a visible row, then back to the line that row
     * shows, so a click can never land on a hidden line.
     */
    @Override
    public int viewToModel(float fx, float fy, Shape a, Position.Bias[] bias) {
        bias[0] = Position.Bias.Forward;
        Rectangle alloc = a.getBounds();
        Element map = getElement();

        updateMetrics();
        int fontHeight = Math.max(1, metrics.getHeight());
        int x = (int) fx;
        int y = (int) fy;

        if (y < alloc.y) {
            return getStartOffset();
        }
        int lastVisible = Math.max(0, visibleLineCount() - 1);
        int row = Math.min(Math.max((y - alloc.y) / fontHeight, 0), lastVisible);
        int line = FoldMapping.toElementIndex(row, folds());

        if (line >= map.getElementCount()) {
            return getEndOffset() - 1;
        }
        Element lineElement = map.getElement(line);
        int p0 = lineElement.getStartOffset();
        int p1 = Math.max(p0, lineElement.getEndOffset() - 1);

        if (x < alloc.x) {
            return p0;
        }
        try {
            Segment buffer = getLineBuffer();
            getDocument().getText(p0, p1 - p0, buffer);
            tabBase = alloc.x;

            return p0 + Utilities.getTabbedTextOffset(buffer, metrics, tabBase, x, this, p0);
        } catch (BadLocationException cannotHappen) {
            // The offsets came from the element map, so they are in range.
            return p0;
        }
    }

    // ── Painting ────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Walks the visible rows intersecting the clip and draws the line each one
     * shows. Everything below the line — selection splitting, tab expansion,
     * syntax highlighting — is the inherited {@code drawLine}, which dispatches
     * to the concrete view's text-drawing overrides.
     */
    @Override
    public void paint(Graphics g, Shape a) {
        Rectangle alloc = a.getBounds();
        JTextComponent host = (JTextComponent) getContainer();

        g.setFont(host.getFont());
        if (g instanceof Graphics2D) {
            // Whole-pixel glyph positions: the editor aligns its margins, folding
            // marks and caret on integer coordinates, and fractional advances
            // drift out of step with them.
            ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        }

        updateMetrics();
        tabBase = alloc.x;

        int fontHeight = Math.max(1, metrics.getHeight());
        Rectangle clip = g.getClipBounds();
        Rectangle damage = (clip != null) ? clip : alloc;

        int firstRow = Math.max((damage.y - alloc.y) / fontHeight, 0);
        int lastRow = Math.min(
                (damage.y + damage.height - alloc.y) / fontHeight,
                visibleLineCount() - 1);

        Element map = getElement();
        Highlighter highlighter = host.getHighlighter();
        LayeredHighlighter layered = (highlighter instanceof LayeredHighlighter)
                ? (LayeredHighlighter) highlighter : null;

        int y = alloc.y + (firstRow * fontHeight) + metrics.getAscent();

        for (int row = firstRow; row <= lastRow; row++) {
            int line = FoldMapping.toElementIndex(row, folds());

            if (line >= map.getElementCount()) {
                break;
            }
            if (layered != null) {
                Element lineElement = map.getElement(line);
                layered.paintLayeredHighlights(g, lineElement.getStartOffset(),
                        lineElement.getEndOffset() - 1, a, host, this);
            }
            drawLine(line, g, alloc.x, y);
            y += fontHeight;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Draws one line, splitting it around the current selection so the
     * concrete view's {@code drawUnselectedText}/{@code drawSelectedText} — which
     * is where syntax highlighting lives — sees each run separately.
     *
     * <p>This cannot be inherited. {@code PlainView} keeps the selection bounds it
     * draws with in private fields that only <em>its own</em> {@code paint} sets,
     * and {@link #paint} is overridden here to walk visible rows. Inheriting
     * would render every line as unselected; a test caught exactly that.
     */
    @Override
    protected void drawLine(int lineIndex, Graphics g, int x, int y) {
        Element line = getElement().getElement(lineIndex);
        JTextComponent host = (JTextComponent) getContainer();
        int selectionStart = host.getSelectionStart();
        int selectionEnd = host.getSelectionEnd();

        try {
            if (line.isLeaf()) {
                drawRun(line, g, x, y, selectionStart, selectionEnd);
            } else {
                // Composed text (an active input method) splits the line further.
                for (int i = 0; i < line.getElementCount(); i++) {
                    x = drawRun(line.getElement(i), g, x, y, selectionStart, selectionEnd);
                }
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException("Can't render line: " + lineIndex, e);
        }
    }

    /**
     * Draws one element's text, breaking it at the selection boundaries.
     *
     * @param elem the run to draw
     * @param g the graphics context
     * @param x starting x
     * @param y text baseline
     * @param selectionStart start of the host's selection
     * @param selectionEnd end of the host's selection
     * @return the x coordinate just past the drawn text
     * @throws BadLocationException if the element's offsets are out of range
     */
    private int drawRun(Element elem, Graphics g, int x, int y,
            int selectionStart, int selectionEnd) throws BadLocationException {
        int p0 = elem.getStartOffset();
        int p1 = Math.min(getDocument().getLength(), elem.getEndOffset());

        // Clamp the selection to this run, leaving three possible spans:
        // [p0, from) unselected, [from, to) selected, [to, p1) unselected. Any of
        // them can be empty, which covers "no selection" and "wholly selected"
        // without special cases.
        if (selectionStart == selectionEnd) {
            // No selection: one call, so a lexer-driven subclass is not made to
            // restart mid-token at the caret on every paint.
            return drawUnselectedText(g, x, y, p0, p1);
        }
        int from = Math.min(Math.max(selectionStart, p0), p1);
        int to = Math.min(Math.max(selectionEnd, p0), p1);

        if (from > p0) {
            x = drawUnselectedText(g, x, y, p0, from);
        }
        if (to > from) {
            x = drawSelectedText(g, x, y, from, to);
        }
        if (p1 > to) {
            x = drawUnselectedText(g, x, y, to, p1);
        }
        return x;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sets the colour before delegating. {@code PlainView} draws with private
     * colour fields that only <em>its own</em> {@code paint} assigns, and this
     * class supplies its own paint loop — so the inherited implementation would
     * call {@code setColor(null)}, which is silently ignored, leaving text in
     * whatever colour the {@code Graphics} last held. Subclasses that override
     * this are unaffected, but their {@code lexer == null} fallbacks reach here.
     */
    @Override
    protected int drawUnselectedText(Graphics g, int x, int y, int p0, int p1)
            throws BadLocationException {
        JTextComponent host = (JTextComponent) getContainer();

        if (host != null) {
            g.setColor(host.isEnabled() ? host.getForeground() : host.getDisabledTextColor());
        }
        return super.drawUnselectedText(g, x, y, p0, p1);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sets the colour before delegating, for the reason given on
     * {@link #drawUnselectedText}.
     */
    @Override
    protected int drawSelectedText(Graphics g, int x, int y, int p0, int p1)
            throws BadLocationException {
        JTextComponent host = (JTextComponent) getContainer();

        if (host != null) {
            g.setColor(host.getSelectedTextColor());
        }
        return super.drawSelectedText(g, x, y, p0, p1);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Tab stops are measured from the text origin recorded during
     * {@link #paint}, so they stay aligned regardless of which rows were drawn.
     */
    @Override
    public float nextTabStop(float x, int tabOffset) {
        if (tabWidth <= 0) {
            return x;
        }
        int stops = (((int) x) - tabBase) / tabWidth;

        return tabBase + ((stops + 1) * tabWidth);
    }

    // ── Caret movement ──────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Vertical movement steps over collapsed folds: the superclass works in the
     * fold-aware geometry this class supplies, and anything that still lands
     * inside a fold is nudged clear so the caret is never parked on a line the
     * user cannot see.
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
     * Moves the caret one visible row up or down, keeping its column.
     *
     * <p>Cannot delegate to the superclass: {@code View} routes vertical movement
     * through {@code Utilities.getPositionAbove/Below}, which dereferences
     * {@code modelToView} without a null check. Since {@link #modelToView} returns
     * null for a line inside a collapsed fold, the first step into a fold threw an
     * NPE out of the caret action and the arrow key silently did nothing.
     *
     * <p>Working in visible rows means folded lines are simply not there to step
     * onto, so no escaping is needed afterwards.
     *
     * @param pos the current offset
     * @param a the allocated region
     * @param up true for NORTH, false for SOUTH
     * @param biasRet receives the bias of the returned position
     * @return the offset one visible row away, or {@code pos} at the ends
     * @throws BadLocationException if the position cannot be mapped
     */
    private int verticalPosition(int pos, Shape a, boolean up, Position.Bias[] biasRet)
            throws BadLocationException {
        Shape current = modelToView(pos, a, Position.Bias.Forward);

        if (current == null) {
            biasRet[0] = Position.Bias.Forward;
            return pos;
        }
        updateMetrics();
        Rectangle box = current.getBounds();
        int fontHeight = Math.max(1, metrics.getHeight());

        // Keep the column the user has been travelling in, as Swing does.
        JTextComponent host = (JTextComponent) getContainer();
        Caret caret = (host != null) ? host.getCaret() : null;
        Point magic = (caret != null) ? caret.getMagicCaretPosition() : null;
        int x = (magic != null) ? magic.x : box.x;

        Rectangle alloc = a.getBounds();
        int y = box.y + (up ? -fontHeight : fontHeight);

        if (y < alloc.y || y >= alloc.y + Math.max(1, visibleLineCount()) * fontHeight) {
            biasRet[0] = Position.Bias.Forward;
            return pos;   // already at the first or last visible row
        }
        return viewToModel(x, y, a, biasRet);
    }

    /**
     * Moves an offset out of the collapsed fold containing it.
     *
     * @param offset a position inside a fold
     * @param backwards true to leave via the fold's start line, false via its end
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

        // Direction matters. Stepping backwards out of a fold should land at the
        // END of the preceding visible line — landing at its start would skip the
        // whole line, so Left-then-Right was not an identity and Shift+Left
        // selected the entire line in one keystroke.
        return backwards ? Math.max(target.getEndOffset() - 1, target.getStartOffset())
                : target.getStartOffset();
    }
}
