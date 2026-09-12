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

package com.dogsbay.dogsbayaieditor.properties;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.io.File;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.XMLUtilities;
import com.dogsbay.xml.editor.Bookmark;
import com.dogsbay.xml.editor.EditorProperties;
//import com.dogsbay.xml.grid.GridProperties;
import com.dogsbay.xml.helper.HelperProperties;
import com.dogsbay.xml.navigator.NavigatorProperties;
import com.dogsbay.xml.properties.Properties;
import com.dogsbay.xml.properties.PropertiesFile;
import com.dogsbay.xml.properties.PropertyList;
import com.dogsbay.xml.viewer.ViewerProperties;
import com.dogsbay.xml.browser.BrowserProperties;
import com.dogsbay.dogsbayaieditor.Main;
import com.dogsbay.dogsbayaieditor.URLUtilities;
import com.dogsbay.dogsbayaieditor.grammar.GrammarProperties;
import com.dogsbay.dogsbayaieditor.plugins.PluginViewProperties;
import com.dogsbay.dogsbayaieditor.framework.FrameworkProperties;
import com.dogsbay.dogsbayaieditor.project.ProjectProperties;
import com.dogsbay.dogsbayaieditor.scenario.ScenarioProperties;
import com.dogsbay.dogsbayaieditor.template.TemplateProperties;
// import com.dogsbay.xslt.debugger.ui.DebuggerProperties;

/**
 * Handles the Xml Plus configuration document.
 *
 * @version $Revision: 1.27 $, $Date: 2005/08/31 10:09:15 $
 * @author Dogsbay
 */
public class ConfigurationProperties extends Properties {

	private static boolean DEBUG = true;


	private static final int MAX_REPLACES = 10;
	private static final int MAX_SEARCHES = 10;
	private static final int MAX_XPATHS = 10;
	private static final int MAX_XPATH_SEARCHES = 10;
	private static final int MAX_XPATH_PREDICATE_SEARCHES = 10;
	private static final int MAX_SORT_XPATH_PREDICATE_SEARCHES = 10;
	private static final int MAX_XPATH_TOOLS = 10;
	private static final int DEFAULT_SPACES = 4;
	private static final int MAX_DATABASE_DRIVERS = 10;
	private static final int MAX_DATABASE_CONNECTIONS = 10;

	private static final String PREFER_PUBLIC_IDENTIFIERS = "prefer-public-identifiers";

	private static final String LOOK_AND_FEEL = "look-and-feel";

	private static final String LICENSE_ACCEPTED = "license-accepted";

	public static final String XSLT_PROCESSOR_SAXON_XSLT2 = "net.sf.saxon.TransformerFactoryImpl";  // Saxon 9.x
	public static final String XSLT_PROCESSOR_SAXON_XSLT3 = "net.sf.saxon.TransformerFactoryImpl";  // Saxon-HE 12.7


	private static final String USE_PROXY = "use-proxy";
	private static final String PROXY_PORT = "proxy-port";
	private static final String PROXY_HOST = "proxy-host";

	private static final String BROWSER = "browser";
	private static final String LAST_OPENED_FOLDER = "last-opened-folder";
	private static final String LAST_DITA_MAP = "last-dita-map";
	private static final String SESSION_ACTIVE_DOCUMENT = "session-active-document";
	private static final String LAST_PROJECT_NAME = "last-project-name";
	private static final String FILE_EXPLORER_SINGLE_CLICK_OPEN = "file-explorer-single-click-open";

	private static final String DITA_PUBLISH_TRANSTYPE = "dita-publish-transtype";
	private static final String DITA_PUBLISH_OUTPUT_DIR = "dita-publish-output-dir";

	private static final String PREVIEW_THEME = "preview-theme";
	private static final String PREVIEW_SCROLL_SYNC = "preview-scroll-sync";

	// private static final String AUTO_GRAMMAR_CREATION = "auto-grammar-creation";
	private static final String AUTO_SYNC_SELECTION = "auto-sync-selection";
	private static final String ATTRIBUTES_NEW_LINE = "attributes-new-line";

	// Whether the editor checks GitHub for a newer release on startup (a network
	// call). On by default, but user-disableable and disclosed for a public build.
	private static final String UPDATE_CHECK_ENABLED = "update-check-enabled";

	// Integration server (MCP / CLI / REST). Off by default — opt-in surface.
	private static final String MCP_SERVER_ENABLED = "mcp-server-enabled";
	private static final String ACP_DEFAULT_TIER = "acp-default-tier";
	private static final String MCP_SERVER_PORT = "mcp-server-port";
	private static final String MCP_ENDPOINT_ENABLED = "mcp-endpoint-enabled";
	private static final String RPC_ENDPOINT_ENABLED = "rpc-endpoint-enabled";

	private static final String SHOW_FULL_PATH = "show-full-path";

	private static final String SCROLL_DOCUMENT_TABS = "scroll-document-tabs";

	private static final String SHOW_EDITOR_TOOLBAR = "show-editor-toolbar";
	private static final String SHOW_MAIN_TOOLBAR = "show-main-toolbar";

	private static final String SYNCHRONISE_SPLITS = "synchronise-splits";

	private static final String PRIMARY_SIDEBAR_VISIBLE = "primary-sidebar-visible";
	private static final String SECONDARY_SIDEBAR_VISIBLE = "secondary-sidebar-visible";
	private static final String BOTTOM_PANEL_VISIBLE = "bottom-panel-visible";
	private static final String AUTHOR_SPLIT_XML_LEFT = "author-split-xml-left";

	private static final String AUTHOR_FONT_SCALE = "author-font-scale";

	private static final String CHECK_TYPE_ON_OPENING = "check-type-opening";
	private static final String PROMPT_CREATE_TYPE_ON_OPENING = "prompt-create-type-opening";
	private static final String VALIDATE_ON_OPENING = "validate-opening";
	private static final String USE_INTERNAL_SCHEMA = "use-internal-schema";

	private static final String SHOW_DOCUMENT_PROPERTIES = "show-document-properties";
	private static final String MULTIPLE_DOCUMENT_OCCURRENCES = "multiple-document-occurrences";

	private static final String HIDE_EXECUTE_SCENARIO_DIALOG_WHEN_COMPLETE = "hide-execute-scenario-dialog-when-complete";
	private static final String SHOW_EXECUTE_SCENARIO_DIALOG_LOG = "show-execute-scenario-dialog-log";
	private static final String OPEN_XINCLUDE_NEW_DOCUMENT = "open-xinclude-new-document";
	private static final String REOPEN_SESSION_FILES = "reopen-session-files";

	private static final String SEARCH_REGULAR_EXPRESSION = "search-regular-expression";
	private static final String SEARCH_XPATH = "search-xpath";
	private static final String SEARCH_MATCH_WHOLE_WORD = "search-match-whole-word";
	private static final String SEARCH_MATCH_CASE = "search-match-case";
	private static final String SEARCH_DIRECTION_DOWN = "search-direction-down";
	private static final String SEARCH_WRAP = "search-wrap";
	private static final String SEARCH_BASIC = "search-basic";
	private static final String REPLACE_BASIC = "replace-basic";

	private static final String LOAD_DTD_GRAMMAR = "load-dtd-grammar";

	private static final String XPATH_SEARCH = "xpath-search";
	private static final String XPATH_PREDICATE = "xpath-predicate";
	private static final String SORT_XPATH_PREDICATE = "sort-xpath-predicate";
	private static final String XPATH_TOOLS = "xpath-tools";
	private static final String XPATH_UNIQUE = "xpath-unique";

	private static final String DATABASE_DRIVERS = "database-drivers";
	private static final String DATABASE_CONNECTIONS = "database-connections";

	private static final String FIND_IN_FILES_FOLDER = "find-in-files-folder";

	// Search Panel properties
	private static final String SEARCH_PANEL_MATCH_CASE = "search-panel-match-case";
	private static final String SEARCH_PANEL_WHOLE_WORD = "search-panel-whole-word";
	private static final String SEARCH_PANEL_USE_REGEX = "search-panel-use-regex";
	private static final String SEARCH_PANEL_PRESERVE_CASE = "search-panel-preserve-case";
	private static final String SEARCH_PANEL_INCLUDE_PATTERN = "search-panel-include-pattern";
	private static final String SEARCH_PANEL_EXCLUDE_PATTERN = "search-panel-exclude-pattern";
	private static final String SEARCH_PANEL_SEARCH_HISTORY = "search-panel-search-history";
	private static final String SEARCH_PANEL_REPLACE_HISTORY = "search-panel-replace-history";

	private static final String PREFIX_NAMESPACE_MAPPING = "prefix-namespace-mapping";

	private static final String SPACES = "spaces";
	private static final String SEARCH = "search";
	private static final String XPATH = "xpath";
	private static final String REPLACE = "replace";

	private static final String EXTENSION = "extension";
	private static final String CATALOG = "catalog";

	private PropertyList extensions = null;
	private PropertyList catalogs = null;
	private PropertyList prefixNamespaceMappings = null;

	private PropertyList replaces = null;
	private PropertyList searches = null;
	private PropertyList xpaths = null;
	private PropertyList xpathSearches = null;
	private PropertyList xpathPredicates = null;
	private PropertyList sortXPathPredicates = null;
	private PropertyList xpathTools = null;
	private PropertyList lastDocuments = null;
	private PropertyList lastProjects = null;
	private PropertyList lastURLs = null;
	private PropertyList sessionDocuments = null;

	private PropertyList databaseDrivers = null;
	private PropertyList databaseConnections = null;

	private PropertyList searchPanelSearchHistory = null;
	private PropertyList searchPanelReplaceHistory = null;

	// Built-in (transient) registrations contributed by plugins. Never persisted;
	// re-added on every editor startup by the plugin's activate() method.
	private final Vector builtInGrammars = new Vector();
	private final Vector builtInTemplates = new Vector();
	private final Vector builtInCatalogs = new Vector();

