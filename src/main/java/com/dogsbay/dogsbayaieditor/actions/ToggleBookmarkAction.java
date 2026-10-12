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
import javax.swing.JFrame;

import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.GotoDialog;

/**
 * An action that can be used to toggle a bookmark on the current line.
 *
 * @version	$Revision: 1.3 $, $Date: 2004/07/21 09:09:42 $
 * @author Dogsbay
 */
 public class ToggleBookmarkAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	
	private GotoDialog dialog = null;
	private Editor editor = null;
	private JFrame parent = null;

 	/**
	 * The constructor for the action which allows the user to 
	 * goto a specific line in the Xml Editor.
	 *
	 * @param editor the XML Editor
	 */
 	public ToggleBookmarkAction( JFrame parent) {
		super( "Toggle Bookmark");
		
		if (DEBUG) System.out.println( "ToggleBookmarkAction( "+parent+")");
		
		putValue( MNEMONIC_KEY, Integer.valueOf( 'B'));
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK, false));
//		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/xml/editor/icons/Goto16.gif"));
		putValue( SHORT_DESCRIPTION, "Toggle Bookmark");

	 	this.parent = parent;
		
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
		
		setEnabled( editor != null);
	}

	/**
	 * The implementation of the goto action, called 
	 * after a user action.
	 *
	 * @param event the action event.
	 */
 	public void actionPerformed( ActionEvent event) {
 		if (DEBUG) System.out.println( "ToggleBookmarkAction.actionPerformed( "+event+")");
		
		editor.toggleBookmarkCurrentLine();
		editor.setFocus();
 	}
}
