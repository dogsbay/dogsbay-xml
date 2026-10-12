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

import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import javax.swing.Action;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.JCheckBoxMenuItem;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.XElement;
import com.dogsbay.xml.properties.Properties;
import com.dogsbay.xml.properties.PropertiesFile;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayMenuItem;
import com.dogsbay.dogsbayaieditor.plugins.PluginActionKeyMapping;
// import com.dogsbay.xslt.debugger.ui.XSLTDebuggerFrame;


/**
 * Handles the KeyMapping Preferences.
 *
 * @version	$Revision: 1.51 $, $Date: 2005/09/05 15:16:25 $
 * @author Dogs bay
 */
public class KeyPreferences extends PropertiesFile {

	

	public static final String KEY_MAPPINGS		= "key-mappings";

	// Commands that arrived after the key settings stopped being able to grow:
	// the views, the panels, the agent and the project checks.
	public static final String SHOW_EDITOR_VIEW_ACTION = "View:  ShowEditorView";
	public static final String SHOW_EDITOR_VIEW_ACTION_DESC = "Show the Editor view";
	public static final String SHOW_AUTHOR_VIEW_ACTION = "View:  ShowAuthorView";
	public static final String SHOW_AUTHOR_VIEW_ACTION_DESC = "Show the Author view";
	public static final String TOGGLE_AUTHOR_SPLIT_ACTION = "View:  ToggleAuthorSplit";
	public static final String TOGGLE_AUTHOR_SPLIT_ACTION_DESC = "Show the Author view beside the source";
	public static final String TOGGLE_PRIMARY_SIDEBAR_ACTION = "View:  TogglePrimarySidebar";
	public static final String TOGGLE_PRIMARY_SIDEBAR_ACTION_DESC = "Show or hide the primary sidebar";
	public static final String TOGGLE_BOTTOM_PANEL_ACTION = "View:  ToggleBottomPanel";
	public static final String TOGGLE_BOTTOM_PANEL_ACTION_DESC = "Show or hide the bottom panel";
	public static final String TOGGLE_SECONDARY_SIDEBAR_ACTION = "View:  ToggleSecondarySidebar";
	public static final String TOGGLE_SECONDARY_SIDEBAR_ACTION_DESC = "Show or hide the secondary sidebar";
	public static final String FOCUS_AGENT_ACTION = "Agent:  FocusAgent";
	public static final String FOCUS_AGENT_ACTION_DESC = "Go to the AI Agent panel";
	public static final String SEND_SELECTION_TO_AGENT_ACTION = "Agent:  SendSelection";
	public static final String SEND_SELECTION_TO_AGENT_ACTION_DESC = "Send the selection to the AI Agent";
	public static final String SHOW_PROPOSALS_ACTION = "Agent:  ShowProposals";
	public static final String SHOW_PROPOSALS_ACTION_DESC = "Go to the Proposals panel";
	public static final String AGENT_ACTIVITY_ACTION = "Agent:  Activity";
	public static final String AGENT_ACTIVITY_ACTION_DESC = "Show what agents have changed";
	private static final String ACTIVE_CONFIG	= "active-configuration";
	private static final String CONFIG			= "configuration";
	private static final String NAME			= "name";
	private static final String KEYMAP			= "keymap";
	private static final String ACTION			= "action";
	private static final String DESCRIPTION		= "description";
	private static final String KEYSTROKE		= "keystroke";
	private static final String KEY				= "key";
	private static final String MASK			= "mask";
	private static final String VALUE			= "value";
	static final String DEFAULT			= "default";
	public static final String EMACS			= "emacs";
	static final String CTRL			= "Ctrl";
	static final String ALT				= "Alt";
	static final String CTRLSHIFT		= "Ctrl+Shift";
	private static final String CTRLALT			= "Ctrl+Alt";
	private static final String CTRLALTSHIFT	= "Ctrl+Alt+Shift";
	private static final String ALTSHIFT		= "ALT+Shift";
	private static final String META			= "Meta";
	static final String SHIFT			= "Shift";
	
	//for mac os
	static final String CMD				= "Command";
	static final String CMDSHIFT		= "Command+Shift";
	private static final String CMDALT			= "Command+Alt";
	private static final String CMDCTRL			= "Command+Ctrl";
	private static final String CMDALTSHIFT		= "Command+Alt+Shift";
	private static final String CMDCTRLSHIFT	= "Command+Ctrl+Shift";
	private static final String CMDCTRLALTSHIFT	= "Command+Ctrl+Alt+Shift";
	
	
	private List pluginMappings = null;

	//all the possible actions
	public static final String OPEN_ACTION				= "File:  Open";
	
	public static final String CLOSE_ACTION				= "File:  Close";
	
	public static final String CLOSE_ALL_ACTION			= "File:  CloseAll";
	
	public static final String SAVE_ACTION				= "File:  Save";
	
	public static final String SAVE_ALL_ACTION			= "File:  SaveAll";
	
	public static final String UNDO_ACTION				= "Edit:  Undo";
	
	public static final String SELECT_ALL_ACTION		= "Editor:  SelectAll";
	
	public static final String SAVE_AS_ACTION			= "File:  SaveAs";
	
	public static final String SELECT_DOCUMENT_ACTION		= "View:  SelectDocument";
	
	public static final String PRINT_ACTION				= "File:  Print";
	
	public static final String FIND_ACTION				= "Edit:  Find";
	
	public static final String FIND_NEXT_ACTION			= "Edit:  FindNext";
	
	public static final String REPLACE_ACTION			= "Edit:  Replace";
	
	public static final String CUT_ACTION 			= "Editor:  Cut";
	
	public static final String COPY_ACTION 			= "Editor:  Copy";
	
	public static final String PASTE_ACTION 		= "Editor:  Paste";
	
	public static final String COMMENT_ACTION 		= "Edit:  AddComment";
	
	public static final String TAB_ACTION 			= "Edit:  Indent";
	
	public static final String GOTO_ACTION 			= "Edit:  GotoLine";
	
	public static final String UNINDENT_ACTION 		= "Edit:  Unindent";
	
	public static final String UP_ACTION 			= "Editor:  Up";
	
	public static final String DOWN_ACTION 			= "Editor:  Down";
	
	public static final String RIGHT_ACTION 		= "Editor:  Forward";
	
	public static final String LEFT_ACTION 			= "Editor:  Backward";
	
	public static final String PAGE_UP_ACTION 		= "Editor:  PageUp";
	
	public static final String PAGE_DOWN_ACTION 		= "Editor:  PageDown";
	
	public static final String BEGIN_LINE_ACTION 		= "Editor:  BeginLine";
	
	public static final String END_LINE_ACTION 				= "Editor:  EndLine";
	
	public static final String BEGIN_ACTION 				= "Editor:  Begin";
	
	public static final String END_ACTION 					= "Editor:  End";
	
	public static final String PREVIOUS_WORD_ACTION 		= "Editor:  PreviousWord";
	
	public static final String NEXT_WORD_ACTION 			= "Editor:  NextWord";
	
	public static final String DELETE_NEXT_CHAR_ACTION 			= "Editor:  DeleteNextChar";
	
	public static final String DELETE_PREV_CHAR_ACTION 			= "Editor:  DeletePrevChar";
	
	public static final String WELL_FORMEDNESS_ACTION 			= "XML:  WellFormedness";
	
	public static final String VALIDATE_ACTION 					= "XML:  Validate";
	
	public static final String START_BROWSER_ACTION 			= "Tools:  StartBrowser";

	public static final String PREVIEW_IN_TAB_ACTION 			= "Tools:  PreviewInTab";
	public static final String PREVIEW_IN_SPLIT_ACTION 			= "Tools:  PreviewInSplit";

	public static final String NEW_DOCUMENT_ACTION 				= "File:  NewDocument";
	
	public static final String REDO_ACTION 						= "Edit:  Redo";
	
	public static final String SELECT_ELEMENT_ACTION 			= "Edit:  SelectElement";
	
	public static final String SELECT_ELEMENT_CONTENT_ACTION 	  = "Edit:  SelectElementContent";
	
	public static final String INSERT_SPECIAL_CHAR_ACTION 	    = "Edit:  InsertSpecialChar";
	
	public static final String TAG_ACTION 	  					= "Edit:  AddTag";
	
	public static final String REPEAT_TAG_ACTION 				= "Edit:  RepeatTag";
	
	public static final String GOTO_START_TAG_ACTION 	  		= "Edit:  GotoStartTag";

	public static final String TOGGLE_EMPTY_ELEMENT_ACTION 	  	= "Edit: ExpandEmptyElement";

	public static final String RENAME_ELEMENT_ACTION 	  		= "Edit: RenameElement";

	public static final String GOTO_END_TAG_ACTION 	  		    = "Edit:  GotoEndTag";
	

	public static final String TOGGLE_BOOKMARK_ACTION 	  		= "Edit:  ToggleBookmark";

	public static final String GOTO_NEXT_ATTRIBUTE_VALUE_ACTION			= "Edit: GotoNextAttributeValue";
	
	public static final String GOTO_PREVIOUS_ATTRIBUTE_VALUE_ACTION		 = "Edit: GotoPreviousAttributeValue";


	public static final String SELECT_BOOKMARK_ACTION 	  		= "Edit:  SelectBookmark";
	
	public static final String SELECT_FRAGMENT_ACTION 	  		= "Edit:  SelectFragment";

	public static final String SCHEMA_ACTION 	  				= "Views:  Schema";
	
	public static final String OUTLINER_ACTION 	  				= "Views:  Outliner";
	
	public static final String EDITOR_ACTION 	  				= "Views:  Editor";
	
	public static final String VIEWER_ACTION 	  				= "Views:  Viewer";
	
	//public static final String GRID_ACTION 	  				= "Views:  Grid";
	//public static final String GRID_ACTION_DESC 			= "Switch to grid";
	//public static final String BROWSER_ACTION 	  				= "browserAction";
	//public static final String BROWSER_ACTION_DESC 				= "Switch to browser";
	
	public static final String ADD_ELEMENT_OUTLINER_ACTION 	  	= "Edit:  AddElementOutliner";
	
	public static final String DELETE_ELEMENT_OUTLINER_ACTION 	    = "Edit:  DeleteElementOutliner";
	
	public static final String OPEN_REMOTE_ACTION			= "File:  OpenRemote";
	
	public static final String RELOAD_ACTION				= "File:  Reload";
	
	public static final String SAVE_AS_TEMPLATE_ACTION		= "File:  SaveAsTemplate";
	
	public static final String MANAGE_TEMPLATE_ACTION		= "File:  ManageTemplate";
	
	public static final String PAGE_SETUP_ACTION				= "File:  PageSetUp";
	
	public static final String PREFERENCES_ACTION				= "File:  Preferences";
	
	public static final String CREATE_REQUIRED_NODE_ACTION		= "Edit:  CreateRequiredNode";
	
	public static final String SPLIT_ELEMENT_ACTION		   		= "Edit:  SplitElement";
	
	public static final String CONVERT_ENTITIES_ACTION		   	= "Edit:  ConvertEntities";
	
	public static final String CONVERT_CHARACTERS_ACTION		 = "Edit:  ConvertCharacters";
	
	public static final String STRIP_TAG_ACTION		   = "Edit:  StripTags";
	
	public static final String ADD_CDATA_ACTION		   = "Edit:  AddCDATA";
	
	public static final String LOCK_ACTION		  		= "Edit:  Lock";
	
	public static final String FORMAT_ACTION			= "Edit:  Format";
	
	public static final String EXPAND_ALL_ACTION		= "View:  ExpandAll";
	
	public static final String COLLAPSE_ALL_ACTION		= "View:  CollapseAll";
	
	public static final String SYNCHRONISE_ACTION		= "View:  SynchroniseSelection";
	
	public static final String TOGGLE_FULL_ACTION		= "View:  ToggleFullScreen";
	
