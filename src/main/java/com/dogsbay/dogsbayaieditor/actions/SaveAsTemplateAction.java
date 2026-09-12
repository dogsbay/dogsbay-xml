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
import java.net.URL;
import java.util.Vector;

import javax.swing.AbstractAction;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.ChangeManager;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.FileUtilities;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.dogsbay.dogsbayaieditor.template.TemplateProperties;
import com.dogsbay.dogsbayaieditor.template.TemplatePropertiesDialog;

/**
 * An action that can be used to save a XML Document as a template.
 *
 * @version	$Revision: 1.5 $, $Date: 2004/10/28 16:17:07 $
 * @author Dogsbay
 */
public class SaveAsTemplateAction extends AbstractAction {
 	private static final boolean DEBUG = false;
 	private DogsBayAIEditor parent = null;
 	private ConfigurationProperties properties = null;
	private TemplatePropertiesDialog dialog = null;

 	/**
	 * Constructs the save as action.
	 */
 	public SaveAsTemplateAction( DogsBayAIEditor parent, ConfigurationProperties props) {
 		super( "Save As Template ...");
		
		this.parent = parent;
		this.properties = props;

		putValue( MNEMONIC_KEY, Integer.valueOf( 'T'));
//		putValue( SMALL_ICON, DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/SaveAs16.gif"));
		putValue( SHORT_DESCRIPTION, "Save As Template ...");
		
		setEnabled( false);
 	}
 	
	public void setDocument( DogsBayDocument document) {
		if ( document != null) {
			setEnabled( true);
		} else {
			setEnabled( false);
		}
	}

	/**
	 * The implementation of the save as template action, called 
	 * after a user action.
	 *
	 * @param the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		if ( dialog == null) {
			dialog = new TemplatePropertiesDialog( parent, true);
		}
		
		DogsBayDocument document = parent.getDocument();
		
		Vector gs = properties.getTemplateProperties();
		Vector names = new Vector();

		for ( int i = 0; i < gs.size(); i++) {
			String name = ((TemplateProperties)gs.elementAt( i)).getName();
			
			names.addElement( name);
		}

		dialog.show( document.getName(), document.getURL(), names);

	 	DogsBayView view = parent.getView();
	 	Editor editor = view.getEditor();
	 	ChangeManager changeManager = view.getChangeManager();
		
		if ( !dialog.isCancelled()) {
			URL url = dialog.getURL();
			
			if ( url.getProtocol().equals( "file")) {
				File file = new File( url.getFile());

				parent.getView().updateModel();

				FileUtilities.saveAsDocument( file);
			}
			
			TemplateProperties props = new TemplateProperties();
			
			// update list...
			props.setName( dialog.getName());
			props.setURL( dialog.getURL());

			properties.addTemplateProperties( props);
	 	}
		
 	}
 }
