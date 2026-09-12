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

import com.dogsbay.xml.editor.Bookmark;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.SelectBookmarkDialog;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to change between documents.
 *
 * @version	$Revision: 1.3 $, $Date: 2004/07/21 09:09:42 $
 * @author Dogs bay
 */
 public class SelectBookmarkAction extends AbstractAction {
 	private static final boolean DEBUG = false;

 	private SelectBookmarkDialog dialog = null;

 	private DogsBayAIEditor parent = null;
	private ConfigurationProperties properties = null;
	
 	/**
	 * The constructor for the action which allows changing which document is active
	 */
 	public SelectBookmarkAction( DogsBayAIEditor parent) {
 		super( "Select Bookmark...");

		putValue( MNEMONIC_KEY, Integer.valueOf('o'));
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK+InputEvent.SHIFT_DOWN_MASK, false));
		putValue( SHORT_DESCRIPTION, "Select Bookmark");
		
		this.parent = parent;
		
		setEnabled( false);
 	}
	
	private SelectBookmarkDialog getDialog() {
		if ( dialog == null) {
			dialog = new SelectBookmarkDialog( parent);
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
 		SelectBookmarkDialog dialog = getDialog();
		
		dialog.show( parent.getView().getEditor().getBookmarks());

		if (!dialog.isCancelled()) {
			Bookmark bookmark = dialog.getSelectedBookmark();
			parent.getView().getEditor().selectLineWithoutEnd( bookmark.getLineNumber()+1);
		}
 	}
 }
