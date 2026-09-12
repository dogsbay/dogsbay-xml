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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import java.awt.CardLayout;

import org.bounce.DefaultFileFilter;
import org.bounce.FormConstraints;
import org.bounce.FormLayout;
import org.bounce.QLabel;

import com.dogsbay.util.loader.ExtensionClassLoader;
import com.dogsbay.xml.XMLUtilities;
import com.dogsbay.xml.editor.Constants;
import com.dogsbay.xml.editor.EditorProperties;

import com.dogsbay.xml.navigator.NavigatorProperties;
import com.dogsbay.xml.viewer.ViewerProperties;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.dogsbay.dogsbayaieditor.properties.FontType;
import com.dogsbay.dogsbayaieditor.properties.KeyMap;
import com.dogsbay.dogsbayaieditor.properties.KeyPreferences;
import com.dogsbay.dogsbayaieditor.properties.Keystroke;
import com.dogsbay.dogsbayaieditor.properties.TextPreferences;
import com.l2fprod.common.swing.JDirectoryChooser;


/**
 * The preferences dialog for the editor application.
 *
 * @version	$Revision: 1.36 $, $Date: 2005/08/31 10:27:16 $
 * @author Dogsbay
 */
public class PreferencesDialog extends DogsBayDialog {
	private static final Dimension SIZE = new Dimension( 750, 600);
	
	private static final String[] FONT_SIZES =  { "8", "9", "10", "11", "12", "14", "16", "18", "20", "22", "24", "26", "28", "36", "48", "72"};
	private static final String[] SPACES =  { "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"};

	private DogsBayAIEditor parent = null;

	private Font[] fonts = null;

	private Vector styles 		= null;

	private TextPreferences textPreferences	= null;
	private ConfigurationProperties properties = null;
	private EditorProperties editorProperties = null;
	private ViewerProperties viewerProperties = null;
	private NavigatorProperties navigatorProperties = null;

	private FontStylePanel stylePanel		= null;

	private JColorChooser colorChooser	= null;

	private JList<String> categoryList = null;
	private JPanel cardPanel = null;
	private CardLayout cardLayout = null;
	private com.dogsbay.dogsbayaieditor.plugin.PluginManagerPanel pluginManagerPanel = null;

	private JComboBox fontSelectionBox	= null;
	private JComboBox sizeSelectionBox	= null;
	private JComboBox tabSizeBox		= null;
	private JCheckBox convertTabBox		= null;

	// Editor boxes
	private JCheckBox indentMixedContentBox	= null;
//	private JCheckBox softWrapBox			= null;
//	private JCheckBox tagCompletionBox		= null;
//	private JCheckBox showMarginBox			= null;
//	private JCheckBox showOverviewMarginBox	= null;
//	private JCheckBox showFoldingMarginBox	= null;
//	private JCheckBox autoIndentationBox	= null;
//	private JCheckBox textPromptingBox		= null;
	private JCheckBox antialiasingBox		= null;
//	private JCheckBox strictTextPromptingBox	= null;
	
	// Viewer boxes
//	private JCheckBox showAttributes	= null;
//	private JCheckBox showNamespaces	= null;
//	private JCheckBox showValues		= null;
//	private JCheckBox showComments		= null;
//	private JCheckBox showInline			= null;
//	private JCheckBox showPI			= null;

	// Printing

	// Designer boxes
//	private JCheckBox autoCreateRequiredBox	= null;
//	private JCheckBox showAttributeValuesBox	= null;
//	private JCheckBox showElementValuesBox	= null;

	// Integration server (Server tab)
	private JCheckBox mcpServerEnabledBox							= null;
	private JTextField mcpServerPortField							= null;
	private JLabel mcpServerPortLabel								= null;
	private JCheckBox mcpEndpointBox									= null;
	private JCheckBox rpcEndpointBox									= null;
	private JComboBox<String> acpDefaultTierCombo						= null;
	private JCheckBox agentProposalsBox								= null;
	private JComboBox<String> sessionRetentionCombo					= null;
	private JLabel mcpStatusLabel									= null;

	// General boxes
	private JCheckBox autoSyncSelectionBox							= null;
	private JCheckBox scrollDocumentTabsBox							= null;
	private JCheckBox reopenSessionFilesBox							= null;
	private JCheckBox showFullPathBox								= null;
//	private JCheckBox showNavigatorAttributesBox					= null;
	private JCheckBox checkTypeOnOpeningBox							= null;
	private JCheckBox promptCreateTypeOnOpeningBox					= null;
	private JCheckBox validateOnOpeningBox							= null;
	private JCheckBox useInternalSchemaBox							= null;
	private JCheckBox hideScenarioExecutionDialogWhenCompleteBox	= null;
	private JCheckBox openXIncludeInNewDocumentBox					= null;
	private JCheckBox uniqueXPathBox								= null;
	private JCheckBox multipleDocumentsBox							= null;

	// Proxy fields
	private JCheckBox useProxyCheck	= null;
	private JLabel proxyHostLabel	= null;
	private JTextField proxyHostField	= null;
	private JLabel proxyPortLabel	= null;
	private JTextField proxyPortField	= null;
	private JTextField browserField	= null;


	private JCheckBox loadDTDGrammarCheck	= null;

	// Format boxes
	private JCheckBox wrapTextCheck					= null;
	private JFormattedTextField wrappingColumnField	= null;

	private JRadioButton customFormatterRadio	= null;
	private JRadioButton compactFormatterRadio	= null;
	private JRadioButton standardFormatterRadio	= null;

	private JCheckBox padTextCheck				= null;
	private JCheckBox indentCheck				= null;
	private JCheckBox newlinesCheck				= null;
	private JCheckBox stripWhitespaceCheck		= null;
	private JCheckBox preserveMixedContentCheck	= null;

	// House style (FormatStyle) editor — the active Format tab UI.
	private JComboBox<String> fsIndentUnit		= null;   // Spaces | Tabs
	private JFormattedTextField fsIndentSize	= null;
	private JComboBox<String> fsNewline			= null;   // LF | CRLF | Platform
	private JCheckBox fsFinalNewline			= null;
	private JCheckBox fsPreserveMixed			= null;
	private JCheckBox fsPreserveBlank			= null;
	private JCheckBox fsSemanticBreaks			= null;
	private JComboBox<String> fsContinuation	= null;   // Block | Flush
	private JCheckBox fsTrimWhitespace			= null;
	private JFormattedTextField fsMaxWidth		= null;
	private JCheckBox fsFormatOnSave			= null;
	private JLabel fsSourceLabel				= null;
	private JButton fsSaveToProject				= null;
	/** True when the panel is showing a project's declared style (not the user default),
	 *  so OK must not overwrite the personal default. */
	private boolean fsEditingProjectStyle		= false;

	private JList catalogList				= null;
	private DefaultListModel catalogsModel	= null;
	/*private Vector removedCatalogs			= null;
	private Vector addedCatalogs			= null;*/
	
	private PrefixNamespaceMappingPanel prefixNamespaceMappingPanel = null;

	private JCheckBox preferPublicIdentifiersBox	= null;

	private JList extensionList					= null;
	private DefaultListModel extensionsModel	= null;
	//private Vector removedExtensions			= null;
	//private Vector addedExtensions				= null;
	
	private JComboBox lafSelectionBox			= null;
	private com.dogsbay.dogsbayaieditor.properties.KeyBindings keyBindings = null;
	private com.dogsbay.dogsbayaieditor.properties.KeyBindingsPanel keyBindingsPanel = null;

	
	// stores the current keymapping configuration 
	private JComboBox actionNames 			    = null;
	private JTextArea actionDescription 		= null;
	private JTextField keySequence				= null;
	private Hashtable keyMaps					= null;
	private Hashtable configMap					= null;
	private KeyMapDialog keymapDialog 			= null;

    private Vector initialExtensions;

    private Vector currentExtensions;

	/**
	 * The dialog that displays the preferences for the editor.
	 *
	 * @param frame the parent frame.
	 */
	public PreferencesDialog( DogsBayAIEditor parent, ConfigurationProperties props) {
		super( parent, false);
		
		this.parent = parent;
		this.properties = props;
		this.textPreferences = props.getTextPreferences();
		this.editorProperties = props.getEditorProperties();
		this.viewerProperties = props.getViewerProperties();

		this.navigatorProperties = props.getNavigatorProperties();
	
		
		setResizable( true);
		setTitle( "Settings");
		setDialogDescription( "Specify the global DogsBay XML settings.");

		JPanel main = new JPanel( new BorderLayout());
		main.setBorder( new EmptyBorder( 5, 5, 5, 5));

		// Category names for the left list
		// Server and Plugins first: what someone opens this dialog to change.
		// Print is gone for now — printing still reads its settings, but nobody
		// came here to set a print font.
		String[] categories = { "Server", "Plugins", "Bindings", "Text", "Views", "XML", "Format", "System" };

		// Left panel: category list
		categoryList = new JList<>( categories);
		categoryList.setSelectionMode( ListSelectionModel.SINGLE_SELECTION);
		categoryList.setFixedCellHeight( 32);
		categoryList.setSelectedIndex( 0);
		JScrollPane listScroller = new JScrollPane( categoryList);
		listScroller.setBorder( new MatteBorder( 0, 0, 0, 1, UIManager.getColor( "Separator.foreground")));
		listScroller.setPreferredSize( new Dimension( 140, 0));

		// Right panel: card layout
		cardLayout = new CardLayout();
		cardPanel = new JPanel( cardLayout);

		cardPanel.add( wrapInScrollPane( createServerTab()), "Server");
		cardPanel.add( wrapInScrollPane( createKeysTab()), "Bindings");
		cardPanel.add( wrapInScrollPane( createTextTab()), "Text");
		cardPanel.add( wrapInScrollPane( createViewsTab()), "Views");
		cardPanel.add( wrapInScrollPane( createXMLTab()), "XML");
		cardPanel.add( wrapInScrollPane( createFormatTab()), "Format");
		cardPanel.add( wrapInScrollPane( createSystemTab()), "System");
		pluginManagerPanel = new com.dogsbay.dogsbayaieditor.plugin.PluginManagerPanel(
				parent.getPluginManager());
		cardPanel.add( pluginManagerPanel, "Plugins");

		// Wire up selection listener
		categoryList.addListSelectionListener( new ListSelectionListener() {
			public void valueChanged( ListSelectionEvent e) {
				if ( !e.getValueIsAdjusting()) {
					String selected = categoryList.getSelectedValue();
					if ( selected != null) {
						cardLayout.show( cardPanel, selected);
					}
				}
			}
		});

		// Split pane
		JSplitPane splitPane = new JSplitPane( JSplitPane.HORIZONTAL_SPLIT, listScroller, cardPanel);
		splitPane.setDividerLocation( 140);
		splitPane.setDividerSize( 1);
		splitPane.setBorder( null);

		main.add( splitPane, BorderLayout.CENTER);

		setContentPane( main);

		setDefaultCloseOperation( HIDE_ON_CLOSE);

		setSize( SIZE);
		setMinimumSize( new Dimension( 600, 450));

		setLocationRelativeTo( parent);
	}
	
