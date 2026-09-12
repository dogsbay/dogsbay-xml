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
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JTextField;




/**
 *
 *
 * @version	$Revision: 1.2 $, $Date: 2005/06/30 09:09:17 $
 */
public class UserView {

    private UserViewPanel panel = null;
    private String identifier = null;
    private NavigationButton button = null;
    private ImageIcon icon = null;
    private DogsBayAIEditor parent = null;
    
    public UserView(JPanel panel, String identifier, DogsBayAIEditor parent) {
        
        this.panel = new UserViewPanel(panel);
        this.identifier = identifier;
        this.parent = parent;
        this.icon = DogsBayImageLoader.get().getImage( "com/dogsbay/dogsbayaieditor/icons/DogsBayAIEditorSmallIcon.gif");
        this.button = new NavigationButton(identifier, icon);
        this.button.setPreferredSize(new Dimension(this.button.getPreferredSize().width, 29));
        
        button.addItemListener(new UserViewItemListener());
        JTextField field = new JTextField();
        field.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {

           
                
            }
            
        });
    }
    
    public UserView() {

    }
    
    
    /**
     * @return Returns the identifier.
     */
    public String getIdentifier() {

        return identifier;
    }
    /**
     * @param identifier The identifier to set.
     */
    public void setIdentifier(String identifier) {

        this.identifier = identifier;
    }
    /**
     * @return Returns the panel.
     */
    public UserViewPanel getPanel() {

        return panel;
    }
    /**
     * @param panel The panel to set.
     */
    public void setPanel(UserViewPanel panel) {

        this.panel = panel;
    }

    
    /**
     * @return Returns the button.
     */
    public NavigationButton getButton() {

        return button;
    }
    /**
     * @param button The button to set.
     */
    public void setButton(NavigationButton button) {

        this.button = button;
    }
    
    private class UserViewItemListener implements ItemListener {
		public void itemStateChanged(ItemEvent event) {
			if (event.getStateChange() == ItemEvent.SELECTED) {
			    DogsBayView view = parent.getView();
				ViewPanel current = view.getCurrentView();
				if (view != null) {
					try {
						parent.switchToUserView(UserView.this);
					} catch (Exception e) {
					    e.printStackTrace();
						MessageHandler.showMessage(	"Please ensure the document is well-formed\nbefore switching to the \"Grid\".");
						current.setFocus();
					}
				}
			}
		}
	}
    
    public class UserViewPanel extends ViewPanel {
            
        public UserViewPanel(JPanel newPanel) {
            super(new BorderLayout());
            this.add(newPanel, BorderLayout.CENTER);
        }
        
	    /* (non-Javadoc)
	     * @see com.dogsbay.dogsbayaieditor.ViewPanel#setFocus()
	     */
	    public void setFocus() {
	
	        // TODO
	        this.revalidate();
	        this.repaint();
	        
	    }
	
	    /* (non-Javadoc)
	     * @see com.dogsbay.dogsbayaieditor.ViewPanel#updatePreferences()
	     */
	    public void updatePreferences() {
	
	        // TODO
	        
	    }
	
	    /* (non-Javadoc)
	     * @see com.dogsbay.dogsbayaieditor.ViewPanel#setProperties()
	     */
	    public void setProperties() {
	
	        // TODO
	        
	    }
	    	    
    }
}
