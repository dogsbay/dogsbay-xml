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
import java.awt.FontMetrics;
import java.awt.GraphicsEnvironment;

import javax.swing.UIManager;
import java.util.Vector;

import javax.swing.JTextArea;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.properties.Properties;
import com.dogsbay.xml.properties.PropertiesFile;

/**
 * Handles the Xml Plus configuration document.
 *
 * @version	$Revision: 1.4 $, $Date: 2004/11/03 10:00:31 $
 * @author Dogsbay
 */
public class TextPreferences extends PropertiesFile {

	public static final String TEXT_PREFERENCES		= "text-preferences";

	private Vector types	= null;

	public static final String CDATA			= "CDATA";
	public static final String COMMENT			= "Comment";
	public static final String ENTITY			= "Entity";
	public static final String SPECIAL			= "Special";
	public static final String PREFIX			= "Prefix";

	private static final String ANTIALIASING	= "antialiasing";

	public static final String NAMESPACE_NAME 	= "Namespace Name";
	public static final String NAMESPACE_VALUE	= "Namespace Value";
	public static final String NAMESPACE_PREFIX = PREFIX;

	public static final String ATTRIBUTE_NAME 	= "Attribute Name";
	public static final String ATTRIBUTE_VALUE	= "Attribute Value";
	public static final String ATTRIBUTE_PREFIX = PREFIX;

	public static final String ELEMENT_NAME 	= "Element Name";
	public static final String ELEMENT_VALUE	= "Element Value";
	public static final String ELEMENT_PREFIX 	= PREFIX;

	public static final String PI_NAME 		= "PI Name";
	public static final String PI_VALUE		= "PI Value";
	public static final String PI_TARGET	= "PI Target";

	public static final String STRING_VALUE 		= "DTD: String Value";
	public static final String ENTITY_VALUE 		= "DTD: Entity Reference";

	public static final String ENTITY_DECLARATION	= "DTD: ENTITY Declaration";
	public static final String ENTITY_NAME			= "DTD: Entity Name";
	public static final String ENTITY_TYPE 			= "DTD: Entity Type";

	public static final String ATTLIST_DECLARATION	= "DTD: ATTLIST Declaration";
	public static final String ATTLIST_NAME			= "DTD: Attribute Name";
	public static final String ATTLIST_TYPE			= "DTD: Attribute Type";
	public static final String ATTLIST_VALUE		= "DTD: Attribute Enumerated Value";
	public static final String ATTLIST_DEFAULT		= "DTD: Attribute Defaults (#REQUIRED, #IMPLIED, #FIXED)";

	public static final String ELEMENT_DECLARATION			= "DTD: ELEMENT Declaration";
	public static final String ELEMENT_DECLARATION_NAME		= "DTD: Element Name";
	public static final String ELEMENT_DECLARATION_TYPE		= "DTD: Element Type (EMPTY, ANY)";
	public static final String ELEMENT_DECLARATION_PCDATA	= "DTD: #PCDATA";
	public static final String ELEMENT_DECLARATION_OPERATOR	= "DTD: Element Operator";

	public static final String NOTATION_DECLARATION			= "DTD: NOTATION Declaration";
	public static final String NOTATION_DECLARATION_NAME	= "DTD: Notation Name";
	public static final String NOTATION_DECLARATION_TYPE	= "DTD: Notation Type (PUBLIC, SYSTEM)";

	public static final String DOCTYPE_DECLARATION			= "DTD: DOCTYPE Declaration";
	public static final String DOCTYPE_DECLARATION_TYPE		= "DTD: Doctype Type (PUBLIC, SYSTEM)";

	// Markdown token names (for syntax highlighting)
	public static final String MD_TEXT				= "Markdown: Text";
	public static final String MD_HEADER			= "Markdown: Header";
	public static final String MD_EMPHASIS			= "Markdown: Emphasis";
	public static final String MD_STRONG			= "Markdown: Strong";
	public static final String MD_CODE				= "Markdown: Inline Code";
	public static final String MD_CODE_BLOCK		= "Markdown: Code Block";
	public static final String MD_LINK				= "Markdown: Link";
	public static final String MD_URL				= "Markdown: URL";
	public static final String MD_IMAGE				= "Markdown: Image";
	public static final String MD_BLOCKQUOTE		= "Markdown: Blockquote";
	public static final String MD_LIST				= "Markdown: List";
	public static final String MD_RULE				= "Markdown: Rule";
	public static final String MD_TABLE				= "Markdown: Table";
	public static final String MD_STRIKETHROUGH		= "Markdown: Strikethrough";
	public static final String MD_TASK				= "Markdown: Task";
	public static final String MD_HTML				= "Markdown: HTML";
	public static final String MD_YAML				= "Markdown: YAML Front Matter";

	// AsciiDoc token names
	public static final String AD_TEXT				= "AsciiDoc: Text";
	public static final String AD_SECTION_TITLE		= "AsciiDoc: Section Title";
	public static final String AD_BOLD				= "AsciiDoc: Bold";
	public static final String AD_ITALIC			= "AsciiDoc: Italic";
	public static final String AD_MONOSPACE			= "AsciiDoc: Monospace";
	public static final String AD_LINK				= "AsciiDoc: Link";
	public static final String AD_XREF				= "AsciiDoc: Cross Reference";
	public static final String AD_IMAGE				= "AsciiDoc: Image";
	public static final String AD_LIST_MARKER		= "AsciiDoc: List Marker";
	public static final String AD_ADMONITION		= "AsciiDoc: Admonition";
	public static final String AD_BLOCK_DELIMITER	= "AsciiDoc: Block Delimiter";
	public static final String AD_ATTRIBUTE			= "AsciiDoc: Attribute";
	public static final String AD_MACRO				= "AsciiDoc: Macro";
	public static final String AD_COMMENT			= "AsciiDoc: Comment";
	public static final String AD_TABLE				= "AsciiDoc: Table";
	public static final String AD_PASSTHROUGH		= "AsciiDoc: Passthrough";
	public static final String AD_LITERAL			= "AsciiDoc: Literal";
	public static final String AD_HIGHLIGHT			= "AsciiDoc: Highlight";
	public static final String AD_BLOCK_ATTR		= "AsciiDoc: Block Attribute";
	public static final String AD_ATTR_REF			= "AsciiDoc: Attribute Reference";
	public static final String AD_BLOCK_TITLE		= "AsciiDoc: Block Title";
	public static final String AD_CALLOUT			= "AsciiDoc: Callout";
	public static final String AD_DESC_LIST			= "AsciiDoc: Description List";

	// ── XML light-theme colors (blue-based, consistent with dark theme hues) ──
	public static final Color DEFAULT_CDATA_COLOR		= new Color( 128, 128, 128);
	public static final Color DEFAULT_COMMENT_COLOR		= new Color( 0, 128, 0);
	public static final Color DEFAULT_ENTITY_COLOR		= new Color( 0, 128, 128);
	public static final Color DEFAULT_SPECIAL_COLOR		= new Color( 4, 81, 165);
	public static final Color DEFAULT_PREFIX_COLOR		= new Color( 4, 81, 165);

	public static final Color DEFAULT_NAMESPACE_NAME_COLOR	= new Color( 16, 118, 189);
	public static final Color DEFAULT_NAMESPACE_VALUE_COLOR	= new Color( 136, 57, 12);

	public static final Color DEFAULT_ATTRIBUTE_NAME_COLOR	= new Color( 16, 118, 189);
	public static final Color DEFAULT_ATTRIBUTE_VALUE_COLOR	= new Color( 136, 57, 12);

	public static final Color DEFAULT_ELEMENT_NAME_COLOR	= new Color( 4, 81, 165);
	public static final Color DEFAULT_ELEMENT_VALUE_COLOR	= new Color( 0, 0, 0);

	public static final Color PI_TARGET_COLOR 		= new Color( 4, 81, 165);
	public static final Color PI_NAME_COLOR 		= new Color( 16, 118, 189);
	public static final Color PI_VALUE_COLOR 		= new Color( 136, 57, 12);

	public static final Color STRING_VALUE_COLOR 		= new Color( 136, 57, 12);
	public static final Color ENTITY_VALUE_COLOR 		= new Color( 0, 128, 128);

	public static final Color ENTITY_DECLARATION_COLOR	= new Color( 4, 81, 165);
	public static final Color ENTITY_NAME_COLOR			= new Color( 16, 118, 189);
	public static final Color ENTITY_TYPE_COLOR 		= new Color( 0, 128, 128);

	public static final Color ATTLIST_DECLARATION_COLOR	= new Color( 4, 81, 165);
	public static final Color ATTLIST_NAME_COLOR		= new Color( 16, 118, 189);
	public static final Color ATTLIST_TYPE_COLOR		= new Color( 0, 128, 128);
	public static final Color ATTLIST_VALUE_COLOR		= new Color( 136, 57, 12);
	public static final Color ATTLIST_DEFAULT_COLOR		= new Color( 4, 81, 165);

	public static final Color ELEMENT_DECLARATION_COLOR				= new Color( 4, 81, 165);
	public static final Color ELEMENT_DECLARATION_NAME_COLOR		= new Color( 16, 118, 189);
	public static final Color ELEMENT_DECLARATION_TYPE_COLOR		= new Color( 0, 128, 128);
	public static final Color ELEMENT_DECLARATION_PCDATA_COLOR		= new Color( 4, 81, 165);
	public static final Color ELEMENT_DECLARATION_OPERATOR_COLOR	= new Color( 16, 118, 189);

	public static final Color NOTATION_DECLARATION_COLOR		= new Color( 4, 81, 165);
	public static final Color NOTATION_DECLARATION_NAME_COLOR	= new Color( 16, 118, 189);
	public static final Color NOTATION_DECLARATION_TYPE_COLOR	= new Color( 0, 128, 128);

	public static final Color DOCTYPE_DECLARATION_COLOR			= new Color( 4, 81, 165);
	public static final Color DOCTYPE_DECLARATION_TYPE_COLOR	= new Color( 16, 118, 189);

