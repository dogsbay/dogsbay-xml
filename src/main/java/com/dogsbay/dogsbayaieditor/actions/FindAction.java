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
import com.dogsbay.dogsbayaieditor.FindDialog;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to find information in a XML Document.
 *
 * @version	$Revision: 1.9 $, $Date: 2005/08/23 14:28:46 $
 * @author Dogsbay
 */
 public class FindAction extends AbstractAction {
 	private static final boolean DEBUG = false;

 	private FindDialog dialog = null;

 	private DogsBayAIEditor parent = null;
	private ConfigurationProperties properties = null;
	
 	/**
	 * The constructor for the action which allows addition of 
	 * documents to the application.
	 */
 	public FindAction( DogsBayAIEditor parent, ConfigurationProperties props) {
 		super( "Find...");

		putValue( MNEMONIC_KEY, Integer.valueOf( 'F'));
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Find16.gif"));
		putValue( SHORT_DESCRIPTION, "Find");
		
		this.parent = parent;
	 	this.properties = props;
		
		setEnabled( false);
 	}
	
	private FindDialog getDialog() {
		if ( dialog == null) {
			dialog = new FindDialog( parent, properties, parent);
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
	 * The implementation of the find action, called
	 * after a user action.
	 *
	 * @param the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
 		// Use the new VSCode-style find bar
 		if (parent.getCurrent() instanceof Editor) {
 			Editor editor = (Editor) parent.getCurrent();
 			String selectedText = editor.getSelectedText();
 			editor.getFindBar().showFind(selectedText);
 		}

 		/* OLD DIALOG-BASED IMPLEMENTATION - Replaced with FindBar
 		FindDialog dialog = getDialog();
 		//now do this through an action listener in the dialog if not xpath
 		if(dialog.isXPath() == true) {
 			parent.getView().updateModel();
 		}

 		dialog.init( parent.getDocument().isError());
		dialog.show( parent.getView().getEditor().getSelectedText());



		boolean matchCase = dialog.isCaseSensitive();
		boolean down = dialog.isSearchDirectionDown();
		boolean regExp = dialog.isRegularExpression();
		boolean matchWord = dialog.isMatchWholeWord();
		boolean wrapSearch = dialog.isWrapSearch();
		boolean isXPath = dialog.isXPath();

		properties.setMatchCase( matchCase);
		properties.setMatchWholeWord( matchWord);
		properties.setDirectionDown( down);
		properties.setBasicSearch( dialog.isBasic());
		properties.setXPath( isXPath);
		properties.setRegularExpression( regExp);
		properties.setWrapSearch( wrapSearch);

		if ( dialog.isBasic()) {
			wrapSearch = true;
			isXPath = false;
			regExp = false;
 		}

		if ( !dialog.isCancelled()) {
			String xpath = null;

			if ( isXPath) {
				xpath = dialog.getXPath();
			}

			String search = dialog.getSearch();

			if ( search != null) {
				((Editor)parent.getCurrent()).search( xpath, search, regExp, matchCase, matchWord, down, wrapSearch);

				properties.addSearch( search);

				if ( !dialog.isBasic()) {
					properties.addXPath( xpath);
				}
			}
		}

		parent.getCurrent().setFocus();
		*/
 	}
 }
