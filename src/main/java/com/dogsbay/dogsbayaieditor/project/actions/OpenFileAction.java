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
import javax.swing.SwingUtilities;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.project.DocumentNode;
import com.dogsbay.dogsbayaieditor.project.DocumentProperties;
import com.dogsbay.dogsbayaieditor.project.Project;

/**
 * An action that can be used to open a XML Document.
 *
 * @version	$Revision: 1.10 $, $Date: 2004/11/03 15:25:11 $
 * @author Dogsbay
 */
 public class OpenFileAction extends AbstractAction {
 	private static final boolean DEBUG = false;

 	private Project project = null;
 	private DogsBayAIEditor parent = null;
	
 	/**
	 * The constructor for the add action.
	 *
	 * @param editor the editor to copy information from.
	 */
 	public OpenFileAction( DogsBayAIEditor parent, Project project) {
 		super( "Open File");
 		
 		this.parent = parent;
 		this.project = project;

		putValue( MNEMONIC_KEY, Integer.valueOf( 'O'));
		putValue( SHORT_DESCRIPTION, "Open a File");
		
		setEnabled( false);
 	}
 	
	/**
	 * The implementation of the copy action.
	 *
	 * @param e the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		execute();
 	}
	
	public void execute() {
//		System.out.println("OpenFileAction.execute()");
		parent.setWait( true);
		parent.setStatus( "Opening ...");
		
		// Run in Thread!!!
		Runnable runner = new Runnable() {
			public void run()  {

				try {
					DocumentNode node = (DocumentNode)project.getSelectedNode();
					DocumentProperties properties = node.getProperties();
					
					parent.open( properties.getURL(), null, true);
			 	} finally {
					SwingUtilities.invokeLater( new Runnable() {
						public void run() {
							DocumentNode node = (DocumentNode)project.getSelectedNode();
							node.setDocument( parent.getDocument());
						 	node.setType( parent.getGrammar());

						 	DogsBayView view = parent.getView();
		
							if ( view != null) {
								view.getCurrentView().setFocus();
							}
						}
					});

					parent.setStatus( "Done");
			 		parent.setWait( false);
			 	}
			}
		};

		// Create and start the thread ...
		Thread thread = new Thread( runner);
		thread.start();
	}
}
