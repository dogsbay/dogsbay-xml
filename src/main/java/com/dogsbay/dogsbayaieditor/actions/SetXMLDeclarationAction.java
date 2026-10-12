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
import com.dogsbay.dogsbayaieditor.XMLDeclarationDialog;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.xml.XMLUtilities;


/**
 * An action that can be used to set the XML Declaration
 *
 * @version	$Revision: 1.2 $, $Date: 2004/10/11 08:38:30 $
 * @author Dogs bay
 */
public class SetXMLDeclarationAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	
 	private DogsBayAIEditor parent = null;
 	private XMLDeclarationDialog dialog = null;
 	
 	

 	/**
	 * The constructor for the action which sets the XML Declaration
	 *
	 * @param parent the parent frame.
	 */
 	public SetXMLDeclarationAction( DogsBayAIEditor parent) 
	{
 		super( "Set XML Declaration ...");

		this.parent = parent;

		putValue( MNEMONIC_KEY, Integer.valueOf( 'S'));
		putValue( SHORT_DESCRIPTION, "Set XML Declaration");
		
		// initally don't have this option available
		setEnabled( false);
 	}
 	
 	/**
 	 * The implementation of the Set XML Declaration action
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

		// create a new dialog each time, as the dailog must represent the current document
		if (dialog == null)
		{
			dialog = new XMLDeclarationDialog(parent);
		}
				
		if (document != null)
		{
			dialog.show(document);
		}
		
		if (!dialog.isCancelled()) 
		{
			try
			{
				
				// get the version
				String version;
				if (dialog.isVersion10())
				{
					version = "1.0";
				}
				else
				{
					version = "1.1";
				}
				
				// get the encoding
				String encoding = dialog.getEncoding();
				
				// get the standalone
				String standalone = dialog.getStandalone();
				
				// get the updated text with the new declaration
				final String updatedText = XMLUtilities.updateXMLDeclaration(document,version,encoding,standalone);
				
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
				MessageHandler.showError( err, "Set XML Declaration Error");
				err.printStackTrace();
			} 
			catch ( final Throwable t) {
				t.printStackTrace();
			}
		}
		
 	}
} 	