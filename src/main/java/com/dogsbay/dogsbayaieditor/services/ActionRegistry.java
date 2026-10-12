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

import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;

import com.dogsbay.xml.editor.Editor;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.OpenFolderAction;
import com.dogsbay.dogsbayaieditor.actions.*;

/**
 * Registry for all action instances used by DogsBayAIEditor.
 * Actions are lazily initialized on first access.
 */
public class ActionRegistry {

	private final DogsBayAIEditor editor;
	private List actions = null;

	// Schema actions

	// File actions
	private NewAction newDocument = null;
	private OpenAction openDocument = null;
	private OpenCurrentURLAction openCurrentURL = null;
	private OpenRemoteDocumentAction openRemoteDocument = null;
	private OpenFolderAction openFolder = null;
	private CloseAction closeDocument = null;
	private CloseAllAction closeAllDocuments = null;
	private ReloadAction reloadDocument = null;
	private SaveAction saveDocument = null;
	private SaveAllAction saveAllDocuments = null;
	private SaveAsAction saveDocumentAs = null;
	private SaveAsRemoteAction saveDocumentAsRemote = null;
	private SaveAsTemplateAction saveAsTemplate = null;
	private PreferencesAction preferencesAction = null;
	private UnsplitTabsAction unsplitTabs = null;
	private SplitTabsVerticallyAction splitTabsVertically = null;
	private SplitTabsHorizontallyAction splitTabsHorizontally = null;

	// Grammar actions
	private NewGrammarAction newGrammar = null;
	private OpenGrammarAction openGrammar = null;
	private GrammarPropertiesAction grammarProperties = null;
	private ManageGrammarAction manageGrammar = null;

	// Conversion/Web Services actions

	// Schema/Script actions

	// Scenario/Transform actions
	private ExecuteDefaultScenarioAction executeDefaultScenario = null;
	private ExecutePreviousScenarioAction executePreviousScenario = null;
	private ExecuteSimpleXSLTAction executeSimpleXSLT = null;
	private ExecuteXSLTAction executeXSLT = null;
	private ExecuteFOAction executeFO = null;
	private ExecutePreviousXSLTAction executePreviousXSLT = null;
	private ExecutePreviousFOAction executePreviousFO = null;
	private ManageScenarioAction manageScenario = null;
	private ManageTemplateAction manageTemplate = null;

	// Edit actions
	private UndoAction undoAction = null;
	private RedoAction redoAction = null;
	private CopyAction copyAction = null;
	private CutAction cutAction = null;
	private PasteAction pasteAction = null;

	// Find actions
	private FindAction findAction = null;
	private ReplaceAction replaceAction = null;
	private FindNextAction findNextAction = null;
	private FindInFilesAction findInFilesAction = null;
	private ReplaceInFilesAction replaceInFilesAction = null;

	// Tree actions
	private ExpandAllAction expandAll = null;
	private CollapseAllAction collapseAll = null;

	// Help actions

	// Designer actions

	// Editor specific actions
	private SelectElementAction selectElementAction = null;
	private SelectElementContentAction selectElementContentAction = null;
	private GotoStartTagAction gotoStartTagAction = null;
	private GotoEndTagAction gotoEndTagAction = null;
	private GotoNextAttributeValueAction gotoNextAttributeValueAction = null;
	private GotoPreviousAttributeValueAction gotoPreviousAttributeValueAction = null;
	private ToggleEmptyElementAction toggleEmptyElementAction = null;
	private RenameElementAction renameElementAction = null;
	private HighlightAction highlightAction = null;
	private InsertEntityAction insertEntityAction = null;
	private SubstituteCharactersAction substituteCharactersAction = null;
	private SubstituteEntitiesAction substituteEntitiesAction = null;
	private StripTagsAction stripTagsAction = null;
	private SplitElementAction splitElementAction = null;
	private TagAction tagAction = null;
	private RepeatTagAction repeatTagAction = null;
	private CDATAAction cdataAction = null;
	private CommentAction commentAction = null;
	private LockAction lockAction = null;
	private GotoAction gotoAction = null;
	private ToggleBookmarkAction toggleBookmarkAction = null;
	private SelectBookmarkAction selectBookmarkAction = null;
	private SelectFragmentAction selectFragmentAction = null;
	private IndentAction indentAction = null;
	private UnindentAction unindentAction = null;
	private FormatAction formatAction = null;
	private PrintAction printAction = null;
	private PageSetupAction pageSetupAction = null;