	public static final String NEW_PROJECT_ACTION		= "Project:  NewProject";
	
	public static final String IMPORT_PROJECT_ACTION			= "Project:  ImportProject";
	
	public static final String DELETE_PROJECT_ACTION			= "Project:  DeleteProject";
	
	public static final String RENAME_PROJECT_ACTION			= "Project:  RenameProject";
	
	public static final String CHECK_WELLFORMEDNESS_ACTION		 = "Project:  ProjectCheckWellFormedness";
	
	public static final String VALIDATE_PROJECT_ACTION			= "Project:  ProjectValidate";
	
	public static final String FIND_IN_PROJECTS_ACTION			= "Project:  FindInProjects";
	
	public static final String FIND_IN_FILES_ACTION				= "File:  FindInFiles";
	
	public static final String ADD_FILE_ACTION					= "Project:  AddFile";
	
	public static final String ADD_REMOTE_FILE_ACTION  		  	= "Project:  AddRemoteFile";
	
	public static final String REMOVE_FILE_ACTION  		  		= "Project:  RemoveFile";
	
	public static final String ADD_DIRECTORY_ACTION  	  		= "Project:  AddDirectory";
	
	public static final String ADD_DIRECTORY_CONTENTS_ACTION  	  	= "Project:  AddDirectoryontents";
	
	public static final String ADD_FOLDER_ACTION  	  			= "Project:  AddFolder";
	
	public static final String REMOVE_FOLDER_ACTION  	  		= "Project:  RemoveFolder";
	
	public static final String RENAME_FOLDER_ACTION  	  		= "Project:  RenameFolder";
	
	public static final String VALIDATE_XML_SCHEMA_ACTION 		= "Schema:  ValidateXMLSchema";
	
	public static final String VALIDATE_DTD_ACTION 	       		= "Schema:  ValidateDTD";
	
	public static final String VALIDATE_RELAXNG_ACTION 	   	 	= "Schema:  ValidateRelaxNG";
	
	public static final String SET_XML_DECLARATION_ACTION 	   	   = "XML:  SetXMLDeclaration";
	
	public static final String SET_DOCTYPE_DECLARATION_ACTION 	   = "XML:  SetDOCTYPEDeclaration";
	
	public static final String SET_SCHEMA_LOCATION_ACTION 	       = "XML:  SetSchemaLocation";
	
	public static final String RESOLVE_XINCLUDES_ACTION 	   	= "XML:  ResolveXIncludes";
	
	public static final String SET_SCHEMA_PROPS_ACTION 	   	   	= "XML:  SetDocumentProperties";
	
	public static final String INFER_SCHEMA_ACTION 	   	   		= "Schema:  InferSchemaProperties";
	
	public static final String CREATE_TYPE_ACTION 	   	   		= "Schema:  CreateType";
	
	public static final String SET_TYPE_ACTION 	   	   			= "Schema:  SetType";
	
	public static final String TYPE_PROPERTIES_ACTION 	   	 	= "Schema:  TypeProperties";
	
	public static final String MANAGE_TYPES_ACTION 	   	     	= "Schema:  ManageTypes";
	
	public static final String CONVERT_SCHEMA_ACTION 	   	 	= "Schema:  ConvertSchema";

	



	public static final String EXECUTE_SIMPLE_XSLT_ACTION 	   	 		= "Transform:  ExecuteSimpleXSLT";
	
	public static final String EXECUTE_ADVANCED_XSLT_ACTION 	   	 		= "Transform:  ExecuteAdvancedXSLT";
	
	public static final String EXECUTE_PREVIOUS_XSLT_ACTION 	  = "Transform:  ExecutePreviousXSLT";

	public static final String EXECUTE_FO_ACTION 	   	 		= "Transform:  ExecuteFO";
	
	public static final String EXECUTE_PREVIOUS_FO_ACTION 	   	= "Transform:  ExecutePreviousFO";

	public static final String EXECUTE_XQUERY_ACTION 	   	 	= "Transform:  ExecuteXQuery";
	
	
	public static final String EXECUTE_PREVIOUS_XQUERY_ACTION 	   	= "Transform:  ExecutePreviousXQuery";

	public static final String EXECUTE_SCENARIO_ACTION 	   	 	= "Transform:  ExecuteScenario";
	
	public static final String EXECUTE_PREVIOUS_SCENARIO_ACTION 		= "Transform:  ExecutePreviousScenario";

	public static final String XSLT_DEBUGGER_ACTION 	   	 	= "Transform:  StartXSLTDebugger";
	
	public static final String MANAGE_SCENARIOS_ACTION	   	 	= "Transform:  ManageScenarios";
	
	
	
	public static final String SEND_SOAP_MESSAGE_ACTION	   	 	= "Tools:  SendSOAPMessage";
	
	public static final String ANALYSE_WSDL_ACTION	   	 		= "Tools:  AnalyseWSDL";
	
	public static final String CLEAN_UP_HTML_ACTION	   	 		= "Tools:  CleanUpHTML";
	
	public static final String IMPORT_FROM_TEXT_ACTION			= "File:  ImportFromText";
	
	public static final String IMPORT_FROM_EXCEL_ACTION			= "File:  ImportFromExcel";
	
	public static final String IMPORT_FROM_DBTABLE_ACTION		= "File:  ImportFromDBTable";
	
	public static final String XDIFF_ACTION						= "Tools:  XML Diff and Merge";
	
	public static final String TOOLS_EMPTY_DOCUMENT_ACTION		= "XML:  EmptyDocument";
	
	public static final String TOOLS_CAPITALIZE_ACTION			= "XML:  Capitalize";
	
	public static final String TOOLS_DECAPITALIZE_ACTION		= "XML:  DeCapitalize";
	
	public static final String TOOLS_LOWERCASE_ACTION			= "XML:  Lowercase";
	
	public static final String TOOLS_UPPERCASE_ACTION			= "XML:  Uppercase";
	
	public static final String IMPORT_FROM_SQLXML_ACTION		= "File:  ImportFromSQLXML";
	
	public static final String TOOLS_MOVE_NS_TO_ROOT_ACTION		= "XML:  MoveNamespacesToRoot";
	
	public static final String TOOLS_MOVE_NS_TO_FIRST_USED_ACTION		= "XML:  MoveNamespacesToWhereFirstUsed";
	
	public static final String TOOLS_CHANGE_NS_PREFIX_ACTION		= "XML:  ChangeNamespacePrefix";
	
	public static final String TOOLS_RENAME_NODE_ACTION			= "XML:  RenameNode";
	
	public static final String TOOLS_REMOVE_NODE_ACTION			= "XML:  RemoveNode";
    
	public static final String TOOLS_ADD_NODE_TO_NS_ACTION			= "XML:  AddNodeToNamespace";
	
	public static final String TOOLS_SET_NODE_VALUE_ACTION		= "XML:  SetNodeValue";
	
	public static final String TOOLS_ADD_NODE_ACTION			= "XML:  AddNode";

	public static final String TOOLS_REMOVE_UNUSED_NS_ACTION		= "XML:  RemoveUsusedNamespaces";
	
	public static final String TOOLS_CONVERT_NODE_ACTION		= "XML:  ConvertNode";
	
	public static final String TOOLS_SORT_NODE_ACTION		= "XML:  SortNode";
	
	// for the debugger
	public static final String DEBUGGER_NEW_TRANSFORMATION_ACTION		= "Debugger:  NewTransformation";
	
	public static final String DEBUGGER_CLOSE_ACTION		= "Debugger:  Close";
	
	public static final String DEBUGGER_OPEN_SCENARIO_ACTION		= "Debugger:  OpenScenario";
	
	public static final String DEBUGGER_OPEN_INPUT_ACTION		= "Debugger:  OpenInput";
	
	public static final String DEBUGGER_OPEN_STYLESHEET_ACTION		= "Debugger:  OpenStylesheet";
	
	public static final String DEBUGGER_CLOSE_TRANSFORMATION_ACTION		= "Debugger:  CloseTransformation";
	
	public static final String DEBUGGER_SAVE_AS_SCENARIO_ACTION		= "Debugger:  SaveAsScenario";
	
	public static final String DEBUGGER_FIND_ACTION		= "Debugger:  Find";
	
	public static final String DEBUGGER_FIND_NEXT_ACTION		= "Debugger:  FindNext";
	
	public static final String DEBUGGER_GOTO_ACTION		= "Debugger:  Goto";
	
	public static final String DEBUGGER_START_ACTION		= "Debugger:  Start";
	
	public static final String DEBUGGER_RUN_END_ACTION		= "Debugger:  RunToEnd";
	
	public static final String DEBUGGER_PAUSE_ACTION		= "Debugger:  Pause";
	
	public static final String DEBUGGER_STOP_ACTION		= "Debugger:  Stop";
	
	public static final String DEBUGGER_STEP_INTO_ACTION		= "Debugger:  StepInto";
	
	public static final String DEBUGGER_STEP_OVER_ACTION		= "Debugger:  StepOver";
	
	public static final String DEBUGGER_STEP_OUT_ACTION		= "Debugger:  StepOut";
	
	public static final String DEBUGGER_REMOVE_ALL_BREAKPOINTS_ACTION		= "Debugger:  RemoveAllBreakpoints";
	
	public static final String DEBUGGER_RELOAD_ACTION		= "Debugger:  Reload";
	
	public static final String DEBUGGER_EXIT_ACTION		= "Debugger:  Exit";
	
	public static final String DEBUGGER_COLLAPSE_ALL_ACTION		= "Debugger:  CollapseAll";
	
	public static final String DEBUGGER_EXPAND_ALL_ACTION		= "Debugger:  ExpandAll";
	
	public static final String DEBUGGER_STYLESHEET_SHOW_LINE_NUMBER_ACTION	= "Debugger:  StylesheetShowLineNumberMargin";
	
	public static final String DEBUGGER_STYLESHEET_SHOW_OVERVIEW_ACTION	= "Debugger:  StylesheetShowOverviewMargin";
	
	public static final String DEBUGGER_STYLESHEET_SHOW_FOLDING_ACTION	= "Debugger:  StylesheetShowFoldingMargin";
	
	public static final String DEBUGGER_STYLESHEET_SOFT_WRAPPING_ACTION	= "Debugger:  StylesheetUseSoftWrapping";	
	
	public static final String DEBUGGER_INPUT_SHOW_LINE_NUMBER_ACTION	= "Debugger:  InputShowLineNumberMargin";
	
	public static final String DEBUGGER_INPUT_SHOW_OVERVIEW_ACTION	= "Debugger:  InputShowOverviewMargin";
	
	public static final String DEBUGGER_INPUT_SHOW_FOLDING_ACTION	= "Debugger:  InputShowFoldingMargin";
	
	public static final String DEBUGGER_INPUT_SOFT_WRAPPING_ACTION	    = "Debugger:  InputUseSoftWrapping";	
	
	public static final String DEBUGGER_OUTPUT_SHOW_LINE_NUMBER_ACTION	    = "Debugger:  OutputShowLineNumberMargin";
	
	public static final String DEBUGGER_OUTPUT_SOFT_WRAPPING_ACTION	        = "Debugger:  OutputUseSoftWrapping";	
	
	public static final String DEBUGGER_AUTO_OPEN_INPUT_ACTION	        = "Debugger:  AutomaticallyOpenInput";	
	
	public static final String DEBUGGER_ENABLE_TRACING_ACTION	        = "Debugger:  EnableTracing";	
	
	public static final String DEBUGGER_REDIRECT_OUTPUT_ACTION	        = "Debugger:  RedirectOutput";	
	
	public static final String DEBUGGER_SET_PARAMETERS_ACTION	        = "Debugger:  SetParameters";	
	
	public static final String DEBUGGER_DISABLE_ALL_BREAKPOINTS_ACTION	        = "Debugger:  DisableAllBreakPoints";	
	
