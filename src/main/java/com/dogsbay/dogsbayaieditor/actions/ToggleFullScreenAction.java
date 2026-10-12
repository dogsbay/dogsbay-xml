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
 * An action that can be used to collapse all nodes in the viewer.
 *
 * @version	$Revision: 1.3 $, $Date: 2004/10/21 15:43:40 $
 * @author Dogsbay
 */
public class ToggleFullScreenAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	private DogsBayAIEditor parent = null;
	
 	/**
	 * The constructor for the action to collapse all nodes 
	 * in the viewer.
	 *
	 * @param editor the XML viewer
	 */
 	public ToggleFullScreenAction( DogsBayAIEditor parent) {
		super( "Full Screen");
		
		this.parent = parent;
		
		putValue( MNEMONIC_KEY, Integer.valueOf( 'F'));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/FullScreen16.gif"));
		putValue( SHORT_DESCRIPTION, "Toggle Full Screen");
 	}
 	
	/**
	 * The implementation of the collapse all action, called 
	 * after a user action.
	 *
	 * @param event the action event.
	 */
 	public void actionPerformed( ActionEvent event) {
 		parent.toggleFullScreen();
 		
 		if ( parent.getCurrent() != null) {
 			parent.getCurrent().setFocus();
 		}
 	}
}