	// Validation actions
	private ParseAction parseAction = null;
	private ValidateAction validateAction = null;
	private ValidateDTDAction validateDTDAction = null;
	private ValidateRelaxNGAction validateRelaxNGAction = null;
	private SetXMLDeclarationAction setXMLDeclarationAction = null;
	private SetXMLDoctypeAction setXMLDoctypeAction = null;
	private ResolveXIncludesAction resolveXIncludesAction = null;
	private XDiffAction xdiffAction = null;
	private CleanUpHTMLAction cleanUpHTMLAction = null;
	private OpenBrowserAction openBrowserAction = null;
	private OpenPreviewAction openPreviewAction = null;
	private com.dogsbay.dogsbayaieditor.actions.OpenPreviewSplitAction openPreviewSplitAction = null;
	private com.dogsbay.dogsbayaieditor.actions.ShowEditorViewAction showEditorViewAction = null;
	private com.dogsbay.dogsbayaieditor.actions.ShowAuthorViewAction showAuthorViewAction = null;
	private com.dogsbay.dogsbayaieditor.actions.ToggleAuthorSplitAction toggleAuthorSplitAction = null;
	private ChangeDocumentAction changeDocumentAction = null;

	// Import actions
	private ImportFromTextAction importFromTextAction = null;
	private ImportFromExcelAction importFromExcelAction = null;
	private ImportFromDBTableAction importFromDBTableAction = null;
	private ImportFromSQLXMLAction importFromSQLXMLAction = null;

	// Tools actions
	private ToolsStripTextAction toolsStripTextAction = null;
	private ToolsCapitalizeAction toolsCapitalizeAction = null;
	private ToolsDeCapitalizeAction toolsDeCapitalizeAction = null;
	private ToolsLowercaseAction toolsLowercaseAction = null;
	private ToolsUppercaseAction toolsUppercaseAction = null;
	private ToolsMoveNSToRootAction toolsMoveNSToRootAction = null;
	private ToolsMoveNSToFirstUsedAction toolsMoveNSToFirstUsedAction = null;
	private ToolsChangeNSPrefixAction toolsChangeNSPrefixAction = null;
	private ToolsRenameNodeAction toolsRenameNodeAction = null;
	private ToolsRemoveNodeAction toolsRemoveNodeAction = null;
	private ToolsAddNodeToNamespaceAction toolsAddNodeToNamespaceAction = null;
	private ToolsSetNodeValueAction toolsSetNodeValueAction = null;
	private ToolsAddNodeAction toolsAddNodeAction = null;
	private ToolsRemoveUnusedNSAction toolsRemoveUnusedNSAction = null;
	private ToolsConvertNodeAction toolsConvertNodeAction = null;
	private ToolsSortNodeAction toolsSortNodeAction = null;

	// View actions
	private SynchroniseSelectionAction syncSelection = null;
	private ToggleFullScreenAction toggleFullScreen = null;

	// Error list action
	private CopyErrorListAction copyErrorListAction = null;

	public ActionRegistry(DogsBayAIEditor editor) {
		this.editor = editor;
	}

	/**
	 * @return the actions list
	 */
	public List getActions() {
		if (actions == null) {
			actions = new ArrayList();
		}
		return actions;
	}

	// ---- Getter methods (lazy initialization) ----

	public TagAction getTagAction() {
		if (tagAction == null) {
			tagAction = new TagAction(editor);
		}
		return tagAction;
	}

	public RepeatTagAction getRepeatTagAction() {
		if (repeatTagAction == null) {
			repeatTagAction = new RepeatTagAction(editor);
		}
		return repeatTagAction;
	}

	public SelectElementAction getSelectElementAction() {
		if (selectElementAction == null) {
			selectElementAction = new SelectElementAction(editor);
			getActions().add(selectElementAction);
		}
		return selectElementAction;
	}

	public SelectElementContentAction getSelectElementContentAction() {
		if (selectElementContentAction == null) {
			selectElementContentAction = new SelectElementContentAction(editor);
			getActions().add(selectElementContentAction);
		}
		return selectElementContentAction;
	}

	public HighlightAction getHighlightAction() {
		if (highlightAction == null) {
			highlightAction = new HighlightAction(editor);
			getActions().add(highlightAction);
		}
		return highlightAction;
	}

	public GotoStartTagAction getGotoStartTagAction() {
		if (gotoStartTagAction == null) {
			gotoStartTagAction = new GotoStartTagAction(editor);
			getActions().add(gotoStartTagAction);
		}
		return gotoStartTagAction;
	}

