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

package com.dogsbay.schema;

import java.net.URL;
import java.util.Vector;

import com.dogsbay.xml.XMLGrammar;

/**
 * A cross-grammar container for element related information.
 *
 * @version	$Revision: 1.2 $, $Date: 2004/09/23 10:50:00 $
 * @author Dogsbay
 */
public interface SchemaDocument {
	public static final int TYPE_XSD = XMLGrammar.TYPE_XSD;
	public static final int TYPE_DTD = XMLGrammar.TYPE_DTD;
	public static final int TYPE_RNG = XMLGrammar.TYPE_RNG;
	public static final int TYPE_RNC = XMLGrammar.TYPE_RNC;

	/**
	 * Get all the elements defined by this schema.
	 *
	 * @return a list of ElementInformation objects.
	 */
	public Vector getElements();

	/**
	 * Get all the any elements defined by this schema.
	 *
	 * @return a list of any elements.
	 */
	public Vector getAnyElements();

	/**
	 * Get all the global elements defined by this schema.
	 *
	 * @return a list of ElementInformation objects.
	 */
	public Vector getGlobalElements();

	/**
	 * Updates all prefixes based on the namespace 
	 * declarations supplied.
	 *
	 * @param declarations a list of namespace declarations.
	 */
	public void updatePrefixes( Vector declarations);

	/**
	 * Get the url for this schema document.
	 *
	 * @return the url.
	 */
	public URL getURL();

	/**
	 * Get this document's type.
	 *
	 * @return the type of this document.
	 */
	public int getType();
} 
