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

package com.dogsbay.xml.navigator;

import java.util.Enumeration;
import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.tree.DefaultMutableTreeNode;

import org.dom4j.Namespace;
import org.dom4j.Node;
import org.dom4j.Text;

import com.dogsbay.xml.XAttribute;
import com.dogsbay.xml.XElement;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;

/**
 * The node for the XML tree, containing an XML element.
 *
 * @version	$Revision: 1.8 $, $Date: 2004/10/26 16:04:21 $
 * @author Dogsbay
 */
public class XmlElementNode extends DefaultMutableTreeNode {
	private static final boolean DEBUG = false;
	
	private String name = null;
	private String value = null;
	private String description = null;
	private ImageIcon icon = null;
	private Vector attributes = null;

	//	private static final int MAX_LINE_LENGTH = 50;
	
	public static final ImageIcon[] ELEMENT_ICONS = 
		{ 
			DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/NavigatorElementIcon1.gif"),
			DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/NavigatorElementIcon2.gif"),
			DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/NavigatorElementIcon3.gif"),
			DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/NavigatorElementIcon4.gif"),
			DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/NavigatorElementIcon5.gif"),
			DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/NavigatorElementIcon6.gif"),
			DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/NavigatorElementIcon7.gif"),
			DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/NavigatorElementIcon8.gif"),
			DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/NavigatorElementIcon9.gif"),
			DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/NavigatorElementIcon10.gif")
		};

	private static final int MAX_ATTRIBUTE_VALUE = 12;
	private XElement element = null;
	private Navigator navigator = null;
	private Vector nodes = null;
	private Vector namespaces = null;
	
	public XmlElementNode( Navigator navigator, XElement parent, Vector nodes, Vector shrinkingNodes, Vector namespaces, boolean root, boolean all) {
		if (DEBUG) System.out.println( "[root] XmlElementNode( "+parent.getName()+")");

		this.navigator = navigator;
		this.nodes = nodes;
		this.namespaces = namespaces;
		
		if ( nodes != null) {
			if ( all || contains( shrinkingNodes, parent)) {
				add( new XmlElementNode( navigator, parent, nodes, shrinkingNodes, namespaces, all));
			} else {
				if ( navigator.showAttributes()) {
					XAttribute[] attributes = parent.getAttributes();
					
					for ( int i = 0; i < attributes.length; i++) {
						if ( contains( shrinkingNodes, attributes[i])) {
							add( new XmlElementNode( navigator, parent, nodes, shrinkingNodes, namespaces, all));
							return;
						}
					}
				}
	
				findChildren( parent, shrinkingNodes, all);
			}
		}
	}

	/**
	 * Constructs the node for the XML element.
	 *
	 * @param element the XML element.
	 */	
	public XmlElementNode( Navigator navigator, XElement element, Vector nodes, Vector shrinkingNodes, Vector namespaces, boolean all) {
		if (DEBUG) System.out.println( "XmlElementNode( "+element.getName()+") ["+element.getTextTrim()+"]");
		this.navigator = navigator;
		this.nodes = nodes;
		this.namespaces = namespaces;
		this.element = element;
		
		findChildren( element, shrinkingNodes, all);
	}
	
	private void findChildren( XElement parent, Vector nodes, boolean all) {
		if (DEBUG) System.out.println( "XmlElementNode.findChildren( "+parent.getName()+") ["+parent.getTextTrim()+"]");

		if ( parent != null) {
			if ( this.element != parent && navigator.showAttributes()) {
				XAttribute[] attributes = parent.getAttributes();
				
				for ( int i = 0; i < attributes.length; i++) {
					if ( contains( nodes, attributes[i])) {
						add( new XmlElementNode( navigator, parent, this.nodes, nodes, namespaces, all));
						return;
					}
				}
			}

			for ( int i = 0; i < parent.nodeCount(); i++) {
				Node node = parent.node( i);
				XElement element = null;
				
				if ( node instanceof XElement) {
					element = (XElement)node;
				} else {
					element = (XElement)node.getParent();
				}
				
				if ( this.element != element && element != null && !contains( element))	{
					if ( all || contains( nodes, node)) {
						add( new XmlElementNode( navigator, element, this.nodes, nodes, namespaces, all));

						if ( element == parent) {
							return;
						}
					} else if ( element != parent) {
						findChildren( element, nodes, all);
					}
				}
			}
		}
	}
	
