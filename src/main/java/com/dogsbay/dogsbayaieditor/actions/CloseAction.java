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
import javax.swing.SwingUtilities;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;

/**
 * An action that can be used to close a XML Document.
 *
 * @version	$Revision: 1.7 $, $Date: 2004/07/21 09:09:42 $
 * @author Dogsbay
 */
 public class CloseAction extends AbstractAction {
 	private static final boolean DEBUG = false;

 	private DogsBayAIEditor parent = null;
	
 	/**
	 * The constructor for the action which allows addition of 
	 * documents to the application.
	 */
 	public CloseAction( DogsBayAIEditor parent) {
 		super( "Close");

		putValue( MNEMONIC_KEY, Integer.valueOf( 'C'));
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SHORT_DESCRIPTION, "Close current Document.");
		
		this.parent = parent;

		setEnabled( false);
 	}
 	
	/**
	 * The implementation of the save document action, called 
	 * after a user action.
	 *
	 * @param the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		// Check if there's a selected tab (could be DogsBayView or custom panel)
		java.awt.Component selectedComponent = parent.getSelectedTabbedView().getSelectedComponent();

		if (selectedComponent instanceof DogsBayView) {
			// Standard document view
			DogsBayView view = (DogsBayView) selectedComponent;
			parent.close( view);

			view = parent.getView();

			if ( view != null) {
				view.getCurrentView().setFocus();
			}
			else
			{
				parent.setIntialFocus();
			}
		} else if (selectedComponent != null) {
			// Custom panel - just remove from tab
			parent.getSelectedTabbedView().removeTab(selectedComponent);

			// Set focus to remaining view if any
			DogsBayView view = parent.getView();
			if ( view != null) {
				view.getCurrentView().setFocus();
			} else {
				parent.setIntialFocus();
			}
		}

		// make sure this runs after the gui is updated!
		SwingUtilities.invokeLater( new Runnable() {
		    public void run() {
			    System.gc();
		    }
		});
 	}
 }
