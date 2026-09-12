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

/**
 * The contants used for the XML editor.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:44:46 $
 * @author Dogsbay
 */
public interface Constants {

    public static final long MAXFILESIZE		= 0xffffffffL;
    public static final long MAXLINENUMBER		= 0xffffffffL;

    public static final int ELEMENT_NAME 		= 1;
    public static final int ELEMENT_PREFIX 		= 2;
    public static final int ELEMENT_VALUE 		= 3;

    public static final int ATTRIBUTE_NAME 		= 5;
    public static final int ATTRIBUTE_PREFIX 	= 6;
    public static final int ATTRIBUTE_VALUE 	= 7;

    public static final int NAMESPACE_NAME 		= 10;
    public static final int NAMESPACE_PREFIX 	= 11;
    public static final int NAMESPACE_VALUE 	= 12;

    public static final int ENTITY		 		= 15;
    public static final int COMMENT 			= 16;
    public static final int DECLARATION 		= 17;
    public static final int CDATA 				= 18;

    public static final int SPECIAL				= 20;
    public static final int STRING				= 21;

	public static final int PI_TARGET			= 22;
	public static final int PI_NAME				= 23;
	public static final int PI_VALUE			= 24;

	public static final int STRING_VALUE 		= 30;	// DTD: String Value
	public static final int ENTITY_VALUE 		= 31;	// DTD: Entity Reference

	public static final int ENTITY_DECLARATION	= 32;	// DTD: ENTITY Declaration
	public static final int ENTITY_NAME			= 33;	// DTD: Entity Name
	public static final int ENTITY_TYPE 		= 34;	// DTD: Entity Type

	public static final int ATTLIST_DECLARATION	= 35; 	// DTD: ATTLIST Declaration
	public static final int ATTLIST_NAME		= 36;	// DTD: Attribute Name
	public static final int ATTLIST_TYPE		= 37;	// DTD: Attribute Type
	public static final int ATTLIST_VALUE		= 38;	// DTD: Attribute Enumeration
	public static final int ATTLIST_DEFAULT		= 39;	// DTD: Attribute Default #REQUIRED/#IMPLIED/#FIXED

	public static final int ELEMENT_DECLARATION			= 40;	// DTD: ELEMENT Declaration
	public static final int ELEMENT_DECLARATION_NAME	= 41;	// DTD: Element Name
	public static final int ELEMENT_DECLARATION_CHILD	= ELEMENT_DECLARATION_NAME;
	public static final int ELEMENT_DECLARATION_TYPE	= 42;	// DTD: Element Type (EMPTY, ANY)
	public static final int ELEMENT_DECLARATION_PCDATA	= 43;	// DTD: #PCDATA
	public static final int ELEMENT_DECLARATION_OPERATOR= 44;	// DTD: Element Operator

	public static final int NOTATION_DECLARATION		= 45;	// DTD: NOTATION Declaration
	public static final int NOTATION_DECLARATION_NAME	= 46;	// DTD: Notation Name
	public static final int NOTATION_DECLARATION_TYPE	= 47;	// DTD: Notation Type (PUBLIC, SYSTEM)

	public static final int DOCTYPE_DECLARATION			= 48;	// DTD: DOCTYPE Declaration
	public static final int DOCTYPE_DECLARATION_TYPE	= 49;	// DTD: Doctype Type (PUBLIC, SYSTEM)

	// Markdown token ids start at 50 to avoid breaking existing XML/DTD ids.
	public static final int MD_TEXT				= 50;
	public static final int MD_HEADER			= 51;
	public static final int MD_EMPHASIS		= 52;
	public static final int MD_STRONG			= 53;
	public static final int MD_CODE			= 54;
	public static final int MD_CODE_BLOCK		= 55;
	public static final int MD_LINK			= 56;
	public static final int MD_URL				= 57;
	public static final int MD_IMAGE			= 58;
	public static final int MD_BLOCKQUOTE		= 59;
	public static final int MD_LIST			= 60;
	public static final int MD_RULE			= 61;
	public static final int MD_TABLE			= 62;
	public static final int MD_STRIKETHROUGH	= 63;
	public static final int MD_TASK			= 64;
	public static final int MD_HTML			= 65;
	public static final int MD_YAML			= 66;

	// AsciiDoc tokens (67-89)
	public static final int AD_TEXT				= 67;
	public static final int AD_SECTION_TITLE		= 68;
	public static final int AD_BOLD				= 69;
	public static final int AD_ITALIC				= 70;
	public static final int AD_MONOSPACE			= 71;
	public static final int AD_LINK				= 72;
	public static final int AD_XREF				= 73;
	public static final int AD_IMAGE				= 74;
	public static final int AD_LIST_MARKER			= 75;
	public static final int AD_ADMONITION			= 76;
	public static final int AD_BLOCK_DELIMITER		= 77;
	public static final int AD_ATTRIBUTE			= 78;
	public static final int AD_MACRO				= 79;
	public static final int AD_COMMENT				= 80;
	public static final int AD_TABLE				= 81;
	public static final int AD_PASSTHROUGH			= 82;
	public static final int AD_LITERAL				= 83;
	public static final int AD_HIGHLIGHT			= 84;
	public static final int AD_BLOCK_ATTR			= 85;  // [id="...", role="..."]
	public static final int AD_ATTR_REF			= 86;  // {attribute-name}
	public static final int AD_BLOCK_TITLE			= 87;  // .Title
	public static final int AD_CALLOUT				= 88;  // <1>, <2>
	public static final int AD_DESC_LIST			= 89;  // term:: description

	// JSON tokens (90-97)
	public static final int JSON_KEY				= 90;
	public static final int JSON_STRING			= 91;
	public static final int JSON_NUMBER			= 92;
	public static final int JSON_KEYWORD			= 93;  // true, false, null
	public static final int JSON_PUNCTUATION		= 94;  // { } [ ] : ,
	public static final int JSON_COMMENT			= 95;  // JSONC // and /* */
	public static final int JSON_ERROR				= 96;
	public static final int JSON_TEXT				= 97;

	// YAML tokens (98-106)
	public static final int YAML_KEY				= 98;
	public static final int YAML_STRING			= 99;
	public static final int YAML_NUMBER			= 100;
	public static final int YAML_KEYWORD			= 101;  // true, false, null, yes, no
	public static final int YAML_PUNCTUATION		= 102;  // : - | > { } [ ]
	public static final int YAML_COMMENT			= 103;  // #
	public static final int YAML_ANCHOR			= 104;  // &anchor, *alias
	public static final int YAML_TAG				= 105;  // !!type, !custom, %directive
	public static final int YAML_TEXT				= 106;

	public static final int MAX_TOKENS	= 110;
}