	// ── DTD/PI dark-theme colors (VS Code Dark+ inspired) ──
	public static final Color DARK_PI_TARGET_COLOR					= new Color( 86, 156, 214);
	public static final Color DARK_PI_NAME_COLOR					= new Color( 156, 220, 254);
	public static final Color DARK_PI_VALUE_COLOR					= new Color( 206, 145, 120);

	public static final Color DARK_STRING_VALUE_COLOR				= new Color( 206, 145, 120);
	public static final Color DARK_ENTITY_VALUE_COLOR				= new Color( 78, 201, 176);

	public static final Color DARK_ENTITY_DECLARATION_COLOR			= new Color( 86, 156, 214);
	public static final Color DARK_ENTITY_NAME_COLOR				= new Color( 156, 220, 254);
	public static final Color DARK_ENTITY_TYPE_COLOR				= new Color( 78, 201, 176);

	public static final Color DARK_ATTLIST_DECLARATION_COLOR		= new Color( 86, 156, 214);
	public static final Color DARK_ATTLIST_NAME_COLOR				= new Color( 156, 220, 254);
	public static final Color DARK_ATTLIST_TYPE_COLOR				= new Color( 78, 201, 176);
	public static final Color DARK_ATTLIST_VALUE_COLOR				= new Color( 206, 145, 120);
	public static final Color DARK_ATTLIST_DEFAULT_COLOR			= new Color( 86, 156, 214);

	public static final Color DARK_ELEMENT_DECLARATION_COLOR		= new Color( 86, 156, 214);
	public static final Color DARK_ELEMENT_DECLARATION_NAME_COLOR	= new Color( 156, 220, 254);
	public static final Color DARK_ELEMENT_DECLARATION_TYPE_COLOR	= new Color( 78, 201, 176);
	public static final Color DARK_ELEMENT_DECLARATION_PCDATA_COLOR	= new Color( 86, 156, 214);
	public static final Color DARK_ELEMENT_DECLARATION_OPERATOR_COLOR = new Color( 212, 212, 212);

	public static final Color DARK_NOTATION_DECLARATION_COLOR		= new Color( 86, 156, 214);
	public static final Color DARK_NOTATION_DECLARATION_NAME_COLOR	= new Color( 156, 220, 254);
	public static final Color DARK_NOTATION_DECLARATION_TYPE_COLOR	= new Color( 78, 201, 176);

	public static final Color DARK_DOCTYPE_DECLARATION_COLOR		= new Color( 86, 156, 214);
	public static final Color DARK_DOCTYPE_DECLARATION_TYPE_COLOR	= new Color( 156, 220, 254);

	// ── Markdown light-theme colors (blue-based, consistent with XML light theme) ──
	public static final Color DEFAULT_MD_TEXT_COLOR			= new Color( 0, 0, 0);
	public static final Color DEFAULT_MD_HEADER_COLOR		= new Color( 4, 81, 165);
	public static final Color DEFAULT_MD_EMPHASIS_COLOR		= new Color( 0, 0, 0);
	public static final Color DEFAULT_MD_STRONG_COLOR		= new Color( 0, 0, 0);
	public static final Color DEFAULT_MD_CODE_COLOR			= new Color( 136, 57, 12);
	public static final Color DEFAULT_MD_CODE_BLOCK_COLOR	= new Color( 136, 57, 12);
	public static final Color DEFAULT_MD_LINK_COLOR			= new Color( 4, 81, 165);
	public static final Color DEFAULT_MD_URL_COLOR			= new Color( 0, 128, 128);
	public static final Color DEFAULT_MD_IMAGE_COLOR			= new Color( 4, 81, 165);
	public static final Color DEFAULT_MD_BLOCKQUOTE_COLOR	= new Color( 0, 128, 0);
	public static final Color DEFAULT_MD_LIST_COLOR			= new Color( 4, 81, 165);
	public static final Color DEFAULT_MD_RULE_COLOR			= new Color( 128, 128, 128);
	public static final Color DEFAULT_MD_TABLE_COLOR			= new Color( 4, 81, 165);
	public static final Color DEFAULT_MD_STRIKETHROUGH_COLOR	= new Color( 128, 128, 128);
	public static final Color DEFAULT_MD_TASK_COLOR			= new Color( 4, 81, 165);
	public static final Color DEFAULT_MD_HTML_COLOR			= new Color( 128, 0, 0);
	public static final Color DEFAULT_MD_YAML_COLOR			= new Color( 128, 128, 128);

	// ── Markdown dark-theme colors (VS Code Dark+ inspired) ──
	public static final Color DARK_MD_TEXT_COLOR			= new Color( 212, 212, 212);
	public static final Color DARK_MD_HEADER_COLOR			= new Color( 86, 156, 214);
	public static final Color DARK_MD_EMPHASIS_COLOR		= new Color( 212, 212, 212);
	public static final Color DARK_MD_STRONG_COLOR			= new Color( 212, 212, 212);
	public static final Color DARK_MD_CODE_COLOR			= new Color( 206, 145, 120);
	public static final Color DARK_MD_CODE_BLOCK_COLOR		= new Color( 206, 145, 120);
	public static final Color DARK_MD_LINK_COLOR			= new Color( 86, 156, 214);
	public static final Color DARK_MD_URL_COLOR				= new Color( 78, 201, 176);
	public static final Color DARK_MD_IMAGE_COLOR			= new Color( 86, 156, 214);
	public static final Color DARK_MD_BLOCKQUOTE_COLOR		= new Color( 106, 153, 85);
	public static final Color DARK_MD_LIST_COLOR			= new Color( 86, 156, 214);
	public static final Color DARK_MD_RULE_COLOR			= new Color( 128, 128, 128);
	public static final Color DARK_MD_TABLE_COLOR			= new Color( 86, 156, 214);
	public static final Color DARK_MD_STRIKETHROUGH_COLOR	= new Color( 128, 128, 128);
	public static final Color DARK_MD_TASK_COLOR			= new Color( 86, 156, 214);
	public static final Color DARK_MD_HTML_COLOR			= new Color( 206, 145, 120);
	public static final Color DARK_MD_YAML_COLOR			= new Color( 128, 128, 128);

	// ── AsciiDoc light-theme colors ──
	public static final Color DEFAULT_AD_TEXT_COLOR			= new Color( 0, 0, 0);
	public static final Color DEFAULT_AD_SECTION_TITLE_COLOR	= new Color( 4, 81, 165);
	public static final Color DEFAULT_AD_BOLD_COLOR			= new Color( 0, 0, 0);
	public static final Color DEFAULT_AD_ITALIC_COLOR			= new Color( 0, 0, 0);
	public static final Color DEFAULT_AD_MONOSPACE_COLOR		= new Color( 136, 57, 12);
	public static final Color DEFAULT_AD_LINK_COLOR			= new Color( 4, 81, 165);
	public static final Color DEFAULT_AD_XREF_COLOR			= new Color( 4, 81, 165);
	public static final Color DEFAULT_AD_IMAGE_COLOR			= new Color( 128, 0, 128);
	public static final Color DEFAULT_AD_LIST_MARKER_COLOR		= new Color( 4, 81, 165);
	public static final Color DEFAULT_AD_ADMONITION_COLOR		= new Color( 200, 120, 0);
	public static final Color DEFAULT_AD_BLOCK_DELIMITER_COLOR	= new Color( 128, 128, 128);
	public static final Color DEFAULT_AD_ATTRIBUTE_COLOR		= new Color( 0, 128, 0);
	public static final Color DEFAULT_AD_MACRO_COLOR			= new Color( 128, 0, 128);
	public static final Color DEFAULT_AD_COMMENT_COLOR			= new Color( 0, 128, 0);
	public static final Color DEFAULT_AD_TABLE_COLOR			= new Color( 4, 81, 165);
	public static final Color DEFAULT_AD_PASSTHROUGH_COLOR		= new Color( 128, 128, 128);
	public static final Color DEFAULT_AD_LITERAL_COLOR			= new Color( 136, 57, 12);
	public static final Color DEFAULT_AD_HIGHLIGHT_COLOR		= new Color( 200, 120, 0);
	public static final Color DEFAULT_AD_BLOCK_ATTR_COLOR		= new Color( 128, 128, 128);
	public static final Color DEFAULT_AD_ATTR_REF_COLOR		= new Color( 0, 128, 128);
	public static final Color DEFAULT_AD_BLOCK_TITLE_COLOR		= new Color( 4, 81, 165);
	public static final Color DEFAULT_AD_CALLOUT_COLOR			= new Color( 200, 120, 0);
	public static final Color DEFAULT_AD_DESC_LIST_COLOR		= new Color( 4, 81, 165);

	// ── AsciiDoc dark-theme colors ──
	public static final Color DARK_AD_TEXT_COLOR				= new Color( 212, 212, 212);
	public static final Color DARK_AD_SECTION_TITLE_COLOR		= new Color( 86, 156, 214);
	public static final Color DARK_AD_BOLD_COLOR				= new Color( 212, 212, 212);
	public static final Color DARK_AD_ITALIC_COLOR				= new Color( 212, 212, 212);
	public static final Color DARK_AD_MONOSPACE_COLOR			= new Color( 206, 145, 120);
	public static final Color DARK_AD_LINK_COLOR				= new Color( 86, 156, 214);
	public static final Color DARK_AD_XREF_COLOR				= new Color( 86, 156, 214);
	public static final Color DARK_AD_IMAGE_COLOR				= new Color( 197, 134, 192);
	public static final Color DARK_AD_LIST_MARKER_COLOR		= new Color( 86, 156, 214);
	public static final Color DARK_AD_ADMONITION_COLOR			= new Color( 220, 170, 100);
	public static final Color DARK_AD_BLOCK_DELIMITER_COLOR	= new Color( 128, 128, 128);
	public static final Color DARK_AD_ATTRIBUTE_COLOR			= new Color( 106, 153, 85);
	public static final Color DARK_AD_MACRO_COLOR				= new Color( 197, 134, 192);
	public static final Color DARK_AD_COMMENT_COLOR			= new Color( 106, 153, 85);
	public static final Color DARK_AD_TABLE_COLOR				= new Color( 86, 156, 214);
	public static final Color DARK_AD_PASSTHROUGH_COLOR		= new Color( 128, 128, 128);
	public static final Color DARK_AD_LITERAL_COLOR			= new Color( 206, 145, 120);
	public static final Color DARK_AD_HIGHLIGHT_COLOR			= new Color( 220, 170, 100);
	public static final Color DARK_AD_BLOCK_ATTR_COLOR			= new Color( 128, 128, 128);
	public static final Color DARK_AD_ATTR_REF_COLOR			= new Color( 78, 201, 176);
	public static final Color DARK_AD_BLOCK_TITLE_COLOR		= new Color( 86, 156, 214);
	public static final Color DARK_AD_CALLOUT_COLOR			= new Color( 220, 170, 100);
	public static final Color DARK_AD_DESC_LIST_COLOR			= new Color( 86, 156, 214);

