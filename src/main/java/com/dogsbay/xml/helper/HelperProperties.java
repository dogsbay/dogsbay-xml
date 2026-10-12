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

package com.dogsbay.xml.helper;

import com.dogsbay.xml.XElement;
import com.dogsbay.xml.properties.Properties;

/**
 * Handles the properties for the Helper.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:45:46 $
 * @author Dogsbay
 */
public class HelperProperties extends Properties {
	private static final String DIVIDER_LOCATION		= "divider-location";
	private static final int DEFAULT_DIVIDER_LOCATION	= 100;

	/**
	 * Constructor for the Helper properties.
	 *
	 * @param element the element that contains the properties,
	 *        for the Helper.
	 */
	public HelperProperties( XElement element) {
		super( element);
	}

	/**
	 * Set the split divider location.
	 *
	 * @param location the split divider location.
	 */
	public void setDividerLocation( int location) {
		set( DIVIDER_LOCATION, location);
	}

	/**
	 * Gets the location of the split divider.
	 *
	 * @return the location of the split divider.
	 */
	public int getDividerLocation() {
		return getInteger( DIVIDER_LOCATION, DEFAULT_DIVIDER_LOCATION);
	}
} 
