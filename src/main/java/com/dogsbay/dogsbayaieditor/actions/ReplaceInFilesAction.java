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
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * An action that opens the search panel in replace mode.
 *
 * @version $Revision: 1.0 $, $Date: 2025/12/07 $
 * @author DogsBay Ltd
 */
public class ReplaceInFilesAction extends AbstractAction {
	private DogsBayAIEditor parent = null;

	/**
	 * The constructor for the replace in files action.
	 *
	 * @param parent the parent editor
	 * @param props the configuration properties
	 */
	public ReplaceInFilesAction(DogsBayAIEditor parent, ConfigurationProperties props) {
		super("Replace in Files ...");

		this.parent = parent;

		putValue(SHORT_DESCRIPTION, "Replace in Files ...");

		setEnabled(true);
	}

	/**
	 * Opens the search panel in replace mode.
	 *
	 * @param e the action event
	 */
	public void actionPerformed(ActionEvent e) {
		// Use the new VSCode-style search panel in replace mode
		if (parent.getSearchPanel() != null) {
			parent.getSearchPanel().activateReplaceMode();
		}
	}
}