	public GotoEndTagAction getGotoEndTagAction() {
		if (gotoEndTagAction == null) {
			gotoEndTagAction = new GotoEndTagAction(editor);
			getActions().add(gotoEndTagAction);
		}
		return gotoEndTagAction;
	}

	public ToggleEmptyElementAction getToggleEmptyElementAction() {
		if (toggleEmptyElementAction == null) {
			toggleEmptyElementAction = new ToggleEmptyElementAction(editor);
			getActions().add(toggleEmptyElementAction);
		}
		return toggleEmptyElementAction;
	}

	public RenameElementAction getRenameElementAction() {
		if (renameElementAction == null) {
			renameElementAction = new RenameElementAction(editor);
			getActions().add(renameElementAction);
		}
		return renameElementAction;
	}

	public GotoNextAttributeValueAction getGotoNextAttributeValueAction() {
		if (gotoNextAttributeValueAction == null) {
			gotoNextAttributeValueAction = new GotoNextAttributeValueAction(editor);
			getActions().add(gotoNextAttributeValueAction);
		}
		return gotoNextAttributeValueAction;
	}

	public GotoPreviousAttributeValueAction getGotoPreviousAttributeValueAction() {
		if (gotoPreviousAttributeValueAction == null) {
			gotoPreviousAttributeValueAction = new GotoPreviousAttributeValueAction(editor);
			getActions().add(gotoPreviousAttributeValueAction);
		}
		return gotoPreviousAttributeValueAction;
	}

	public ToolsStripTextAction getToolsStripTextAction() {
		if (toolsStripTextAction == null) {
			toolsStripTextAction = new ToolsStripTextAction(editor, ((Editor) editor.getCurrent()), editor.getProperties());
			getActions().add(toolsStripTextAction);
		}
		return toolsStripTextAction;
	}

	public ToolsCapitalizeAction getToolsCapitalizeAction() {
		if (toolsCapitalizeAction == null) {
			toolsCapitalizeAction = new ToolsCapitalizeAction(editor, ((Editor) editor.getCurrent()), editor.getProperties());
			getActions().add(toolsCapitalizeAction);
		}
		return toolsCapitalizeAction;
	}

	public ToolsDeCapitalizeAction getToolsDeCapitalizeAction() {
		if (toolsDeCapitalizeAction == null) {
			toolsDeCapitalizeAction = new ToolsDeCapitalizeAction(editor, ((Editor) editor.getCurrent()), editor.getProperties());
			getActions().add(toolsDeCapitalizeAction);
		}
		return toolsDeCapitalizeAction;
	}

	public ToolsLowercaseAction getToolsLowercaseAction() {
		if (toolsLowercaseAction == null) {
			toolsLowercaseAction = new ToolsLowercaseAction(editor, ((Editor) editor.getCurrent()), editor.getProperties());
			getActions().add(toolsLowercaseAction);
		}
		return toolsLowercaseAction;
	}

	public ToolsUppercaseAction getToolsUppercaseAction() {
		if (toolsUppercaseAction == null) {
			toolsUppercaseAction = new ToolsUppercaseAction(editor, ((Editor) editor.getCurrent()), editor.getProperties());
			getActions().add(toolsUppercaseAction);
		}
		return toolsUppercaseAction;
	}

	public ToolsMoveNSToRootAction getToolsMoveNSToRootAction() {
		if (toolsMoveNSToRootAction == null) {
			toolsMoveNSToRootAction = new ToolsMoveNSToRootAction(editor, ((Editor) editor.getCurrent()));
			getActions().add(toolsMoveNSToRootAction);
		}
		return toolsMoveNSToRootAction;
	}

	public ToolsMoveNSToFirstUsedAction getToolsMoveNSToFirstUsedAction() {
		if (toolsMoveNSToFirstUsedAction == null) {
			toolsMoveNSToFirstUsedAction = new ToolsMoveNSToFirstUsedAction(editor, ((Editor) editor.getCurrent()),
					editor.getProperties());
			getActions().add(toolsMoveNSToFirstUsedAction);
		}
		return toolsMoveNSToFirstUsedAction;
	}

	public ToolsChangeNSPrefixAction getToolsChangeNSPrefixAction() {
		if (toolsChangeNSPrefixAction == null) {
			toolsChangeNSPrefixAction = new ToolsChangeNSPrefixAction(editor, ((Editor) editor.getCurrent()), editor.getProperties());
			getActions().add(toolsChangeNSPrefixAction);
		}
		return toolsChangeNSPrefixAction;
	}

