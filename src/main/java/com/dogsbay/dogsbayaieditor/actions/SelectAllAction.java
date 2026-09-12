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

import com.dogsbay.xml.editor.XmlEditorPane;

/**
 * An action that can be used to select all content in the
 * Xml Editor.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:53:18 $
 * @author Dogsbay
 */
 public class SelectAllAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	
	private XmlEditorPane editor = null;

 	/**
	 * The constructor for the action which allows for selecting all
	 * the Xml Editor content.
	 *
	 * @param editor the XML Editor
	 */
 	public SelectAllAction( XmlEditorPane editor) {
		super( "Select All");
		
		if (DEBUG) System.out.println( "SelectAllAction( "+editor+")");
		
		putValue( MNEMONIC_KEY, Integer.valueOf( 'A'));
//		putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK, false));

		this.editor = editor;
 	}
 	
	/**
	 * The implementation of the select all action, called 
	 * after a user action.
	 *
	 * @param event the action event.
	 */
 	public void actionPerformed( ActionEvent event) {
 		if (DEBUG) System.out.println( "SelectAllAction.actionPerformed( "+event+")");
		
		editor.selectAll();
		editor.requestFocusInWindow();
 	}
}
