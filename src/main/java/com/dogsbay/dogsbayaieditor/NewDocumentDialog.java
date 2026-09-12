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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.Vector;

import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.dogsbay.dogsbayaieditor.template.TemplateProperties;

/**
 * Simplified new document dialog with template-first design and single-click creation.
 *
 * Shows all templates sorted by Most Recently Used, followed by special options
 * (Blank XML, DTD, From Clipboard). User can single-click to create document immediately.
 */
public class NewDocumentDialog extends DogsBayDialog {
	private static final boolean DEBUG = false;
	private static final Dimension SIZE = new Dimension(400, 500);

	// Document type constants (for backward compatibility with NewAction)
	public static final int NEW_DEFAULT_XML_DOCUMENT 	= 0;
	public static final int NEW_TYPE_DOCUMENT			= 1;
	public static final int NEW_TEMPLATE_DOCUMENT		= 2;
	public static final int NEW_DTD_DOCUMENT 			= 3;
	public static final int NEW_FROM_CLIPBOARD_DOCUMENT = 4;

	private ConfigurationProperties properties = null;
	private JList documentsList = null;
	private DocumentListModel documentsModel = null;
	private DocumentItem selectedItem = null;
	private String clipboardData = null;

	/**
	 * Create the new document dialog.
	 *
	 * @param parent the parent frame
	 * @param props the configuration properties
	 */
	public NewDocumentDialog(JFrame parent, ConfigurationProperties props) {
		super(parent, true);

		this.properties = props;

		setResizable(false);
		setTitle("New XML Document");
		setDialogDescription("Select a template or document type:");

		JPanel main = new JPanel(new BorderLayout(0, 10));
		main.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		// Create the documents list
		documentsModel = new DocumentListModel();
		documentsList = new JList(documentsModel);
		documentsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		documentsList.setCellRenderer(new DocumentListRenderer());

		// Single-click handler
		documentsList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 1) {
					int index = documentsList.locationToIndex(e.getPoint());
					if (index >= 0) {
						DocumentItem item = (DocumentItem) documentsModel.getElementAt(index);
						if (item != null && !item.isSeparator()) {
							createDocumentAndClose(item);
						}
					}
				}
			}
		});

		// Enter key handler
		documentsList.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					DocumentItem item = (DocumentItem) documentsList.getSelectedValue();
					if (item != null && !item.isSeparator()) {
						createDocumentAndClose(item);
					}
				} else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					cancelled = true;
					setVisible(false);
				}
			}
		});

		JScrollPane scrollPane = new JScrollPane(documentsList);
		scrollPane.setPreferredSize(SIZE);

		main.add(scrollPane, BorderLayout.CENTER);

		setContentPane(main);
		pack();
		setLocationRelativeTo(parent);
	}

	/**
	 * Override setVisible to refresh the list before showing.
	 */
	@Override
	public void setVisible(boolean visible) {
		if (visible) {
			// Refresh the list with current templates and clipboard state
			documentsModel.refresh();

			// Select first item if available
			if (documentsModel.getSize() > 0) {
				// Skip separator if first item is separator
				if (((DocumentItem)documentsModel.getElementAt(0)).isSeparator()) {
					documentsList.setSelectedIndex(1);
				} else {
					documentsList.setSelectedIndex(0);
				}
			}

			selectedItem = null;
			cancelled = true;
		}

		super.setVisible(visible);
	}

	/**
	 * Create document from selected item and close dialog.
	 */
	private void createDocumentAndClose(DocumentItem item) {
		// Record usage for templates
		if (item.getType() == DocumentItem.TYPE_TEMPLATE && item.getTemplate() != null) {
			item.getTemplate().recordUsage();
			properties.save(); // Persist usage timestamp
		}

		selectedItem = item;
		cancelled = false;
		setVisible(false);
	}

	/**
	 * Get the type of document to create (for backward compatibility).
	 */
	public int getNewDocumentType() {
		if (selectedItem == null) {
			return NEW_DEFAULT_XML_DOCUMENT;
		}

		switch (selectedItem.getType()) {
			case DocumentItem.TYPE_TEMPLATE:
				return NEW_TEMPLATE_DOCUMENT;
			case DocumentItem.TYPE_BLANK:
				return NEW_DEFAULT_XML_DOCUMENT;
			case DocumentItem.TYPE_DTD:
				return NEW_DTD_DOCUMENT;
			case DocumentItem.TYPE_CLIPBOARD:
				return NEW_FROM_CLIPBOARD_DOCUMENT;
			default:
				return NEW_DEFAULT_XML_DOCUMENT;
		}
	}

	/**
	 * Get the selected template (for backward compatibility).
	 */
	public TemplateProperties getSelectedTemplate() {
		if (selectedItem != null && selectedItem.getType() == DocumentItem.TYPE_TEMPLATE) {
			return selectedItem.getTemplate();
		}
		return null;
	}

	/**
	 * Get the template URL (for backward compatibility).
	 */
	public URL getTemplateURL() {
		TemplateProperties template = getSelectedTemplate();
		return (template != null) ? template.getURL() : null;
	}

	/**
	 * Set clipboard data for "From Clipboard" option.
	 */
	public void setClipboardData(String data) {
		this.clipboardData = data;
	}

	/**
	 * Get clipboard data (for backward compatibility).
	 */
	public String getClipboardData() {
		return clipboardData;
	}

	/**
	 * Get selected grammar type (for backward compatibility).
	 * Returns null since "For Type" option was removed.
	 */
	public com.dogsbay.dogsbayaieditor.grammar.GrammarProperties getSelectedType() {
		return null;
	}

	/**
	 * Check if clipboard has text content.
	 */
	private boolean hasClipboardText() {
		try {
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
				String data = (String) clipboard.getData(DataFlavor.stringFlavor);
				if (data != null && !data.trim().isEmpty()) {
					setClipboardData(data);
					return true;
				}
			}
		} catch (Exception e) {
			// Clipboard access failed, ignore
		}
		return false;
	}

	// ========================================================================
	// Inner Classes
	// ========================================================================

	/**
	 * Represents an item in the documents list.
	 */
	private static class DocumentItem {
		static final int TYPE_TEMPLATE = 0;
		static final int TYPE_BLANK = 1;
		static final int TYPE_DTD = 2;
		static final int TYPE_CLIPBOARD = 3;
		static final int TYPE_SEPARATOR = -1;

		private TemplateProperties template;
		private String name;
		private int type;

		// Constructor for template item
		DocumentItem(TemplateProperties template) {
			this.template = template;
			this.name = template.getName();
			this.type = TYPE_TEMPLATE;
		}

		// Constructor for special item
		DocumentItem(String name, int type) {
			this.template = null;
			this.name = name;
			this.type = type;
		}

		public TemplateProperties getTemplate() {
			return template;
		}

		public String getName() {
			return name;
		}

		public int getType() {
			return type;
		}

		public boolean isSeparator() {
			return type == TYPE_SEPARATOR;
		}

		public boolean isRecentlyUsed() {
			return template != null && template.hasBeenUsed();
		}

		@Override
		public String toString() {
			return name;
		}
	}

	/**
	 * List model that combines templates and special items.
	 */
	private class DocumentListModel extends AbstractListModel {
		private Vector<DocumentItem> items = new Vector<>();

		public void refresh() {
			items.clear();

			// Add templates sorted by MRU
			Vector templates = properties.getTemplatesSortedByUsage();
			for (int i = 0; i < templates.size(); i++) {
				TemplateProperties template = (TemplateProperties) templates.elementAt(i);
				items.add(new DocumentItem(template));
			}

			// Add separator
			items.add(new DocumentItem("────────────────────", DocumentItem.TYPE_SEPARATOR));

			// Add special items
			items.add(new DocumentItem("Blank XML Document", DocumentItem.TYPE_BLANK));
			items.add(new DocumentItem("DTD Document", DocumentItem.TYPE_DTD));

			// Add clipboard option if clipboard has text
			if (hasClipboardText()) {
				items.add(new DocumentItem("Document From Clipboard", DocumentItem.TYPE_CLIPBOARD));
			}

			fireContentsChanged(this, 0, items.size());
		}

		@Override
		public int getSize() {
			return items.size();
		}

		@Override
		public Object getElementAt(int index) {
			if (index >= 0 && index < items.size()) {
				return items.elementAt(index);
			}
			return null;
		}
	}

	/**
	 * Custom cell renderer for document items.
	 */
	private class DocumentListRenderer extends JLabel implements ListCellRenderer {
		public DocumentListRenderer() {
			setOpaque(true);
			setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		}

		@Override
		public Component getListCellRendererComponent(
				JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {

			DocumentItem item = (DocumentItem) value;

			if (item.isSeparator()) {
				setText(item.getName());
				setEnabled(false);
				setFont(list.getFont().deriveFont(9.0f));
			} else {
				String text = item.getName();

				// Add star for recently used templates
				if (item.isRecentlyUsed()) {
					text = "⭐ " + text;
				}

				// Add icon prefix based on type
				if (item.getType() == DocumentItem.TYPE_TEMPLATE) {
					text = "📄 " + text;
				} else if (item.getType() == DocumentItem.TYPE_BLANK) {
					text = "📄 " + text;
				} else if (item.getType() == DocumentItem.TYPE_DTD) {
					text = "📄 " + text;
				} else if (item.getType() == DocumentItem.TYPE_CLIPBOARD) {
					text = "📋 " + text;
				}

				setText(text);
				setEnabled(true);
				setFont(list.getFont());
			}

			// Selection colors
			if (isSelected && !item.isSeparator()) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			} else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}

			return this;
		}
	}
}
