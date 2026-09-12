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

package com.dogsbay.dogsbayaieditor.grammar;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;

import org.bounce.DefaultFileFilter;
import org.bounce.FormLayout;

import com.dogsbay.dogsbayaieditor.FileUtilities;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.URLUtilities;
import com.dogsbay.dogsbayaieditor.DogsBayDialog;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.dogsbay.dogsbayaieditor.properties.KeyMap;
import com.dogsbay.dogsbayaieditor.properties.Keystroke;
import com.dogsbay.dogsbayaieditor.properties.TextPreferences;

/**
 * The namespace properties dialog.
 *
 * @version	$Revision: 1.4 $, $Date: 2004/10/23 15:07:23 $
 * @author Dogsbay
 */
public class FragmentPropertiesDialog extends DogsBayDialog {
	private static final Dimension SIZE 	= new Dimension( 400, 400);

	private FragmentProperties fragment			= null;
	private Vector fragments					= null;
	private ConfigurationProperties properties	= null;

	private JFileChooser iconChooser			= null;
	private DefaultFileFilter iconFilter		= null;

	// The components that contain the values
	private JTextField nameField				= null;
	private JLabel iconLabel					= null;
	private JButton iconButton					= null;
	private String iconLocation					= null;

	private JRadioButton blockCheck			= null;
	private JRadioButton inlineCheck			= null;
	private JTextField shortcutField		= null;
	private JTextArea contentEditor			= null;
	private JButton insertCursorButton		= null;
	private JButton insertSelectionButton	= null;

