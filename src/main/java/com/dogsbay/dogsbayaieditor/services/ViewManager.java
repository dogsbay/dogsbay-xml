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
import java.awt.Component;
import java.util.List;
import java.util.Vector;

import javax.swing.JSplitPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.editor.Editor;
import com.dogsbay.xml.viewer.Viewer;
import com.dogsbay.dogsbayaieditor.ChangeManager;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayTabbedView;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.UserView;
import com.dogsbay.dogsbayaieditor.ViewPanel;
import com.dogsbay.dogsbayaieditor.plugins.PluginActionKeyMapping;
import com.dogsbay.dogsbayaieditor.plugins.PluginView;
import com.dogsbay.dogsbayaieditor.plugins.PluginViewPanel;
import com.dogsbay.dogsbayaieditor.services.events.ActiveDocumentChangedEvent;
import com.dogsbay.dogsbayaieditor.services.events.ViewSwitchedEvent;

/**
 * Manages view/tab operations for the DogsBayAIEditor.
 * Extracted from DogsBayAIEditor to reduce class size and improve cohesion.
 *
 * Responsibilities:
 * - Tracking the current and previous views
 * - Managing tabbed view containers (split/unsplit)
 * - Switching between view types (editor, viewer, designer, schema, plugin)
 * - Distributing current view panel state to actions
 */
public class ViewManager {

	private final DogsBayAIEditor editor;

	private DogsBayView currentView = null;
	private DogsBayView previousView = null;
	private DogsBayTabbedView selectedTabbedView = null;
	private Vector tabbedViews = null;

	public ViewManager(DogsBayAIEditor editor) {
		this.editor = editor;
	}

	// --- Field accessors ---

	public DogsBayView getCurrentView() {
		return currentView;
	}

	public void setCurrentView(DogsBayView view) {
		this.currentView = view;
	}

	public DogsBayView getPreviousView() {
		return previousView;
	}

	public void setPreviousView(DogsBayView view) {
		this.previousView = view;
	}

	public DogsBayTabbedView getSelectedTabbedView() {
		return selectedTabbedView;
	}

	public void setSelectedTabbedView(DogsBayTabbedView tabbedView) {
		this.selectedTabbedView = tabbedView;
	}

	public Vector getTabbedViews() {
		return tabbedViews;
	}

	public void setTabbedViews(Vector tabbedViews) {
		this.tabbedViews = tabbedViews;
	}

	// --- View management methods ---

	public DogsBayView getView() {
		return currentView;
	}

	public DogsBayView getView(DogsBayDocument document) {
		Vector views = getViews();

		for (int i = 0; i < views.size(); i++) {
			DogsBayView view = (DogsBayView) views.elementAt(i);

			if (view.getDocument() == document) {
				return view;
			}
		}

		return null;
	}

	public Vector getViews() {
		Vector result = new Vector();

		for (int i = 0; i < tabbedViews.size(); i++) {
			Vector views = ((DogsBayTabbedView) tabbedViews.elementAt(i)).getViews();

			for (int j = 0; j < views.size(); j++) {
				result.addElement(views.elementAt(j));
			}
		}

		return result;
	}

