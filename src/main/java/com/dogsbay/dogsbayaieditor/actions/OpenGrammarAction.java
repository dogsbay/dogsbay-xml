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

import javax.swing.AbstractAction;

import com.dogsbay.schema.SchemaDocument;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.FileUtilities;
import com.dogsbay.dogsbayaieditor.grammar.GrammarProperties;
import com.dogsbay.dogsbayaieditor.grammar.GrammarSelectionDialog;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to open a XML Document.
 *
 * @version	$Revision: 1.3 $, $Date: 2004/09/23 10:48:09 $
 * @author Dogsbay
 */
public class OpenGrammarAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	
	private DogsBayAIEditor parent = null;
	private ConfigurationProperties properties = null;

 	/**
	 * The constructor for the action which allows opening 
	 * of XML Documents.
	 *
	 * @param parent the parent frame.
	 */
 	public OpenGrammarAction( DogsBayAIEditor parent, ConfigurationProperties props) {
 		super( "Set Type ...");
// 		super( parent, props, "Open");

		this.parent = parent;
		this.properties = props;

		putValue( MNEMONIC_KEY, Integer.valueOf( 'S'));
		putValue( SHORT_DESCRIPTION, "Open a XML Type");
 	}
 	
 	/**
 	 * The implementation of the open grammar action.
 	 *
 	 * @param the action event.
 	 */
 	public void actionPerformed( ActionEvent e) {
 		DogsBayDocument doc = parent.getDocument();
 		GrammarProperties grammar = parent.getGrammar();
 		
 		// bring up a type dialog and let the user 
 		// change the type for this document...
 		GrammarSelectionDialog dialog = FileUtilities.getGrammarSelectionDialog();
 		dialog.setProperties( properties.getGrammarProperties());
		dialog.setSelectedGrammar( grammar);
 		dialog.setVisible( true);
 			
 		if ( !dialog.isCancelled()) {
			grammar = dialog.getSelectedType();

 			parent.setGrammar( grammar);
 			parent.setTagCompletionSchemas( FileUtilities.createTagCompletionSchemas( doc, grammar));

 			parent.updateStatus();
 			parent.getView().updateValidationGrammar();

// 			parent.setGrammar( grammar);
// 			parent.setRelax( relax);
// 			parent.setDTD( dtd);
// 			parent.setSchema( schema);
 		} 
 	}
}