	public ToolsRenameNodeAction getToolsRenameNodeAction() {
		if (toolsRenameNodeAction == null) {
			toolsRenameNodeAction = new ToolsRenameNodeAction(editor, ((Editor) editor.getCurrent()), editor.getProperties());
			getActions().add(toolsRenameNodeAction);
		}
		return toolsRenameNodeAction;
	}

	public ToolsRemoveNodeAction getToolsRemoveNodeAction() {
		if (toolsRemoveNodeAction == null) {
			toolsRemoveNodeAction = new ToolsRemoveNodeAction(editor, ((Editor) editor.getCurrent()), editor.getProperties());
			getActions().add(toolsRemoveNodeAction);
		}
		return toolsRemoveNodeAction;
	}

	public ToolsAddNodeToNamespaceAction getToolsAddNodeToNamespaceAction() {
		if (toolsAddNodeToNamespaceAction == null) {
			toolsAddNodeToNamespaceAction = new ToolsAddNodeToNamespaceAction(editor, ((Editor) editor.getCurrent()),
					editor.getProperties());
			getActions().add(toolsAddNodeToNamespaceAction);
		}
		return toolsAddNodeToNamespaceAction;
	}

	public ToolsSetNodeValueAction getToolsSetNodeValueAction() {
		if (toolsSetNodeValueAction == null) {
			toolsSetNodeValueAction = new ToolsSetNodeValueAction(editor, ((Editor) editor.getCurrent()), editor.getProperties());
			getActions().add(toolsSetNodeValueAction);
		}
		return toolsSetNodeValueAction;
	}

	public ToolsAddNodeAction getToolsAddNodeAction() {
		if (toolsAddNodeAction == null) {
			toolsAddNodeAction = new ToolsAddNodeAction(editor, ((Editor) editor.getCurrent()), editor.getProperties());
			getActions().add(toolsAddNodeAction);
		}
		return toolsAddNodeAction;
	}

	public ToolsRemoveUnusedNSAction getToolsRemoveUnusedNSAction() {
		if (toolsRemoveUnusedNSAction == null) {
			toolsRemoveUnusedNSAction = new ToolsRemoveUnusedNSAction(editor, ((Editor) editor.getCurrent()), editor.getProperties());
			getActions().add(toolsRemoveUnusedNSAction);
		}
		return toolsRemoveUnusedNSAction;
	}

	public ToolsConvertNodeAction getToolsConvertNodeAction() {
		if (toolsConvertNodeAction == null) {
			toolsConvertNodeAction = new ToolsConvertNodeAction(editor, ((Editor) editor.getCurrent()), editor.getProperties());
			getActions().add(toolsConvertNodeAction);
		}
		return toolsConvertNodeAction;
	}

	public ToolsSortNodeAction getToolsSortNodeAction() {
		if (toolsSortNodeAction == null) {
			toolsSortNodeAction = new ToolsSortNodeAction(editor, ((Editor) editor.getCurrent()), editor.getProperties());
			getActions().add(toolsSortNodeAction);
		}
		return toolsSortNodeAction;
	}

	public CommentAction getCommentAction() {
		if (commentAction == null) {
			commentAction = new CommentAction(editor);
			getActions().add(commentAction);
		}
		return commentAction;
	}

	public LockAction getLockAction() {
		if (lockAction == null) {
			lockAction = new LockAction(editor);
			getActions().add(lockAction);
		}
		return lockAction;
	}

	public CDATAAction getCDATAAction() {
		if (cdataAction == null) {
			cdataAction = new CDATAAction(editor);
			getActions().add(cdataAction);
		}
		return cdataAction;
	}

	public GotoAction getGotoAction() {
		if (gotoAction == null) {
			gotoAction = new GotoAction(editor);
			getActions().add(gotoAction);
		}
		return gotoAction;
	}

	public ToggleBookmarkAction getToggleBookmarkAction() {
		if (toggleBookmarkAction == null) {
			toggleBookmarkAction = new ToggleBookmarkAction(editor);
			getActions().add(toggleBookmarkAction);
		}
		return toggleBookmarkAction;
	}

	public SelectBookmarkAction getSelectBookmarkAction() {
		if (selectBookmarkAction == null) {
			selectBookmarkAction = new SelectBookmarkAction(editor);
			getActions().add(selectBookmarkAction);
		}
		return selectBookmarkAction;
	}

