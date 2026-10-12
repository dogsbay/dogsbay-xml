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

package com.dogsbay.dogsbayaieditor;

import java.awt.CardLayout;

import javax.swing.JPanel;

/**
 * The dialog that shows the DogsBay identity.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:52:46 $
 * @author Dogsbay
 */
public class NavigationPanel extends JPanel {
	CardLayout layout = null;

	/**
	 * The constructor for the about dialog.
	 *
	 * @param frame the parent frame.
	 */
	public NavigationPanel() {
		super();
		
//		setBorder( new BevelBorder( BevelBorder.LOWERED, Color.white, new Color( 204, 204, 204), new Color( 204, 204, 204), new Color( 102, 102, 102)));
		layout = new CardLayout();
		
		setLayout( layout);
	}

	public void show( String identifier) {
		layout.show( this, identifier);
	}

//	public void showDocument( String identifier) {
//		layout.show( this, identifier);
//	}
} 
