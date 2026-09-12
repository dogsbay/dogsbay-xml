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
import java.awt.Container;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.net.URL;
import java.util.Vector;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.bounce.event.DoubleClickListener;
import org.bounce.event.PopupListener;

import com.dogsbay.dogsbayaieditor.component.GUIUtilities;
import com.dogsbay.dogsbayaieditor.explorer.actions.CopyPathAction;
import com.dogsbay.dogsbayaieditor.explorer.actions.CopyRelativePathAction;

/**
 * This DogsBayTabbedView is used to ...
 *
 * @version $Revision: 1.12 $, $Date: 2004/11/09 10:18:26 $
 * @author Dogsbay
 */
public class DogsBayTabbedView extends JPanel {
	private static final Border UNSELECTED_TABS_BORDER = 
		new CompoundBorder(
			new CompoundBorder(
				new MatteBorder( 1, 1, 0, 0, UIManager.getColor("controlDkShadow")),
				new MatteBorder(0, 0, 1, 1, UIManager.getColor("controlHighlight") != null ? UIManager.getColor("controlHighlight") : Color.white)),
			new EmptyBorder( 1, 1, 1, 1));

	private static final Border SELECTED_TABS_BORDER = 
		new CompoundBorder(
			new CompoundBorder(
				new MatteBorder( 1, 1, 0, 0, UIManager.getColor( "controlDkShadow")),
				new MatteBorder(0, 0, 1, 1, UIManager.getColor("controlHighlight") != null ? UIManager.getColor("controlHighlight") : Color.white)),
			new MatteBorder( 1, 1, 1, 1, UIManager.getColor( "TabbedPane.focus")));

	private JTabbedPane tabs = null;
	private DogsBayTabbedView parent = null;
	private PopupListener popupListener = null;
	private DoubleClickListener doubleClickListener = null;
	private MouseAdapter singleClickListener = null;
	private DogsBayAIEditor editor = null;
	private boolean selected = false;
	private boolean disabled = false;
	
	public DogsBayTabbedView( DogsBayAIEditor _editor, DogsBayTabbedView _parent) {
		super( new BorderLayout());
		
		this.editor = _editor;
		this.parent = _parent;
		
		setBorder( UNSELECTED_TABS_BORDER);
			
		tabs = new JTabbedPane();
		tabs.addChangeListener( new ChangeListener() {
			public void stateChanged( ChangeEvent event) {
				if ( !disabled) {
					Component selectedComponent = tabs.getSelectedComponent();
					// Only set view if it's an DogsBayView (not a custom panel)
					if (selectedComponent instanceof DogsBayView) {
						editor.setView((DogsBayView) selectedComponent);
					} else if (selectedComponent != null) {
						// Custom panel selected - enable close action but don't set view
						editor.getCloseAction().setEnabled(true);
					}
					int index = tabs.getSelectedIndex();

					if (index != -1) {
						editor.setControllerIcon( tabs.getIconAt( tabs.getSelectedIndex()));
					} else {
						editor.setControllerIcon( null);
					}
				}
			}
		});
		
		tabs.setTabPlacement( JTabbedPane.TOP);
		tabs.setFont( tabs.getFont().deriveFont( Font.PLAIN));

		// FlatLaf: show an X on every tab; the callback closes whatever it is
		// (a document goes through the save-prompt flow, a custom panel is removed).
		tabs.putClientProperty( "JTabbedPane.tabClosable", Boolean.TRUE);
		tabs.putClientProperty( "JTabbedPane.tabCloseToolTipText", "Close");
		tabs.putClientProperty( "JTabbedPane.tabCloseCallback",
				(java.util.function.IntConsumer) this::closeTabAt);
		
		doubleClickListener = new DoubleClickListener() {
			public void doubleClicked( MouseEvent e) {
				editor.toggleFullScreen();

		 		if ( editor.getCurrent() != null) {
		 			editor.getCurrent().setFocus();
		 		}
			}
		};
		
		addMouseListener( tabs, doubleClickListener);
		
		popupListener = new PopupListener() {
			public void popupTriggered(MouseEvent e) {
				showTabPopup( e);
			}
		};

		addMouseListener( tabs, popupListener);
		
		singleClickListener = new MouseAdapter() {
			public void mouseClicked( MouseEvent e) {
				setSelected( true);
			}
		};

		addMouseListener( tabs, singleClickListener);

		tabs.setRequestFocusEnabled(false);
		
		add( tabs, BorderLayout.CENTER);
	}
	
