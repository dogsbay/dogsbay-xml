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

import java.awt.Font;
import java.awt.Graphics;
import java.awt.Shape;

import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.Segment;
import javax.swing.text.Style;
import javax.swing.text.Utilities;

/**
 * View for rendering Markdown with syntax highlighting.
 */
public class MarkdownView extends FoldingPlainView {
	private static final boolean DEBUG = false;

	private boolean highlight = false;
	private MarkdownScanner lexer;
	private boolean lexerValid;
	private XmlContext context = null;

	public MarkdownView(XmlContext context, Element elem) {
		super(elem);
		this.context = context;

		XmlDocument doc = (XmlDocument) getDocument();
		try {
			lexer = new MarkdownScanner(doc);
		} catch (Exception e) {
			lexer = null;
		}
		lexerValid = false;
	}

	public void paint(Graphics g, Shape a) {
		super.paint(g, a);
		lexerValid = false;
	}

	public void setHighlight(boolean enabled) {
		highlight = enabled;
	}

	public boolean isHighlight() {
		return highlight;
	}

	protected int drawUnselectedText(Graphics g, int x, int y, int start, int end) throws BadLocationException {
		if (lexer == null) {
			return super.drawUnselectedText(g, x, y, start, end);
		}

		Document doc = getDocument();
		Style lastStyle = null;
		int mark = start;

		if (DEBUG) System.out.println("MarkdownView.drawUnselectedText()");

		// Always use the plain font so that character widths match PlainView's
		// modelToView/viewToModel calculations. Only change color per token.
		Font plainFont = context.getFont(context.getStyle(Constants.MD_TEXT));
		g.setFont(plainFont);

		while (start < end) {
			updateScanner(start);

			int p = Math.min(lexer.getEndOffset(), end);
			p = (p <= start) ? end : p;

			Style style = resolveStyle(context.getStyle(lexer.token));

			if (style != lastStyle && lastStyle != null) {
				Segment text = getLineBuffer();
				doc.getText(mark, start - mark, text);

				g.setColor(context.getForeground(lastStyle));
				x = Utilities.drawTabbedText(text, x, y, g, this, mark);

				mark = start;
			}

			lastStyle = style;
			start = p;
		}

		if (lastStyle == null) {
			lastStyle = resolveStyle(context.getStyle(Constants.MD_TEXT));
		}

		Segment text = getLineBuffer();
		doc.getText(mark, end - mark, text);
		g.setColor(context.getForeground(lastStyle));
		x = Utilities.drawTabbedText(text, x, y, g, this, mark);

		return x;
	}

	protected int drawSelectedText(Graphics g, int x, int y, int start, int end) throws BadLocationException {
		if (lexer == null) {
			return super.drawSelectedText(g, x, y, start, end);
		}

		Document doc = getDocument();
		int mark = start;

		// Always use the plain font for consistent character widths
		Font plainFont = context.getFont(context.getStyle(Constants.MD_TEXT));
		g.setFont(plainFont);
		g.setColor(UIManager.getColor("TextPane.selectionForeground"));

		while (start < end) {
			updateScanner(start);

			int p = Math.min(lexer.getEndOffset(), end);
			p = (p <= start) ? end : p;

			start = p;
		}

		Segment text = getLineBuffer();
		doc.getText(mark, end - mark, text);
		x = Utilities.drawTabbedText(text, x, y, g, this, mark);

		return x;
	}

	private Style resolveStyle(Style style) {
		if (style == null) {
			return context.getStyle(Constants.MD_TEXT);
		}
		return style;
	}

	private void updateScanner(int p) {
		try {
			if (lexer == null) {
				return;
			}

			if (!lexerValid) {
				XmlDocument doc = (XmlDocument) getDocument();
				lexer.setRange(0, doc.getLength());
				lexerValid = true;
			}

			while (lexer.getEndOffset() <= p) {
				if (lexer.token == -1) {
					break;
				}
				lexer.scan();
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
}
