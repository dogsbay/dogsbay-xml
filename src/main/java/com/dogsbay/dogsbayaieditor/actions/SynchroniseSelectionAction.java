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

import com.dogsbay.xml.XElement;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.xml.viewer.Viewer;
import com.dogsbay.dogsbayaieditor.plugins.PluginViewPanel;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;

/**
 * An action that can be used to collapse all nodes in the viewer.
 *
 * @version	$Revision: 1.5 $, $Date: 2005/03/09 17:05:38 $
 * @author Dogsbay
 */
public class SynchroniseSelectionAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	private Object view = null;
	private DogsBayAIEditor parent = null;
	
 	/**
	 * The constructor for the action to collapse all nodes 
	 * in the viewer.
	 *
	 * @param editor the XML viewer
	 */
 	public SynchroniseSelectionAction( DogsBayAIEditor parent) {
		super( "Synchronise Selection");
		
		this.parent = parent;
		
		putValue( MNEMONIC_KEY, Integer.valueOf( 'y'));
//		putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK, false));
//		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/CollapseAll.gif"));
		putValue( SHORT_DESCRIPTION, "Synchronise the Selection");
		
		setEnabled( false);
 	}
 	
	/**
	 * Sets the current view.
	 *
	 * @param view the current view.
	 */
	public void setView( Object view) {
		if ( view instanceof Viewer) {
			this.view = view;
		} else if ( view instanceof Editor) {
			this.view = view;
		} else if ( view instanceof PluginViewPanel) {
		    this.view = view;
		} else {
			this.view = null;
		}
		
		setEnabled( this.view != null);
	}

	/**
	 * The implementation of the collapse all action, called 
	 * after a user action.
	 *
	 * @param event the action event.
	 */
 	public void actionPerformed( ActionEvent event) {
		XElement element = parent.getView().getPreviousSelectedElement();
		
		if ( element != null) {
		 	if ( view instanceof Viewer) {
		 		((Viewer)view).setSelectedElement( element, false, -1);
		 	} else if ( view instanceof PluginViewPanel) {
		 	    ((PluginViewPanel)view).setSelectedElement( element);
		 	} else if ( view instanceof Editor) {
			 	if ( element.getContentStartPosition() > 0) {
			 		((Editor)view).setCursorPosition( element.getContentStartPosition());
			 	} else {
			 		((Editor)view).setCursorPosition( element.getElementEndPosition());
			 	}
		 	}
		}
 	}
}