	public static final String XMLPLUS_HOME = System.getProperty("user.home") + File.separator + ".editor"
			+ File.separator;
	public static final String PROPERTIES_FILE = ".editor.xml";

	public static final String LAST_OPENED_DOCUMENT = "last-opened-document";
	public static final String LAST_OPENED_PROJECT = "last-opened-project";
	public static final String LAST_OPENED_URL = "last-opened-url";
	public static final String LAST_OPENED_GRAMMAR = "last-opened-grammar";

	public static final String TEXT_EDITOR = "text-editor";
	public static final String DOM_EDITOR = "dom-editor";
	public static final String XML_VIEWER = "xml-viewer";
	public static final String XML_BROWSER = "xml-browser";
	public static final String SCHEMA_VIEWER = "schema-viewer";
	public static final String XML_GRID = "xml-grid";

	// public static final String DEBUGGER = "debugger";

	public static final String HELPER = "helper";
	public static final String NAVIGATOR = "navigator";

	public static final String XPOS = "xpos";
	public static final String YPOS = "ypos";
	public static final String WIDTH = "width";
	public static final String HEIGHT = "height";

	public static final int DEFAULT_WIDTH = 1024;
	public static final int DEFAULT_HEIGHT = 768;

	public static final String WINDOW_MAXIMISED = "window-maximised";

	private static final String TOP_DIVIDER_LOCATION = "top-divider-location";
	private static final int DEFAULT_TOP_DIVIDER_LOCATION = 280;

	private static final String DIVIDER_LOCATION = "divider-location";
	private static final int DEFAULT_DIVIDER_LOCATION = 400;

	private static final String RIGHT_PANEL_WIDTH = "right-panel-width";
	private static final int DEFAULT_RIGHT_PANEL_WIDTH = -1;   // -1 = nobody has dragged it

	private static final String SAVE_PROPERTIES_INTERVAL = "save-properties-interval";
	private static final long DEFAULT_SAVE_PROPERTIES_INTERVAL = 300000;

	private DogsBayDocument document = null;

	private EditorProperties editorProperties = null;
	private ViewerProperties viewerProperties = null;
	private BrowserProperties browserProperties = null;
	// private GridProperties gridProperties = null;

	// private DebuggerProperties debuggerProperties = null;

	private HelperProperties helperProperties = null;
	private NavigatorProperties navigatorProperties = null;


	private TextPreferences textPreferences = null;
	private PrintPreferences printPreferences = null;

	private XercesProperties xercesProperties = null;

	private PropertyList findInFilesFolder;

	private List pluginProperties = null;
	private List propertyFiles = null;

	/**
	 * Creates the Configuration Document wrapper.
	 * It reads in the root element and if it has to, it creates the property file.
	 *
	 * @param the url to the XML document.
	 */
	public ConfigurationProperties(DogsBayDocument document) {
		super(document.getRoot());
		this.document = document;

		searches = getList(SEARCH, MAX_SEARCHES);
		xpaths = getList(XPATH, MAX_XPATHS);
		replaces = getList(REPLACE, MAX_REPLACES);
		extensions = getList(EXTENSION, -1);
		catalogs = getList(CATALOG, -1);

		prefixNamespaceMappings = getList(PREFIX_NAMESPACE_MAPPING, -1);
		xpathSearches = getList(XPATH_SEARCH, MAX_XPATH_SEARCHES);
		xpathPredicates = getList(XPATH_PREDICATE, MAX_XPATH_PREDICATE_SEARCHES);
		sortXPathPredicates = getList(SORT_XPATH_PREDICATE, MAX_SORT_XPATH_PREDICATE_SEARCHES);
		xpathTools = getList(XPATH_TOOLS, MAX_XPATH_TOOLS);

		databaseDrivers = getList(DATABASE_DRIVERS, MAX_DATABASE_DRIVERS);
		databaseConnections = getList(DATABASE_CONNECTIONS, MAX_DATABASE_CONNECTIONS);

		findInFilesFolder = getList(FIND_IN_FILES_FOLDER, 1);

		lastDocuments = getList(LAST_OPENED_DOCUMENT, 5);
		lastProjects = getList(LAST_OPENED_PROJECT, 5);
		lastURLs = getList(LAST_OPENED_URL, 10);
		sessionDocuments = getList("session-document", -1);

		// editorProperties = new EditorProperties( get( TEXT_EDITOR));
		viewerProperties = new ViewerProperties(get(XML_VIEWER));
		// gridProperties = new GridProperties( get( XML_GRID));
		browserProperties = new BrowserProperties(get(XML_BROWSER));
		setPluginProperties(new ArrayList());

		// debuggerProperties = new DebuggerProperties( get( DEBUGGER));

		helperProperties = new HelperProperties(get(HELPER));
		navigatorProperties = new NavigatorProperties(get(NAVIGATOR));


		printPreferences = new PrintPreferences(get(PrintPreferences.PRINT_PREFERENCES));

		setPropertyFiles(new ArrayList());
		// Sections of this document, not files of their own. The split gave one
		// logical thing four files, divided by which class happened to extend
		// PropertiesFile rather than by anything a reader would recognise — and
		// three of the four had no way back when a release renamed them.
		xercesProperties = new XercesProperties(get(XercesProperties.XERCES_PROPERTIES));
		editorProperties = new EditorProperties(get(EditorProperties.TEXT_EDITOR));
		textPreferences = new TextPreferences(get(TextPreferences.TEXT_PREFERENCES));

		// tjc 16102008
		// the save to disk thread
		Timer timer = new Timer("SavePropertiesToDisk", true);

		timer.schedule(new TimerTask() {
			public void run() {

				if (DEBUG)
					System.out.println("Saving properties to disk");
				ConfigurationProperties.this.saveToDisk();
			}
		}, ConfigurationProperties.this.getSavePropertiesInterval(),
				ConfigurationProperties.this.getSavePropertiesInterval());

	}

	public static DogsBayDocument createPropertiesFile(String fileName, String rootName) {

		DogsBayDocument document = null;
		boolean firstTime = false;

		File dir = new File(Main.DOGSBAY_HOME);

		if (!dir.exists()) {
			dir.mkdir();
		}

		File file = new File(dir, fileName);
		URL url = null;

		try {
			url = com.dogsbay.xml.DogsBayURLUtilities.getURLFromFile(file); // MalformedURLException
		} catch (Exception e) {
			// Should never happen, am not sure what to do in this case...
			e.printStackTrace();
		}

		firstTime = true;
		if (file.exists()) {
			try {
				document = new DogsBayDocument(url);
				document.loadWithoutSubstitution();
				firstTime = false;
			} catch (Exception e) {
				// should not happen, document should always be valid...
				e.printStackTrace();
				return null;
			}
		}

		XElement root = null;
		// String namespaceURI = null;

		if (firstTime == true) {
			// root = new XElement( rootName,
			// "http://www.dogsbay.ai/dogsbay-editor/"+Identity.getIdentity().getVersion()+"/");
			root = new XElement(rootName);
			root.setText("\n");
			document = new DogsBayDocument(url, root);

			// namespaceURI = root.getNamespaceURI();
		} else {
			root = document.getRoot();
			// namespaceURI = root.getNamespaceURI();
		}

		return (document);

	}

	/**
	 * Check to find out if auto sync is selected.
	 *
	 * @return true when auto sync is selected.
	 */
	public boolean isAutoSyncSelection() {
		return getBoolean(AUTO_SYNC_SELECTION, true);
	}

	/**
	 * Set the auto-sync selection.
	 *
	 * @param sync auto sync the selection.
	 */
	public void setAutoSyncSelection(boolean sync) {
		set(AUTO_SYNC_SELECTION, sync);
	}

	/**
	 * Whether the editor checks GitHub for a newer release on startup. This makes
	 * an outbound network call to the GitHub Releases API; users can turn it off.
	 *
	 * @return true when the startup update check should run.
	 */
	public boolean isUpdateCheckEnabled() {
		return getBoolean(UPDATE_CHECK_ENABLED, true);
	}

	/**
	 * Enable or disable the startup update check.
	 *
	 * @param enabled true to check for updates on startup.
	 */
	public void setUpdateCheckEnabled(boolean enabled) {
		set(UPDATE_CHECK_ENABLED, enabled);
	}

	/**
	 * Check to find out if the document tabs should scroll.
	 *
	 * @return true when the document tabs should scroll.
	 */
	public boolean isScrollDocumentTabs() {
		return getBoolean(SCROLL_DOCUMENT_TABS, true);
	}

	/**
	 * Set wether the document should scroll.
	 *
	 * @param scroll true when the document tabs should scroll.
	 */
	public void setScrollDocumentTabs(boolean scroll) {
		set(SCROLL_DOCUMENT_TABS, scroll);
	}

	/**
	 * Whether the embedded integration server (MCP at {@code /mcp}, JSON-RPC at
	 * {@code /rpc}, health at {@code /status}) starts with the editor. Off by
	 * default — it is an opt-in surface for external tools (CLI editor-commands,
	 * AI assistants, REST). The in-app agent does not need it (it calls tools
	 * in-process).
	 *
	 * @return true when the integration server should run.
	 */
	/**
	 * The tier a hosted ACP agent starts at unless the user picks another when
	 * starting it. Commands-only by default: the safest, and the one where the
	 * agent gets nothing an MCP client could not.
	 */
	public com.dogsbay.agent.session.CapabilityTier getAcpDefaultTier() {
		try {
			String t = getText(ACP_DEFAULT_TIER);
			return t == null || t.isBlank() ? com.dogsbay.agent.session.CapabilityTier.T1_COMMANDS
					: com.dogsbay.agent.session.CapabilityTier.valueOf(t);
		} catch (IllegalArgumentException e) {
			return com.dogsbay.agent.session.CapabilityTier.T1_COMMANDS;
		}
	}