	public void setView(DogsBayView tab) {

		if (currentView != tab) {
			previousView = currentView;
		}

		currentView = tab;

		if (tab != null) {
			ViewPanel current = tab.getCurrentView();
			editor.getHighlightButton().setSelected(currentView.getEditor().isHighlight());
			editor.getMenuBuilder().getHighlightMenuItem().setSelected(currentView.getEditor().isHighlight());

			editor.getOutputPanel().setErrorList(tab.getErrors());

			editor.setDocumentInternal(tab.getDocument(), true);
			editor.setSchemaInternal(tab.getSchema());

			editor.getNavigator().setDocument(tab.getDocument());
			if (editor.getSidebarViewer() != null) {
				editor.getSidebarViewer().setDocument(tab.getDocument());
			}

			editor.updateGrammarActions();
			editor.updateFragments();
			// updateScenarioActions();

			currentView.changeView(current);

			editor.setDocumentViewButtonPanel(tab.getDocumentViewButtonPanel());
			editor.updateDocumentViewButtonPanel();

			editor.setDocumentViewsMenu(tab.getDocumentViewsMenu());
			editor.updateDocumentViewMenu();

			ChangeManager manager = currentView.getChangeManager();

			editor.getUndoAction().setChangeManager(manager);
			editor.getRedoAction().setChangeManager(manager);
			editor.getCloseAction().setEnabled(true);
			editor.getCloseAllAction().setEnabled(true);
			editor.getReloadAction().setEnabled(true);

			// Highlight the current file in the file explorer
			// Skip during session restore to avoid repeated tree updates
			if (!editor.isRestoringSession() && editor.getFileExplorer() != null && tab.getDocument() != null) {
				java.net.URL docURL = tab.getDocument().getURL();
				if (docURL != null && "file".equals(docURL.getProtocol())) {
					try {
						java.io.File file = new java.io.File(docURL.toURI());
						editor.getFileExplorer().selectFile(file);
					} catch (Exception e) {
						// Ignore errors in file highlighting
					}
				}
			}

		} else {
			editor.getHighlightButton().setSelected(false);
			editor.getMenuBuilder().getHighlightMenuItem().setSelected(false);

			editor.getOutputPanel().setErrorList(null);

			editor.getCloseAction().setEnabled(false);
			editor.getCloseAllAction().setEnabled(false);
			editor.getReloadAction().setEnabled(false);

			editor.getUndoAction().setChangeManager(null);
			editor.getRedoAction().setChangeManager(null);

			editor.setDocumentInternal(null, false);
			editor.setSchemaInternal(null);
			editor.updateGrammarActions();
			editor.updateFragments();

			editor.getNavigator().setDocument(null);
			if (editor.getSidebarViewer() != null) {
				editor.getSidebarViewer().setDocument(null);
			}
			// updateScenarioActions();
			setCurrent(null);
		}

		editor.getSplitTabsHorizontallyAction().setEnabled(selectedTabbedView.getViews().size() > 1 && !editor.isFullScreen());
		editor.getSplitTabsVerticallyAction().setEnabled(selectedTabbedView.getViews().size() > 1 && !editor.isFullScreen());
		editor.getUnsplitTabsAction().setEnabled(tabbedViews.size() > 1 && !editor.isFullScreen());

		if (tabbedViews.size() > 1 && !editor.isFullScreen()) {
			editor.getMenuBuilder().getSynchroniseSplits().setEnabled(true);
		} else {
			editor.getMenuBuilder().getSynchroniseSplits().setEnabled(false);
		}

		editor.updateStatus();
		editor.setTitle(currentView);

		DogsBayDocument doc = (currentView != null) ? currentView.getDocument() : null;
		editor.getEventBus().publish(new ActiveDocumentChangedEvent(doc));
	}

