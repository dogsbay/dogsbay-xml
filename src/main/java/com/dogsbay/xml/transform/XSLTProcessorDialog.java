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

/*
 * Created on 14-Mar-2005
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */

package com.dogsbay.xml.transform;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.dogsbay.util.loader.ExtensionClassLoader;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayDialog;
import javax.swing.JFrame;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.JLabel;

import com.dogsbay.dogsbayaieditor.scenario.ScenarioProperties;

import org.bounce.FormLayout;

/**
 * @author DogsBay
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class XSLTProcessorDialog extends DogsBayDialog {
	private static final Dimension SIZE = new Dimension( 400, 300);
	private JRadioButton processorDefaultButton	= null;
	private JRadioButton processorSaxon2Button	= null;
	private JRadioButton processorSaxon3Button	= null;

	public XSLTProcessorDialog( JFrame parent) {
		super( parent, true);
		
		
		//this.parent = parent;
		
		//setResizable( false);
		//setTitle( "Execute XSLT");
		//setDialogDescription( "Specify XSL Transformation settings."); 
		JPanel processorPanel = new JPanel( new FormLayout( 10, 2));
		
		processorPanel.setBorder( new CompoundBorder( 
									new TitledBorder( "Processor"),
									new EmptyBorder( 0, 5, 5, 5)));
							
		ButtonGroup processorGroup = new ButtonGroup();		

		processorDefaultButton	= new JRadioButton( "Use Default Processor");
		processorPanel.add( processorDefaultButton, FormLayout.FULL);
		processorGroup.add( processorDefaultButton);

		processorPanel.add( getSeparator(), FormLayout.FULL_FILL);

		processorSaxon2Button	= new JRadioButton( "Saxon 9.x (XSLT 2.0)");
		processorPanel.add( processorSaxon2Button, FormLayout.FULL);
		processorGroup.add( processorSaxon2Button);

		processorSaxon3Button	= new JRadioButton( "Saxon-HE 12.7 (XSLT 3.0)");
		processorPanel.add( processorSaxon3Button, FormLayout.FULL);
		processorGroup.add( processorSaxon3Button);
		JPanel dialogPanel = new JPanel(new BorderLayout());
		JPanel main = new JPanel( new BorderLayout());
		
		main.setBorder( new EmptyBorder( 2, 2, 5, 2));
		
		main.add( processorPanel, BorderLayout.CENTER);
		
//		JButton closeButton = new JButton( "OK");
//		closeButton.setMnemonic('O');
//		closeButton.addActionListener( new ActionListener() {
//			public void actionPerformed( ActionEvent e) {
//				hide();
//			}
//		});
		
		JPanel buttonPanel = new JPanel( new FlowLayout( FlowLayout.CENTER, 0, 0));
		buttonPanel.setBorder( new EmptyBorder( 5, 0, 0, 0));
//		buttonPanel.add( closeButton);
		
		main.add( buttonPanel, BorderLayout.SOUTH);
		
	
		dialogPanel.add(main,BorderLayout.CENTER);
		
		setContentPane( dialogPanel);
		pack();

		setSize( new Dimension( 250, getSize().height));
		
	}
	
	private JComponent getSeparator() {
		JComponent separator = new JPanel();
		separator.setPreferredSize( new Dimension( 100, 10));

		return separator;
	}
	
	
	public int getProcessor() {
		int type = ScenarioProperties.PROCESSOR_DEFAULT;

		if ( processorSaxon2Button.isSelected()) {
			type = ScenarioProperties.PROCESSOR_SAXON_XSLT2;
		} else if ( processorSaxon3Button.isSelected()) {
			type = ScenarioProperties.PROCESSOR_SAXON_XSLT3;
		}


		return type;
	}

	public void setProcessor( int type) {

		switch ( type) {
			case ScenarioProperties.PROCESSOR_SAXON_XSLT2:
				processorSaxon2Button.setSelected( true);
				break;
			case ScenarioProperties.PROCESSOR_SAXON_XSLT3:
				processorSaxon3Button.setSelected( true);
				break;
			default:
				processorDefaultButton.setSelected( true);
				break;
		}
	}
}