	public void setScrollTabs( boolean scroll) {
		if ( scroll) {
			tabs.setTabLayoutPolicy( JTabbedPane.SCROLL_TAB_LAYOUT);
		} else {
			tabs.setTabLayoutPolicy( JTabbedPane.WRAP_TAB_LAYOUT);
		}
		
		refreshMouseListeners();
	}
	
	private void addMouseListener( Component c, MouseListener listener) {    
		String v = System.getProperty( "java.class.version","44.0");

		if ( "49.0".compareTo(v) <= 0) { // jdk 1.5 or higher...
			c.addMouseListener( listener);
		} else {
			if ( !(c instanceof DogsBayView) && !(c instanceof AbstractButton)) {
			  	c.addMouseListener( listener);
			  	
			  	if ( c instanceof Container) {      
			  		Component[] comps = ((Container)c).getComponents();
			  		
			  		for ( int i = 0; i < comps.length; i++) {
			  			addMouseListener( comps[i], listener);    
			  		}
			  	}
			}
		}
	}
	  
	private void removeMouseListener( Component c, MouseListener listener) {    
		String v = System.getProperty( "java.class.version","44.0");

		if ( "49.0".compareTo(v) <= 0) { // jdk 1.5 or higher...
			c.removeMouseListener( listener);
		} else {
			if ( !(c instanceof DogsBayView) && !(c instanceof AbstractButton)) {
			  	c.removeMouseListener( listener);    
			  	
			  	if ( c instanceof Container) {      
			  		Component[] comps = ((Container)c).getComponents();
			  		
			  		for ( int i = 0; i < comps.length; i++) {
			  			removeMouseListener( comps[i], listener);    
			  		}
			  	}
			}
		}
	}

	private void refreshMouseListeners() {    
		removeMouseListener( tabs, singleClickListener);
		removeMouseListener( tabs, doubleClickListener);
		removeMouseListener( tabs, popupListener);

		addMouseListener( tabs, singleClickListener);
		addMouseListener( tabs, doubleClickListener);
		addMouseListener( tabs, popupListener);
	}

	public void add( DogsBayView view, String name) {
		tabs.add( view, name);

		int index = tabs.indexOfComponent( view);
		URL url = view.getDocument().getURL();

		if ( url != null) {
			tabs.setToolTipTextAt( index, URLUtilities.toRelativeString( url));
		} else {
			tabs.setToolTipTextAt( index, name);
		}

		tabs.setRequestFocusEnabled( false);
	}

	/**
	 * Adds a custom component (not an DogsBayView) to the tab pane.
	 * This allows for custom views like diff viewers, image viewers, etc.
	 *
	 * @param component the component to add
	 * @param name the tab name
	 * @param tooltip optional tooltip (can be null)
	 */
	public void addCustomPanel(Component component, String name, String tooltip) {
		tabs.add(component, name);

		int index = tabs.indexOfComponent(component);
		if (tooltip != null) {
			tabs.setToolTipTextAt(index, tooltip);
		} else {
			tabs.setToolTipTextAt(index, name);
		}

		tabs.setRequestFocusEnabled(false);
		tabs.setSelectedComponent(component);
	}

	public DogsBayTabbedView getParentTabbedView() {
		return parent;
	}

	public void setParentTabbedView( DogsBayTabbedView parent) {
		this.parent = parent;
	}

	public void remove( DogsBayView view) {
		tabs.remove( view);

		if ( tabs.getTabCount() > 0) {
			tabs.setRequestFocusEnabled( false);
		} else {
			tabs.setRequestFocusEnabled( true);
			tabs.requestFocus();

			int policy = tabs.getTabLayoutPolicy();

			// Workaround for paint bug!
			tabs.setTabLayoutPolicy( JTabbedPane.WRAP_TAB_LAYOUT);
			tabs.setTabLayoutPolicy( policy);

			refreshMouseListeners();
		}
	}

