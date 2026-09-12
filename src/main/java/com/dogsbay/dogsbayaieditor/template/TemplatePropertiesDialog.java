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

package com.dogsbay.dogsbayaieditor.template;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Vector;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import org.bounce.FormLayout;

import com.dogsbay.xml.DogsBayURLUtilities;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.FileUtilities;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.DogsBayDialog;
import com.dogsbay.dogsbayaieditor.grammar.GrammarProperties;

/**
 * The grammar properties selection dialog.
 *
 * @version	$Revision: 1.4 $, $Date: 2004/11/04 19:21:50 $
 * @author Dogsbay
 */
public class TemplatePropertiesDialog extends DogsBayDialog {
	private static final boolean DEBUG = false;
	private static final Dimension SIZE = new Dimension( 400, 250);

	private DogsBayAIEditor parent = null;

	private JFileChooser chooser = null;
	private File file			 = null;

	private JTextField nameField = null;

	private JTextField urlField = null;
	private JButton fileSelectionButton = null;

	private Vector names = null;
	private boolean save = false;

	/**
	 * The dialog that displays the list of grammar properties.
	 *
	 * @param frame the parent frame.
	 * @param props the configuration properties.
	 */
	public TemplatePropertiesDialog( DogsBayAIEditor parent, boolean save) {
		super( parent, true);

		this.parent = parent;

		setResizable( true);
		
		if ( save) {
			setTitle( "Save as Template");
			setDialogDescription( "Specify the Template settings.");
		} else {
			setTitle( "Template Properties");
			setDialogDescription( "Specify the Template settings.");
		}
		
		JPanel formPanel = new JPanel( new FormLayout( 10, 2));
		formPanel.setBorder( new CompoundBorder( 
									new TitledBorder( "Template"),
									new EmptyBorder( 0, 5, 5, 5)));
		
		ButtonGroup group = new ButtonGroup();

		// default
		nameField = new JTextField();
		formPanel.add( new JLabel( "Name:"), FormLayout.LEFT);
		formPanel.add( nameField, FormLayout.RIGHT_FILL);
		
		urlField = new JTextField();
		
		JLabel urlLabel = new JLabel( "URL:");

		fileSelectionButton = new JButton( "...");

		fileSelectionButton.setMargin( new Insets( 0, 10, 0, 10));
		fileSelectionButton.setPreferredSize( new Dimension( fileSelectionButton.getPreferredSize().width, urlField.getPreferredSize().height));
		fileSelectionButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e) {
				fileSelectionButtonPressed();
			}
		});

		JPanel urlInputPanel = new JPanel( new BorderLayout());
		urlInputPanel.add( urlField, BorderLayout.CENTER);
		urlInputPanel.add( fileSelectionButton, BorderLayout.EAST);

		formPanel.add( urlLabel, FormLayout.LEFT);
		formPanel.add( urlInputPanel, FormLayout.RIGHT_FILL);

		JPanel mainPanel = new JPanel( new BorderLayout());
		mainPanel.setBorder( new EmptyBorder( 5, 5, 5, 5));
		mainPanel.add( formPanel, BorderLayout.CENTER);

		this.setContentPane( mainPanel);
		pack();

		setSize( new Dimension( Math.max( SIZE.width, getSize().width), Math.max( SIZE.height, this.getPreferredSize().height)));
		setLocationRelativeTo( parent);
	}
	
	protected void okButtonPressed() {
		if ( checkName( nameField.getText()) && checkURL( urlField.getText())) {
			super.okButtonPressed();
		}
	}
	
	private JFileChooser getFileChooser() {
		if ( chooser == null) {
			chooser = FileUtilities.getFileChooser();
		} 
		
		if ( file != null) {
			chooser.setCurrentDirectory( file);
		}
		
		return chooser;
	}

	private void fileSelectionButtonPressed() {
		if ( save) {
			GrammarProperties type = parent.getGrammar();
			File file = null;
	
			if ( type != null) {
				file = FileUtilities.selectOutputFile( (File)null, FileUtilities.getExtension( type));
			} else {
				file = FileUtilities.selectOutputFile( (File)null, "xml");
			}
			
			if ( file != null) {
				try {
					urlField.setText( DogsBayURLUtilities.getURLFromFile(file).toString());
					urlField.setCaretPosition( 0);
				} catch ( Exception e) {}
			}
		} else {
			JFileChooser chooser = getFileChooser();
			int value = chooser.showOpenDialog( parent);
			file = chooser.getSelectedFile();

			if ( value == JFileChooser.APPROVE_OPTION) {
				URL url = null;

				try {
					url = com.dogsbay.xml.DogsBayURLUtilities.getURLFromFile(file);
				} catch ( MalformedURLException x) {
					x.printStackTrace(); // should never happen
				}
				
				if ( file != null) {
					try {
						urlField.setText( DogsBayURLUtilities.getURLFromFile(file).toString());
						urlField.setCaretPosition( 0);
					} catch ( Exception e) {}
				}
			}
			
		}
	}

	/**
	 * Set the properties.
	 *
	 * @param properties the list of grammar properties.
	 */
	public void show( String name, URL url, Vector names) {
		nameField.setText( name);
		nameField.setCaretPosition( 0);
		
		if ( url != null) {
			urlField.setText( url.toString());
			urlField.setCaretPosition( 0);
		} else {
			urlField.setText( "");
		}
		
		this.names = names;
		
		super.setVisible(true);
	}

	/**
	 * Returns the url.
	 *
	 * @return the url.
	 */
	public URL getURL() {
		URL url = null;
		try {
			url = new URL( urlField.getText());
		} catch (Exception e) {
		}

		return url;
	}
	
	/**
	 * Returns the name.
	 *
	 * @return the name.
	 */
	public String getName() {
		return nameField.getText();
	}

	protected boolean isEmpty( String string) {
		if ( string != null && string.trim().length() > 0) {
			return false;
		}
		
		return true;
	}

	private boolean checkName( String name) {
		if ( isEmpty( name)) {
			MessageHandler.showMessage( "Please specify a Name for this Template.");
			return false;
		}
		
		for ( int i = 0; i < names.size(); i++) {
			if ( ((String)names.elementAt(i)).equalsIgnoreCase( name)) {
				MessageHandler.showMessage( "A Template with the name \""+name+"\" exists already.\n"+
											"Please specify another name.");
				return false;
			}
		}
		
		return true;
	}

	private boolean checkURL( String url) {
		try {
			new URL( url);
		} catch (Exception e) {
			MessageHandler.showMessage( "Please specify a valid URL.");
			return false;
		}
		
		return true;
	}
} 