	public SelectFragmentAction getSelectFragmentAction() {
		if (selectFragmentAction == null) {
			selectFragmentAction = new SelectFragmentAction(editor);
			getActions().add(selectFragmentAction);
		}
		return selectFragmentAction;
	}

	public ParseAction getParseAction() {
		if (parseAction == null) {
			parseAction = new ParseAction(editor);
			getActions().add(parseAction);
		}
		return parseAction;
	}

	public ValidateAction getValidateAction() {
		if (validateAction == null) {
			validateAction = new ValidateAction(editor);
			getActions().add(validateAction);
		}
		return validateAction;
	}


	public ValidateDTDAction getValidateDTDAction() {
		if (validateDTDAction == null) {
			validateDTDAction = new ValidateDTDAction(editor);
			getActions().add(validateDTDAction);
		}
		return validateDTDAction;
	}

	public ValidateRelaxNGAction getValidateRelaxNGAction() {
		if (validateRelaxNGAction == null) {
			validateRelaxNGAction = new ValidateRelaxNGAction(editor);
			getActions().add(validateRelaxNGAction);
		}
		return validateRelaxNGAction;
	}

	public CleanUpHTMLAction getCleanUpHTMLAction() {
		if (cleanUpHTMLAction == null) {
			cleanUpHTMLAction = new CleanUpHTMLAction(editor);
			getActions().add(cleanUpHTMLAction);
		}
		return cleanUpHTMLAction;
	}

	public SetXMLDeclarationAction getSetXMLDeclarationAction() {
		if (setXMLDeclarationAction == null) {
			setXMLDeclarationAction = new SetXMLDeclarationAction(editor);
			getActions().add(setXMLDeclarationAction);
		}
		return setXMLDeclarationAction;
	}

	public SetXMLDoctypeAction getSetXMLDoctypeAction() {
		if (setXMLDoctypeAction == null) {
			setXMLDoctypeAction = new SetXMLDoctypeAction(editor);
			getActions().add(setXMLDoctypeAction);
		}
		return setXMLDoctypeAction;
	}

	public ChangeDocumentAction getChangeDocumentAction() {
		if (changeDocumentAction == null) {
			changeDocumentAction = new ChangeDocumentAction(editor);
			getActions().add(changeDocumentAction);
		}
		return changeDocumentAction;
	}


	public ResolveXIncludesAction getResolveXIncludesAction() {
		if (resolveXIncludesAction == null) {
			resolveXIncludesAction = new ResolveXIncludesAction(editor, editor.getProperties());
			getActions().add(resolveXIncludesAction);
		}
		return resolveXIncludesAction;
	}

	public XDiffAction getXDiffAction() {
		if (xdiffAction == null) {
			xdiffAction = new XDiffAction(editor, editor.getProperties());
			getActions().add(xdiffAction);
		}
		return xdiffAction;
	}

	public OpenBrowserAction getOpenBrowserAction() {
		if (openBrowserAction == null) {
			openBrowserAction = new OpenBrowserAction(editor);
			getActions().add(openBrowserAction);
		}
		return openBrowserAction;
	}

	public OpenPreviewAction getOpenPreviewAction() {
		if (openPreviewAction == null) {
			openPreviewAction = new OpenPreviewAction(editor);
			getActions().add(openPreviewAction);
		}
		return openPreviewAction;
	}

	public com.dogsbay.dogsbayaieditor.actions.OpenPreviewSplitAction getOpenPreviewSplitAction() {
		if (openPreviewSplitAction == null) {
			openPreviewSplitAction = new com.dogsbay.dogsbayaieditor.actions.OpenPreviewSplitAction(editor);
			getActions().add(openPreviewSplitAction);
		}
		return openPreviewSplitAction;
	}

	public com.dogsbay.dogsbayaieditor.actions.ShowEditorViewAction getShowEditorViewAction() {
		if (showEditorViewAction == null) {
			showEditorViewAction = new com.dogsbay.dogsbayaieditor.actions.ShowEditorViewAction(editor);
			getActions().add(showEditorViewAction);
		}
		return showEditorViewAction;
	}

	public com.dogsbay.dogsbayaieditor.actions.ShowAuthorViewAction getShowAuthorViewAction() {
		if (showAuthorViewAction == null) {
			showAuthorViewAction = new com.dogsbay.dogsbayaieditor.actions.ShowAuthorViewAction(editor);
			getActions().add(showAuthorViewAction);
		}
		return showAuthorViewAction;
	}

