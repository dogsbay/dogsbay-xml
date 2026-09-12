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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.metal.MetalButtonUI;

import org.bounce.CenterLayout;
import org.bounce.FormLayout;
import org.bounce.LinkButton;
import org.bounce.QPanel;
import org.bounce.util.BrowserLauncher;

/**
 * The dialog that shows the DogsBay identity.
 *
 * @version	$Revision: 1.4 $, $Date: 2005/09/05 13:55:11 $
 * @author Dogsbay
 */
public class AboutDialog extends JDialog {
	private static final Dimension SIZE = new Dimension( 346, 240);
	private LinkButton referenceField  = null;

	/**
	 * The constructor for the about dialog.
	 *
	 * @param frame the parent frame.
	 */
	public AboutDialog( JFrame frame) {
		super( frame, false);
		
		setResizable( false);
		setUndecorated( true);
		
		setTitle( "About DogsBay XML");
		
		Identity identity = Identity.getIdentity();
		
		
		QPanel contentPane = new QPanel( new BorderLayout());
		contentPane.setGradientBackground( true);
		contentPane.setBackground( Color.white);
		contentPane.setBorder( new CompoundBorder(
				new EmptyBorder( 5, 5, 5, 5),
				new CompoundBorder( 
					new LineBorder( new Color( 51, 102, 153), 2),
					new EmptyBorder( 5, 5, 5, 5))));
		
		JLabel logoLabel = new JLabel( DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/DogsBayLogo.gif"));
		logoLabel.setHorizontalAlignment( SwingConstants.CENTER);

		JLabel titleField = new JLabel( identity.getTitle()+" V "+identity.getVersion());
		Font prevFont = titleField.getFont();
		titleField.setFont( prevFont.deriveFont( Font.BOLD, prevFont.getSize() + 8));
		titleField.setForeground( Color.white);
		titleField.setBackground( new Color( 51, 102, 153));
		titleField.setOpaque( true);
		titleField.setBorder( new EmptyBorder( 5, 5, 5, 5));
		titleField.setHorizontalAlignment( SwingConstants.CENTER);

		final LinkButton referenceField = new LinkButton( identity.getReference());
		referenceField.setForeground( Color.blue);
		referenceField.setOpaque( false);
		referenceField.setHorizontalAlignment( SwingConstants.CENTER);
		referenceField.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e) {
				try {
					BrowserLauncher.openURL( referenceField.getText());
				} catch( Exception ex) {
					ex.printStackTrace();
				}
			}
		});
		

		JLabel copyrightField = new JLabel( identity.getCopyright());
		copyrightField.setHorizontalAlignment( SwingConstants.CENTER);
		copyrightField.setForeground( new Color( 51, 102, 153));
		copyrightField.setOpaque( false);
		copyrightField.setBorder( new EmptyBorder( 0, 0, 0, 0));
		
		JLabel licenseField = new JLabel();
		licenseField.setHorizontalAlignment( SwingConstants.CENTER);
		licenseField.setForeground( new Color( 51, 102, 153));
		licenseField.setOpaque( false);
		licenseField.setBorder( new EmptyBorder( 0, 10, 0, 10));
		
		JLabel remarkField = new JLabel();
		remarkField.setHorizontalAlignment( SwingConstants.CENTER);
		remarkField.setForeground( new Color( 51, 102, 153));
		remarkField.setOpaque( false);
		remarkField.setBorder( new EmptyBorder( 0, 10, 0, 10));

		/*if (licenseManager == null || licenseManager.getLicenseType().equals( LicenseType.LICENSE_LITE))
		{		  
			try {
				licenseField.setText( "DogsBay XML Lite" );
				remarkField.setText( "(For academic and/or non-profit use only.)");
			} catch( Exception e) {
				e.printStackTrace();
			}
		  
		}
		else if ( licenseManager.getLicenseType().equals( LicenseType.LICENSE_TEMPORARY)) {
			try {
				licenseField.setText( "This Evaluation License is issued to: "+licenseManager.getUserName());
				remarkField.setText( "(Expires in "+licenseManager.daysLeft()+" days)");
			} catch( Exception e) {
				e.printStackTrace();
			}
		} else if ( licenseManager.getLicenseType().equals( LicenseType.LICENSE_ACADEMIC)) {
			try {
				licenseField.setText( "This Academic License is issued to: "+licenseManager.getUserName());
				remarkField.setText( "(For academic and/or non-profit use only.)");
			} catch( Exception e) {
				e.printStackTrace();
			}
		} else if ( licenseManager.getLicenseType().equals( LicenseType.LICENSE_COMPLIMENTARY)) {
			try {
				licenseField.setText( "This Complimentary License is issued to: "+licenseManager.getUserName());
			} catch( Exception e) {
				e.printStackTrace();
			}
		} else if ( licenseManager.getLicenseType().equals( LicenseType.LICENSE_PERMANENT)) {
			try {
				licenseField.setText( "This Permanent License is issued to: "+licenseManager.getUserName());
			} catch( Exception e) {
				e.printStackTrace();
			}
		}*/

		JPanel referencePanel = new JPanel( new FormLayout( 0, 0));
		referencePanel.add( licenseField, FormLayout.FULL_FILL);
		if ( remarkField.getText() != null && remarkField.getText().trim().length() > 0) {
			referencePanel.add( remarkField, FormLayout.FULL_FILL);
		}
		referencePanel.add( Box.createVerticalStrut(20), FormLayout.FULL_FILL);
		referencePanel.add( referenceField, FormLayout.FULL_FILL);
		referencePanel.add( Box.createVerticalStrut(20), FormLayout.FULL_FILL);
		referencePanel.add( copyrightField, FormLayout.FULL_FILL);
		referencePanel.add( Box.createVerticalStrut(20), FormLayout.FULL_FILL);
		referencePanel.setBackground( Color.white);
		referencePanel.setOpaque( false);

		JPanel centerPanel = new JPanel( new CenterLayout( CenterLayout.VERTICAL));
		centerPanel.add( titleField, CenterLayout.CENTER);
		centerPanel.setOpaque( false);

		contentPane.add( logoLabel, BorderLayout.NORTH);
		contentPane.add( centerPanel, BorderLayout.CENTER);
		contentPane.add( referencePanel, BorderLayout.SOUTH);

		XButton okButton = new XButton( "Close");
		okButton.setForeground( new Color( 51, 102, 153));
		okButton.setBorder( new CompoundBorder( 
									new LineBorder( new Color( 51, 102, 153), 1),
									new EmptyBorder( 2, 10, 2, 10)));
