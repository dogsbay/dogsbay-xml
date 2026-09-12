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
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;

import org.bounce.FormLayout;

import com.dogsbay.xml.editor.EditorProperties;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * The dialog that shows the settings for entity substitution.
 *
 * @version	$Revision: 1.4 $, $Date: 2004/11/04 19:21:49 $
 * @author Dogsbay
 */
public class SubstituteEntitiesDialog extends DogsBayDialog {
	private static final Dimension SIZE = new Dimension( 250, 300);
	
	private EditorProperties properties = null;

	// The components that contain the values
	private JCheckBox convertLtButton			= null;
	private JCheckBox convertGtButton			= null;
	private JCheckBox convertAmpButton			= null;
	private JCheckBox convertQuotButton			= null;
	private JCheckBox convertAposButton			= null;

	private JCheckBox convertCharacterEntitiesButton	= null;
	private JCheckBox convertNamedEntitiesButton		= null;

	/**
	 * The dialog that displays the possible entities.
	 *
	 * @param frame the parent frame.
	 */
	public SubstituteEntitiesDialog( JFrame parent, ConfigurationProperties props) {
		super( parent, true);
		
		properties = props.getEditorProperties();
		
		setResizable( false);
		setTitle( "Convert Entities", "Convert Entities to Characters");
		setDialogDescription( "Specify conversion settings");
		
		JPanel main = new JPanel( new BorderLayout());
		main.setBorder( new EmptyBorder( 10, 5, 5, 5));

		main.getActionMap().put( "escapeAction", new AbstractAction() {
			public void actionPerformed( ActionEvent event) {
				cancelButtonPressed();
			}
		});
		main.getInputMap( JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put( KeyStroke.getKeyStroke( KeyEvent.VK_ESCAPE, 0, false), "escapeAction");
	/*
		cancelButton = new JButton( "Cancel");
		cancelButton.setMnemonic( 'C');
		cancelButton.setFont( cancelButton.getFont().deriveFont( Font.PLAIN));
		cancelButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e) {
				cancelButtonPressed();
			}
		});

		okButton = new JButton( "OK");
		okButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e) {
				okButtonPressed();
			}
		});

		getRootPane().setDefaultButton( okButton);

		JPanel buttonPanel = new JPanel( new FlowLayout( FlowLayout.CENTER, 0, 0));
		buttonPanel.setBorder( new EmptyBorder( 10, 0, 3, 0));
		buttonPanel.add( okButton);
		buttonPanel.add( cancelButton);
*/
		convertLtButton 	= new JCheckBox( "Convert &lt; to <");
		convertGtButton 	= new JCheckBox( "Convert &gt; to >");
		convertAmpButton 	= new JCheckBox( "Convert &amp; to &");
		convertQuotButton 	= new JCheckBox( "Convert &quot; to \"");
		convertAposButton 	= new JCheckBox( "Convert &apos; to '");

		JPanel centerPanel = new JPanel( new FormLayout( 5, 0));
		centerPanel.add( convertLtButton, FormLayout.FULL);
		centerPanel.add( convertGtButton, FormLayout.FULL);
		centerPanel.add( convertAmpButton, FormLayout.FULL);
		centerPanel.add( convertQuotButton, FormLayout.FULL);
		centerPanel.add( convertAposButton, FormLayout.FULL);
		
		centerPanel.add( getSeparator(), FormLayout.FULL_FILL);
		
		convertCharacterEntitiesButton = new JCheckBox( "Convert character entities.");
		convertNamedEntitiesButton = new JCheckBox( "Convert named entities.");
		centerPanel.add( convertCharacterEntitiesButton, FormLayout.FULL);
		centerPanel.add( convertNamedEntitiesButton, FormLayout.FULL);
		
		centerPanel.setBorder( new CompoundBorder( 
									new TitledBorder( "Conversion Settings:"),
									new EmptyBorder( 5, 10, 10, 10)));

		main.add( centerPanel, BorderLayout.CENTER);
		//main.add( buttonPanel, BorderLayout.SOUTH);

		setContentPane( main);
		
		/*addWindowListener( new WindowAdapter() {
			public void windowClosing( WindowEvent e) {
				cancelButtonPressed();
			}
		});
*/
		setDefaultCloseOperation( DO_NOTHING_ON_CLOSE);
		
		pack();
		setSize( new Dimension( Math.max( SIZE.width, getSize().width), this.getSize().height));

		setLocationRelativeTo( parent);
	}
	
	public void showDialog() {
		convertLtButton.setSelected( properties.isConvertLtToCharacter());
		convertGtButton.setSelected( properties.isConvertGtToCharacter());
		convertAmpButton.setSelected( properties.isConvertAmpToCharacter());
		convertQuotButton.setSelected( properties.isConvertQuotToCharacter());
		convertAposButton.setSelected( properties.isConvertAposToCharacter());
	
		convertCharacterEntitiesButton.setSelected( properties.isConvertCharacterEntity());
		convertNamedEntitiesButton.setSelected( properties.isConvertNamedEntityToCharacter());
 
 		super.setVisible(true);
	}
	
	protected void okButtonPressed() {
		properties.setConvertLtToCharacter( convertLtButton.isSelected());
		properties.setConvertGtToCharacter( convertGtButton.isSelected());
		properties.setConvertAmpToCharacter( convertAmpButton.isSelected());
		properties.setConvertQuotToCharacter( convertQuotButton.isSelected());
		properties.setConvertAposToCharacter( convertAposButton.isSelected());

		properties.setConvertCharacterEntity( convertCharacterEntitiesButton.isSelected());
		properties.setConvertNamedEntityToCharacter( convertNamedEntitiesButton.isSelected());
 
 		super.okButtonPressed();
	}

	protected void cancelButtonPressed() {
		super.cancelButtonPressed();
	}

	private JComponent getSeparator() {
		JComponent separator = new JPanel();
		separator.setPreferredSize( new Dimension( 100, 10));

		return separator;
	}
} 
