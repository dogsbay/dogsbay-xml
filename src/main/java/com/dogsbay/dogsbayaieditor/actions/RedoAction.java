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
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.plugins.PluginViewPanel;
import com.dogsbay.dogsbayaieditor.ChangeManager;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.ViewPanel;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;

/**
 * An action that can be used to save a XML Document.
 *
 * @version	$Revision: 1.9 $, $Date: 2005/04/25 08:28:14 $
 * @author Dogsbay
 */
 public class RedoAction extends AbstractAction implements ChangeListener {
 	private static final boolean DEBUG = false;

 	private ChangeManager handler	= null;
 	private DogsBayAIEditor parent 		= null;
	
 	/**
	 * The constructor for the action which allows addition of 
	 * documents to the application.
	 */
 	public RedoAction( DogsBayAIEditor parent) {
 		super( "Redo");

		putValue( MNEMONIC_KEY, Integer.valueOf( 'R'));
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Redo16.gif"));
		putValue( SHORT_DESCRIPTION, "Redo");
		
		this.parent = parent;
		
		setEnabled( false);
 	}
 	
	/**
	 * The implementation of the redo action.
	 *
	 * @param e the action event.
	 */
	public void setChangeManager( ChangeManager undo) {
		if (DEBUG) System.out.println( "RedoAction.setChangeManager( "+undo+")");
		if ( handler != null) {
			handler.removeChangeListener( this);
		}
		
		handler = undo;

		if ( handler != null) {
			handler.addChangeListener( this);
			setEnabled( handler.canRedo());
			
		} else {
			setEnabled( false);
		}
		
	}

	public void stateChanged( ChangeEvent event) {
		if (DEBUG) System.out.println( "RedoAction.stateChanged( "+event+") ["+handler.canRedo()+"]");
		setEnabled( handler.canRedo());
	}

	/**
	 * The implementation of the redo action.
	 *
	 * @param e the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		DogsBayView view = parent.getView();
		ViewPanel current = view.getCurrentView();
		
		if( handler.isPluginRedo()) {
		    
		    if ( !(current instanceof PluginViewPanel)) {
				try {
					parent.switchToPluginView((PluginViewPanel)current);
				} catch ( Exception x) {
					// should not happen
					x.printStackTrace();
				}
			}
			
			handler.redo();
		} else if ( handler.isAuthorRedo()
				&& ( current instanceof com.dogsbay.dogsbayaieditor.author.AuthorView
					|| current instanceof com.dogsbay.dogsbayaieditor.author.AuthorSplitView)) {
			// an Author-view edit is undone where it was made: switching to the
			// XML source first would take the writer out of the view they are in
			handler.redo();
		} else {
			Editor editor = parent.getView().getEditor();
			editor.setFocus();
			editor.updateCaretManual( true);
			editor.updateBookmarks();

			if ( !(current instanceof Editor)) {
				try {
					parent.switchToEditor();
				} catch ( Exception x) {
					// should not happen
					x.printStackTrace();
				}
			}
			
			handler.redo();

			editor.updateCaretManual( false);
			editor.resetBookmarks();
			editor.revalidate();
			editor.repaint();
		}
		
		parent.getCurrent().setFocus();
 	}
 }
