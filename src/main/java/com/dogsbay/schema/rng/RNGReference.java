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

package com.dogsbay.schema.rng;

import java.util.Vector;

/**
 * A container for a SchemaElement and possible attributes/child elements
 *
 * @version	$Revision: 1.2 $, $Date: 2004/09/23 10:49:47 $
 * @author Dogsbay
 */
public class RNGReference {
	private Vector elements 	= null;
	private Vector attributes	= null;

	private RNGDefinition definition = null;
	private String name = null;
	private boolean required = false;
	private boolean external = false;
	
	public RNGReference( String name, boolean required) {
		this( name, required, false);
	}

	public RNGReference( String name, boolean required, boolean external) {
		this.name		= name;
		this.required	= required;
		this.external	= external;
	}
	
	public String getName() {
		return name;
	}

	public boolean isRequired() {
		return required;
	}

	public boolean isExternal() {
		return external;
	}

	public Vector getElements() {
		if ( elements == null && isResolved()) {
			elements = new Vector();
			
			Vector elems = definition.getElements();
			
			for ( int i = 0; i < elems.size(); i++) {
				RNGElement element = (RNGElement)elems.elementAt(i);
//				RNGElement childElement = new RNGElement( element.getName(), element.getNamespace(), element.getPrefix(), element.isRequired() && required);
				elements.addElement( element);
			}
		}

		return elements;
	}

	public Vector getAttributes() {
//		System.out.println( "RNGReference.getAttributes() ["+getName()+"]");
		if ( attributes == null && isResolved()) {
			attributes = new Vector();
			
			Vector attribs = definition.getAttributes();
			
			for ( int i = 0; i < attribs.size(); i++) {
				RNGAttribute attribute = (RNGAttribute)attribs.elementAt(i);
//				RNGAttribute childAttribute = new RNGAttribute( attribute.getName(), attribute.getNamespace(), attribute.getPrefix(), attribute.getType(), attribute.getEnumeration(), attribute.isRequired() && required);
				attributes.addElement( attribute);
			}
		}

		return attributes;
	}

	public void setDefinition( RNGDefinition definition) {
		this.definition = definition;
	}
	
	public boolean isResolved() {
		return definition != null;
	}
} 
