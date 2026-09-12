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
import java.awt.Dimension;
import java.io.IOException;
import java.util.Vector;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;

import org.xml.sax.SAXParseException;

import com.dogsbay.xml.XMLError;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;

import com.dogsbay.dogsbayaieditor.terminal.TerminalContainerPanel;

/**
 * The panel that shows the different outputs.
 *
 * @version $Revision: 1.13 $, $Date: 2005/09/05 13:55:11 $
 * @author Dogsbay
 */
public class OutputPanel extends JPanel {
	private DogsBayAIEditor parent = null;
	private ErrorPane errorPane = null;
	private ErrorPane projectErrorPane = null;
	private JTabbedPane tabPane = null;
	// FindInFilesResults removed — functionality covered by SearchPanel
	// XPathResults removed — functionality covered by XPathQueryPanel in sidebar
	private TerminalContainerPanel terminalContainer = null;
	private boolean locked = false;
	private ConfigurationProperties properties = null;

	/**
	 * The constructor for the about dialog.
	 *
	 * @param frame the parent frame.
	 */
	public OutputPanel(DogsBayAIEditor parent, ConfigurationProperties properties) {
		super(new BorderLayout());

		this.parent = parent;
		this.properties = properties;

		setBorder(new BevelBorder(BevelBorder.LOWERED, Color.white, UIManager.getColor("control"),
				UIManager.getColor("control"), UIManager.getColor("controlDkShadow")));

		tabPane = new JTabbedPane();
		add(tabPane, BorderLayout.CENTER);

		tabPane.addTab("Errors", createParseTab());
		tabPane.addTab("Terminal", createTerminalTab());
		tabPane.addTab("Project Validation", createProjectTab());

		setMinimumSize(new Dimension(0, 30));
	}

	/**
	 * Returns the tabbed pane so plugins can add tabs.
	 */
	public JTabbedPane getTabbedPane() {
		return tabPane;
	}

	public void setCurrent(Object view) {
		errorPane.setCurrent(view);
	}

