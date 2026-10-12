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

package com.dogsbay.dogsbayaieditor.component;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;

import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import com.dogsbay.dogsbayaieditor.IconFactory;

/**
 * This GUIUtilities is used to ...
 *
 * @version $Revision: 1.1 $, $Date: 2004/10/13 18:32:33 $
 * @author Dogsbay
 */
public class GUIUtilities {

	/**
	 * Aligns the sub items in menu.
	 * 
	 * @param menu
	 */
	public static void alignMenu( JMenu menu) {
		//alignMenu( menu.getMenuComponents());
	}

	/**
	 * Aligns the sub items in a popup menu.
	 * 
	 * @param menu
	 */
	public static void alignMenu( JPopupMenu menu) {
		//alignMenu( menu.getComponents());
	}

	private static void alignMenu( Component[] items) {
		Dimension iconSize = new Dimension( 0, 0);
		Dimension checkSize = null;
		Insets margin = null;
		
		if ( UIManager.getLookAndFeel().getName().toLowerCase().indexOf( "windows") == -1) {
			
			// calculate the correct size...
			for ( int i = 0; i < items.length; i++) {
				if ( items[i] instanceof JMenuItem) {
					if ( items[i] instanceof JCheckBoxMenuItem || items[i] instanceof JRadioButtonMenuItem && checkSize == null) {
						Icon checkIcon = UIManager.getIcon( "CheckBoxMenuItem.checkIcon");
						margin = ((JMenuItem)items[i]).getMargin(); 
	
						if ( checkIcon != null) {
							checkSize = new Dimension( checkIcon.getIconWidth(), checkIcon.getIconHeight());
						}
					}
	
					Icon icon = ((JMenuItem)items[i]).getIcon();
	
					if ( icon != null) {
						if ( iconSize == null) {
							iconSize = new Dimension( icon.getIconWidth(), icon.getIconHeight());
						} else {
							if ( iconSize.width < icon.getIconWidth()) {
								iconSize.width = icon.getIconWidth();
							}
		
							if ( iconSize.height < icon.getIconHeight()) {
								iconSize.height = icon.getIconHeight();
							}
						}
					}
				} else if ( items[i] instanceof JMenu) {
					Icon icon = ((JMenu)items[i]).getIcon();
	
					if ( icon != null) {
						if ( iconSize == null) {
							iconSize = new Dimension( icon.getIconWidth(), icon.getIconHeight());
						} else {
							if ( iconSize.width < icon.getIconWidth()) {
								iconSize.width = icon.getIconWidth();
							}
		
							if ( iconSize.height < icon.getIconHeight()) {
								iconSize.height = icon.getIconHeight();
							}
						}
					}
				}
			}
			
			EmptyBorder border = null;
			
			if ( checkSize != null && margin != null) {
				border = new EmptyBorder( margin.top, margin.left + checkSize.width, margin.bottom, margin.right);
			}
	
			// Set the correct values...
			for ( int i = 0; i < items.length; i++) {
				if ( items[i] instanceof JMenuItem) {
					if ( items[i] instanceof JCheckBoxMenuItem || items[i] instanceof JRadioButtonMenuItem) {
						JMenuItem item = (JMenuItem)items[i];
	
						Icon icon = item.getIcon();
						
						if ( icon != null && icon.getIconWidth() < iconSize.width) {
							item.setIconTextGap( item.getIconTextGap() + ((iconSize.width - icon.getIconWidth())/2));
						} else if ( icon == null) {
							item.setIcon( IconFactory.getEmptyIcon( iconSize.width, iconSize.height));
						}
					} else {
						JMenuItem item = (JMenuItem)items[i];
	
						Icon icon = item.getIcon();
						
						if ( border != null) {
							item.setBorder( border);
						}

						if ( icon != null && icon.getIconWidth() < iconSize.width) {
							Insets insets = item.getMargin();
							item.setBorder( new EmptyBorder( insets.top, insets.left+ (iconSize.width - icon.getIconWidth()), insets.bottom, insets.right));
						} else if ( icon == null) {
							item.setIcon( IconFactory.getEmptyIcon( iconSize.width, iconSize.height));
						}
					}
				} else if ( items[i] instanceof JMenu) {
					JMenu item = (JMenu)items[i];
	
					Icon icon = item.getIcon();
					
					if ( border != null) {
						item.setBorder( border);
					}

					if ( icon != null && icon.getIconWidth() < iconSize.width) {
						Insets insets = item.getMargin();
						item.setBorder( new EmptyBorder( insets.top, insets.left+ (iconSize.width - icon.getIconWidth()), insets.bottom, insets.right));
					} else if ( icon == null) {
						item.setIcon( IconFactory.getEmptyIcon( iconSize.width, iconSize.height));
					}
				}
			}
		} else { // Windows!
			// calculate the correct size...
			for ( int i = 0; i < items.length; i++) {
				if ( items[i] instanceof JMenuItem) {
					Icon icon = ((JMenuItem)items[i]).getIcon();
	
					if ( icon != null) {
						if ( iconSize == null) {
							iconSize = new Dimension( icon.getIconWidth(), icon.getIconHeight());
						} else {
							if ( iconSize.width < icon.getIconWidth()) {
								iconSize.width = icon.getIconWidth();
							}
		
							if ( iconSize.height < icon.getIconHeight()) {
								iconSize.height = icon.getIconHeight();
							}
						}
					}
				} else if ( items[i] instanceof JMenu) {
					Icon icon = ((JMenu)items[i]).getIcon();
	
					if ( icon != null) {
						if ( iconSize == null) {
							iconSize = new Dimension( icon.getIconWidth(), icon.getIconHeight());
						} else {
							if ( iconSize.width < icon.getIconWidth()) {
								iconSize.width = icon.getIconWidth();
							}
		
							if ( iconSize.height < icon.getIconHeight()) {
								iconSize.height = icon.getIconHeight();
							}
						}
					}
				}
			}
			
			// Set the correct values...
			for ( int i = 0; i < items.length; i++) {
				if ( items[i] instanceof JMenuItem) {
					if ( items[i] instanceof JCheckBoxMenuItem || items[i] instanceof JRadioButtonMenuItem) {
						JMenuItem item = (JMenuItem)items[i];
	
						if ( item instanceof JRadioButtonMenuItem) {
							item.setBorder( new EmptyBorder( 2, 5, 2, 2));
						}
	
						Icon icon = item.getIcon();
						
						if ( icon != null && icon.getIconWidth() < iconSize.width) {
							item.setIconTextGap( item.getIconTextGap() + ((iconSize.width - icon.getIconWidth())/2));
						} else if ( icon == null) {
							item.setIcon( IconFactory.getEmptyIcon( iconSize.width, iconSize.height));
						}
					} else {
						JMenuItem item = (JMenuItem)items[i];
	
						Icon icon = item.getIcon();
						
						if ( icon != null && icon.getIconWidth() < iconSize.width) {
							Insets insets = item.getMargin();
							item.setBorder( new EmptyBorder( insets.top, insets.left+ (iconSize.width - icon.getIconWidth()), insets.bottom, insets.right));
						} else if ( icon == null) {
							item.setIcon( IconFactory.getEmptyIcon( iconSize.width, iconSize.height));
						}
					}
				} else if ( items[i] instanceof JMenu) {
					JMenu item = (JMenu)items[i];
	
					Icon icon = item.getIcon();
					
					if ( icon != null && icon.getIconWidth() < iconSize.width) {
						Insets insets = item.getMargin();
						item.setBorder( new EmptyBorder( insets.top, insets.left+ (iconSize.width - icon.getIconWidth()), insets.bottom, insets.right));
					} else if ( icon == null) {
						item.setIcon( IconFactory.getEmptyIcon( iconSize.width, iconSize.height));
					}
				}
			}
		}
	}
}
