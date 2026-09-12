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

package com.dogsbay.xml.author.model;

import java.util.ArrayList;
import java.util.List;

import com.dogsbay.xml.author.model.ops.InsertBlockOp;
import com.dogsbay.xml.author.model.ops.MoveBlockOp;
import com.dogsbay.xml.author.model.ops.RemoveBlockOp;
import com.dogsbay.xml.author.model.ops.SetAttributeOp;
import com.dogsbay.xml.author.model.ops.SetTextOp;
import com.dogsbay.xml.author.undo.BlockEdit;

/**
 * An atomic, undoable batch of block mutations.
 *
 * <p>Operations validate against the {@link BlockTypeRegistry} and apply
 * immediately (so later operations can read the intermediate state). If any
 * operation fails, everything already applied is reverted and the transaction
 * is dead. {@link #commit()} returns a single {@link BlockEdit} covering the
 * whole batch, ready to post to an undo manager.
 */
public final class Transaction {

    private final AuthorDocument doc;
    private final String presentationName;
    private final List<BlockOp> ops = new ArrayList<>();
    private boolean closed;

    Transaction(AuthorDocument doc, String presentationName) {
        this.doc = doc;
        this.presentationName = presentationName;
    }

    // ------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------

    /** Creates a block of {@code typeName} and inserts it under {@code parent} at {@code index}. */
    public AuthorBlock insertBlock(String typeName, AuthorBlock parent, int index) {
        AuthorBlock block = doc.createBlock(typeName);
        insertBlock(block, parent, index);
        return block;
    }

    /** Inserts an existing detached block (subtree) under {@code parent} at {@code index}. */
    public void insertBlock(AuthorBlock block, AuthorBlock parent, int index) {
        checkOpen();
        requireValidChild(parent, block);
        requireValidSequence(parent, block, index, null);
        run(new InsertBlockOp(parent, index, block));
    }

    /** Removes a block (subtree). The root cannot be removed. */
    public void removeBlock(AuthorBlock block) {
        checkOpen();
        if (block.getParent() == null) {
            throw rollbackAndFail("cannot remove the document root");
        }
        run(new RemoveBlockOp(block));
    }

    /** Moves a block under {@code newParent} at {@code newIndex} (index before removal). */
    public void moveBlock(AuthorBlock block, AuthorBlock newParent, int newIndex) {
        checkOpen();
        if (block.getParent() == null) {
            throw rollbackAndFail("cannot move the document root");
        }
        // Checked here, not in the op: MoveBlockOp detaches before it attaches,
        // so a cycle failing inside apply() would leave the block dangling.
        if (block.isAncestorOf(newParent)) {
            throw rollbackAndFail("cannot move " + block + " into its own subtree");
        }
        // Also checked here for the same reason: a bad index would fail after the detach.
        if (newIndex < 0 || newIndex > newParent.getChildren().size()) {
            throw rollbackAndFail("index " + newIndex + " is out of range for " + newParent
                    + " (" + newParent.getChildren().size() + " children)");
        }
        requireValidChild(newParent, block);
        requireValidSequence(newParent, block, newIndex, block);
        run(new MoveBlockOp(block, newParent, newIndex));
    }

    /** Sets ({@code value != null}) or removes one attribute. */
    public void setAttribute(AuthorBlock block, String name, String value) {
        checkOpen();
        run(new SetAttributeOp(block, name, value));
    }

    /** Replaces the inline text of a text block. */
    public void setText(AuthorBlock block, List<InlineRun> runs) {
        checkOpen();
        if (!block.getType().hasText()) {
            throw rollbackAndFail("block type '" + block.getType().getName() + "' does not carry text");
        }
        run(new SetTextOp(block, runs));
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /** Finalizes the transaction and returns the combined undoable edit. */
    public BlockEdit commit() {
        checkOpen();
        closed = true;
        return new BlockEdit(doc, presentationName, List.copyOf(ops));
    }

    /** Reverts every applied operation and closes the transaction. */
    public void rollback() {
        if (closed) {
            return;
        }
        closed = true;
        revertAll();
    }

    public boolean isEmpty() {
        return ops.isEmpty();
    }

    private void run(BlockOp op) {
        try {
            op.apply(doc);
        } catch (RuntimeException e) {
            closed = true;
            revertAll();
            throw e;
        }
        ops.add(op);
    }

    private void revertAll() {
        for (int i = ops.size() - 1; i >= 0; i--) {
            ops.get(i).revert(doc);
        }
        ops.clear();
    }

    private void requireValidChild(AuthorBlock parent, AuthorBlock child) {
        if (!doc.getRegistry().isValidChild(parent.getType(), child.getType())) {
            throw rollbackAndFail("'" + child.getType().getName()
                    + "' is not allowed inside '" + parent.getType().getName() + "'");
        }
    }

    /**
     * The parent's children with {@code block} at {@code index} (and
     * {@code moving} taken out first) must fit the parent's content model.
     */
    private void requireValidSequence(AuthorBlock parent, AuthorBlock block, int index, AuthorBlock moving) {
        if (parent.getType().getContentModel() == null) {
            return;
        }
        java.util.List<String> names = new java.util.ArrayList<>();
        for (AuthorBlock c : parent.getChildren()) {
            if (c != moving) {
                names.add(c.getType().getName());
            }
        }
        if (!parent.getType().acceptsChildren(names)) {
            return;   // an imported document already outside the model: do not lock its writer out
        }
        int at = Math.max(0, Math.min(index, names.size()));
        if (moving != null && moving.getParent() == parent && index > moving.indexInParent()) {
            at = Math.max(0, Math.min(index - 1, names.size()));
        }
        names.add(at, block.getType().getName());
        if (!parent.getType().acceptsChildren(names)) {
            throw rollbackAndFail("'" + block.getType().getName() + "' cannot go at position " + index
                    + " of '" + parent.getType().getName() + "': the content model allows "
                    + describe(parent.getType().getContentModel()));
        }
    }

    private static String describe(ContentModel model) {
        StringBuilder sb = new StringBuilder();
        for (ContentModel.Slot s : model.slots()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(String.join("|", s.names()));
            sb.append(s.max() == ContentModel.UNBOUNDED ? (s.min() == 0 ? "*" : "+") : s.min() == 0 ? "?" : "");
        }
        return sb.toString();
    }

    private AuthorStructureException rollbackAndFail(String message) {
        closed = true;
        revertAll();
        return new AuthorStructureException(message);
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("transaction is closed");
        }
    }
}
