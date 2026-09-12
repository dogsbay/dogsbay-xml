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
 * @version	$Revision: 1.2 $, $Date: 2004/09/15 16:23:40 $
 * @author Dogsbay
 */
public class ParameterProperties extends Properties {
	
	public static final String PARAMETER	= "parameter";

	private static final String NAME		= "name";
	private static final String VALUE		= "value";

	/**
	 * Constructor for the scenario properties.
	 *
	 * @param element the element that contains the properties,
	 *        for the scenario-type.
	 */
	public ParameterProperties( XElement element) {
		super( element);
	}

	/**
	 * Constructor for the scenario properties.
	 *
	 * @param props the higher level properties object.
	 */
	public ParameterProperties( Properties props) {
		super( props.getElement());
	}

	/**
	 * Constructor for the scenario properties, creates a copy.
	 *
	 * @param props the original to copy.
	 */
	public ParameterProperties( ParameterProperties original) {
		super( new XElement( PARAMETER));
		
		setName( original.getName());
		setValue( original.getValue());
	}

	/**
	 * Constructor for the scenario properties, creates a copy.
	 *
	 * @param props the original to copy.
	 */
	public ParameterProperties( String name, String value) {
		super( new XElement( PARAMETER));
		
		setName( name);
		setValue( value);
	}

	/**
	 * Constructor for a new scenario properties object.
	 */
	public ParameterProperties() {
		super( new XElement( PARAMETER));
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
	 * @param name the parameter name.
	 */
	public void setName( String name) {
		set( NAME, name);
	}

	/**
	 * Return the value.
	 *
	 * @return the value.
	 */
	public String getValue() {
		return getText( VALUE);
	}

	/**
	 * Set the value.
	 *
	 * @param value the param value.
	 */
	public void setValue( String value) {
		set( VALUE, value);
	}
	
	public String toString() {
	    return(this.getName());
	}
} 
