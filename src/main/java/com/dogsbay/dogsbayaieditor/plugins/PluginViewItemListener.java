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

import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.ViewPanel;


/**
 * 
 *
 * @version	$Revision: 1.0 $, $Date: 13 Mar 2007 16:02:32 $
 */
public class PluginViewItemListener implements ItemListener {

	private PluginView plugin = null;
	private DogsBayAIEditor editor = null;
	
	public PluginViewItemListener(PluginView pluginView, DogsBayAIEditor editor) {
		this.editor = editor;
		this.plugin = pluginView;
		
	}
	/* (non-Javadoc)
	 * @see java.awt.event.ItemListener#itemStateChanged(java.awt.event.ItemEvent)
	 */
	public void itemStateChanged(ItemEvent event) {

		if((editor != null) && (editor.getView() != null)) {
			if (event.getStateChange() == ItemEvent.SELECTED) {
				ViewPanel current = editor.getView().getCurrentView();
				
				try {
					editor.switchToPluginView(plugin);
				} catch (Exception e) {
				    e.printStackTrace();
					if (current instanceof Editor) {
						//TODO editor.getEditorButton().setSelected(true);
						//TODO editor.getEditorViewItem().setSelected(true);
//					} else if (current instanceof Browser) {
//						browserButton.setSelected(true);
//						getBrowserViewItem().setSelected(true);
					}
					MessageHandler.showMessage(	"Please ensure the document is well-formed\nbefore switching to the \"Grid\".");
					current.setFocus();
				}
				
			}
		}

	}
	/**
	 * @param editor the editor to set
	 */
	public void setDogsBayAIEditor(DogsBayAIEditor editor) {

		this.editor = editor;
	}
	/**
	 * @return the editor
	 */
	public DogsBayAIEditor getDogsBayAIEditor() {

		return editor;
	}

}
