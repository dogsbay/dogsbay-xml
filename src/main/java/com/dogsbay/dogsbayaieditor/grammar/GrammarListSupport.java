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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import javax.swing.AbstractListModel;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import org.bounce.DefaultFileFilter;
import org.bounce.FormConstraints;
import org.bounce.FormLayout;
import org.bounce.event.DoubleClickListener;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XDocumentFactory;
import com.dogsbay.xml.XMLGrammar;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.FileUtilities;
import com.dogsbay.dogsbayaieditor.IconFactory;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.StringUtilities;
import com.dogsbay.dogsbayaieditor.URLUtilities;
import com.dogsbay.dogsbayaieditor.DogsBayDialog;
import com.dogsbay.dogsbayaieditor.DogsBayDialogHeader;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.dogsbay.dogsbayaieditor.properties.KeyMap;
import com.dogsbay.dogsbayaieditor.properties.Keystroke;
import com.dogsbay.dogsbayaieditor.scenario.ScenarioProperties;
import com.dogsbay.dogsbayaieditor.scenario.ScenarioSelectionDialog;

/*
 * List models and cell renderers for GrammarPropertiesDialog's scenario / XPath /
 * fragment / tag-completion lists, extracted to top level (Slice 4 of the large-file
 * split). Each was a self-contained inner class (verified: compiles as a static nested
 * class with no outer access). Behavior-preserving move; package-private.
 */
class ScenarioListCellRenderer extends DefaultListCellRenderer {
	public Component getListCellRendererComponent( JList list, Object value, int index, boolean selected, boolean focus) {
		Component comp = super.getListCellRendererComponent( list, value, index, selected, focus);

		if ( list.getModel() instanceof ScenarioListModel) {
			ScenarioListModel model = (ScenarioListModel)list.getModel();
			
			ScenarioProperties scenario = model.getScenario( index);

			if ( model.isDefault( scenario)) {
				comp.setFont( comp.getFont().deriveFont( Font.BOLD));
			} else {
				comp.setFont( comp.getFont().deriveFont( Font.PLAIN));
			}
		}
		
		return comp;
	}
}

class XPathListCellRenderer extends DefaultListCellRenderer {
	public Component getListCellRendererComponent( JList list, Object value, int index, boolean selected, boolean focus) {
		Component comp = super.getListCellRendererComponent( list, value, index, selected, focus);

		if ( list.getModel() instanceof XPathListModel) {
			XPathListModel model = (XPathListModel)list.getModel();
			
			NamedXPathProperties xpath = model.getXPath( index);

			if ( model.isDefault( xpath)) {
				comp.setFont( comp.getFont().deriveFont( Font.BOLD));
			} else {
				comp.setFont( comp.getFont().deriveFont( Font.PLAIN));
			}
		}
		
		return comp;
	}
}

class ScenarioListModel extends AbstractListModel {
	Vector elements = null;
	ScenarioProperties defaultScenario = null; 
	
	public ScenarioListModel( Vector list) {
		elements = new Vector();

		for ( int i = 0; i < list.size(); i++) {
			ScenarioProperties element = (ScenarioProperties)list.elementAt(i);

			// Find out where to insert the element...
			int index = -1;

			for ( int j = 0; j < elements.size() && index == -1; j++) {
				// Compare alphabeticaly
				if ( element.getName().compareToIgnoreCase( ((ScenarioProperties)elements.elementAt(j)).getName()) <= 0) {
					index = j;
				}
			}
			
			if ( index != -1) {
				elements.insertElementAt( element, index);
			} else {
				elements.addElement( element);
			}
		}
	}
	
	public int getSize() {
		if ( elements != null) {
			return elements.size();
		}
		
		return 0;
	}

	public void addScenario( ScenarioProperties props) {
		if ( !contains( props)) {
			elements.addElement( props);

			fireIntervalAdded( this, elements.size()-1, elements.size()-1);
		}
	}

	public void removeScenario( ScenarioProperties props) {
		int index = elements.indexOf( props);
		elements.removeElement( props);

		fireIntervalRemoved( this, index, index);
	}
	
	private boolean contains( ScenarioProperties props) {
		for ( int i = 0; i < elements.size(); i++) {
			ScenarioProperties scenario = (ScenarioProperties)elements.elementAt(i);
			if ( scenario.getID().equals( props.getID())) {
				return true;
			}
		}
		
		return false;
	}

	public void setDefault( ScenarioProperties scenario) {
		defaultScenario = scenario;
		
		fireContentsChanged( this, 0, elements.size()-1);
	}

