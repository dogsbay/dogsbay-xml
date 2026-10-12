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

import com.dogsbay.dogsbayaieditor.project.BaseNode;
import com.dogsbay.dogsbayaieditor.project.DocumentNode;
import com.dogsbay.dogsbayaieditor.project.FolderNode;
import com.dogsbay.dogsbayaieditor.project.Project;
import com.dogsbay.dogsbayaieditor.project.ProjectNode;

/**
 * An action that can be used to copy information 
 * in a XML Document.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:55:19 $
 * @author Dogsbay
 */
 public class DeleteAction extends AbstractAction {
 	private static final boolean DEBUG = false;

 	private Project project = null;
	private DeleteProjectAction deleteProject = null;
	private RemoveFolderAction deleteFolder = null;
	private RemoveFileAction deleteFile = null;
	
 	/**
	 * The constructor for the delete action.
	 *
	 * @param editor the editor to copy information from.
	 */
 	public DeleteAction( Project project, DeleteProjectAction deleteProject, RemoveFolderAction deleteFolder, RemoveFileAction deleteFile) {
 		super( "Delete");
 		
 		this.project = project;

 		this.deleteProject = deleteProject;
 		this.deleteFolder = deleteFolder;
 		this.deleteFile = deleteFile;
 	}
 	
	/**
	 * The implementation of the delete project action.
	 *
	 * @param e the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		BaseNode node = project.getSelectedNode();
		
		if ( node instanceof DocumentNode) {
			deleteFile.actionPerformed( e);
		} else if ( node instanceof ProjectNode) {
			deleteProject.actionPerformed( e);
		} else if ( node instanceof FolderNode) {
			deleteFolder.actionPerformed( e);
		}
 	}
 }
