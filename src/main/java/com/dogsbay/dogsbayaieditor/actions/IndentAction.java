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
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.KeyStroke;

import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;

/**
 * An action that can be used to indent the selected text.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:53:19 $
 * @author Dogsbay
 */
 public class IndentAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	
	private Editor editor = null;

 	/**
	 * The constructor for the action which indents 
	 * the selected text.
	 *
	 * @param editor the XML Editor
	 */
 	public IndentAction() {
		super( "Indent");
		
		if (DEBUG) System.out.println( "IndentAction( "+editor+")");
		
		putValue( MNEMONIC_KEY, Integer.valueOf( 'I'));
		putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_TAB, 0, false));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/xml/editor/icons/Indent16.gif"));
		putValue( SHORT_DESCRIPTION, "Indent");
		
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
	 * The implementation of the unindent action, called 
	 * after a user action.
	 *
	 * @param event the action event.
	 */
 	public void actionPerformed( ActionEvent event) {
 		if (DEBUG) System.out.println( "IndentAction.actionPerformed( "+event+")");
		
		editor.indentSelectedText( false);
		editor.setFocus();
 	}
}