	public boolean isDefault( ScenarioProperties scenario) {
		boolean result = false;
		
		if ( defaultScenario != null && scenario != null) {
			result = defaultScenario.getID().equals( scenario.getID()); 
		}

		return result; 
	}

	public ScenarioProperties getDefault() {
		return defaultScenario; 
	}

	public Vector getScenarios() {
		return elements; 
	}

	public Object getElementAt( int i) {
		return ((ScenarioProperties)elements.elementAt( i)).getName();
	}

	public ScenarioProperties getScenario( int i) {
		return (ScenarioProperties)elements.elementAt( i);
	}
}

class XPathListModel extends AbstractListModel {
	Vector elements = null;
	NamedXPathProperties defaultXPath = null; 
	
	public XPathListModel( Vector list) {
		elements = new Vector();

		for ( int i = 0; i < list.size(); i++) {
			NamedXPathProperties element = (NamedXPathProperties)list.elementAt(i);

			// Find out where to insert the element...
			int index = -1;

			for ( int j = 0; j < elements.size() && index == -1; j++) {
				// Compare alphabeticaly
				if ( element.getName().compareToIgnoreCase( ((NamedXPathProperties)elements.elementAt(j)).getName()) <= 0) {
					index = j;
				}
			}
			
			if ( index != -1) {
				elements.insertElementAt( element, index);
			} else {
				elements.addElement( element);
			}
		}
	}
	
	public int getSize() {
		if ( elements != null) {
			return elements.size();
		}
		
		return 0;
	}

	public void addXPath( NamedXPathProperties props) {
		if ( !contains( props)) {
			elements.addElement( props);

			fireIntervalAdded( this, elements.size()-1, elements.size()-1);
		}
	}

	public void updateXPath( NamedXPathProperties props) {
		int index = elements.indexOf( props);

		fireContentsChanged( this, index, index);
	}

	public void removeXPath( NamedXPathProperties props) {
		int index = elements.indexOf( props);
		elements.removeElement( props);

		fireIntervalRemoved( this, index, index);
	}
	
	private boolean contains( NamedXPathProperties props) {
		for ( int i = 0; i < elements.size(); i++) {
			NamedXPathProperties xpath = (NamedXPathProperties)elements.elementAt(i);
			if ( xpath.getID().equals( props.getID())) {
				return true;
			}
		}
		
		return false;
	}

	public void setDefault( NamedXPathProperties xpath) {
		defaultXPath = xpath;
		
		fireContentsChanged( this, 0, elements.size()-1);
	}

	public boolean isDefault( NamedXPathProperties xpath) {
		boolean result = false;
		
		if ( defaultXPath != null && xpath != null) {
			result = defaultXPath.getID().equals( xpath.getID()); 
		}

		return result; 
	}

	public NamedXPathProperties getDefault() {
		return defaultXPath; 
	}

	public Vector getXPaths() {
		return elements; 
	}

	public Object getElementAt( int i) {
		return ((NamedXPathProperties)elements.elementAt( i)).getName();
	}

	public NamedXPathProperties getXPath( int i) {
		return (NamedXPathProperties)elements.elementAt( i);
	}
}

class FragmentListModel extends AbstractListModel {
	Vector elements = null;
	FragmentProperties defaultScenario = null; 
	
	public FragmentListModel( Vector list) {
		elements = new Vector();

		for ( int i = 0; i < list.size(); i++) {
			FragmentProperties element = (FragmentProperties)list.elementAt(i);

			// Find out where to insert the element...
			int index = -1;

			for ( int j = 0; j < elements.size() && index == -1; j++) {
				// Compare alphabeticaly
				if ( element.getOrder() <= ((FragmentProperties)elements.elementAt(j)).getOrder()) {
					index = j;
				}
			}
			
			if ( index != -1) {
				elements.insertElementAt( element, index);
			} else {
				elements.addElement( element);
			}
		}
	}
	
	public int getSize() {
		if ( elements != null) {
			return elements.size();
		}
		
		return 0;
	}

	public void addFragment( FragmentProperties props) {
		elements.addElement( props);
		props.setOrder( elements.size()-1);

		fireIntervalAdded( this, elements.size()-1, elements.size()-1);
	}

	public void updateFragment( FragmentProperties props) {
		int index = elements.indexOf( props);

		fireContentsChanged( this, index, index);
	}

