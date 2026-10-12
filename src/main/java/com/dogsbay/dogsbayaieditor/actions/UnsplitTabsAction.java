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

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;

/**
 * An action that can be used to close a XML Document.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/07/28 16:59:08 $
 * @author Dogsbay
 */
 public class UnsplitTabsAction extends AbstractAction {
 	private static final boolean DEBUG = false;

 	private DogsBayAIEditor parent = null;
	
 	/**
	 * The constructor for the action which allows addition of 
	 * documents to the application.
	 */
 	public UnsplitTabsAction( DogsBayAIEditor parent) {
 		super( "Unsplit");

		putValue( MNEMONIC_KEY, Integer.valueOf( 'V'));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Unsplit16.gif"));
		putValue( SHORT_DESCRIPTION, "Unsplit the Current selectected Tab.");
		
		this.parent = parent;
 	}
 	
	/**
	 * The implementation of the save document action, called 
	 * after a user action.
	 *
	 * @param the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		parent.unsplit();

		if ( parent.getCurrent() != null) {
 			parent.getCurrent().setFocus();
 		}
 	}
}
