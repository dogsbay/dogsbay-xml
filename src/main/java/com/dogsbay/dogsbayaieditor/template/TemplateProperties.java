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

package com.dogsbay.dogsbayaieditor.template;

import java.net.URL;

import com.dogsbay.xml.XAttribute;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.properties.Properties;
import com.dogsbay.dogsbayaieditor.Identity;
import com.dogsbay.dogsbayaieditor.URLUtilities;

/**
 * Handles the template properties.
 *
 * @version	$Revision: 1.3 $, $Date: 2004/09/15 15:01:27 $
 * @author Dogsbay
 */
public class TemplateProperties extends Properties {
	
	private static final boolean DEBUG	= false;

	public static final String TEMPLATE_PROPERTIES	= "template";

	private static final String NAME	= "name";
	private static final String URL		= "url";
	private static final String LAST_USED_TIMESTAMP = "lastUsedTimestamp";

	/**
	 * Constructor for a new grammar properties object.
	 */
	public TemplateProperties() {
		super( new XElement( TEMPLATE_PROPERTIES));
	}

	/**
	 * Creates the Template Properties wrapper.
	 *
	 * @param element the properties element.
	 */
	public TemplateProperties( XElement element) {
		super( element);
	}

	/**
	 * Creates the Template Properties wrapper.
	 *
	 * @param props the properties object.
	 */
	public TemplateProperties( Properties props) {
		super( props.getElement());
	}

	/**
	 * Constructor for a new template properties object.
	 *
	 * @param template imported template.
	 */
	public TemplateProperties( URL url, XElement template) {
		super( new XElement( TEMPLATE_PROPERTIES));
		
		importTemplate( url, template);
	}

	/**
	 * Creates the Template wrapper.
	 *
	 * @param url the url to the template document.
	 * @param name the name of the template  document.
	 */
	public TemplateProperties( String name, URL url) {
		super( new XElement( TEMPLATE_PROPERTIES));
		
		setURL( url);
		setName( name);
	}

	/**
	 * Sets the url for the document.
	 *
	 * @param url the url for the document.
	 */
	public void setURL( URL url) {
		set( URL, url.toString());
	}

	/**
	 * Sets the url for the document.
	 *
	 * @param url the url for the document.
	 */
	public void setURL( String url) {
		set( URL, url);
	}

	/**
	 * Get the url for the document.
	 *
	 * @return the document url.
	 */
	public URL getURL() {
//		System.out.println("DocumentProperties.getURL()");
		URL url = null;

		try {
			url = new URL( getText( URL));
		} catch (Exception e) { 
			// should not happen
//			e.printStackTrace();
		}
		
		return url;
	}

	/**
	 * Return the name.
	 *
	 * @return the name.
	 */
	public String getName() {
		return getText( NAME);
	}

	/**
	 * Set the name.
	 *
	 * @param name the template name.
	 */
	public void setName( String name) {
		set( NAME, name);
	}
	
	public void importTemplate( URL url, XElement element) {
		setName( getAttributeValue( element, "name"));
		setURL( URLUtilities.resolveURL( url, getAttributeValue( element, "src")));
	}

	public XElement exportTemplate( URL url) {
		//XElement root = new XElement( "template", "http://www.dogsbay.ai/dogsbay-editor/"+Identity.getIdentity().getVersion()+"/");
		XElement root = new XElement( "template");

		addAttribute( root, "name", getName());
		addAttribute( root, "src", URLUtilities.getRelativePath( url, getURL().toString()));

		return root;
	}
	
	private static String getAttributeValue( XElement element, String attributeName) {
		if ( element != null) {
			return element.getAttribute( attributeName);
		}
		
		return null;
	}
	
	private static void addAttribute( XElement element, String name, String value) {
		if ( value != null) {
			element.putAttribute( new XAttribute( name, value));
		}
	}
	
	/**
	 * Get the last used timestamp for this template.
	 * Used for MRU (Most Recently Used) sorting.
	 *
	 * @return the timestamp in milliseconds, or 0 if never used
	 */
	public long getLastUsedTimestamp() {
		String value = getText(LAST_USED_TIMESTAMP);
		if (value != null && !value.isEmpty()) {
			try {
				return Long.parseLong(value);
			} catch (NumberFormatException e) {
				return 0;
			}
		}
		return 0;
	}

	/**
	 * Set the last used timestamp for this template.
	 *
	 * @param timestamp the timestamp in milliseconds
	 */
	public void setLastUsedTimestamp(long timestamp) {
		set(LAST_USED_TIMESTAMP, String.valueOf(timestamp));
	}

	/**
	 * Record that this template was just used.
	 * Updates the last used timestamp to the current time.
	 */
	public void recordUsage() {
		setLastUsedTimestamp(System.currentTimeMillis());
	}

	/**
	 * Check if this template has been used (has a timestamp).
	 *
	 * @return true if the template has been used at least once
	 */
	public boolean hasBeenUsed() {
		return getLastUsedTimestamp() > 0;
	}

	public String toString() {
	    return(this.getName());
	}
} 