	public void moveUp( FragmentProperties props) {
		int index = elements.indexOf( props);
		
		if ( index > 0) {
			FragmentProperties frag = (FragmentProperties)elements.elementAt( index - 1);
			frag.setOrder( index);
			props.setOrder( index - 1);
			
			elements.removeElement( props);
			elements.insertElementAt( props, index -1);

			fireContentsChanged( this, index-1, index);
		}
	}

	public void moveDown( FragmentProperties props) {
		int index = elements.indexOf( props);
		
		if ( index < elements.size() - 1) {
			FragmentProperties frag = (FragmentProperties)elements.elementAt( index + 1);
			frag.setOrder( index);
			props.setOrder( index + 1);
			
			elements.removeElement( props);
			elements.insertElementAt( props, index+1);

			fireContentsChanged( this, index+1, index);
		}
	}

	public void removeFragment( FragmentProperties props) {
		int index = elements.indexOf( props);
		elements.removeElement( props);
		
		for ( int i = index; i < elements.size(); i++) {
			FragmentProperties frag = (FragmentProperties)elements.elementAt(i);
			frag.setOrder( i);
		}

		fireIntervalRemoved( this, index, index);
	}
	
	public Vector getFragments() {
		return elements; 
	}

	public Object getElementAt( int i) {
		return (FragmentProperties)elements.elementAt( i);
	}

	public FragmentProperties getFragment( int i) {
		return (FragmentProperties)elements.elementAt( i);
	}
}

class TagCompletionListModel extends AbstractListModel {
	Vector elements = null;
	FragmentProperties defaultScenario = null; 
	
	public TagCompletionListModel( Vector list) {
		elements = new Vector( list);
	}
	
	public int getSize() {
		if ( elements != null) {
			return elements.size();
		}
		
		return 0;
	}

	public void addTagCompletion( TagCompletionProperties props) {
		elements.addElement( props);

		fireIntervalAdded( this, elements.size()-1, elements.size()-1);
	}

	public void updateTagCompletion( TagCompletionProperties props) {
		int index = elements.indexOf( props);

		fireContentsChanged( this, index, index);
	}

	public void removeTagCompletion( TagCompletionProperties props) {
		int index = elements.indexOf( props);
		elements.removeElement( props);

		fireIntervalRemoved( this, index, index);
	}
	
	public Vector getTagCompletionList() {
		return elements; 
	}

	public Object getElementAt( int i) {
		return (TagCompletionProperties)elements.elementAt( i);
	}

	public TagCompletionProperties getTagCompletion( int i) {
		return (TagCompletionProperties)elements.elementAt( i);
	}
}

class TagCompletionListCellRenderer extends JLabel implements ListCellRenderer {
//		private JLabel type 		= null;
//		private JLabel location		= null;
	
	public TagCompletionListCellRenderer() {
//			super( new BorderLayout());
//			
//			setBorder( new EmptyBorder( 1, 2, 1, 5));
//			setOpaque( true);
//			
//			type = new JLabel();
//			type.setBorder( new EmptyBorder( 0, 0, 0, 2));
		setOpaque( true);
//			
//			location = new JLabel();
//			location.setOpaque( false);
//			
//			this.add( type, BorderLayout.WEST);
//			this.add( location, BorderLayout.CENTER);
	}
	
	public Component getListCellRendererComponent(JList list,Object value,int selectedIndex,boolean isSelected, boolean cellHasFocus) {	
		boolean clash = false;
		
		if ( value instanceof TagCompletionProperties) {
			TagCompletionProperties t = (TagCompletionProperties)value;
			setText( URLUtilities.getFileName( t.getLocation()));
			
			String grammar = "XSD";
			
			switch ( t.getType()) {
				case XMLGrammar.TYPE_DTD:
					grammar = "DTD";
					break;
				case XMLGrammar.TYPE_RNC:
					grammar = "RNC";
					break;
				case XMLGrammar.TYPE_RNG:
					grammar = "RNG";
					break;
			default:
					grammar = "XSD";
					break;
			}
			
			setIcon( IconFactory.getIconForExtension( grammar));
			setToolTipText( t.getLocation());
			
//				type.setText( "["+grammar+"]");
		}
		
		if (isSelected && list.isEnabled()) {
			setBackground(list.getSelectionBackground());
			setForeground( list.getSelectionForeground());
//				type.setForeground( list.getSelectionForeground());
		} else {
			setBackground( list.getBackground());
			setForeground( list.getForeground());
		}

		setEnabled(list.isEnabled());
		
		setFont( list.getFont().deriveFont( Font.PLAIN));

		return this;
	}
}
