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
 * An action that can be used to paste information in 
 * the XML Document.
 *
 * @version	$Revision: 1.5 $, $Date: 2005/05/06 14:46:35 $
 * @author Dogsbay
 */
 public class PasteAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	private DogsBayAIEditor parent = null;
    private Object view;

 	/**
	 * The constructor for the paste action.
	 *
	 * @param editor the editor to paste information to.
	 */
 	public PasteAction( DogsBayAIEditor parent) {
 		super( "Paste");

		putValue( MNEMONIC_KEY, Integer.valueOf( 'P'));
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Paste16.gif"));
		putValue( SHORT_DESCRIPTION, "Paste");
		
		this.parent = parent;
		setEnabled( false);
 	}
 	
	/**
	 * Sets the current view.
	 *
	 * @param view the current view.
	 */
	public void setView( Object view) {
		//setEnabled( view instanceof Editor);
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
	 * The implementation of the paste action.
	 *
	 * @param the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		/*((Editor)parent.getCurrent()).paste();
		((Editor)parent.getCurrent()).setFocus();*/
 	    
 	   if( view instanceof Editor) {
 	       Editor editor = (Editor)parent.getCurrent();
 	       editor.paste();
 	       editor.setFocus();
	    }
	    else if( view instanceof PluginViewPanel) {
	    	PluginViewPanel pluginViewPanel = (PluginViewPanel)parent.getCurrent();
	    	pluginViewPanel.paste();
	    	pluginViewPanel.setFocus();
	    }
 	}
 }
