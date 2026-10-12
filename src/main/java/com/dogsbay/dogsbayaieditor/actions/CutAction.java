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
import com.dogsbay.dogsbayaieditor.plugins.PluginViewPanel;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;

/**
 * An action that can be used to cut information from
 * the XML Document.
 *
 * @version	$Revision: 1.5 $, $Date: 2005/05/05 14:25:58 $
 * @author Dogsbay
 */
 public class CutAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	private DogsBayAIEditor parent = null;
    private Object view;

 	/**
 	 * The constructor for the copy action.
 	 *
 	 * @param editor the editor to copy information from.
 	 */
 	public CutAction( DogsBayAIEditor parent) {
 		super( "Cut");

		putValue( MNEMONIC_KEY, Integer.valueOf( 't'));
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Cut16.gif"));
		putValue( SHORT_DESCRIPTION, "Cut");
		
		this.parent = parent;
		
		setEnabled( false);
 	}
 	
	/**
	 * Sets the current view.
	 *
	 * @param view the current view.
	 */
	public void setView( Object view) {
	    this.view = view;
	    if( view instanceof Editor) {
	        setEnabled(true);
	    }
	    else if( view instanceof PluginViewPanel) {
	        setEnabled(true);
	    }
	    else {
	        setEnabled(false);
	    }
	}

	/**
	 * The implementation of the cut action.
	 *
	 * @param e the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		/*((Editor)parent.getCurrent()).cut();
		((Editor)parent.getCurrent()).setFocus();*/
		
		if( view instanceof Editor) {
 	       Editor editor = (Editor)parent.getCurrent();
 	       editor.cut();
 	       editor.setFocus();
	    }
	    else if( view instanceof PluginViewPanel) {
	    	PluginViewPanel pluginViewPanel = (PluginViewPanel)parent.getCurrent();
	    	pluginViewPanel.cut();
	    	pluginViewPanel.setFocus();
	    }
 	}
 }
