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

package com.dogsbay.xml.author.model.ops;

import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.AuthorDocument;
import com.dogsbay.xml.author.model.BlockOp;

/** Moves a block to a new parent/index, remembering the old position for undo. */
public final class MoveBlockOp implements BlockOp {

    private final AuthorBlock block;
    private final AuthorBlock oldParent;
    private final int oldIndex;
    private final AuthorBlock newParent;
    private final int newIndex;

    public MoveBlockOp(AuthorBlock block, AuthorBlock newParent, int newIndex) {
        this.block = block;
        this.oldParent = block.getParent();
        this.oldIndex = block.indexInParent();
        this.newParent = newParent;
        this.newIndex = newIndex;
    }

    @Override
    public void apply(AuthorDocument doc) {
        doc.detach(block);
        doc.attach(newParent, adjustedNewIndex(), block);
    }

    @Override
    public void revert(AuthorDocument doc) {
        doc.detach(block);
        doc.attach(oldParent, oldIndex, block);
    }

    /**
     * When moving forward within the same parent, removing the block first
     * shifts later siblings down by one.
     */
    private int adjustedNewIndex() {
        if (newParent == oldParent && newIndex > oldIndex) {
            return newIndex - 1;
        }
        return newIndex;
    }
}