	public static final String DEBUGGER_ENABLE_ALL_BREAKPOINTS_ACTION	        = "Debugger:  EnableAllBreakPoints";	
	
	// end of debugger keys
	
	public static final String HIGHLIGHT_ACTION	        = "View:  Highlight";	
	
	public static final String VIEW_STANDARD_BUTTONS_ACTION	        = "View:  StandardButtons";	
	
	public static final String VIEW_EDITOR_BUTTONS_ACTION	        = "View:  EditorButtons";	
	
	public static final String VIEW_FRAGMENT_BUTTONS_ACTION	        = "View:  FragmentButtons";	
	
	public static final String VIEW_EDITOR_SHOW_LINE_NUMBER_ACTION	= "View:  EditorShowLineNumberMargin";
	
	public static final String VIEW_EDITOR_SHOW_OVERVIEW_ACTION	= "View:  EitordShowOverviewMargin";
	
	public static final String VIEW_EDITOR_SHOW_FOLDING_ACTION	= "View:  EditorShowFoldingMargin";
	
	public static final String VIEW_EDITOR_SHOW_ANNOTATION_ACTION		= "View:  EditorShowAnnotationMargin";

	public static final String VIEW_EDITOR_TAG_COMPLETION_ACTION		= "View:  EditorUseTagCompletion";
	
	public static final String VIEW_EDITOR_END_TAG_COMPLETION_ACTION	    = "View:  EditorUseEndTagCompletion";
	
	public static final String VIEW_EDITOR_SMART_INDENTATION_ACTION	        = "View:  EditorUseSmartIndentation";
	
	public static final String VIEW_EDITOR_ERROR_HIGHLIGHTING_ACTION	    = "View:  EditorUseErrorHighlighting";

	public static final String VIEW_EDITOR_SOFT_WRAPPING_ACTION	        = "View:  EditorUseSoftWrapping";	
	
	public static final String VIEWER_SHOW_NAMESPACES_ACTION	    = "View:  ViewerShowNamespace";	
	
	public static final String VIEWER_SHOW_ATTRIBUTES_ACTION	    = "View:  ViewerShowAttributes";	
	
	public static final String VIEWER_SHOW_COMMENTS_ACTION	    = "View:  ViewerShowComments";	
	
	public static final String VIEWER_SHOW_TEXT_CONTENT_ACTION	    = "View:  ViewerShowtextContent";	
	
	public static final String VIEWER_SHOW_PROCESSING_INSTRUCTIONS_ACTION	    = "View:  ViewerShowProcessingInstructions";	
	
	public static final String VIEWER_INLINE_MIXED_CONTENT_ACTION	    		= "View:  ViewerInlineMixedContent";	
	
	public static final String OUTLINER_SHOW_ATTRIBUTE_VALUES_ACTION	    = "View:  OutlinerShowAttributeValues";	
	
	public static final String OUTLINER_SHOW_ELEMENT_VALUES_ACTION	    = "View:  OutlinerShowElementValues";	
	
	public static final String OUTLINER_CREATE_REQUIRED_NODES_ACTION	    = "View:  OutlinerCreateRequiredNodes";	
	
	public static final String VIEW_SYNCHRONIZE_SPLITS_ACTION	        = "View:  SynchroniseSplits";	
	
	public static final String VIEW_SPLIT_HORIZONTALLY_ACTION	        = "View:  SplitHorizontally";	
	
	public static final String VIEW_SPLIT_VERTICALLY_ACTION	        = "View:  SplitVertically";	
	
	public static final String VIEW_UNSPLIT_ACTION	        = "View:  Unsplit";	
	
	/*public static final String GRID_ADD_ATTRIBUTE_TO_SELECTED_ACTION	=	"Grid: AddAttributeToSelected";
	
	public static final String GRID_ADD_TEXT_TO_SELECTED_ACTION			=	"Grid: AddTextToSelected";
	
	public static final String GRID_ADD_CHILD_TABLE_ACTION				=	"Grid: AddChildTable";
	
	public static final String GRID_ADD_ELEMENT_BEFORE_ACTION			=	"Grid: AddElementBefore";
	
	public static final String GRID_ADD_ELEMENT_AFTER_ACTION			=	"Grid: AddElementAfter";
	
	public static final String GRID_ADD_ATTRIBUTE_COLUMN_ACTION			=	"Grid: AddAttributeColumn";
	
	public static final String GRID_ADD_TEXT_COLUMN_ACTION				=	"Grid: AddTextColumn";
	
	public static final String GRID_DELETE_SELECTED_ATTRIBUTE_ACTION	=	"Grid: DeleteSelectedAttribute";
	
	public static final String GRID_DELETE_SELECTED_TEXT_ACTION			=	"Grid: DeleteSelectedText";
	
	public static final String GRID_DELETE_ROW_ACTION					=	"Grid: DeleteRow";
	
	public static final String GRID_DELETE_CHILD_TABLE_ACTION			=	"Grid: DeleteChildTable";
	
	public static final String GRID_DELETE_COLUMN_ACTION				=	"Grid: DeleteColumn";
	
	public static final String GRID_DELETE_ATTS_AND_TEXT_ACTION			=	"Grid: DeleteAttributesAndText";
	
	public static final String GRID_RENAME_ATTRIBUTE_ACTION			=	"Grid: RenameAttribute";
	
	public static final String GRID_RENAME_SELECTED_ATTRIBUTE_ACTION			=	"Grid: RenameSelectedAttribute";
		
	public static final String GRID_MOVE_ROW_UP_ACTION					=	"Grid: MoveRowUp";
	
	public static final String GRID_MOVE_ROW_DOWN_ACTION				=	"Grid: MoveRowDown";
	
	public static final String GRID_SORT_TABLE_DESCENDING_ACTION					=	"Grid: SortDescending";
	
	public static final String GRID_SORT_TABLE_ASCENDING_ACTION					=	"Grid: SortAscending";
	
	public static final String GRID_UNSORT_ACTION					=	"Grid: Unsort";
	
	public static final String GRID_GOTO_PARENT_TABLE_ACTION					=	"Grid: GotoParentTable";
	
	public static final String GRID_GOTO_CHILD_TABLE_ACTION					=	"Grid: GotoChildTable";
	
	public static final String GRID_COLLAPSE_ROW_ACTION					=	"Grid: CollapseRow";
	
	public static final String GRID_EXPAND_ROW_ACTION					=	"Grid: ExpandRow";
	
	public static final String GRID_COPY_SHALLOW_ACTION					=	"Grid: CopyShallow";
	
	public static final String GRID_PASTE_AS_CHILD_ACTION					=	"Grid: PasteAsChild";
	
	public static final String GRID_PASTE_BEFORE_ACTION					=	"Grid: PasteBefore";
	
	public static final String GRID_PASTE_AFTER_ACTION					=	"Grid: PasteAfter";
	
	public static final String GRID_COLLAPSE_CURRENT_TABLE_ACTION		=	"Grid: CollapseCurrentTable";
	
	public static final String GRID_DELETE_ACTION		=	"Grid: DeleteCurrentCell";
	
	
	
	
	
	/**
	 * Creates the Key Preferences.
	 *
	 * @param element the security preferences element.
	 */
	/*public KeyPreferences(DogsBayDocument document, XElement element) {
		super( document, element);
		
		//addDefaultConfigurations();
	}*/
	
	public KeyPreferences(String fileName, String rootName) {
		super(loadPropertiesFile(fileName, rootName));
		// No seeding: the defaults are the product's, in KeyBindingCatalogue,
		// and what a reader changes is an override against them.
	}

	/**
	 * Returns the active configuration name
	 *
	 * @return The DogsBayMenuItem
	 */
	public String getActiveConfiguration() 
	{
		return getText(ACTIVE_CONFIG, DEFAULT);
	}

	/**
	 * Sets the active configuration
	 *
	 * @param name The active configuration name
	 */
	public void setActiveConfiguration(String name) 
	{
		set(ACTIVE_CONFIG, name);
	}
	
	/**
	 * Returns all the configurations
	 *
	 * @return Vector containing the configurations
	 */
	public Vector getConfigurations() 
	{
		return getProperties(CONFIG);
	}	
	
	/**
	 * Returns all the configuration names
	 *
	 * @return Vector containing the configuration names
	 */
	public Vector getConfigurationNames()
	{
		Vector configNames = new Vector();
		
		Vector configs = getConfigurations();
		
		for (int i=0;i<configs.size();i++)
		{
			String name = ((Properties)configs.get(i)).getText(NAME);
			configNames.add(name);
		}
		
		return configNames;
	}
	
	/**
	 * Get the configuration element for a particular name, else create it
	 */
	private XElement getConfiguration(String configName)
	{
		XElement config = null;
		Vector configs = getConfigurations();
		
		for (int i=0;i<configs.size();i++)
		{
			XElement name = ((Properties)configs.get(i)).get(NAME);
			if (name.getText().equals(configName))
			{
				config = ((Properties)configs.get(i)).getElement();
				break;
			}
		}
		
		if (config == null)
		{
			// doesn't already exist, so we need to create it
			config = new XElement(CONFIG);
			
			XElement nameEle = new XElement(NAME);
			nameEle.setText(configName);
			
			config.add(nameEle);
			getElement().add(config);
		}
		
		return config;
	}
	
	
	
	
	
	
	
	/**
	 * sets the keymap
	 */
	void setKeyMap(String configName, String action,Keystroke keystroke)
	{
		setKeyMap(configName,action,null,keystroke);
	}
	
	/**
	 * sets the keymap with a description
	 */
	void setKeyMap(String configName, String action, String description,Keystroke keystroke)
	{
		
		// add the key
		XElement config = getConfiguration(configName);
		
		
		KeyMap keymap = new KeyMap(action,description,keystroke);
		config.add(keymap.getElement());
	}
	
	/**
	 * sets the keymap with a description
	 */
	void setKeyMap(String configName, String action, String description,Keystroke keystroke,Keystroke keystroke2)
	{
		// add the key
		XElement config = getConfiguration(configName);
		
		KeyMap keymap = new KeyMap(action,description,keystroke,keystroke2);
		config.add(keymap.getElement());
	}
	
	 /**
	 * sets the keymap using an existing KeyMap object
	 *
	 * @param configName The configuration name
	 * @param keymap The new KeyMap object  
	 */
	public void setKeyMap(String configName, KeyMap keymap)
	{
		XElement config = getConfiguration(configName);
		config.add(keymap.getElement());
	}
	
	 /**
	 * Removes the mappings for a particular configuration
	 *
	 * @param configName The configuration name
	 */
	public void removeMapping(String configName)
	{
		XElement configuration = getConfiguration(configName);
		XElement keymapping = getElement();
		
		keymapping.remove(configuration); 
	}
	
	/**
	 * Returns all the KeyMap Elements for a particular configuration
	 *
	 * @param configName The configuration name
	 *
	 * @return Hashtable containing the required KeyMaps Elements using action name as keys
	 */
	public Hashtable getKeyMapElements(String configName)
	{
		Hashtable keyMap = new Hashtable();
		
		XElement config = getConfiguration(configName);
		
		Iterator keymaps = config.elementIterator(KEYMAP); 
		while (keymaps.hasNext())
		{
			XElement keymap = (XElement)keymaps.next();
			XElement action = (XElement)keymap.element(ACTION);
			String actionName = action.getText();
			
			keyMap.put(actionName,keymap);
		}
		
		return keyMap;
	}
	