	/**
	 * Whether an agent's edits to a DITA document land as marked proposals
	 * (status/rev marks the writer accepts or rejects) rather than as plain
	 * text. On by default; the user's own edits are never marked.
	 */
	public boolean isAgentProposalsEnabled() {
		return getBoolean("review-agent-proposals", true);
	}

	public void setAgentProposalsEnabled(boolean enabled) {
		set("review-agent-proposals", enabled);
	}

	/**
	 * How many days an agent session transcript is kept before the editor
	 * deletes it at startup. Zero — the default — keeps them forever.
	 *
	 * <p>A transcript is the readable record of what an agent did to a project,
	 * the counterpart to the audit log, so nothing is deleted unless the user
	 * asks for it and says how old is old.
	 */
	public int getAgentSessionRetentionDays() {
		return getInteger("agent-session-retention-days", 0);
	}

	public void setAgentSessionRetentionDays(int days) {
		set("agent-session-retention-days", Math.max(0, days));
	}

	/** The element holding the reader's own key choices. */
	private static final String KEY_BINDINGS = "key-bindings";

	private KeyBindings keyBindings = null;

	/**
	 * The keys as they stand: the product's defaults with the reader's choices
	 * applied. One instance, so the settings page, the menus and the clash
	 * checks all see the same thing.
	 */
	public synchronized KeyBindings getKeyBindings() {
		if (keyBindings == null) {
			keyBindings = new KeyBindings();
			java.util.List<String> dropped = keyBindings.load(getKeyBindingOverrides());
			if (!dropped.isEmpty()) {
				// A binding for a command this build does not have. Said once,
				// because it is the reader's file explaining itself, not a fault.
				System.out.println("[settings] ignoring key bindings for commands that no longer exist: "
						+ String.join(", ", dropped));
			}
		}
		return keyBindings;
	}

	/**
	 * The keys the reader has chosen, by command id.
	 *
	 * <p>Only their choices: the defaults are the product's and live in
	 * {@link KeyBindingCatalogue}. Storing every binding is what let a file
	 * accumulate the same command hundreds of times, and what froze a release's
	 * defaults into every machine that had ever run it.
	 */
	public java.util.Map<String, String> getKeyBindingOverrides() {
		java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
		XElement bindings = get(KEY_BINDINGS);
		if (bindings == null) {
			return out;
		}
		for (XElement binding : bindings.getElements("binding")) {
			String action = binding.getAttribute("action");
			if (action != null && !action.isBlank()) {
				String stroke = binding.getAttribute("keystroke");
				out.put(action, stroke == null ? "" : stroke);
			}
		}
		return out;
	}

	/** Replace the stored choices with these, one element each. */
	public void setKeyBindingOverrides(java.util.Map<String, String> overrides) {
		XElement bindings = get(KEY_BINDINGS);
		if (bindings == null) {
			return;
		}
		// Replace rather than append: appending is what produced a file with
		// 854 entries for 206 commands.
		for (XElement existing : bindings.getElements("binding")) {
			bindings.remove(existing);
		}
		if (overrides == null) {
			return;
		}
		overrides.forEach((action, stroke) -> {
			XElement binding = new XElement("binding");
			binding.addAttribute("action", action);
			binding.addAttribute("keystroke", stroke == null ? "" : stroke);
			bindings.add(binding);
		});
	}

	public void setAcpDefaultTier(com.dogsbay.agent.session.CapabilityTier tier) {
		set(ACP_DEFAULT_TIER, tier == null ? "" : tier.name());
	}

	public boolean isMcpServerEnabled() {
		return getBoolean(MCP_SERVER_ENABLED, false);
	}

	/**
	 * Enable or disable the integration server.
	 *
	 * @param enabled true to run the server.
	 */
	public void setMcpServerEnabled(boolean enabled) {
		set(MCP_SERVER_ENABLED, enabled);
	}

	/**
	 * Preferred TCP port for the integration server, or {@code 0} to pick the
	 * first free port in the range 19601&ndash;19699 automatically. A fixed port
	 * keeps external client configuration (e.g. {@code claude mcp add}) stable
	 * across restarts.
	 *
	 * @return the preferred port, or 0 for automatic.
	 */
	public int getMcpServerPort() {
		return getInteger(MCP_SERVER_PORT, 0);
	}

	/**
	 * Set the preferred integration server port ({@code 0} = automatic).
	 *
	 * @param port the preferred port.
	 */
	public void setMcpServerPort(int port) {
		set(MCP_SERVER_PORT, port);
	}

	/**
	 * Whether the MCP endpoint ({@code /mcp}) is exposed when the server runs.
	 *
	 * @return true when the MCP endpoint should be registered.
	 */
	public boolean isMcpEndpointEnabled() {
		return getBoolean(MCP_ENDPOINT_ENABLED, true);
	}

	/**
	 * Enable or disable the MCP endpoint.
	 *
	 * @param enabled true to expose {@code /mcp}.
	 */
	public void setMcpEndpointEnabled(boolean enabled) {
		set(MCP_ENDPOINT_ENABLED, enabled);
	}

	/**
	 * Whether the JSON-RPC endpoint ({@code /rpc}) is exposed when the server
	 * runs. This is the surface the {@code dogsbay} CLI's editor-commands use.
	 *
	 * @return true when the JSON-RPC endpoint should be registered.
	 */
	public boolean isRpcEndpointEnabled() {
		return getBoolean(RPC_ENDPOINT_ENABLED, true);
	}

	/**
	 * Enable or disable the JSON-RPC endpoint.
	 *
	 * @param enabled true to expose {@code /rpc}.
	 */
	public void setRpcEndpointEnabled(boolean enabled) {
		set(RPC_ENDPOINT_ENABLED, enabled);
	}

	/**
	 * Set the accepted license.
	 */
	public void setLicenseAccepted(String type) {
		set(LICENSE_ACCEPTED, type);
	}

	/**
	 * Check to find out if the license has been accepted.
	 *
	 * @return true when the license has been accepted.
	 */
	public boolean isLicenseAccepted(String type) {
		String current = getText(LICENSE_ACCEPTED);

		if (current != null && current.equals(type)) {
			return true;
		}

		return false;
	}

	/**
	 * Check to find out if attributes should be places on a new line.
	 *
	 * @return true when attributes should be placed on a new line.
	 */
	public boolean isAttributesNewLine() {
		return getBoolean(ATTRIBUTES_NEW_LINE, false);
	}

	/**
	 * Set the attributes on a new line.
	 *
	 * @param enabled set attributes on a new line.
	 */
	public void setAttributesNewLine(boolean enabled) {
		set(ATTRIBUTES_NEW_LINE, enabled);
	}

	/**
	 * Check to find out the full path should be shown for the document.
	 *
	 * @return true when a new type should be checked for.
	 */
	public boolean isShowFullPath() {
		return getBoolean(SHOW_FULL_PATH, false);
	}

	public void setShowFullPath(boolean show) {
		set(SHOW_FULL_PATH, show);
	}

	/**
	 * Check to find out the toolbar should be shown for the document.
	 *
	 * @return true when a the toolbar should be visible.
	 */
	public boolean isShowToolbar() {
		// Hidden by default — except on macOS, where the screen menu bar can't host the
		// view/split buttons, so they fall back to this toolbar (which must then be visible).
		return getBoolean(SHOW_MAIN_TOOLBAR, com.dogsbay.dogsbayaieditor.Platform.isMacScreenMenuBar());
	}

	public void setShowToolbar(boolean show) {
		set(SHOW_MAIN_TOOLBAR, show);
	}

	/**
	 * Check to find out the splits should be synchronised.
	 *
	 * @return true when the splits should be synchronised.
	 */
	public boolean isSynchroniseSplits() {
		return getBoolean(SYNCHRONISE_SPLITS, false);
	}

	public void setSynchroniseSplits(boolean sync) {
		set(SYNCHRONISE_SPLITS, sync);
	}

	public boolean isPrimarySidebarVisible() {
		return getBoolean(PRIMARY_SIDEBAR_VISIBLE, true);
	}

	public void setPrimarySidebarVisible(boolean visible) {
		set(PRIMARY_SIDEBAR_VISIBLE, visible);
	}

	public boolean isSecondarySidebarVisible() {
		return getBoolean(SECONDARY_SIDEBAR_VISIBLE, true);
	}

	public void setSecondarySidebarVisible(boolean visible) {
		set(SECONDARY_SIDEBAR_VISIBLE, visible);
	}

	public boolean isBottomPanelVisible() {
		return getBoolean(BOTTOM_PANEL_VISIBLE, true);
	}

	public void setBottomPanelVisible(boolean visible) {
		set(BOTTOM_PANEL_VISIBLE, visible);
	}

	/**
	 * Pane order of the Author+Editor split: XML source on the left (default,
	 * mirroring editor-left / preview-right) or on the right.
	 */
	public boolean isAuthorSplitXmlLeft() {
		return getBoolean(AUTHOR_SPLIT_XML_LEFT, true);
	}

	public void setAuthorSplitXmlLeft(boolean xmlLeft) {
		set(AUTHOR_SPLIT_XML_LEFT, xmlLeft);
	}

	/** Author-view font scale as a percentage of the normal size (Ctrl+Plus / Ctrl+Minus). */
	public int getAuthorFontScalePercent() {
		return getInteger(AUTHOR_FONT_SCALE, 100);
	}

	public void setAuthorFontScalePercent(int percent) {
		set(AUTHOR_FONT_SCALE, percent);
	}

	/**
	 * Check to find out wether the xpath editor should show
	 * unique xpaths.
	 *
	 * @return true when a the xpath editor shows unique xpaths.
	 */
	public boolean isUniqueXPath() {
		return getBoolean(XPATH_UNIQUE, false);
	}

	public void setUniqueXPath(boolean unique) {
		set(XPATH_UNIQUE, unique);
	}

	/**
	 * Check to find out the editor toolbar should be shown for the document.
	 *
	 * @return true when a the editor toolbar should be visible.
	 */
	public boolean isShowEditorToolbar() {
		return getBoolean(SHOW_EDITOR_TOOLBAR, true);
	}

