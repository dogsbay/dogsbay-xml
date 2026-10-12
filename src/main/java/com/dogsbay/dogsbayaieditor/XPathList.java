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

import java.util.Vector;

/**
 * This XPathList is used to keep a list of all the XPath results for a document.
 *
 * @version $Revision: 1.1 $, $Date: 2004/07/22 15:57:58 $
 * @author Dogsbay
 */
public class XPathList {
	private Vector results = null;

	/**
	 * A list of XML Errors.
	 */
	public XPathList() {
		results = new Vector();
	}
	
	public Vector getResults() {
		return results;
	}
	
	public void setResults( Vector results) {
		this.results = results;
	}
	
	public void reset() { 
		results.removeAllElements();
	}
}	
