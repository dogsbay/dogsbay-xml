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

package com.dogsbay.dogsbayaieditor.grammar;

import com.dogsbay.xml.XElement;
import com.dogsbay.xml.XMLGrammar;
import com.dogsbay.xml.properties.Properties;
import com.dogsbay.dogsbayaieditor.URLUtilities;

/**
 * Handles the properties for a namespace.
 *
 * @version	$Revision: 1.2 $, $Date: 2004/10/08 14:12:36 $
 * @author Dogsbay
 */
public class TagCompletionProperties extends Properties {
	
	public static final String TAGCOMPLETION	= "tag-completion";

	private static final String TYPE		= "type";
	private static final String LOCATION	= "location";

	/**
	 * Constructor for the tag-completion properties.
	 *
	 * @param element the element that contains the properties,
	 *        for the tag-completion.
	 */
	public TagCompletionProperties( XElement element) {
		super( element);
	}

	/**
	 * Constructor for the tag-completion properties.
	 *
	 * @param props the higher level properties object.
	 */
	public TagCompletionProperties( Properties props) {
		super( props.getElement());
	}

	/**
	 * Constructor for the tag-completion properties, creates a copy.
	 *
	 * @param props the original to copy.
	 */
	public TagCompletionProperties( TagCompletionProperties original) {
		super( new XElement( TAGCOMPLETION));
		
		setLocation( original.getLocation());
		setType( original.getType());
	}

	/**
	 * Constructor for the tag-completion properties.
	 *
	 * @param location the tag-completion location.
	 * @param type the tag-completion type.
	 */
	public TagCompletionProperties( String location, int type) {
		super( new XElement( TAGCOMPLETION));
		
		setLocation( location);
		setType( type);
	}

	/**
	 * Constructor for a new tag-completion properties object.
	 */
	public TagCompletionProperties() {
		super( new XElement( TAGCOMPLETION));
	}

	/**
	 * Return the Location.
	 *
	 * @return the Location.
	 */
	public String getLocation() {
		return getText( LOCATION);
	}

	/**
	 * Set the Location.
	 *
	 * @param location the tag-completion location.
	 */
	public void setLocation( String location) {
		set( LOCATION, location);
	}

	/**
	 * Return the tag-completion type.
	 *
	 * @return the tag-completion type.
	 */
	public int getType() {
		return getInteger( TYPE, XMLGrammar.TYPE_DTD);
	}

	/**
	 * Set the tag-completion type.
	 *
	 * @param type the tag-completion type.
	 */
	public void setType( int type) {
		set( TYPE, type);
	}
	
	/**
	 * Set the toString method 
	 */
	public String toString() {
	    return(URLUtilities.getFileName( getLocation()));
	}
} 
