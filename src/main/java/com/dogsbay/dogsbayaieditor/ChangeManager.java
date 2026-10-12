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

import java.util.EventListener;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;
import javax.swing.undo.AbstractUndoableEdit;

//import com.dogsbay.xml.grid.UndoableGridEditAbstact;
import com.dogsbay.dogsbayaieditor.author.UndoableAuthorEdit;
import com.dogsbay.dogsbayaieditor.plugins.UndoablePluginEditAbstact;

/**
 * Handles undo events and actions.
 *
 * @version	$Revision: 1.9 $, $Date: 2005/06/08 13:38:32 $
 * @author Dogsbay
 */
public class ChangeManager extends UndoManager {
	private static final boolean DEBUG = false;
	
	private EventListenerList listeners = null;

	private UndoableEdit syncMark = null;
	private UndoableEdit saveMark = null;
	private UndoableEdit parseMark = null;
	private boolean validated = false;
	private UndoableEdit validateMark = null;

	private CompoundEdit compoundEdit = null;

	/**
	 * The constructor for the undo handler.
	 */
	public ChangeManager() {
		listeners = new EventListenerList();
	}
	
	// Mark the Synchronisation between model and text...
	public void markSync() {
		if (DEBUG) System.out.println( "ChangeManager.markSync()");

		syncMark = editToBeUndone();
		fireStateChanged( new ChangeEvent( this));
	}

	public void markSave() {
		if (DEBUG) System.out.println( "ChangeManager.markSave()");

		saveMark = editToBeUndone();
		fireStateChanged( new ChangeEvent( this));
	}

	public void markParse() {
		if (DEBUG) System.out.println( "ChangeManager.markParse()");

		parseMark = editToBeUndone();
		fireStateChanged( new ChangeEvent( this));
	}

	public void markValid() {
		if (DEBUG) System.out.println( "ChangeManager.markParse()");

		validateMark = editToBeUndone();
		
		validated = true;
		fireStateChanged( new ChangeEvent( this));
	}

	public void startCompound( boolean significant) {
		if (DEBUG) System.out.println( "ChangeManager.startCompound( "+significant+")");

		if ( syncing) {
			return;
		}

		if ( significant) {
			compoundEdit = new CompoundEdit();
		} else {
			compoundEdit = new InsignificantCompoundEdit();
		}
	}

	public void endCompound() {
		if (DEBUG) System.out.println( "ChangeManager.endCompound()");

		if ( compoundEdit == null) {
			return;
		}

		compoundEdit.end();

		if ( (editToBeUndone() instanceof UndoablePluginEditAbstact) && compoundEdit.isSignificant()) {
			super.discardAllEdits();
		}
		
		super.addEdit( compoundEdit);
	
		compoundEdit = null;
		
		fireStateChanged( new ChangeEvent( this));
	}

	public void discardCompound() {
		if (DEBUG) System.out.println( "ChangeManager.discardCompound()");
		compoundEdit = null;
	}

	public boolean isCompound() {
		if (DEBUG) System.out.println( "ChangeManager.isCompound()");
		return compoundEdit != null;
	}

	public synchronized boolean addEdit( UndoableEdit edit) {
		if (DEBUG) System.out.println( "ChangeManager.addEdit( "+edit+")");
		if ( syncing) {
			// a view being re-synced after undo/redo is not a user edit, and
			// recording one here would discard everything still redoable
			return false;
		}
		boolean result = false;
		validated = false;
		
		if ( compoundEdit == null) {
			if (DEBUG) System.out.println( "super.addEdit( "+edit+")");

			if ( edit instanceof UndoablePluginEditAbstact && !(editToBeUndone() instanceof UndoablePluginEditAbstact)) {
				super.discardAllEdits();
			} else if ( !(edit instanceof UndoablePluginEditAbstact) && editToBeUndone() instanceof UndoablePluginEditAbstact) {
				super.discardAllEdits();
			}

			// Author-view and text edits share one history: undoing across the
			// boundary re-syncs the other side (see DogsBayView.synchroniseAfterUndo).

			result = super.addEdit( edit);
		
			fireStateChanged( new ChangeEvent( this));
		} else {
			result = compoundEdit.addEdit( edit);
		}
		
		return result;
	}
	
