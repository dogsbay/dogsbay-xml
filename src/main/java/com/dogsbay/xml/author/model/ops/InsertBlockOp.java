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

/** Attaches a detached block (subtree) under a parent at a fixed index. */
public final class InsertBlockOp implements BlockOp {

    private final AuthorBlock parent;
    private final int index;
    private final AuthorBlock block;

    public InsertBlockOp(AuthorBlock parent, int index, AuthorBlock block) {
        this.parent = parent;
        this.index = index;
        this.block = block;
    }

    @Override
    public void apply(AuthorDocument doc) {
        doc.attach(parent, index, block);
    }

    @Override
    public void revert(AuthorDocument doc) {
        doc.detach(block);
    }
}
