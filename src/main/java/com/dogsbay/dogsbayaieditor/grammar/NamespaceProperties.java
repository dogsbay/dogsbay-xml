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
import com.dogsbay.xml.properties.Properties;

/**
 * Handles the properties for a namespace.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:54:01 $
 * @author Dogsbay
 */
public class NamespaceProperties extends Properties {
	
	public static final String NAMESPACE	= "additional-namespace";

	private static final String URI		= "uri";
	private static final String PREFIX	= "prefix";

	/**
	 * Constructor for the namespace properties.
	 *
	 * @param element the element that contains the properties,
	 *        for the namespace.
	 */
	public NamespaceProperties( XElement element) {
		super( element);
	}

	/**
	 * Constructor for the namespace properties.
	 *
	 * @param props the higher level properties object.
	 */
	public NamespaceProperties( Properties props) {
		super( props.getElement());
	}

	/**
	 * Constructor for the namespace properties, creates a copy.
	 *
	 * @param props the original to copy.
	 */
	public NamespaceProperties( NamespaceProperties original) {
		super( new XElement( NAMESPACE));
		
		setURI( original.getURI());
		setPrefix( original.getPrefix());
	}

	/**
	 * Constructor for the namespace properties.
	 *
	 * @param uri the namespace uri.
	 * @param prefix the namespace prefix.
	 */
	public NamespaceProperties( String uri, String prefix) {
		super( new XElement( NAMESPACE));
		
		setURI( uri);
		setPrefix( prefix);
	}

	/**
	 * Constructor for a new namespace properties object.
	 */
	public NamespaceProperties() {
		super( new XElement( NAMESPACE));
	}

	/**
	 * Return the URI.
	 *
	 * @return the URI.
	 */
	public String getURI() {
		return getText( URI);
	}

	/**
	 * Set the namespace URI.
	 *
	 * @param uri the namespace URI.
	 */
	public void setURI( String uri) {
		set( URI, uri);
	}

	/**
	 * Return the namespace prefix.
	 *
	 * @return the namespace prefix.
	 */
	public String getPrefix() {
		return getText( PREFIX);
	}

	/**
	 * Set the namespace prefix.
	 *
	 * @param prefix the namespace prefix.
	 */
	public void setPrefix( String prefix) {
		set( PREFIX, prefix);
	}
} 
