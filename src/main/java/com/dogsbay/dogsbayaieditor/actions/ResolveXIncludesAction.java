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
import com.dogsbay.xml.DogsBayOutputFormat;
import com.dogsbay.xml.XMLUtilities;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.ChangeManager;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.dogsbay.dogsbayaieditor.FileUtilities;


/**
 * An action that can be used to reolve any XIncludes.
 *
 * @version	$Revision: 1.5 $, $Date: 2004/10/11 08:38:29 $
 * @author Dogs bay
 */
 public class ResolveXIncludesAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	private DogsBayAIEditor parent = null;
	private Editor editor = null;
	private ConfigurationProperties props = null;
	
 	/**
	 * The constructor for the action which allows for resolving XIncludes.
	 *
	 * @param editor the XML Editor
	 */
 	public ResolveXIncludesAction( DogsBayAIEditor parent,ConfigurationProperties props) {
		super( "Resolve XIncludes");

		putValue( MNEMONIC_KEY, Integer.valueOf( 'I'));
		putValue( SHORT_DESCRIPTION, "Resolve XIncludes");
		
		this.parent = parent;
		this.props = props;
		
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
		parent.setStatus( "Resolving XIncludes ...");

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
			if ( document.isError()) {
				throw new Exception("Please make sure the document is well-formed");
			}

			DogsBayOutputFormat format = new DogsBayOutputFormat( "", false, document.getEncoding());
			
			if ( document.hasDeclaration()) {
				if ( document.getStandalone() != DogsBayDocument.STANDALONE_NONE) {
					format.setStandalone( document.getStandalone());
					format.setOmitStandalone( false);
				}
				
				format.setVersion( document.getVersion());

				format.setOmitEncoding( !document.hasEncoding());
				
				format.setSuppressDeclaration( false);
			} else {
				format.setSuppressDeclaration( true);
			}

			final String updatedText = XMLUtilities.resolveXIncludes( document.getText(), 
					document.getEncoding(),document.getURL(), format);
			
			SwingUtilities.invokeLater( new Runnable(){
				public void run() {
				if (!props.isOpenXIncludeInNewDocument()) 
				{
					parent.switchToEditor();
					parent.getView().getEditor().setText(updatedText);
					parent.getView().updateModel();
				}	
				else 
				{
					try{
					
					DogsBayDocument newDocument = FileUtilities.createDocument();
					
					newDocument.setText(updatedText);
					
					parent.open( newDocument, null);
					
					}
					catch(Exception e)
					{
						// ignore
					}
				}
				
				}
			});
		} catch ( final Exception e) {
			MessageHandler.showError( e, "Resolving XIncludes Error");
			e.printStackTrace();
		} catch ( final Throwable t) {
			t.printStackTrace();
		}
 	}
}