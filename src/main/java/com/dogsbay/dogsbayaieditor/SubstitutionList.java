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

import java.awt.event.MouseEvent;
import java.util.Vector;

import javax.swing.AbstractListModel;
import javax.swing.JList;

import org.bounce.event.DoubleClickListener;

import com.dogsbay.dogsbayaieditor.properties.TextPreferences;

/**
 * The panel that shows the results for a XPath search.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:52:46 $
 * @author Dogsbay
 */
public class SubstitutionList extends JList {
	private Object view = null;
	private Vector nodes = null;
	private DogsBayAIEditor parent = null;

	/**
	 * The constructor for the about dialog.
	 *
	 * @param frame the parent frame.
	 */
	public SubstitutionList( DogsBayAIEditor parent) {
		super();
		
		this.parent = parent;
		
		setFont( TextPreferences.getBaseFont().deriveFont( (float)12));

		addMouseListener( new DoubleClickListener() { 
			public void doubleClicked( MouseEvent e) {
				int index = getSelectedIndex();

				if ( index != -1) { // A row is selected...
					// perform the selection.
					substitutionSelected();
				}
			}
		});
		
		setVisibleRowCount( 4);
		setFixedCellHeight( 18);
		
	}

	public void updatePreferences() {
		setFont( TextPreferences.getBaseFont().deriveFont( (float)12));
	}

	public void setCurrent( Object view) {
		this.view = view;
	}

	public void setSubstitutes( Vector results) {
		nodes = results;
		
		setModel( new SubstitionListModel( results));
	}
	
	private void substitutionSelected() {
//		Object node = getModel()model.getElementAt( getSelectedIndex());
//		
//		if ( view instanceof Editor) {
//		}
	}

	class SubstitionListModel extends AbstractListModel {
		Vector names = null;
		
		public SubstitionListModel( Vector list) {
			names = new Vector();
			
			if ( list != null) {
				for ( int i = 0; i < list.size(); i++) {
					String name = (String)list.elementAt(i);
					int index = -1;

					for ( int j = 0; j < names.size() && index == -1; j++) {
						// Compare alphabeticaly
						if ( name.compareToIgnoreCase( (String)names.elementAt(j)) <= 0) {
							index = j;
						}
					}
					
					if ( index != -1) {
						names.insertElementAt( name, index);
					} else {
						names.addElement( name);
					}
				}
				
//				if ( cdata && "![CDATA[".startsWith( prefix)) {
//					names.addElement( "![CDATA[");
//				}
//
//				if ( xmlns && "xmlns".startsWith( prefix)) {
//					names.addElement( "xmlns");
//				}
			}
		}
		
		public int getSize() {
			if ( names != null) {
				return names.size();
			}
			
			return 0;
		}

		public Object getElementAt( int i) {
			return names.elementAt( i);
		}

		public String getName( int i) {
			return (String)names.elementAt( i);
		}
	}
} 
