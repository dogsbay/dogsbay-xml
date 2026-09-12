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

import java.io.IOException;
import java.io.Reader;

/**
 * Utilities for reading and writing of XML documents.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:41:32 $
 * @author Dogsbay
 */
public class XMLParserUtilities {
	private static final boolean DEBUG = false;
	
	public static char skipWhitespace( Reader reader) throws IOException {
		if (DEBUG) System.out.println("XMLParserUtilities.skipWhitespace( "+reader+")");
		char c = (char)reader.read();
		
		while ( Character.isWhitespace( c)) {
	    	c = (char)reader.read();
		}
		
		return c;
	}

	public static String parseString( Reader reader, char delim) throws IOException {
//		System.out.println("XMLParserUtilities.parseString( "+reader+", "+delim+")");
		StringBuffer buffer = new StringBuffer();

		char c = (char)reader.read();
		
		while ( c != delim) {
			buffer.append( c);;
			c = (char)reader.read();
		}
		
		String result = buffer.toString();
		
		if (DEBUG) System.out.println("XMLParserUtilities.parseString( "+reader+", "+delim+") ["+result+"]");

		return result;
	}

	public static boolean hasString( Reader reader, String string, char c) throws IOException {
		if (DEBUG) System.out.println("XMLParserUtilities.hasString( "+reader+", "+string+", "+c+")");

		char[] chars = string.toCharArray();

		for ( int i = 0; i < chars.length; i++) {
			if (DEBUG) System.out.print( c);
			if ( chars[i] != c) {
				if (DEBUG) System.out.println("XMLParserUtilities.hasString() [false]");
				return false;
			}
			
			if ( (i + 1) < chars.length) {
				c = (char)reader.read();
			}
		}
		
		if (DEBUG) System.out.println("XMLParserUtilities.hasString() [true]");
		return true;
	}
} 
