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

/** Detaches a block (subtree), remembering its position for undo. */
public final class RemoveBlockOp implements BlockOp {

    private final AuthorBlock block;
    private final AuthorBlock parent;
    private final int index;

    public RemoveBlockOp(AuthorBlock block) {
        this.block = block;
        this.parent = block.getParent();
        this.index = block.indexInParent();
    }

    @Override
    public void apply(AuthorDocument doc) {
        doc.detach(block);
    }

    @Override
    public void revert(AuthorDocument doc) {
        doc.attach(parent, index, block);
    }
}
