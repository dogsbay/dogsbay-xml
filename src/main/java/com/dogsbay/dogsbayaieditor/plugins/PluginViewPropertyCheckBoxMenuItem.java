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

package com.dogsbay.dogsbayaieditor.plugins;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Vector;

import javax.swing.JCheckBoxMenuItem;

import com.dogsbay.dogsbayaieditor.DogsBayView;


/**
 * 
 *
 * @version	$Revision: 1.0 $, $Date: 18 Apr 2007 12:17:40 $
 */
public class PluginViewPropertyCheckBoxMenuItem extends JCheckBoxMenuItem implements ItemListener {

	private PluginView pluginView = null;
	private String propertyName = null;
	private String propertyLabel = null;

	public PluginViewPropertyCheckBoxMenuItem(PluginView view, String propertyName, String propertyLabel) {
		
		super(propertyLabel);
		this.pluginView = view;
		this.propertyName = propertyName;
		
		this.addItemListener(this);
		
		if(pluginView.getProperties() != null) {
			this.setSelected(pluginView.getProperties().getBoolean((propertyName)));
		}
	
	}
	
	/* (non-Javadoc)
	 * @see java.awt.event.ItemListener#itemStateChanged(java.awt.event.ItemEvent)
	 */
	public void itemStateChanged(ItemEvent e) {
	
//		properties.getGridProperties().hideContainerTables( gridHideContainerTables.isSelected());
			
		//Vector views = getViews();
		//for (int i = 0; i < views.size(); i++) {
//				((DogsBayView) views.elementAt(i)).getGrid().updatePreferences();
		//}
		
		pluginView.getProperties().set(propertyName, this.isSelected());
		
		Vector views = pluginView.getDogsBayAIEditor().getViews();		
		for (int i = 0; i < views.size(); i++) {
			((DogsBayView) views.elementAt(i)).updatePreferences();
		}
		
		
	}
	
}
