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

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JList;
import javax.swing.ListModel;

import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.ErrorPane;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;

/**
 * An action that can be used to Copy the error list information 
 * in a XML Document.
 *
 * @version	$Revision: 1.1 $, $Date: 2005/04/12 15:45:17 $
 */
 public class CopyErrorListAction extends AbstractAction {
 	private static final boolean DEBUG = false;

 	private DogsBayAIEditor parent = null;
	
 	/**
	 * The constructor for the CopyErrorList action.
	 *
	 * @param editor the editor to CopyErrorList information from.
	 */
 	public CopyErrorListAction( DogsBayAIEditor parent) {
 		super( "Copy To Clipboard");

		putValue( MNEMONIC_KEY, Integer.valueOf( 'C'));
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Copy16.gif"));
		putValue( SHORT_DESCRIPTION, "Copy To Clipboard");
		
		this.parent = parent;
		
		setEnabled( false);
 	}
 	
	/**
	 * Sets the current view.
	 *
	 * @param view the current view.
	 */
	public void setView( Object view) {
		setEnabled( view instanceof Editor);
	}

	/**
	 * The implementation of the CopyErrorList action.
	 *
	 * @param e the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		try {
            ErrorPane errors = parent.getOutputPanel().getErrorPane();
            JList list = errors.getList();
            ListModel model = (ListModel)list.getModel();
            StringBuffer buffer = new StringBuffer("");
            for(int cnt=0;cnt<model.getSize();++cnt) {
                
                buffer.append(model.getElementAt(cnt)+"\n");
            }
            String str = buffer.toString();
            StringSelection ss = new StringSelection(str);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, null);
        }
        catch (Exception e1) {
            // catch HeadlessException
            //e1.printStackTrace();
            MessageHandler.showError(parent, "Error copying contents to clipboard", "Copy To Clipboard Error");
        }
	    
 	}
 }
