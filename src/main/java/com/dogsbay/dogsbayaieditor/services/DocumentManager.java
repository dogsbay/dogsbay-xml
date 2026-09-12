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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Vector;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.xml.sax.SAXParseException;

import com.dogsbay.schema.SchemaDocument;
import com.dogsbay.xml.DogsBayDocument;
import com.dogsbay.xml.DogsBayURLUtilities;
import com.dogsbay.xml.editor.Bookmark;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayTabbedView;
import com.dogsbay.dogsbayaieditor.DogsBayView;
import com.dogsbay.dogsbayaieditor.FileUtilities;
import com.dogsbay.dogsbayaieditor.MessageHandler;
import com.dogsbay.dogsbayaieditor.URLUtilities;
import com.dogsbay.dogsbayaieditor.component.AutomaticProgressMonitor;
import com.dogsbay.dogsbayaieditor.grammar.GrammarProperties;
import com.dogsbay.dogsbayaieditor.services.events.DocumentClosedEvent;
import com.dogsbay.dogsbayaieditor.services.events.DocumentOpenedEvent;

/**
 * Manages document lifecycle operations: opening, closing, saving sessions,
 * and application exit. Extracted from DogsBayAIEditor to reduce its size.
 */
public class DocumentManager {

	private static final boolean DEBUG = false;

	private final DogsBayAIEditor editor;
	private int newDocumentCounter = 0;

	public DocumentManager(DogsBayAIEditor editor) {
		this.editor = editor;
	}

	/**
	 * Gets the currently active document, or null if none is open.
	 */
	public DogsBayDocument getActiveDocument() {
		return editor.getDocument();
	}

	/**
	 * Gets the new document counter value.
	 */
	public int getNewDocumentCounter() {
		return newDocumentCounter;
	}

	/**
	 * Opens a document from a URL with optional progress monitoring.
	 */
	public void open(URL url, GrammarProperties type, boolean monitorProgress) {
		AutomaticProgressMonitor monitor = null;

		if (DEBUG)
			System.out.println("open(URL url, GrammarProperties type, boolean monitorProgress)");

		if (monitorProgress) {
			monitor = new AutomaticProgressMonitor(editor, null, "Opening \"" + URLUtilities.toString(url) + "\".", 250);
		}
		editor.setVisible(true);

		boolean unrecoverableError = false;
		boolean alreadyLoaded = false;
		DogsBayDocument document = null;

		try {
			if (!editor.getProperties().isMultipleDocumentOccurrences()) {
				Vector views = editor.getViews();

				System.out.println("=== DUPLICATE CHECK: opening url=" + url.toString());
				for (int i = 0; i < views.size(); i++) {
					DogsBayView view = (DogsBayView) views.elementAt(i);

					// file already in editor?
					URL viewURL = view.getDocument().getURL();
					System.out.println("  view[" + i + "] url=" + (viewURL != null ? viewURL.toString() : "null")
						+ " match=" + (viewURL != null && viewURL.toString().equals(url.toString())));
					if (viewURL != null
							&& viewURL.toString().equals(url.toString())) {
						editor.select(view);
						alreadyLoaded = true;
					}
				}
				System.out.println("  alreadyLoaded=" + alreadyLoaded);
			}
			if (!alreadyLoaded) {
				if (monitor != null) {
					monitor.start();
				}

				document = new DogsBayDocument(url);
				document.load(editor.getProperties());
			}
			// set the document somewhere....
		} catch (SAXParseException spe) {
			// This is returned from the document, do not report???
			// spe.printStackTrace();
		} catch (IOException ie) {
			if (ie instanceof UnsupportedEncodingException) {
				MessageHandler.showError(
						"Could not open " + url.getFile() + "\nUnsupported Encoding: " + ie.getMessage(),
						"Document Creation Error");
			} else {
				MessageHandler.showError("Could not open " + url.getFile(), ie, "Document Creation Error");
			}

			unrecoverableError = true;
		} catch (Exception e) {
			// System.out.println( "*** ERRROR FOUND!")
			MessageHandler.showError("Could not open Document.", e, "Document Creation Error");
			e.printStackTrace();
		} finally {
			if (monitor != null && !monitor.isCanceled()) {
				// the document is loaded, stop monitoring!
				monitor.stop();
			}

			if (!alreadyLoaded && !unrecoverableError && (monitor == null || !monitor.isCanceled())) {
				if (url.getProtocol().equals("file")) {
					editor.getProperties().setLastOpenedDocument(url.getFile());
				} else {
					editor.getProperties().setLastOpenedURL(url);
				}

				open(document, type);
			}
		}
	}

