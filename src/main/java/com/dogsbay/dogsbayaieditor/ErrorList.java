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

package com.dogsbay.dogsbayaieditor;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

import org.apache.commons.collections.ComparatorUtils;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XMLError;

/**
 * This ErrorList is used to keep a list of all the XML errors for a document.
 *
 * @version $Revision: 1.1 $, $Date: 2004/07/22 15:58:55 $
 * @author Dogsbay
 */
public class ErrorList implements Comparator {
	private DogsBayDocument document = null;
	private Vector errors = null;
	private String header = null;
	private String footer = null;

	/**
	 * A list of XML Errors.
	 */
	public ErrorList() {
		errors = new Vector();
	}
	
	public void setDocument( DogsBayDocument doc) {
		document = doc;
	}

	public Vector getErrors() {
		return errors;
	}
	
	public void sortErrorsByLineNumber() {
		
		Collections.sort(errors,this);
		
	}
	
	public Vector getCurrentErrors() {
		Vector result = new Vector();
		
		for ( int i = 0; i < errors.size(); i++) {
			String systemId = ((XMLError)errors.elementAt(i)).getSystemId();

			if ( systemId != null) {
 				String name = document.getName();
 				
 				if ( systemId.endsWith( name)) {
 					result.add( errors.elementAt(i));
 				}
			} else {
				result.add( errors.elementAt(i));
			}
		}

		return result;
	}

	public void addError( XMLError error) {
		errors.addElement( error);
	}
	
	public void addErrorSortedByLineNumber( XMLError error) {
		if(error != null) {
			if(errors != null) {
				boolean greaterThanFound = false;
				int cnt = 0;
				while((greaterThanFound == false) && (cnt < errors.size())) {
					
					Object tempObj = errors.get(cnt);
					if(tempObj instanceof XMLError) {
						XMLError tempError = (XMLError) tempObj;
						if(tempError != null) {
							if(error.getLineNumber() > tempError.getLineNumber()) {
								//add after
							}
							else if(error.getLineNumber() < tempError.getLineNumber()) {
								//add before now
								greaterThanFound = true;
							}
							else {
								
								if(error.getColumnNumber() > tempError.getColumnNumber()) {
									//add after
								}
								else if(error.getColumnNumber() < tempError.getColumnNumber()) {
									greaterThanFound = true;
								}
								else {
									//prob wont happen
								}
							}
						}
						
						if(greaterThanFound == false) {
							cnt++;
						}
					}
				}
				
				if(errors.size() == 0) {
					errors.add(error);
				}
				else if(greaterThanFound == true) {
					//add before cnt
					errors.add(cnt, error);
					
				}
			}				
		}
		//errors.addElement( error);
	}
	
	public void reset() { 
		errors.removeAllElements();
		header = null;
		footer = null;
	}
	
	public void setFooter( String footer) {
		this.footer = footer;
	}

	public String getFooter() {
		return footer;
	}

	public void setHeader( String header) {
		this.header = header;
	}

	public String getHeader() {
		return header;
	}

	@Override
	public int compare(Object o1, Object o2) {
		if(o1 != null) {
			if(o2 != null) {
				if(o1 instanceof XMLError) {
					if(o2 instanceof XMLError) {
						
						int lineNumber1 = ((XMLError)o1).getLineNumber();
						int lineNumber2 = ((XMLError)o2).getLineNumber();
						
						if(lineNumber1 < lineNumber2) {
							return(-1);
						}
						else if(lineNumber1 > lineNumber2) {
							return(1);
						}
						else {
							
							int columnNumber1 = ((XMLError)o1).getColumnNumber();
							int columnNumber2 = ((XMLError)o2).getColumnNumber();
							
							if(columnNumber1 < columnNumber2) {
								return(-1);
							}
							else if(columnNumber1 > columnNumber2) {
								return(1);
							}
							else {
								return(0);
							}
						}
					}
					else {
						return(-1);
					}
				}
				else {
					if(o2 instanceof XMLError) {
						return(1);
					}
					else {
						return(0);
					}
				}
			}
			else {
				//o1 not null, o2 is null
				return(-1);
			}
		}
		else {
			if(o2 != null) {
				return(1);
			}
			else {
				//both are null
				return(0);
			}
		}
		
	}
}