	/**
	 * Returns all the KeyMap objects for a particular configuration
	 *
	 * @param configName The configuration name
	 *
	 * @return Hashtable containing the required KeyMap Objects using action name as keys
	 */
	public Hashtable getKeyMaps(String configName)
	{
		Hashtable keyMaps = new Hashtable();
		
		Hashtable keyMapElements = getKeyMapElements(configName);
		
		Enumeration elements = keyMapElements.elements();
		while (elements.hasMoreElements())
		{
			XElement keyMapElement = (XElement)elements.nextElement();
			XElement action = (XElement)keyMapElement.element(ACTION);
			String actionName = action.getText();
			
			KeyMap keymap = new KeyMap(keyMapElement);
			keyMaps.put(actionName,keymap);
		}
		
		return keyMaps;
	}
	
	/**
	 * Returns all the Keystrokes for a particular KeyMap
	 *
  	 * @param keymap The particular KeyMap
	 *
	 * @return Vector containing the Keystrokes
	 */
	public Vector getKeystrokes(XElement keymap)
	{
		Vector keyStroke = new Vector();
		
		Iterator keystrokes = keymap.elementIterator(KEYSTROKE); 
		while (keystrokes.hasNext())
		{
			XElement keystroke = (XElement)keystrokes.next();
			String mask = null;
			XElement maskEle = (XElement)keystroke.element(MASK);
			if (maskEle != null)
			{
				mask = maskEle.getText();
			}
			
			XElement valueEle = (XElement)keystroke.element(VALUE);
			String value = null;
			if (valueEle != null)
			{
				value = valueEle.getText();
			}
			
			if (value != null)
			{ 
				Keystroke keystrokeObj = new Keystroke(mask,value);
			
				keyStroke.add(keystrokeObj);
			}
		}
		
		return keyStroke;
	}
	
	/**
	 * Returns a key sequence for particular Keystrokes
	 *
	 * @param keystrokes A vector of keystroke objects
	 *
	 * @return The key sequence 
	 */
	public String getKeySequence(Vector keystrokes)
	{
		StringBuffer sequence = new StringBuffer();
	
		if (keystrokes.size() == 1)
		{
			Keystroke keystroke = (Keystroke)keystrokes.get(0);
			sequence.append(getKeySequence(keystroke));
		}
		else if (keystrokes.size() == 2)
		{
			Keystroke keystroke1 = (Keystroke)keystrokes.get(0);
			Keystroke keystroke2 = (Keystroke)keystrokes.get(1);
			sequence.append(getKeySequence(keystroke1));
			sequence.append(',');
			sequence.append(getKeySequence(keystroke2));
		}
		else
		{
			// no sequence assigned yet
			sequence.append("");
		}
		
		return sequence.toString();
	}
	
	/**
	 * Returns a key sequence for particular Keystroke
	 *
	 * @param keystroke The Keystroke object
	 *
	 * @return The key sequence 
	 */
	public String getKeySequence(Keystroke keystroke)
	{
		StringBuffer sequence = new StringBuffer();
		
		String maskValue = keystroke.getMask();
		if (maskValue != null && !maskValue.equals(""))
		{
			sequence.append(maskValue);
			sequence.append('+');
		}
		
		String value = keystroke.getValue();
		if (value != null)
		{
			sequence.append(value);
		}
		else
		{
			// no assignment yet
			value = "";
			sequence.append(value);
		}
		
		return sequence.toString();
	}
	
	/**
	 * Returns the sorted action names
	 *
	 * @param keyMaps All the keymaps
	 *
	 * @return All the sorted action names
	 */
	public Vector getSortedActionNames(Hashtable keyMaps) 
	{			
		Enumeration names = keyMaps.keys();
		Vector unsortedNames = new Vector();
		while (names.hasMoreElements())
		{
			String name = (String)names.nextElement();
			unsortedNames.add(name);
		}
		
		Vector sortedNames = sort(unsortedNames); 
		return sortedNames;
	}
	
	// sorts the action names
	private Vector sort( Vector list) 
	{
		Vector elements = new Vector(list.size());
		
		for ( int i = 0; i < list.size(); i++) 
		{
			String actionName = (String)list.elementAt(i);

			// Find out where to insert the element...
			int index = -1;

			for ( int j = 0; j < elements.size() && index == -1; j++) {
				// Compare alphabeticaly
				if ( actionName.compareToIgnoreCase ((String)elements.elementAt(j)) <= 0) {
					index = j;
				}
			}
			
			if ( index != -1) {
				elements.insertElementAt( actionName, index);
			} else {
				elements.addElement( actionName);
			}
		}
			
		return elements;
	}
	
	/**
	 * Creates a java.swing.KeyStroke from a given Keystroke object
	 *
     * @param keystroke The particular keystroke
     *
	 * @return the KeyStroke
	 */
	public KeyStroke getKeyStroke(Keystroke keystroke)
	{
		int maskInt = 0;
		int valueInt = 0;
		
		String mask = keystroke.getContentIfExists(MASK);
		String value = keystroke.getContentIfExists(VALUE);
		
		if (mask == null)
		{
			maskInt = 0;
		}
		else if (mask.equalsIgnoreCase(CTRL))
		{
			maskInt = InputEvent.CTRL_DOWN_MASK;
		}
		else if (mask.equalsIgnoreCase(ALT))
		{
			maskInt = InputEvent.ALT_DOWN_MASK;
		}
		else if (mask.equalsIgnoreCase(CTRLSHIFT))
		{
			maskInt = InputEvent.CTRL_DOWN_MASK+InputEvent.SHIFT_DOWN_MASK;
		}
		else if (mask.equalsIgnoreCase(CTRLALT))
		{
			maskInt = InputEvent.CTRL_DOWN_MASK+InputEvent.ALT_DOWN_MASK;
		}
		else if (mask.equalsIgnoreCase(CTRLALTSHIFT))
		{
			maskInt = InputEvent.CTRL_DOWN_MASK+InputEvent.ALT_DOWN_MASK+InputEvent.SHIFT_DOWN_MASK;
		}
		else if (mask.equalsIgnoreCase(ALTSHIFT))
		{
			maskInt = InputEvent.ALT_DOWN_MASK+InputEvent.SHIFT_DOWN_MASK;
		}
		else if (mask.equalsIgnoreCase(SHIFT))
		{
			maskInt = InputEvent.SHIFT_DOWN_MASK;
		}
		//mac os
		else if (mask.equalsIgnoreCase(CMD))
		{
			maskInt = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		}
		else if (mask.equalsIgnoreCase(CMDSHIFT))
		{
			maskInt = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()+InputEvent.SHIFT_DOWN_MASK;
		}
		else if (mask.equalsIgnoreCase(CMDALT))
		{
			maskInt = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()+InputEvent.ALT_DOWN_MASK;
		}
		else if (mask.equalsIgnoreCase(CMDCTRL))
		{
			maskInt = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()+InputEvent.CTRL_DOWN_MASK;
		}
		else if (mask.equalsIgnoreCase(CMDALTSHIFT))
		{
			maskInt = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()+InputEvent.ALT_DOWN_MASK+InputEvent.SHIFT_DOWN_MASK;
		}
		else if (mask.equalsIgnoreCase(CMDCTRLSHIFT))
		{
			maskInt = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()+InputEvent.CTRL_DOWN_MASK+InputEvent.SHIFT_DOWN_MASK;
		}
		else if (mask.equalsIgnoreCase(CMDCTRLALTSHIFT))
		{
			maskInt = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()+InputEvent.CTRL_DOWN_MASK+InputEvent.ALT_DOWN_MASK+InputEvent.SHIFT_DOWN_MASK;
		}
		else
		{
			maskInt = 0;
		}
		
		if (value.equals(";"))
		{
			// to cover semicolon, getkeycode doesn't appear to work for semicolon
			valueInt = KeyEvent.VK_SEMICOLON;
		}
		else if (value.equalsIgnoreCase("UP"))
		{
			valueInt = KeyEvent.VK_UP;
		}
		else if (value.equalsIgnoreCase("DOWN"))
		{
			valueInt = KeyEvent.VK_DOWN;
		}
		else if (value.equalsIgnoreCase("RIGHT"))
		{
			valueInt = KeyEvent.VK_RIGHT;
		}
		else if (value.equalsIgnoreCase("LEFT"))
		{
			valueInt = KeyEvent.VK_LEFT;
		}
		else if (value.equalsIgnoreCase("PAGEUP"))
		{
			valueInt = KeyEvent.VK_PAGE_UP;
		}
		else if (value.equalsIgnoreCase("PAGEDOWN"))
		{
			valueInt = KeyEvent.VK_PAGE_DOWN;
		}
		else if (value.equalsIgnoreCase("BACKSPACE"))
		{
			valueInt = KeyEvent.VK_BACK_SPACE;
		}
		else if (value.equalsIgnoreCase("DELETE"))
		{
			valueInt = KeyEvent.VK_DELETE;
		}
		else
		{
			try{
			valueInt = KeyStroke.getKeyStroke(value.toUpperCase()).getKeyCode();
			}
			catch (Exception e)
			{
				System.out.println("Could not create a KeyStroke for the key - "+value);
			}
		}
		
		KeyStroke stroke = null;
		try{
			stroke = KeyStroke.getKeyStroke(valueInt,maskInt,false);
		}
		catch (Exception e)
		{
			// unknown keystroke
			System.out.println("The following error occurred getting the keystroke: "+e.getMessage());
		}
		
		return stroke;
	}
	
