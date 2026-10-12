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
import javax.swing.ImageIcon;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.OpenRemoteDocumentDialog;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to open a remote document.
 *
 * @version	$Revision: 1.5 $, $Date: 2004/05/21 17:30:49 $
 * @author Dogsbay
 */
 public class OpenRemoteDocumentAction extends AbstractAction {
 	private OpenRemoteDocumentDialog dialog = null;
 	private DogsBayAIEditor parent = null;
 	private ConfigurationProperties properties = null;
	
 	/**
	 * The constructor for the action which allows opening
	 * of remote documents.
	 *
	 * @param parent the parent app.
	 */
 	public OpenRemoteDocumentAction( DogsBayAIEditor parent, ConfigurationProperties props) {
 		super( "Open Remote ...");

//		putValue( MNEMONIC_KEY, Integer.valueOf( 't'));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/OpenRemote16.gif"));
		putValue( SHORT_DESCRIPTION, "Open Remote Document");

		this.properties = props;
		this.parent = parent;
		dialog = new OpenRemoteDocumentDialog( props, parent);
		
//		setEnabled( false);
 	}
 	
	/**
	 * The implementation of the add document action, called 
	 * after a user action.
	 *
	 * @param the action event.
	 */
 	public void actionPerformed( ActionEvent e) {

		dialog.show( "http://");
		
		if ( !dialog.isCancelled()) {
		
//			try {
		        final URL url = dialog.getURL();

//				if ( !url.getProtocol().equalsIgnoreCase( "http")) {
//					JOptionPane.showMessageDialog(	parent,
//												    "The "+url.getProtocol()+" protocol is not supported for a Remote Document.\n",
//												    "Document Error",
//												    JOptionPane.ERROR_MESSAGE);
//				} else {
					parent.setWait( true);
					parent.setStatus( "Opening ...");
		
					// Run in Thread!!!
					Runnable runner = new Runnable() {
						public void run()  {
							try {
								parent.open( url, null, true);
							} finally {
								parent.setStatus( "Done");
								parent.setWait( false);
							}
						}
					};
					
					// Create and start the thread ...
					Thread thread = new Thread( runner);
					thread.start();
//				}
//			} catch ( MalformedURLException mue) {
//				JOptionPane.showMessageDialog(	parent,
//											    "Invalid URL: "+dialog.getURL()+"\n"+
//												mue.getMessage(),
//											    "Document Error",
//											    JOptionPane.ERROR_MESSAGE);
//			} 
 		}
 	}
 }
