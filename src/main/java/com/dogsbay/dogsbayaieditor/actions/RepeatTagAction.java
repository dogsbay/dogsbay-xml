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
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;

/**
 * An action that can be used to tag the selected text.
 *
 * @version	$Revision: 1.2 $, $Date: 2004/10/28 16:17:07 $
 * @author Dogsbay
 */
 public class RepeatTagAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	
	private Editor editor = null;
	private DogsBayAIEditor parent = null;
	private String tag = null;

 	/**
	 * The constructor for the action which tags the selected text.
	 *
	 * @param editor the XML Editor
	 */
 	public RepeatTagAction( DogsBayAIEditor parent) {
		super( "Repeat last Tag");
		
		if (DEBUG) System.out.println( "RepeatTagAction( "+editor+")");
		
		putValue( SHORT_DESCRIPTION, "Repeat last Tag...");

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
		
		setDocument( parent.getDocument());
	}

	public void setDocument( DogsBayDocument doc) {
		if ( doc != null && tag != null) {
			setEnabled( editor != null);
		} else {
			setEnabled( false);
		}
	}
	
	public void setTag( String tag) {
		
		if ( tag != null && tag.trim().length() > 0) {
			this.tag = tag;
		} else {
			this.tag = null;
		}
		
		setDocument( parent.getDocument());
	}

	/**
	 * The implementation of the tag action, called 
	 * after a user action.
	 *
	 * @param event the action event.
	 */
 	public void actionPerformed( ActionEvent event) {
 		if (DEBUG) System.out.println( "RepeatTagAction.actionPerformed( "+event+")");
		
		if ( tag != null) {
	 		editor.insertTag( tag);
		}

		editor.setFocus();
 	}
}
