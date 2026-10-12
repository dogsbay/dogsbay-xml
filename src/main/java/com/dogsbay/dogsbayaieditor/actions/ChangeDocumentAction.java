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
import javax.swing.JTabbedPane;

import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.ChangeDocumentDialog;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to change between documents.
 *
 * @version	$Revision: 1.5 $, $Date: 2004/07/28 17:00:00 $
 * @author Dogs bay
 */
 public class ChangeDocumentAction extends AbstractAction {
 	private static final boolean DEBUG = false;

 	private ChangeDocumentDialog dialog = null;

 	private DogsBayAIEditor parent = null;
	private ConfigurationProperties properties = null;
	
 	/**
	 * The constructor for the action which allows changing which document is active
	 */
 	public ChangeDocumentAction( DogsBayAIEditor parent) {
 		super( "Select document...");

		//putValue( MNEMONIC_KEY, Integer.valueOf('d'));
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_1,InputEvent.ALT_DOWN_MASK, false));
		putValue( SHORT_DESCRIPTION,"Select document");
		
		this.parent = parent;
		
		setEnabled( false);
 	}
	
	private ChangeDocumentDialog getDialog() {
		if ( dialog == null) {
			dialog = new ChangeDocumentDialog(parent);
		}
		
		return dialog;
	}
 	
	/**
	 * Sets the current view.
	 *
	 * @param view the current view.
	 */
	public void setView( Object view) {
		setEnabled( ( view instanceof Editor));
	}

	/**
	 * The implementation of the Select document action
	 *
	 * @param the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		ChangeDocumentDialog dialog = getDialog();
		
		final JTabbedPane tab = parent.getTabbedPane();
		
		if ( parent.getViews().size() > 1) {
			dialog.show( parent.getViews(), parent.getView(), parent.getPreviousView());
	
			if (!dialog.isCancelled()) {
				DogsBayView view = dialog.getSelectedView();
				
				// select the chose tab
				parent.select( view);	
				view.getCurrentView().setFocus();
			}
		}
 	}
 }
