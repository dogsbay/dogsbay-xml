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

package com.dogsbay.dogsbayaieditor.project;

import java.util.Enumeration;
import java.util.Vector;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.tree.DefaultTreeModel;

import org.bounce.image.ImageUtilities;

import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.DogsBayProgressDialog;

/**
 * The default node for an folder in a project.
 *
 * @version	$Revision: 1.2 $, $Date: 2005/09/05 09:08:29 $
 * @author Dogsbay
 */
public class FolderNode extends BaseNode {
	private static final boolean DEBUG = true;

	private static final ImageIcon ICON = DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/project/icons/FolderIcon.gif");
	private static final ImageIcon EXPANDED_ICON = DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/project/icons/SelectedFolderIcon.gif");
	private FolderProperties properties = null;
	private DefaultTreeModel model = null;
	
	/**
	 * The constructor for the folder node.
	 *
	 * @param properties the folder properties.
	 */
	public FolderNode( DefaultTreeModel model, FolderProperties properties, boolean isStartup) {
		this.properties = properties;
		this.model = model;
		
		update(isStartup);
		
	}
	
	public FolderProperties getProperties() {
		return properties;
	}
	
	public void update(boolean isStartup) {
		if ( getChildCount() > 0) {
			removeAllChildren();
		}

		if (DEBUG) System.out.println("FolderNode.update: Updating tree node for folder '" + properties.getName() + "' (isStartup=" + isStartup + ")");

		Vector documents = properties.getDocumentProperties();
		if (DEBUG) System.out.println("  Creating " + documents.size() + " DocumentNode children");
		for ( int i = 0; i < documents.size(); i++) {
			DocumentProperties doc = (DocumentProperties)documents.elementAt(i);
			if (DEBUG) System.out.println("    DocumentNode[" + i + "]: " + doc.getName());
			add( new DocumentNode( model, doc));
		}

		Vector folders = properties.getFolderProperties();
		if (DEBUG) System.out.println("  Creating " + folders.size() + " FolderNode children");
		for ( int i = 0; i < folders.size(); i++) {
			add( new FolderNode( model, (FolderProperties)folders.elementAt(i), isStartup));
		}

		Vector virtualFolders = properties.getVirtualFolderProperties();
		if (DEBUG) System.out.println("  Creating " + virtualFolders.size() + " VirtualFolderNode children");
		for ( int i = 0; i < virtualFolders.size(); i++) {
			add( new VirtualFolderNode( model, (VirtualFolderProperties)virtualFolders.elementAt(i), isStartup));
		}

		if (DEBUG) System.out.println("  Total children added to tree node: " + getChildCount());
	}

	public void parse() {
		Enumeration children = children();
		
		while ( children.hasMoreElements()) {
			BaseNode node = (BaseNode)children.nextElement();
			
			if ( node instanceof FolderNode) {
				((FolderNode)node).parse();
			} else if ( node instanceof DocumentNode) {
				((DocumentNode)node).parse();
			} else if ( node instanceof VirtualFolderNode) {
				((VirtualFolderNode)node).parse();
			}
			
		}
	}

	public void validate() {
		Enumeration children = children();
		
		while ( children.hasMoreElements()) {
			BaseNode node = (BaseNode)children.nextElement();
			
			if ( node instanceof FolderNode) {
				((FolderNode)node).validate();
			} else if ( node instanceof DocumentNode) {
				((DocumentNode)node).validate();
			} else if ( node instanceof VirtualFolderNode) {
				((VirtualFolderNode)node).validate();
			}
		}
	}

