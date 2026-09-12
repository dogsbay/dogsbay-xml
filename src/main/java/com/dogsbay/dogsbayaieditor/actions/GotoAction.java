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
import javax.swing.ImageIcon;
import javax.swing.JFrame;

import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.GotoDialog;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;

/**
 * An action that can be used to goto a specific line in the
 * Xml Editor.
 *
 * @version	$Revision: 1.6 $, $Date: 2004/10/27 16:23:53 $
 * @author Dogsbay
 */
 public class GotoAction extends AbstractAction {
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
 	public GotoAction( JFrame parent) {
		super( "Goto...");
		
		if (DEBUG) System.out.println( "GotoAction( "+editor+")");
		
		putValue( MNEMONIC_KEY, Integer.valueOf( 'G'));
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/xml/editor/icons/Goto16.gif"));
		putValue( SHORT_DESCRIPTION, "Goto...");

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
 		if (DEBUG) System.out.println( "GotoAction.actionPerformed( "+event+")");

		if ( dialog == null) {
			dialog = new GotoDialog( parent);
		}

		dialog.showDialog();
		editor.setFocus();

		if ( !dialog.isCancelled()) {
			editor.gotoLine( dialog.getLine());
		}

 	}
}
