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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The block tree for one document being authored, plus its id index and change
 * listeners.
 *
 * <p>Adapters build the tree with {@link #createBlock}, {@link #attach} and
 * {@link #setRoot} (unvalidated — imports must accept any real-world XML, with
 * unknown markup landing in raw blocks). All user-driven edits go through
 * {@link #begin(String)} transactions, which validate against the
 * {@link BlockTypeRegistry} and produce {@link javax.swing.undo.UndoableEdit}s.
 */
public final class AuthorDocument {

    private final BlockTypeRegistry registry;
    private final Map<String, AuthorBlock> blocksById = new HashMap<>();
    private final CopyOnWriteArrayList<AuthorDocumentListener> listeners = new CopyOnWriteArrayList<>();
    private AuthorBlock root;
    private long nextId = 1;

    public AuthorDocument(BlockTypeRegistry registry) {
        this.registry = registry;
    }

    public BlockTypeRegistry getRegistry() {
        return registry;
    }

    public AuthorBlock getRoot() {
        return root;
    }

    public AuthorBlock getBlock(String id) {
        return blocksById.get(id);
    }

    // ------------------------------------------------------------------
    // Construction API (adapters and ops; not validated against the registry)
    // ------------------------------------------------------------------

    /** Creates a detached block of the given type with a fresh document-unique id. */
    public AuthorBlock createBlock(String typeName) {
        return new AuthorBlock("b" + nextId++, registry.require(typeName));
    }

    /** Creates a detached raw-XML fallback block. */
    public AuthorBlock createRawBlock(String rawXml) {
        AuthorBlock block = new AuthorBlock("b" + nextId++, registry.raw());
        block.setRawXml(rawXml);
        return block;
    }

    public void setRoot(AuthorBlock newRoot) {
        if (root != null) {
            unregister(root);
        }
        root = newRoot;
        if (newRoot != null) {
            if (newRoot.getParent() != null) {
                throw new AuthorStructureException("root must be detached");
            }
            register(newRoot);
        }
        fire(new AuthorDocumentEvent(this, AuthorDocumentEvent.Kind.ROOT, newRoot));
    }

    /**
     * Attaches a detached block (subtree) under {@code parent} at {@code index}.
     * Nesting rules are NOT checked here — that is {@link Transaction}'s job.
     */
    public void attach(AuthorBlock parent, int index, AuthorBlock child) {
        if (child.getParent() != null) {
            throw new AuthorStructureException(child + " is already attached");
        }
        if (child == root || child.isAncestorOf(parent)) {
            throw new AuthorStructureException("cannot attach " + child + " under its own subtree");
        }
        parent.children().add(index, child);
        child.setParent(parent);
        register(child);
        fire(new AuthorDocumentEvent(this, AuthorDocumentEvent.Kind.STRUCTURE, parent));
    }

    /** Detaches a block (subtree) from its parent. The root cannot be detached. */
    public void detach(AuthorBlock block) {
        AuthorBlock parent = block.getParent();
        if (parent == null) {
            throw new AuthorStructureException("cannot detach " + block + " (no parent)");
        }
        parent.children().remove(block);
        block.setParent(null);
        unregister(block);
        fire(new AuthorDocumentEvent(this, AuthorDocumentEvent.Kind.STRUCTURE, parent));
    }

    /** Replaces a block's inline text and notifies listeners. */
    public void setBlockText(AuthorBlock block, java.util.List<InlineRun> runs) {
        block.setText(runs);
        fire(new AuthorDocumentEvent(this, AuthorDocumentEvent.Kind.TEXT, block));
    }

    /** Sets ({@code value != null}) or removes an attribute and notifies listeners. */
    public void setBlockAttribute(AuthorBlock block, String name, String value) {
        block.setAttribute(name, value);
        fire(new AuthorDocumentEvent(this, AuthorDocumentEvent.Kind.ATTRIBUTES, block));
    }

    // ------------------------------------------------------------------
    // Transactions
    // ------------------------------------------------------------------

    /** Starts an undoable transaction; ops apply immediately, commit yields the edit. */
    public Transaction begin(String presentationName) {
        return new Transaction(this, presentationName);
    }

    // ------------------------------------------------------------------
    // Listeners
    // ------------------------------------------------------------------

    public void addListener(AuthorDocumentListener listener) {
        listeners.add(listener);
    }

    public void removeListener(AuthorDocumentListener listener) {
        listeners.remove(listener);
    }

    private void fire(AuthorDocumentEvent event) {
        for (AuthorDocumentListener l : listeners) {
            l.documentChanged(event);
        }
    }

    private void register(AuthorBlock block) {
        AuthorBlock previous = blocksById.put(block.getId(), block);
        if (previous != null && previous != block) {
            throw new AuthorStructureException("duplicate block id: " + block.getId());
        }
        for (AuthorBlock child : block.getChildren()) {
            register(child);
        }
    }

    private void unregister(AuthorBlock block) {
        blocksById.remove(block.getId());
        for (AuthorBlock child : block.getChildren()) {
            unregister(child);
        }
    }
}
