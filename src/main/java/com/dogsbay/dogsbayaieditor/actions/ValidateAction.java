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
import java.io.IOException;
import java.net.URL;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;

import org.xml.sax.SAXParseException;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XMLError;
import com.dogsbay.xml.XMLErrorHandler;
import com.dogsbay.xml.XMLErrorReporter;
import com.dogsbay.dogsbayaieditor.plugins.PluginViewPanel;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.FileUtilities;
import com.dogsbay.dogsbayaieditor.ValidationDialog;
import com.dogsbay.dogsbayaieditor.XMLGrammarImpl;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;

/**
 * An action that can be used to parse the XML content.
 *
 * @version	$Revision: 1.10 $, $Date: 2005/08/19 13:22:05 $
 * @author Dogsbay
 */
 public class ValidateAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	private DogsBayAIEditor parent = null;
	private ValidationDialog dialog = null;
	private Object view = null;
	
	private ImageIcon validatedIcon = null;
	private ImageIcon unvalidatedIcon = null;
	
 	/**
	 * The constructor for the action which allows for validating
	 * the XML content.
	 *
	 * @param editor the XML Editor
	 */
 	public ValidateAction( DogsBayAIEditor parent) {
		super( "Validate");
		
		validatedIcon = DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Validate16.gif");
		unvalidatedIcon = DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Unvalidate16.gif");

		putValue( MNEMONIC_KEY, Integer.valueOf( 'V'));
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_F7, 0, false));
		putValue( SMALL_ICON, unvalidatedIcon);
//		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/xml/editor/icons/Parse16.gif"));
		putValue( SHORT_DESCRIPTION, "Validate");
		
		this.parent = parent;
		
		setEnabled( false);
 	}
 	
	/**
	 * Sets the current view.
	 *
	 * @param view the current view.
	 */
	public void setView( Object view) {
		this.view = view;
		
		setDocument( parent.getDocument());
	}

	public void setDocument( DogsBayDocument doc) {
		if ( doc != null && doc.isXML()) {
			setEnabled( view != null);
		} else {
			setEnabled( false);
		}
	}
	
	public void setValidated( boolean valid) {
		if ( valid) {
			putValue( SMALL_ICON, validatedIcon);
		} else {
			putValue( SMALL_ICON, unvalidatedIcon);
		}
	}

	/**
	 * The implementation of the validate action, called 
	 * after a user action.
	 *
	 * @param event the action event.
	 */
 	public void actionPerformed( ActionEvent event) {
		parent.setWait( true);
		parent.setStatus( "Validating ...");

		Runnable runner = new Runnable() {
			public void run()  {
				try {
					execute();
				} finally {
					parent.setWait( false);
					parent.setStatus( "Done");
					parent.getView().getCurrentView().setFocus();
				}
			}
		};

		// Create and start the thread ...
		Thread thread = new Thread( runner);
		thread.start();
 	}
	
	public void execute() {
		
		//Vector expandedRows = null;
		try {
			DogsBayDocument document = parent.getDocument();

			parent.getView().updateModel();
			
//			if ( document.isError()) {
//				return;
//			}
			
			if(view instanceof PluginViewPanel) {
				((PluginViewPanel)view).saveState();
				
			}

			String schemaLocation = "Internal DocType Declaration";
			
			boolean invalid = true;
			while ( invalid) {
				try {
					URL url = document.checkSchemaLocation();

					if ( url != null) {
						schemaLocation = url.toString();
					}
					
					invalid = false;
				} catch ( IOException ioe) {
					// show the message
					if ( dialog == null) {																			
						dialog = new ValidationDialog( parent, "Please provide a valid schema location.");
					}

					dialog.show( parent.getView().getValidationGrammar(), ioe.getMessage());
					
					if ( dialog.isCancelled()) {
						return;
					} 
					
					parent.updateStatus();
				}
			}
			XMLGrammarImpl grammar = parent.getView().getValidationGrammar();
			
			if ( grammar.getType() == XMLGrammarImpl.TYPE_DTD || grammar.getType() == XMLGrammarImpl.TYPE_XSD) {
				parent.getOutputPanel().startCheck( "VAL", "["+FileUtilities.getXercesVersion()+"] Validating \""+document.getName()+"\" against \""+schemaLocation+"\" ...");
			} else {
				parent.getOutputPanel().startCheck( "VAL", "["+FileUtilities.getRelaxNGVersion()+"] Validating \""+document.getName()+"\" against \""+schemaLocation+"\" ...");
			}
			XMLErrorHandler handler = new XMLErrorHandler( new XMLErrorReporter() {
				public void report( XMLError error) {
					parent.getOutputPanel().addError( "VAL", error);
				}
			}, 100);
			document.validate( handler);
			
			if ( !handler.hasErrors()) {
				parent.getView().getChangeManager().markValid();
				parent.getOutputPanel().endCheck( "VAL", "Valid Document.");
			} else {
				int errors = handler.getErrors().size();
				if ( errors > 0) {
					parent.getOutputPanel().endCheck( "VAL", errors+" Errors");
				} else {
					parent.getOutputPanel().endCheck( "VAL", errors+" Error");
				}
			}
		} catch ( final SAXParseException e) {
			parent.getOutputPanel().endCheck( "VAL", "1 Error");
			e.printStackTrace();
//			parent.getOutputPanel().setError( (SAXParseException)e);
//			e.printStackTrace();
		} catch ( final IOException e) {
			parent.getOutputPanel().setError( "VAL", (IOException)e);
			parent.getOutputPanel().endCheck( "VAL", "1 Error");
			e.printStackTrace();
		} catch ( final Throwable t) {
			t.printStackTrace();
		}
		
		if(view instanceof PluginViewPanel) {
			((PluginViewPanel)view).returnToPreviousState();
			
		}
 	}
}
