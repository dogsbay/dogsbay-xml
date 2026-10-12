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

package com.dogsbay.xml.viewer;

import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;
import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import org.dom4j.ProcessingInstruction;
import org.dom4j.tree.DefaultDocument;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.DogsBayDocumentEvent;
import com.dogsbay.xml.DogsBayDocumentListener;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.ViewPanel;
import com.dogsbay.dogsbayaieditor.ViewTreePanel;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.properties.TextPreferences;

/**
 * Shows a tree view of a XML document.
 *
 * @version	$Revision: 1.11 $, $Date: 2004/11/05 11:44:52 $
 * @author Dogsbay
 */
public class Viewer extends ViewTreePanel implements DogsBayDocumentListener {
	private static final boolean DEBUG = false;
	private static final ImageIcon ICON = DogsBayImageLoader.get().getImage( "com/dogsbay/xml/viewer/icons/ViewerIcon.gif");
	private XmlTree tree = null;
	private JScrollPane scrollPane = null;

	private ComponentAdapter resizeListener = null;
	private JLabel universalNameLabel = null;
	private JLabel namespacesLabel = null;
	private JLabel valuesLabel = null;
	private JLabel attributesLabel = null;
	private JLabel commentsLabel = null;
	
	private DogsBayDocument document	= null;
	private ViewerProperties properties = null;
	private DogsBayAIEditor editor = null;
	private DogsBayView view = null;
	
	private boolean showNamespaces	= false;
	private boolean showAttributes	= false;
	private boolean showValues 		= false;
	private boolean showComments	= false;
	private boolean showInline		= false;
	private boolean showPI			= false;
	

	private boolean hasLatest = false;
	public static final String DOGSBAY_DUMMY_ROOT = "dogsbay_dummy_element";


