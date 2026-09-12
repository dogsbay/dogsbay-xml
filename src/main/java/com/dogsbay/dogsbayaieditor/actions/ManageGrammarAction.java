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

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.grammar.GrammarManagementDialog;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that can be used to create a new XML Type.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:53:19 $
 * @author Dogsbay
 */
 public class ManageGrammarAction extends AbstractAction {
 	private DogsBayAIEditor parent = null;
	private ConfigurationProperties properties = null;
	private GrammarManagementDialog dialog = null;

 	/**
	 * The constructor for the action which creates a new
	 * document.
	 *
	 * @param parent the parent frame.
	 */
 	public ManageGrammarAction( DogsBayAIEditor parent, ConfigurationProperties props) {
 		super( "Manage Types");

		this.parent = parent;
		this.properties = props;

		putValue( MNEMONIC_KEY, Integer.valueOf( 'M'));
//		putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK, false));
		putValue( SHORT_DESCRIPTION, "Manage Types");
 	}
 	
	/**
	 * The implementation of the new grammar action.
	 *
	 * @param the action event.
	 */
 	public void actionPerformed( ActionEvent e) {
		if ( dialog == null) {
			dialog = new GrammarManagementDialog( parent, properties);
		}

		dialog.showDialog();

		parent.updateGrammarActions();
	}
}