	protected void okButtonPressed() {
		if ( isVisible()) {
			String selectedFont = (String)fontSelectionBox.getSelectedItem();
			int size = Integer.parseInt( (String)sizeSelectionBox.getSelectedItem());
			int tabSize = Integer.parseInt( (String)tabSizeBox.getSelectedItem());

			if ( selectedFont != null) {
				// Use FontManager to create font (handles bundled font markers)
				FontManager fontManager = FontManager.getInstance();
				Font font = fontManager.createFont(selectedFont, size);
				textPreferences.setFont( font);
			}
	
			for ( int i = 0; i < styles.size(); i++) {
				FontStyle style = (FontStyle)styles.elementAt(i);
				style.update();
			}
			
			textPreferences.setConvertTab( convertTabBox.isSelected());
			textPreferences.setSpaces( tabSize);
	
			// Formatter hard-wrap flag (drives DogsBayXMLWriter's max line length).
			// Editor soft-wrap is a separate setting — see View > Soft Wrapping.
			editorProperties.setWrapText( wrapTextCheck.isSelected());

			// House style: only write the PERSONAL default (and personal format-on-save)
			// when the panel is showing the user default. When it's showing a project's
			// style, OK must not overwrite the personal default — project edits are saved
			// explicitly via "Save to project".
			if ( !fsEditingProjectStyle) {
				editorProperties.setFormatStyle( houseStyleFromControls());
				editorProperties.setFormatOnSave( fsFormatOnSave.isSelected());
				com.dogsbay.dogsbayaieditor.format.FormatOnSaveHook.setUserEnabled( fsFormatOnSave.isSelected());
			}
	
			int value = -1;
			try {
				wrappingColumnField.commitEdit();
				value = ((Long)wrappingColumnField.getValue()).intValue();
			} catch( ParseException e) { 
				e.printStackTrace();
			}
	
			editorProperties.setWrappingColumn( value);
			
			if ( customFormatterRadio.isSelected()) {
				editorProperties.setFormatType( EditorProperties.FORMAT_CUSTOM);
	
				editorProperties.setCustomPadText( padTextCheck.isSelected());
				editorProperties.setCustomIndent( indentCheck.isSelected());
				editorProperties.setCustomNewline( newlinesCheck.isSelected());
				editorProperties.setCustomStrip( stripWhitespaceCheck.isSelected());
				editorProperties.setCustomPreserveMixedContent( preserveMixedContentCheck.isSelected());
			} else if ( compactFormatterRadio.isSelected()) {
				editorProperties.setFormatType( EditorProperties.FORMAT_COMPACT);
			} else { // standardFormatterRadio.isSelected()
				editorProperties.setFormatType( EditorProperties.FORMAT_STANDARD);
			}
		
			// Editor boxes
	//		editorProperties.setIndentMixedContent( indentMixedContentBox.isSelected());
//			editorProperties.setTagCompletion( tagCompletionBox.isSelected());
//			editorProperties.setSoftWrapping( softWrapBox.isSelected());
//			editorProperties.setShowMargin( showMarginBox.isSelected());
//			editorProperties.setShowOverviewMargin( showOverviewMarginBox.isSelected());
//			editorProperties.setShowFoldingMargin( showFoldingMarginBox.isSelected());
//			editorProperties.setSmartIndentation( autoIndentationBox.isSelected());
//			editorProperties.setTextPrompting( textPromptingBox.isSelected());
			textPreferences.setAntialiasing( antialiasingBox.isSelected());
			properties.setUniqueXPath( uniqueXPathBox.isSelected());
			properties.setMultipleDocumentOccurrences( multipleDocumentsBox.isSelected());
			
			//		editorProperties.setStrictTextPrompting( strictTextPromptingBox.isSelected());
	
//			designerProperties.setAutoCreateRequired( autoCreateRequiredBox.isSelected());
//			designerProperties.setShowAttributeValues( showAttributeValuesBox.isSelected());
//			designerProperties.setShowElementValues( showElementValuesBox.isSelected());
	
			properties.setAutoSyncSelection( autoSyncSelectionBox.isSelected());
			properties.setScrollDocumentTabs( scrollDocumentTabsBox.isSelected());
			properties.setReopenSessionFiles( reopenSessionFilesBox.isSelected());

			properties.setShowFullPath( showFullPathBox.isSelected());
	//		navigatorProperties.setShowAttributeValues( showNavigatorAttributesBox.isSelected());
	
			properties.setCheckTypeOnOpening( checkTypeOnOpeningBox.isSelected());
			properties.setPromptCreateTypeOnOpening( promptCreateTypeOnOpeningBox.isSelected());
			properties.setValidateOnOpening( validateOnOpeningBox.isSelected());
			properties.setUseInternalSchema( useInternalSchemaBox.isSelected());
	
			properties.setHideExecuteScenarioDialogWhenComplete( hideScenarioExecutionDialogWhenCompleteBox.isSelected());
			properties.setOpenXIncludeInNewDocument( openXIncludeInNewDocumentBox.isSelected());
	
			String port = proxyPortField.getText();
			String host = proxyHostField.getText();
			
			properties.setUseProxy( useProxyCheck.isSelected());
			properties.setProxyPort( port);
			properties.setProxyHost( host);
			
			
			if ( useProxyCheck.isSelected()) {
				System.setProperty( "http.proxyHost", host);
				System.setProperty( "http.proxyPort", port);
			} else {
				System.getProperties().remove( "http.proxyHost");
				System.getProperties().remove( "http.proxyPort");
			}
	
			if ( browserField != null) {
				String browser = browserField.getText();
				
				if ( browser != null && browser.trim().length() > 0) {
					System.setProperty( "org.bounce.browser", host);
				} else {
					System.getProperties().remove( "org.bounce.browser");
				}
		
				properties.setBrowser( browser);
			}
	
	//		try {
	//
	//		} catch( ParseException e) { 
	//			e.printStackTrace();
	//		}
	
	//		try {
	//
	//		} catch( ParseException e) { 
	//			e.printStackTrace();
	//		}
	
			int lafIndex = lafSelectionBox.getSelectedIndex();
			
			if ( lafIndex >= 0 ) {
				UIManager.LookAndFeelInfo[] info = UIManager.getInstalledLookAndFeels();
				
				if ( lafIndex < info.length) {
					properties.setLookAndFeel( info[ lafIndex].getClassName());
				}
			}
			
			properties.setPreferPublicIdentifiers( preferPublicIdentifiersBox.isSelected());
			
			// extensions
			//intital list of extensions: initialExtensions
			//current list of extensions: currentExtensions
			//for each extension in the initial vector, search for it in the current vector
			//if found, remove it from the currentVector and do nothing
			//if not found, need to delete it from the properties and current
			//at the end, any extensions that are left in the current vector are new ones
			//so these must be loaded and added to the properties
			
			int initialSize = initialExtensions.size();
			for(int cnt=0;cnt<initialSize;++cnt) {
			    String initialPath = (String)initialExtensions.get(cnt);
			    
			    //see if it exists in the current vector
			    int currentSize = currentExtensions.size();
			    boolean itExists = false;
			    for(int icnt=0;icnt<currentSize;++icnt) {
			        
			        String currentPath = (String)currentExtensions.get(icnt);
			        if(currentPath.equalsIgnoreCase(initialPath)) {
			            itExists = true;
			            
			            //escape the loop
			            icnt=currentSize;
			            
			        }
			        
			    }
			    
			    if(itExists) {
			        //if it exists in the current vector, just remove it from the current
			        removeStringFromVector(initialPath, currentExtensions);
			        
			    }
			    else {
			        //need to delete it from the properties and then delete it from current
			        removeStringFromVector(initialPath, currentExtensions);
			        properties.removeExtension( initialPath);
			    }
			    			    
			    
			}
			
			//now check the current vector
			
			int currentSize = currentExtensions.size();
			if(currentSize>0) {
			    
			    ClassLoader classLoader = getClass().getClassLoader();
			    
			    //need to add these to properties and load them with classloader
			    for(int cnt=0;cnt<currentSize;++cnt) {
			        
			        String currentPath = (String)currentExtensions.get(cnt);
			        
			        if ( classLoader instanceof ExtensionClassLoader) {
						((ExtensionClassLoader)classLoader).addExtension( new File( currentPath));
					}
			
					properties.addExtension( currentPath);
			    }
			}
			
			/*for ( int i = 0; i < removedExtensions.size(); i++) {
				properties.removeExtension( (String)removedExtensions.elementAt( i));
			}
	
			ClassLoader classLoader = getClass().getClassLoader();
	
			for ( int i = 0; i < addedExtensions.size(); i++) {
				String addedExtension = (String)addedExtensions.elementAt( i);
	
				if ( classLoader instanceof ExtensionClassLoader) {
					((ExtensionClassLoader)classLoader).addExtension( new File( addedExtension));
				}
		
				properties.addExtension( addedExtension);
			}*/

			// catalogs
			saveCatalogs();
			/*for ( int i = 0; i < removedCatalogs.size(); i++) {
				properties.removeCatalog( (String)removedCatalogs.elementAt( i));
			}
	
			for ( int i = 0; i < addedCatalogs.size(); i++) {
				properties.addCatalog( (String)addedCatalogs.elementAt( i));
			}*/
			
			prefixNamespaceMappingPanel.save();
			
//			properties.setResolveEntities( resolveEntitiesCheck.isSelected());
			properties.setLoadDTDGrammar( loadDTDGrammarCheck.isSelected());

//			XMLUtilities.setResolveEntities( resolveEntitiesCheck.isSelected());
			XMLUtilities.setLoadDTDGrammar( loadDTDGrammarCheck.isSelected());

			
			// viewer
//			viewerProperties.showAttributes( showAttributes.isSelected());
//			viewerProperties.showNamespaces( showNamespaces.isSelected());
//			viewerProperties.showComments( showComments.isSelected());
//			viewerProperties.showValues( showValues.isSelected());
//			viewerProperties.showInline( showInline.isSelected());
//			viewerProperties.showPI( showPI.isSelected());
			
			
			// Only what the reader changed is stored; the defaults stay in the
			// product, so improving one reaches everybody who never chose.
			if ( keyBindings != null) {
				properties.setKeyBindingOverrides( keyBindings.toStore());
				// Onto the shared instance too: the menus, the editor's own
				// input maps and the clash checks all read that one, and without
				// this they kept the keys they were built with until a restart.
				properties.getKeyBindings().adopt( keyBindings);
				properties.getKeyBindings().applyTo( parent);
			}
	
			parent.updatePreferences();

			// Save plugin enable/disable state
			if (pluginManagerPanel != null) {
				pluginManagerPanel.saveState(properties);
			}

			// Integration server settings + apply at runtime
			boolean serverWasEnabled = properties.isMcpServerEnabled();
			properties.setMcpServerEnabled( mcpServerEnabledBox.isSelected());
			properties.setMcpServerPort( parsePortField());
			properties.setMcpEndpointEnabled( mcpEndpointBox.isSelected());
			properties.setRpcEndpointEnabled( rpcEndpointBox.isSelected());
			properties.setAcpDefaultTier( com.dogsbay.agent.session.CapabilityTier.values()[acpDefaultTierCombo.getSelectedIndex()]);
			properties.setAgentProposalsEnabled( agentProposalsBox.isSelected());
			properties.setAgentSessionRetentionDays(
					RETENTION_DAYS[ sessionRetentionCombo.getSelectedIndex()]);

			var ipcManager = parent.getIpcServerManager();
			if (ipcManager != null) {
				if (mcpServerEnabledBox.isSelected()) {
					// (re)start to pick up port/endpoint changes
					ipcManager.restart();
				} else if (serverWasEnabled) {
					ipcManager.stop();
				}
				// A hosted agent is handed its tools at session start, so the
				// ones already running cannot pick this up. Tell them.
				com.dogsbay.dogsbayaieditor.plugin.agent.AgentPlugin.integrationServerChanged();
			}

			properties.save();

			super.okButtonPressed();
		}
	}
	
	private void saveCatalogs() {
		// remove previous catalogs...
	    
		Vector catalogs = properties.getCatalogs();
				
		for ( int i = 0; i < catalogs.size(); i++) {
			String catalog = (String)catalogs.elementAt(i);
			properties.removeCatalog(catalog);
		}

		// add current catalogs...
		for ( int i = 0; i < catalogsModel.getSize(); i++) {
			String catalog = (String)catalogsModel.elementAt( i);
			properties.addCatalog(catalog);
		}
				
	}

	protected void cancelButtonPressed() {
		//setVisible( false);
	    super.cancelButtonPressed();
	}

	private JPanel createTextTab() {
		JPanel panel = createFontsTextPanel();
		panel.setBorder( new EmptyBorder( 5, 5, 5, 5));
		return panel;
	}