	// ── XML dark-theme colors (VS Code Dark+ inspired) ──
	public static final Color DARK_ELEMENT_NAME_COLOR		= new Color( 86, 156, 214);
	public static final Color DARK_ELEMENT_VALUE_COLOR		= new Color( 212, 212, 212);
	public static final Color DARK_ATTRIBUTE_NAME_COLOR		= new Color( 156, 220, 254);
	public static final Color DARK_ATTRIBUTE_VALUE_COLOR	= new Color( 206, 145, 120);
	public static final Color DARK_PREFIX_COLOR				= new Color( 86, 156, 214);
	public static final Color DARK_NAMESPACE_NAME_COLOR		= new Color( 206, 145, 120);
	public static final Color DARK_NAMESPACE_VALUE_COLOR	= new Color( 206, 145, 120);
	public static final Color DARK_CDATA_COLOR				= new Color( 128, 128, 128);
	public static final Color DARK_COMMENT_COLOR			= new Color( 106, 153, 85);
	public static final Color DARK_ENTITY_COLOR				= new Color( 78, 201, 176);
	public static final Color DARK_SPECIAL_COLOR			= new Color( 86, 156, 214);

	public static final int DEFAULT_CDATA_STYLE			= Font.PLAIN;
	public static final int DEFAULT_COMMENT_STYLE		= Font.PLAIN;
	public static final int DEFAULT_ENTITY_STYLE		= Font.PLAIN;
	public static final int DEFAULT_SPECIAL_STYLE		= Font.PLAIN;
	public static final int DEFAULT_PREFIX_STYLE		= Font.PLAIN;

	public static final int DEFAULT_NAMESPACE_NAME_STYLE	= Font.PLAIN;
	public static final int DEFAULT_NAMESPACE_VALUE_STYLE	= Font.PLAIN;

	public static final int DEFAULT_ATTRIBUTE_NAME_STYLE	= Font.PLAIN;
	public static final int DEFAULT_ATTRIBUTE_VALUE_STYLE	= Font.PLAIN;

	public static final int DEFAULT_ELEMENT_NAME_STYLE		= Font.PLAIN;
	public static final int DEFAULT_ELEMENT_VALUE_STYLE		= Font.PLAIN;

	public static final int DEFAULT_MD_TEXT_STYLE			= Font.PLAIN;
	public static final int DEFAULT_MD_HEADER_STYLE			= Font.BOLD;
	public static final int DEFAULT_MD_EMPHASIS_STYLE		= Font.ITALIC;
	public static final int DEFAULT_MD_STRONG_STYLE			= Font.BOLD;
	public static final int DEFAULT_MD_CODE_STYLE			= Font.PLAIN;
	public static final int DEFAULT_MD_CODE_BLOCK_STYLE		= Font.PLAIN;
	public static final int DEFAULT_MD_LINK_STYLE			= Font.PLAIN;
	public static final int DEFAULT_MD_URL_STYLE			= Font.PLAIN;
	public static final int DEFAULT_MD_IMAGE_STYLE			= Font.PLAIN;
	public static final int DEFAULT_MD_BLOCKQUOTE_STYLE		= Font.ITALIC;
	public static final int DEFAULT_MD_LIST_STYLE			= Font.PLAIN;
	public static final int DEFAULT_MD_RULE_STYLE			= Font.PLAIN;
	public static final int DEFAULT_MD_TABLE_STYLE			= Font.PLAIN;
	public static final int DEFAULT_MD_STRIKETHROUGH_STYLE	= Font.PLAIN;
	public static final int DEFAULT_MD_TASK_STYLE			= Font.PLAIN;
	public static final int DEFAULT_MD_HTML_STYLE			= Font.PLAIN;
	public static final int DEFAULT_MD_YAML_STYLE			= Font.PLAIN;

	public static final int DEFAULT_AD_TEXT_STYLE			= Font.PLAIN;
	public static final int DEFAULT_AD_SECTION_TITLE_STYLE	= Font.BOLD;
	public static final int DEFAULT_AD_BOLD_STYLE			= Font.BOLD;
	public static final int DEFAULT_AD_ITALIC_STYLE			= Font.ITALIC;
	public static final int DEFAULT_AD_MONOSPACE_STYLE		= Font.PLAIN;
	public static final int DEFAULT_AD_LINK_STYLE			= Font.PLAIN;
	public static final int DEFAULT_AD_XREF_STYLE			= Font.PLAIN;
	public static final int DEFAULT_AD_IMAGE_STYLE			= Font.PLAIN;
	public static final int DEFAULT_AD_LIST_MARKER_STYLE	= Font.PLAIN;
	public static final int DEFAULT_AD_ADMONITION_STYLE		= Font.BOLD;
	public static final int DEFAULT_AD_BLOCK_DELIMITER_STYLE = Font.PLAIN;
	public static final int DEFAULT_AD_ATTRIBUTE_STYLE		= Font.PLAIN;
	public static final int DEFAULT_AD_MACRO_STYLE			= Font.PLAIN;
	public static final int DEFAULT_AD_COMMENT_STYLE		= Font.ITALIC;
	public static final int DEFAULT_AD_TABLE_STYLE			= Font.PLAIN;
	public static final int DEFAULT_AD_PASSTHROUGH_STYLE	= Font.PLAIN;
	public static final int DEFAULT_AD_LITERAL_STYLE		= Font.PLAIN;
	public static final int DEFAULT_AD_HIGHLIGHT_STYLE		= Font.PLAIN;
	public static final int DEFAULT_AD_BLOCK_ATTR_STYLE	= Font.PLAIN;
	public static final int DEFAULT_AD_ATTR_REF_STYLE		= Font.PLAIN;
	public static final int DEFAULT_AD_BLOCK_TITLE_STYLE	= Font.BOLD;
	public static final int DEFAULT_AD_CALLOUT_STYLE		= Font.BOLD;
	public static final int DEFAULT_AD_DESC_LIST_STYLE		= Font.BOLD;

	private static Font defaultFont	= null;

	private static Font font					= null;
	private static TextPreferences preferences	= null;

	public static final int DEFAULT_TAB_SIZE	= 4;

	private static final String SPACES		= "spaces";

	private static final String FONT_NAME	= "font-name";
	private static final String FONT_SIZE	= "font-size";
	private static final String FONT_STYLE	= "font-style";

	private static final String CONVERT_TAB	= "convert-tab";
	private static final String THEME_DARK	= "theme-dark";

	/**
	 * Creates the Font Property with initial values.
	 *
	 * @param element the font-type element.
	 */
	/**
	 * A section of the settings document, rather than a file of its own.
	 *
	 * <p>Splitting these out gave one logical thing four files, split by which
	 * class happened to extend PropertiesFile rather than by anything a reader
	 * would recognise.
	 */
	public TextPreferences(com.dogsbay.xml.XElement element) {
		super(null, element);
		initialise();
	}

	public TextPreferences(String fileName, String rootName) {
		super(loadPropertiesFile(fileName, rootName));
		initialise();
	}

