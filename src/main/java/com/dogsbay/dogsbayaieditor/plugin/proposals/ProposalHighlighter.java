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
package com.dogsbay.dogsbayaieditor.plugin.proposals;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.LayeredHighlighter;
import javax.swing.text.View;

import com.dogsbay.xml.review.Proposal;

/**
 * Paints proposals over the text editor: insertions tinted green, deletions
 * red with a line through, changed elements amber, comments yellow. Re-run
 * {@link #show} whenever the document changes; {@link #clear} on document
 * switch. Colours are translucent so syntax colouring stays readable.
 */
public final class ProposalHighlighter {

    private static final Color NEW = new Color(0x2E, 0x7D, 0x32, 0x40);
    private static final Color DELETED = new Color(0xC6, 0x28, 0x28, 0x40);
    private static final Color CHANGED = new Color(0xEF, 0x6C, 0x00, 0x40);
    private static final Color COMMENT = new Color(0xF9, 0xA8, 0x25, 0x50);

    private final List<Object> tags = new ArrayList<>();
    private JTextComponent current;

    public void show(JTextComponent text, List<Proposal> proposals) {
        clear();
        current = text;
        Highlighter h = text.getHighlighter();
        for (Proposal p : proposals) {
            try {
                Highlighter.HighlightPainter painter = switch (p.kind()) {
                    case INSERT -> new DefaultHighlighter.DefaultHighlightPainter(NEW);
                    case DELETE -> new Strike(DELETED);
                    case CHANGED -> new DefaultHighlighter.DefaultHighlightPainter(CHANGED);
                    case COMMENT -> new DefaultHighlighter.DefaultHighlightPainter(COMMENT);
                };
                tags.add(h.addHighlight(p.start(), Math.min(p.end(), text.getDocument().getLength()), painter));
            } catch (BadLocationException ignore) {
                // the document moved under us; the next refresh catches up
            }
        }
    }

    public void clear() {
        if (current != null) {
            Highlighter h = current.getHighlighter();
            for (Object tag : tags) {
                try {
                    h.removeHighlight(tag);
                } catch (RuntimeException ignore) {
                    // already gone
                }
            }
        }
        tags.clear();
        current = null;
    }

    /** A tint plus a line through the middle of the text. */
    static final class Strike extends LayeredHighlighter.LayerPainter {
        private final Color color;

        Strike(Color color) {
            this.color = color;
        }

        @Override
        public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
            // handled by paintLayer for layered highlighters
        }

        @Override
        public Shape paintLayer(Graphics g, int p0, int p1, Shape bounds, JTextComponent c, View view) {
            try {
                Shape s = view.modelToView(p0, javax.swing.text.Position.Bias.Forward, p1,
                        javax.swing.text.Position.Bias.Backward, bounds);
                Rectangle r = s instanceof Rectangle rect ? rect : s.getBounds();
                g.setColor(color);
                g.fillRect(r.x, r.y, r.width, r.height);
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 0xC0));
                int y = r.y + r.height / 2;
                g.drawLine(r.x, y, r.x + r.width, y);
                return r;
            } catch (BadLocationException e) {
                return null;
            }
        }
    }
}
