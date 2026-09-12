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


import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.net.URL;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.dom4j.DocumentType;
import org.dom4j.Namespace;
import org.dom4j.Node;
import org.dom4j.QName;

import com.dogsbay.schema.SchemaDocument;
import com.dogsbay.schema.SchemaDocument;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.DogsBayDocumentEvent;
import com.dogsbay.xml.DogsBayDocumentListener;
import com.dogsbay.xml.XAttribute;
import com.dogsbay.xml.XDocument;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.XMLError;
import com.dogsbay.xml.XMLGrammar;
import com.dogsbay.dogsbayaieditor.author.AuthorSplitView;
import com.dogsbay.dogsbayaieditor.author.AuthorView;
import com.dogsbay.xml.browser.Browser;
//import com.dogsbay.dogsbayaieditor.plugins.PluginViewPanel;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.xml.navigator.NavigatorSettings;
import com.dogsbay.xml.properties.Properties;
import com.dogsbay.xml.viewer.Viewer;
import com.dogsbay.dogsbayaieditor.component.GUIUtilities;
import com.dogsbay.dogsbayaieditor.grammar.GrammarProperties;
import com.dogsbay.dogsbayaieditor.grammar.NamespaceProperties;
import com.dogsbay.dogsbayaieditor.plugins.PluginView;
import com.dogsbay.dogsbayaieditor.plugins.PluginViewPanel;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

/**
 * The panel that holds the panels and information for a document.
 *
 * @version	$Revision: 1.40 $, $Date: 2005/09/27 15:22:29 $
 * @author Dogsbay
 */
public class DogsBayView extends NavigationPanel implements DogsBayDocumentListener, ChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final boolean DEBUG = false;
	
	public static final String BROWSER_ICON = "com/dogsbay/xml/browser/icons/BrowserIcon.gif";
	public static final String VIEWER_ICON = "com/dogsbay/dogsbayaieditor/icons/sidebar/open-preview.png";
	public static final String EDITOR_ICON = "com/dogsbay/xml/editor/icons/EditorIcon.gif";
	public static final String AUTHOR_ICON = "com/dogsbay/dogsbayaieditor/icons/sidebar/symbol-file.png";
	public static final String GRID_ICON = "com/dogsbay/xml/grid/icons/GridIcon.gif";
	
	private static Vector entityNames = null;

	private Vector allElements		= null;
	private Vector anyElements			= null;
	private Vector namespaces			= null;
	private Vector globalElements		= null;
	private Vector elementNames			= null;
	private Hashtable attributeNames	= null;

	private Vector tagSchemas			= null;
	private ErrorList errors			= null;
	private XPathList results			= null;

	private SchemaDocument schema			= null;
	private DogsBayDocument document	= null;
	private XElement selectedElement	= null; // sticky selection element
	
	private Hashtable icons				= null;

	private GrammarProperties grammar			= null;
	private XMLGrammarImpl validationGrammar	= null;
	private NavigatorSettings navigatorSettings = null;

	private ViewPanel current			= null;

	private DogsBayAIEditor main 				= null;
	private ConfigurationProperties properties	= null;

	private Editor editor				= null;
	private Viewer viewer				= null;
	private AuthorView author			= null;
	private AuthorSplitView authorSplit	= null;
	private javax.swing.JCheckBoxMenuItem authorSplitItem = null;
	//private Grid grid					= null;
	
	private Vector userViews			= null;
	
	private Vector pluginViewPanels 	= null;
	
	
		
	
	private FocusListener focusListener = null;
	
	private ChangeManager changeManager = null;
	

	private String defaultValidationGrammar = "";
	
	
	private NavigationButton editorButton	= null;
	private NavigationButton viewerButton	= null;
	private NavigationButton authorButton	= null;
	//private NavigationButton browserButton	= null;
	/*private NavigationButton gridButton = null;*/
	
	private ButtonGroup documentViewButtonGroup = null;
	private JPanel documentViewButtonPanel = null;
	
	private JMenu documentViewsMenu = null;
	
	private JRadioButtonMenuItem editorItem		= null;
	private JRadioButtonMenuItem viewerItem		= null;
	private JRadioButtonMenuItem browserItem	= null;
	private JRadioButtonMenuItem authorItem	= null;
	/*private JRadioButtonMenuItem gridItem 		= null;*/

	public DogsBayView( DogsBayAIEditor parent, ConfigurationProperties properties) {
		main = parent;
		this.properties = properties;
		
		errors = new ErrorList();
		results = new XPathList();

		focusListener = new FocusListener() {
			public void focusGained( FocusEvent e) {
				current.setFocus();
			}
			public void focusLost( FocusEvent e) {
			}
		};
		
		addFocusListener( focusListener);
		
//		browser = new Browser( parent, properties.getBrowserProperties());
//		add( getBrowser(), "Browser");

		viewer = new Viewer( parent, properties.getViewerProperties(), this);
		add( viewer, "Viewer");

		

		editor = new Editor( parent, properties, this, errors);
		add( editor, "Editor");

		author = new AuthorView( this);
		add( author, "Author");
		
		//grid = new Grid( parent, properties.getGridProperties(), properties, this);
		//add( grid, "Grid");
		
		setupViewButtons();
		setupViewMenu();
		
		setPluginViewPanels(new Vector());
		
		
//		for each of the plugin buttons
		for(int cnt=0;cnt<parent.getPluginViews().size();++cnt) {
			Object obj = parent.getPluginViews().get(cnt);
			if((obj != null) && (obj instanceof PluginView)) {
				PluginView pluginView = (PluginView)obj;
				if(pluginView != null) {
					
					//XElement propertiesElement = properties.getPluginViewProperties(pluginView.getPropertyElementName());
					Properties pluginProperties = pluginView.getProperties();
					//if(pluginProperties != null) {
						PluginViewPanel pluginViewPanel = pluginView.createNewPluginViewPanel(parent,pluginProperties, properties, this);
						
						if(pluginViewPanel != null) {
							add(pluginViewPanel, pluginView.getIdentifier());
							getPluginViewPanels().add(pluginViewPanel);
							
						}
					//}
					
				}				
			}
		}
		
		userViews = new Vector();
		
		changeManager = new ChangeManager();
		changeManager.setViewSynchroniser( this::synchroniseAfterUndo);
		changeManager.setPendingEditCommitter( () -> {
			if ( author != null) {
				author.commitPendingText();
			}
		});
		changeManager.addChangeListener( this);
		changeManager.setLimit( 100);
		validationGrammar = new XMLGrammarImpl();
	}
	
	public void addUserView(UserView newUserView) {
	    
	    //see if a view with that identifier already exists
	    boolean alreadyExists = false;
	    
	    for(int cnt=0;cnt<userViews.size();++cnt) {
	    	
	    	UserView tempUserView = (UserView) userViews.get(cnt);
	    	if(tempUserView.getIdentifier().equals(newUserView.getIdentifier())) {
	    		alreadyExists = true;
	    	}
	    }
	    
	    if(alreadyExists == false) {
		    userViews.add(newUserView);
		    main.getDocumentViewButtonPanel().add(newUserView.getButton());
		    main.getDocumentViewButtonGroup().add(newUserView.getButton());
		
	    
	    	add( newUserView.getPanel(), newUserView.getIdentifier());
	    	this.repaint();
	    }
	    else {
	    	MessageHandler.showError("A view with the identifier "+newUserView.getIdentifier()+ " already exists", "Script Error");
	    	
	    }
	}
	
	private void setupViewButtons() {
		
		documentViewButtonGroup = new ButtonGroup();
		setDocumentViewButtonPanel(new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0)));
		getDocumentViewButtonPanel().setBorder(new EmptyBorder(0, 0, 2, 0));

		// Schema Viewer button - initialized but not added to panel
		// getDocumentViewButtonPanel().add(getSchemaButton());
		// documentViewButtonGroup.add(getSchemaButton());

		// Outliner (Tag Free Editor) button - initialized but not added to panel
		// getDocumentViewButtonPanel().add(getDesignerButton());
		// documentViewButtonGroup.add(getDesignerButton());

		setEditorButton(new NavigationButton( "Editor", getIcon(EDITOR_ICON)));
		getEditorButton().setToolTipText( "Programmers Editor");
		getDocumentViewButtonPanel().add(getEditorButton());
		documentViewButtonGroup.add(getEditorButton());

		getEditorButton().addItemListener(new EditorItemListener());

		setAuthorButton(new NavigationButton( "Author", getIcon(AUTHOR_ICON)));
		getAuthorButton().setToolTipText( "WYSIWYG Author");
		getDocumentViewButtonPanel().add(getAuthorButton());
		documentViewButtonGroup.add(getAuthorButton());

		getAuthorButton().addItemListener(new AuthorItemListener());

		// Viewer moved to right sidebar - initialized but not added to panel
		setViewerButton(new NavigationButton( "Viewer", getIcon(VIEWER_ICON)));
		getViewerButton().setToolTipText( "XML Viewer");
		// getDocumentViewButtonPanel().add(getViewerButton());
		// documentViewButtonGroup.add(getViewerButton());
		// getViewerButton().addItemListener(new ViewerItemListener());

//		browserButton = new NavigationButton( "Browser", getIcon(BROWSER_ICON));
//		browserButton.setToolTipText( "Browser View");
//		documentViewButtonPanel.add( browserButton);
//		documentViewButtonGroup.add( browserButton);
//
//		browserButton.addItemListener(new BrowserItemListener());

		
		
		/*gridButton = new NavigationButton( "Grid", getIcon(GRID_ICON));
		gridButton.setToolTipText( "Grid Editor");
			
		if(!Identity.getIdentity().getEdition().equals( Identity.XMLPLUS_EDITION_LITE)) {
		
			documentViewButtonPanel.add(gridButton);
			documentViewButtonGroup.add(gridButton);
			gridButton.addItemListener(new GridItemListener());
		}*/
		
		for(int cnt=0;cnt<main.getPluginViews().size();++cnt) {
			Object obj = main.getPluginViews().get(cnt);
			if((obj != null) && (obj instanceof PluginView)) {
				PluginView pluginView = (PluginView)obj;
				if(pluginView != null) {
									
					if((pluginView.getPluginViewPanelFile() != null) && (pluginView.getPluginViewPanelFile().length() > 0)) { 
						NavigationButton pluginNavigationButton = pluginView.getButton();
						if(pluginNavigationButton != null) {
							getDocumentViewButtonPanel().add(pluginNavigationButton);
							documentViewButtonGroup.add(pluginNavigationButton);
							
						}
					}
				}				
			}
		}
		
		/*
		 * TODO
		 * T. Curley 17/05/05
		 * Width + 4 to compensate for the 4 pixel increase in border size
		 * when the button is selected so the text isnt cut off
		 */
		int width = 0;
		width = Math.max(getViewerButton().getPreferredSize().width, width);
		width = Math.max(getEditorButton().getPreferredSize().width, width);
		width = Math.max(getAuthorButton().getPreferredSize().width, width);
