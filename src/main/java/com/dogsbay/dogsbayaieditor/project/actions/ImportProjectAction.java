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
import javax.swing.JFileChooser;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.FileUtilities;
import com.dogsbay.dogsbayaieditor.project.Project;

/**
 * An action that can be used to copy information 
 * in a XML Document.
 *
 * @version	$Revision: 1.3 $, $Date: 2005/09/05 09:08:29 $
 * @author Dogsbay
 */
public class ImportProjectAction extends AbstractAction {
 	private static final boolean DEBUG = false;

 	private Project project = null;
 	private DogsBayAIEditor parent = null;
 	private File dir = null;
	
 	/**
	 * The constructor for the add action.
	 *
	 * @param editor the editor to copy information from.
	 */
 	public ImportProjectAction( DogsBayAIEditor parent, Project project) {
 		super( "Import Project ...");
 		
 		this.project = project;
 		this.parent = parent;

		putValue( MNEMONIC_KEY, Integer.valueOf( 'm'));
		putValue( SHORT_DESCRIPTION, "Import a Project from file");
 	}
 	
	/**
	 * The implementation of the copy action.
	 *
	 * @param e the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		JFileChooser chooser = FileUtilities.getFileChooser();
		
		if ( dir != null) {
			chooser.setCurrentDirectory( dir);
			chooser.rescanCurrentDirectory();
		}

		int value = chooser.showOpenDialog( parent);
		dir = chooser.getCurrentDirectory();
		
	 	if ( value == JFileChooser.APPROVE_OPTION) {
		 	File file = chooser.getSelectedFile();
		 	boolean isStartup = false;
			project.importProject( file, isStartup);
	 	}
 	}
}
