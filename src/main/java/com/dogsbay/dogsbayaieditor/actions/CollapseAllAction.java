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
import com.dogsbay.xml.viewer.Viewer;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.plugins.PluginViewPanel;


/**
 * An action that can be used to collapse all nodes in the viewer.
 *
 * @version	$Revision: 1.4 $, $Date: 2005/01/28 12:24:21 $
 * @author Dogsbay
 */
public class CollapseAllAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	private Object view = null;
	
 	/**
	 * The constructor for the action to collapse all nodes 
	 * in the viewer.
	 *
	 * @param editor the XML viewer
	 */
 	public CollapseAllAction() {
		super( "Collapse All");
		
		putValue( MNEMONIC_KEY, Integer.valueOf( 'C'));
//		putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/CollapseAll.gif"));
		putValue( SHORT_DESCRIPTION, "Collapses All Nodes");
		
		setEnabled( false);
 	}
 	
	/**
	 * Sets the current view.
	 *
	 * @param view the current view.
	 */
	public void setView( Object view) {
		this.view = view;
		
		setEnabled( this.view != null);
	}

	/**
	 * The implementation of the collapse all action, called 
	 * after a user action.
	 *
	 * @param event the action event.
	 */
 	public void actionPerformed( ActionEvent event) {
 		if (DEBUG) System.out.println( "CollapseAllAction.actionPerformed( "+event+")");
		
	 	if ( view instanceof Viewer) {
	 		((Viewer)view).collapseAll();
		 	((Viewer)view).setFocus();
	 	} else if ( view instanceof Editor) {
		 	((Editor)view).collapseAll();
		 	((Editor)view).setFocus();
	 	} else if ( view instanceof PluginViewPanel) {
		 	((PluginViewPanel)view).collapseAll();
		 	((PluginViewPanel)view).setFocus();
	 	}
 	}
}
