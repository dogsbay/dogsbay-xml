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
import java.awt.font.TextLayout;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

import javax.swing.text.AttributeSet;
import javax.swing.text.StyleConstants;

/**
 * Text-rendering helpers that {@code javax.swing.text.Utilities} keeps package-private.
 *
 * <p>Everything else the folding views need — {@code drawTabbedText},
 * {@code getTabbedTextWidth}, {@code getTabbedTextOffset}, {@code getBreakLocation},
 * {@code getPositionAbove}/{@code Below} — is public on the JDK class and is called there
 * directly. Only these two composed-text helpers have no public equivalent, so they are
 * implemented here against the public AWT text API.
 *
 * <p>"Composed text" is the in-progress text an input method shows before the user commits
 * it — the underlined pre-edit string when typing CJK, for instance. Swing hands it to a
 * view as an {@link AttributedString} under
 * {@link StyleConstants#ComposedTextAttribute}, already carrying the input method's own
 * styling, so it must be drawn as an attributed run rather than as plain characters.
 */
final class TextUtils {

    private TextUtils() {
    }

    /**
     * Checks whether an attribute set carries input-method composed text.
     *
     * @param attr the attributes of the element being drawn; may be null
     * @return true if the element holds composed text
     */
    static boolean isComposedTextAttributeDefined(AttributeSet attr) {
        return attr != null && attr.isDefined(StyleConstants.ComposedTextAttribute);
    }

    /**
     * Draws the composed text held in an attribute set.
     *
     * <p>The stored {@link AttributedString} already carries the input method's styling
     * (underlines, highlight bands), so it is rendered through a {@link TextLayout} rather
     * than drawn character by character — that is what preserves those decorations.
     *
     * @param attr the attributes holding the composed text
     * @param g    the graphics context
     * @param x    the x origin
     * @param y    the y origin, on the baseline
     * @param p0   the start offset within the composed text
     * @param p1   the end offset within the composed text
     * @return the x location at the end of the drawn range
     */
    static int drawComposedText(AttributeSet attr, Graphics g, int x, int y, int p0, int p1) {
        if (p0 >= p1) {
            return x;
        }

        Object composed = attr.getAttribute(StyleConstants.ComposedTextAttribute);
        if (!(composed instanceof AttributedString)) {
            return x;
        }

        Graphics2D g2d = (Graphics2D) g;
        AttributedCharacterIterator text = ((AttributedString) composed).getIterator();

        // The offsets are relative to the composed run, but the iterator is indexed from
        // its own begin index.
        int begin = text.getBeginIndex();
        int end = text.getEndIndex();
        int from = Math.min(begin + p0, end);
        int to = Math.min(begin + p1, end);
        if (from >= to) {
            return x;
        }

        AttributedString range = new AttributedString(text, from, to);
        TextLayout layout = new TextLayout(range.getIterator(), g2d.getFontRenderContext());
        layout.draw(g2d, x, y);

        return x + (int) layout.getAdvance();
    }
}
