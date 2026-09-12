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

package com.dogsbay.dogsbayaieditor.author;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;

/**
 * Marker wrapper for Author-view edits on the shared ChangeManager stack.
 * Like UndoableDesignerEdit, it identifies model-level (not text-level)
 * changes so updateModel() knows to re-export the block tree to the document
 * before other views read it.
 */
public class UndoableAuthorEdit extends javax.swing.undo.AbstractUndoableEdit {

    private final UndoableEdit delegate;
    private final java.util.function.Supplier<com.dogsbay.xml.author.model.AuthorDocument> currentModel;
    private final com.dogsbay.xml.author.model.AuthorDocument model;

    public UndoableAuthorEdit(UndoableEdit delegate) {
        this(delegate, null, null);
    }

    /**
     * @param currentModel the block model the Author view holds now, for staleness checks
     * @param model the block model this edit applies to
     */
    public UndoableAuthorEdit(UndoableEdit delegate,
            java.util.function.Supplier<com.dogsbay.xml.author.model.AuthorDocument> currentModel,
            com.dogsbay.xml.author.model.AuthorDocument model) {
        this.delegate = delegate;
        this.currentModel = currentModel;
        this.model = model;
    }

    /**
     * Whether the block model this edit applies to has been replaced (the
     * Author view re-imported the document after the XML side changed). A
     * stale edit can no longer be applied, so undo stops there rather than
     * corrupting the model.
     */
    public boolean isStale() {
        return currentModel != null && model != null && currentModel.get() != model;
    }

    @Override
    public boolean canUndo() {
        return super.canUndo() && !isStale();
    }

    @Override
    public boolean canRedo() {
        return super.canRedo() && !isStale();
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        delegate.undo();
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        delegate.redo();
    }

    @Override
    public boolean isSignificant() {
        return delegate.isSignificant();
    }

    @Override
    public String getPresentationName() {
        return delegate.getPresentationName();
    }

    @Override
    public void die() {
        super.die();
        delegate.die();
    }
}