//		width = Math.max(browserButton.getPreferredSize().width, width);
		/*width = Math.max(gridButton.getPreferredSize().width, width);*/
		
		//for each of the plugin buttons
		for(int cnt=0;cnt<main.getPluginViews().size();++cnt) {
			Object obj = main.getPluginViews().get(cnt);
			if((obj != null) && (obj instanceof PluginView)) {
				PluginView pluginView = (PluginView)obj;
				if(pluginView != null) {
					
					NavigationButton pluginNavigationButton = pluginView.getButton();
					if(pluginNavigationButton != null) {
						width = Math.max(pluginNavigationButton.getPreferredSize().width, width);
						
					}
				}				
			}
		}
		
		width = width + 4;
		getEditorButton().setPreferredSize(new Dimension(width, 29));
		getViewerButton().setPreferredSize(new Dimension(width, 29));
		getAuthorButton().setPreferredSize(new Dimension(width, 29));
//		browserButton.setPreferredSize(new Dimension(width, 29));
		/*gridButton.setPreferredSize(new Dimension(width, 29));*/
		
//		for each of the plugin buttons
		for(int cnt=0;cnt<main.getPluginViews().size();++cnt) {
			Object obj = main.getPluginViews().get(cnt);
			if((obj != null) && (obj instanceof PluginView)) {
				PluginView pluginView = (PluginView)obj;
				if(pluginView != null) {
					
					NavigationButton pluginNavigationButton = pluginView.getButton();
					if(pluginNavigationButton != null) {
						pluginNavigationButton.setPreferredSize(new Dimension(width, 29));
						
					}
				}				
			}
		}

		getViewerButton().setEnabled(false);
		getViewerViewItem().setEnabled(false);
		getEditorButton().setEnabled(false);
		getEditorViewItem().setEnabled(false);
		getAuthorButton().setEnabled(false);
		getAuthorViewItem().setEnabled(false);
		getAuthorSplitItem().setEnabled(false);
//		browserButton.setEnabled(false);
//		getBrowserViewItem().setEnabled(false);
		/*gridButton.setEnabled(false);
		getGridViewItem().setEnabled(false);*/
		
//		for each of the plugin buttons
		for(int cnt=0;cnt<main.getPluginViews().size();++cnt) {
			Object obj = main.getPluginViews().get(cnt);
			if((obj != null) && (obj instanceof PluginView)) {
				PluginView pluginView = (PluginView)obj;
				if(pluginView != null) {
					
					NavigationButton pluginNavigationButton = pluginView.getButton();
					if(pluginNavigationButton != null) {
						pluginNavigationButton.setEnabled(false);
					}
					JRadioButtonMenuItem pluginMenuItem = pluginView.getPluginViewItem();
					if(pluginMenuItem != null) {
						pluginMenuItem.setEnabled(false);
					}
					
				}				
			}
		}
		
		main.setDocumentViewButtonPanel(getDocumentViewButtonPanel());
		main.updateDocumentViewButtonPanel();
		
		
	}
	
	public void setupViewMenu() {
		// Editing modes (radio). The Schema, Outliner and Viewer modes are
		// obsolete and intentionally absent (their views still exist in code
		// but are no longer reachable from the menu). The preview commands
		// live directly below this submenu in the View menu (MenuBuilder).
		ButtonGroup group = new ButtonGroup();
		setDocumentViewsMenu(new JMenu("Document Views"));
		getDocumentViewsMenu().setMnemonic('D');

		JRadioButtonMenuItem item = getEditorViewItem();
		group.add(item);
		getDocumentViewsMenu().add(item);

		item = getAuthorViewItem();
		group.add(item);
		getDocumentViewsMenu().add(item);

//		for each of the plugin buttons
		for(int cnt=0;cnt<main.getPluginViews().size();++cnt) {
			Object obj = main.getPluginViews().get(cnt);
			if((obj != null) && (obj instanceof PluginView)) {
				PluginView pluginView = (PluginView)obj;
				if(pluginView != null) {

					JRadioButtonMenuItem pluginItem = pluginView.getPluginViewItem();
					if(pluginItem != null) {
						group.add(pluginItem);
						getDocumentViewsMenu().add(pluginItem);
					}

				}
			}
		}

		getDocumentViewsMenu().addSeparator();
		getDocumentViewsMenu().add(getAuthorSplitItem());

		GUIUtilities.alignMenu( getDocumentViewsMenu());

		main.setDocumentViewsMenu(getDocumentViewsMenu());
		main.updateDocumentViewMenu();
	}
	
	public void addViewToViewMenu(JMenu viewMenu) {
		
	}
	
	public void changeView(ViewPanel current) {
		
		setCurrent(current);
		
		//updateDocNameLabel(document.getName());
		
		
		if (current instanceof Editor) {
			getEditorButton().setSelected(true);
			editorItem.setSelected(true);
		}
	}
	
	public void removeUserView(String identifier) {
	    
	    //see if a view with that identifier already exists
	    boolean alreadyExists = false;
	    
	    for(int cnt=0;cnt<userViews.size();++cnt) {
	    	
	    	UserView tempUserView = (UserView) userViews.get(cnt);
	    	if(tempUserView.getIdentifier().equals(identifier)) {
	    		alreadyExists = true;
	    		
	    		main.getDocumentViewButtonPanel().remove(tempUserView.getButton());
			    main.getDocumentViewButtonGroup().remove(tempUserView.getButton());
	    		
	    		userViews.remove(cnt);
	    		remove(tempUserView.getPanel());
	    	}
	    }
	    
	    
	    this.repaint();
	    if(alreadyExists == false) {
	    	//MessageHandler.showError("A view with the identifier "+identifier+ " could not be found", "Script Error");
	    	
	    	
	    }
	}
	
