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

package com.dogsbay.dogsbayaieditor.project;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.net.URL;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.dogsbay.xml.XMLUtilities;
import com.dogsbay.dogsbayaieditor.StringUtilities;

/**
 * The base node class.
 *
 * @version	$Revision: 1.4 $, $Date: 2005/09/07 16:20:51 $
 * @author Dogsbay
 */
public class Finder {
	private static final boolean DEBUG = false;
	
	public static Vector find( URL url, String search, boolean regExp, boolean matchCase, boolean wholeWord) {
		if (DEBUG) System.out.println( "Finder.find( "+url+", "+search+", "+regExp+", "+matchCase+")");

		return find( url, createPattern( search, regExp, matchCase, wholeWord));
	}

	public static Vector find( URL url, Pattern pattern) {
		if (DEBUG) System.out.println( "Finder.find( "+url+", "+pattern+")");
		Vector matches = new Vector();
		
		try {
			String text = XMLUtilities.getText( url, new XMLUtilities.XMLDeclaration());
			LineNumberReader reader = new LineNumberReader( new StringReader( text));

			String line = reader.readLine();
			
			while ( line != null) {
				Matcher matcher = pattern.matcher( line);
				
				while ( matcher.find()) {
					matches.addElement( new Match( url, reader.getLineNumber(), matcher.start(), matcher.end(), line));
				}
				
				line = reader.readLine();
			}
		} catch (IOException e) {
			//System.err.println( "Error: Could not read file '"+url+"'!");
//			matches.addElement( new Match( url, -1, -1, -1, "ERROR: Could Not Read File."));
//			e.printStackTrace(); // don't worry about it, just continue...
		}
		
		return matches;
	}

	private static Pattern createPattern( String search, boolean regExp, boolean matchCase, boolean wholeWord) {
		if (DEBUG) System.out.println( "Finder.createPattern( "+search+", "+regExp+", "+matchCase+")");

		String regularSearch = search;

		// Maybe pre-compile the matcher for a list of documents???
		Pattern pattern = null;
		
		if ( !regExp) {
			regularSearch = "\\Q"+StringUtilities.prepareNonRegularExpression( search)+"\\E";
		}

		if ( wholeWord) { 
			regularSearch = "\\b"+regularSearch+"\\b";
		}

		if ( !matchCase) {
			pattern = Pattern.compile( regularSearch, Pattern.CASE_INSENSITIVE);
		} else {
			pattern = Pattern.compile( regularSearch);
		}

		return pattern;
	}
}