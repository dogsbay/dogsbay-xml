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

import com.dogsbay.xml.transform.ScenarioUtilities;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.scenario.ScenarioProperties;

/**
 * An action that can be used to open a XML Document.
 *
 * @version	$Revision: 1.2 $, $Date: 2005/03/16 17:45:15 $
 * @author Dogsbay
 */
public class ExecutePreviousFOAction extends AbstractAction {
 	private static final boolean DEBUG = false;
	
	private DogsBayAIEditor parent = null;
	private ScenarioProperties scenario = null;

 	/**
	 * The constructor for the action which allows opening 
	 * of XML Documents.
	 *
	 * @param parent the parent frame.
	 */
 	public ExecutePreviousFOAction( DogsBayAIEditor parent) {
// 		super( "Repeat XSLT");
 		super( "Execute Previous FO");

		this.parent = parent;

//		putValue( MNEMONIC_KEY, Integer.valueOf( 'P'));
		putValue( SHORT_DESCRIPTION, "Execute the previous FO Transformation");
		
		setEnabled( false);
 	}
 	
 	public void setScenario( ScenarioProperties scenario) {
 		this.scenario = scenario;
 		
 		setEnabled( scenario != null);
 	}
 	
 	/**
 	 * The implementation of the execute XSLT action.
 	 *
 	 * @param the action event.
 	 */
 	public void actionPerformed( ActionEvent e) {
		ScenarioUtilities.execute( parent.getDocument(), scenario);
 	}
}