	private JPanel createFontsTextPanel() {
		JPanel panel = new JPanel( new FormLayout( 0, 5));
		panel.setBorder( new EmptyBorder( 5, 0, 5, 0));

		// Use FontManager for enhanced font selection (bundled + system monospace fonts)
		FontManager fontManager = FontManager.getInstance();
		List<String> availableFonts = fontManager.getAvailableFonts();

		Vector names = new Vector();
		for (String fontFamily : availableFonts) {
			// Add display name (bundled fonts marked with ★)
			names.addElement(fontManager.getDisplayName(fontFamily));
		}

		// Keep original getAllFonts() for compatibility
		GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
		fonts = env.getAllFonts();

		fontSelectionBox = new JComboBox( names);
		fontSelectionBox.addItemListener( new ItemListener() {
			public void itemStateChanged( ItemEvent e) {
				updateEditor();
			}
		});
		sizeSelectionBox = new JComboBox( FONT_SIZES);
		sizeSelectionBox.addItemListener( new ItemListener() {
			public void itemStateChanged( ItemEvent e) {
				updateEditor();
			}
		});

		JPanel fontSpecificationPanel = new JPanel( new BorderLayout());
		JPanel fontSelectionPanel = new JPanel( new BorderLayout());
		JPanel fontSizePanel = new JPanel( new FlowLayout( FlowLayout.RIGHT, 5, 0));
		JLabel fontSizeLabel = new JLabel( "Size:");
		JLabel fontSelectionLabel = new JLabel( "Font:");
		fontSelectionLabel.setBorder( new EmptyBorder( 0, 0, 0, 5));
		fontSelectionPanel.add( fontSelectionLabel, BorderLayout.WEST);
		fontSelectionPanel.add( fontSelectionBox, BorderLayout.CENTER);
		
		fontSizePanel.add( fontSizeLabel);
		fontSizePanel.add( sizeSelectionBox);

		fontSpecificationPanel.add( fontSelectionPanel, BorderLayout.CENTER);
		fontSpecificationPanel.add( fontSizePanel, BorderLayout.EAST);
		fontSpecificationPanel.setBorder( new CompoundBorder( 
											new TitledBorder( "Font Selection"),
											new EmptyBorder( 0, 5, 5, 0)));
		
		panel.add( fontSpecificationPanel, FormLayout.FULL_FILL);
		
		Font defaultFont = TextPreferences.getDefaultFont();
		boolean canbeBold = hasSameWidth( defaultFont, Font.PLAIN, Font.BOLD);
		
		styles = new Vector();
		styles.addElement( new FontStyle( "Element Name", TextPreferences.getFontType( TextPreferences.ELEMENT_NAME), Constants.ELEMENT_NAME, TextPreferences.DEFAULT_ELEMENT_NAME_STYLE, TextPreferences.DEFAULT_ELEMENT_NAME_COLOR));
		
		if (canbeBold)
			styles.addElement( new FontStyle( "Content", TextPreferences.getFontType( TextPreferences.ELEMENT_VALUE), Constants.ELEMENT_VALUE, TextPreferences.DEFAULT_ELEMENT_VALUE_STYLE, TextPreferences.DEFAULT_ELEMENT_VALUE_COLOR));
		else
			styles.addElement( new FontStyle( "Content", TextPreferences.getFontType( TextPreferences.ELEMENT_VALUE), Constants.ELEMENT_VALUE, Font.PLAIN, TextPreferences.DEFAULT_ELEMENT_VALUE_COLOR));

		styles.addElement( new FontStyle( "Attribute Name", TextPreferences.getFontType( TextPreferences.ATTRIBUTE_NAME), Constants.ATTRIBUTE_NAME, TextPreferences.DEFAULT_ATTRIBUTE_NAME_STYLE, TextPreferences.DEFAULT_ATTRIBUTE_NAME_COLOR));
		
		if (canbeBold)
			styles.addElement( new FontStyle( "Attribute Value", TextPreferences.getFontType( TextPreferences.ATTRIBUTE_VALUE), Constants.ATTRIBUTE_VALUE, TextPreferences.DEFAULT_ATTRIBUTE_VALUE_STYLE, TextPreferences.DEFAULT_ATTRIBUTE_VALUE_COLOR));
		else
			styles.addElement( new FontStyle( "Attribute Value", TextPreferences.getFontType( TextPreferences.ATTRIBUTE_VALUE), Constants.ATTRIBUTE_VALUE, Font.PLAIN, TextPreferences.DEFAULT_ATTRIBUTE_VALUE_COLOR));

		styles.addElement( new FontStyle( "Namespace Prefix", TextPreferences.getFontType( TextPreferences.PREFIX), -1, TextPreferences.DEFAULT_PREFIX_STYLE, TextPreferences.DEFAULT_PREFIX_COLOR));
		styles.addElement( new FontStyle( "Namespace Name", TextPreferences.getFontType( TextPreferences.NAMESPACE_NAME), Constants.NAMESPACE_NAME, TextPreferences.DEFAULT_NAMESPACE_NAME_STYLE, TextPreferences.DEFAULT_NAMESPACE_NAME_COLOR));
		
		if (canbeBold)
			styles.addElement( new FontStyle( "Namespace Value", TextPreferences.getFontType( TextPreferences.NAMESPACE_VALUE), Constants.NAMESPACE_VALUE, TextPreferences.DEFAULT_NAMESPACE_VALUE_STYLE, TextPreferences.DEFAULT_NAMESPACE_VALUE_COLOR));
		else
			styles.addElement( new FontStyle( "Namespace Value", TextPreferences.getFontType( TextPreferences.NAMESPACE_VALUE), Constants.NAMESPACE_VALUE, Font.PLAIN, TextPreferences.DEFAULT_NAMESPACE_VALUE_COLOR));

		styles.addElement( new FontStyle( "Comment", TextPreferences.getFontType( TextPreferences.COMMENT), Constants.COMMENT, TextPreferences.DEFAULT_COMMENT_STYLE, TextPreferences.DEFAULT_COMMENT_COLOR));
		styles.addElement( new FontStyle( "CDATA", TextPreferences.getFontType( TextPreferences.CDATA), Constants.CDATA, TextPreferences.DEFAULT_CDATA_STYLE, TextPreferences.DEFAULT_CDATA_COLOR));
		styles.addElement( new FontStyle( "Special", TextPreferences.getFontType( TextPreferences.SPECIAL), Constants.SPECIAL, TextPreferences.DEFAULT_SPECIAL_STYLE, TextPreferences.DEFAULT_SPECIAL_COLOR));

		styles.addElement( new FontStyle( "PI Target", TextPreferences.getFontType( TextPreferences.PI_TARGET), Constants.PI_TARGET, Font.PLAIN, TextPreferences.PI_TARGET_COLOR));
		styles.addElement( new FontStyle( "PI Name", TextPreferences.getFontType( TextPreferences.PI_NAME), Constants.PI_NAME, Font.PLAIN, TextPreferences.PI_NAME_COLOR));
		styles.addElement( new FontStyle( "PI Value", TextPreferences.getFontType( TextPreferences.PI_VALUE), Constants.PI_VALUE, Font.PLAIN, TextPreferences.PI_VALUE_COLOR));

		styles.addElement( new FontStyle( "DTD: String Value", TextPreferences.getFontType( TextPreferences.STRING_VALUE), Constants.STRING_VALUE, Font.PLAIN, TextPreferences.STRING_VALUE_COLOR));
		styles.addElement( new FontStyle( "DTD: Entity Reference", TextPreferences.getFontType( TextPreferences.ENTITY_VALUE), Constants.ENTITY_VALUE, Font.PLAIN, TextPreferences.ENTITY_VALUE_COLOR));

		styles.addElement( new FontStyle( "DTD: ENTITY Declaration", TextPreferences.getFontType( TextPreferences.ENTITY_DECLARATION), Constants.ENTITY_DECLARATION, Font.PLAIN, TextPreferences.ENTITY_DECLARATION_COLOR));
		styles.addElement( new FontStyle( "DTD: Entity Name", TextPreferences.getFontType( TextPreferences.ENTITY_NAME), Constants.ENTITY_NAME, Font.PLAIN, TextPreferences.ENTITY_NAME_COLOR));
		styles.addElement( new FontStyle( "DTD: Entity Type", TextPreferences.getFontType( TextPreferences.ENTITY_TYPE), Constants.ENTITY_TYPE, Font.PLAIN, TextPreferences.ENTITY_TYPE_COLOR));

		styles.addElement( new FontStyle( "DTD: ATTLIST Declaration", TextPreferences.getFontType( TextPreferences.ATTLIST_DECLARATION), Constants.ATTLIST_DECLARATION, Font.PLAIN, TextPreferences.ATTLIST_DECLARATION_COLOR));
		styles.addElement( new FontStyle( "DTD: Attribute Name", TextPreferences.getFontType( TextPreferences.ATTLIST_NAME), Constants.ATTLIST_NAME, Font.PLAIN, TextPreferences.ATTLIST_NAME_COLOR));
		styles.addElement( new FontStyle( "DTD: Attribute Type", TextPreferences.getFontType( TextPreferences.ATTLIST_TYPE), Constants.ATTLIST_TYPE, Font.PLAIN, TextPreferences.ATTLIST_TYPE_COLOR));
		styles.addElement( new FontStyle( "DTD: Attribute Enumeration", TextPreferences.getFontType( TextPreferences.ATTLIST_VALUE), Constants.ATTLIST_VALUE, Font.PLAIN, TextPreferences.ATTLIST_VALUE_COLOR));
		styles.addElement( new FontStyle( "DTD: Attribute Defaults", TextPreferences.getFontType( TextPreferences.ATTLIST_DEFAULT), Constants.ATTLIST_DEFAULT, Font.PLAIN, TextPreferences.ATTLIST_DEFAULT_COLOR));
	
		styles.addElement( new FontStyle( "DTD: ELEMENT Declaration", TextPreferences.getFontType( TextPreferences.ELEMENT_DECLARATION), Constants.ELEMENT_DECLARATION, Font.PLAIN, TextPreferences.ELEMENT_DECLARATION_COLOR));
		styles.addElement( new FontStyle( "DTD: Element Name", TextPreferences.getFontType( TextPreferences.ELEMENT_DECLARATION_NAME), Constants.ELEMENT_DECLARATION_NAME, Font.PLAIN, TextPreferences.ELEMENT_DECLARATION_NAME_COLOR));
		styles.addElement( new FontStyle( "DTD: Element Type", TextPreferences.getFontType( TextPreferences.ELEMENT_DECLARATION_TYPE), Constants.ELEMENT_DECLARATION_TYPE, Font.PLAIN, TextPreferences.ELEMENT_DECLARATION_TYPE_COLOR));
		styles.addElement( new FontStyle( "DTD: #PCDATA", TextPreferences.getFontType( TextPreferences.ELEMENT_DECLARATION_PCDATA), Constants.ELEMENT_DECLARATION_PCDATA, Font.PLAIN, TextPreferences.ELEMENT_DECLARATION_PCDATA_COLOR));
		styles.addElement( new FontStyle( "DTD: Element Operator", TextPreferences.getFontType( TextPreferences.ELEMENT_DECLARATION_OPERATOR), Constants.ELEMENT_DECLARATION_OPERATOR, Font.PLAIN, TextPreferences.ELEMENT_DECLARATION_OPERATOR_COLOR));

		styles.addElement( new FontStyle( "DTD: NOTATION Declaration", TextPreferences.getFontType( TextPreferences.NOTATION_DECLARATION), Constants.NOTATION_DECLARATION, Font.PLAIN, TextPreferences.NOTATION_DECLARATION_COLOR));
		styles.addElement( new FontStyle( "DTD: Notation Name", TextPreferences.getFontType( TextPreferences.NOTATION_DECLARATION_NAME), Constants.NOTATION_DECLARATION_NAME, Font.PLAIN, TextPreferences.NOTATION_DECLARATION_NAME_COLOR));
		styles.addElement( new FontStyle( "DTD: Notation Type", TextPreferences.getFontType( TextPreferences.NOTATION_DECLARATION_TYPE), Constants.NOTATION_DECLARATION_TYPE, Font.PLAIN, TextPreferences.NOTATION_DECLARATION_TYPE_COLOR));

		styles.addElement( new FontStyle( "DTD: DOCTYPE Declaration", TextPreferences.getFontType( TextPreferences.DOCTYPE_DECLARATION), Constants.DOCTYPE_DECLARATION, Font.PLAIN, TextPreferences.DOCTYPE_DECLARATION_COLOR));
		styles.addElement( new FontStyle( "DTD: Doctype Type", TextPreferences.getFontType( TextPreferences.DOCTYPE_DECLARATION_TYPE), Constants.DOCTYPE_DECLARATION_TYPE, Font.PLAIN, TextPreferences.DOCTYPE_DECLARATION_TYPE_COLOR));

		styles.addElement( new FontStyle( "Markdown: Text", TextPreferences.getFontType( TextPreferences.MD_TEXT), Constants.MD_TEXT, TextPreferences.DEFAULT_MD_TEXT_STYLE, TextPreferences.DEFAULT_MD_TEXT_COLOR));
		styles.addElement( new FontStyle( "Markdown: Header", TextPreferences.getFontType( TextPreferences.MD_HEADER), Constants.MD_HEADER, TextPreferences.DEFAULT_MD_HEADER_STYLE, TextPreferences.DEFAULT_MD_HEADER_COLOR));
		styles.addElement( new FontStyle( "Markdown: Emphasis", TextPreferences.getFontType( TextPreferences.MD_EMPHASIS), Constants.MD_EMPHASIS, TextPreferences.DEFAULT_MD_EMPHASIS_STYLE, TextPreferences.DEFAULT_MD_EMPHASIS_COLOR));
		styles.addElement( new FontStyle( "Markdown: Strong", TextPreferences.getFontType( TextPreferences.MD_STRONG), Constants.MD_STRONG, TextPreferences.DEFAULT_MD_STRONG_STYLE, TextPreferences.DEFAULT_MD_STRONG_COLOR));
		styles.addElement( new FontStyle( "Markdown: Inline Code", TextPreferences.getFontType( TextPreferences.MD_CODE), Constants.MD_CODE, TextPreferences.DEFAULT_MD_CODE_STYLE, TextPreferences.DEFAULT_MD_CODE_COLOR));
		styles.addElement( new FontStyle( "Markdown: Code Block", TextPreferences.getFontType( TextPreferences.MD_CODE_BLOCK), Constants.MD_CODE_BLOCK, TextPreferences.DEFAULT_MD_CODE_BLOCK_STYLE, TextPreferences.DEFAULT_MD_CODE_BLOCK_COLOR));
		styles.addElement( new FontStyle( "Markdown: Link", TextPreferences.getFontType( TextPreferences.MD_LINK), Constants.MD_LINK, TextPreferences.DEFAULT_MD_LINK_STYLE, TextPreferences.DEFAULT_MD_LINK_COLOR));
		styles.addElement( new FontStyle( "Markdown: URL", TextPreferences.getFontType( TextPreferences.MD_URL), Constants.MD_URL, TextPreferences.DEFAULT_MD_URL_STYLE, TextPreferences.DEFAULT_MD_URL_COLOR));
		styles.addElement( new FontStyle( "Markdown: Image", TextPreferences.getFontType( TextPreferences.MD_IMAGE), Constants.MD_IMAGE, TextPreferences.DEFAULT_MD_IMAGE_STYLE, TextPreferences.DEFAULT_MD_IMAGE_COLOR));
		styles.addElement( new FontStyle( "Markdown: Blockquote", TextPreferences.getFontType( TextPreferences.MD_BLOCKQUOTE), Constants.MD_BLOCKQUOTE, TextPreferences.DEFAULT_MD_BLOCKQUOTE_STYLE, TextPreferences.DEFAULT_MD_BLOCKQUOTE_COLOR));
		styles.addElement( new FontStyle( "Markdown: List", TextPreferences.getFontType( TextPreferences.MD_LIST), Constants.MD_LIST, TextPreferences.DEFAULT_MD_LIST_STYLE, TextPreferences.DEFAULT_MD_LIST_COLOR));
		styles.addElement( new FontStyle( "Markdown: Rule", TextPreferences.getFontType( TextPreferences.MD_RULE), Constants.MD_RULE, TextPreferences.DEFAULT_MD_RULE_STYLE, TextPreferences.DEFAULT_MD_RULE_COLOR));
		styles.addElement( new FontStyle( "Markdown: Table", TextPreferences.getFontType( TextPreferences.MD_TABLE), Constants.MD_TABLE, TextPreferences.DEFAULT_MD_TABLE_STYLE, TextPreferences.DEFAULT_MD_TABLE_COLOR));
		styles.addElement( new FontStyle( "Markdown: Strikethrough", TextPreferences.getFontType( TextPreferences.MD_STRIKETHROUGH), Constants.MD_STRIKETHROUGH, TextPreferences.DEFAULT_MD_STRIKETHROUGH_STYLE, TextPreferences.DEFAULT_MD_STRIKETHROUGH_COLOR));
		styles.addElement( new FontStyle( "Markdown: Task", TextPreferences.getFontType( TextPreferences.MD_TASK), Constants.MD_TASK, TextPreferences.DEFAULT_MD_TASK_STYLE, TextPreferences.DEFAULT_MD_TASK_COLOR));
		styles.addElement( new FontStyle( "Markdown: HTML", TextPreferences.getFontType( TextPreferences.MD_HTML), Constants.MD_HTML, TextPreferences.DEFAULT_MD_HTML_STYLE, TextPreferences.DEFAULT_MD_HTML_COLOR));
		styles.addElement( new FontStyle( "Markdown: YAML Front Matter", TextPreferences.getFontType( TextPreferences.MD_YAML), Constants.MD_YAML, TextPreferences.DEFAULT_MD_YAML_STYLE, TextPreferences.DEFAULT_MD_YAML_COLOR));

		stylePanel = new FontStylePanel( styles);

		JPanel typesPanel = new JPanel( new BorderLayout());
		typesPanel.setBorder( new CompoundBorder( 
									new TitledBorder( "Styles"),
									new EmptyBorder( 0, 5, 5, 5)));
		typesPanel.add( stylePanel, BorderLayout.CENTER);
		panel.add( typesPanel, FormLayout.FULL_FILL);
		
		JPanel tabPanel = new JPanel( new BorderLayout());
		tabPanel.setBorder( new CompoundBorder( 
									new TitledBorder( "Tab"),
									new EmptyBorder( 0, 5, 5, 0)));

		JPanel tabSizePanel = new JPanel( new FlowLayout( FlowLayout.RIGHT, 5, 0));
		tabSizeBox = new JComboBox( SPACES);
		tabSizeBox.addItemListener( new ItemListener() {
			public void itemStateChanged( ItemEvent e) {
				updateEditor();
			}
		});
		tabSizePanel.add( new JLabel( "Tab Size:"));
		tabSizePanel.add( tabSizeBox);

		convertTabBox = new JCheckBox( "Convert to Spaces");
//		convertTabBox.setFont( convertTabBox.getFont().deriveFont( Font.PLAIN));
		tabPanel.add( convertTabBox, BorderLayout.WEST);
		tabPanel.add( tabSizePanel, BorderLayout.EAST);

		panel.add( tabPanel, FormLayout.FULL_FILL);
		
		JButton defaultButton = new JButton( "Set Default");
		defaultButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e) {
				setDefault();
			}
		});
		antialiasingBox	= new JCheckBox( "Antialiase Text");
		antialiasingBox.addItemListener( new ItemListener() {
			public void itemStateChanged( ItemEvent e) {
				updateEditor();
			}
		});

		JPanel buttonPanel = new JPanel( new BorderLayout());
		buttonPanel.add( defaultButton, BorderLayout.EAST);
		buttonPanel.add( antialiasingBox, BorderLayout.WEST);

		panel.add( buttonPanel, FormLayout.FULL_FILL);

		return panel;
	}


	private void updateEditor() {
		String selectedFont = (String)fontSelectionBox.getSelectedItem();
		int size = Integer.parseInt( (String)sizeSelectionBox.getSelectedItem());

		if ( selectedFont != null) {
			// Use FontManager to create font (handles bundled font markers)
			FontManager fontManager = FontManager.getInstance();
			Font font = fontManager.createFont(selectedFont, size);

			boolean canbeBold = hasSameWidth( font, Font.PLAIN, Font.BOLD);
			boolean canbeItalic = hasSameWidth( font, Font.PLAIN, Font.ITALIC);
			stylePanel.reset( canbeItalic, canbeBold);

			for ( int i = 0; i < styles.size(); i++) {
				FontStyle style = (FontStyle)styles.elementAt(i);

				if ( !canbeItalic && style.isItalic()) {
					style.setStyle( Font.PLAIN);
				}

				if ( !canbeBold && style.isBold()) {
					style.setStyle( Font.PLAIN);
				}
			}
		}
	}

	private JComponent createViewsTab() {
		Box panel = Box.createVerticalBox();
		panel.setBorder( new EmptyBorder( 5, 5, 5, 5));
		
		// General stuff
		JPanel generalPanel 	= new JPanel( new FormLayout( 0, 5));
		generalPanel.setBorder( new CompoundBorder( 
									new TitledBorder( "General"),
									new EmptyBorder( 0, 5, 5, 0)));
		autoSyncSelectionBox	= new JCheckBox( "Synchronise Selection between Views");
		generalPanel.add( autoSyncSelectionBox, FormLayout.FULL);
		multipleDocumentsBox	= new JCheckBox( "Open multiple occurrences of the same Document");
		generalPanel.add( multipleDocumentsBox, FormLayout.FULL);
		scrollDocumentTabsBox	= new JCheckBox( "Scroll Document Tabs");
		generalPanel.add( scrollDocumentTabsBox, FormLayout.FULL);
		reopenSessionFilesBox	= new JCheckBox( "Reopen files from previous session");
		generalPanel.add( reopenSessionFilesBox, FormLayout.FULL);
		panel.add( generalPanel);

		JPanel typesPanel 	= new JPanel( new FormLayout( 0, 5));
		typesPanel.setBorder( new CompoundBorder( 
									new TitledBorder( "XML Types"),
									new EmptyBorder( 0, 5, 5, 0)));

		checkTypeOnOpeningBox	= new JCheckBox( "Check for Type opening Document");
		checkTypeOnOpeningBox.addItemListener( new ItemListener() {
			public void itemStateChanged( ItemEvent e) {
				promptCreateTypeOnOpeningBox.setEnabled( checkTypeOnOpeningBox.isSelected());
			}
		});
		typesPanel.add( checkTypeOnOpeningBox, FormLayout.FULL);
		
		promptCreateTypeOnOpeningBox	= new JCheckBox( "Prompt to create a Type when no Type found");
		promptCreateTypeOnOpeningBox.setEnabled( false);

		JPanel promptPanel = new JPanel( new BorderLayout());
		promptPanel.setBorder( new EmptyBorder( 0, 20, 0, 0));
		promptPanel.add( promptCreateTypeOnOpeningBox, BorderLayout.CENTER);
		typesPanel.add( promptPanel, FormLayout.FULL);

		validateOnOpeningBox	= new JCheckBox( "Validate Document on opening");
		typesPanel.add( validateOnOpeningBox, FormLayout.FULL);
		useInternalSchemaBox	= new JCheckBox( "Set Schema or DTD defined in Document");
		typesPanel.add( useInternalSchemaBox, FormLayout.FULL);
		
		panel.add( typesPanel);

		JPanel scenariosPanel 	= new JPanel( new FormLayout( 0, 5));
		scenariosPanel.setBorder( new CompoundBorder( 
									new TitledBorder( "Scenario Execution"),
									new EmptyBorder( 0, 5, 5, 0)));

		hideScenarioExecutionDialogWhenCompleteBox	= new JCheckBox( "Hide Scenario Execution Dialog when complete");
		scenariosPanel.add( hideScenarioExecutionDialogWhenCompleteBox, FormLayout.FULL);

		panel.add( scenariosPanel);

		JPanel xincludePanel 	= new JPanel( new FormLayout( 0, 5));
		xincludePanel.setBorder( new CompoundBorder( 
									new TitledBorder( "XInclude"),
									new EmptyBorder( 0, 5, 5, 0)));

		openXIncludeInNewDocumentBox	= new JCheckBox( "Open Resolve XInclude in New Document");
		xincludePanel.add( openXIncludeInNewDocumentBox, FormLayout.FULL);

		panel.add( xincludePanel);

		JPanel xpathPanel 	= new JPanel( new FormLayout( 0, 5));
		xpathPanel.setBorder( new CompoundBorder( 
									new TitledBorder( "XPath Editor"),
									new EmptyBorder( 0, 5, 5, 0)));

		uniqueXPathBox			= new JCheckBox( "Generate Unique XPath");
		xpathPanel.add( uniqueXPathBox, FormLayout.FULL);

		panel.add( xpathPanel);

		//		attributesNewLineBox	= new JCheckBox( "Place Attribute/Namespace on a new Line");
//		generalPanel.add( attributesNewLineBox, FormLayout.FULL);

		JPanel projectPanel 	= new JPanel( new FormLayout( 0, 5));
		projectPanel.setBorder( new CompoundBorder( 
									new TitledBorder( "Project"),
									new EmptyBorder( 0, 5, 5, 0)));

		showFullPathBox	= new JCheckBox( "Show full Path for Documents");
		projectPanel.add( showFullPathBox, FormLayout.FULL);

		panel.add( projectPanel);

		return panel;
	}

	private JPanel createFormatTab() {
		// The legacy Custom/Compact/Standard formatter controls and Wrap Text/Wrapping
		// Column are no longer shown — the main format path uses the house style
		// (FormatStyle) below. They stay instantiated (off-screen) so the save/load code
		// and the legacy SOAP/WSDL/SQL-import paths keep their stored values.
		wrapTextCheck = new JCheckBox( "Wrap Text");
		NumberFormat legacyFormat = NumberFormat.getIntegerInstance();
		legacyFormat.setMinimumIntegerDigits( 0);
		wrappingColumnField = new JFormattedTextField( legacyFormat);
		customFormatterRadio	= new JRadioButton( "Custom Formatter");
		compactFormatterRadio	= new JRadioButton( "Compact Formatter");
		standardFormatterRadio	= new JRadioButton( "Standard Formatter");
		ButtonGroup legacyGroup = new ButtonGroup();
		legacyGroup.add( customFormatterRadio);
		legacyGroup.add( compactFormatterRadio);
		legacyGroup.add( standardFormatterRadio);
		indentCheck					= new JCheckBox( "Indent");
		padTextCheck				= new JCheckBox( "Pad Text");
		newlinesCheck				= new JCheckBox( "Newlines");
		stripWhitespaceCheck		= new JCheckBox( "Trim Text");
		preserveMixedContentCheck	= new JCheckBox( "Preserve Mixed Content");

		JPanel panel = new JPanel( new FormLayout( 0, 5));
		panel.setBorder( new EmptyBorder( 5, 5, 5, 5));

		JPanel housePanel = new JPanel( new FormLayout( 0, 5));
		housePanel.setBorder( new CompoundBorder(
									new TitledBorder( "House Style"),
									new EmptyBorder( 0, 5, 5, 5)));

		// Source indicator (project vs default) — set in the load step.
		fsSourceLabel = new JLabel( " ");
		housePanel.add( fsSourceLabel, FormLayout.FULL);

		// Indent: [unit] Size [n]
		fsIndentUnit = new JComboBox<>( new String[] { "Spaces", "Tabs" });
		fsIndentSize = intField();
		JPanel indentRow = new JPanel( new FlowLayout( FlowLayout.LEFT, 5, 0));
		indentRow.add( new JLabel( "Indent:"));
		indentRow.add( fsIndentUnit);
		indentRow.add( new JLabel( "Size:"));
		indentRow.add( fsIndentSize);
		housePanel.add( indentRow, FormLayout.FULL);

		// Newline: [style]  [x] Final newline
		fsNewline = new JComboBox<>( new String[] { "LF", "CRLF", "Platform" });
		fsFinalNewline = new JCheckBox( "Final newline");
		JPanel newlineRow = new JPanel( new FlowLayout( FlowLayout.LEFT, 5, 0));
		newlineRow.add( new JLabel( "Newline:"));
		newlineRow.add( fsNewline);
		newlineRow.add( Box.createHorizontalStrut( 10));
		newlineRow.add( fsFinalNewline);
		housePanel.add( newlineRow, FormLayout.FULL);

		fsPreserveMixed = new JCheckBox( "Preserve mixed content (keep inline markup inline)");
		housePanel.add( fsPreserveMixed, FormLayout.FULL);

		fsPreserveBlank = new JCheckBox( "Preserve blank lines (keep one, collapse extras)");
		housePanel.add( fsPreserveBlank, FormLayout.FULL);

		fsSemanticBreaks = new JCheckBox( "Semantic line breaks (keep one sentence per line)");
		housePanel.add( fsSemanticBreaks, FormLayout.FULL);

		fsTrimWhitespace = new JCheckBox( "Trim insignificant whitespace (collapse extra spaces)");
		housePanel.add( fsTrimWhitespace, FormLayout.FULL);

		// Continuation: [Block|Flush]
		fsContinuation = new JComboBox<>( new String[] { "Block", "Flush" });
		JPanel continuationRow = new JPanel( new FlowLayout( FlowLayout.LEFT, 5, 0));
		continuationRow.add( new JLabel( "Sentence continuation indent:"));
		continuationRow.add( fsContinuation);
		housePanel.add( continuationRow, FormLayout.FULL);

		// Max line width (0 = no hard wrap)
		fsMaxWidth = intField();
		JPanel widthRow = new JPanel( new FlowLayout( FlowLayout.LEFT, 5, 0));
		widthRow.add( new JLabel( "Max line width:"));
		widthRow.add( fsMaxWidth);
		widthRow.add( new JLabel( "(0 = no hard wrap)"));
		housePanel.add( widthRow, FormLayout.FULL);

		panel.add( housePanel, FormLayout.FULL_FILL);

		// Behaviour + project actions
		JPanel actionPanel = new JPanel( new FormLayout( 0, 5));
		actionPanel.setBorder( new CompoundBorder(
									new TitledBorder( "Apply"),
									new EmptyBorder( 0, 5, 5, 5)));

		fsFormatOnSave = new JCheckBox( "Format on save");
		actionPanel.add( fsFormatOnSave, FormLayout.FULL);

		fsSaveToProject = new JButton( "Save to project (.dogsbay/config.xml)…");
		fsSaveToProject.addActionListener( e -> saveHouseStyleToProject());
		JPanel saveRow = new JPanel( new FlowLayout( FlowLayout.LEFT, 0, 0));
		saveRow.add( fsSaveToProject);
		actionPanel.add( saveRow, FormLayout.FULL);

		panel.add( actionPanel, FormLayout.FULL_FILL);

		return panel;
	}

	/** A small right-aligned integer field (shared style for size/width). */
	private JFormattedTextField intField() {
		NumberFormat fmt = NumberFormat.getIntegerInstance();
		fmt.setMinimumIntegerDigits( 0);
		fmt.setGroupingUsed( false);
		JFormattedTextField field = new JFormattedTextField( fmt);
		field.setPreferredSize( new Dimension( 50, field.getPreferredSize().height));
		field.setHorizontalAlignment( JTextField.RIGHT);
		return field;
	}

	/** Build a FormatStyle from the House Style controls (preserve-space elements unchanged). */
	private com.dogsbay.xml.format.FormatStyle houseStyleFromControls() {
		com.dogsbay.xml.format.FormatStyle d = com.dogsbay.xml.format.FormatStyle.defaults();
		com.dogsbay.xml.format.FormatStyle.IndentUnit unit = fsIndentUnit.getSelectedIndex() == 1
				? com.dogsbay.xml.format.FormatStyle.IndentUnit.TABS
				: com.dogsbay.xml.format.FormatStyle.IndentUnit.SPACES;
		com.dogsbay.xml.format.FormatStyle.NewlineStyle newline = switch ( fsNewline.getSelectedIndex()) {
			case 1 -> com.dogsbay.xml.format.FormatStyle.NewlineStyle.CRLF;
			case 2 -> com.dogsbay.xml.format.FormatStyle.NewlineStyle.PLATFORM;
			default -> com.dogsbay.xml.format.FormatStyle.NewlineStyle.LF;
		};
		com.dogsbay.xml.format.FormatStyle.TextContinuation continuation = fsContinuation.getSelectedIndex() == 1
				? com.dogsbay.xml.format.FormatStyle.TextContinuation.FLUSH
				: com.dogsbay.xml.format.FormatStyle.TextContinuation.BLOCK;
		return new com.dogsbay.xml.format.FormatStyle(
				unit, intValue( fsIndentSize, d.indentSize()), intValue( fsMaxWidth, d.maxLineWidth()),
				fsPreserveMixed.isSelected(), d.preserveSpaceElements(), newline,
				fsFinalNewline.isSelected(), fsSemanticBreaks.isSelected(), fsPreserveBlank.isSelected(),
				continuation, fsTrimWhitespace.isSelected());
	}

	/** Populate the House Style controls from a FormatStyle. */
	private void houseStyleToControls( com.dogsbay.xml.format.FormatStyle s) {
		fsIndentUnit.setSelectedIndex( s.unit() == com.dogsbay.xml.format.FormatStyle.IndentUnit.TABS ? 1 : 0);
		fsIndentSize.setValue( Integer.valueOf( s.indentSize()));
		fsMaxWidth.setValue( Integer.valueOf( s.maxLineWidth()));
		fsPreserveMixed.setSelected( s.preserveMixed());
		fsNewline.setSelectedIndex( switch ( s.newline()) {
			case CRLF -> 1; case PLATFORM -> 2; default -> 0; });
		fsFinalNewline.setSelected( s.finalNewline());
		fsSemanticBreaks.setSelected( s.preserveTextLineBreaks());
		fsPreserveBlank.setSelected( s.preserveBlankLines());
		fsContinuation.setSelectedIndex(
				s.textContinuation() == com.dogsbay.xml.format.FormatStyle.TextContinuation.FLUSH ? 1 : 0);
		fsTrimWhitespace.setSelected( s.trimWhitespace());
	}

	private int intValue( JFormattedTextField field, int fallback) {
		try {
			field.commitEdit();
			Object v = field.getValue();
			return (v instanceof Number) ? ((Number) v).intValue() : fallback;
		} catch ( Exception e) {
			return fallback;
		}
	}

	/** The current workspace root, or null when no project is open. */
	private java.nio.file.Path currentWorkspaceRoot() {
		try {
			java.io.File root = parent.getFileExplorer().getRootDirectory();
			return root == null ? null : root.toPath();
		} catch ( Exception e) {
			return null;
		}
	}

	/** Write the current House Style controls to the project's .dogsbay/config.xml. */
	private void saveHouseStyleToProject() {
		java.nio.file.Path root = currentWorkspaceRoot();
		if ( root == null) {
			JOptionPane.showMessageDialog( this,
					"Open a project folder first — there's no workspace to save the house style to.",
					"No project", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		try {
			com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig cfg =
					com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig.load( root);
			cfg.setFormatStyle( houseStyleFromControls());
			cfg.setFormatOnSave( fsFormatOnSave.isSelected());
			cfg.saveShared( root);
			fsEditingProjectStyle = true;   // now editing the project style; OK won't touch the user default
			fsSourceLabel.setText( "Project style — .dogsbay/config.xml (shared with your team)");
			JOptionPane.showMessageDialog( this,
					"Saved the house style to " + root.resolve( ".dogsbay/config.xml") + ".\n"
					+ "Commit it to share with your team.",
					"Saved to project", JOptionPane.INFORMATION_MESSAGE);
		} catch ( Exception e) {
			JOptionPane.showMessageDialog( this,
					"Could not write the project house style: " + e.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	// 	JPanel panel = new JPanel( new FormLayout( 0, 5));
	// 	panel.setBorder( new EmptyBorder( 5, 5, 5, 5));
	// 								new TitledBorder( "Private Key Details"),
	// 								new EmptyBorder( 0, 5, 5, 5)));
	// 		public void actionPerformed( ActionEvent e) {
	// 		}
	// 	});
	// 	return panel;
	// }

	private JPanel createSystemTab() {
		JPanel panel = new JPanel( new FormLayout( 0, 5));
		panel.setBorder( new EmptyBorder( 5, 5, 5, 5));

//									new EmptyBorder( 0, 5, 5, 5)));
//
//		NumberFormat format = NumberFormat.getIntegerInstance();
//		format.setMinimumIntegerDigits( 0);
//
//		
//		
//
//		
//
//

		JPanel proxyPanel 	= new JPanel( new FormLayout( 5, 5));
		proxyPanel.setBorder( new CompoundBorder( 
									new TitledBorder( "Proxy Configuration"),
									new EmptyBorder( 0, 5, 5, 5)));

		useProxyCheck	= new JCheckBox( "Use Proxy");
		useProxyCheck.addItemListener( new ItemListener() {
			public void itemStateChanged( ItemEvent e) {
				proxyHostField.setEnabled( useProxyCheck.isSelected());
				proxyHostLabel.setEnabled( useProxyCheck.isSelected());
				proxyPortField.setEnabled( useProxyCheck.isSelected());
				proxyPortLabel.setEnabled( useProxyCheck.isSelected());
			}
		});

		proxyPanel.add( useProxyCheck, FormLayout.FULL);

		JPanel proxyFieldsPanel = new JPanel( new FormLayout( 5, 5));
		proxyFieldsPanel.setBorder( new EmptyBorder( 5, 20, 5, 0));

		proxyHostField	= new JTextField();
		proxyHostLabel = new JLabel("Host Address:");
		proxyFieldsPanel.add( proxyHostLabel, FormLayout.LEFT);
		proxyFieldsPanel.add( proxyHostField, FormLayout.RIGHT_FILL);

		proxyPortField	= new JTextField();
		proxyPortLabel = new JLabel("Port Number:");
		proxyFieldsPanel.add( proxyPortLabel, FormLayout.LEFT);
		proxyFieldsPanel.add( proxyPortField, FormLayout.RIGHT_FILL);
		
		proxyPanel.add( proxyFieldsPanel, FormLayout.FULL_FILL);

		proxyHostLabel.setEnabled( false);
		proxyPortLabel.setEnabled( false);
		proxyHostField.setEnabled( false);
		proxyPortField.setEnabled( false);

		panel.add( proxyPanel, FormLayout.FULL_FILL);
		
		String osName = System.getProperty( "os.name");
		
		if ( !osName.startsWith( "Mac OS") && !osName.startsWith("Windows")) {
			// this must be Linux/Unix
			JPanel browserPanel 	= new JPanel( new FormLayout( 5, 5));
			browserPanel.setBorder( new CompoundBorder( 
										new TitledBorder( "Browser"),
										new EmptyBorder( 0, 5, 5, 5)));
	
			browserField	= new JTextField();
			browserPanel.add( new JLabel("Browser:"), FormLayout.LEFT);
			browserPanel.add( browserField, FormLayout.RIGHT_FILL);
	
			panel.add( browserPanel, FormLayout.FULL_FILL);
		}

		JPanel extensionsPanel 	= new JPanel( new BorderLayout());
		extensionsPanel.setBorder( new CompoundBorder( 
									new TitledBorder( "Extensions"),
									new EmptyBorder( 0, 5, 5, 5)));
							
		extensionList = new JList();
		
		//extensionList.setSelectionMode( ListSelectionModel.SINGLE_SELECTION);
		extensionList.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		
		extensionList.setVisibleRowCount(5);
		extensionsModel = new DefaultListModel();
		extensionList.setModel( extensionsModel);
		extensionList.addListSelectionListener(new ListSelectionListener() {

            public void valueChanged(ListSelectionEvent arg0) {
                int selected = extensionList.getSelectedIndex();
                if(selected>-1) {
                    extensionList.ensureIndexIsVisible(selected);
                }
            }
		    
		});
		
		JPanel buttonPanel = new JPanel( new FlowLayout( FlowLayout.RIGHT, 0, 0));
		buttonPanel.setBorder( new EmptyBorder( 2, 0, 0, 0));

		JButton addDirButton = new JButton("Add Dir ...");
		addDirButton.setFont( addDirButton.getFont().deriveFont( Font.PLAIN));
		addDirButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e) {
				JDirectoryChooser chooser = FileUtilities.getDirectoryChooser();

				if ( extensionsModel.getSize() > 0) {
					chooser.setCurrentDirectory( new File( (String)extensionsModel.lastElement()));
				}
				
				int result = chooser.showOpenDialog( parent);
				
				if ( result == JDirectoryChooser.APPROVE_OPTION) {
					File file = chooser.getSelectedFile();

					if ( !file.isDirectory()) {
						file = file.getParentFile();
					}
					
					String path = file.getPath();

					if ( extensionsModel.indexOf( path) == -1) {
						extensionsModel.addElement( path);
						//addedExtensions.addElement( path);
						currentExtensions.addElement(path);
					}
				}
			}
		});
		buttonPanel.add( addDirButton);

		JButton addJarButton = new JButton("Add Jar ...");
		addJarButton.setFont( addJarButton.getFont().deriveFont( Font.PLAIN));
		addJarButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e) {
				JFileChooser chooser = FileUtilities.getJarChooser();

				if ( extensionsModel.getSize() > 0) {
					chooser.setCurrentDirectory( new File( (String)extensionsModel.lastElement()));
				}
				
				int result = chooser.showOpenDialog( parent);
				
				if ( result == JFileChooser.APPROVE_OPTION) {
					File file = chooser.getSelectedFile();
					String path = file.getPath();

					if ( !file.isDirectory() && extensionsModel.indexOf( path) == -1) {
						extensionsModel.addElement( path);
						//addedExtensions.addElement( path);
						currentExtensions.addElement(path);
					}
				}
			}
		});
		buttonPanel.add( addJarButton);
		
		
		JButton deleteButton = new JButton( "Delete");
		deleteButton.setFont( deleteButton.getFont().deriveFont( Font.PLAIN));
		deleteButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e) {
			    
//			  *******************START new code*********************************
			    
			    //get the selected objects
			    Object[] selectedObjects = extensionList.getSelectedValuesList().toArray();
			    
			    //only continue with the confirm all if the array has 2 or more items
			    if(selectedObjects.length>0) {
			        
			        //create the variable that the user can set if they just want to delete all
			        boolean deleteAll = false;
			        
			        //loop through the selected objects
			        for(int cnt=0;cnt<selectedObjects.length;++cnt) {
			            
			            //if the deleteAll flag is false, then ask the user about each individual object
			            if( deleteAll == false) {
			            
				            //create the message for the user
				            String message = "Are you sure you want to delete:\n ";
				            message += (String)selectedObjects[cnt];
				            
				            //ask the question
				            int questionResult = -1;
				            if(selectedObjects.length>1) {
		                        questionResult = MessageHandler.showConfirmYesNoAll(parent,message);
		                    }
		                    else {
		                        questionResult = MessageHandler.showConfirm(parent,message);
		                    }
				            		            
				            //if the user answered All, don't do anything for now and delete them all later
				            if(questionResult==MessageHandler.CONFIRM_ALL_OPTION) {
				                extensionsModel.removeElement( selectedObjects[cnt]);
				                if(!removeStringFromVector((String)selectedObjects[cnt], currentExtensions)) {
								    MessageHandler.showError(parent,"Error Removing Extension","Error");
								}				                
			                	deleteAll=true;
			                } 
			                //user choose to delete this object, remove it from the list
			                else if(questionResult==JOptionPane.YES_OPTION) {
			                    extensionsModel.removeElement( selectedObjects[cnt]);
			                    if(!removeStringFromVector((String)selectedObjects[cnt], currentExtensions)) {
								    MessageHandler.showError(parent,"Error Removing Extension","Error");
								}
							}
				            
			            } else {
			                extensionsModel.removeElement( selectedObjects[cnt]);
			                if(!removeStringFromVector((String)selectedObjects[cnt], currentExtensions)) {
							    MessageHandler.showError(parent,"Error Removing Extension","Error");
							}
			            }
			        } //end for loop
			    } //end if(selectedObjects.length>1) {
			    
			    	    
			    
			    //*******************END new code*********************************
			    			    
			}
		});
		buttonPanel.add( deleteButton);

		extensionsPanel.add( new JScrollPane( extensionList), BorderLayout.CENTER);
		extensionsPanel.add( buttonPanel, BorderLayout.SOUTH);

		panel.add( extensionsPanel, FormLayout.FULL_FILL);
		
		/*JPanel keyPanel 	= new JPanel( new FormLayout( 10, 5));
		keyPanel.setBorder( new CompoundBorder( 
									new TitledBorder( "Keyboard Shortcuts"),
									new EmptyBorder( 5, 5, 10, 5)));

		keySelectionBox	= new JComboBox();

		keyPanel.add( new JLabel( "Active Configuration:"), FormLayout.LEFT);
		keyPanel.add( keySelectionBox, FormLayout.RIGHT_FILL);

		panel.add( keyPanel, FormLayout.FULL_FILL);*/
		

		JPanel lafPanel 	= new JPanel( new FormLayout( 10, 5));
		lafPanel.setBorder( new CompoundBorder( 
									new TitledBorder( "Look And Feel"),
									new EmptyBorder( 0, 5, 5, 5)));

		QLabel label = new QLabel();
		label.setLines( 2);
		label.setForeground( Color.red);
		
		label.setText( "A restart of the application is required before changes to the Look and Feel take effect.");
		label.setBorder( new EmptyBorder( 10, 10, 10, 10));
		
		lafPanel.add( label, FormLayout.FULL_FILL);

		lafSelectionBox	= new JComboBox();
		UIManager.LookAndFeelInfo[] info = UIManager.getInstalledLookAndFeels();
		
		for ( int i = 0; i < info.length; i++) {
			lafSelectionBox.addItem( info[i].getName());
		}

		lafPanel.add( new JLabel( "Look and Feel:"), FormLayout.LEFT);
		lafPanel.add( lafSelectionBox, FormLayout.RIGHT_FILL);

		panel.add( lafPanel, FormLayout.FULL_FILL);

		return panel;
	}
	
	private JComponent createServerTab() {
		Box panel = Box.createVerticalBox();
		panel.setBorder( new EmptyBorder( 5, 5, 5, 5));

		// Master enable + explanation + live status
		JPanel serverPanel = new JPanel( new FormLayout( 0, 5));
		serverPanel.setBorder( new CompoundBorder(
									new TitledBorder( "Integration Server"),
									new EmptyBorder( 0, 5, 5, 0)));

		mcpServerEnabledBox = new JCheckBox( "Enable integration server (MCP / CLI / REST)");
		mcpServerEnabledBox.addItemListener( new ItemListener() {
			public void itemStateChanged( ItemEvent e) {
				updateServerControlsEnabled();
			}
		});
		serverPanel.add( mcpServerEnabledBox, FormLayout.FULL);

		JLabel info = new JLabel( "<html><body style='width:360px'>"
				+ "Lets external tools drive the editor over localhost: the AI "
				+ "assistant MCP endpoint, the <code>dogsbay</code> CLI's editor "
				+ "commands, and the REST API. Off by default — the in-app agent "
				+ "does not need it. Enable only when you want external access."
				+ "</body></html>");
		info.setBorder( new EmptyBorder( 2, 22, 6, 0));
		serverPanel.add( info, FormLayout.FULL);

		mcpStatusLabel = new JLabel();
		mcpStatusLabel.setBorder( new EmptyBorder( 0, 22, 2, 0));
		serverPanel.add( mcpStatusLabel, FormLayout.FULL);

		panel.add( serverPanel);

		// Connection: port + which endpoints
		JPanel connectionPanel = new JPanel( new FormLayout( 5, 5));
		connectionPanel.setBorder( new CompoundBorder(
									new TitledBorder( "Connection"),
									new EmptyBorder( 0, 5, 5, 0)));

		mcpServerPortField = new JTextField();
		mcpServerPortLabel = new JLabel( "Port (0 = automatic):");
		connectionPanel.add( mcpServerPortLabel, FormLayout.LEFT);
		connectionPanel.add( mcpServerPortField, FormLayout.RIGHT_FILL);

		mcpEndpointBox = new JCheckBox( "Expose MCP endpoint (/mcp) for AI assistants");
		connectionPanel.add( mcpEndpointBox, FormLayout.FULL);
		rpcEndpointBox = new JCheckBox( "Expose JSON-RPC endpoint (/rpc) for the CLI");
		connectionPanel.add( rpcEndpointBox, FormLayout.FULL);

		panel.add( connectionPanel);

		// Token / install helpers
		JPanel tokenPanel = new JPanel( new FlowLayout( FlowLayout.LEFT, 5, 0));
		tokenPanel.setBorder( new CompoundBorder(
									new TitledBorder( "Access"),
									new EmptyBorder( 0, 5, 5, 0)));

		JButton copyInstallButton = new JButton( "Copy MCP install command");
		copyInstallButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e) {
				copyMcpInstallCommand();
			}
		});
		tokenPanel.add( copyInstallButton);

		JButton regenTokenButton = new JButton( "Regenerate token");
		regenTokenButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e) {
				regenerateServerToken();
			}
		});
		tokenPanel.add( regenTokenButton);

		panel.add( tokenPanel);

		// Hosted ACP agents (started from the AI Agent sidebar's + button)
		JPanel acpPanel = new JPanel( new FormLayout( 5, 5));
		acpPanel.setBorder( new TitledBorder( "Hosted agents"));
		acpPanel.add( new JLabel( "Default tier for a new agent:"), FormLayout.LEFT);
		acpDefaultTierCombo = new JComboBox<>( new String[] {
			"T1 — commands only", "T2 — read files", "T3 — read and write files" });
		acpDefaultTierCombo.setToolTipText( "You are asked again each time an agent starts; this is the preselected answer");
		acpPanel.add( acpDefaultTierCombo, FormLayout.RIGHT_FILL);
		agentProposalsBox = new JCheckBox( "Agent edits to DITA documents land as proposals for review");
		agentProposalsBox.setToolTipText( "Insertions and deletions are marked with status and rev; you accept or reject them in the Proposals panel");
		acpPanel.add( agentProposalsBox, FormLayout.FULL);
		panel.add( acpPanel);

		// Session transcripts: kept forever unless the user asks otherwise.
		JPanel sessionPanel = new JPanel( new FormLayout( 5, 5));
		sessionPanel.setBorder( new CompoundBorder(
									new TitledBorder( "Agent sessions"),
									new EmptyBorder( 0, 5, 5, 0)));
		sessionPanel.add( new JLabel( "Keep session transcripts for:"), FormLayout.LEFT);
		sessionRetentionCombo = new JComboBox<>( RETENTION_LABELS);
		sessionRetentionCombo.setToolTipText(
				"Transcripts of the built-in agent's conversations, in ~/.xagent/sessions. "
				+ "Older ones are deleted when the editor starts.");
		sessionPanel.add( sessionRetentionCombo, FormLayout.RIGHT_FILL);
		JLabel retentionInfo = new JLabel( "<html><body style='width:360px'>"
				+ "A transcript records what the agent did, so nothing is deleted "
				+ "unless you choose an age here. Delete individual sessions from "
				+ "the AI Agent panel's session picker (<code>/sessions</code>)."
				+ "</body></html>");
		retentionInfo.setBorder( new EmptyBorder( 2, 0, 2, 0));
		sessionPanel.add( retentionInfo, FormLayout.FULL);
		panel.add( sessionPanel);

		return panel;
	}

	/** Retention choices, and the days each one means (0 = forever). */
	private static final String[] RETENTION_LABELS = {
		"Forever", "90 days", "60 days", "30 days", "14 days", "7 days" };
	private static final int[] RETENTION_DAYS = { 0, 90, 60, 30, 14, 7 };

	private static int retentionIndexFor( int days) {
		for ( int i = 0; i < RETENTION_DAYS.length; i++) {
			if ( RETENTION_DAYS[i] == days) {
				return i;
			}
		}
		return 0;   // an unrecognised value means keep everything, the safe answer
	}

	/** Enable the connection controls only when the server is enabled. */
	private void updateServerControlsEnabled() {
		boolean on = mcpServerEnabledBox.isSelected();
		mcpServerPortField.setEnabled( on);
		mcpServerPortLabel.setEnabled( on);
		mcpEndpointBox.setEnabled( on);
		rpcEndpointBox.setEnabled( on);
	}

	/** Show whether the server is running and on which port. */
	private void refreshServerStatus() {
		if ( mcpStatusLabel == null) {
			return;
		}
		var mgr = parent.getIpcServerManager();
		if ( mgr != null && mgr.isRunning()) {
			mcpStatusLabel.setText( "● Running on http://localhost:" + mgr.getPort());
			mcpStatusLabel.setForeground( new Color( 0x2E, 0x7D, 0x32));
		} else {
			mcpStatusLabel.setText( "○ Stopped");
			mcpStatusLabel.setForeground( Color.GRAY);
		}
	}

	/** Copy a ready-to-paste {@code claude mcp add} command to the clipboard. */
	private void copyMcpInstallCommand() {
		var mgr = parent.getIpcServerManager();
		int port = ( mgr != null && mgr.isRunning()) ? mgr.getPort() : parsePortField();
		if ( port <= 0) {
			port = 19601;   // auto / not yet bound — show the range start as a hint
		}
		String token = mgr != null ? mgr.getAuthToken() : "<token>";
		String cmd = "claude mcp add --transport http dogsbay-editor "
				+ "http://localhost:" + port + "/mcp "
				+ "--header \"Authorization: Bearer " + token + "\"";
		java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents( new java.awt.datatransfer.StringSelection( cmd), null);
		JOptionPane.showMessageDialog( this,
				"Copied to clipboard:\n\n" + cmd,
				"MCP install command", JOptionPane.INFORMATION_MESSAGE);
	}

	/** Reset the persistent bearer token; restart the server if it is running. */
	private void regenerateServerToken() {
		int choice = JOptionPane.showConfirmDialog( this,
				"Regenerate the access token? Existing MCP/CLI clients will need "
				+ "to be reconfigured with the new token.",
				"Regenerate token", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE);
		if ( choice != JOptionPane.OK_OPTION) {
			return;
		}
		com.dogsbay.dogsbayaieditor.ipc.DiscoveryFile.resetToken();
		var mgr = parent.getIpcServerManager();
		if ( mgr != null && mgr.isRunning()) {
			mgr.restart();
		}
		refreshServerStatus();
		JOptionPane.showMessageDialog( this,
				"A new token has been generated.",
				"Regenerate token", JOptionPane.INFORMATION_MESSAGE);
	}

	private int parsePortField() {
		try {
			String text = mcpServerPortField.getText().trim();
			return text.isEmpty() ? 0 : Integer.parseInt( text);
		} catch ( NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * The key settings, listing the commands this build has.
	 *
	 * <p>The page used to list whatever the saved file contained, and to offer
	 * a choice of named key configurations. Both are gone: the list is the
	 * product's, and what a reader changes is an override against it.
	 */
	private JPanel createKeysTab() {
		// A copy of the live bindings, not a fresh catalogue: the live instance
		// is where plugins register their commands, and building from the
		// catalogue alone dropped every plugin override the moment OK was
		// pressed. A copy, so Cancel leaves the live instance untouched.
		keyBindings = properties.getKeyBindings().copy();

		JPanel panel = new JPanel( new BorderLayout());
		keyBindingsPanel = new com.dogsbay.dogsbayaieditor.properties.KeyBindingsPanel( keyBindings);
		panel.add( keyBindingsPanel, BorderLayout.CENTER);
		return panel;
	}
	
	
	private KeyMapDialog getKeyMapDialog() 
	{
		if (keymapDialog == null) {
			keymapDialog = new KeyMapDialog(parent);
		}
		
		return keymapDialog;
	}

	private JPanel createXMLTab() {
		JPanel panel = new JPanel( new FormLayout( 0, 5));
		panel.setBorder( new EmptyBorder( 5, 5, 5, 5));

		
		JPanel advancedPanel = new JPanel( new FormLayout( 5, 5));
		advancedPanel.setBorder( new CompoundBorder( 
				new TitledBorder( "Non Validating Feature"),
				new EmptyBorder( 0, 5, 5, 5)));

//		resolveEntitiesCheck = new JCheckBox( "Always try to resolve Entities");
//		advancedPanel.add( resolveEntitiesCheck, FormLayout.FULL);
		
		loadDTDGrammarCheck = new JCheckBox( "Load DTD Grammar");
		advancedPanel.add( loadDTDGrammarCheck, FormLayout.FULL);

		panel.add( advancedPanel, FormLayout.FULL_FILL);
		
		JPanel catalogsPanel 	= new JPanel( new FormLayout( 0, 5));
		catalogsPanel.setBorder( new CompoundBorder( 
									new TitledBorder( "Catalogs"),
									new EmptyBorder( 0, 5, 5, 5)));
							
		JPanel catalogsListPanel = new JPanel( new BorderLayout());

		catalogList = new JList();
		catalogList.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		catalogList.setVisibleRowCount( 5);
		catalogsModel = new DefaultListModel();
		catalogList.setModel( catalogsModel);
		catalogList.addListSelectionListener(new ListSelectionListener() {

            public void valueChanged(ListSelectionEvent arg0) {
                int selected = catalogList.getSelectedIndex();
                if(selected>-1) {
                    catalogList.ensureIndexIsVisible(selected);
                }
            }
		    
		});

		JPanel buttonPanel = new JPanel( new FlowLayout( FlowLayout.RIGHT, 0, 0));
		buttonPanel.setBorder( new EmptyBorder( 2, 0, 0, 0));

		JButton addButton = new JButton("Add ...");
		addButton.setFont( addButton.getFont().deriveFont( Font.PLAIN));
		addButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e) {
				JFileChooser chooser = FileUtilities.getCatalogChooser();

				if ( catalogsModel.getSize() > 0) {
					chooser.setCurrentDirectory( new File( (String)catalogsModel.lastElement()));
				}
				
				int result = chooser.showOpenDialog( parent);
				
				if ( result == JFileChooser.APPROVE_OPTION) {
					File file = chooser.getSelectedFile();
					String path = file.getPath();

					if ( !file.isDirectory() && catalogsModel.indexOf( path) == -1) {
						catalogsModel.addElement( path);
						/*addedCatalogs.addElement( path);*/
					}
				}
			}
		});
		buttonPanel.add( addButton);
		
		
		JButton deleteButton = new JButton( "Delete");
		deleteButton.setFont( deleteButton.getFont().deriveFont( Font.PLAIN));
		deleteButton.addActionListener( new ActionListener() {
			public void actionPerformed( ActionEvent e) {
			    
//				  *******************START new code*********************************
			    
			    //get the selected objects
			    Object[] selectedObjects = catalogList.getSelectedValuesList().toArray();
			    
			    //only continue with the confirm all if the array has 2 or more items
			    if(selectedObjects.length>0) {
			        
			        //create the variable that the user can set if they just want to delete all
			        boolean deleteAll = false;
			        
			        //loop through the selected objects
			        for(int cnt=0;cnt<selectedObjects.length;++cnt) {
			            
			            //if the deleteAll flag is false, then ask the user about each individual object
			            if( deleteAll == false) {
			            
				            //create the message for the user
				            String message = "Are you sure you want to delete:\n ";
				            message += (String)selectedObjects[cnt];
				            
				            //ask the question
				            int questionResult = -1;
				            if(selectedObjects.length>1) {
		                        questionResult = MessageHandler.showConfirmYesNoAll(parent,message);
		                    }
		                    else {
		                        questionResult = MessageHandler.showConfirm(parent,message);
		                    }
				            		            
				            //if the user answered All, don't do anything for now and delete them all later
				            if(questionResult==MessageHandler.CONFIRM_ALL_OPTION) {
			                    catalogsModel.removeElement( selectedObjects[cnt]);
			                    deleteAll=true;
			                } 
			                //user choose to delete this object, remove it from the list
			                else if(questionResult==JOptionPane.YES_OPTION) {
			                    catalogsModel.removeElement( selectedObjects[cnt]);
			                }
				            
			            } else {
		                    catalogsModel.removeElement( selectedObjects[cnt]);
		                }
			        } //end for loop
			    } //end if(selectedObjects.length>1) {
			    
			    	    
			    
			    //*******************END new code*********************************
			 
			}
		});
		buttonPanel.add( deleteButton);

		catalogsListPanel.add( new JScrollPane( catalogList), BorderLayout.CENTER);
		JPanel preferPublicIdentifiersPanel = new JPanel( new BorderLayout());
		preferPublicIdentifiersPanel.add( buttonPanel, BorderLayout.EAST);

		preferPublicIdentifiersBox = new JCheckBox( "Prefer Public Identifiers");
		preferPublicIdentifiersPanel.add( preferPublicIdentifiersBox, BorderLayout.WEST);
		catalogsListPanel.add( preferPublicIdentifiersPanel, BorderLayout.SOUTH);

		catalogsPanel.add( catalogsListPanel, FormLayout.FULL_FILL);

