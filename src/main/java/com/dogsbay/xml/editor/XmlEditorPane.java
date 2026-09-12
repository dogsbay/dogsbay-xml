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

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseListener;
import java.util.EventListener;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.InputMap;
import javax.swing.JEditorPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;
import javax.swing.text.Element;

import com.dogsbay.dogsbayaieditor.ErrorList;
import com.dogsbay.dogsbayaieditor.StringUtilities;

/**
 * An extension of the JEditorPane that allows for syntax-highlighted
 * text in the XML format.
 *
 * @version $Revision: 1.19 $, $Date: 2005/08/29 16:30:18 $
 * @author Dogsbay
 */
public class XmlEditorPane extends JEditorPane {
	private static int defaultTabSize = 4;

	public static final int NOT_LOCKED = 0;
	public static final int LOCKED = 1;
	public static final int DOUBLE_LOCKED = 2;

	private boolean tagCompletion = true;
	private boolean smartIndent = true;
	private DogsBayEditorKit kit = null;
	private boolean wrapped = false;
	private boolean doubleLocked = false;
	private int locked = NOT_LOCKED;
	private boolean antialiasing = false;
	private Pattern pattern = null;
	private ErrorList errors = null;
	private FoldingMargin margin = null;

	public XmlEditorPane(boolean wrapped) {
		this.wrapped = wrapped;

		registerEditorKits(null);

		setEditable(true);
		setDragEnabled(true);
		rebindBackspace();
	}

	/**
	 * Constructs the XML editor pane, sets the font,
	 * background color, content-type and the editor kit.
	 */
	public XmlEditorPane(boolean wrapped, ErrorList errors) {
		this.wrapped = wrapped;
		this.errors = errors;

		registerEditorKits(errors);

		setEditable(true);
		setDragEnabled(true);
		rebindBackspace();
	}

	/**
	 * Registers editor kits for all formats in the DocumentFormatRegistry,
	 * plus a fallback XmlEditorKit as the default active kit.
	 */
	private void registerEditorKits(ErrorList errors) {
		// Register kits from all registered document formats
		for (DocumentFormat format : DocumentFormatRegistry.getAll()) {
			DogsBayEditorKit formatKit = format.createEditorKit(this);
			setEditorKitForContentType(formatKit.getContentType(), formatKit);
		}

		// The XML kit with error support needs special handling
		// since it requires the ErrorList parameter
		if (errors != null) {
			kit = new XmlEditorKit(this, errors);
			setEditorKitForContentType(kit.getContentType(), kit);
		}

		// Default to XML content type
		DocumentFormat xmlFormat = DocumentFormatRegistry.findByName("XML");
		if (xmlFormat != null) {
			setContentType(xmlFormat.getContentType());
		} else {
			setContentType("text/xml");
		}
		refreshEditorKit();
		super.setFont(kit.getFont());
	}

	public void refreshEditorKit() {
		if (getEditorKit() instanceof DogsBayEditorKit) {
			kit = (DogsBayEditorKit) getEditorKit();
		}
	}

	public void setCaretPosition(int pos) {
		// if (DEBUG) System.out.println( "setCaretPosition( "+pos+")");

		Document doc = getDocument();
		if (doc != null) {
			if (pos > doc.getLength() || pos < 0) {
				throw new IllegalArgumentException("bad position: " + pos);
			}

			if (margin != null) {
				Element e = doc.getDefaultRootElement();
				int index = e.getElementIndex(pos);
				margin.unfold(index);
			}

			super.setCaretPosition(pos);
		}
	}

	public void moveCaretPosition(int pos) {
		// System.out.println( "moveCaretPosition( "+pos+")");
		Document doc = getDocument();

		if (doc != null) {
			if (pos > doc.getLength() || pos < 0) {
				throw new IllegalArgumentException("bad position: " + pos);
			}

			if (margin != null) {
				Element e = doc.getDefaultRootElement();
				int index = e.getElementIndex(pos);
				margin.unfold(index);
			}

			super.moveCaretPosition(pos);
		}
	}

