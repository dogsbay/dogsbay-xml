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

import com.dogsbay.schema.SchemaDocument;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XMLGrammar;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.FileUtilities;
import com.dogsbay.dogsbayaieditor.grammar.GrammarProperties;
import com.dogsbay.dogsbayaieditor.grammar.GrammarPropertiesDialog;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to create a new XML Type.
 *
 * @version	$Revision: 1.4 $, $Date: 2004/09/23 10:48:09 $
 * @author Dogsbay
 */
 public class NewGrammarAction extends AbstractAction {
 	private DogsBayAIEditor parent = null;
	private ConfigurationProperties properties = null;

 	/**
	 * The constructor for the action which creates a new
	 * document.
	 *
	 * @param parent the parent frame.
	 */
 	public NewGrammarAction( DogsBayAIEditor parent, ConfigurationProperties props) {
 		super( "Create Type");
// 		super( parent, props, "New");

		this.parent = parent;
		this.properties = props;

		putValue( MNEMONIC_KEY, Integer.valueOf( 'C'));
//		putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SHORT_DESCRIPTION, "Create XML Type");
 	}
 	
	/**
	 * The implementation of the new grammar action.
	 *
	 * @param the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		DogsBayView view = parent.getView();
		GrammarProperties grammar = null;
		
		if ( view != null) {
			DogsBayDocument doc = view.getDocument();
			XMLGrammar validationGrammar = view.getValidationGrammar();
			grammar = new GrammarProperties( properties, doc);

			SchemaDocument schema = view.getSchema();

//			if ( schema != null) {
//				grammar.setTemplateLocation( schema.getURL().toString());
//			}

			if ( validationGrammar.getLocation() != null && validationGrammar.getLocation().trim().length() > 0) {
				grammar.setValidationGrammar( validationGrammar.getType());
				grammar.setValidationLocation( validationGrammar.getLocation());
				grammar.setUseXMLValidationLocation( !validationGrammar.useExternal());
			}
		} else {
			grammar = new GrammarProperties( properties);
		}
		
		Vector gs = properties.getGrammarProperties();
		Vector names = new Vector();

		for ( int i = 0; i < gs.size(); i++) {
			String name = ((GrammarProperties)gs.elementAt( i)).getDescription();

			names.addElement( name);
		}

		// bring up a type creation dialog and let the user 
		// create a type for this document...
		GrammarPropertiesDialog dialog = FileUtilities.getGrammarPropertiesDialog( "Create", true);
		dialog.show( grammar, true, names);
			
		if ( !dialog.isCancelled()) {
			boolean grammarUpdated = false;
			
			for ( int i = 0; i < gs.size(); i++) {
				String name = ((GrammarProperties)gs.elementAt( i)).getDescription();

				if ( name.equals( grammar.getDescription())) {
					GrammarProperties oldGrammar = (GrammarProperties)gs.elementAt( i);
					oldGrammar.update( grammar);
					grammar = oldGrammar;
					grammarUpdated = true;
					break;
				}
			}
			
			if ( !grammarUpdated) {
				properties.addGrammarProperties( grammar);
			}
			
			if ( view != null) {
				DogsBayDocument doc = view.getDocument();

				parent.setGrammar( grammar);
				parent.setTagCompletionSchemas( FileUtilities.createTagCompletionSchemas( doc, grammar));

				parent.updateStatus();
				parent.getView().updateValidationGrammar();
			}
	

			parent.updateGrammarActions();
			properties.save();
		} 
	}
}