	public void setCurrent(ViewPanel view) {

		editor.getOutputPanel().setCurrent(view);
		editor.getHelper().setView(view);
		editor.getCutAction().setView(view);
		editor.getCopyAction().setView(view);
		editor.getPasteAction().setView(view);
		editor.getFindAction().setView(view);
		editor.getFindNextAction().setView(view);
		editor.getReplaceAction().setView(view);

		editor.getSelectElementAction().setView(view);
		editor.getSelectElementContentAction().setView(view);

		editor.getGotoStartTagAction().setView(view);
		editor.getGotoEndTagAction().setView(view);
		editor.getToggleEmptyElementAction().setView(view);
		editor.getRenameElementAction().setView(view);
		editor.getHighlightAction().setView(view);
		editor.getGotoNextAttributeValueAction().setView(view);
		editor.getGotoPreviousAttributeValueAction().setView(view);

		editor.getTagAction().setView(view);
		editor.getRepeatTagAction().setView(view);
		editor.getCommentAction().setView(view);
		editor.getLockAction().setView(view);
		editor.getCDATAAction().setView(view);
		editor.getGotoAction().setView(view);
		editor.getToggleBookmarkAction().setView(view);
		editor.getSelectBookmarkAction().setView(view);
		editor.getSelectFragmentAction().setView(view);
		editor.getParseAction().setView(view);
		editor.getValidateAction().setView(view);
		editor.getValidateDTDAction().setView(view);
		editor.getValidateRelaxNGAction().setView(view);
		editor.getIndentAction().setView(view);
		editor.getInsertEntityAction().setView(view);
		editor.getSubstituteCharactersAction().setView(view);
		editor.getSubstituteEntitiesAction().setView(view);
		editor.getStripTagsAction().setView(view);
		editor.getSplitElementAction().setView(view);
		editor.getUnindentAction().setView(view);
		editor.getFormatAction().setView(view);
		editor.getReflowSentencesAction().setView(view);


		editor.getExpandAllAction().setView(view);
		editor.getCollapseAllAction().setView(view);
		editor.getSynchroniseSelectionAction().setView(view);

		// for each of the plugin buttons
		for (int cnt = 0; cnt < editor.getPluginViews().size(); ++cnt) {
			Object obj = editor.getPluginViews().get(cnt);
			if ((obj != null) && (obj instanceof PluginView)) {
				PluginView pluginView = (PluginView) obj;
				if (pluginView != null) {

					List actionList = pluginView.getActions();
					for (int acnt = 0; acnt < actionList.size(); ++acnt) {
						PluginActionKeyMapping pluginAction = (PluginActionKeyMapping) actionList.get(acnt);
						pluginAction.getAction().setView(view);
					}
				}
			}
		}

		// refactoring tools
		editor.getToolsStripTextAction().setView(view);
		editor.getToolsCapitalizeAction().setView(view);
		editor.getToolsDeCapitalizeAction().setView(view);
		editor.getToolsLowercaseAction().setView(view);
		editor.getToolsUppercaseAction().setView(view);
		editor.getToolsMoveNSToRootAction().setView(view);
		editor.getToolsMoveNSToFirstUsedAction().setView(view);
		editor.getToolsChangeNSPrefixAction().setView(view);
		editor.getToolsRenameNodeAction().setView(view);
		editor.getToolsRemoveNodeAction().setView(view);
		editor.getToolsAddNodeToNamespaceAction().setView(view);
		editor.getToolsSetNodeValueAction().setView(view);
		editor.getToolsAddNodeAction().setView(view);
		editor.getToolsRemoveUnusedNSAction().setView(view);
		editor.getToolsConvertNodeAction().setView(view);
		editor.getToolsSortNodeAction().setView(view);

		if (view instanceof Editor) {
			((Editor) view).updateHelper();
		} else if (view instanceof Viewer) {
			((Viewer) view).updateHelper();
		} else if (view instanceof PluginViewPanel) {
			((PluginViewPanel) view).updateHelper();
		}

		editor.updateFragments();

		String viewId = (view != null) ? view.getClass().getSimpleName() : "none";
		editor.getEventBus().publish(new ViewSwitchedEvent(viewId, view));
	}

	public void select(DogsBayView view) {
		for (int i = 0; i < tabbedViews.size(); i++) {
			DogsBayTabbedView tabbedView = (DogsBayTabbedView) tabbedViews.elementAt(i);

			if (tabbedView.contains(view)) {
				if (editor.isFullScreen() && !tabbedView.isSelected()) {
					editor.toggleFullScreen();
				}
				tabbedView.select(view);

				return;
			}
		}
	}

	public void select(DogsBayDocument document) {
		select(getView(document));
	}

