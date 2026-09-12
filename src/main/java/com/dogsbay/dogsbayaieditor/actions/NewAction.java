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
import javax.swing.SwingUtilities;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.FileUtilities;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.NewDocumentDialog;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to create a new XML Document.
 *
 * @version	$Revision: 1.6 $, $Date: 2004/10/27 13:26:15 $
 * @author Dogsbay
 */
 public class NewAction extends AbstractAction {
 	private DogsBayAIEditor parent = null;
	private ConfigurationProperties properties = null;
	private NewDocumentDialog dialog = null;

 	/**
	 * The constructor for the action which creates a new
	 * document.
	 *
	 * @param parent the parent frame.
	 */
 	public NewAction( DogsBayAIEditor parent, ConfigurationProperties properties) {
// 		super( parent, props, "New");
 		super( "New");
		
		this.parent = parent;
		this.properties = properties;

		putValue( MNEMONIC_KEY, Integer.valueOf( 'N'));
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/New16.gif"));
		putValue( SHORT_DESCRIPTION, "New Document");
 	}
	
	/**
	 * The implementation of the new document action.
	 *
	 * @param the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
 		parent.repaint();

 		if ( dialog == null) {
			dialog = new NewDocumentDialog( parent, properties);
		}
		
		dialog.setVisible(true);
		
		if ( !dialog.isCancelled()) {
			parent.setWait( true);
			parent.setStatus( "Creating new Document...");

			SwingUtilities.invokeLater( new Runnable() {
				public void run() {
					try {
						if ( dialog.getNewDocumentType() == NewDocumentDialog.NEW_DEFAULT_XML_DOCUMENT) {
							FileUtilities.newDocument();
						} else if ( dialog.getNewDocumentType() == NewDocumentDialog.NEW_DTD_DOCUMENT) {
							FileUtilities.newDTDDocument();
						} else if ( dialog.getNewDocumentType() == NewDocumentDialog.NEW_TEMPLATE_DOCUMENT) {
							FileUtilities.newDocument( dialog.getTemplateURL());
						} else if ( dialog.getNewDocumentType() == NewDocumentDialog.NEW_FROM_CLIPBOARD_DOCUMENT) {
							FileUtilities.newDocument();
							parent.getView().getEditor().setText(dialog.getClipboardData());
						} else { // if ( dialog.getNewDocumentType() == NewDocumentDialog.NEW_TYPE_DOCUMENT) {
							FileUtilities.newDocument( dialog.getSelectedType());
						}
					} catch ( Exception e) {
						MessageHandler.showError( "Could not create Template", e, "Template Error");
					} finally {
						parent.setWait( false);
						parent.setStatus( "Done");

						DogsBayView view = parent.getView();

						if (view != null) {
				 			view.requestFocus();
				 		}
					}
				}
			});
		}
		else
		{
			// if cancelled pressed set the focu back
			DogsBayView view = parent.getView();
	 		if (view != null)
	 		{
	 			view.requestFocus();
	 		}
	 		else
	 		{
	 			// no view available, so set focus back to intial
	 			parent.setIntialFocus();
	 		}
		}
		
//		try {
//			GrammarProperties type = FileUtilities.getSelectedGrammar( chooser);
//			File file = chooser.getSelectedFile();
//			
//			if ( file.exists()) {
//				// show a dialog asking if it is okay to overwrite the existing file
//				int result = MessageHandler.showConfirm( "The document \""+file.getName()+"\" already exists,\n"+
//				                                         "do you want to replace the existing document?");
//
//				if ( result == JOptionPane.YES_OPTION) {
//					FileUtilities.newDocument( file.toURL(), type);
//				}
//			} else {
//				String path = chooser.getSelectedFile().getPath();
//
//				if ( path.indexOf( ".") == -1) {
//					if ( type != null) {
//						file = new File( path+"."+FileUtilities.getExtension( type));
//					} else {
//						file = new File( path+".xml");
//					}
//				}
//
//				FileUtilities.newDocument( file.toURL(), type);
//			}
//		
//		} catch ( MalformedURLException x) {
//			x.printStackTrace();
//		}
	}
}