	public void setShowEditorToolbar(boolean show) {
		set(SHOW_EDITOR_TOOLBAR, show);
	}

	/**
	 * Check to find out if new types should be checked for.
	 *
	 * @return true when a new type should be checked for.
	 */
	public boolean isCheckTypeOnOpening() {
		return getBoolean(CHECK_TYPE_ON_OPENING, true);
	}

	public void setCheckTypeOnOpening(boolean check) {
		set(CHECK_TYPE_ON_OPENING, check);
	}

	/**
	 * Check to find out wether the execute dialog should
	 * stay visible after competion.
	 *
	 * @return true when the dialog should stay visible after completion.
	 */
	public boolean isHideExecuteScenarioDialogWhenComplete() {
		return getBoolean(HIDE_EXECUTE_SCENARIO_DIALOG_WHEN_COMPLETE, false);
	}

	public void setHideExecuteScenarioDialogWhenComplete(boolean enabled) {
		set(HIDE_EXECUTE_SCENARIO_DIALOG_WHEN_COMPLETE, enabled);
	}

	/**
	 * Check to find out wether Resolve XInclude, should open in a new document.
	 *
	 * @return true when Resolve XInclude, should open in a new document.
	 */
	public boolean isOpenXIncludeInNewDocument() {
		return getBoolean(OPEN_XINCLUDE_NEW_DOCUMENT, false);
	}

	public void setOpenXIncludeInNewDocument(boolean enabled) {
		set(OPEN_XINCLUDE_NEW_DOCUMENT, enabled);
	}

	/**
	 * Check if files from the previous session should be reopened on startup.
	 *
	 * @return true when files should be reopened from previous session.
	 */
	public boolean isReopenSessionFiles() {
		return getBoolean(REOPEN_SESSION_FILES, true);
	}

	/**
	 * Set whether to reopen files from the previous session.
	 *
	 * @param reopen whether to reopen files from previous session.
	 */
	public void setReopenSessionFiles(boolean reopen) {
		set(REOPEN_SESSION_FILES, reopen);
	}

	/**
	 * Check to find out wether the execute dialog log should be visible.
	 *
	 * @return true when the dialog log should be visible.
	 */
	public boolean isShowExecuteScenarioDialogLog() {
		return getBoolean(SHOW_EXECUTE_SCENARIO_DIALOG_LOG, true);
	}

	public void setShowExecuteScenarioDialogLog(boolean enabled) {
		set(SHOW_EXECUTE_SCENARIO_DIALOG_LOG, enabled);
	}

	/**
	 * Check to find out wether the document properties should be visible.
	 *
	 * @return true when the document properties should be visible.
	 */
	public boolean isShowDocumentProperties() {
		return getBoolean(SHOW_DOCUMENT_PROPERTIES, true);
	}

	public void setShowDocumentProperties(boolean enabled) {
		set(SHOW_DOCUMENT_PROPERTIES, enabled);
	}

	/**
	 * Check to find out wether the application should be able to
	 * load the same document more than once.
	 *
	 * @return true when the application should be able to load the
	 *         same document more than once.
	 */
	public boolean isMultipleDocumentOccurrences() {
		return getBoolean(MULTIPLE_DOCUMENT_OCCURRENCES, false);
	}

	public void setMultipleDocumentOccurrences(boolean enabled) {
		set(MULTIPLE_DOCUMENT_OCCURRENCES, enabled);
	}

	private static final String SHOW_WELCOME_ON_STARTUP = "show-welcome-on-startup";

	/** Whether to open the Welcome tab on startup (default true until the user opts out). */
	public boolean isShowWelcomeOnStartup() {
		return getBoolean(SHOW_WELCOME_ON_STARTUP, true);
	}

	public void setShowWelcomeOnStartup(boolean show) {
		set(SHOW_WELCOME_ON_STARTUP, show);
	}

	/**
	 * Prompt the user when a type could not be found.
	 *
	 * @return true when a the user should be prompted to create a new type.
	 */
	public boolean isPromptCreateTypeOnOpening() {
		return getBoolean(PROMPT_CREATE_TYPE_ON_OPENING, false);
	}

	public void setPromptCreateTypeOnOpening(boolean check) {
		set(PROMPT_CREATE_TYPE_ON_OPENING, check);
	}

	/**
	 * Check to find out if a document should be validated when openened.
	 *
	 * @return true when the document should be validated when openened.
	 */
	public boolean isValidateOnOpening() {
		return getBoolean(VALIDATE_ON_OPENING, false);
	}

	public void setValidateOnOpening(boolean validate) {
		set(VALIDATE_ON_OPENING, validate);
	}

	/**
	 * Check to find out if a the schema defined in the document should
	 * be used as the template schema.
	 *
	 * @return true when the schema in the document should be used.
	 */
	public boolean useInternalSchema() {
		return getBoolean(USE_INTERNAL_SCHEMA, true);
	}

	public void setUseInternalSchema(boolean use) {
		set(USE_INTERNAL_SCHEMA, use);
	}

	/**
	 * Check to find out if a new Grammar should be created automatically.
	 *
	 * @return true when a new Grammar should be created automatically.
	 */
	// public boolean isAutoCreateGrammar() {
	// return getBoolean( AUTO_GRAMMAR_CREATION, true);
	// }

	/**
	 * Set the auto new grammar creation.
	 *
	 * @param create automatically create a new XML Type.
	 */
	// public void setAutoCreateGrammar( boolean create) {
	// set( AUTO_GRAMMAR_CREATION, create);
	// }

	/**
	 * Use a proxy server?
	 *
	 * @return true when a proxy server should be used.
	 */
	public boolean isUseProxy() {
		return getBoolean(USE_PROXY, false);
	}

	/**
	 * Set wether to use a proxy server.
	 *
	 * @param use wether to use a proxy server.
	 */
	public void setUseProxy(boolean use) {
		set(USE_PROXY, use);
	}

	/**
	 * Prefer public identifiers are for catalogs?
	 *
	 * @return true when public identifiers are preferred for catalogs.
	 */
	public boolean isPreferPublicIdentifiers() {
		return getBoolean(PREFER_PUBLIC_IDENTIFIERS, true);
	}

	/**
	 * Set wether to prefer public identifiers for catalogs.
	 *
	 * @param prefer wether to prefer public identifiers for catalogs.
	 */
	public void setPreferPublicIdentifiers(boolean prefer) {
		set(PREFER_PUBLIC_IDENTIFIERS, prefer);
	}

	/**
	 * Get the proxy port.
	 *
	 * @return the proxy port.
	 */
	public String getProxyPort() {
		return getText(PROXY_PORT);
	}

	/**
	 * Set the proxy port.
	 *
	 * @param port the proxy port.
	 */
	public void setProxyPort(String port) {
		set(PROXY_PORT, port);
	}

	/**
	 * Get the look and feel class name.
	 *
	 * @return the look and feel class name.
	 */
	public String getLookAndFeel() {
		return getText(LOOK_AND_FEEL);
	}

	/**
	 * Set the look and feel class name.
	 *
	 * @param laf the look and feel class name.
	 */
	public void setLookAndFeel(String laf) {
		set(LOOK_AND_FEEL, laf);
	}





	/**
	 * Set true when external dtds should be loaded.
	 *
	 * @param enabled when external dtds should be loaded.
	 */
	public void setLoadDTDGrammar(boolean enabled) {
		set(LOAD_DTD_GRAMMAR, enabled);
	}

	public boolean isLoadDTDGrammar() {
		return getBoolean(LOAD_DTD_GRAMMAR, false);
	}

	/**
	 * Get the XSLT Processor.
	 *
	 * @return the XSLT Processor property.
	 */
	public String getXSLTProcessor() {
		// Saxon is the only supported XSLT processor; there is nothing to configure.
		return XSLT_PROCESSOR_SAXON_XSLT3;
	}

	/**
	 * Get the proxy host.
	 *
	 * @return the proxy host.
	 */
	public String getProxyHost() {
		return getText(PROXY_HOST);
	}

	/**
	 * Set the proxy host.
	 *
	 * @param host the proxy host.
	 */
	public void setProxyHost(String host) {
		set(PROXY_HOST, host);
	}

	/**
	 * Get the browser string.
	 *
	 * @return the browser string.
	 */
	public String getBrowser() {
		return getText(BROWSER);
	}

	/**
	 * Set the browser.
	 *
	 * @param browser the default browser.
	 */
	public void setBrowser(String host) {
		set(BROWSER, host);
	}

	/**
	 * Check to find out if the search matches the case.
	 *
	 * @return true when the search matches case.
	 */
	public boolean isMatchCase() {
		return getBoolean(SEARCH_MATCH_CASE, false);
	}

	/**
	 * Set the match-case search property.
	 *
	 * @param matchCase the search property.
	 */
	public void setMatchCase(boolean matchCase) {
		set(SEARCH_MATCH_CASE, matchCase);
	}

	/**
	 * Check to find out if the search should use the basic configuration.
	 *
	 * @return true when the basic configuration has been selected.
	 */
	public boolean isBasicSearch() {
		return getBoolean(SEARCH_BASIC, true);
	}

	/**
	 * Set the basic search configuration property.
	 *
	 * @param enabled the basic search configuration property.
	 */
	public void setBasicSearch(boolean enabled) {
		set(SEARCH_BASIC, enabled);
	}

	/**
	 * Check to find out if the replace should use the basic configuration.
	 *
	 * @return true when the basic configuration has been selected.
	 */
	public boolean isBasicReplace() {
		return getBoolean(REPLACE_BASIC, true);
	}

	/**
	 * Set the basic replace configuration property.
	 *
	 * @param enabled the basic replace configuration property.
	 */
	public void setBasicReplace(boolean enabled) {
		set(REPLACE_BASIC, enabled);
	}

	/**
	 * Check to find out if the search matches the whole word.
	 *
	 * @return true when the search matches the whole word.
	 */
	public boolean isMatchWholeWord() {
		return getBoolean(SEARCH_MATCH_WHOLE_WORD, false);
	}