//		okButton.setContentAreaFilled( false);
		okButton.setOpaque( false);
		
		okButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e) {
				okButtonPressed();
			}
		});
		
		XButton buyButton = new XButton( "Buy Now");
		buyButton.setForeground( new Color( 51, 102, 153));
		buyButton.setBorder( new CompoundBorder( 
									new LineBorder( new Color( 51, 102, 153), 1),
									new EmptyBorder( 2, 10, 2, 10)));
		buyButton.setOpaque( false);
		
		buyButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e) {
				buyButtonPressed();
			}
		});

		JPanel main = new JPanel( new BorderLayout());
//		main.setBorder( new EmptyBorder( 2, 2, 2, 2));
		
		JPanel buttonPanel = new JPanel( new FlowLayout( FlowLayout.CENTER, 5, 0));
		buttonPanel.setBorder( new EmptyBorder( 5, 0, 3, 0));

		//if ( licenseManager.getLicenseType().equals( LicenseType.LICENSE_TEMPORARY)) {
		//	buttonPanel.add( buyButton);
		//}
		buttonPanel.add( okButton);
		buttonPanel.setOpaque( false);

		//comment out this line to create the splash screen
		referencePanel.add( buttonPanel, FormLayout.FULL_FILL);

		main.add( contentPane, BorderLayout.CENTER);
//		main.add( buttonPanel, BorderLayout.SOUTH);
		
		setContentPane( main);
		
		setSize( SIZE);

		getRootPane().setDefaultButton( okButton);
		setDefaultCloseOperation( HIDE_ON_CLOSE);
	}
	// Ok button handler
	private void okButtonPressed() {
		//setVisible(false);
		hide();
	}

	// Ok button handler
	private void buyButtonPressed() {
		try {
			BrowserLauncher.openURL( "https://dogsbay.ai");
		} catch (Exception e) {
		}
	}
	
	private class XButton extends JButton {
		public XButton( String name) {
			super( name);
		}
		
		public void updateUI() {
		    setUI( MetalButtonUI.createUI( this));
		}
	}
} 
