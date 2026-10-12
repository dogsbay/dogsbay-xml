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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import com.dogsbay.dogsbayaieditor.git.BranchSwitcherPopup;
import com.dogsbay.dogsbayaieditor.git.GitPanel;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

/**
 * The status bar for the DogsBayAIEditor application.
 *
 * @version $Revision: 1.7 $, $Date: 2004/07/21 09:10:24 $
 * @author Dogsbay
 */
public class Statusbar extends JPanel {
	private static final boolean DEBUG = false;

	public static final String VALIDATOR_TYPE_SCHEMA = "XSD";
	public static final String VALIDATOR_TYPE_DTD = "DTD";

	public static final String VALIDATION_LOCATION_INTERNAL = "INT";
	public static final String VALIDATION_LOCATION_EXTERNAL = "EXT";

	public static final String DOCUMENT_STATUS_VALID = "VAL";
	public static final String DOCUMENT_STATUS_ERROR = "ERR";
	public static final String DOCUMENT_STATUS_WELLFORMED = "WF";
	public static final String DOCUMENT_STATUS_UNKNOWN = "UNK";

	private static final ImageIcon ERROR_STATUS_ICON = DogsBayImageLoader.get()
			.getImage("com/dogsbay/dogsbayaieditor/icons/ErrorStatusIcon.gif");
	private static final ImageIcon UNKNOWN_STATUS_ICON = DogsBayImageLoader.get()
			.getImage("com/dogsbay/dogsbayaieditor/icons/UnknownStatusIcon.gif");
	private static final ImageIcon VALID_STATUS_ICON = DogsBayImageLoader.get()
			.getImage("com/dogsbay/dogsbayaieditor/icons/ValidStatusIcon.gif");
	private static final ImageIcon WELLFORMED_STATUS_ICON = DogsBayImageLoader.get()
			.getImage("com/dogsbay/dogsbayaieditor/icons/WellformedStatusIcon.gif");

	private static final String CTRLX = "Ctrl-X";

	private static final Border BEVEL_BORDER = new CompoundBorder(
			new EmptyBorder(0, 2, 0, 0),
			new CompoundBorder(
					// new BevelBorder( BevelBorder.LOWERED, Color.white, new Color( 204, 204, 204),
					// new Color( 204, 204, 204), new Color( 102, 102, 102)),
					new BevelBorder(BevelBorder.LOWERED, UIManager.getColor("controlHighlight"), UIManager.getColor("control"),
							UIManager.getColor("control"), UIManager.getColor("controlDkShadow")),
					new EmptyBorder(0, 2, 0, 0)));

	private JLabel statusLabel = null;
	private JLabel positionLabel = null;
	private JLabel locationLabel = null;
	private JLabel validatorLabel = null;
	private JLabel typeLabel = null;
	private StatusSegment branchSegment = null;
	private JPanel westPanel = null;
	private JPanel pluginItems = null;
	private final java.util.Map<String, javax.swing.JComponent> pluginItemsById =
			new java.util.HashMap<>();
	private JTextField modeField = null;
	private JTextField statusField = null;

	private InputMap modeMap = new InputMap();
	private InputMap currentMap = null;
	private DogsBayAIEditor parent = null;

