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
import java.io.IOException;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.dogsbayaieditor.ChangeManager;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.URLUtilities;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.component.AutomaticProgressMonitor;

/**
 * An action that can be used to save a XML Document.
 *
 * @version	$Revision: 1.8 $, $Date: 2005/08/29 08:31:25 $
 * @author Dogsbay
 */
 public class SaveAction extends AbstractAction {
 	private static final boolean DEBUG = false;

 	private DogsBayAIEditor parent = null;
	private DogsBayDocument document = null;
	
 	/**
	 * The constructor for the action which allows addition of 
	 * documents to the application.
	 */
 	public SaveAction( DogsBayAIEditor parent) {
 		super( "Save");

		putValue( MNEMONIC_KEY, Integer.valueOf( 'S'));
		//putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Save16.gif"));
		putValue( SHORT_DESCRIPTION, "Save current Document");
		
		this.parent = parent;

		setEnabled( false);
 	}
 	
	public void setDocument( DogsBayDocument document) {
		this.document = document;
		
		setEnabled( document != null);
	}

	/**
	 * The implementation of the save document action, called 
	 * after a user action.
	 *
	 * @param the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		//try {
		//	LicenseManager licenseManager = LicenseManager.getInstance();
		//	licenseManager.isValid( com.dogsbay.license.KeyGenerator.generate(2), "DogsBay XML");
		//} catch (Exception x) {
		//	System.exit(0);
		//	return;
		//}

		if ( document != null) {
			if ( document.isReadOnly() || (document.getURL() == null)) {
				parent.getSaveAsAction().execute();
			} else {
				parent.setWait( true);
				parent.setStatus( "Saving ...");
	
				// Run in Thread!!!
				Runnable runner = new Runnable() {
					public void run()  {
						try {
							parent.getView().updateModelOrThrow();   // a failed flush must not save stale text

							// Format-on-save: reformat the editor buffer in place first, so the
							// view shows exactly what gets written (the disk-bytes hook alone
							// would leave the view stale). No-op unless enabled and the text
							// editor is the active view.
							if ( com.dogsbay.dogsbayaieditor.format.FormatOnSaveHook.isEnabledFor( document.getURL())) {
								parent.getFormatAction().formatInPlace();
							}

							ChangeManager changeManager = parent.getView().getChangeManager();
	
							AutomaticProgressMonitor monitor = new AutomaticProgressMonitor( parent, null, "Saving \""+URLUtilities.toString( document.getURL())+"\".", 250);
	
							monitor.start();
							document.save();
							monitor.stop();
							
							changeManager.markSave();
						} catch ( IOException x) {
							JOptionPane.showMessageDialog(	parent,
														    "Could not save "+document.getName()+"\n"+
															x.getMessage(),
														    "Save Error",
														    JOptionPane.ERROR_MESSAGE);
						
						} finally {
							DogsBayView view = parent.getView();
							
							if ( view != null) {
								view.getCurrentView().setFocus();
							}
	
							parent.setStatus( "Done");
							parent.setWait( false);
						}
					}
				};
				
				// Create and start the thread ...
				Thread thread = new Thread( runner);
				thread.start();
			}
		}
 	}
 }
