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

import java.net.URL;

import javax.swing.text.Element;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.editor.XmlDocument;

/**
 * Represents a Match for the Finder.
 *
 * @version	$Revision: 1.2 $, $Date: 2004/10/08 11:43:18 $
 * @author Dogsbay
 */
public class Match {
	private Element element = null;
	private DogsBayDocument document = null;
	
	private boolean removed = false;

	private int lineNumber = -1;
	private int start = -1;
	private int end = -1;
	private String lineValue = null;
	private URL url = null;
	
	public Match( URL url, int number, int start, int end, String value) {
		// System.out.println( "Match( "+url+", "+number+", "+start+", "+end+", "+value+")");
		this.url = url;
		this.lineNumber = number;
		this.start = start;
		this.end = end;
		this.lineValue = value;
	}
	
	public boolean isRemoved() {
		return removed;
	}

	public void setRemoved( boolean removed) {
		this.removed = removed;
	}

	public Element getTextElement() {
		testElement();
		
		return element;
	}
	
	private void testElement() {
		if ( element != null && element.getEndOffset() <= 0) {
			element = null;
			document = null;
		}
	}

	public int getLineNumber() {
		testElement();
		
		if ( element != null) {
			Element root = element.getParentElement();
			return root.getElementIndex( element.getStartOffset());
		} else {
			return lineNumber;
		}
	}

	public int getStart() {
		return start;
	}

	public int getEnd() {
		return end;
	}

	public void update() {
		lineValue = getLineValue();
		lineNumber = getLineNumber();
	}

	public DogsBayDocument getDocument() {
		testElement();
		
		return document;
	}

	public void setDocument( DogsBayDocument document) {
		this.document = document;
	}

	public String getLineValue() {
		testElement();
		
		if ( element != null) {
			XmlDocument doc = (XmlDocument)element.getDocument();

			try {
				return doc.getText( element.getStartOffset(), element.getEndOffset() - element.getStartOffset());
			} catch ( Exception e) {
				e.printStackTrace();
			}
		}
		
		return lineValue;
	}
	
	public boolean equals( Object object) {
		testElement();
		
		if ( object instanceof Match) {
			Match result = (Match)object;

			if ( result.element != null && element != null && result.element == element) {
				return true;
			}
			
			if ( result.getURL() != null && getURL() != null && getURL().equals( result.getURL()) && result.getLineNumber() == getLineNumber()) {
				return true;
			}
		}
		
		return false;
	}

	public URL getURL() {
		return url;
	}

	public String toString() {
		return getPath()+" ["+lineNumber+"] "+lineValue;
	}
	
	private String getPath() {
		String path = url.toString();
		
		if ( url.getProtocol().equals( "file")) {
			path = path.substring( 6, path.length());
		}

		return path;
	}
	
} 