	public com.dogsbay.dogsbayaieditor.actions.ToggleAuthorSplitAction getToggleAuthorSplitAction() {
		if (toggleAuthorSplitAction == null) {
			toggleAuthorSplitAction = new com.dogsbay.dogsbayaieditor.actions.ToggleAuthorSplitAction(editor);
			getActions().add(toggleAuthorSplitAction);
		}
		return toggleAuthorSplitAction;
	}

	public PrintAction getPrintAction() {
		if (printAction == null) {
			printAction = new PrintAction(editor, editor.getProperties());
			getActions().add(printAction);
		}
		return printAction;
	}

	public PageSetupAction getPageSetupAction() {
		if (pageSetupAction == null) {
			pageSetupAction = new PageSetupAction();
			getActions().add(pageSetupAction);
		}
		return pageSetupAction;
	}

	public InsertEntityAction getInsertEntityAction() {
		if (insertEntityAction == null) {
			insertEntityAction = new InsertEntityAction(editor);
			getActions().add(insertEntityAction);
		}
		return insertEntityAction;
	}

	public SubstituteEntitiesAction getSubstituteEntitiesAction() {
		if (substituteEntitiesAction == null) {
			substituteEntitiesAction = new SubstituteEntitiesAction(editor, editor.getProperties());
			getActions().add(substituteEntitiesAction);
		}
		return substituteEntitiesAction;
	}

	public StripTagsAction getStripTagsAction() {
		if (stripTagsAction == null) {
			stripTagsAction = new StripTagsAction(editor);
			getActions().add(stripTagsAction);
		}
		return stripTagsAction;
	}

	public SplitElementAction getSplitElementAction() {
		if (splitElementAction == null) {
			splitElementAction = new SplitElementAction(editor);
			getActions().add(splitElementAction);
		}
		return splitElementAction;
	}

	public SubstituteCharactersAction getSubstituteCharactersAction() {
		if (substituteCharactersAction == null) {
			substituteCharactersAction = new SubstituteCharactersAction(editor, editor.getProperties());
			getActions().add(substituteCharactersAction);
		}
		return substituteCharactersAction;
	}

	public IndentAction getIndentAction() {
		if (indentAction == null) {
			indentAction = new IndentAction();
			getActions().add(indentAction);
		}
		return indentAction;
	}

	public UnindentAction getUnindentAction() {
		if (unindentAction == null) {
			unindentAction = new UnindentAction();
			getActions().add(unindentAction);
		}
		return unindentAction;
	}

	public FormatAction getFormatAction() {
		if (formatAction == null) {
			formatAction = new FormatAction(editor, editor.getProperties());
			getActions().add(formatAction);
		}
		return formatAction;
	}

	private com.dogsbay.dogsbayaieditor.actions.ReflowSentencesAction reflowSentencesAction = null;

	public com.dogsbay.dogsbayaieditor.actions.ReflowSentencesAction getReflowSentencesAction() {
		if (reflowSentencesAction == null) {
			reflowSentencesAction = new com.dogsbay.dogsbayaieditor.actions.ReflowSentencesAction(editor);
			getActions().add(reflowSentencesAction);
		}
		return reflowSentencesAction;
	}






	public UndoAction getUndoAction() {
		if (undoAction == null) {
			undoAction = new UndoAction(editor);
		}
		return undoAction;
	}

	public RedoAction getRedoAction() {
		if (redoAction == null) {
			redoAction = new RedoAction(editor);
		}
		return redoAction;
	}

	public CopyAction getCopyAction() {
		if (copyAction == null) {
			copyAction = new CopyAction(editor);
		}
		return copyAction;
	}

	public CutAction getCutAction() {
		if (cutAction == null) {
			cutAction = new CutAction(editor);
		}
		return cutAction;
	}

	public PasteAction getPasteAction() {
		if (pasteAction == null) {
			pasteAction = new PasteAction(editor);
		}
		return pasteAction;
	}

	public FindAction getFindAction() {
		if (findAction == null) {
			findAction = new FindAction(editor, editor.getProperties());
		}
		return findAction;
	}

	public ReplaceAction getReplaceAction() {
		if (replaceAction == null) {
			replaceAction = new ReplaceAction(editor, editor.getProperties());
		}
		return replaceAction;
	}

	public FindNextAction getFindNextAction() {
		if (findNextAction == null) {
			findNextAction = new FindNextAction(editor, editor.getProperties());
		}
		return findNextAction;
	}

	public FindInFilesAction getFindInFilesAction() {
		if (findInFilesAction == null) {
			findInFilesAction = new FindInFilesAction(editor, editor.getProperties());
		}
		return findInFilesAction;
	}

