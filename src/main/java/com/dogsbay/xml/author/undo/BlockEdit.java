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

package com.dogsbay.xml.author.undo;

import java.util.List;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import com.dogsbay.xml.author.model.AuthorDocument;
import com.dogsbay.xml.author.model.BlockOp;

/**
 * UndoableEdit covering one committed {@link com.dogsbay.xml.author.model.Transaction}.
 * Standard javax.swing.undo, so the editor can feed it to its shared
 * ChangeManager while standalone hosts use a plain UndoManager.
 */
public class BlockEdit extends AbstractUndoableEdit {

    private final transient AuthorDocument doc;
    private final String presentationName;
    private final transient List<BlockOp> ops;

    public BlockEdit(AuthorDocument doc, String presentationName, List<BlockOp> ops) {
        this.doc = doc;
        this.presentationName = presentationName;
        this.ops = ops;
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        for (int i = ops.size() - 1; i >= 0; i--) {
            ops.get(i).revert(doc);
        }
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        for (BlockOp op : ops) {
            op.apply(doc);
        }
    }

    @Override
    public String getPresentationName() {
        return presentationName;
    }

    @Override
    public boolean isSignificant() {
        return !ops.isEmpty();
    }
}
