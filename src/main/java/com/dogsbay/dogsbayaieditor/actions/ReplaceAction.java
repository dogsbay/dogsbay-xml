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

import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.ReplaceDialog;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to find information in a XML Document.
 *
 * @version	$Revision: 1.8 $, $Date: 2004/10/21 15:41:55 $
 * @author Dogsbay
 */
 public class ReplaceAction extends AbstractAction {
 	private static final boolean DEBUG = false;

 	private ReplaceDialog dialog = null;

 	private DogsBayAIEditor parent = null;
	private ConfigurationProperties properties = null;
	
 	/**
	 * The constructor for the action which allows addition of 
	 * documents to the application.
	 */
 	public ReplaceAction( DogsBayAIEditor parent, ConfigurationProperties props) {
 		super( "Replace...");

		putValue( MNEMONIC_KEY, Integer.valueOf( 'e'));
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Replace16.gif"));
		putValue( SHORT_DESCRIPTION, "Replace");
		
		this.parent = parent;
	 	this.properties = props;
		
		setEnabled( false);
 	}
	
	private ReplaceDialog getDialog() {
		if ( dialog == null) {
			dialog = new ReplaceDialog( parent, properties);
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
		
		if ( getDialog().isVisible()) {
			getDialog().setVisible(false);
		}
	}

	/**
	 * The implementation of the replace action, called
	 * after a user action.
	 *
	 * @param the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
 		// Use the new VSCode-style find bar in replace mode
 		if (parent.getCurrent() instanceof Editor) {
 			Editor editor = (Editor) parent.getCurrent();
 			String selectedText = editor.getSelectedText();
 			editor.getFindBar().showReplace(selectedText);
 		}

 		/* OLD DIALOG-BASED IMPLEMENTATION - Replaced with FindBar
		ReplaceDialog dialog = getDialog();

		if ( parent.getView() != null) {
			parent.getView().updateModel();

			dialog.init( parent.getView().getEditor().getSelectedText(), parent.getDocument().isError());
		}

		dialog.setVisible(true);
		*/
 	}
 }
