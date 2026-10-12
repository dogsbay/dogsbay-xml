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
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;

import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to find the next information 
 * in a XML Document.
 *
 * @version	$Revision: 1.7 $, $Date: 2004/10/26 10:06:49 $
 * @author Dogsbay
 */
 public class FindNextAction extends AbstractAction {
 	private static final boolean DEBUG = false;

 	private DogsBayAIEditor parent = null;
 	private ConfigurationProperties properties = null;
	
 	/**
	 * The constructor for the action which allows addition of 
	 * documents to the application.
	 */
 	public FindNextAction( DogsBayAIEditor parent, ConfigurationProperties props) {
 		super( "Find Next");

		putValue( MNEMONIC_KEY, Integer.valueOf( 'N'));
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_F3, 0, false));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/FindNext16.gif"));
		putValue( SHORT_DESCRIPTION, "Find Next");
		
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
	 	setEnabled( ( view instanceof Editor));
	 }

	 /**
	  * The implementation of the find action, called 
	  * after a user action.
	  *
	  * @param the action event.
	  */
	 public void actionPerformed( ActionEvent e) {
	 	Vector searches = properties.getSearches();
	 	Vector xpaths = properties.getXPaths();
		
		if ( searches.size() > 0) {
	 		String search = (String)searches.elementAt(0);
	 		String xpath = null;
	 		
	 		if ( properties.isXPath()) {
	 			xpath = (String)xpaths.elementAt(0);
	 		}

	 		boolean matchCase = properties.isMatchCase();
	 		boolean down = properties.isDirectionDown();
	 		boolean regExp = properties.isRegularExpression();
	 		boolean matchWord = properties.isMatchWholeWord();
	 		boolean wrapSearch = properties.isWrapSearch();

			if ( properties.isBasicSearch()) {
				xpath = null;
				wrapSearch = true;
				regExp = false;
	 		}

			if ( search != null) {
	 			((Editor)parent.getCurrent()).search( xpath, search, regExp, matchCase, matchWord, down, wrapSearch);
	 		}
		}
	 }
 }