	/**
	 * Opens an already-loaded document, determining its type and schema.
	 */
	public void open(DogsBayDocument document, GrammarProperties type) {
		if (DEBUG)
			System.out.println("open( DogsBayDocument document, GrammarProperties type)");

		if (editor.getProperties().isCheckTypeOnOpening()) {
			type = FileUtilities.getType(document, type);
		} else {
			type = null;
		}

		Vector tagCompletionSchemas = FileUtilities.createTagCompletionSchemas(document, type);

		// end result can either be a selected type or no type selected
		// get schema/dtd...
		open(document, null, tagCompletionSchemas, type);
	}

	/**
	 * Opens a document with explicit schema and tag completion schemas.
	 */
	public void open(DogsBayDocument document, SchemaDocument schema, Vector tagCompletionSchemas,
			GrammarProperties type) {
		if (DEBUG)
			System.out.println(
					"open( DogsBayDocument document, SchemaDocument schema, Vector tagCompletionSchemas, GrammarProperties type)");

		if (document.getURL() == null) {
			document.setName("New Document " + (newDocumentCounter + 1));
			newDocumentCounter++;
		}

		editor.createView(document, schema, tagCompletionSchemas, type);

		editor.getEventBus().publish(new DocumentOpenedEvent(document));

		if (editor.getProperties().isValidateOnOpening()
				&& !document.isError()
				&& document.isXML()) {
			editor.getValidateAction().execute();
		}

		// make sure this runs after the gui is updated!
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				System.gc();
			}
		});
	}

	/**
	 * Closes all open documents, prompting to save changes.
	 */
	public void closeAll() {
		Vector views = editor.getViews();

		int result = 0;
		// possible results can be
		// CANCEL, YES(Save), NO(Dont save), NO TO ALL(Dont save any)

		for (int i = 0; i < views.size(); i++) {
			DogsBayView view = (DogsBayView) views.elementAt(i);
			view.setProperties();

			result = closeNotThreaded(view, result, (views.size() - i));

			if (result == MessageHandler.CONFIRM_CANCEL_OPTION) {
				break;
			}

		}
	}

	/**
	 * Closes a view without threading (used during closeAll).
	 */
	public int closeNotThreaded(DogsBayView view, int previousResult, int numberOfViews) {

		int result = MessageHandler.CONFIRM_YES_OPTION;
		DogsBayDocument doc = view.getDocument();
		if (previousResult == MessageHandler.CONFIRM_NO_TO_ALL_OPTION) {
			result = previousResult;
		}

		if ((view.isChanged() == true) && (previousResult != MessageHandler.CONFIRM_NO_TO_ALL_OPTION)) {
			if (numberOfViews > 1) {
				result = MessageHandler.showConfirmYesNoNoToAll(editor, "Save changes to " + doc.getName() + "?");
			} else {
				result = MessageHandler.showConfirmCancel(editor, "Save changes to " + doc.getName() + "?");
			}

			if (result == MessageHandler.CONFIRM_YES_OPTION) {
				// Flush THIS view, not the active one. closeAll() walks every view
				// without selecting each in turn, so closing a background tab used
				// to push the active tab's buffer into the model and then save this
				// document from its stale text — losing everything typed in it.
				// DogsBayTabbedView already guards the single-tab path by selecting
				// first; this path never got the same treatment.
				view.updateModel();

				if (doc.isReadOnly() || doc.getURL() == null) {
					GrammarProperties type = editor.getGrammar();
					File file = null;

					if (type != null) {
						file = FileUtilities.selectOutputFile((File) null, FileUtilities.getExtension(type));
					} else {
						file = FileUtilities.selectOutputFile((File) null, "xml");
					}

					if (file != null) {
						try {
							URL url = DogsBayURLUtilities.getURLFromFile(file);

							try {
								doc.setURL(url);
								doc.save();

								editor.getProperties().setLastOpenedDocument(url.getFile());
								editor.setDocument(doc);
								editor.getChangeManager().discardAllEdits();
								finishClose(view);
								return (result);
							} catch (IOException ex) {
								ex.printStackTrace();
								MessageHandler.showError("Could not save Document.", ex, "Saving Error");
								return (MessageHandler.CONFIRM_CANCEL_OPTION);
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					} else {
						return (MessageHandler.CONFIRM_CANCEL_OPTION);
					}
				} else { // ( doc.isReadOnly() || doc.getURL() == null)
					try {
						doc.save();
					} catch (IOException x) {
						MessageHandler.showError(editor, "Could not save " + doc.getName() + "\n" + x.getMessage(),
								"Save Error");
						return (MessageHandler.CONFIRM_CANCEL_OPTION);
					}
				}
			}
		}

		if (result != MessageHandler.CONFIRM_CANCEL_OPTION) {
			finishClose(view);
			return (result);
		}

		return (MessageHandler.CONFIRM_CANCEL_OPTION);
	}

	/**
	 * Closes a single document view with save prompt (threaded save).
	 */
	public boolean close(final DogsBayView view) {
		int result = JOptionPane.YES_OPTION;
		final DogsBayDocument document = view.getDocument();

		if (view.isChanged()) {
			result = JOptionPane.showConfirmDialog(editor, "Save changes to " + document.getName() + "?",
					"Please Confirm", JOptionPane.YES_NO_CANCEL_OPTION);

			if (result == JOptionPane.YES_OPTION) {
				// This view, not the active one — see closeNotThreaded above.
				view.updateModel();

				if (document.isReadOnly() || document.getURL() == null) {
					GrammarProperties type = editor.getGrammar();
					File file = null;

					if (type != null) {
						file = FileUtilities.selectOutputFile((File) null, FileUtilities.getExtension(type));
					} else {
						file = FileUtilities.selectOutputFile((File) null, "xml");
					}

					if (file != null) {
						try {
							final URL url = DogsBayURLUtilities.getURLFromFile(file);

							editor.setWait(true);
							editor.setStatus("Saving ...");

							// Run in Thread!!!
							Runnable runner = new Runnable() {
								public void run() {
									URL oldURL = document.getURL();
									boolean saved = false;

									try {
										document.setURL(url);

										document.save();
										saved = true;
									} catch (IOException ex) {
										ex.printStackTrace();
										document.setURL(oldURL);
										MessageHandler.showError("Could not save Document.", ex, "Saving Error");
									} catch (Exception ex) {
										ex.printStackTrace();
										document.setURL(oldURL);
									} finally {
										editor.setStatus("Done");
										editor.setWait(false);
									}

									// Only finish the close when the save actually succeeded —
									// otherwise the tab would close and edits would be discarded
									// despite the write failing (data loss).
									if (saved) {
										editor.getProperties().setLastOpenedDocument(url.getFile());

										SwingUtilities.invokeLater(new Runnable() {
											public void run() {
												editor.setDocument(document);
												editor.getChangeManager().discardAllEdits();
												finishClose(view);
											}
										});
									}
								}
							};

							// Create and start the thread ...
							Thread thread = new Thread(runner);
							thread.start();

							return true;
						} catch (Exception e) {
							e.printStackTrace();
						}
					} else {
						return false;
					}
				} else { // ( doc.isReadOnly() || doc.getURL() == null)
					try {
						document.save();
					} catch (IOException x) {
						JOptionPane.showMessageDialog(editor,
								"Could not save " + document.getName() + "\n" + x.getMessage(), "Save Error",
								JOptionPane.ERROR_MESSAGE);
						return false;
					}
				}
			}
		}

		if (result != JOptionPane.CANCEL_OPTION) {
			finishClose(view);
			return true;
		}

		return false;
	}

	/**
	 * Completes the close operation: updates bookmarks and removes the view.
	 */
	private void finishClose(DogsBayView view) {
		DogsBayDocument closedDoc = view.getDocument();

		if (view.getDocument().getURL() == null) {
			Vector bookmarks = view.getEditor().getBookmarks();

			for (int i = 0; i < bookmarks.size(); i++) {
				editor.removeBookmark((Bookmark) bookmarks.elementAt(i));
			}
		} else {
			view.updateBookmarks();
		}

		removeView(view);

		editor.getEventBus().publish(new DocumentClosedEvent(closedDoc));

		// make sure this runs after the gui is updated!
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				System.gc();
			}
		});
	}

	/**
	 * Removes a view from the tabbed pane.
	 */
	public void removeView(DogsBayView view) {
		Vector tabbedViews = editor.getTabbedViews();

		for (int i = 0; i < tabbedViews.size(); i++) {
			DogsBayTabbedView tabbedView = (DogsBayTabbedView) tabbedViews.elementAt(i);

			if (tabbedView.contains(view)) {
				tabbedView.remove(view);

				view.cleanup();

				if (tabbedView.getViews().size() == 0) {
					if (editor.isFullScreen()) {
						editor.toggleFullScreen();
					}

					editor.unsplit();
				}

				editor.setView((DogsBayView) editor.getSelectedTabbedView().getSelectedView());
				return;
			}
		}
	}

	/**
	 * Exits the application: saves session, closes all docs, saves properties.
	 */
	public void exit() {
		// Save currently open documents for session restoration
		if (editor.getProperties().isReopenSessionFiles()) {
			saveCurrentSession();
		}

		closeAll();

		if (editor.getViews().size() == 0) {
			editor.getHelper().setProperties();

			saveProperties();

			editor.getProperties().setDimension(editor.getSize());

			int rightSplitDividerLocation = editor.getRightSplitDividerLocation();
			int splitDividerLocation = editor.getSplitDividerLocation();

			if (rightSplitDividerLocation != -1
					&& editor.getRightSplit().getDividerLocation() >= editor.getRightSplit().getMaximumDividerLocation()) {
				editor.getProperties().setDividerLocation(rightSplitDividerLocation);
			} else {
				editor.getProperties().setDividerLocation(editor.getRightSplit().getDividerLocation());
			}

			// Persist the left-sidebar divider only when it's a usable width; a
			// collapsed/garbage value (≈0, or the transient negative seen during
			// teardown) would blank the sidebar on next launch. Prefer the live
			// location, fall back to the pre-collapse one, else leave the saved value.
			int liveTop = editor.getSplit().getDividerLocation();
			if (liveTop >= 40) {
				editor.getProperties().setTopDividerLocation(liveTop);
			} else if (splitDividerLocation >= 40) {
				editor.getProperties().setTopDividerLocation(splitDividerLocation);
			}

			// Save the right sidebar's WIDTH, not the divider's position: a
			// position restored into a different window size squeezes the panel,
			// and saving that squeeze on the next exit makes it permanent. A
			// collapsed panel is not remembered at all.
			if (editor.getMainContentSplit() != null) {
				var rightSplit = editor.getMainContentSplit();
				int width = com.dogsbay.dogsbayaieditor.RightPanelWidth.widthToSave(rightSplit.getWidth(),
						rightSplit.getDividerSize(), rightSplit.getDividerLocation());
				if (width > 0) {
					editor.getProperties().setRightPanelWidth(width);
				}
			}

			editor.getProperties().setPosition(editor.getLocation());
			editor.getProperties().save();
			editor.getProperties().saveToDisk();

			// PHASE 3: Debugger disabled, always exit
			// if (!debugger.isVisible()) {
			System.exit(0);
			// } else {
			// setVisible(false);
			// }
			return;
		}
	}

	/**
	 * Saves the list of currently open documents to the session.
	 * This allows them to be reopened on next application start.
	 */
	public void saveCurrentSession() {
		Vector views = editor.getViews();
		Vector sessionPaths = new Vector();

		for (int i = 0; i < views.size(); i++) {
			DogsBayView view = (DogsBayView) views.elementAt(i);
			DogsBayDocument doc = view.getDocument();

			if (doc != null && doc.getURL() != null) {
				URL url = doc.getURL();

				// Only save file-based documents (not remote URLs or unsaved docs)
				if (url.getProtocol().equals("file")) {
					sessionPaths.addElement(url.getFile());
				}
			}
		}

		editor.getProperties().setSessionDocuments(sessionPaths);

		// Save the active document path so it can be restored as the selected tab
		String activeDocPath = null;
		DogsBayView currentView = editor.getView();
		if (currentView != null && currentView.getDocument() != null) {
			URL activeURL = currentView.getDocument().getURL();
			if (activeURL != null && "file".equals(activeURL.getProtocol())) {
				activeDocPath = activeURL.getFile();
			}
		}
		editor.getProperties().setSessionActiveDocument(activeDocPath);
	}

	/**
	 * Saves configuration properties to disk.
	 */
	public void saveProperties() {
		// Currently a no-op placeholder for future property saving logic.
		// The original implementation was commented out.
	}
}
