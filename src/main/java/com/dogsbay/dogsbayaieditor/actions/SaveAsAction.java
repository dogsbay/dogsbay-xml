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
import java.io.File;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.FileUtilities;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to save a XML Document with a different name.
 *
 * @version	$Revision: 1.6 $, $Date: 2004/11/04 19:20:50 $
 * @author Dogsbay
 */
 public class SaveAsAction extends AbstractAction {
 	private static final boolean DEBUG = false;
 	private DogsBayAIEditor parent = null;
 	private ConfigurationProperties properties = null;

 	/**
	 * Constructs the save as action.
	 */
 	public SaveAsAction( DogsBayAIEditor parent, ConfigurationProperties props) {
 		super( "Save As ...");
		
		this.parent = parent;
		this.properties = props;

		putValue( MNEMONIC_KEY, Integer.valueOf( 'A'));
		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/SaveAs16.gif"));
		putValue( SHORT_DESCRIPTION, "Save current Document As...");
		
		setEnabled( false);
 	}
 	
	public void setDocument( DogsBayDocument document) {
		setEnabled( document != null);
	}

	/**
	 * The implementation of the save document action, called 
	 * after a user action.
	 *
	 * @param the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
 		execute();
 	}
 	
 	public void execute() { 
	 	DogsBayView view = parent.getView();
		File file = FileUtilities.selectOutputFile( (File)null, null);
		
		if ( file != null) {
			view.updateModelOrThrow();

			FileUtilities.saveAsDocument( file);

			if ( view != null) {
				view.getCurrentView().setFocus();
			}
	 	}
 	}
}
