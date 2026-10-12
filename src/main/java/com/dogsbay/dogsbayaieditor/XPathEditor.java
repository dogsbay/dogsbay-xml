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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Vector;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;

import org.dom4j.InvalidXPathException;
import org.dom4j.Node;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XAttribute;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.plugins.PluginViewPanel;
import com.dogsbay.xml.viewer.Viewer;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.dogsbay.dogsbayaieditor.properties.TextPreferences;

/**
 * The panel that shows the results for a XPath search.
 *
 * @version	$Revision: 1.15 $, $Date: 2005/04/29 12:25:54 $
 * @author Dogsbay
 */
public class XPathEditor extends JPanel {
	private static final ImageIcon PUSHPIN_ICON = DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Pushpin16.gif");
	private static final ImageIcon PUSHPIN_SELECTED_ICON = DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/PushpinIn16.gif");
	private static final ImageIcon PLAY_ICON = DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/Play16.gif");

	private Object view = null;

	private JComboBox xpath = null;
	private OutputPanel panel = null;
	private DogsBayDocument document = null;
	private JButton select = null;
	private JRadioButton autoUpdateButton = null;
	
	private ConfigurationProperties properties = null;
	private DogsBayAIEditor parent = null;

	/**
	 * The constructor for the about dialog.
	 *
	 * @param frame the parent frame.
	 */
	public XPathEditor( DogsBayAIEditor parent, OutputPanel panel, ConfigurationProperties props) {
		super( new BorderLayout( 0, 0));
		
		this.parent = parent;
		this.panel = panel;
		this.properties = props;
		
		xpath = new JComboBox();
		xpath.getEditor().getEditorComponent().addKeyListener( new KeyAdapter() {
			public void keyPressed( KeyEvent e) {
				if ( e.getKeyCode() == KeyEvent.VK_ENTER) {
					selectButtonPressed();
				}
			}
		});

		xpath.getEditor().getEditorComponent().addFocusListener( new FocusListener() {
			public void focusGained( FocusEvent e) {
				//if ( xpath.getEditor().getItem() != null) { 
				//	xpath.getEditor().selectAll();
				//}
				if ( xpath.getEditor().getItem() != null) { 
				  JTextComponent comp = (JTextComponent)xpath.getEditor().getEditorComponent();
					comp.setCaretPosition(0);
					comp.moveCaretPosition(((String)xpath.getEditor().getItem()).length());
				}
			}

			public void focusLost( FocusEvent e) {}
		});

		xpath.setFont( TextPreferences.getBaseFont().deriveFont( (float)12));
		xpath.setPreferredSize( new Dimension( 100, 19));
		xpath.setEditable( true);

		JLabel xpathLabel = new JLabel( "XPath:");
//		xpathLabel.setBorder( new EmptyBorder( 0, 0, 0, 5));
//		xpathLabel.setForeground( UIManager.getColor( "controlDkShadow"));
//		xpathLabel.setForeground( new Color( 102, 102, 102));
		
		autoUpdateButton = new JRadioButton( PUSHPIN_ICON);
		autoUpdateButton.setSelectedIcon( PUSHPIN_SELECTED_ICON);
		autoUpdateButton.setToolTipText( "Toggle automatic XPath updates");

		select = new JButton( PLAY_ICON);
		select.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e) {
				selectButtonPressed();
			}
		});
		select.setMargin( new Insets( 0, 2, 0, 2));
		select.setFocusPainted( false);
		select.setEnabled( false);
//		select.setOpaque( false);
		select.setToolTipText( "Execute current XPath");

//		JPanel buttonPanel = new JPanel( new FlowLayout( FlowLayout.RIGHT, 0, 0));
		JPanel buttonPanel = new JPanel( new BorderLayout());
		buttonPanel.add( autoUpdateButton, BorderLayout.CENTER);
		buttonPanel.add( xpathLabel, BorderLayout.WEST);

		setBorder( new EmptyBorder( 2, 5, 2, 15));
		
		JPanel selectPanel = new JPanel( new FlowLayout( FlowLayout.RIGHT, 0, 0));
		selectPanel.setBorder( new EmptyBorder( 3, 5, 3, 0));
		selectPanel.add( select);

		JPanel xpanel = new JPanel( new BorderLayout());
		xpanel.setBorder( new EmptyBorder( 3, 0, 3, 0));
		xpanel.add( xpath, BorderLayout.CENTER);

		add( buttonPanel, BorderLayout.WEST);
		add( xpanel, BorderLayout.CENTER);
		add( selectPanel, BorderLayout.EAST);
		
		setXPaths();
	}

	public void setDocument( DogsBayDocument doc) {
		this.document = doc;
		
		select.setEnabled( doc != null);
	}

	public void updatePreferences() {
		xpath.setFont( TextPreferences.getBaseFont().deriveFont( (float)12));
	}
	