	public void setViewIcon(DogsBayView view, javax.swing.Icon icon) {
		for (int i = 0; i < tabbedViews.size(); i++) {
			DogsBayTabbedView tabbedView = (DogsBayTabbedView) tabbedViews.elementAt(i);

			if (tabbedView.contains(view)) {
				tabbedView.setIcon(view, icon);
				return;
			}
		}
	}

	public void setViewTitle(DogsBayView view, String title) {
		for (int i = 0; i < tabbedViews.size(); i++) {
			DogsBayTabbedView tabbedView = (DogsBayTabbedView) tabbedViews.elementAt(i);

			if (tabbedView.contains(view)) {
				tabbedView.setTitle(view, title);
				break;
			}
		}

		editor.setTitle(view);
	}

	public void setSelected(DogsBayTabbedView tabs) {
		if (selectedTabbedView != tabs) {

			for (int i = 0; i < tabbedViews.size(); i++) {
				DogsBayTabbedView tabbedView = (DogsBayTabbedView) tabbedViews.elementAt(i);

				if (tabbedView != tabs) {
					tabbedView.setSelected(false);
				}
			}

			editor.getSplitTabsHorizontallyAction().setEnabled(tabs.getViews().size() > 1 && !editor.isFullScreen());
			editor.getSplitTabsVerticallyAction().setEnabled(tabs.getViews().size() > 1 && !editor.isFullScreen());
			editor.getUnsplitTabsAction().setEnabled(tabbedViews.size() > 1 && !editor.isFullScreen());

			if (tabbedViews.size() > 1 && !editor.isFullScreen()) {
				editor.getMenuBuilder().getSynchroniseSplits().setEnabled(true);
			} else {
				editor.getMenuBuilder().getSynchroniseSplits().setEnabled(false);
			}

			selectedTabbedView = tabs;
			setView(selectedTabbedView.getSelectedView());
		}
	}

	// --- View type switching ---

