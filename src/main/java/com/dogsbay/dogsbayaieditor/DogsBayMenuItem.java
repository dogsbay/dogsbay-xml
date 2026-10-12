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

import java.awt.Font;

import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

/**
 * Handles MenuItems, allows for emacs accelerator keys
 *
 * @version	$Revision: 1.2 $, $Date: 2004/10/04 16:19:28 $
 * @author Dogs bay
 */
public class DogsBayMenuItem extends JMenuItem{
	KeyStroke accelerator = null;
	
	public DogsBayMenuItem (Action action)
	{
		super(action);
	}
	
	public DogsBayMenuItem (String name, char shortcut)
	{
		super(name,shortcut);
	}
	
    public KeyStroke getAccelerator()
    {
        return accelerator;
    }

    public void setAccelerator(KeyStroke keystroke, boolean emacsMode)
    {
    	// start off by blanking the existing accelerator
    	setAccelerator(null);
    	
		KeyStroke keystroke1 = accelerator;
		accelerator = keystroke;
		if (!emacsMode)
		{
			firePropertyChange("accelerator",null, keystroke);
			this.setFont(UIManager.getFont("MenuItem.font"));
		}
		else
		{
			// put any emacs mode keys in Italic
			Font font = this.getFont().deriveFont(Font.ITALIC+this.getFont().getStyle());
			this.setFont(font);
		}
		
		
    }
    
    public void setAccelerator(KeyStroke keystroke)
    {
        KeyStroke keystroke1 = accelerator;
        accelerator = keystroke;
        firePropertyChange("accelerator", keystroke1, accelerator);
    }
    
    
}
