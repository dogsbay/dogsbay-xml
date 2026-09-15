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
import java.awt.Graphics2D;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.Timer;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import org.bounce.CenterLayout;
import org.bounce.util.BrowserLauncher;
import org.dom4j.Node;
import org.xml.sax.SAXParseException;

import com.apple.eawt.Application;
import com.apple.eawt.ApplicationEvent;
import com.apple.eawt.ApplicationListener;
import com.dogsbay.schema.SchemaDocument;
import com.dogsbay.schema.SchemaDocument;
import com.dogsbay.util.loader.ExtensionClassLoader;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.DogsBayDocumentEvent;
import com.dogsbay.xml.DogsBayDocumentListener;
import com.dogsbay.xml.DogsBayXMLWriter;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.DogsBayURLUtilities;
//import com.dogsbay.xml.browser.Browser;
import com.dogsbay.dogsbayaieditor.plugins.PluginViewPanel;
import com.dogsbay.xml.editor.Bookmark;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.xml.helper.Helper;
import com.dogsbay.xml.navigator.Navigator;
import com.dogsbay.xml.viewer.Viewer;
import com.dogsbay.dogsbayaieditor.actions.*;

import com.dogsbay.dogsbayaieditor.component.AutomaticProgressMonitor;
import com.dogsbay.dogsbayaieditor.component.GUIUtilities;
import com.dogsbay.dogsbayaieditor.grammar.FragmentProperties;
import com.dogsbay.dogsbayaieditor.grammar.GrammarProperties;
import com.dogsbay.dogsbayaieditor.plugins.PluginActionKeyMapping;
import com.dogsbay.dogsbayaieditor.plugins.PluginUtilities;
import com.dogsbay.dogsbayaieditor.plugins.PluginView;
import com.dogsbay.dogsbayaieditor.framework.FrameworkImportDialog;
import com.dogsbay.dogsbayaieditor.framework.FrameworkManagementDialog;
import com.dogsbay.dogsbayaieditor.framework.FrameworkProperties;
import com.dogsbay.dogsbayaieditor.project.Project;
import com.dogsbay.dogsbayaieditor.project.ProjectProperties;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.dogsbay.dogsbayaieditor.properties.KeyPreferences;
import com.dogsbay.dogsbayaieditor.properties.TextPreferences;
// PHASE 3: Disabled XSLT Debugger during Saxon upgrade
// import com.dogsbay.xslt.debugger.ui.XSLTDebuggerFrame;
import com.dogsbay.dogsbayaieditor.actions.ChangeDocumentAction;
import com.dogsbay.dogsbayaieditor.actions.SubstituteCharactersAction;
import com.dogsbay.dogsbayaieditor.services.ActionRegistry;
import com.dogsbay.dogsbayaieditor.services.DocumentManager;
import com.dogsbay.dogsbayaieditor.services.EventBus;
import com.dogsbay.dogsbayaieditor.services.MenuBuilder;
import com.dogsbay.dogsbayaieditor.services.SchemaManager;
import com.dogsbay.dogsbayaieditor.services.ToolbarManager;
import com.dogsbay.dogsbayaieditor.services.ViewManager;

/**
 * The desktop frame, displays the desktop services buttons and
 * allows for adding, removing and opening services that are
 * placed on the desktop.
 *
 * @version $Revision: 1.153 $, $Date: 2005/09/06 08:22:29 $
 * @author Dogsbay
 */
public class DogsBayAIEditor extends StatusFrame implements DogsBayDocumentListener {
	private static final boolean DEBUG = false;

	private static final CompoundBorder TITLE_BORDER = new CompoundBorder(
			new CompoundBorder(
					new MatteBorder(1, 1, 0, 0, UIManager.getColor("controlDkShadow")),
					new MatteBorder(0, 0, 0, 1, UIManager.getColor("controlHighlight"))),
			new CompoundBorder(
					new MatteBorder(1, 1, 0, 0, UIManager.getColor("controlHighlight")),
					new MatteBorder(0, 0, 0, 1, UIManager.getColor("controlDkShadow"))));

	private static final String TITLE = "DogsBay XML";

	/**
	 * The application icon — the same paw as the website and the blog. Several
	 * sizes, because a window manager picks one for the title bar, another for
	 * the task switcher, and a third for the dock, and scaling one bitmap for
	 * all three loses the gaps between the toes.
	 */
	private static final String[] ICONS = {
			"com/dogsbay/dogsbayaieditor/icons/app/dogsbay-paw-16.png",
			"com/dogsbay/dogsbayaieditor/icons/app/dogsbay-paw-24.png",
			"com/dogsbay/dogsbayaieditor/icons/app/dogsbay-paw-32.png",
			"com/dogsbay/dogsbayaieditor/icons/app/dogsbay-paw-48.png",
			"com/dogsbay/dogsbayaieditor/icons/app/dogsbay-paw-64.png",
			"com/dogsbay/dogsbayaieditor/icons/app/dogsbay-paw-128.png",
			"com/dogsbay/dogsbayaieditor/icons/app/dogsbay-paw-256.png",
			"com/dogsbay/dogsbayaieditor/icons/app/dogsbay-paw-512.png" };
	private static final String CLOSE_ICON = "com/dogsbay/dogsbayaieditor/icons/Close8.gif";

	private static final String PROJECT_ICON = "com/dogsbay/dogsbayaieditor/project/icons/ProjectIcon.gif";
	private static final String NAVIGATOR_ICON = "com/dogsbay/dogsbayaieditor/icons/sidebar/symbol-file.png";
	private static final String HELPER_ICON = "com/dogsbay/dogsbayaieditor/icons/sidebar/lightbulb.png";
	private static final String FILE_EXPLORER_ICON = "com/dogsbay/dogsbayaieditor/icons/sidebar/files.png";
	private static final String GIT_ICON = "com/dogsbay/dogsbayaieditor/icons/sidebar/source-control.png";
	private static final String SEARCH_ICON = "com/dogsbay/dogsbayaieditor/icons/sidebar/search.png";
	private static final String DITA_EXPLORER_ICON = "com/dogsbay/dogsbayaieditor/icons/sidebar/list-tree.png";
	private static final String BOOKMARK_ICON = "com/dogsbay/dogsbayaieditor/icons/sidebar/bookmark.png";
	private static final String XPATH_QUERY_ICON = "com/dogsbay/dogsbayaieditor/icons/sidebar/search-sparkle.png";
	private static final String PROPERTIES_ICON = "com/dogsbay/dogsbayaieditor/icons/sidebar/settings-gear.png";

	private static final String SYNCHRONISE_SPLITS_ICON = "com/dogsbay/dogsbayaieditor/icons/SynchroniseSplits16.gif";
	private static final String LAYOUT_SIDEBAR_LEFT_ICON = "com/dogsbay/dogsbayaieditor/icons/sidebar/layout-sidebar-left.png";
	private static final String LAYOUT_PANEL_ICON = "com/dogsbay/dogsbayaieditor/icons/sidebar/layout-panel.png";
	private static final String LAYOUT_SIDEBAR_RIGHT_ICON = "com/dogsbay/dogsbayaieditor/icons/sidebar/layout-sidebar-right.png";

	private AboutDialog aboutDialog = null;
	private Component tabbedViewParent = null;
	private JPanel fullScreenPanel = null;

	// private RootSelectionDialog rootDialog = null;

	private ConfigurationProperties properties = null;
	// PHASE 3: Disabled XSLT Debugger during Saxon upgrade
	// private XSLTDebuggerFrame debugger = null;
	// private Properties Properties = null;

	private Hashtable icons = null;

	private JPanel mainPanel = null;
	// private SubstitutionList substitutionList = null;

	private JPanel buttonContainer = null;

	/**
	 * this is the panel that holds the xpath editor and view buttons
	 */
	private JPanel northPanel = null;

	private ExplorerContainer explorerContainer = null;
	private JPopupMenu tabPopup = null;
	private DogsBayDocument document = null;

	private PropertiesPanel propertiesPanel = null;
	private Project projectPanel = null;
	private Helper helper = null;
	private Navigator navigator = null;
	// outlinePanel is now managed by OutlinePlugin
	private com.dogsbay.dogsbayaieditor.explorer.FileExplorerPanel fileExplorer = null;
	// gitPanel is now managed by GitPlugin
	private Timer fileChangeTimer = null;
	private static final int FILE_CHANGE_CHECK_INTERVAL = 2000;
	// searchPanel is now managed by SearchPlugin
	private ProjectSwitcher projectSwitcher = null;
	private java.io.File pendingProjectFolder = null; // deferred until setVisible
	private boolean pendingMinimizePrimary = false;
	private boolean pendingMinimizeSecondary = false;
	// ditaExplorer is now managed by DitaPlugin
	// bookmarkExplorer is now managed by BookmarksPlugin
	// xpathQueryPanel is now managed by XPathQueryPlugin
	private Statusbar statusbar = null;
	private OutputPanel outputPanel = null;
	private JSplitPane rightSplit = null;
	private JSplitPane tabSplit = null;
	private JSplitPane split = null;
	private JSplitPane mainContentSplit = null;
	private ExplorerContainer rightExplorerContainer = null;
	private JToggleButton togglePrimarySidebar = null;
	private JToggleButton toggleBottomPanel = null;
	private JToggleButton toggleSecondarySidebar = null;
	private int savedBottomPanelDivider = -1;
	private JPanel centerPanel = null;
	private JPanel splitPanel = null;
	private JPanel editorPanel = null;

	private JButton closeButton = null;


	private static URL macFile = null;
	private static boolean started = false;

	private int modeSelected = 0;

	private ToolbarManager toolbarManager;

	private JPanel documentViewButtonPanel = null;
	private ButtonGroup documentViewButtonGroup = null;

	// declare the cut copy paste menuitems so they can be used in grid menu as well
	JMenuItem cutMenuItem = null;
	JMenuItem copyMenuItem = null;
	JMenuItem pasteMenuItem = null;

	private List pluginViews = null;

	private EventBus eventBus;
	private com.dogsbay.dogsbayaieditor.services.DeliverableService deliverableService;
	private ActionRegistry actionRegistry;
	private DocumentManager documentManager;
	private ViewManager viewManager;
	private com.dogsbay.dogsbayaieditor.plugin.PluginManager pluginManager;
	private SchemaManager schemaManager;
	private com.dogsbay.dogsbayaieditor.commands.CommandRegistry commandRegistry;
	private com.dogsbay.dogsbayaieditor.commands.SessionExecutor commandExecutor;
	private com.dogsbay.agent.session.AgentSessionRegistry sessionRegistry;
	private com.dogsbay.agent.session.AuditLog auditLog;
	private com.dogsbay.dogsbayaieditor.commands.WriteGate writeGate;
	private com.dogsbay.dogsbayaieditor.ipc.IpcServerManager ipcServerManager;

	// tip of the day
	private LatestNewsModel tipsModel = null;
	private LatestNewsDialog tipOfTheDayDialog = null;

	private static ExtensionClassLoader extensionClassLoader = null;

	/**
	 * the views menu on the view menu, used to show all the different views
	 * of a document
	 */
	private MenuBuilder menuBuilder;

