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

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.MessageHandler;

/**
 * An action that can be used to reload a XML Document.
 *
 * @version	$Revision: 1.2 $, $Date: 2004/08/02 09:08:56 $
 * @author Dogsbay
 */
public class ReloadAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	
	private DogsBayAIEditor parent = null;

 	/**
	 * The constructor for the action which allows opening 
	 * of XML Documents.
	 *
	 * @param parent the parent frame.
	 */
 	public ReloadAction( DogsBayAIEditor parent) {
 		super( "Reload");
		
		this.parent = parent;

		putValue( MNEMONIC_KEY, Integer.valueOf( 'R'));
//		putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK, false));
//		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Open16.gif"));
		putValue( SHORT_DESCRIPTION, "Reload current XML Document");
		
		setEnabled( false);
 	}
 	
	/**
	 * The implementation of the add document action, called 
	 * after a user action.
	 *
	 * @param the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
 		execute();
 	}
 	
 	public void execute() {
		int result = JOptionPane.YES_OPTION;
		
		if ( parent.getChangeManager().isChanged()) {
	 		result = MessageHandler.showConfirm( "Are you sure you want to discard changes to \""+parent.getDocument().getName()+"\"?");
		}

 		if ( result == JOptionPane.YES_OPTION) {

	 		parent.setWait( true);
	 		parent.setStatus( "Loading ...");

	 		// Run in Thread!!!
	 		Runnable runner = new Runnable() {
	 			public void run()  {
			 		try {
			 	        parent.getView().reload();
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
 	}
	
}