	/**
	 *  sets all the keymappings for the specified configuation
	 *
 	 * @param parent The DogsBayAIEditor
 	 * @param configName The configuration name 
	 */
	public void setKeyMappings(DogsBayAIEditor parent, String configName)
	{
		if (configName.equalsIgnoreCase(KeyPreferences.EMACS))
		{
			// set extra emac editing mode on
			parent.setEmacsModeOn(true);
		}
		else
		{
			// turn off editing mode
			parent.setEmacsModeOn(false);
		}
		
		Hashtable keyMap = getKeyMapElements(configName);
		
		Enumeration actionNames = keyMap.keys();
		
		while (actionNames.hasMoreElements())
		{
			String actionName = (String)actionNames.nextElement();
			
			XElement keymap = (XElement)keyMap.get(actionName);
			
			Vector keystrokes = getKeystrokes(keymap);
			Keystroke keystroke = null;
			Keystroke keystroke2 = null;
			
			int keystrokeSize = keystrokes.size();
			
			if (keystrokeSize == 1)
			{
				keystroke = (Keystroke)keystrokes.get(0);
			}
			else if (keystrokeSize == 2)
			{
				keystroke = (Keystroke)keystrokes.get(0);
				keystroke2 = (Keystroke)keystrokes.get(1);
			}
			else
			{
				// there are no keys assigned for this acttion
			}
			  
			// to check debugger keys
			// PHASE 3: Debugger disabled during Saxon upgrade
			// XSLTDebuggerFrame debugger = null; // parent.getDebugger();
			Object debugger = null;
			
			// only need to change the high level ones here (i.e menu items etc, not editing functionality,
			// that is done in updatePreferences in each Editor)
			
			
			if (actionName.equals(KeyPreferences.OPEN_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.OPEN_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);	
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.OPEN_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.OPEN_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.OPEN_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.OPEN_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.CLOSE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CLOSE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CLOSE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.CLOSE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.CLOSE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CLOSE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
				
			}
			else if (actionName.equals(KeyPreferences.CLOSE_ALL_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CLOSE_ALL_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CLOSE_ALL_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.CLOSE_ALL_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.CLOSE_ALL_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CLOSE_ALL_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.SAVE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SAVE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SAVE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.SAVE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.SAVE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SAVE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.SAVE_ALL_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SAVE_ALL_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SAVE_ALL_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.SAVE_ALL_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.SAVE_ALL_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SAVE_ALL_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.UNDO_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.UNDO_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.UNDO_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.UNDO_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.UNDO_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.UNDO_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.SAVE_AS_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SAVE_AS_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if ((keystrokeSize == 2))
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SAVE_AS_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.SAVE_AS_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.SAVE_AS_ACTION,stroke,action);
				}
				else if (keystrokeSize == 2)
				{
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SAVE_AS_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SAVE_AS_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.SELECT_DOCUMENT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SELECT_DOCUMENT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SELECT_DOCUMENT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.SELECT_DOCUMENT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.SELECT_DOCUMENT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SELECT_DOCUMENT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.PRINT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.PRINT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.PRINT_ACTION);
					if(item != null) item.setAccelerator(null);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.PRINT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.PRINT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.PRINT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.FIND_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.FIND_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.FIND_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.FIND_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.FIND_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.FIND_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.FIND_NEXT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.FIND_NEXT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.FIND_NEXT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.FIND_NEXT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.FIND_NEXT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.FIND_NEXT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.REPLACE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.REPLACE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.REPLACE_ACTION);
					if(item != null) item.setAccelerator(null);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.REPLACE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.REPLACE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.REPLACE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.CUT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CUT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CUT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.CUT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.CUT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CUT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.COPY_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.COPY_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.COPY_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.COPY_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.COPY_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.COPY_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.PASTE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.PASTE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
					
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.PASTE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.PASTE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.PASTE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.PASTE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.COMMENT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.COMMENT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.COMMENT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.COMMENT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.COMMENT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.COMMENT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GOTO_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GOTO_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GOTO_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GOTO_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GOTO_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GOTO_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.WELL_FORMEDNESS_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.WELL_FORMEDNESS_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.WELL_FORMEDNESS_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.WELL_FORMEDNESS_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.WELL_FORMEDNESS_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.WELL_FORMEDNESS_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.VALIDATE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VALIDATE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VALIDATE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.VALIDATE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.VALIDATE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VALIDATE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.START_BROWSER_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.START_BROWSER_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.START_BROWSER_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.START_BROWSER_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.START_BROWSER_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.START_BROWSER_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.NEW_DOCUMENT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.NEW_DOCUMENT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.NEW_DOCUMENT_ACTION);
					if(item != null) item.setAccelerator(null);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.NEW_DOCUMENT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.NEW_DOCUMENT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.NEW_DOCUMENT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.REDO_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.REDO_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.REDO_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.REDO_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.REDO_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.REDO_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.SELECT_ELEMENT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SELECT_ELEMENT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SELECT_ELEMENT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.SELECT_ELEMENT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.SELECT_ELEMENT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SELECT_ELEMENT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.SELECT_ELEMENT_CONTENT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SELECT_ELEMENT_CONTENT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SELECT_ELEMENT_CONTENT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.SELECT_ELEMENT_CONTENT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.SELECT_ELEMENT_CONTENT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SELECT_ELEMENT_CONTENT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.INSERT_SPECIAL_CHAR_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.INSERT_SPECIAL_CHAR_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.INSERT_SPECIAL_CHAR_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.INSERT_SPECIAL_CHAR_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.INSERT_SPECIAL_CHAR_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.INSERT_SPECIAL_CHAR_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.TAG_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TAG_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TAG_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TAG_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TAG_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TAG_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.REPEAT_TAG_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.REPEAT_TAG_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.REPEAT_TAG_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.REPEAT_TAG_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.REPEAT_TAG_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.REPEAT_TAG_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GOTO_START_TAG_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GOTO_START_TAG_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GOTO_START_TAG_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GOTO_START_TAG_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GOTO_START_TAG_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GOTO_START_TAG_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GOTO_END_TAG_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GOTO_END_TAG_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GOTO_END_TAG_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GOTO_END_TAG_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GOTO_END_TAG_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GOTO_END_TAG_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.TOGGLE_EMPTY_ELEMENT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOGGLE_EMPTY_ELEMENT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOGGLE_EMPTY_ELEMENT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TOGGLE_EMPTY_ELEMENT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TOGGLE_EMPTY_ELEMENT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOGGLE_EMPTY_ELEMENT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.RENAME_ELEMENT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.RENAME_ELEMENT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.RENAME_ELEMENT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.RENAME_ELEMENT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.RENAME_ELEMENT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.RENAME_ELEMENT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GOTO_PREVIOUS_ATTRIBUTE_VALUE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GOTO_PREVIOUS_ATTRIBUTE_VALUE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GOTO_PREVIOUS_ATTRIBUTE_VALUE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GOTO_PREVIOUS_ATTRIBUTE_VALUE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GOTO_PREVIOUS_ATTRIBUTE_VALUE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GOTO_PREVIOUS_ATTRIBUTE_VALUE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GOTO_NEXT_ATTRIBUTE_VALUE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GOTO_NEXT_ATTRIBUTE_VALUE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GOTO_NEXT_ATTRIBUTE_VALUE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GOTO_NEXT_ATTRIBUTE_VALUE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GOTO_NEXT_ATTRIBUTE_VALUE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GOTO_NEXT_ATTRIBUTE_VALUE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.TOGGLE_BOOKMARK_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOGGLE_BOOKMARK_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOGGLE_BOOKMARK_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TOGGLE_BOOKMARK_ACTION);	
					parent.getStatusbar().addToModeMap(KeyPreferences.TOGGLE_BOOKMARK_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOGGLE_BOOKMARK_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.SELECT_BOOKMARK_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SELECT_BOOKMARK_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SELECT_BOOKMARK_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.SELECT_BOOKMARK_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.SELECT_BOOKMARK_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SELECT_BOOKMARK_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.SELECT_FRAGMENT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SELECT_FRAGMENT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SELECT_FRAGMENT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.SELECT_FRAGMENT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.SELECT_FRAGMENT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SELECT_FRAGMENT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.SCHEMA_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					//TODO JRadioButtonMenuItem item = parent.getSchemaViewItem();
					//TODO if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					//TODO JRadioButtonMenuItem item = parent.getSchemaViewItem();
					//TODO if(item != null) item.setAccelerator(null);
				}
			}
			else if (actionName.equals(KeyPreferences.OUTLINER_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					//TODO JRadioButtonMenuItem item = parent.getDesignerViewItem();
					//TODO if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					//TODO JRadioButtonMenuItem item = parent.getDesignerViewItem();
					//TODO if(item != null) item.setAccelerator(null);
				}
			}
			else if (actionName.equals(KeyPreferences.EDITOR_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					//TODO JRadioButtonMenuItem item = parent.getEditorViewItem();
					//TODO if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					//TODO JRadioButtonMenuItem item = parent.getEditorViewItem();
					//TODO if(item != null) item.setAccelerator(null);
				}
			}
			else if (actionName.equals(KeyPreferences.VIEWER_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					//TODO JRadioButtonMenuItem item = parent.getViewerViewItem();
					//TODO if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					//TODO JRadioButtonMenuItem item = parent.getViewerViewItem();
					//TODO if(item != null) item.setAccelerator(null);
				}
			}
			
			/*else if (actionName.equals(KeyPreferences.GRID_ACTION))
			{
			    // update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JRadioButtonMenuItem item = parent.getGridViewItem();
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JRadioButtonMenuItem item = parent.getGridViewItem();
					if(item != null) item.setAccelerator(null);
				}
			}*/
			
			else if (actionName.equals(KeyPreferences.ADD_ELEMENT_OUTLINER_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_ELEMENT_OUTLINER_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_ELEMENT_OUTLINER_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.ADD_ELEMENT_OUTLINER_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.ADD_ELEMENT_OUTLINER_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_ELEMENT_OUTLINER_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.DELETE_ELEMENT_OUTLINER_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.DELETE_ELEMENT_OUTLINER_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.DELETE_ELEMENT_OUTLINER_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.DELETE_ELEMENT_OUTLINER_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.DELETE_ELEMENT_OUTLINER_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.DELETE_ELEMENT_OUTLINER_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.OPEN_REMOTE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.OPEN_REMOTE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.OPEN_REMOTE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.OPEN_REMOTE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.OPEN_REMOTE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.OPEN_REMOTE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.RELOAD_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.RELOAD_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.RELOAD_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.RELOAD_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.RELOAD_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.RELOAD_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.SAVE_AS_TEMPLATE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SAVE_AS_TEMPLATE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SAVE_AS_TEMPLATE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.SAVE_AS_TEMPLATE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.SAVE_AS_TEMPLATE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SAVE_AS_TEMPLATE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.MANAGE_TEMPLATE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.MANAGE_TEMPLATE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.MANAGE_TEMPLATE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.MANAGE_TEMPLATE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.MANAGE_TEMPLATE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.MANAGE_TEMPLATE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.PAGE_SETUP_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.PAGE_SETUP_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.PAGE_SETUP_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.PAGE_SETUP_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.PAGE_SETUP_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.PAGE_SETUP_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.PREFERENCES_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.PREFERENCES_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.PREFERENCES_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.PREFERENCES_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.PREFERENCES_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.PREFERENCES_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.CREATE_REQUIRED_NODE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CREATE_REQUIRED_NODE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CREATE_REQUIRED_NODE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.CREATE_REQUIRED_NODE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.CREATE_REQUIRED_NODE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CREATE_REQUIRED_NODE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.SPLIT_ELEMENT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SPLIT_ELEMENT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SPLIT_ELEMENT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.SPLIT_ELEMENT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.SPLIT_ELEMENT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SPLIT_ELEMENT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.CONVERT_ENTITIES_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CONVERT_ENTITIES_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CONVERT_ENTITIES_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.CONVERT_ENTITIES_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.CONVERT_ENTITIES_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CONVERT_ENTITIES_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.CONVERT_CHARACTERS_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CONVERT_CHARACTERS_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CONVERT_CHARACTERS_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.CONVERT_CHARACTERS_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.CONVERT_CHARACTERS_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CONVERT_CHARACTERS_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.TAB_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TAB_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TAB_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TAB_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TAB_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TAB_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.UNINDENT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.UNINDENT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.UNINDENT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.UNINDENT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.UNINDENT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.UNINDENT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.STRIP_TAG_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.STRIP_TAG_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.STRIP_TAG_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.STRIP_TAG_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.STRIP_TAG_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.STRIP_TAG_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.ADD_CDATA_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_CDATA_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_CDATA_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.ADD_CDATA_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.ADD_CDATA_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_CDATA_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.LOCK_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.LOCK_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.LOCK_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.LOCK_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.LOCK_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.LOCK_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.FORMAT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.FORMAT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.FORMAT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.FORMAT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.FORMAT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.FORMAT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.EXPAND_ALL_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXPAND_ALL_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXPAND_ALL_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.EXPAND_ALL_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.EXPAND_ALL_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXPAND_ALL_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.COLLAPSE_ALL_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.COLLAPSE_ALL_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.COLLAPSE_ALL_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.COLLAPSE_ALL_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.COLLAPSE_ALL_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.COLLAPSE_ALL_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.SYNCHRONISE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SYNCHRONISE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SYNCHRONISE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.SYNCHRONISE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.SYNCHRONISE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SYNCHRONISE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.TOGGLE_FULL_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOGGLE_FULL_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOGGLE_FULL_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TOGGLE_FULL_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TOGGLE_FULL_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOGGLE_FULL_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.NEW_PROJECT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.NEW_PROJECT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.NEW_PROJECT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.NEW_PROJECT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.NEW_PROJECT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.NEW_PROJECT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.IMPORT_PROJECT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.IMPORT_PROJECT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.IMPORT_PROJECT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.IMPORT_PROJECT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.IMPORT_PROJECT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.IMPORT_PROJECT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.DELETE_PROJECT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.DELETE_PROJECT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.DELETE_PROJECT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.DELETE_PROJECT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.DELETE_PROJECT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.DELETE_PROJECT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.RENAME_PROJECT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.RENAME_PROJECT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.RENAME_PROJECT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.RENAME_PROJECT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.RENAME_PROJECT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.RENAME_PROJECT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.CHECK_WELLFORMEDNESS_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CHECK_WELLFORMEDNESS_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CHECK_WELLFORMEDNESS_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.CHECK_WELLFORMEDNESS_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.CHECK_WELLFORMEDNESS_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CHECK_WELLFORMEDNESS_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.VALIDATE_PROJECT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VALIDATE_PROJECT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VALIDATE_PROJECT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.VALIDATE_PROJECT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.VALIDATE_PROJECT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VALIDATE_PROJECT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.FIND_IN_FILES_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.FIND_IN_FILES_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.FIND_IN_FILES_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.FIND_IN_FILES_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.FIND_IN_FILES_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.FIND_IN_FILES_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.FIND_IN_PROJECTS_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.FIND_IN_PROJECTS_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.FIND_IN_PROJECTS_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.FIND_IN_PROJECTS_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.FIND_IN_PROJECTS_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.FIND_IN_PROJECTS_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.ADD_FILE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_FILE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_FILE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.ADD_FILE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.ADD_FILE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_FILE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.ADD_REMOTE_FILE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_REMOTE_FILE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_REMOTE_FILE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.ADD_REMOTE_FILE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.ADD_REMOTE_FILE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_REMOTE_FILE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.REMOVE_FILE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.REMOVE_FILE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.REMOVE_FILE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.REMOVE_FILE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.REMOVE_FILE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.REMOVE_FILE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.ADD_DIRECTORY_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_DIRECTORY_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_DIRECTORY_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.ADD_DIRECTORY_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.ADD_DIRECTORY_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_DIRECTORY_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.ADD_DIRECTORY_CONTENTS_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_DIRECTORY_CONTENTS_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_DIRECTORY_CONTENTS_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.ADD_DIRECTORY_CONTENTS_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.ADD_DIRECTORY_CONTENTS_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_DIRECTORY_CONTENTS_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.ADD_FOLDER_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_FOLDER_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_FOLDER_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.ADD_FOLDER_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.ADD_FOLDER_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ADD_FOLDER_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.REMOVE_FOLDER_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.REMOVE_FOLDER_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.REMOVE_FOLDER_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.REMOVE_FOLDER_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.REMOVE_FOLDER_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.REMOVE_FOLDER_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.RENAME_FOLDER_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.RENAME_FOLDER_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.RENAME_FOLDER_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.RENAME_FOLDER_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.RENAME_FOLDER_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.RENAME_FOLDER_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.VALIDATE_XML_SCHEMA_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VALIDATE_XML_SCHEMA_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VALIDATE_XML_SCHEMA_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.VALIDATE_XML_SCHEMA_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.VALIDATE_XML_SCHEMA_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VALIDATE_XML_SCHEMA_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.VALIDATE_DTD_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VALIDATE_DTD_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VALIDATE_DTD_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.VALIDATE_DTD_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.VALIDATE_DTD_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VALIDATE_DTD_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.VALIDATE_RELAXNG_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VALIDATE_RELAXNG_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VALIDATE_RELAXNG_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.VALIDATE_RELAXNG_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.VALIDATE_RELAXNG_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VALIDATE_RELAXNG_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.SET_XML_DECLARATION_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SET_XML_DECLARATION_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SET_XML_DECLARATION_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.SET_XML_DECLARATION_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.SET_XML_DECLARATION_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SET_XML_DECLARATION_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.SET_DOCTYPE_DECLARATION_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SET_DOCTYPE_DECLARATION_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SET_DOCTYPE_DECLARATION_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.SET_DOCTYPE_DECLARATION_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.SET_DOCTYPE_DECLARATION_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SET_DOCTYPE_DECLARATION_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.SET_SCHEMA_LOCATION_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SET_SCHEMA_LOCATION_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SET_SCHEMA_LOCATION_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.SET_SCHEMA_LOCATION_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.SET_SCHEMA_LOCATION_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SET_SCHEMA_LOCATION_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.RESOLVE_XINCLUDES_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.RESOLVE_XINCLUDES_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.RESOLVE_XINCLUDES_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.RESOLVE_XINCLUDES_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.RESOLVE_XINCLUDES_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.RESOLVE_XINCLUDES_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.SET_SCHEMA_PROPS_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SET_SCHEMA_PROPS_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SET_SCHEMA_PROPS_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.SET_SCHEMA_PROPS_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.SET_SCHEMA_PROPS_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SET_SCHEMA_PROPS_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.INFER_SCHEMA_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.INFER_SCHEMA_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.INFER_SCHEMA_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.INFER_SCHEMA_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.INFER_SCHEMA_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.INFER_SCHEMA_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.CREATE_TYPE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CREATE_TYPE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CREATE_TYPE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.CREATE_TYPE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.CREATE_TYPE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CREATE_TYPE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.SET_TYPE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SET_TYPE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SET_TYPE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.SET_TYPE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.SET_TYPE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SET_TYPE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.TYPE_PROPERTIES_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TYPE_PROPERTIES_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TYPE_PROPERTIES_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TYPE_PROPERTIES_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TYPE_PROPERTIES_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TYPE_PROPERTIES_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.MANAGE_TYPES_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.MANAGE_TYPES_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.MANAGE_TYPES_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.MANAGE_TYPES_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.MANAGE_TYPES_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.MANAGE_TYPES_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.CONVERT_SCHEMA_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CONVERT_SCHEMA_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CONVERT_SCHEMA_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.CONVERT_SCHEMA_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.CONVERT_SCHEMA_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CONVERT_SCHEMA_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.EXECUTE_SIMPLE_XSLT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_SIMPLE_XSLT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_SIMPLE_XSLT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.EXECUTE_SIMPLE_XSLT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.EXECUTE_SIMPLE_XSLT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_SIMPLE_XSLT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.EXECUTE_ADVANCED_XSLT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_ADVANCED_XSLT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_ADVANCED_XSLT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.EXECUTE_ADVANCED_XSLT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.EXECUTE_ADVANCED_XSLT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_ADVANCED_XSLT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.EXECUTE_PREVIOUS_XSLT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_PREVIOUS_XSLT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_PREVIOUS_XSLT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.EXECUTE_PREVIOUS_XSLT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.EXECUTE_PREVIOUS_XSLT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_PREVIOUS_XSLT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.EXECUTE_FO_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_FO_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_FO_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.EXECUTE_FO_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.EXECUTE_FO_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_FO_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.EXECUTE_PREVIOUS_FO_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_PREVIOUS_FO_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_PREVIOUS_FO_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.EXECUTE_PREVIOUS_FO_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.EXECUTE_PREVIOUS_FO_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_PREVIOUS_FO_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.EXECUTE_XQUERY_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_XQUERY_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_XQUERY_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.EXECUTE_XQUERY_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.EXECUTE_XQUERY_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_XQUERY_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.EXECUTE_PREVIOUS_XQUERY_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_PREVIOUS_XQUERY_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_PREVIOUS_XQUERY_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.EXECUTE_PREVIOUS_XQUERY_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.EXECUTE_PREVIOUS_XQUERY_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_PREVIOUS_XQUERY_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.EXECUTE_SCENARIO_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_SCENARIO_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_SCENARIO_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.EXECUTE_SCENARIO_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.EXECUTE_SCENARIO_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_SCENARIO_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.EXECUTE_PREVIOUS_SCENARIO_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_PREVIOUS_SCENARIO_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_PREVIOUS_SCENARIO_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.EXECUTE_PREVIOUS_SCENARIO_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.EXECUTE_PREVIOUS_SCENARIO_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.EXECUTE_PREVIOUS_SCENARIO_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.XSLT_DEBUGGER_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.XSLT_DEBUGGER_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.XSLT_DEBUGGER_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.XSLT_DEBUGGER_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.XSLT_DEBUGGER_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.XSLT_DEBUGGER_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.MANAGE_SCENARIOS_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.MANAGE_SCENARIOS_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.MANAGE_SCENARIOS_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.MANAGE_SCENARIOS_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.MANAGE_SCENARIOS_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.MANAGE_SCENARIOS_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.SEND_SOAP_MESSAGE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SEND_SOAP_MESSAGE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SEND_SOAP_MESSAGE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.SEND_SOAP_MESSAGE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.SEND_SOAP_MESSAGE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.SEND_SOAP_MESSAGE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.ANALYSE_WSDL_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ANALYSE_WSDL_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ANALYSE_WSDL_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.ANALYSE_WSDL_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.ANALYSE_WSDL_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.ANALYSE_WSDL_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.CLEAN_UP_HTML_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CLEAN_UP_HTML_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CLEAN_UP_HTML_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.CLEAN_UP_HTML_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.CLEAN_UP_HTML_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.CLEAN_UP_HTML_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.IMPORT_FROM_TEXT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.IMPORT_FROM_TEXT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.IMPORT_FROM_TEXT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.IMPORT_FROM_TEXT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.IMPORT_FROM_TEXT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.IMPORT_FROM_TEXT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.IMPORT_FROM_EXCEL_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.IMPORT_FROM_EXCEL_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.IMPORT_FROM_EXCEL_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.IMPORT_FROM_EXCEL_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.IMPORT_FROM_EXCEL_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.IMPORT_FROM_EXCEL_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.IMPORT_FROM_DBTABLE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.IMPORT_FROM_DBTABLE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.IMPORT_FROM_DBTABLE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.IMPORT_FROM_DBTABLE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.IMPORT_FROM_DBTABLE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.IMPORT_FROM_DBTABLE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.TOOLS_EMPTY_DOCUMENT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_EMPTY_DOCUMENT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_EMPTY_DOCUMENT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TOOLS_EMPTY_DOCUMENT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TOOLS_EMPTY_DOCUMENT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_EMPTY_DOCUMENT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.TOOLS_CAPITALIZE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_CAPITALIZE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_CAPITALIZE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TOOLS_CAPITALIZE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TOOLS_CAPITALIZE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_CAPITALIZE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.TOOLS_DECAPITALIZE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_DECAPITALIZE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_DECAPITALIZE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TOOLS_DECAPITALIZE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TOOLS_DECAPITALIZE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_DECAPITALIZE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.TOOLS_LOWERCASE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_LOWERCASE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_LOWERCASE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TOOLS_LOWERCASE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TOOLS_LOWERCASE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_LOWERCASE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.TOOLS_UPPERCASE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_UPPERCASE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_UPPERCASE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TOOLS_UPPERCASE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TOOLS_UPPERCASE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_UPPERCASE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.IMPORT_FROM_SQLXML_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.IMPORT_FROM_SQLXML_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.IMPORT_FROM_SQLXML_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.IMPORT_FROM_SQLXML_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.IMPORT_FROM_SQLXML_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.IMPORT_FROM_SQLXML_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.TOOLS_MOVE_NS_TO_ROOT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_MOVE_NS_TO_ROOT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_MOVE_NS_TO_ROOT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TOOLS_MOVE_NS_TO_ROOT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TOOLS_MOVE_NS_TO_ROOT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_MOVE_NS_TO_ROOT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.TOOLS_MOVE_NS_TO_FIRST_USED_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_MOVE_NS_TO_FIRST_USED_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_MOVE_NS_TO_FIRST_USED_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TOOLS_MOVE_NS_TO_FIRST_USED_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TOOLS_MOVE_NS_TO_FIRST_USED_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_MOVE_NS_TO_FIRST_USED_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.TOOLS_CHANGE_NS_PREFIX_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_CHANGE_NS_PREFIX_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_CHANGE_NS_PREFIX_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TOOLS_CHANGE_NS_PREFIX_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TOOLS_CHANGE_NS_PREFIX_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_CHANGE_NS_PREFIX_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.TOOLS_RENAME_NODE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_RENAME_NODE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_RENAME_NODE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TOOLS_RENAME_NODE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TOOLS_RENAME_NODE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_RENAME_NODE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.TOOLS_REMOVE_NODE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_REMOVE_NODE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_REMOVE_NODE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TOOLS_REMOVE_NODE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TOOLS_REMOVE_NODE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_REMOVE_NODE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.TOOLS_ADD_NODE_TO_NS_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_ADD_NODE_TO_NS_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_ADD_NODE_TO_NS_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TOOLS_ADD_NODE_TO_NS_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TOOLS_ADD_NODE_TO_NS_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_ADD_NODE_TO_NS_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.TOOLS_SET_NODE_VALUE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_SET_NODE_VALUE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_SET_NODE_VALUE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TOOLS_SET_NODE_VALUE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TOOLS_SET_NODE_VALUE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_SET_NODE_VALUE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.TOOLS_ADD_NODE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_ADD_NODE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);

					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_ADD_NODE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);

					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TOOLS_ADD_NODE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TOOLS_ADD_NODE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_ADD_NODE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}

			else if (actionName.equals(KeyPreferences.TOOLS_REMOVE_UNUSED_NS_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_REMOVE_UNUSED_NS_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);

					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_REMOVE_UNUSED_NS_ACTION);
					if(item != null) item.setAccelerator(stroke,true);

					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TOOLS_REMOVE_UNUSED_NS_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TOOLS_REMOVE_UNUSED_NS_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_REMOVE_UNUSED_NS_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.TOOLS_CONVERT_NODE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_CONVERT_NODE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);

					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_CONVERT_NODE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);

					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TOOLS_CONVERT_NODE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TOOLS_CONVERT_NODE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_CONVERT_NODE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.TOOLS_SORT_NODE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_SORT_NODE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);

					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_SORT_NODE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);

					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.TOOLS_SORT_NODE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.TOOLS_SORT_NODE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.TOOLS_SORT_NODE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.XDIFF_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.XDIFF_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);

					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.XDIFF_ACTION);
					if(item != null) item.setAccelerator(stroke,true);

					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.XDIFF_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.XDIFF_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.XDIFF_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.HIGHLIGHT_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.HIGHLIGHT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.HIGHLIGHT_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.VIEW_STANDARD_BUTTONS_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_STANDARD_BUTTONS_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_STANDARD_BUTTONS_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.VIEW_EDITOR_BUTTONS_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_BUTTONS_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_BUTTONS_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.VIEW_FRAGMENT_BUTTONS_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_FRAGMENT_BUTTONS_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_FRAGMENT_BUTTONS_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}	
			
			else if (actionName.equals(KeyPreferences.VIEW_EDITOR_SHOW_LINE_NUMBER_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_SHOW_LINE_NUMBER_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_SHOW_LINE_NUMBER_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.VIEW_EDITOR_SHOW_OVERVIEW_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_SHOW_OVERVIEW_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_SHOW_OVERVIEW_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.VIEW_EDITOR_SHOW_FOLDING_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_SHOW_FOLDING_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_SHOW_FOLDING_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.VIEW_EDITOR_SHOW_ANNOTATION_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_SHOW_ANNOTATION_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_SHOW_ANNOTATION_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}

			else if (actionName.equals(KeyPreferences.VIEW_EDITOR_TAG_COMPLETION_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_TAG_COMPLETION_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_TAG_COMPLETION_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.VIEW_EDITOR_END_TAG_COMPLETION_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_END_TAG_COMPLETION_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_END_TAG_COMPLETION_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.VIEW_EDITOR_SMART_INDENTATION_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_SMART_INDENTATION_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_SMART_INDENTATION_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.VIEW_EDITOR_SOFT_WRAPPING_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_SOFT_WRAPPING_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_SOFT_WRAPPING_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.VIEW_EDITOR_ERROR_HIGHLIGHTING_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_ERROR_HIGHLIGHTING_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_EDITOR_ERROR_HIGHLIGHTING_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}

			else if (actionName.equals(KeyPreferences.VIEWER_SHOW_NAMESPACES_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEWER_SHOW_NAMESPACES_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEWER_SHOW_NAMESPACES_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.VIEWER_SHOW_ATTRIBUTES_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEWER_SHOW_ATTRIBUTES_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEWER_SHOW_ATTRIBUTES_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.VIEWER_SHOW_COMMENTS_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEWER_SHOW_COMMENTS_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEWER_SHOW_COMMENTS_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.VIEWER_SHOW_TEXT_CONTENT_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEWER_SHOW_TEXT_CONTENT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEWER_SHOW_TEXT_CONTENT_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.VIEWER_SHOW_PROCESSING_INSTRUCTIONS_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEWER_SHOW_PROCESSING_INSTRUCTIONS_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEWER_SHOW_PROCESSING_INSTRUCTIONS_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.VIEWER_INLINE_MIXED_CONTENT_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEWER_INLINE_MIXED_CONTENT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEWER_INLINE_MIXED_CONTENT_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.OUTLINER_SHOW_ATTRIBUTE_VALUES_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.OUTLINER_SHOW_ATTRIBUTE_VALUES_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.OUTLINER_SHOW_ATTRIBUTE_VALUES_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.OUTLINER_SHOW_ELEMENT_VALUES_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.OUTLINER_SHOW_ELEMENT_VALUES_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.OUTLINER_SHOW_ELEMENT_VALUES_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.OUTLINER_CREATE_REQUIRED_NODES_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.OUTLINER_CREATE_REQUIRED_NODES_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.OUTLINER_CREATE_REQUIRED_NODES_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.VIEW_SYNCHRONIZE_SPLITS_ACTION))
			{
				// update the debugger shortcutt
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_SYNCHRONIZE_SPLITS_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke));
				}
				else
				{
					// just blank the accelerator
					JCheckBoxMenuItem item = parent.getCheckBoxItem(KeyPreferences.VIEW_SYNCHRONIZE_SPLITS_ACTION);
					if(item != null) item.setAccelerator(null);
				}
			}
			
			else if (actionName.equals(KeyPreferences.VIEW_SPLIT_HORIZONTALLY_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VIEW_SPLIT_HORIZONTALLY_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);

					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VIEW_SPLIT_HORIZONTALLY_ACTION);
					if(item != null) item.setAccelerator(stroke,true);

					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.VIEW_SPLIT_HORIZONTALLY_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.VIEW_SPLIT_HORIZONTALLY_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VIEW_SPLIT_HORIZONTALLY_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.VIEW_SPLIT_VERTICALLY_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VIEW_SPLIT_VERTICALLY_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);

					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VIEW_SPLIT_VERTICALLY_ACTION);
					if(item != null) item.setAccelerator(stroke,true);

					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.VIEW_SPLIT_VERTICALLY_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.VIEW_SPLIT_VERTICALLY_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VIEW_SPLIT_VERTICALLY_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			
			else if (actionName.equals(KeyPreferences.VIEW_UNSPLIT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VIEW_UNSPLIT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);

					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VIEW_UNSPLIT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);

					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.VIEW_UNSPLIT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.VIEW_UNSPLIT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.VIEW_UNSPLIT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			/*else if (actionName.equals(KeyPreferences.GRID_DELETE_ACTION))
			{
			    // update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_ACTION);
					if(item != null) 
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_ACTION);
					if(item != null)
						if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_DELETE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_DELETE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_ADD_ATTRIBUTE_COLUMN_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_ATTRIBUTE_COLUMN_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_ATTRIBUTE_COLUMN_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_ADD_ATTRIBUTE_COLUMN_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_ADD_ATTRIBUTE_COLUMN_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_ATTRIBUTE_COLUMN_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_ADD_ATTRIBUTE_TO_SELECTED_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_ATTRIBUTE_TO_SELECTED_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_ATTRIBUTE_TO_SELECTED_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_ADD_ATTRIBUTE_TO_SELECTED_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_ADD_ATTRIBUTE_TO_SELECTED_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_ATTRIBUTE_TO_SELECTED_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_ADD_CHILD_TABLE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_CHILD_TABLE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_CHILD_TABLE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_ADD_CHILD_TABLE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_ADD_CHILD_TABLE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_CHILD_TABLE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_ADD_ELEMENT_AFTER_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_ELEMENT_AFTER_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_ELEMENT_AFTER_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_ADD_ELEMENT_AFTER_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_ADD_ELEMENT_AFTER_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_ELEMENT_AFTER_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_ADD_ELEMENT_BEFORE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_ELEMENT_BEFORE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_ELEMENT_BEFORE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_ADD_ELEMENT_BEFORE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_ADD_ELEMENT_BEFORE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_ELEMENT_BEFORE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_ADD_TEXT_COLUMN_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_TEXT_COLUMN_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_TEXT_COLUMN_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_ADD_TEXT_COLUMN_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_ADD_TEXT_COLUMN_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_TEXT_COLUMN_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_ADD_TEXT_TO_SELECTED_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_TEXT_TO_SELECTED_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_TEXT_TO_SELECTED_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_ADD_TEXT_TO_SELECTED_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_ADD_TEXT_TO_SELECTED_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_ADD_TEXT_TO_SELECTED_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_DELETE_ATTS_AND_TEXT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_ATTS_AND_TEXT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_ATTS_AND_TEXT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_DELETE_ATTS_AND_TEXT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_DELETE_ATTS_AND_TEXT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_ATTS_AND_TEXT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}				
			else if (actionName.equals(KeyPreferences.GRID_DELETE_CHILD_TABLE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_CHILD_TABLE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_CHILD_TABLE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_DELETE_CHILD_TABLE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_DELETE_CHILD_TABLE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_CHILD_TABLE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_DELETE_COLUMN_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_COLUMN_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_COLUMN_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_DELETE_COLUMN_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_DELETE_COLUMN_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_COLUMN_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_DELETE_ROW_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_ROW_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_ROW_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_DELETE_ROW_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_DELETE_ROW_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_ROW_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_DELETE_SELECTED_ATTRIBUTE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_SELECTED_ATTRIBUTE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_SELECTED_ATTRIBUTE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_DELETE_SELECTED_ATTRIBUTE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_DELETE_SELECTED_ATTRIBUTE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_SELECTED_ATTRIBUTE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_DELETE_SELECTED_TEXT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_SELECTED_TEXT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_SELECTED_TEXT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_DELETE_SELECTED_TEXT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_DELETE_SELECTED_TEXT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_DELETE_SELECTED_TEXT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_RENAME_ATTRIBUTE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_RENAME_ATTRIBUTE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_RENAME_ATTRIBUTE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_RENAME_ATTRIBUTE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_RENAME_ATTRIBUTE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_RENAME_ATTRIBUTE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_RENAME_SELECTED_ATTRIBUTE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_RENAME_SELECTED_ATTRIBUTE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_RENAME_SELECTED_ATTRIBUTE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_RENAME_SELECTED_ATTRIBUTE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_RENAME_SELECTED_ATTRIBUTE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_RENAME_SELECTED_ATTRIBUTE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_MOVE_ROW_DOWN_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_MOVE_ROW_DOWN_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_MOVE_ROW_DOWN_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_MOVE_ROW_DOWN_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_MOVE_ROW_DOWN_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_MOVE_ROW_DOWN_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_MOVE_ROW_UP_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_MOVE_ROW_UP_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_MOVE_ROW_UP_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_MOVE_ROW_UP_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_MOVE_ROW_UP_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_MOVE_ROW_UP_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_SORT_TABLE_DESCENDING_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_SORT_TABLE_DESCENDING_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_SORT_TABLE_DESCENDING_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_SORT_TABLE_DESCENDING_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_SORT_TABLE_DESCENDING_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_SORT_TABLE_DESCENDING_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_SORT_TABLE_ASCENDING_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_SORT_TABLE_ASCENDING_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_SORT_TABLE_ASCENDING_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_SORT_TABLE_ASCENDING_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_SORT_TABLE_ASCENDING_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_SORT_TABLE_ASCENDING_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_UNSORT_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_UNSORT_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_UNSORT_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_UNSORT_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_UNSORT_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_UNSORT_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}				
			else if (actionName.equals(KeyPreferences.GRID_GOTO_PARENT_TABLE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_GOTO_PARENT_TABLE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_GOTO_PARENT_TABLE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_GOTO_PARENT_TABLE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_GOTO_PARENT_TABLE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_GOTO_PARENT_TABLE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_GOTO_CHILD_TABLE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_GOTO_CHILD_TABLE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_GOTO_CHILD_TABLE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_GOTO_CHILD_TABLE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_GOTO_CHILD_TABLE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_GOTO_CHILD_TABLE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_COPY_SHALLOW_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_COPY_SHALLOW_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_COPY_SHALLOW_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_COPY_SHALLOW_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_COPY_SHALLOW_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_COPY_SHALLOW_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_PASTE_AS_CHILD_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_PASTE_AS_CHILD_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_PASTE_AS_CHILD_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_PASTE_AS_CHILD_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_PASTE_AS_CHILD_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_PASTE_AS_CHILD_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_PASTE_BEFORE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_PASTE_BEFORE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_PASTE_BEFORE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_PASTE_BEFORE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_PASTE_BEFORE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_PASTE_BEFORE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_PASTE_AFTER_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_PASTE_AFTER_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_PASTE_AFTER_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_PASTE_AFTER_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_PASTE_AFTER_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_PASTE_AFTER_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_COLLAPSE_ROW_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_COLLAPSE_ROW_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_COLLAPSE_ROW_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_COLLAPSE_ROW_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_COLLAPSE_ROW_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_COLLAPSE_ROW_ACTION);
					if(item != null)
						if(item != null) item.setAccelerator(null,false);
					
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_EXPAND_ROW_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_EXPAND_ROW_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_EXPAND_ROW_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_EXPAND_ROW_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_EXPAND_ROW_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_EXPAND_ROW_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}
			else if (actionName.equals(KeyPreferences.GRID_COLLAPSE_CURRENT_TABLE_ACTION))
			{
				// update the shortcuts
				if (keystrokeSize == 1)
				{
					// only a single keystroke
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_COLLAPSE_CURRENT_TABLE_ACTION);
					if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
				}
				else if (keystrokeSize == 2)
				{
					KeyStroke stroke = getKeyStroke(keystroke2);
				
					// we are in emacs mode, turn off accelarator from menu
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_COLLAPSE_CURRENT_TABLE_ACTION);
					if(item != null) item.setAccelerator(stroke,true);
					
					//add the second keystroke to the mode's map
					Action action = parent.getModeAction(KeyPreferences.GRID_COLLAPSE_CURRENT_TABLE_ACTION);
					parent.getStatusbar().addToModeMap(KeyPreferences.GRID_COLLAPSE_CURRENT_TABLE_ACTION,stroke,action);
				}
				else
				{
					// just blank the accelerator
					DogsBayMenuItem item = parent.getMenuItem(KeyPreferences.GRID_COLLAPSE_CURRENT_TABLE_ACTION);
					if(item != null) item.setAccelerator(null,false);
				}
			}*/
			
			// debuggerStart
			// PHASE 3: Debugger disabled during Saxon upgrade - entire block commented out
			/* else if (debugger != null)
			{
				if (actionName.equals(KeyPreferences.DEBUGGER_NEW_TRANSFORMATION_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_NEW_TRANSFORMATION_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_NEW_TRANSFORMATION_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_CLOSE_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_CLOSE_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_CLOSE_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_OPEN_SCENARIO_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_OPEN_SCENARIO_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_OPEN_SCENARIO_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_CLOSE_TRANSFORMATION_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_CLOSE_TRANSFORMATION_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_CLOSE_TRANSFORMATION_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_SAVE_AS_SCENARIO_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_SAVE_AS_SCENARIO_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_SAVE_AS_SCENARIO_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_FIND_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_FIND_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_FIND_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_FIND_NEXT_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_FIND_NEXT_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_FIND_NEXT_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_GOTO_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_GOTO_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_GOTO_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_START_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_START_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_START_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_RUN_END_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_RUN_END_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_RUN_END_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_PAUSE_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_PAUSE_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_PAUSE_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_STOP_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_STOP_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_STOP_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_STEP_INTO_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_STEP_INTO_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_STEP_INTO_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_STEP_OVER_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_STEP_OVER_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_STEP_OVER_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_STEP_OUT_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_STEP_OUT_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_STEP_OUT_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_REMOVE_ALL_BREAKPOINTS_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_REMOVE_ALL_BREAKPOINTS_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_REMOVE_ALL_BREAKPOINTS_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_OPEN_INPUT_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_OPEN_INPUT_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_OPEN_INPUT_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_OPEN_STYLESHEET_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_OPEN_STYLESHEET_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_OPEN_STYLESHEET_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_RELOAD_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_RELOAD_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_RELOAD_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_EXIT_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_EXIT_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_EXIT_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_COLLAPSE_ALL_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_COLLAPSE_ALL_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_COLLAPSE_ALL_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_EXPAND_ALL_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_EXPAND_ALL_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_EXPAND_ALL_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_STYLESHEET_SHOW_LINE_NUMBER_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_STYLESHEET_SHOW_LINE_NUMBER_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke));
					}
					else
					{
						// just blank the accelerator
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_STYLESHEET_SHOW_LINE_NUMBER_ACTION);
						if(item != null) item.setAccelerator(null);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_STYLESHEET_SHOW_OVERVIEW_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_STYLESHEET_SHOW_OVERVIEW_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke));
					}
					else
					{
						// just blank the accelerator
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_STYLESHEET_SHOW_OVERVIEW_ACTION);
						if(item != null) item.setAccelerator(null);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_STYLESHEET_SHOW_FOLDING_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_STYLESHEET_SHOW_FOLDING_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke));
					}
					else
					{
						// just blank the accelerator
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_STYLESHEET_SHOW_FOLDING_ACTION);
						if(item != null) item.setAccelerator(null);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_STYLESHEET_SOFT_WRAPPING_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_STYLESHEET_SOFT_WRAPPING_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke));
					}
					else
					{
						// just blank the accelerator
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_STYLESHEET_SOFT_WRAPPING_ACTION);
						if(item != null) item.setAccelerator(null);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_INPUT_SHOW_LINE_NUMBER_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_INPUT_SHOW_LINE_NUMBER_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke));
					}
					else
					{
						// just blank the accelerator
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_INPUT_SHOW_LINE_NUMBER_ACTION);
						if(item != null) item.setAccelerator(null);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_INPUT_SHOW_OVERVIEW_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_INPUT_SHOW_OVERVIEW_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke));
					}
					else
					{
						// just blank the accelerator
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_INPUT_SHOW_OVERVIEW_ACTION);
						if(item != null) item.setAccelerator(null);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_INPUT_SHOW_FOLDING_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_INPUT_SHOW_FOLDING_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke));
					}
					else
					{
						// just blank the accelerator
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_INPUT_SHOW_FOLDING_ACTION);
						if(item != null) item.setAccelerator(null);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_INPUT_SOFT_WRAPPING_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_INPUT_SOFT_WRAPPING_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke));
					}
					else
					{
						// just blank the accelerator
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_INPUT_SOFT_WRAPPING_ACTION);
						if(item != null) item.setAccelerator(null);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_OUTPUT_SHOW_LINE_NUMBER_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_OUTPUT_SHOW_LINE_NUMBER_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke));
					}
					else
					{
						// just blank the accelerator
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_OUTPUT_SHOW_LINE_NUMBER_ACTION);
						if(item != null) item.setAccelerator(null);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_OUTPUT_SOFT_WRAPPING_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_OUTPUT_SOFT_WRAPPING_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke));
					}
					else
					{
						// just blank the accelerator
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_OUTPUT_SOFT_WRAPPING_ACTION);
						if(item != null) item.setAccelerator(null);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_AUTO_OPEN_INPUT_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_AUTO_OPEN_INPUT_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke));
					}
					else
					{
						// just blank the accelerator
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_AUTO_OPEN_INPUT_ACTION);
						if(item != null) item.setAccelerator(null);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_ENABLE_TRACING_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_ENABLE_TRACING_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke));
					}
					else
					{
						// just blank the accelerator
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_ENABLE_TRACING_ACTION);
						if(item != null) item.setAccelerator(null);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_REDIRECT_OUTPUT_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_REDIRECT_OUTPUT_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke));
					}
					else
					{
						// just blank the accelerator
						JCheckBoxMenuItem item = debugger.getCheckBoxItem(KeyPreferences.DEBUGGER_REDIRECT_OUTPUT_ACTION);
						if(item != null) item.setAccelerator(null);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_SET_PARAMETERS_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_SET_PARAMETERS_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_SET_PARAMETERS_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_DISABLE_ALL_BREAKPOINTS_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_DISABLE_ALL_BREAKPOINTS_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_DISABLE_ALL_BREAKPOINTS_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}
				else if (actionName.equals(KeyPreferences.DEBUGGER_ENABLE_ALL_BREAKPOINTS_ACTION))
				{
					// update the debugger shortcutt
					if (keystrokeSize == 1)
					{
						// only a single keystroke
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_ENABLE_ALL_BREAKPOINTS_ACTION);
						if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
					}
					else
					{
						// just blank the accelerator
						DogsBayMenuItem item = debugger.getMenuItem(KeyPreferences.DEBUGGER_ENABLE_ALL_BREAKPOINTS_ACTION);
						if(item != null) item.setAccelerator(null,false);
					}
				}





			} // end debugger block */
			else {
								
				for(int cnt=0;cnt<getPluginMappings().size();++cnt) {
					Object obj = getPluginMappings().get(cnt);
					if((obj != null) && (obj instanceof PluginActionKeyMapping)) {
						PluginActionKeyMapping keyMapping = (PluginActionKeyMapping)obj;
						
						if(actionName.equals(keyMapping.getKeystroke_action_name())) {
						
							
//							 update the shortcuts
							if (keystrokeSize == 1)
							{
								// only a single keystroke
								DogsBayMenuItem item = parent.getMenuItem(keyMapping.getKeystroke_action_name());
								if(item != null) 
									if(item != null) item.setAccelerator(getKeyStroke(keystroke),false);
							}
							else if (keystrokeSize == 2)
							{
								KeyStroke stroke = getKeyStroke(keystroke2);
							
								// we are in emacs mode, turn off accelarator from menu
								DogsBayMenuItem item = parent.getMenuItem(keyMapping.getKeystroke_action_name());
								if(item != null)
									if(item != null) item.setAccelerator(stroke,true);
								
								//add the second keystroke to the mode's map
								Action action = parent.getModeAction(keyMapping.getKeystroke_action_name());
								parent.getStatusbar().addToModeMap(keyMapping.getKeystroke_action_name(),stroke,action);
							}
							else
							{
								// just blank the accelerator
								DogsBayMenuItem item = parent.getMenuItem(keyMapping.getKeystroke_action_name());
								if(item != null) item.setAccelerator(null,false);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * @param keystroke_action_name
	 * @param keystroke_action_description
	 * @param keystroke_mask
	 * @param keystroke_value
	 */
	public PluginActionKeyMapping addKeyMapping(String keystroke_action_name, String keystroke_action_description, String keystroke_mask, String keystroke_value) {

		PluginActionKeyMapping keyMapping = new PluginActionKeyMapping();
		keyMapping.setKeystroke_action_name(keystroke_action_name);
		keyMapping.setKeystroke_action_description(keystroke_action_description);
		keyMapping.setKeystroke_mask(keystroke_mask);
		keyMapping.setKeystroke_value(keystroke_value);
		
		this.getPluginMappings().add(keyMapping);
		
		return(keyMapping);
	}

	/**
	 * @param pluginMappings the pluginMappings to set
	 */
	public void setPluginMappings(List pluginMappings) {

		this.pluginMappings = pluginMappings;
	}

	/**
	 * @return the pluginMappings
	 */
	public List getPluginMappings() {

		if(pluginMappings == null) {
			pluginMappings = new ArrayList();
		}
		return pluginMappings;
	}
	

	
} 
