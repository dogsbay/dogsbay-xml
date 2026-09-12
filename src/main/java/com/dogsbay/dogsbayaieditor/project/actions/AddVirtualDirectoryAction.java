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
import java.io.File;

import javax.swing.AbstractAction;

import com.dogsbay.dogsbayaieditor.DirectoryChooserDialog;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.FileUtilities;
import com.dogsbay.dogsbayaieditor.OpenRemoteDocumentDialog;
import com.dogsbay.dogsbayaieditor.project.Project;
import com.l2fprod.common.swing.JDirectoryChooser;

/**
 * An action that can be used to copy information 
 * in a XML Document.
 *
 * @version	$Revision: 1.2 $, $Date: 2005/09/05 09:14:18 $
 * @author Dogsbay
 */
 public class AddVirtualDirectoryAction extends AbstractAction {
 	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final boolean DEBUG = false;

 	private Project project = null;

	private DogsBayAIEditor parent;
	
	private DirectoryChooserDialog dialog = null;
	
 	/**
	 * The constructor for the add action.
 	 * @param parent 
	 *
	 * @param editor the editor to copy information from.
	 */
 	public AddVirtualDirectoryAction( DogsBayAIEditor parent, Project project) {
 		super( "Add Virtual Folder ...");
 		
 		this.project = project;
 		this.parent = parent;

		//putValue( MNEMONIC_KEY, Integer.valueOf( 'o'));
		putValue( SHORT_DESCRIPTION, "Add a Virtual Folder");
		
		setEnabled( false);
		
		dialog = new DirectoryChooserDialog( parent);
 	}
 	
	/**
	 * The implementation of the copy action.
	 *
	 * @param e the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
 		dialog.showDialog();

	 	if ( !dialog.isCancelled()) {
	 		
	 		File folder = dialog.getSelectedFolder();
	 		if((folder != null) && (folder.exists() == true)) {
	 			
		 		boolean isStartup = false;
		 		project.addVirtualDirectory( folder, isStartup);
		 		
		 	}
	 	}
 		/*JDirectoryChooser chooser = FileUtilities.getDirectoryChooser();
		
	 	int value = chooser.showOpenDialog( parent);
		
	 	if ( value == JDirectoryChooser.APPROVE_OPTION) {
		 	final File file = chooser.getSelectedFile();
			
		 	if(file != null) {
		 		boolean isStartup = false;
		 		project.addVirtualDirectory( file, isStartup);
		 		
		 	}
			
	 	}*/
 	}
 }