	/**
	 * The constructor for the about dialog.
	 *
	 * @param frame the parent frame.
	 */
	public Statusbar(DogsBayAIEditor _parent) {
		super(new BorderLayout());

		this.parent = _parent;
		JPanel flowPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));

		statusLabel = new JLabel();
		statusLabel.setForeground(UIManager.getColor("Label.foreground"));
		statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN));
		statusLabel.setBorder(BEVEL_BORDER);
		statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
		statusLabel.setVerticalAlignment(SwingConstants.CENTER);
		statusLabel.setPreferredSize(new Dimension(24, 16));

		positionLabel = new JLabel("Ln 1 Col 1");
		positionLabel.setFont(positionLabel.getFont().deriveFont(Font.PLAIN));
		positionLabel.setForeground(UIManager.getColor("Label.foreground"));
		positionLabel.setPreferredSize(new Dimension(120, 16));
		positionLabel.setHorizontalAlignment(SwingConstants.CENTER);
		positionLabel.setBorder(BEVEL_BORDER);
		positionLabel.setText("");

		locationLabel = new JLabel("WWW");
		locationLabel.setFont(locationLabel.getFont().deriveFont(Font.PLAIN));
		locationLabel.setForeground(UIManager.getColor("Label.foreground"));
		locationLabel.setBorder(BEVEL_BORDER);
		locationLabel.setHorizontalAlignment(JLabel.CENTER);
		locationLabel.setPreferredSize(new Dimension(locationLabel.getPreferredSize().width, 16));
		locationLabel.setText("");

		validatorLabel = new JLabel();
		validatorLabel.setFont(validatorLabel.getFont().deriveFont(Font.PLAIN));
		validatorLabel.setForeground(UIManager.getColor("Label.foreground"));
		validatorLabel.setPreferredSize(locationLabel.getPreferredSize());
		validatorLabel.setHorizontalAlignment(JLabel.CENTER);
		validatorLabel.setBorder(BEVEL_BORDER);
		validatorLabel.setText("");

		typeLabel = new JLabel();
		typeLabel.setFont(typeLabel.getFont().deriveFont(Font.PLAIN));
		typeLabel.setForeground(UIManager.getColor("Label.foreground"));
		typeLabel.setPreferredSize(new Dimension(200, 16));
		typeLabel.setBorder(BEVEL_BORDER);
		typeLabel.setText("");

		modeField = new JTextField(" Ctrl-X ");
		modeField.setFocusable(false);
		modeField.setEditable(false);
		modeField.setFont(modeField.getFont().deriveFont(Font.PLAIN));
		modeField.setForeground(UIManager.getColor("Label.foreground"));
		modeField.setBorder(BEVEL_BORDER);
		modeField.setBackground(getBackground());
		modeField.setPreferredSize(new Dimension(modeField.getPreferredSize().width, 16));
		modeField.setText("");

		// if Ctrl-X caught then return back to current editor
		KeyStroke ctrlX = KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK, false);
		Action modeAction = new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				if (parent.getView() != null) {
					parent.getView().getEditor().setFocus();
				} else {
					parent.setIntialFocus();
				}
			}
		};

		modeMap.put(ctrlX, "MODE");
		modeField.getActionMap().put("MODE", modeAction);

		// rememeber the current map
		currentMap = modeField.getInputMap();

		FocusListener fs = new FocusListener() {
			public void focusGained(FocusEvent e) {
				modeField.setText(CTRLX);
				modeField.setInputMap(JComponent.WHEN_FOCUSED, modeMap);
			}

			public void focusLost(FocusEvent e) {
				modeField.setText("");
				modeField.setInputMap(JComponent.WHEN_FOCUSED, currentMap);
			}
		};

		modeField.addFocusListener(fs);

		statusField = new JTextField();
		statusField.setFont(statusField.getFont().deriveFont(Font.PLAIN));
		statusField.setBorder(null);
		statusField.setEditable(false);
		statusField.setBackground(getBackground());

		branchSegment = new StatusSegment("🔀", "Git branch — click to switch");
		branchSegment.setOnClick(this::showBranchSwitcher);
		branchSegment.setValue(null, "No branch");

		flowPanel.add(modeField);
		flowPanel.add(typeLabel);
		flowPanel.add(validatorLabel);
		flowPanel.add(locationLabel);
		flowPanel.add(statusLabel);
		flowPanel.add(positionLabel);

		// Plugin-contributed status-bar items (e.g. the DITA deliverable selector)
		// live here, added via UIService.addStatusBarItem.
		pluginItems = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

		westPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		westPanel.add(branchSegment);
		westPanel.add(pluginItems);

		// Breathing room so the message isn't cramped against the context strip.
		statusField.setBorder(new EmptyBorder(0, 12, 0, 0));

		this.add(statusField, BorderLayout.CENTER);
		this.add(westPanel, BorderLayout.WEST);
		this.add(flowPanel, BorderLayout.EAST);

		setBorder(new EmptyBorder(2, 2, 0, 0));
	}

	// public void setError( boolean enabled) {
	// if ( enabled) {
	// errorLabel.setText( "ERROR");
	// } else {
	// errorLabel.setText( "");
	// }
	// }

	/**
	 * adds an action to the emacs editing mode input\action map
	 *
	 * @param actionName The action name
	 * @param stroke     The KeyStroke to invoke the action
	 * @param action     The action
	 */
	public void addToModeMap(String actionName, KeyStroke stroke, Action action) {
		modeMap.put(stroke, actionName);
		modeField.getActionMap().put(actionName, action);
	}

	public JTextField getModeField() {
		return modeField;
	}

	public void setModeFocusable(boolean condition) {
		if (condition) {
			modeField.setFocusable(true);
		} else {
			modeField.setFocusable(false);
		}
	}

	public void setStatus(String status) {
		if (DEBUG)
			System.out.println("Statusbar.setStatus( " + status + ")");
		statusField.setText(status);
		statusField.setCaretPosition(0);
	}

	public void setDocumentStatus(String status) {
		if (DEBUG)
			System.out.println("Statusbar.setDocumentStatus( " + status + ")");
		if (status == DOCUMENT_STATUS_VALID) {
			statusLabel.setIcon(VALID_STATUS_ICON);
		} else if (status == DOCUMENT_STATUS_ERROR) {
			statusLabel.setIcon(ERROR_STATUS_ICON);
		} else if (status == DOCUMENT_STATUS_WELLFORMED) {
			statusLabel.setIcon(WELLFORMED_STATUS_ICON);
		} else if (status == DOCUMENT_STATUS_UNKNOWN) {
			statusLabel.setIcon(UNKNOWN_STATUS_ICON);
		} else {
			statusLabel.setIcon(null);
		}
	}

	public void setValidator(String validator) {
		if (DEBUG)
			System.out.println("Statusbar.setValidator( " + validator + ")");
		validatorLabel.setText(validator);
	}

	public void setLocation(String location) {
		if (DEBUG)
			System.out.println("Statusbar.setLocation( " + location + ")");
		locationLabel.setText(location);
	}

	public void setType(String type) {
		if (DEBUG)
			System.out.println("Statusbar.setType( " + type + ")");
		typeLabel.setText(type);
	}

	public void setPosition(int line, int col) {
		positionLabel.setText("Ln " + line + " Col " + col);
	}

	public void clearPosition() {
		positionLabel.setText("");
	}

	public void setBranch(String branch) {
		if (DEBUG)
			System.out.println("Statusbar.setBranch( " + branch + ")");
		// Always-present segment with a muted "No branch" placeholder when there's none.
		if (branchSegment != null) {
			branchSegment.setValue(branch, "No branch");
		}
	}

	public void clearBranch() {
		if (DEBUG)
			System.out.println("Statusbar.clearBranch()");
		setBranch(null);
	}

	/** Host the project switcher as the leading status-bar segment (before branch). */
	public void setProjectSwitcher(java.awt.Component switcher) {
		if (westPanel == null || switcher == null) {
			return;
		}
		westPanel.remove(switcher); // idempotent: don't stack duplicates if called again
		westPanel.add(switcher, 0);
		westPanel.revalidate();
		westPanel.repaint();
	}

	/** Muted foreground for empty-state placeholders ("No branch" etc.); LaF-null-safe. */
	public static java.awt.Color placeholderForeground() {
		java.awt.Color c = UIManager.getColor("Label.disabledForeground");
		if (c == null) {
			c = UIManager.getColor("textInactiveText");
		}
		return c != null ? c : java.awt.Color.GRAY;
	}

	/** Normal foreground for a real value in a status-bar segment; LaF-null-safe. */
	public static java.awt.Color valueForeground() {
		java.awt.Color c = UIManager.getColor("Label.foreground");
		return c != null ? c : java.awt.Color.DARK_GRAY;
	}

	private void showBranchSwitcher() {
		// No git repo → nothing to switch (covers the "No branch" placeholder case).
		GitPanel gitPanel = parent.getGitPanel();
		if (gitPanel == null || gitPanel.getGit() == null) {
			return;
		}

		BranchSwitcherPopup popup = new BranchSwitcherPopup(gitPanel);
		popup.show(branchSegment, 0, -popup.getPreferredSize().height);
	}

	/** Contribute a persistent status-bar component (plugin-owned), tracked by id. */
	public void addPluginItem(String id, javax.swing.JComponent item) {
		removePluginItem(id);
		pluginItemsById.put(id, item);
		pluginItems.add(item);
		pluginItems.revalidate();
		pluginItems.repaint();
	}

	/** Remove a plugin-contributed status-bar item by id. No-op if absent. */
	public void removePluginItem(String id) {
		javax.swing.JComponent existing = pluginItemsById.remove(id);
		if (existing != null) {
			pluginItems.remove(existing);
			pluginItems.revalidate();
			pluginItems.repaint();
		}
	}
}