	/**
	 * Adds a dummy undoable edit to make sure the change manager knows 
	 * the content has changed but this cannot be undone.
	 * 
	 * @return boolean ...
	 */
	public synchronized boolean addNotUndoableEdit() {
		return addEdit( new NotUndoableEdit());
	}

	public synchronized void redo() {
		if (DEBUG) System.out.println( "ChangeManager.redo()");

		// Safety check to prevent CannotRedoException
		if (!canRedo()) {
			return;
		}

		commitPendingViewEdits();
		UndoableEdit redone = editToBeRedone();
		super.redo();
		validated = false;
		synchroniseViews( redone);

		fireStateChanged( new ChangeEvent( this));
	}

	public void discardAllEdits() {
		if (DEBUG) System.out.println( "ChangeManager.discardAllEdits()");
		super.discardAllEdits();
		
		syncMark = null;
		saveMark = null;
		parseMark = null;
		validateMark = null;
		validated = false;
	
		compoundEdit = null;
		
		fireStateChanged( new ChangeEvent( this));
	}

	/**
	 * Undo the last change.
	 */
	public synchronized void undo() {
		if (DEBUG) System.out.println( "ChangeManager.undo()");

		// Safety check to prevent CannotUndoException
		if (!canUndo()) {
			return;
		}

		commitPendingViewEdits();
		UndoableEdit undone = editToBeUndone();
		super.undo();
		validated = false;
		synchroniseViews( undone);

		fireStateChanged( new ChangeEvent( this));
	}

	/**
	 * Brings the views that did not make the change back in line with it: an
	 * Author edit is exported to the document for the XML side, a text edit is
	 * parsed for the Author side. Nothing done here is recorded as an edit.
	 */
	private void synchroniseViews( UndoableEdit edit) {
		if ( edit == null || viewSynchroniser == null) {
			return;
		}
		syncing = true;
		try {
			viewSynchroniser.accept( edit);
		} catch ( RuntimeException failed) {
			failed.printStackTrace();
		} finally {
			syncing = false;
		}
	}

	/**
	 * Text a view is still holding must become its own edit before the undo
	 * runs. Committed inside the sync it would be dropped (syncing), applied to
	 * the model and unundoable — typed text that could never be taken back.
	 */
	private void commitPendingViewEdits() {
		if ( pendingEditCommitter == null || syncing) {
			return;
		}
		try {
			pendingEditCommitter.run();
		} catch ( RuntimeException failed) {
			failed.printStackTrace();
		}
	}

	/** Installed by the view: flushes text a view has typed but not yet committed. */
	public void setPendingEditCommitter( Runnable committer) {
		this.pendingEditCommitter = committer;
	}

	private Runnable pendingEditCommitter;
	private boolean syncing;
	private java.util.function.Consumer<UndoableEdit> viewSynchroniser;

	/** Installed by the view: re-syncs the other views after an undo or redo. */
	public void setViewSynchroniser( java.util.function.Consumer<UndoableEdit> synchroniser) {
		this.viewSynchroniser = synchroniser;
	}

	/** Whether a post-undo view re-sync is running, so edits it causes are not recorded. */
	public boolean isSyncing() {
		return syncing;
	}
	
	/**
	 * Check to see if the last undo item was a significant text change.
	 *
	 * @return true when the text has been changed.
	 */
	public boolean isTextChanged() {
		boolean result = false;
		UndoableEdit undo = editToBeUndone();
		
		// see if the last undoable edit was a significant text edit???
//		if ( undo != syncMark && undo != parseMark && undo != saveMark && !(undo instanceof UndoableDesignerEdit)) {
		if ( undo != parseMark && !(undo instanceof UndoablePluginEditAbstact)
				&& !(undo instanceof UndoableAuthorEdit)) {
			result = true;
		}

		if (DEBUG) System.out.println( "ChangeManager.isTextChanged() ["+result+"]");
		
		return result;
	}

	/**
	 * Check to see if the last undo item was a significant model change.
	 *
	 * @return true when the model has been changed.
	 */
	public boolean isModelChanged() {
		boolean result = false;
		UndoableEdit undo = editToBeUndone();
		
		// see if the last undoable edit was a significant text edit???
		
		if (DEBUG) System.out.println( "ChangeManager.isModelChanged() ["+result+"]");

		return result;
	}