	public ReplaceInFilesAction getReplaceInFilesAction() {
		if (replaceInFilesAction == null) {
			replaceInFilesAction = new ReplaceInFilesAction(editor, editor.getProperties());
		}
		return replaceInFilesAction;
	}

	public OpenAction getOpenAction() {
		if (openDocument == null) {
			openDocument = new OpenAction(editor, editor.getProperties());
		}
		return openDocument;
	}

	public OpenFolderAction getOpenFolderAction() {
		if (openFolder == null) {
			openFolder = new OpenFolderAction(editor);
		}
		return openFolder;
	}

	private com.dogsbay.dogsbayaieditor.OpenSampleProjectAction openSampleProject = null;

	public com.dogsbay.dogsbayaieditor.OpenSampleProjectAction getOpenSampleProjectAction() {
		if (openSampleProject == null) {
			openSampleProject = new com.dogsbay.dogsbayaieditor.OpenSampleProjectAction(editor);
		}
		return openSampleProject;
	}

	public OpenCurrentURLAction getOpenCurrentURLAction() {
		if (openCurrentURL == null) {
			openCurrentURL = new OpenCurrentURLAction(editor);
		}
		return openCurrentURL;
	}

	public OpenRemoteDocumentAction getOpenRemoteDocumentAction() {
		if (openRemoteDocument == null) {
			openRemoteDocument = new OpenRemoteDocumentAction(editor, editor.getProperties());
		}
		return openRemoteDocument;
	}

	public CloseAction getCloseAction() {
		if (closeDocument == null) {
			closeDocument = new CloseAction(editor);
		}
		return closeDocument;
	}

	public SplitTabsHorizontallyAction getSplitTabsHorizontallyAction() {
		if (splitTabsHorizontally == null) {
			splitTabsHorizontally = new SplitTabsHorizontallyAction(editor);
		}
		return splitTabsHorizontally;
	}

	public UnsplitTabsAction getUnsplitTabsAction() {
		if (unsplitTabs == null) {
			unsplitTabs = new UnsplitTabsAction(editor);
		}
		return unsplitTabs;
	}

	public SplitTabsVerticallyAction getSplitTabsVerticallyAction() {
		if (splitTabsVertically == null) {
			splitTabsVertically = new SplitTabsVerticallyAction(editor);
		}
		return splitTabsVertically;
	}

	public CloseAllAction getCloseAllAction() {
		if (closeAllDocuments == null) {
			closeAllDocuments = new CloseAllAction(editor);
		}
		return closeAllDocuments;
	}

	public ReloadAction getReloadAction() {
		if (reloadDocument == null) {
			reloadDocument = new ReloadAction(editor);
		}
		return reloadDocument;
	}

	public SaveAction getSaveAction() {
		if (saveDocument == null) {
			saveDocument = new SaveAction(editor);
		}
		return saveDocument;
	}

	public SaveAllAction getSaveAllAction() {
		if (saveAllDocuments == null) {
			saveAllDocuments = new SaveAllAction(editor);
		}
		return saveAllDocuments;
	}

	public SaveAsAction getSaveAsAction() {
		if (saveDocumentAs == null) {
			saveDocumentAs = new SaveAsAction(editor, editor.getProperties());
		}
		return saveDocumentAs;
	}

	public SaveAsRemoteAction getSaveAsRemoteAction() {
		if (saveDocumentAsRemote == null) {
			saveDocumentAsRemote = new SaveAsRemoteAction(editor, editor.getProperties());
		}
		return saveDocumentAsRemote;
	}

	public SaveAsTemplateAction getSaveAsTemplateAction() {
		if (saveAsTemplate == null) {
			saveAsTemplate = new SaveAsTemplateAction(editor, editor.getProperties());
		}
		return saveAsTemplate;
	}

	public PreferencesAction getPreferencesAction() {
		if (preferencesAction == null) {
			preferencesAction = new PreferencesAction(editor, editor.getProperties());
		}
		return preferencesAction;
	}

	public NewAction getNewAction() {
		if (newDocument == null) {
			newDocument = new NewAction(editor, editor.getProperties());
		}
		return newDocument;
	}

	public NewGrammarAction getNewGrammarAction() {
		if (newGrammar == null) {
			newGrammar = new NewGrammarAction(editor, editor.getProperties());
		}
		return newGrammar;
	}





	public OpenGrammarAction getOpenGrammarAction() {
		if (openGrammar == null) {
			openGrammar = new OpenGrammarAction(editor, editor.getProperties());
		}
		return openGrammar;
	}

