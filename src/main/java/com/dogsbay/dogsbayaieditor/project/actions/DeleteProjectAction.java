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
import javax.swing.JOptionPane;

import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.project.BaseNode;
import com.dogsbay.dogsbayaieditor.project.Project;

/**
 * An action that can be used to copy information 
 * in a XML Document.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:55:19 $
 * @author Dogsbay
 */
 public class DeleteProjectAction extends AbstractAction {
 	private static final boolean DEBUG = false;

 	private Project project = null;
	
 	/**
	 * The constructor for the delete action.
	 *
	 * @param editor the editor to copy information from.
	 */
 	public DeleteProjectAction( Project project) {
 		super( "Delete Project");
 		
 		this.project = project;

		putValue( MNEMONIC_KEY, Integer.valueOf( 'D'));
		putValue( SHORT_DESCRIPTION, "Delete a Project");
		
		setEnabled( false);
 	}
 	
	/**
	 * The implementation of the delete project action.
	 *
	 * @param e the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		BaseNode node = project.getSelectedNode();
	 	int result = MessageHandler.showConfirm( "Are you sure you want to delete this project \""+node.getName()+"\"?");

	 	if ( result == JOptionPane.YES_OPTION) {
 			project.deleteProject();
	 	}
 	}
 }
