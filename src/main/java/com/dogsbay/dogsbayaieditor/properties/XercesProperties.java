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

package com.dogsbay.dogsbayaieditor.properties;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.properties.PropertiesFile;
import com.dogsbay.dogsbayaieditor.Main;

public class XercesProperties extends PropertiesFile {

	public static final String XERCES_PROPERTIES_FILENAME = "xerces-properties";
	public static final String XERCES_PROPERTIES = "xerces-properties";
	
	//http://apache.org/xml/features/warn-on-duplicate-entitydef
	private static final String WARN_ON_DUPLICATE_ENTITYDEF = "warn-on-duplicate-entitydef";
	
	/**
	 * A section of the settings document, rather than a file of its own.
	 *
	 * <p>Splitting these out gave one logical thing four files, split by which
	 * class happened to extend PropertiesFile rather than by anything a reader
	 * would recognise.
	 */
	public XercesProperties(com.dogsbay.xml.XElement element) {
		super(null, element);
	}

	public XercesProperties(String fileName, String rootName) {
		super(loadPropertiesFile(fileName, rootName));
	}
	/**
	 * Creates the Xerces Properties.
	 *
	 * @param element the print preferences element.
	 */
	/*public XercesProperties(DogsBayDocument document, XElement element) {
		super(document, element);
	}*/
	
	public void setWarnOnDuplicateEntityDef( boolean enable) {
		set( WARN_ON_DUPLICATE_ENTITYDEF, enable);
	}

	public boolean isWarnOnDuplicateEntityDef() {
		return getBoolean( WARN_ON_DUPLICATE_ENTITYDEF, false);
	}
}