	public GrammarPropertiesAction getGrammarPropertiesAction() {
		if (grammarProperties == null) {
			grammarProperties = new GrammarPropertiesAction(editor, editor.getProperties());
		}
		return grammarProperties;
	}

	public ManageGrammarAction getManageGrammarAction() {
		if (manageGrammar == null) {
			manageGrammar = new ManageGrammarAction(editor, editor.getProperties());
		}
		return manageGrammar;
	}

	public ExecuteDefaultScenarioAction getDefaultScenarioAction() {
		if (executeDefaultScenario == null) {
			executeDefaultScenario = new ExecuteDefaultScenarioAction(editor, editor.getProperties());
		}
		return executeDefaultScenario;
	}

	public ExecutePreviousScenarioAction getExecutePreviousScenarioAction() {
		if (executePreviousScenario == null) {
			executePreviousScenario = new ExecutePreviousScenarioAction(editor);
		}
		return executePreviousScenario;
	}

	public ExecuteXSLTAction getExecuteAdvancedXSLTAction() {
		if (executeXSLT == null) {
			executeXSLT = new ExecuteXSLTAction(editor);
		}
		return executeXSLT;
	}

	public ExecuteSimpleXSLTAction getExecuteSimpleXSLTAction() {
		if (executeSimpleXSLT == null) {
			executeSimpleXSLT = new ExecuteSimpleXSLTAction(editor);
		}
		return executeSimpleXSLT;
	}

	public ExecuteFOAction getExecuteFOAction() {
		if (executeFO == null) {
			executeFO = new ExecuteFOAction(editor);
		}
		return executeFO;
	}

	public ExecutePreviousFOAction getExecutePreviousFOAction() {
		if (executePreviousFO == null) {
			executePreviousFO = new ExecutePreviousFOAction(editor);
		}
		return executePreviousFO;
	}

	public ExecutePreviousXSLTAction getExecutePreviousXSLTAction() {
		if (executePreviousXSLT == null) {
			executePreviousXSLT = new ExecutePreviousXSLTAction(editor);
		}
		return executePreviousXSLT;
	}

	public ManageScenarioAction getManageScenarioAction() {
		if (manageScenario == null) {
			manageScenario = new ManageScenarioAction(editor, editor.getProperties());
		}
		return manageScenario;
	}

	public ManageTemplateAction getManageTemplateAction() {
		if (manageTemplate == null) {
			manageTemplate = new ManageTemplateAction(editor, editor.getProperties());
		}
		return manageTemplate;
	}

	public ImportFromTextAction getImportFromTextAction() {
		if (importFromTextAction == null) {
			importFromTextAction = new ImportFromTextAction(editor);
		}
		return importFromTextAction;
	}

	public ImportFromExcelAction getImportFromExcelAction() {
		if (importFromExcelAction == null) {
			importFromExcelAction = new ImportFromExcelAction(editor);
		}
		return importFromExcelAction;
	}

	public ImportFromDBTableAction getImportFromDBTableAction() {
		if (importFromDBTableAction == null) {
			importFromDBTableAction = new ImportFromDBTableAction(editor, editor.getProperties());
		}
		return importFromDBTableAction;
	}

	public ImportFromSQLXMLAction getImportFromSQLXMLAction() {
		if (importFromSQLXMLAction == null) {
			importFromSQLXMLAction = new ImportFromSQLXMLAction(editor, editor.getProperties());
		}
		return importFromSQLXMLAction;
	}

	public ExpandAllAction getExpandAllAction() {
		if (expandAll == null) {
			expandAll = new ExpandAllAction();
		}
		return expandAll;
	}

	public CollapseAllAction getCollapseAllAction() {
		if (collapseAll == null) {
			collapseAll = new CollapseAllAction();
		}
		return collapseAll;
	}

	public SynchroniseSelectionAction getSynchroniseSelectionAction() {
		if (syncSelection == null) {
			syncSelection = new SynchroniseSelectionAction(editor);
		}
		return syncSelection;
	}

	public ToggleFullScreenAction getToggleFullScreenAction() {
		if (toggleFullScreen == null) {
			toggleFullScreen = new ToggleFullScreenAction(editor);
		}
		return toggleFullScreen;
	}

	public CopyErrorListAction getCopyErrorListAction() {
		if (copyErrorListAction == null) {
			copyErrorListAction = new CopyErrorListAction(editor);
		}
		return copyErrorListAction;
	}
}