	/**
	 * The dialog that displays the preferences for the editor.
	 *
	 * @param frame the parent frame.
	 */
	public FragmentPropertiesDialog( JFrame parent, ConfigurationProperties properties) {
		super( parent, true);
		
		this.properties = properties;
		setResizable( false);
		setTitle( "Fragment Properties");
		setDialogDescription( "Specify Fragment information");
		
		JPanel main = new JPanel( new BorderLayout());
		main.setBorder( new EmptyBorder( 5, 5, 5, 5));
		
		// GENERAL
		JPanel generalPanel = new JPanel( new FormLayout( 10, 2));
		generalPanel.setBorder( new EmptyBorder( 5, 5, 5, 5));
	
		// icon		
		iconLabel = new JLabel();

		iconButton = new JButton( "Change Icon ...");
		iconButton.setFont( iconButton.getFont().deriveFont( Font.PLAIN));
		iconButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e) {
				iconButtonPressed();
			}
		});
		
		iconLabel.setPreferredSize( new Dimension( iconButton.getPreferredSize().height, iconButton.getPreferredSize().height));
		iconLabel.setBorder( new LineBorder( UIManager.getColor( "controlShadow"), 1));
		iconLabel.setHorizontalAlignment( JLabel.CENTER);
		iconLabel.setVerticalAlignment( JLabel.CENTER);

		JPanel iconPanel = new JPanel( new BorderLayout());
		iconPanel.add( iconLabel, BorderLayout.WEST);
		iconPanel.add( iconButton, BorderLayout.EAST);

		// icon
		JLabel iconLabel = new JLabel( "Icon:");
		generalPanel.add( iconLabel, FormLayout.LEFT);
		generalPanel.add( iconPanel, FormLayout.RIGHT_FILL);

		generalPanel.add( getSeparator(), FormLayout.FULL_FILL);

		// name
		nameField = new JTextField();

		JLabel nameLabel = new JLabel( "Name:");
		generalPanel.add( nameLabel, FormLayout.LEFT);
		generalPanel.add( nameField, FormLayout.RIGHT_FILL);

		// shortcut
		shortcutField = new JTextField();
		shortcutField.addKeyListener(new KeyListener(){
			
			 /** Handle the key typed event from the text field. */
		    public void keyTyped(KeyEvent e) {
		    }

		    /** Handle the key pressed event from the text field. */
		    public void keyPressed(KeyEvent e) {
		    	
		    	int modifiers = e.getModifiersEx();
		        final String modString = KeyEvent.getModifiersExText(modifiers);
		    	
		    	int keyCode = e.getKeyCode();
	            final String keyText = KeyEvent.getKeyText(keyCode);
	            
	            if (keyText.equalsIgnoreCase("DELETE") || keyText.equalsIgnoreCase("BACKSPACE")) {
	            	shortcutField.setText("");

	            	if ( isClash( shortcutField.getText())) {
			    		shortcutField.setForeground( Color.red);
			    	} else {
			    		shortcutField.setForeground( Color.black);
			    	}
	            	
	            	return;
	            }
	                          
		        if (modString.indexOf(keyText) != -1) {
		        	SwingUtilities.invokeLater(new Runnable() { 
		    			public void run() { 
		    				shortcutField.setText(modString+"+");

		    				if ( isClash( shortcutField.getText())) {
					    		shortcutField.setForeground( Color.red);
					    	} else {
					    		shortcutField.setForeground( Color.black);
					    	}
		    			} 
		        	}); 
		        }
		        else if (modString != null && !modString.equals("")) {
		        	SwingUtilities.invokeLater(new Runnable() { 
		        		public void run() { 
		        			shortcutField.setText(modString+"+"+keyText);

		        			if ( isClash( shortcutField.getText())) {
					    		shortcutField.setForeground( Color.red);
					    	} else {
					    		shortcutField.setForeground( Color.black);
					    	}
		    			} 
		        	}); 
		        }
		        else {
		        	SwingUtilities.invokeLater(new Runnable() { 
		    			public void run() { 
		    				shortcutField.setText(keyText);

		    				if ( isClash( shortcutField.getText())) {
					    		shortcutField.setForeground( Color.red);
					    	} else {
					    		shortcutField.setForeground( Color.black);
					    	}
		    			} 
		        	}); 
		        }
		    }

		    /** Handle the key released event from the text field. */
		    public void keyReleased(KeyEvent e) {
		    	
		    	int modifiers = e.getModifiersEx();
		        final String modString = KeyEvent.getModifiersExText(modifiers);
		    	
		    	int keyCode = e.getKeyCode();
	            final String keyText = KeyEvent.getKeyText(keyCode);
	            
	            String current = shortcutField.getText();
	            
	            if (!current.endsWith("+"))
	            {
	            	return;
	            }
	            
	            if (current.equalsIgnoreCase(keyText+"+"))
	            {
	            	SwingUtilities.invokeLater(new Runnable() 
		        	{ 
		    			public void run() 
		    			{ 
		    				shortcutField.setText("");

		    				if ( isClash( shortcutField.getText())) {
					    		shortcutField.setForeground( Color.red);
					    	} else {
					    		shortcutField.setForeground( Color.black);
					    	}
		    			} 
		        	}); 
	            	
	            }
	            else if (current.indexOf(keyText) != -1)
	            {
	            	if (modString != null && !modString.equals(""))
					{
	            		SwingUtilities.invokeLater(new Runnable() 
			        	{ 
			    			public void run() 
			    			{ 
			    				shortcutField.setText(modString+"+");

			    				if ( isClash( shortcutField.getText())) {
						    		shortcutField.setForeground( Color.red);
						    	} else {
						    		shortcutField.setForeground( Color.black);
						    	}
			    			} 
			        	}); 
					}
	            }
	            else
	            {
	            	// leave the current text
	            }
		    }
		});
		JLabel shortcutLabel = new JLabel( "Shortcut Key:");
		generalPanel.add( shortcutLabel, FormLayout.LEFT);
		generalPanel.add( shortcutField, FormLayout.RIGHT_FILL);

		// indent??
		inlineCheck = new JRadioButton( "Inline");
		inlineCheck.setFont( inlineCheck.getFont().deriveFont( Font.PLAIN));

		blockCheck = new JRadioButton( "Block");
		blockCheck.setFont( blockCheck.getFont().deriveFont( Font.PLAIN));
		
		JPanel insertPanel = new JPanel( new FlowLayout( FlowLayout.LEFT, 0, 0));
		insertPanel.add( inlineCheck);
		insertPanel.add( blockCheck);
		
		ButtonGroup group = new ButtonGroup();
		group.add( inlineCheck);
		group.add( blockCheck);
		
		generalPanel.add( new JLabel( "Insert:"), FormLayout.LEFT);
		generalPanel.add( insertPanel, FormLayout.RIGHT);

		generalPanel.add( getSeparator(), FormLayout.FULL);

		// content
		JPanel editorPanel = new JPanel( new BorderLayout());
		editorPanel.setBorder( new CompoundBorder( 
				new TitledBorder( "Content"),
				new EmptyBorder( 0, 5, 5, 5)));

		contentEditor = new JTextArea();
		contentEditor.addCaretListener( new CaretListener() {
			public void caretUpdate( CaretEvent e) {
				String text = contentEditor.getText();
				if ( text != null) {
					insertCursorButton.setEnabled( text.indexOf( "${cursor}") == -1);
				} else {
					insertCursorButton.setEnabled( true);
				}
			}
		});

		JScrollPane scroller = new JScrollPane( contentEditor);
		editorPanel.add( scroller, BorderLayout.CENTER);

		insertCursorButton = new JButton( "${cursor}");
		insertCursorButton.setFont( insertCursorButton.getFont().deriveFont( Font.PLAIN));
		insertCursorButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e) {
				contentEditor.replaceSelection( "${cursor}");
				contentEditor.requestFocusInWindow();
			}
		});
		
		insertSelectionButton = new JButton( "${selection}");
		insertSelectionButton.setFont( insertSelectionButton.getFont().deriveFont( Font.PLAIN));
		insertSelectionButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e) {
				contentEditor.replaceSelection( "${selection}");
				contentEditor.requestFocusInWindow();
			}
		});

		JPanel contentButtonPanel = new JPanel( new FlowLayout( FlowLayout.RIGHT, 0, 0));
		contentButtonPanel.setBorder( new EmptyBorder( 3, 0, 0, 0));
		contentButtonPanel.add( insertCursorButton);
		contentButtonPanel.add( insertSelectionButton);

		editorPanel.add( contentButtonPanel, BorderLayout.SOUTH);

		//removed for dialog
		/*cancelButton = new JButton( "Cancel");
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
		buttonPanel.setBorder( new EmptyBorder( 15, 0, 3, 0));
		buttonPanel.add( okButton);
		buttonPanel.add( cancelButton);
		*/
		JPanel centerPanel = new JPanel( new BorderLayout());
		centerPanel.add( generalPanel, BorderLayout.NORTH);
		centerPanel.add( editorPanel, BorderLayout.CENTER);

		main.add( centerPanel, BorderLayout.CENTER);
		/*main.add( buttonPanel, BorderLayout.SOUTH);

		addWindowListener( new WindowAdapter() {
			public void windowClosing( WindowEvent e) {
				cancelled = true;
				hide();
			}
		});
*/
		setContentPane( main);
		
		setDefaultCloseOperation( HIDE_ON_CLOSE);
		
