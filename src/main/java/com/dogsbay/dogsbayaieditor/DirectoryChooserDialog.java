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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.bounce.FormLayout;

import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.l2fprod.common.swing.JDirectoryChooser;

public class DirectoryChooserDialog extends DogsBayDialog {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private Dimension size = new Dimension(350, 180);
	private DogsBayAIEditor editor = null;
	private ConfigurationProperties properties = null;
	private JPanel basicPanel = null;
	private JTextField inputLocationField = null;
	private JPanel locationPanel = null;
	private JPanel locButtonPanel = null;
	
	private File selectedFolder = null;
	
	public DirectoryChooserDialog(DogsBayAIEditor editor) {
		super(editor, true);
		
		if( editor != null) {
			this.editor = editor;
		}
		properties = editor.getProperties();
		
		setResizable( false);
	    setTitle( "Directory Chooser", "Choose a directory");
		setDialogDescription( "Enter or browse for a directory");

		JPanel main = new JPanel( new BorderLayout());

		basicPanel = new JPanel( new BorderLayout());
		
		inputLocationField = new JTextField();
		inputLocationField.addAncestorListener( new AncestorListener() {
			public void ancestorAdded( AncestorEvent e) {
			    inputLocationField.requestFocusInWindow();
			}

			public void ancestorMoved( AncestorEvent e) {}
			public void ancestorRemoved( AncestorEvent e) {}
		});
		

		JButton inputLocationButton = new JButton( "...");
		inputLocationButton.setMargin( new Insets( 0, 10, 0, 10));
		inputLocationButton.setPreferredSize( 
		        new Dimension( inputLocationButton.getPreferredSize().width, 
		                		inputLocationField.getPreferredSize().height));
		
		inputLocationButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e) {
				inputLocationButtonPressed();
			}
		});
		
		inputLocationField.setEnabled( true);
		inputLocationButton.setEnabled( true);

		locationPanel = new JPanel( new FormLayout(3,2));
		
		locButtonPanel = new JPanel(new BorderLayout());
		locButtonPanel.add( inputLocationField, BorderLayout.CENTER);
		locButtonPanel.add( inputLocationButton, BorderLayout.EAST);
		
		JLabel locationLabel = new JLabel("Location: ");
		
		locationPanel.add( locationLabel, FormLayout.LEFT);
		locationPanel.add( locButtonPanel,FormLayout.RIGHT_FILL);
		
		basicPanel.add(locationPanel, BorderLayout.CENTER);
		main.add( basicPanel, BorderLayout.CENTER);

		main.setBorder(new CompoundBorder(new TitledBorder("Select Directory"), new EmptyBorder(5,5,5,5)));
		
		setContentPane( main);
		setDefaultCloseOperation( DO_NOTHING_ON_CLOSE);

		pack();
		
		setLocationRelativeTo( editor);

		setSize(size);
	}
	
	public void showDialog() {
		this.selectedFolder = null;
		super.setVisible(true);
	}

	protected void okButtonPressed() {
		
		if(selectedFolder == null) {
			if((inputLocationField.getText() != null) && (inputLocationField.getText().length() > 0)) {
				selectedFolder = new File(inputLocationField.getText());
			}
			else {
				MessageHandler.showError("Please enter or select a folder", "Directory Chooser");
			}
		}
		
		
		if(selectedFolder != null) {
			if(selectedFolder.exists() == true) {
				if(selectedFolder.isDirectory() == true) {
					super.okButtonPressed();
				}
				else {
					MessageHandler.showError("The selected folder is not valid", "Directory Chooser");
				}
			}
			else {
				MessageHandler.showError("The selected folder does not exist", "Directory Chooser");
			}
		}
		else {
			MessageHandler.showError("Error selecting folder", "Directory Chooser");		
		}
		
		
	}
	

	private void inputLocationButtonPressed() {
		
		JDirectoryChooser chooser = FileUtilities.getDirectoryChooser();
		
	 	int value = chooser.showOpenDialog( editor);
		
	 	if ( value == JDirectoryChooser.APPROVE_OPTION) {
		 	setSelectedFolder(chooser.getSelectedFile());
			inputLocationField.setText(selectedFolder.toString());
	 	}
	}



	public void setSelectedFolder(File selectedFolder) {
		this.selectedFolder = selectedFolder;
	}



	public File getSelectedFolder() {
		return selectedFolder;
	}
}