	/**
	 * Set the match-whole-word search property.
	 *
	 * @param matchWord the search property.
	 */
	public void setMatchWholeWord(boolean matchWord) {
		set(SEARCH_MATCH_WHOLE_WORD, matchWord);
	}

	/**
	 * Check to find out if the search wraps.
	 *
	 * @return true when the search wraps.
	 */
	public boolean isWrapSearch() {
		return getBoolean(SEARCH_WRAP, true);
	}

	/**
	 * Set the wrap search property.
	 *
	 * @param wrap the search property.
	 */
	public void setWrapSearch(boolean wrap) {
		set(SEARCH_WRAP, wrap);
	}

	/**
	 * Check to find out if the search is a Regular Expression.
	 *
	 * @return true when the search is a Regular Expression.
	 */
	public boolean isRegularExpression() {
		return getBoolean(SEARCH_REGULAR_EXPRESSION, false);
	}

	/**
	 * Set the Regular Expression search property.
	 *
	 * @param regExp the search property.
	 */
	public void setRegularExpression(boolean regExp) {
		set(SEARCH_REGULAR_EXPRESSION, regExp);
	}

	/**
	 * Check to find out if the search uses a xpath.
	 *
	 * @return true when the search uses a xpath.
	 */
	public boolean isXPath() {
		return getBoolean(SEARCH_XPATH, false);
	}

	/**
	 * Set the XPath search property.
	 *
	 * @param xpath the search property.
	 */
	public void setXPath(boolean xpath) {
		set(SEARCH_XPATH, xpath);
	}

	/**
	 * Set the number of spaces to substitute for a tab.
	 *
	 * @param spaces the number of spaces.
	 */
	public void setSpaces(int spaces) {
		set(SPACES, spaces);
	}

	/**
	 * Gets the number of spaces to substitute for a tab.
	 *
	 * @return the number of spaces.
	 */
	public int getSpaces() {
		return getInteger(SPACES, DEFAULT_SPACES);
	}

	/**
	 * Check to find out if the search direction is down.
	 *
	 * @return true when the search direction is down.
	 */
	public boolean isDirectionDown() {
		return getBoolean(SEARCH_DIRECTION_DOWN, true);
	}

	/**
	 * Set the search direction.
	 *
	 * @param downward the search direction.
	 */
	public void setDirectionDown(boolean downward) {
		set(SEARCH_DIRECTION_DOWN, downward);
	}

	/**
	 * Adds a Search string to the properties.
	 *
	 * @param search the search.
	 */
	public void addSearch(String search) {
		searches.add(search);
		save();
	}

	/**
	 * Returns the list of searches.
	 *
	 * @return the list of searches.
	 */
	public Vector getSearches() {
		return searches.get();
	}

	/**
	 * Adds a XPath Search string to the properties.
	 *
	 * @param search the xpath search.
	 */
	public void addXPath(String search) {
		xpaths.add(search);
		save();
	}

	/**
	 * Returns the list of xpaths used in the replace and find dialogs.
	 *
	 * @return the list of xpaths.
	 */
	public Vector getXPaths() {
		return xpaths.get();
	}

	/**
	 * Adds a Replace string to the properties.
	 *
	 * @param replace the replace string.
	 */
	public void addReplace(String replace) {
		replaces.add(replace);
		save();
	}

	/**
	 * Returns the list of replaces.
	 *
	 * @return the list of replaces.
	 */
	public Vector getReplaces() {
		return replaces.get();
	}

	/**
	 * Adds an extension string to the properties.
	 *
	 * @param extension the extension string.
	 */
	public void addExtension(String extension) {
		extensions.add(extension);
		save();
	}

	/**
	 * Removes an extension string to the properties.
	 *
	 * @param extension the extension string.
	 */
	public void removeExtension(String extension) {
		extensions.remove(extension);
		save();
	}

	/**
	 * Returns the list of extensions.
	 *
	 * @return the list of extensions.
	 */
	public Vector getExtensions() {
		return extensions.get();
	}

	/**
	 * Adds a prefix namespace mapping to the properties.
	 *
	 * @param prefix the prefix for the namespace.
	 * @param uri    the namespace uri.
	 */
	public void addPrefixNamespaceMapping(String prefix, String uri) {
		prefixNamespaceMappings.add(prefix + ":" + uri);
		save();
	}

	/**
	 * Removes a prefix namespace mapping from the properties.
	 *
	 * @param prefix the prefix for the namespace.
	 * @param uri    the namespace uri.
	 */
	public void removePrefixNamespaceMapping(String prefix, String namespace) {
		prefixNamespaceMappings.remove(prefix + ":" + namespace);
		save();
	}

	/**
	 * Returns the list of prefix namespace mappings.
	 *
	 * @return the list of prefix namespace mappings.
	 */
	public Map getPrefixNamespaceMappings() {
		Vector mappings = prefixNamespaceMappings.get();
		Map result = new HashMap();

		for (int i = 0; i < mappings.size(); i++) {
			String mapping = (String) mappings.elementAt(i);
			int index = mapping.indexOf(':');

			String prefix = mapping.substring(0, index);
			String uri = mapping.substring(index + 1, mapping.length());

			// put( key, value)
			result.put(prefix, uri);
		}

		return result;
	}

	/**
	 * Adds a catalog string to the properties.
	 *
	 * @param catalog the catalog string.
	 */
	public void addCatalog(String catalog) {
		catalogs.add(catalog);
		save();
		applyActiveCatalogs();
	}

