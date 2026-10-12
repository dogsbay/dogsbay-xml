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
import java.awt.Font;

import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;

/**
 * A list of styles used to render syntx-highlighted XML text.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:44:46 $
 * @author Dogsbay
 */
public class XmlContext extends StyleContext {

    private Style[] styles = null;
	private Font italicFont = null;
	private Font plainFont = null;
	private Font boldFont = null;

    /**
     * Constructs a set of style objects to represent XML 
	 * lexical tokens and initialises these tokens with a font 
	 * style and color.
     */
    public XmlContext() {
		super();
		
		styles = new Style[ Constants.MAX_TOKENS + 1];

		for ( int i = 0; i < styles.length; i++) {
		    styles[ i] = new NamedStyle();
		}
    }
	
	public void setFont( Font font) {

		plainFont = font.deriveFont( Font.PLAIN);
		italicFont = font.deriveFont( Font.ITALIC);
		boldFont = font.deriveFont( Font.BOLD);
	}

    /**
     * Sets the token attributes, like foreground color and 
	 * Font style. 
     * 
     * @param token the token to set the font for.
     * @param foreground the foreground color for the token.
     * @param style the font-style value for the token.
     */
    public void setAttributes( int token, Color foreground, int style) {
    	setForeground( token, foreground);
		setFontStyle( token, style);
    }

    /**
     * Sets the font to use for a lexical token with the 
	 * given value.
     * 
     * @param token the token to set the font for.
     * @param style the font-style value for the token.
     */
    public void setFontStyle( int token, int style) {
    	
    	Style s = this.getStyle( token);
		
    	StyleConstants.setItalic( s, (style & Font.ITALIC) > 0);
	    StyleConstants.setBold( s, (style & Font.BOLD) > 0);
    }

    /**
     * Sets the foreground color to use for a lexical
     * token with the given value.
     * 
     * @param token the token to set the foreground for.
     * @param color the foreground color value for the token.
     */
    public void setForeground( int token, Color color) {
    	Style s = this.getStyle( token);
    	StyleConstants.setForeground( s, color);
    }

    /**
     * Gets the foreground color to use for a lexical
     * token with the given value.
     * 
     * @param scanValue the scan value for the token.
	 * 
     * @return the foreground color value for the token.
     */
    public Color getForeground( int token) {
		if ( (token >= 0) && (token < styles.length)) {
			Style s = styles[ token];
			return super.getForeground( s);
		}
		
		Color fg = javax.swing.UIManager.getColor("TextPane.foreground");
		return fg != null ? fg : Color.black;
    }

    /**
     * Fetch the font to use for a lexical token 
	 * with the given scan value.
     */
    public Font getFont( int token) {
	    if ( (token >= 0) && (token < styles.length)) {
		    Style s = styles[token];
		    return getFont( s);
		}
		
		return null;
    }

    /*
     * Checks to find out if the style is null.
     */
    public Font getFont( Style style) {
		Font font = plainFont;
		
        if ( style != null) {
			if ( StyleConstants.isItalic( style)) {
				font = italicFont;
			} else if ( StyleConstants.isBold( style)) {
				font = boldFont;
			}
    	}
    	
    	return font;
    }

    /*
     * Fixes a Null pointer exception ...
     */
    public Color getForeground( Style style) {
        if ( style != null) {
    	    return super.getForeground( style);
    	}
    	
    	return null;
    }

    /**
     * Fetches the attribute set to use for the given
     * scan code.  The set is stored in a table to
     * facilitate relatively fast access to use in 
     * conjunction with the scanner.
     */
    public Style getStyle( int token) {
		if ( (token >= 0) && (token < styles.length)) {
		    return styles[token];
		}
		
		return null;
    }
	
    
    public void cleanup() {
		finalize();
    }
	
	protected void finalize()  {
		styles = null;
		italicFont = null;
		plainFont = null;
		boldFont = null;
	}
}
