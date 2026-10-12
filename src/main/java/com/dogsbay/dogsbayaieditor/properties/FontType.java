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

package com.dogsbay.dogsbayaieditor.properties;

import java.awt.Color;
import java.awt.Font;

import com.dogsbay.xml.XElement;
import com.dogsbay.xml.properties.Properties;

/**
 * Handles the Xml Plus configuration document.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:56:13 $
 * @author Dogsbay
 */
public class FontType extends Properties {

	public static final String FONT_TYPE	= "font-type";
	
	private static final String NAME		= "name";
	private static final String STYLE		= "style";
	private static final String COLOR		= "color";

	private Font font = null;

	/**
	 * Creates the Font Property with initial values.
	 *
	 * @param name the name of the font-type.
	 * @param style the font style.
	 * @param color the font color.
	 */
	public FontType( String name, int style, Color color) {
		super( new XElement( FONT_TYPE));
		
		setName( name);
		setStyle( style);
		setColor( color);
	}
	
	/**
	 * Creates the Font Property with initial values.
	 *
	 * @param element the font-type element.
	 */
	public FontType( XElement element) {
		super( element);
	}

	/**
	 * Get the name of this font-type.
	 *
	 * @return the name of the type.
	 */
	public String getName() {
		return getText( NAME);
	}

	// Set the name of this font-type.
	private void setName( String name) {
		set( NAME, name);
	}

	// Set the base font for this type.
	public void setFont( Font font) {
		this.font = font.deriveFont( getStyle());
	}

	// Set the base font for this type.
	public Font getFont() {
		return font;
	}

	/**
	 * Get the font-style.
	 *
	 * @return the style of the font.
	 */
	public int getStyle() {
		return getInteger( STYLE);
	}

	/**
	 * Set the font style.
	 *
	 * @param style the font-style.
	 */
	public void setStyle( int style) {
		set( STYLE, style);
		
		if ( font != null) {
			this.font = font.deriveFont( getStyle());
		}
	}

	/**
	 * Get the color for this font-type.
	 *
	 * @return the color.
	 */
	public Color getColor() {
		return getColor( COLOR);
	}

	/**
	 * Set the color font property.
	 *
	 * @param color the font style color.
	 */
	public void setColor( Color color) {
		set( COLOR, color);
	}
}