	public void startCheck(final String id, final String text) {
		if (SwingUtilities.isEventDispatchThread()) {
			errorPane.startCheck(text);
		} else {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					errorPane.startCheck(text);
				}
			});
		}
	}

	/*
	 * public void startSchematronCheck( final String id, final String text) {
	 * if ( SwingUtilities.isEventDispatchThread()) {
	 * schematronErrorPane.startCheck( text);
	 * } else {
	 * SwingUtilities.invokeLater( new Runnable() {
	 * public void run() {
	 * schematronErrorPane.startCheck( text);
	 * }
	 * });
	 * }
	 * }
	 */

	public void endCheck(final String id, final String text) {
		if (SwingUtilities.isEventDispatchThread()) {
			errorPane.endCheck(text);
		} else {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					errorPane.endCheck(text);
				}
			});
		}
	}

	/*
	 * public void endSchematronCheck( final String id, final String text) {
	 * if ( SwingUtilities.isEventDispatchThread()) {
	 * schematronErrorPane.endCheck( text);
	 * } else {
	 * SwingUtilities.invokeLater( new Runnable() {
	 * public void run() {
	 * schematronErrorPane.endCheck( text);
	 * }
	 * });
	 * }
	 * }
	 */

	public void addError(final String id, final XMLError e) {
		if (SwingUtilities.isEventDispatchThread()) {
			errorPane.addError(e);
		} else {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					errorPane.addError(e);
				}
			});
		}
	}

	public void addErrorSortedByLineNumber(final String id, final XMLError e) {
		if (SwingUtilities.isEventDispatchThread()) {
			errorPane.addErrorSortedByLineNumber(e);
		} else {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					errorPane.addErrorSortedByLineNumber(e);
				}
			});
		}
	}

	public void sortErrorListByLineNumber() {
		errorPane.sortErrorsByLineNumber();
	}

	/*
	 * public void addSchematronError( final String id, final XMLError e) {
	 * if ( SwingUtilities.isEventDispatchThread()) {
	 * schematronErrorPane.addError( e);
	 * } else {
	 * SwingUtilities.invokeLater( new Runnable() {
	 * public void run() {
	 * schematronErrorPane.addError( e);
	 * }
	 * });
	 * }
	 * }
	 */

	public void setErrorList(ErrorList errors) {
		errorPane.setErrorList(errors);
	}

	public void setError(String id, IOException e) {
		addError(id, new XMLError(e));
	}

	public void selectError(XMLError error) {
		selectParseTab();
		errorPane.select(error);
	}

	public void setError(String id, SAXParseException e) {
		addError(id, new XMLError(e, XMLError.ERROR));
	}

	public void updatePreferences() {
		errorPane.updatePreferences();
	}

	public void clearErrors() {
		errorPane.clear();
	}

	public void setXPathResults(Vector results) {
		// No-op — XPath Results tab removed; use XPathQueryPanel in sidebar instead
	}

	public void setXPathList(XPathList results) {
		// No-op — XPath Results tab removed; use XPathQueryPanel in sidebar instead
	}

	public void setLocked(boolean enabled) {
		locked = enabled;
	}

	public boolean isLocked() {
		return locked;
	}

	private JPanel createTerminalTab() {
		terminalContainer = new TerminalContainerPanel(parent);
		terminalContainer.setMinimumSize(new Dimension(0, 0));
		return terminalContainer;
	}

	public void selectTerminalTab() {
		if (!locked) {
			// Look the tab up by name rather than by index: removing the Scripting
			// tab shifted every index after it, and plugins add tabs at the end.
			int i = tabPane.indexOfTab("Terminal");
			if (i >= 0) {
				tabPane.setSelectedIndex(i);
			}
		}
	}

	public void startFindInFiles(String text) {
		// No-op — Find in Files tab removed; use SearchPanel instead
	}

	public void finishFindInFiles() {
		// No-op — Find in Files tab removed; use SearchPanel instead
	}

	public void addFindInFiles(Vector matches) {
		// No-op — Find in Files tab removed; use SearchPanel instead
	}

	public void selectXPathTab() {
		// No-op — XPath Results tab removed; use XPathQueryPanel in sidebar instead
	}

	public void selectFindInFilesTab() {
		// No-op — Find in Files tab removed; use SearchPanel instead
	}

	public void selectParseTab() {
		if (!locked) {
			int errors = tabPane.indexOfTab("Errors");
			int project = tabPane.indexOfTab("Project Validation");
			// Don't steal focus from Project Validation or from plugin-contributed
			// tabs, which sit after it. Anchored on the tab name, not a literal
			// index, so adding or removing a core tab cannot silently re-target it.
			if (errors >= 0 && (project < 0 || tabPane.getSelectedIndex() < project)) {
				tabPane.setSelectedIndex(errors);
			}
		}
	}

	private JPanel createParseTab() {
		JPanel panel = new JPanel(new BorderLayout());

		errorPane = new ErrorPane(parent);
		// JScrollPane scroller = new JScrollPane( errorPane);

		panel.add(errorPane, BorderLayout.CENTER);
		panel.setMinimumSize(new Dimension(0, 0));

		return panel;
	}

	private JPanel createProjectTab() {
		JPanel panel = new JPanel(new BorderLayout());
		projectErrorPane = new ProjectErrorPane(parent);
		panel.add(projectErrorPane, BorderLayout.CENTER);
		panel.setMinimumSize(new Dimension(0, 0));
		return panel;
	}

	// ── Project-wide validation results ─────────────────────────────────
	// A separate pane from "Errors": opening a result's file re-runs the
	// per-document well-formedness check (which clears the "Errors" pane), so
	// project results must live elsewhere to survive navigation.

	public void startProjectCheck(final String text) {
		if (SwingUtilities.isEventDispatchThread()) {
			projectErrorPane.startCheck(text);
		} else {
			SwingUtilities.invokeLater(() -> projectErrorPane.startCheck(text));
		}
	}

	public void addProjectError(final XMLError e) {
		if (SwingUtilities.isEventDispatchThread()) {
			projectErrorPane.addError(e);
		} else {
			SwingUtilities.invokeLater(() -> projectErrorPane.addError(e));
		}
	}

	public void endProjectCheck(final String text) {
		if (SwingUtilities.isEventDispatchThread()) {
			projectErrorPane.endCheck(text);
		} else {
			SwingUtilities.invokeLater(() -> projectErrorPane.endCheck(text));
		}
	}

	public void selectProjectTab() {
		if (!locked) {
			int i = tabPane.indexOfTab("Project Validation");
			if (i >= 0) {
				tabPane.setSelectedIndex(i);
			}
		}
	}

		/**
	 * @return Returns the errorPane.
	 */
	public ErrorPane getErrorPane() {

		return errorPane;
	}

	/**
	 * @param errorPane The errorPane to set.
	 */
	public void setErrorPane(ErrorPane errorPane) {

		this.errorPane = errorPane;
	}
}