//	public Browser getBrowser() {
//		if ( browser == null) { 
//			browser = new Browser( main, properties.getBrowserProperties());
//		}
//
//		return browser;
//	}
	
	// make sure the tabbed view knows about this.
	public void setFocussed() {
		Component parent = getParent();

		while ( parent != null) {
			if ( parent instanceof DogsBayTabbedView) {
				((DogsBayTabbedView)parent).setFocussed();

				checkExternalModification();

				return;
			}

			parent = parent.getParent();
		}
	}

	/**
	 * Checks if the document has been modified externally and handles it:
	 * - Clean buffer (no unsaved changes): silently reloads
	 * - Dirty buffer (unsaved changes): prompts the user
	 */
	public void checkExternalModification() {
		if ( document != null && document.hasChangedOnDisk()) {
			if ( !changeManager.isChanged()) {
				// Clean buffer — silently reload without prompting (no error dialog
				// either). reload() -> document.load() acknowledges the new state.
				reload( false);
			} else {
				// Dirty buffer — prompt the user
				int value = MessageHandler.showConfirm( "Document \""+document.getName()+"\" has been changed by another process.\n"+
														"Do you want to reload the document?");

				if ( value == JOptionPane.YES_OPTION) {
					main.getReloadAction().execute();
				} else {
					// Declined: record that we have seen this change, or the same
					// prompt reappears on every window focus.
					document.acknowledgeDiskState();
				}
			}
		}
	}

	/**
	 * Checks if the document has been modified externally, but only
	 * silently reloads clean buffers. Dirty buffers are skipped and
	 * will be handled when the user focuses the tab.
	 */
	public void checkExternalModificationSilent() {
		// A dirty buffer is left alone entirely, so the change is still pending when
		// the user focuses this tab and checkExternalModification() can prompt.
		if ( document == null || changeManager.isChanged()) {
			return;
		}
		if ( document.hasChangedOnDisk()) {
			// Background/silent path — never pop a modal dialog here.
			reload( false);
		}
	}

	public ErrorList getErrors() {
		return errors;
	}

	public XPathList getXPathList() {
		return results;
	}

	public void reload() {
		// Explicit (user-triggered) reload — surface load failures.
		reload( true);
	}

	/**
	 * Reloads the document from disk.
	 *
	 * @param showError when true a failed load is reported via a modal dialog; the
	 *        automatic external-change checks pass false so a background reload of a
	 *        clean buffer never interrupts the user with a dialog. Either way a failed
	 *        load keeps the last-good buffer and does not discard edits.
	 */
	public void reload( boolean showError) {
		try {
			document.load();
		} catch( org.xml.sax.SAXParseException notWellFormed) {
			// The text WAS read: DogsBayDocument.setText() assigns the new content
			// before it parses, and only then throws. This is the normal path for
			// Markdown/AsciiDoc/plain text (XMLUtilities.NotXMLException extends
			// SAXParseException) and for XML that is momentarily malformed on disk —
			// common right after an external edit. Bailing here would leave the
			// editor showing pre-reload text with the model already advanced, and
			// the external-change flag already consumed, so nothing would ever
			// re-sync it. Fall through and refresh; the error markers come from
			// validation, not from here.
		} catch( Exception e) {
			// A genuine read failure (IO, missing file). Keep the last successfully
			// loaded buffer rather than discarding edits.
			e.printStackTrace();
			if ( showError) {
				MessageHandler.showError( "Could not reload document \""+
						(document != null ? document.getName() : "")+"\".", e, "Reload Error");
			}
			return;
		}

		// document.load() refreshes the *model* only — the text component keeps its
		// own buffer (see CLAUDE.md, "getText() returns the model text, not the live
		// editor buffer"). Nothing else pushes one into the other: Editor's
		// documentUpdated() only flags the model stale for the editor-to-model
		// direction. So without this the file changed on disk, the model followed,
		// and the editor went on showing the old text — then the next save wrote
		// that stale buffer back over the change.
		//
		// This is the single refresh point for every reload path: Git discard/stash/
		// checkout/pull, an agent turn's edits, the map and reltable editors, the
		// window-focus external-change check, and File > Reload.
		Runnable refresh = new Runnable() {
			public void run() {
				try {
					refreshCurrentView();
				} finally {
					// Always runs: the reload is not a user edit, so its undo entries
					// must go or the tab stays marked modified. In a try/finally
					// because a refresh failure must not abort the caller either —
					// checkAllDocumentsForExternalChanges loops over every open
					// document, and an exception here would skip all the rest.
					changeManager.discardAllEdits();
				}
			}
		};

		// ReloadAction runs this on a background thread, the Git and agent paths on
		// the EDT. Marshal either way, and run inline when already on the EDT so the
		// buffer is up to date before the caller continues.
		if ( SwingUtilities.isEventDispatchThread()) {
			refresh.run();
		} else {
			SwingUtilities.invokeLater( refresh);
		}
	}

	/**
	 * Pushes the reloaded model into whichever view is on screen.
	 *
	 * <p>Views other than the current one refresh lazily when switched to (each
	 * view's {@code setDocument} runs on the switch), so only the visible one has
	 * to be updated here.
	 *
	 * <p>This dispatch was lost in commit {@code 8f3ce473}, which removed the
	 * Designer branch and emptied the whole block with it — leaving every reload
	 * path updating the model but no view.
	 */
	private void refreshCurrentView() {
		if ( document == null) {
			return;
		}
		if ( current instanceof Editor) {
			// Preserve the caret: setDocument re-sets the text, which parks it at 0,
			// and a background reload that scrolls the user to the top is jarring.
			int caret = ((Editor)current).getCursorPosition();
			((Editor)current).setDocument( document);
			setCaretSafely( (Editor)current, caret);
		} else if ( current instanceof Viewer) {
			((Viewer)current).setDocument( document);
		} else if ( current instanceof Browser) {
			((Browser)current).setDocument( document);
		} else if ( current instanceof AuthorView) {
			// Only when the document moved underneath it. Re-importing an
			// up-to-date Author view would replace the block model, which
			// throws away the undo history that model belongs to — and the
			// Author view's own edits come back here through this path.
			if ( !((AuthorView)current).hasLatestInformation()) {
				((AuthorView)current).setDocument( document);
			}
		} else if ( current instanceof AuthorSplitView) {
			((AuthorSplitView)current).refreshBothPanes();
		} else if ( current instanceof PluginViewPanel) {
			((PluginViewPanel)current).setDocument( document);
		}
	}

	/**
	 * Restores the caret, tolerating a document that shrank or emptied.
	 *
	 * <p>{@code Editor.setCursorPosition} only clamps when the document is
	 * non-empty ({@code pos >= length && length > 0}), so restoring a non-zero
	 * caret into a file an external change truncated to empty throws
	 * {@code IllegalArgumentException} out of the reload.
	 */
	private void setCaretSafely( Editor target, int caret) {
		try {
			target.setCursorPosition( Math.max( caret, 0));
		} catch( RuntimeException outOfRange) {
			target.setCursorPosition( 0);
		}
	}

	public DogsBayDocument getDocument() {
		return document;
	}
	
	public void updateGrammar( GrammarProperties grammar) {
		if ( this.grammar != null && grammar != null && this.grammar.getID().equals( grammar.getID())) {
			setTagCompletionSchemas( FileUtilities.createTagCompletionSchemas( document, grammar));
	
			updateValidationGrammar();
		}
	}

	public SchemaDocument getSchema() {
		return schema;
	}

	public Vector getTagCompletionSchemas() {
		return tagSchemas;
	}

	public ViewPanel getCurrentView() {
		return current;
	}

	public String getStatusType() {
		if ( grammar != null) {
			return grammar.getDescription();
		} else if ( document != null && document.isDTD()) {
			return "Document Type Definition";
		}
		
		return "";
	}

	public String getStatusValidator() {
		int type = -1;

		if ( document != null && document.isXML()) {
			if ( validationGrammar.useExternal()) {
				type = validationGrammar.getType();
			} else {
				type = document.getInternalGrammarType();
			}
		}
				
		switch ( type) {
			case XMLGrammar.TYPE_XSD:
				return "XSD";
			case XMLGrammar.TYPE_RNG:
				return "RNG";
			case XMLGrammar.TYPE_RNC:
				return "RNC";
			case XMLGrammar.TYPE_NRL:
				return "NRL";
			case XMLGrammar.TYPE_DTD:
				return "DTD";
			default:
				return "";
		}
	}

	public String getStatusLocation() {
		if ( document != null && document.isXML()) {
			return validationGrammar.useExternal() ? "EXT" : "INT";
		} else {
			return "";
		}
	}

	public String getStatusDocumentStatus() {
		if ( document != null && document.isXML()) {
			if ( document.isError()) {
				return Statusbar.DOCUMENT_STATUS_ERROR;
			} else if ( changeManager.isTextChanged()) {
				return Statusbar.DOCUMENT_STATUS_UNKNOWN;
			} else if ( changeManager.isValidated()) {
				return Statusbar.DOCUMENT_STATUS_VALID;
			} else {
				return Statusbar.DOCUMENT_STATUS_WELLFORMED;
			}
		}
		
		return "";
	}

	private void setCurrent( ViewPanel view) {
		current = view;
		main.setCurrent( view);
	}

	public void setProperties() {
		editor.setProperties();
		viewer.setProperties();
		author.setProperties();
		//grid.setProperties();
		
//		for each of the plugin buttons
		/*for(int cnt=0;cnt<this.main.getPluginViews().size();++cnt) {
			Object obj = this.main.getPluginViews().get(cnt);
			if((obj != null) && (obj instanceof PluginView)) {
				PluginView pluginView = (PluginView)obj;
				if(pluginView != null) {
					
					PluginViewPanel pluginViewPanel = pluginView.getPluginViewPanel();
					if(pluginViewPanel != null) {
						pluginViewPanel.setProperties();
					}
				}				
			}
		}*/
		
		for(int cnt=0;cnt<this.getPluginViewPanels().size();++cnt) {
			Object obj = this.getPluginViewPanels().get(cnt);
			if((obj != null) && (obj instanceof PluginViewPanel)) {
				PluginViewPanel pluginViewPanel = (PluginViewPanel)obj;
				if(pluginViewPanel != null) {					
					pluginViewPanel.setProperties();					
				}				
			}
		}
	}
	
	public void updateBookmarks() {
		editor.updateBookmarks();
	}

	public void initBookmarks() {
		editor.initBookmarks( properties.getBookmarks());
	}

	public boolean isChanged() {
		return changeManager.isChanged() || document.getURL() == null;
	}

	public void stateChanged( ChangeEvent e) {
//		main.getParseAction().setEnabled( !changeManager.isParsed());
		main.getParseAction().setParsed( !changeManager.isTextChanged() && !document.isError());
		main.getValidateAction().setValidated( changeManager.isValidated());
		updateTitle();
		main.updateStatus();
		setViewIcons();
	}
	
	private void updateTitle() {
		if ( isChanged() || document.getURL() == null) {
			main.setViewTitle( this, document.getName()+"*");
		} else {
			main.setViewTitle( this, document.getName());
		}
	}

	public void setGrammar( GrammarProperties grammar) {
		if (DEBUG) System.out.println( "DogsBayView.setGrammar( "+grammar+")");
		this.grammar = grammar;
		setViewIcons();
		updateValidationGrammar();

		main.updateProperties();
	}

	public GrammarProperties getGrammar() {
		return grammar;
	}

	public XMLGrammarImpl getValidationGrammar() {
		return validationGrammar;
	}

	public void updateValidationGrammar() {
		if (DEBUG) System.out.println( "DogsBayView.updateValidationGrammar()");

		if ( grammar != null) {
			validationGrammar.setExternal( grammar.useExternal());
			validationGrammar.setType( grammar.getType());
			validationGrammar.setLocation( grammar.getLocation());
		}
		
		if ( grammar != null && editor != null) {
			editor.setFragments( grammar.getFragments());
		}

		main.updateFragments();
		main.updateStatus();
	}

	public void updatePreferences() {
		editor.updatePreferences();
		viewer.updatePreferences();
		author.updatePreferences();
//		browser.updatePreferences();
		//grid.updatePreferences();
		
//		for each of the plugin buttons
		/*for(int cnt=0;cnt<this.main.getPluginViews().size();++cnt) {
			Object obj = this.main.getPluginViews().get(cnt);
			if((obj != null) && (obj instanceof PluginView)) {
				PluginView pluginView = (PluginView)obj;
				if(pluginView != null) {
					
					PluginViewPanel pluginViewPanel = pluginView.getPluginViewPanel();
					if(pluginViewPanel != null) {
						pluginViewPanel.updatePreferences();
					}
				}				
			}
		}*/
		
		for(int cnt=0;cnt<this.getPluginViewPanels().size();++cnt) {
			Object obj = this.getPluginViewPanels().get(cnt);
			if((obj != null) && (obj instanceof PluginViewPanel)) {
				PluginViewPanel pluginViewPanel = (PluginViewPanel)obj;
				if(pluginViewPanel != null) {					
					pluginViewPanel.updatePreferences();					
				}				
			}
		}
	}

	public Editor getEditor() {
		return editor;
	}
	
	public Viewer getViewer() {
		return viewer;
	}
	public AuthorView getAuthorView() {
		return author;
	}

	public DogsBayAIEditor getMainEditor() {
		return main;
	}

	public NavigationButton getAuthorButton() {
		return authorButton;
	}

	public void setAuthorButton(NavigationButton authorButton) {
		this.authorButton = authorButton;
	}

	public JRadioButtonMenuItem getAuthorViewItem() {
		if (authorItem == null) {
			authorItem = new JRadioButtonMenuItem("Author");
			authorItem.setIcon( getIcon(DogsBayView.AUTHOR_ICON));
			authorItem.setMnemonic('A');
			authorItem.setAccelerator( javax.swing.KeyStroke.getKeyStroke(
					java.awt.event.KeyEvent.VK_A,
					java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.ALT_DOWN_MASK));
			authorItem.addItemListener(new AuthorItemListener());
		}

		return authorItem;
	}

	/** Checkbox toggling the side-by-side XML + Author split (Ctrl+Alt+S). */
	public javax.swing.JCheckBoxMenuItem getAuthorSplitItem() {
		if (authorSplitItem == null) {
			authorSplitItem = new javax.swing.JCheckBoxMenuItem("Split: XML + Author");
			authorSplitItem.setMnemonic('S');
			authorSplitItem.setAccelerator( javax.swing.KeyStroke.getKeyStroke(
					java.awt.event.KeyEvent.VK_S,
					java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.ALT_DOWN_MASK));
			authorSplitItem.addItemListener(new ItemListener() {
				public void itemStateChanged(ItemEvent event) {
					boolean selected = event.getStateChange() == ItemEvent.SELECTED;
					boolean splitActive = getCurrentView() instanceof AuthorSplitView;
					try {
						if (selected && !splitActive) {
							main.switchToAuthorSplit();
						} else if (!selected && splitActive) {
							if (authorSplit.getLiveSide() == AuthorSplitView.Side.AUTHOR) {
								main.switchToAuthor();
							} else {
								main.switchToEditor();
							}
						}
					} catch (Exception e) {
						authorSplitItem.setSelected(false);
						MessageHandler.showMessage( "Please ensure the document is well-formed\nbefore splitting with the \"Author\" view.");
					}
				}
			});
		}

		return authorSplitItem;
	}
	//public Grid getGrid() {
	//	return grid;
	//}

	/**
	 * The Author view's selection as a path, when the writer is actually in an
	 * Author view. In the split only the live pane counts: the mirror keeps a
	 * selection of its own, and following that would move the caret away from
	 * the line being typed in the XML pane.
	 */
	private java.util.List<com.dogsbay.dogsbayaieditor.author.BlockTextMapper.Step> authorPathOfCurrentView() {
		try {
			if ( current instanceof AuthorView) {
				return author.getSelectionPath();
			}
			if ( current instanceof AuthorSplitView split
					&& split.getLiveSide() == AuthorSplitView.Side.AUTHOR) {
				return author.getSelectionPath();
			}
		} catch ( RuntimeException failed) {
			failed.printStackTrace();   // a convenience, never a reason to fail the switch
		}
		return null;
	}

	/** The text position of the element a block path names, or -1 when it cannot be found. */
	private int positionOfPath(
			java.util.List<com.dogsbay.dogsbayaieditor.author.BlockTextMapper.Step> path) {
		// A failed parse leaves getRoot() null and getLastRoot() describing the
		// PREVIOUS text, whose positions no longer mean anything.
		if ( path == null || document == null || document.isError() || document.getRoot() == null) {
			return -1;
		}
		try {
			org.dom4j.Element element = com.dogsbay.dogsbayaieditor.author.BlockTextMapper.resolveOrNull(
					document.getRoot(), path);
			if ( !(element instanceof com.dogsbay.xml.XElement found)) {
				return -1;
			}
			int at = found.getContentStartPosition() > 0
					? found.getContentStartPosition() : found.getElementEndPosition();
			return at > 0 ? at : -1;
		} catch ( RuntimeException failed) {
			failed.printStackTrace();
			return -1;
		}
	}

	/** The block path of the element at a text position, or null when there is none. */
	private java.util.List<com.dogsbay.dogsbayaieditor.author.BlockTextMapper.Step> pathOfPosition( int pos) {
		if ( document == null || document.isError()) {
			return null;
		}
		try {
			com.dogsbay.xml.XElement element = document.getLastElement( pos);
			return element == null ? null : com.dogsbay.dogsbayaieditor.author.BlockTextMapper.pathOf( element);
		} catch ( RuntimeException failed) {
			failed.printStackTrace();
			return null;
		}
	}

	/**
	 * After an undo or redo, brings the views that did not make the change
	 * back in line: an Author-view edit is exported to the document and the
	 * XML side reloaded, a text edit is parsed and the Author side re-imported.
	 * Runs with the change manager syncing, so none of it is recorded.
	 */
	private void synchroniseAfterUndo( javax.swing.undo.UndoableEdit edit) {
		if ( document == null) {
			return;
		}
		try {
			if ( edit instanceof com.dogsbay.dogsbayaieditor.author.UndoableAuthorEdit) {
				author.applyToDocument();
				if ( current instanceof AuthorSplitView) {
					((AuthorSplitView) current).refreshEditorFromModel();
				} else if ( !(current instanceof AuthorView)) {
					refreshCurrentView();
				}
			} else {
				// a text edit: the Author model must be rebuilt from the new text
				if ( current instanceof AuthorSplitView) {
					editor.parse();
					((AuthorSplitView) current).refreshAuthorFromText();
				} else if ( current instanceof AuthorView) {
					editor.parse();
					refreshCurrentView();
				}
			}
		} catch ( Exception failed) {
			// Mid-undo XML that does not parse. The block model must not be left
			// ahead of the document: the split view's mirror would write it back
			// and quietly reverse the undo. Re-import it from what the document
			// actually holds.
			failed.printStackTrace();
			try {
				author.setDocument( document);
			} catch ( RuntimeException alsoFailed) {
				alsoFailed.printStackTrace();
			}
		}
	}

	/** The application configuration, for views that keep preferences (Author font scale). */
	public ConfigurationProperties getProperties() {
		return properties;
	}

	public ChangeManager getChangeManager() {
		return changeManager;
	}
	
	/*public void updateDocNameLabel(final String text) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				docNameLabel.setText(text);
				docNameLabel.invalidate();
				docNameLabel.repaint();
			}
		});
	}*/

	public void setDocument( DogsBayDocument document) {
		
		if(document != null) {
			if ( this.document != null) {
				this.document.removeListener( this);
			}
		}
			
		
			
		this.document = document;
		
		errors.setDocument( document);

		if ( document != null) {
			document.addListener( this);
			
			//updateDocNameLabel(document.getName());
			
			document.setGrammar( validationGrammar);

			if ( !document.isError() && document.isXML()) {
				XElement root = document.getRoot();
				XDocument doc = document.getDocument();
				DocumentType docType = null;
				
				if ( doc != null) {
					doc.getDocType();
				}

				if ( docType != null) {
					defaultValidationGrammar = "DTD";
				} else if ( root != null) {
					String location = root.getAttribute( "schemaLocation");
					
					if ( location == null) {
						location = root.getAttribute( "noNamespaceSchemaLocation");
					}
					
					if ( location != null) {
						defaultValidationGrammar = "XSD";
					} else {
						defaultValidationGrammar = "";
					}
				} else {
					defaultValidationGrammar = "";
				}
				
				if (getSchema() != null) {
				}
				


				viewerButton.setEnabled(true);
				getViewerViewItem().setEnabled(true);

				getAuthorButton().setEnabled(true);
				getAuthorViewItem().setEnabled(true);
				getAuthorSplitItem().setEnabled(true);

//					for each of the plugin buttons
				for(int cnt=0;cnt<main.getPluginViews().size();++cnt) {
					Object obj = main.getPluginViews().get(cnt);
					if((obj != null) && (obj instanceof PluginView)) {
						PluginView pluginView = (PluginView)obj;
						if(pluginView != null) {
							
							NavigationButton pluginButton = pluginView.getButton();
							if(pluginButton != null) {
								pluginButton.setEnabled(true);
							}
							
							JRadioButtonMenuItem pluginItem = pluginView.getPluginViewItem();
							if(pluginItem != null) {
								pluginItem.setEnabled(true);
							}
							
						}				
					}
				}
				
				getEditorButton().setEnabled(true);
			}
			else {
				getEditorButton().setEnabled(true);
				
				
				
				getViewerButton().setEnabled(false);
				getViewerViewItem().setEnabled(false);
				getDocumentViewButtonPanel().remove(getViewerButton());
				getDocumentViewsMenu().remove(getViewerViewItem());

				getAuthorButton().setEnabled(false);
				getAuthorViewItem().setEnabled(false);
				getAuthorSplitItem().setEnabled(false);

				/*gridButton.setEnabled(false);
				getGridViewItem().setEnabled(false);*/
				
//					for each of the plugin buttons
				for(int cnt=0;cnt<main.getPluginViews().size();++cnt) {
					Object obj = main.getPluginViews().get(cnt);
					if((obj != null) && (obj instanceof PluginView)) {
						PluginView pluginView = (PluginView)obj;
						if(pluginView != null) {
							
							NavigationButton pluginButton = pluginView.getButton();
							if(pluginButton != null) {
								pluginButton.setEnabled(false);
								getDocumentViewButtonPanel().remove(pluginButton);								
							}
							
							JRadioButtonMenuItem pluginItem = pluginView.getPluginViewItem();
							if(pluginItem != null) {
								pluginItem.setEnabled(false);
								getDocumentViewsMenu().remove(pluginItem);
								
							}
							
						}				
					}
				}
				
				for(int cnt=0;cnt<getUserViews().size();++cnt) {
				    UserView userView = (UserView) getUserViews().get(cnt);
				    userView.getButton().setEnabled(true);
				}
				
				getEditorButton().setEnabled(true);
				getEditorViewItem().setEnabled(true);
			}
			
			getEditorButton().setEnabled(true);
			getEditorViewItem().setEnabled(true);
			
//			setTitle( "DogsBayAIEditor - "+document.getName());
		}
		else {
			
			
			getViewerButton().setEnabled(false);
			getViewerViewItem().setEnabled(false);

			getDocumentViewButtonPanel().remove(getViewerButton());
			getDocumentViewsMenu().remove(getViewerViewItem());

			getAuthorButton().setEnabled(false);
			getAuthorViewItem().setEnabled(false);
			getAuthorSplitItem().setEnabled(false);

			getEditorButton().setEnabled(false);
			getEditorViewItem().setEnabled(false);
			
			getDocumentViewButtonPanel().remove(getEditorButton());
			getDocumentViewsMenu().remove(getEditorViewItem());
//				browserButton.setEnabled(false);
//				getBrowserViewItem().setEnabled(false);
			/*gridButton.setEnabled(false);
			getGridViewItem().setEnabled(false);*/
			
//				for each of the plugin buttons
			for(int cnt=0;cnt<main.getPluginViews().size();++cnt) {
				Object obj = main.getPluginViews().get(cnt);
				if((obj != null) && (obj instanceof PluginView)) {
					PluginView pluginView = (PluginView)obj;
					if(pluginView != null) {
						
						NavigationButton pluginButton = pluginView.getButton();
						if(pluginButton != null) {
							pluginButton.setEnabled(false);
							getDocumentViewButtonPanel().remove(pluginButton);
							
						}
						
						JRadioButtonMenuItem pluginItem = pluginView.getPluginViewItem();
						if(pluginItem != null) {
							pluginItem.setEnabled(false);
							getDocumentViewsMenu().remove(pluginItem);
						}
						
					}				
				}
			}
		}
			
		editor.setDocument( document);
		//grid.setDocument( document);
//		browser.setDocument( document);
//		viewer.setDocument( document);

//		for each of the plugin buttons
		/*for(int cnt=0;cnt<this.main.getPluginViews().size();++cnt) {
			Object obj = this.main.getPluginViews().get(cnt);
			if((obj != null) && (obj instanceof PluginView)) {
				PluginView pluginView = (PluginView)obj;
				if(pluginView != null) {
					
					PluginViewPanel pluginViewPanel = pluginView.getPluginViewPanel();
					if(pluginViewPanel != null) {
						pluginViewPanel.setDocument(document);
					}
				}				
			}
		}*/
		
		for(int cnt=0;cnt<this.getPluginViewPanels().size();++cnt) {
			Object obj = this.getPluginViewPanels().get(cnt);
			if((obj != null) && (obj instanceof PluginViewPanel)) {
				PluginViewPanel pluginViewPanel = (PluginViewPanel)obj;
				if(pluginViewPanel != null) {					
					pluginViewPanel.setDocument(document);					
				}				
			}
		}
		
		updateTitle();
		
		main.updateStatus();
		
		main.setDocumentViewButtonPanel(getDocumentViewButtonPanel());
		main.updateDocumentViewButtonPanel();
		
		main.setDocumentViewsMenu(getDocumentViewsMenu());
		main.updateDocumentViewMenu();
		
		

//		setChanged( false);
//		changeManager.discardAllEdits();
		
	}
	
