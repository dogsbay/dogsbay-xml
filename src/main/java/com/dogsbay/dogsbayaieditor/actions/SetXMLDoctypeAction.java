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
import javax.swing.SwingUtilities;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.XMLDoctypeDialog;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.xml.XMLUtilities;


/**
 * An action that can be used to set the XML DOCTYPE
 *
 * @version	$Revision: 1.6 $, $Date: 2004/10/11 08:38:30 $
 * @author Dogs bay
 */
public class SetXMLDoctypeAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	
 	private DogsBayAIEditor parent = null;
 	private XMLDoctypeDialog dialog = null;
 	
 	

 	/**
	 * The constructor for the action which sets the XML Doctype
	 *
	 * @param parent the parent frame.
	 */
 	public SetXMLDoctypeAction( DogsBayAIEditor parent) 
	{
 		super( "Set DOCTYPE Declaration...");

		this.parent = parent;

		putValue( MNEMONIC_KEY, Integer.valueOf( 'O'));
		putValue( SHORT_DESCRIPTION, "Set DOCTYPE Declaration");
		
		// initally don't have this option available
		setEnabled( false);
 	}
 	
 	/**
 	 * The implementation of the Set XML DOCTYPE action
 	 *
 	 * @param the action event.
 	 */
 	public void actionPerformed( ActionEvent e) 
	{
 		// make sure the document is up to date
 		final DogsBayView view = parent.getView();
		view.updateModel();

		// get the document
		DogsBayDocument document = parent.getDocument();
		
		if ( document.isError()) {
			MessageHandler.showError( "Please make sure the document is well-formed.", "Parser Error");
			return;
		}

		if (dialog == null)
		{
			dialog = new XMLDoctypeDialog(parent);
		}
				
		if (document != null)
		{
			dialog.show(document);
		}
		
		if (!dialog.isCancelled()) 
		{
			try
			{
				
				// get the element name
				String name = dialog.getName();
				
				// get the keyword (SYSTEM or PUBLIC)
				String type = dialog.getDoctypeString();
				
				// get the public identifier
				String publicID = dialog.getPublicID();
				
				// get the system identifier
				String systemID = dialog.getSystemID();
				
				// Need to crete a temp exchangerdocument as if we update the existing one
				// before we set text on the editor, the undo functionality is not available
				// This event modele should be looked at again in future version.
				DogsBayDocument tempDoc =  new DogsBayDocument(document.getText());
				
				// update the dom with the new doctype
				XMLUtilities.setXMLDoctype(tempDoc,name,type,publicID,systemID);
				
				// update the text in this temp doc
				tempDoc.update();
				
				// get the text
				final String updatedText = tempDoc.getText();
				
				SwingUtilities.invokeLater( new Runnable(){
					public void run() {
						try{
						parent.switchToEditor();
						view.getEditor().setText(updatedText);
						view.updateModel();
						}
						catch ( Exception e) 
						{
							e.printStackTrace();
						}
					}
				});
			
			} 
			catch ( final Exception err) {
				MessageHandler.showError( err, "Set Doctype Declaration Error");
				err.printStackTrace();
			} 
			catch ( final Throwable t) {
				t.printStackTrace();
			}
		}
		
 	}
} 	