	private void rebindBackspace() {
		InputMap im = getInputMap(); // remove old binding
		KeyStroke typed010 = KeyStroke.getKeyStroke("typed \010");
		InputMap pim = im;

		while (pim != null) {
			pim.remove(typed010);
			pim = pim.getParent();
		} // rebind backspace

		KeyStroke bksp = KeyStroke.getKeyStroke("BACK_SPACE");
		im.put(bksp, DefaultEditorKit.deletePrevCharAction);
	}

	public int getLocked() {
		return locked;
	}

	public void setLocked(int locked) {
		this.locked = locked;
	}

	/**
	 * Gets the number of soft wrapped lines this line makes up.
	 *
	 * @return the number of soft lines in the line.
	 */
	public int getLineNumber(int y) throws BadLocationException {
		int pos = viewToModel2D(new Point(0, y));

		Element root = getDocument().getDefaultRootElement();
		return root.getElementIndex(pos);
	}

	public int getNextLineNumber(int line) throws BadLocationException {
		line++;

		if (margin != null) {
			Fold fold = margin.getFold(line);

			if (fold != null) {
				line = fold.getEnd();
			}
		}

		return line;
	}

	/**
	 * Selects the indicated line.
	 *
	 * @param line the line to select.
	 */
	public void selectLine(int line) {
		if (line > 0) {
			Element root = getDocument().getDefaultRootElement();
			Element elem = root.getElement(line - 1);

			if (elem != null) {
				int start = elem.getStartOffset();
				int end = elem.getEndOffset();

				select(start, end);
			}
		}
	}

	/**
	 * Gets the number of lines in the document.
	 *
	 * @return the number of lines in the document.
	 */
	public int getLines() {
		Element root = getDocument().getDefaultRootElement();
		return root.getElementCount();
	}

	public void setHighlight(boolean enabled) {
		kit.setHighlight(enabled);
	}

	public boolean isHighlight() {
		return kit.isHighlight();
	}

	public void setErrorHighlighting(boolean enabled) {
		kit.setErrorHighlighting(enabled);
	}

	public boolean isErrorHighlighting() {
		return kit.isErrorHighlighting();
	}

	/**
	 * Selects the indicated line.
	 *
	 * @param line the line to select.
	 */
	public void selectLineForOffset(int off) {
		int pos = viewToModel2D(new Point(0, off));

		// System.out.println( "Line = "+pos);
		//
		if (pos >= 0) {
			Element root = getDocument().getDefaultRootElement();
			Element elem = root.getElement(root.getElementIndex(pos));

			if (elem != null) {
				int start = elem.getStartOffset();
				int end = elem.getEndOffset();

				select(start, end);
			}
		}
	}

	// public void replaceSelection( String content) {
	// super.replaceSelection( content);
	// }

	/**
	 * Gets the number of soft wrapped lines this line makes up.
	 *
	 * @return the number of soft lines in the line.
	 */
	public int getLineStart(int i) throws BadLocationException {
		Element root = getDocument().getDefaultRootElement();
		Element line = root.getElement(i);
		// System.out.println( "getLineStart( "+i+") ["+margin.isFolded( i)+",
		// "+line.getStartOffset()+"]");
		Rectangle result = modelToView2D(line.getStartOffset()).getBounds();

		if (result != null) {
			return result.y;
		}

		return -1;
	}

	public void setAntialiasing(boolean enabled) {
		antialiasing = enabled;
	}

	public void setFoldingMargin(FoldingMargin margin) {
		this.margin = margin;
	}

	public FoldingMargin getFoldingMargin() {
		return margin;
	}

	protected void paintComponent(Graphics g) {
		if (g instanceof Graphics2D) {
			Graphics2D g2d = (Graphics2D) g;
			if (antialiasing) {
				// Use LCD subpixel rendering for much better quality on modern monitors
				g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
						RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
				// Disable fractional metrics to prevent character overlap
				// (code uses integer-based charWidth() which doesn't work with fractional metrics)
				g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
						RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
			} else {
				g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
						RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
				g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
						RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
			}
		}

		super.paintComponent(g);
	}

	public boolean isWrapped() {
		return wrapped;
	}

