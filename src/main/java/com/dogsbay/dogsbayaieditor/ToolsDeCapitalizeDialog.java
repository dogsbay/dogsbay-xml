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

import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import org.bounce.FormLayout;

import com.dogsbay.dogsbayaieditor.component.XPathFilterPanel;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;


/**
 * Dialog used by the ToolsDeCapitalizeAction class
 *
 * @version	$Revision: 1.12 $, $Date: 2004/10/27 17:03:51 $
 */
public class ToolsDeCapitalizeDialog extends DogsBayDialog {

    private static final Dimension SIZE = new Dimension( 400, 315);
    private JFrame parent = null;
    private JPanel main = null;

    public JRadioButton toNewDocumentRadio;

    public JRadioButton toCurrentDocumentRadio;

    public XPathFilterPanel xpathPanel;
    private ConfigurationProperties props = null;
    public JRadioButton elementsRadio;
    public JRadioButton attributeRadio;
    public JRadioButton elementsAndAttributesRadio;
    
    /**
     * @param frame
     * @param modal
     */
    public ToolsDeCapitalizeDialog(JFrame frame,ConfigurationProperties props) {

        super(frame, true);
        super.setTitle("XML Tools",
				"DeCapitalize Elements and Attributes",
				"Choose the various options...");
	    
        this.parent = frame;
        this.props = props;
	    main = new JPanel();
	    main.setLayout(new BorderLayout());
	    
	    JPanel optionsPanel = this.buildOptionsPanel();
	    JPanel generalPanel = this.buildGeneralPanel();
	    xpathPanel = new XPathFilterPanel(frame,props,XPathFilterPanel.XPATH_OPTIONAL,XPathFilterPanel.DONT_ALLOW_TRAVERSE_CHILDREN);
	    
	    main.add(optionsPanel,BorderLayout.NORTH);
	    main.add(xpathPanel,BorderLayout.CENTER);
	    main.add(generalPanel,BorderLayout.SOUTH);
	    
	    main.setBorder(new EmptyBorder(5,5,5,5));
	    
		//setSize( new Dimension( SIZE.width, SIZE.height));
		
	    setContentPane( main);
	    pack();
	    setSize( new Dimension( SIZE.width, (getSize().height+15)));
	    
    }
    
    /**
     * @return
     */
    private JPanel buildOptionsPanel() {
     
        JPanel options = new JPanel();
        options.setLayout(new FormLayout(5,5));
                     
        elementsRadio = new JRadioButton("DeCapitalize Elements Only");
        attributeRadio = new JRadioButton("DeCapitalize Attributes Only");
        elementsAndAttributesRadio = new JRadioButton("DeCapitalize Elements and Attributes");
                
        elementsAndAttributesRadio.addAncestorListener( new AncestorListener() {
			public void ancestorAdded( AncestorEvent e) {
			    elementsAndAttributesRadio.requestFocusInWindow();
			}

			public void ancestorMoved( AncestorEvent e) {}
			public void ancestorRemoved( AncestorEvent e) {}
		});
        
        ButtonGroup radios = new ButtonGroup();
        radios.add(elementsRadio);
        radios.add(attributeRadio);
        radios.add(elementsAndAttributesRadio);
                
        options.add(elementsAndAttributesRadio,FormLayout.FULL_FILL);
        options.add(elementsRadio,FormLayout.FULL_FILL);
        options.add(attributeRadio,FormLayout.FULL_FILL);
                        
        options.setBorder(new CompoundBorder(new TitledBorder("Options"),new EmptyBorder(0,5,5,5)));
                
        return(options);
    }
    

    /**
     * @return
     */
    private JPanel buildGeneralPanel() {

        JPanel general = new JPanel();
        general.setLayout(new FormLayout(3,2));
        
        toNewDocumentRadio = new JRadioButton("To New Document");
        toCurrentDocumentRadio = new JRadioButton("To Current Document");
        ButtonGroup radioGroup = new ButtonGroup();
        radioGroup.add(toNewDocumentRadio);
        radioGroup.add(toCurrentDocumentRadio);
        toCurrentDocumentRadio.setSelected(true);
               
        general.add(toNewDocumentRadio,FormLayout.FULL_FILL);
        general.add(toCurrentDocumentRadio,FormLayout.FULL_FILL);
        
        
        general.setBorder(new CompoundBorder(new TitledBorder("Output"),new EmptyBorder(0,5,5,5)));
        
        return (general);
    }
    
    


    public void show(String currentXPath) {
        xpathPanel.init(currentXPath);
		selectDefaultRadio();
        super.setVisible(true);
    }
    
    public void okButtonPressed() {
        
        //if xpath was selected
        if(xpathPanel.xpathBox.isSelected()) {
            try {
	            String value = (String)xpathPanel.xpathField.getSelectedItem();
	            if(!value.trim().equals("")) {
	                
	                //test the expression
	                xpathPanel.saveAllProperties();
	                super.okButtonPressed();
	            }
	            else {
	                MessageHandler.showError(parent,"Please Enter the XPath Expression","Tools Capitalize");
	            }
            }catch (NullPointerException e) {
                MessageHandler.showError(parent,"Please Enter the XPath Expression","Tools Capitalize");
            }
        }
        else {
            super.okButtonPressed();
        }
       
    }
    
    private void selectDefaultRadio() {
        if(!elementsRadio.isSelected()) {
            if(!attributeRadio.isSelected()) {
                if(!elementsAndAttributesRadio.isSelected()) {
                    elementsAndAttributesRadio.setSelected(true);
                }
            }
        }
    }
}
