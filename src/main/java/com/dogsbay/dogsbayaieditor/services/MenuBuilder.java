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

package com.dogsbay.dogsbayaieditor.services;

import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.URL;
import java.util.Hashtable;
import java.util.Vector;

import javax.swing.Action;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import org.bounce.util.BrowserLauncher;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.Identity;
import com.dogsbay.dogsbayaieditor.ManageProjectsDialog;
import com.dogsbay.dogsbayaieditor.actions.OpenMRUAction;
import com.dogsbay.dogsbayaieditor.actions.OpenRecentProjectAction;
import com.dogsbay.dogsbayaieditor.DogsBayMenuItem;
import com.dogsbay.dogsbayaieditor.component.GUIUtilities;
import com.dogsbay.dogsbayaieditor.plugins.PluginView;
import com.dogsbay.dogsbayaieditor.framework.FrameworkImportDialog;
import com.dogsbay.dogsbayaieditor.framework.FrameworkManagementDialog;
import com.dogsbay.dogsbayaieditor.properties.KeyPreferences;
import com.dogsbay.xml.DogsBayURLUtilities;

/**
 * Builds and manages the application menu bar and all menus for DogsBayAIEditor.
 * Extracted from DogsBayAIEditor to reduce class size and improve cohesion.
 */
public class MenuBuilder {

	/** Where the user documentation lives; the Help menu opens it in a browser. */
	private static final String DOCUMENTATION_URL = "https://dogsbay.ai";


	/** Name of the Project menu, for plugins contributing into it. */
	public static final String PROJECT_MENU = "Project";

	/**
	 * Name of the Project &rarr; Validate submenu. Plugins add their own project-wide
	 * validators here so validation stays in one group.
	 */
	public static final String PROJECT_VALIDATE_MENU = "Validate";

	/**
	 * The item plugins insert their Project-menu groups before. Keeping the formatting
	 * commands last gives contributed groups a stable place to land.
	 */
	public static final String FORMAT_PROJECT_ITEM = "Format Project";

	private final DogsBayAIEditor editor;

	private JMenuBar menuBar = null;
	private JMenu fileMenu = null;
	private JMenu editMenu = null;
	private JMenu viewMenu = null;
	private JMenu documentViewsMenu = null;
	private JMenu installedDocumentViewsMenu = null;

	private Hashtable menuItemMap = new Hashtable();
	private Hashtable modeActionMap = new Hashtable();

	// JCheckBoxMenuItem fields created in menu construction but also accessed elsewhere
	private JCheckBoxMenuItem highlightMenuItem = null;
	private JCheckBoxMenuItem showStandardButtons = null;
	private JCheckBoxMenuItem showEditorButtons = null;
	private JCheckBoxMenuItem showFragmentButtons = null;
	private JCheckBoxMenuItem synchroniseSplits = null;
	private JCheckBoxMenuItem toggleFullScreenMenuItem = null;
	private JCheckBoxMenuItem togglePrimarySidebarMenuItem = null;
	private JCheckBoxMenuItem toggleBottomPanelMenuItem = null;
	private JCheckBoxMenuItem toggleSecondarySidebarMenuItem = null;

	private JCheckBoxMenuItem outlinerAutoCreateRequiredNodes = null;
	private JCheckBoxMenuItem outlinerShowAttributeValues = null;
	private JCheckBoxMenuItem outlinerShowElementValues = null;

	private JCheckBoxMenuItem editorLinenumberMargin = null;
	private JCheckBoxMenuItem editorOverviewMargin = null;
	private JCheckBoxMenuItem editorFoldingMargin = null;
	private JCheckBoxMenuItem editorBookmarkMargin = null;
	private JCheckBoxMenuItem editorEndTagCompletion = null;
	private JCheckBoxMenuItem editorTagCompletion = null;
	private JCheckBoxMenuItem editorSmartIndentation = null;
	private JCheckBoxMenuItem editorErrorHighlighting = null;
	private JCheckBoxMenuItem editorSoftWrapping = null;

	private JCheckBoxMenuItem viewerShowNamespaces = null;
	private JCheckBoxMenuItem viewerShowAttributes = null;
	private JCheckBoxMenuItem viewerShowComments = null;
	private JCheckBoxMenuItem viewerShowPIs = null;
	private JCheckBoxMenuItem viewerShowContent = null;
	private JCheckBoxMenuItem viewerInlineMixed = null;

	private JCheckBoxMenuItem gridHideContainerTables = null;

	// Icon path constants (duplicated from DogsBayAIEditor for menu bar layout buttons)
	private static final String CLOSE_ICON = "com/dogsbay/dogsbayaieditor/icons/Close8.gif";
	private static final String SYNCHRONISE_SPLITS_ICON = "com/dogsbay/dogsbayaieditor/icons/SynchroniseSplits16.gif";
	private static final String LAYOUT_SIDEBAR_LEFT_ICON = "com/dogsbay/dogsbayaieditor/icons/sidebar/layout-sidebar-left.png";
	private static final String LAYOUT_PANEL_ICON = "com/dogsbay/dogsbayaieditor/icons/sidebar/layout-panel.png";
	private static final String LAYOUT_SIDEBAR_RIGHT_ICON = "com/dogsbay/dogsbayaieditor/icons/sidebar/layout-sidebar-right.png";

	public MenuBuilder(DogsBayAIEditor editor) {
		this.editor = editor;
	}

	// ---------------------------------------------------------------
	// Public accessors
	// ---------------------------------------------------------------

	public JMenuBar getMenuBar() {
		return menuBar;
	}

	public JMenu getFileMenu() {
		return fileMenu;
	}

	public JMenu getEditMenu() {
		return editMenu;
	}

	public JMenu getViewMenu() {
		return viewMenu;
	}

	public JMenu getDocumentViewsMenu() {
		return documentViewsMenu;
	}

	public void setDocumentViewsMenu(JMenu menu) {
		this.documentViewsMenu = menu;
		installDocumentViewsMenu();
	}

	/**
	 * Shows the current document's "Document Views" submenu at the top of the
	 * View menu, swapping out the previous document's one. Called whenever the
	 * active tab changes (ViewManager.setView -> setDocumentViewsMenu).
	 */
	private void installDocumentViewsMenu() {
		if (viewMenu == null || documentViewsMenu == null
				|| installedDocumentViewsMenu == documentViewsMenu) {
			return;
		}
		if (installedDocumentViewsMenu != null) {
			viewMenu.remove(installedDocumentViewsMenu);
		}
		viewMenu.insert(documentViewsMenu, 0);
		installedDocumentViewsMenu = documentViewsMenu;
		documentViewsMenu.setEnabled(documentViewsMenu.getMenuComponentCount() > 0);
	}

	public Hashtable getMenuItemMap() {
		return menuItemMap;
	}

	public Hashtable getModeActionMap() {
		return modeActionMap;
	}

	public JCheckBoxMenuItem getHighlightMenuItem() {
		return highlightMenuItem;
	}

	public JCheckBoxMenuItem getSynchroniseSplits() {
		return synchroniseSplits;
	}

	public JCheckBoxMenuItem getToggleFullScreenMenuItem() {
		return toggleFullScreenMenuItem;
	}

	public JCheckBoxMenuItem getTogglePrimarySidebarMenuItem() {
		return togglePrimarySidebarMenuItem;
	}

	public JCheckBoxMenuItem getToggleBottomPanelMenuItem() {
		return toggleBottomPanelMenuItem;
	}

	public JCheckBoxMenuItem getToggleSecondarySidebarMenuItem() {
		return toggleSecondarySidebarMenuItem;
	}

	public JCheckBoxMenuItem getShowStandardButtons() {
		return showStandardButtons;
	}

	public JCheckBoxMenuItem getShowEditorButtons() {
		return showEditorButtons;
	}

	public JCheckBoxMenuItem getShowFragmentButtons() {
		return showFragmentButtons;
	}

	public JCheckBoxMenuItem getOutlinerAutoCreateRequiredNodes() {
		return outlinerAutoCreateRequiredNodes;
	}

	public JCheckBoxMenuItem getOutlinerShowAttributeValues() {
		return outlinerShowAttributeValues;
	}

	public JCheckBoxMenuItem getOutlinerShowElementValues() {
		return outlinerShowElementValues;
	}

