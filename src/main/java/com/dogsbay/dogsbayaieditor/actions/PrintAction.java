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
import java.awt.print.PrinterException;
import java.net.URL;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.text.PlainDocument;

import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.TextPrinter;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.dogsbay.dogsbayaieditor.properties.PrintPreferences;
import com.dogsbay.dogsbayaieditor.properties.TextPreferences;

/**
 * An action that can be used to print the XML Document.
 *
 * @version	$Revision: 1.7 $, $Date: 2004/07/21 09:09:42 $
 * @author Dogsbay
 */
 public class PrintAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	private DogsBayAIEditor parent = null;
	private ConfigurationProperties properties = null;
	
 	/**
	 * The constructor for the action which prints the current document.
	 *
	 * @param parent the DogsBayAIEditor main class.
	 */
 	public PrintAction( DogsBayAIEditor parent, ConfigurationProperties properties) {
		super( "Print");
		
		if (DEBUG) System.out.println( "PrintAction( "+parent+", "+properties+")");
		
		putValue( MNEMONIC_KEY, Integer.valueOf( 'P'));
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Print16.gif"));
		putValue( SHORT_DESCRIPTION, "Print");
		
		this.properties = properties;
		this.parent = parent;
		
		setEnabled( false);
 	}
 	
	/**
	 * The implementation of the print action, called 
	 * after a user action.
	 *
	 * @param event the action event.
	 */
 	public void actionPerformed( ActionEvent event) {
 		if (DEBUG) System.out.println( "PrintAction.actionPerformed( "+event+")");
		Editor editor = parent.getView().getEditor();
		
		parent.getView().updateModel();
		
		TextPrinter printer = TextPrinter.getPrinter();
		PrintPreferences prefs = properties.getPrintPreferences();
		
		printer.setFont( prefs.getFont());
		printer.setPrintHeader( prefs.isPrintHeader());
		printer.setPrintLineNumber( prefs.isPrintLineNumbers());
		printer.setWrapText( prefs.isWrapText());
		
		String location = "New Document";
		URL url = parent.getDocument().getURL();
		
		if ( url != null) {
			location = url.toString();
		}
		
		try {
			printer.print( (PlainDocument)editor.getEditor().getDocument(), location, TextPreferences.getTabSize());
		} catch ( PrinterException e) {
			MessageHandler.showError( "Error printing the Document", "Printing Error");
			e.printStackTrace();
		}
		
		DogsBayView view = parent.getView();
		
		if ( view != null) {
			view.getCurrentView().setFocus();
		}
 	}
}