	public void switchToEditor() {
		currentView.switchToEditor();

		currentView.getEditorButton().setSelected(true);
		currentView.getEditorViewItem().setSelected(true);

		// make sure this runs after the gui is updated!
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				System.gc();
			}
		});
	}

	public void switchToViewer() throws Exception {
		currentView.switchToViewer();

		// make sure this runs after the gui is updated!
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				System.gc();
			}
		});
	}

	public void switchToAuthorSplit() throws Exception {
		currentView.switchToAuthorSplit();

		// make sure this runs after the gui is updated!
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				System.gc();
			}
		});
	}

	public void switchToAuthor() throws Exception {
		currentView.switchToAuthor();

		// make sure this runs after the gui is updated!
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				System.gc();
			}
		});
	}



	public void switchToUserView(UserView newUserView) throws Exception {
		currentView.switchToUserView(newUserView);

		// make sure this runs after the gui is updated!
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				System.gc();
			}
		});
	}

	public void switchToPluginView(PluginView pluginView) throws Exception {
		currentView.switchToPluginView(pluginView);

		// make sure this runs after the gui is updated!
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				System.gc();
			}
		});
	}

	public void switchToPluginView(PluginViewPanel pluginViewPanel) throws Exception {
		if ((getView() != null) && (getView().getPluginViewPanels() != null)) {
			for (int vcnt = 0; vcnt < getView().getPluginViewPanels().size(); ++vcnt) {
				PluginViewPanel panel = (PluginViewPanel) getView().getPluginViewPanels().get(vcnt);
				if (pluginViewPanel == panel) {
					switchToPluginView(pluginViewPanel.getPluginView());
				}
			}
		}
	}

	// --- Split/unsplit tab management ---

	public void splitHorizontally() {
		DogsBayTabbedView tabbedView = selectedTabbedView;

		if (tabbedView.getTabCount() > 1) {

			tabbedView.disableChangeListener(true);

			Component parentComponent = tabbedView.getParent();

			if (parentComponent instanceof JSplitPane) {
				JSplitPane split = (JSplitPane) parentComponent;
				boolean right = true;

				if (tabbedView == split.getLeftComponent()) {
					right = false;
				}

				DogsBayTabbedView bottomView = new DogsBayTabbedView(editor, tabbedView);
				bottomView.setScrollTabs(editor.getProperties().isScrollDocumentTabs());
				bottomView.disableChangeListener(true);

				tabbedViews.addElement(bottomView);

				DogsBayView view = tabbedView.getSelectedView();
				if (view != null) {
					tabbedView.remove(view);
					bottomView.add(view, view.getDocument().getName());
				} else {
					// Selected tab is a custom panel (Preview, Diff, etc.)
					Component selected = tabbedView.getSelectedComponent();
					if (selected == null) {
						tabbedViews.removeElement(bottomView);
						tabbedView.disableChangeListener(false);
						return;
					}
					int idx = tabbedView.getSelectedTabIndex();
					String title = tabbedView.getTabTitleAt(idx);
					tabbedView.removeTab(selected);
					bottomView.addCustomPanel(selected, title, title);
				}

				JSplitPane newSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabbedView, bottomView);
				newSplit.setResizeWeight(0.5);

				if (newSplit.getDividerSize() > 6) {
					newSplit.setDividerSize(6);
				}

				Object ui = newSplit.getUI();
				if (ui instanceof BasicSplitPaneUI) {
					((BasicSplitPaneUI) ui).getDivider().setBorder(null);
				}

				newSplit.setBorder(null);
				newSplit.setOneTouchExpandable(true);

				if (right) {
					split.setRightComponent(newSplit);
				} else {
					split.setLeftComponent(newSplit);
				}

				bottomView.setSelected(true);
				if (view != null) {
					view.setViewIcons();
				}

				bottomView.disableChangeListener(false);
			} else if (parentComponent instanceof JPanel) {
				JPanel panel = (JPanel) parentComponent;

				DogsBayTabbedView bottomView = new DogsBayTabbedView(editor, tabbedView);
				bottomView.setScrollTabs(editor.getProperties().isScrollDocumentTabs());
				bottomView.disableChangeListener(true);
				tabbedViews.addElement(bottomView);

				DogsBayView view = tabbedView.getSelectedView();
				if (view != null) {
					tabbedView.remove(view);
					bottomView.add(view, view.getDocument().getName());
				} else {
					// Selected tab is a custom panel (Preview, Diff, etc.)
					Component selected = tabbedView.getSelectedComponent();
					if (selected == null) {
						tabbedViews.removeElement(bottomView);
						tabbedView.disableChangeListener(false);
						return;
					}
					int idx = tabbedView.getTabCount() - 1;
					for (int i = 0; i < tabbedView.getTabCount(); i++) {
						Component c = ((javax.swing.JTabbedPane) ((java.awt.Container) tabbedView.getComponent(0)).getComponent(0)).getComponentAt(i);
						if (c == selected) { idx = i; break; }
					}
					String title = tabbedView.getTabTitleAt(idx);
					tabbedView.removeTab(selected);
					bottomView.addCustomPanel(selected, title, title);
				}

				JSplitPane newSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabbedView, bottomView);
				newSplit.setResizeWeight(0.5);

				if (newSplit.getDividerSize() > 6) {
					newSplit.setDividerSize(6);
				}

				Object ui = newSplit.getUI();
				if (ui instanceof BasicSplitPaneUI) {
					((BasicSplitPaneUI) ui).getDivider().setBorder(null);
				}

				newSplit.setBorder(null);
				newSplit.setOneTouchExpandable(true);

				panel.removeAll();
				panel.add(editor.getEditorToolbarPanel(), BorderLayout.NORTH);
				panel.add(newSplit, BorderLayout.CENTER);

				bottomView.setSelected(true);
				if (view != null) {
					view.setViewIcons();
				}
				bottomView.disableChangeListener(false);
			}

			tabbedView.disableChangeListener(false);
		}

		Vector views = getViews();
		if (views != null) {
			for (int i = 0; i < views.size(); i++) {
				DogsBayView view = (DogsBayView) views.elementAt(i);
				view.getEditor().scrollCursorToVisible();
			}
		}
	}

	public void splitVertically() {
		DogsBayTabbedView tabbedView = selectedTabbedView;

		if (tabbedView.getTabCount() > 1) {
			Component parentComponent = tabbedView.getParent();

			tabbedView.disableChangeListener(true);

			if (parentComponent instanceof JSplitPane) {
				JSplitPane split = (JSplitPane) parentComponent;
				boolean right = true;

				if (tabbedView == split.getLeftComponent()) {
					right = false;
				}

				DogsBayTabbedView bottomView = new DogsBayTabbedView(editor, tabbedView);
				bottomView.setScrollTabs(editor.getProperties().isScrollDocumentTabs());
				bottomView.disableChangeListener(true);
				tabbedViews.addElement(bottomView);

				DogsBayView view = tabbedView.getSelectedView();
				if (view != null) {
					tabbedView.remove(view);
					bottomView.add(view, view.getDocument().getName());
				} else {
					// Selected tab is a custom panel (Preview, Diff, etc.)
					Component selected = tabbedView.getSelectedComponent();
					if (selected == null) {
						tabbedViews.removeElement(bottomView);
						tabbedView.disableChangeListener(false);
						return;
					}
					int idx = tabbedView.getSelectedTabIndex();
					String title = tabbedView.getTabTitleAt(idx);
					tabbedView.removeTab(selected);
					bottomView.addCustomPanel(selected, title, title);
				}

				JSplitPane newSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabbedView, bottomView);
				newSplit.setResizeWeight(0.5);

				if (newSplit.getDividerSize() > 6) {
					newSplit.setDividerSize(6);
				}

				Object ui = newSplit.getUI();
				if (ui instanceof BasicSplitPaneUI) {
					((BasicSplitPaneUI) ui).getDivider().setBorder(null);
				}

				newSplit.setBorder(null);
				newSplit.setOneTouchExpandable(true);

				if (right) {
					split.setRightComponent(newSplit);
				} else {
					split.setLeftComponent(newSplit);
				}

				bottomView.setSelected(true);
				if (view != null) {
					view.setViewIcons();
				}

				bottomView.disableChangeListener(false);
			} else if (parentComponent instanceof JPanel) {
				JPanel panel = (JPanel) parentComponent;

				DogsBayTabbedView bottomView = new DogsBayTabbedView(editor, tabbedView);
				bottomView.setScrollTabs(editor.getProperties().isScrollDocumentTabs());
				bottomView.disableChangeListener(true);
				tabbedViews.addElement(bottomView);

				DogsBayView view = tabbedView.getSelectedView();
				if (view != null) {
					tabbedView.remove(view);
					bottomView.add(view, view.getDocument().getName());
				} else {
					// Selected tab is a custom panel (Preview, Diff, etc.)
					Component selected = tabbedView.getSelectedComponent();
					if (selected == null) {
						tabbedViews.removeElement(bottomView);
						tabbedView.disableChangeListener(false);
						return;
					}
					int idx = tabbedView.getSelectedTabIndex();
					String title = tabbedView.getTabTitleAt(idx);
					tabbedView.removeTab(selected);
					bottomView.addCustomPanel(selected, title, title);
				}

				JSplitPane newSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabbedView, bottomView);
				newSplit.setResizeWeight(0.5);

				if (newSplit.getDividerSize() > 6) {
					newSplit.setDividerSize(6);
				}

				Object ui = newSplit.getUI();
				if (ui instanceof BasicSplitPaneUI) {
					((BasicSplitPaneUI) ui).getDivider().setBorder(null);
				}

				newSplit.setBorder(null);
				newSplit.setOneTouchExpandable(true);

				panel.removeAll();
				panel.add(editor.getEditorToolbarPanel(), BorderLayout.NORTH);
				panel.add(newSplit, BorderLayout.CENTER);

				bottomView.setSelected(true);
				if (view != null) {
					view.setViewIcons();
				}
				bottomView.disableChangeListener(false);
			}

			tabbedView.disableChangeListener(false);
		}

		Vector views = getViews();
		if (views != null) {
			for (int i = 0; i < views.size(); i++) {
				DogsBayView view = (DogsBayView) views.elementAt(i);
				view.getEditor().scrollCursorToVisible();
			}
		}
	}

	public void unsplit() {
		if (tabbedViews.size() > 1) {
			DogsBayTabbedView parentTabbedView = null;
			DogsBayView selectedView = null;
			DogsBayTabbedView tabbedView = selectedTabbedView;
			int index = tabbedViews.indexOf(tabbedView);

			if (index > 0) {
				parentTabbedView = (DogsBayTabbedView) tabbedViews.elementAt(index - 1);

				selectedView = tabbedView.getSelectedView();
				if (selectedView == null) {
					selectedView = parentTabbedView.getSelectedView();
				}
			} else {
				// Make the next element the tabbedView instead ...
				tabbedView = (DogsBayTabbedView) tabbedViews.elementAt(1);
				// ... and the current the parent.
				parentTabbedView = selectedTabbedView;

				selectedView = parentTabbedView.getSelectedView();

				if (selectedView == null) {
					selectedView = tabbedView.getSelectedView();
				}
			}

			tabbedView.removeListeners();
			parentTabbedView.disableChangeListener(true);

			JSplitPane split = (JSplitPane) tabbedView.getParent();
			Component parentComponent = split.getParent();

			if (parentComponent instanceof JSplitPane) {
				JSplitPane parentSplit = (JSplitPane) parentComponent;
				boolean right = true;

				if (tabbedView == split.getLeftComponent()) {
					right = false;
				}

				Vector views = tabbedView.getViews();

				for (int i = 0; i < views.size(); i++) {
					DogsBayView view = (DogsBayView) views.elementAt(i);
					tabbedView.remove(view);

					parentTabbedView.add(view, view.getDocument().getName());
					view.setViewIcons();
				}

				if (split == parentSplit.getLeftComponent()) {
					if (right) {
						parentSplit.setLeftComponent(split.getLeftComponent());
					} else {
						parentSplit.setLeftComponent(split.getRightComponent());
					}
				} else {
					if (right) {
						parentSplit.setRightComponent(split.getLeftComponent());
					} else {
						parentSplit.setRightComponent(split.getRightComponent());
					}
				}
			} else if (parentComponent instanceof JPanel) {
				JPanel parentPanel = (JPanel) parentComponent;

				boolean right = true;

				if (tabbedView == split.getLeftComponent()) {
					right = false;
				}

				Vector views = tabbedView.getViews();

				for (int i = 0; i < views.size(); i++) {
					DogsBayView view = (DogsBayView) views.elementAt(i);
					tabbedView.remove(view);

					parentTabbedView.add(view, view.getDocument().getName());
					view.setViewIcons();
				}

				parentPanel.removeAll();
				parentPanel.add(editor.getEditorToolbarPanel(), BorderLayout.NORTH);

				if (right) {
					parentPanel.add(split.getLeftComponent(), BorderLayout.CENTER);
				} else {
					parentPanel.add(split.getRightComponent(), BorderLayout.CENTER);
				}

				parentPanel.revalidate();
				parentPanel.repaint();
			}

			tabbedViews.remove(tabbedView);

			parentTabbedView.disableChangeListener(false);
			parentTabbedView.select(selectedView);

			editor.getSplitTabsHorizontallyAction().setEnabled(selectedTabbedView.getViews().size() > 1 && !editor.isFullScreen());
			editor.getSplitTabsVerticallyAction().setEnabled(selectedTabbedView.getViews().size() > 1 && !editor.isFullScreen());
			editor.getUnsplitTabsAction().setEnabled(tabbedViews.size() > 1 && !editor.isFullScreen());
		}
	}
}
