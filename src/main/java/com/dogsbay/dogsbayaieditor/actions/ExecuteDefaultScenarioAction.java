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
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.transform.ScenarioUtilities;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.grammar.GrammarProperties;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.dogsbay.dogsbayaieditor.scenario.ScenarioProperties;
import com.dogsbay.dogsbayaieditor.scenario.ScenarioSelectionDialog;

/**
 * 
 * An action that can be used to open a XML Document.
 *
 * @version $Revision: 1.5 $, $Date: 2004/10/13 18:24:32 $
 * @author Dogsbay
 */
public class ExecuteDefaultScenarioAction extends AbstractAction {
	private DogsBayAIEditor parent = null;
	private ConfigurationProperties properties = null;
	private ScenarioSelectionDialog allDialog = null;
	private ScenarioSelectionDialog dialog = null;
	// private ExecuteScenarioDialog executeDialog = null;

	/**
	 * The constructor for the action which changes Grammar properties.
	 *
	 * @param parent the parent frame.
	 */
	public ExecuteDefaultScenarioAction(DogsBayAIEditor parent, ConfigurationProperties props) {
		// super( parent, props, "Properties");
		super("Execute Scenario ...");

		this.parent = parent;
		this.properties = props;

		putValue(MNEMONIC_KEY, Integer.valueOf('E'));
		putValue(SMALL_ICON, DogsBayImageLoader.get().getImage("com/dogsbay/dogsbayaieditor/icons/ExecuteScenario16.gif"));
		// putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_F4, 0,
		// false));
		// putValue( ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_N,
		// InputEvent.CTRL_DOWN_MASK, false));
		putValue(SHORT_DESCRIPTION, "Execute Scenario");

		// setEnabled( false);
	}

	/**
	 * The implementation of the execute default scenario action.
	 *
	 * @param the action event.
	 */
	public void actionPerformed(ActionEvent e) {
		DogsBayDocument document = parent.getDocument();
		GrammarProperties grammar = parent.getGrammar();
		ScenarioProperties defaultScenario = null;

		Vector scenarios = null;

		if (grammar != null) {
			scenarios = grammar.getScenarios();
			defaultScenario = grammar.getDefaultScenario();
		}

		if (scenarios != null && scenarios.size() > 0) {
			if (dialog == null) {
				dialog = new ScenarioSelectionDialog(parent, properties, "Execute", true, false);
			}

			dialog.showDialog(scenarios, defaultScenario);

			if (!dialog.isCancelled()) {
				ScenarioProperties scenario = dialog.getSelectedScenario();
				parent.getExecutePreviousScenarioAction().setScenario(scenario);
				ScenarioUtilities.execute(document, scenario);
			}
		} else {
			scenarios = properties.getScenarioProperties();

			if (scenarios.size() > 0) {
				if (allDialog == null) {
					allDialog = new ScenarioSelectionDialog(parent, properties, "Execute", false, false);
				}

				allDialog.showDialog();

				if (!allDialog.isCancelled()) {
					ScenarioProperties scenario = allDialog.getSelectedScenario();
					parent.getExecutePreviousScenarioAction().setScenario(scenario);

					ScenarioUtilities.execute(document, scenario);
				}
			} else {
				MessageHandler.showMessage("No Scenarios Available.");
			}
		}

		DogsBayView view = parent.getView();

		if (view != null) {
			view.getCurrentView().setFocus();
		}
	}
}