	/**
	 * Check to see if the last undo item was a significant change.
	 *
	 * @return true when there has been a change.
	 */
	public boolean isChanged() {
		boolean result = false;
		UndoableEdit undo = editToBeUndone();
		
		// see if the last undoable edit was a significant text edit???
		if ( undo != saveMark) {
			result = true;
		}
		
		if (DEBUG) System.out.println( "ChangeManager.isChanged() ["+result+"]");

		return result;
	}

//	/**
//	 * Check to see if the current info has been parsed.
//	 *
//	 * @return true when the current info is parsed.
//	 */
//	public boolean isParsed() {
//		boolean result = false;
//		UndoableEdit undo = editToBeUndone();
//		
//		// see if the last undoable edit was a significant text edit???
//		if ( undo != null && (undo == parseMark || undo instanceof UndoableDesignerEdit)) {
//			result = true;
//		} else if ( undo == null && parseMark == null) {
//			result = true;
//		}
//		
//		if (DEBUG) System.out.println( "ChangeManager.isParsed() ["+result+"]");
//
//		return result;
//	}

	public boolean isValidated() {
		boolean result = false;
		UndoableEdit undo = editToBeUndone();
		
		// see if the last undoable edit was a significant text edit???
		if ( undo != null && undo == validateMark) {
			result = true;
		} else if ( undo == null && validated) {
			result = true;
		}
		
		if (DEBUG) System.out.println( "ChangeManager.isValid() ["+result+"]");

		return result;
	}

	public boolean isPluginRedo() {
		return (editToBeRedone() instanceof UndoablePluginEditAbstact);
	}

	public boolean isGridRedo() {
		return (editToBeRedone() instanceof UndoablePluginEditAbstact);
	}

	/*public boolean isGridUndo() {
		return (editToBeUndone() instanceof UndoablePluginEditAbstact);
	}*/
	public boolean isPluginUndo() {
		return (editToBeUndone() instanceof UndoablePluginEditAbstact);
	}

	/** Whether the next undo is an Author-view edit, which must not drag the user to the XML source. */
	public boolean isAuthorUndo() {
		return (editToBeUndone() instanceof UndoableAuthorEdit);
	}

	/** Whether the next redo is an Author-view edit. */
	public boolean isAuthorRedo() {
		return (editToBeRedone() instanceof UndoableAuthorEdit);
	}

	/** 
	 * Adds a change listener to the list of listeners.
	 *
	 * @param the change listener.
	 */
	public void addChangeListener( ChangeListener listener) {
		listeners.add( (Class)listener.getClass(), listener);
	}

	/** 
	 * Removes a change listener from the list of listeners.
	 *
	 * @param the change listener.
	 */
	public void removeChangeListener( ChangeListener listener) {
		if ( listeners != null) {
			listeners.remove( (Class)listener.getClass(), listener);
		}
	}

	/** 
	 * Notifies the listeners about a popup trigger on a node.
	 *
	 * @param the mouse event.
	 * @param the node.
	 */
	protected void fireStateChanged( ChangeEvent event) {
		// Guaranteed to return a non-null array
		Object[] list = listeners.getListenerList();
		
		// Process the listeners last to first, notifying
		// those that are interested in this event
		for ( int i = list.length-2; i >= 0; i -= 2) {
			((ChangeListener)list[i+1]).stateChanged( event);
		}
	}
	
	protected void removeAllListeners() {
		// Guaranteed to return a non-null array
		Object[] list = listeners.getListenerList();
		
		for ( int i = list.length-2; i >= 0; i -= 2) {
			listeners.remove( (Class)list[i], (EventListener)list[i+1]);
		}
	}

	public void cleanup() {
		finalize();
	}
	
	protected void finalize() {
		removeAllListeners();

		listeners = null;

		syncMark = null;
		saveMark = null;
		parseMark = null;
		validateMark = null;

		compoundEdit = null;
	}

	public class InsignificantCompoundEdit extends CompoundEdit {
		public boolean isSignificant() {
			return false;
		}
	}

	// This edit cannot be undone but can be
	public class NotUndoableEdit extends AbstractUndoableEdit {
		public boolean canRedo() {
			return false;
		}

		public boolean canUndo() {
			return false;
		}

		public boolean isSignificant() {
			return true;
		}
	}
}