// Implementation of the DogsBayDocumentListener interface...	
	public void documentUpdated( DogsBayDocumentEvent event) {
		if (DEBUG) System.out.println( "DogsBayView.documentUpdated()");

		// perform on event dispatch thread...
		SwingUtilities.invokeLater( new Runnable() {
		    public void run() {
				if ( document != null) { 
					if ( !document.isError() && document.isXML()) {
						XElement root = document.getRoot();
						XDocument doc = document.getDocument();
						DocumentType docType = null;
						
						if ( doc != null) {
							doc.getDocType();
						}
		
						if ( docType != null) {
							defaultValidationGrammar = "DTD";
						} else if ( root != null) {
							String location = root.getAttribute( "schemaLocation");
							
							if ( location == null) {
								location = root.getAttribute( "noNamespaceSchemaLocation");
								if (DEBUG) System.out.println( "noNamespaceSchemaLocation = "+location);
							} else {
								if (DEBUG) System.out.println( "schemaLocation = "+location);
							}
							
							if ( location != null) {
								defaultValidationGrammar = "XSD";
							} else {
								defaultValidationGrammar = "";
							}
						} else {
							defaultValidationGrammar = "";
						}
					}

					setViewIcons();
					updateNames();
					updateTitle();
					main.updateStatus();
				}
		    }
		});
	}
	
	/**
	 * Intentionally empty. This only ever enabled the Schema Viewer and Designer
	 * buttons, both removed with XML Schema support in commit {@code 8f3ce473}.
	 * Unlike {@code updateModel()} and {@code reload()} — emptied by the same
	 * commit <em>by accident</em> — there is nothing here to restore.
	 *
	 * @param schema the associated grammar, or null
	 */
	public void setSchemaInternal(SchemaDocument schema) {
		if (schema != null) {

			if (document != null && !document.isError() && document.isXML()) {
			}
		} else { // schema == null
			//			statusbar.setSchemaType( "");
		}
	}
	
	public void setViewIcons() {
		if ( document != null) {
			if ( document.isXML()) {
				if ( document.isError() && !document.isHTML()) {
					if ( document.isRemote()) {
						main.setViewIcon( this, IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_REMOTE, IconFactory.XML_STATUS_ERROR));
					} else if ( document.isReadOnly()) {
						main.setViewIcon( this, IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_READ_ONLY, IconFactory.XML_STATUS_ERROR));
					} else {
						main.setViewIcon( this, IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_NORMAL, IconFactory.XML_STATUS_ERROR));
					}
				} else if ( changeManager != null && changeManager.isTextChanged()) {
						if ( document.isRemote()) {
							main.setViewIcon( this, IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_REMOTE, IconFactory.XML_STATUS_UNKNOWN));
						} else if ( document.isReadOnly()) {
							main.setViewIcon( this, IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_READ_ONLY, IconFactory.XML_STATUS_UNKNOWN));
						} else {
							main.setViewIcon( this, IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_NORMAL, IconFactory.XML_STATUS_UNKNOWN));
						}
				} else if ( changeManager != null && changeManager.isValidated()) {
					if ( document.isRemote()) {
						main.setViewIcon( this, IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_REMOTE, IconFactory.XML_STATUS_VALID));
					} else if ( document.isReadOnly()) {
						main.setViewIcon( this, IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_READ_ONLY, IconFactory.XML_STATUS_VALID));
					} else {
						main.setViewIcon( this, IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_NORMAL, IconFactory.XML_STATUS_VALID));
					}
				} else { // if ( !document.isError()) {
					if ( document.isRemote()) {
						main.setViewIcon( this, IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_REMOTE, IconFactory.XML_STATUS_NORMAL));
					} else if ( document.isReadOnly()) {
						main.setViewIcon( this, IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_READ_ONLY, IconFactory.XML_STATUS_NORMAL));
					} else {
						main.setViewIcon( this, IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_NORMAL, IconFactory.XML_STATUS_NORMAL));
					}
				} 
			} else if ( document.isDTD()) {
				// not xml ...
				if ( document.isRemote()) {
					main.setViewIcon( this, IconFactory.getIcon( IconFactory.DOCUMENT_TYPE_DTD, IconFactory.FILE_STATUS_REMOTE, IconFactory.XML_STATUS_NORMAL));
				} else if ( document.isReadOnly()) {
					main.setViewIcon( this, IconFactory.getIcon( IconFactory.DOCUMENT_TYPE_DTD, IconFactory.FILE_STATUS_READ_ONLY, IconFactory.XML_STATUS_NORMAL));
				} else {
					main.setViewIcon( this, IconFactory.getIcon( IconFactory.DOCUMENT_TYPE_DTD, IconFactory.FILE_STATUS_NORMAL, IconFactory.XML_STATUS_NORMAL));
				}
			} else {
				// not xml ...
				URL url = document.getURL();
				String extension = "";
				
				if ( url != null) {
					String location = url.toString();
					int pos = location.lastIndexOf( ".");

					if ( pos != -1 && ((pos+1) < location.length())) {
					    extension = location.substring( pos+1, location.length());
					}
				}
				
				if ( document.isRemote()) {
					main.setViewIcon( this, IconFactory.getIconForExtension( extension, IconFactory.FILE_STATUS_REMOTE));
				} else if ( document.isReadOnly()) {
					main.setViewIcon( this, IconFactory.getIconForExtension( extension, IconFactory.FILE_STATUS_READ_ONLY));
				} else {
					main.setViewIcon( this, IconFactory.getIconForExtension( extension, IconFactory.FILE_STATUS_NORMAL));
				}
			}
		}
	}

	public Icon getViewIcon() {
		if ( document != null) {
			if ( document.isXML()) {
				if ( document.isError() && !document.isHTML()) {
					if ( document.isRemote()) {
						return IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_REMOTE, IconFactory.XML_STATUS_ERROR);
					} else if ( document.isReadOnly()) {
						return IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_READ_ONLY, IconFactory.XML_STATUS_ERROR);
					} else {
						return IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_NORMAL, IconFactory.XML_STATUS_ERROR);
					}
				} else if ( changeManager != null && changeManager.isTextChanged()) {
						if ( document.isRemote()) {
							return IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_REMOTE, IconFactory.XML_STATUS_UNKNOWN);
						} else if ( document.isReadOnly()) {
							return IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_READ_ONLY, IconFactory.XML_STATUS_UNKNOWN);
						} else {
							return IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_NORMAL, IconFactory.XML_STATUS_UNKNOWN);
						}
				} else if ( changeManager != null && changeManager.isValidated()) {
					if ( document.isRemote()) {
						return IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_REMOTE, IconFactory.XML_STATUS_VALID);
					} else if ( document.isReadOnly()) {
						return IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_READ_ONLY, IconFactory.XML_STATUS_VALID);
					} else {
						return IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_NORMAL, IconFactory.XML_STATUS_VALID);
					}
				} else { // if ( !document.isError()) {
					if ( document.isRemote()) {
						return IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_REMOTE, IconFactory.XML_STATUS_NORMAL);
					} else if ( document.isReadOnly()) {
						return IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_READ_ONLY, IconFactory.XML_STATUS_NORMAL);
					} else {
						return IconFactory.getIconForType( getGrammar(), IconFactory.FILE_STATUS_NORMAL, IconFactory.XML_STATUS_NORMAL);
					}
				} 
			} else if ( document.isDTD()) {
				// not xml ...
				if ( document.isRemote()) {
					return IconFactory.getIcon( IconFactory.DOCUMENT_TYPE_DTD, IconFactory.FILE_STATUS_REMOTE, IconFactory.XML_STATUS_NORMAL);
				} else if ( document.isReadOnly()) {
					return IconFactory.getIcon( IconFactory.DOCUMENT_TYPE_DTD, IconFactory.FILE_STATUS_READ_ONLY, IconFactory.XML_STATUS_NORMAL);
				} else {
					return IconFactory.getIcon( IconFactory.DOCUMENT_TYPE_DTD, IconFactory.FILE_STATUS_NORMAL, IconFactory.XML_STATUS_NORMAL);
				}
			} else {
				// not xml ...
				URL url = document.getURL();
				String extension = "";
				
				if ( url != null) {
					String location = url.toString();
					int pos = location.lastIndexOf( ".");

					if ( pos != -1 && ((pos+1) < location.length())) {
					    extension = location.substring( pos+1, location.length());
					}
				}
				
				if ( document.isRemote()) {
					return IconFactory.getIconForExtension( extension, IconFactory.FILE_STATUS_REMOTE);
				} else if ( document.isReadOnly()) {
					return IconFactory.getIconForExtension( extension, IconFactory.FILE_STATUS_READ_ONLY);
				} else {
					return IconFactory.getIconForExtension( extension, IconFactory.FILE_STATUS_NORMAL);
				}
			}
		}
		
		return null;
	}

	public void documentDeleted( DogsBayDocumentEvent event) {}

	public XElement getSelectedElement() {
		XElement element = null;

		if ( current instanceof Viewer) {
			element = ((Viewer)current).getSelectedElement();
		}

		return element;
	}

	public XElement getPreviousSelectedElement() {
		return selectedElement;
	}

	public void setSelectedNode( Node node, boolean endTag, Vector namespaces, int y) {
		while ( node != null) {
			Vector results = document.search( node.getUniquePath(), namespaces);
			
			if ( results.size() > 0) {
				Node n = (Node)results.elementAt(0);
				
				if ( n != null) {
					if ( current instanceof Editor) {
						if ( n instanceof XElement) {
							((Editor)current).selectElement( (XElement)n, endTag, y);
						} else if ( n instanceof XAttribute) {
							((Editor)current).selectAttribute( (XAttribute)n, y);
						}
					} else if ( current instanceof Viewer) {
						if ( n instanceof XElement) {
							((Viewer)current).setSelectedElement( (XElement)n, endTag, y);
						} else if ( n instanceof XAttribute) {
							((Viewer)current).setSelectedElement( (XElement)((XAttribute)n).getParent(), false, y);
						}
					}
					
				}
				return;
			}
			
			endTag = false;
			node = (Node)node.getParent();
		}
	}

	// Updates the element name and attribute name list for the 
	private void updateNames() {
		if ( document != null && !document.isError() && document.isXML()) {
			Vector declaredNamespaces = document.getDeclaredNamespaces();

			if ( tagSchemas != null) {
				for ( int i = 0; i < tagSchemas.size(); i++) {
					SchemaDocument schema = (SchemaDocument)tagSchemas.elementAt(i);
					schema.updatePrefixes( declaredNamespaces);
				}
			}
			
			// Add all grammar namespaces!!!
			namespaces = new Vector();
			
			Vector grammars = properties.getGrammarProperties();
			for ( int i = 0; i < grammars.size(); i++) {
				GrammarProperties grammar = (GrammarProperties)grammars.elementAt(i);
				String namespace = grammar.getNamespace();
				
				if ( namespace != null && namespace.length() > 0) {
					String prefix = grammar.getNamespacePrefix();
					String ns = null;

					if ( prefix != null && prefix.length() > 0) {
						ns = "xmlns:"+prefix+"=\""+namespace+"\"";
					} else {
						ns = "xmlns=\""+namespace+"\"";
					}

					if ( !namespaces.contains( ns)) {
						namespaces.addElement( ns);
					}
				}
				
				Vector nss = grammar.getNamespaces();
				for ( int j = 0; j < nss.size(); j++) {
					NamespaceProperties np = (NamespaceProperties)nss.elementAt(j);
					namespace = np.getURI();
					
					if ( namespace != null && namespace.length() > 0) {
						String prefix = np.getPrefix();
						String ns = null;

						if ( prefix != null && prefix.length() > 0) {
							ns = "xmlns:"+prefix+"=\""+namespace+"\"";
						} else {
							ns = "xmlns=\""+namespace+"\"";
						}
						
						if ( !namespaces.contains( ns)) {
							namespaces.addElement( ns);
						}
					}
				}
			}
			
			Vector nss = document.getDeclaredNamespaces();
			for ( int j = 0; j < nss.size(); j++) {
				Namespace np = (Namespace)nss.elementAt(j);
				String namespace = np.getURI();
				
				if ( namespace != null && namespace.length() > 0) {
					String prefix = np.getPrefix();
					String ns = null;

					if ( prefix != null && prefix.length() > 0) {
						ns = "xmlns:"+prefix+"=\""+namespace+"\"";
					} else {
						ns = "xmlns=\""+namespace+"\"";
					}
					
					if ( !namespaces.contains( ns)) {
						namespaces.addElement( ns);
					}
				}
			}

			// Add all other namespaces!!!
			Map namespaceURIs = properties.getPrefixNamespaceMappings();
			Iterator keys = namespaceURIs.keySet().iterator();
			
			while ( keys.hasNext()) {
				Object prefix = keys.next();
				Object namespace = namespaceURIs.get( prefix);
				String ns = "xmlns:"+prefix+"=\""+namespace+"\"";
				
				if ( !namespaces.contains( ns)) {
					namespaces.add( ns);
				}
			}

			Vector documentElementNames = document.getElementNames();
			Vector documentAttributeNames = document.getAttributeNames();

			elementNames = new Vector();
			attributeNames = new Hashtable();
			
			for ( int i = 0; i < documentElementNames.size(); i++) {
				elementNames.addElement( ((QName)documentElementNames.elementAt(i)).getQualifiedName());
			}

			for ( int i = 0; i < documentAttributeNames.size(); i++) {
				QName name = (QName)documentAttributeNames.elementAt(i);
				attributeNames.put( name.getQualifiedName(), document.getAttributeValues( name));
			}
			
			editor.setNamespaces( namespaces);
			editor.setElementNames( elementNames);
			editor.setAttributeNames( attributeNames);
			editor.setEntityNames( getEntityNames());
			editor.setSchemas( tagSchemas);
		}
	}
	
	public Vector getElementNames() {
		return elementNames;
	}
	
	public Vector getAttributeNames() {
		if ( attributeNames != null) {
			return new Vector( attributeNames.keySet());
		}
		
		return null;
	}

	public Vector getGlobalElements() {
		return globalElements;
	}

	public Vector getAllElements() {
		if ( allElements == null) { 
			allElements = new Vector();
			
			if ( tagSchemas != null) {
				for ( int i = 0; i < tagSchemas.size(); i++) {
					Vector elems = ((SchemaDocument)tagSchemas.elementAt(i)).getElements();

					if ( elems != null) {
						allElements.addAll( elems);
					}
				}
			}
		}
		
		return allElements;
	}

	private static Vector getEntityNames() {
		if ( entityNames == null) {
			entityNames = new Vector();
			entityNames.addElement( "&amp;");
			entityNames.addElement( "&gt;");
			entityNames.addElement( "&lt;");
			entityNames.addElement( "&apos;");
			entityNames.addElement( "&quot;");
		}
		
		return entityNames;
	}

	public void setTagCompletionSchemas( Vector schemas) {
		this.tagSchemas = schemas;

		updateNames();

		main.updateProperties();
	}

	public boolean setSchema( SchemaDocument schema) {

		this.schema = schema;
		//grid.setSchema(schema);
		
//		for each of the plugin buttons
		/*for(int cnt=0;cnt<this.main.getPluginViews().size();++cnt) {
			Object obj = this.main.getPluginViews().get(cnt);
			if((obj != null) && (obj instanceof PluginView)) {
				PluginView pluginView = (PluginView)obj;
				if(pluginView != null) {
					
					PluginViewPanel pluginViewPanel = pluginView.getPluginViewPanel();
					if(pluginViewPanel != null) {
						pluginViewPanel.setSchema(schema);
					}
				}				
			}
		}*/
		for(int cnt=0;cnt<this.getPluginViewPanels().size();++cnt) {
			Object obj = this.getPluginViewPanels().get(cnt);
			if((obj != null) && (obj instanceof PluginViewPanel)) {
				PluginViewPanel pluginViewPanel = (PluginViewPanel)obj;
				if(pluginViewPanel != null) {					
					pluginViewPanel.setSchema(schema);					
				}				
			}
		}
		
		main.updateProperties();

		return true;
	}
	

	/**
	 * Pushes pending edits from the active view into the document model.
	 *
	 * <p>Must run before anything reads {@code document.getText()} — above all
	 * before {@link com.dogsbay.xml.DogsBayDocument#save()}, which writes the
	 * <em>model</em>, not the live editor buffer. Without this the model stays at
	 * its last parsed state and a save writes stale text, silently discarding
	 * everything typed since.
	 *
	 * <p>This body was emptied by commit {@code 8f3ce473}, which removed the
	 * Designer branch and took the Editor, Author and plugin branches with it —
	 * the same accident as {@code reload()}.
	 */
	public void updateModel() {
		Object current = getCurrentView();

		try {
			if ( current instanceof Editor) {
				Editor editor = (Editor)current;

				if ( changeManager.isTextChanged()) {
					editor.parse();
				}
			} else if ( current instanceof AuthorView) {
				AuthorView authorView = (AuthorView)current;

				if ( authorView.isModelDirty()) {
					authorView.applyToDocument();
				}
			} else if ( current instanceof AuthorSplitView) {
				((AuthorSplitView)current).flushLiveSide();
			} else if ( current instanceof PluginViewPanel) {
				PluginViewPanel pluginViewPanel = (PluginViewPanel)current;
				pluginViewPanel.updateDocument();

				if ( changeManager.isModelChanged()) {
					editor.parse();
					document.update();
				} else if ( changeManager.isTextChanged()) {
					editor.parse();
				}
			}
			lastModelUpdateError = null;
			lastReportedModelUpdateError = null;
		} catch ( com.dogsbay.xml.XMLUtilities.NotXMLException notXML) {
			// The document is not XML at all — a .gitignore, Markdown, AsciiDoc,
			// plain text. DogsBayDocument.setText() assigns the text before it
			// parses, so the model DID receive the view's edits; there is simply
			// no XML model to build from them, which is not a failure. Treating
			// it as one told people their .gitignore was not well-formed and
			// refused to save it.
			lastModelUpdateError = null;
			lastReportedModelUpdateError = null;
		} catch ( Exception x) {
			// A failure here means the model did NOT receive the view's edits, and
			// the caller is usually about to save, which would write stale text.
			// Remember it for callers that check, and tell the user once per failure.
			x.printStackTrace();
			lastModelUpdateError = x;
			String msg = x.getMessage() == null ? x.toString() : x.getMessage();
			if (!msg.equals(lastReportedModelUpdateError)) {
				lastReportedModelUpdateError = msg;
				// Asynchronously: commands reach here inside invokeAndWait, and a modal
				// dialog shown synchronously would hang them until someone clicks OK.
				SwingUtilities.invokeLater(() -> MessageHandler.showMessage(
						"The view's edits could not be applied to the document:\n" + msg
						+ "\n\nThe document was not changed. Fix the problem before saving."));
			}
		}
	}

	private Exception lastModelUpdateError;
	private String lastReportedModelUpdateError;

	/** The failure of the last {@link #updateModel()}, or null when it applied the view's edits. */
	public Exception getLastModelUpdateError() {
		return lastModelUpdateError;
	}

	/**
	 * {@link #updateModel()} for callers about to write the document: throws
	 * when the view's edits did not reach the model, so nothing stale is saved.
	 */
	public void updateModelOrThrow() {
		updateModel();
		if (lastModelUpdateError != null) {
			throw new IllegalStateException("The view's edits could not be applied to the document: "
					+ lastModelUpdateError.getMessage(), lastModelUpdateError);
		}
	}

	/** Re-shows the document in whichever view is current, after the model changed underneath it. */
	public void refreshCurrentViewFromModel() {
		refreshCurrentView();
	}

	public void switchToViewer() throws Exception {
		XElement previousElement = viewer.getSelectedElement();
		
		updateModel();
		unsplitAuthorCards();
		
		if ( !document.isError() && document.isXML()) {

			if ( !viewer.hasLatestInformation()) {
			    viewer.setDocument( document);
			}

			if ( main.isAutoSynchroniseSelection() && selectedElement != null) {
				viewer.setSelectedElement( selectedElement, false, -1);
			} else {
				viewer.setSelectedElement( previousElement, false, -1);
			}

			show( "Viewer");
			viewerButton.setSelected(true);
			getViewerViewItem().setSelected(true);
	//		viewerButton.setSelected( true);
	//		getViewerViewItem().setSelected( true);
			viewer.setFocus();
			setCurrent( viewer);
		} else {
			throw document.getError();
		}
	}

	public void switchToAuthor() throws Exception {
		// the caret before updateModel(): parsing may renumber the text under it
		int editorPos = current instanceof Editor ? editor.getCursorPosition() : -1;

		updateModel();

		if ( !document.isError() && document.isXML()) {
			unsplitAuthorCards();
			if ( !author.hasLatestInformation()) {
				author.setDocument( document);
			}
			if ( editorPos >= 0 && main.isAutoSynchroniseSelection()) {
				try {
					author.selectPath( pathOfPosition( editorPos));
				} catch ( RuntimeException failed) {
					failed.printStackTrace();   // never leave the switch half done over a caret
				}
			}

			show( "Author");
			getAuthorButton().setSelected(true);
			getAuthorViewItem().setSelected(true);
			getAuthorSplitItem().setSelected(false);
			author.setFocus();
			setCurrent( author);
		} else {
			// Not well-formed XML: leave the current view intact (don't unsplit into a
			// blank pane) and report the reason. getError() may be null for non-XML.
			Exception err = document.getError();
			throw err != null ? err
					: new Exception("The document must be well-formed XML for the Author view.");
		}
	}

	/**
	 * Shows the XML source and the Author view side by side ("live + mirror"
	 * split): the focused pane is live, the other refreshes shortly behind.
	 * Pane order follows ConfigurationProperties.isAuthorSplitXmlLeft.
	 */
	public void switchToAuthorSplit() throws Exception {
		if ( current instanceof AuthorSplitView) {
			return;
		}
		updateModel();

		if ( document.isError() || !document.isXML()) {
			Exception err = document.getError();
			throw err != null ? err
					: new Exception("The document must be well-formed XML for the Author view.");
		}

		AuthorSplitView.Side live = current instanceof AuthorView
				? AuthorSplitView.Side.AUTHOR : AuthorSplitView.Side.EDITOR;

		if ( authorSplit == null) {
			authorSplit = new AuthorSplitView( this);
			add( authorSplit, "AuthorSplit");
		}

		// the split borrows the card components; they are re-carded on unsplit
		remove( editor);
		remove( author);
		authorSplit.activate( editor, author, properties.isAuthorSplitXmlLeft(), live);

		show( "AuthorSplit");
		if ( live == AuthorSplitView.Side.AUTHOR) {
			getAuthorButton().setSelected(true);
			getAuthorViewItem().setSelected(true);
		} else {
			getEditorButton().setSelected(true);
			getEditorViewItem().setSelected(true);
		}
		getAuthorSplitItem().setSelected(true);
		setCurrent( authorSplit);
		authorSplit.setFocus();
	}

	/** Returns the borrowed Editor/Author components to the card layout. */
	private void unsplitAuthorCards() {
		if ( authorSplit == null || !authorSplit.isActive()) {
			return;
		}
		authorSplit.deactivate();
		add( editor, "Editor");
		add( author, "Author");
		getAuthorSplitItem().setSelected(false);
	}


	
	public void switchToEditor() {
		int previousPos = editor.getCursorPosition();

		// where the writer is in the Author view, read before updateModel()
		// re-exports the block tree (which is also what gives the XML its
		// positions), so the caret can follow them into the source
		java.util.List<com.dogsbay.dogsbayaieditor.author.BlockTextMapper.Step> authorPath =
				authorPathOfCurrentView();

		updateModel();
		unsplitAuthorCards();

		if ( current instanceof Viewer) {
			selectedElement = ((Viewer)current).getSelectedElement();
		}
		
		if ( !editor.hasLatestInformation()) {
			editor.setDocument( document);
		}

		int authorPos = main.isAutoSynchroniseSelection() ? positionOfPath( authorPath) : -1;

		if ( authorPos >= 0) {
			editor.setCursorPosition( authorPos);
		} else if ( main.isAutoSynchroniseSelection() && selectedElement != null) {
			if ( selectedElement.getContentStartPosition() > 0) {
				editor.setCursorPosition( selectedElement.getContentStartPosition());
			} else {
				editor.setCursorPosition( selectedElement.getElementEndPosition());
			}
		} else {
			editor.setCursorPosition( previousPos);
		}
		
		getEditorButton().setSelected(true);
		
		
		show( "Editor");
		editor.setFocus();
		setCurrent( editor);
	}
	
	public void documentUpdated() {
		if (document != null) {
			if (document.isXML()) {
				if (document.isError()) {
				
				} else {
				
				}

				if (getSchema() != null) {
				}

				viewerButton.setEnabled(true);
				getViewerViewItem().setEnabled(true);
				
//				for each of the plugin buttons
				for(int cnt=0;cnt<main.getPluginViews().size();++cnt) {
					Object obj = main.getPluginViews().get(cnt);
					if((obj != null) && (obj instanceof PluginView)) {
						PluginView pluginView = (PluginView)obj;
						if(pluginView != null) {
							
							NavigationButton pluginButton = pluginView.getButton();
							if(pluginButton != null) {
								pluginButton.setEnabled(true);
							}
							
							JRadioButtonMenuItem pluginItem = pluginView.getPluginViewItem();
							if(pluginItem != null) {
								pluginItem.setEnabled(true);
							}
							
						}				
					}
				}
				
			} else { // Not XML
				viewerButton.setEnabled(false);
				getViewerViewItem().setEnabled(false);


				
				/*gridButton.setEnabled(false);
				getGridViewItem().setEnabled(false);*/
				
//				for each of the plugin buttons
				for(int cnt=0;cnt<main.getPluginViews().size();++cnt) {
					Object obj = main.getPluginViews().get(cnt);
					if((obj != null) && (obj instanceof PluginView)) {
						PluginView pluginView = (PluginView)obj;
						if(pluginView != null) {
							
							NavigationButton pluginButton = pluginView.getButton();
							if(pluginButton != null) {
								pluginButton.setEnabled(true);
							}
							
							JRadioButtonMenuItem pluginItem = pluginView.getPluginViewItem();
							if(pluginItem != null) {
								pluginItem.setEnabled(true);
							}
							
						}				
					}
				}
				
				for(int cnt=0;cnt<getUserViews().size();++cnt) {
				    UserView userView = (UserView) getUserViews().get(cnt);
				    userView.getButton().setEnabled(true);
				}
			}
		}

	}
	
	public void switchToBrowser() {
		updateModel();
		
		if ( current instanceof Viewer) {
			selectedElement = ((Viewer)current).getSelectedElement();
		}
		
//		browser.update();
		
//		show( "Browser");
//		browser.setFocus();
//		setCurrent( browser);
	}

	/*public void switchToGrid() throws Exception {
	    
	    XElement previousElement = grid.getSelectedElement();
			    
		updateModel();
		
		if ( !document.isError() && document.isXML()) {

			if ( !grid.hasLatestInformation()) {
				grid.setDocument( document);
			}

			System.out.println("DogsBayView - switchToGridView - "+main.isAutoSynchroniseSelection());
			if (( main.isAutoSynchroniseSelection()) && (selectedElement != null)) {
			    //grid.setSelectedElement( selectedElement, null);
			    grid.selectElement(selectedElement);
			} else if(previousElement != null) {
				//grid.setSelectedElement( previousElement, null);
			    grid.selectElement(previousElement);
			}
			else {
			    
			}
			
			show( "Grid");
	//		viewerButton.setSelected( true);
	//		getViewerViewItem().setSelected( true);
		
			grid.setFocus();
			setCurrent( grid);
		} else {
			throw document.getError();
		}
		
	    
		//updateModel();
		
		//if ( current instanceof Viewer) {
		//	selectedElement = ((Viewer)current).getSelectedElement();
		//} else if ( current instanceof Designer) {
		//	selectedElement = ((Designer)current).getSelectedElement();
		//} else if ( current instanceof Editor) {
		//	selectedElement = document.getLastElement( ((Editor)current).getCursorPosition());
		//}
		
//		//browser.update();
		
		//show( "Grid");
		//grid.setFocus();
		//setCurrent( grid);
	}*/
	
