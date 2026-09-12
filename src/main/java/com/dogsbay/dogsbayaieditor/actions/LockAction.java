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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.KeyStroke;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.xml.editor.XmlEditorPane;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;

/**
 * An action that can be used to Comment the selected text.
 *
 * @version	$Revision: 1.3 $, $Date: 2004/05/06 11:09:59 $
 * @author Dogsbay
 */
 public class LockAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	
	private Editor editor = null;
	private DogsBayAIEditor parent = null;

 	/**
	 * The constructor for the action that Comments the selected text.
	 *
	 * @param editor the XML Editor
	 */
 	public LockAction( DogsBayAIEditor parent) {
		super( "Unlock");
		
		this.parent = parent;
		
		if (DEBUG) System.out.println( "LockAction( "+editor+")");
		
		putValue( MNEMONIC_KEY, Integer.valueOf( 'l'));
		putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/xml/editor/icons/Unlock16.gif"));
		putValue( SHORT_DESCRIPTION, "Unlock");
		
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
		 if ( doc != null && (doc.isXML() || doc.isDTD())) {
		 	setEnabled( editor != null);
		 } else {
		 	setEnabled( false);
		 }
	}

	/**
	 * Sets wether the action is being used 
	 * for commenting or uncommenting.
	 *
	 * @param enabled enable un-commenting.
	 */
	public void setUnlock( int value) {
		if ( value == XmlEditorPane.NOT_LOCKED) {
			putValue( NAME, "Lock");
			putValue( SHORT_DESCRIPTION, "Allow all changes.");
			putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/xml/editor/icons/Unlock16.gif"));
		} else if ( value == XmlEditorPane.LOCKED){
			putValue( NAME, "Double Lock");
			putValue( SHORT_DESCRIPTION, "Allow Attribute and Element content changes.");
			putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/xml/editor/icons/Lock16.gif"));
		} else if ( value == XmlEditorPane.DOUBLE_LOCKED){
			putValue( NAME, "Unlock");
			putValue( SHORT_DESCRIPTION, "Allow Element content changes.");
			putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/xml/editor/icons/DoubleLock16.gif"));
		}
	}

	/**
	 * The implementation of the comment action, called 
	 * after a user action.
	 *
	 * @param event the action event.
	 */
 	public void actionPerformed( ActionEvent event) {
 		if (DEBUG) System.out.println( "CommentAction.actionPerformed( "+event+")");
		
		editor.lock();
		editor.setFocus();
 	}
}
