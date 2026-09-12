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
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import javax.swing.AbstractAction;

import org.xml.sax.SAXParseException;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XMLError;
import com.dogsbay.xml.XMLErrorHandler;
import com.dogsbay.xml.XMLErrorReporter;
import com.dogsbay.xml.XMLUtilities;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.FileUtilities;

/**
 * An action that can be used to validate a DTD document.
 *
 * @version	$Revision: 1.3 $, $Date: 2004/05/03 18:39:15 $
 * @author Dogsbay
 */
 public class ValidateDTDAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	private DogsBayAIEditor parent = null;
	
 	/**
	 * The constructor for the action which allows for validating
	 * a XML Schema document.
	 *
	 * @param parent the main parent frame
	 */
 	public ValidateDTDAction( DogsBayAIEditor parent) {
		super( "Validate DTD");

		putValue( MNEMONIC_KEY, Integer.valueOf( 'D'));
//		putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_F8, 0, false));
//		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Validate16.gif"));
//		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/xml/editor/icons/Parse16.gif"));
		putValue( SHORT_DESCRIPTION, "Validate DTD");
		
		this.parent = parent;
		
		setEnabled( false);
 	}
 	
	/**
	 * Sets the current view.
	 *
	 * @param view the current view.
	 */
	public void setView( Object view) {
		DogsBayDocument doc = parent.getDocument();
		
		if ( doc != null && doc.isDTD()) {
			setEnabled( true);
		} else {
			setEnabled( false);
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

		// Run in Thread!!!
		Runnable runner = new Runnable() {
			public void run()  {
				try {
					parent.getView().updateModel();
					DogsBayDocument doc = parent.getView().getDocument();
		
					ByteArrayInputStream stream = new ByteArrayInputStream( doc.getText().getBytes( doc.getJavaEncoding()));
					InputStreamReader reader = new InputStreamReader( stream, doc.getJavaEncoding());
					String systemId = null;
					URL url = doc.getURL();
		
					if ( url != null) {
						systemId = url.toString();
					}

					parent.getOutputPanel().startCheck( "VAL DTD", "["+FileUtilities.getXercesVersion()+"] Validating DTD \""+doc.getName()+"\" ...");


					XMLErrorHandler handler = new XMLErrorHandler( new XMLErrorReporter() {
						public void report( XMLError error) {
							parent.getOutputPanel().addError( "VAL DTD", error);
						}
					}, 100);

					XMLUtilities.validateDTD( handler, new BufferedReader( reader), systemId, doc.getEncoding(), parent.getProperties());

					if ( !handler.hasErrors()) {
						parent.getOutputPanel().endCheck( "VAL DTD", "Valid DTD.");
					} else {
						int errors = handler.getErrors().size();
						if ( errors > 1) {
							parent.getOutputPanel().endCheck( "VAL DTD", errors+" Errors");
						} else {
							parent.getOutputPanel().endCheck( "VAL DTD", "1 Error");
						}
					}
				} catch ( final SAXParseException e) {
				} catch ( final IOException e) {
					parent.getOutputPanel().setError( "VAL DTD", (IOException)e);
					parent.getOutputPanel().endCheck( "VAL DTD", "1 Error");
				} catch ( final Throwable t) {
					t.printStackTrace();
				} finally {
					parent.setWait( false);
					parent.setStatus( "Done");
				}
		 	}
		};

		// Create and start the thread ...
		Thread thread = new Thread( runner);
		thread.start();
 	}
}