	public JCheckBoxMenuItem getEditorLinenumberMargin() {
		return editorLinenumberMargin;
	}

	public JCheckBoxMenuItem getEditorOverviewMargin() {
		return editorOverviewMargin;
	}

	public JCheckBoxMenuItem getEditorFoldingMargin() {
		return editorFoldingMargin;
	}

	public JCheckBoxMenuItem getEditorBookmarkMargin() {
		return editorBookmarkMargin;
	}

	public JCheckBoxMenuItem getEditorEndTagCompletion() {
		return editorEndTagCompletion;
	}

	public JCheckBoxMenuItem getEditorTagCompletion() {
		return editorTagCompletion;
	}

	public JCheckBoxMenuItem getEditorSmartIndentation() {
		return editorSmartIndentation;
	}

	public JCheckBoxMenuItem getEditorErrorHighlighting() {
		return editorErrorHighlighting;
	}

	public JCheckBoxMenuItem getEditorSoftWrapping() {
		return editorSoftWrapping;
	}

	public JCheckBoxMenuItem getViewerShowNamespaces() {
		return viewerShowNamespaces;
	}

	public JCheckBoxMenuItem getViewerShowAttributes() {
		return viewerShowAttributes;
	}

	public JCheckBoxMenuItem getViewerShowComments() {
		return viewerShowComments;
	}

	public JCheckBoxMenuItem getViewerShowPIs() {
		return viewerShowPIs;
	}

	public JCheckBoxMenuItem getViewerShowContent() {
		return viewerShowContent;
	}

	public JCheckBoxMenuItem getViewerInlineMixed() {
		return viewerInlineMixed;
	}

	public JCheckBoxMenuItem getGridHideContainerTables() {
		return gridHideContainerTables;
	}

	// ---------------------------------------------------------------
	// MenuItem creation helpers
	// ---------------------------------------------------------------

	private JMenuItem createMenuItem(Action action) {
		DogsBayMenuItem item = new DogsBayMenuItem(action);
		return item;
	}

	/**
	 * Creates a menu item and registers it in the menuItemMap and modeActionMap.
	 */
	public JMenuItem createMenuItem(Action action, String actionName) {
		if (actionName != null) {
			DogsBayMenuItem temp = getMenuItem(actionName);
			if (temp != null) {
				return temp;
			}

			DogsBayMenuItem item = new DogsBayMenuItem(action);
			menuItemMap.put(actionName, item);
			modeActionMap.put(actionName, action);

			return item;
		} else {
			return createMenuItem(action);
		}
	}

	/**
	 * Returns the required MenuItem.
	 */
	public DogsBayMenuItem getMenuItem(String actionName) {
		Object item = menuItemMap.get(actionName);
		return item instanceof DogsBayMenuItem menuItem ? menuItem : null;
	}

	/**
	 * Put a key on the command with this id, wherever it lives.
	 *
	 * <p>A command reachable from a menu gets an accelerator. One that is only
	 * a button on the menu bar — the view switches, say — has no menu item to
	 * carry a key, so its key goes in the window's input map instead. Without
	 * this, a command's binding depended on which kind of control someone
	 * happened to build for it.
	 *
	 * @param stroke the key, or null to bind nothing to this command
	 */
	public void bindAccelerator(String actionName, KeyStroke stroke) {
		Object item = menuItemMap.get(actionName);
		if (item instanceof DogsBayMenuItem menuItem) {
			menuItem.setAccelerator(stroke, false);
			return;
		}
		if (item instanceof JMenuItem menuItem) {
			menuItem.setAccelerator(stroke);
			return;
		}
		bindInWindow(actionName, stroke);
	}

	/** A key for a command with no menu item: the window listens for it. */
	private void bindInWindow(String actionName, KeyStroke stroke) {
		Action action = getModeAction(actionName);
		if (action == null || editor.getRootPane() == null) {
			return;
		}
		javax.swing.InputMap inputs =
				editor.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		// Clear the old key first, or a rebind leaves the previous one working.
		for (KeyStroke existing : inputs.keys() == null ? new KeyStroke[0] : inputs.keys()) {
			if (actionName.equals(inputs.get(existing))) {
				inputs.remove(existing);
			}
		}
		if (stroke != null) {
			inputs.put(stroke, actionName);
			editor.getRootPane().getActionMap().put(actionName, action);
		}
	}

	/**
	 * Returns the required JCheckBoxMenuItem.
	 */
	public JCheckBoxMenuItem getCheckBoxItem(String actionName) {
		return (JCheckBoxMenuItem) menuItemMap.get(actionName);
	}

	/**
	 * Returns the required Action.
	 */
	public Action getModeAction(String actionName) {
		return (Action) modeActionMap.get(actionName);
	}

	// ---------------------------------------------------------------
	// Menu bar construction
	// ---------------------------------------------------------------

