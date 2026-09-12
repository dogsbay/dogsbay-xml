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

package com.dogsbay.xml.properties;

import java.awt.Color;
import java.io.File;
import java.net.URL;

import org.dom4j.Namespace;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.XMLUtilities;
import com.dogsbay.dogsbayaieditor.Identity;
import com.dogsbay.dogsbayaieditor.Main;


/**
 * This file allows the original properties to be broken up into 
 * individual files, each one with their own document which can be 
 * saved or updated as needed.
 * 
 *
 */
public class PropertiesFile extends Properties {
	
	private static final boolean DEBUG = false;
	private DogsBayDocument document	= null;	
	private boolean dirty = false;
	
	public PropertiesFile(String fileName, String rootName) {
		this(loadPropertiesFile(fileName, rootName));
	}
	
	public PropertiesFile(DogsBayDocument document) {

		super(document.getRoot());
		this.setDocument(document);
	}
	
	public PropertiesFile(DogsBayDocument document, XElement element) {

		super(element);
		this.setDocument(document);
	}
	
	public void save() {
		
		this.update();
	}
	
	public void saveToDisk() {
		try {
			if(isDirty() == true) {
				if(DEBUG) System.out.println(getDocument().getName() +" is dirty");
				//setDefaultNamespace( getDocument().getRoot());
				
				XMLUtilities.write( getDocument().getDocument(), getDocument().getURL());
				this.setDirty(false);
			}
			else {
				if(DEBUG) System.out.println(getDocument().getName() +" is not dirty");
			}
					
		} catch( Exception e) {
			e.printStackTrace();
		}
	}
	
	private void setDefaultNamespace( XElement element) {
		XElement[] elements = element.getElements();
		
		for ( int i = 0; i < elements.length; i++) {
			setDefaultNamespace( elements[i]);
		}
		
		//element.setNamespace( Namespace.get( "http://www.dogsbay.ai/dogsbay-editor/"+Identity.getIdentity().getVersion()+"/"));
	}
	
	@Override
	public void set(String name, boolean value) {
		// TODO Auto-generated method stub
		super.set(name, value);
		this.setDirty(true);
	}
	
	@Override
	public void set(String name, Color value) {
		// TODO Auto-generated method stub
		super.set(name, value);
		this.setDirty(true);
	}
	
	@Override
	public void set(String name, int value) {
		// TODO Auto-generated method stub
		super.set(name, value);
		this.setDirty(true);
	}
	
	@Override
	public void set(String name, long value) {
		// TODO Auto-generated method stub
		super.set(name, value);
		this.setDirty(true);
	}
	
	@Override
	public void set(String name, String value) {
		// TODO Auto-generated method stub
		super.set(name, value);
		this.setDirty(true);
		if(DEBUG) System.out.println("Set flag to dirty for "+name+"- value: "+value);
	}
	

	public void setDirty(boolean dirty) {
		this.dirty = dirty;
	}

	public boolean isDirty() {
		return dirty;
	}
	
	
	public static DogsBayDocument loadPropertiesFile(String fileName, String rootName) {
		
		DogsBayDocument document = null;
		boolean firstTime = false;

		File dir = new File( Main.DOGSBAY_HOME);

		if ( !dir.exists()) {
			dir.mkdir();
		}
		
		// fileName is a section, such as "key-mappings": the version used to be
		// glued onto it, so a release that bumped the version found no file and
		// silently started this section from defaults. SettingsFile adopts the
		// previous name instead.
		File file = com.dogsbay.dogsbayaieditor.properties.SettingsFile
				.locate( dir.toPath(), fileName).toFile();
		URL url = null;

		try {
			url = com.dogsbay.xml.DogsBayURLUtilities.getURLFromFile(file); // MalformedURLException
		} catch( Exception e) {
			// Should never happen, am not sure what to do in this case...
			e.printStackTrace();
		}
		
		firstTime = true;
		if ( file.exists()) {
			try {
				document = new DogsBayDocument( url);
				document.loadWithoutSubstitution();
				firstTime = false;
			} catch (Exception e) {
				// should not happen, document should always be valid...
				e.printStackTrace();
				return null;
			}
		}
		
		XElement root = null;
		//String namespaceURI = null;
		
		if(firstTime == true) {
			//root = new XElement( rootName, "http://www.dogsbay.ai/dogsbay-editor/"+Identity.getIdentity().getVersion()+"/");
			root = new XElement( rootName);
			root.setText( "\n");
			document = new DogsBayDocument( url, root);
					
			//namespaceURI = root.getNamespaceURI();
		}
		else {
			root = document.getRoot();
			//namespaceURI = root.getNamespaceURI();
		}
		com.dogsbay.dogsbayaieditor.properties.SettingsFile.adopt( file.toPath(), root);
		
		return(document);
		
	}

	public void setDocument(DogsBayDocument document) {
		this.document = document;
	}

	public DogsBayDocument getDocument() {
		return document;
	}
	
}