//		pack();
		setSize( SIZE);

		setLocationRelativeTo( parent);
	}
	
	protected void okButtonPressed() {
		String name = nameField.getText();
		String icon = iconLocation;
		String content = contentEditor.getText();
		String shortcut = shortcutField.getText();
		boolean indent = blockCheck.isSelected();
		
		if ( checkFragment()) {
			
			fragment.setName( name);
			fragment.setIcon( icon);
			fragment.setContent( content);
			fragment.setKey( shortcut);
			fragment.setBlock( indent);
	
			super.okButtonPressed();
		}
	}

	private void iconButtonPressed() {
		JFileChooser chooser = getIconFileChooser();
		int value = chooser.showOpenDialog( getParent());

		if ( value == JFileChooser.APPROVE_OPTION) {
			File file = chooser.getSelectedFile();
			URL url = null;

			try {
				url = com.dogsbay.xml.DogsBayURLUtilities.getURLFromFile(file);
			} catch ( MalformedURLException x) {
				x.printStackTrace(); // should never happen
			}

			setIcon( url.toString());
		}
	}

	private void setIcon( String location) {
//		System.out.println( "setGrammarIcon( "+location+")");
		iconLocation = location;
		ImageIcon icon = null;
		
		try {
			icon = DogsBayImageLoader.get().getImage( new URL( location));

			if ( icon.getIconHeight() != 16 || icon.getIconWidth() != 16) {
				icon = new ImageIcon( icon.getImage().getScaledInstance( 16, 16, Image.SCALE_SMOOTH));
			}
		} catch (Exception e) {
			icon = null;
		}
		
		if ( icon == null) {
			icon = DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/DefaultFragmentIcon.gif");
		}
		
		iconLabel.setIcon( icon);
		iconLabel.revalidate();
	}

	// The creation of the file chooser causes an inspection of all 
	// the drives, this can take some time so only do this when necessary.
	private JFileChooser getIconFileChooser() {
		if ( iconChooser == null) {
			iconChooser = FileUtilities.createFileChooser();
			iconFilter = new DefaultFileFilter( "gif jpg", "Icon File");

			iconChooser.addChoosableFileFilter( iconFilter);
		} 
		
		iconChooser.setFileFilter( iconFilter);

		File file = URLUtilities.toFile( iconLocation);

		if ( file != null) {
			iconChooser.setCurrentDirectory( file);
		} else {
			iconChooser.setCurrentDirectory( FileUtilities.getLastOpenedFile());
		}
		
		iconChooser.rescanCurrentDirectory();
		
		return iconChooser;
	}

	public void show( FragmentProperties fragment, Vector fragments) {
		this.fragment = fragment;
		this.fragments = fragments;
		
		setText( nameField, fragment.getName());
		setIcon( fragment.getIcon());
		contentEditor.setText( fragment.getContent());
		contentEditor.setCaretPosition( 0);
		
		if ( isClash( fragment.getKey())) {
    		shortcutField.setForeground( Color.red);
    	} else {
    		shortcutField.setForeground( Color.black);
    	}

		setText( shortcutField, fragment.getKey());
		
		if ( fragment.isBlock()) {
			inlineCheck.setSelected( false);
			blockCheck.setSelected( true);
		} else {
			inlineCheck.setSelected( true);
			blockCheck.setSelected( false);
		}
			
		
		updatePreferences();
		
		super.setVisible(true);
	}

	protected void cancelButtonPressed() {
		super.cancelButtonPressed();
	}

	private void setText( JTextField field, String text) {
		field.setText( text);
		field.setCaretPosition( 0);
	}
	
	protected boolean isEmpty( String string) {
		if ( string != null && string.trim().length() > 0) {
			return false;
		}
		
		return true;
	}

	private boolean checkFragment() {
		String name = nameField.getText();
		String key = shortcutField.getText();
		
		if ( isEmpty( name)) {
			MessageHandler.showMessage( "Please specify a Name for this Fragment.");
			return false;
		}
		
		for ( int i = 0; i < fragments.size(); i++) {
			if ( ((FragmentProperties)fragments.elementAt(i)).getName().equals( name)) {
				MessageHandler.showMessage( "A fragment with this name \""+name+"\" has been defined already.\n"+
											"Please specify a different name.");
				return false;
			}
		}
		
		if ( !isEmpty( key)) {
			for ( int i = 0; i < fragments.size(); i++) {
				if ( ((FragmentProperties)fragments.elementAt(i)).getKey().equals( key)) {
					MessageHandler.showMessage( "A fragment with this shortcut key \""+key+"\" has been defined already.\n"+
												"Please specify a different shortcut key.");
					return false;
				}
			}

			if ( isClash( key)) {
				MessageHandler.showMessage( "This shortcut key \""+key+"\" clashes with a shortcut key defined by the application.\n"+
											"Previous functionality might no longer work correctly.");
	    	}
		}

		return true;
	}
	
	private boolean isClash( String key) {
		// A fragment's shortcut must not take a key a command already carries.
		// This used to walk every keymap in the active configuration; the
		// bindings answer it directly, and know what the product actually has.
		if ( key == null) {
			return false;
		}
		int index = key.lastIndexOf("+");
		String mask = index == -1 ? null : key.substring( 0, index);
		String value = index == -1 ? key : key.substring( index + 1);
		javax.swing.KeyStroke stroke = com.dogsbay.dogsbayaieditor.properties.KeyBinding.stroke(
				mask == null ? null : mask.toUpperCase( java.util.Locale.ROOT).replace( "+", "_"), value);
		return properties.getKeyBindings().isTaken( stroke);
	}

	public void updatePreferences() {
		contentEditor.setFont( TextPreferences.getBaseFont());
		contentEditor.setTabSize( TextPreferences.getTabSize());
	}
	
	private JComponent getSeparator() {
		JComponent separator = new JPanel();
		separator.setPreferredSize( new Dimension( 100, 10));

		return separator;
	}
}