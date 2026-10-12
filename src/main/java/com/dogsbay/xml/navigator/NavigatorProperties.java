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

package com.dogsbay.xml.navigator;

import com.dogsbay.xml.XElement;
import com.dogsbay.xml.properties.Properties;

/**
 * Handles the properties for the navigator.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:46:20 $
 * @author Dogsbay
 */
public class NavigatorProperties extends Properties {
	private static final String SHOW_ATTRIBUTE_VALUES	= "show-attribute-values";

	/**
	 * Constructor for the navigator properties.
	 *
	 * @param element the element that contains the properties,
	 *        for the navigator.
	 */
	public NavigatorProperties( XElement element) {
		super( element);
	}

	public void setShowAttributeValues( boolean show) {
		set( SHOW_ATTRIBUTE_VALUES, show);
	}

	public boolean isShowAttributeValues() {
		return getBoolean( SHOW_ATTRIBUTE_VALUES, false);
	}
} 
