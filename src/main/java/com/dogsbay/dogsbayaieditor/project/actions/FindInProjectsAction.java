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

import javax.swing.AbstractAction;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.FindInProjectsDialog;
import com.dogsbay.dogsbayaieditor.project.Project;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to add a directory to a project or 
 * folder.
 *
 * @version	$Revision: 1.2 $, $Date: 2005/08/31 09:18:15 $
 * @author Dogsbay
 */
public class FindInProjectsAction extends AbstractAction {
 	private static final boolean DEBUG = false;

 	private ConfigurationProperties properties = null; 
 	private DogsBayAIEditor parent = null;
	private FindInProjectsDialog findDialog = null;

 	private Project project = null;
	
 	/**
	 * The constructor for the add action.
	 *
	 * @param editor the editor to copy information from.
	 */
 	public FindInProjectsAction( DogsBayAIEditor parent, ConfigurationProperties props, Project project) {
 		super( "Find in Projects ...");
 		
		this.parent = parent;
 		this.project = project;
		this.properties = props;

//		putValue( MNEMONIC_KEY, Integer.valueOf( 'V'));
//		putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, 0, false));
//		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Copy16.gif"));
		putValue( SHORT_DESCRIPTION, "Find in Projects ...");
		
		setEnabled( false);
 	}
 	
	/**
	 * The implementation of the copy action.
	 *
	 * @param e the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
	 	if ( findDialog == null) {
	 		findDialog = new FindInProjectsDialog( parent, properties);
	 	}
	 	
	 	findDialog.init(project);
		
	 	findDialog.setVisible( true);
	 	
	 	if ( !findDialog.isCancelled()) {
	 		String search = findDialog.getSearch();
	 		boolean matchCase = findDialog.isCaseSensitive();
	 		boolean regExp = findDialog.isRegularExpression();
	 		boolean wholeWord = findDialog.isMatchWholeWord();
	 		String workingProject = findDialog.getProject();

	 		if ( search != null) {
	 			properties.addSearch( search);
	 			properties.setMatchCase( matchCase);
	 			properties.setRegularExpression( regExp);
	 			properties.setMatchWholeWord( wholeWord);
				
	 			project.selectProject(workingProject);
	 			project.findInFiles( search, regExp, matchCase, wholeWord);

	 		}
	 	}
 	}
}
