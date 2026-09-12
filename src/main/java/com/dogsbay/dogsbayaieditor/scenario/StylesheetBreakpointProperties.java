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

package com.dogsbay.dogsbayaieditor.scenario;

import com.dogsbay.xml.XElement;
import com.dogsbay.xml.properties.Properties;

/**
 * Handles the properties for a Transformation scenario.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:56:35 $
 * @author Dogsbay
 */
public class StylesheetBreakpointProperties extends BreakpointProperties {
	
	public static final String STYLESHEET_BREAKPOINT	= "stylesheet-breakpoint";

	/**
	 * Constructor for the scenario properties.
	 *
	 * @param element the element that contains the properties,
	 *        for the scenario-type.
	 */
	public StylesheetBreakpointProperties( XElement element) {
		super( element);
	}

	/**
	 * Constructor for the scenario properties.
	 *
	 * @param props the higher level properties object.
	 */
	public StylesheetBreakpointProperties( Properties props) {
		super( props.getElement());
	}

	/**
	 * Constructor for the scenario properties, creates a copy.
	 *
	 * @param props the original to copy.
	 */
	public StylesheetBreakpointProperties( BreakpointProperties original) {
		super( new XElement( STYLESHEET_BREAKPOINT));
		
		setURL( original.getURL());
		setLine( original.getLine());
		setEnabled( original.isEnabled());
	}

	/**
	 * Constructor for the scenario properties, creates a copy.
	 *
	 * @param props the original to copy.
	 */
	public StylesheetBreakpointProperties( String url, int line, boolean enabled) {
		super( new XElement( STYLESHEET_BREAKPOINT));
		
		setURL( url);
		setLine( line);
		setEnabled( enabled);
	}

	/**
	 * Constructor for a new scenario properties object.
	 */
	public StylesheetBreakpointProperties() {
		super( new XElement( STYLESHEET_BREAKPOINT));
	}
} 