	public void createMenubar() {
		menuBar = new JMenuBar();

		menuBar.add(createFileMenu());
		menuBar.add(createEditMenu());
		menuBar.add(createViewMenu());
		menuBar.add(createXMLMenu());
		menuBar.add(createProjectMenu());
		menuBar.add(createGrammarMenu());
		menuBar.add(createScenarioMenu());
		menuBar.add(createRefactorMenu());
		menuBar.add(createToolsMenu());

		// for each of the plugin buttons
		for (int cnt = 0; cnt < editor.getPluginViews().size(); ++cnt) {
			Object obj = editor.getPluginViews().get(cnt);
			if ((obj != null) && (obj instanceof PluginView)) {
				PluginView pluginView = (PluginView) obj;
				if (pluginView != null) {

					JMenu pluginMenu = pluginView.createPluginViewMenu();
					if (pluginMenu != null) {
						menuBar.add(pluginMenu);
						GUIUtilities.alignMenu(pluginMenu);
					}

				}
			}
		}

		// >>> Help Menu
		JMenu helpMenu = new JMenu("Help");
		helpMenu.setMnemonic('H');

		JMenuItem aboutItem = new JMenuItem("About", 'A');
		aboutItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				editor.showAboutDialog();
			}
		});

		// The documentation lives on the website. This was four JavaHelp items
		// (Contents, Index, Search, Getting Started) reading a help set that is
		// not shipped, so all four were permanently disabled.
		JMenuItem docsItem = new JMenuItem("Documentation", 'D');
		docsItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					BrowserLauncher.openURL(DOCUMENTATION_URL);
				} catch (Exception x) {
					// no browser available: nothing useful to show the user here
				}
			}
		});

		helpMenu.add(docsItem);

		JMenuItem welcomeItem = new JMenuItem("Welcome", 'W');
		welcomeItem.addActionListener(e -> editor.showWelcomeTab());
		helpMenu.add(welcomeItem);

		helpMenu.addSeparator();
		helpMenu.add(aboutItem);
		GUIUtilities.alignMenu(helpMenu);
		menuBar.add(helpMenu);

		// On macOS the OS screen menu bar can't host buttons, so the view/split controls,
		// layout toggles, and close button go in the toolbar / are reached via the View menu
		// there (see ToolbarManager). Everywhere else they live in the in-window menu bar.
		if (!com.dogsbay.dogsbayaieditor.Platform.isMacScreenMenuBar()) {
			menuBar.add(Box.createHorizontalGlue());

			// View switcher + split controls, to the left of the layout toggles (VS Code style)
			addViewSplitButtons(menuBar);
			menuBar.add(Box.createHorizontalStrut(8));

			// Layout toggle buttons on menu bar (VS Code style)
			JToggleButton togglePrimarySidebar = editor.createLayoutToggleButton(
				editor.getSidebarIcon(LAYOUT_SIDEBAR_LEFT_ICON), "Toggle Primary Sidebar (Ctrl+B)");
			togglePrimarySidebar.setSelected(editor.getProperties().isPrimarySidebarVisible());
			togglePrimarySidebar.addActionListener(e -> editor.togglePrimarySidebar());
			editor.setTogglePrimarySidebar(togglePrimarySidebar);
			menuBar.add(togglePrimarySidebar);

			JToggleButton toggleBottomPanel = editor.createLayoutToggleButton(
				editor.getSidebarIcon(LAYOUT_PANEL_ICON), "Toggle Bottom Panel (Ctrl+J)");
			toggleBottomPanel.setSelected(editor.getProperties().isBottomPanelVisible());
			toggleBottomPanel.addActionListener(e -> editor.toggleBottomPanel());
			editor.setToggleBottomPanel(toggleBottomPanel);
			menuBar.add(toggleBottomPanel);

			JToggleButton toggleSecondarySidebar = editor.createLayoutToggleButton(
				editor.getSidebarIcon(LAYOUT_SIDEBAR_RIGHT_ICON), "Toggle Secondary Sidebar (Ctrl+Alt+B)");
			toggleSecondarySidebar.setSelected(editor.getProperties().isSecondarySidebarVisible());
			toggleSecondarySidebar.addActionListener(e -> editor.toggleSecondarySidebar());
			editor.setToggleSecondarySidebar(toggleSecondarySidebar);
			menuBar.add(toggleSecondarySidebar);

			menuBar.add(Box.createHorizontalStrut(4));

			JButton closeButton = new JButton();
			closeButton.setMargin(new Insets(1, 1, 1, 1));
			closeButton.setAction(editor.getCloseAction());
			closeButton.setText(null);
			closeButton.setIcon(editor.getIcon(CLOSE_ICON));

			menuBar.add(closeButton);
		}

		editor.setJMenuBar(menuBar);
	}

	/**
	 * Adds the view switcher (Editor / Author / Preview) and split controls to the menu
	 * bar, to the left of the layout toggles — the single place to switch views and split.
	 */
	private void addViewSplitButtons(JMenuBar menuBar) {
		// Registered by id as well as added: a button carries no accelerator,
		// so its key goes in the window's input map, and that needs the id.
		modeActionMap.put(KeyPreferences.SHOW_EDITOR_VIEW_ACTION, editor.getShowEditorViewAction());
		modeActionMap.put(KeyPreferences.SHOW_AUTHOR_VIEW_ACTION, editor.getShowAuthorViewAction());
		modeActionMap.put(KeyPreferences.TOGGLE_AUTHOR_SPLIT_ACTION, editor.getToggleAuthorSplitAction());

		menuBar.add(menuBarButton(editor.getShowEditorViewAction()));
		menuBar.add(menuBarButton(editor.getShowAuthorViewAction()));
		menuBar.add(menuBarButton(editor.getOpenPreviewAction()));
		menuBar.add(Box.createHorizontalStrut(6));
		menuBar.add(menuBarButton(editor.getToggleAuthorSplitAction()));
		menuBar.add(menuBarButton(editor.getOpenPreviewSplitAction()));
		menuBar.add(Box.createHorizontalStrut(6));
		menuBar.add(menuBarButton(editor.getSplitTabsHorizontallyAction()));
		menuBar.add(menuBarButton(editor.getSplitTabsVerticallyAction()));
		menuBar.add(menuBarButton(editor.getUnsplitTabsAction()));
	}

	/** A flat, icon-only menu-bar button backed by an action (matches the layout toggles). */
	private JButton menuBarButton(Action action) {
		JButton button = new JButton(action);
		button.setHideActionText(true);
		button.setText(null);
		button.setToolTipText((String) action.getValue(Action.SHORT_DESCRIPTION));
		button.setFocusable(false);
		button.setPreferredSize(new java.awt.Dimension(28, 24));
		button.setMaximumSize(new java.awt.Dimension(28, 24));
		button.setMinimumSize(new java.awt.Dimension(28, 24));
		button.setBorderPainted(false);
		button.setContentAreaFilled(false);
		button.setOpaque(false);
		return button;
	}

	// ---------------------------------------------------------------
	// Individual menu construction
	// ---------------------------------------------------------------

	private JMenu createFileMenu() {
		fileMenu = new JMenu("File");
		fileMenu.setMnemonic('F');
		fileMenu.addMenuListener(new MenuListener() {
			public void menuCanceled(MenuEvent e) {
			}

			public void menuSelected(MenuEvent e) {
				updateFileMenu();
			}

			public void menuDeselected(MenuEvent e) {
			}
		});

		updateFileMenu();

		return fileMenu;
	}

	public void updateFileMenu() {
		if (fileMenu.getItemCount() > 0) {
			fileMenu.removeAll();
		}

		JMenuItem newItem = createMenuItem(editor.getNewAction(), KeyPreferences.NEW_DOCUMENT_ACTION);
		newItem.setText("New File");
		fileMenu.add(newItem);

		JMenuItem newProjectItem = new JMenuItem("New Project");
		newProjectItem.setMnemonic('P');
		newProjectItem.addActionListener(e -> {
			ManageProjectsDialog dialog = new ManageProjectsDialog(editor, editor.getProperties());
			dialog.setVisible(true);
			if (editor.getProjectPanel() != null) {
				editor.getProjectPanel().setProjects(editor.getProperties().getProjectProperties(), false);
			}
		});
		fileMenu.add(newProjectItem);
		fileMenu.addSeparator();

		JMenuItem openItem = createMenuItem(editor.getOpenAction(), KeyPreferences.OPEN_ACTION);
		openItem.setText("Open File");
		fileMenu.add(openItem);

		fileMenu.add(createMenuItem(editor.getOpenFolderAction(), null));

		// Disable "Open Sample Project" while the sample is already the open project.
		Action sampleAction = editor.getOpenSampleProjectAction();
		java.io.File openRoot = editor.getFileExplorer() != null
				? editor.getFileExplorer().getRootDirectory() : null;
		sampleAction.setEnabled(com.dogsbay.dogsbayaieditor.samples.SampleProject.isAvailable()
				&& !com.dogsbay.dogsbayaieditor.samples.SampleProject.isSampleFolder(openRoot));
		fileMenu.add(createMenuItem(sampleAction, null));

		Vector docs = editor.getProperties().getLastOpenedDocuments();
		Vector projects = editor.getProperties().getLastOpenedProjects();

		// Always show "Open Recent..." menu (like VSCode)
		if (docs.size() > 0 || projects.size() > 0) {
			JMenu openRecentMenu = new JMenu("Open Recent...");
			openRecentMenu.setMnemonic('R');

			// Recent Files section
			if (docs.size() > 0) {
				// Add "Files" section header
				JMenuItem filesHeader = new JMenuItem("Files");
				filesHeader.setEnabled(false);
				filesHeader.setFont(filesHeader.getFont().deriveFont(Font.BOLD));
				openRecentMenu.add(filesHeader);

				for (int i = 0; i < docs.size(); i++) {
					URL url = null;

					try {
						url = DogsBayURLUtilities.getURLFromFile(new File((String) docs.elementAt(i)));
					} catch (Exception e) {
						e.printStackTrace();
						// should not happen
					}

					openRecentMenu.add(createMenuItem(new OpenMRUAction(editor, url, i + 1)));
				}
			}

			// Separator between files and projects
			if (docs.size() > 0 && projects.size() > 0) {
				openRecentMenu.addSeparator();
			}

			// Recent Projects section
			if (projects.size() > 0) {
				// Add "Projects" section header
				JMenuItem projectsHeader = new JMenuItem("Projects");
				projectsHeader.setEnabled(false);
				projectsHeader.setFont(projectsHeader.getFont().deriveFont(Font.BOLD));
				openRecentMenu.add(projectsHeader);

				for (int i = 0; i < projects.size(); i++) {
					String projectPath = (String) projects.elementAt(i);
					openRecentMenu.add(createMenuItem(new OpenRecentProjectAction(editor, projectPath, i + 1)));
				}
			}

			fileMenu.add(openRecentMenu);
		}
		fileMenu.addSeparator();

		// Register action for keyboard shortcut without adding to menu
		createMenuItem(editor.getReloadAction(), KeyPreferences.RELOAD_ACTION);

		fileMenu.add(createMenuItem(editor.getSaveAction(), KeyPreferences.SAVE_ACTION));
		fileMenu.add(createMenuItem(editor.getSaveAsAction(), KeyPreferences.SAVE_AS_ACTION));
		fileMenu.add(createMenuItem(editor.getSaveAllAction(), KeyPreferences.SAVE_ALL_ACTION));

		fileMenu.addSeparator();

		fileMenu.add(createMenuItem(editor.getPreferencesAction(), KeyPreferences.PREFERENCES_ACTION));

		fileMenu.addSeparator();

		JMenuItem importFrameworkItem = new JMenuItem("Import Framework...");
		importFrameworkItem.setMnemonic('m');
		importFrameworkItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				FrameworkImportDialog dlg = new FrameworkImportDialog(editor, editor.getProperties());
				dlg.setVisible(true);
			}
		});
		fileMenu.add(importFrameworkItem);

		JMenuItem manageFrameworksItem = new JMenuItem("Manage Frameworks...");
		manageFrameworksItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				FrameworkManagementDialog dlg = new FrameworkManagementDialog(editor, editor.getProperties());
				dlg.setVisible(true);
			}
		});
		fileMenu.add(manageFrameworksItem);

		fileMenu.addSeparator();

		fileMenu.add(createMenuItem(editor.getCloseAction(), KeyPreferences.CLOSE_ACTION));
		fileMenu.add(createMenuItem(editor.getCloseAllAction(), KeyPreferences.CLOSE_ALL_ACTION));

		fileMenu.addSeparator();

		JMenuItem exitItem = new JMenuItem("Exit", 'x');
		exitItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				editor.dispatchEvent(new WindowEvent(editor, WindowEvent.WINDOW_CLOSING));
			}
		});

		fileMenu.add(exitItem);

		GUIUtilities.alignMenu(fileMenu);
	}

	private JMenu createEditMenu() {
		editMenu = new JMenu("Edit");
		editMenu.setMnemonic('E');

		editMenu.add(createMenuItem(editor.getUndoAction(), KeyPreferences.UNDO_ACTION));
		editMenu.add(createMenuItem(editor.getRedoAction(), KeyPreferences.REDO_ACTION));

		editMenu.addSeparator();

		editMenu.add(createMenuItem(editor.getCutAction(), KeyPreferences.CUT_ACTION));
		editMenu.add(createMenuItem(editor.getCopyAction(), KeyPreferences.COPY_ACTION));
		editMenu.add(createMenuItem(editor.getPasteAction(), KeyPreferences.PASTE_ACTION));

		// Register action for keyboard shortcut without adding to menu
		createMenuItem(editor.getGotoAction(), KeyPreferences.GOTO_ACTION);

		editMenu.add(createMenuItem(editor.getFindAction(), KeyPreferences.FIND_ACTION));
		editMenu.add(createMenuItem(editor.getReplaceAction(), KeyPreferences.REPLACE_ACTION));

		editMenu.addSeparator();

		editMenu.add(createMenuItem(editor.getFindInFilesAction(), KeyPreferences.FIND_IN_FILES_ACTION));
		editMenu.add(createMenuItem(editor.getReplaceInFilesAction(), null));

		GUIUtilities.alignMenu(editMenu);

		// Manually register Ctrl+G for Goto action since menu item is not in menu
		editor.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
			KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK),
			KeyPreferences.GOTO_ACTION
		);
		editor.getRootPane().getActionMap().put(KeyPreferences.GOTO_ACTION, editor.getGotoAction());

		return editMenu;
	}

	private JMenu createViewMenu() {
		viewMenu = new JMenu("View");
		viewMenu.setMnemonic('V');

		// Startup skeleton: editing modes (Editor/Author) when a document is
		// already open, plus the preview commands. Per-document menus built by
		// DogsBayView.setupViewMenu() replace this via setDocumentViewsMenu().
		setDocumentViewsMenu(new JMenu("Document Views"));
		getDocumentViewsMenu().setMnemonic('D');
		ButtonGroup group = new ButtonGroup();
		DogsBayView currentView = editor.getView();
		if (currentView != null) {

			JRadioButtonMenuItem item = currentView.getEditorViewItem();
			group.add(item);
			getDocumentViewsMenu().add(item);

			item = currentView.getAuthorViewItem();
			group.add(item);
			getDocumentViewsMenu().add(item);

			// for each of the plugin buttons
			for (int cnt = 0; cnt < editor.getPluginViews().size(); ++cnt) {
				Object obj = editor.getPluginViews().get(cnt);
				if ((obj != null) && (obj instanceof PluginView)) {
					PluginView pluginView = (PluginView) obj;
					if (pluginView != null) {

						JRadioButtonMenuItem pluginItem = pluginView.getPluginViewItem();
						if (pluginItem != null) {
							group.add(pluginItem);
							getDocumentViewsMenu().add(pluginItem);
						}

					}
				}
			}
		}

		GUIUtilities.alignMenu(getDocumentViewsMenu());

		// The per-document view switcher lives at the top of the View menu —
		// already inserted by setDocumentViewsMenu() above. (The old top-right
		// button row was dismantled in the FlatLaf layout; this submenu,
		// swapped per document by installDocumentViewsMenu, is the visible way
		// to reach the Editor/Author views.) The preview commands sit directly
		// below it: same "how do I view this document" cluster, but global
		// items so keyboard-preference remapping keeps working (menuItemMap).
		getDocumentViewsMenu().setEnabled(getDocumentViewsMenu().getMenuComponentCount() > 0);
		viewMenu.add(createMenuItem(editor.getOpenPreviewAction(), KeyPreferences.PREVIEW_IN_TAB_ACTION));
		viewMenu.add(createMenuItem(editor.getOpenPreviewSplitAction(), KeyPreferences.PREVIEW_IN_SPLIT_ACTION));
		viewMenu.addSeparator();

		Action a = editor.getHighlightAction();

		highlightMenuItem = new JCheckBoxMenuItem((String) a.getValue(Action.NAME),
				(ImageIcon) a.getValue(Action.SMALL_ICON));
		highlightMenuItem.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				editor.getHighlightButton().setSelected(highlightMenuItem.isSelected());
			}
		});
		highlightMenuItem.setAction(a);
		highlightMenuItem.setEnabled(a.isEnabled());
		menuItemMap.put(KeyPreferences.HIGHLIGHT_ACTION, highlightMenuItem);

		// Create Appearance submenu
		JMenu appearanceMenu = new JMenu("Appearance");
		appearanceMenu.setMnemonic('A');
		appearanceMenu.add(createMenuItem(editor.getToggleFullScreenAction(), KeyPreferences.TOGGLE_FULL_ACTION));

		appearanceMenu.addSeparator();

		showStandardButtons = new JCheckBoxMenuItem("Standard toolbar");
		showStandardButtons.setSelected(editor.getProperties().isShowToolbar());
		showStandardButtons.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				if (editor.getToolbar() != null) {
					editor.getToolbar().setVisible(showStandardButtons.isSelected());
					editor.getToolbarPanel().setVisible(showStandardButtons.isSelected());
					editor.getProperties().setShowToolbar(showStandardButtons.isSelected());
				}
			}
		});

		appearanceMenu.add(showStandardButtons);
		menuItemMap.put(KeyPreferences.VIEW_STANDARD_BUTTONS_ACTION, showStandardButtons);

		showEditorButtons = new JCheckBoxMenuItem("Editor toolbar");
		showEditorButtons.setSelected(editor.getProperties().isShowEditorToolbar());
		showEditorButtons.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				if (editor.getToolbar() != null) {
					editor.getEditorToolbar().setVisible(showEditorButtons.isSelected());
					editor.getEditorToolbarPanel().setVisible(showEditorButtons.isSelected());
					editor.getProperties().setShowEditorToolbar(showEditorButtons.isSelected());
				}
			}
		});

		appearanceMenu.add(showEditorButtons);
		menuItemMap.put(KeyPreferences.VIEW_EDITOR_BUTTONS_ACTION, showEditorButtons);

		showFragmentButtons = new JCheckBoxMenuItem("Fragment toolbar");
		showFragmentButtons.setSelected(editor.getProperties().getEditorProperties().isShowFragmentsToolbar());

		showFragmentButtons.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				editor.getProperties().getEditorProperties().setShowFragmentsToolbar(showFragmentButtons.isSelected());

				Vector views = editor.getViews();
				for (int i = 0; i < views.size(); i++) {
					((DogsBayView) views.elementAt(i)).getEditor()
							.showFragmentToolbar(showFragmentButtons.isSelected());
				}
			}
		});

		appearanceMenu.add(showFragmentButtons);
		menuItemMap.put(KeyPreferences.VIEW_FRAGMENT_BUTTONS_ACTION, showFragmentButtons);

		appearanceMenu.addSeparator();

		togglePrimarySidebarMenuItem = new JCheckBoxMenuItem("Toggle Primary Sidebar");
		togglePrimarySidebarMenuItem.setSelected(editor.getProperties().isPrimarySidebarVisible());
		togglePrimarySidebarMenuItem.addActionListener(e -> editor.togglePrimarySidebar());
		menuItemMap.put(KeyPreferences.TOGGLE_PRIMARY_SIDEBAR_ACTION, togglePrimarySidebarMenuItem);
		appearanceMenu.add(togglePrimarySidebarMenuItem);

		toggleBottomPanelMenuItem = new JCheckBoxMenuItem("Toggle Bottom Panel");
		toggleBottomPanelMenuItem.setSelected(editor.getProperties().isBottomPanelVisible());
		toggleBottomPanelMenuItem.addActionListener(e -> editor.toggleBottomPanel());
		menuItemMap.put(KeyPreferences.TOGGLE_BOTTOM_PANEL_ACTION, toggleBottomPanelMenuItem);
		appearanceMenu.add(toggleBottomPanelMenuItem);

		toggleSecondarySidebarMenuItem = new JCheckBoxMenuItem("Toggle Secondary Sidebar");
		toggleSecondarySidebarMenuItem.setSelected(editor.getProperties().isSecondarySidebarVisible());
		toggleSecondarySidebarMenuItem.addActionListener(e -> editor.toggleSecondarySidebar());
		menuItemMap.put(KeyPreferences.TOGGLE_SECONDARY_SIDEBAR_ACTION, toggleSecondarySidebarMenuItem);
		appearanceMenu.add(toggleSecondarySidebarMenuItem);

		GUIUtilities.alignMenu(appearanceMenu);
		viewMenu.add(appearanceMenu);

		// Create Editor Layout submenu
		JMenu editorLayoutMenu = new JMenu("Editor Layout");
		editorLayoutMenu.setMnemonic('L');
		editorLayoutMenu.add(createMenuItem(editor.getSplitTabsHorizontallyAction(), KeyPreferences.VIEW_SPLIT_HORIZONTALLY_ACTION));
		editorLayoutMenu.add(createMenuItem(editor.getSplitTabsVerticallyAction(), KeyPreferences.VIEW_SPLIT_VERTICALLY_ACTION));
		editorLayoutMenu.add(createMenuItem(editor.getUnsplitTabsAction(), KeyPreferences.VIEW_UNSPLIT_ACTION));
		editorLayoutMenu.addSeparator();
		synchroniseSplits = new JCheckBoxMenuItem("Synchronise Splits on XPath", editor.getIcon(SYNCHRONISE_SPLITS_ICON));
		synchroniseSplits.setSelected(editor.getProperties().isSynchroniseSplits());
		synchroniseSplits.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				editor.getProperties().setSynchroniseSplits(synchroniseSplits.isSelected());
			}
		});
		editorLayoutMenu.add(synchroniseSplits);
		menuItemMap.put(KeyPreferences.VIEW_SYNCHRONIZE_SPLITS_ACTION, synchroniseSplits);
		GUIUtilities.alignMenu(editorLayoutMenu);
		viewMenu.add(editorLayoutMenu);

		viewMenu.addSeparator();
		JMenu editorProperties = new JMenu("Editor Properties");

		viewMenu.add(editorProperties);

		editorBookmarkMargin = new JCheckBoxMenuItem("Show Annotation Margin");
		editorBookmarkMargin.setSelected(editor.getProperties().getEditorProperties().isShowAnnotationMargin());
		editorBookmarkMargin.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				editor.getProperties().getEditorProperties().setShowAnnotationMargin(editorBookmarkMargin.isSelected());

				Vector views = editor.getViews();
				for (int i = 0; i < views.size(); i++) {
					((DogsBayView) views.elementAt(i)).getEditor()
							.showAnnotationMargin(editorBookmarkMargin.isSelected());
				}
			}
		});

		editorProperties.add(editorBookmarkMargin);
		menuItemMap.put(KeyPreferences.VIEW_EDITOR_SHOW_ANNOTATION_ACTION, editorBookmarkMargin);

		editorLinenumberMargin = new JCheckBoxMenuItem("Show Linenumber Margin");
		editorLinenumberMargin.setSelected(editor.getProperties().getEditorProperties().isShowMargin());
		editorLinenumberMargin.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				editor.getProperties().getEditorProperties().setShowMargin(editorLinenumberMargin.isSelected());

				Vector views = editor.getViews();
				for (int i = 0; i < views.size(); i++) {
					((DogsBayView) views.elementAt(i)).getEditor()
							.showLinenumberMargin(editorLinenumberMargin.isSelected());
				}
			}
		});

		editorProperties.add(editorLinenumberMargin);
		menuItemMap.put(KeyPreferences.VIEW_EDITOR_SHOW_LINE_NUMBER_ACTION, editorLinenumberMargin);

		editorFoldingMargin = new JCheckBoxMenuItem("Show Folding Margin");
		editorFoldingMargin.setSelected(editor.getProperties().getEditorProperties().isShowFoldingMargin());
		editorFoldingMargin.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				editor.getProperties().getEditorProperties().setShowFoldingMargin(editorFoldingMargin.isSelected());

				Vector views = editor.getViews();
				for (int i = 0; i < views.size(); i++) {
					((DogsBayView) views.elementAt(i)).getEditor()
							.showFoldingMargin(editorFoldingMargin.isSelected());
				}
			}
		});

		editorProperties.add(editorFoldingMargin);
		menuItemMap.put(KeyPreferences.VIEW_EDITOR_SHOW_FOLDING_ACTION, editorFoldingMargin);

		editorOverviewMargin = new JCheckBoxMenuItem("Show Overview Margin");
		editorOverviewMargin.setSelected(editor.getProperties().getEditorProperties().isShowOverviewMargin());
		editorOverviewMargin.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				editor.getProperties().getEditorProperties().setShowOverviewMargin(editorOverviewMargin.isSelected());

				Vector views = editor.getViews();
				for (int i = 0; i < views.size(); i++) {
					((DogsBayView) views.elementAt(i)).getEditor()
							.showOverviewMargin(editorOverviewMargin.isSelected());
				}
			}
		});

		editorProperties.add(editorOverviewMargin);
		menuItemMap.put(KeyPreferences.VIEW_EDITOR_SHOW_OVERVIEW_ACTION, editorOverviewMargin);

		editorProperties.addSeparator();

		editorTagCompletion = new JCheckBoxMenuItem("Tag Completion");
		editorTagCompletion.setSelected(editor.getProperties().getEditorProperties().isTextPrompting());
		editorTagCompletion.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				editor.getProperties().getEditorProperties().setTextPrompting(editorTagCompletion.isSelected());
			}
		});

		editorProperties.add(editorTagCompletion);
		menuItemMap.put(KeyPreferences.VIEW_EDITOR_TAG_COMPLETION_ACTION, editorTagCompletion);

		editorEndTagCompletion = new JCheckBoxMenuItem("End Tag Completion");
		editorEndTagCompletion.setSelected(editor.getProperties().getEditorProperties().isTagCompletion());
		editorEndTagCompletion.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				editor.getProperties().getEditorProperties().setTagCompletion(editorEndTagCompletion.isSelected());

				Vector views = editor.getViews();
				for (int i = 0; i < views.size(); i++) {
					((DogsBayView) views.elementAt(i)).getEditor()
							.setEndTagCompletion(editorEndTagCompletion.isSelected());
				}
			}
		});

		editorProperties.add(editorEndTagCompletion);
		menuItemMap.put(KeyPreferences.VIEW_EDITOR_END_TAG_COMPLETION_ACTION, editorEndTagCompletion);

		editorSmartIndentation = new JCheckBoxMenuItem("Smart Indentation");
		editorSmartIndentation.setSelected(editor.getProperties().getEditorProperties().isSmartIndentation());
		editorSmartIndentation.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				editor.getProperties().getEditorProperties().setSmartIndentation(editorSmartIndentation.isSelected());

				Vector views = editor.getViews();
				for (int i = 0; i < views.size(); i++) {
					((DogsBayView) views.elementAt(i)).getEditor()
							.setSmartIndentation(editorSmartIndentation.isSelected());
				}
			}
		});

		editorProperties.add(editorSmartIndentation);
		menuItemMap.put(KeyPreferences.VIEW_EDITOR_SMART_INDENTATION_ACTION, editorSmartIndentation);

		editorProperties.addSeparator();

		editorErrorHighlighting = new JCheckBoxMenuItem("Error Highlighting");
		editorErrorHighlighting.setSelected(editor.getProperties().getEditorProperties().isErrorHighlighting());
		editorErrorHighlighting.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				editor.getProperties().getEditorProperties().setErrorHighlighting(editorErrorHighlighting.isSelected());

				Vector views = editor.getViews();
				for (int i = 0; i < views.size(); i++) {
					((DogsBayView) views.elementAt(i)).getEditor()
							.setErrorHighlighting(editorErrorHighlighting.isSelected());
				}
			}
		});

		editorProperties.add(editorErrorHighlighting);
		menuItemMap.put(KeyPreferences.VIEW_EDITOR_ERROR_HIGHLIGHTING_ACTION, editorErrorHighlighting);

		editorProperties.addSeparator();

		editorSoftWrapping = new JCheckBoxMenuItem("Soft Wrapping");
		editorSoftWrapping.setSelected(editor.getProperties().getEditorProperties().isSoftWrapping());
		editorSoftWrapping.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				editor.getProperties().getEditorProperties().setSoftWrapping(editorSoftWrapping.isSelected());

				Vector views = editor.getViews();
				for (int i = 0; i < views.size(); i++) {
					((DogsBayView) views.elementAt(i)).getEditor().updatePreferences();
				}
			}
		});

		editorProperties.add(editorSoftWrapping);
		menuItemMap.put(KeyPreferences.VIEW_EDITOR_SOFT_WRAPPING_ACTION, editorSoftWrapping);

		JMenu viewerProperties = new JMenu("Viewer Properties");
		viewMenu.add(viewerProperties);

		viewerShowNamespaces = new JCheckBoxMenuItem("Show Namespaces");
		viewerShowNamespaces.setSelected(editor.getProperties().getViewerProperties().isShowNamespaces());
		viewerShowNamespaces.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				editor.getProperties().getViewerProperties().showNamespaces(viewerShowNamespaces.isSelected());

				Vector views = editor.getViews();
				for (int i = 0; i < views.size(); i++) {
					((DogsBayView) views.elementAt(i)).getViewer().updatePreferences();
				}
			}
		});

		viewerProperties.add(viewerShowNamespaces);
		menuItemMap.put(KeyPreferences.VIEWER_SHOW_NAMESPACES_ACTION, viewerShowNamespaces);

		viewerShowAttributes = new JCheckBoxMenuItem("Show Attributes");
		viewerShowAttributes.setSelected(editor.getProperties().getViewerProperties().isShowAttributes());
		viewerShowAttributes.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				editor.getProperties().getViewerProperties().showAttributes(viewerShowAttributes.isSelected());

				Vector views = editor.getViews();
				for (int i = 0; i < views.size(); i++) {
					((DogsBayView) views.elementAt(i)).getViewer().updatePreferences();
				}
			}
		});

		viewerProperties.add(viewerShowAttributes);
		menuItemMap.put(KeyPreferences.VIEWER_SHOW_ATTRIBUTES_ACTION, viewerShowAttributes);

		viewerShowComments = new JCheckBoxMenuItem("Show Comments");
		viewerShowComments.setSelected(editor.getProperties().getViewerProperties().isShowComments());
		viewerShowComments.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				editor.getProperties().getViewerProperties().showComments(viewerShowComments.isSelected());

				Vector views = editor.getViews();
				for (int i = 0; i < views.size(); i++) {
					((DogsBayView) views.elementAt(i)).getViewer().updatePreferences();
				}
			}
		});

		viewerProperties.add(viewerShowComments);
		menuItemMap.put(KeyPreferences.VIEWER_SHOW_COMMENTS_ACTION, viewerShowComments);

		viewerShowContent = new JCheckBoxMenuItem("Show Text Content");
		viewerShowContent.setSelected(editor.getProperties().getViewerProperties().isShowValues());
		viewerShowContent.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				editor.getProperties().getViewerProperties().showValues(viewerShowContent.isSelected());

				Vector views = editor.getViews();
				for (int i = 0; i < views.size(); i++) {
					((DogsBayView) views.elementAt(i)).getViewer().updatePreferences();
				}
			}
		});

		viewerProperties.add(viewerShowContent);
		menuItemMap.put(KeyPreferences.VIEWER_SHOW_TEXT_CONTENT_ACTION, viewerShowContent);

		viewerShowPIs = new JCheckBoxMenuItem("Show Processing Instructions");
		viewerShowPIs.setSelected(editor.getProperties().getViewerProperties().isShowPI());
		viewerShowPIs.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				editor.getProperties().getViewerProperties().showPI(viewerShowPIs.isSelected());

				Vector views = editor.getViews();
				for (int i = 0; i < views.size(); i++) {
					((DogsBayView) views.elementAt(i)).getViewer().updatePreferences();
				}
			}
		});

		viewerProperties.add(viewerShowPIs);
		menuItemMap.put(KeyPreferences.VIEWER_SHOW_PROCESSING_INSTRUCTIONS_ACTION, viewerShowPIs);
		viewerProperties.addSeparator();

		viewerInlineMixed = new JCheckBoxMenuItem("Inline Mixed Content");
		viewerInlineMixed.setSelected(editor.getProperties().getViewerProperties().isShowInline());
		viewerInlineMixed.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				editor.getProperties().getViewerProperties().showInline(viewerInlineMixed.isSelected());

				Vector views = editor.getViews();
				for (int i = 0; i < views.size(); i++) {
					((DogsBayView) views.elementAt(i)).getViewer().updatePreferences();
				}
			}
		});

		viewerProperties.add(viewerInlineMixed);
		menuItemMap.put(KeyPreferences.VIEWER_INLINE_MIXED_CONTENT_ACTION, viewerInlineMixed);

		// Commented out Outliner Properties - Designer view removed
		// (large block of commented-out code omitted for clarity)

		// for each of the plugin buttons
		for (int cnt = 0; cnt < editor.getPluginViews().size(); ++cnt) {
			Object obj = editor.getPluginViews().get(cnt);
			if ((obj != null) && (obj instanceof PluginView)) {
				PluginView pluginView = (PluginView) obj;
				if (pluginView != null) {

					JMenu pluginMenu = pluginView.createPluginViewPropertiesMenu();
					if (pluginMenu != null) {
						viewMenu.add(pluginMenu);
					}

				}
			}
		}

		viewMenu.addSeparator();

		viewMenu.add(createMenuItem(editor.getChangeDocumentAction(), KeyPreferences.SELECT_DOCUMENT_ACTION));

		GUIUtilities.alignMenu(viewMenu);
		return viewMenu;
	}

	private JMenu createProjectMenu() {
		JMenu projectMenu = new JMenu("Project");
		projectMenu.setMnemonic('P');

		JMenuItem manageProjectsItem = new JMenuItem("Manage Projects...");
		manageProjectsItem.addActionListener(e -> {
			ManageProjectsDialog dialog = new ManageProjectsDialog(editor, editor.getProperties());
			dialog.setVisible(true);
			if (editor.getProjectPanel() != null) {
				editor.getProjectPanel().setProjects(editor.getProperties().getProjectProperties(), false);
			}
		});
		projectMenu.add(manageProjectsItem);

		JMenuItem saveProjectSettingsItem = new JMenuItem("Save Project Settings...");
		saveProjectSettingsItem.addActionListener(e -> editor.saveProjectSettings());
		projectMenu.add(saveProjectSettingsItem);

		projectMenu.addSeparator();

		// Project-wide validation. DitaPlugin adds its DITA-OT and controlled-value
		// validators into this same submenu, so every "validate the project" command lives
		// in one place rather than being split across the menu.
		JMenu validateMenu = new JMenu(PROJECT_VALIDATE_MENU);
		validateMenu.setMnemonic('V');

		JMenuItem validateProjectItem = new JMenuItem("Project...");
		validateProjectItem.addActionListener(e -> editor.validateProject());
		validateMenu.add(validateProjectItem);

		JMenuItem schematronProjectItem = new JMenuItem("Project with Schematron...");
		schematronProjectItem.addActionListener(e -> editor.runProjectSchematron());
		validateMenu.add(schematronProjectItem);

		// Document-scoped: needs no project folder, checks only the active file.
		// Without this there is no GUI route to Schematron for a single document,
		// which is what the retired legacy dialog offered.
		JMenuItem schematronDocumentItem = new JMenuItem("Document with Schematron...");
		schematronDocumentItem.addActionListener(e -> editor.runDocumentSchematron());
		validateMenu.add(schematronDocumentItem);

		projectMenu.add(validateMenu);

		projectMenu.addSeparator();

		// Project-wide formatting to the house style (same engine as per-file Format/Reflow).
		// Kept last: DitaPlugin inserts its groups before FORMAT_PROJECT_ITEM, so the
		// deliverable commands land above these rather than trailing off the end.
		projectMenu.add(new JMenuItem(
				new com.dogsbay.dogsbayaieditor.actions.FormatProjectAction(editor, false)));
		projectMenu.add(new JMenuItem(
				new com.dogsbay.dogsbayaieditor.actions.FormatProjectAction(editor, true)));

		GUIUtilities.alignMenu(projectMenu);
		return projectMenu;
	}

	private JMenu createXMLMenu() {
		JMenu xmlMenu = new JMenu("XML");
		xmlMenu.setMnemonic('X');

		xmlMenu.add(createMenuItem(editor.getParseAction(), KeyPreferences.WELL_FORMEDNESS_ACTION));
		xmlMenu.add(createMenuItem(editor.getValidateAction(), KeyPreferences.VALIDATE_ACTION));
		// Project-wide validation, deliverables, and project settings live in the
		// Project menu (see createProjectMenu).

		xmlMenu.addSeparator();

		xmlMenu.add(createMenuItem(editor.getSelectElementAction(), KeyPreferences.SELECT_ELEMENT_ACTION));
		xmlMenu.add(createMenuItem(editor.getSelectElementContentAction(), KeyPreferences.SELECT_ELEMENT_CONTENT_ACTION));

		xmlMenu.addSeparator();

		xmlMenu.add(createMenuItem(editor.getSplitElementAction(), KeyPreferences.SPLIT_ELEMENT_ACTION));

		xmlMenu.addSeparator();

		xmlMenu.add(createMenuItem(editor.getInsertEntityAction(), KeyPreferences.INSERT_SPECIAL_CHAR_ACTION));
		xmlMenu.add(createMenuItem(editor.getSubstituteEntitiesAction(), KeyPreferences.CONVERT_ENTITIES_ACTION));
		xmlMenu.add(createMenuItem(editor.getSubstituteCharactersAction(), KeyPreferences.CONVERT_CHARACTERS_ACTION));

		xmlMenu.addSeparator();

		xmlMenu.add(createMenuItem(editor.getStripTagsAction(), KeyPreferences.STRIP_TAG_ACTION));

		xmlMenu.addSeparator();

		xmlMenu.add(createMenuItem(editor.getTagAction(), KeyPreferences.TAG_ACTION));
		xmlMenu.add(createMenuItem(editor.getRepeatTagAction(), KeyPreferences.REPEAT_TAG_ACTION));
		xmlMenu.add(createMenuItem(editor.getRenameElementAction(), KeyPreferences.RENAME_ELEMENT_ACTION));
		xmlMenu.add(createMenuItem(editor.getToggleEmptyElementAction(), KeyPreferences.TOGGLE_EMPTY_ELEMENT_ACTION));
		xmlMenu.add(createMenuItem(editor.getCommentAction(), KeyPreferences.COMMENT_ACTION));
		xmlMenu.add(createMenuItem(editor.getCDATAAction(), KeyPreferences.ADD_CDATA_ACTION));

		xmlMenu.addSeparator();
		xmlMenu.add(createMenuItem(editor.getLockAction(), KeyPreferences.LOCK_ACTION));
		xmlMenu.addSeparator();

		xmlMenu.add(createMenuItem(editor.getFormatAction(), KeyPreferences.FORMAT_ACTION));
		xmlMenu.add(new javax.swing.JMenuItem(editor.getReflowSentencesAction()));

		xmlMenu.addSeparator();

		xmlMenu.add(createMenuItem(editor.getGotoStartTagAction(), KeyPreferences.GOTO_START_TAG_ACTION));
		xmlMenu.add(createMenuItem(editor.getGotoEndTagAction(), KeyPreferences.GOTO_END_TAG_ACTION));

		xmlMenu.add(createMenuItem(editor.getGotoPreviousAttributeValueAction(),
				KeyPreferences.GOTO_PREVIOUS_ATTRIBUTE_VALUE_ACTION));
		xmlMenu.add(createMenuItem(editor.getGotoNextAttributeValueAction(), KeyPreferences.GOTO_NEXT_ATTRIBUTE_VALUE_ACTION));

		xmlMenu.addSeparator();

		xmlMenu.add(createMenuItem(editor.getToolsStripTextAction(), KeyPreferences.TOOLS_EMPTY_DOCUMENT_ACTION));

		JMenu changeCaseMenu = new JMenu("Change Case");
		changeCaseMenu.setMnemonic('H');

		changeCaseMenu.add(createMenuItem(editor.getToolsCapitalizeAction(), KeyPreferences.TOOLS_CAPITALIZE_ACTION));
		changeCaseMenu.add(createMenuItem(editor.getToolsDeCapitalizeAction(), KeyPreferences.TOOLS_DECAPITALIZE_ACTION));
		changeCaseMenu.add(createMenuItem(editor.getToolsUppercaseAction(), KeyPreferences.TOOLS_UPPERCASE_ACTION));
		changeCaseMenu.add(createMenuItem(editor.getToolsLowercaseAction(), KeyPreferences.TOOLS_LOWERCASE_ACTION));

		xmlMenu.add(changeCaseMenu);

		JMenu namespacesMenu = new JMenu("Namespaces");
		namespacesMenu.setMnemonic('A');

		namespacesMenu.add(createMenuItem(editor.getToolsMoveNSToRootAction(), KeyPreferences.TOOLS_MOVE_NS_TO_ROOT_ACTION));
		namespacesMenu.add(
				createMenuItem(editor.getToolsMoveNSToFirstUsedAction(), KeyPreferences.TOOLS_MOVE_NS_TO_FIRST_USED_ACTION));
		namespacesMenu
				.add(createMenuItem(editor.getToolsChangeNSPrefixAction(), KeyPreferences.TOOLS_CHANGE_NS_PREFIX_ACTION));
		namespacesMenu
				.add(createMenuItem(editor.getToolsRemoveUnusedNSAction(), KeyPreferences.TOOLS_REMOVE_UNUSED_NS_ACTION));

		xmlMenu.add(namespacesMenu);

		JMenu nodesMenu = new JMenu("Nodes");
		nodesMenu.setMnemonic('N');

		nodesMenu.add(createMenuItem(editor.getToolsAddNodeAction(), KeyPreferences.TOOLS_ADD_NODE_ACTION));
		nodesMenu.add(createMenuItem(editor.getToolsRemoveNodeAction(), KeyPreferences.TOOLS_REMOVE_NODE_ACTION));
		nodesMenu.add(createMenuItem(editor.getToolsSetNodeValueAction(), KeyPreferences.TOOLS_SET_NODE_VALUE_ACTION));
		nodesMenu.add(createMenuItem(editor.getToolsRenameNodeAction(), KeyPreferences.TOOLS_RENAME_NODE_ACTION));
		nodesMenu.add(createMenuItem(editor.getToolsConvertNodeAction(), KeyPreferences.TOOLS_CONVERT_NODE_ACTION));
		nodesMenu.add(createMenuItem(editor.getToolsAddNodeToNamespaceAction(), KeyPreferences.TOOLS_ADD_NODE_TO_NS_ACTION));
		nodesMenu.add(createMenuItem(editor.getToolsSortNodeAction(), KeyPreferences.TOOLS_SORT_NODE_ACTION));

		xmlMenu.add(nodesMenu);

		GUIUtilities.alignMenu(xmlMenu);

		return xmlMenu;
	}


	private JMenu createToolsMenu() {
		JMenu toolsMenu = new JMenu("Utilities");
		toolsMenu.setMnemonic('U');

		if (!Identity.getIdentity().getEdition().equals(Identity.XMLPLUS_EDITION_LITE)) {
			toolsMenu.add(createMenuItem(editor.getXDiffAction(), KeyPreferences.XDIFF_ACTION));
			toolsMenu.addSeparator();
		}
		toolsMenu.add(createMenuItem(editor.getOpenBrowserAction(), KeyPreferences.START_BROWSER_ACTION));
		// preview actions moved to View > Document Views
		toolsMenu.addSeparator();
		toolsMenu.add(createMenuItem(editor.getResolveXIncludesAction(), KeyPreferences.RESOLVE_XINCLUDES_ACTION));
		toolsMenu.addSeparator();
		toolsMenu.add(createMenuItem(editor.getSaveAsTemplateAction(), KeyPreferences.SAVE_AS_TEMPLATE_ACTION));
		toolsMenu.add(createMenuItem(editor.getManageTemplateAction(), KeyPreferences.MANAGE_TEMPLATE_ACTION));
		toolsMenu.addSeparator();
		toolsMenu.add(createMenuItem(editor.getSelectFragmentAction(), KeyPreferences.SELECT_FRAGMENT_ACTION));
		toolsMenu.add(createMenuItem(editor.getToggleBookmarkAction(), KeyPreferences.TOGGLE_BOOKMARK_ACTION));
		toolsMenu.add(createMenuItem(editor.getSelectBookmarkAction(), KeyPreferences.SELECT_BOOKMARK_ACTION));

		GUIUtilities.alignMenu(toolsMenu);
		return toolsMenu;
	}

	private JMenu createRefactorMenu() {
		JMenu refactorMenu = new JMenu("Refactor");
		refactorMenu.setMnemonic('R');

		// One-time: the caret-based "Extract to Conref" editor popup item
		com.dogsbay.dogsbayaieditor.refactor.RefactorUi.installEditorPopupContributor(editor);

		JMenuItem renameFileItem = new JMenuItem("Rename/Move File with References...", 'F');
		renameFileItem.addActionListener(e -> {
			com.dogsbay.dogsbayaieditor.refactor.RefactorUi ui =
					new com.dogsbay.dogsbayaieditor.refactor.RefactorUi(editor);
			ui.renameFileWithReferences(ui.activeFileOrNull(), null);
		});
		refactorMenu.add(renameFileItem);

		JMenuItem retargetItem = new JMenuItem("Retarget References...", 'T');
		retargetItem.addActionListener(e -> {
			com.dogsbay.dogsbayaieditor.refactor.RefactorUi ui =
					new com.dogsbay.dogsbayaieditor.refactor.RefactorUi(editor);
			ui.retarget(ui.activeFileOrNull(), null);
		});
		refactorMenu.add(retargetItem);

		JMenuItem renameKeyItem = new JMenuItem("Rename Key...", 'K');
		renameKeyItem.addActionListener(e ->
				new com.dogsbay.dogsbayaieditor.refactor.RefactorUi(editor).renameKey(null, null));
		refactorMenu.add(renameKeyItem);

		JMenuItem splitTopicItem = new JMenuItem("Split Topic by Sections...", 'O');
		splitTopicItem.addActionListener(e ->
				new com.dogsbay.dogsbayaieditor.refactor.RefactorUi(editor).splitTopic());
		refactorMenu.add(splitTopicItem);

		JMenuItem extractConrefItem = new JMenuItem("Extract Element to Conref...", 'E');
		extractConrefItem.addActionListener(e ->
				new com.dogsbay.dogsbayaieditor.refactor.RefactorUi(editor).extractConref());
		refactorMenu.add(extractConrefItem);

		JMenuItem createKeyItem = new JMenuItem("Create Key from Selected Text...", 'S');
		createKeyItem.addActionListener(e -> {
			com.dogsbay.dogsbayaieditor.refactor.RefactorUi ui =
					new com.dogsbay.dogsbayaieditor.refactor.RefactorUi(editor);
			ui.createKeyFromSelection(ui.activeEditorPaneOrNull());
		});
		refactorMenu.add(createKeyItem);

		JMenuItem renameIdItem = new JMenuItem("Rename Element Id...", 'I');
		renameIdItem.addActionListener(e ->
				new com.dogsbay.dogsbayaieditor.refactor.RefactorUi(editor).renameElementId());
		refactorMenu.add(renameIdItem);

		JMenuItem mergeKeydefsItem = new JMenuItem("Merge Duplicate Keydefs...", 'M');
		mergeKeydefsItem.addActionListener(e ->
				new com.dogsbay.dogsbayaieditor.refactor.RefactorUi(editor).mergeKeydefs());
		refactorMenu.add(mergeKeydefsItem);

		JMenuItem profileValueItem = new JMenuItem("Rename Profiling Value...", 'P');
		profileValueItem.addActionListener(e ->
				new com.dogsbay.dogsbayaieditor.refactor.RefactorUi(editor).renameProfileValue());
		refactorMenu.add(profileValueItem);

		refactorMenu.addSeparator();

		JMenuItem healthItem = new JMenuItem("Project Health Report...", 'H');
		healthItem.addActionListener(e ->
				new com.dogsbay.dogsbayaieditor.refactor.RefactorUi(editor).showHealthReport());
		refactorMenu.add(healthItem);

		GUIUtilities.alignMenu(refactorMenu);
		return refactorMenu;
	}

	private JMenu createGrammarMenu() {
		JMenu grammarMenu = new JMenu("Types");
		grammarMenu.setMnemonic('Y');

		grammarMenu.add(createMenuItem(editor.getNewGrammarAction(), KeyPreferences.CREATE_TYPE_ACTION));
		grammarMenu.add(createMenuItem(editor.getOpenGrammarAction(), KeyPreferences.SET_TYPE_ACTION));
		grammarMenu.add(createMenuItem(editor.getGrammarPropertiesAction(), KeyPreferences.TYPE_PROPERTIES_ACTION));
		grammarMenu.add(createMenuItem(editor.getManageGrammarAction(), KeyPreferences.MANAGE_TYPES_ACTION));

		GUIUtilities.alignMenu(grammarMenu);
		return grammarMenu;
	}

	private JMenu createScenarioMenu() {
		JMenu scenarioMenu = new JMenu("Transform");
		scenarioMenu.setMnemonic('T');

		scenarioMenu.add(createMenuItem(editor.getExecuteSimpleXSLTAction(), KeyPreferences.EXECUTE_SIMPLE_XSLT_ACTION));
		scenarioMenu.add(createMenuItem(editor.getExecuteAdvancedXSLTAction(), KeyPreferences.EXECUTE_ADVANCED_XSLT_ACTION));
		scenarioMenu.add(createMenuItem(editor.getExecuteFOAction(), KeyPreferences.EXECUTE_FO_ACTION));
		// PHASE 3: Disabled XQuery during Saxon upgrade

		scenarioMenu.addSeparator();

		scenarioMenu.add(createMenuItem(editor.getDefaultScenarioAction(), KeyPreferences.EXECUTE_SCENARIO_ACTION));

		scenarioMenu.addSeparator();
		scenarioMenu.add(createMenuItem(editor.getManageScenarioAction(), KeyPreferences.MANAGE_SCENARIOS_ACTION));

		JMenu executePreviousMenu = new JMenu("Execute Previous");
		executePreviousMenu.setMnemonic('P');

		executePreviousMenu
				.add(createMenuItem(editor.getExecutePreviousXSLTAction(), KeyPreferences.EXECUTE_PREVIOUS_XSLT_ACTION));
		executePreviousMenu
				.add(createMenuItem(editor.getExecutePreviousFOAction(), KeyPreferences.EXECUTE_PREVIOUS_FO_ACTION));
		executePreviousMenu.add(
				createMenuItem(editor.getExecutePreviousScenarioAction(), KeyPreferences.EXECUTE_PREVIOUS_SCENARIO_ACTION));

		GUIUtilities.alignMenu(executePreviousMenu);
		scenarioMenu.add(executePreviousMenu);

		GUIUtilities.alignMenu(scenarioMenu);

		return scenarioMenu;
	}
}