	public Vector getDocuments(DogsBayProgressDialog progressDialog) {
		Vector documents = new Vector();
		Enumeration children = children();
		
		while ( children.hasMoreElements()) {
			BaseNode node = (BaseNode)children.nextElement();
			
			if ( node instanceof FolderNode) {
				Vector docs = ((FolderNode)node).getDocuments();

				if(progressDialog.isCancelled() == true) {
					return(null);
				}
				
				for ( int i = 0; i < docs.size(); i++) {
					documents.addElement( docs.elementAt(i));
				}
			} else if ( node instanceof DocumentNode) {
				documents.addElement( node);
				
			} else if ( node instanceof VirtualFolderNode) {
				Vector docs = ((VirtualFolderNode)node).getDocuments();

				if(progressDialog.isCancelled() == true) {
					return(null);
				}
				
				for ( int i = 0; i < docs.size(); i++) {
					documents.addElement( docs.elementAt(i));
				}
			}
		}
		
		return documents;
	}
	
	public Vector getDocuments() {
		Vector documents = new Vector();
		Enumeration children = children();
		
		while ( children.hasMoreElements()) {
			BaseNode node = (BaseNode)children.nextElement();
			
			if ( node instanceof FolderNode) {
				Vector docs = ((FolderNode)node).getDocuments();

				for ( int i = 0; i < docs.size(); i++) {
					documents.addElement( docs.elementAt(i));
				}
			} else if ( node instanceof DocumentNode) {
				documents.addElement( node);
				
			} else if ( node instanceof VirtualFolderNode) {
				Vector docs = ((VirtualFolderNode)node).getDocuments();

				for ( int i = 0; i < docs.size(); i++) {
					documents.addElement( docs.elementAt(i));
				}
			}
		}
		
		return documents;
	}

	public FolderNode addFolder( FolderProperties props, boolean isStartup) {
		properties.addFolderProperties( props);
		
		FolderNode node = new FolderNode( model, props, isStartup);
		
		add( node);
		
		return node;
	}

	/**
	 * The name for this node.
	 *
	 * @return the name for the element.
	 */
	public String getName() {
		return properties.getName();
	}

	/**
	 * Sets the name for this node.
	 *
	 * @param name the new name.
	 */	
	public void setName( String name) {
		properties.setName( name);
	}

	/**
	 * Sets the user object for the folder.
	 *
	 * @param object the new name.
	 */	
	public void setUserObject( Object object) {
		setName( (String)object);
	}

	/**
	 * The description for this node.
	 *
	 * @return the description for the element.
	 */
	public String getDescription() {
		return properties.getName();
	}

	/**
	 * Returns the icon that is shown when the node is selected.
	 *
	 * @return the selected icon.
	 */
	public Icon getSelectedIcon() {
		return ImageUtilities.createDarkerImage( ICON);
	}

	/**
	 * Returns the icon that is shown when the node is expanded and selected.
	 *
	 * @return the selected expanded icon.
	 */
	public Icon getExpandedSelectedIcon() {
		return ImageUtilities.createDarkerImage( EXPANDED_ICON);
	}


	/**
	 * Returns the icon that is shown when the node is expanded.
	 *
	 * @return the expanded icon.
	 */
	public Icon getExpandedIcon() {
		return EXPANDED_ICON;
	}

	/**
	 * The icon for this node.
	 *
	 * @return the icon for the element.
	 */
	public Icon getIcon() {
		return ICON;
	}
	
	/**
	 * Returns a string version of this node.
	 *
	 * @return the name for the element.
	 */
	public String toString() {
		return getName();
	}
	
	/** 
	 * Adds the node to the parent at a sorted location.
	 *
	 * @param the node to be added.
	 */
	public void add( FolderNode node) {
		super.add( node);
	}

	/** 
	 * Adds the node to the parent at a sorted location.
	 *
	 * @param the node to be added.
	 */
	public int add( DocumentNode node) {
		int index = 0;
		
		for ( index = 0; index < getChildCount(); index++) {
			BaseNode n = (BaseNode)getChildAt( index);
			
			if ( n instanceof DocumentNode) {
				if ( node.compareTo( n) <= 0) {
					insert( node, index);
					return index;
				}
			} else {
				insert( node, index);
				return index;
			}
		}
		
		super.add( node);
		
		return index;
	}
	
} 