	/**
	 * Fill in what has not been set, and publish this as the one instance the
	 * static accessors read.
	 *
	 * <p>Shared by both constructors: a section read from the settings document
	 * needs the same defaults as one read from a file of its own, and skipping
	 * them left the static null and every caller of {@code getTabString} with a
	 * NullPointerException on the first save.
	 */
	private void initialise() {
		
		if ( getFontTypes().size() == 0) {
			setFont( getDefaultFont());
			boolean dark = isDarkTheme();

			// XML defaults — theme-aware
			addFontType( new FontType( ELEMENT_NAME, DEFAULT_ELEMENT_NAME_STYLE, dark ? DARK_ELEMENT_NAME_COLOR : DEFAULT_ELEMENT_NAME_COLOR));
			addFontType( new FontType( ELEMENT_VALUE, DEFAULT_ELEMENT_VALUE_STYLE, dark ? DARK_ELEMENT_VALUE_COLOR : DEFAULT_ELEMENT_VALUE_COLOR));
			addFontType( new FontType( ATTRIBUTE_NAME, DEFAULT_ATTRIBUTE_NAME_STYLE, dark ? DARK_ATTRIBUTE_NAME_COLOR : DEFAULT_ATTRIBUTE_NAME_COLOR));
			addFontType( new FontType( ATTRIBUTE_VALUE, DEFAULT_ATTRIBUTE_VALUE_STYLE, dark ? DARK_ATTRIBUTE_VALUE_COLOR : DEFAULT_ATTRIBUTE_VALUE_COLOR));
			addFontType( new FontType( PREFIX, DEFAULT_PREFIX_STYLE, dark ? DARK_PREFIX_COLOR : DEFAULT_PREFIX_COLOR));
			addFontType( new FontType( NAMESPACE_NAME, DEFAULT_NAMESPACE_NAME_STYLE, dark ? DARK_NAMESPACE_NAME_COLOR : DEFAULT_NAMESPACE_NAME_COLOR));
			addFontType( new FontType( NAMESPACE_VALUE, DEFAULT_NAMESPACE_VALUE_STYLE, dark ? DARK_NAMESPACE_VALUE_COLOR : DEFAULT_NAMESPACE_VALUE_COLOR));

			addFontType( new FontType( CDATA, DEFAULT_CDATA_STYLE, dark ? DARK_CDATA_COLOR : DEFAULT_CDATA_COLOR));
			addFontType( new FontType( COMMENT, DEFAULT_COMMENT_STYLE, dark ? DARK_COMMENT_COLOR : DEFAULT_COMMENT_COLOR));
			addFontType( new FontType( ENTITY, DEFAULT_ENTITY_STYLE, dark ? DARK_ENTITY_COLOR : DEFAULT_ENTITY_COLOR));
			addFontType( new FontType( SPECIAL, DEFAULT_SPECIAL_STYLE, dark ? DARK_SPECIAL_COLOR : DEFAULT_SPECIAL_COLOR));

			addFontType( new FontType( PI_TARGET, Font.PLAIN, dark ? DARK_PI_TARGET_COLOR : PI_TARGET_COLOR));
			addFontType( new FontType( PI_NAME, Font.PLAIN, dark ? DARK_PI_NAME_COLOR : PI_NAME_COLOR));
			addFontType( new FontType( PI_VALUE, Font.PLAIN, dark ? DARK_PI_VALUE_COLOR : PI_VALUE_COLOR));

			addFontType( new FontType( STRING_VALUE, Font.PLAIN, dark ? DARK_STRING_VALUE_COLOR : STRING_VALUE_COLOR));
			addFontType( new FontType( ENTITY_VALUE, Font.PLAIN, dark ? DARK_ENTITY_VALUE_COLOR : ENTITY_VALUE_COLOR));
			addFontType( new FontType( ENTITY_DECLARATION, Font.PLAIN, dark ? DARK_ENTITY_DECLARATION_COLOR : ENTITY_DECLARATION_COLOR));
			addFontType( new FontType( ENTITY_NAME, Font.PLAIN, dark ? DARK_ENTITY_NAME_COLOR : ENTITY_NAME_COLOR));
			addFontType( new FontType( ENTITY_TYPE, Font.PLAIN, dark ? DARK_ENTITY_TYPE_COLOR : ENTITY_TYPE_COLOR));
			addFontType( new FontType( ATTLIST_DECLARATION, Font.PLAIN, dark ? DARK_ATTLIST_DECLARATION_COLOR : ATTLIST_DECLARATION_COLOR));
			addFontType( new FontType( ATTLIST_NAME, Font.PLAIN, dark ? DARK_ATTLIST_NAME_COLOR : ATTLIST_NAME_COLOR));
			addFontType( new FontType( ATTLIST_TYPE, Font.PLAIN, dark ? DARK_ATTLIST_TYPE_COLOR : ATTLIST_TYPE_COLOR));

			addFontType( new FontType( ATTLIST_VALUE, Font.PLAIN, dark ? DARK_ATTLIST_VALUE_COLOR : ATTLIST_VALUE_COLOR));
			addFontType( new FontType( ATTLIST_DEFAULT, Font.PLAIN, dark ? DARK_ATTLIST_DEFAULT_COLOR : ATTLIST_DEFAULT_COLOR));
			addFontType( new FontType( ELEMENT_DECLARATION, Font.PLAIN, dark ? DARK_ELEMENT_DECLARATION_COLOR : ELEMENT_DECLARATION_COLOR));
			addFontType( new FontType( ELEMENT_DECLARATION_NAME, Font.PLAIN, dark ? DARK_ELEMENT_DECLARATION_NAME_COLOR : ELEMENT_DECLARATION_NAME_COLOR));
			addFontType( new FontType( ELEMENT_DECLARATION_TYPE, Font.PLAIN, dark ? DARK_ELEMENT_DECLARATION_TYPE_COLOR : ELEMENT_DECLARATION_TYPE_COLOR));
			addFontType( new FontType( ELEMENT_DECLARATION_PCDATA, Font.PLAIN, dark ? DARK_ELEMENT_DECLARATION_PCDATA_COLOR : ELEMENT_DECLARATION_PCDATA_COLOR));
			addFontType( new FontType( ELEMENT_DECLARATION_OPERATOR, Font.PLAIN, dark ? DARK_ELEMENT_DECLARATION_OPERATOR_COLOR : ELEMENT_DECLARATION_OPERATOR_COLOR));
			addFontType( new FontType( NOTATION_DECLARATION, Font.PLAIN, dark ? DARK_NOTATION_DECLARATION_COLOR : NOTATION_DECLARATION_COLOR));
			addFontType( new FontType( NOTATION_DECLARATION_NAME, Font.PLAIN, dark ? DARK_NOTATION_DECLARATION_NAME_COLOR : NOTATION_DECLARATION_NAME_COLOR));

			addFontType( new FontType( NOTATION_DECLARATION_TYPE, Font.PLAIN, dark ? DARK_NOTATION_DECLARATION_TYPE_COLOR : NOTATION_DECLARATION_TYPE_COLOR));
			addFontType( new FontType( DOCTYPE_DECLARATION, Font.PLAIN, dark ? DARK_DOCTYPE_DECLARATION_COLOR : DOCTYPE_DECLARATION_COLOR));
			addFontType( new FontType( DOCTYPE_DECLARATION_TYPE, Font.PLAIN, dark ? DARK_DOCTYPE_DECLARATION_TYPE_COLOR : DOCTYPE_DECLARATION_TYPE_COLOR));

			// Markdown defaults — theme-aware
			addFontType( new FontType( MD_TEXT, DEFAULT_MD_TEXT_STYLE, dark ? DARK_MD_TEXT_COLOR : DEFAULT_MD_TEXT_COLOR));
			addFontType( new FontType( MD_HEADER, DEFAULT_MD_HEADER_STYLE, dark ? DARK_MD_HEADER_COLOR : DEFAULT_MD_HEADER_COLOR));
			addFontType( new FontType( MD_EMPHASIS, DEFAULT_MD_EMPHASIS_STYLE, dark ? DARK_MD_EMPHASIS_COLOR : DEFAULT_MD_EMPHASIS_COLOR));
			addFontType( new FontType( MD_STRONG, DEFAULT_MD_STRONG_STYLE, dark ? DARK_MD_STRONG_COLOR : DEFAULT_MD_STRONG_COLOR));
			addFontType( new FontType( MD_CODE, DEFAULT_MD_CODE_STYLE, dark ? DARK_MD_CODE_COLOR : DEFAULT_MD_CODE_COLOR));
			addFontType( new FontType( MD_CODE_BLOCK, DEFAULT_MD_CODE_BLOCK_STYLE, dark ? DARK_MD_CODE_BLOCK_COLOR : DEFAULT_MD_CODE_BLOCK_COLOR));
			addFontType( new FontType( MD_LINK, DEFAULT_MD_LINK_STYLE, dark ? DARK_MD_LINK_COLOR : DEFAULT_MD_LINK_COLOR));
			addFontType( new FontType( MD_URL, DEFAULT_MD_URL_STYLE, dark ? DARK_MD_URL_COLOR : DEFAULT_MD_URL_COLOR));
			addFontType( new FontType( MD_IMAGE, DEFAULT_MD_IMAGE_STYLE, dark ? DARK_MD_IMAGE_COLOR : DEFAULT_MD_IMAGE_COLOR));
			addFontType( new FontType( MD_BLOCKQUOTE, DEFAULT_MD_BLOCKQUOTE_STYLE, dark ? DARK_MD_BLOCKQUOTE_COLOR : DEFAULT_MD_BLOCKQUOTE_COLOR));
			addFontType( new FontType( MD_LIST, DEFAULT_MD_LIST_STYLE, dark ? DARK_MD_LIST_COLOR : DEFAULT_MD_LIST_COLOR));
			addFontType( new FontType( MD_RULE, DEFAULT_MD_RULE_STYLE, dark ? DARK_MD_RULE_COLOR : DEFAULT_MD_RULE_COLOR));
			addFontType( new FontType( MD_TABLE, DEFAULT_MD_TABLE_STYLE, dark ? DARK_MD_TABLE_COLOR : DEFAULT_MD_TABLE_COLOR));
			addFontType( new FontType( MD_STRIKETHROUGH, DEFAULT_MD_STRIKETHROUGH_STYLE, dark ? DARK_MD_STRIKETHROUGH_COLOR : DEFAULT_MD_STRIKETHROUGH_COLOR));
			addFontType( new FontType( MD_TASK, DEFAULT_MD_TASK_STYLE, dark ? DARK_MD_TASK_COLOR : DEFAULT_MD_TASK_COLOR));
			addFontType( new FontType( MD_HTML, DEFAULT_MD_HTML_STYLE, dark ? DARK_MD_HTML_COLOR : DEFAULT_MD_HTML_COLOR));
			addFontType( new FontType( MD_YAML, DEFAULT_MD_YAML_STYLE, dark ? DARK_MD_YAML_COLOR : DEFAULT_MD_YAML_COLOR));

			// AsciiDoc defaults — theme-aware
			addFontType( new FontType( AD_TEXT, DEFAULT_AD_TEXT_STYLE, dark ? DARK_AD_TEXT_COLOR : DEFAULT_AD_TEXT_COLOR));
			addFontType( new FontType( AD_SECTION_TITLE, DEFAULT_AD_SECTION_TITLE_STYLE, dark ? DARK_AD_SECTION_TITLE_COLOR : DEFAULT_AD_SECTION_TITLE_COLOR));
			addFontType( new FontType( AD_BOLD, DEFAULT_AD_BOLD_STYLE, dark ? DARK_AD_BOLD_COLOR : DEFAULT_AD_BOLD_COLOR));
			addFontType( new FontType( AD_ITALIC, DEFAULT_AD_ITALIC_STYLE, dark ? DARK_AD_ITALIC_COLOR : DEFAULT_AD_ITALIC_COLOR));
			addFontType( new FontType( AD_MONOSPACE, DEFAULT_AD_MONOSPACE_STYLE, dark ? DARK_AD_MONOSPACE_COLOR : DEFAULT_AD_MONOSPACE_COLOR));
			addFontType( new FontType( AD_LINK, DEFAULT_AD_LINK_STYLE, dark ? DARK_AD_LINK_COLOR : DEFAULT_AD_LINK_COLOR));
			addFontType( new FontType( AD_XREF, DEFAULT_AD_XREF_STYLE, dark ? DARK_AD_XREF_COLOR : DEFAULT_AD_XREF_COLOR));
			addFontType( new FontType( AD_IMAGE, DEFAULT_AD_IMAGE_STYLE, dark ? DARK_AD_IMAGE_COLOR : DEFAULT_AD_IMAGE_COLOR));
			addFontType( new FontType( AD_LIST_MARKER, DEFAULT_AD_LIST_MARKER_STYLE, dark ? DARK_AD_LIST_MARKER_COLOR : DEFAULT_AD_LIST_MARKER_COLOR));
			addFontType( new FontType( AD_ADMONITION, DEFAULT_AD_ADMONITION_STYLE, dark ? DARK_AD_ADMONITION_COLOR : DEFAULT_AD_ADMONITION_COLOR));
			addFontType( new FontType( AD_BLOCK_DELIMITER, DEFAULT_AD_BLOCK_DELIMITER_STYLE, dark ? DARK_AD_BLOCK_DELIMITER_COLOR : DEFAULT_AD_BLOCK_DELIMITER_COLOR));
			addFontType( new FontType( AD_ATTRIBUTE, DEFAULT_AD_ATTRIBUTE_STYLE, dark ? DARK_AD_ATTRIBUTE_COLOR : DEFAULT_AD_ATTRIBUTE_COLOR));
			addFontType( new FontType( AD_MACRO, DEFAULT_AD_MACRO_STYLE, dark ? DARK_AD_MACRO_COLOR : DEFAULT_AD_MACRO_COLOR));
			addFontType( new FontType( AD_COMMENT, DEFAULT_AD_COMMENT_STYLE, dark ? DARK_AD_COMMENT_COLOR : DEFAULT_AD_COMMENT_COLOR));
			addFontType( new FontType( AD_TABLE, DEFAULT_AD_TABLE_STYLE, dark ? DARK_AD_TABLE_COLOR : DEFAULT_AD_TABLE_COLOR));
			addFontType( new FontType( AD_PASSTHROUGH, DEFAULT_AD_PASSTHROUGH_STYLE, dark ? DARK_AD_PASSTHROUGH_COLOR : DEFAULT_AD_PASSTHROUGH_COLOR));
			addFontType( new FontType( AD_LITERAL, DEFAULT_AD_LITERAL_STYLE, dark ? DARK_AD_LITERAL_COLOR : DEFAULT_AD_LITERAL_COLOR));
			addFontType( new FontType( AD_HIGHLIGHT, DEFAULT_AD_HIGHLIGHT_STYLE, dark ? DARK_AD_HIGHLIGHT_COLOR : DEFAULT_AD_HIGHLIGHT_COLOR));
			addFontType( new FontType( AD_BLOCK_ATTR, DEFAULT_AD_BLOCK_ATTR_STYLE, dark ? DARK_AD_BLOCK_ATTR_COLOR : DEFAULT_AD_BLOCK_ATTR_COLOR));
			addFontType( new FontType( AD_ATTR_REF, DEFAULT_AD_ATTR_REF_STYLE, dark ? DARK_AD_ATTR_REF_COLOR : DEFAULT_AD_ATTR_REF_COLOR));
			addFontType( new FontType( AD_BLOCK_TITLE, DEFAULT_AD_BLOCK_TITLE_STYLE, dark ? DARK_AD_BLOCK_TITLE_COLOR : DEFAULT_AD_BLOCK_TITLE_COLOR));
			addFontType( new FontType( AD_CALLOUT, DEFAULT_AD_CALLOUT_STYLE, dark ? DARK_AD_CALLOUT_COLOR : DEFAULT_AD_CALLOUT_COLOR));
			addFontType( new FontType( AD_DESC_LIST, DEFAULT_AD_DESC_LIST_STYLE, dark ? DARK_AD_DESC_LIST_COLOR : DEFAULT_AD_DESC_LIST_COLOR));

			types = null;
			set( THEME_DARK, isDarkTheme());
		}

		font = resolveSavedFont( getName(), getStyle(), getSize());

		Vector fonts = getFontTypes();
		
		for ( int i = 0; i < fonts.size(); i++) {
			((FontType)fonts.elementAt(i)).setFont( getFont());
		}
		
		preferences = this;
		}

