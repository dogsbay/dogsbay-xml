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

package com.dogsbay.dogsbayaieditor.actions;

import java.awt.event.ActionEvent;
import java.util.Vector;

import javax.swing.AbstractAction;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.FileUtilities;
import com.dogsbay.dogsbayaieditor.grammar.GrammarProperties;
import com.dogsbay.dogsbayaieditor.grammar.GrammarPropertiesDialog;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to open a XML Document.
 *
 * @version	$Revision: 1.6 $, $Date: 2004/09/28 13:51:01 $
 * @author Dogsbay
 */
public class GrammarPropertiesAction extends AbstractAction {
	private DogsBayAIEditor parent = null;
	private ConfigurationProperties properties = null;

 	/**
	 * The constructor for the action which changes Grammar properties.
	 *
	 * @param parent the parent frame.
	 */
 	public GrammarPropertiesAction( DogsBayAIEditor parent, ConfigurationProperties props) {
// 		super( parent, props, "Properties");
 		super( "Type Properties");
		
		this.parent = parent;
		this.properties = props;

		putValue( MNEMONIC_KEY, Integer.valueOf( 'P'));
//		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Schema16.gif"));
//		putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SHORT_DESCRIPTION, "XML Type Properties");
		
		setEnabled( false);
 	}
 	
	/**
	 * The implementation of the new grammar action.
	 *
	 * @param the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		DogsBayDocument doc = parent.getDocument();
		GrammarProperties grammar = parent.getGrammar();
		
		Vector gs = properties.getGrammarProperties();
		Vector names = new Vector();

		for ( int i = 0; i < gs.size(); i++) {
			String name = ((GrammarProperties)gs.elementAt( i)).getDescription();
			
			if ( !name.equals( grammar.getDescription())) {
				names.addElement( name);
			}
		}

		// bring up a type dialog and let the user 
		// change the type for this document...
		GrammarPropertiesDialog dialog = FileUtilities.getGrammarPropertiesDialog( "OK", false);
		dialog.show( grammar, false, names);
			
		if ( !dialog.isCancelled()) {
			parent.updateGrammar( grammar);
			properties.save();
		} 
	}
}
