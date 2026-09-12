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

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.MatteBorder;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayView;

/**
 * Manages toolbar creation and state for the DogsBayAIEditor.
 * Extracted from DogsBayAIEditor to reduce class size and improve cohesion.
 *
 * Responsibilities:
 * - Creating the main toolbar (file, edit, search, validate, transform buttons)
 * - Creating the editor toolbar (XML editing actions)
 * - Managing format-specific toolbars (Markdown, AsciiDoc, etc.)
 * - Switching between XML and format-specific editor toolbars based on document type
 */
public class ToolbarManager {

	private static final CompoundBorder TITLE_BORDER = new CompoundBorder(
			new CompoundBorder(
					new MatteBorder(1, 1, 0, 0, UIManager.getColor("controlDkShadow")),
					new MatteBorder(0, 0, 0, 1, UIManager.getColor("controlHighlight"))),
			new CompoundBorder(
					new MatteBorder(1, 1, 0, 0, UIManager.getColor("controlHighlight")),
					new MatteBorder(0, 0, 0, 1, UIManager.getColor("controlDkShadow"))));

	private final DogsBayAIEditor editor;

	private JToolBar toolbar = null;
	private JToolBar editorToolbar = null;
	private JPanel toolbarPanel = null;
	private JPanel editorToolbarPanel = null;
	private JToggleButton highlightButton = null;
	private JButton buttonOpen = null;
	private Map<String, JToolBar> formatToolbars = new HashMap<>();
	private JToolBar activeFormatToolbar = null;

	public ToolbarManager(DogsBayAIEditor editor) {
		this.editor = editor;
	}

	/**
	 * Creates and initializes the editor toolbar and its panel.
	 * Called during editor construction.
	 */
	public void initEditorToolbar() {
		editorToolbar = createEditorToolbar();
		editorToolbar.setVisible(editor.getProperties().isShowEditorToolbar());

		editorToolbarPanel = new JPanel(new BorderLayout());
		editorToolbarPanel.add(editorToolbar, BorderLayout.CENTER);
		editorToolbarPanel.setVisible(editor.getProperties().isShowEditorToolbar());
		editorToolbarPanel.setBorder(TITLE_BORDER);
	}

	/**
	 * Creates and initializes the main toolbar and its panel.
	 * Called during editor construction.
	 */
	public void initMainToolbar() {
		toolbar = createToolbar();
		toolbar.setVisible(editor.getProperties().isShowToolbar());
		toolbarPanel = new JPanel(new BorderLayout());
		toolbarPanel.add(toolbar, BorderLayout.CENTER);
		toolbarPanel.setVisible(editor.getProperties().isShowToolbar());
		toolbarPanel.setBorder(new MatteBorder(0, 0, 1, 0, UIManager.getColor("controlDkShadow")));
	}