public void switchToPluginView(PluginView pluginView) throws Exception {

	if((this.current!= null) && (this.current.equals(pluginView))) {
		//dont do anything
		//already at that view
	}
	else {
	    //XElement previousElement = pluginView.getPluginViewPanel().getSelectedElement();
		XElement previousElement = null;
		for(int cnt=0;cnt<this.getPluginViewPanels().size();++cnt) {
			Object obj = this.getPluginViewPanels().get(cnt);
			if((obj != null) && (obj instanceof PluginViewPanel)) {
				PluginViewPanel pluginViewPanel = (PluginViewPanel)obj;
				if(pluginViewPanel != null) {
					if(pluginViewPanel.getPluginView() == pluginView) {
						previousElement = pluginViewPanel.getSelectedElement();
					}
										
				}				
			}
		}
			    
		updateModel();
		
		if ( !document.isError() && document.isXML()) {

			
				//pluginView.getPluginViewPanel().setDocument( document);
				for(int cnt=0;cnt<this.getPluginViewPanels().size();++cnt) {
					Object obj = this.getPluginViewPanels().get(cnt);
					if((obj != null) && (obj instanceof PluginViewPanel)) {
						PluginViewPanel pluginViewPanel = (PluginViewPanel)obj;
						if(pluginViewPanel != null) {
							if(pluginViewPanel.getPluginView() == pluginView) {
								if ( pluginViewPanel.hasLatestInformation()) {
									pluginViewPanel.setDocument(document);
								}
							}
												
						}				
					}
				
			}

			if (( main.isAutoSynchroniseSelection()) && (selectedElement != null)) {
			    //grid.setSelectedElement( selectedElement, null);
				//pluginView.getPluginViewPanel().selectElement(selectedElement);
				for(int cnt=0;cnt<this.getPluginViewPanels().size();++cnt) {
					Object obj = this.getPluginViewPanels().get(cnt);
					if((obj != null) && (obj instanceof PluginViewPanel)) {
						PluginViewPanel pluginViewPanel = (PluginViewPanel)obj;
						if(pluginViewPanel != null) {
							if(pluginViewPanel.getPluginView() == pluginView) {
								pluginViewPanel.selectElement(selectedElement);
							}
												
						}				
					}
				}
			} else if(previousElement != null) {
				//grid.setSelectedElement( previousElement, null);
				//pluginView.getPluginViewPanel().selectElement(previousElement);
				for(int cnt=0;cnt<this.getPluginViewPanels().size();++cnt) {
					Object obj = this.getPluginViewPanels().get(cnt);
					if((obj != null) && (obj instanceof PluginViewPanel)) {
						PluginViewPanel pluginViewPanel = (PluginViewPanel)obj;
						if(pluginViewPanel != null) {
							if(pluginViewPanel.getPluginView() == pluginView) {
								pluginViewPanel.selectElement(previousElement);
							}
												
						}				
					}
				}
			}
			else {
			    
			}
			
			show( pluginView.getIdentifier());
	//		viewerButton.setSelected( true);
	//		getViewerViewItem().setSelected( true);
		
			for(int cnt=0;cnt<this.getPluginViewPanels().size();++cnt) {
				Object obj = this.getPluginViewPanels().get(cnt);
				if((obj != null) && (obj instanceof PluginViewPanel)) {
					PluginViewPanel pluginViewPanel = (PluginViewPanel)obj;
					if(pluginViewPanel != null) {
						if(pluginViewPanel.getPluginView() == pluginView) {
							pluginViewPanel.setFocus();
							setCurrent(pluginViewPanel);
						}
											
					}				
				}
			}
			//pluginView.getPluginViewPanel().setFocus();
			//setCurrent( pluginView.getPluginViewPanel());
		} else {
			throw document.getError();
		}
		
		pluginView.getButton().setSelected(true);
		pluginView.getPluginViewItem().setSelected(true);
		/*updateModel();
		
		if ( current instanceof Viewer) {
			selectedElement = ((Viewer)current).getSelectedElement();
		}
		
//		browser.update();
		
		show( "Grid");
		grid.setFocus();
		setCurrent( grid);*/
	}
	}
	
	public void switchToUserView(UserView newUserView) throws Exception {
	    
	    //XElement previousElement = grid.getSelectedElement();
			    
		//updateModel();
		
		//if ( !document.isError() && document.isXML()) {
		newUserView.getButton().setSelected(true);
		//getViewerViewItem().setSelected(true);
		show( newUserView.getIdentifier());
		//.setFocus();
		setCurrent( newUserView.getPanel());
		
				//} else {
		//	throw document.getError();
		//}
		
	}

	/*
	 * Gets an icon for the string.
	 */
	public ImageIcon getIcon( String path) {
		if ( icons == null) {
			icons = new Hashtable();
		}
		
		ImageIcon icon = (ImageIcon)icons.get( path);
		
		if ( icon == null) {
			icon = DogsBayImageLoader.get().getImage( path);
			icons.put( path, icon);
		}
		
		return icon;
	}
	
	// Navigator settings.
	public NavigatorSettings getNavigatorSettings() {
		if ( navigatorSettings == null) {
			navigatorSettings = new NavigatorSettings();
		}

		return navigatorSettings;
	}
	
	public void cleanup() {
//		System.out.println( ((Object)this).hashCode()+" ["+document.getName()+"] cleanup()");
		removeFocusListener( focusListener);
		
		if ( tagSchemas != null) {
			tagSchemas.removeAllElements();
		}
		
		editor.cleanup();
		viewer.cleanup();
		changeManager.cleanup();
		document.cleanup();
		
//		for each of the plugin buttons
		/*for(int cnt=0;cnt<main.getPluginViews().size();++cnt) {
			Object obj = main.getPluginViews().get(cnt);
			if((obj != null) && (obj instanceof PluginView)) {
				PluginView pluginView = (PluginView)obj;
				if(pluginView != null) {
					PluginViewPanel pluginViewPanel = pluginView.getPluginViewPanel();
					if(pluginViewPanel != null) {
						pluginViewPanel.cleanup();
					}
					
				}				
			}
		}*/
		for(int cnt=0;cnt<this.getPluginViewPanels().size();++cnt) {
			Object obj = this.getPluginViewPanels().get(cnt);
			if((obj != null) && (obj instanceof PluginViewPanel)) {
				PluginViewPanel pluginViewPanel = (PluginViewPanel)obj;
				if(pluginViewPanel != null) {
					pluginViewPanel.cleanup();										
				}				
			}
		}
//		browser.cleanup();
		
		removeAll();

		finalize();
	}
	
	protected void finalize() {
//		System.out.println( "["+this.hashCode()+"] finalize()");

		schema	= null;
		tagSchemas	= null;
		selectedElement	= null; // sticky selection element
		icons = null;
		grammar	= null;
		validationGrammar = null;
		current	= null;
		
//		for each of the plugin buttons
		/*for(int cnt=0;cnt<main.getPluginViews().size();++cnt) {
			Object obj = main.getPluginViews().get(cnt);
			if((obj != null) && (obj instanceof PluginView)) {
				PluginView pluginView = (PluginView)obj;
				if(pluginView != null) {
					pluginView.setPluginViewPanel(null);
					
				}				
			}
		}*/
		
		for(int cnt=0;cnt<this.getPluginViewPanels().size();++cnt) {
			Object obj = this.getPluginViewPanels().get(cnt);
			if((obj != null) && (obj instanceof PluginViewPanel)) {
				PluginViewPanel pluginViewPanel = (PluginViewPanel)obj;
				if(pluginViewPanel != null) {
					pluginViewPanel = null;
				}				
			}
		}
		this.setPluginViewPanels(new Vector());
		
		main = null;
		editor = null;
		viewer = null;
//		browser = null;
		changeManager = null;
		defaultValidationGrammar = null;
		document = null;
		

		
	}
	

	public JRadioButtonMenuItem getEditorViewItem() {
		if (editorItem == null) {
			editorItem = new JRadioButtonMenuItem("Editor");
			editorItem.setIcon( getIcon(DogsBayView.EDITOR_ICON));
			editorItem.setMnemonic('E');
			editorItem.setAccelerator( javax.swing.KeyStroke.getKeyStroke(
					java.awt.event.KeyEvent.VK_E,
					java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.ALT_DOWN_MASK));
			editorItem.addItemListener(new EditorItemListener());
		}

		return editorItem;
	}

	public JRadioButtonMenuItem getBrowserViewItem() {
		if (browserItem == null) {
			browserItem = new JRadioButtonMenuItem("Browser");
			browserItem.setMnemonic('B');
			browserItem.addItemListener(new BrowserItemListener());
		}

		return browserItem;
	}


	public JRadioButtonMenuItem getViewerViewItem() {
		if (viewerItem == null) {
			viewerItem = new JRadioButtonMenuItem("Viewer");
			viewerItem.setIcon( getIcon(DogsBayView.VIEWER_ICON));
			viewerItem.setMnemonic('V');
			viewerItem.addItemListener(new ViewerItemListener());
		}

		return viewerItem;
	}
	
	/*public JRadioButtonMenuItem getGridViewItem() {
		if (gridItem == null) {
			gridItem = new JRadioButtonMenuItem("Grid");
			gridItem.setIcon( getIcon(GRID_ICON));
			gridItem.setMnemonic('G');
			gridItem.addItemListener(new GridItemListener());
		}

		return gridItem;
	}*/
	
    /**
     * @return Returns the userViews.
     */
    public Vector getUserViews() {

        return userViews;
    }
    /**
     * @param userViews The userViews to set.
     */
    public void setUserViews(Vector userViews) {

        this.userViews = userViews;
    }

	/**
	 * @param pluginViewPanels the pluginViewPanels to set
	 */
	public void setPluginViewPanels(Vector pluginViewPanels) {

		this.pluginViewPanels = pluginViewPanels;
	}

	/**
	 * @return the pluginViewPanels
	 */
	public Vector getPluginViewPanels() {

		return pluginViewPanels;
	}



	public void setEditorButton(NavigationButton editorButton) {
		this.editorButton = editorButton;
	}

	public NavigationButton getEditorButton() {
		return editorButton;
	}



	public void setViewerButton(NavigationButton viewerButton) {
		this.viewerButton = viewerButton;
	}

	public NavigationButton getViewerButton() {
		return viewerButton;
	}

	public void setDocumentViewButtonPanel(JPanel documentViewButtonPanel) {
		this.documentViewButtonPanel = documentViewButtonPanel;
	}

	public JPanel getDocumentViewButtonPanel() {
		return documentViewButtonPanel;
	}
    
	public void setDocumentViewsMenu(JMenu documentViewsMenu) {
		this.documentViewsMenu = documentViewsMenu;
	}

	public JMenu getDocumentViewsMenu() {
		return documentViewsMenu;
	}



	public class EditorItemListener implements ItemListener {
		public void itemStateChanged(ItemEvent event) {
		    if (event.getStateChange() == ItemEvent.SELECTED) {
				if (DogsBayView.this != null
					&& !(DogsBayView.this.getCurrentView() instanceof Editor)) {
					main.switchToEditor();
				}
			}
		}
	}

	public class ViewerItemListener implements ItemListener {
		public void itemStateChanged(ItemEvent event) {
			if (event.getStateChange() == ItemEvent.SELECTED) {
				ViewPanel current = DogsBayView.this.getCurrentView();
				if (DogsBayView.this != null && !(current instanceof Viewer)) {
					try {
						main.switchToViewer();
					} catch (Exception e) {
						e.printStackTrace();
						if (current instanceof Editor) {
							DogsBayView.this.getEditorButton().setSelected(true);
							getEditorViewItem().setSelected(true);
						}
						MessageHandler.showMessage(	"Please ensure the document is well-formed\nbefore switching to the \"Viewer\".");
						current.setFocus();
					}
				}
			}
		}
	}

	public class AuthorItemListener implements ItemListener {
		public void itemStateChanged(ItemEvent event) {
			if (event.getStateChange() == ItemEvent.SELECTED) {
				ViewPanel current = DogsBayView.this.getCurrentView();
				if (DogsBayView.this != null && !(current instanceof AuthorView)) {
					try {
						main.switchToAuthor();
					} catch (Exception e) {
						if (current instanceof Editor) {
							DogsBayView.this.getEditorButton().setSelected(true);
							getEditorViewItem().setSelected(true);
						}
						if (getLastModelUpdateError() == null) {   // else updateModel already said why
							String why = e.getMessage() == null || e.getMessage().isBlank() ? "" : "\n\n" + e.getMessage();
							MessageHandler.showMessage( "The document could not be opened in the \"Author\" view."
									+ why + "\n\nCheck that it is well-formed; if it is, the Author view cannot represent it yet.");
						}
						current.setFocus();
					}
				}
			}
		}
	}

	public class BrowserItemListener implements ItemListener {
		public void itemStateChanged(ItemEvent event) {
//			if (event.getStateChange() == ItemEvent.SELECTED) {
//				ViewPanel current = currentView.getCurrentView();
//				if (currentView != null && !(current instanceof Browser)) {
//					try {
//						switchToBrowser();
//					} catch (Exception e) {}
//				}
//			}
		}
	}
	
	/*private class GridItemListener implements ItemListener {
		public void itemStateChanged(ItemEvent event) {
			if (event.getStateChange() == ItemEvent.SELECTED) {
				ViewPanel current = currentView.getCurrentView();
				if (currentView != null && !(current instanceof Grid)) {
					try {
						switchToGrid();
					} catch (Exception e) {
					    e.printStackTrace();
						if (current instanceof Editor) {
							getEditorButton().setSelected(true);
							getEditorViewItem().setSelected(true);
						}
						
						MessageHandler.showMessage(	"Please ensure the document is well-formed\nbefore switching to the \"Grid\".");
						current.setFocus();
					}
				}
			}
		}
	}*/
} 