	public void gotoLine(int line) {
		if (line > 0) {
			Element root = getDocument().getDefaultRootElement();

			if (line > root.getElementCount()) {
				line = root.getElementCount();
			}

			Element elem = root.getElement(line - 1);
			int pos = elem.getStartOffset();
			setCaretPosition(pos);

			// Scroll to make the caret visible
			try {
				Rectangle rect = modelToView2D(pos).getBounds();
				scrollRectToVisible(rect);
			} catch (Exception e) {
				// Ignore if we can't scroll
			}

			repaint();
		}
	}

	public Matcher search(Vector ranges, String search, boolean regExp, boolean matchCase, boolean matchword,
			boolean down, boolean wrap) {
		String regularSearch = search;

		if (ranges == null) {
			ranges = new Vector();
			ranges.addElement(new Range(0, getDocument().getLength()));
		}

		if (!regExp) {
			regularSearch = "\\Q" + StringUtilities.prepareNonRegularExpression(regularSearch) + "\\E";
		}

		if (matchword) {
			regularSearch = "\\b" + regularSearch + "\\b";
		}

		if (!matchCase) {
			pattern = Pattern.compile(regularSearch, Pattern.CASE_INSENSITIVE);
		} else {
			pattern = Pattern.compile(regularSearch);
		}

		try {
			int caret = Math.max(getCaretPosition(), Math.max(getSelectionStart(), getSelectionEnd()));
			Matcher matcher = pattern.matcher(getText(0, getDocument().getLength()));

			int start = -1;
			int end = -1;

			if (down) {
				boolean match = matcher.find(caret);

				while (match && !inRanges(ranges, matcher)) {
					match = matcher.find();
				}

				// could not find a match, start from the start...
				// if ( wrap && (!match || !inRanges( ranges, matcher))) {
				if (wrap && !match) {
					match = matcher.find(0);
				}

				// continue to search
				while (match && !inRanges(ranges, matcher)) {
					match = matcher.find();
				}

				if (match) {
					start = matcher.start();
					end = matcher.end();
				}
			} else {
				caret = Math.min(getCaretPosition(), Math.min(getSelectionStart(), getSelectionEnd()));

				while (matcher.find()) {
					if (matcher.start() < caret) {
						if (inRanges(ranges, matcher)) {
							start = matcher.start();
							end = matcher.end();
						}
					} else {
						break;
					}
				}

				if (start == -1 && wrap) {
					boolean match = matcher.find(caret);
					// get last matching element.
					while (match) {
						if (inRanges(ranges, matcher)) {
							start = matcher.start();
							end = matcher.end();
						}

						match = matcher.find();
					}
				}
			}

			if (end != -1) {
				matcher.find(start);
				select(start, end);
				repaint();
				return matcher;
			} else {
				repaint();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	private boolean inRanges(Vector ranges, Matcher matcher) {
		for (int i = 0; i < ranges.size(); i++) {
			Range range = (Range) ranges.elementAt(i);
			if (range == null) {

			}
			if (range.contains(((XmlDocument) getDocument()).calculateOldPosition(matcher.start()))
					&& range.contains(((XmlDocument) getDocument()).calculateOldPosition(matcher.end()))) {
				return true;
			}
		}
		return false;
	}

	public void setWrapped(boolean wrapped) {
		if (this.wrapped != wrapped) {
			this.wrapped = wrapped;
			boolean highlight = kit.isHighlight();

			kit.cleanup();

			if (getContentType().equals("text/txt")) {
				kit = new TextEditorKit(this);
				kit.setHighlight(highlight);
			} else {
				kit = new XmlEditorKit(this, errors);
				kit.setHighlight(highlight);
			}

			setEditorKit(kit);
		}
	}

	public boolean isTagCompletion() {
		return tagCompletion;
	}

	public void setTagCompletion(boolean complete) {
		this.tagCompletion = complete;
	}

	public boolean isSmartIndent() {
		return smartIndent;
	}

	public void setSmartIndent(boolean smart) {
		smartIndent = smart;
	}

	private JViewport getViewPort() {
		Component parent = getParent();

		while (!(parent instanceof JViewport)) {
			parent = parent.getParent();
		}

		return (JViewport) parent;
	}

	public void select(int start, int end) {
		// margin.unfold( getDocument().getDefaultRootElement().getElementIndex( start),
		// getDocument().getDefaultRootElement().getElementIndex( end));

		super.select(start, end);

		try {
			Rectangle selectStart = modelToView2D(start).getBounds();
			Rectangle selectEnd = modelToView2D(end).getBounds();
			Rectangle selectRect = new Rectangle(selectStart.x, selectStart.y, selectEnd.x - selectStart.x,
					selectEnd.y - selectStart.y);

			JViewport view = getViewPort();

			if (view != null) {
				Rectangle rect = view.getViewRect();

				if (rect.y > selectRect.y) {
					// Have to scroll down ...
					if (rect.height > selectRect.height) {
						selectRect.y = selectRect.y - ((rect.height - selectRect.height) / 4);
					}
				} else if (selectRect.y + selectRect.height > rect.y + rect.height) {
					// Have to scroll up ...
					if (rect.height > selectRect.height) {
						selectRect.y = ((rect.height - selectRect.height) / 4) + selectRect.y;
					}
				}

				if (rect.x > selectRect.x) {
					// Have to scroll right...
					if (rect.width > selectRect.height) {
						selectRect.x = selectRect.x - ((rect.width - selectRect.width) / 4);
					}
				} else if (selectRect.x + selectRect.width > rect.x + rect.width) {
					// Have to scroll lefy...
					if (rect.width > selectRect.width) {
						selectRect.x = ((rect.width - selectRect.width) / 4) + selectRect.x;
					}
				}

				if (selectRect.x < 0) {
					selectRect.x = 0;
				}

				if (selectRect.y < 0) {
					selectRect.y = 0;
				}

				scrollRectToVisible(selectRect);
			}
		} catch (BadLocationException e) {
			// should not happen!
		} catch (Exception e) {
			// cannot scroll
		}
	}

	public void setTabSize(int size) {
		defaultTabSize = size;
		Document doc = getDocument();

		if (doc != null) {
			int old = getTabSize();

			doc.putProperty(XmlDocument.tabSizeAttribute, Integer.valueOf(size));
			firePropertyChange("tabSize", old, size);
		}
	}

	public int getDefaultTabSize() {
		return defaultTabSize;
	}

	/**
	 * Gets the number of characters used to expand tabs. If the document is
	 * null or doesn't have a tab setting, return a default of 8.
	 *
	 * @return the number of characters
	 */
	public int getTabSize() {
		int size = defaultTabSize;
		Document doc = getDocument();

		if (doc != null) {
			Integer i = (Integer) doc.getProperty(XmlDocument.tabSizeAttribute);
			if (i != null) {
				size = i.intValue();
			}
		}

		return size;
	}

	public void setFont(Font font) {
		if (kit != null) {
			kit.setFont(font);
		}
		super.setFont(font);
	}

	public void updateUI() {
		super.updateUI();
		// After L&F change, re-apply the editor font so that layout metrics
		// and drawing fonts stay in sync (prevents garbled text rendering).
		if (kit != null && kit.getFont() != null) {
			super.setFont(kit.getFont());
		}
	}

	public void setAttributes(int id, Color color, int style) {
		kit.setAttributes(id, color, style);
	}

	public void setText(String text) {
		XmlDocument doc = (XmlDocument) getDocument();
		doc.setLoading(true);
		super.setText(text);
		doc.setLoading(false);
	}

	protected void removeAllListeners() {
		MouseListener[] mouseListeners = getMouseListeners();

		for (int i = 0; i < mouseListeners.length; i++) {
			removeMouseListener(mouseListeners[i]);
		}

		// Guaranteed to return a non-null array
		Object[] list = listenerList.getListenerList();

		for (int i = list.length - 2; i >= 0; i -= 2) {
			listenerList.remove((Class) list[i], (EventListener) list[i + 1]);
		}
	}

	public void cleanup() {
		removeAllListeners();

		XmlDocument doc = (XmlDocument) getDocument();
		doc.cleanup();

		finalize();
	}

	protected void finalize() {
		kit = null;

	}
}