	/**
	 * Removes a custom panel tab (not an DogsBayView).
	 * @param component the component to remove
	 */
	public void removeTab(Component component) {
		tabs.remove(component);

		if ( tabs.getTabCount() > 0) {
			tabs.setRequestFocusEnabled( false);
		} else {
			tabs.setRequestFocusEnabled( true);
			tabs.requestFocus();

			int policy = tabs.getTabLayoutPolicy();

			// Workaround for paint bug!
			tabs.setTabLayoutPolicy( JTabbedPane.WRAP_TAB_LAYOUT);
			tabs.setTabLayoutPolicy( policy);

			refreshMouseListeners();
		}
	}

	/**
	 * Close the tab at {@code index}, whatever it holds: a document view goes through
	 * the normal close flow (dirty prompt), a custom panel (Welcome, preview) is removed.
	 * Used by the per-tab X button and the right-click menu.
	 */
	public void closeTabAt(int index) {
		if (index < 0 || index >= tabs.getTabCount()) {
			return;
		}
		Component c = tabs.getComponentAt(index);
		if (c instanceof DogsBayView) {
			// Make the target the active view first: DocumentManager.close() flushes the
			// *current* view's editor buffer into its model before saving, so closing a
			// background tab without selecting it would flush/save the wrong document.
			if (tabs.getSelectedComponent() != c) {
				tabs.setSelectedComponent(c);
			}
			editor.close((DogsBayView) c);
		} else if (c != null) {
			removeTab(c);
		}
	}

	/** Close the tab holding {@code c} (resolved fresh, so order changes are safe). */
	public void closeTab(Component c) {
		closeTabAt(tabs.indexOfComponent(c));
	}

	/** Close every tab except the one holding {@code keep}. */
	public void closeOtherTabs(Component keep) {
		// Walk from the end so indices stay valid as tabs are removed.
		for (int i = tabs.getTabCount() - 1; i >= 0; i--) {
			if (tabs.getComponentAt(i) != keep) {
				closeTabAt(i);
			}
		}
	}

	/** Remove all non-document (custom) panels — Welcome, previews, etc. */
	public void closeCustomPanels() {
		for (int i = tabs.getTabCount() - 1; i >= 0; i--) {
			if (!(tabs.getComponentAt(i) instanceof DogsBayView)) {
				removeTab(tabs.getComponentAt(i));
			}
		}
	}

	/**
	 * Gets the currently selected component (could be DogsBayView or custom panel).
	 * @return the selected component
	 */
	public Component getSelectedComponent() {
		return tabs.getSelectedComponent();
	}

	/**
	 * Finds a tab by its title and selects it if found.
	 * @param tabName the title of the tab to find
	 * @return true if the tab was found and selected, false otherwise
	 */
	public boolean selectTabByName(String tabName) {
		for (int i = 0; i < tabs.getTabCount(); i++) {
			if (tabName.equals(tabs.getTitleAt(i))) {
				tabs.setSelectedIndex(i);
				return true;
			}
		}
		return false;
	}

	/** The component behind the tab of this name, or null. */
	public java.awt.Component tabComponentByName(String tabName) {
		for (int i = 0; i < tabs.getTabCount(); i++) {
			if (tabName.equals(tabs.getTitleAt(i))) {
				return tabs.getComponentAt(i);
			}
		}
		return null;
	}

	public void setSelected( boolean selected) {
		
//		if ( this.selected != selected) {
		final DogsBayView view = getSelectedView();
		
		if ( view != null) {
//			System.out.println( "["+view.getDocument().getName()+"] DogsBayTabbedView.setSelected( "+selected+"]");
		}

			if ( tabs.getTabCount() > 0) {
				tabs.setRequestFocusEnabled( false);
			} else {
				tabs.setRequestFocusEnabled( true);
				tabs.requestFocus();
			}

			boolean previous = this.selected;
			this.selected = selected;
			
			if ( selected) {
				setBorder( SELECTED_TABS_BORDER);
				editor.setSelected( this);
	
				int index = tabs.getSelectedIndex();
				
				if (index != -1) {
					editor.setControllerIcon( tabs.getIconAt( tabs.getSelectedIndex()));
				} else {
					editor.setControllerIcon( null);
				}
				
				if ( view != null && view.getCurrentView() != null && previous != selected) {
					view.getCurrentView().setFocus();
				}
			} else {
				setBorder( UNSELECTED_TABS_BORDER);
			}
//		}
	}
	
