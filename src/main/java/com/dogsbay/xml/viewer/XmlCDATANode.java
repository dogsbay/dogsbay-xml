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

package com.dogsbay.xml.viewer;

import java.util.Vector;

import org.dom4j.CDATA;

/**
 * The node for the XML tree, containing a CDATA section.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/08/18 10:19:21 $
 * @author Dogs bay
 */
public class XmlCDATANode extends XmlElementNode {
	private CDATA cdata = null;
	private Line[] lines = null;

	/**
	 * Constructs the node for the XML CDATA Section.
	 *
	 * @param cdata The CDATA section.
	 */	
	public XmlCDATANode( Viewer viewer, CDATA cdata) {
		super( viewer);
		
		this.cdata = cdata;
		
		format();		
	}
	
	private void format() {
		Vector lines = new Vector();
		Line current = new Line();
		lines.add( current);
		
		current = parseCDATA( lines, current, cdata);

		this.lines = new Line[lines.size()];
		
		for ( int i = 0; i < lines.size(); i++) {
			this.lines[i] = (Line)lines.elementAt(i);
		}
	}
	
	/**
	 * Returns the formatted lines for this element.
	 *
	 * @return the formatted Lines.
	 */	
	public Line[] getLines() {
		return lines;
	}
} 