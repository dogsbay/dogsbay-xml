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

package com.dogsbay.dogsbayaieditor;

import com.dogsbay.xml.XMLGrammar;

/**
 * The default implementation of XML Grammar.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:52:46 $
 * @author Dogsbay
 */
public class XMLGrammarImpl implements XMLGrammar {
	private String location = null;
 	private int type = TYPE_XSD;
	private boolean external = false;

 	public String getLocation() {
		return location;
 	}

 	public void setLocation( String location) {
//		System.out.println( "XMLGrammarImpl.setLocation( "+location+")");
		if ( location == null || location.trim().length() == 0) {
	 		this.location = null;
		} else {
			this.location = location;
		}
 	}

 	public int getType() {
		return type;
 	}
	
 	public void setType( int type) {
 		this.type = type;
 	}

 	public void setExternal( boolean external) {
 		this.external = external;
 	}

 	public boolean useExternal() {
		return external;
 	}
	
	public String toString() {
		return "XMLGrammarImpl[location="+location+"ext="+external+",type="+type+"]";
	}
} 
