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
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.SubstituteEntitiesDialog;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to substitute the selected 
 * entities with characters.
 *
 * @version	$Revision: 1.2 $, $Date: 2004/10/28 16:17:07 $
 * @author Dogsbay
 */
 public class SubstituteEntitiesAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	
	private SubstituteEntitiesDialog dialog = null;
	private ConfigurationProperties properties = null;
	private Editor editor = null;
	private DogsBayAIEditor parent = null;

 	/**
	 * The constructor for the action which tags the selected text.
	 *
	 * @param editor the XML Editor
	 */
 	public SubstituteEntitiesAction( DogsBayAIEditor parent, ConfigurationProperties props) {
		super( "Convert Entities to Characters...");
		
		if (DEBUG) System.out.println( "SusbtituteEntitiesAction( "+parent+", "+props+")");
		
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/ConvertFromEntities16.gif"));
		putValue( SHORT_DESCRIPTION, "Convert Entities to Characters...");

	 	this.parent = parent;
	 	this.properties = props;

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
	 * The implementation of the tag action, called 
	 * after a user action.
	 *
	 * @param event the action event.
	 */
 	public void actionPerformed( ActionEvent event) {
		if ( dialog == null) {
			dialog = new SubstituteEntitiesDialog( parent, properties);
		}

		dialog.showDialog();
		
		if ( !dialog.isCancelled()) {
			editor.substituteSelectedEntities();
		}

		editor.setFocus();
 	}
}