	private boolean contains( Vector nodes, Node node) {
		for ( int i = 0; i < nodes.size(); i++) {
			if ( nodes.elementAt(i) == node) {
				nodes.removeElementAt(i);
				return true;
			}
		}
		
		return false;
	}
	
	private boolean contains( XElement element) {
		Enumeration e = children();
		
		while ( e.hasMoreElements()) {
			XmlElementNode node = (XmlElementNode)e.nextElement();
			
			if ( node.getElement() == element) {
				return true;
			}
		}
		
		return false;
	}

	
	public void update() {
		for ( int i = 0; i < getChildCount(); i++) {
			XmlElementNode node = (XmlElementNode)getChildAt( i);
			node.update();
		}
	}
	
	/**
	 * Returns the viewer.
	 *
	 * @return the viewer.
	 */	
	public Navigator getNavigator() {
		return navigator;
	}

	public XElement getElement() {
		return element;
	}

	/**
	 * Constructs the node for the XML element.
	 *
	 * @param element the XML element.
	 */	
	public String getName() {
		if ( name == null) {
			name = element != null ? element.getName() : null;
		}
			
		return name;
	}
	
	public String getValue() {
		if ( value == null) {
			value = "";
			
			if ( element != null) {
				for ( int i = 0; i < element.nodeCount(); i++) {
					Node node = element.node( i);
					
					if ( navigator.hasElementContentInResults()) {
						if ( navigator.showElementContent() && (nodes.contains( node) && (node instanceof Text))) {
							value = value + node.getText();
						}
					} else if ( navigator.showElementContent() && node instanceof Text) {
						value = value + node.getText();
					}
				}
			}
		}

		return value;
	}
	
	public String getDescription() {
		if ( description == null) {
			StringBuffer buffer = new StringBuffer( "<html><table cellspacing='10'><tr><td>");
	
			if ( element != null) {
				buffer.append( "<b>"+element.getQualifiedName()+"</b> ");
				
				String value = getValue( element.getValue());
				if ( value != null && value.length() > 0) {
					buffer.append( "["+value+"]");
				}
			
				XAttribute[] attributes = element.getAttributes();
				
				for ( int i = 0; i < attributes.length; i++) {
					buffer.append( "<br>"+attributes[i].getQualifiedName()+"=\""+getValue( attributes[i].getValue())+"\"");
				}
				
				buffer.append( "</td></tr></table></html>");
			}
			
			description = buffer.toString();
		}

		return description;
	}
	
	private String getValue( String value) {
		if ( value != null) {
			value = value.trim();
			
			if ( value.length() > 25) {
				value = value.substring( 0, 22)+"...";
			}
		}
		
		return value;
		
	}

	public Vector getAttributes() {
		if ( attributes == null) {
			attributes = new Vector();
	
			if ( element != null) {
				XAttribute[] attribs = element.getAttributes();
				
				for ( int i = 0; i < attribs.length; i++) {
					if ( navigator.hasAttributesInResults()) {
						if ( navigator.showAttributes() && nodes.contains( attribs[i])) {
							attributes.add( attribs[i]);
						}
					} else if ( navigator.showAttributes()) {
						attributes.add( attribs[i]);
					}
				}
			}
		}

		return attributes;
	}
	
	public ImageIcon getIcon() {
		if ( icon == null) {
			icon = ELEMENT_ICONS[0];
			
			if ( element != null) {
				Namespace namespace = element.getNamespace();
	
				if ( namespace != null)	{
					for ( int i = 0; i < namespaces.size(); i++)	{
						if ( namespace == namespaces.elementAt(i))	{
							icon = ELEMENT_ICONS[ (i+1)%10];
							break;
						}
					}
				}
			}
		}
		
		return icon;
	}

} 
