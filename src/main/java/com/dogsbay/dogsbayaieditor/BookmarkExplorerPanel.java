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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.net.URL;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.bounce.event.PopupListener;

import com.dogsbay.xml.editor.Bookmark;
import com.dogsbay.dogsbayaieditor.component.ScrollableListPanel;
import com.dogsbay.dogsbayaieditor.properties.ConfigurationProperties;
import com.dogsbay.dogsbayaieditor.properties.TextPreferences;

/**
 * Explorer panel for managing bookmarks in the left sidebar.
 */
public class BookmarkExplorerPanel extends ViewTreePanel {
	private static final ImageIcon DELETE_ICON = DogsBayImageLoader.get().getImage("com/dogsbay/xml/editor/icons/Delete16.gif");
	private static final EmptyBorder NO_FOCUS_BORDER = new EmptyBorder(1, 1, 1, 1);

	private DogsBayAIEditor parent = null;
	private ConfigurationProperties properties = null;
	private JList list = null;
	private BookmarkListModel model = null;
	private DeleteAction deleteAction = null;
	private GotoAction gotoAction = null;
	private JPopupMenu popup = null;
	private JButton deleteButton = null;

	public BookmarkExplorerPanel(DogsBayAIEditor parent, ConfigurationProperties properties) {
		super(new BorderLayout());

		this.parent = parent;
		this.properties = properties;

		model = new BookmarkListModel(new Vector(properties.getBookmarks()));
		list = new JList(model);
		list.setCellRenderer(new BookmarkCellRenderer());

		// Keyboard bindings
		deleteAction = new DeleteAction();
		gotoAction = new GotoAction();

		list.getActionMap().put("deleteAction", deleteAction);
		list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0, false), "deleteAction");

		list.getActionMap().put("gotoAction", gotoAction);
		list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "gotoAction");

		// Single-click navigation
		list.addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				if (!e.getValueIsAdjusting()) {
					int index = list.getSelectedIndex();
					if (index != -1) {
						bookmarkSelected();
					}
				}
			}
		});

		// Right-click context menu
		list.addMouseListener(new PopupListener() {
			public void popupTriggered(MouseEvent e) {
				firePopupTriggered(e);
			}
		});

		// Toolbar
		JToolBar toolbar = new JToolBar();
		toolbar.setFloatable(false);
		toolbar.setRollover(true);
		toolbar.setBorder(new EmptyBorder(2, 2, 2, 2));

		// Title label
		JLabel titleLabel = new JLabel("Bookmarks");
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 11f));
		titleLabel.setBorder(new EmptyBorder(0, 2, 0, 0));
		toolbar.add(titleLabel);
		toolbar.add(javax.swing.Box.createHorizontalGlue());

		deleteButton = new JButton(deleteAction);
		deleteButton.setText(null);
		deleteButton.setIcon(DELETE_ICON);
		deleteButton.setToolTipText("Delete Bookmark(s)");
		toolbar.add(deleteButton);

		// Scroll pane
		JScrollPane scroller = new JScrollPane(new ScrollableListPanel(list),
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroller.getViewport().setBackground(list.getBackground());

		add(toolbar, BorderLayout.NORTH);
		add(scroller, BorderLayout.CENTER);
	}

	public void addBookmark(Bookmark bookmark) {
		model.addBookmark(bookmark);
		properties.addBookmark(bookmark);
	}

	public void removeBookmark(Bookmark bookmark) {
		model.removeBookmark(bookmark);
		properties.removeBookmark(bookmark);
	}

	private void bookmarkSelected() {
		final Bookmark bookmark = model.getBookmark(list.getSelectedIndex());

		if (bookmark.getLineNumber() != -1) {
			parent.getOutputPanel().setLocked(true);

			if (bookmark.getDocument() != null) {
				parent.select(bookmark.getDocument());
				parent.switchToEditor();
				parent.getView().getEditor().selectLineWithoutEnd(bookmark.getLineNumber() + 1);
				parent.getOutputPanel().setLocked(false);
			} else {
				parent.setWait(true);
				parent.setStatus("Opening ...");

				Runnable runner = new Runnable() {
					public void run() {
						try {
							parent.open(new URL(bookmark.getURL()), null, true);
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							parent.setStatus("Done");
							parent.setWait(false);

							SwingUtilities.invokeLater(new Runnable() {
								public void run() {
									parent.switchToEditor();
									parent.getView().getEditor().selectLineWithoutEnd(bookmark.getLineNumber() + 1);
									parent.getOutputPanel().setLocked(false);
								}
							});
						}
					}
				};

				Thread thread = new Thread(runner);
				thread.start();
			}
		}
	}

	protected void firePopupTriggered(MouseEvent event) {
		// Select the item under the mouse if not already selected
		int index = list.locationToIndex(event.getPoint());
		if (index != -1 && !list.isSelectedIndex(index)) {
			list.setSelectedIndex(index);
		}

		if (popup == null) {
			popup = new JPopupMenu();
			popup.add(gotoAction);
			popup.addSeparator();
			popup.add(deleteAction);
		}

		popup.show(list, event.getX(), event.getY());
	}

	// ViewTreePanel overrides (list-based, not tree)
	public void expandAll() { /* no-op */ }
	public void collapseAll() { /* no-op */ }

	// ViewPanel overrides
	public void setFocus() {
		list.requestFocus();
	}

	public void updatePreferences() {
		if (list != null) {
			list.setFont(TextPreferences.getBaseFont().deriveFont((float) 12));
		}
	}

	public void setProperties() { /* no-op */ }

	// Inner classes

	class BookmarkListModel extends AbstractListModel {
		Vector bookmarks = null;

		public BookmarkListModel(Vector list) {
			bookmarks = new Vector();

			for (int i = 0; i < list.size(); i++) {
				Bookmark bm = (Bookmark) list.elementAt(i);
				insertSorted(bm);
			}
		}

		private void insertSorted(Bookmark bm) {
			int index = -1;

			for (int j = 0; j < bookmarks.size() && index == -1; j++) {
				Bookmark other = (Bookmark) bookmarks.elementAt(j);
				int cmp = bm.getName().compareToIgnoreCase(other.getName());
				if (cmp < 0) {
					index = j;
				} else if (cmp == 0) {
					if (bm.getLineNumber() < other.getLineNumber()) {
						index = j;
					}
				}
			}

			if (index != -1) {
				bookmarks.insertElementAt(bm, index);
			} else {
				bookmarks.addElement(bm);
			}
		}

		public int getSize() {
			return bookmarks != null ? bookmarks.size() : 0;
		}

		public void removeBookmark(Bookmark bookmark) {
			int index = bookmarks.indexOf(bookmark);
			if (index >= 0) {
				bookmarks.removeElement(bookmark);
				fireIntervalRemoved(this, index, index);
			}
		}

		public int addBookmark(Bookmark bookmark) {
			int sizeBefore = bookmarks.size();
			insertSorted(bookmark);
			int index = bookmarks.indexOf(bookmark);
			fireIntervalAdded(this, index, index);
			return index;
		}

		public Object getElementAt(int i) {
			return bookmarks.elementAt(i);
		}

		public Bookmark getBookmark(int i) {
			return (Bookmark) bookmarks.elementAt(i);
		}
	}

	class DeleteAction extends AbstractAction {
		public DeleteAction() {
			super("Delete Bookmark(s)");
		}

		public void actionPerformed(ActionEvent e) {
			int[] indexes = list.getSelectedIndices();
			for (int i = indexes.length - 1; i >= 0; i--) {
				Bookmark bm = model.getBookmark(indexes[i]);
				removeBookmark(bm);
				bm.setRemoved(true);

				DogsBayView view = parent.getView();
				if (view != null) {
					view.revalidate();
					view.repaint();
				}
			}
		}
	}

	class GotoAction extends AbstractAction {
		public GotoAction() {
			super("Goto Bookmark");
		}

		public void actionPerformed(ActionEvent e) {
			bookmarkSelected();
		}
	}

	class BookmarkCellRenderer extends JPanel implements ListCellRenderer {
		private JLabel icon = null;
		private JLabel line = null;
		private JLabel document = null;
		private JLabel content = null;
		private JPanel westPanel = null;

		public BookmarkCellRenderer() {
			super(new BorderLayout());

			westPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			westPanel.setOpaque(false);

			icon = new JLabel();

			line = new JLabel();
			line.setBorder(new EmptyBorder(0, 2, 0, 2));
			line.setOpaque(false);
			line.setFont(line.getFont().deriveFont(Font.PLAIN));
			line.setForeground(Color.black);

			document = new JLabel();
			document.setBorder(new EmptyBorder(0, 2, 0, 2));
			document.setOpaque(false);
			document.setFont(document.getFont().deriveFont(Font.PLAIN));
			document.setForeground(Color.black);

			content = new JLabel();
			content.setOpaque(false);
			content.setFont(content.getFont().deriveFont(Font.PLAIN));
			content.setForeground(Color.black);

			westPanel.add(icon);
			westPanel.add(document);
			westPanel.add(line);

			this.add(westPanel, BorderLayout.WEST);
			this.add(content, BorderLayout.CENTER);
		}

		public void setPreferredFont(Font font) {
			document.setFont(font.deriveFont(Font.BOLD));
			line.setFont(font.deriveFont(Font.PLAIN));
			content.setFont(font.deriveFont(Font.PLAIN));
		}

		public Component getListCellRendererComponent(JList list, Object node, int index, boolean selected, boolean focus) {
			if (node instanceof Bookmark) {
				Bookmark b = (Bookmark) node;

				String extension = URLUtilities.getExtension(b.getName());
				if (extension == null) {
					extension = "";
				}

				icon.setIcon(IconFactory.getIconForExtension(extension));
				document.setText(b.getName());
				line.setText("[" + (b.getLineNumber() + 1) + "]");
				content.setText(b.getContent().trim());
				setToolTipText(b.getURL() + " [" + (b.getLineNumber() + 1) + "]");
			}

			if (selected) {
				document.setForeground(list.getSelectionForeground());
				line.setForeground(list.getSelectionForeground());
				content.setForeground(list.getSelectionForeground());
				this.setBackground(list.getSelectionBackground());
			} else {
				document.setForeground(list.getForeground());
				line.setForeground(list.getForeground());
				content.setForeground(list.getForeground());
				this.setBackground(list.getBackground());
			}

			setEnabled(list.isEnabled());
			setPreferredFont(list.getFont());
			setBorder(focus ? UIManager.getBorder("List.focusCellHighlightBorder") : NO_FOCUS_BORDER);

			return this;
		}
	}
}
