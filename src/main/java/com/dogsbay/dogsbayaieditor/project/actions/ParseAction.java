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

import com.dogsbay.dogsbayaieditor.project.Project;

/**
 * An action that can be used to add a directory to a project or 
 * folder.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:55:19 $
 * @author Dogsbay
 */
public class ParseAction extends AbstractAction {
 	private static final boolean DEBUG = false;

 	private Project project = null;
	
 	/**
	 * The constructor for the add action.
	 *
	 * @param editor the editor to copy information from.
	 */
 	public ParseAction( Project project) {
 		super( "Check Well-Formedness");
 		
 		this.project = project;

		putValue( MNEMONIC_KEY, Integer.valueOf( 'W'));
//		putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_ENTER, 0, false));
//		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Copy16.gif"));
		putValue( SHORT_DESCRIPTION, "Check Well-Formedness");
		
		setEnabled( false);
 	}
 	
	/**
	 * The implementation of the copy action.
	 *
	 * @param e the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		// parses current selected file/folder or project.
		project.parse();
 	}
}