	/**
	 * Creates the main toolbar with standard buttons (file, edit, search, etc.).
	 */
	private JToolBar createToolbar() {
		JToolBar toolbar = new JToolBar();
		toolbar.setRollover(true);
		toolbar.setFloatable(false);
		toolbar.setBorderPainted(false);

		ActionRegistry actions = editor.getActionRegistry();

		toolbar.add(editor.getNewAction()).setMnemonic(0);
		buttonOpen = toolbar.add(editor.getOpenAction());
		buttonOpen.setMnemonic(0);
		toolbar.add(editor.getOpenRemoteDocumentAction()).setMnemonic(0);
		toolbar.add(editor.getSaveAction()).setMnemonic(0);
		toolbar.add(editor.getSaveAsAction()).setMnemonic(0);
		toolbar.add(editor.getSaveAsRemoteAction()).setMnemonic(0);
		toolbar.addSeparator();
		toolbar.add(editor.getPrintAction()).setMnemonic(0);
		toolbar.addSeparator();
		toolbar.add(editor.getUndoAction()).setMnemonic(0);
		toolbar.add(editor.getRedoAction()).setMnemonic(0);
		toolbar.addSeparator();
		toolbar.add(editor.getCutAction()).setMnemonic(0);
		toolbar.add(editor.getCopyAction()).setMnemonic(0);
		toolbar.add(editor.getPasteAction()).setMnemonic(0);
		toolbar.addSeparator();
		toolbar.add(editor.getFindAction()).setMnemonic(0);
		toolbar.add(editor.getFindNextAction()).setMnemonic(0);
		toolbar.add(editor.getReplaceAction()).setMnemonic(0);
		toolbar.addSeparator();
		toolbar.add(editor.getValidateAction()).setMnemonic(0);
		toolbar.add(editor.getParseAction()).setMnemonic(0);
		toolbar.addSeparator();
		// The view switcher (Editor/Author/Preview) and split controls live in the menu bar,
		// to the left of the layout toggles — see MenuBuilder.addViewSplitButtons(). On macOS
		// the screen menu bar can't host buttons, so they fall back to the toolbar there
		// (mutually exclusive with the menu-bar placement).
		if (isMacScreenMenuBar()) {
			toolbar.add(editor.getShowEditorViewAction()).setMnemonic(0);
			toolbar.add(editor.getShowAuthorViewAction()).setMnemonic(0);
			toolbar.add(editor.getOpenPreviewAction()).setMnemonic(0);
			toolbar.addSeparator();
			toolbar.add(editor.getToggleAuthorSplitAction()).setMnemonic(0);
			toolbar.add(editor.getOpenPreviewSplitAction()).setMnemonic(0);
			toolbar.addSeparator();
			toolbar.add(editor.getSplitTabsHorizontallyAction()).setMnemonic(0);
			toolbar.add(editor.getSplitTabsVerticallyAction()).setMnemonic(0);
			toolbar.add(editor.getUnsplitTabsAction()).setMnemonic(0);
			toolbar.addSeparator();
		}

		toolbar.add(editor.getDefaultScenarioAction()).setMnemonic(0);
		// PHASE 3: Disabled XSLT Debugger during Saxon upgrade
		// toolbar.add(editor.getDebugScenarioAction()).setMnemonic(0);

		return toolbar;
	}

	/**
	 * True on macOS where the screen menu bar can't host buttons — so the view/split controls
	 * fall back to the toolbar there. Exactly the inverse of MenuBuilder's guard for adding
	 * them to the menu bar, so the buttons appear in exactly one place per platform.
	 */
	private static boolean isMacScreenMenuBar() {
		return com.dogsbay.dogsbayaieditor.Platform.isMacScreenMenuBar();
	}