//		preferPublicIdentifiersBox = new JCheckBox( "Prefer Public Identifiers");
//		catalogsPanel.add( preferPublicIdentifiersBox, FormLayout.FULL);

//		catalogsPanel.add( getSeparator(), FormLayout.FULL);

		panel.add( catalogsPanel, FormLayout.FULL_FILL);

		JPanel mappingsPanel 	= new JPanel( new FormLayout( 0, 5));
		mappingsPanel.setBorder( new CompoundBorder( 
									new TitledBorder( "Prefix Namespace Mappings"),
									new EmptyBorder( 0, 5, 5, 5)));
							
		prefixNamespaceMappingPanel = new PrefixNamespaceMappingPanel( parent, properties, 5);

		mappingsPanel.add( prefixNamespaceMappingPanel, FormLayout.FULL_FILL);

		panel.add( mappingsPanel, FormLayout.FULL_FILL);

		return panel;
	}

	private JColorChooser getColorChooser() {
		if ( colorChooser == null) {
			colorChooser = new JColorChooser();
		}
		
		return colorChooser;
	}

	private void setDefault() {
		Font font = TextPreferences.getDefaultFont();
		fontSelectionBox.setSelectedItem( font.getName());
		sizeSelectionBox.setSelectedItem( ""+font.getSize());
		tabSizeBox.setSelectedItem( ""+TextPreferences.DEFAULT_TAB_SIZE);

		for ( int i = 0; i < styles.size(); i++) {
			FontStyle style = (FontStyle)styles.elementAt(i);
			style.setDefault();
		}

		antialiasingBox.setSelected( false);
		convertTabBox.setSelected( false);

		stylePanel.selectFontStyle( (FontStyle)styles.elementAt(0));
	}

	/**
	 * Initialises the values in the dialog and displays it.
	 * Renamed from show() to avoid conflict with deprecated AWT Component.show()
	 * which was causing StackOverflowError in Java 21.
	 */
	public void display() {
		Font font = textPreferences.getFont();
		
		fontSelectionBox.setSelectedItem( font.getName());
		sizeSelectionBox.setSelectedItem( ""+font.getSize());
		tabSizeBox.setSelectedItem( ""+textPreferences.getSpaces());
		convertTabBox.setSelected( textPreferences.convertTab());

		// Reset category list to first item
		categoryList.setSelectedIndex( 0);

		for ( int i = 0; i < styles.size(); i++) {
			FontStyle style = (FontStyle)styles.elementAt(i);
			style.reset();

		}

		stylePanel.selectFontStyle( (FontStyle)styles.elementAt(0));

		// Editor boxes
//		indentMixedContentBox.setSelected( editorProperties.isIndentMixedContent());

//		tagCompletionBox.setSelected( editorProperties.isTagCompletion());
//		softWrapBox.setSelected( editorProperties.isSoftWrapping());
//		showMarginBox.setSelected( editorProperties.isShowMargin());
//		showOverviewMarginBox.setSelected( editorProperties.isShowOverviewMargin());
//		showFoldingMarginBox.setSelected( editorProperties.isShowFoldingMargin());
//		autoIndentationBox.setSelected( editorProperties.isSmartIndentation());
//		textPromptingBox.setSelected( editorProperties.isTextPrompting());
		antialiasingBox.setSelected( TextPreferences.isAntialiasing());
		uniqueXPathBox.setSelected( properties.isUniqueXPath());
		multipleDocumentsBox.setSelected( properties.isMultipleDocumentOccurrences());
//		strictTextPromptingBox.setSelected( editorProperties.isStrictTextPrompting());

		// Designer boxes
//		autoCreateRequiredBox.setSelected( designerProperties.isAutoCreateRequired());
//		showElementValuesBox.setSelected( designerProperties.isShowElementValues());
//		showAttributeValuesBox.setSelected( designerProperties.isShowAttributeValues());

		// Integration server (Server tab)
		mcpServerEnabledBox.setSelected( properties.isMcpServerEnabled());
		mcpServerPortField.setText( Integer.toString( properties.getMcpServerPort()));
		mcpServerPortField.setCaretPosition( 0);
		mcpEndpointBox.setSelected( properties.isMcpEndpointEnabled());
		rpcEndpointBox.setSelected( properties.isRpcEndpointEnabled());
		acpDefaultTierCombo.setSelectedIndex( properties.getAcpDefaultTier().ordinal());
		agentProposalsBox.setSelected( properties.isAgentProposalsEnabled());
		sessionRetentionCombo.setSelectedIndex(
				retentionIndexFor( properties.getAgentSessionRetentionDays()));
		updateServerControlsEnabled();
		refreshServerStatus();

		// General boxes
		autoSyncSelectionBox.setSelected( properties.isAutoSyncSelection());
		scrollDocumentTabsBox.setSelected( properties.isScrollDocumentTabs());
		reopenSessionFilesBox.setSelected( properties.isReopenSessionFiles());
		checkTypeOnOpeningBox.setSelected( properties.isCheckTypeOnOpening());
		showFullPathBox.setSelected( properties.isShowFullPath());

//		showNavigatorAttributesBox.setSelected( navigatorProperties.isShowAttributeValues());

		promptCreateTypeOnOpeningBox.setSelected( properties.isPromptCreateTypeOnOpening());
		validateOnOpeningBox.setSelected( properties.isValidateOnOpening());
		useInternalSchemaBox.setSelected( properties.useInternalSchema());
		hideScenarioExecutionDialogWhenCompleteBox.setSelected( properties.isHideExecuteScenarioDialogWhenComplete());
		openXIncludeInNewDocumentBox.setSelected( properties.isOpenXIncludeInNewDocument());
//		attributesNewLineBox.setSelected( properties.isAttributesNewLine());

		useProxyCheck.setSelected( properties.isUseProxy());
		proxyPortField.setText( properties.getProxyPort());
		proxyPortField.setCaretPosition(0);
		proxyHostField.setText( properties.getProxyHost());
		proxyHostField.setCaretPosition(0);
		
		if ( browserField != null) {
			browserField.setText( properties.getBrowser());
		}
		
//		resolveEntitiesCheck.setSelected( properties.isResolveEntities());
		loadDTDGrammarCheck.setSelected( properties.isLoadDTDGrammar());

//		showNamespaces.setSelected( viewerProperties.isShowNamespaces());
//		showAttributes.setSelected( viewerProperties.isShowAttributes());
//		showValues.setSelected( viewerProperties.isShowValues());
//		showComments.setSelected( viewerProperties.isShowComments());
//		showInline.setSelected( viewerProperties.isShowInline());
//		showPI.setSelected( viewerProperties.isShowPI());
		
		// format (legacy fields, off-screen but still persisted)
		wrapTextCheck.setSelected( editorProperties.isWrapText());
		wrappingColumnField.setValue( Integer.valueOf( editorProperties.getWrappingColumn()));

		// House style: show the project's declared style if this workspace has one,
		// otherwise the user's default. Project always wins, so it's what we display.
		// We remember which one we're showing so OK persists to the right place — OK
		// must never overwrite the personal default with project values.
		java.nio.file.Path wsRoot = currentWorkspaceRoot();
		com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig projectCfg = ( wsRoot != null
				&& com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig.exists( wsRoot))
				? com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig.load( wsRoot)
				: null;
		fsEditingProjectStyle = projectCfg != null && projectCfg.getFormatStyle() != null;
		if ( fsEditingProjectStyle) {
			houseStyleToControls( projectCfg.getFormatStyle());
			fsSourceLabel.setText( "Project style — .dogsbay/config.xml (shared with your team)");
			fsFormatOnSave.setSelected( projectCfg.isFormatOnSave());   // the project's own flag
		} else {
			houseStyleToControls( editorProperties.getFormatStyle());
			fsSourceLabel.setText( "Default style — used when a project doesn't define one");
			fsFormatOnSave.setSelected( editorProperties.isFormatOnSave());
		}
		

		//removedExtensions	= new Vector();
		//addedExtensions	= new Vector();
		
		
		UIManager.LookAndFeelInfo[] info = UIManager.getInstalledLookAndFeels();
		String current = UIManager.getLookAndFeel().getClass().getName();
		
		for ( int i = 0; i < info.length; i++) {		
			if ( current.equals( info[i].getClassName())) {
				lafSelectionBox.setSelectedIndex( i);
				break;
			}
		} 
		
		extensionsModel.removeAllElements();
		initialExtensions = properties.getExtensions();
		Vector extensions = properties.getExtensions();
		currentExtensions = (Vector)extensions.clone();
		for ( int i = extensions.size()-1; i >= 0; i--) {
			extensionsModel.addElement( extensions.elementAt(i));
		}

		/*removedCatalogs	= new Vector();
		addedCatalogs	= new Vector();*/
		
		preferPublicIdentifiersBox.setSelected( properties.isPreferPublicIdentifiers());

		prefixNamespaceMappingPanel.init();
		

		catalogsModel.removeAllElements();

		Vector catalogs = properties.getCatalogs();
		for ( int i = catalogs.size()-1; i >= 0; i--) {
			catalogsModel.addElement( catalogs.elementAt(i));
		}

		switch ( editorProperties.getFormatType()) {
			case EditorProperties.FORMAT_CUSTOM:
				customFormatterRadio.setSelected( true);

				indentCheck.setSelected( editorProperties.isCustomIndent());
				padTextCheck.setSelected( editorProperties.isCustomPadText());
				newlinesCheck.setSelected( editorProperties.isCustomNewline());
				stripWhitespaceCheck.setSelected( editorProperties.isCustomStrip());
				preserveMixedContentCheck.setSelected( editorProperties.isCustomPreserveMixedContent());
				break;

			case EditorProperties.FORMAT_COMPACT:
				compactFormatterRadio.setSelected( true);
				break;

			case EditorProperties.FORMAT_STANDARD:
				standardFormatterRadio.setSelected( true);
				break;
		}
		
		
		if ( keyBindingsPanel != null) {
			// Re-seed from the live bindings on every open, so a change that was
			// cancelled is genuinely gone rather than sitting in the dialog
			// waiting for the next OK.
			keyBindings = properties.getKeyBindings().copy();
			keyBindingsPanel.setBindings( keyBindings);
		}
		
		super.setVisible(true);
	}
	
	private JScrollPane wrapInScrollPane( JComponent content) {
		JScrollPane sp = new JScrollPane( content);
		sp.setBorder( null);
		sp.getVerticalScrollBar().setUnitIncrement( 16);
		sp.setHorizontalScrollBarPolicy( JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		return sp;
	}

	// 	int value = chooser.showOpenDialog( getParent());
	// 	if ( value == JFileChooser.APPROVE_OPTION) {
	// 		File file = chooser.getSelectedFile();
	// 	}
	// }

	// 	}
	// 	if ( file == null) {
	// 		file = FileUtilities.getLastOpenedFile();
	// 	}
	// }

	private JComponent getSeparator() {
		JComponent separator = new JPanel();
		separator.setPreferredSize( new Dimension( 100, 10));

		return separator;
	}

	private boolean hasSameWidth( Font font, int style1, int style2) {
	    String testString = "<Test test:nms=\"http://test.org\"/>";
		JTextArea pane = new JTextArea();
		
	    Font font1 = font.deriveFont( style1, 12);
	    FontMetrics fm = pane.getFontMetrics( font1);
	    int width1 = fm.stringWidth( testString);

	    Font font2 = font.deriveFont( style2, 12);
	    fm = pane.getFontMetrics( font2);
	    int width2 = fm.stringWidth( testString);

		if ( width1 == width2) {	// && italicWidth == italicBoldWidth) { 
			return true;
		} 
		
		return false;
	}
	
	private boolean removeStringFromVector(String obj, Vector vector) {
	    
	    int size = vector.size();
	    for(int cnt=0;cnt<size;++cnt) {
	        String objectTemp = (String)vector.get(cnt);
	        if(objectTemp.equalsIgnoreCase(obj)) {
	            vector.remove(cnt);
	            return(true);
	        }
	    }
	    return(false);
	}

	private class FontStylePanel extends JPanel implements ActionListener {
		private FontStyle style = null;

		private JRadioButton italicButton = null;
		private JRadioButton boldButton = null;
		private JRadioButton plainButton = null;

		private JComboBox stylesBox = null;
		private ColorIcon icon = null;
		private JButton colorButton = null;
		
		private int id;

		public FontStylePanel( Vector styles) {
			super( new FormLayout( 5, 5));

			stylesBox = new JComboBox( styles);
			stylesBox.addItemListener( new ItemListener() {
				public void itemStateChanged( ItemEvent e) {
					setFontStyle( (FontStyle)stylesBox.getSelectedItem());
				}
			});
			
			ButtonGroup group = new ButtonGroup();

			italicButton = new JRadioButton( "Italic");
			italicButton.setFont( italicButton.getFont().deriveFont( Font.PLAIN));
			italicButton.addItemListener( new ItemListener() {
				public void itemStateChanged( ItemEvent e) {
					style.setStyle( Font.ITALIC);
					updateEditor();
				}
			});
			group.add( italicButton);

			boldButton = new JRadioButton( "Bold");
			boldButton.setFont( boldButton.getFont().deriveFont( Font.PLAIN));
			boldButton.addItemListener( new ItemListener() {
				public void itemStateChanged( ItemEvent e) {
					style.setStyle( Font.BOLD);
					updateEditor();
				}
			});
			group.add( boldButton);

			plainButton = new JRadioButton( "Plain");
			plainButton.setFont( plainButton.getFont().deriveFont( Font.PLAIN));
			plainButton.addItemListener( new ItemListener() {
				public void itemStateChanged( ItemEvent e) {
					style.setStyle( Font.PLAIN);
					updateEditor();
				}
			});
			group.add( plainButton);
			
			icon = new ColorIcon( Color.black, 32, 16);
			colorButton = new JButton( icon);
			colorButton.addActionListener( this);
			colorButton.setMargin( new Insets( 1, 1, 1, 1));
			
			JPanel flowPanel = new JPanel( new FlowLayout( FlowLayout.LEFT, 5, 0));
			flowPanel.add( plainButton);
			flowPanel.add( italicButton);
			flowPanel.add( boldButton);
			
			JPanel settingsPanel = new JPanel( new BorderLayout());
			settingsPanel.add( flowPanel, BorderLayout.CENTER);
			settingsPanel.add( colorButton, BorderLayout.EAST);
			
			add( new JLabel( "Style:"), FormLayout.LEFT);
			add( stylesBox, FormLayout.RIGHT_FILL);
			add( new JLabel( "Settings:"), FormLayout.LEFT);
			add( settingsPanel, FormLayout.RIGHT_FILL);
		}
		
		public void setFontStyle( FontStyle style) {
			this.style = style;
			
			icon.setColor( style.getColor());
			
			if ( style.getStyle() == Font.BOLD) {
				boldButton.setSelected( true);
			} else if ( style.getStyle() == Font.ITALIC) {
				italicButton.setSelected( true);
			} else {
				plainButton.setSelected( true);
			}

			colorButton.repaint();
		}

		public void selectFontStyle( FontStyle style) {
			stylesBox.setSelectedItem( style);
			
			setFontStyle( style);
		}

		public void reset( boolean italic, boolean bold) {
			if ( !bold) {
				if ( boldButton.isSelected()) {
					plainButton.setSelected( true);
				}
				boldButton.setEnabled( false);
			} else {
				boldButton.setEnabled( true);
			}

			if ( !italic) {
				if ( italicButton.isSelected()) {
					plainButton.setSelected( true);
				}
				italicButton.setEnabled( false);
			} else {
				italicButton.setEnabled( true);
			}
		}
		
		// color button pressed
		public void actionPerformed( ActionEvent e) {
			// get color chooser and set the color chosen in the icon
			Color color = JColorChooser.showDialog( parent, style.getType().getName()+" Color", icon.getColor());
			
			if ( color != null)	{
				icon.setColor( color);
				style.setColor( color);
				updateEditor();
			}
		}
	}

	private class FontStyle {
		private FontType type = null;

		private int defaultStyle	= -1;
		private Color defaultColor	= null;
		
		private Color color	= null;
		private int style	= -1;
		private String name	= null;
		
		private int id		= -1;

		public FontStyle( String name, FontType type, int id, int defaultStyle, Color defaultColor) {
			this.color = type.getColor();
			this.style = type.getStyle();
			this.name = name;
			
			this.defaultColor = defaultColor;
			this.defaultStyle = defaultStyle;
			
			this.type = type;
			this.id = id;
		}
		
		public int getId() {
			return id;
		}

		public FontType getType() {
			return type;
		}

		public int getStyle() {
			return style;
		}

		public void setStyle( int style) {
			this.style = style;
		}

		public void setItalic( boolean enabled) {
			if ( (style & Font.ITALIC) > 0) {
				if ( !enabled) {
					style = style - Font.ITALIC;
				}
			} else {
				if ( enabled) {
					style = style + Font.ITALIC;
				}
			}
		}

		public boolean isItalic() {
			return (style & Font.ITALIC) > 0;
		}

		public void setBold( boolean enabled) {
			if ( (style & Font.BOLD) > 0) {
				if ( !enabled) {
					style = style - Font.BOLD;
				}
			} else {
				if ( enabled) {
					style = style + Font.BOLD;
				}
			}
		}

		public boolean isBold() {
			return (style & Font.BOLD) > 0;
		}

		public Color getColor() {
			return color;
		}

		public void setColor( Color color) {
			this.color = color;
		}

		// Set the values from the fonttype object		
		public void reset() {
			style = type.getStyle();
			color = type.getColor();
		}
		
		// Set the default values	
		public void setDefault() {
			style = defaultStyle;
			color = defaultColor;
		}

		// Set the values in the fonttype object		
		public void update() {
			type.setStyle( style);
			type.setColor( color);
		}

		public String toString() {
			return name;
		}
	}

	private class ColorIcon implements Icon {
		private Color color = null;
		private Dimension size = null;
		
		public ColorIcon( Color color, int w, int h) {
			size = new Dimension( w, h);
			this.color = color;
		}
		
		public void paintIcon( Component c, Graphics g, int x, int y) {
			g.setColor( Color.black);
			g.drawRect( x, y, size.width-1, size.height-1);
			
			g.setColor( color);
			g.fillRect( x+1, y+1, size.width-2, size.height-2);
		}
		
		public void setColor( Color color) {
			this.color = color;
		}

		public Color getColor() {
			return color;
		}

		public int getIconWidth() {
			return size.width;
		}
	
		public int getIconHeight() {
			return size.height;
		}
	}
	
	

} 
