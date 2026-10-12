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

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.xml.editor.Tag;
import com.dogsbay.xml.editor.XmlDocument;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.RenameElementDialog;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;

/**
 * An action that can be used to Comment the selected text.
 *
 * @version	$Revision: 1.2 $, $Date: 2004/10/28 16:17:07 $
 * @author Dogsbay
 */
 public class RenameElementAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	
	private Editor editor = null;
	private DogsBayAIEditor parent = null;
	private RenameElementDialog dialog = null;

 	/**
	 * The constructor for the action that Comments the selected text.
	 *
	 * @param editor the XML Editor
	 */
 	public RenameElementAction( DogsBayAIEditor parent) {
		super( "Rename Element ...");
		
		this.parent = parent;

		if (DEBUG) System.out.println( "RenameElementAction( "+editor+")");
		
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/RenameElement16.gif"));
		putValue( SHORT_DESCRIPTION, "Rename Element");
		
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
 	
	/**
	 * The implementation of the comment action, called 
	 * after a user action.
	 *
	 * @param event the action event.
	 */
 	public void actionPerformed( ActionEvent event) {
 		if (DEBUG) System.out.println( "RenameElementAction.actionPerformed( "+event+")");
		
		if ( dialog == null) {
			dialog = new RenameElementDialog( parent);
		}

		Tag endTag = null;
		Tag startTag = editor.getCurrentTag();

		if ( startTag != null && startTag.getType() == Tag.END_TAG) {
			endTag = startTag;
			startTag = ((XmlDocument)editor.getEditor().getDocument()).getStartTag( startTag);
		} else if ( startTag == null || (startTag.getType() != Tag.START_TAG && startTag.getType() != Tag.EMPTY_TAG)) {
			startTag = editor.getParentStartTag();
		}

		if ( startTag == null) {
			// Could not find a start tag.
			MessageHandler.showError( "Could not find a Start tag.", "Rename Error");
			return;
		} else if ( startTag.getType() != Tag.EMPTY_TAG) {
			
			if ( startTag.getType() == Tag.START_TAG) {
				if ( endTag == null) {
					endTag = ((XmlDocument)editor.getEditor().getDocument()).getEndTag( startTag);
				}
				
				if ( endTag == null || !endTag.getQualifiedName().equals( startTag.getQualifiedName())) {
					// could not find matching end tag
					MessageHandler.showError( "Could not find a matching End tag.", "Rename Error");
					return;
				}
			} else { 
				// could not find start-tag
				MessageHandler.showError( "Could not find a Start tag.", "Rename Error");
				return;
			}
		}

		dialog.init( editor, startTag);
		dialog.setVisible(true);
	
		if ( !dialog.isCancelled()) {
			String tag = dialog.getTag();
			
			if ( tag != null && tag.trim().length() > 0) {
				editor.renameCurrentElement( tag);
			}
		}

		editor.setFocus();
 	}
}
