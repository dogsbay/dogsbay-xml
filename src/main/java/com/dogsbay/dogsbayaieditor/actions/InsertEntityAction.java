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

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.EntitySelectionDialog;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;

/**
 * An action that can be used to indent the selected text.
 *
 * @version	$Revision: 1.4 $, $Date: 2004/10/28 16:17:07 $
 * @author Dogsbay
 */
 public class InsertEntityAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	
	private Editor editor = null;
	private DogsBayAIEditor parent = null;
	private EntitySelectionDialog dialog = null;

 	/**
	 * The constructor for the action which indents 
	 * the selected text.
	 *
	 * @param editor the XML Editor
	 */
 	public InsertEntityAction( DogsBayAIEditor parent) {
		super( "Insert Special Character");
		
		this.parent = parent;
		
		if (DEBUG) System.out.println( "InsertEntityAction( "+editor+")");
		
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SHORT_DESCRIPTION, "Insert Special Character");
		
		setEnabled( false);
 	}
 	
	/**
	 * Sets the current view.
	 *
	 * @param view the current view.
	 */
	public void setView( Object view) {
		if ( view instanceof Editor) {
			editor = (Editor)view;
		} else {
			editor = null;
		}
		
		setDocument( parent.getDocument());
	}

	public void setDocument( DogsBayDocument doc) {
		if ( doc != null) {
			setEnabled( editor != null);
		} else {
			setEnabled( false);
		}
	}
	
	public void updatePreferences() {
		getEntitySelectionDialog().updatePreferences();
	}
	
	private EntitySelectionDialog getEntitySelectionDialog() {
		if ( dialog == null) {
			dialog = new EntitySelectionDialog( parent);
		}
		
		return dialog;
	}
	
	/**
	 * The implementation of the unindent action, called 
	 * after a user action.
	 *
	 * @param event the action event.
	 */
 	public void actionPerformed( ActionEvent event) {
 		if (DEBUG) System.out.println( "IndentAction.actionPerformed( "+event+")");

		execute();
 	}
	
	public void execute() {
		EntitySelectionDialog dialog = getEntitySelectionDialog();
		dialog.showDialog();
		
		if ( !dialog.isCancelled()) {
			String value = dialog.getSelectedValue();

			if ( value != null) {
				editor.insert( value);
			}
		}
	}
}