	public void setFocussed() {
		final DogsBayView view = getSelectedView();
		
		if ( view != null) {
//			System.out.println( "["+view.getDocument().getName()+"] DogsBayTabbedView.setFocussed()");
		}

		if ( this.selected == false) {
			this.selected = true;

			setBorder( SELECTED_TABS_BORDER);
			editor.setSelected( this);

			int index = tabs.getSelectedIndex();
			
			if (index != -1) {
				editor.setControllerIcon( tabs.getIconAt( tabs.getSelectedIndex()));
			} else {
				editor.setControllerIcon( null);
			}
		}
	}

	public void select( DogsBayView view) {
		if ( tabs.indexOfComponent( view) != -1) {
			tabs.setSelectedComponent( view);
			setSelected( true);
		}
	}
	
	public DogsBayView getSelectedView() {
		Component selectedComponent = tabs.getSelectedComponent();
		if (selectedComponent instanceof DogsBayView) {
			return (DogsBayView) selectedComponent;
		}
		return null;
	}

	public void setIcon( DogsBayView view, Icon icon) {
		int index = tabs.indexOfComponent( view);

		if ( index != -1) {
			tabs.setIconAt( index, icon);
			editor.setControllerIcon( icon);
		}
	}
	
	public Icon getSelectedIcon() {
		int index = tabs.getSelectedIndex();
		
		if ( index != -1) {
			return tabs.getIconAt(index);
		}

		return null;
	}

	public void setTitle( DogsBayView view, String title) {
		int index = tabs.indexOfComponent( view);
	
		if ( index != -1) {
			URL url = view.getDocument().getURL();
			
			if ( url != null) {
				tabs.setToolTipTextAt( index, URLUtilities.toRelativeString( url));
			} else {
				tabs.setToolTipTextAt( index, title);
			}
			
			tabs.setTitleAt( index, title);
		}
	}

	public Vector getViews() {
		Vector views = new Vector();

		for (int i = 0; i < tabs.getTabCount(); i++) {
			Component component = tabs.getComponentAt(i);
			// Only include DogsBayView instances, not custom panels
			if (component instanceof DogsBayView) {
				views.addElement(component);
			}
		}

		return views;
	}

	public boolean contains( DogsBayView view) {
		return tabs.indexOfComponent( view) != -1;
	}

	/**
	 * Returns the total number of tabs (including custom panels).
	 */
	public int getTabCount() {
		return tabs.getTabCount();
	}

	/**
	 * Returns the title of the tab at the given index.
	 */
	public String getTabTitleAt(int index) {
		return tabs.getTitleAt(index);
	}

	/**
	 * Returns the tooltip of the tab at the given index.
	 */
	public String getTabToolTipAt(int index) {
		return tabs.getToolTipTextAt(index);
	}

	/**
	 * Returns the index of the currently selected tab.
	 */
	public int getSelectedTabIndex() {
		return tabs.getSelectedIndex();
	}

	public boolean isSelected() {
		return selected;
	}
	
	/** The tab index under a (possibly nested) mouse event, else the selected tab. */
	private int tabIndexAt( MouseEvent e) {
		java.awt.Point p = javax.swing.SwingUtilities.convertPoint(
				(Component) e.getSource(), e.getPoint(), tabs);
		int idx = tabs.indexAtLocation( p.x, p.y);
		return idx >= 0 ? idx : tabs.getSelectedIndex();
	}

