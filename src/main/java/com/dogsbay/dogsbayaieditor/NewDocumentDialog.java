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

	/** The extension of the file being made, or null when there is no file yet (File ▸ New). */
	private String forExtension = null;

	/** A template's root element, remembered so the list does not read the same files each time it opens. */
	private static final java.util.Map<String, String> ROOTS = new java.util.concurrent.ConcurrentHashMap<>();
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

			if (documentsModel.getSize() > 0) {
				documentsList.setSelectedIndex(documentsModel.preferredIndex());
			}

			selectedItem = null;
			cancelled = true;
		}

		super.setVisible(visible);
	}

	/**
	 * Show only the templates that can make this kind of file.
	 *
	 * <p>Offering a concept to someone naming a map, and pre-selecting it, is worse than offering nothing: the highlighted
	 * row reads as a recommendation. The file name already says what is being made.
	 *
	 * @param filename the name of the file being created, or null for no filtering.
	 */
	public void setForFile(String filename) {
		forExtension = extensionOf(filename);
	}

	/** The extension, lower case and without the dot, or null. */
	static String extensionOf(String filename) {
		if (filename == null || !filename.contains(".")) {
			return null;
		}
		String extension = filename.substring(filename.lastIndexOf('.') + 1).trim().toLowerCase();
		return extension.isEmpty() ? null : extension;
	}

	/**
	 * Whether a template can make a file with this extension.
	 *
	 * <p>Filtering only hides what is positively wrong: a topic template when a map is being named, or a map template when a
	 * topic is. Everything else is offered. A first version worked the other way round, listing the extensions each root
	 * could make, which hid every template from anyone naming an {@code .xsl}, {@code .xsd}, {@code .svg} or {@code .rng} —
	 * a worse fault than the one it fixed, and invisible to whoever hit it, since an empty list looks like having no
	 * templates.
	 */
	static boolean suits(TemplateProperties template, String extension) {
		if (extension == null || template == null) {
			return true;
		}
		String root = rootOf(template);
		if (root == null) {
			return true;
		}
		String name = localName(root);
		// A filter and a rule set are neither maps nor topics, but we know exactly where each belongs — enough to keep
		// them out of the map and topic lists, which is all this needs to decide.
		boolean placedExactly = "val".equals(name) || "schema".equals(name);
		Boolean makesMaps = makesMaps(root);
		if (makesMaps == null && !placedExactly) {
			return true; // a root we cannot place, including anyone's own specialisation
		}
		return switch (extension) {
			case "ditamap", "bookmap" -> Boolean.TRUE.equals(makesMaps);
			case "dita" -> Boolean.FALSE.equals(makesMaps);
			case "ditaval" -> "val".equals(name);
			case "sch" -> "schema".equals(name);
			// Everything else keeps the rule that matters: hide only what is positively wrong. rules.xml is an
			// ordinary name for a Schematron file, and a DITAVAL saved as .xml is not wrong either.
			default -> true;
		};
	}

	/**
	 * Whether a root element makes maps, makes topics, or is not something this can place.
	 *
	 * @return true for a map, false for a topic, null when it is neither or unknown.
	 */
	static Boolean makesMaps(String root) {
		String name = localName(root);
		if (name == null || name.isEmpty()) {
			return null;
		}
		if (name.equals("map") || name.equals("bookmap") || name.equals("subjectScheme")
				|| name.endsWith("Map") || name.endsWith("Bookmap")) {
			return Boolean.TRUE;
		}
		return switch (name) {
			case "topic", "concept", "task", "reference", "glossentry", "glossgroup", "troubleshooting", "learningBase" ->
				Boolean.FALSE;
			default -> null; // a specialisation, a schema, an SVG: not ours to judge
		};
	}

	/** The element name without its namespace prefix. */
	static String localName(String root) {
		if (root == null) {
			return null;
		}
		int colon = root.indexOf(':');
		return colon < 0 ? root : root.substring(colon + 1);
	}

	/** The root element named in a template's document type declaration, or its first element. */
	static String rootOf(TemplateProperties template) {
		URL url = template.getURL();
		if (url == null) {
			return null;
		}
		String found = ROOTS.computeIfAbsent(url.toString(), key -> {
			try (java.io.InputStream in = url.openStream()) {
				String head = new String(in.readNBytes(4096), java.nio.charset.StandardCharsets.UTF_8);
				java.util.regex.Matcher doctype =
					java.util.regex.Pattern.compile("<!DOCTYPE\\s+([A-Za-z_][\\w.:-]*)").matcher(head);
				if (doctype.find()) {
					return doctype.group(1);
				}
				String withoutProlog = head.replaceAll("<\\?[^?]*\\?>", "").replaceAll("(?s)<!--.*?-->", "");
				java.util.regex.Matcher element =
					java.util.regex.Pattern.compile("<([A-Za-z_][\\w.:-]*)").matcher(withoutProlog);
				return element.find() ? element.group(1) : "";
			} catch (Exception unreadable) {
				return "";
			}
		});
		return found.isEmpty() ? null : found;
	}

	/** Everything {@link #setVisible} does except showing the window, so a test can set the list up without one. */
	void prepareForTest() {
		documentsModel.refresh();
		if (documentsModel.getSize() > 0) {
			documentsList.setSelectedIndex(documentsModel.preferredIndex());
		}
		selectedItem = null;
		cancelled = true;
	}

	/** The template names the list is offering, for a test that checks the filtering end to end. */
	java.util.List<String> templateNamesForTest() {
		java.util.List<String> names = new java.util.ArrayList<>();
		for (int i = 0; i < documentsModel.getSize(); i++) {
			DocumentItem item = (DocumentItem) documentsModel.getElementAt(i);
			if (item.getType() == DocumentItem.TYPE_TEMPLATE) {
				names.add(item.toString());
			}
		}
		return names;
	}

	/** Whether the highlighted row is the separator, which is never a choice. */
	boolean highlightedIsSeparatorForTest() {
		DocumentItem item = highlighted();
		return item != null && item.isSeparator();
	}

	/** Choose the blank document, as clicking it would. */
	void chooseBlankForTest() {
		for (int i = 0; i < documentsModel.getSize(); i++) {
			DocumentItem item = (DocumentItem) documentsModel.getElementAt(i);
			if (item.getType() == DocumentItem.TYPE_BLANK) {
				selectedItem = item;
				cancelled = false;
				return;
			}
		}
	}

	/** The row the list is showing as highlighted. */
	DocumentItem highlighted() {
		return (DocumentItem) documentsList.getSelectedValue();
	}

	/** What was actually chosen, which is what the callers read. */
	DocumentItem chosenForTest() {
		return selectedItem;
	}

	/** Press OK without a button to press. */
	void pressOkForTest() {
		okButtonPressed();
	}

	/**
	 * OK acts on whatever is highlighted.
	 *
	 * <p>Without this, the highlighted item was only remembered when it was double-clicked or chosen with Enter, because
	 * those are the two paths that ran {@link #createDocumentAndClose}. The base dialog's OK simply closed, leaving nothing
	 * chosen, and nothing chosen reads as the blank default — so picking a template and clicking OK quietly produced an
	 * empty document. The list highlights a row as soon as the dialog opens, which made it look as though a choice had been
	 * made.
	 *
	 * <p>Nothing highlighted leaves the dialog open rather than closing on a choice the user did not make.
	 */
	@Override
	protected void okButtonPressed() {
		DocumentItem item = (DocumentItem) documentsList.getSelectedValue();
		if (item == null || item.isSeparator()) {
			return;
		}
		createDocumentAndClose(item);
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
	 * The plainest document for an extension, or null when the extension has no single plainest one.
	 *
	 * <p>A {@code .ditamap} is a map. Bookmap and subject scheme are maps too, and they sort before it alphabetically,
	 * so the dialog opened on Bookmap and a reader who pressed Enter got the wrong document — the commonest case
	 * costing the most attention. A {@code .dita} is deliberately absent: concept, task and reference are all
	 * first-class, and none of them is the one to guess.
	 */
	static String plainestRootFor(String extension) {
		if (extension == null) {
			return null;
		}
		return switch (extension) {
			case "ditamap" -> "map";
			case "bookmap" -> "bookmap";
			case "ditaval" -> "val";
			case "sch" -> "schema";
			default -> null;
		};
	}

	/**
	 * List model that combines templates and special items.
	 */
	private class DocumentListModel extends AbstractListModel {
		private Vector<DocumentItem> items = new Vector<>();

		public void refresh() {
			items.clear();

			// Add templates sorted by MRU, skipping the ones that cannot make this kind of file
			Vector templates = properties.getTemplatesSortedByUsage();
			for (int i = 0; i < templates.size(); i++) {
				TemplateProperties template = (TemplateProperties) templates.elementAt(i);
				if (suits(template, forExtension)) {
					items.add(new DocumentItem(template));
				}
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

		/**
		 * Which item the dialog should open on.
		 *
		 * <p>What someone used last is the best guess there is, so a template with a usage time keeps the top spot.
		 * Only when none of them has ever been used does the extension decide, which is exactly the case this is for:
		 * a reader meeting the dialog for the first time.
		 */
		int preferredIndex() {
			int first = -1;
			for (int i = 0; i < items.size(); i++) {
				if (!items.elementAt(i).isSeparator()) {
					first = i;
					break;
				}
			}
			if (first < 0) {
				return 0;
			}
			TemplateProperties firstTemplate = items.elementAt(first).getTemplate();
			if (firstTemplate != null && firstTemplate.hasBeenUsed()) {
				return first;
			}
			String plainest = plainestRootFor(forExtension);
			if (plainest != null) {
				for (int i = 0; i < items.size(); i++) {
					TemplateProperties template = items.elementAt(i).getTemplate();
					if (template != null && plainest.equals(localName(rootOf(template)))) {
						return i;
					}
				}
			}
			return first;
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
