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

import java.awt.LayoutManager;
import java.io.IOException;

import org.xml.sax.SAXParseException;

import com.dogsbay.schema.SchemaDocument;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XAttribute;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.XMLError;
import com.dogsbay.dogsbayaieditor.ViewPanel;


/**
 * 
 *
 * @version	$Revision: 1.0 $, $Date: 13 Mar 2007 16:05:25 $
 */
public abstract class PluginViewPanel extends ViewPanel {

	private PluginView pluginView = null;
	
	public PluginViewPanel(PluginView pluginView, LayoutManager layout) {
		super(layout);
		this.setPluginView(pluginView);
	}
	
	/**
	 * @return
	 */
	public abstract XElement getSelectedElement();

	public abstract void setSelectedElement(XElement element);
	public abstract void setSelectedElement(XAttribute attribute);
	
	public abstract void setSchema(SchemaDocument schema);
	public abstract void updateDocument();
	public abstract DogsBayDocument getDocument();
	public abstract void cleanup();
	
	public abstract void setFocus();
	
	/**
	 * @return
	 */
	public abstract boolean hasLatestInformation();
	/**
	 * @param document
	 */
	public abstract void setDocument(DogsBayDocument document);
	/**
	 * @param selectedElement
	 */
	public abstract void selectElement(XElement selectedElement);
	
	public abstract void updateHelper();
	
	public abstract void selectError(XMLError error);

	/**
	 * 
	 */
	public abstract void createRequired();

	/**
	 * 
	 */
	public abstract void parse() throws SAXParseException, IOException;

	/**
	 * @param name
	 */
	public abstract void addNewElementToSelected(String name);

	/**
	 * @param name
	 */
	public abstract void addNewAttributeToSelected(String name);

	/**
	 * @param name
	 */
	public abstract void selectAttribute(String name);

	/**
	 * @param attribute
	 * @param i
	 */
	public abstract void selectAttribute(XAttribute attribute, int i);

	/**
	 * 
	 */
	public abstract void collapseAll();
	public abstract void expandAll();

	/**
	 * 
	 */
	public abstract void copy() ;

	/**
	 * 
	 */
	public abstract void cut();

	/**
	 * 
	 */
	public abstract void paste();

	/**
	 * @return
	 */
	public abstract void saveState();

	/**
	 * 
	 */
	public abstract void returnToPreviousState();

	/**
	 * @param pluginView the pluginView to set
	 */
	public void setPluginView(PluginView pluginView) {

		this.pluginView = pluginView;
	}

	/**
	 * @return the pluginView
	 */
	public PluginView getPluginView() {

		return pluginView;
	}
}