	/**
	 * Context menu for the right-clicked tab. Document actions (parse/validate/save…)
	 * appear only for document tabs; Close / Close Others / Close All always work and
	 * operate on the clicked tab — including Welcome and preview panels.
	 */
	private void showTabPopup( MouseEvent e) {
		final int index = tabIndexAt( e);
		if (index >= 0) {
			tabs.setSelectedIndex( index); // act on the tab you clicked
		}
		Component c = (index >= 0) ? tabs.getComponentAt( index) : null;

		JPopupMenu menu = new JPopupMenu();
		if (c instanceof DogsBayView) {
			menu.add( editor.getParseAction());
			menu.add( editor.getValidateAction());
			menu.addSeparator();
			menu.add( editor.getOpenBrowserAction());
			menu.addSeparator();
			menu.add( editor.getReloadAction());
			menu.addSeparator();
			menu.add( editor.getSaveAction());
			menu.add( editor.getSaveAsAction());
			menu.add( editor.getSaveAsRemoteAction());
			menu.addSeparator();
			addCopyPathItems( menu, (DogsBayView) c);
			menu.addSeparator();
		}

		// Capture the component (not the index) so the action hits the right tab even
		// if tab order changed while the menu was open.
		final Component target = c;
		javax.swing.JMenuItem close = new javax.swing.JMenuItem( "Close");
		close.setEnabled( target != null);
		close.addActionListener( a -> closeTab( target));
		menu.add( close);

		javax.swing.JMenuItem closeOthers = new javax.swing.JMenuItem( "Close Others");
		closeOthers.setEnabled( target != null && tabs.getTabCount() > 1);
		closeOthers.addActionListener( a -> closeOtherTabs( target));
		menu.add( closeOthers);

		javax.swing.JMenuItem closeAll = new javax.swing.JMenuItem( "Close All");
		closeAll.setEnabled( tabs.getTabCount() > 0);
		// Use the editor's Close All so dirty documents get the unified save prompt
		// (Don't-Save-All + cancel-aborts-the-batch), not one dialog per document.
		closeAll.addActionListener( a -> editor.closeAll());
		menu.add( closeAll);

		GUIUtilities.alignMenu( menu);
		menu.show( (Component) e.getSource(), e.getX(), e.getY());
	}

	/**
	 * Adds Copy Path / Copy Relative Path for a document tab.
	 *
	 * <p>Both are disabled for a document that has never been saved, since it has no
	 * location to copy. A remote document has a location but no local file, so Copy Path
	 * yields its URL and Copy Relative Path stays disabled — there is nothing sensible to
	 * make it relative to.
	 *
	 * <p>The relative path uses the File Explorer root, so the two menus agree on what
	 * "relative" means.
	 *
	 * @param menu the popup menu to add to
	 * @param view the document tab that was clicked
	 */
	private void addCopyPathItems( JPopupMenu menu, DogsBayView view) {
		java.net.URL url = view.getDocument() != null ? view.getDocument().getURL() : null;
		java.io.File root = editor.getFileExplorer() != null
				? editor.getFileExplorer().getRootDirectory() : null;

		for ( javax.swing.Action action : copyPathActionsFor( url, root)) {
			menu.add( action);
		}
	}

	/**
	 * Builds the Copy Path / Copy Relative Path actions for a document location.
	 *
	 * @param url  the document's location, or null if it has never been saved
	 * @param root the File Explorer root to make the relative path relative to; may be null
	 * @return the two actions, Copy Path first
	 */
	static javax.swing.Action[] copyPathActionsFor( java.net.URL url, java.io.File root) {
		if ( url == null) {
			// Never saved: no location to copy.
			return new javax.swing.Action[] {
				new CopyPathAction( (java.io.File) null),
				new CopyRelativePathAction( (java.io.File) null, null) };
		}

		java.io.File file = URLUtilities.toFile( url);
		if ( file == null) {
			// Remote, or a URL with no local equivalent: copy the location itself. There
			// is nothing local for a relative path to be relative to.
			return new javax.swing.Action[] {
				new CopyPathAction( URLUtilities.toString( url)),
				new CopyRelativePathAction( (java.io.File) null, null) };
		}

		return new javax.swing.Action[] {
			new CopyPathAction( file),
			new CopyRelativePathAction( file, root) };
	}
	
	public void disableChangeListener( boolean disable) {
		this.disabled = disable;
	}

	public void removeListeners() {
		ChangeListener[] changeListeners = tabs.getChangeListeners();
		
		for ( int i = 0; i < changeListeners.length; i++) {
			tabs.removeChangeListener( changeListeners[i]);
		}

//		MouseListener[] listeners = tabs.getMouseListeners();
//		
//		for ( int i = 0; i < listeners.length; i++) {
//			tabs.removeMouseListener( listeners[i]);
//		}
//		
		removeMouseListener( tabs, singleClickListener);
		removeMouseListener( tabs, doubleClickListener);
		removeMouseListener( tabs, popupListener);
	}
}