	public DogsBayAIEditor(ExtensionClassLoader loader, ConfigurationProperties properties) {

		this.setExtensionClassLoader(loader);

		setTitle(TITLE);
		setIconImages(appIcons());

		mainPanel = new JPanel(new BorderLayout());

		setContentPane(mainPanel);

		this.setProperties(properties);

		// Install format-on-save: canonicalizes the bytes written to disk with the
		// project house style (no-op unless the project/user enables it).
		com.dogsbay.dogsbayaieditor.format.FormatOnSaveHook.install();

		// Wire the user's default house style as the resolver fallback (project config
		// still wins), and seed the user format-on-save preference.
		com.dogsbay.dogsbayaieditor.project.FormatStyleResolver.setUserDefaultSupplier(
				() -> properties.getEditorProperties().getFormatStyle());
		com.dogsbay.dogsbayaieditor.format.FormatOnSaveHook.setUserEnabled(
				properties.getEditorProperties().isFormatOnSave());

		this.eventBus = new EventBus();
		this.deliverableService =
				new com.dogsbay.dogsbayaieditor.services.DeliverableService(eventBus);
		// Persist the active deliverable (a pointer) into the current project's
		// properties whenever it changes.
		this.eventBus.subscribe(
				com.dogsbay.dogsbayaieditor.services.events.DeliverableChangedEvent.class,
				e -> persistActiveDeliverable(e.deliverable()));
		this.actionRegistry = new ActionRegistry(this);
		this.menuBuilder = new MenuBuilder(this);
		this.documentManager = new DocumentManager(this);
		this.viewManager = new ViewManager(this);
		this.schemaManager = new SchemaManager(this);
		this.toolbarManager = new ToolbarManager(this);
		this.commandRegistry = new com.dogsbay.dogsbayaieditor.commands.CommandRegistry();
		// Who is calling the command engine (the user, the built-in agent, hosted
		// and external agents) and what the agents did. Must exist before the
		// executor, which binds a session around every command.
		this.sessionRegistry = new com.dogsbay.agent.session.AgentSessionRegistry();
		this.auditLog = new com.dogsbay.agent.session.AuditLog(this::projectRootPath,
				java.time.Clock.systemUTC(),
				e -> System.err.println("[audit] could not write agent audit entry: " + e.getMessage()));
		// The raw executor runs commands; the session executor around it binds
		// the caller, applies the write gate to agents and audits what they did.
		var rawExecutor = new com.dogsbay.dogsbayaieditor.commands.EditorExecutor(this);
		this.writeGate = new com.dogsbay.dogsbayaieditor.commands.WriteGate(
				new com.dogsbay.agent.session.WriteLease(), this::projectRootPath,
				new com.dogsbay.dogsbayaieditor.commands.WriteGate.Documents() {
					@Override
					public java.nio.file.Path activeDocument() {
						try {
							for (var d : rawExecutor.execute(
									new com.dogsbay.dogsbayaieditor.commands.ListDocumentsCommand())) {
								if (d.active()) {
									return d.file();
								}
							}
						} catch (com.dogsbay.dogsbayaieditor.commands.CommandException ignore) {
							// no documents
						}
						return null;
					}

					@Override
					public String content(java.nio.file.Path file)
							throws com.dogsbay.dogsbayaieditor.commands.CommandException {
						try {
							return rawExecutor.execute(
									new com.dogsbay.dogsbayaieditor.commands.GetContentCommand(file));
						} catch (com.dogsbay.dogsbayaieditor.commands.CommandException e) {
							if (e.getCode() == com.dogsbay.dogsbayaieditor.commands.CommandException.ErrorCode.DOCUMENT_NOT_OPEN
									|| e.getCode() == com.dogsbay.dogsbayaieditor.commands.CommandException.ErrorCode.FILE_NOT_FOUND) {
								return null;
							}
							throw e;
						}
					}
				});
		this.sessionRegistry.addListener(e -> {
			if (e instanceof com.dogsbay.agent.session.SessionEvent.Closed c) {
				writeGate.sessionClosed(c.session());
			}
		});
		this.commandExecutor = new com.dogsbay.dogsbayaieditor.commands.SessionExecutor(
				rawExecutor, sessionRegistry, writeGate, auditLog);

		// Integration server (CLI/MCP/REST) — opt-in, off by default. The manager
		// reads config fresh on each (re)start so Preferences changes apply at
		// runtime; the /mcp and /rpc endpoints are gated by their own toggles.
		this.ipcServerManager = new com.dogsbay.dogsbayaieditor.ipc.IpcServerManager(
				() -> getProperties().isMcpServerEnabled(),
				() -> getProperties().getMcpServerPort(),
				this::buildIpcEndpoints);
		this.ipcServerManager.setSessionResolver(sessionRegistry::byToken);
		this.ipcServerManager.start();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> ipcServerManager.stop()));

		this.setPluginViews(PluginUtilities.loadPlugins());
		for (int cnt = 0; cnt < this.getPluginViews().size(); ++cnt) {
			Object obj = this.getPluginViews().get(cnt);
			if ((obj != null) && (obj instanceof PluginView)) {
				PluginView pluginView = (PluginView) obj;
				if (pluginView != null) {

					pluginView.setDogsBayAIEditor(this);
					pluginView.loadActions();
				}
			}
		}

		statusbar = new Statusbar(this);
		mainPanel.add(statusbar, BorderLayout.SOUTH);


		DogsBayTabbedView startTabbedView = new DogsBayTabbedView(this, null);
		startTabbedView.setScrollTabs(this.getProperties().isScrollDocumentTabs());
		Vector tabbedViews = new Vector();
		tabbedViews.addElement(startTabbedView);
		viewManager.setTabbedViews(tabbedViews);

		outputPanel = new OutputPanel(this, properties);

		setProjectPanel(new Project(this, properties));
		// projectPanel.setBorder(new EmptyBorder(2, 2, 1, 1));

		navigator = new Navigator(this, properties.getNavigatorProperties());
		// navigator.setBorder(new EmptyBorder(0, 2, 1, 1));

		// OutlinePanel is now created by OutlinePlugin

		helper = new Helper(this, properties.getHelperProperties());
		// helper.setBorder(new EmptyBorder(2, 2, 1, 1));

		fileExplorer = new com.dogsbay.dogsbayaieditor.explorer.FileExplorerPanel(this);



		// Create ExplorerContainer with vertical buttons (VSCode-style) - LEFT SIDE
		explorerContainer = new ExplorerContainer();
		explorerContainer.setOrientation(VerticalButtonBar.Orientation.LEFT);

		// Add change listener to handle explorer selection changes
		explorerContainer.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent event) {
				Component comp = explorerContainer.getSelectedComponent();
				getProjectPanel().setActionsEnabled(comp == getProjectPanel());
			}
		});

		// File Explorer stays in core — always needed
		explorerContainer.addExplorer("fileExplorer", getSidebarIcon(FILE_EXPLORER_ICON), "Explorer", fileExplorer);
		// Git, Search, DITA, Bookmarks, XPath Query are added by their respective plugins

		// Settings gear at bottom of sidebar
		explorerContainer.addBottomButton(getSidebarIcon(PROPERTIES_ICON), "Settings", e -> {
			getPreferencesAction().execute();
		});

		// Git icon badge is now refreshed by GitPlugin on activation

		// Add properties panel - moved to Right Explorer
		propertiesPanel = new PropertiesPanel(properties, this);
		// explorerContainer.setPropertiesPanel(propertiesPanel);

		projectSwitcher = new ProjectSwitcher(this, properties);
		// The project selector lives in the status bar (leading segment), alongside
		// the git branch and DITA deliverable — one consistent project-context strip.
		statusbar.setProjectSwitcher(projectSwitcher);

		// Load and open the last project if available. Match by folder path first
		// (unique) and fall back to project name — two projects can share a basename
		// (the default project name), so a name-only match could restore the wrong one.
		Vector<ProjectProperties> projects = properties.getProjectProperties();
		String lastFolder = properties.getLastOpenedFolder();
		String lastProjectName = properties.getLastProjectName();
		ProjectProperties lastProject = null;
		if (lastFolder != null && !lastFolder.isEmpty()) {
			for (ProjectProperties project : projects) {
				if (lastFolder.equals(project.getFolderPath())) {
					lastProject = project;
					break;
				}
			}
		}
		if (lastProject == null && lastProjectName != null && !lastProjectName.isEmpty()) {
			for (ProjectProperties project : projects) {
				if (lastProjectName.equals(project.getName())) {
					lastProject = project;
					break;
				}
			}
		}
		if (lastProject != null) {
			String folderPath = lastProject.getFolderPath();
			if (folderPath != null && !folderPath.isEmpty()) {
				java.io.File projectFolder = new java.io.File(folderPath);
				if (projectFolder.exists() && projectFolder.isDirectory()) {
					// Store for deferred init in setVisible()
					pendingProjectFolder = projectFolder;
				}
			}
			// Update the switcher to show the last project
			projectSwitcher.setCurrentProject(lastProject);
		}

		// ExplorerContainer handles its own border, so use it directly
		JPanel leftControllerTabPanel = explorerContainer;

		// Create ExplorerContainer for RIGHT SIDE
		rightExplorerContainer = new ExplorerContainer();
		rightExplorerContainer.setOrientation(VerticalButtonBar.Orientation.RIGHT);

		// Add change listener for right side
		rightExplorerContainer.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent event) {
				Component comp = rightExplorerContainer.getSelectedComponent();

				if (comp == navigator) {
					navigator.updateOutline();
				} else if (comp == helper) {
					helper.updateInformation();
				}
			}
		});

		// Add explorers to container - RIGHT SIDE
		// Properties Panel at the top
		rightExplorerContainer.addExplorer("properties", getSidebarIcon(PROPERTIES_ICON), "Properties", propertiesPanel);
		rightExplorerContainer.addExplorer("navigator", getSidebarIcon(NAVIGATOR_ICON), "Navigator", navigator);
		// Outline is now added by OutlinePlugin via UIService.addSidebarPanel()
		rightExplorerContainer.addExplorer("helper", getSidebarIcon(HELPER_ICON), "Helper", helper);

		toolbarManager.initEditorToolbar();

		editorPanel = new JPanel(new BorderLayout());
		editorPanel.add(startTabbedView, BorderLayout.CENTER);
		editorPanel.add(toolbarManager.getEditorToolbarPanel(), BorderLayout.NORTH);

		// Editor + Output Split (Vertical)
		rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorPanel, outputPanel);
		rightSplit.setResizeWeight(1);

		if (rightSplit.getDividerSize() > 6) {
			rightSplit.setDividerSize(6);
		}

		Object ui = rightSplit.getUI();
		if (ui instanceof BasicSplitPaneUI) {
			((BasicSplitPaneUI) ui).getDivider().setBorder(null);
		}
		rightSplit.setBorder(null);
		rightSplit.setDividerLocation(properties.getDividerLocation());
		rightSplit.setOneTouchExpandable(true);

		// Editor/Output + Right Explorer Split (Horizontal)
		mainContentSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, rightSplit, rightExplorerContainer);
		mainContentSplit.setResizeWeight(1); // Give extra space to editor

		if (mainContentSplit.getDividerSize() > 6) {
			mainContentSplit.setDividerSize(6);
		}
		mainContentSplit.setBorder(null);
		ui = mainContentSplit.getUI();
		if (ui instanceof BasicSplitPaneUI) {
			((BasicSplitPaneUI) ui).getDivider().setBorder(null);
		}
		// The right panel is sized once the split has a real width. A divider
		// position set before the first layout pass is discarded, which is why
		// the agent panel used to open as an unreadable sliver.
		mainContentSplit.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent event) {
				sizeRightPanelOnce();
			}
		});
		mainContentSplit.setOneTouchExpandable(true);

		// Left Explorer + Main Content Split (Horizontal)
		split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftControllerTabPanel, mainContentSplit);

		if (split.getDividerSize() > 6) {
			split.setDividerSize(6);
		}
		split.setBorder(null);
		ui = split.getUI();
		if (ui instanceof BasicSplitPaneUI) {
			((BasicSplitPaneUI) ui).getDivider().setBorder(null);
		}
		split.setDividerLocation(restoredSidebarDivider());
		split.setOneTouchExpandable(true);

		splitPanel = new JPanel(new BorderLayout());
		splitPanel.add(split, BorderLayout.CENTER);

		centerPanel = new JPanel(new BorderLayout());
		centerPanel.add(splitPanel, BorderLayout.CENTER);

		mainPanel.add(centerPanel, BorderLayout.CENTER);

		// Sidebar minimize on startup is disabled — the split pane doesn't
		// reliably respect divider changes before the window is fully laid out.
		// Users can minimize manually after startup.
		if (!properties.isBottomPanelVisible()) {
			outputPanel.setVisible(false);
			rightSplit.setDividerSize(0);
		}

		setNorthPanel(new JPanel(new BorderLayout()));
		northPanel.setBorder(new EmptyBorder(2, 0, 0, 0));


		setButtonContainer(new JPanel(new BorderLayout()));
		setDocumentViewButtonPanel(new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0)));
		getDocumentViewButtonPanel().setBorder(new EmptyBorder(0, 0, 2, 0));
		getButtonContainer().add(documentViewButtonPanel, BorderLayout.SOUTH);

		documentViewButtonGroup = new ButtonGroup();

		JPanel middlePanel = new JPanel(new CenterLayout(CenterLayout.VERTICAL));
		middlePanel.add(getButtonContainer(), CenterLayout.CENTER);
		northPanel.add(middlePanel, BorderLayout.EAST);

		menuBuilder.createMenubar();

		// put the keys on the menus: the product's defaults, with whatever the
		// reader has changed
		properties.getKeyBindings().applyTo(this);

		toolbarManager.initMainToolbar();
		centerPanel.add(toolbarManager.getToolbarPanel(), BorderLayout.NORTH);

		// set the size of the buttons.
		int height = toolbarManager.getToolbar().getComponentAtIndex(0).getPreferredSize().height;

		fullScreenPanel = new JPanel(new BorderLayout());

		updateGrammarActions();
		// updateScenarioActions();

		if (properties.isWindowMaximised() == true) {

			this.setExtendedState(this.getExtendedState() | MAXIMIZED_BOTH);
		} else {
			// set it to a specific size
			setSize(properties.getDimension());
		}

		setLocation(properties.getPosition());

		if (System.getProperty("mrj.version") != null) {
			// System.out.println( "Apple Mac found");
			new MacOSApplicationAdapter(this);
		}

		this.addWindowStateListener(new WindowStateListener() {

			/*
			 * NORMAL
			 * ICONIFIED
			 * MAXIMIZED_HORIZ
			 * MAXIMIZED_VERT
			 * MAXIMIZED_BOTH
			 * MAXIMIZED_HORIZ
			 * MAXIMIZED_VERT
			 */

			public void windowStateChanged(WindowEvent e) {
				boolean DEBUG = false;

				int oldState = e.getOldState();
				int newState = e.getNewState();

				if ((oldState & Frame.ICONIFIED) == 0
						&& (newState & Frame.ICONIFIED) != 0) {
					if (DEBUG)
						System.out.println("Frame was iconized");
				} else if ((oldState & Frame.ICONIFIED) != 0
						&& (newState & Frame.ICONIFIED) == 0) {
					if (DEBUG)
						System.out.println("Frame was deiconized");
				}

				if ((oldState & Frame.MAXIMIZED_BOTH) == 0
						&& (newState & Frame.MAXIMIZED_BOTH) != 0) {
					if (DEBUG)
						System.out.println("Frame was maximized");
					DogsBayAIEditor.this.properties.setWindowMaximised(true);

				} else if ((oldState & Frame.MAXIMIZED_BOTH) != 0
						&& (newState & Frame.MAXIMIZED_BOTH) == 0) {
					if (DEBUG)
						System.out.println("Frame was minimized");
					setSize(DogsBayAIEditor.this.properties.getDimension());
				}

			}

		});

		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				exit();
			}

		});

		// Check all open documents for external changes when the window regains focus.
		// Clean buffers are silently reloaded; dirty buffers are deferred to tab focus.
		addWindowFocusListener(new WindowAdapter() {
			public void windowGainedFocus(WindowEvent e) {
				checkAllDocumentsForExternalChanges();
			}

			public void windowLostFocus(WindowEvent e) {
				dismissTransientPopups();
			}
		});

		// Dialogs are separate windows whose own focus events the frame never sees,
		// so also dismiss popups whenever focus leaves every app window (e.g. the
		// user tabs to another application) — covers open combos in modal dialogs.
		java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
				.addPropertyChangeListener("activeWindow", evt -> {
					if (evt.getNewValue() == null) {
						dismissTransientPopups();
					}
				});

		// Periodic timer to check the active document for external changes.
		// Handles cases where window/component focus events don't fire reliably.
		fileChangeTimer = new Timer(FILE_CHANGE_CHECK_INTERVAL, e -> {
			DogsBayView activeView = getView();
			if (activeView != null) {
				activeView.checkExternalModification();
			}
		});
		fileChangeTimer.setRepeats(true);
		fileChangeTimer.start();

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

		DogsBayXMLWriter.setAttributeOnNewLine(
				properties.isAttributesNewLine());

		if (properties.getEditorProperties().isWrapText()) {
			DogsBayXMLWriter.setMaxLineLength(
					properties.getEditorProperties().getWrappingColumn());
		} else {
			DogsBayXMLWriter.setMaxLineLength(-1);
		}

		DogsBayXMLWriter.setIndentString(TextPreferences.getTabString());

		startTabbedView.setSelected(true);

		// Initialize and activate new-style plugins
		this.pluginManager = new com.dogsbay.dogsbayaieditor.plugin.PluginManager(this);
		pluginManager.loadState(getProperties());
		pluginManager.loadPlugins();
		pluginManager.loadPlugins(new File("plugins"));
		pluginManager.activateAll();

		// Run in Thread!!!
		Runnable runner = new Runnable() {
			public void run() {
				try {
					// On first launch, the non-modal Welcome tab replaces the
					// Tip-of-the-Day modal (the checkbox on the tab persists the choice).
					if (DogsBayAIEditor.this.getProperties().isShowWelcomeOnStartup()) {
						SwingUtilities.invokeLater(DogsBayAIEditor.this::showWelcomeTab);
						return;
					}

					// tip of the day

					LatestNewsModel latestNewsModel = new LatestNewsModel();

					DogsBayAIEditor.this.setTipsModel(latestNewsModel);

					DogsBayAIEditor.this.tipOfTheDayDialog = new LatestNewsDialog(DogsBayAIEditor.this, true,
							DogsBayAIEditor.this.getTipsModel());

					if (DogsBayAIEditor.this.getTipsModel() != null) {
						if (DogsBayAIEditor.this.tipOfTheDayDialog != null) {
							// tipOfTheDayDialog.setCurrentTip(0);
							if (DogsBayAIEditor.this.tipsModel.getTipCount() > 0) {
								DogsBayAIEditor.this.tipOfTheDayDialog.showDialog();
							}
						} else {
							// System.err.println("DogsBayAIEditor::setVisible - tipOftheDayDialog is
							// null");
						}
					} else {
						// System.err.println("DogsBayAIEditor::setVisible - tipModel is null");
					}

				} catch (Exception e) {
					// This should never happen, just report and continue
					// MessageHandler.showUnexpectedError( e);
					e.printStackTrace();
				} finally {

				}
			}
		};

		// Create and start the thread ...
		Thread thread = new Thread(runner);
		thread.start();

		// Check for updates after a short delay
		scheduleUpdateCheck();

	}

	/**
	 * Schedules a background update check 5 seconds after startup.
	 */
	private void scheduleUpdateCheck() {
		// Honor the user's opt-out — the check makes an outbound network call.
		if (!getProperties().isUpdateCheckEnabled()) {
			return;
		}
		javax.swing.Timer timer = new javax.swing.Timer(5000, e -> {
			String currentVersion = getClass().getPackage() != null
				? getClass().getPackage().getImplementationVersion() : null;
			if (currentVersion == null) {
				currentVersion = com.dogsbay.dogsbayaieditor.Identity.getIdentity().getVersion();
			}
			com.dogsbay.dogsbayaieditor.update.UpdateChecker.checkAsync(currentVersion, (newVersion, releaseUrl) -> {
				javax.swing.SwingUtilities.invokeLater(() -> {
					com.dogsbay.dogsbayaieditor.update.UpdateNotificationPanel panel =
						new com.dogsbay.dogsbayaieditor.update.UpdateNotificationPanel(newVersion, releaseUrl);
					// Add notification at the top of the editor
					java.awt.Container content = getLayeredPane().getParent();
					if (content instanceof javax.swing.JRootPane) {
						javax.swing.JPanel wrapper = new javax.swing.JPanel(new java.awt.BorderLayout());
						java.awt.Component existing = ((javax.swing.JRootPane) content).getContentPane();
						((javax.swing.JRootPane) content).setContentPane(wrapper);
						wrapper.add(panel, java.awt.BorderLayout.NORTH);
						wrapper.add(existing, java.awt.BorderLayout.CENTER);
						wrapper.revalidate();
					}
				});
			});
		});
		timer.setRepeats(false);
		timer.start();
	}

	public void hideDocumentViewButton() {
		if ((this.getDocumentViewButtonGroup() != null) && (this.getDocumentViewButtonPanel() != null)) {

		}
	}

	/**
	 * turns the emacs editing mode on\off depending on condition boolean
	 *
	 * @param condition boolean which decides whether to turn emacs editing mode
	 *                  on\off
	 */
	/**
	 * Sets whether the editor is currently restoring session files.
	 * When true, per-file tab change listener and explorer updates are suppressed.
	 */
	public boolean isRestoringSession() {
		return restoringSession;
	}

	public void setRestoringSession(boolean restoring) {
		this.restoringSession = restoring;

		if (getSelectedTabbedView() != null) {
			getSelectedTabbedView().disableChangeListener(restoring);
		}
	}

	/**
	 * Finalizes the UI after session restore: restores the previously active
	 * tab, triggers setView, runs validation output, and syncs the file explorer.
	 * Call once after all files are restored.
	 */
	public void finalizeSessionRestore() {
		DogsBayTabbedView selectedTabbedView = getSelectedTabbedView();
		if (selectedTabbedView == null) {
			return;
		}

		// Find and select the previously active tab
		String activeDocPath = getProperties().getSessionActiveDocument();
		DogsBayView targetView = null;

		if (activeDocPath != null) {
			Vector views = selectedTabbedView.getViews();
			for (int i = 0; i < views.size(); i++) {
				DogsBayView view = (DogsBayView) views.elementAt(i);
				DogsBayDocument doc = view.getDocument();
				if (doc != null && doc.getURL() != null) {
					if (activeDocPath.equals(doc.getURL().getFile())) {
						targetView = view;
						break;
					}
				}
			}
		}

		// Fall back to the last tab if the active document wasn't found
		if (targetView == null) {
			targetView = (DogsBayView) selectedTabbedView.getViews().lastElement();
		}

		if (targetView != null) {
			selectedTabbedView.select(targetView);
			setView(targetView);
		}

		// Debug: file explorer state after session restore
		if (fileExplorer != null && explorerContainer != null) {
			SwingUtilities.invokeLater(() -> {
				System.out.println("=== FILE EXPLORER DEBUG (finalizeSessionRestore) ===");
				System.out.println("  fileExplorer.isShowing(): " + fileExplorer.isShowing());
				System.out.println("  fileExplorer.isVisible(): " + fileExplorer.isVisible());
				System.out.println("  fileExplorer.getSize(): " + fileExplorer.getSize());
				System.out.println("  fileExplorer.getParent(): " + (fileExplorer.getParent() != null ? fileExplorer.getParent().getClass().getSimpleName() + " size=" + fileExplorer.getParent().getSize() : "null"));
				System.out.println("  explorerContainer.isShowing(): " + explorerContainer.isShowing());
				System.out.println("  explorerContainer.getSize(): " + explorerContainer.getSize());
				System.out.println("  explorerContainer.getSelectedId(): " + explorerContainer.getSelectedId());
				System.out.println("  fileExplorer root: " + fileExplorer.getRootDirectory());
				System.out.println("  Attempting refreshSelectedExplorer...");
				explorerContainer.refreshSelectedExplorer();
				System.out.println("  After refresh - fileExplorer.isShowing(): " + fileExplorer.isShowing());
				System.out.println("  After refresh - fileExplorer.getSize(): " + fileExplorer.getSize());
				System.out.println("=== END DEBUG ===");
			});
		}
	}

	public void setEmacsModeOn(boolean condition) {
		KeyStroke ctrlX = KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK, false);

		if (condition) {
			// add the Ctrl-X editing mode

			Action modeAction = new AbstractAction() {
				public void actionPerformed(ActionEvent e) {
					statusbar.getModeField().requestFocus();
				}
			};

			mainPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ctrlX, "MODE");
			mainPanel.getActionMap().put("MODE", modeAction);
			statusbar.setModeFocusable(true);
		} else {
			mainPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ctrlX, "none");
			statusbar.setModeFocusable(false);
		}

	}

	/**
	 * Gets the explorer container.
	 * Replaces the legacy getControllerTabbedPane() method.
	 *
	 * @return the explorer container
	 */
	public ExplorerContainer getExplorerContainer() {
		return explorerContainer;
	}

	/**
	 * Returns the right sidebar explorer container.
	 */
	public ExplorerContainer getRightExplorerContainer() {
		return rightExplorerContainer;
	}

	/**
	 * @deprecated Use getExplorerContainer() instead
	 */
	@Deprecated
	public JTabbedPane getControllerTabbedPane() {
		// Return null since we no longer use JTabbedPane
		return null;
	}

	public Vector getGrammarProperties() {
		return schemaManager.getGrammarProperties();
	}

	public void switchToProjectTab() {
		explorerContainer.setSelectedExplorer("projects");
	}

	public void switchToFileExplorerTab() {
		explorerContainer.setSelectedExplorer("fileExplorer");
	}

	public void switchToGitTab() {
		explorerContainer.setSelectedExplorer("git");
	}

	public com.dogsbay.dogsbayaieditor.explorer.FileExplorerPanel getFileExplorer() {
		return fileExplorer;
	}

	public com.dogsbay.dogsbayaieditor.git.GitPanel getGitPanel() {
		com.dogsbay.dogsbayaieditor.plugin.git.GitPlugin p = pluginManager != null
				? pluginManager.getPlugin(com.dogsbay.dogsbayaieditor.plugin.git.GitPlugin.class) : null;
		return p != null ? p.getGitPanel() : null;
	}

	public com.dogsbay.dogsbayaieditor.dita.ui.DitaExplorerPanel getDitaExplorer() {
		com.dogsbay.dogsbayaieditor.plugin.dita.DitaPlugin p = pluginManager != null
				? pluginManager.getPlugin(com.dogsbay.dogsbayaieditor.plugin.dita.DitaPlugin.class) : null;
		return p != null ? p.getDitaExplorer() : null;
	}

	public com.dogsbay.dogsbayaieditor.search.SearchPanel getSearchPanel() {
		com.dogsbay.dogsbayaieditor.plugin.search.SearchPlugin p = pluginManager != null
				? pluginManager.getPlugin(com.dogsbay.dogsbayaieditor.plugin.search.SearchPlugin.class) : null;
		return p != null ? p.getSearchPanel() : null;
	}

	public ProjectSwitcher getProjectSwitcher() {
		return projectSwitcher;
	}

	public boolean isFullScreen() {
		return tabbedViewParent != null;
	}

	public void updateGrammar(GrammarProperties grammar) {
		schemaManager.updateGrammar(grammar);
	}

	int rightSplitDividerLocation = -1;

	/**
	 * Whether the right panel has been given its width yet this session. The
	 * width itself lives in {@link RightPanelWidth}; this only stops the split's
	 * own resize events from setting it a second time, over a divider the reader
	 * has since dragged.
	 */
	private boolean rightPanelSized = false;
	int splitDividerLocation = -1;

	// CopyErrorListAction moved to ActionRegistry

	/*
	 * private GridBridgeAddAttributeToSelectedAction
	 * gridBridgeAddAttributeToSelectedAction;
	 * 
	 * private GridBridgeAddAttributeColumnAction
	 * gridBridgeAddAttributeColumnAction;
	 * 
	 * private GridBridgeAddChildTableAction gridBridgeAddChildTableAction;
	 * 
	 * private GridBridgeAddElementAfterAction gridBridgeAddElementAfterAction;
	 * private GridBridgeAddElementBeforeAction gridBridgeAddElementBeforeAction;
	 * 
	 * 
	 * private GridBridgeMoveRowUpAction gridBridgeMoveRowUpAction;
	 * 
	 * private GridBridgeMoveRowDownAction gridBridgeMoveRowDownAction;
	 * 
	 * private GridBridgeEditAttributeNameAction gridBridgeEditAttributeNameAction;
	 * 
	 * private GridBridgeDeleteRowAction gridBridgeDeleteElementAction;
	 * 
	 * private GridBridgeDeleteColumnAction gridBridgeDeleteColumnAction;
	 * 
	 * private GridBridgeAddTextToSelectedAction gridBridgeAddTextToSelectedAction;
	 * 
	 * private GridBridgeAddTextColumnAction gridBridgeAddTextColumnAction;
	 * 
	 * private GridBridgeDeleteChildTableAction gridBridgeDeleteChildTableAction;
	 * 
	 * private GridBridgeDeleteAttsAndTextAction gridBridgeDeleteAttsAndTextAction;
	 * 
	 * 
	 * 
	 * private GridBridgeDeleteSelectedAttributeAction
	 * gridBridgeDeleteSelectedAttributeAction;
	 * 
	 * private GridBridgeDeleteSelectedTextAction
	 * gridBridgeDeleteSelectedTextAction;
	 * 
	 * private GridBridgeCopyShallowAction gridBridgeCopyShallowAction;
	 * 
	 * private GridBridgePasteAsChildAction gridBridgePasteAsChildAction;
	 * 
	 * private GridBridgePasteBeforeAction gridBridgePasteBeforeAction;
	 * 
	 * private GridBridgePasteAfterAction gridBridgePasteAfterAction;
	 * 
	 * private GridBridgeGotoChildTableAction gridBridgeGotoChildTableAction;
	 * 
	 * private GridBridgeGotoParentTableAction gridBridgeGotoParentTableAction;
	 * 
	 * private GridBridgeEditSelectedAttributeNameAction
	 * gridBridgeEditSelectedAttributeNameAction;
	 * 
	 * private GridBridgeSortTableAscendingAction
	 * gridBridgeSortTableAscendingAction;
	 * 
	 * private GridBridgeSortTableDescendingAction
	 * gridBridgeSortTableDescendingAction;
	 * 
	 * private GridBridgeUnsortTableAction gridBridgeUnsortTableAction;
	 * 
	 * private GridBridgeExpandRowAction gridBridgeExpandRowAction;
	 * 
	 * private GridBridgeCollapseRowAction gridBridgeCollapseRowAction;
	 * 
	 * private GridBridgeCollapseCurrentTableAction
	 * gridBridgeCollapseCurrentTableAction;
	 * 
	 * private GridBridgeDeleteAction gridBridgeDeleteAction;
	 */

	private JCheckBoxMenuItem gridSupportMixedContent;

	private JCheckBoxMenuItem gridSchemaAware;

	private JCheckBoxMenuItem gridSchemaHighlightRequired;

	private JRadioButtonMenuItem gridToolbarShowOnLeft;

	private JRadioButtonMenuItem gridToolbarShowOnTop;

	private JRadioButtonMenuItem gridToolbarHide;

	// FindInFilesAction and ReplaceInFilesAction moved to ActionRegistry

	public void toggleFullScreen() {
		boolean full = true;

		if (rightSplit.getDividerLocation() < rightSplit.getMaximumDividerLocation()) {
			rightSplitDividerLocation = rightSplit.getDividerLocation();
			rightSplit.setDividerLocation((double) 1.0);
			full = false;
		}

		if (split.getDividerLocation() > 0) {
			splitDividerLocation = split.getDividerLocation();
			split.setDividerLocation((double) 0);
			full = false;
		}

		if (full) {
			split.setDividerLocation(splitDividerLocation);
			rightSplit.setDividerLocation(rightSplitDividerLocation);

			Vector views = getViews();
			if (views != null) {
				for (int i = 0; i < views.size(); i++) {
					DogsBayView view = (DogsBayView) views.elementAt(i);
					view.getEditor().scrollCursorToVisible();
				}
			}
		}

		// Not null, turn full screen off!
		// if ( tabbedViewParent != null) {
		// splitPanel.remove( fullScreenPanel);
		// fullScreenPanel.remove( editorToolbarPanel);
		// fullScreenPanel.remove( selectedTabbedView);
		//
		// if ( tabbedViewParent instanceof JSplitPane) {
		// JSplitPane split = (JSplitPane)tabbedViewParent;
		//
		// if ( split.getLeftComponent() == null) {
		// split.setLeftComponent( selectedTabbedView);
		// } else {
		// split.setRightComponent( selectedTabbedView);
		// }
		// } else {
		// JPanel panel = (JPanel)tabbedViewParent;
		// panel.add( selectedTabbedView, BorderLayout.CENTER);
		// }
		//
		// editorPanel.add( editorToolbarPanel, BorderLayout.NORTH);
		// splitPanel.add( split);
		// splitPanel.revalidate();
		// splitPanel.repaint();
		// tabbedViewParent = null;
		// } else { // turn full screen on!
		// tabbedViewParent = selectedTabbedView.getParent();
		//
		// splitPanel.remove( split);
		// editorPanel.remove( editorToolbarPanel);
		//
		// fullScreenPanel.add( editorToolbarPanel, BorderLayout.NORTH);
		// fullScreenPanel.add( selectedTabbedView, BorderLayout.CENTER);
		//
		// splitPanel.add( fullScreenPanel, BorderLayout.CENTER);
		// splitPanel.revalidate();
		// splitPanel.repaint();
		// }
		//
		// getSplitTabsHorizontallyAction().setEnabled(
		// selectedTabbedView.getViews().size() > 1 && !isFullScreen());
		// getSplitTabsVerticallyAction().setEnabled(
		// selectedTabbedView.getViews().size() > 1 && !isFullScreen());
		// getUnsplitTabsAction().setEnabled( tabbedViews.size() > 1 &&
		// !isFullScreen());
		//
		// if ( tabbedViews.size() > 1 && !isFullScreen()) {
		// synchroniseSplits.setEnabled( true);
		// } else {
		// synchroniseSplits.setEnabled( false);
		// }
		//
		// toggleFullScreenMenuItem.setSelected( isFullScreen());
	}

	public EventBus getEventBus() {
		return eventBus;
	}

	/**
	 * The active-deliverable service — the shared "current map · profile" state for
	 * deep validate, preview, publish, and the status bar.
	 */
	public com.dogsbay.dogsbayaieditor.services.DeliverableService getDeliverableService() {
		return deliverableService;
	}

	/** The selected project's {@link ProjectProperties}, or null if none/!DITA. */
	private ProjectProperties currentProjectProperties() {
		if (projectPanel == null) {
			return null;
		}
		com.dogsbay.dogsbayaieditor.project.ProjectNode sel = projectPanel.getSelectedProject();
		if (sel != null && sel.getProperties() instanceof ProjectProperties pp) {
			return pp;
		}
		return null;
	}

	private com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig dogsbayConfig;
	private java.io.File dogsbayConfigRoot;

	/**
	 * The project-local {@code .dogsbay} config for the open workspace (committed
	 * {@code config.xml} + gitignored {@code local.xml}), cached per workspace root.
	 * Null when no folder is open.
	 */
	public com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig dogsbayConfig() {
		java.io.File root = getFileExplorer() != null
				? getFileExplorer().getRootDirectory() : null;
		if (root == null || !root.isDirectory()) {
			return null;
		}
		if (dogsbayConfig == null || !root.equals(dogsbayConfigRoot)) {
			dogsbayConfig = com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig
					.load(root.toPath());
			dogsbayConfigRoot = root;
		}
		return dogsbayConfig;
	}

	/** Forget the cached {@code .dogsbay} config (call after writing it). */
	private void invalidateDogsbayConfig() {
		dogsbayConfig = null;
		dogsbayConfigRoot = null;
	}

	/**
	 * Write the current workspace's shareable settings to {@code .dogsbay/config.xml}
	 * (project type, default root map, framework name, the active deliverable as the
	 * default) plus a {@code .gitignore} for the personal {@code local.xml}, keeping the
	 * metadata policy and house style already there. Menu: Project &gt; Save Project Settings.
	 */
	public void saveProjectSettings() {
		java.io.File root = getFileExplorer() != null
				? getFileExplorer().getRootDirectory() : null;
		if (root == null || !root.isDirectory()) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"Open a project folder first (File → Open Folder).",
					"Save Project Settings", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		int confirm = javax.swing.JOptionPane.showConfirmDialog(this,
				"Write shareable project settings to\n" + root.toPath().resolve(".dogsbay")
				+ " ?\n(Commit .dogsbay/config.xml to share with your team; "
				+ "local.xml stays gitignored.)",
				"Save Project Settings", javax.swing.JOptionPane.OK_CANCEL_OPTION,
				javax.swing.JOptionPane.QUESTION_MESSAGE);
		if (confirm != javax.swing.JOptionPane.OK_OPTION) {
			return;
		}
		try {
			ProjectProperties pp = currentProjectProperties();
			// Default root map (relative to the workspace), from the resolved map.
			java.io.File map = getDefaultRootMapFile();
			// The active deliverable becomes the shared default.
			var active = deliverableService != null
					? deliverableService.getActiveDeliverable() : null;
			boolean hasActive = active != null && active.sourceFile() != null;
			// Merge into the existing file: the policy and house style stay put.
			com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig.saveSetup(root.toPath(),
					pp != null ? pp.getProjectType() : null,
					pp != null ? pp.getFrameworkName() : null,
					map != null ? relativizeToRoot(root, map) : null,
					hasActive ? relativizeToRoot(root, active.sourceFile().toFile()) : null,
					hasActive ? active.name() : null);
			invalidateDogsbayConfig();
			javax.swing.JOptionPane.showMessageDialog(this,
					"Saved to .dogsbay/config.xml. Commit it to share with your team.",
					"Save Project Settings", javax.swing.JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception e) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"Could not save project settings:\n" + e.getMessage(),
					"Save Project Settings", javax.swing.JOptionPane.ERROR_MESSAGE);
		}
	}

	/** A workspace-relative path string for a file, or its absolute path if outside. */
	private static String relativizeToRoot(java.io.File root, java.io.File file) {
		try {
			return root.toPath().toAbsolutePath().normalize()
					.relativize(file.toPath().toAbsolutePath().normalize()).toString();
		} catch (IllegalArgumentException e) {
			return file.getAbsolutePath();
		}
	}

	/**
	 * (Re)load the DITA project context for the open workspace and install it on the
	 * {@link DeliverableService}, restoring the persisted active deliverable (else
	 * the default). Does a little disk I/O (small project files); call off the EDT
	 * for large workspaces. Safe to call when no folder/DITA project is open.
	 */
	public void refreshDeliverableContext() {
		if (deliverableService == null) {
			return;
		}
		java.io.File root = getFileExplorer() != null
				? getFileExplorer().getRootDirectory() : null;
		if (root == null || !root.isDirectory()) {
			deliverableService.setContext(null, null, null);
			return;
		}
		try {
			java.util.List<java.nio.file.Path> catalogs = new java.util.ArrayList<>();
			java.nio.file.Path bundled = com.dogsbay.dogsbayaieditor.validate
					.DocumentValidator.builtinDitaCatalog();
			if (bundled != null) {
				catalogs.add(bundled);
			}
			String ditaOt = getDitaOtPath();
			var ctx = com.dogsbay.dogsbayaieditor.ditaproject.ProjectContextLoader.load(
					root.toPath(), null, catalogs,
					ditaOt != null ? java.nio.file.Path.of(ditaOt) : null);

			// Restore the active deliverable: the persisted per-user selection
			// (ProjectProperties), else .dogsbay/local.xml (personal), else the
			// .dogsbay/config.xml shared default.
			ProjectProperties pp = currentProjectProperties();
			String prefName = null;
			String prefRel = null;
			if (pp != null) {
				prefName = pp.getActiveDeliverableName();
				prefRel = pp.getActiveDeliverableFile();
			}
			com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig dc = dogsbayConfig();
			if ((prefName == null || prefName.isBlank()) && dc != null) {
				prefName = dc.getActiveDeliverableName();
				prefRel = dc.getActiveDeliverableFile();
				if (prefName == null || prefName.isBlank()) {
					prefName = dc.getDefaultDeliverableName();
					prefRel = dc.getDefaultDeliverableFile();
				}
			}
			java.nio.file.Path prefFile = (prefRel != null && !prefRel.isBlank())
					? root.toPath().resolve(prefRel).normalize() : null;
			deliverableService.setContext(ctx, prefFile, prefName);
		} catch (Exception e) {
			deliverableService.setContext(null, null, null);
		}
	}

	/**
	 * Make {@code d} the active deliverable and persist the choice to the per-user,
	 * gitignored {@code .dogsbay/local.xml} so it survives restarts (read back by
	 * {@link #refreshDeliverableContext()}). Used by the status-bar switcher and the
	 * Manage Deliverables dialog.
	 */
	public void setActiveDeliverableAndPersist(
			com.dogsbay.dogsbayaieditor.ditaproject.Deliverable d) {
		if (deliverableService == null || d == null) {
			return;
		}
		deliverableService.setActiveDeliverable(d); // fires DeliverableChangedEvent → status bar
		java.io.File root = getFileExplorer() != null
				? getFileExplorer().getRootDirectory() : null;
		if (root == null || !root.isDirectory()) {
			return;
		}
		String rel = d.sourceFile() != null
				? relativizeToRoot(root, d.sourceFile().toFile()) : null;
		try {
			var dc = dogsbayConfig();
			if (dc != null) {
				dc.setActiveDeliverable(rel, d.name());
				dc.saveLocal(root.toPath());
			}
		} catch (Exception ex) {
			// persistence is best effort — the in-session selection still applies
		}
		// keep the in-memory per-project properties (read first on reload) in sync
		ProjectProperties pp = currentProjectProperties();
		if (pp != null) {
			pp.setActiveDeliverable(rel, d.name());
		}
	}

	/**
	 * Persist the active deliverable as a {@code (relative-file, name)} pointer in
	 * the current project's properties. No-op when there is no configured project
	 * (a File-Explorer-only workspace can't persist) or the deliverable was
	 * synthesized (no source file).
	 */
	private void persistActiveDeliverable(
			com.dogsbay.dogsbayaieditor.ditaproject.Deliverable d) {
		ProjectProperties pp = currentProjectProperties();
		if (pp == null) {
			return;
		}
		try {
			String newFile = null;
			String newName = null;
			if (d != null && d.sourceFile() != null) {
				newName = d.name();
				newFile = d.sourceFile().toString();
				java.io.File root = getFileExplorer() != null
						? getFileExplorer().getRootDirectory() : null;
				if (root != null) {
					try {
						newFile = root.toPath().toAbsolutePath().normalize()
								.relativize(d.sourceFile().toAbsolutePath().normalize()).toString();
					} catch (IllegalArgumentException ignore) {
						// different roots → keep the absolute path
					}
				}
			}
			// Skip the disk write when nothing changed — avoids rewriting the whole
			// config on every startup just to re-persist the restored selection.
			if (java.util.Objects.equals(newFile, pp.getActiveDeliverableFile())
					&& java.util.Objects.equals(newName, pp.getActiveDeliverableName())) {
				return;
			}
			pp.setActiveDeliverable(newFile, newName);
			// pp is the live config object; flush the whole configuration to disk.
			properties.saveToDisk();
		} catch (Exception ignore) {
			// persistence is best-effort; never block a selection change
		}
	}

	/**
	 * Add or edit a deliverable in {@code projectFile} (in place, with a write
	 * confirmation), preserving the file's other content. After writing, reload the
	 * context and make the edited deliverable active. Menu / status-bar action.
	 */
	public void manageDeliverable(java.nio.file.Path projectFile,
			com.dogsbay.dogsbayaieditor.ditaproject.Deliverable existing) {
		if (!com.dogsbay.dogsbayaieditor.ditaproject.DitaProjectWriter.isEditable(projectFile)) {
			javax.swing.JOptionPane.showMessageDialog(this,
					projectFile.getFileName() + " is an XML project file; edit it by hand.",
					"Edit Deliverable", javax.swing.JOptionPane.WARNING_MESSAGE);
			return;
		}
		com.dogsbay.dogsbayaieditor.ditaproject.DeliverableEdit edit =
				com.dogsbay.dogsbayaieditor.dita.ui.DeliverableEditorDialog
						.showDialog(this, projectFile, existing);
		if (edit == null) {
			return;
		}
		try {
			com.dogsbay.dogsbayaieditor.ditaproject.DitaProjectWriter
					.upsertDeliverable(projectFile, edit);
			refreshDeliverableContext();
			if (deliverableService != null) {
				deliverableService.setActiveDeliverable(
						projectFile.toAbsolutePath().normalize(), edit.name());
			}
		} catch (java.io.IOException e) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"Could not write the deliverable:\n" + e.getMessage(),
					"Write Failed", javax.swing.JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Manage the project's DITA deliverables: a table of every deliverable (across
	 * the discovered DITA-OT project files) with Add / Edit / Delete / Set active.
	 * Add/Edit reuse the in-place field-preserving editor; Delete removes the
	 * deliverable from its (JSON/YAML) project file. Contributed to the Project menu.
	 */
	public void manageDeliverables() {
		java.io.File root = getFileExplorer() != null
				? getFileExplorer().getRootDirectory() : null;
		if (root == null || !root.isDirectory()) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"Open a project folder first (File → Open Folder).",
					"Deliverables", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		javax.swing.JDialog dialog = new javax.swing.JDialog(this,
				"Manage DITA Deliverables", true);
		String[] cols = {"Active", "Name", "Map", "DITAVAL", "Transtype", "Output", "Project file"};
		javax.swing.table.DefaultTableModel model =
				new javax.swing.table.DefaultTableModel(cols, 0) {
			@Override
			public boolean isCellEditable(int r, int c) {
				return false;
			}
		};
		javax.swing.JTable table = new javax.swing.JTable(model);
		table.getColumnModel().getColumn(0).setMaxWidth(50);
		java.util.List<com.dogsbay.dogsbayaieditor.ditaproject.Deliverable> rows =
				new java.util.ArrayList<>();

		// Rebuild the table from the *current* context (no re-resolve), marking the
		// active deliverable. Used after Set active so it isn't reverted.
		Runnable rebuildRows = () -> {
			var ctx = deliverableService != null ? deliverableService.getContext() : null;
			var activeNow = deliverableService != null
					? deliverableService.getActiveDeliverable() : null;
			model.setRowCount(0);
			rows.clear();
			if (ctx != null) {
				for (var d : ctx.deliverables()) {
					rows.add(d);
					boolean isActive = activeNow != null
							&& java.util.Objects.equals(activeNow.sourceFile(), d.sourceFile())
							&& activeNow.name().equals(d.name());
					String ditavals = d.ditavals().stream()
							.map(p -> p.getFileName().toString())
							.reduce((a, b) -> a + ", " + b).orElse("");
					model.addRow(new Object[] {
							isActive ? "●" : "",
							d.name(),
							d.map() != null ? d.map().getFileName().toString() : "",
							ditavals,
							d.transtype() == null ? "" : d.transtype(),
							d.output() == null ? "" : d.output().toString(),
							d.sourceFile() != null
									? d.sourceFile().getFileName().toString() : "(synthesized)"});
				}
			}
		};
		// Re-resolve the project files from disk, then rebuild — after add/edit/delete.
		Runnable refreshFromDisk = () -> {
			refreshDeliverableContext();
			rebuildRows.run();
		};
		refreshFromDisk.run();

		java.util.function.Supplier<com.dogsbay.dogsbayaieditor.ditaproject.Deliverable> sel =
				() -> {
					int r = table.getSelectedRow();
					return r >= 0 && r < rows.size() ? rows.get(r) : null;
				};

		javax.swing.JButton add = new javax.swing.JButton("Add…");
		add.addActionListener(e -> {
			addOrCreateDeliverable();
			refreshFromDisk.run();
		});
		javax.swing.JButton edit = new javax.swing.JButton("Edit…");
		edit.addActionListener(e -> {
			var d = sel.get();
			if (d == null) {
				return;
			}
			if (d.sourceFile() == null) {
				javax.swing.JOptionPane.showMessageDialog(dialog,
						"This deliverable is synthesized from the default root map — "
						+ "use Add… to create a project file for it.",
						"Edit", javax.swing.JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			manageDeliverable(d.sourceFile(), d);
			refreshFromDisk.run();
		});
		javax.swing.JButton delete = new javax.swing.JButton("Delete");
		delete.addActionListener(e -> {
			var d = sel.get();
			if (d == null) {
				return;
			}
			if (d.sourceFile() == null) {
				javax.swing.JOptionPane.showMessageDialog(dialog,
						"Synthesized deliverable — nothing to delete.",
						"Delete", javax.swing.JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			if (javax.swing.JOptionPane.showConfirmDialog(dialog,
					"Delete deliverable “" + d.name() + "” from "
					+ d.sourceFile().getFileName() + "?", "Delete deliverable",
					javax.swing.JOptionPane.OK_CANCEL_OPTION)
					!= javax.swing.JOptionPane.OK_OPTION) {
				return;
			}
			try {
				boolean removed = com.dogsbay.dogsbayaieditor.ditaproject.DitaProjectWriter
						.deleteDeliverable(d.sourceFile(), d.name());
				if (!removed) {
					javax.swing.JOptionPane.showMessageDialog(dialog,
							"Deliverable not found in the project file.",
							"Delete", javax.swing.JOptionPane.WARNING_MESSAGE);
				}
			} catch (Exception ex) {
				javax.swing.JOptionPane.showMessageDialog(dialog,
						"Could not delete: " + ex.getMessage(),
						"Delete", javax.swing.JOptionPane.ERROR_MESSAGE);
			}
			refreshFromDisk.run();
		});
		javax.swing.JButton setActive = new javax.swing.JButton("Set active");
		setActive.addActionListener(e -> {
			var d = sel.get();
			if (d != null && deliverableService != null) {
				// Set by identity (the deliverable came from the live context) and
				// only rebuild rows — re-resolving from disk would revert the choice.
				setActiveDeliverableAndPersist(d);
				rebuildRows.run();
			}
		});
		javax.swing.JButton closeBtn = new javax.swing.JButton("Close");
		closeBtn.addActionListener(e -> dialog.dispose());

		javax.swing.JPanel buttons = new javax.swing.JPanel(
				new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
		buttons.add(add);
		buttons.add(edit);
		buttons.add(delete);
		buttons.add(setActive);
		buttons.add(closeBtn);
		dialog.setLayout(new java.awt.BorderLayout());
		dialog.add(new javax.swing.JScrollPane(table), java.awt.BorderLayout.CENTER);
		dialog.add(buttons, java.awt.BorderLayout.SOUTH);
		dialog.setSize(760, 340);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	/** Add a deliverable to the project's primary project file, creating
	 *  {@code project.json} when the workspace has none yet. */
	public void addOrCreateDeliverable() {
		java.io.File root = getFileExplorer() != null
				? getFileExplorer().getRootDirectory() : null;
		if (root == null || !root.isDirectory()) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"Open a project folder first (File → Open Folder).",
					"Add Deliverable", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		refreshDeliverableContext();
		var ctx = deliverableService != null ? deliverableService.getContext() : null;
		java.nio.file.Path target = (ctx != null && ctx.projectFile() != null)
				? ctx.projectFile() : root.toPath().resolve("project.json");
		manageDeliverable(target, null);
	}

	/** Edit the active deliverable (status-bar action). */
	public void editActiveDeliverable() {
		var active = deliverableService != null
				? deliverableService.getActiveDeliverable() : null;
		if (active == null) {
			javax.swing.JOptionPane.showMessageDialog(this, "No active deliverable to edit.",
					"Edit Deliverable", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (active.sourceFile() == null) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"This deliverable is synthesized from the default root map — there is no "
					+ "project file to edit. Use “Add deliverable” to create one.",
					"Edit Deliverable", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		manageDeliverable(active.sourceFile(), active);
	}

	/**
	 * Build all the project's deliverables with DITA-OT to a chosen output folder
	 * (each under a subdir), off the EDT with a progress dialog, then summarize
	 * per-deliverable success. Menu: XML &gt; Build Deliverables. Requires DITA-OT.
	 */
	public void buildDeliverables() {
		java.io.File root = ditaOtValidationRoot();
		if (root == null) {
			return;
		}
		final String ditaOtPath = ditaOtPathOrWarn();
		if (ditaOtPath == null) {
			return;
		}

		// Pick which deliverables to build (active one pre-ticked). Output folders come
		// from each deliverable's <output> in the project — no destination prompt.
		refreshDeliverableContext();
		com.dogsbay.dogsbayaieditor.services.DeliverableService svc = getDeliverableService();
		com.dogsbay.dogsbayaieditor.ditaproject.ProjectContext ctx = svc != null ? svc.getContext() : null;
		java.util.List<com.dogsbay.dogsbayaieditor.ditaproject.Deliverable> deliverables =
				ctx != null ? ctx.deliverables() : java.util.List.of();
		if (deliverables.isEmpty()) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"No deliverables found in this project.", "Build Deliverables",
					javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		java.util.List<String> selected = com.dogsbay.dogsbayaieditor.dita.ui.BuildDeliverablesDialog.choose(
				this, deliverables, svc.getActiveDeliverable());
		if (selected == null || selected.isEmpty()) {
			return; // cancelled, or nothing selected
		}
		final java.util.List<String> selectedNames = selected;

		javax.swing.JDialog progress = new javax.swing.JDialog(this, "Building…", false);
		javax.swing.JPanel pp = new javax.swing.JPanel(new java.awt.BorderLayout(10, 10));
		pp.setBorder(javax.swing.BorderFactory.createEmptyBorder(15, 15, 15, 15));
		pp.add(new javax.swing.JLabel("Building deliverables with DITA-OT (this can take a "
				+ "while)…"), java.awt.BorderLayout.NORTH);
		javax.swing.JProgressBar bar = new javax.swing.JProgressBar();
		bar.setIndeterminate(true);
		pp.add(bar, java.awt.BorderLayout.CENTER);
		progress.setContentPane(pp);
		progress.pack();
		progress.setLocationRelativeTo(this);
		progress.setVisible(true);

		final java.io.File rootDir = root;
		new javax.swing.SwingWorker<
				java.util.List<com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild>,
				Void>() {
			@Override
			protected java.util.List<
					com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild>
					doInBackground() throws Exception {
				// One invocation: the executor loads the project once and builds every
					// selected deliverable to its own <output> (outputBaseDir = null).
					return new com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor()
							.execute(new com.dogsbay.dogsbayaieditor.commands.BuildDeliverablesCommand(
									rootDir.getAbsolutePath(), null, null, ditaOtPath, selectedNames));
			}

			@Override
			protected void done() {
				progress.dispose();
				try {
					var results = get();
					if (results.isEmpty()) {
						javax.swing.JOptionPane.showMessageDialog(DogsBayAIEditor.this,
								"No deliverables found in this project.", "Build Deliverables",
								javax.swing.JOptionPane.INFORMATION_MESSAGE);
						return;
					}
					long failed = results.stream().filter(b -> !b.success()).count();
					StringBuilder sb = new StringBuilder();
					for (var b : results) {
						long errs = b.messages().stream()
								.filter(com.dogsbay.xml.dita.DitaOtMessage::isError).count();
						sb.append(b.success() ? "  OK    " : "  FAILED").append("  ")
								.append(b.name()).append(" (").append(b.transtype()).append(")")
								.append(errs > 0 ? " — " + errs + " error(s)" : "")
								.append("\n      → ").append(b.outputDir()).append("\n")
									.append(b.tempDir() != null ? "      temporary files → " + b.tempDir() + "\n" : "");
					}
					javax.swing.JTextArea area = new javax.swing.JTextArea(sb.toString());
					area.setEditable(false);
					area.setRows(Math.min(14, results.size() * 2 + 1));
					javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(area);
					String header = results.size() + " deliverable(s): "
							+ (results.size() - failed) + " ok, " + failed + " failed.";
					// Surface the actual DITA-OT diagnostics in the Project Validation pane
					// (click-to-open) so a failed build is inspectable, not just a count.
					java.util.List<com.dogsbay.xml.XMLError> buildDiagnostics =
								com.dogsbay.dogsbayaieditor.validate.ProjectValidationErrors.fromDitaOtBuilds(results, rootDir);
						// Always refresh the pane (startProjectCheck clears it) so a later successful
						// rebuild drops the previous run's stale errors. Only steal focus / claim
						// "errors" when a deliverable actually failed (warnings on a clean build must
						// not look like a failure).
						outputPanel.startProjectCheck("Build Deliverables — " + header);
						for (com.dogsbay.xml.XMLError be : buildDiagnostics) {
							outputPanel.addProjectError(be);
						}
						outputPanel.endProjectCheck(header);
						if (failed > 0) {
							outputPanel.selectProjectTab();
						}
					int choice = javax.swing.JOptionPane.showOptionDialog(DogsBayAIEditor.this,
							failed > 0
								? new Object[] { header, scroll, "Errors are listed in the Project Validation tab (click to open)." }
								: new Object[] { header, scroll },
								"Build Deliverables",
							javax.swing.JOptionPane.DEFAULT_OPTION,
							failed > 0 ? javax.swing.JOptionPane.WARNING_MESSAGE
									: javax.swing.JOptionPane.INFORMATION_MESSAGE,
							null, results.stream().anyMatch(b -> b.tempDir() != null)
										? new String[] { "Open Output Folder", "Open Temp Folder", "OK" }
										: new String[] { "Open Output Folder", "OK" }, "OK");
						if (choice == 1 && results.stream().anyMatch(b -> b.tempDir() != null)) {
							openBuildTemp(results);
						}
					if (choice == 0) {
						try {
							// Open the first build's actual output dir (generated HTML/PDF),
								// falling back to the project root.
								java.io.File toOpen = rootDir;
								for (var b : results) {
									if (b.outputDir() != null) {
										java.io.File d = new java.io.File(b.outputDir());
										if (d.isDirectory()) { toOpen = d; break; }
									}
								}
								if (java.awt.Desktop.isDesktopSupported()) {
									java.awt.Desktop.getDesktop().open(toOpen);
								}
						} catch (Exception ignore) {
							// user can navigate manually
						}
					}
				} catch (Exception ex) {
					Throwable cause = (ex instanceof java.util.concurrent.ExecutionException
							&& ex.getCause() != null) ? ex.getCause() : ex;
					javax.swing.JOptionPane.showMessageDialog(DogsBayAIEditor.this,
							"Build failed:\n" + cause.getMessage(), "Build Deliverables",
							javax.swing.JOptionPane.ERROR_MESSAGE);
				}
			}
		}.execute();
	}

	/** Open the first temporary folder a build kept, in the system file browser. */
	private void openBuildTemp(java.util.List<com.dogsbay.dogsbayaieditor.commands.results.DeliverableBuild> results) {
		for (var b : results) {
			if (b.tempDir() != null && new java.io.File(b.tempDir()).isDirectory()) {
				try {
					if (java.awt.Desktop.isDesktopSupported()) {
						java.awt.Desktop.getDesktop().open(new java.io.File(b.tempDir()));
					}
				} catch (Exception ignore) {
					// the path is shown in the results
				}
				return;
			}
		}
	}

	/**
	 * Project &gt; Clear Temporary Build Files: removes the DITA-OT temporary files
	 * that builds kept in {@code .dogsbay/temp}.
	 */
	public void clearBuildTempFiles() {
		java.nio.file.Path root = projectRootForAgents();
		if (root == null) {
			javax.swing.JOptionPane.showMessageDialog(this, "Open a project first.",
					"Clear Temporary Build Files", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		java.nio.file.Path folder = com.dogsbay.dogsbayaieditor.commands.DitaOtTemp.folder(root);
		if (!java.nio.file.Files.isDirectory(folder)) {
			javax.swing.JOptionPane.showMessageDialog(this, "No builds in this project kept temporary files.",
					"Clear Temporary Build Files", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		int confirm = javax.swing.JOptionPane.showConfirmDialog(this,
				"Delete the temporary files that DITA-OT builds kept in\n" + folder + " ?",
				"Clear Temporary Build Files", javax.swing.JOptionPane.OK_CANCEL_OPTION,
				javax.swing.JOptionPane.QUESTION_MESSAGE);
		if (confirm != javax.swing.JOptionPane.OK_OPTION) {
			return;
		}
		try {
			int removed = com.dogsbay.dogsbayaieditor.commands.DitaOtTemp.clear(root);
			javax.swing.JOptionPane.showMessageDialog(this,
					"Removed the temporary files of " + removed + " deliverable(s).",
					"Clear Temporary Build Files", javax.swing.JOptionPane.INFORMATION_MESSAGE);
		} catch (java.io.IOException e) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"Could not remove every temporary file:\n" + e.getMessage(),
					"Clear Temporary Build Files", javax.swing.JOptionPane.WARNING_MESSAGE);
		}
	}

	public DocumentManager getDocumentManager() {
		return documentManager;
	}

	public com.dogsbay.dogsbayaieditor.commands.CommandRegistry getCommandRegistry() {
		return commandRegistry;
	}

	public com.dogsbay.dogsbayaieditor.commands.CommandExecutor getCommandExecutor() {
		return commandExecutor;
	}

	/** Live agent sessions: who may call the command engine and how they are attributed. */
	public com.dogsbay.agent.session.AgentSessionRegistry getSessionRegistry() {
		return sessionRegistry;
	}

	private com.dogsbay.agent.acp.AgentRegistryCatalog agentCatalog;

	/** The ACP agent registry: which agents exist and which this machine can run. Refreshed in the background. */
	public synchronized com.dogsbay.agent.acp.AgentRegistryCatalog getAgentCatalog() {
		if (agentCatalog == null) {
			// A registry snapshot, not settings: it belongs where the platform
			// puts things it is free to delete.
			agentCatalog = new com.dogsbay.agent.acp.AgentRegistryCatalog(UserDirectories.cache());
			Thread.ofVirtual().name("acp-registry-refresh").start(agentCatalog::refresh);
		}
		return agentCatalog;
	}

	/** The gate every mutating agent command passes: containment, lease, conflict. */
	public com.dogsbay.dogsbayaieditor.commands.WriteGate getWriteGate() {
		return writeGate;
	}

	/** The append-only record of what agent sessions did, under the project's {@code .dogsbay/}. */
	public com.dogsbay.agent.session.AuditLog getAuditLog() {
		return auditLog;
	}

	/** The open folder as a path, or null when none is open: the root agents are contained to. */
	public java.nio.file.Path projectRootForAgents() {
		return projectRootPath();
	}

	/** The open folder as a path, or null when none is open. */
	private java.nio.file.Path projectRootPath() {
		java.io.File root = getFileExplorer() != null ? getFileExplorer().getRootDirectory() : null;
		return root != null && root.isDirectory() ? root.toPath() : null;
	}

	/**
	 * The integration-server manager (CLI/MCP/REST). The Preferences dialog uses
	 * it to apply config changes (start/stop/restart) and read live status.
	 */
	public com.dogsbay.dogsbayaieditor.ipc.IpcServerManager getIpcServerManager() {
		return ipcServerManager;
	}

	/**
	 * Build the integration-server endpoints to register, honoring the per-
	 * endpoint toggles. Called fresh on each (re)start so toggles apply at
	 * runtime. {@code /status} is always added by the server itself.
	 */
	private java.util.List<com.dogsbay.dogsbayaieditor.ipc.IpcServerManager.Endpoint> buildIpcEndpoints() {
		java.util.List<com.dogsbay.dogsbayaieditor.ipc.IpcServerManager.Endpoint> endpoints =
				new java.util.ArrayList<>();

		if (getProperties().isRpcEndpointEnabled()) {
			var jsonRpcHandler = new com.dogsbay.dogsbayaieditor.ipc.JsonRpcHandler(commandExecutor);
			// The master token is the user's own: the dogsbay CLI and the user's
			// scripts run as the person at the keyboard, ungated and unaudited.
			// A per-session token identifies an agent session.
			endpoints.add(new com.dogsbay.dogsbayaieditor.ipc.IpcServerManager.Endpoint("/rpc", exchange -> {
				String body = new String(exchange.getRequestBody().readAllBytes());
				var session = com.dogsbay.dogsbayaieditor.ipc.EditorHttpServer.sessionOf(exchange)
						.orElse(sessionRegistry.user());
				sessionRegistry.touch(session.id());
				String response = jsonRpcHandler.handle(body, session);
				com.dogsbay.dogsbayaieditor.ipc.EditorHttpServer.sendJson(exchange, 200, response);
			}));
		}

		if (getProperties().isMcpEndpointEnabled()) {
			var mcpServer = new com.dogsbay.dogsbayaieditor.mcp.McpServer(commandExecutor, sessionRegistry);
			endpoints.add(new com.dogsbay.dogsbayaieditor.ipc.IpcServerManager.Endpoint("/mcp", mcpServer,
					mcpServer::close));
		}

		return endpoints;
	}

	public ViewManager getViewManager() {
		return viewManager;
	}

	public SchemaManager getSchemaManager() {
		return schemaManager;
	}

	public com.dogsbay.dogsbayaieditor.plugin.PluginManager getPluginManager() {
		return pluginManager;
	}

	public Vector getTabbedViews() {
		return viewManager.getTabbedViews();
	}

	/**
	 * Put the divider where the right panel can be read, once and once only.
	 * Called from the split's own resize events, because a divider position set
	 * before the first layout pass is thrown away.
	 */
	void sizeRightPanelOnce() {
		if (rightPanelSized || mainContentSplit == null) {
			return;
		}
		int divider = RightPanelWidth.dividerFor(mainContentSplit.getWidth(), mainContentSplit.getDividerSize(),
				properties.getRightPanelWidth());
		if (divider < 0) {
			return;
		}
		rightPanelSized = true;
		mainContentSplit.setDividerLocation(divider);
	}

	public JSplitPane getRightSplit() {
		return rightSplit;
	}

	public JSplitPane getSplit() {
		return split;
	}

	public JSplitPane getMainContentSplit() {
		return mainContentSplit;
	}

	public int getRightSplitDividerLocation() {
		return rightSplitDividerLocation;
	}

	public int getSplitDividerLocation() {
		return splitDividerLocation;
	}

	/**
	 * The left-sidebar divider location to restore, clamped so a corrupted or
	 * collapsed persisted value (≈0, which would leave the sidebar blank) recovers
	 * to a usable default. A sub-minimum width is never a real user preference —
	 * you can't see a 0-width sidebar — so it can only be stale/transient state.
	 */
	private int restoredSidebarDivider() {
		int loc = properties.getTopDividerLocation();
		// Below ~40px the sidebar content is gone (only the icon bar shows), which is
		// never a real preference — treat it as a corrupted/collapsed value.
		return loc >= 40 ? loc : 280; // 280 = DEFAULT_TOP_DIVIDER_LOCATION
	}

	public Navigator getNavigator() {
		return navigator;
	}

	public com.dogsbay.xml.viewer.OutlinePanel getSidebarViewer() {
		com.dogsbay.dogsbayaieditor.plugin.outline.OutlinePlugin p = pluginManager != null
				? pluginManager.getPlugin(com.dogsbay.dogsbayaieditor.plugin.outline.OutlinePlugin.class) : null;
		return p != null ? p.getOutlinePanel() : null;
	}

	public Helper getHelper() {
		return helper;
	}

	public DogsBayDocument getDocument() {
		if (getView() != null) {
			return getView().getDocument();
		}

		return null;
	}

	public ViewPanel getCurrent() {
		if (getView() != null) {
			return getView().getCurrentView();
		}

		return null;
	}

	public OutputPanel getOutputPanel() {
		return outputPanel;
	}

	/**
	 * Validate every file under the open project folder on disk (no documents
	 * opened) and report results in the error pane. Each error opens its file on
	 * click. Menu: XML &gt; Validate Project.
	 */
	public void validateProject() {
		java.io.File root = getFileExplorer() != null
				? getFileExplorer().getRootDirectory() : null;
		if (root == null || !root.isDirectory()) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"Open a project folder first (File → Open Folder).",
					"Validate Project", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		final java.io.File projectRoot = root;
		outputPanel.startProjectCheck(
				"Validating project \"" + projectRoot.getName() + "\" ...");

		new javax.swing.SwingWorker<
				com.dogsbay.dogsbayaieditor.ditaproject.BatchResult<
						com.dogsbay.dogsbayaieditor.commands.results.FileValidation>, Void>() {
			@Override
			protected com.dogsbay.dogsbayaieditor.ditaproject.BatchResult<
					com.dogsbay.dogsbayaieditor.commands.results.FileValidation>
					doInBackground() throws Exception {
				return new com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor()
						.execute(new com.dogsbay.dogsbayaieditor.commands.ValidateProjectCommand(
								projectRoot.getAbsolutePath()));
			}

			@Override
			protected void done() {
				try {
					var result = get();
					// startProjectCheck() already reset the project error list.
					for (com.dogsbay.xml.XMLError err :
							com.dogsbay.dogsbayaieditor.validate.ProjectValidationErrors
									.toErrors(result)) {
						outputPanel.addProjectError(err);
					}
					outputPanel.endProjectCheck(
							result.total() + " file(s): " + result.passed() + " valid, "
							+ result.failed() + " invalid"
							+ (result.truncated() > 0
									? " (+" + result.truncated() + " more failing)" : ""));
					outputPanel.selectProjectTab();
				} catch (Exception ex) {
					outputPanel.endProjectCheck(
							"Validation failed: " + ex.getMessage());
				}
			}
		}.execute();
	}

	/**
	 * Run a Schematron schema (chosen via a file picker) against every file under
	 * the open project folder on disk and report failed assertions in the error
	 * pane. Menu: XML &gt; Validate Project with Schematron. Parallels
	 * {@link #validateProject()}; uses ph-schematron via the command engine.
	 */
	public void runProjectSchematron() {
		java.io.File root = getFileExplorer() != null
				? getFileExplorer().getRootDirectory() : null;
		if (root == null || !root.isDirectory()) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"Open a project folder first (File → Open Folder).",
					"Schematron", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(root);
		chooser.setDialogTitle("Choose a Schematron schema (.sch)");
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
				"Schematron schema (*.sch)", "sch"));
		if (chooser.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
			return;
		}
		final java.io.File schema = chooser.getSelectedFile();
		final java.io.File projectRoot = root;
		outputPanel.startProjectCheck(
				"Running Schematron \"" + schema.getName() + "\" on \""
				+ projectRoot.getName() + "\" ...");

		new javax.swing.SwingWorker<
				com.dogsbay.dogsbayaieditor.ditaproject.BatchResult<
						com.dogsbay.dogsbayaieditor.commands.results.SchematronFinding>, Void>() {
			@Override
			protected com.dogsbay.dogsbayaieditor.ditaproject.BatchResult<
					com.dogsbay.dogsbayaieditor.commands.results.SchematronFinding>
					doInBackground() throws Exception {
				return new com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor()
						.execute(new com.dogsbay.dogsbayaieditor.commands.SchematronProjectCommand(
								projectRoot.getAbsolutePath(), null, schema.getAbsolutePath()));
			}

			@Override
			protected void done() {
				try {
					var result = get();
					// startProjectCheck() already reset the project error list.
					for (com.dogsbay.xml.XMLError err :
							com.dogsbay.dogsbayaieditor.validate.ProjectValidationErrors
									.fromSchematron(result)) {
						outputPanel.addProjectError(err);
					}
					outputPanel.endProjectCheck(
							result.total() + " file(s): " + result.passed() + " pass, "
							+ result.failed() + " with failed assertions"
							+ (result.truncated() > 0
									? " (+" + result.truncated() + " more)" : ""));
					outputPanel.selectProjectTab();
				} catch (Exception ex) {
					outputPanel.endProjectCheck(
							"Schematron run failed: " + ex.getMessage());
				}
			}
		}.execute();
	}

	/**
	 * Run a Schematron schema (chosen via a file picker) against the <em>active
	 * document</em> and report rule matches in the Project Validation pane.
	 *
	 * <p>The document-scoped counterpart to {@link #runProjectSchematron()}: it
	 * needs no open project folder and checks only the file you are looking at.
	 * This is the affordance the retired legacy Schematron dialog provided, and
	 * without it there would be no way to check a single document from the GUI at
	 * all — see {@code plans/retire-legacy-schematron.md}.
	 */
	public void runDocumentSchematron() {
		DogsBayDocument document = getDocument();
		java.io.File target = (document != null && document.getURL() != null)
				? com.dogsbay.dogsbayaieditor.URLUtilities.toFile(document.getURL()) : null;

		if (target == null || !target.isFile()) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"Open and save a document first — Schematron runs against the file on disk.",
					"Schematron", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		// Flush the editor buffer to the model and to disk first: the engine reads
		// the file, so unsaved edits would otherwise be checked in their last-saved
		// state and the results would silently describe stale content.
		if (getView() != null) {
			getView().updateModel();
		}

		javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(target.getParentFile());
		chooser.setDialogTitle("Choose a Schematron schema (.sch)");
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
				"Schematron schema (*.sch)", "sch"));
		if (chooser.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
			return;
		}
		final java.io.File schema = chooser.getSelectedFile();
		final java.io.File document_ = target;
		outputPanel.startProjectCheck(
				"Running Schematron \"" + schema.getName() + "\" on \""
				+ document_.getName() + "\" ...");

		new javax.swing.SwingWorker<
				com.dogsbay.dogsbayaieditor.ditaproject.BatchResult<
						com.dogsbay.dogsbayaieditor.commands.results.SchematronFinding>, Void>() {
			@Override
			protected com.dogsbay.dogsbayaieditor.ditaproject.BatchResult<
					com.dogsbay.dogsbayaieditor.commands.results.SchematronFinding>
					doInBackground() throws Exception {
				return new com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor()
						.execute(new com.dogsbay.dogsbayaieditor.commands.SchematronCommand(
								document_.getAbsolutePath(), schema.getAbsolutePath()));
			}

			@Override
			protected void done() {
				try {
					var result = get();
					for (com.dogsbay.xml.XMLError err :
							com.dogsbay.dogsbayaieditor.validate.ProjectValidationErrors
									.fromSchematron(result)) {
						outputPanel.addProjectError(err);
					}
					outputPanel.endProjectCheck(result.isClean()
							? "No rule matches in \"" + document_.getName() + "\"."
							: (result.findings().size() + result.truncated())
									+ " rule match(es) in \"" + document_.getName() + "\".");
					outputPanel.selectProjectTab();
				} catch (Exception ex) {
					outputPanel.endProjectCheck(
							"Schematron run failed: " + ex.getMessage());
				}
			}
		}.execute();
	}

	/**
	 * Deep-validate <em>all</em> the project's deliverables with DITA-OT (the
	 * engine tier: runs the real preprocessing pipeline, applying each
	 * deliverable's DITAVAL) and report diagnostics in the Project Validation pane,
	 * click-to-open. Menu: XML &gt; Validate with DITA-OT — All Deliverables.
	 */
	public void validateAllDeliverablesWithDitaOt() {
		java.io.File root = ditaOtValidationRoot();
		if (root == null) {
			return;
		}
		final String ditaOtPath = ditaOtPathOrWarn();
		if (ditaOtPath == null) {
			return;
		}
		runDitaOtValidation(root, "Deep-validating all deliverables of \""
				+ root.getName() + "\" with DITA-OT (full preprocess, this can take a "
				+ "moment) ...",
				() -> new com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor()
						.execute(new com.dogsbay.dogsbayaieditor.commands.ValidateDeepCommand(
								root.getAbsolutePath(), null, null, ditaOtPath)));
	}

	/**
	 * Deep-validate the <em>current deliverable</em>: the active deliverable from
	 * the {@link DeliverableService} if set, else the map loaded in the DITA Map
	 * Explorer, else the primary deliverable. One preprocess — faster than
	 * validating every deliverable. Menu: XML &gt; Validate with DITA-OT — Current Map.
	 */
	public void validateCurrentMapWithDitaOt() {
		java.io.File root = ditaOtValidationRoot();
		if (root == null) {
			return;
		}
		final String ditaOtPath = ditaOtPathOrWarn();
		if (ditaOtPath == null) {
			return;
		}
		// Resolve the target on the EDT (reads Swing + the active selection).
		refreshDeliverableContext();
		com.dogsbay.dogsbayaieditor.ditaproject.Deliverable active =
				deliverableService.getActiveDeliverable();
		String map = null;
		String label;
		if (active != null) {
			map = active.map().toString();
			label = "\"" + active.name() + "\"";
		} else {
			com.dogsbay.dogsbayaieditor.dita.ui.DitaExplorerPanel explorer = getDitaExplorer();
			java.io.File currentMap =
					explorer != null ? explorer.getCurrentMapFile() : null;
			if (currentMap != null) {
				map = currentMap.getAbsolutePath();
				label = "\"" + currentMap.getName() + "\"";
			} else {
				label = "the primary deliverable";
			}
		}
		final String mapArg = map;
		runDitaOtValidation(root,
				"Deep-validating " + label + " with DITA-OT ...",
				() -> {
					String m = mapArg != null ? mapArg : primaryDeliverableMap(root, ditaOtPath);
					return new com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor()
							.execute(new com.dogsbay.dogsbayaieditor.commands.ValidateDeepCommand(
									root.getAbsolutePath(), null, m, ditaOtPath));
				});
	}

	/**
	 * Check controlled values (subjectScheme) across the whole project: scan every
	 * source file for profiling-attribute values — and DITAVAL conditions — that
	 * aren't in the scheme's vocabulary, and report them in the Project Validation
	 * pane (click-to-open). Violations are source-level, not deliverable-specific,
	 * so this scans all source in one pass; the governing scheme is discovered from
	 * the active deliverable's / default root map's closure. No scheme ⇒ nothing to
	 * flag. Contributed to the Project menu by DitaPlugin.
	 */
	public void validateConditions() {
		java.io.File root = getFileExplorer() != null
				? getFileExplorer().getRootDirectory() : null;
		if (root == null || !root.isDirectory()) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"Open a project folder first (File → Open Folder).",
					"Controlled Values", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		// A controlled-value violation is a property of the source, not of one
		// deliverable's filtered view, so scan the WHOLE project in one pass. We
		// still need a root map to discover the governing scheme (its closure
		// contains the subjectScheme) — prefer the active deliverable's map, else
		// the current map, else the project's default root map. The scheme is
		// project-global in v1, so any of these resolves the same vocabulary.
		refreshDeliverableContext();
		java.io.File schemeMap = null;
		com.dogsbay.dogsbayaieditor.ditaproject.Deliverable active =
				deliverableService != null ? deliverableService.getActiveDeliverable() : null;
		if (active != null && active.map() != null) {
			schemeMap = active.map().isAbsolute()
					? active.map().toFile()
					: new java.io.File(root, active.map().toString());
		}
		if (schemeMap == null || !schemeMap.isFile()) {
			com.dogsbay.dogsbayaieditor.dita.ui.DitaExplorerPanel explorer = getDitaExplorer();
			schemeMap = explorer != null ? explorer.getCurrentMapFile() : null;
		}
		if (schemeMap == null || !schemeMap.isFile()) {
			schemeMap = getDefaultRootMapFile();
		}
		if (schemeMap == null || !schemeMap.isFile()) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"Open a DITA map (or set a default root map) so the subject scheme "
					+ "can be resolved.",
					"Controlled Values", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		final String schemePath = schemeMap.getAbsolutePath();
		final java.io.File projectRoot = root;
		outputPanel.startProjectCheck("Checking controlled values across the project "
				+ "(scheme from \"" + schemeMap.getName() + "\") ...");

		new javax.swing.SwingWorker<
				com.dogsbay.dogsbayaieditor.ditaproject.BatchResult<
						com.dogsbay.dogsbayaieditor.commands.results.ConditionViolation>, Void>() {
			@Override
			protected com.dogsbay.dogsbayaieditor.ditaproject.BatchResult<
					com.dogsbay.dogsbayaieditor.commands.results.ConditionViolation>
					doInBackground() throws Exception {
				return new com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor()
						.execute(new com.dogsbay.dogsbayaieditor.commands.ValidateConditionsCommand(
								projectRoot.getAbsolutePath(), "root", schemePath));
			}

			@Override
			protected void done() {
				try {
					var result = get();
					for (com.dogsbay.xml.XMLError err :
							com.dogsbay.dogsbayaieditor.validate.ProjectValidationErrors
									.fromConditions(result)) {
						outputPanel.addProjectError(err);
					}
					outputPanel.endProjectCheck(
							result.total() + " file(s): " + result.passed() + " pass, "
							+ result.failed() + " with controlled-value issues"
							+ (result.truncated() > 0
									? " (+" + result.truncated() + " more)" : ""));
					outputPanel.selectProjectTab();
				} catch (Exception ex) {
					outputPanel.endProjectCheck(
							"Controlled-value check failed: " + ex.getMessage());
				}
			}
		}.execute();
	}

	/**
	 * Audit the project's metadata against its required-metadata policy
	 * (.dogsbay/config.xml), reporting violations in the Project Validation pane
	 * (click-to-open). No policy ⇒ nothing flagged. Contributed to the Project menu
	 * by DitaPlugin.
	 */
	public void auditMetadata() {
		java.io.File root = getFileExplorer() != null
				? getFileExplorer().getRootDirectory() : null;
		if (root == null || !root.isDirectory()) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"Open a project folder first (File → Open Folder).",
					"Metadata", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		final java.io.File projectRoot = root;
		outputPanel.startProjectCheck("Auditing metadata in \"" + root.getName() + "\" ...");

		new javax.swing.SwingWorker<
				com.dogsbay.dogsbayaieditor.ditaproject.BatchResult<
						com.dogsbay.dogsbayaieditor.commands.results.MetadataFinding>, Void>() {
			@Override
			protected com.dogsbay.dogsbayaieditor.ditaproject.BatchResult<
					com.dogsbay.dogsbayaieditor.commands.results.MetadataFinding>
					doInBackground() throws Exception {
				return new com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor()
						.execute(new com.dogsbay.dogsbayaieditor.commands.MetadataAuditCommand(
								projectRoot.getAbsolutePath(), "root", null));
			}

			@Override
			protected void done() {
				try {
					var result = get();
					for (com.dogsbay.xml.XMLError err :
							com.dogsbay.dogsbayaieditor.validate.ProjectValidationErrors
									.fromMetadata(result)) {
						outputPanel.addProjectError(err);
					}
					outputPanel.endProjectCheck(
							result.total() + " file(s): " + result.passed() + " pass, "
							+ result.failed() + " with metadata issues"
							+ (result.truncated() > 0
									? " (+" + result.truncated() + " more)" : ""));
					outputPanel.selectProjectTab();
				} catch (Exception ex) {
					outputPanel.endProjectCheck("Metadata audit failed: " + ex.getMessage());
				}
			}
		}.execute();
	}

	/**
	 * Export the project's required-metadata policy as ISO Schematron to a file the
	 * user chooses — portable validation for CI / DITA-OT / oXygen. Contributed to
	 * the Project menu by DitaPlugin.
	 */
	public void exportMetadataSchematron() {
		java.io.File root = getFileExplorer() != null
				? getFileExplorer().getRootDirectory() : null;
		if (root == null || !root.isDirectory()) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"Open a project folder first (File → Open Folder).",
					"Metadata", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(root);
		chooser.setDialogTitle("Export metadata policy as Schematron");
		chooser.setSelectedFile(new java.io.File(root, "metadata-policy.sch"));
		if (chooser.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
			return;
		}
		java.io.File out = chooser.getSelectedFile();
		try {
			new com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor().execute(
					new com.dogsbay.dogsbayaieditor.commands.ExportMetadataSchematronCommand(
							root.getAbsolutePath(), null, out.getAbsolutePath()));
			javax.swing.JOptionPane.showMessageDialog(this,
					"Wrote " + out.getName() + ".",
					"Metadata", javax.swing.JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception ex) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"Export failed: " + ex.getMessage(),
					"Metadata", javax.swing.JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Normalize metadata across the project: a small form (field · value · mode ·
	 * dry-run) drives a field-preserving {@code metadata_set} over all topics.
	 * Contributed to the Project menu by DitaPlugin.
	 */
	public void normalizeMetadata() {
		java.io.File root = getFileExplorer() != null
				? getFileExplorer().getRootDirectory() : null;
		if (root == null || !root.isDirectory()) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"Open a project folder first (File → Open Folder).",
					"Metadata", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String[] fields = java.util.Arrays.stream(
				com.dogsbay.dogsbayaieditor.links.metadata.MetadataField.values())
				.map(com.dogsbay.dogsbayaieditor.links.metadata.MetadataField::key)
				.toArray(String[]::new);
		javax.swing.JComboBox<String> fieldBox = new javax.swing.JComboBox<>(fields);
		javax.swing.JTextField valueField = new javax.swing.JTextField(20);
		javax.swing.JComboBox<String> modeBox = new javax.swing.JComboBox<>(
				new String[] {"fill", "set", "append", "remove"});
		javax.swing.JCheckBox dryRun = new javax.swing.JCheckBox("Dry run (preview only)", true);
		javax.swing.JPanel form = new javax.swing.JPanel(new java.awt.GridLayout(0, 2, 4, 4));
		form.add(new javax.swing.JLabel("Field:"));     form.add(fieldBox);
		form.add(new javax.swing.JLabel("Value:"));     form.add(valueField);
		form.add(new javax.swing.JLabel("Mode:"));      form.add(modeBox);
		form.add(new javax.swing.JLabel(""));           form.add(dryRun);

		if (javax.swing.JOptionPane.showConfirmDialog(this, form, "Normalize Metadata",
				javax.swing.JOptionPane.OK_CANCEL_OPTION,
				javax.swing.JOptionPane.PLAIN_MESSAGE) != javax.swing.JOptionPane.OK_OPTION) {
			return;
		}
		var spec = new com.dogsbay.dogsbayaieditor.commands.MetadataSetSpec(
				(String) fieldBox.getSelectedItem(), valueField.getText(),
				(String) modeBox.getSelectedItem());
		try {
			var result = new com.dogsbay.dogsbayaieditor.commands.HeadlessExecutor().execute(
					new com.dogsbay.dogsbayaieditor.commands.MetadataSetCommand(
							root.getAbsolutePath(), "root", java.util.List.of(spec),
							dryRun.isSelected()));
			javax.swing.JOptionPane.showMessageDialog(this,
					(dryRun.isSelected() ? "[dry run] " : "")
					+ result.failed() + " file(s) " + (dryRun.isSelected() ? "would change" : "changed")
					+ ", " + result.passed() + " unchanged.",
					"Normalize Metadata", javax.swing.JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception ex) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"Normalize failed: " + ex.getMessage(),
					"Metadata", javax.swing.JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Dismiss popups that X11/Wayland leave stranded over other applications when
	 * our app loses focus: open menu-bar menus and {@link javax.swing.JPopupMenu}s
	 * (via the menu-selection manager), any visible tooltip, and open
	 * {@link javax.swing.JComboBox} dropdowns in every app window (combo popups
	 * aren't managed by the menu-selection manager, so they need an explicit sweep).
	 */
	static void dismissTransientPopups() {
		javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
		javax.swing.ToolTipManager tips = javax.swing.ToolTipManager.sharedInstance();
		boolean enabled = tips.isEnabled();
		tips.setEnabled(false); // hides any visible tooltip
		tips.setEnabled(enabled);
		for (java.awt.Window w : java.awt.Window.getWindows()) {
			if (w.isVisible()) {
				hideOpenComboPopups(w);
			}
		}
	}

	private static void hideOpenComboPopups(java.awt.Container c) {
		for (java.awt.Component comp : c.getComponents()) {
			if (comp instanceof javax.swing.JComboBox<?> combo) {
				if (combo.isPopupVisible()) {
					combo.setPopupVisible(false);
				}
			} else if (comp instanceof java.awt.Container child) {
				hideOpenComboPopups(child);
			}
		}
	}

	/**
	 * Open the relationship-table grid editor for the active deliverable's map (or
	 * the default root map). Edits route through the {@code edit_reltable} command
	 * engine. Contributed to the Project menu by DitaPlugin.
	 */
	public void editReltables() {
		java.io.File map = null;
		var active = deliverableService != null
				? deliverableService.getActiveDeliverable() : null;
		if (active != null && active.map() != null) {
			map = active.map().toFile();
		}
		if (map == null || !map.isFile()) {
			map = getDefaultRootMapFile();
		}
		if (map == null || !map.isFile()) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"No DITA map found — open a project with a root map or active deliverable.",
					"Relationship Tables", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		javax.swing.JDialog dialog = new javax.swing.JDialog(this,
				"Relationship Tables — " + map.getName(), false);
		dialog.setLayout(new java.awt.BorderLayout());
		dialog.add(new com.dogsbay.dogsbayaieditor.dita.ui.ReltableEditorPanel(map, this),
				java.awt.BorderLayout.CENTER);
		dialog.setSize(760, 440);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	/**
	 * Open the structural map editor (topicref tree + property form) for the active
	 * deliverable's map (or the default root map). Edits route through the
	 * {@code edit_map} command engine. Contributed to the Project menu by DitaPlugin.
	 */
	public void editMapStructure() {
		java.io.File map = null;
		var active = deliverableService != null
				? deliverableService.getActiveDeliverable() : null;
		if (active != null && active.map() != null) {
			map = active.map().toFile();
		}
		if (map == null || !map.isFile()) {
			map = getDefaultRootMapFile();
		}
		if (map == null || !map.isFile()) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"No DITA map found — open a project with a root map or active deliverable.",
					"Map Structure", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		javax.swing.JDialog dialog = new javax.swing.JDialog(this,
				"Map Structure — " + map.getName(), false);
		dialog.setLayout(new java.awt.BorderLayout());
		dialog.add(new com.dogsbay.dogsbayaieditor.dita.ui.MapEditorPanel(map, this),
				java.awt.BorderLayout.CENTER);
		dialog.setSize(820, 480);
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	/**
	 * Edit the project's required-metadata policy in a rules table and write it back
	 * to the shared {@code .dogsbay/config.xml} (preserving the other project
	 * settings). Contributed to the Project menu by DitaPlugin.
	 */
	public void editMetadataPolicy() {
		java.io.File root = getFileExplorer() != null
				? getFileExplorer().getRootDirectory() : null;
		if (root == null || !root.isDirectory()) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"Open a project folder first (File → Open Folder).",
					"Metadata Policy", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig config =
				com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig.load(root.toPath());

		String[] cols = {"Topic type (blank = any)", "Field", "Presence",
				"Allowed values (space-sep)", "Pattern"};
		javax.swing.table.DefaultTableModel model =
				new javax.swing.table.DefaultTableModel(cols, 0);
		for (var r : config.getMetadataPolicy().rules()) {
			model.addRow(new Object[] {
					r.topicType() == null ? "" : r.topicType(),
					r.field().key(),
					r.presence().name().toLowerCase(java.util.Locale.ROOT),
					String.join(" ", r.allowedValues()),
					r.pattern() == null ? "" : r.pattern()});
		}
		String[] fields = java.util.Arrays.stream(
				com.dogsbay.dogsbayaieditor.links.metadata.MetadataField.values())
				.map(com.dogsbay.dogsbayaieditor.links.metadata.MetadataField::key)
				.toArray(String[]::new);
		javax.swing.JTable table = new javax.swing.JTable(model);
		table.getColumnModel().getColumn(1).setCellEditor(
				new javax.swing.DefaultCellEditor(new javax.swing.JComboBox<>(fields)));
		table.getColumnModel().getColumn(2).setCellEditor(new javax.swing.DefaultCellEditor(
				new javax.swing.JComboBox<>(new String[] {"required", "recommended", "forbidden"})));

		javax.swing.JButton add = new javax.swing.JButton("Add rule");
		add.addActionListener(e -> model.addRow(
				new Object[] {"", fields[0], "required", "", ""}));
		javax.swing.JButton remove = new javax.swing.JButton("Remove selected");
		remove.addActionListener(e -> {
			if (table.isEditing()) {
				table.getCellEditor().stopCellEditing();
			}
			int row = table.getSelectedRow();
			if (row >= 0) {
				model.removeRow(row);
			}
		});
		javax.swing.JPanel buttons = new javax.swing.JPanel(
				new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
		buttons.add(add);
		buttons.add(remove);
		javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.BorderLayout(0, 6));
		panel.add(new javax.swing.JScrollPane(table), java.awt.BorderLayout.CENTER);
		panel.add(buttons, java.awt.BorderLayout.SOUTH);
		panel.setPreferredSize(new java.awt.Dimension(640, 300));

		if (javax.swing.JOptionPane.showConfirmDialog(this, panel,
				"Edit Metadata Policy (.dogsbay/config.xml)",
				javax.swing.JOptionPane.OK_CANCEL_OPTION,
				javax.swing.JOptionPane.PLAIN_MESSAGE) != javax.swing.JOptionPane.OK_OPTION) {
			return;
		}
		if (table.isEditing()) {
			table.getCellEditor().stopCellEditing();
		}

		java.util.List<com.dogsbay.dogsbayaieditor.links.metadata.MetadataRule> rules =
				new java.util.ArrayList<>();
		for (int i = 0; i < model.getRowCount(); i++) {
			String fieldName = cell(model, i, 1);
			var field = com.dogsbay.dogsbayaieditor.links.metadata.MetadataField
					.byKey(fieldName);
			if (field == null) {
				continue; // blank/unknown field row — skip
			}
			String tt = cell(model, i, 0);
			var presence = com.dogsbay.dogsbayaieditor.links.metadata.MetadataRule.Presence
					.parse(cell(model, i, 2));
			String allowed = cell(model, i, 3);
			java.util.List<String> allowedValues = allowed.isBlank()
					? java.util.List.of() : java.util.List.of(allowed.trim().split("\\s+"));
			String pattern = cell(model, i, 4);
			rules.add(new com.dogsbay.dogsbayaieditor.links.metadata.MetadataRule(
					tt.isBlank() ? null : tt, field, presence, allowedValues,
					pattern.isBlank() ? null : pattern));
		}
		config.setMetadataPolicy(
				new com.dogsbay.dogsbayaieditor.links.metadata.MetadataPolicy(rules));
		try {
			config.saveShared(root.toPath());
			javax.swing.JOptionPane.showMessageDialog(this,
					"Saved " + rules.size() + " rule(s) to .dogsbay/config.xml.",
					"Metadata Policy", javax.swing.JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception ex) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"Failed to save policy: " + ex.getMessage(),
					"Metadata Policy", javax.swing.JOptionPane.ERROR_MESSAGE);
		}
	}

	private static String cell(javax.swing.table.DefaultTableModel m, int row, int col) {
		Object v = m.getValueAt(row, col);
		return v == null ? "" : v.toString().trim();
	}

	/** The open project folder, or null after warning the user none is open. */
	private java.io.File ditaOtValidationRoot() {
		java.io.File root = getFileExplorer() != null
				? getFileExplorer().getRootDirectory() : null;
		if (root == null || !root.isDirectory()) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"Open a project folder first (File → Open Folder).",
					"Validate with DITA-OT", javax.swing.JOptionPane.INFORMATION_MESSAGE);
			return null;
		}
		return root;
	}

	/** The resolved DITA-OT path, or null after warning that none is configured. */
	private String ditaOtPathOrWarn() {
		String ditaOtPath = getDitaOtPath();
		if (ditaOtPath == null || ditaOtPath.trim().isEmpty()) {
			javax.swing.JOptionPane.showMessageDialog(this,
					"DITA-OT is not configured. Import the DITA-OT framework (or set its "
					+ "path in project properties) to use deep validation.",
					"Validate with DITA-OT", javax.swing.JOptionPane.WARNING_MESSAGE);
			return null;
		}
		return ditaOtPath;
	}

	/** The primary (first) deliverable's map path, or null if none — used as the
	 * "Current Map" fallback when the Map Explorer has no map loaded. */
	private String primaryDeliverableMap(java.io.File root, String ditaOtPath) {
		try {
			java.util.List<java.nio.file.Path> catalogs = new java.util.ArrayList<>();
			java.nio.file.Path bundled = com.dogsbay.dogsbayaieditor.validate
					.DocumentValidator.builtinDitaCatalog();
			if (bundled != null) {
				catalogs.add(bundled);
			}
			var ctx = com.dogsbay.dogsbayaieditor.ditaproject.ProjectContextLoader
					.load(root.toPath(), null, catalogs,
							ditaOtPath != null ? java.nio.file.Path.of(ditaOtPath) : null);
			var d = ctx.defaultDeliverable();
			return d != null ? d.map().toString() : null;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Run a DITA-OT deep validation off the EDT and pour its diagnostics into the
	 * Project Validation pane (de-duplicated across deliverables), click-to-open.
	 * Shared by the All-Deliverables and Current-Map actions.
	 */
	private void runDitaOtValidation(java.io.File projectRoot, String startMessage,
			java.util.concurrent.Callable<java.util.List<
					com.dogsbay.dogsbayaieditor.commands.results.DitaOtValidation>> task) {
		outputPanel.startProjectCheck(startMessage);
		new javax.swing.SwingWorker<
				java.util.List<com.dogsbay.dogsbayaieditor.commands.results.DitaOtValidation>,
				Void>() {
			@Override
			protected java.util.List<
					com.dogsbay.dogsbayaieditor.commands.results.DitaOtValidation>
					doInBackground() throws Exception {
				return task.call();
			}

			@Override
			protected void done() {
				try {
					var results = get();
					// startProjectCheck() already reset the project error list.
					for (com.dogsbay.xml.XMLError err :
							com.dogsbay.dogsbayaieditor.validate.ProjectValidationErrors
									.fromDitaOt(results, projectRoot)) {
						outputPanel.addProjectError(err);
					}
					int deliverables = results.size();
					long failed = results.stream().filter(r -> !r.success()).count();
					int messages = results.stream()
							.mapToInt(r -> r.messages().size()).sum();
					outputPanel.endProjectCheck(deliverables + " deliverable(s): "
							+ (deliverables - failed) + " ok, " + failed
							+ " with errors (" + messages + " message(s))");
					outputPanel.selectProjectTab();
				} catch (Exception ex) {
					Throwable cause = (ex instanceof java.util.concurrent.ExecutionException
							&& ex.getCause() != null) ? ex.getCause() : ex;
					outputPanel.endProjectCheck(
							"DITA-OT validation failed: " + cause.getMessage());
				}
			}
		}.execute();
	}

	public DogsBayView getView(DogsBayDocument document) {
		return viewManager.getView(document);
	}

	public DogsBayView getView() {
		return viewManager.getView();
	}

	public DogsBayView getPreviousView() {
		return viewManager.getPreviousView();
	}

	/**
	 * Gets the currently selected tabbed view container.
	 *
	 * @return the selected tabbed view
	 */
	public DogsBayTabbedView getSelectedTabbedView() {
		return viewManager.getSelectedTabbedView();
	}

	public void addBookmark(Bookmark bookmark) {
		BookmarkExplorerPanel be = getBookmarkExplorer();
		if (be != null) {
			be.addBookmark(bookmark);
		}
	}

	public void removeBookmark(Bookmark bookmark) {
		BookmarkExplorerPanel be = getBookmarkExplorer();
		if (be != null) {
			be.removeBookmark(bookmark);
		}
	}

	public BookmarkExplorerPanel getBookmarkExplorer() {
		com.dogsbay.dogsbayaieditor.plugin.bookmarks.BookmarksPlugin p = pluginManager != null
				? pluginManager.getPlugin(com.dogsbay.dogsbayaieditor.plugin.bookmarks.BookmarksPlugin.class) : null;
		return p != null ? p.getBookmarkExplorer() : null;
	}

	public void setView(DogsBayView tab) {
		viewManager.setView(tab);
	}

	public void setCurrent(ViewPanel view) {
		viewManager.setCurrent(view);
	}

	public void setTitle(DogsBayView view) {
		StringBuffer title = new StringBuffer(TITLE);

		if (view != null) {
			DogsBayDocument doc = view.getDocument();

			title.append(" - [");

			URL url = doc.getURL();

			if (url != null) {
				title.append(URLUtilities.toRelativeString(url));
			} else {
				title.append(doc.getName());
			}

			title.append("]");
		}

		setTitle(title.toString());
	}

	public void select(DogsBayView view) {
		viewManager.select(view);
	}

	public void select(DogsBayDocument document) {
		viewManager.select(document);
	}

	public void setStatus(final String status) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				statusbar.setStatus(status);
			}
		});
	}

	public void setViewIcon(DogsBayView view, Icon icon) {
		viewManager.setViewIcon(view, icon);
	}

	public void setViewTitle(DogsBayView view, String title) {
		viewManager.setViewTitle(view, title);
	}

	public Vector getViews() {
		return viewManager.getViews();
	}

	/**
	 * Checks all open documents for external modifications.
	 * Clean buffers are silently reloaded. Dirty buffers on the active tab
	 * are prompted; dirty buffers on background tabs are deferred to tab focus.
	 */
	public void checkAllDocumentsForExternalChanges() {
		Vector views = getViews();
		DogsBayView activeView = getView();

		for (int i = 0; i < views.size(); i++) {
			DogsBayView view = (DogsBayView) views.elementAt(i);

			if (view == activeView) {
				// Active tab: silently reload clean, prompt dirty
				view.checkExternalModification();
			} else {
				// Background tab: only silently reload clean buffers
				view.checkExternalModificationSilent();
			}
		}
	}

	public void closeAll() {
		documentManager.closeAll();
		// documentManager.closeAll() only closes document views; also drop the
		// non-document tabs (Welcome, previews) so "Close All" really clears them —
		// but ONLY if every document actually closed. If the user cancelled a
		// dirty-save prompt, documents remain open, so leave the rest intact.
		Vector remaining = getViews();
		if (remaining != null && !remaining.isEmpty()) {
			return;
		}
		Vector tvs = getTabbedViews();
		if (tvs != null) {
			for (int i = 0; i < tvs.size(); i++) {
				((DogsBayTabbedView) tvs.elementAt(i)).closeCustomPanels();
			}
		}
	}

	public void exit() {
		if (pluginManager != null) {
			pluginManager.saveState(getProperties());
			pluginManager.deactivateAll();
		}
		documentManager.exit();
	}

	private void saveCurrentSession() {
		documentManager.saveCurrentSession();
	}

	private void saveProperties() {
		documentManager.saveProperties();
	}

	public ChangeManager getChangeManager() {
		if (getView() != null) {
			return getView().getChangeManager();
		}

		return null;
	}

	public void open(URL url, GrammarProperties type, boolean monitorProgress) {
		documentManager.open(url, type, monitorProgress);
	}

	public void open(DogsBayDocument document, GrammarProperties type) {
		documentManager.open(document, type);
	}

	public void open(DogsBayDocument document, SchemaDocument schema, Vector tagCompletionSchemas,
			GrammarProperties type) {
		documentManager.open(document, schema, tagCompletionSchemas, type);
	}

	public int closeNotThreaded(DogsBayView view, int previousResult, int numberOfViews) {
		return documentManager.closeNotThreaded(view, previousResult, numberOfViews);
	}

	/**
	 * Close a view.
	 *
	 * @param currentView  the view to close.
	 * @param checkChanged true when the app should check if the view is
	 *                     changed.
	 *
	 * @return false when close was cancelled.
	 */
	public boolean close(final DogsBayView view) {
		return documentManager.close(view);
	}

	public SchemaDocument getSchema() {
		return schemaManager.getSchema();
	}

	public Statusbar getStatusbar() {
		return statusbar;
	}

	/*
	 * public void show() {
	 * SwingUtilities.invokeLater(new Runnable() {
	 * public void run() {
	 * DogsBayAIEditor.super.setVisible(true);
	 * }
	 * });
	 * 
	 * if (macFile != null && !started) {
	 * started = true;
	 * URL url = macFile;
	 * macFile = null;
	 * 
	 * open(url, null, false);
	 * } else {
	 * started = true;
	 * macFile = null;
	 * }
	 * 
	 * setIntialFocus();
	 * }
	 */

	public void setVisible(boolean value) {
		if (value == true) {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					DogsBayAIEditor.super.setVisible(true);

					// Re-apply divider locations after layout completes,
					// since setDividerLocation(pixels) before the window is
					// visible gets overridden by Swing's layout manager.
					SwingUtilities.invokeLater(() -> {
						if (split != null) {
							split.setDividerLocation(restoredSidebarDivider());
						}
						sizeRightPanelOnce();

						// Open the last project folder now that UI is visible
						if (pendingProjectFolder != null) {
							fileExplorer.setRootDirectory(pendingProjectFolder);
							if (getGitPanel() != null) {
								getGitPanel().refreshRepository();
							}
							pendingProjectFolder = null;
						}
						// Resolve the active deliverable AFTER this layout/divider pass
						// settles (its own EDT cycle), so the status-bar update can't
						// perturb the session-restore layout.
						SwingUtilities.invokeLater(() -> {
							refreshDeliverableContext();
							// Restore the Map Explorer's map (last-open, else default root
							// map) so a reopened DITA project isn't blank until the tab is
							// clicked. Does not touch the active deliverable.
							com.dogsbay.dogsbayaieditor.dita.ui.DitaExplorerPanel ditaEx = getDitaExplorer();
							if (ditaEx != null) {
								ditaEx.restoreMapForCurrentProject();
							}
						});

					});
				}
			});

			if (macFile != null && !started) {
				started = true;
				URL url = macFile;
				macFile = null;

				open(url, null, false);
			} else {
				started = true;
				macFile = null;
			}

			setIntialFocus();
		} else {
			super.setVisible(value);
		}
	}

	public void setIntialFocus() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (toolbarManager.getButtonOpen() != null)
					toolbarManager.getButtonOpen().grabFocus();
			}
		});
	}

	public void updatePreferences() {
		Vector views = getViews();

		Vector catalogs = getProperties().getCatalogs();
		StringBuffer catalogFiles = new StringBuffer();

		for (int i = 0; i < catalogs.size(); i++) {
			catalogFiles.append((String) catalogs.elementAt(i));
			catalogFiles.append(";");
		}
		// System.out.println( "xml.catalog.files="+catalogFiles.toString());
		System.setProperty("xml.catalog.files", catalogFiles.toString());

		if (getProperties().isPreferPublicIdentifiers()) {
			System.setProperty("xml.catalog.prefer", "public");
		} else {
			System.setProperty("xml.catalog.prefer", "system");
		}

		// String laf = properties.getLookAndFeel();
		// String currentLaf = UIManager.getLookAndFeel().getClass().getName();
		//
		// System.out.println( "laf = "+laf);
		// System.out.println( "currentLaf = "+currentLaf);
		//
		// if ( laf != null && laf.length() > 0 && (currentLaf == null ||
		// !currentLaf.equals( laf))) {
		// try {
		// UIManager.setLookAndFeel( laf);
		// SwingUtilities.updateComponentTreeUI( this );
		// } catch (Exception e) {
		// e.printStackTrace();
		// }
		// }

		navigator.updatePreferences();
		if (getSidebarViewer() != null) {
			getSidebarViewer().updatePreferences();
		}
		helper.updatePreferences();
		outputPanel.updatePreferences();
		getProjectPanel().updatePreferences();

		DogsBayXMLWriter.setAttributeOnNewLine(
				getProperties().isAttributesNewLine());

		if (getProperties().getEditorProperties().isWrapText()) {
			DogsBayXMLWriter.setMaxLineLength(
					getProperties().getEditorProperties().getWrappingColumn());
		} else {
			DogsBayXMLWriter.setMaxLineLength(-1);
		}

		// DogsBayXMLWriter.setIndentSize(
		// properties.getTextPreferences().getTabSize());
		DogsBayXMLWriter.setIndentString(TextPreferences.getTabString());

		for (int i = 0; i < views.size(); i++) {
			((DogsBayView) views.elementAt(i)).updatePreferences();
		}

		for (int i = 0; i < getTabbedViews().size(); i++) {
			((DogsBayTabbedView) getTabbedViews().elementAt(i)).setScrollTabs(getProperties().isScrollDocumentTabs());
		}

		// PHASE 3: Debugger disabled during Saxon upgrade
		// getDebugger().updatePreferences();

		// getSchemaInstanceGenerationAction().updatePreferences();

	}

	public void updateIcons() {
		IconFactory.reset();

		Vector views = getViews();

		getProjectPanel().updatePreferences();

		for (int i = 0; i < views.size(); i++) {
			((DogsBayView) views.elementAt(i)).setViewIcons();
		}

		// Note: Dynamic icon changing not supported with vertical button bar
		// The navigator always uses its standard icon
		// Icon icon = selectedTabbedView.getSelectedIcon();
	}

	public void setGrammar(GrammarProperties grammar) {
		schemaManager.setGrammar(grammar);
	}

	public GrammarProperties getGrammar() {
		return schemaManager.getGrammar();
	}

	public void setDocument(DogsBayDocument document) {
		if (getView() != null) {
			getView().setDocument(document);
			updateStatus();
		}

		setDocumentInternal(document, false);
	}

	public void setDocumentInternal(DogsBayDocument document, boolean output) {
		SchemaDocument schema = getSchema();

		if (this.document != null) {
			this.document.removeListener(this);
		}

		this.document = document;

		if (document != null) {
			document.addListener(this);

			if (document.isXML() && !document.isHTML()) {
				if (output) {
					outputPanel.startCheck("WF", "[" + FileUtilities.getXercesVersion() + "] Checking \""
							+ document.getName() + "\" for Well-formedness ...");
				}

				if (!document.isError()) {
					if (output) {
						outputPanel.endCheck("WF", "Well-formed Document.");
						outputPanel.selectParseTab();
					}
				} else if (output) {
					Exception e = document.getError();

					if (e instanceof SAXParseException) {
						outputPanel.setError("WF", (SAXParseException) e);
						outputPanel.endCheck("WF", "1 Error");
						outputPanel.selectParseTab();
					} else if (e instanceof IOException) {
						outputPanel.setError("WF", (IOException) e);
						outputPanel.endCheck("WF", "1 Error");
						outputPanel.selectParseTab();
					}
				}

			} else {
				if (output) {
					outputPanel.setErrorList(null);
				}

			}

			// browserButton.setEnabled(true);
			// getBrowserViewItem().setEnabled(true);

		} else {
			helper.clear();

			if (output) {
				outputPanel.setErrorList(null);
			}

		}
		// navigator.setDocument(document);

		updateActions(document);
		updateStatus();
	}

	// Implementation of the DogsBayDocumentListener interface...
	public void documentUpdated(DogsBayDocumentEvent event) {

		// perform on event dispacth thread...
		Runnable runner = new Runnable() {
			public void run() {
				if (document != null) {
					if (document.isXML() && !document.isHTML()) {
						outputPanel.startCheck("WF", "[" + FileUtilities.getXercesVersion() + "] Checking \""
								+ document.getName() + "\" for Well-formedness ...");

						if (document.isError()) {
							Exception e = document.getError();

							if (e instanceof SAXParseException) {
								outputPanel.setError("WF", (SAXParseException) e);
								outputPanel.endCheck("WF", "1 Error");
								outputPanel.selectParseTab();
							} else if (e instanceof IOException) {
								outputPanel.setError("WF", (IOException) e);
								outputPanel.endCheck("WF", "1 Error");
								outputPanel.selectParseTab();
							}
						} else {
							outputPanel.endCheck("WF", "Well-formed Document.");
							outputPanel.selectParseTab();
						}

					} else { // Not XML or HTML

					}
				}

				updateActions(document);
				updateStatus();
				getView().documentUpdated();

				// Publish event for plugins
				if (document != null) {
					eventBus.publish(new com.dogsbay.dogsbayaieditor.services.events.DocumentModifiedEvent(document));
				}
			}
		};

		if (SwingUtilities.isEventDispatchThread()) {
			runner.run();
		} else {
			SwingUtilities.invokeLater(runner);
		}

		// make sure this runs after the gui is updated!
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				System.gc();
			}
		});
	}

	// ED: FIX ME, have the actions updating themselves, depending on the document
	// state.
	private void updateActions(DogsBayDocument document) {
		if (document != null) {
			toolbarManager.updateEditorToolbar(document);
			getCleanUpHTMLAction().setEnabled(true);
			getResolveXIncludesAction().setEnabled(true);
			getSetXMLDeclarationAction().setEnabled(true);
			getSetXMLDoctypeAction().setEnabled(true);
			getChangeDocumentAction().setEnabled(true);
			getOpenBrowserAction().setEnabled(true);
			getOpenPreviewAction().setEnabled(true);
			getOpenPreviewSplitAction().setEnabled(true);
			getShowEditorViewAction().setEnabled(true);
			getShowAuthorViewAction().setEnabled(!document.isError() && document.isXML());
			getToggleAuthorSplitAction().setEnabled(!document.isError() && document.isXML());

			getToolsStripTextAction().setEnabled(true);
			getToolsCapitalizeAction().setEnabled(true);
			getToolsDeCapitalizeAction().setEnabled(true);
			getToolsLowercaseAction().setEnabled(true);
			getToolsUppercaseAction().setEnabled(true);
			getToolsMoveNSToRootAction().setEnabled(true);
			getToolsMoveNSToFirstUsedAction().setEnabled(true);
			getToolsChangeNSPrefixAction().setEnabled(true);
			getToolsRenameNodeAction().setEnabled(true);
			getToolsRemoveNodeAction().setEnabled(true);
			getToolsAddNodeToNamespaceAction().setEnabled(true);
			getToolsSetNodeValueAction().setEnabled(true);
			getToolsAddNodeAction().setEnabled(true);
			getToolsRemoveUnusedNSAction().setEnabled(true);
			getToolsConvertNodeAction().setEnabled(true);
			getToolsSortNodeAction().setEnabled(true);

			if (document.isXML()) {
				if (!document.isError()) {
					getParseAction().setParsed(!getView().getChangeManager().isTextChanged());
					getValidateRelaxNGAction().setEnabled(isRelaxNGDocument(document));

				} else {
					getParseAction().setParsed(false);
					getValidateRelaxNGAction().setEnabled(false);

					// if ( document.getName().endsWith( "htm") || document.getName().endsWith(
					// "html")) {
					// getOpenBrowserAction().setEnabled(true);
					// } else {
					// getOpenBrowserAction().setEnabled(false);
					// }
				}
			} else {
				getParseAction().setParsed(false);
				getValidateRelaxNGAction().setEnabled(false);
				getResolveXIncludesAction().setEnabled(false);
				getSetXMLDeclarationAction().setEnabled(false);
				getSetXMLDoctypeAction().setEnabled(false);

				getToolsStripTextAction().setEnabled(false);
				getToolsCapitalizeAction().setEnabled(false);
				getToolsDeCapitalizeAction().setEnabled(false);
				getToolsLowercaseAction().setEnabled(false);
				getToolsUppercaseAction().setEnabled(false);
				getToolsMoveNSToRootAction().setEnabled(false);
				getToolsMoveNSToFirstUsedAction().setEnabled(false);
				getToolsChangeNSPrefixAction().setEnabled(false);
				getToolsRenameNodeAction().setEnabled(false);
				getToolsRemoveNodeAction().setEnabled(false);
				getToolsAddNodeToNamespaceAction().setEnabled(false);
				getToolsSetNodeValueAction().setEnabled(false);
				getToolsAddNodeAction().setEnabled(false);
				getToolsRemoveUnusedNSAction().setEnabled(false);
				getToolsConvertNodeAction().setEnabled(false);
				getToolsSortNodeAction().setEnabled(false);

				// if ( document.getName().endsWith( "htm") || document.getName().endsWith(
				// "html")) {
				// getOpenBrowserAction().setEnabled(true);
				// } else {
				// getOpenBrowserAction().setEnabled(false);
				// }
			}

			if (document.isDTD()) {
				getValidateDTDAction().setEnabled(true);
			} else {
				getValidateDTDAction().setEnabled(false);
			}

			getPrintAction().setEnabled(true);
		} else {
			toolbarManager.updateEditorToolbar(null);
			getValidateRelaxNGAction().setEnabled(false);
			getOpenBrowserAction().setEnabled(false);
			getOpenPreviewAction().setEnabled(false);
			getOpenPreviewSplitAction().setEnabled(false);
			getShowEditorViewAction().setEnabled(false);
			getShowAuthorViewAction().setEnabled(false);
			getToggleAuthorSplitAction().setEnabled(false);
			getPrintAction().setEnabled(false);
			getParseAction().setParsed(false);
			getValidateDTDAction().setEnabled(false);
			getResolveXIncludesAction().setEnabled(false);
			getSetXMLDeclarationAction().setEnabled(false);
			getSetXMLDoctypeAction().setEnabled(false);
			getCleanUpHTMLAction().setEnabled(false);
			getChangeDocumentAction().setEnabled(false);

			getToolsStripTextAction().setEnabled(false);
			getToolsCapitalizeAction().setEnabled(false);
			getToolsDeCapitalizeAction().setEnabled(false);
			getToolsLowercaseAction().setEnabled(false);
			getToolsUppercaseAction().setEnabled(false);
			getToolsMoveNSToRootAction().setEnabled(false);
			getToolsMoveNSToFirstUsedAction().setEnabled(false);
			getToolsChangeNSPrefixAction().setEnabled(false);
			getToolsRenameNodeAction().setEnabled(false);
			getToolsRemoveNodeAction().setEnabled(false);
			getToolsAddNodeToNamespaceAction().setEnabled(false);
			getToolsSetNodeValueAction().setEnabled(false);
			getToolsAddNodeAction().setEnabled(false);
			getToolsRemoveUnusedNSAction().setEnabled(false);
			getToolsConvertNodeAction().setEnabled(false);
			getToolsSortNodeAction().setEnabled(false);
		}

		getSaveAction().setDocument(document);
		getSaveAllAction().setDocument(document);
		getSaveAsAction().setDocument(document);
		getSaveAsRemoteAction().setDocument(document);
		getSaveAsTemplateAction().setDocument(document);

		getSelectElementAction().setDocument(document);
		getSelectElementContentAction().setDocument(document);

		getGotoStartTagAction().setDocument(document);
		getHighlightAction().setDocument(document);
		getToggleEmptyElementAction().setDocument(document);
		getRenameElementAction().setDocument(document);
		getGotoEndTagAction().setDocument(document);
		getGotoNextAttributeValueAction().setDocument(document);
		getGotoPreviousAttributeValueAction().setDocument(document);

		getTagAction().setDocument(document);
		getRepeatTagAction().setDocument(document);
		getCDATAAction().setDocument(document);
		getCommentAction().setDocument(document);
		getLockAction().setDocument(document);
		getValidateAction().setDocument(document);
		getInsertEntityAction().setDocument(document);
		getSubstituteCharactersAction().setDocument(document);
		getSubstituteEntitiesAction().setDocument(document);
		getStripTagsAction().setDocument(document);
		getSplitElementAction().setDocument(document);
		getFormatAction().setDocument(document);
		getReflowSentencesAction().setDocument(document);
		getParseAction().setDocument(document);

		// for each of the plugin buttons
		for (int cnt = 0; cnt < this.getPluginViews().size(); ++cnt) {
			Object obj = this.getPluginViews().get(cnt);
			if ((obj != null) && (obj instanceof PluginView)) {
				PluginView pluginView = (PluginView) obj;
				if (pluginView != null) {

					List actionList = pluginView.getActions();
					for (int acnt = 0; acnt < actionList.size(); ++acnt) {
						PluginActionKeyMapping pluginAction = (PluginActionKeyMapping) actionList.get(acnt);
						pluginAction.getAction().updateActions(document);
					}
				}
			}
		}

		ChangeManager changeManager = getChangeManager();

		if (changeManager != null) {
			getValidateAction().setValidated(changeManager.isValidated());
		} else {
			getValidateAction().setValidated(false);
		}

		updateGrammarActions();
	}

	public void documentDeleted(DogsBayDocumentEvent event) {
	}

	public void setSchemaInternal(SchemaDocument schema) {
		schemaManager.setSchemaInternal(schema);
	}

	public void setSchema(SchemaDocument schema) {
		schemaManager.setSchema(schema);
	}

	public void setTagCompletionSchemas(Vector schemas) {
		schemaManager.setTagCompletionSchemas(schemas);
	}

	// PHASE 3: Disabled XSLT Debugger during Saxon upgrade
	// public void setDebugger(XSLTDebuggerFrame debugger) {
	// this.debugger = debugger;
	// }

	// public XSLTDebuggerFrame getDebugger() {
	// return debugger;
	// }

	public void createView(final DogsBayDocument document, SchemaDocument schema, Vector tagCompletionSchemas,
			GrammarProperties grammar) {
		if (DEBUG) {
			System.out.println("createView (document, schema, tagCompletionSchemas, grammar)");
		}
		viewManager.setPreviousView(getView());
		DogsBayView newView = new DogsBayView(this, getProperties());
		viewManager.setCurrentView(newView);

		outputPanel.setErrorList(newView.getErrors());

		// newView.setDocument(document);
		// updateStatus();

		setDocumentInternal(document, !restoringSession);
		newView.setDocument(document);

		setDocument(document);
		setSchema(schema);
		setTagCompletionSchemas(tagCompletionSchemas);
		setGrammar(grammar);

		SwingUtilities.invokeLater(
				new Runnable() {
					public void run() {
						getSelectedTabbedView().add(getView(), document.getName());
						getView().initBookmarks();

						getView().getEditorButton().setSelected(true);
						getView().getEditorViewItem().setSelected(true);
						getView().switchToEditor();

						getView().setViewIcons();

						getSelectedTabbedView().select(getView());
					}
				});
	}


	public Vector openTagCompletionSchemas(Vector list) {
		return schemaManager.openTagCompletionSchemas(list);
	}

	public void removeView(DogsBayView view) {
		documentManager.removeView(view);
	}

	public void switchToViewer() throws Exception {
		viewManager.switchToViewer();
	}

	public void switchToUserView(UserView newUserView) throws Exception {
		viewManager.switchToUserView(newUserView);
	}

	public void switchToPluginView(PluginView pluginView) throws Exception {
		viewManager.switchToPluginView(pluginView);
	}

	public void switchToPluginView(PluginViewPanel pluginViewPanel) throws Exception {
		viewManager.switchToPluginView(pluginViewPanel);
	}


	public void switchToAuthor() throws Exception {
		viewManager.switchToAuthor();
	}

	public void switchToAuthorSplit() throws Exception {
		viewManager.switchToAuthorSplit();
	}


	public void switchToEditor() {
		viewManager.switchToEditor();
	}

	// Toolbar creation and management delegated to ToolbarManager



	public void updateFragments() {
		if (getView() != null && getCurrent() instanceof Editor) {
			((Editor) getCurrent()).updateFragmentKeys();
		}
	}


	public void updateProperties() {
		if (getView() != null) {
			String validationLocation = PropertiesPanel.INTERNAL_GRAMMAR;

			if (getView().getValidationGrammar().useExternal()) {
				validationLocation = getView().getValidationGrammar().getLocation();
			}

			String tagCompletionLocation = PropertiesPanel.NO_LOCATION;

			Vector schemas = getView().getTagCompletionSchemas();
			Vector tagCompletionLocations = new Vector();
			if (schemas != null) {
				for (int i = 0; i < schemas.size(); i++) {
					tagCompletionLocations.addElement(((SchemaDocument) schemas.elementAt(i)).getURL().toString());
				}
			}

			String schemaLocation = PropertiesPanel.NO_LOCATION;

			SchemaDocument schema = getView().getSchema();
			if (schema != null) {
				schemaLocation = schema.getURL().toString();
			}

			if (getView().getGrammar() != null) {
				propertiesPanel.setName(getView().getGrammar().getDescription());
			} else {
				propertiesPanel.setName("");
			}

			if (document != null) {
				propertiesPanel.setEncoding(document.getEncoding());
			}

			propertiesPanel.setValidationLocation(validationLocation);
			propertiesPanel.setTagCompletionLocations(tagCompletionLocations);
			propertiesPanel.setSchemaViewerLocation(schemaLocation);
		} else {
			propertiesPanel.clear();
		}
	}

	public void updateStatus() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (getView() != null) {
					statusbar.setType(getView().getStatusType());
					statusbar.setValidator(getView().getStatusValidator());
					statusbar.setLocation(getView().getStatusLocation());
					statusbar.setDocumentStatus(getView().getStatusDocumentStatus());
					statusbar.setPosition(getView().getEditor().getPosition().x,
							getView().getEditor().getPosition().y);
				} else {
					statusbar.setType("");
					statusbar.setValidator("");
					statusbar.setLocation("");
					statusbar.clearPosition();
					statusbar.setDocumentStatus("");
				}

				updateProperties();
			}
		});
	}

	public void setSelected(DogsBayTabbedView tabs) {
		viewManager.setSelected(tabs);
	}

	public TagAction getTagAction() {
		return actionRegistry.getTagAction();
	}

	public RepeatTagAction getRepeatTagAction() {
		return actionRegistry.getRepeatTagAction();
	}

	public void splitHorizontally() {
		viewManager.splitHorizontally();
	}

	public void unsplit() {
		viewManager.unsplit();
	}

	public void splitVertically() {
		viewManager.splitVertically();
	}

	public SelectElementAction getSelectElementAction() {
		return actionRegistry.getSelectElementAction();
	}

	public SelectElementContentAction getSelectElementContentAction() {
		return actionRegistry.getSelectElementContentAction();
	}

	public HighlightAction getHighlightAction() {
		return actionRegistry.getHighlightAction();
	}

	public GotoStartTagAction getGotoStartTagAction() {
		return actionRegistry.getGotoStartTagAction();
	}

	public GotoEndTagAction getGotoEndTagAction() {
		return actionRegistry.getGotoEndTagAction();
	}

	public ToggleEmptyElementAction getToggleEmptyElementAction() {
		return actionRegistry.getToggleEmptyElementAction();
	}

	public RenameElementAction getRenameElementAction() {
		return actionRegistry.getRenameElementAction();
	}

	public GotoNextAttributeValueAction getGotoNextAttributeValueAction() {
		return actionRegistry.getGotoNextAttributeValueAction();
	}

	public GotoPreviousAttributeValueAction getGotoPreviousAttributeValueAction() {
		return actionRegistry.getGotoPreviousAttributeValueAction();
	}

	public ToolsStripTextAction getToolsStripTextAction() {
		return actionRegistry.getToolsStripTextAction();
	}

	public ToolsCapitalizeAction getToolsCapitalizeAction() {
		return actionRegistry.getToolsCapitalizeAction();
	}

	public ToolsDeCapitalizeAction getToolsDeCapitalizeAction() {
		return actionRegistry.getToolsDeCapitalizeAction();
	}

	public ToolsLowercaseAction getToolsLowercaseAction() {
		return actionRegistry.getToolsLowercaseAction();
	}

	public ToolsUppercaseAction getToolsUppercaseAction() {
		return actionRegistry.getToolsUppercaseAction();
	}

	public ToolsMoveNSToRootAction getToolsMoveNSToRootAction() {
		return actionRegistry.getToolsMoveNSToRootAction();
	}

	public ToolsMoveNSToFirstUsedAction getToolsMoveNSToFirstUsedAction() {
		return actionRegistry.getToolsMoveNSToFirstUsedAction();
	}

	public ToolsChangeNSPrefixAction getToolsChangeNSPrefixAction() {
		return actionRegistry.getToolsChangeNSPrefixAction();
	}

	public ToolsRenameNodeAction getToolsRenameNodeAction() {
		return actionRegistry.getToolsRenameNodeAction();
	}

	public ToolsRemoveNodeAction getToolsRemoveNodeAction() {
		return actionRegistry.getToolsRemoveNodeAction();
	}

	public ToolsAddNodeToNamespaceAction getToolsAddNodeToNamespaceAction() {
		return actionRegistry.getToolsAddNodeToNamespaceAction();
	}

	public ToolsSetNodeValueAction getToolsSetNodeValueAction() {
		return actionRegistry.getToolsSetNodeValueAction();
	}

	public ToolsAddNodeAction getToolsAddNodeAction() {
		return actionRegistry.getToolsAddNodeAction();
	}

	public ToolsRemoveUnusedNSAction getToolsRemoveUnusedNSAction() {
		return actionRegistry.getToolsRemoveUnusedNSAction();
	}

	public ToolsConvertNodeAction getToolsConvertNodeAction() {
		return actionRegistry.getToolsConvertNodeAction();
	}

	public ToolsSortNodeAction getToolsSortNodeAction() {
		return actionRegistry.getToolsSortNodeAction();
	}

	public CommentAction getCommentAction() {
		return actionRegistry.getCommentAction();
	}

	public LockAction getLockAction() {
		return actionRegistry.getLockAction();
	}

	public CDATAAction getCDATAAction() {
		return actionRegistry.getCDATAAction();
	}

	public GotoAction getGotoAction() {
		return actionRegistry.getGotoAction();
	}

	public ToggleBookmarkAction getToggleBookmarkAction() {
		return actionRegistry.getToggleBookmarkAction();
	}

	public SelectBookmarkAction getSelectBookmarkAction() {
		return actionRegistry.getSelectBookmarkAction();
	}

	public SelectFragmentAction getSelectFragmentAction() {
		return actionRegistry.getSelectFragmentAction();
	}

	public ParseAction getParseAction() {
		return actionRegistry.getParseAction();
	}

	public ValidateAction getValidateAction() {
		return actionRegistry.getValidateAction();
	}


	public ValidateDTDAction getValidateDTDAction() {
		return actionRegistry.getValidateDTDAction();
	}

	public ValidateRelaxNGAction getValidateRelaxNGAction() {
		return actionRegistry.getValidateRelaxNGAction();
	}

	public CleanUpHTMLAction getCleanUpHTMLAction() {
		return actionRegistry.getCleanUpHTMLAction();
	}

	public SetXMLDeclarationAction getSetXMLDeclarationAction() {
		return actionRegistry.getSetXMLDeclarationAction();
	}

	public SetXMLDoctypeAction getSetXMLDoctypeAction() {
		return actionRegistry.getSetXMLDoctypeAction();
	}

	public ChangeDocumentAction getChangeDocumentAction() {
		return actionRegistry.getChangeDocumentAction();
	}


	public ResolveXIncludesAction getResolveXIncludesAction() {
		return actionRegistry.getResolveXIncludesAction();
	}

	public XDiffAction getXDiffAction() {
		return actionRegistry.getXDiffAction();
	}

	public OpenBrowserAction getOpenBrowserAction() {
		return actionRegistry.getOpenBrowserAction();
	}

	public OpenPreviewAction getOpenPreviewAction() {
		return actionRegistry.getOpenPreviewAction();
	}

	public com.dogsbay.dogsbayaieditor.actions.OpenPreviewSplitAction getOpenPreviewSplitAction() {
		return actionRegistry.getOpenPreviewSplitAction();
	}

	public com.dogsbay.dogsbayaieditor.actions.ShowEditorViewAction getShowEditorViewAction() {
		return actionRegistry.getShowEditorViewAction();
	}

	public com.dogsbay.dogsbayaieditor.actions.ShowAuthorViewAction getShowAuthorViewAction() {
		return actionRegistry.getShowAuthorViewAction();
	}

	public com.dogsbay.dogsbayaieditor.actions.ToggleAuthorSplitAction getToggleAuthorSplitAction() {
		return actionRegistry.getToggleAuthorSplitAction();
	}

	public PrintAction getPrintAction() {
		return actionRegistry.getPrintAction();
	}

	public PageSetupAction getPageSetupAction() {
		return actionRegistry.getPageSetupAction();
	}

	public InsertEntityAction getInsertEntityAction() {
		return actionRegistry.getInsertEntityAction();
	}

	public SubstituteEntitiesAction getSubstituteEntitiesAction() {
		return actionRegistry.getSubstituteEntitiesAction();
	}

	public StripTagsAction getStripTagsAction() {
		return actionRegistry.getStripTagsAction();
	}

	public SplitElementAction getSplitElementAction() {
		return actionRegistry.getSplitElementAction();
	}

	public SubstituteCharactersAction getSubstituteCharactersAction() {
		return actionRegistry.getSubstituteCharactersAction();
	}

	public IndentAction getIndentAction() {
		return actionRegistry.getIndentAction();
	}

	public UnindentAction getUnindentAction() {
		return actionRegistry.getUnindentAction();
	}

	public FormatAction getFormatAction() {
		return actionRegistry.getFormatAction();
	}

	public com.dogsbay.dogsbayaieditor.actions.ReflowSentencesAction getReflowSentencesAction() {
		return actionRegistry.getReflowSentencesAction();
	}


	// public void updateScenarioActions() {
	// DogsBayDocument document = getDocument();
	// Vector scenarios = properties.getScenarioProperties();

	// if ( document != null && scenarios.size() > 0) {
	// getExecuteScenarioAction().setEnabled( true);
	// getDefaultScenarioAction().setEnabled( true);
	// getOpenScenarioAction().setEnabled( true);
	// } else {
	// getDefaultScenarioAction().setEnabled( false);
	// getExecuteScenarioAction().setEnabled( false);
	// getOpenScenarioAction().setEnabled( false);
	// getNewScenarioAction().setEnabled( false);
	// }

	// if ( document != null) {
	// getNewScenarioAction().setEnabled( true);
	// }
	// }

	public void updateGrammarActions() {
		schemaManager.updateGrammarActions();
	}






	public UndoAction getUndoAction() {
		return actionRegistry.getUndoAction();
	}

	public RedoAction getRedoAction() {
		return actionRegistry.getRedoAction();
	}

	public CopyAction getCopyAction() {
		return actionRegistry.getCopyAction();
	}

	public CutAction getCutAction() {
		return actionRegistry.getCutAction();
	}

	public PasteAction getPasteAction() {
		return actionRegistry.getPasteAction();
	}

	public FindAction getFindAction() {
		return actionRegistry.getFindAction();
	}

	public ReplaceAction getReplaceAction() {
		return actionRegistry.getReplaceAction();
	}

	public FindNextAction getFindNextAction() {
		return actionRegistry.getFindNextAction();
	}

	public FindInFilesAction getFindInFilesAction() {
		return actionRegistry.getFindInFilesAction();
	}

	public ReplaceInFilesAction getReplaceInFilesAction() {
		return actionRegistry.getReplaceInFilesAction();
	}

	public Action getOpenAction() {
		return actionRegistry.getOpenAction();
	}

	public OpenFolderAction getOpenFolderAction() {
		return actionRegistry.getOpenFolderAction();
	}

	/** Open (or focus, if already open) the Welcome / getting-started tab. EDT only. */
	public void showWelcomeTab() {
		try {
			DogsBayTabbedView tv = getSelectedTabbedView();
			if (tv == null) {
				return;
			}
			// Reuse an existing Welcome tab rather than stacking duplicates — this
			// also lets Help → Welcome reopen it after it's been closed.
			if (tv.selectTabByName(WelcomePanel.TAB_NAME)) {
				return;
			}
			tv.addCustomPanel(new WelcomePanel(this), WelcomePanel.TAB_NAME, "Getting started");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Action getOpenSampleProjectAction() {
		return actionRegistry.getOpenSampleProjectAction();
	}

	public Action getOpenCurrentURLAction() {
		return actionRegistry.getOpenCurrentURLAction();
	}

	public Action getOpenRemoteDocumentAction() {
		return actionRegistry.getOpenRemoteDocumentAction();
	}

	public Action getCloseAction() {
		return actionRegistry.getCloseAction();
	}

	public SplitTabsHorizontallyAction getSplitTabsHorizontallyAction() {
		return actionRegistry.getSplitTabsHorizontallyAction();
	}

	public UnsplitTabsAction getUnsplitTabsAction() {
		return actionRegistry.getUnsplitTabsAction();
	}

	public SplitTabsVerticallyAction getSplitTabsVerticallyAction() {
		return actionRegistry.getSplitTabsVerticallyAction();
	}

	public CloseAllAction getCloseAllAction() {
		return actionRegistry.getCloseAllAction();
	}

	public ReloadAction getReloadAction() {
		return actionRegistry.getReloadAction();
	}

	public SaveAction getSaveAction() {
		return actionRegistry.getSaveAction();
	}

	public SaveAllAction getSaveAllAction() {
		return actionRegistry.getSaveAllAction();
	}

	public SaveAsAction getSaveAsAction() {
		return actionRegistry.getSaveAsAction();
	}

	public SaveAsRemoteAction getSaveAsRemoteAction() {
		return actionRegistry.getSaveAsRemoteAction();
	}

	public SaveAsTemplateAction getSaveAsTemplateAction() {
		return actionRegistry.getSaveAsTemplateAction();
	}

	public PreferencesAction getPreferencesAction() {
		return actionRegistry.getPreferencesAction();
	}

	public Action getNewAction() {
		return actionRegistry.getNewAction();
	}

	public Action getNewGrammarAction() {
		return actionRegistry.getNewGrammarAction();
	}






	// public GraphicalSchemaGenerationAction getGraphicalSchemaGenerationAction() {
	// if (graphicalSchema == null) {
	// graphicalSchema = new GraphicalSchemaGenerationAction(this, properties);
	// }
	//
	// return graphicalSchema;
	// }

	public Action getOpenGrammarAction() {
		return actionRegistry.getOpenGrammarAction();
	}

	public Action getGrammarPropertiesAction() {
		return actionRegistry.getGrammarPropertiesAction();
	}

	public Action getManageGrammarAction() {
		return actionRegistry.getManageGrammarAction();
	}

	public Action getDefaultScenarioAction() {
		return actionRegistry.getDefaultScenarioAction();
	}

	public ExecutePreviousScenarioAction getExecutePreviousScenarioAction() {
		return actionRegistry.getExecutePreviousScenarioAction();
	}

	// PHASE 3: Disabled XSLT Debugger during Saxon upgrade
	// public Action getDebugScenarioAction() {
	// if (debugScenario == null) {
	// debugScenario = new DebugScenarioAction(this, getProperties());
	// }
	//
	// return debugScenario;
	// }

	public ExecuteXSLTAction getExecuteAdvancedXSLTAction() {
		return actionRegistry.getExecuteAdvancedXSLTAction();
	}

	public ExecuteSimpleXSLTAction getExecuteSimpleXSLTAction() {
		return actionRegistry.getExecuteSimpleXSLTAction();
	}

	public ExecuteFOAction getExecuteFOAction() {
		return actionRegistry.getExecuteFOAction();
	}

	public ExecutePreviousFOAction getExecutePreviousFOAction() {
		return actionRegistry.getExecutePreviousFOAction();
	}

	// PHASE 3: Disabled XQuery during Saxon upgrade
	// public ExecuteXQueryAction getExecuteXQueryAction() {
	// if (executeXQuery == null) {
	// executeXQuery = new ExecuteXQueryAction(this);
	// }
	//
	// return executeXQuery;
	// }

	// PHASE 3: Disabled XQuery during Saxon upgrade
	// public ExecutePreviousXQueryAction getExecutePreviousXQueryAction() {
	// if (executePreviousXQuery == null) {
	// executePreviousXQuery = new ExecutePreviousXQueryAction(this);
	// }
	//
	// return executePreviousXQuery;
	// }

	public ExecutePreviousXSLTAction getExecutePreviousXSLTAction() {
		return actionRegistry.getExecutePreviousXSLTAction();
	}

	// private Action getExecuteScenarioAction() {
	// if ( executeScenario == null) {
	// executeScenario = new ExecuteScenarioAction( this, properties);
	// }
	//
	// return executeScenario;
	// }

	// private Action getOpenScenarioAction() {
	// if (openScenario == null) {
	// openScenario = new OpenScenarioAction(this, properties);
	// }
	//
	// return openScenario;
	// }
	//
	public Action getManageScenarioAction() {
		return actionRegistry.getManageScenarioAction();
	}

	public Action getManageTemplateAction() {
		return actionRegistry.getManageTemplateAction();
	}

	private Action getImportFromTextAction() {
		return actionRegistry.getImportFromTextAction();
	}

	private Action getImportFromExcelAction() {
		return actionRegistry.getImportFromExcelAction();
	}

	private Action getImportFromDBTableAction() {
		return actionRegistry.getImportFromDBTableAction();
	}

	private Action getImportFromSQLXMLAction() {
		return actionRegistry.getImportFromSQLXMLAction();
	}

	public ExpandAllAction getExpandAllAction() {
		return actionRegistry.getExpandAllAction();
	}

	public CollapseAllAction getCollapseAllAction() {
		return actionRegistry.getCollapseAllAction();
	}

	public SynchroniseSelectionAction getSynchroniseSelectionAction() {
		return actionRegistry.getSynchroniseSelectionAction();
	}

	public ToggleFullScreenAction getToggleFullScreenAction() {
		return actionRegistry.getToggleFullScreenAction();
	}

	public CopyErrorListAction getCopyErrorListAction() {
		return actionRegistry.getCopyErrorListAction();
	}

	public void showAboutDialog() {
		AboutDialog dialog = getAboutDialog();
		// dialog.setVisible(true);
		dialog.setVisible(true);
	}

	private AboutDialog getAboutDialog() {
		if (aboutDialog == null) {
			aboutDialog = new AboutDialog(this);
			aboutDialog.setLocationRelativeTo(this);
		}

		return aboutDialog;
	}

	public boolean isAutoSynchroniseSelection() {
		return getProperties().isAutoSyncSelection();
	}

	// public void setSubstitutes( Vector substitutes) {
	// substitutionList.setSubstitutes( substitutes);
	// }

	// public RootSelectionDialog getRootSelectionDialog() {
	// if ( rootDialog == null) {
	// rootDialog = new RootSelectionDialog( this);
	// rootDialog.setLocationRelativeTo( this);
	// }
	//
	// return rootDialog;
	// }

	/**
	 * Creates a menu item and registers it in the menu item map.
	 * Delegates to MenuBuilder.
	 */
	public JMenuItem createMenuItem(Action action, String actionName) {
		return menuBuilder.createMenuItem(action, actionName);
	}

	/**
	 * Returns the required MenuItem.
	 * Delegates to MenuBuilder.
	 */
	public DogsBayMenuItem getMenuItem(String actionName) {
		return menuBuilder.getMenuItem(actionName);
	}

	/** Put a key on the command with this id; see MenuBuilder.bindAccelerator. */
	public void bindAccelerator(String actionName, javax.swing.KeyStroke stroke) {
		if (menuBuilder != null) {
			menuBuilder.bindAccelerator(actionName, stroke);
		}
	}

	/**
	 * Returns the required JCheckBoxMenuItem.
	 * Delegates to MenuBuilder.
	 */
	public JCheckBoxMenuItem getCheckBoxItem(String actionName) {
		return menuBuilder.getCheckBoxItem(actionName);
	}

	/**
	 * Returns the required Action.
	 * Delegates to MenuBuilder.
	 */
	public Action getModeAction(String actionName) {
		return menuBuilder.getModeAction(actionName);
	}

	// *********************************************************
	// Grid Specific actions
	// *********************************************************

	/*
	 * public GridBridgeAddAttributeColumnAction
	 * getGridBridgeAddAttributeColumnAction() {
	 * 
	 * if (gridBridgeAddAttributeColumnAction == null) {
	 * gridBridgeAddAttributeColumnAction = new
	 * GridBridgeAddAttributeColumnAction();
	 * }
	 * 
	 * return gridBridgeAddAttributeColumnAction;
	 * }
	 * 
	 * public GridBridgeAddAttributeToSelectedAction
	 * getGridBridgeAddAttributeToSelectedAction() {
	 * 
	 * if (gridBridgeAddAttributeToSelectedAction == null) {
	 * gridBridgeAddAttributeToSelectedAction = new
	 * GridBridgeAddAttributeToSelectedAction();
	 * }
	 * 
	 * return gridBridgeAddAttributeToSelectedAction;
	 * }
	 * 
	 * public GridBridgeAddChildTableAction getGridBridgeAddChildTableAction() {
	 * 
	 * if (gridBridgeAddChildTableAction == null) {
	 * gridBridgeAddChildTableAction = new GridBridgeAddChildTableAction();
	 * }
	 * 
	 * return gridBridgeAddChildTableAction;
	 * }
	 * 
	 * public GridBridgeDeleteChildTableAction getGridBridgeDeleteChildTableAction()
	 * {
	 * 
	 * if (gridBridgeDeleteChildTableAction == null) {
	 * gridBridgeDeleteChildTableAction = new GridBridgeDeleteChildTableAction();
	 * }
	 * 
	 * return gridBridgeDeleteChildTableAction;
	 * }
	 * 
	 * public GridBridgeDeleteAttsAndTextAction
	 * getGridBridgeDeleteAttsAndTextAction() {
	 * 
	 * if (gridBridgeDeleteAttsAndTextAction == null) {
	 * gridBridgeDeleteAttsAndTextAction = new GridBridgeDeleteAttsAndTextAction();
	 * }
	 * 
	 * return gridBridgeDeleteAttsAndTextAction;
	 * }
	 * 
	 * public GridBridgeAddElementAfterAction getGridBridgeAddElementAfterAction() {
	 * 
	 * if (gridBridgeAddElementAfterAction == null) {
	 * gridBridgeAddElementAfterAction = new GridBridgeAddElementAfterAction();
	 * }
	 * 
	 * return gridBridgeAddElementAfterAction;
	 * }
	 * 
	 * public GridBridgeAddElementBeforeAction getGridBridgeAddElementBeforeAction()
	 * {
	 * 
	 * if (gridBridgeAddElementBeforeAction == null) {
	 * gridBridgeAddElementBeforeAction = new GridBridgeAddElementBeforeAction();
	 * }
	 * 
	 * return gridBridgeAddElementBeforeAction;
	 * }
	 * 
	 * public GridBridgeAddTextColumnAction getGridBridgeAddTextColumnAction() {
	 * 
	 * if (gridBridgeAddTextColumnAction == null) {
	 * gridBridgeAddTextColumnAction = new GridBridgeAddTextColumnAction();
	 * }
	 * 
	 * return gridBridgeAddTextColumnAction;
	 * }
	 * 
	 * public GridBridgeAddTextToSelectedAction
	 * getGridBridgeAddTextToSelectedAction() {
	 * 
	 * if (gridBridgeAddTextToSelectedAction == null) {
	 * gridBridgeAddTextToSelectedAction = new GridBridgeAddTextToSelectedAction();
	 * }
	 * 
	 * return gridBridgeAddTextToSelectedAction;
	 * }
	 * 
	 * public GridBridgeDeleteColumnAction getGridBridgeDeleteColumnAction() {
	 * 
	 * if (gridBridgeDeleteColumnAction == null) {
	 * gridBridgeDeleteColumnAction = new GridBridgeDeleteColumnAction();
	 * }
	 * 
	 * return gridBridgeDeleteColumnAction;
	 * }
	 * 
	 * public GridBridgeDeleteSelectedAttributeAction
	 * getGridBridgeDeleteSelectedAttributeAction() {
	 * 
	 * if (gridBridgeDeleteSelectedAttributeAction == null) {
	 * gridBridgeDeleteSelectedAttributeAction = new
	 * GridBridgeDeleteSelectedAttributeAction();
	 * }
	 * 
	 * return gridBridgeDeleteSelectedAttributeAction;
	 * }
	 * 
	 * public GridBridgeDeleteSelectedTextAction
	 * getGridBridgeDeleteSelectedTextAction() {
	 * 
	 * if (gridBridgeDeleteSelectedTextAction == null) {
	 * gridBridgeDeleteSelectedTextAction = new
	 * GridBridgeDeleteSelectedTextAction();
	 * }
	 * 
	 * return gridBridgeDeleteSelectedTextAction;
	 * }
	 * 
	 * public GridBridgeDeleteRowAction getGridBridgeDeleteElementAction() {
	 * 
	 * if (gridBridgeDeleteElementAction == null) {
	 * gridBridgeDeleteElementAction = new GridBridgeDeleteRowAction();
	 * }
	 * 
	 * return gridBridgeDeleteElementAction;
	 * }
	 * 
	 * public GridBridgeEditAttributeNameAction
	 * getGridBridgeEditAttributeNameAction() {
	 * 
	 * if (gridBridgeEditAttributeNameAction == null) {
	 * gridBridgeEditAttributeNameAction = new GridBridgeEditAttributeNameAction();
	 * }
	 * 
	 * return gridBridgeEditAttributeNameAction;
	 * }
	 * 
	 * public GridBridgeMoveRowDownAction getGridBridgeMoveRowDownAction() {
	 * 
	 * if (gridBridgeMoveRowDownAction == null) {
	 * gridBridgeMoveRowDownAction = new GridBridgeMoveRowDownAction();
	 * }
	 * 
	 * return gridBridgeMoveRowDownAction;
	 * }
	 * 
	 * public GridBridgeMoveRowUpAction getGridBridgeMoveRowUpAction() {
	 * 
	 * if (gridBridgeMoveRowUpAction == null) {
	 * gridBridgeMoveRowUpAction = new GridBridgeMoveRowUpAction();
	 * }
	 * 
	 * return gridBridgeMoveRowUpAction;
	 * }
	 * 
	 * public GridBridgeSortTableAscendingAction
	 * getGridBridgeSortTableAscendingAction() {
	 * 
	 * if (gridBridgeSortTableAscendingAction == null) {
	 * gridBridgeSortTableAscendingAction = new
	 * GridBridgeSortTableAscendingAction();
	 * }
	 * 
	 * return gridBridgeSortTableAscendingAction;
	 * }
	 * 
	 * public GridBridgeSortTableDescendingAction
	 * getGridBridgeSortTableDescendingAction() {
	 * 
	 * if (gridBridgeSortTableDescendingAction == null) {
	 * gridBridgeSortTableDescendingAction = new
	 * GridBridgeSortTableDescendingAction();
	 * }
	 * 
	 * return gridBridgeSortTableDescendingAction;
	 * }
	 * 
	 * public GridBridgeUnsortTableAction getGridBridgeUnsortTableAction() {
	 * 
	 * if (gridBridgeUnsortTableAction == null) {
	 * gridBridgeUnsortTableAction = new GridBridgeUnsortTableAction();
	 * }
	 * 
	 * return gridBridgeUnsortTableAction;
	 * }
	 * 
	 * public GridBridgeCopyShallowAction getGridBridgeCopyShallowAction() {
	 * 
	 * if (gridBridgeCopyShallowAction == null) {
	 * gridBridgeCopyShallowAction = new GridBridgeCopyShallowAction();
	 * }
	 * 
	 * return gridBridgeCopyShallowAction;
	 * }
	 * 
	 * public GridBridgePasteAsChildAction getGridBridgePasteAsChildAction() {
	 * 
	 * if (gridBridgePasteAsChildAction == null) {
	 * gridBridgePasteAsChildAction = new GridBridgePasteAsChildAction();
	 * }
	 * 
	 * return gridBridgePasteAsChildAction;
	 * }
	 * 
	 * public GridBridgePasteBeforeAction getGridBridgePasteBeforeAction() {
	 * 
	 * if (gridBridgePasteBeforeAction == null) {
	 * gridBridgePasteBeforeAction = new GridBridgePasteBeforeAction();
	 * }
	 * 
	 * return gridBridgePasteBeforeAction;
	 * }
	 * 
	 * public GridBridgePasteAfterAction getGridBridgePasteAfterAction() {
	 * 
	 * if (gridBridgePasteAfterAction == null) {
	 * gridBridgePasteAfterAction = new GridBridgePasteAfterAction();
	 * }
	 * 
	 * return gridBridgePasteAfterAction;
	 * }
	 * 
	 * public GridBridgeGotoChildTableAction getGridBridgeGotoChildTableAction() {
	 * 
	 * if (gridBridgeGotoChildTableAction == null) {
	 * gridBridgeGotoChildTableAction = new GridBridgeGotoChildTableAction();
	 * }
	 * 
	 * return gridBridgeGotoChildTableAction;
	 * }
	 * 
	 * public GridBridgeGotoParentTableAction getGridBridgeGotoParentTableAction() {
	 * 
	 * if (gridBridgeGotoParentTableAction == null) {
	 * gridBridgeGotoParentTableAction = new GridBridgeGotoParentTableAction();
	 * }
	 * 
	 * return gridBridgeGotoParentTableAction;
	 * }
	 * 
	 * public GridBridgeEditSelectedAttributeNameAction
	 * getGridBridgeEditSelectedAttributeNameAction() {
	 * 
	 * if (gridBridgeEditSelectedAttributeNameAction == null) {
	 * gridBridgeEditSelectedAttributeNameAction = new
	 * GridBridgeEditSelectedAttributeNameAction();
	 * }
	 * 
	 * return gridBridgeEditSelectedAttributeNameAction;
	 * }
	 * 
	 * public GridBridgeExpandRowAction getGridBridgeExpandRowAction() {
	 * 
	 * if (gridBridgeExpandRowAction == null) {
	 * gridBridgeExpandRowAction = new GridBridgeExpandRowAction();
	 * }
	 * 
	 * return gridBridgeExpandRowAction;
	 * }
	 * 
	 * public GridBridgeCollapseRowAction getGridBridgeCollapseRowAction() {
	 * 
	 * if (gridBridgeCollapseRowAction == null) {
	 * gridBridgeCollapseRowAction = new GridBridgeCollapseRowAction();
	 * }
	 * 
	 * return gridBridgeCollapseRowAction;
	 * }
	 * 
	 * public GridBridgeCollapseCurrentTableAction
	 * getGridBridgeCollapseCurrentTableAction() {
	 * 
	 * if (gridBridgeCollapseCurrentTableAction == null) {
	 * gridBridgeCollapseCurrentTableAction = new
	 * GridBridgeCollapseCurrentTableAction();
	 * }
	 * 
	 * return gridBridgeCollapseCurrentTableAction;
	 * }
	 * 
	 * public GridBridgeDeleteAction getGridBridgeDeleteAction() {
	 * 
	 * if (gridBridgeDeleteAction == null) {
	 * gridBridgeDeleteAction = new GridBridgeDeleteAction();
	 * }
	 * 
	 * return gridBridgeDeleteAction;
	 * }
	 */
	/**
	 * Returns the JTabbed pane
	 *
	 * @return tab The JTabbedPane
	 */
	public JTabbedPane getTabbedPane() {
		return null;
	}

	/**
	 * Sets the controller icon (legacy method - no-op with vertical button bar).
	 * Dynamic icon changing is not supported with the new vertical button bar
	 * design.
	 *
	 * @param icon the icon (ignored)
	 * @deprecated No longer supported with vertical button bar
	 */
	@Deprecated
	public void setControllerIcon(Icon icon) {
		// No-op: Dynamic icon changing not supported with vertical button bar
	}

	/*
	 * Gets an icon for the string.
	 */
	/**
	 * The application icon in every size we ship, largest last so a chooser that
	 * ignores the sizes still ends up with a usable one. Also set on the taskbar
	 * where the platform supports it, which is what puts it in the dock and the
	 * app switcher rather than only in the window frame.
	 */
	private java.util.List<java.awt.Image> appIcons() {
		java.util.List<java.awt.Image> images = new java.util.ArrayList<>();
		for (String path : ICONS) {
			ImageIcon icon = getIcon(path);
			if (icon != null && icon.getIconWidth() > 0) {
				images.add(icon.getImage());
			}
		}
		if (!images.isEmpty() && java.awt.Taskbar.isTaskbarSupported()) {
			try {
				java.awt.Taskbar.getTaskbar().setIconImage(images.get(images.size() - 1));
			} catch (UnsupportedOperationException | SecurityException ignored) {
				// Linux window managers mostly do not offer this; the window icon stands.
			}
		}
		return images;
	}

	public ImageIcon getIcon(String path) {
		if (icons == null) {
			icons = new Hashtable();
		}

		ImageIcon icon = (ImageIcon) icons.get(path);

		if (icon == null) {
			if (DEBUG)
				System.out.println("DogsBayAIEditor::getIcon - path: " + path);
			if (DEBUG)
				System.out.println("DogsBayAIEditor::getIcon - DogsBayImageLoader.get(): " + DogsBayImageLoader.get());
			icon = DogsBayImageLoader.get().getImage(path);
			icons.put(path, icon);
		}

		return icon;
	}

	public JToggleButton createLayoutToggleButton(ImageIcon icon, String tooltip) {
		JToggleButton button = new JToggleButton(icon);
		button.setToolTipText(tooltip);
		button.setFocusable(false);
		button.setPreferredSize(new Dimension(24, 24));
		button.setMaximumSize(new Dimension(24, 24));
		button.setMinimumSize(new Dimension(24, 24));
		button.setBorderPainted(false);
		button.setContentAreaFilled(false);
		button.setOpaque(false);
		return button;
	}

	public void togglePrimarySidebar() {
		explorerContainer.toggleMinimized();
		boolean visible = !explorerContainer.isMinimized();
		properties.setPrimarySidebarVisible(visible);
		if (togglePrimarySidebar != null) togglePrimarySidebar.setSelected(visible);
		if (menuBuilder.getTogglePrimarySidebarMenuItem() != null) menuBuilder.getTogglePrimarySidebarMenuItem().setSelected(visible);
	}

	public void toggleSecondarySidebar() {
		rightExplorerContainer.toggleMinimized();
		boolean visible = !rightExplorerContainer.isMinimized();
		properties.setSecondarySidebarVisible(visible);
		if (toggleSecondarySidebar != null) toggleSecondarySidebar.setSelected(visible);
		if (menuBuilder.getToggleSecondarySidebarMenuItem() != null) menuBuilder.getToggleSecondarySidebarMenuItem().setSelected(visible);
	}

	public void toggleBottomPanel() {
		boolean visible = !outputPanel.isVisible();
		if (!visible) {
			savedBottomPanelDivider = rightSplit.getDividerLocation();
			outputPanel.setVisible(false);
			rightSplit.setDividerSize(0);
		} else {
			outputPanel.setVisible(true);
			rightSplit.setDividerSize(6);
			if (savedBottomPanelDivider > 0) {
				rightSplit.setDividerLocation(savedBottomPanelDivider);
			} else {
				rightSplit.setDividerLocation(0.7);
			}
		}
		properties.setBottomPanelVisible(visible);
		if (toggleBottomPanel != null) toggleBottomPanel.setSelected(visible);
		if (menuBuilder.getToggleBottomPanelMenuItem() != null) menuBuilder.getToggleBottomPanelMenuItem().setSelected(visible);
	}

	/**
	 * Returns a sidebar icon tinted for the current theme.
	 * Dark theme: light gray (#C5C5C5), Light theme: dark gray (#424242).
	 */
	public ImageIcon getSidebarIcon(String path) {
		boolean dark = com.dogsbay.dogsbayaieditor.properties.TextPreferences.isDarkTheme();
		String key = path + (dark ? ":dark" : ":light");

		if (icons == null) {
			icons = new Hashtable();
		}

		ImageIcon icon = (ImageIcon) icons.get(key);
		if (icon == null) {
			ImageIcon base = getIcon(path);
			if (base != null) {
				Color tint = dark ? new Color(197, 197, 197) : new Color(66, 66, 66);
				icon = tintIcon(base, tint);
			}
			if (icon != null) {
				icons.put(key, icon);
			}
		}
		return icon;
	}

	/**
	 * Tints a grayscale icon to the given color, preserving alpha.
	 */
	private static ImageIcon tintIcon(ImageIcon source, Color tint) {
		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
				source.getIconWidth(), source.getIconHeight(),
				java.awt.image.BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.drawImage(source.getImage(), 0, 0, null);
		g.dispose();

		int tr = tint.getRed(), tg = tint.getGreen(), tb = tint.getBlue();
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				int argb = img.getRGB(x, y);
				int a = (argb >> 24) & 0xFF;
				if (a > 0) {
					img.setRGB(x, y, (a << 24) | (tr << 16) | (tg << 8) | tb);
				}
			}
		}
		return new ImageIcon(img);
	}

	public boolean isSOAPDocument(DogsBayDocument doc) {
		XElement root = doc.getRoot();

		if (root != null) {
			if (root.getName().equals("Envelope")) {
				if (root.getNamespaceURI().equals("http://schemas.xmlsoap.org/soap/envelope/")) {
					return true;
				} else if (root.getNamespaceURI().equals("http://www.w3.org/2001/12/soap-envelope")) {
					return true;
				}
			}
		}

		return false;
	}

	public boolean isSchemaDocument() {
		return isSchemaDocument(getDocument());
	}

	public boolean isRelaxNGDocument() {
		return isRelaxNGDocument(getDocument());
	}

	private boolean isSchemaDocument(DogsBayDocument doc) {
		if (doc != null) {
			XElement root = doc.getRoot();

			if (root != null) {
				if (root.getName().equals("schema")
						&& root.getNamespaceURI().equals("http://www.w3.org/2001/SchemaDocument")) {
					return true;
				}
			}
		}

		return false;
	}

	private Node synchronisedNode = null;
	private boolean isEndTag = false;
	private boolean restoringSession = false;

	public void synchronise(DogsBayView source, Node node, boolean endTag, int y) {
		if (menuBuilder.getSynchroniseSplits().isSelected() && menuBuilder.getSynchroniseSplits().isEnabled()
				&& (synchronisedNode != node || endTag != isEndTag) && source == getView()) {

			for (int i = 0; i < getTabbedViews().size(); i++) {
				DogsBayTabbedView tabbedView = (DogsBayTabbedView) getTabbedViews().elementAt(i);

				if (!tabbedView.isSelected()) {
					DogsBayView view = tabbedView.getSelectedView();

					if (view != null) {
						view.setSelectedNode(node, endTag, view.getDocument().getDeclaredNamespaces(), y);
					}
				}
			}

			synchronisedNode = node;
			isEndTag = endTag;
		}
	}

	private boolean isRelaxNGDocument(DogsBayDocument doc) {
		if (doc != null) {
			XElement root = doc.getRoot();

			if (root != null) {
				if (root.getName().equals("grammar")
						&& root.getNamespaceURI().equals("http://relaxng.org/ns/structure/1.0")) {
					return true;
				}
			}
		}

		return false;
	}

	private class MacOSApplicationAdapter implements ApplicationListener {
		private DogsBayAIEditor parent = null;

		public MacOSApplicationAdapter(DogsBayAIEditor parent) {
			this.parent = parent;
			Application app = new Application();
			app.setEnabledPreferencesMenu(true);
			app.addApplicationListener(this);
		}

		public void handleAbout(ApplicationEvent e) {
			e.setHandled(true);

			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					parent.showAboutDialog();
				}
			});
		}

		public void handleQuit(ApplicationEvent e) {
			e.setHandled(true);
			parent.exit();
		}

		public void handlePreferences(ApplicationEvent e) {
			e.setHandled(true);

			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					getPreferencesAction().execute();
				}
			});
		}

		public void handleOpenApplication(ApplicationEvent e) {
			// System.out.println(
			// "MacOSApplicationAdapter.handleOpenApplication(
			// [ApplicationEvent filename:"+e.getFilename()+"
			// handled:"+e.isHandled()+"])");
		}

		public void handleReOpenApplication(ApplicationEvent e) {
			// System.out.println(
			// "MacOSApplicationAdapter.handleReOpenApplication(
			// [ApplicationEvent filename:"+e.getFilename()+"
			// handled:"+e.isHandled()+"])");
		}

		public void handleOpenFile(final ApplicationEvent e) {
			e.setHandled(true);

			if (DogsBayAIEditor.started) {
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						try {
							File file = new File(e.getFilename());
							open(DogsBayURLUtilities.getURLFromFile(file), null, false);
						} catch (MalformedURLException e) {
							// Should not happen!
							e.printStackTrace();
						}
					}
				});
			} else {
				try {
					if (e.getFilename() != null) {
						File file = new File(e.getFilename());
						DogsBayAIEditor.macFile = com.dogsbay.xml.DogsBayURLUtilities.getURLFromFile(file);
					}
				} catch (MalformedURLException x) {
					// Should not happen!
					x.printStackTrace();
				}
			}
		}

		public void handlePrintFile(ApplicationEvent e) {
			// System.out.println( "MacOSApplicationAdapter.handlePrintFile(
			// [ApplicationEvent filename:"+e.getFilename()+"
			// handled:"+e.isHandled()+"])");
		}
	}

	public JMenu getEditMenu() {
		return menuBuilder.getEditMenu();
	}

	class FragmentAction extends AbstractAction {
		private FragmentProperties fragment = null;
		private Editor editor = null;

		public FragmentAction(Editor editor, FragmentProperties fragment) {
			super(fragment.getName());

			this.fragment = fragment;
			this.editor = editor;

			setIcon();
			// putValue( ACCELERATOR_KEY, action.getValue( ACCELERATOR_KEY));
			putValue(SHORT_DESCRIPTION, fragment.getName());
		}

		private void setIcon() {
			ImageIcon icon = null;

			try {
				icon = DogsBayImageLoader.get().getImage(new URL(fragment.getIcon()));

				if (icon.getIconHeight() != 16 || icon.getIconWidth() != 16) {
					icon = new ImageIcon(icon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH));
				}
			} catch (Exception e) {
				icon = null;
			}

			if (icon == null) {
				icon = DogsBayImageLoader.get().getImage("com/dogsbay/dogsbayaieditor/icons/DefaultFragmentIcon.gif");
			}

			putValue(SMALL_ICON, icon);
		}

		public void actionPerformed(ActionEvent e) {
			editor.insertFragment(fragment.isBlock(), fragment.getContent());
			editor.setFocus();
		}
	}

	/**
	 * 
	 */
	public JPanel getDocumentViewButtonPanel() {

		return (documentViewButtonPanel);

	}

	public void setDocumentViewButtonPanel(JPanel panel) {
		this.documentViewButtonPanel = panel;
	}

	public void updateDocumentViewButtonPanel() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				getButtonContainer().removeAll();
				getButtonContainer().add(getDocumentViewButtonPanel(), BorderLayout.SOUTH);

				getButtonContainer().invalidate();
				getButtonContainer().repaint();

				getNorthPanel().invalidate();
				getNorthPanel().revalidate();
				getNorthPanel().repaint();
			}
		});

	}

	public void updateDocumentViewMenu() {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				// getViewMenu().remove(0);
				// getViewMenu().insert(getDocumentViewsMenu(), 0);
				getViewMenu().invalidate();
				getViewMenu().repaint();
			}
		});
	}

	/**
	 * @return Returns the documentViewButtonGroup.
	 */
	public ButtonGroup getDocumentViewButtonGroup() {

		return documentViewButtonGroup;
	}

	/**
	 * @param documentViewButtonGroup The documentViewButtonGroup to set.
	 */
	public void setDocumentViewButtonGroup(ButtonGroup group) {

		this.documentViewButtonGroup = group;
	}

	/**
	 * @return Returns the toolbar.
	 */
	public JToolBar getToolbar() {
		return toolbarManager.getToolbar();
	}

	/**
	 * @param toolbar The toolbar to set.
	 */
	public void setToolbar(JToolBar toolbar) {
		toolbarManager.setToolbar(toolbar);
	}

	/**
	 * @param properties the properties to set
	 */
	public void setProperties(ConfigurationProperties properties) {

		this.properties = properties;
	}

	/**
	 * @return the properties
	 */
	public ConfigurationProperties getProperties() {

		return properties;
	}

	/**
	 * Resolves the DITA-OT home directory path using the following priority:
	 * <ol>
	 *   <li>The current project's {@code dita-ot-path} setting (project-specific override)</li>
	 *   <li>The first installed framework that has a non-null {@code dita-ot-path}</li>
	 *   <li>{@code null} — callers should show a clear error directing the user to import a DITA framework</li>
	 * </ol>
	 *
	 * @return the resolved DITA-OT home path, or {@code null} if none configured
	 */
	public String getDitaOtPath() {
		// 0. Project-local .dogsbay: a personal DITA-OT path override (local.xml).
		com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig dc = dogsbayConfig();
		if (dc != null && dc.getDitaOtPath() != null && !dc.getDitaOtPath().isBlank()) {
			return dc.getDitaOtPath();
		}

		// 1. Project-level override (selected project node)
		if (projectPanel != null) {
			com.dogsbay.dogsbayaieditor.project.ProjectNode selectedProject = projectPanel.getSelectedProject();
			if (selectedProject != null) {
				com.dogsbay.dogsbayaieditor.project.FolderProperties fp = selectedProject.getProperties();
				if (fp instanceof ProjectProperties) {
					String projectPath = ((ProjectProperties) fp).getDitaOtPath();
					if (projectPath != null && !projectPath.trim().isEmpty()) {
						return projectPath;
					}
				}
			}
		}

		Vector frameworks = properties.getFrameworkProperties();

		// 2. The framework the project requires by name (.dogsbay/config.xml),
		//    resolved to this machine's install — requirements are shared, paths aren't.
		if (dc != null && dc.getFramework() != null && !dc.getFramework().isBlank()) {
			for (int i = 0; i < frameworks.size(); i++) {
				FrameworkProperties framework = (FrameworkProperties) frameworks.elementAt(i);
				if (dc.getFramework().equals(framework.getName())) {
					String p = framework.getDitaOtPath();
					if (p != null && !p.trim().isEmpty()) {
						return p;
					}
				}
			}
		}

		// 3. First framework with a DITA-OT path
		for (int i = 0; i < frameworks.size(); i++) {
			FrameworkProperties framework = (FrameworkProperties) frameworks.elementAt(i);
			String frameworkPath = framework.getDitaOtPath();
			if (frameworkPath != null && !frameworkPath.trim().isEmpty()) {
				return frameworkPath;
			}
		}

		// 4. Not configured
		return null;
	}

	/**
	 * The Default Root Map for the current working context, used for DITA
	 * key resolution (preview, where-used, key navigation). Resolution order:
	 *
	 * <ol>
	 *   <li>The project selected in the Projects panel.</li>
	 *   <li>The configured project whose folder contains the active document
	 *       — so working from the File Explorer (no Projects-panel selection)
	 *       still picks up the project's root map.</li>
	 *   <li>The configured project whose folder contains the File Explorer's
	 *       root directory.</li>
	 * </ol>
	 *
	 * Relative maps resolve against the project folder. Null when nothing
	 * matches or the map file doesn't exist.
	 */
	public java.io.File getDefaultRootMapFile() {
		// 0. Project-local .dogsbay/config.xml (shared, relative to the workspace).
		java.io.File wsRoot = getFileExplorer() != null
				? getFileExplorer().getRootDirectory() : null;
		com.dogsbay.dogsbayaieditor.project.DogsbayProjectConfig dc = dogsbayConfig();
		if (dc != null && dc.getDefaultRootMap() != null && wsRoot != null) {
			java.io.File m = new java.io.File(wsRoot, dc.getDefaultRootMap());
			if (m.isFile()) {
				return m;
			}
		}

		// 1. Projects-panel selection
		if (projectPanel != null) {
			com.dogsbay.dogsbayaieditor.project.ProjectNode selectedProject = projectPanel.getSelectedProject();
			if (selectedProject != null) {
				com.dogsbay.dogsbayaieditor.project.FolderProperties fp = selectedProject.getProperties();
				if (fp instanceof ProjectProperties) {
					java.io.File map = com.dogsbay.dogsbayaieditor.project.ProjectRootMapResolver
							.rootMapFile((ProjectProperties) fp);
					if (map != null) {
						return map;
					}
				}
			}
		}

		Vector projects;
		try {
			projects = properties.getProjectProperties();
		} catch (Exception e) {
			return null;
		}

		// 2. Project containing the active document
		java.io.File activeFile = null;
		try {
			java.net.URL url = getDocument() != null ? getDocument().getURL() : null;
			if (url != null && "file".equals(url.getProtocol())) {
				activeFile = new java.io.File(url.toURI());
			}
		} catch (Exception ignored) {
			// unsaved / non-file document
		}
		java.io.File map = com.dogsbay.dogsbayaieditor.project.ProjectRootMapResolver.rootMapFile(
				com.dogsbay.dogsbayaieditor.project.ProjectRootMapResolver
						.projectContaining(projects, activeFile));
		if (map != null) {
			return map;
		}

		// 3. Project containing the File Explorer root
		try {
			java.io.File explorerRoot = getFileExplorer() != null
					? getFileExplorer().getRootDirectory() : null;
			return com.dogsbay.dogsbayaieditor.project.ProjectRootMapResolver.rootMapFile(
					com.dogsbay.dogsbayaieditor.project.ProjectRootMapResolver
							.projectContaining(projects, explorerRoot));
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * @param pluginViews the pluginViews to set
	 */
	public void setPluginViews(List pluginViews) {

		this.pluginViews = pluginViews;
	}

	/**
	 * @return the pluginViews
	 */
	public List getPluginViews() {

		if (pluginViews == null) {
			pluginViews = new ArrayList();
		}
		return pluginViews;
	}

	/**
	 * @param actions the actions to set
	 * @deprecated Use ActionRegistry directly
	 */
	public void setActions(List actions) {
		// Actions are now managed by ActionRegistry; this method is kept for compatibility
	}

	/**
	 * @return the ActionRegistry
	 */
	public ActionRegistry getActionRegistry() {
		return actionRegistry;
	}

	public MenuBuilder getMenuBuilder() {
		return menuBuilder;
	}

	public JPanel getToolbarPanel() {
		return toolbarManager.getToolbarPanel();
	}

	public JToolBar getEditorToolbar() {
		return toolbarManager.getEditorToolbar();
	}

	public JPanel getEditorToolbarPanel() {
		return toolbarManager.getEditorToolbarPanel();
	}

	public JToggleButton getHighlightButton() {
		return toolbarManager.getHighlightButton();
	}

	public ToolbarManager getToolbarManager() {
		return toolbarManager;
	}

	public void setTogglePrimarySidebar(JToggleButton button) {
		this.togglePrimarySidebar = button;
	}

	public void setToggleBottomPanel(JToggleButton button) {
		this.toggleBottomPanel = button;
	}

	public void setToggleSecondarySidebar(JToggleButton button) {
		this.toggleSecondarySidebar = button;
	}

	/**
	 * @return the actions
	 */
	public List getActions() {
		return actionRegistry.getActions();
	}

	public Action getAction(String identifier) {
		for (int cnt = 0; cnt < getActions().size(); ++cnt) {
			Object obj = getActions().get(cnt);
			if (obj instanceof Action) {
				String shortDescription = (String) ((Action) obj).getValue(Action.NAME);
				if (shortDescription != null) {
					if (shortDescription.equals(identifier)) {
						return ((Action) obj);
					}
				}
			}
		}
		return (null);
	}

	/**
	 * @param tipsModel the tipsModel to set
	 */
	public void setTipsModel(LatestNewsModel tipsModel) {

		this.tipsModel = tipsModel;
	}

	/**
	 * @return the tipsModel
	 */
	public LatestNewsModel getTipsModel() {

		return tipsModel;
	}

	/**
	 * @param projectPanel the projectPanel to set
	 */
	public void setProjectPanel(Project projectPanel) {

		this.projectPanel = projectPanel;
	}

	/**
	 * @return the projectPanel
	 */
	public Project getProjectPanel() {

		return projectPanel;
	}

	public void setExtensionClassLoader(ExtensionClassLoader _extensionClassLoader) {
		extensionClassLoader = _extensionClassLoader;
	}

	public ExtensionClassLoader getExtensionClassLoader() {
		return extensionClassLoader;
	}

	public static ExtensionClassLoader getStaticExtensionClassLoader() {
		return extensionClassLoader;
	}

	public ClassLoader getClassLoader() {
		if (extensionClassLoader != null) {
			return (this.getExtensionClassLoader());
		} else {
			return (this.getClass().getClassLoader());
		}
	}

	public void setButtonContainer(JPanel buttonContainer) {
		this.buttonContainer = buttonContainer;
	}

	public JPanel getButtonContainer() {
		return buttonContainer;
	}

	public void setNorthPanel(JPanel northPanel) {
		this.northPanel = northPanel;
	}

	public JPanel getNorthPanel() {
		return northPanel;
	}

	public JMenu getViewMenu() {
		return menuBuilder.getViewMenu();
	}

	public void setDocumentViewsMenu(JMenu documentViewsMenu) {
		menuBuilder.setDocumentViewsMenu(documentViewsMenu);
	}

	public JMenu getDocumentViewsMenu() {
		return menuBuilder.getDocumentViewsMenu();
	}
}