	/**
	 * Creates the editor toolbar with XML editing actions.
	 */
	private JToolBar createEditorToolbar() {
		JToolBar toolbar = new JToolBar();
		toolbar.setRollover(true);
		toolbar.setFloatable(false);
		toolbar.setBorderPainted(false);

		toolbar.add(editor.getValidateAction()).setMnemonic(0);
		toolbar.add(editor.getParseAction()).setMnemonic(0);
		toolbar.addSeparator();

		toolbar.add(editor.getSelectElementAction()).setMnemonic(0);
		toolbar.add(editor.getSelectElementContentAction()).setMnemonic(0);

		toolbar.addSeparator();

		toolbar.add(editor.getIndentAction()).setMnemonic(0);
		toolbar.add(editor.getUnindentAction()).setMnemonic(0);

		toolbar.addSeparator();

		toolbar.add(editor.getTagAction()).setMnemonic(0);
		toolbar.add(editor.getCommentAction()).setMnemonic(0);
		toolbar.add(editor.getCDATAAction()).setMnemonic(0);

		toolbar.addSeparator();

		toolbar.add(editor.getSplitElementAction()).setMnemonic(0);

		toolbar.addSeparator();

		toolbar.add(editor.getSubstituteCharactersAction()).setMnemonic(0);
		toolbar.add(editor.getSubstituteEntitiesAction()).setMnemonic(0);

		toolbar.add(editor.getStripTagsAction()).setMnemonic(0);

		toolbar.addSeparator();

		toolbar.add(editor.getGotoStartTagAction()).setMnemonic(0);
		toolbar.add(editor.getGotoEndTagAction()).setMnemonic(0);
		toolbar.add(editor.getGotoAction()).setMnemonic(0);

		toolbar.addSeparator();
		toolbar.add(editor.getFormatAction()).setMnemonic(0);

		toolbar.addSeparator();
		toolbar.add(editor.getLockAction()).setMnemonic(0);

		toolbar.addSeparator();

		Action a = editor.getHighlightAction();

		highlightButton = new JToggleButton((String) a.getValue(Action.NAME),
				(ImageIcon) a.getValue(Action.SMALL_ICON));
		highlightButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				editor.getMenuBuilder().getHighlightMenuItem().setSelected(highlightButton.isSelected());
			}
		});
		highlightButton.setAction(a);
		highlightButton.setText(null);
		highlightButton.setEnabled(a.isEnabled());
		highlightButton.setToolTipText((String) a.getValue(Action.SHORT_DESCRIPTION));

		toolbar.add(highlightButton);

		toolbar.addSeparator();
		toolbar.add(editor.getCollapseAllAction()).setMnemonic(0);
		toolbar.add(editor.getExpandAllAction()).setMnemonic(0);

		return toolbar;
	}

	/**
	 * Swaps between the XML editor toolbar and format-specific toolbars
	 * based on the document's format.
	 */
	public void updateEditorToolbar(DogsBayDocument document) {
		boolean showToolbar = editor.getProperties().isShowEditorToolbar();
		com.dogsbay.xml.editor.DocumentFormat format = (document != null)
				? document.getDocumentFormat() : null;

		// Hide the currently active format toolbar
		if (activeFormatToolbar != null) {
			activeFormatToolbar.setVisible(false);
			activeFormatToolbar = null;
		}

		boolean hasFormatToolbar = false;

		if (format != null) {
			String formatName = format.getName();
			JToolBar formatToolbar = formatToolbars.get(formatName);

			// Lazily create the toolbar on first use
			if (formatToolbar == null) {
				formatToolbar = format.createToolbar();
				if (formatToolbar != null) {
					formatToolbars.put(formatName, formatToolbar);
					editorToolbarPanel.add(formatToolbar, BorderLayout.SOUTH);
				}
			}

			if (formatToolbar != null) {
				// Connect the toolbar to the current editor
				DogsBayView view = editor.getView();
				if (view != null && view.getEditor() != null) {
					com.dogsbay.xml.editor.EditorPanel ep = view.getEditor().getSelectedEditorPanel();
					if (ep != null) {
						if (formatToolbar instanceof com.dogsbay.xml.editor.MarkdownToolbar) {
							((com.dogsbay.xml.editor.MarkdownToolbar) formatToolbar).setEditor(ep.getEditor());
							((com.dogsbay.xml.editor.MarkdownToolbar) formatToolbar).registerKeyBindings(ep.getEditor());
						} else if (formatToolbar instanceof com.dogsbay.xml.editor.AsciiDocToolbar) {
							((com.dogsbay.xml.editor.AsciiDocToolbar) formatToolbar).setEditor(ep.getEditor());
							((com.dogsbay.xml.editor.AsciiDocToolbar) formatToolbar).registerKeyBindings(ep.getEditor());
						}
					}
				}

				editorToolbar.setVisible(false);
				formatToolbar.setVisible(showToolbar);
				activeFormatToolbar = formatToolbar;
				hasFormatToolbar = true;
			}
		}

		if (!hasFormatToolbar) {
			editorToolbar.setVisible(showToolbar);
		}

		editorToolbarPanel.setVisible(showToolbar);
	}

	// --- Getters ---

	public JToolBar getToolbar() {
		return toolbar;
	}

	public void setToolbar(JToolBar toolbar) {
		this.toolbar = toolbar;
	}

	public JToolBar getEditorToolbar() {
		return editorToolbar;
	}

	public JPanel getToolbarPanel() {
		return toolbarPanel;
	}

	public JPanel getEditorToolbarPanel() {
		return editorToolbarPanel;
	}

	public JToggleButton getHighlightButton() {
		return highlightButton;
	}

	public JButton getButtonOpen() {
		return buttonOpen;
	}
}