	/**
	 * Get the default font.
	 *
	 * @return the default font.
	 */
	/**
	 * Resolve the saved editor font safely. Bundled families are derived from their
	 * registered instance (never the ambiguous {@code new Font(family)}, which can pick a
	 * conflicting system copy and overlap glyphs). A saved "Fira Code" — removed because it
	 * overlaps in the fixed-advance editor — migrates to the clean default.
	 */
	private static Font resolveSavedFont( String name, int style, int size) {
		int sz = size > 0 ? size : 12;
		// Fira Code was removed (its glyphs overlap in the fixed-advance editor) — migrate
		// any saved selection to the clean default.
		if ( name == null || name.isBlank() || "Fira Code".equalsIgnoreCase( name)) {
			return getDefaultFont().deriveFont( style, (float) sz);
		}
		// Bundled families: derive from the registered instance (the ambiguous
		// new Font(family) can pick a conflicting system copy and overlap glyphs).
		com.dogsbay.dogsbayaieditor.FontManager fm =
				com.dogsbay.dogsbayaieditor.FontManager.getInstance();
		if ( fm.hasBundledFont( name)) {
			return fm.createFont( name, sz).deriveFont( style, (float) sz);
		}
		// Any other installed family the user explicitly chose → honor it directly,
		// rather than forcing the logical Monospaced face.
		return new Font( name, style, sz);
	}

	public static Font getDefaultFont() {
		if ( defaultFont == null) {
			// Default to a BUNDLED monospace, resolved via FontManager to its registered
			// instance (deterministic + genuinely fixed-width). Two traps this avoids:
			//   • the logical "Monospaced"/"Dialog" font can map to a PROPORTIONAL family
			//     on some systems (fontconfig), which drifts the caret; and
			//   • `new Font("JetBrains Mono")` is ambiguous when that family is BOTH bundled
			//     and system-installed, resolving to a conflicting instance whose glyphs
			//     overlap. Source Code Pro is preferred for a clean, ligature-free default.
			com.dogsbay.dogsbayaieditor.FontManager fm =
					com.dogsbay.dogsbayaieditor.FontManager.getInstance();
			for ( String pref : new String[] { "Source Code Pro", "JetBrains Mono" }) {
				if ( fm.hasBundledFont( pref)) {
					defaultFont = fm.createFont( pref, 12);
					break;
				}
			}
			if ( defaultFont == null) {
				// No hardcoded family matched (e.g. a JDK reports the bundled family
				// under a different name). Fall back to the first genuinely-bundled
				// font FontManager lists (it sorts bundled monospaces first) before
				// the logical Monospaced face, which fontconfig can map to a
				// proportional Dialog and reintroduce the caret drift.
				java.util.List<String> available = fm.getAvailableFonts();
				if ( !available.isEmpty() && fm.hasBundledFont( available.get( 0))) {
					defaultFont = fm.createFont( available.get( 0), 12);
				} else {
					defaultFont = new Font( Font.MONOSPACED, Font.PLAIN, 12);
				}
			}
		}

		return defaultFont;
	}

	/**
	 * Get the name of this font-type.
	 *
	 * @return the name of the type.
	 */
	public static Font getBaseFont() {
		if ( preferences != null) {
			return preferences.getFont();
		} else {
			return getDefaultFont();
		}
	}

	/**
	 * Get the name of this font-type.
	 *
	 * @return the name of the type.
	 */
	public Font getFont() {
		return font;
	}

	/**
	 * Get the name of this font-type.
	 *
	 * @return the name of the type.
	 */
	public void setFont( Font f) {
		font = f;
		
		setName( font.getName());
		setStyle( font.getStyle());
		setSize( font.getSize());
		
		Vector fonts = getFontTypes();
		
		for ( int i = 0; i < fonts.size(); i++) {
			((FontType)fonts.elementAt(i)).setStyle( font.getStyle());
			((FontType)fonts.elementAt(i)).setFont( font);
		}
	}

	// Set/Get the name of this font-type.
	private void setName( String name) {
		set( FONT_NAME, name);
	}

	private String getName() {
		return getText( FONT_NAME);
	}

	// Set/Get the style of this font-type.
	private void setStyle( int style) {
		set( FONT_STYLE, style);
	}

	private int getStyle() {
		return getInteger( FONT_STYLE);
	}

	// Set/Get the size of this font-type.
	private void setSize( int size) {
		set( FONT_SIZE, size);
	}

	private int getSize() {
		return getInteger( FONT_SIZE);
	}

	/**
	 * Set the number of spaces to substitute for a tab.
	 *
	 * @param spaces the number of spaces.
	 */
	public void setSpaces( int spaces) {
		set( SPACES, spaces);
	}

	/**
	 * Gets the number of spaces to substitute for a tab.
	 *
	 * @return the number of spaces.
	 */
	public static int getTabSize() {
		return preferences.getSpaces();
	}

	public static boolean isAntialiasing() {
		return preferences.getBoolean( ANTIALIASING, true);
	}

	public void setAntialiasing( boolean enabled) {
		set( ANTIALIASING, enabled);
	}

