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

package com.dogsbay.xml;

import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.io.ByteArrayInputStream;

import org.cyberneko.html.HTMLConfiguration;

import org.apache.xerces.parsers.SAXParser;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import org.dom4j.io.SAXReader;
import org.dom4j.DocumentException;




/**
 * Utilities class for HTML functionality (including Clean Up HTML)
 *
 * @version	$Revision: 1.6 $, $Date: 2004/05/28 09:14:00 $
 * @author Dogs bay
 */
public class HTMLUtilities {
	
	/** Notify character entity references. */
	public static final String NOTIFY_CHAR_REFS = 
		"http://apache.org/xml/features/scanner/notify-char-refs";

	/** Notify built-in entity references. */
    public static final String NOTIFY_HTML_BUILTIN_REFS = 
    	"http://cyberneko.org/html/features/scanner/notify-builtin-refs";

    /** Filters property identifier. */
    protected static final String FILTERS = 
    	"http://cyberneko.org/html/properties/filters";
    
    /** Element case identifier. */
    public static final String ELEMENT_CASE = 
    	"http://cyberneko.org/html/properties/names/elems";
    
    /** Attribute case identifier. */
    public static final String ATTR_CASE = 
    	"http://cyberneko.org/html/properties/names/attrs";
	
    public static final String ENCODING_PROPERTY = 
    	"http://cyberneko.org/html/properties/default-encoding";

    public static final String IGNORE_SPECIFIED_CHARSET = 
    	"http://cyberneko.org/html/features/scanner/ignore-specified-charset";

    /**
	 * Cleans up HTML, makes HTML well formed etc
	 *
	 * @param text The HTML
	 * @param encoding The encoding
	 */
	public static String cleanUpHTML(String text)
		throws UnsupportedEncodingException,IOException,SAXNotRecognizedException,SAXNotSupportedException,
		DocumentException
	{
		
		//	set the paser to use Neko HTML configuration
		SAXParser parser = new SAXParser(new HTMLConfiguration());
		
		parser.setFeature(NOTIFY_CHAR_REFS, true);
		parser.setFeature(NOTIFY_HTML_BUILTIN_REFS, true);
		parser.setFeature(IGNORE_SPECIFIED_CHARSET, true);
		
		// set the parser to use lower case
		parser.setProperty(ELEMENT_CASE,"lower");
		parser.setProperty(ATTR_CASE,"lower"); 
		parser.setProperty(ENCODING_PROPERTY, "UTF-8"); 
		
	
		// create a dom4j SaxReader
		SAXReader reader = new SAXReader(parser);
		
		// get the bytes from the input text
		ByteArrayInputStream stream = new ByteArrayInputStream( text.getBytes( "UTF-8"));
		
		// using sax read the stream into a dom4j dom
		XDocument doc = (XDocument)reader.read( stream);
		
		// write the new dom
		return XMLUtilities.write( doc, new DogsBayOutputFormat("", false, "UTF-8"));

	}
}
