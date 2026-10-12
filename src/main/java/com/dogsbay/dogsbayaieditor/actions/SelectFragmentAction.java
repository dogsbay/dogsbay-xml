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

import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.SelectFragmentDialog;
import com.dogsbay.dogsbayaieditor.grammar.FragmentProperties;
import com.dogsbay.dogsbayaieditor.grammar.GrammarProperties;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to change between documents.
 *
 * @version	$Revision: 1.2 $, $Date: 2004/08/20 15:33:28 $
 * @author Dogs bay
 */
 public class SelectFragmentAction extends AbstractAction {
 	private static final boolean DEBUG = false;

 	private SelectFragmentDialog dialog = null;

 	private DogsBayAIEditor parent = null;
	private ConfigurationProperties properties = null;
	
 	/**
	 * The constructor for the action which allows changing which document is active
	 */
 	public SelectFragmentAction( DogsBayAIEditor parent) {
 		super( "Insert Fragment...");

		putValue( MNEMONIC_KEY, Integer.valueOf('m'));
		putValue( SHORT_DESCRIPTION, "Insert Fragment");
		
		this.parent = parent;
		
		setEnabled( false);
 	}
	
	private SelectFragmentDialog getDialog() {
		if ( dialog == null) {
			dialog = new SelectFragmentDialog( parent);
		}
		
		return dialog;
	}
 	
	/**
	 * Sets the current view.
	 *
	 * @param view the current view.
	 */
	public void setView( Object view) {
		setEnabled( ( view instanceof Editor));
	}

	/**
	 * The implementation of the Select document action
	 *
	 * @param the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
 		SelectFragmentDialog dialog = getDialog();
 		
 		GrammarProperties grammar = parent.getView().getGrammar();
 		
 		if ( grammar != null) {
			dialog.show( grammar.getFragments());
	
			if (!dialog.isCancelled()) {
				FragmentProperties fragment = dialog.getSelectedFragment();
	
				parent.getView().getEditor().insertFragment( fragment.isBlock(), fragment.getContent());
				parent.getView().getEditor().setFocus();
			}
 		}
 	}
 }
