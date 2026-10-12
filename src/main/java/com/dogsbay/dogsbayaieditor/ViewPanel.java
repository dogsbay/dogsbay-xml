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

import java.awt.LayoutManager;

import javax.swing.JPanel;

/**
 * The base panel for a view component.
 *
 * @version	$Revision: 1.1 $, $Date: 2004/03/25 18:52:46 $
 * @author Dogsbay
 */
public abstract class ViewPanel extends JPanel {
	/**
	 * Constructor, calls the super constructor.
	 */
	public ViewPanel() {
		super();
	}

	/**
	 * Constructor, calls the super constructor.
	 */
	public ViewPanel( boolean isDoubleBuffered) {
		super( isDoubleBuffered);
	}

	/**
	 * Constructor, calls the super constructor.
	 */
	public ViewPanel( LayoutManager layout) {
		super( layout);
	}

	/**
	 * Constructor, calls the super constructor.
	 */
	public ViewPanel( LayoutManager layout, boolean isDoubleBuffered) {
		super( layout, isDoubleBuffered);
	}

	public abstract void setFocus();
	public abstract void updatePreferences();
	public abstract void setProperties();
} 
