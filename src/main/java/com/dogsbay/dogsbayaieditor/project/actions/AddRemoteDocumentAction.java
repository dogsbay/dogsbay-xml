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

package com.dogsbay.dogsbayaieditor.project.actions;

import java.awt.event.ActionEvent;
import java.net.URL;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.OpenRemoteDocumentDialog;
import com.dogsbay.dogsbayaieditor.project.Project;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to add a new remote document to the
 * application.
 *
 * @version	$Revision: 1.4 $, $Date: 2005/09/05 09:08:29 $
 * @author Dogsbay
 */
public class AddRemoteDocumentAction extends AbstractAction {
// 	private DocumentCategory category = null;
 	private Project project = null;
 	private DogsBayAIEditor parent = null;

 	private OpenRemoteDocumentDialog dialog = null;
 	private ConfigurationProperties properties = null;
	
 	/**
	 * The constructor for the action which allows addition of 
	 * remote documents to the application.
	 *
	 * @param frame the parent frame.
	 */
 	public AddRemoteDocumentAction( ConfigurationProperties props, DogsBayAIEditor parent, Project project) {
 		super( "Add Remote ...");

 		this.parent = parent;
 		this.project = project;
 		this.properties = props;

		putValue( MNEMONIC_KEY, Integer.valueOf( 'R'));
		putValue( SHORT_DESCRIPTION, "Add Remote Document");

		dialog = new OpenRemoteDocumentDialog( props, parent);
		
		setEnabled( false);
 	}
 	
	/**
	 * The implementation of the ad remote document action.
	 *
	 * @param e the action event.
	 */
	public void actionPerformed( ActionEvent e) {
		dialog.show( "http://");

	 	if ( !dialog.isCancelled()) {
	 	
 	        URL url = dialog.getURL();

 			if ( url == null) {
 				JOptionPane.showMessageDialog(	parent,
 											    "Could not create a valid URL.",
 											    "Document Error",
 											    JOptionPane.ERROR_MESSAGE);
 			} else {
 				boolean isStartup = false;
	 			project.addRemote( url, isStartup);
 			}
	 	}
	}
}