	/**
	 * Rebuilds the xml.catalog.files system property from the current catalog list.
	 * Call after adding or removing catalogs at runtime so that subsequent calls to
	 * XMLUtilities.getCatalogResolver() pick up the change without a restart.
	 */
	public void applyActiveCatalogs() {
		Vector currentCatalogs = getCatalogs();
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < currentCatalogs.size(); i++) {
			sb.append((String) currentCatalogs.elementAt(i));
			sb.append(";");
		}
		System.setProperty("xml.catalog.files", sb.toString());
	}

	/**
	 * Removes an catalog string to the properties.
	 *
	 * @param catalog the catalog string.
	 */
	public void removeCatalog(String catalog) {
		catalogs.remove(catalog);
		save();
	}

	/**
	 * Returns the list of catalogs (persisted plus built-in).
	 *
	 * @return the list of catalogs.
	 */
	public Vector getCatalogs() {
		Vector result = new Vector(catalogs.get());
		result.addAll(builtInCatalogs);
		return result;
	}

	/**
	 * Registers a catalog file path contributed by a built-in plugin. The path is
	 * not persisted to user config — the plugin must re-register on every startup.
	 * Calls {@link #applyActiveCatalogs()} so the new path takes effect immediately.
	 */
	public void addBuiltInCatalog(String catalog) {
		if (catalog == null || builtInCatalogs.contains(catalog)) {
			return;
		}
		builtInCatalogs.add(catalog);
		applyActiveCatalogs();
	}

	/**
	 * Adds a xpath Search string to the properties.
	 *
	 * @param xpath search the search.
	 */
	public void addXPathSearch(String search) {
		xpathSearches.add(search);
		save();
	}

	/**
	 * Adds an xpath predicate string to the properties.
	 *
	 * @param search The XPath Predicate
	 */
	public void addXPathPredicate(String predicate) {
		xpathPredicates.add(predicate);
		save();
	}

	/**
	 * Adds a sort xpath predicate string to the properties.
	 *
	 * @param search The XPath Predicate
	 */
	public void addSortXPathPredicate(String predicate) {
		sortXPathPredicates.add(predicate);
		save();
	}

	/**
	 * Adds an xpath tools string to the properties.
	 *
	 * @param search The XPath tools
	 */
	public void addXPathTools(String predicate) {
		xpathTools.add(predicate);
		save();
	}

	/**
	 * Returns the list of xpath searches.
	 *
	 * @return the list of xpath searches.
	 */
	public Vector getXPathSearches() {
		return xpathSearches.get();
	}

	/**
	 * Returns the list of database drivers.
	 *
	 * @return the list of database drivers.
	 */
	public Vector getDatabaseDrivers() {
		return databaseDrivers.get();
	}

	/**
	 * Returns the list of database connections.
	 *
	 * @return the list of database connections.
	 */
	public Vector getDatabaseConnections() {
		return databaseConnections.get();
	}

	/**
	 * Returns the list of xpath predicates.
	 *
	 * @return the list of xpath predicates.
	 */
	public Vector getXPathPredicates() {
		return xpathPredicates.get();
	}

	/**
	 * Returns the list of sort xpath predicates.
	 *
	 * @return the list of sort xpath predicates.
	 */
	public Vector getSortXPathPredicates() {
		return sortXPathPredicates.get();
	}

	/**
	 * Returns the list of xpath for the tools.
	 *
	 * @return the list of xpath.
	 */
	public Vector getXPathTools() {
		return xpathTools.get();
	}

	/**
	 * Returns the previously used find in files folder
	 * 
	 * @return the last folder
	 */
	public String getFindInFilesFolder() {
		String result = "";
		Vector docs = findInFilesFolder.get();

		if (docs.size() > 0) {
			result = (String) docs.elementAt(0);
		}

		return result;
	}

	/**
	 * Sets the the find in files folder.
	 *
	 * @param the database connection
	 */
	public void setFindInFilesFolder(String newFolder) {
		findInFilesFolder.add(newFolder);
		save();
	}

	/**
	 * Returns the x/y position on screen of the editor.
	 *
	 * @return the x/y position on screen.
	 */
	public Point getPosition() {
		// Gather the default values...
		Toolkit kit = Toolkit.getDefaultToolkit();
		Dimension screen = kit.getScreenSize();
		Dimension app = getDimension();

		int posx = (screen.width - app.width) / 2;
		int posy = (screen.height - app.height) / 2;

		// if ( posx < 0) {
		// posx = 0;
		// }

		// if ( posy < 0) {
		// posy = 0;
		// }

		return new Point(getInteger(XPOS, posx), getInteger(YPOS, posy));
	}

	/**
	 * Sets the x/y position on screen of the editor.
	 *
	 * @return the x/y position on screen.
	 */
	public void setPosition(Point point) {
		set(XPOS, point.x);
		set(YPOS, point.y);
	}

	// private static Dimension getScreenSize() {
	// if ( screenSize == null) {
	// Toolkit kit = Toolkit.getDefaultToolkit();
	// Dimension screen = kit.getScreenSize();
	// Dimension app = getDimension();
	// }
	// }

	/**
	 * Returns the dimension on screen of the editor.
	 *
	 * @return the dimension on screen.
	 */
	public Dimension getDimension() {
		Toolkit kit = Toolkit.getDefaultToolkit();
		Dimension screen = kit.getScreenSize();

		return new Dimension(Math.min(getInteger(WIDTH, DEFAULT_WIDTH), screen.width),
				Math.min(getInteger(HEIGHT, DEFAULT_HEIGHT), screen.height));
	}

	/**
	 * Returns the path to the last opened document.
	 *
	 * @return the path to the last opened document.
	 */
	public String getLastOpenedDocument() {
		String result = "";
		Vector docs = lastDocuments.get();

		if (docs.size() > 0) {
			result = (String) docs.elementAt(0);
		}

		return result;
	}

	/**
	 * Returns the path to the last opened documents.
	 *
	 * @return the path to the last opened documents.
	 */
	public Vector getLastOpenedDocuments() {
		return lastDocuments.get();
	}

	/**
	 * Sets the path to the last opened document.
	 *
	 * @param path the path to the last opened document.
	 */
	public void setLastOpenedDocument(String path) {
		lastDocuments.add(path);
		save();
	}

	/**
	 * Returns the path to the last opened project.
	 *
	 * @return the path to the last opened project.
	 */
	public String getLastOpenedProject() {
		String result = "";
		Vector projects = lastProjects.get();

		if (projects.size() > 0) {
			result = (String) projects.elementAt(0);
		}

		return result;
	}

	/**
	 * Returns the paths to the last opened projects.
	 *
	 * @return the vector of last opened project paths.
	 */
	public Vector getLastOpenedProjects() {
		return lastProjects.get();
	}

	/**
	 * Sets the path to the last opened project.
	 *
	 * @param path the path to the last opened project.
	 */
	public void setLastOpenedProject(String path) {
		lastProjects.add(path);
		save();
	}

	/**
	 * Returns the list of session documents (currently open files).
	 *
	 * @return the list of session document paths.
	 */
	public Vector getSessionDocuments() {
		return sessionDocuments.get();
	}

	/**
	 * Sets the list of session documents (currently open files).
	 * This replaces the entire list.
	 *
	 * @param paths the Vector of document paths to save as session.
	 */
	public void setSessionDocuments(Vector paths) {
		// Clear existing entries by removing them one by one
		Vector existingPaths = sessionDocuments.get();
		for (int i = 0; i < existingPaths.size(); i++) {
			sessionDocuments.remove((String) existingPaths.elementAt(i));
		}

		// Add all new paths
		if (paths != null) {
			for (int i = 0; i < paths.size(); i++) {
				String path = (String) paths.elementAt(i);
				if (path != null && path.length() > 0) {
					sessionDocuments.add(path);
				}
			}
		}
		save();
	}

	public void setSessionActiveDocument(String path) {
		set(SESSION_ACTIVE_DOCUMENT, path);
	}

	public String getSessionActiveDocument() {
		return getText(SESSION_ACTIVE_DOCUMENT);
	}

	/**
	 * Sets the last opened folder for the file explorer.
	 *
	 * @param path the path to the last opened folder
	 */
	public void setLastOpenedFolder(String path) {
		set(LAST_OPENED_FOLDER, path);
		saveToDisk();
	}

	/**
	 * Gets the last opened folder for the file explorer.
	 *
	 * @return the path to the last opened folder, or null if none
	 */
	public String getLastOpenedFolder() {
		return getText(LAST_OPENED_FOLDER);
	}

	/** Absolute path of the DITA map last shown in the Map Explorer (restored on launch). */
	public void setLastDitaMap(String path) {
		set(LAST_DITA_MAP, path == null ? "" : path);
		saveToDisk();
	}
	public String getLastDitaMap() {
		return getText(LAST_DITA_MAP);
	}

	/**
	 * Sets whether the file explorer uses single-click to open files.
	 *
	 * @param enabled true to enable single-click open, false for double-click only
	 */
	public void setFileExplorerSingleClickOpen(boolean enabled) {
		set(FILE_EXPLORER_SINGLE_CLICK_OPEN, enabled);
		saveToDisk();
	}

	/**
	 * Gets whether the file explorer uses single-click to open files.
	 *
	 * @return true if single-click open is enabled, false otherwise (defaults to true)
	 */
	/** Whether the File Explorer lists hidden entries such as {@code .dogsbay/}. Off by default. */
	public boolean isFileExplorerShowHidden() {
		return getBoolean("file-explorer-show-hidden", false);
	}

	public void setFileExplorerShowHidden(boolean show) {
		set("file-explorer-show-hidden", show);
	}

	public boolean isFileExplorerSingleClickOpen() {
		return getBoolean(FILE_EXPLORER_SINGLE_CLICK_OPEN, true); // Default to true (single-click enabled)
	}

	public void setDitaPublishTranstype(String transtype) {
		set(DITA_PUBLISH_TRANSTYPE, transtype);
		saveToDisk();
	}

	public String getDitaPublishTranstype() {
		String val = getText(DITA_PUBLISH_TRANSTYPE);
		return (val != null && !val.isEmpty()) ? val : "html5";
	}

	public void setDitaPublishOutputDir(String path) {
		set(DITA_PUBLISH_OUTPUT_DIR, path);
		saveToDisk();
	}

	public String getDitaPublishOutputDir() {
		return getText(DITA_PUBLISH_OUTPUT_DIR);
	}

	public void setPreviewTheme(String themeName) {
		set(PREVIEW_THEME, themeName);
		saveToDisk();
	}

	public String getPreviewTheme() {
		String val = getText(PREVIEW_THEME);
		return (val != null && !val.isEmpty()) ? val : "Light";
	}

	public void setPreviewScrollSync(boolean sync) {
		set(PREVIEW_SCROLL_SYNC, sync);
		saveToDisk();
	}

	public boolean isPreviewScrollSync() {
		return getBoolean(PREVIEW_SCROLL_SYNC, true);
	}

	/**
	 * Returns the path to the last opened URL.
	 *
	 * @return the path to the last opened URL.
	 */
	public URL getLastOpenedURL() {
		Vector urls = lastURLs.get();

		if (urls.size() > 0) {
			return URLUtilities.decrypt((String) urls.elementAt(0));
		}

		return null;
	}

	/**
	 * Returns the list to the last opened URLS.
	 *
	 * @return the list to the last opened URLs.
	 */
	public Vector getLastOpenedURLs() {
		Vector urls = lastURLs.get();
		Vector newURLs = new Vector();

		for (int i = 0; i < urls.size(); i++) {
			newURLs.addElement(URLUtilities.decrypt((String) urls.elementAt(i)));
		}

		return newURLs;
	}

	/**
	 * Sets the last opened url.
	 *
	 * @param url the last opened url.
	 */
	public void setLastOpenedURL(URL url) {
		lastURLs.add(URLUtilities.encrypt(url));
		save();
	}

	/**
	 * Sets the the database driver.
	 *
	 * @param the database driver
	 */
	public void setDatabaseDrivers(String newDriver) {
		databaseDrivers.add(newDriver);
		save();
	}

	/**
	 * Sets the the database connection.
	 *
	 * @param the database connection
	 */
	public void setDatabaseConnections(String newConnection) {
		databaseConnections.add(newConnection);
		save();
	}

	/**
	 * Returns the last opened grammar index.
	 *
	 * @return the last opened grammar index -1 when no grammar selected.
	 */
	public int getLastOpenedGrammar() {
		return getInteger(LAST_OPENED_GRAMMAR, -1);
	}

	/**
	 * Set the last opened grammar.
	 *
	 * @param index the index of the last opened grammar. (-1 for all files)
	 */
	public void setLastOpenedGrammar(int index) {
		set(LAST_OPENED_GRAMMAR, index);
	}

	/**
	 * Sets the dimension on screen of the editor.
	 *
	 * @return the dimension on screen.
	 */
	public void setDimension(Dimension dimension) {
		set(WIDTH, dimension.width);
		set(HEIGHT, dimension.height);
	}

	/**
	 * Set the split divider location.
	 *
	 * @param location the split divider location.
	 */
	public void setDividerLocation(int location) {
		set(DIVIDER_LOCATION, location);
	}

	/**
	 * Gets the location of the split divider.
	 *
	 * @return the location of the split divider.
	 */
	public int getDividerLocation() {
		return getInteger(DIVIDER_LOCATION, DEFAULT_DIVIDER_LOCATION);
	}

	/**
	 * Set the split divider location.
	 *
	 * @param location the split divider location.
	 */
	public void setTopDividerLocation(int location) {
		set(TOP_DIVIDER_LOCATION, location);
	}

	/**
	 * Gets the location of the split divider.
	 *
	 * @return the location of the split divider.
	 */
	public int getTopDividerLocation() {
		return getInteger(TOP_DIVIDER_LOCATION, DEFAULT_TOP_DIVIDER_LOCATION);
	}

	/**
	 * Remember how wide the right sidebar is — its width, not the divider's
	 * position. A divider position is measured from the left, so it only means
	 * what it meant if the window is the same size; restored into a narrower
	 * window it squeezed the panel to a sliver, and saving that on exit made it
	 * permanent.
	 */
	public void setRightPanelWidth(int width) {
		set(RIGHT_PANEL_WIDTH, width);
	}

	/** The remembered right sidebar width, or -1 when nobody has set one. */
	public int getRightPanelWidth() {
		return getInteger(RIGHT_PANEL_WIDTH, DEFAULT_RIGHT_PANEL_WIDTH);
	}

	/**
	 * Returns the scenario properties list.
	 *
	 * @return the scenario properties.
	 */
	public Vector getScenarioProperties() {
		Vector result = new Vector();
		Vector list = getProperties(ScenarioProperties.SCENARIO_PROPERTIES);

		for (int i = 0; i < list.size(); i++) {

			result.addElement(new ScenarioProperties((Properties) list.elementAt(i)));
		}

		return result;
	}

	/**
	 * Adds a scenario properties object to this element.
	 *
	 * @param props the scenario properties.
	 */
	public void addScenarioProperties(ScenarioProperties props) {
		add(props);
		save();
	}

	/**
	 * Removes a scenario properties object from this element.
	 *
	 * @param props the scenario properties.
	 */
	public void removeScenarioProperties(ScenarioProperties props) {
		remove(props);
		save();
	}

	Vector bookmarks = null;

	/**
	 * Returns the bookmark list.
	 *
	 * @return the bookmarks.
	 */
	public Vector getBookmarks() {
		if (bookmarks == null) {
			bookmarks = new Vector();
			Vector list = getProperties(Bookmark.BOOKMARK);

			for (int i = 0; i < list.size(); i++) {
				Bookmark bm = new Bookmark((Properties) list.elementAt(i));

				if (bm.getURL() != null && bm.getURL().trim().length() > 0) {
					bookmarks.addElement(new Bookmark((Properties) list.elementAt(i)));
				} else {
					removeBookmark(bm);
				}
			}
		}

		return bookmarks;
	}

	/**
	 * Adds a Bookmark object to this element.
	 *
	 * @param props the Bookmark properties.
	 */
	public void addBookmark(Bookmark props) {
		add(props);
		bookmarks.addElement(props);
		save();
	}

	/**
	 * Removes a Bookmark object from this element.
	 *
	 * @param props the Bookmark.
	 */
	public void removeBookmark(Bookmark props) {
		remove(props);
		bookmarks.removeElement(props);
		save();
	}

	/**
	 * Returns the template properties list.
	 *
	 * @return the template properties.
	 */
	public Vector getTemplateProperties() {
		Vector result = new Vector();
		Vector list = getProperties(TemplateProperties.TEMPLATE_PROPERTIES);

		for (int i = 0; i < list.size(); i++) {
			result.addElement(new TemplateProperties((Properties) list.elementAt(i)));
		}

		result.addAll(builtInTemplates);
		return result;
	}

	/**
	 * Registers a template contributed by a built-in plugin. The entry is not
	 * persisted to user config — the plugin must re-register on every startup.
	 */
	public void addBuiltInTemplateProperties(TemplateProperties props) {
		if (props == null) {
			return;
		}
		String name = props.getName();
		for (int i = 0; i < builtInTemplates.size(); i++) {
			TemplateProperties existing = (TemplateProperties) builtInTemplates.elementAt(i);
			if (name != null && name.equals(existing.getName())) {
				return;
			}
		}
		builtInTemplates.add(props);
	}

	/**
	 * Get template properties sorted by Most Recently Used (MRU).
	 * Templates that have been used appear first, sorted by timestamp (newest first).
	 * Templates never used appear after, sorted alphabetically by name.
	 *
	 * @return Vector of TemplateProperties sorted by MRU
	 */
	public Vector getTemplatesSortedByUsage() {
		Vector templates = getTemplateProperties();

		// Separate into used and unused templates
		Vector usedTemplates = new Vector();
		Vector unusedTemplates = new Vector();

		for (int i = 0; i < templates.size(); i++) {
			TemplateProperties template = (TemplateProperties) templates.elementAt(i);
			if (template.hasBeenUsed()) {
				usedTemplates.addElement(template);
			} else {
				unusedTemplates.addElement(template);
			}
		}

		// Sort used templates by timestamp (most recent first)
		for (int i = 0; i < usedTemplates.size() - 1; i++) {
			for (int j = i + 1; j < usedTemplates.size(); j++) {
				TemplateProperties t1 = (TemplateProperties) usedTemplates.elementAt(i);
				TemplateProperties t2 = (TemplateProperties) usedTemplates.elementAt(j);

				if (t2.getLastUsedTimestamp() > t1.getLastUsedTimestamp()) {
					usedTemplates.setElementAt(t2, i);
					usedTemplates.setElementAt(t1, j);
				}
			}
		}

		// Sort unused templates alphabetically by name
		for (int i = 0; i < unusedTemplates.size() - 1; i++) {
			for (int j = i + 1; j < unusedTemplates.size(); j++) {
				TemplateProperties t1 = (TemplateProperties) unusedTemplates.elementAt(i);
				TemplateProperties t2 = (TemplateProperties) unusedTemplates.elementAt(j);

				if (t2.getName().compareToIgnoreCase(t1.getName()) < 0) {
					unusedTemplates.setElementAt(t2, i);
					unusedTemplates.setElementAt(t1, j);
				}
			}
		}

		// Combine: used templates first, then unused
		Vector result = new Vector();
		for (int i = 0; i < usedTemplates.size(); i++) {
			result.addElement(usedTemplates.elementAt(i));
		}
		for (int i = 0; i < unusedTemplates.size(); i++) {
			result.addElement(unusedTemplates.elementAt(i));
		}

		return result;
	}

	/**
	 * Adds a template properties object to this element.
	 *
	 * @param props the template properties.
	 */
	public void addTemplateProperties(TemplateProperties props) {
		add(props);
		save();
	}

	/**
	 * Removes a template properties object from this element.
	 *
	 * @param props the template properties.
	 */
	public void removeTemplateProperties(TemplateProperties props) {
		remove(props);
		save();
	}

	/**
	 * Returns the grammar properties list.
	 *
	 * @return the grammar properties.
	 */
	public Vector getGrammarProperties() {
		Vector result = new Vector();
		Vector list = getProperties(GrammarProperties.GRAMMAR_PROPERTIES);

		for (int i = 0; i < list.size(); i++) {

			result.addElement(new GrammarProperties(this, (Properties) list.elementAt(i)));
		}

		result.addAll(builtInGrammars);
		return result;
	}

	/**
	 * Registers a grammar type contributed by a built-in plugin. The entry is
	 * not persisted to user config — the plugin must re-register on every startup.
	 * Deduplicates by description so repeated startup activations do not stack.
	 */
	public void addBuiltInGrammarProperties(GrammarProperties props) {
		if (props == null) {
			return;
		}
		String description = props.getDescription();
		for (int i = 0; i < builtInGrammars.size(); i++) {
			GrammarProperties existing = (GrammarProperties) builtInGrammars.elementAt(i);
			if (description != null && description.equals(existing.getDescription())) {
				return;
			}
		}
		builtInGrammars.add(props);
	}

	/**
	 * Adds a grammar properties object to this element.
	 *
	 * @param props the grammar properties.
	 */
	public void addGrammarProperties(GrammarProperties props) {
		add(props);
		save();
	}

	/**
	 * Adds a grammar properties object to this element.
	 *
	 * @param props the grammar properties.
	 */
	public void removeGrammarProperties(GrammarProperties props) {
		remove(props);
		save();
	}

	/**
	 * Returns the project properties list.
	 *
	 * @return the project properties.
	 */
	public Vector getProjectProperties() {
		Vector result = new Vector();
		Vector list = getProperties(ProjectProperties.PROJECT_PROPERTIES);

		if (DEBUG)
			System.out
					.println("ConfigurationProperties.getProjectProperties: Found " + list.size() + " projects in XML");

		for (int i = 0; i < list.size(); i++) {
			ProjectProperties proj = new ProjectProperties((Properties) list.elementAt(i));
			if (DEBUG)
				System.out.println("  Loading project: " + proj.getName() + " with "
						+ proj.getDocumentProperties().size() + " documents");
			result.addElement(proj);
		}

		return result;
	}

	/**
	 * Adds a project properties object to this element.
	 *
	 * @param props the project properties.
	 */
	public ProjectProperties addProjectProperties(ProjectProperties props) {
		add(props);
		save();
		// add() stores a *clone* of the element, so the object handed in is left
		// detached from the document: anything set on it afterwards is written
		// to nothing. Hand back the copy that is actually in the settings, so a
		// caller that configures a project after adding it keeps the changes.
		Vector list = getProperties(ProjectProperties.PROJECT_PROPERTIES);
		if (list.isEmpty()) {
			return props;
		}
		return new ProjectProperties((Properties) list.elementAt(list.size() - 1));
	}

	/**
	 * Adds a project properties object to this element.
	 *
	 * @param props the project properties.
	 */
	public void removeProjectProperties(ProjectProperties props) {
		remove(props);
		save();
	}

	/**
	 * Returns the default directory where frameworks are installed.
	 * Creates the directory if it does not yet exist.
	 *
	 * @return the default frameworks directory (e.g. ~/.dogsbay/frameworks/)
	 */
	public static java.io.File getDefaultFrameworksDirectory() {
		java.io.File dir = new java.io.File(Main.DOGSBAY_HOME + "frameworks");
		if (!dir.exists()) {
			dir.mkdirs();
		}
		return dir;
	}

	/**
	 * Returns the installed framework properties list.
	 *
	 * @return the framework properties.
	 */
	public Vector getFrameworkProperties() {
		Vector result = new Vector();
		Vector list = getProperties(FrameworkProperties.FRAMEWORK_PROPERTIES);

		for (int i = 0; i < list.size(); i++) {
			result.addElement(new FrameworkProperties((com.dogsbay.xml.properties.Properties) list.elementAt(i)));
		}

		return result;
	}

	/**
	 * Adds a framework properties object to this element.
	 *
	 * @param props the framework properties.
	 */
	public void addFrameworkProperties(FrameworkProperties props) {
		add(props);
		save();
	}

	/**
	 * Removes a framework properties object from this element.
	 *
	 * @param props the framework properties.
	 */
	public void removeFrameworkProperties(FrameworkProperties props) {
		remove(props);
		save();
	}

	/**
	 * Gets the name of the last opened project.
	 *
	 * @return the last project name, or null if none
	 */
	public String getLastProjectName() {
		return getText(LAST_PROJECT_NAME);
	}

	/**
	 * Sets the name of the last opened project.
	 *
	 * @param projectName the project name to save
	 */
	public void setLastProjectName(String projectName) {
		set(LAST_PROJECT_NAME, projectName);
		saveToDisk();
	}

	/**
	 * Returns the text preferences.
	 *
	 * @return the text preferences.
	 */
	public TextPreferences getTextPreferences() {
		return textPreferences;
	}


	/**
	 * Returns the print preferences.
	 *
	 * @return the print preferences.
	 */
	public PrintPreferences getPrintPreferences() {
		return printPreferences;
	}


	/**
	 * Returns the properties for the helper.
	 *
	 * @return the helper properties.
	 */
	public HelperProperties getHelperProperties() {
		return helperProperties;
	}

	/**
	 * Returns the properties for the debugger.
	 *
	 * @return the debugger properties.
	 */
	// public DebuggerProperties getDebuggerProperties() {
	// return debuggerProperties;
	// }

	/**
	 * Returns the properties for the navigator.
	 *
	 * @return the navigator properties.
	 */
	public NavigatorProperties getNavigatorProperties() {
		return navigatorProperties;
	}

	/**
	 * Returns the properties for the text editor.
	 *
	 * @return the text editor properties.
	 */
	public EditorProperties getEditorProperties() {
		return editorProperties;
	}

	/**
	 * Returns the properties for the XML viewer.
	 *
	 * @return the XML viewer properties.
	 */
	public ViewerProperties getViewerProperties() {
		return viewerProperties;
	}

	/**
	 * Returns the properties for the XML browser.
	 *
	 * @return the XML browser properties.
	 */
	public BrowserProperties getBrowserProperties() {
		return browserProperties;
	}

	/**
	 * Returns the properties for the Grid.
	 *
	 * @return the Grid properties.
	 */
	/*
	 * public GridProperties getGridProperties() {
	 * return gridProperties;
	 * }
	 */

	/**
	 * Returns the properties for the plugin view.
	 *
	 * @return the plugin view properties.
	 */
	public XElement getPluginViewProperties(String propertiesIdentifier) {

		return (get(propertiesIdentifier));
	}

	/**
	 * tjc 16102008
	 * Doesnt save to disk anymore
	 * Saves the configuration properties.
	 * 
	 */
	public void save() {
		// Guard the DOM-mutation batch against the background saveToDisk() serializer
		// (the 300s timer and exit() both call saveToDisk() off the EDT). Without this,
		// serializing while update() rewrites the DOM can throw ConcurrentModificationException.
		synchronized (saveLock) {

		// Make sure the properties are up to date...
		editorProperties.update();
		viewerProperties.update();
		browserProperties.update();
		// gridProperties.update();

		if (getPluginProperties() != null) {
			for (int cnt = 0; cnt < getPluginProperties().size(); ++cnt) {
				if (getPluginProperties().get(cnt) instanceof PluginViewProperties) {
					((PluginViewProperties) getPluginProperties().get(cnt)).save();
				}
			}
		}

		if (getPropertyFiles() != null) {
			for (int cnt = 0; cnt < getPropertyFiles().size(); ++cnt) {
				if (getPropertyFiles().get(cnt) instanceof PropertiesFile) {
					((PropertiesFile) getPropertyFiles().get(cnt)).save();
				}
			}
		}

		// Rebuild bookmark elements in the DOM: remove all existing, then
		// re-add from the in-memory list after updating each bookmark's state.
		// This is needed because Properties.add() clones elements, so the
		// bookmark's own element diverges from the config DOM copy.
		Vector bms = getBookmarks();
		List existingBookmarkElements = getElement().elements(Bookmark.BOOKMARK);
		for (int i = existingBookmarkElements.size() - 1; i >= 0; i--) {
			getElement().remove((org.dom4j.Element) existingBookmarkElements.get(i));
		}
		for (int i = 0; i < bms.size(); i++) {
			Bookmark bm = (Bookmark) bms.elementAt(i);
			bm.update();
			add(bm);
		}

		// debuggerProperties.update();

		helperProperties.update();
		navigatorProperties.update();

		xercesProperties.update();
		replaces.update();
		searches.update();
		xpaths.update();
		xpathSearches.update();
		xpathPredicates.update();
		sortXPathPredicates.update();
		xpathTools.update();
		lastDocuments.update();
		lastProjects.update();
		prefixNamespaceMappings.update();
		lastURLs.update();
		sessionDocuments.update();
		extensions.update();
		catalogs.update();
		databaseDrivers.update();
		databaseConnections.update();
		findInFilesFolder.update();

		/*
		 * try {
		 * setDefaultNamespace( document.getRoot());
		 * XMLUtilities.write( document.getDocument(), document.getURL());
		 *
		 * } catch( Exception e) {
		 * e.printStackTrace();
		 * }
		 */
		} // end synchronized (saveLock)
	}

	/** Serializes-vs-mutates guard shared by {@link #save()} and {@link #saveToDisk()}. */
	private final Object saveLock = new Object();

	/**
	 * Saves the properties to disk, should only be done
	 * when shutting down the editor to avoid unneeded writes to disk
	 */
	/**
	 * Refuse every write to the settings file. Set when the file on disk was
	 * written by a newer build: this build's model does not know the settings it
	 * holds, so saving would drop them and leave the file still claiming the
	 * newer version. Reading it is fine; writing over it is not.
	 */
	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}

	public boolean isReadOnly() {
		return readOnly;
	}

	private volatile boolean readOnly = false;

	public void saveToDisk() {
		if (readOnly) {
			return;
		}
		synchronized (saveLock) {
			try {
				// setDefaultNamespace( document.getRoot());
				// Write atomically (temp + rename) under the lock so a crash or a race
				// can never leave ~/.dogsbay-v33.xml truncated — losing all settings.
				writeConfigAtomically(document.getDocument(), document.getURL());

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		if (getPluginProperties() != null) {
			for (int cnt = 0; cnt < getPluginProperties().size(); ++cnt) {
				if (getPluginProperties().get(cnt) instanceof PluginViewProperties) {
					((PluginViewProperties) getPluginProperties().get(cnt)).saveToDisk();
				}
			}
		}

		if (getPropertyFiles() != null) {
			for (int cnt = 0; cnt < getPropertyFiles().size(); ++cnt) {
				if (getPropertyFiles().get(cnt) instanceof PropertiesFile) {
					((PropertiesFile) getPropertyFiles().get(cnt)).saveToDisk();
				}
			}
		}
	}

	/**
	 * Serializes the config document to a temp file in the same directory and
	 * atomically moves it into place. If serialization fails (e.g. a torn read
	 * during a concurrent mutation), the previously-good file is left untouched
	 * and the exception propagates.
	 */
	private static void writeConfigAtomically(com.dogsbay.xml.XDocument document, URL url) throws Exception {
		File target;
		try {
			target = new File(url.toURI());
		} catch (Exception e) {
			target = new File(url.getFile());
		}

		Path targetPath = target.toPath();
		Path dir = targetPath.toAbsolutePath().getParent();
		if (dir != null) {
			Files.createDirectories(dir);
		}

		Path tmp = Files.createTempFile(dir, target.getName() + ".", ".tmp");
		try {
			URL tmpUrl = tmp.toUri().toURL();
			XMLUtilities.write(document, tmpUrl);

			try {
				Files.move(tmp, targetPath, StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, targetPath, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (Exception e) {
			try {
				Files.deleteIfExists(tmp);
			} catch (Exception ignored) {
				// best effort
			}
			throw e;
		}
	}

	/*
	 * private void setDefaultNamespace( XElement element) {
	 * XElement[] elements = element.getElements();
	 * 
	 * for ( int i = 0; i < elements.length; i++) {
	 * setDefaultNamespace( elements[i]);
	 * }
	 * 
	 * QName qname = new QName(element.getName(), Namespace.get(
	 * "http://www.dogsbay.ai/dogsbay-editor/"+Identity.getIdentity().getVersion()+
	 * "/"));
	 * element.remove(element.getNamespace());
	 * element.setQName(qname);
	 * //element.setNamespace( Namespace.get(
	 * "http://www.dogsbay.ai/dogsbay-editor/"+Identity.getIdentity().getVersion()+
	 * "/"));
	 * }
	 */

	/**
	 * @param pluginProperties the pluginProperties to set
	 */
	public void setPluginProperties(List pluginProperties) {

		this.pluginProperties = pluginProperties;
	}

	/**
	 * @return the pluginProperties
	 */
	public List getPluginProperties() {

		return pluginProperties;
	}

	public boolean isWindowMaximised() {
		return getBoolean(WINDOW_MAXIMISED, true);
	}

	public void setWindowMaximised(boolean maximised) {
		set(WINDOW_MAXIMISED, maximised);
	}

	public void setXercesProperties(XercesProperties xercesProperties) {
		this.xercesProperties = xercesProperties;
	}

	public XercesProperties getXercesProperties() {
		return xercesProperties;
	}

	public void setSavePropertiesInterval(long interval) {
		set(SAVE_PROPERTIES_INTERVAL, interval);
	}

	public long getSavePropertiesInterval() {
		return (getLong(SAVE_PROPERTIES_INTERVAL, DEFAULT_SAVE_PROPERTIES_INTERVAL));
	}

	public void setPropertyFiles(List propertyFiles) {
		this.propertyFiles = propertyFiles;
	}

	public List getPropertyFiles() {
		if (propertyFiles == null) {
			propertyFiles = new ArrayList();
		}
		return propertyFiles;
	}

}