	/**
	 * Gets the number of spaces to substitute for a tab.
	 *
	 * @return the number of spaces.
	 */
	public int getSpaces() {
		return getInteger( SPACES, DEFAULT_TAB_SIZE);
	}

	/**
	 * Gets the string for the tab, either a string of 
	 * spaces or one tab.
	 *
	 * @return the tab string.
	 */
	public static String getTabString() {
		if ( isConvertTab()) {
			int size = getTabSize();
			char[] chars = new char[size];
			
			for ( int i = 0; i < chars.length; i++) {
				chars[i] = ' ';
			}
			
			return new String( chars);
		}

		return "\t";
	}

	/**
	 * Gets the number of spaces to substitute for a tab.
	 *
	 * @return the number of spaces.
	 */
	public boolean convertTab() {
		return getBoolean( CONVERT_TAB, false);
	}

	public static boolean isConvertTab() {
		return preferences.convertTab();
	}

	public void setConvertTab( boolean enabled) {
		set( CONVERT_TAB, enabled);
	}

	/**
	 * Returns the font-type list.
	 *
	 * @return the font-types.
	 */
	/**
	 * Checks if the current L&F theme differs from the colors in use,
	 * and resets them if needed. Should be called after L&F is set.
	 */
	public void updateColorsForCurrentTheme() {
		boolean currentDark = isDarkTheme();
		if ( currentDark != getBoolean( THEME_DARK, false)) {
			resetColorsForCurrentTheme( currentDark);
			set( THEME_DARK, currentDark);
		}
	}

	/**
	 * Resets all font-type colors to their defaults for the given theme.
	 * Called when the theme (light/dark) has changed since colors were last saved.
	 */
	private void resetColorsForCurrentTheme( boolean dark) {
		Vector fonts = getFontTypes();
		for ( int i = 0; i < fonts.size(); i++) {
			FontType ft = (FontType) fonts.elementAt(i);
			Color c = getDefaultColorForTheme( ft.getName(), dark);
			if ( c != null) {
				ft.setColor( c);
			}
		}
		types = null; // force reload
	}

	/**
	 * Returns the default color for a given font-type name and theme.
	 */
	private static Color getDefaultColorForTheme( String name, boolean dark) {
		switch ( name) {
			// XML tokens
			case ELEMENT_NAME:		return dark ? DARK_ELEMENT_NAME_COLOR : DEFAULT_ELEMENT_NAME_COLOR;
			case ELEMENT_VALUE:		return dark ? DARK_ELEMENT_VALUE_COLOR : DEFAULT_ELEMENT_VALUE_COLOR;
			case ATTRIBUTE_NAME:	return dark ? DARK_ATTRIBUTE_NAME_COLOR : DEFAULT_ATTRIBUTE_NAME_COLOR;
			case ATTRIBUTE_VALUE:	return dark ? DARK_ATTRIBUTE_VALUE_COLOR : DEFAULT_ATTRIBUTE_VALUE_COLOR;
			case PREFIX:			return dark ? DARK_PREFIX_COLOR : DEFAULT_PREFIX_COLOR;
			case NAMESPACE_NAME:	return dark ? DARK_NAMESPACE_NAME_COLOR : DEFAULT_NAMESPACE_NAME_COLOR;
			case NAMESPACE_VALUE:	return dark ? DARK_NAMESPACE_VALUE_COLOR : DEFAULT_NAMESPACE_VALUE_COLOR;
			case CDATA:				return dark ? DARK_CDATA_COLOR : DEFAULT_CDATA_COLOR;
			case COMMENT:			return dark ? DARK_COMMENT_COLOR : DEFAULT_COMMENT_COLOR;
			case ENTITY:			return dark ? DARK_ENTITY_COLOR : DEFAULT_ENTITY_COLOR;
			case SPECIAL:			return dark ? DARK_SPECIAL_COLOR : DEFAULT_SPECIAL_COLOR;

			// PI tokens
			case PI_TARGET:			return dark ? DARK_PI_TARGET_COLOR : PI_TARGET_COLOR;
			case PI_NAME:			return dark ? DARK_PI_NAME_COLOR : PI_NAME_COLOR;
			case PI_VALUE:			return dark ? DARK_PI_VALUE_COLOR : PI_VALUE_COLOR;

			// DTD tokens
			case STRING_VALUE:				return dark ? DARK_STRING_VALUE_COLOR : STRING_VALUE_COLOR;
			case ENTITY_VALUE:				return dark ? DARK_ENTITY_VALUE_COLOR : ENTITY_VALUE_COLOR;
			case ENTITY_DECLARATION:		return dark ? DARK_ENTITY_DECLARATION_COLOR : ENTITY_DECLARATION_COLOR;
			case ENTITY_NAME:				return dark ? DARK_ENTITY_NAME_COLOR : ENTITY_NAME_COLOR;
			case ENTITY_TYPE:				return dark ? DARK_ENTITY_TYPE_COLOR : ENTITY_TYPE_COLOR;
			case ATTLIST_DECLARATION:		return dark ? DARK_ATTLIST_DECLARATION_COLOR : ATTLIST_DECLARATION_COLOR;
			case ATTLIST_NAME:				return dark ? DARK_ATTLIST_NAME_COLOR : ATTLIST_NAME_COLOR;
			case ATTLIST_TYPE:				return dark ? DARK_ATTLIST_TYPE_COLOR : ATTLIST_TYPE_COLOR;
			case ATTLIST_VALUE:				return dark ? DARK_ATTLIST_VALUE_COLOR : ATTLIST_VALUE_COLOR;
			case ATTLIST_DEFAULT:			return dark ? DARK_ATTLIST_DEFAULT_COLOR : ATTLIST_DEFAULT_COLOR;
			case ELEMENT_DECLARATION:		return dark ? DARK_ELEMENT_DECLARATION_COLOR : ELEMENT_DECLARATION_COLOR;
			case ELEMENT_DECLARATION_NAME:	return dark ? DARK_ELEMENT_DECLARATION_NAME_COLOR : ELEMENT_DECLARATION_NAME_COLOR;
			case ELEMENT_DECLARATION_TYPE:	return dark ? DARK_ELEMENT_DECLARATION_TYPE_COLOR : ELEMENT_DECLARATION_TYPE_COLOR;
			case ELEMENT_DECLARATION_PCDATA:	return dark ? DARK_ELEMENT_DECLARATION_PCDATA_COLOR : ELEMENT_DECLARATION_PCDATA_COLOR;
			case ELEMENT_DECLARATION_OPERATOR: return dark ? DARK_ELEMENT_DECLARATION_OPERATOR_COLOR : ELEMENT_DECLARATION_OPERATOR_COLOR;
			case NOTATION_DECLARATION:		return dark ? DARK_NOTATION_DECLARATION_COLOR : NOTATION_DECLARATION_COLOR;
			case NOTATION_DECLARATION_NAME:	return dark ? DARK_NOTATION_DECLARATION_NAME_COLOR : NOTATION_DECLARATION_NAME_COLOR;
			case NOTATION_DECLARATION_TYPE:	return dark ? DARK_NOTATION_DECLARATION_TYPE_COLOR : NOTATION_DECLARATION_TYPE_COLOR;
			case DOCTYPE_DECLARATION:		return dark ? DARK_DOCTYPE_DECLARATION_COLOR : DOCTYPE_DECLARATION_COLOR;
			case DOCTYPE_DECLARATION_TYPE:	return dark ? DARK_DOCTYPE_DECLARATION_TYPE_COLOR : DOCTYPE_DECLARATION_TYPE_COLOR;

			// Markdown tokens
			case MD_TEXT:			return dark ? DARK_MD_TEXT_COLOR : DEFAULT_MD_TEXT_COLOR;
			case MD_HEADER:			return dark ? DARK_MD_HEADER_COLOR : DEFAULT_MD_HEADER_COLOR;
			case MD_EMPHASIS:		return dark ? DARK_MD_EMPHASIS_COLOR : DEFAULT_MD_EMPHASIS_COLOR;
			case MD_STRONG:			return dark ? DARK_MD_STRONG_COLOR : DEFAULT_MD_STRONG_COLOR;
			case MD_CODE:			return dark ? DARK_MD_CODE_COLOR : DEFAULT_MD_CODE_COLOR;
			case MD_CODE_BLOCK:		return dark ? DARK_MD_CODE_BLOCK_COLOR : DEFAULT_MD_CODE_BLOCK_COLOR;
			case MD_LINK:			return dark ? DARK_MD_LINK_COLOR : DEFAULT_MD_LINK_COLOR;
			case MD_URL:			return dark ? DARK_MD_URL_COLOR : DEFAULT_MD_URL_COLOR;
			case MD_IMAGE:			return dark ? DARK_MD_IMAGE_COLOR : DEFAULT_MD_IMAGE_COLOR;
			case MD_BLOCKQUOTE:		return dark ? DARK_MD_BLOCKQUOTE_COLOR : DEFAULT_MD_BLOCKQUOTE_COLOR;
			case MD_LIST:			return dark ? DARK_MD_LIST_COLOR : DEFAULT_MD_LIST_COLOR;
			case MD_RULE:			return dark ? DARK_MD_RULE_COLOR : DEFAULT_MD_RULE_COLOR;
			case MD_TABLE:			return dark ? DARK_MD_TABLE_COLOR : DEFAULT_MD_TABLE_COLOR;
			case MD_STRIKETHROUGH:	return dark ? DARK_MD_STRIKETHROUGH_COLOR : DEFAULT_MD_STRIKETHROUGH_COLOR;
			case MD_TASK:			return dark ? DARK_MD_TASK_COLOR : DEFAULT_MD_TASK_COLOR;
			case MD_HTML:			return dark ? DARK_MD_HTML_COLOR : DEFAULT_MD_HTML_COLOR;
			case MD_YAML:			return dark ? DARK_MD_YAML_COLOR : DEFAULT_MD_YAML_COLOR;

			// AsciiDoc tokens
			case AD_TEXT:			return dark ? DARK_AD_TEXT_COLOR : DEFAULT_AD_TEXT_COLOR;
			case AD_SECTION_TITLE:	return dark ? DARK_AD_SECTION_TITLE_COLOR : DEFAULT_AD_SECTION_TITLE_COLOR;
			case AD_BOLD:			return dark ? DARK_AD_BOLD_COLOR : DEFAULT_AD_BOLD_COLOR;
			case AD_ITALIC:			return dark ? DARK_AD_ITALIC_COLOR : DEFAULT_AD_ITALIC_COLOR;
			case AD_MONOSPACE:		return dark ? DARK_AD_MONOSPACE_COLOR : DEFAULT_AD_MONOSPACE_COLOR;
			case AD_LINK:			return dark ? DARK_AD_LINK_COLOR : DEFAULT_AD_LINK_COLOR;
			case AD_XREF:			return dark ? DARK_AD_XREF_COLOR : DEFAULT_AD_XREF_COLOR;
			case AD_IMAGE:			return dark ? DARK_AD_IMAGE_COLOR : DEFAULT_AD_IMAGE_COLOR;
			case AD_LIST_MARKER:	return dark ? DARK_AD_LIST_MARKER_COLOR : DEFAULT_AD_LIST_MARKER_COLOR;
			case AD_ADMONITION:		return dark ? DARK_AD_ADMONITION_COLOR : DEFAULT_AD_ADMONITION_COLOR;
			case AD_BLOCK_DELIMITER: return dark ? DARK_AD_BLOCK_DELIMITER_COLOR : DEFAULT_AD_BLOCK_DELIMITER_COLOR;
			case AD_ATTRIBUTE:		return dark ? DARK_AD_ATTRIBUTE_COLOR : DEFAULT_AD_ATTRIBUTE_COLOR;
			case AD_MACRO:			return dark ? DARK_AD_MACRO_COLOR : DEFAULT_AD_MACRO_COLOR;
			case AD_COMMENT:		return dark ? DARK_AD_COMMENT_COLOR : DEFAULT_AD_COMMENT_COLOR;
			case AD_TABLE:			return dark ? DARK_AD_TABLE_COLOR : DEFAULT_AD_TABLE_COLOR;
			case AD_PASSTHROUGH:	return dark ? DARK_AD_PASSTHROUGH_COLOR : DEFAULT_AD_PASSTHROUGH_COLOR;
			case AD_LITERAL:		return dark ? DARK_AD_LITERAL_COLOR : DEFAULT_AD_LITERAL_COLOR;
			case AD_HIGHLIGHT:		return dark ? DARK_AD_HIGHLIGHT_COLOR : DEFAULT_AD_HIGHLIGHT_COLOR;
			case AD_BLOCK_ATTR:		return dark ? DARK_AD_BLOCK_ATTR_COLOR : DEFAULT_AD_BLOCK_ATTR_COLOR;
			case AD_ATTR_REF:		return dark ? DARK_AD_ATTR_REF_COLOR : DEFAULT_AD_ATTR_REF_COLOR;
			case AD_BLOCK_TITLE:	return dark ? DARK_AD_BLOCK_TITLE_COLOR : DEFAULT_AD_BLOCK_TITLE_COLOR;
			case AD_CALLOUT:		return dark ? DARK_AD_CALLOUT_COLOR : DEFAULT_AD_CALLOUT_COLOR;
			case AD_DESC_LIST:		return dark ? DARK_AD_DESC_LIST_COLOR : DEFAULT_AD_DESC_LIST_COLOR;

			default: return null;
		}
	}

