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
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.FileUtilities;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to open a XML Document.
 *
 * @version	$Revision: 1.7 $, $Date: 2005/08/29 08:32:39 $
 * @author Dogsbay
 */
public class OpenAction extends AbstractAction {
 	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final boolean DEBUG = false;
	
	private DogsBayAIEditor parent = null;
	private ConfigurationProperties properties = null;

 	/**
	 * The constructor for the action which allows opening 
	 * of XML Documents.
	 *
	 * @param parent the parent frame.
	 */
 	public OpenAction( DogsBayAIEditor parent, ConfigurationProperties props) {
 		super( "Open ...");
		
		this.parent = parent;
		this.properties = props;

		putValue( MNEMONIC_KEY, Integer.valueOf( 'O'));
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Open16.gif"));
		putValue( SHORT_DESCRIPTION, "Open Document");
 	}
 	
	/**
	 * The implementation of the add document action, called 
	 * after a user action.
	 *
	 * @param the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		//boolean alreadyLoaded = false;
		//XMLSchema schema = null;
		//DogsBayDocument document = null;

		//try {
		//	LicenseManager licenseManager = LicenseManager.getInstance();
		//	licenseManager.isValid( com.dogsbay.license.KeyGenerator.generate(2), "DogsBay XML");
		//} catch (Exception x) {
		//	System.exit(0);
		//	return;
		//}

		final JFileChooser chooser = FileUtilities.getFileChooser();
	 	int value = chooser.showOpenDialog( parent);

	 	if ( value == JFileChooser.APPROVE_OPTION) {
	 		parent.setWait( true);
	 		parent.setStatus( "Opening ...");

	 		// Run in Thread!!!
	 		Runnable runner = new Runnable() {
	 			public void run()  {
			 		try {
			 	        File file = chooser.getSelectedFile();

			 	        URL url = com.dogsbay.xml.DogsBayURLUtilities.getURLFromFile(file);
			 	        
			 	        parent.open( url, FileUtilities.getSelectedGrammar( chooser), true);
						
			 			// set the document somewhere....
			 		} catch ( MalformedURLException mue) {
			 			// This should never happen, just report and continue
			 			MessageHandler.showUnexpectedError( mue);
			 		} finally {
				 		parent.setStatus( "Done");
				 		parent.setWait( false);
			 		}
	 			}
	 		};
	 		
	 		// Create and start the thread ...
	 		Thread thread = new Thread( runner);
	 		thread.start();
	 	}
	 	else
	 	{
	 		DogsBayView view = parent.getView();
	 		if (view != null)
	 		{
	 			view.requestFocus();
	 		}
	 		else
	 		{
	 			// no view available, so set focus back
	 			parent.setIntialFocus();
	 		}
	 	}
 	}
	
}
