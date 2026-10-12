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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.EventListener;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;

import org.bounce.event.DoubleClickListener;

import com.dogsbay.xml.XMLError;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;

/**
 * The margin component for the editor.
 *
 * @version	$Revision: 1.10 $, $Date: 2004/10/13 18:33:35 $
 * @author Dogsbay
 */
public class BookmarkMargin extends JComponent {
	private static final ImageIcon BOOKMARK_ICON = DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Bookmarks8.gif");
	private static final ImageIcon ERROR_ICON = DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Error8.gif");
	private static final ImageIcon WARNING_ICON = DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Warning8.gif");

	// Set right/left margin
	private final static int WIDTH = 9;

	// heights and widths
	private int lineHeight	= 16;
	private int topMargin	= 3;

	// Metrics of this LineNumber component
	private FontMetrics fontMetrics = null;
	private XmlEditorPane editor = null;
	private Editor parent = null;
	private EditorPanel panel = null;

//	private Tag startTag = null;
//	private Tag endTag = null;

	private int lines = 0;

	/**
	 * Convenience constructor for Text Components
	 */
	public BookmarkMargin( Editor _parent, EditorPanel _panel, XmlEditorPane _editor) {
		this.editor = _editor;
		this.parent = _parent;
		this.panel = _panel;
		
		setBackground( UIManager.getColor( "TextPane.background") != null ? UIManager.getColor( "TextPane.background") : Color.white);
		setForeground( UIManager.getColor( "textText"));
		setFont( editor.getFont());
		
		Insets insets = editor.getMargin();
		
		if ( insets != null) {
			topMargin = insets.top;
		} else {
			topMargin = 0;
		}
		
		this.addMouseMotionListener( new MouseMotionListener() {
			public void mouseMoved( MouseEvent e) {
				try {
					int line = editor.getLineNumber( e.getY());
					XMLError error = parent.getError( line);
	
					if ( error != null) {
						try {
							if ( error.getType() == XMLError.WARNING) {
								setToolTipText( "Warning: ["+error.getLineNumber()+", "+error.getColumnNumber()+"] "+error.getMessage());
							} else {
								setToolTipText( "Error: ["+error.getLineNumber()+", "+error.getColumnNumber()+"] "+error.getMessage());
							}
						} catch ( Exception x) {
						}
					} else {
						setToolTipText( null);
					}
				} catch ( BadLocationException x) {}
			}

			public void mouseDragged( MouseEvent e) {
				try {
					int line = editor.getLineNumber( e.getY());
					XMLError error = parent.getError( line);
	
					if ( error != null) {
						try {
							if ( error.getType() == XMLError.WARNING) {
								setToolTipText( "Warning: ["+error.getLineNumber()+", "+error.getColumnNumber()+"] "+error.getMessage());
							} else {
								setToolTipText( "Error: ["+error.getLineNumber()+", "+error.getColumnNumber()+"] "+error.getMessage());
							}
						} catch ( Exception x) {
						}
					} else {
						setToolTipText( null);
					}
				} catch ( BadLocationException x) {}
			}
		});

		this.addMouseListener( new DoubleClickListener() {
			public void doubleClicked( MouseEvent e) {
				try {
					int line = editor.getLineNumber( e.getY());
					XMLError error = parent.getError( line);
					
					if ( error != null) {
						panel.selectError( error);
					} else {
						parent.toggleBookmark( line);
					}
				} catch ( Exception x) {
					x.printStackTrace();
				}
			}
		});

		this.addMouseListener( new MouseAdapter() {
			public void mouseClicked( MouseEvent e) {
				panel.setFocus();
			}
		});
	}

	public Dimension getPreferredSize() {
		return new Dimension( WIDTH, editor.getPreferredSize().height);		
	}

	public Dimension getMaximumSize() {
		return new Dimension( WIDTH, editor.getMaximumSize().height);		
	}

	public Dimension getMinimumSize() {
		return new Dimension( WIDTH, editor.getMinimumSize().height);		
	}

	/**
	 * The line height defaults to the line height of the font for this
	 * component. The line height can be overridden by setting it to a
	 * positive non-zero value.
	 */
	public int getLineHeight() {
		return lineHeight;
	}

	public int getStartOffset() {
		return 4;
	}
	
	public void setFont( Font font) {
		super.setFont( font);
		
		if ( font != null) {
			fontMetrics = getFontMetrics( font);
			lineHeight = fontMetrics.getHeight();
		}
	}

//	public void setTags( Tag startTag, Tag endTag) {
//		this.startTag = startTag;
//		this.endTag = endTag;
//	}
	
	private Tag startTag = null;
	private Tag endTag = null;


