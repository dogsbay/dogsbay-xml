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

import com.dogsbay.dogsbayaieditor.TextPrinter;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;

/**
 * An action that can be used to print the XML Document.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:53:18 $
 * @author Dogsbay
 */
 public class PageSetupAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	
 	/**
	 * The constructor for the action which indents 
	 * the selected text.
	 *
	 * @param editor the XML Editor
	 */
 	public PageSetupAction() {
		super( "Page Setup");
		
		putValue( MNEMONIC_KEY, Integer.valueOf( 'u'));
//		putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/PageSetup16.gif"));
		putValue( SHORT_DESCRIPTION, "Page Setup");
 	}
 	
	/**
	 * The implementation of the unindent action, called 
	 * after a user action.
	 *
	 * @param event the action event.
	 */
 	public void actionPerformed( ActionEvent event) {
 		if (DEBUG) System.out.println( "PageSetupAction.actionPerformed( "+event+")");
		
		TextPrinter printer = TextPrinter.getPrinter();
		printer.setup();
 	}
}
