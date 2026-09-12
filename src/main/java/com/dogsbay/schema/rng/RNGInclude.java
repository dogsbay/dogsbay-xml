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
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:39:07 $
 * @author Dogsbay
 */
public class RNGInclude {
	private String href = null;
	protected Vector definitions	= null;
	protected Vector elements		= null;
	protected Vector references		= null;
	
	public RNGInclude( String href) {
		this.href = href;
		
		definitions = new Vector();
	}
	
	public String getHref() {
		return href;
	}
	
	public boolean addDefinition( RNGDefinition definition) {
		if ( definition != null) {
			for ( int i = 0; i < definitions.size(); i++) {
				RNGDefinition def = (RNGDefinition)definitions.elementAt(i);
				
				if ( def.getName().equals( definition.getName())) {
					def.combine( definition);
					return false;
				}
			}
			
			definitions.addElement( definition);
			return true;
		} else {
			return false;
		}
	}
	
	public boolean include( RNGGrammar grammar) {
		Vector newDefs = new Vector();

		if ( grammar.getURI().equals( href)) {
			references = grammar.getReferences();
			elements = grammar.getElements();
			Vector oldDefs = definitions;
			Vector grammarDefs = grammar.getDefinitions();

			for ( int i = 0; i < grammarDefs.size(); i++) {
				RNGDefinition def = (RNGDefinition)grammarDefs.elementAt(i);
				boolean skip = false;
				
				for ( int j = 0; j < oldDefs.size(); j++) {
					RNGDefinition oldDef = (RNGDefinition)oldDefs.elementAt(j);
					if ( oldDef.getName().equals( def.getName())) {
						skip = true;
						break;
					}
				}
				
				if ( !skip) {
					boolean added = false;
					
					for ( int j = 0; j < newDefs.size(); j++) {
						RNGDefinition newDef = (RNGDefinition)newDefs.elementAt(j);
						
						if ( newDef.getName().equals( def.getName())) {
							newDef.combine( def);
							added = true;
						}
					}
					
					if ( !added) {
						newDefs.addElement( def);
					}
				}
			}
			definitions = newDefs;
			return true;
		}

		return false;
	}

	public Vector getDefinitions() {
		return definitions;
	}

	public Vector getElements() {
		return elements;
	}

	public Vector getReferences() {
		return references;
	}
} 
