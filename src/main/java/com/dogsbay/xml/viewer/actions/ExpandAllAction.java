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

package com.dogsbay.xml.viewer.actions;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.KeyStroke;

import com.dogsbay.xml.viewer.Viewer;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;

/**
 * An action that can be used to expand all nodes in the viewer.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:51:00 $
 * @author Dogsbay
 */
 public class ExpandAllAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	
	private Viewer viewer = null;

 	/**
	 * The constructor for the action to expand all nodes 
	 * in the viewer.
	 *
	 * @param editor the XML viewer
	 */
 	public ExpandAllAction( Viewer viewer) {
		super( "Expand All");
		
		if (DEBUG) System.out.println( "ExpandAllAction( "+viewer+")");
		
		putValue( MNEMONIC_KEY, Integer.valueOf( 'E'));
		putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/xml/viewer/icons/ExpandAll.gif"));
		putValue( SHORT_DESCRIPTION, "Expand All Nodes");

		this.viewer = viewer;
 	}
 	
	/**
	 * The implementation of the expand all action, called 
	 * after a user action.
	 *
	 * @param event the action event.
	 */
 	public void actionPerformed( ActionEvent event) {
 		if (DEBUG) System.out.println( "ExpandAllAction.actionPerformed( "+event+")");

		viewer.expandAll();
 	}
}