//	public void setXPath( String path) {
//		if ( !autoUpdateButton.isSelected()) {
//			xpath.getEditor().setItem( path);
//		}
//	}

	public void setXPath( final XElement element) {
		SwingUtilities.invokeLater( new Runnable() {
			public void run() {
				if ( !autoUpdateButton.isSelected()) {
					if ( properties.isUniqueXPath()) {
						xpath.getEditor().setItem( element.getUniquePath());
					} else {
						xpath.getEditor().setItem( element.getPath());
					}
				}
			}
		});
	}

	public String getXPath() {
		return (String)xpath.getEditor().getItem();
	}

	public void setXPath( final XAttribute attribute) {
		SwingUtilities.invokeLater( new Runnable() {
			public void run() {
				if ( !autoUpdateButton.isSelected()) {
					if ( properties.isUniqueXPath()) {
						xpath.getEditor().setItem( attribute.getUniquePath());
					} else {
						xpath.getEditor().setItem( attribute.getPath());
					}
				}
			}
		});
	}

	public void selectButtonPressed() {
		if ( document != null) {
			final String search = (String)xpath.getEditor().getItem();
	
			parent.setWait( true);
			parent.setStatus( "Evaluating XPath expression \""+search+"\" ...");
	
			// Run in Thread!!!
			Runnable runner = new Runnable() {
				public void run()  {
					try	{
						parent.getView().updateModel();
						
						if ( !document.isError()) {
							final Vector nodes = document.search( search);
							
					 		SwingUtilities.invokeLater( new Runnable(){
								public void run() {
									updateView( search, nodes);
								}
					 		});
						} else {
							MessageHandler.showError( "Could not evaluate XPath expression \""+search+"\".\nPlease make sure the Document is wellformed.", "XPath Error");
						}
					} catch ( InvalidXPathException e) {
						MessageHandler.showError( e, "XPath Error");
					} catch ( Exception e) {
						MessageHandler.showError( "Invalid XPath expression: \""+search+"\".", "XPath Error");
					} finally {
						parent.setStatus( "Done");
						parent.setWait( false);
	
						parent.getView().getCurrentView().setFocus();
					}
				}
			};
			
			// Create and start the thread ...
			Thread thread = new Thread( runner);
			thread.start();
		}
	}
	
	private void updateView( String search, Vector nodes) {
		try {
			if ( document != null && !document.isError()) {
				view = parent.getView().getCurrentView();
				
				if ( view instanceof Viewer) {
					if ( nodes.size() > 0) {
						Object node = nodes.elementAt(0);
						
						if ( node instanceof XElement) {
							((Viewer)view).setSelectedElement( (XElement)node, false, -1);
						} else if ( node instanceof XAttribute) {
							((Viewer)view).setSelectedElement( (XElement)((XAttribute)node).getParent(), false, -1);
						} else if ( node instanceof Node) {
							((Viewer)view).setSelectedElement( (XElement)((Node)node).getParent(), false, -1);
						}
					}
				} 
				
				
				properties.addXPathSearch( search);
				
				int size = nodes.size();
				
				if ( size > 0) {
					nodes.insertElementAt( "XPath results for \""+search+"\".", 0);
				} else {
					nodes.addElement( "No XPath results found for \""+search+"\".");
				}
				
				panel.setXPathResults( nodes);
				panel.selectXPathTab();

				setXPaths();
			}
		} catch ( Throwable t) {
			MessageHandler.showError( t, "XPath Error");
			
		}
	}

	private void setXPaths() {
		if ( xpath.getItemCount() > 0) {
			xpath.removeAllItems();
		}
		
		Vector xpaths = properties.getXPathSearches();
		
		for ( int i = 0; i < xpaths.size(); i++) {
			xpath.addItem( xpaths.elementAt(i));
		}
		
		if ( xpath.getItemCount() > 0) {
			xpath.setSelectedIndex( 0);
		}
	}
} 