	public void paintComponent( Graphics g ) {
		if ( fontMetrics != null) {
			Element root = editor.getDocument().getDefaultRootElement();

			// The tag lookup chains through XMLSegment into BufferContent and
			// can throw transiently under Java 21 paint/edit races (e.g. on
			// window focus return after "Reveal in System"). Catch so the
			// exception doesn't kill Swing's paint loop and freeze the UI.
			Tag sTag = null;
			Tag eTag = null;
			try {
				sTag = panel.getCurrentStartTag();
				eTag = panel.getEndTag( sTag);
			} catch ( Exception e) {
				e.printStackTrace();
			}

			if ( sTag != null) {
				startTag = sTag;
				endTag = eTag;
			}

			int selectionStart = -1;
			int selectionEnd = -1;

			if ( startTag != null) {
				int line = root.getElementIndex( startTag.getStart());
				try {
					selectionStart = editor.getLineStart( line);
				} catch ( Exception e) {
					e.printStackTrace();
				}
			}
			
			if ( endTag != null) {
				int line = root.getElementIndex( endTag.getEnd());

				try {
					selectionEnd = editor.getLineStart( line) + getLineHeight();
				} catch ( Exception e) {
					e.printStackTrace();
				}

			} else if ( startTag != null) {
				int line = root.getElementIndex( startTag.getEnd());

				try {
					selectionEnd = editor.getLineStart( line) + getLineHeight();
				} catch ( Exception e) {
					e.printStackTrace();
				}
			}
				
			int lineHeight = getLineHeight();
			int startOffset = getStartOffset();
			Rectangle drawHere = g.getClipBounds();

			int width = getPreferredSize().width;

			// System.out.println( drawHere );
			// Paint the background
			g.setColor( getBackground());
			g.fillRect( drawHere.x, drawHere.y, drawHere.width, drawHere.height );

			if ( selectionStart != -1 && selectionEnd != -1) {
				g.setColor( UIManager.getColor("control"));
				g.fillRect( drawHere.x, selectionStart, drawHere.width-1, selectionEnd - selectionStart);
			}

			g.setColor( UIManager.getColor("controlShadow"));
			g.drawLine( width-1, drawHere.y, width-1, drawHere.y + drawHere.height);
//			g.drawLine( 1, drawHere.y, 1, drawHere.y + drawHere.height);

			// Determine the number of lines to draw in the foreground.
			g.setColor( getForeground());

			int startLineNumber = 0; //( drawHere.y / lineHeight ) + 1;
			int endLineNumber = 0;
			
			try{
				startLineNumber = editor.getLineNumber( drawHere.y);
				if ( startLineNumber > 1) {
					startLineNumber = startLineNumber - 1;
				}
			} catch ( Exception e) {
				e.printStackTrace();
				return;
			}

			try{
				int lines = editor.getLines();
				endLineNumber = editor.getLineNumber( drawHere.y + drawHere.height);

				if ( endLineNumber <= lines - 1) {
					endLineNumber = endLineNumber+1;
				} else {
					endLineNumber = lines;
				}
			} catch ( Exception e) {
				e.printStackTrace();
				return;
			}

			int line = startLineNumber;
			int previous = -1;

			while ( line < endLineNumber) {
				try {
					XMLError error = parent.getError( line);
					int start = editor.getLineStart( line);
					
					if ( previous != -1 && line - previous > 1) {
						g.setColor( UIManager.getColor( "controlShadow"));
						g.drawLine( 1, start-1, WIDTH-3, start-1);
						g.setColor( getForeground());
					}

					if ( error != null) {
						if ( start != -1) {
							if ( error.getType() == XMLError.WARNING) {
								WARNING_ICON.paintIcon( this, g, 0, start + ((lineHeight - BOOKMARK_ICON.getIconHeight())/2));
							} else {
								ERROR_ICON.paintIcon( this, g, 0, start + ((lineHeight - BOOKMARK_ICON.getIconHeight())/2));
							}
						}
					} else if ( parent.isBookmark( line)) {
						if ( start != -1) {
							BOOKMARK_ICON.paintIcon( this, g, 0, start + ((lineHeight - BOOKMARK_ICON.getIconHeight())/2));
						}
					}

					previous = line;
					line = editor.getNextLineNumber( line);
				} catch ( Exception e) {
					e.printStackTrace();
					return;
				}
			}
		}
	}
	
	protected void removeAllListeners() {
		MouseListener[] mouseListeners = getMouseListeners();

		for ( int i = 0; i >= mouseListeners.length; i++) {
			removeMouseListener( mouseListeners[i]);
		}

		// Guaranteed to return a non-null array
		Object[] list = listenerList.getListenerList();
		
		for ( int i = list.length-2; i >= 0; i -= 2) {
			listenerList.remove( (Class)list[i], (EventListener)list[i+1]);
		}
	}

	public void cleanup() {
		removeAllListeners();
		finalize();
	}
	
	protected void finalize() {
		fontMetrics = null;
		parent = null;
		editor = null;
	}
}