	/**
	 * Returns true if the current L&F has a dark background.
	 */
	public static boolean isDarkTheme() {
		Color bg = UIManager.getColor("TextPane.background");
		if (bg == null) bg = UIManager.getColor("Panel.background");
		if (bg == null) return false;
		// Luminance threshold: dark if below 128
		double luminance = 0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue();
		return luminance < 128;
	}

	public static FontType getFontType( String name) {
		Vector types = preferences.getFontTypes();
		
		for ( int i = 0; i < types.size(); i++) {
			FontType type = (FontType)types.elementAt(i);
			if ( type.getName().equals( name)) {
				return type;
			}
		}
		
		return null;
	}

	/**
	 * Returns the font-type list.
	 *
	 * @return the font-types.
	 */
	public Vector getFontTypes() {
		if ( types == null) {
			Vector result = new Vector();
			Vector list = getProperties( FontType.FONT_TYPE);
			boolean cdataFound = false;
			boolean dtdFound = false;
			boolean markdownFound = false;

			for ( int i = 0; i < list.size(); i++) {
				FontType type = new FontType( ((Properties)list.elementAt(i)).getElement());
				if ( type.getName().equals( CDATA)) {
					cdataFound = true;
				}

				if ( type.getName().equals( STRING_VALUE)) {
					dtdFound = true;
				}
				
				if ( type.getName().equals( MD_TEXT)) {
					markdownFound = true;
				}

				result.addElement( type);
			}
			
			if ( !cdataFound && list.size() > 0) {
				boolean dk = isDarkTheme();
				addFontType( new FontType( CDATA, DEFAULT_CDATA_STYLE, dk ? DARK_CDATA_COLOR : DEFAULT_CDATA_COLOR));

				result = getFontTypes();
			}
			
			if ( !dtdFound && list.size() > 0) {
				boolean dk = isDarkTheme();
				addFontType( new FontType( PI_TARGET, Font.PLAIN, dk ? DARK_PI_TARGET_COLOR : PI_TARGET_COLOR));
				addFontType( new FontType( PI_NAME, Font.PLAIN, dk ? DARK_PI_NAME_COLOR : PI_NAME_COLOR));
				addFontType( new FontType( PI_VALUE, Font.PLAIN, dk ? DARK_PI_VALUE_COLOR : PI_VALUE_COLOR));

				addFontType( new FontType( STRING_VALUE, Font.PLAIN, dk ? DARK_STRING_VALUE_COLOR : STRING_VALUE_COLOR));
				addFontType( new FontType( ENTITY_VALUE, Font.PLAIN, dk ? DARK_ENTITY_VALUE_COLOR : ENTITY_VALUE_COLOR));

				addFontType( new FontType( ENTITY_DECLARATION, Font.PLAIN, dk ? DARK_ENTITY_DECLARATION_COLOR : ENTITY_DECLARATION_COLOR));
				addFontType( new FontType( ENTITY_NAME, Font.PLAIN, dk ? DARK_ENTITY_NAME_COLOR : ENTITY_NAME_COLOR));
				addFontType( new FontType( ENTITY_TYPE, Font.PLAIN, dk ? DARK_ENTITY_TYPE_COLOR : ENTITY_TYPE_COLOR));

				addFontType( new FontType( ATTLIST_DECLARATION, Font.PLAIN, dk ? DARK_ATTLIST_DECLARATION_COLOR : ATTLIST_DECLARATION_COLOR));
				addFontType( new FontType( ATTLIST_NAME, Font.PLAIN, dk ? DARK_ATTLIST_NAME_COLOR : ATTLIST_NAME_COLOR));
				addFontType( new FontType( ATTLIST_TYPE, Font.PLAIN, dk ? DARK_ATTLIST_TYPE_COLOR : ATTLIST_TYPE_COLOR));
				addFontType( new FontType( ATTLIST_VALUE, Font.PLAIN, dk ? DARK_ATTLIST_VALUE_COLOR : ATTLIST_VALUE_COLOR));
				addFontType( new FontType( ATTLIST_DEFAULT, Font.PLAIN, dk ? DARK_ATTLIST_DEFAULT_COLOR : ATTLIST_DEFAULT_COLOR));

				addFontType( new FontType( ELEMENT_DECLARATION, Font.PLAIN, dk ? DARK_ELEMENT_DECLARATION_COLOR : ELEMENT_DECLARATION_COLOR));
				addFontType( new FontType( ELEMENT_DECLARATION_NAME, Font.PLAIN, dk ? DARK_ELEMENT_DECLARATION_NAME_COLOR : ELEMENT_DECLARATION_NAME_COLOR));
				addFontType( new FontType( ELEMENT_DECLARATION_TYPE, Font.PLAIN, dk ? DARK_ELEMENT_DECLARATION_TYPE_COLOR : ELEMENT_DECLARATION_TYPE_COLOR));
				addFontType( new FontType( ELEMENT_DECLARATION_PCDATA, Font.PLAIN, dk ? DARK_ELEMENT_DECLARATION_PCDATA_COLOR : ELEMENT_DECLARATION_PCDATA_COLOR));
				addFontType( new FontType( ELEMENT_DECLARATION_OPERATOR, Font.PLAIN, dk ? DARK_ELEMENT_DECLARATION_OPERATOR_COLOR : ELEMENT_DECLARATION_OPERATOR_COLOR));

				addFontType( new FontType( NOTATION_DECLARATION, Font.PLAIN, dk ? DARK_NOTATION_DECLARATION_COLOR : NOTATION_DECLARATION_COLOR));
				addFontType( new FontType( NOTATION_DECLARATION_NAME, Font.PLAIN, dk ? DARK_NOTATION_DECLARATION_NAME_COLOR : NOTATION_DECLARATION_NAME_COLOR));
				addFontType( new FontType( NOTATION_DECLARATION_TYPE, Font.PLAIN, dk ? DARK_NOTATION_DECLARATION_TYPE_COLOR : NOTATION_DECLARATION_TYPE_COLOR));

				addFontType( new FontType( DOCTYPE_DECLARATION, Font.PLAIN, dk ? DARK_DOCTYPE_DECLARATION_COLOR : DOCTYPE_DECLARATION_COLOR));
				addFontType( new FontType( DOCTYPE_DECLARATION_TYPE, Font.PLAIN, dk ? DARK_DOCTYPE_DECLARATION_TYPE_COLOR : DOCTYPE_DECLARATION_TYPE_COLOR));

				result = getFontTypes();
			}
			
			if ( !markdownFound && list.size() > 0) {
				boolean dk = isDarkTheme();
				addFontType( new FontType( MD_TEXT, DEFAULT_MD_TEXT_STYLE, dk ? DARK_MD_TEXT_COLOR : DEFAULT_MD_TEXT_COLOR));
				addFontType( new FontType( MD_HEADER, DEFAULT_MD_HEADER_STYLE, dk ? DARK_MD_HEADER_COLOR : DEFAULT_MD_HEADER_COLOR));
				addFontType( new FontType( MD_EMPHASIS, DEFAULT_MD_EMPHASIS_STYLE, dk ? DARK_MD_EMPHASIS_COLOR : DEFAULT_MD_EMPHASIS_COLOR));
				addFontType( new FontType( MD_STRONG, DEFAULT_MD_STRONG_STYLE, dk ? DARK_MD_STRONG_COLOR : DEFAULT_MD_STRONG_COLOR));
				addFontType( new FontType( MD_CODE, DEFAULT_MD_CODE_STYLE, dk ? DARK_MD_CODE_COLOR : DEFAULT_MD_CODE_COLOR));
				addFontType( new FontType( MD_CODE_BLOCK, DEFAULT_MD_CODE_BLOCK_STYLE, dk ? DARK_MD_CODE_BLOCK_COLOR : DEFAULT_MD_CODE_BLOCK_COLOR));
				addFontType( new FontType( MD_LINK, DEFAULT_MD_LINK_STYLE, dk ? DARK_MD_LINK_COLOR : DEFAULT_MD_LINK_COLOR));
				addFontType( new FontType( MD_URL, DEFAULT_MD_URL_STYLE, dk ? DARK_MD_URL_COLOR : DEFAULT_MD_URL_COLOR));
				addFontType( new FontType( MD_IMAGE, DEFAULT_MD_IMAGE_STYLE, dk ? DARK_MD_IMAGE_COLOR : DEFAULT_MD_IMAGE_COLOR));
				addFontType( new FontType( MD_BLOCKQUOTE, DEFAULT_MD_BLOCKQUOTE_STYLE, dk ? DARK_MD_BLOCKQUOTE_COLOR : DEFAULT_MD_BLOCKQUOTE_COLOR));
				addFontType( new FontType( MD_LIST, DEFAULT_MD_LIST_STYLE, dk ? DARK_MD_LIST_COLOR : DEFAULT_MD_LIST_COLOR));
				addFontType( new FontType( MD_RULE, DEFAULT_MD_RULE_STYLE, dk ? DARK_MD_RULE_COLOR : DEFAULT_MD_RULE_COLOR));
				addFontType( new FontType( MD_TABLE, DEFAULT_MD_TABLE_STYLE, dk ? DARK_MD_TABLE_COLOR : DEFAULT_MD_TABLE_COLOR));
				addFontType( new FontType( MD_STRIKETHROUGH, DEFAULT_MD_STRIKETHROUGH_STYLE, dk ? DARK_MD_STRIKETHROUGH_COLOR : DEFAULT_MD_STRIKETHROUGH_COLOR));
				addFontType( new FontType( MD_TASK, DEFAULT_MD_TASK_STYLE, dk ? DARK_MD_TASK_COLOR : DEFAULT_MD_TASK_COLOR));
				addFontType( new FontType( MD_HTML, DEFAULT_MD_HTML_STYLE, dk ? DARK_MD_HTML_COLOR : DEFAULT_MD_HTML_COLOR));
				addFontType( new FontType( MD_YAML, DEFAULT_MD_YAML_STYLE, dk ? DARK_MD_YAML_COLOR : DEFAULT_MD_YAML_COLOR));

				// AsciiDoc defaults (always added with Markdown)
				addFontType( new FontType( AD_TEXT, DEFAULT_AD_TEXT_STYLE, dk ? DARK_AD_TEXT_COLOR : DEFAULT_AD_TEXT_COLOR));
				addFontType( new FontType( AD_SECTION_TITLE, DEFAULT_AD_SECTION_TITLE_STYLE, dk ? DARK_AD_SECTION_TITLE_COLOR : DEFAULT_AD_SECTION_TITLE_COLOR));
				addFontType( new FontType( AD_BOLD, DEFAULT_AD_BOLD_STYLE, dk ? DARK_AD_BOLD_COLOR : DEFAULT_AD_BOLD_COLOR));
				addFontType( new FontType( AD_ITALIC, DEFAULT_AD_ITALIC_STYLE, dk ? DARK_AD_ITALIC_COLOR : DEFAULT_AD_ITALIC_COLOR));
				addFontType( new FontType( AD_MONOSPACE, DEFAULT_AD_MONOSPACE_STYLE, dk ? DARK_AD_MONOSPACE_COLOR : DEFAULT_AD_MONOSPACE_COLOR));
				addFontType( new FontType( AD_LINK, DEFAULT_AD_LINK_STYLE, dk ? DARK_AD_LINK_COLOR : DEFAULT_AD_LINK_COLOR));
				addFontType( new FontType( AD_XREF, DEFAULT_AD_XREF_STYLE, dk ? DARK_AD_XREF_COLOR : DEFAULT_AD_XREF_COLOR));
				addFontType( new FontType( AD_IMAGE, DEFAULT_AD_IMAGE_STYLE, dk ? DARK_AD_IMAGE_COLOR : DEFAULT_AD_IMAGE_COLOR));
				addFontType( new FontType( AD_LIST_MARKER, DEFAULT_AD_LIST_MARKER_STYLE, dk ? DARK_AD_LIST_MARKER_COLOR : DEFAULT_AD_LIST_MARKER_COLOR));
				addFontType( new FontType( AD_ADMONITION, DEFAULT_AD_ADMONITION_STYLE, dk ? DARK_AD_ADMONITION_COLOR : DEFAULT_AD_ADMONITION_COLOR));
				addFontType( new FontType( AD_BLOCK_DELIMITER, DEFAULT_AD_BLOCK_DELIMITER_STYLE, dk ? DARK_AD_BLOCK_DELIMITER_COLOR : DEFAULT_AD_BLOCK_DELIMITER_COLOR));
				addFontType( new FontType( AD_ATTRIBUTE, DEFAULT_AD_ATTRIBUTE_STYLE, dk ? DARK_AD_ATTRIBUTE_COLOR : DEFAULT_AD_ATTRIBUTE_COLOR));
				addFontType( new FontType( AD_MACRO, DEFAULT_AD_MACRO_STYLE, dk ? DARK_AD_MACRO_COLOR : DEFAULT_AD_MACRO_COLOR));
				addFontType( new FontType( AD_COMMENT, DEFAULT_AD_COMMENT_STYLE, dk ? DARK_AD_COMMENT_COLOR : DEFAULT_AD_COMMENT_COLOR));
				addFontType( new FontType( AD_TABLE, DEFAULT_AD_TABLE_STYLE, dk ? DARK_AD_TABLE_COLOR : DEFAULT_AD_TABLE_COLOR));
				addFontType( new FontType( AD_PASSTHROUGH, DEFAULT_AD_PASSTHROUGH_STYLE, dk ? DARK_AD_PASSTHROUGH_COLOR : DEFAULT_AD_PASSTHROUGH_COLOR));
				addFontType( new FontType( AD_LITERAL, DEFAULT_AD_LITERAL_STYLE, dk ? DARK_AD_LITERAL_COLOR : DEFAULT_AD_LITERAL_COLOR));
				addFontType( new FontType( AD_HIGHLIGHT, DEFAULT_AD_HIGHLIGHT_STYLE, dk ? DARK_AD_HIGHLIGHT_COLOR : DEFAULT_AD_HIGHLIGHT_COLOR));
				addFontType( new FontType( AD_BLOCK_ATTR, DEFAULT_AD_BLOCK_ATTR_STYLE, dk ? DARK_AD_BLOCK_ATTR_COLOR : DEFAULT_AD_BLOCK_ATTR_COLOR));
				addFontType( new FontType( AD_ATTR_REF, DEFAULT_AD_ATTR_REF_STYLE, dk ? DARK_AD_ATTR_REF_COLOR : DEFAULT_AD_ATTR_REF_COLOR));
				addFontType( new FontType( AD_BLOCK_TITLE, DEFAULT_AD_BLOCK_TITLE_STYLE, dk ? DARK_AD_BLOCK_TITLE_COLOR : DEFAULT_AD_BLOCK_TITLE_COLOR));
				addFontType( new FontType( AD_CALLOUT, DEFAULT_AD_CALLOUT_STYLE, dk ? DARK_AD_CALLOUT_COLOR : DEFAULT_AD_CALLOUT_COLOR));
				addFontType( new FontType( AD_DESC_LIST, DEFAULT_AD_DESC_LIST_STYLE, dk ? DARK_AD_DESC_LIST_COLOR : DEFAULT_AD_DESC_LIST_COLOR));

				result = getFontTypes();
			}

			types = result;
		}
	
		return types;
	}

	/**
	 * Adds a font-type object to this element.
	 *
	 * @param props the font-type.
	 */
	public void addFontType( FontType props) {
		add( props);
	}

	/**
	 * Adds a font-type object to this element.
	 *
	 * @param props the font-type.
	 */
	public void removeFontType( FontType props) {
		remove( props);
	}
	
	private boolean hasSameWidth( Font font, int style1, int style2) {
	    String testString = "<Test test:nms=\"http://test.org\"/>";
		JTextArea pane = new JTextArea();
		
	    Font font1 = font.deriveFont( style1, 12);
	    FontMetrics fm = pane.getFontMetrics( font1);
	    int width1 = fm.stringWidth( testString);

	    Font font2 = font.deriveFont( style2, 12);
	    fm = pane.getFontMetrics( font2);
	    int width2 = fm.stringWidth( testString);

		if ( width1 == width2) {	// && italicWidth == italicBoldWidth) { 
			return true;
		} 
		
		return false;
	}

} 