	/**
	 * Constructs an explorer view with the ExplorerProperties supplied.
	 *
	 * @param properties the explorer properties.
	 */
	public Viewer( DogsBayAIEditor parent, ViewerProperties props, DogsBayView _view) {
		super( new BorderLayout());
		
		this.properties = props;
		this.editor = parent;
		this.view = _view;

		try {
			tree = new XmlTree();
		} catch (Exception e) {
			e.printStackTrace();
			// should not happen
		}
		
		tree.addTreeSelectionListener( new TreeSelectionListener() {
			public void valueChanged( TreeSelectionEvent e) {
				TreePath path = tree.getSelectionPath();
				if ( path != null) {
					XmlElementNode node = (XmlElementNode) path.getLastPathComponent();
					XElement element = node.getElement();

					if ( element != null && tree != null) {
//						universalNameLabel.setText( node.getElement().getUniversalName());
						editor.getNavigator().setSelectedElement( element);
						editor.getHelper().setElement( element);

						Rectangle bounds = tree.getPathBounds( path);

						if ( bounds != null) {
							int y = bounds.y;
							y = y - scrollPane.getViewport().getViewRect().y;

							// If this is a sidebar viewer (view == null), sync with current editor
							if (view == null) {
								ViewPanel current = editor.getCurrent();
								if (current instanceof Editor) {
									((Editor)current).selectElement(element, node.isEndTag(), y);
								}
							} else {
								editor.synchronise( view, element, node.isEndTag(), y);
							}
						}
					}
//					} else {
//						universalNameLabel.setText( "");
//						xpath.getEditor().setItem( null);
//					}
//				} else {
//					universalNameLabel.setText( null);
				}
			}
		});
		
		scrollPane = new JScrollPane(	tree,
										JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
										JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

		/**
		 * Work around to make sure the scroll pane shows the vertical 
		 * scrollbar for the first time when resized to a size small enough.
		 * JDK 1.3.0-C 
		 *
		 * Got work around from Bug ID: 4243631 (It should be fixed...)
		 *
		 * ED: Check with JDK1.4
		 */
		resizeListener = new ComponentAdapter() {
			public void componentResized( ComponentEvent e) {
				scrollPane.doLayout();
			}
		};

		scrollPane.getViewport().addComponentListener( resizeListener);

		this.setBorder( new EmptyBorder( 0, 0, 0, 0));
		this.add( scrollPane, BorderLayout.CENTER);

		showAttributes = properties.isShowAttributes();
		showNamespaces = properties.isShowNamespaces();
		showValues = properties.isShowValues();
		showComments = properties.isShowComments();
		showInline = properties.isShowInline();
		showPI = properties.isShowPI();

		((XmlCellRenderer)tree.getCellRenderer()).setFont( TextPreferences.getBaseFont());
		
		tree.addFocusListener( new FocusListener() {
			public void focusGained( FocusEvent e) {
				if (view != null) {
					view.setFocussed();
				}
			}

			public void focusLost( FocusEvent e) {}
		});
	}
	
	public void updateHelper() {
		TreePath path = tree.getSelectionPath();
	
		if ( path != null) {
			XmlElementNode node = (XmlElementNode)path.getLastPathComponent();
			XElement element = node.getElement();
			
			if ( element != null) {
				editor.getHelper().setElement( node.getElement());
			}
		}
	}
	
	/**
	 * Check to find out if namespaces should be visible.
	 *
	 * @return true if namespaces are visible.
	 */
	public XElement getSelectedElement() {
		return tree.getSelectedElement();
	}

	public void setFocus() {
		if ( !tree.hasFocus()) {
			tree.requestFocusInWindow();
		}
	}

	/**
	 * Update the preferences.
	 */
	public void updatePreferences() {
		((XmlCellRenderer)tree.getCellRenderer()).setFont( TextPreferences.getBaseFont());
		
		if ( propertiesUpdated()) {
			update();
		} else {
			tree.updateUI();
		}
	}

	/**
	 * Adds an element to the list of selected elements.
	 *
	 * @param element the element to select.
	 */
	public void addSelectedElement( XElement element) {
		tree.setSelectedNode( element, false);
	}

	/**
	 * Clears the current selection.
	 */
	public void clearSelection() {
		tree.clearSelection();
	}

	public void setProperties() {
	}

	/**
	 * Check to find out if namespaces should be visible.
	 *
	 * @return true if namespaces are visible.
	 */
	public void setSelectedElement( XElement element, boolean end, int y) {
		tree.clearSelection();

		if ( element != null) {
			tree.setSelectedNode( element, end);

			if ( y != -1 && tree.getSelectionPath() != null) {
				Rectangle r = tree.getVisibleRect();
				Rectangle sr = tree.getPathBounds( tree.getSelectionPath());
				sr.height = r.height;
				sr.y = sr.y - y;
	
				if ( r.height > y && sr.y >= 0) {
					tree.scrollRectToVisible( sr);

					tree.revalidate();
					tree.repaint();
				}
			}
		}
	}

	//Check to see it the properties have been updated.
	private boolean propertiesUpdated() {
		if ( (properties.isShowAttributes() == showAttributes) &&
			 (properties.isShowNamespaces() == showNamespaces) &&
			 (properties.isShowValues() == showValues) &&
			 (properties.isShowComments() == showComments) && 
			 (properties.isShowInline() == showInline) &&
			 (properties.isShowPI() == showPI))
			 {
			 return false;
		}

		showAttributes = properties.isShowAttributes();
		showNamespaces = properties.isShowNamespaces();
		showValues = properties.isShowValues();
		showComments = properties.isShowComments();
		showInline = properties.isShowInline();
		showPI = properties.isShowPI();
		
		return true;
	}

	/**
	 * Check to find out if namespaces should be visible.
	 *
	 * @return true if namespaces are visible.
	 */
	public boolean showNamespaces() {
		boolean result = properties.isShowNamespaces();
		if (DEBUG) System.out.println( "Viewer.showNamespaces() ["+result+"]");
		return result;
	}

	/**
	 * Check to find out if attributes should be visible.
	 *
	 * @return true if attributes are visible.
	 */
	public boolean showAttributes() {
		boolean result = properties.isShowAttributes();
		if (DEBUG) System.out.println( "Viewer.showAttributes() ["+result+"]");
		return result;
	}

	/**
	 * Check to find out if comments should be visible.
	 *
	 * @return true if comments are visible.
	 */
	public boolean showComments() {
		boolean result = properties.isShowComments();
		if (DEBUG) System.out.println( "Viewer.showComments() ["+result+"]");
		return result;
	}
	
	/**
	 * Check to find out if PIs should be visible.
	 *
	 * @return true if PIs are visible.
	 */
	public boolean showPI() {
		boolean result = properties.isShowPI();
		return result;
	}
	
	/**
	 * Check to find out if CDATAs should be visible.
	 *
	 * @return true if CDATA sections are visible.
	 */
	public boolean showCDATA() {
		boolean result = properties.isShowInline();
		return result;
	}
	
	/**
	 * Check to find out if mixed should be shown inline
	 *
	 * @return true if CDATA sections are visible.
	 */
	public boolean showInline() {
		boolean result = properties.isShowInline();
		return result;
	}
	

	private void update() {
		if ( tree != null && document != null) {
			if (showPI())
			{
				XElement dummyElement = new XElement(DOGSBAY_DUMMY_ROOT);
				DefaultDocument doc = (DefaultDocument)document.getDocument();
				List pis = doc.processingInstructions();
				
				Vector allPIs = new Vector();;
				for (int i=0;i<pis.size();i++)
				{
					ProcessingInstruction pi = (ProcessingInstruction)pis.get(i);
					if (!addedPI(allPIs,pi.getTarget()+pi.getText()))
					{
						dummyElement.addProcessingInstruction(pi.getTarget(),pi.getText());
						allPIs.add(pi.getTarget()+pi.getText());
					}
				}
				
				XmlElementNode dummyRoot = new XmlElementNode( this, dummyElement);	
				XmlElementNode node = new XmlElementNode( this, (XElement)document.getRoot());
				dummyRoot.add(node);
				tree.setRoot(dummyRoot);
				tree.expand(4);
				
				tree.setRootVisible(false);
			}
			else
			{
				XmlElementNode node = new XmlElementNode( this, (XElement)document.getRoot());
				tree.setRoot( node);
				tree.expand( 3);
				
				tree.setRootVisible(true);
			}
		}
	}

	// to avoid dom4j bug where the processing instructions are returned twice from the document
	private boolean addedPI(Vector allPIs,String newPI)
	{
		boolean found = false;
		for (int i=0;i<allPIs.size();i++)
		{
			String oldPI = (String)allPIs.get(i);
			if (oldPI.equals(newPI))
			{
				found = true;
			}
		}
		return found;
	}
	
	/**
	 * Check to find out if values should be visible.
	 *
	 * @return true if values are visible.
	 */
	public boolean showValues() {
		boolean result = properties.isShowValues();
		if (DEBUG) System.out.println( "Viewer.showValues() ["+result+"]");
		return result;
	}

	/**
	 * Collapses all the nodes in the tree.
	 */
	public void collapseAll() {
		tree.collapseAll();
		
		if (showPI())
			tree.expand(2);
		else
			tree.expand(1);
	}

	/**
	 * Collapses all the nodes in the tree.
	 *
	 * @param node the node to collapse all nodes for.
	 */
	public void collapseNode( XmlElementNode node) {
		tree.collapseNode( node);
	}

	/**
	 * Expands all the nodes in the tree.
	 */
	public void expandAll() {
		tree.expandAll();
	}

	/**
	 * Expands all the nodes in the tree from this node down.
	 *
	 * @param node the node to expand all nodes for.
	 */
	public void expandNode( XmlElementNode node) {
		tree.expandNode( node);
	}

	/**
	 * Returns the icon
	 */
	public ImageIcon getIcon() {
		return ICON;
	}

	public void setDocument( DogsBayDocument document) {
		if (DEBUG) System.out.println( "Viewer.setDocument( "+document+")");

		if ( this.document != null) {
			this.document.removeListener( this);
		}
		
		this.document = document;

		if ( document != null) {
			document.addListener( this);
		}

		if ( document != null && !document.isError()) {


			if (showPI())
			{
				XElement dummyElement = new XElement(DOGSBAY_DUMMY_ROOT);
				DefaultDocument doc = (DefaultDocument)document.getDocument();
				if (doc == null) return;
				List pis = doc.processingInstructions();
				for (int i=0;i<pis.size();i++)
				{
					ProcessingInstruction pi = (ProcessingInstruction)pis.get(i);
					dummyElement.addProcessingInstruction(pi.getTarget(),pi.getText());
				}
				
				XmlElementNode dummyRoot = new XmlElementNode( this, dummyElement);	
				XmlElementNode node = new XmlElementNode( this, (XElement)document.getRoot());
				dummyRoot.add(node);
				tree.setRoot(dummyRoot);
				tree.expand(4);
				
				tree.setRootVisible(false);
			}
			else
			{
				XmlElementNode node = new XmlElementNode( this, (XElement)document.getRoot());
				tree.setRoot( node);
				tree.expand( 3);
				
				tree.setRootVisible(true);
			}
			
			
		} else {
			tree.setRoot( null);
		}

		hasLatest = true;
	}
	
	public boolean hasLatestInformation() {
		return hasLatest;
	}

// Implementation of the XDocumentListener interface...	
	public void documentUpdated( DogsBayDocumentEvent event) {
		if (DEBUG) System.out.println( "Viewer.documentUpdated( "+event+")");

		hasLatest = false;
	}
	
	public void documentDeleted( DogsBayDocumentEvent event) {}
	
	public void cleanup() {
		tree.cleanup();
		
		scrollPane.getViewport().removeComponentListener( resizeListener);

		removeAll();

		finalize();
	}
	
	protected void finalize() {
		tree = null;
		scrollPane = null;

		universalNameLabel = null;
		namespacesLabel = null;
		valuesLabel = null;
		attributesLabel = null;
		commentsLabel = null;
		
		document = null;
		properties = null;
		editor = null;
	}
} 
