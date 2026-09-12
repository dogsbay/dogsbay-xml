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
import java.net.URL;

import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.HTMLUtilities;
import com.dogsbay.dogsbayaieditor.ChangeManager;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.MessageHandler;

/**
 * An action that can be used to make HTML well-formed.
 *
 * @version	$Revision: 1.4 $, $Date: 2004/10/11 08:38:29 $
 * @author Dogs bay
 */
 public class CleanUpHTMLAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	private DogsBayAIEditor parent = null;
	//private Editor editor = null;
	
 	/**
	 * The constructor for the action which allows for cleaning up
	 * the HTML content.
	 *
	 * @param editor the XML Editor
	 */
 	public CleanUpHTMLAction( DogsBayAIEditor parent) {
		super( "Clean Up HTML");

		putValue( MNEMONIC_KEY, Integer.valueOf( 'u'));
		putValue( SHORT_DESCRIPTION, "Cleans Up HTML");
		
		this.parent = parent;
		
		setEnabled( false);
 	}
 	
	/**
	 * Sets the current view.
	 *
	 * @param view the current view.
	 */
	/*public void setView( Object view) {
		if ( view instanceof Editor) {
			editor = (Editor)view;
		} else {
			editor = null;
		}

		setDocument( parent.getDocument());
	}*/
	
	/*public void setDocument( DogsBayDocument doc) {
		 if ( doc != null && doc.isXML()) {
		 	setEnabled( editor != null);
		 } else {
		 	setEnabled( false);
		 }
	}*/

	/**
	 * The implementation of the validate action, called 
	 * after a user action.
	 *
	 * @param event the action event.
	 */
 	public void actionPerformed( ActionEvent event) {
		parent.setWait( true);
		parent.setStatus( "Cleaning Up HTML ...");

		Runnable runner = new Runnable() {
			public void run()  {
				try {
					execute();
				} finally {
					parent.setWait( false);
					parent.setStatus( "Done");
					parent.getView().getEditor().setFocus();
				}
			}
		};

		// Create and start the thread ...
		Thread thread = new Thread( runner);
		thread.start();
 	}
	
	public void execute() {

		try {
//			Editor editor = parent.getView().getEditor();
			ChangeManager changeManager = parent.getView().getChangeManager();
			final DogsBayView view = parent.getView();

			view.updateModel();
			
			DogsBayDocument document = parent.getDocument();
			
			
			
			
			String cleantext = document.getText();
			// strip out XML declaration if present
			int xmlDeclStart = cleantext.indexOf("<?xml");
			if (xmlDeclStart != -1)
			{
				int xmlDeclEnd = cleantext.indexOf("?>",xmlDeclStart);
				cleantext = cleantext.substring(xmlDeclEnd+2,cleantext.length());
			}
			
			final String text = (HTMLUtilities.cleanUpHTML( cleantext)).trim();
			
			URL url = document.getURL();
			String systemId = null;
			
			if ( url != null) {
				systemId = url.toString();
			}

			final String formattedText = parent.getFormatAction().format( text, document.getEncoding(), systemId);
			
			SwingUtilities.invokeLater( new Runnable(){
				public void run() {
					try{
					parent.switchToEditor();
					view.getEditor().setText( formattedText);
					view.updateModel();
					}
					catch ( Exception e) 
					{
						e.printStackTrace();
					}
					
					//FormatAction action = new FormatAction(parent);
					//action.actionPerformed(null);
					
				}
			});
		} catch ( final Exception e) {
			MessageHandler.showError( e, "Clean Up HTML Error");
			e.printStackTrace();
		} catch ( final Throwable t) {
			t.printStackTrace();
		}
 	}
}