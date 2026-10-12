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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;

import org.bounce.QPanel;


/**
 * 
 *
 * @version	$Revision: 1.1 $, $Date: 2005/08/31 09:18:42 $
 */
public class DogsBayProgressDialog extends DogsBayDialogHeader {

	private static final Dimension SIZE = new Dimension( 400, 150);
	public JProgressBar monitor = null;
	public JLabel label;
	private JButton cancelButton;
	/**
	 * Initialise the class DogsBayProgressDialog.java
	 * @param frame
	 * @param modal
	 */
	public DogsBayProgressDialog(JFrame parent, boolean modal) {

		super(parent, modal);
		
		monitor = new JProgressBar();
		label = new JLabel("Searching");
		
		JPanel main = new JPanel(new BorderLayout());
		JPanel progressPanel = new JPanel(new BorderLayout());
		
		progressPanel.add(label, BorderLayout.NORTH);
		progressPanel.add(monitor, BorderLayout.CENTER);
		
		progressPanel.setBorder(new EmptyBorder(5,5,5,5));
		
		main.add(progressPanel, BorderLayout.CENTER);
		main.add(buildFooter(), BorderLayout.SOUTH);
		
		setContentPane( main);
		
		setDefaultCloseOperation( DO_NOTHING_ON_CLOSE);

		pack();
		setSize( new Dimension( Math.max( SIZE.width, getSize().width), Math.max( SIZE.height, getSize().height)));

		setLocationRelativeTo( parent);
	}
	
	private QPanel buildFooter() {
	    QPanel footer = new QPanel();
	    footer.setLayout(new FlowLayout());
	    footer.setOpaque(true);
	    
	    //add buttons
	    	    
	    cancelButton = new JButton("Cancel");
	    cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				cancelButtonPressed();
			}});
	    

	    getRootPane().setDefaultButton(cancelButton);
	    
	    
	    footer.add(cancelButton);
	    
	    return(footer);
	}
	
	/* (non-Javadoc)
	 * @see java.awt.Component#setVisible(boolean)
	 */
	public void setVisible(boolean b) {
	
		if(b == true) {
			
			
		}
		else {
			monitor.setIndeterminate(false);
		}
		super.setVisible(b);
	}
	
	public void setVisible(int min, int max) {
		remakeMonitor(min, max);
		
		super.setVisible(true);
	}
	
	public void incrementMonitor(int value) {
		if(value > -1) {
			this.monitor.setValue(this.monitor.getValue()+value);
		}
	}

	public void remakeMonitor(int min, int max) {
		this.monitor = new JProgressBar(min, max);
		this.monitor.setStringPainted(true);
		this.monitor.setValue(0);
	}
	
	public boolean isCancelled() {
		return(super.cancelled);
	}
}
